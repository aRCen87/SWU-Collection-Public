package fr.swucollection.app

import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class CollectionTest18Test {
    private val a = CatalogCard("SOR", "030", "Carte A", "Unité", "Commune", "https://example.test/card_0101030_FR.png")
    private val b = a.copy(number = "031", name = "Carte B", imageUrl = "https://example.test/card_0101031_FR.png")
    private val context get() = RuntimeEnvironment.getApplication() as Context
    private fun fresh(block: (CollectionDatabase) -> Unit) {
        context.deleteDatabase("swu_collection.db")
        val db = CollectionDatabase(context)
        try { block(db) } finally { db.close() }
    }

    @Test fun ambiguousImagesKeepTwoDistinctRealReferencesWithoutInventingLanguageOrFinish() {
        val choices = scannerVisualChoices(listOf("SOR" to "30", "SOR" to "030", "SOR" to "031", "BAD" to "999"), listOf(a, b))
        assertEquals(listOf(a, b), choices)
        assertTrue(scannerVisualChoices(listOf("BAD" to "999"), listOf(a, b)).isEmpty())
        assertEquals(2, scannerVisualChoices(listOf("SOR" to "030", "SOR" to "031", "SOR" to "032"), listOf(a, b, a.copy(number = "032"))).size)
    }

    @Test fun reviewsOnlyFlagCheckableIssuesAndConfirmedLargeQuantitiesReturnWhenEdited() {
        val unknown = CollectionCard(1, a.name, "SOR", "030", "Showcase", 25, "UND")
        assertEquals(3, CollectionReview.find(listOf(unknown), listOf(a)).single().reasons.size)
        assertTrue(CollectionReview.find(listOf(unknown), emptyList()).isEmpty())
        val normal = unknown.copy(language = "EN", variant = "Foil")
        val confirmed = setOf(CollectionReview.quantityKey(normal))
        assertTrue(CollectionReview.find(listOf(normal), listOf(a), confirmed).isEmpty())
        assertEquals(1, CollectionReview.find(listOf(normal.copy(quantity = 26)), listOf(a), confirmed).size)
        assertEquals(1, CollectionReview.find(listOf(normal.copy(number = "999", quantity = 1)), listOf(a)).single().reasons.size)
    }

    @Test fun correctionMergesCopiesReversiblyAndRejectsStaleReviewOrOverflowAtomically() = fresh { db ->
        repeat(2) { db.addOrIncrement(a.name, "SOR", "030", "Normale", "UND") }
        db.addOrIncrement(a.name, "SOR", "030", "Normale", "FR")
        val before = db.allCards()
        val source = before.single { it.language == "UND" }
        db.correctReviewedRow(source, a, "FR", "Normale", 2)
        assertEquals(3, db.allCards().single().quantity)
        assertEquals("FR", db.allCards().single().language)
        val merged = db.exportJson()
        assertThrows(IllegalStateException::class.java) { db.correctReviewedRow(source, a, "EN", "Normale", 2) }
        assertEquals(merged, db.exportJson())
        db.undoLastChange(); assertEquals(before, db.allCards())
        val original = db.exportJson()
        assertThrows(IllegalArgumentException::class.java) { db.correctReviewedRow(source, a, "FR", "Normale", 9999) }
        assertEquals(original, db.exportJson())
        assertThrows(IllegalArgumentException::class.java) { db.correctReviewedRow(source, a, "FR", "Showcase", 2) }
        assertEquals(original, db.exportJson())
    }

    @Test fun correctingCannotRelabelOrRemoveLentCopies() = fresh { db ->
        repeat(2) { db.addOrIncrement(a.name, "SOR", "030", "Normale", "FR") }
        val source = db.allCards().single()
        db.putTool("loans", LoanBook().add(source, "Ami", 2, 0, 100).json())
        val before = db.exportJson()
        assertThrows(IllegalArgumentException::class.java) { db.correctReviewedRow(source, a, "EN", "Normale", 2) }
        assertThrows(IllegalArgumentException::class.java) { db.correctReviewedRow(source, a, "FR", "Normale", 1) }
        assertEquals(before, db.exportJson())
    }

    @Test fun healthNeverTreatsPendingRestorationOrOtherAccountsAsSaved() {
        val prefs = context.getSharedPreferences("automatic_account_backup", 0)
        prefs.edit().clear().commit()
        assertTrue(AutomaticAccountBackup.health(context, "alice", "one").pending)
        AutomaticAccountBackup.acknowledge(context, "alice", "saved", "one")
        val verified = AutomaticAccountBackup.health(context, "alice", "one")
        assertFalse(verified.pending); assertTrue(verified.verifiedAt > 0)
        assertTrue(AutomaticAccountBackup.health(context, "alice", "edited").pending)
        assertTrue(AutomaticAccountBackup.health(context, "bob", "one").pending)
        assertFalse(AutomaticAccountBackup.health(context, "bob", "one").ownerMatches)
        // Simulate the pending marker without scheduling a Worker in this test.
        prefs.edit().putString("alice.hash", "").putLong("alice.savedAt", 0).commit()
        val pending = AutomaticAccountBackup.health(context, "alice", "one")
        assertTrue(pending.pending); assertEquals(verified.verifiedAt, pending.verifiedAt)
        prefs.edit().clear().commit()
    }

    @Test fun scannedCollectionsProduceFriendDraftAndHandoverSurvivesBackupWithoutReplay() {
        val catalog = listOf(a, b)
        val details = CardDetails.empty()
        var aliceBefore = ""; var bobBefore = ""
        lateinit var mine: TradeProfile; lateinit var theirs: TradeProfile
        fresh { db ->
            db.commitScanBatch("scan-alice", listOf(ScanBatchEntry(a, "Normale", 4)), details)
            db.commitScanBatch("scan-alice", listOf(ScanBatchEntry(a, "Normale", 4)), details)
            assertEquals(4, db.allCards().single().quantity)
            mine = TradeProfile.build(catalog, db.allCards(), mapOf("SOR" to false), details)
            aliceBefore = db.exportJson()
        }
        fresh { db ->
            db.commitScanBatch("scan-bob", listOf(ScanBatchEntry(b.copy(language = "EN"), "Normale", 4)), details)
            theirs = TradeProfile.build(catalog, db.allCards(), mapOf("SOR" to false), details)
            bobBefore = db.exportJson()
        }
        val draft = FriendTradeSuggestions.prepare(PlayerProfile("bob", "Bob"), mine, TradeProfile.parse(theirs.json()))
        assertEquals("FR", draft.offer.single().language); assertEquals("EN", draft.wanted.single().language)
        assertEquals("SOR-030", draft.offer.single().cardId); assertEquals("SOR-031", draft.wanted.single().cardId)
        val trade = PlayerTrade("test18", listOf("alice", "bob"), "alice", "accepted", CommunitySocial.encodeTrade(draft.offer), CommunitySocial.encodeTrade(draft.wanted), 100)
        fresh { db ->
            db.importJson(aliceBefore, true)
            assertTrue(db.applyConfirmedTrade("alice", trade, catalog)); assertFalse(db.applyConfirmedTrade("alice", trade, catalog))
            assertEquals(3, db.allCards().single { it.number == "030" }.quantity)
            assertEquals("EN", db.allCards().single { it.number == "031" }.language)
            val saved = db.exportJson(); db.importJson(saved, true)
            assertFalse(db.applyConfirmedTrade("alice", trade, catalog)); assertEquals(saved, db.exportJson())
        }
        fresh { db ->
            db.importJson(bobBefore, true)
            assertTrue(db.applyConfirmedTrade("bob", trade, catalog))
            assertEquals(3, db.allCards().single { it.number == "031" }.quantity)
            assertEquals("FR", db.allCards().single { it.number == "030" }.language)
            assertEquals(4, db.allCards().sumOf { it.quantity })
        }
    }
}
