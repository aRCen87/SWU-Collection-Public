package fr.swucollection.app

import org.junit.Assert.*
import org.junit.Test

class DeckMultiFiltersTest {
    private val card = CatalogCard("HMW", "100", "Carte test", "Unité", "Rare")
    private val detail = CardDetail("family", "Test", 3, listOf("Command", "Villainy"), listOf("Underworld", "Creature"), "ground")

    @Test fun alternativesWithinEachFilterCombineAcrossCategories() {
        val filters = DeckBrowserFilters(aspect = "Command|Cunning", set = "LAW|HMW", trait = "Underworld|Force")
        assertTrue(filters.matches(card, detail, 0, false))
        assertFalse(filters.copy(set = "LAW|SOR").matches(card, detail, 0, false))
        assertFalse(filters.copy(trait = "Force|Jedi").matches(card, detail, 0, false))
        assertFalse(filters.copy(aspect = "Cunning|Heroism").matches(card, detail, 0, false))
    }

    @Test fun oldSingleSelectionsAndUnknownMetadataRemainSafe() {
        assertTrue(DeckBrowserFilters(aspect = "Command", trait = "Creature", set = "HMW").matches(card, detail, 1, false))
        assertFalse(DeckBrowserFilters(aspect = "Command|Cunning").matches(card, null, 1, false))
        assertTrue(DeckBrowserFilters(set = "HMW|LAW").matches(card, null, 1, false))
        assertTrue(DeckBrowserFilters().matches(card, null, 0, false))
    }
}
