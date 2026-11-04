package fr.swucollection.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DeckCompanionTest {
    private val leader = CatalogCard("SOR", "001", "Chef", "Leader", "Commune")
    private val base = CatalogCard("SOR", "024", "Base", "Base", "Commune")
    private val unit = CatalogCard("SOR", "095", "Éclaireur impérial", "Unité", "Commune")
    private val reprint = unit.copy(setCode = "HMW", number = "100")
    private val catalog = listOf(reprint, leader, base, unit)
    private val details = CardDetails.parse(JSONObject().put("format", "SWU_CARD_DETAILS").put("version", 1).put("cards", JSONObject().apply {
        catalog.forEach { card -> put(MarketPricing.key(card.setCode, card.number), JSONObject()
            .put("family", if (card == unit || card == reprint) "unit" else card.type)
            .put("en", if (card == unit || card == reprint) "Imperial Scout" else card.name).put("cost", 2)
            .put("aspects", JSONArray()).put("traits", JSONArray()).put("arena", "Ground")) }
    }).toString())
    private val deck = PortableDeck("SOR-001", "SOR-024", listOf(DeckEntry("SOR-095", 2)), listOf(DeckEntry("HMW-100", 1)))
    private val saved = DeckStore.SavedDeck("id", "Équipe impériale", "Premier", deck, 1)
    private val owned = listOf(CollectionCard(1, unit.name, "HMW", "100", "Foil", 2), CollectionCard(2, leader.name, "SOR", "001", "Normale", 1), CollectionCard(3, base.name, "SOR", "024", "Normale", 1))

    @Test fun globalSearchFindsAccentsEnglishNamesReferencesAndDeckContents() {
        val index = GlobalSearchIndex(catalog, owned, listOf(saved), details)
        assertTrue(index.cardMatches(unit, "eclaireur sor-095"))
        assertTrue(index.cardMatches(unit, "imperial scout"))
        assertTrue(index.deckMatches(saved, "equipe"))
        assertTrue(index.deckMatches(saved, "imperial scout"))
        assertFalse(index.deckMatches(saved, "introuvable"))
        assertEquals(2, index.stock(unit)); assertEquals(1, index.deckCount(reprint))
    }
    @Test fun usagesAndShoppingAllocateSharedStockAcrossBothZonesOnce() {
        assertEquals(3, DeckCompanion.usages(unit, listOf(saved), catalog, details).single().second)
        assertEquals(2, DeckCompanion.stock(unit, owned, catalog, details))
        val shopping = DeckCompanion.shopping(deck, owned, catalog, details)
        assertEquals("1 × Éclaireur impérial · HMW 100", shopping)
        assertEquals(2, owned.first().quantity)
    }
    @Test fun cardSheetAddRespectsFamilyAndReserveLimits() {
        assertThrows(IllegalArgumentException::class.java) { DeckCompanion.add(deck, unit, catalog, details, DeckEditor.Zone.MAIN) }
        val empty = deck.copy(deck = emptyList(), sideboard = emptyList())
        val added = DeckCompanion.add(empty, unit, catalog, details, DeckEditor.Zone.SIDEBOARD)
        assertTrue(empty.sideboard.isEmpty()); assertEquals(listOf(DeckEntry("SOR-095", 1)), added.sideboard)
        val full = empty.copy(sideboard = listOf(DeckEntry("SOR-999", 10)))
        assertThrows(IllegalArgumentException::class.java) { DeckCompanion.add(full, unit, catalog, details, DeckEditor.Zone.SIDEBOARD) }
    }
    @Test fun identitySelectionPreservesAllDrawEntriesAndUnknownsBlockShopping() {
        val newBase = base.copy(number = "025")
        val changed = DeckCompanion.add(deck, newBase, catalog + newBase, details, DeckEditor.Zone.MAIN)
        assertEquals("SOR-025", changed.base); assertEquals(deck.deck, changed.deck); assertEquals(deck.sideboard, changed.sideboard)
        assertThrows(IllegalArgumentException::class.java) { DeckCompanion.shopping(deck.copy(leader = "SOR-999"), owned, catalog, details) }
    }
}
