package fr.swucollection.app

import org.junit.Assert.*
import org.junit.Test

class DeckScanTest {
    private val leader = CatalogCard("HMW", "001", "Leader", "Leader", "Rare")
    private val otherLeader = leader.copy(number = "002")
    private val base = leader.copy(number = "020", name = "Base", type = "Base")
    private val unit = leader.copy(number = "100", name = "Unité", type = "Unité")
    private val variant = unit.copy(number = "300")
    private val catalog = listOf(leader, otherLeader, base, unit, variant)
    private val details = CardDetails.parse("""{"format":"SWU_CARD_DETAILS","version":1,"cards":{
        "HMW:100":{"family":"unit","en":"Unit","cost":2,"aspects":[],"traits":[]},
        "HMW:300":{"family":"unit","en":"Unit","cost":2,"aspects":[],"traits":[]}
    }}""")
    private val empty = PortableDeck("HMW-001", "HMW-020", emptyList())
    private fun add(deck: PortableDeck, card: CatalogCard, zone: DeckEditor.Zone = DeckEditor.Zone.MAIN) =
        DeckScan.add(deck, card, zone, catalog, details)

    @Test fun findsExactCollectorNumberIncludingVariants() {
        assertEquals(variant, DeckScan.find(catalog, " hmw ", "300 / 264"))
        assertEquals(leader, DeckScan.find(catalog, "HMW", "1"))
        assertNull(DeckScan.find(catalog, "HMW", "999"))
        assertNull(DeckScan.find(catalog, "HMW", "abc"))
    }
    @Test fun routesIdentitiesAndAddsOnlyToChosenZone() {
        val deck = add(add(empty, otherLeader), unit, DeckEditor.Zone.SIDEBOARD)
        assertEquals("HMW-002", deck.leader)
        assertEquals("HMW-020", deck.base)
        assertTrue(deck.deck.isEmpty())
        assertEquals(listOf(DeckEntry("HMW-100", 1)), deck.sideboard)
        assertEquals(emptyList<DeckEntry>(), empty.sideboard)
        assertEquals("HMW-020", add(deck.copy(base = "HMW-021"), base).base)
    }
    @Test fun copyLimitIncludesOtherPrintsAndReserveWithoutMutatingTheDraft() {
        val deck = add(add(add(empty, unit), unit), variant, DeckEditor.Zone.SIDEBOARD)
        assertTrue(runCatching { add(deck, variant) }.isFailure)
        assertEquals(2, deck.deck.single().count)
        assertEquals(1, deck.sideboard.single().count)
    }
    @Test fun fullReserveStillAllowsMainDeckAndIdentityChanges() {
        val full = empty.copy(sideboard = listOf(DeckEntry("HMW-999", 10)))
        assertTrue(runCatching { add(full, unit, DeckEditor.Zone.SIDEBOARD) }.isFailure)
        assertEquals(1, add(full, unit).deck.single().count)
        assertEquals("HMW-002", add(full, otherLeader, DeckEditor.Zone.SIDEBOARD).leader)
    }
    @Test fun unsupportedTypesAreRejected() {
        assertTrue(runCatching { add(empty, unit.copy(type = "Jeton")) }.isFailure)
    }
}
