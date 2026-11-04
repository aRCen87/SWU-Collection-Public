package fr.swucollection.app

import org.json.JSONArray
import org.json.JSONObject

/** Portable deck interchange; never changes inventory quantities. */
internal data class DeckEntry(val cardId: String, val count: Int)
internal data class PortableDeck(
    val leader: String,
    val base: String,
    val deck: List<DeckEntry>,
    val sideboard: List<DeckEntry> = emptyList()
)

/** SWUDB-compatible JSON structure. Verify against live exports before enabling UI. */
internal object DeckInterchange {
    private val cardIdPattern = Regex("^[A-Za-z0-9]{2,12}[-_][0-9]{1,5}$")

    fun normalizeId(raw: String): String {
        val id = raw.trim().uppercase().replace('_', '-')
        require(cardIdPattern.matches(id)) { "Référence de carte invalide : utilise le format SET-001." }
        val parts = id.split('-')
        return parts[0] + "-" + parts[1].padStart(3, '0')
    }

    private fun readIdentity(value: Any?): String = when (value) {
        is String -> normalizeId(value)
        is JSONObject -> {
            if (value.has("count")) {
                val count = value.get("count")
                require((count is Int || count is Long) && (count as Number).toLong() == 1L) { "Le leader et la base doivent être en un exemplaire." }
            }
            normalizeId(value.getString("id"))
        }
        else -> throw IllegalArgumentException("Leader ou base absent : vérifie les références du deck.")
    }

    private fun parseEntries(value: JSONArray?): List<DeckEntry> {
        if (value == null) return emptyList()
        val result = linkedMapOf<String, Int>()
        for (i in 0 until value.length()) {
            val row = value.getJSONObject(i)
            val id = normalizeId(row.getString("id"))
            val countValue = row.opt("count")
            require(countValue is Int || countValue is Long) { "La quantité de chaque carte doit être un nombre entier." }
            val countLong = (countValue as Number).toLong()
            require(countLong in 1L..99L) { "La quantité de chaque carte doit être comprise entre 1 et 99." }
            val count = countLong.toInt()
            require(count in 1..99) { "La quantité de chaque carte doit être comprise entre 1 et 99." }
            result[id] = (result[id] ?: 0) + count
            require(result.getValue(id) <= 99) { "Une référence dépasse 99 exemplaires." }
        }
        return result.map { DeckEntry(it.key, it.value) }
    }

    fun importJson(raw: String): PortableDeck {
        require(raw.length <= 1_000_000) { "Fichier trop volumineux (1 Mo maximum)." }
        val root = JSONObject(raw)
        require(!root.has("secondleader") || root.isNull("secondleader")) { "Les decks à deux leaders ne sont pas encore pris en charge." }
        require(!root.has("deck") || root.get("deck") is JSONArray) { "Deck principal invalide." }
        require(!root.has("sideboard") || root.get("sideboard") is JSONArray) { "Réserve invalide." }
        return PortableDeck(
            leader = readIdentity(root.opt("leader")),
            base = readIdentity(root.opt("base")),
            deck = parseEntries(root.optJSONArray("deck")),
            sideboard = parseEntries(root.optJSONArray("sideboard"))
        )
    }

    fun exportJson(deck: PortableDeck, name: String = "Mon deck"): String {
        fun externalId(id: String) = normalizeId(id).replace('-', '_')
        fun entries(items: List<DeckEntry>) = JSONArray().also { array ->
            items.forEach {
                require(it.count in 1..99)
                array.put(JSONObject().put("id", externalId(it.cardId)).put("count", it.count))
            }
        }
        return JSONObject()
            .put("metadata", JSONObject().put("name", name.trim().take(120)).put("author", ""))
            .put("leader", JSONObject().put("id", externalId(deck.leader)).put("count", 1))
            .put("base", JSONObject().put("id", externalId(deck.base)).put("count", 1))
            .put("deck", entries(deck.deck))
            .put("sideboard", entries(deck.sideboard))
            .toString(2)
    }
}



