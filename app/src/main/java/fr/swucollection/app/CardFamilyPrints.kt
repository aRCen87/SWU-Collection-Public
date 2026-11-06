package fr.swucollection.app

/** Display rows only: collection identities and quantities are never merged. */
internal data class FamilyPrint(val card: CatalogCard, val variant: String, val owned: List<CollectionCard>) {
    val quantity get() = owned.sumOf { it.quantity }
}

internal fun familyPrints(card: CatalogCard, catalog: List<CatalogCard>, owned: List<CollectionCard>,
    details: CardDetails): List<FamilyPrint> {
    val stock = owned.groupBy { MarketPricing.key(it.setCode, it.number) }
    return details.related(card, catalog)
        .distinctBy { MarketPricing.key(it.setCode, it.number) }
        .sortedWith(DeckBrowser.comparator(DeckBrowser.DEFAULT_SORT, details, DeckBrowser.extensionOrder(catalog)))
        .flatMap { print ->
            val copies = stock[MarketPricing.key(print.setCode, print.number)].orEmpty()
            // Keep historical stock visible even when the publisher refines a treatment.
            (ScanVariants.detect(print).choices + copies.map { it.variant }).distinct().map { finish ->
                FamilyPrint(print, finish, copies.filter { it.variant == finish })
            }
        }
}
