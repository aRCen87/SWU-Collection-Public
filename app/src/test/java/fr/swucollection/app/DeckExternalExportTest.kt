package fr.swucollection.app

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class DeckExternalExportTest {
    private val catalog = listOf(
        CatalogCard("SOR", "001", "Directeur Krennic — Aspire au pouvoir", "Leader", "Commune"),
        CatalogCard("SOR", "024", "Base Echo", "Base", "Commune"),
        CatalogCard("SOR", "095", "Infanterie de Marine", "Unité", "Commune"),
        CatalogCard("SOR", "092", "Barrage dévastateur", "Évènement", "Peu commune"))
    private val deck = PortableDeck("SOR-001", "SOR-024", listOf(DeckEntry("SOR-095", 3)), listOf(DeckEntry("SOR-092", 2)))
    private fun bundledDetails(): CardDetails {
        val file = listOf(File("src/main/assets/card-details.json.gz"), File("app/src/main/assets/card-details.json.gz"))
            .first { it.isFile }
        return CardDetails.loadAsset { file.inputStream() }
    }
    @Test fun meleeUsesVerifiedEnglishNamesSubtitlesSectionsAndSideboard() {
        val text = DeckExternalExport.payload(DeckDestination.MELEE, "Équipe d’été", deck, catalog, bundledDetails())
        assertEquals("Leaders\n1 | Director Krennic | Aspiring to Authority\n\nBase\n1 | Echo Base\n\nDeck\n3 | Battlefield Marine\n\nSideboard\n2 | Overwhelming Barrage\n", text)
        assertEquals(deck, DeckTextImport.parse(text, catalog, bundledDetails()))
    }
    @Test fun karabastPreservesTheFullDeckAndUnicodeTitleInSwudbJson() {
        val json = DeckExternalExport.payload(DeckDestination.KARABAST, "Équipe d’été", deck, catalog, CardDetails.empty())
        assertEquals(deck, DeckInterchange.importJson(json))
        assertEquals("Équipe d’été", org.json.JSONObject(json).getJSONObject("metadata").getString("name"))
    }
    @Test fun meleeNeverSilentlyExportsFrenchFallbackOrUnknownReferences() {
        listOf(deck, deck.copy(sideboard = listOf(DeckEntry("SOR-999", 1)))).forEach { input ->
            try {
                DeckExternalExport.payload(DeckDestination.MELEE, "Test", input, catalog, CardDetails.empty())
                fail("An unresolved English name was exported")
            } catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun classicLeaderAndUnitIdentitiesStayDistinctInBundledData() {
        val details = bundledDetails()
        val leader = catalog.first()
        val vader = CatalogCard("SOR", "010", "Dark Vador", "Leader", "Spéciale")
        assertEquals("Director Krennic — Aspiring to Authority", details.get(leader)?.englishName)
        assertEquals("Darth Vader — Dark Lord of the Sith", details.get(vader)?.englishName)
        assertNotEquals(details.family(leader), details.family(vader))
    }
    @Test fun scannedPrintsExportAsClassicReferencesAndKeepZoneCounts() {
        val normal = CatalogCard("JTL", "100", "Carte", "Unité", "Commune",
            "https://cards.example/card_0401100_FR.webp")
        val hyperspace = normal.copy(number = "300", imageUrl = "https://cards.example/card_0402300_FR.webp")
        val foil = normal.copy(number = "788", imageUrl = "https://cards.example/card_0432788_FR.webp")
        val details = CardDetails.parse("""{"format":"SWU_CARD_DETAILS","version":1,"cards":{
            "JTL:100":{"family":"same","en":"Card","cost":2,"aspects":[],"traits":[]},
            "JTL:300":{"family":"same","en":"Card","cost":2,"aspects":[],"traits":[]},
            "JTL:788":{"family":"same","en":"Card","cost":2,"aspects":[],"traits":[]}
        }}""")
        val scanned = PortableDeck("SOR-001", "SOR-024",
            listOf(DeckEntry("JTL-100", 1), DeckEntry("JTL-300", 1)), listOf(DeckEntry("JTL-788", 1)))
        val json = DeckExternalExport.payload(DeckDestination.KARABAST, "Scanné", scanned, catalog + listOf(normal, hyperspace, foil), details)
        val exported = DeckInterchange.importJson(json)
        assertEquals(listOf(DeckEntry("JTL-100", 2)), exported.deck)
        assertEquals(listOf(DeckEntry("JTL-100", 1)), exported.sideboard)
        assertEquals("JTL-788", scanned.sideboard.single().cardId)
    }
    @Test fun unidentifiedVariantFamilyCannotSilentlyExportAnUnsupportedPrint() {
        val variant = CatalogCard("JTL", "788", "Variante", "Unité", "Rare",
            "https://cards.example/card_0432788_FR.webp")
        assertThrows(IllegalArgumentException::class.java) {
            DeckExternalExport.payload(DeckDestination.KARABAST, "Test",
                deck.copy(deck = listOf(DeckEntry("JTL-788", 1))), catalog + variant, CardDetails.empty())
        }
    }
    @Test fun ambiguousOldHyperspaceAndPromoPrintsRequireVerifiedClassicEquivalents() {
        val oldHyperspace = CatalogCard("SOR", "300", "Variante", "Unité", "Rare",
            "https://cards.example/card_SWH_01fr_300_HYP_x.webp")
        val promo = CatalogCard("C24", "006", "Promo", "Unité", "Rare",
            "https://cards.example/card_0101006_FR.webp")
        listOf(oldHyperspace, promo).forEach { card ->
            assertThrows(IllegalArgumentException::class.java) {
                DeckExternalExport.payload(DeckDestination.KARABAST, "Test",
                    deck.copy(deck = listOf(DeckEntry(DeckConstruction.reference(card), 1))),
                    catalog + card, CardDetails.empty())
            }
        }
    }
}

