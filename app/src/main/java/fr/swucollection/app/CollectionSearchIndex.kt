package fr.swucollection.app

/** English aliases are normalized once per inventory/catalogue change. No persistent cache. */
internal class CollectionSearchIndex(
    private val cards: List<CollectionCard>,
    catalogByKey: Map<String, CatalogCard>,
    details: CardDetails
) {
    private val englishNames = cards.associate { row ->
        row.id to DeckBrowser.searchKey(catalogByKey[MarketPricing.key(row.setCode, row.number)]
            ?.let { details.get(it)?.englishName }.orEmpty())
    }

    fun search(query: String): List<CollectionCard> {
        if (query.isEmpty()) return cards
        val englishQuery = DeckBrowser.searchKey(query)
        return cards.filter { row ->
            englishNames.getValue(row.id).contains(englishQuery) ||
                row.name.contains(query, ignoreCase = true) ||
                row.setCode.contains(query, ignoreCase = true) ||
                row.number.contains(query, ignoreCase = true)
        }
    }
}
