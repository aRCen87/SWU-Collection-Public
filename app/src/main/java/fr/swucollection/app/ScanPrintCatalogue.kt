package fr.swucollection.app

import android.content.Context
import org.json.JSONObject
import java.io.FileNotFoundException
import java.util.zip.GZIPInputStream

/** Small identity-checked print table; shared collector numbers always require a choice. */
internal object ScanPrintCatalogue {
    @Volatile private var rows: Map<String,List<String>> = emptyMap()
    fun initialize(context: Context) {
        rows = runCatching {
            val input = try { context.assets.open("card-print-finishes.json") }
                catch (_: FileNotFoundException) { context.assets.open("card-print-finishes.json.gz") }
            input.buffered().use { buffered ->
                buffered.mark(2); val zipped = buffered.read()==0x1f && buffered.read()==0x8b;buffered.reset()
                val decoded = if(zipped) GZIPInputStream(buffered) else buffered
                decoded.bufferedReader(Charsets.UTF_8).use { parse(it.readText()) }
            }
        }.getOrDefault(emptyMap())
    }
    internal fun parse(json: String): Map<String,List<String>> {
        val root=JSONObject(json);require(root.getString("format")=="SWU_PRINT_FINISHES")
        val cards=root.getJSONObject("cards")
        return cards.keys().asSequence().associateWith { key ->
            val array=cards.getJSONArray(key)
            val values=(0 until array.length()).map { array.getString(it).also { value -> require(value in ScanVariants.options) } }.toSet()
            ScanVariants.options.filter { it in values }
        }
    }
    fun choices(card: CatalogCard): List<String>? = rows[MarketPricing.key(card.setCode,card.number)]
}
