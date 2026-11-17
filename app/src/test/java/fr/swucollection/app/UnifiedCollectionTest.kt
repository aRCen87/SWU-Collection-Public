package fr.swucollection.app

import org.junit.Assert.*
import org.junit.Test

class UnifiedCollectionTest {
    private val first = CatalogCard("SOR", "095", "Carte A", "Unité", "Rare")
    private val second = CatalogCard("HMW", "100", "Carte B", "Unité", "Légendaire")
    private val catalog = listOf(first, second)
    private val owned = listOf(CollectionCard(1, "Carte A", "SOR", "095", "Foil", 2, "EN"))

    @Test fun catalogueAndMissingViewsShareTheExtensionAndLanguageFilters() {
        fun select(scope: String, set: String = "Toutes", language: String = "Toutes") =
            UnifiedCollection.catalogCards(catalog, owned, scope, set, language, "", CardDetails.empty())
        assertEquals(catalog, select("Toutes les cartes"))
        assertEquals(listOf(second), select("Manquantes"))
        assertEquals(listOf(first), select("Toutes les cartes", "SOR"))
        assertTrue(select("Manquantes", "SOR").isEmpty())
        assertEquals(listOf(first), select("Manquantes", "SOR", "FR"))
        assertTrue(select("Manquantes", "SOR", "EN").isEmpty())
        assertEquals(2, owned.single().quantity)
    }
    @Test fun legacyExtensionLinksAndHomePreferencesRemainCompatible() {
        assertEquals("Collection", UnifiedCollection.section("Extensions"))
        assertEquals("Mes listes", UnifiedCollection.section("Mes listes"))
        val order = listOf("SCANNER", "EXTENSIONS", "MA COLLECTION", "MES DECKS")
        assertEquals(listOf("SCANNER", "MA COLLECTION", "MES DECKS"), UnifiedCollection.visibleHome(order))
        assertEquals(4, order.size)
    }
}
