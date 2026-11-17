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
        (aspect.isEmpty() || detail?.aspects?.any { it in aspect.split("|") } == true) &&
        (type.isEmpty() || identity || card.type == type) && (set.isEmpty() || card.setCode in set.split("|")) &&
        (rarity.isEmpty() || card.rarity in rarity.split("|")) &&
        (identity || cost.isEmpty() || detail?.cost?.let { if (cost == "8+") it >= 8 else it == cost.toIntOrNull() } == true) &&
        (identity || arena.isEmpty() || detail?.arena?.let { current -> arena.split("|").any { DeckBrowser.arenaKey(it) == DeckBrowser.arenaKey(current) } } == true) && (trait.isEmpty() || detail?.traits?.any { it in trait.split("|") } == true) &&
        (stock.isEmpty() || if (stock == "owned") owned > 0 else owned == 0)
}
internal object DeckBrowser {
    fun arenaKey(value: String): String = when (searchKey(value)) {
        "ground", "ground arena", "terrestre" -> "ground"
        "space", "space arena", "spatiale", "spatial" -> "space"
        else -> searchKey(value)
    }
    const val DEFAULT_SORT = "Extensions récentes"
    private val combiningMarks = Regex("\\p{M}+")
    private val separators = Regex("[^a-z0-9]+")
    // MainActivity flattens allCatalogSets(), already ordered by CatalogChronology.
    fun extensionOrder(catalog: List<CatalogCard>): List<String> = catalog.map { it.setCode }.distinct()
    fun searchKey(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(combiningMarks, "").lowercase(Locale.ROOT).replace(separators, " ").trim()
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
