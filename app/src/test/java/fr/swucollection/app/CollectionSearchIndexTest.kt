package fr.swucollection.app

import org.junit.Assert.*
import org.junit.Test

class CollectionSearchIndexTest {
    private val catalog = listOf(
        CatalogCard("SOR", "030", "Équipe rebelle", "Unité", "Rare", ""),
        CatalogCard("JTL", "054", "Vaisseau", "Unité", "Commune", "")
    )
    private val details = CardDetails(mapOf(
        MarketPricing.key("SOR", "030") to CardDetail("rebels", "Rebel Équipe", 3, emptyList(), emptyList(), "Ground")
    ))
    private val byKey = catalog.associateBy { MarketPricing.key(it.setCode, it.number) }
    private val owned = listOf(
        CollectionCard(1, "Équipe rebelle", "SOR", "030", "Normale", 1, "FR"),
        CollectionCard(2, "Équipe rebelle", "SOR", "030", "Foil", 4, "EN"),
        CollectionCard(3, "Vaisseau", "JTL", "054", "Normale", 2, "FR"),
        CollectionCard(4, "Carte personnelle", "XYZ", "999", "Normale", 1, "FR")
    )

    private fun previousSearch(rows: List<CollectionCard>, query: String) = rows.filter { row ->
        val english = byKey[MarketPricing.key(row.setCode, row.number)]?.let { details.get(it)?.englishName }.orEmpty()
        DeckBrowser.searchKey(english).contains(DeckBrowser.searchKey(query)) ||
            row.name.contains(query, true) || row.setCode.contains(query, true) || row.number.contains(query, true)
    }

    @Test fun indexedSearchPreservesNamesAliasesReferencesLanguagesAndOrdering() {
        val index = CollectionSearchIndex(owned, byKey, details)
        for (query in listOf("", "rebelle", "REBEL", "equipe", "Équipe", "SOR", "03", "Vaisseau", "personnelle", "introuvable", "  ")) {
            assertEquals(query, previousSearch(owned, query), index.search(query))
        }
        assertEquals(listOf(1L, 2L), index.search("rebel").map { it.id })
    }

    @Test fun rebuildingAfterQuantityAndCatalogueChangesNeverReturnsStaleRows() {
        val changed = owned.drop(1).map { it.copy(quantity = it.quantity + 1) }
        val changedDetails = CardDetails(mapOf(MarketPricing.key("JTL", "054") to
            CardDetail("ship", "New English Alias", null, emptyList(), emptyList(), "Space")))
        val index = CollectionSearchIndex(changed, byKey, changedDetails)
        assertEquals(changed, index.search(""))
        assertEquals(listOf(changed[1]), index.search("new english"))
        assertTrue(index.search("Rebel Équipe").isEmpty())
    }

    @Test fun largeInventoryKeepsExactResults() {
        val rows = (0 until 10_000).map { owned[it % owned.size].copy(id = it.toLong()) }
        val index = CollectionSearchIndex(rows, byKey, details)
        for (query in listOf("rebel", "054", "XYZ", "introuvable")) {
            assertEquals(previousSearch(rows, query), index.search(query))
        }
    }
}
