package fr.swucollection.app

import java.text.Normalizer
import java.util.Locale

/** Filtering is read-only and precedes family grouping, so edition filters remain reliable. */
internal data class DeckBrowserFilters(
    val aspect: String = "", val type: String = "", val set: String = "",
    val rarity: String = "", val cost: String = "", val arena: String = "",
    val trait: String = "", val stock: String = ""
) {
    val activeCount get() = listOf(aspect, type, set, rarity, cost, arena, trait, stock).count { it.isNotEmpty() }
    fun forIdentity() = copy(type = "", cost = "", arena = "")
    fun matches(card: CatalogCard, detail: CardDetail?, owned: Int, identity: Boolean): Boolean =
        (aspect.isEmpty() || detail?.aspects?.contains(aspect) == true) &&
        (type.isEmpty() || identity || card.type == type) && (set.isEmpty() || card.setCode == set) &&
        (rarity.isEmpty() || card.rarity == rarity) &&
        (identity || cost.isEmpty() || detail?.cost?.let { if (cost == "8+") it >= 8 else it == cost.toIntOrNull() } == true) &&
        (identity || arena.isEmpty() || detail?.arena == arena) && (trait.isEmpty() || detail?.traits?.contains(trait) == true) &&
        (stock.isEmpty() || if (stock == "owned") owned > 0 else owned == 0)
}
internal object DeckBrowser {
    const val DEFAULT_SORT = "Extensions récentes"
    // MainActivity flattens allCatalogSets(), already ordered by CatalogChronology.
    fun extensionOrder(catalog: List<CatalogCard>): List<String> = catalog.map { it.setCode }.distinct()
    fun searchKey(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), " ").trim()
    fun search(card: CatalogCard, query: String): Boolean = query.isBlank() ||
        searchKey(card.name + " " + card.setCode + "-" + card.number).contains(searchKey(query.trim()))
    fun comparator(sort: String, details: CardDetails, setOrder: List<String> = emptyList()): Comparator<CatalogCard> {
        val ranks = setOrder.withIndex().associate { it.value to it.index }
        val chronology = compareBy<CatalogCard> { ranks[it.setCode] ?: Int.MAX_VALUE }
            .thenBy { it.setCode }.thenBy { it.number.toIntOrNull() ?: Int.MAX_VALUE }.thenBy { it.number }
        return when (sort) {
            "Coût croissant" -> compareBy<CatalogCard> { details.get(it)?.cost ?: Int.MAX_VALUE }.then(chronology)
            "Coût décroissant" -> compareByDescending<CatalogCard> { details.get(it)?.cost ?: -1 }.then(chronology)
            else -> chronology
        }
    }
    val sorts = listOf(DEFAULT_SORT, "Coût croissant", "Coût décroissant")
}
