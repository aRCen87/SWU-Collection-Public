package fr.swucollection.app

/** Construction checks and stock calculation share physical print families. No inventory writes. */
internal object DeckConstruction {
    data class Need(val id: String, val name: String, val required: Int, val owned: Int) {
        val missing get() = (required - owned).coerceAtLeast(0)
    }
    data class Report(val issues: List<String>, val needs: List<Need>, val unknown: Int)
    fun type(card: CatalogCard) = card.type.lowercase().replace('é', 'e').replace('è', 'e')
    fun isDraw(card: CatalogCard) = type(card) in setOf("unite", "unit", "evenement", "event", "amelioration", "upgrade")
    fun stockReference(set: String, number: String): String? =
        runCatching { DeckInterchange.normalizeId("${set.trim()}-${number.substringBefore('/').trim()}") }.getOrNull()
    fun reference(card: CatalogCard) = DeckInterchange.normalizeId("${card.setCode}-${card.number}")

    fun report(deck: PortableDeck, owned: List<CollectionCard>, catalog: List<CatalogCard>,
               details: CardDetails, rules: LegalityRules): Report {
        val byId = catalog.associateBy(::reference)
        fun identity(id: String) = byId[id]?.let(details::family) ?: "unknown:$id"
        val rows = deck.deck + deck.sideboard + listOf(DeckEntry(deck.leader, 1), DeckEntry(deck.base, 1))
        val groups = rows.groupBy { identity(it.cardId) }
        val stock = owned.mapNotNull { row -> stockReference(row.setCode, row.number)?.let { identity(it) to row.quantity } }
            .groupBy { it.first }.mapValues { (_, cards) -> cards.sumOf { it.second } }
        val needs = groups.map { (family, entries) ->
            val id = entries.first().cardId
            Need(id, byId[id]?.name ?: id, entries.sumOf { it.count }, stock[family] ?: 0)
        }.sortedWith(compareByDescending<Need> { it.missing }.thenBy { it.name })
        val issues = mutableListOf<String>()
        val leader = byId[deck.leader]
        val base = byId[deck.base]
        if (leader == null || type(leader) !in setOf("leader", "chef")) issues += "Choisis un leader reconnu."
        if (base == null || type(base) != "base") issues += "Choisis une base reconnue."
        val count = deck.deck.sumOf { it.count }
        if (count < 50) issues += "Deck principal : $count / 50 cartes minimum."
        val side = deck.sideboard.sumOf { it.count }
        if (side > 10) issues += "Réserve : $side cartes, maximum 10."
        (deck.deck + deck.sideboard).groupBy { identity(it.cardId) }.values.forEach { entries ->
            val total = entries.sumOf { it.count }
            if (total > 3) issues += "${byId[entries.first().cardId]?.name ?: entries.first().cardId} : $total exemplaires, maximum 3 deck et réserve réunis."
        }
        (deck.deck + deck.sideboard).forEach { entry ->
            byId[entry.cardId]?.let { card ->
                if (!isDraw(card)) issues += "${card.name} : ce type de carte ne va ni dans le deck principal ni dans la réserve."
            }
        }
        // Reuse rotation, suspension and reprint checks already used by personal lists.
        val auditList = CardList("audit", "audit", rows.mapNotNull { entry ->
            byId[entry.cardId]?.let { CardListEntry(it, entry.count) }
        }, "Premier")
        val audit = DeckAudit.report(auditList, owned, catalog, details, rules)
        issues += audit.issues.filter { it.contains("Premier") || it.contains("contrôle partiel") }
        val unknown = rows.count { it.cardId !in byId }
        if (unknown > 0) issues += "$unknown référence(s) inconnue(s) : contrôle incomplet."
        return Report(issues.distinct(), needs, unknown)
    }
}
