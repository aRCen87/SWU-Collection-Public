package fr.swucollection.app

import android.content.Context
import org.json.JSONObject
import java.util.zip.GZIPInputStream
import java.io.InputStream
import java.io.FileNotFoundException

internal data class CardDetail(val family: String, val englishName: String, val cost: Int?,
    val aspects: List<String>, val traits: List<String>, val arena: String)
internal class CardDetails(private val rows: Map<String,CardDetail>) {
    fun get(card: CatalogCard): CardDetail?=rows[MarketPricing.key(card.setCode,card.number)]
    fun family(card: CatalogCard)=get(card)?.family ?: "unknown:${MarketPricing.key(card.setCode,card.number)}"
    fun related(card: CatalogCard,catalog: List<CatalogCard>): List<CatalogCard> {
        val family=get(card)?.family ?: return listOf(card)
        return catalog.filter { get(it)?.family==family }.sortedWith(compareBy({it.setCode},{it.number.toIntOrNull()?:99999}))
    }
    companion object {
        fun empty(): CardDetails = CardDetails(emptyMap())
        fun load(context: Context): CardDetails = loadAsset { context.assets.open(it) }

        // AAPT expands .gz assets and strips their suffix in the installed APK.
        // Source-asset test environments can still expose the original gzip file.
        internal fun loadAsset(open: (String) -> InputStream): CardDetails {
            val input = try {
                open("card-details.json")
            } catch (_: FileNotFoundException) {
                open("card-details.json.gz")
            }
            return input.buffered().use { buffered ->
                buffered.mark(2)
                val gzip = buffered.read() == 0x1f && buffered.read() == 0x8b
                buffered.reset()
                val decoded = if (gzip) GZIPInputStream(buffered) else buffered
                decoded.bufferedReader(Charsets.UTF_8).use { parse(it.readText()) }
            }
        }
        fun parse(json: String): CardDetails {
            val root=JSONObject(json);require(root.getString("format")=="SWU_CARD_DETAILS"&&root.getInt("version")==1)
            val cards=root.getJSONObject("cards")
            return CardDetails(cards.keys().asSequence().associateWith { key ->
                val c=cards.getJSONObject(key)
                fun strings(field: String)=c.getJSONArray(field).let { a ->(0 until a.length()).map { a.getString(it) } }
                CardDetail(c.getString("family"),c.getString("en"),if(c.isNull("cost"))null else c.getInt("cost"),strings("aspects"),strings("traits"),c.optString("arena"))
            })
        }
    }
}
internal val aspectNames=mapOf("Vigilance" to "Vigilance","Command" to "Commandement","Aggression" to "Agressivité",
    "Cunning" to "Ruse","Heroism" to "Héroïsme","Villainy" to "Infâmie")
internal data class SearchFacets(val set: String="Toutes",val rarity: String="Toutes",val type: String="Tous",
    val aspect: String="Toutes",val minCost: Int?=null,val maxCost: Int?=null) {
    fun matches(card: CatalogCard, detail: CardDetail?): Boolean =
        (set=="Toutes"||card.setCode==set)&&(rarity=="Toutes"||card.rarity==rarity)&&
        (type=="Tous"||card.type==type)&&(aspect=="Toutes"||detail?.aspects?.contains(aspect)==true)&&
        (minCost==null||(detail?.cost?.let { it>=minCost }==true))&&
        (maxCost==null||(detail?.cost?.let { it<=maxCost }==true))
}

