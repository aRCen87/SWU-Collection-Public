package fr.swucollection.app

internal enum class CollectionPriceSort(val label: String) {
    CATALOG("Catalogue"), ASCENDING("Prix croissant"), DESCENDING("Prix décroissant")
}

internal fun collectionPriceKey(set: String, number: String, variant: String): String =
    MarketPricing.key(set, number) + ":" + variant

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
