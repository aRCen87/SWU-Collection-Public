package fr.swucollection.app

internal enum class DeckDestination(val title: String, val url: String) {
    MELEE("Melee", "https://melee.gg/decklist/form"),
    KARABAST("Karabast", "https://karabast.net/")
}

/** Public import formats; opening a site never submits or publishes a deck. */
internal object DeckExternalExport {
    private val promoCode = Regex("^(?:(?:C|G|J|P|MV)\\d{2}|GG|CST|\\w{2,3}O?P)$")
    private fun isPromo(card: CatalogCard) = promoCode.matches(card.setCode.uppercase())
    private fun gameplayPrint(card: CatalogCard): Boolean {
        val print = ScanVariants.detect(card)
        return !isPromo(card) && (CollectionPlan.isClassic(card) || print.variant == "Normale" ||
            print.choices == listOf("Normale", "Foil"))
    }
    /** Simulators use gameplay print IDs, while the inventory keeps exact physical prints. */
    fun gameplayDeck(deck: PortableDeck, catalog: List<CatalogCard>, details: CardDetails): PortableDeck {
        val byId = catalog.associateBy(DeckConstruction::reference)
        val resolved = mutableMapOf<String, String>()
        fun gameplayId(raw: String): String {
            val id = DeckInterchange.normalizeId(raw)
            return resolved.getOrPut(id) {
                val card = byId[id] ?: throw IllegalArgumentException("Référence inconnue : $id. Actualise le catalogue.")
                if (gameplayPrint(card)) id
                else {
                    val classic = details.related(card, catalog).filter {
                        gameplayPrint(it) && DeckConstruction.type(it) == DeckConstruction.type(card)
                    }.minWithOrNull(compareBy<CatalogCard>({ it.setCode != card.setCode }, { it.setCode }, { it.number.toIntOrNull() ?: Int.MAX_VALUE }))
                    if (classic != null) DeckConstruction.reference(classic)
                    else {
                        val print = ScanVariants.detect(card)
                        require(!isPromo(card) && print.variant == null && print.choices != listOf("Hyperspace", "Hyperspace Foil")) {
                            "Version classique introuvable pour ${card.name}. Actualise le catalogue avant l’export."
                        }
                        id
                    }
                }
            }
        }
        fun entries(rows: List<DeckEntry>): List<DeckEntry> = rows.groupBy { gameplayId(it.cardId) }.map { (id, copies) ->
            val count = copies.sumOf { it.count }
            require(count in 1..99) { "Quantité trop élevée pour $id." }
            DeckEntry(id, count)
        }
        return PortableDeck(gameplayId(deck.leader), gameplayId(deck.base), entries(deck.deck), entries(deck.sideboard))
    }

    fun payload(destination: DeckDestination, name: String, deck: PortableDeck,
        catalog: List<CatalogCard>, details: CardDetails): String {
        val gameplay = gameplayDeck(deck, catalog, details)
        if (destination == DeckDestination.KARABAST) return DeckInterchange.exportJson(gameplay, name)
        val byId = catalog.associateBy(DeckConstruction::reference)
        val ids = listOf(gameplay.leader, gameplay.base) + (gameplay.deck + gameplay.sideboard).map { it.cardId }
        ids.distinct().forEach { id ->
            val card = byId[id]
            require(card != null && !details.get(card)?.englishName.isNullOrBlank()) {
                "Le nom anglais de $id est indisponible. Actualise le catalogue ou utilise l’export JSON."
            }
        }
        return DeckTextExport.render(gameplay, catalog, details)
    }
}

