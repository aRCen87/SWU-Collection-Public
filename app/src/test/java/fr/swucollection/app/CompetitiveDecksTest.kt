package fr.swucollection.app

import android.content.Context
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class CompetitiveDecksTest {
    private lateinit var context: Context
    private lateinit var inventory: CollectionDatabase
    private lateinit var store: DeckStore
    private val leader = CatalogCard("JTL", "001", "Leader", "Leader", "Rare")
    private val base = CatalogCard("JTL", "024", "Base", "Base", "Commune")
    private val unit = CatalogCard("JTL", "095", "Unité", "Unité", "Commune")
    private val reprint = unit.copy(setCode = "HMW", number = "100")
    private val catalog = listOf(leader, base, unit, reprint)
    private val deck = PortableDeck("JTL-001", "JTL-024", listOf(DeckEntry("JTL-095", 2)), listOf(DeckEntry("HMW-100", 1)))
    private val details = CardDetails.parse("""{"format":"SWU_CARD_DETAILS","version":1,"cards":{
        "JTL:001":{"family":"leader","en":"Leader","aspects":[],"traits":[]},
        "JTL:024":{"family":"base","en":"Base","aspects":[],"traits":[]},
        "JTL:095":{"family":"unit","en":"Unit","aspects":[],"traits":[]},
        "HMW:100":{"family":"unit","en":"Unit","aspects":[],"traits":[]}}}""")

    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("swu_collection.db")
        inventory = CollectionDatabase(context); store = DeckStore(context)
    }
    @After fun cleanup() { store.close(); inventory.close(); context.deleteDatabase("swu_collection.db") }

    @Test fun missingCardsPoolReprintsLanguagesAndFoilsAcrossMainAndSideboardOnlyOnce() {
        val owned = listOf(CollectionCard(1, "Unité", "HMW", "100", "Foil", 2),
            CollectionCard(2, "Leader", "JTL", "001", "Normale", 1))
        val readiness = CompetitiveDecks.readiness(deck, owned, catalog, details)
        assertEquals(5, readiness.required)
        assertEquals(2, readiness.missing) // one base and one shared-family unit
        assertFalse(readiness.ready)
        assertEquals(2, owned.first().quantity)
    }
    @Test fun unrecognizedReferenceCannotBeReportedAsACompleteDeck() {
        val result = CompetitiveDecks.readiness(deck.copy(sideboard = listOf(DeckEntry("HMW-999", 1))), emptyList(), catalog, details)
        assertEquals(1, result.unknown)
        assertFalse(result.ready)
    }
    @Test fun savingTournamentCopiesPreservesExistingDeckAndInventoryAndSurvivesBackupRestore() {
        inventory.addOrIncrement("Unité", "HMW", "100", "Foil")
        val original = store.save("Mon deck personnel", "Premier", deck)
        val cards = inventory.exportCsv()
        val first = CompetitiveDecks.saveCopy(store, "Top 8 — été", deck, catalog)
        val second = CompetitiveDecks.saveCopy(store, "Top 8 — copie", deck, catalog)
        assertNotEquals(original, first); assertNotEquals(first, second)
        assertEquals("Mon deck personnel", store.all().first { it.id == original }.name)
        assertEquals(cards, inventory.exportCsv())
        val backup = inventory.exportJson()
        store.delete(first)
        inventory.importJson(backup, true)
        assertEquals(deck, store.all().first { it.id == first }.deck)
        assertEquals("Top 8 — été", store.all().first { it.id == first }.name)
        assertEquals(cards, inventory.exportCsv())
    }
    @Test fun unknownOrWrongIdentityCannotSaveAPartialSilentImport() {
        val before = inventory.exportJson()
        for (bad in listOf(deck.copy(base = "HMW-999"), deck.copy(leader = "JTL-095"))) {
            try { CompetitiveDecks.saveCopy(store, "Invalide", bad, catalog); fail("Invalid deck saved") }
            catch (_: IllegalArgumentException) { }
        }
        assertEquals(before, inventory.exportJson())
    }
    @Test fun exportedCopiedListRoundTripsLeaderBaseMainAndReserve() {
        val copied = DeckInterchange.exportJson(deck, "Tournoi — Finale")
        assertEquals(deck, DeckInterchange.importJson(copied))
    }
}
