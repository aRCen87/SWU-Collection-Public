package fr.swucollection.app

import java.math.BigDecimal
import java.text.Normalizer
import java.util.Locale

internal data class PlanRow(val card: CatalogCard, val owned: Int, val target: Int) {
    val missing get() = (target - owned).coerceAtLeast(0)
}
internal data class PlanBudget(val minimum: BigDecimal, val maximum: BigDecimal, val unpricedCopies: Int)

internal object CollectionPlan {
    // Official image identifiers encode finish 01 for the classic print; old SOR/SHD
    // filenames instead mark alternate prints HYP/SHO. Unknown artwork is not guessed.
    fun isClassic(card: CatalogCard): Boolean {
        val file = card.imageUrl.substringAfterLast('/')
        val finish = Regex("^card_\\d{2}(\\d{2})\\d+_", RegexOption.IGNORE_CASE).find(file)
        if (finish != null) return finish.groupValues[1] == "01"
        return file.startsWith("card_SWH_", true) && !Regex("_(HYP|SHO)_", RegexOption.IGNORE_CASE).containsMatchIn(file)
    }
    fun target(card: CatalogCard, playset: Boolean): Int {
        val type = Normalizer.normalize(card.type, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
        return if (!playset || type.contains("leader") || type == "base") 1 else 3
    }
    fun rows(catalog: List<CatalogCard>, owned: List<CollectionCard>, playset: Boolean): List<PlanRow> {
        val quantities = owned.filter { it.variant == "Normale" }.groupBy { MarketPricing.key(it.setCode, it.number) }
            .mapValues { (_, cards) -> cards.sumOf { it.quantity } }
        return catalog.filter(::isClassic).distinctBy { MarketPricing.key(it.setCode, it.number) }.map {
            PlanRow(it, quantities[MarketPricing.key(it.setCode, it.number)] ?: 0, target(it, playset))
        }
    }
    fun budget(rows: List<PlanRow>, quotes: Map<String, MarketQuote>): PlanBudget {
        var low = BigDecimal.ZERO; var high = BigDecimal.ZERO; var unknown = 0
        rows.filter { it.missing > 0 }.forEach { row ->
            val quote = catalogueQuote(quotes, row.card.setCode, row.card.number)
            val price = quote?.amount?.takeIf { it.isFinite() && it > 0 }
            if (price == null) unknown += row.missing else {
                low += BigDecimal.valueOf(price) * row.missing.toBigDecimal()
                val upper = quote?.maxAmount?.takeIf { it.isFinite() && it >= price } ?: price
                high += BigDecimal.valueOf(upper) * row.missing.toBigDecimal()
            }
        }
        return PlanBudget(low, high, unknown)
    }
    fun exchange(owned: List<CollectionCard>, catalog: List<CatalogCard>, playset: Boolean): List<CollectionCard> {
        val byKey = catalog.associateBy { MarketPricing.key(it.setCode, it.number) }
        return owned.groupBy { "${MarketPricing.key(it.setCode,it.number)}:${it.variant}" }.values.flatMap { rows ->
            val reference = byKey[MarketPricing.key(rows.first().setCode, rows.first().number)]
            var keep = reference?.let { target(it, playset) } ?: if (playset) 3 else 1
            rows.sortedBy { it.id }.mapNotNull { row ->
                val retained = minOf(keep, row.quantity)
                keep -= retained
                (row.quantity - retained).takeIf { it > 0 }?.let {
                    row.copy(number = reference?.number ?: row.number, quantity = it)
                }
            }
        }
    }

    fun shareAvailable(extras: List<CollectionCard>): String = buildString {
        appendLine("SWU Collection — Cartes disponibles à l’échange")
        extras.forEach { appendLine("${it.quantity} × ${it.name} · ${it.setCode} ${it.number} · ${it.variant} · ${CardLanguages.label(it.language)}") }
        if (extras.isEmpty()) appendLine("Aucun surplus pour l’objectif choisi")
    }
    fun shareText(rows: List<PlanRow>, extras: List<CollectionCard>): String = buildString {
        appendLine("SWU Collection — Échanges")
        appendLine("DISPONIBLES (exemplaires au-delà de l’objectif, par variante)")
        extras.forEach { appendLine("${it.quantity} × ${it.name} · ${it.setCode} ${it.number} · ${it.variant} · ${CardLanguages.label(it.language)}") }
        if (extras.isEmpty()) appendLine("Aucun surplus")
        appendLine(); appendLine("RECHERCHÉES — classiques, non foil")
        rows.filter { it.missing > 0 }.forEach { appendLine("${it.missing} × ${it.card.name} · ${it.card.setCode} ${it.card.number}") }
    }
    fun csv(rows: List<PlanRow>, extras: List<CollectionCard>): String {
        fun line(values: List<String>) = values.joinToString(";") { "\"${it.replace("\"", "\"\"")}\"" }
        return (listOf("Statut;Nom;Extension;Numéro;Variante;Quantité;Langue") +
            extras.map { line(listOf("Disponible", it.name, it.setCode, it.number, it.variant, it.quantity.toString(), it.language)) } +
            rows.filter { it.missing > 0 }.map { line(listOf("Recherchée", it.card.name, it.card.setCode, it.card.number, "Normale", it.missing.toString(), "Toutes")) })
            .joinToString("\n")
    }
}


