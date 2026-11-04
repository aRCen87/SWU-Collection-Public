package fr.swucollection.app

import org.junit.Assert.*
import org.junit.Test

class DeckTextInterchangeTest {
    private val catalog = listOf(
        CatalogCard("SOR", "001", "Directrice Krennic", "Leader", "Rare"),
        CatalogCard("SOR", "024", "Base", "Base", "Commune"),
        CatalogCard("SOR", "100", "Éclairs — Frappe éclair", "Évènement", "Rare"))
    private val details = CardDetails.parse("""{"format":"SWU_CARD_DETAILS","version":1,"cards":{
        "SOR:001":{"family":"leader","en":"Director Krennic, Aspiring to Authority","cost":null,"aspects":[],"traits":[]},
        "SOR:024":{"family":"base","en":"Command Center","cost":null,"aspects":[],"traits":[]},
        "SOR:100":{"family":"event","en":"Lightning, Sudden Strike","cost":2,"aspects":[],"traits":[]}
    }}""")
    private val deck = PortableDeck("SOR-001", "SOR-024", listOf(DeckEntry("SOR-100", 2)), listOf(DeckEntry("SOR-100", 1)))
    @Test fun textRoundTripMatchesSwubaseSectionAndSubtitleSyntax() {
        val text = DeckTextExport.render(deck, catalog, details)
        assertTrue(text.startsWith("Leaders\n1 | Director Krennic | Aspiring to Authority"))
        assertTrue(text.contains("Sideboard\n1 | Lightning | Sudden Strike"))
        assertEquals(deck, DeckTextImport.parse(text, catalog, details))
    }
    @Test fun frenchNamesAccentsAndExplicitReferencesAreAccepted() {
        val text = "Leader\n1 SOR_001\nBase\n1 SOR-024\nMainDeck\n2 Eclairs | Frappe eclair\nSideboard\n1 SOR-100"
        assertEquals(deck, DeckTextImport.parse(text, catalog, details))
    }
    @Test fun ambiguousUnknownAndSecondLeaderAreRejected() {
        val text = DeckTextExport.render(deck, catalog, details)
        listOf(text.replace("Lightning | Sudden Strike", "Carte inconnue"),
            text.replace("Base\n", "1 SOR-001\nBase\n")).forEach { bad ->
            try { DeckTextImport.parse(bad, catalog, details); fail("Accepted invalid text") }
            catch (_: IllegalArgumentException) { }
        }
        val ambiguous = catalog + catalog.last().copy(number = "101")
        try { DeckTextImport.parse(text.replace("Lightning | Sudden Strike", "Éclairs | Frappe éclair"), ambiguous, details); fail("Accepted ambiguous name") }
        catch (_: IllegalArgumentException) { }
    }
}
