package fr.swucollection.app

/** Public pages, opened in the user's browser. No private API or inferred tournament data. */
internal object CompetitiveDecks {
    const val TOURNAMENTS = "https://swubase.com/meta?formatId=1&minTournamentType=pq&page=tournaments"
    const val META = "https://swubase.com/meta?formatId=1&minTournamentType=pq&page=meta&maMetaInfo=leadersAndBase"
    const val CHAMPIONS = "$META&maMetaPart=champions"
    const val DECKS = META
    const val CONVERTER = "https://swubase.com/tools/deck-format-converter"

    data class Readiness(val required: Int, val missing: Int, val unknown: Int) {
        val ready get() = missing == 0 && unknown == 0
    }

    fun readiness(deck: PortableDeck, owned: List<CollectionCard>, catalog: List<CatalogCard>,
                  details: CardDetails): Readiness {
        val report = DeckConstruction.report(deck, owned, catalog, details, LegalityRules.DEFAULT)
        return Readiness(report.needs.sumOf { it.required }, report.needs.sumOf { it.missing }, report.unknown)
    }

    fun saveCopy(store: DeckStore, name: String, deck: PortableDeck, catalog: List<CatalogCard>): String {
        val byId = catalog.associateBy(DeckConstruction::reference)
        val references = deck.deck + deck.sideboard + listOf(DeckEntry(deck.leader, 1), DeckEntry(deck.base, 1))
        require(references.all { it.cardId in byId }) { "Certaines cartes sont inconnues : mets le catalogue à jour avant l’enregistrement." }
        require(byId[deck.leader]?.let(DeckConstruction::type) in setOf("leader", "chef")) { "Leader invalide." }
        require(byId[deck.base]?.let(DeckConstruction::type) == "base") { "Base invalide." }
        // Generate a fresh ID: importing or copying cannot replace a personal deck.
        return store.save(name.trim(), "Premier", deck)
    }
}
