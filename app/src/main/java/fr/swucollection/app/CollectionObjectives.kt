package fr.swucollection.app

/** One target shared by completion, dashboard and exchanges. Families stay within an extension. */
internal object CollectionObjectives {
    fun family(card: CatalogCard, details: CardDetails): String = card.setCode + ":" + details.family(card)

    fun rows(catalog: List<CatalogCard>, owned: List<CollectionCard>, playset: Boolean,
        acceptVariants: Boolean, details: CardDetails, printGoal: PrintGoal? = null): List<PlanRow> {
        if (printGoal != null) return printGoalRows(catalog, owned, playset, printGoal)
        if (!acceptVariants) return CollectionPlan.rows(catalog, owned, playset)
        val byKey = catalog.associateBy { MarketPricing.key(it.setCode, it.number) }
        val quantities = owned.groupBy { row ->
            byKey[MarketPricing.key(row.setCode, row.number)]?.let { family(it, details) }
                ?: "unmatched:${row.setCode}:${row.number}"
        }.mapValues { (_, copies) -> copies.sumOf { it.quantity } }
        return catalog.filter(CollectionPlan::isClassic).distinctBy { family(it, details) }.map { card ->
            PlanRow(card, quantities[family(card, details)] ?: 0, CollectionPlan.target(card, playset))
        }
    }

    fun extras(owned: List<CollectionCard>, catalog: List<CatalogCard>, playset: Boolean,
        acceptVariants: Boolean, details: CardDetails): List<CollectionCard> {
        if (!acceptVariants) return CollectionPlan.exchange(owned, catalog, playset)
        val byKey = catalog.associateBy { MarketPricing.key(it.setCode, it.number) }
        val result = mutableListOf<CollectionCard>()
        val normalized = owned.groupBy { "${MarketPricing.key(it.setCode,it.number)}:${it.variant}:${it.language}" }.values.map { copies ->
            val first = copies.first()
            first.copy(number = byKey[MarketPricing.key(first.setCode,first.number)]?.number ?: first.number,
                quantity = copies.sumOf { it.quantity })
        }
        normalized.groupBy { row ->
            byKey[MarketPricing.key(row.setCode, row.number)]?.let { family(it, details) }
                ?: "unmatched:${row.setCode}:${row.number}:${row.variant}"
        }.values.forEach { group ->
            // Unknown references are kept: never offer unidentifiable cards automatically.
            val reference = byKey[MarketPricing.key(group.first().setCode, group.first().number)] ?: return@forEach
            var keep = CollectionPlan.target(reference, playset)
            group.sortedWith(compareBy<CollectionCard> {
                val c = byKey[MarketPricing.key(it.setCode, it.number)]
                if (it.variant == "Normale" && c != null && CollectionPlan.isClassic(c)) 0 else 1
            }.thenBy { it.id }).forEach { row ->
                val retained = minOf(keep, row.quantity)
                keep -= retained
                if (row.quantity > retained) result += row.copy(quantity = row.quantity - retained)
            }
        }
        return result
    }
}
