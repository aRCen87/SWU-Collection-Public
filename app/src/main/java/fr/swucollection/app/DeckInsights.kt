package fr.swucollection.app

internal object DeckInsights {
    enum class Affinity { MATCH, OUTSIDE, UNKNOWN }
    data class Analysis(val total: Int, val costs: Map<String, Int>, val types: Map<String, Int>,
        val arenas: Map<String, Int>, val aspects: Map<String, Int>, val unknownCosts: Int, val unknownCards: Int)
    data class Estimate(val low: Double, val high: Double, val priced: Int, val unknown: Int)
    data class Budget(val total: Estimate, val missing: Estimate, val date: String, val cached: Boolean)
    fun affinityPool(deck: PortableDeck, catalog: List<CatalogCard>, details: CardDetails): List<String>? {
        val byId = catalog.associateBy(DeckConstruction::reference)
        val leader = byId[deck.leader]?.let(details::get) ?: return null
        val base = byId[deck.base]?.let(details::get) ?: return null
        return leader.aspects + base.aspects
    }
    fun affinity(card: CatalogCard, pool: List<String>?, details: CardDetails): Affinity {
        if (pool == null) return Affinity.UNKNOWN
        val requested = details.get(card)?.aspects ?: return Affinity.UNKNOWN
        val available = pool.groupingBy { it }.eachCount()
        return if (requested.groupingBy { it }.eachCount().all { (aspect, count) -> (available[aspect] ?: 0) >= count }) Affinity.MATCH else Affinity.OUTSIDE
    }
    fun analyse(entries: List<DeckEntry>, catalog: List<CatalogCard>, details: CardDetails): Analysis {
        val byId = catalog.associateBy(DeckConstruction::reference)
        val costs = linkedMapOf<String, Int>(); val types = linkedMapOf<String, Int>()
        val arenas = linkedMapOf<String, Int>(); val aspects = linkedMapOf<String, Int>()
        var unknownCosts = 0; var unknownCards = 0
        fun add(map: MutableMap<String, Int>, key: String, count: Int) { map[key] = (map[key] ?: 0) + count }
        entries.forEach { row ->
            val card = byId[row.cardId]
            if (card == null) { unknownCards += row.count; unknownCosts += row.count; return@forEach }
            val detail = details.get(card)
            if (detail?.cost == null) unknownCosts += row.count else add(costs, if (detail.cost >= 8) "8+" else detail.cost.toString(), row.count)
            add(types, card.type, row.count)
            if (DeckConstruction.type(card) in listOf("unite", "unit")) add(arenas, when (detail?.arena?.lowercase()) {
                "ground", "ground arena", "terrestre" -> "Terrestres"; "space", "space arena", "spatiale" -> "Spatiales"; else -> "Arène inconnue"
            }, row.count)
            if (detail == null) add(aspects, "Affinités inconnues", row.count)
            else if (detail.aspects.isEmpty()) add(aspects, "Neutres", row.count)
            else detail.aspects.distinct().forEach { add(aspects, aspectNames[it] ?: it, row.count) }
        }
        return Analysis(entries.sumOf { it.count }, (0..7).associate { it.toString() to (costs[it.toString()] ?: 0) } + ("8+" to (costs["8+"] ?: 0)), types, arenas, aspects, unknownCosts, unknownCards)
    }
    fun missingPrints(deck: PortableDeck, owned: List<CollectionCard>, catalog: List<CatalogCard>, details: CardDetails): List<PrintCard> {
        val byId = catalog.associateBy(DeckConstruction::reference)
        fun family(card: CatalogCard) = details.family(card)
        val stock = owned.mapNotNull { row -> DeckConstruction.stockReference(row.setCode, row.number)?.let { byId[it] }?.let { family(it) to row.quantity } }
            .groupBy { it.first }.mapValues { (_, rows) -> rows.sumOf { it.second } }.toMutableMap()
        return CardPdfPlan.fromDeck(deck, catalog).mapNotNull { item ->
            val key = family(item.card); val available = stock[key] ?: 0
            val used = minOf(available.coerceAtLeast(0), item.quantity)
            stock[key] = available - used
            (item.quantity - used).takeIf { it > 0 }?.let { item.copy(quantity = it) }
        }
    }
    fun budget(all: List<PrintCard>, missing: List<PrintCard>, quotes: Map<String, MarketQuote>): Budget {
        val used = mutableListOf<MarketQuote>()
        fun estimate(items: List<PrintCard>): Estimate {
            var low = 0.0; var high = 0.0; var priced = 0; var unknown = 0
            items.forEach { item ->
                val q = quotes[DeckConstruction.reference(item.card)]
                val price = q?.amount?.takeIf { it.isFinite() && it > 0 }
                if (price == null) unknown += item.quantity else {
                    val quote = requireNotNull(q)
                    val upper = quote.maxAmount?.takeIf { it.isFinite() && it >= price } ?: price
                    low += price * item.quantity; high += upper * item.quantity; priced += item.quantity; used += quote
                }
            }
            return Estimate(low, high, priced, unknown)
        }
        val total = estimate(all); val short = estimate(missing)
        return Budget(total, short, used.map { it.date.take(10) }.filter { it.isNotBlank() }.minOrNull().orEmpty(), used.any { it.cached })
    }
}
