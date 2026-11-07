package fr.swucollection.app

import kotlin.math.abs

internal data class DeckAlternative(val card: CatalogCard, val available: Int, val reason: String, val rank: Int)
internal object DeckAlternatives {
    fun suggest(target: CatalogCard, deck: PortableDeck, owned: List<CollectionCard>, catalog: List<CatalogCard>,
        details: CardDetails, rules: LegalityRules): List<DeckAlternative> {
        val original = details.get(target) ?: return emptyList()
        if (!DeckConstruction.isDraw(target)) return emptyList()
        val byId = catalog.associateBy(DeckConstruction::reference)
        val used = (deck.deck + deck.sideboard).groupBy { byId[it.cardId]?.let(details::family) ?: it.cardId }
            .mapValues { (_,rows) -> rows.sumOf { it.count } }
        val pool = DeckInsights.affinityPool(deck,catalog,details) ?: return emptyList()
        val legality = PremierLegality(catalog,rules)
        val activeFamilies=catalog.filter {it.setCode in rules.activeSets}.map(details::family).toSet()
        return catalog.asSequence().filter { DeckConstruction.type(it) == DeckConstruction.type(target) }
            .filter { details.family(it) != details.family(target) && details.family(it) in activeFamilies && legality.status(it) == null }
            .mapNotNull { card ->
                val d = details.get(card) ?: return@mapNotNull null
                if (d.cost == null || original.cost == null || abs(d.cost-original.cost)>1 ||
                    DeckInsights.affinity(card,pool,details) != DeckInsights.Affinity.MATCH || d.arena != original.arena) return@mapNotNull null
                val count = used[details.family(card)] ?: 0
                val free = minOf(DeckCompanion.stock(card,owned,catalog,details)-count,3-count)
                if (free <= 0) return@mapNotNull null
                val shared = d.traits.intersect(original.traits.toSet()).size
                val equalCost = d.cost == original.cost
                DeckAlternative(card,free,listOf(if(equalCost) "même coût" else "coût proche", "même type et arène", "dans les aspects", if(shared>0) "$shared trait(s) commun(s)" else "traits différents").joinToString(" · "),(if(equalCost) 10 else 0)+shared)
            }.sortedWith(compareByDescending<DeckAlternative> { it.rank }.thenBy { it.card.name })
            .distinctBy { details.family(it.card) }.take(5).toList()
    }
    fun replaceOne(deck: PortableDeck, target: CatalogCard, alternative: CatalogCard, zone: DeckEditor.Zone): PortableDeck {
        val from=DeckConstruction.reference(target); val to=DeckConstruction.reference(alternative)
        require(from!=to && DeckConstruction.isDraw(alternative) && DeckEditor.quantity(deck,from,zone)>0)
        val removed=DeckEditor.setQuantity(deck,from,DeckEditor.quantity(deck,from,zone)-1,zone)
        return DeckEditor.setQuantity(removed,to,DeckEditor.quantity(removed,to,zone)+1,zone)
    }
}
