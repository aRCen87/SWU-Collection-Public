package fr.swucollection.app

import org.json.JSONArray

/** Deck-only scan operations. The caller confirms identity replacement before applying. */
internal object DeckScan {
    fun addCollectionCopy(entries: List<ScanBatchEntry>, card: CatalogCard, variant: String): List<ScanBatchEntry> {
        require(variant.isEmpty() || variant in ScanVariants.options) { "Finition de carte invalide." }
        val index = entries.indexOfFirst { DeckConstruction.reference(it.card) == DeckConstruction.reference(card) && it.variant == variant && it.card.language == card.language }
        return entries.toMutableList().apply {
            if (index < 0) add(ScanBatchEntry(card, variant, 1))
            else this[index] = this[index].copy(quantity = this[index].quantity + 1)
        }
    }

    fun collectionJson(entries: List<ScanBatchEntry>): JSONArray = JSONArray().apply {
        entries.forEach { put(cardToJson(it.card).put("variant", it.variant).put("quantity", it.quantity)) }
    }

    fun collectionFromJson(array: JSONArray?): List<ScanBatchEntry> {
        if (array == null) return emptyList()
        require(array.length() <= 1000)
        return (0 until array.length()).map { index ->
            val row = array.getJSONObject(index)
            val variant = row.getString("variant"); require(variant.isEmpty() || variant in ScanVariants.options)
            val count = row.get("quantity")
            require(count is Int && count in 1..9999)
            ScanBatchEntry(cardFromJson(row), variant, count)
        }
    }

    fun find(catalog: List<CatalogCard>, set: String, number: String): CatalogCard? {
        val id = DeckConstruction.stockReference(set, number) ?: return null
        return catalog.firstOrNull { DeckConstruction.reference(it) == id }
    }

    fun add(deck: PortableDeck, card: CatalogCard, zone: DeckEditor.Zone,
        catalog: List<CatalogCard>, details: CardDetails): PortableDeck {
        val id = DeckConstruction.reference(card)
        return when (DeckConstruction.type(card)) {
            "leader", "chef" -> deck.copy(leader = id)
            "base" -> deck.copy(base = id)
            else -> {
                require(DeckConstruction.isDraw(card)) { "Ce type de carte ne peut pas être ajouté au deck." }
                val byId = catalog.associateBy(DeckConstruction::reference)
                val family = details.family(card)
                val copies = (deck.deck + deck.sideboard).filter { entry ->
                    byId[DeckInterchange.normalizeId(entry.cardId)]?.let { details.family(it) == family } == true
                }.sumOf { it.count }
                require(copies < 3) { "Maximum 3 exemplaires, variantes et réserve comprises." }
                require(zone != DeckEditor.Zone.SIDEBOARD || deck.sideboard.sumOf { it.count } < 10) {
                    "La réserve contient déjà 10 cartes."
                }
                DeckEditor.setQuantity(deck, id, DeckEditor.quantity(deck, id, zone) + 1, zone)
            }
        }
    }
}
