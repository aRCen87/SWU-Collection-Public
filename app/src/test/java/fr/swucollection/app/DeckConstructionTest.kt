package fr.swucollection.app

import org.junit.Assert.*
import org.junit.Test

class DeckConstructionTest {
    private val leader = CatalogCard("HMW", "001", "Leader", "Leader", "Rare")
    private val base = CatalogCard("HMW", "020", "Base", "Base", "Commune")
    private val unit = CatalogCard("HMW", "100", "Unité", "Unité", "Commune")
    private val variant = unit.copy(number = "300")
    private val catalog = listOf(leader, base, unit, variant)
    private val details = CardDetails.parse("""{"format":"SWU_CARD_DETAILS","version":1,"cards":{
        "HMW:001":{"family":"leader","en":"Leader","cost":null,"aspects":[],"traits":[]},
        "HMW:020":{"family":"base","en":"Base","cost":null,"aspects":[],"traits":[]},
        "HMW:100":{"family":"unit","en":"Unit","cost":2,"aspects":[],"traits":[]},
        "HMW:300":{"family":"unit","en":"Unit","cost":2,"aspects":[],"traits":[]}
    }}""")
    private fun report(deck: PortableDeck, owned: List<CollectionCard> = emptyList()) =
        DeckConstruction.report(deck, owned, catalog, details, LegalityRules.DEFAULT)

    @Test fun mainAndSideboardShareTheCopyLimitAcrossPrints() {
        val r = report(PortableDeck("HMW-001", "HMW-020", listOf(DeckEntry("HMW-100", 2)), listOf(DeckEntry("HMW-300", 2))))
        assertTrue(r.issues.any { it.contains("maximum 3") })
    }
    @Test fun stockIsNotCountedTwiceForMainAndReserve() {
        val owned = listOf(CollectionCard(1, "Unité", "HMW", "300", "Hyperspace", 2))
        val r = report(PortableDeck("HMW-001", "HMW-020", listOf(DeckEntry("HMW-100", 2)), listOf(DeckEntry("HMW-300", 1))), owned)
        val need = r.needs.single { it.name == "Unité" }
        assertEquals(3, need.required)
        assertEquals(2, need.owned)
        assertEquals(1, need.missing)
        assertEquals(3, r.needs.sumOf { it.missing }) // one unit, one leader and one base
        assertEquals(2, owned.single().quantity)
    }
    @Test fun manuallyEnteredStockReferencesDoNotCrashAndFractionsAreMatched() {
        val owned = listOf(
            CollectionCard(1, "Unité", "HMW", "100 / 264", "Foil", 2),
            CollectionCard(2, "Ancienne saisie", " HMW ", "inconnu", "Normale", 99))
        val r = report(PortableDeck("HMW-001", "HMW-020", listOf(DeckEntry("HMW-100", 3))), owned)
        assertEquals(1, r.needs.single { it.name == "Unité" }.missing)
        assertNull(DeckConstruction.stockReference("HMW", "inconnu"))
        assertEquals("HMW-100", DeckConstruction.stockReference(" hmw ", "100 / 264"))
    }
    @Test fun reserveDoesNotMeetTheMainDeckMinimum() {
        val r = report(PortableDeck("HMW-001", "HMW-020", listOf(DeckEntry("HMW-100", 49)), listOf(DeckEntry("HMW-300", 11))))
        assertTrue(r.issues.any { it.contains("49 / 50") })
        assertTrue(r.issues.any { it.contains("maximum 10") })
    }
    @Test fun unknownAndWrongZoneCardsCannotLookValid() {
        val r = report(PortableDeck("HMW-100", "HMW-020", listOf(DeckEntry("HMW-001", 1), DeckEntry("HMW-999", 1))))
        assertEquals(1, r.unknown)
        assertTrue(r.issues.any { it.contains("leader reconnu") })
        assertTrue(r.issues.any { it.contains("ce type de carte") })
    }
    @Test fun exportRetainsNameAndUsesExternalReferences() {
        val deck = PortableDeck("HMW-001", "HMW-020", listOf(DeckEntry("HMW-100", 3)))
        val json = org.json.JSONObject(DeckInterchange.exportJson(deck, "L’été d’Élodie"))
        assertEquals("L’été d’Élodie", json.getJSONObject("metadata").getString("name"))
        assertEquals("HMW_001", json.getJSONObject("leader").getString("id"))
        assertEquals(deck, DeckInterchange.importJson(json.toString()))
    }
}
