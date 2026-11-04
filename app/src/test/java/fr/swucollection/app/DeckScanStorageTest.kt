package fr.swucollection.app

import android.content.Context
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class DeckScanStorageTest {
    private lateinit var context: Context
    private val card = CatalogCard("JTL", "788", "Carte scannée", "Unité", "Rare")
    private val deck = PortableDeck("SOR-001", "SOR-024", listOf(DeckEntry("JTL-788", 1)))
    private val entries = listOf(ScanBatchEntry(card, "Hyperspace Foil", 1))
    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("swu_collection.db")
        context.filesDir.resolve("deck-drafts").deleteRecursively()
    }
    @After fun cleanup() {
        context.deleteDatabase("swu_collection.db")
        context.filesDir.resolve("deck-drafts").deleteRecursively()
    }
    @Test fun pendingCollectionAdditionsSurviveReopeningWithoutChangingInventory() {
        val db = CollectionDatabase(context)
        try {
            db.addOrIncrement(card.name, card.setCode, card.number, "Foil")
            val before = db.exportJson()
            val value = DeckDraft("Mon deck", deck, true, true, entries, "pending-one")
            DeckDraftStore(context).write("new", "baseline", value)
            assertEquals(value, DeckDraftStore(context).read("new", "baseline"))
            assertEquals(before, db.exportJson())
        } finally { db.close() }
    }
    @Test fun collectionIsOptionalAndPhysicalFinishIsPreservedOnCommit() {
        DeckStore(context).use { store ->
            val db = CollectionDatabase(context)
            try {
                store.saveWithScans("Deck seul", "Premier", deck, "deck-only", "no-additions", emptyList(), CardDetails.empty())
                assertTrue(db.allCards().isEmpty())
                store.saveWithScans("Avec collection", "Premier", deck, "deck-and-collection", "batch-one", entries, CardDetails.empty())
                assertEquals("788", db.allCards().single().number)
                assertEquals("Hyperspace Foil", db.allCards().single().variant)
                assertEquals(1, db.allCards().single().quantity)
                // Replaying a recovered draft must not count its physical cards twice.
                store.saveWithScans("Avec collection", "Premier", deck, "deck-and-collection", "batch-one", entries, CardDetails.empty())
                assertEquals(1, db.allCards().single().quantity)
                assertEquals(2, store.all().size)
            } finally { db.close() }
        }
    }
    @Test fun deckSaveFailureRollsBackInventoryHistoryAndScanEvents() {
        DeckStore(context).use { store ->
            val db = CollectionDatabase(context)
            try {
                db.addOrIncrement("Déjà possédée", "SOR", "095", "Normale")
                val before = db.exportJson()
                assertTrue(runCatching {
                    store.saveWithScans("", "Premier", deck, "broken", "batch-failed", entries, CardDetails.empty())
                }.isFailure)
                assertEquals(before, db.exportJson())
                assertFalse(db.isScanBatchCommitted("batch-failed"))
                assertTrue(store.all().isEmpty())
            } finally { db.close() }
        }
    }
    @Test fun pendingCopiesAggregateByExactPrintAndFinish() {
        val one = DeckScan.addCollectionCopy(emptyList(), card, "Hyperspace Foil")
        val two = DeckScan.addCollectionCopy(one, card, "Hyperspace Foil")
        val different = DeckScan.addCollectionCopy(two, card, "Hyperspace")
        assertEquals(1, one.single().quantity)
        assertEquals(listOf(2, 1), different.map { it.quantity })
        assertEquals(different, DeckScan.collectionFromJson(DeckScan.collectionJson(different)))
        assertTrue(runCatching { DeckScan.addCollectionCopy(one, card, "inconnue") }.isFailure)
    }
}
