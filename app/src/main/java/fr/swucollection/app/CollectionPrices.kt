package fr.swucollection.app

internal enum class CollectionPriceSort(val label: String) {
    CATALOG("Catalogue"), ASCENDING("Prix croissant"), DESCENDING("Prix décroissant")
}

internal fun collectionPriceKey(set: String, number: String, variant: String): String =
    MarketPricing.key(set, number) + ":" + variant

internal fun catalogueQuote(quotes: Map<String, MarketQuote>, set: String, number: String): MarketQuote? =
    quotes[collectionPriceKey(set, number, MarketPricing.CATALOGUE)]
        ?: quotes[collectionPriceKey(set, number, "Normale")]

/** Stable ties preserve catalogue order. Missing, zero and invalid prices always come last. */
internal fun <T> sortCollectionByPrice(items: List<T>, order: CollectionPriceSort,
                                     amount: (T) -> Double?): List<T> {
    if (order == CollectionPriceSort.CATALOG) return items
    fun valid(item: T) = amount(item)?.takeIf { it.isFinite() && it > 0.0 }
    return items.sortedWith(Comparator { a, b ->
        val left = valid(a)
        val right = valid(b)
        when {
            left == null && right == null -> 0
            left == null -> 1
            right == null -> -1
            order == CollectionPriceSort.ASCENDING -> left.compareTo(right)
            else -> right.compareTo(left)
        }
    })
}

/** Include the requested finish even when no copy of that finish is owned yet. */
internal fun collectionPriceRequests(catalog: List<CatalogCard>, owned: List<CollectionCard>, goals: Map<String, PrintGoal>): List<Pair<CatalogCard,String>> {
    val byRef = catalog.associateBy { MarketPricing.key(it.setCode,it.number) }
    return (catalog.map { it to MarketPricing.CATALOGUE } + owned.mapNotNull { row -> byRef[MarketPricing.key(row.setCode,row.number)]?.let { it to row.variant } } +
        catalog.mapNotNull { card -> goals[card.setCode]?.finish?.takeIf { it in ScanVariants.detect(card).choices }?.let { card to it } })
        .distinctBy { (card,finish) -> collectionPriceKey(card.setCode,card.number,finish) }
}
