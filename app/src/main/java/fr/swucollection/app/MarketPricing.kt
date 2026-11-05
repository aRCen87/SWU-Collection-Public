package fr.swucollection.app

import org.json.JSONObject
import java.text.Normalizer
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

internal data class MarketQuote(
    val amount: Double?, val date: String = "", val cached: Boolean = false,
    val message: String = "", val product: String = "", val maxAmount: Double? = null,
    val search: String = ""
)

/** Parses only published Cardmarket trend figures; no inferred or invented market prices. */
internal object MarketPricing {
    const val CATALOGUE = "Catalogue"
    fun key(set: String, number: String): String = set.trim().uppercase(java.util.Locale.ROOT) + ":" +
        number.trim().substringBefore('/').trim().padStart(3, '0')

    fun timestamp(root: JSONObject): Long = runCatching {
        OffsetDateTime.parse(root.getString("createdAt")
            .replace(Regex("([+-][0-9]{2})([0-9]{2})$"), "$1:$2")).toInstant().toEpochMilli()
    }.getOrDefault(0L)

    fun displayDate(date: String): String = runCatching {
        java.time.LocalDate.parse(date.take(10)).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
    }.getOrDefault(date.take(10))

    fun validate(root: JSONObject): JSONObject {
        require(timestamp(root) > 0 && root.getJSONArray("priceGuides").length() in 1..50000)
        return root
    }

    fun validateMapping(root: JSONObject): JSONObject {
        require(root.optInt("version") == 3 && timestamp(root) > 0)
        val cards = root.getJSONObject("cards")
        require(cards.length() in 2500..20000)
        cards.keys().forEach { reference ->
            require(reference.matches(Regex("[A-Z0-9]+:[0-9]+")))
            val entry = cards.getJSONObject(reference)
            require(entry.getString("nameFr").isNotBlank() && entry.getString("nameEn").isNotBlank())
            val finishes = entry.getJSONObject("finishes")
            require(finishes.length() > 0)
            finishes.keys().forEach { finish ->
                val selection = finishes.getJSONObject(finish)
                require(selection.getString("field") in listOf("trend", "trend-foil"))
                val ids = selection.getJSONArray("products")
                require(ids.length() in 1..30)
                for (index in 0 until ids.length()) require(ids.getLong(index) > 0)
            }
            val default = entry.optString("defaultFinish")
            require(default.isEmpty() || finishes.has(default))
        }
        return root
    }

    private fun sameName(a: String, b: String) =
        Normalizer.normalize(a.trim(), Normalizer.Form.NFC)
            .equals(Normalizer.normalize(b.trim(), Normalizer.Form.NFC), ignoreCase = true)

    fun resolve(entry: JSONObject?, expectedName: String, rows: Map<Long, JSONObject>,
                variant: String, date: String, cached: Boolean): MarketQuote {
        if (entry == null || !sameName(entry.optString("nameFr"), expectedName))
            return MarketQuote(null, message = "Cette impression n’est pas encore reliée à Cardmarket. Consulte sa fiche avec le bouton ci-dessous.")
        val product = entry.getString("nameFr")
        val search = entry.getString("nameEn")
        val finish = if (variant == CATALOGUE) entry.optString("defaultFinish", "Normale") else variant
        val selections = entry.optJSONObject("finishes")
        val selection = if (selections != null) selections.optJSONObject(finish)
            else if (finish == "Normale" || finish == "Foil") entry else null
        if (selection == null) return MarketQuote(null,
            message = "Cette variante n’est pas encore reliée à un produit Cardmarket vérifié.",
            product = product, search = search)
        val ids = selection.getJSONArray("products")
        val field = selection.optString("field", if (finish == "Foil") "trend-foil" else "trend")
        if (field != "trend" && field != "trend-foil") return MarketQuote(null,
            message = "Correspondance Cardmarket invalide.", product = product, search = search)
        val average = if (field == "trend-foil") "avg-foil" else "avg"
        val candidates = (0 until ids.length()).mapNotNull { rows[ids.getLong(it)] }
            .filter { it.optInt("idCategory") == 1644 }
            .filter { it.optDouble(field, Double.NaN).let { price -> price.isFinite() && price > 0.0 } }
        // The guide separates regular and foil trade averages, even when a stale trend remains
        // on another product. Never choose the first ID or the cheapest product arbitrarily.
        val active = candidates.filter { it.optDouble(average, Double.NaN).let { v -> v.isFinite() && v > 0 } }
        val baseRange = selection.optBoolean("range")
        val selected = if (!baseRange && active.size == 1) active else candidates
        if (selected.isEmpty()) return MarketQuote(null, date, cached,
            "Pas de tendance $finish exploitable dans le guide téléchargé. Le prix peut être consultable sur Cardmarket.", product, search = search)
        val range = baseRange || selected.size > 1
        val values = selected.map { it.getDouble(field) }
        return MarketQuote(values.minOrNull(), date, cached,
            if (baseRange) "Fourchette selon le verso de la base, non identifié dans le catalogue."
            else if (range) "Fourchette entre impressions Cardmarket de la même carte." else "",
            product, if (range) values.maxOrNull() else null, search)
    }
}
