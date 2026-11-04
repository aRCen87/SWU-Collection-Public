package fr.swucollection.app

import org.junit.Assert.*
import org.junit.Test

class DeckBrowserTest {
    private val card = CatalogCard("SOR", "092", "Barrage dévastateur", "Évènement", "Peu commune")
    private val detail = CardDetail("barrage", "Overwhelming Barrage", 5, listOf("Command", "Villainy"), listOf("Tactique"), "Ground")
    @Test fun searchAcceptsAccentsCaseAndReferences() {
        assertTrue(DeckBrowser.search(card, "DEVASTATEUR"))
        assertTrue(DeckBrowser.search(card, "sor-092"))
        assertFalse(DeckBrowser.search(card, "Krennic"))
    }
    @Test fun referencesAcceptSpacesUnderscoresAndLongDashes() {
        listOf("SOR 092", "sor_092", "SOR – 092", "  BARRAGE  DEVASTATEUR  ").forEach {
            assertTrue("Search rejected: $it", DeckBrowser.search(card, it))
        }
        assertFalse(DeckBrowser.search(card, "SOR 093"))
        assertEquals(DeckBrowser.searchKey("L’attaque d’Élodie"), DeckBrowser.searchKey("L'attaque d'Elodie"))
    }
    @Test fun changingFromDrawCardsToIdentityRemovesOnlyInapplicableFilters() {
        val filters = DeckBrowserFilters(aspect = "Command", type = "Unité", set = "SOR",
            rarity = "Peu commune", cost = "5", arena = "Ground", trait = "Tactique", stock = "owned")
        val identity = filters.forIdentity()
        assertEquals(filters.copy(type = "", cost = "", arena = ""), identity)
        val leader = card.copy(type = "Leader")
        val leaderDetail = detail.copy(cost = null, arena = "")
        assertTrue(filters.matches(leader, leaderDetail, 2, true))
        assertTrue(identity.matches(leader, leaderDetail, 2, true))
        assertFalse(identity.copy(set = "HMW").matches(leader, leaderDetail, 2, true))
        assertFalse(identity.matches(leader, leaderDetail, 0, true))
        assertFalse(filters.matches(card, leaderDetail, 2, false))
    }

    @Test fun combinedFiltersRespectStockAndUnknownFacets() {
        val filters = DeckBrowserFilters(aspect = "Command", type = "Évènement", set = "SOR", cost = "5", arena = "Ground", trait = "Tactique", stock = "owned")
        assertTrue(filters.matches(card, detail, 2, false))
        assertFalse(filters.matches(card, detail, 0, false))
        assertFalse(filters.matches(card, null, 2, false))
        assertFalse(filters.copy(cost = "8+").matches(card, detail, 2, false))
        assertTrue(filters.copy(cost = "8+").matches(card, detail.copy(cost = 10), 2, false))
    }
    @Test fun typeFilterDoesNotHideLeadersButOtherFiltersRemainActive() {
        val leader = card.copy(type = "Leader")
        assertTrue(DeckBrowserFilters(type = "Unité").matches(leader, detail, 0, true))
        assertFalse(DeckBrowserFilters(type = "Unité", set = "HMW").matches(leader, detail, 0, true))
    }
    @Test fun sortingUnknownCostsLastAndNumbersNumerically() {
        val a = card.copy(number = "2", name = "Deux")
        val b = card.copy(number = "10", name = "Dix")
        assertEquals(listOf(a, b), listOf(b, a).sortedWith(DeckBrowser.comparator("Numéro de carte", CardDetails.empty())))
        assertEquals(8, DeckBrowserFilters("a", "t", "s", "r", "c", "a", "t", "s").activeCount)
    }
    @Test fun defaultOrderUsesExtensionChronologyBeforeNamesAndNumericCardNumbers() {
        val recentTen = card.copy(setCode = "HMW", number = "010", name = "Alpha")
        val recentTwo = card.copy(setCode = "HMW", number = "002", name = "Zulu")
        val middle = card.copy(setCode = "ASH", number = "001", name = "Aardvark")
        val oldest = card.copy(setCode = "SOR", number = "001", name = "Abc")
        val unknown = card.copy(setCode = "NEW", number = "001", name = "A")
        val comparator = DeckBrowser.comparator(DeckBrowser.DEFAULT_SORT, CardDetails.empty(), listOf("HMW", "ASH", "SOR"))
        assertEquals(listOf(recentTwo, recentTen, middle, oldest, unknown),
            listOf(oldest, recentTen, unknown, middle, recentTwo).sortedWith(comparator))
        assertEquals(listOf("HMW", "ASH", "SOR"), DeckBrowser.extensionOrder(listOf(recentTen, recentTwo, middle, oldest)))
        assertEquals(DeckBrowser.DEFAULT_SORT, DeckBrowser.sorts.first())
        assertFalse(DeckBrowser.sorts.contains("Nom A–Z"))
    }
    @Test fun previousAlphabeticalChoiceFallsBackToChronology() {
        val recent = card.copy(setCode = "HMW", name = "Zulu")
        val old = card.copy(setCode = "SOR", name = "Alpha")
        assertEquals(listOf(recent, old), listOf(old, recent).sortedWith(
            DeckBrowser.comparator("Nom A–Z", CardDetails.empty(), listOf("HMW", "SOR"))))
    }

}
