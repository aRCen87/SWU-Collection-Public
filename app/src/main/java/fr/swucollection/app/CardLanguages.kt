package fr.swucollection.app

import android.content.Context
import org.json.JSONObject
import androidx.compose.runtime.*
import java.util.zip.GZIPInputStream
import java.io.InputStream
import java.io.FileNotFoundException

internal object CardLanguages {
    const val UNKNOWN = "UND"
    val supported = listOf("FR", "EN", "DE", "ES", "IT")
    fun valid(value: String) = value in supported || value == UNKNOWN
    fun label(value: String) = when (value) { "FR" -> "Français"; "EN" -> "Anglais"; "DE" -> "Allemand"; "ES" -> "Espagnol"; "IT" -> "Italien"; else -> "À préciser" }
    internal fun imageLanguage(url: String): String? {
        val file=url.substringAfterLast('/')
        for (pattern in listOf("^card_SWH_\\d{2}(FR|EN|DE|ES|IT)_","^card_\\d+(FR|EN|DE|ES|IT)_","^card_[^_]+_(?:\\d+_)?(FR|EN|DE|ES|IT)_")) {
            val match=Regex(pattern,RegexOption.IGNORE_CASE).find(file)
            if(match!=null)return match.groupValues[1].uppercase(java.util.Locale.ROOT)
        }
        return if(Regex("^card_SWH_\\d{2}_",RegexOption.IGNORE_CASE).containsMatchIn(file)) "EN" else null
    }
    fun read(row: JSONObject, default: String = UNKNOWN): String {
        val value = if (row.has("language")) row.get("language") else default
        require(value is String && valid(value)) { "Langue de carte invalide." }
        return value
    }
}

/** Exact publisher URLs, indexed by collector reference. No guessed language URL substitution. */
internal object CardLanguageArtwork {
    private data class Tables(
        val cards: Map<String, Map<String, List<String>>>,
        val prints: Map<String, Map<String, Map<String, List<String>>>>
    )
    @Volatile private var images: Tables? = null
    private fun load(context: Context): Tables = images ?: synchronized(this) {
        images ?: run {
            val root = loadAsset { context.assets.open(it) }
            require(root.getString("format") == "SWU_CARD_ART_LANGUAGES")
            val dictionary = root.getJSONArray("urls")
            fun faces(languages: JSONObject): Map<String,List<String>> = CardLanguages.supported.associateWith { language ->
                val row = languages.optJSONArray(language)
                (0..1).map { i ->
                    val index = row?.optInt(i,-1) ?: -1
                    if (index in 0 until dictionary.length()) dictionary.getString(index) else ""
                }
            }
            val rows = root.getJSONObject("cards")
            val cards = rows.keys().asSequence().associateWith { key -> faces(rows.getJSONObject(key)) }
            val extra = root.optJSONObject("printArt") ?: JSONObject()
            val prints = extra.keys().asSequence().associateWith { key ->
                val variants = extra.getJSONObject(key)
                variants.keys().asSequence().associateWith { variant -> faces(variants.getJSONObject(variant)) }
            }
            Tables(cards,prints).also { images = it }
        }
    }
    internal fun loadAsset(open: (String) -> InputStream): JSONObject {
        val input = try { open("card-art-languages.json") } catch (_: FileNotFoundException) { open("card-art-languages.json.gz") }
        return input.buffered().use { buffered ->
            buffered.mark(2)
            val compressed = buffered.read() == 0x1f && buffered.read() == 0x8b
            buffered.reset()
            val decoded = if (compressed) GZIPInputStream(buffered) else buffered
            decoded.bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
        }
    }
    fun localized(context: Context, card: CatalogCard, language: String, variant: String? = null): CatalogCard {
        require(CardLanguages.valid(language))
        if (language == CardLanguages.UNKNOWN) return card.copy(language = language)
        val faces = runCatching {
            val data = load(context); val key = MarketPricing.key(card.setCode,card.number)
            variant?.let { data.prints[key]?.get(it) } ?: data.cards[key]
        }.getOrNull()
        return select(card,language,faces)
    }
    /** Display fallback is explicit and never changes the stored language or finish. */
    fun displayFace(context: Context, card: CatalogCard, language: String, variant: String? = null, reverse: Boolean = false): Pair<String,String?> {
        fun face(value: CatalogCard) = if (reverse) value.backImageUrl else value.imageUrl
        val exact = face(localized(context,card,language,variant))
        if (exact.isNotBlank()) return exact to CardLanguages.imageLanguage(exact)
        for (fallback in listOf("EN","FR","DE","ES","IT").filter { it != language }) {
            val url = face(localized(context,card,fallback,variant))
            if (url.isNotBlank()) return url to fallback
        }
        return "" to null
    }
    fun allFaces(context: Context,card: CatalogCard): List<CatalogCard> = runCatching {
        val data=load(context); val key=MarketPricing.key(card.setCode,card.number)
        val all=listOfNotNull(data.cards[key]) + data.prints[key].orEmpty().values
        if(all.isEmpty()) listOf(card) else all.flatMap { faces ->
            CardLanguages.supported.map { language -> select(card,language,faces) }
        }
    }.getOrElse { listOf(card) }
    internal fun select(card: CatalogCard, language: String, faces: Map<String, List<String>>?): CatalogCard {
        require(CardLanguages.valid(language))
        if (language == CardLanguages.UNKNOWN) return card.copy(language = language)
        fun checked(url: String): String = url.takeIf { CardLanguages.imageLanguage(it).let { code -> code == null || code == language } }.orEmpty()
        fun fallback(url: String): String = url.takeIf {
            val code=CardLanguages.imageLanguage(it)
            code == language || (code == null && card.language == language)
        }.orEmpty()
        val front = faces?.get(language)?.getOrNull(0)?.let(::checked) ?: fallback(card.imageUrl)
        val back = faces?.get(language)?.getOrNull(1)?.let(::checked) ?: fallback(card.backImageUrl)
        return card.copy(imageUrl = front, backImageUrl = back, language = language)
    }
}

@androidx.compose.runtime.Composable
internal fun CardLanguageChoices(language: String, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Column {
        androidx.compose.material3.Text("Langue de la carte")
        androidx.compose.foundation.layout.Box {
            androidx.compose.material3.OutlinedButton(onClick = { expanded = true }) {
                androidx.compose.material3.Text(if (language == CardLanguages.UNKNOWN) "Choisir une langue" else CardLanguages.label(language))
                androidx.compose.material3.Text(" ▾")
            }
            androidx.compose.material3.DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                CardLanguages.supported.forEach { code ->
                    androidx.compose.material3.DropdownMenuItem(text = { androidx.compose.material3.Text(CardLanguages.label(code)) },
                        onClick = { onChange(code); expanded = false })
                }
            }
        }
    }
}
