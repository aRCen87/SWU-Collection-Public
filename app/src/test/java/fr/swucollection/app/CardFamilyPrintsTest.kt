package fr.swucollection.app

import org.junit.Assert.*
import org.junit.Test

class CardFamilyPrintsTest {
    private val standard = CatalogCard("JTL", "054", "Gold Leader", "Unité", "Rare", "https://example.com/card_04010054_EN.webp")
    private val foil = standard.copy(number = "552", imageUrl = "https://example.com/card_04310552_EN.webp")
    private val reprint = standard.copy(setCode = "HMW", number = "100", imageUrl = "https://example.com/card_09010100_FR.webp")
    private val unrelated = standard.copy(number = "055")
    private val details = CardDetails.parse("""{"format":"SWU_CARD_DETAILS","version":1,"cards":{
        "JTL:054":{"family":"gold","en":"Gold Leader","cost":3,"aspects":[],"traits":[]},
        "JTL:552":{"family":"gold","en":"Gold Leader","cost":3,"aspects":[],"traits":[]},
        "HMW:100":{"family":"gold","en":"Gold Leader","cost":3,"aspects":[],"traits":[]}
    }}""")
    private val owned = listOf(
        CollectionCard(1, standard.name, "JTL", "054", "Normale", 2, "FR"),
        CollectionCard(2, standard.name, "JTL", "054", "Normale", 3, "EN"),
        CollectionCard(3, standard.name, "JTL", "552", "Foil", 1, "DE"))

    @Test fun familySheetGroupsLanguagesAndKeepsExactPrintsAndChronology() {
        val prints = familyPrints(standard, listOf(reprint, standard, foil, unrelated), owned, details)
        assertEquals(listOf("HMW", "JTL", "JTL"), prints.map { it.card.setCode })
        assertEquals(listOf("Normale", "Normale", "Foil"), prints.map { it.variant })
        assertEquals(listOf(0, 5, 1), prints.map { it.quantity })
        assertEquals(listOf("FR", "EN"), prints[1].owned.map { it.language })
        assertEquals(owned, prints.flatMap { it.owned })
    }
    @Test fun unknownFamiliesNeverMergeByNameAndHistoricalStockRemainsVisible() {
        assertEquals(listOf(unrelated), familyPrints(unrelated, listOf(standard, unrelated), owned, details).map { it.card }.distinct())
        val legacy = owned.first().copy(variant = "Promo")
        val prints = familyPrints(standard, listOf(standard), listOf(legacy), details)
        assertEquals(listOf("Normale", "Promo"), prints.map { it.variant })
        assertEquals(2, prints.last().quantity)
        assertFalse("Promo" in ScanVariants.detect(standard).choices)
    }
    @Test fun pendingAndDeckScansRejectAnIncompatibleFinishWithoutLosingStock() {
        assertFalse(ScanBatchEntry(foil, "Normale", 1).resolved)
        assertTrue(ScanBatchEntry(foil, "Foil", 1).resolved)
        assertFalse(ScanBatchEntry(foil.copy(language = CardLanguages.UNKNOWN), "Foil", 1).resolved)
        assertThrows(IllegalArgumentException::class.java) { DeckScan.addCollectionCopy(emptyList(), foil, "Normale") }
        val full = listOf(ScanBatchEntry(foil, "Foil", 9999))
        assertThrows(IllegalArgumentException::class.java) { DeckScan.addCollectionCopy(full, foil, "Foil") }
        assertEquals(9999, full.single().quantity)
        val english = DeckScan.addCollectionCopy(full, foil.copy(language = "EN"), "Foil")
        assertEquals(listOf(9999, 1), english.map { it.quantity })
    }
}
