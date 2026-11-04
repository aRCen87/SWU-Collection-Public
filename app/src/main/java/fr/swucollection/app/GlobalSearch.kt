package fr.swucollection.app

/** Build once per data change, not once per visible row or search keystroke. */
internal class GlobalSearchIndex(catalog: List<CatalogCard>, owned: List<CollectionCard>, decks: List<DeckStore.SavedDeck>, private val details: CardDetails) {
    private val byId = catalog.associateBy(DeckConstruction::reference)
    private val cardKeys = catalog.associate { DeckConstruction.reference(it) to DeckBrowser.searchKey("${it.name} ${it.setCode}-${it.number} ${details.get(it)?.englishName.orEmpty()}") }
    private val deckKeys = decks.associate { saved -> saved.id to DeckBrowser.searchKey(saved.name + " " + DeckCompanion.rows(saved.deck).joinToString(" ") { cardKeys[it.cardId] ?: it.cardId }) }
    private val stock = owned.mapNotNull { row -> DeckConstruction.stockReference(row.setCode, row.number)?.let { byId[it] }?.let { details.family(it) to row.quantity } }
        .groupBy { it.first }.mapValues { (_, rows) -> rows.sumOf { it.second } }
    private val usage = decks.flatMap { saved -> DeckCompanion.rows(saved.deck).mapNotNull { byId[it.cardId]?.let(details::family) }.distinct() }
        .groupingBy { it }.eachCount()
    fun terms(query: String) = DeckBrowser.searchKey(query).trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    private fun matches(text: String, terms: List<String>) = terms.all { it in text }
    fun cardMatches(card: CatalogCard, query: String) = cardMatches(card, terms(query))
    fun cardMatches(card: CatalogCard, terms: List<String>) = matches(cardKeys[DeckConstruction.reference(card)].orEmpty(), terms)
    fun deckMatches(deck: DeckStore.SavedDeck, query: String) = deckMatches(deck, terms(query))
    fun deckMatches(deck: DeckStore.SavedDeck, terms: List<String>) = matches(deckKeys[deck.id].orEmpty(), terms)
    fun setMatches(set: CatalogSet, query: String) = matches(DeckBrowser.searchKey("${set.code} ${set.name}"), terms(query))
    fun stock(card: CatalogCard) = stock[details.family(card)] ?: 0
    fun deckCount(card: CatalogCard) = usage[details.family(card)] ?: 0
}
