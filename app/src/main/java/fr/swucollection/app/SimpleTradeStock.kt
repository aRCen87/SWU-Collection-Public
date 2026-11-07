package fr.swucollection.app

/** Exact owned printings, excluding copies currently lent to another player. */
internal object SimpleTradeStock {
    fun available(stock: List<CollectionCard>, reserved: Map<String, Int>): List<TradeSelection> = stock.mapNotNull { row ->
        val finish = when (row.variant.lowercase()) {
            "normale", "classique" -> "Classique"
            "foil" -> "Foil"
            "hyperspace" -> "Hyperspace"
            "hyperspace foil" -> "Hyperspace foil"
            else -> return@mapNotNull null
        }
        if (row.language !in listOf("FR", "EN", "DE", "ES", "IT")) return@mapNotNull null
        val quantity = (row.quantity - (reserved[loanKey(row)] ?: 0)).coerceAtLeast(0)
        if (quantity == 0) null else TradeSelection(DeckConstruction.stockReference(row.setCode, row.number) ?: return@mapNotNull null, quantity, row.language, finish)
    }.groupBy { Triple(it.cardId, it.language, it.finish) }.values.map { rows ->
        rows.first().copy(quantity = rows.sumOf { it.quantity }.coerceAtMost(9))
    }
    fun limit(item: TradeSelection, available: List<TradeSelection>): Int = available.firstOrNull {
        it.cardId == item.cardId && it.language == item.language && it.finish == item.finish
    }?.quantity ?: 0
    fun valid(items: List<TradeSelection>, available: List<TradeSelection>): Boolean = items.isNotEmpty() &&
        items.groupBy { Triple(it.cardId, it.language, it.finish) }.values.all { rows ->
            rows.all { it.quantity > 0 } && rows.sumOf { it.quantity } <= limit(rows.first(), available)
        }
}
