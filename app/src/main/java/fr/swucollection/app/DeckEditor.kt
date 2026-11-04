package fr.swucollection.app

/** Pure deck editing operations, independent of inventory and Android UI. */
internal object DeckEditor {
    enum class Zone { MAIN, SIDEBOARD }

    fun quantity(deck: PortableDeck, id: String, zone: Zone): Int {
        val normalized = DeckInterchange.normalizeId(id)
        val entries = if (zone == Zone.MAIN) deck.deck else deck.sideboard
        return entries.filter { DeckInterchange.normalizeId(it.cardId) == normalized }.sumOf { it.count }
    }

    /** A zero quantity removes the entry. The inventory is never changed. */
    fun setQuantity(deck: PortableDeck, id: String, count: Int, zone: Zone): PortableDeck {
        require(count in 0..99) { "Quantity must be between 0 and 99" }
        val normalized = DeckInterchange.normalizeId(id)
        val old = if (zone == Zone.MAIN) deck.deck else deck.sideboard
        val updated = old.filterNot { DeckInterchange.normalizeId(it.cardId) == normalized }.toMutableList()
        if (count > 0) updated.add(DeckEntry(normalized, count))
        return if (zone == Zone.MAIN) deck.copy(deck = updated) else deck.copy(sideboard = updated)
    }

    fun move(deck: PortableDeck, id: String, destination: Zone, count: Int): PortableDeck {
        require(count > 0)
        val source = if (destination == Zone.MAIN) Zone.SIDEBOARD else Zone.MAIN
        require(quantity(deck, id, source) >= count) { "Not enough copies in source" }
        val removed = setQuantity(deck, id, quantity(deck, id, source) - count, source)
        val newCount = quantity(removed, id, destination) + count
        return setQuantity(removed, id, newCount, destination)
    }

    data class MissingCard(val cardId: String, val needed: Int, val owned: Int) {
        val missing: Int get() = (needed - owned).coerceAtLeast(0)
    }

    /** ownedByCardId must be aggregated across physical variants before calling. */
    fun missingCards(deck: PortableDeck, ownedByCardId: Map<String, Int>): List<MissingCard> {
        val owned = ownedByCardId.entries.groupBy { DeckInterchange.normalizeId(it.key) }
            .mapValues { (_, entries) -> entries.sumOf { it.value } }
        val required = (deck.deck + deck.sideboard)
            .groupBy { DeckInterchange.normalizeId(it.cardId) }
            .mapValues { (_, entries) -> entries.sumOf { it.count } }
        return required.map { (id, needed) -> MissingCard(id, needed, owned[id] ?: 0) }
            .filter { it.missing > 0 }.sortedByDescending { it.missing }
    }
}

