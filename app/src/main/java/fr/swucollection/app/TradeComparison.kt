package fr.swucollection.app

import org.json.JSONArray
import org.json.JSONObject

internal data class TradeItem(val set: String,val number: String,val variant: String,val name: String,
    val family: String,val quantity: Int,val acceptVariants: Boolean = false, val language: String = CardLanguages.UNKNOWN) {
    val printKey: String get()="${MarketPricing.key(set,number)}:$variant"
    val key: String get()="$printKey:$language"
    fun json()=JSONObject().put("set",set).put("number",number).put("variant",variant).put("name",name).put("family",family).put("quantity",quantity).put("acceptVariants",acceptVariants).put("language",language)
}
internal data class TradeProfile(val offers: List<TradeItem>,val needs: List<TradeItem>,val acceptVariants: Boolean=false) {
    fun json(): String=JSONObject().put("format","SWU_TRADE_PROFILE").put("version",3)
        .put("offers",JSONArray().apply { offers.forEach { put(it.json()) } })
        .put("needs",JSONArray().apply { needs.forEach { put(it.json()) } }).put("acceptVariants",acceptVariants).toString()
    companion object {
        fun parse(text: String): TradeProfile {
            require(text.toByteArray(Charsets.UTF_8).size<=4*1024*1024) { "Profil trop volumineux." }
            val root=JSONObject(text);require(root.getString("format")=="SWU_TRADE_PROFILE"&&root.getInt("version") in 1..3) { "Profil d’échange invalide." }
            fun rows(field: String): List<TradeItem> {
                val a=root.getJSONArray(field);require(a.length()<=20000)
                val seen=mutableSetOf<String>()
                return (0 until a.length()).map { i ->val r=a.getJSONObject(i)
                    require(root.getInt("version") < 3 || r.has("language")) { "Langue absente du profil d’échange." }
                    TradeItem(r.getString("set"),r.getString("number"),r.getString("variant"),r.getString("name"),r.getString("family"),r.getInt("quantity"),r.optBoolean("acceptVariants",false),CardLanguages.read(r)).also {
                        require(it.set.matches(Regex("[A-Z0-9]{2,12}"))&&it.number.matches(Regex("[0-9]{1,5}")))
                        require(it.variant.length in 1..60&&it.name.length in 1..350&&it.family.length in 1..100&&it.quantity in 1..9999&&seen.add(it.key))
                    }
                }
            }
            return TradeProfile(rows("offers"),rows("needs"),root.optBoolean("acceptVariants",false))
        }
        fun build(catalog: List<CatalogCard>,owned: List<CollectionCard>,goals: Map<String,Boolean>,details: CardDetails,
            setFilter: String="Toutes",acceptVariants: Boolean=false,variantsBySet: Map<String,Boolean> = emptyMap(), reserved: Map<String,Int> = emptyMap(), printGoals: Map<String,PrintGoal> = emptyMap()): TradeProfile {
            val scope=if(setFilter=="Toutes") goals.keys+owned.map { it.setCode } else setOf(setFilter)
            val selected=catalog.filter { it.setCode in scope }
            val byKey=selected.associateBy { MarketPricing.key(it.setCode,it.number) }
            val offers = scope.flatMap { code ->
                val exactGoal = printGoals[code]
                val retainedOwned = if (exactGoal == null) owned else owned.filter { it.variant == exactGoal.finish && (exactGoal.language.isBlank() || it.language == exactGoal.language) }
                CollectionObjectives.extras(retainedOwned.filter { it.setCode == code }.map { it.copy(quantity=(it.quantity-(reserved[loanKey(it)]?:0)).coerceAtLeast(0)) }, selected.filter { it.setCode == code },
                    goals[code] ?: true, acceptVariants || variantsBySet[code] == true, details)
            }.mapNotNull { row ->
                val c = byKey[MarketPricing.key(row.setCode,row.number)] ?: return@mapNotNull null
                TradeItem(c.setCode,c.number,row.variant,c.name,details.family(c),row.quantity,language=row.language)
            }
            val needs = scope.flatMap { code ->
                val accept = printGoals[code] == null && (acceptVariants || variantsBySet[code] == true)
                CollectionObjectives.rows(selected.filter { it.setCode == code }, owned, goals[code] ?: true, accept, details, printGoals[code])
                    .filter { it.missing > 0 }.map { TradeItem(it.card.setCode,it.card.number,it.variant,it.card.name,details.family(it.card),it.missing,accept, it.language.ifBlank { CardLanguages.UNKNOWN }) }
            }
            return TradeProfile(offers,needs,acceptVariants)
        }
    }
}
internal data class TradeMatch(val offered: TradeItem,val wanted: TradeItem,val quantity: Int)
internal object TradeComparison {
    /** Allocate each offered copy at most once, even when several wants share a family. */
    fun matching(giver: TradeProfile,receiver: TradeProfile): List<TradeMatch> {
        val available=giver.offers.associate { it.key to it.quantity }.toMutableMap()
        val remaining=receiver.needs.associate {it.key to it.quantity}.toMutableMap()
        val result=mutableListOf<TradeMatch>()
        val exact=giver.offers.groupBy {it.printKey}
        val families=giver.offers.groupBy {it.family}
        fun allocate(offered: TradeItem,wanted: TradeItem) {
            if (wanted.language != CardLanguages.UNKNOWN && offered.language != wanted.language) return
            val qty=minOf(remaining[wanted.key]?:0,available[offered.key]?:0)
            if(qty>0) {result+=TradeMatch(offered,wanted,qty);remaining[wanted.key]=(remaining[wanted.key]?:0)-qty;available[offered.key]=(available[offered.key]?:0)-qty}
        }
        // Reserve all exact matches before allocating interchangeable versions.
        receiver.needs.forEach { wanted -> exact[wanted.printKey].orEmpty().forEach { allocate(it,wanted) } }
        receiver.needs.filter {(receiver.acceptVariants || it.acceptVariants) && !it.family.startsWith("unknown:")}.forEach {wanted->
            families[wanted.family].orEmpty().filter { receiver.acceptVariants || it.set == wanted.set }.forEach {offered->allocate(offered,wanted)}
        }
        return result
    }
}

