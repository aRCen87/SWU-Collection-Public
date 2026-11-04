package fr.swucollection.app

/** A readable interchange fallback. Unknown references remain explicit instead of guessing names. */
internal object DeckTextExport {
    fun render(deck: PortableDeck, catalog: List<CatalogCard>, details: CardDetails): String {
        val byId = catalog.associateBy(DeckConstruction::reference)
        fun line(id: String, count: Int): String {
            val card = byId[id]
            val label = card?.let { details.get(it)?.englishName?.takeIf(String::isNotBlank) ?: it.name } ?: id
            val clean = label.replace('\n', ' ').replace('\r', ' ')
            val external = if (clean.contains(" — ")) clean.replace(" — ", " | ") else clean.replace(", ", " | ")
            return "$count | $external"
        }
        return buildString {
            appendLine("Leaders"); appendLine(line(deck.leader, 1))
            appendLine(); appendLine("Base"); appendLine(line(deck.base, 1))
            appendLine(); appendLine("Deck")
            deck.deck.forEach { appendLine(line(it.cardId, it.count)) }
            appendLine(); appendLine("Sideboard")
            deck.sideboard.forEach { appendLine(line(it.cardId, it.count)) }
        }
    }
}
