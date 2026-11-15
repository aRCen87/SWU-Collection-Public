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
class DeckDraftStoreTest {
    private lateinit var context: Context
    private val deck = PortableDeck("SOR-001", "SOR-024", listOf(DeckEntry("SOR-095", 3)))
    @Before fun setup() { context = RuntimeEnvironment.getApplication(); context.filesDir.resolve("deck-drafts").deleteRecursively(); context.deleteDatabase("swu_collection.db") }
    @After fun cleanup() { context.filesDir.resolve("deck-drafts").deleteRecursively(); context.deleteDatabase("swu_collection.db") }
    @Test fun draftSurvivesStoreReopeningAndKeepsPartialIdentityChoices() {
        val value = DeckDraft("Équipe d’été", deck, true, false)
        DeckDraftStore(context).write("new", "baseline", value)
        assertTrue(DeckDraftStore(context).hasNewDraft())
        assertEquals(value, DeckDraftStore(context).read("new", "baseline"))
        assertNull(DeckDraftStore(context).read("new", "changed saved deck"))
    }
    @Test fun savingOrDiscardingOneDraftDoesNotChangeOtherDraftsOrInventory() {
        val store = DeckDraftStore(context)
        val db = CollectionDatabase(context)
        try {
            db.addOrIncrement("Carte", "SOR", "095", "Foil")
            val before = db.exportJson()
            store.write("new", "base", DeckDraft("Test", deck, true, true))
            store.write("saved-deck-id", "base2", DeckDraft("Autre", deck, true, true))
            store.clear("new")
            assertFalse(store.hasNewDraft()); assertNotNull(store.read("saved-deck-id", "base2"))
            assertEquals(before, db.exportJson())
        } finally { db.close() }
    }
    @Test fun unfinishedDecksAreListedSeparatelyAfterReopeningAndKeepIdentityChoices() {
        val store = DeckDraftStore(context)
        val first = DeckDraft("Premier chantier", deck.copy(deck = emptyList()), false, false)
        val second = DeckDraft("Deuxième chantier", deck, true, false)
        store.write("new:first", "baseline", first)
        store.write("new:second", "baseline", second)
        val reopened = DeckDraftStore(context)
        assertEquals(mapOf("new:first" to first, "new:second" to second), reopened.newDrafts().toMap())
        assertEquals(first, reopened.read("new:first", "baseline"))
        assertEquals(second, reopened.read("new:second", "baseline"))
    }
    @Test fun legacyDraftIsVisibleAndDeletingAnotherDraftKeepsInventoryAndSavedEdits() {
        val store = DeckDraftStore(context)
        val value = DeckDraft("Mon ancien deck", deck, false, true)
        // Earlier app versions did not store the key in the JSON payload.
        store.write("new", "baseline", value)
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest("new".toByteArray())
            .joinToString("") { "%02x".format(it) }
        val legacy = context.filesDir.resolve("deck-drafts/$hash.json")
        val json = org.json.JSONObject(legacy.readText()); json.remove("key"); legacy.writeText(json.toString())
        store.write("new:other", "baseline", value.copy(name = "Autre deck"))
        store.write("saved-deck-id", "saved baseline", value)
        val db = CollectionDatabase(context)
        try {
            db.addOrIncrement("Carte", "SOR", "095", "Foil")
            val before = db.exportJson()
            store.clear("new:other")
            assertEquals(listOf("new" to value), DeckDraftStore(context).newDrafts())
            assertEquals(value, store.read("saved-deck-id", "saved baseline"))
            assertEquals(before, db.exportJson())
        } finally { db.close() }
    }

    @Test fun inlineUpdatePreservesBaselinePendingScansOtherDraftAndInventoryAndRejectsStaleChanges() {
        val card = CatalogCard("SOR", "095", "Carte test", "Unité", "Rare")
        val original = DeckDraft("À finir", deck, false, false,
            listOf(ScanBatchEntry(card, "Foil", 2)), "scan-pending")
        val store = DeckDraftStore(context)
        store.write("new:inline", "original baseline", original)
        store.write("new:other", "other baseline", original.copy(name = "Autre"))
        val db = CollectionDatabase(context)
        try {
            db.addOrIncrement("Carte déjà possédée", "SOR", "095", "Normale")
            val inventory = db.exportJson()
            val changed = original.copy(deck = deck.copy(deck = listOf(DeckEntry("SOR-095", 2))))
            store.update("new:inline", original, changed)
            assertEquals(changed, DeckDraftStore(context).read("new:inline", "original baseline"))
            assertFalse(changed.leaderChosen); assertFalse(changed.baseChosen)
            assertEquals(original.collectionEntries, changed.collectionEntries)
            assertEquals(original.collectionBatchId, changed.collectionBatchId)
            assertTrue(runCatching { store.update("new:inline", original, original) }.isFailure)
            assertEquals(changed, store.read("new:inline", "original baseline"))
            assertEquals(original.copy(name = "Autre"), store.read("new:other", "other baseline"))
            assertEquals(inventory, db.exportJson())
        } finally { db.close() }
    }

}

