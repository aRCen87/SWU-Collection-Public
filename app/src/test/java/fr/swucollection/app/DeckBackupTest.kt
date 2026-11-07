package fr.swucollection.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.json.JSONObject
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
class DeckBackupTest {
    private lateinit var context: Context
    private lateinit var inventory: CollectionDatabase
    private lateinit var store: DeckStore
    private val deck = PortableDeck("SOR-001", "SOR-024", listOf(DeckEntry("SOR-100", 3)), listOf(DeckEntry("SOR-101", 2)))
    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("swu_decks.db"); context.deleteDatabase("swu_collection.db")
        inventory = CollectionDatabase(context); store = DeckStore(context)
    }
    @After fun cleanup() {
        store.close(); inventory.close()
        context.deleteDatabase("swu_decks.db"); context.deleteDatabase("swu_collection.db")
    }
    private fun seed(): String {
        inventory.addOrIncrement("Éclairs", "SOR", "100", "Foil")
        val list = inventory.createCardList("À trouver")
        inventory.putListCard(list, CatalogCard("SOR", "100", "Éclairs", "Évènement", "Rare"), 2)
        return store.save("L’été d’Élodie", "Premier", deck)
    }
    @Test fun globalBackupRestoresDecksListsAndVariantsTogether() {
        val id = seed(); val backup = inventory.exportJson()
        assertEquals(1, validateBackup(backup).decks)
        store.delete(id); inventory.changeQuantity(inventory.allCards().single().id, 3)
        inventory.importJson(backup, true)
        assertEquals(backup, inventory.exportJson())
        assertEquals(deck, store.all().single().deck)
        assertEquals("Foil", inventory.allCards().single().variant)
        assertEquals(1, inventory.allCards().single().quantity)
    }
    @Test fun malformedDeckRejectsTheWholeBackupBeforeAnyMutation() {
        seed(); val before = inventory.exportJson(); val root = JSONObject(before)
        root.getJSONArray("decks").getJSONObject(0).getJSONObject("deck")
            .getJSONArray("deck").getJSONObject(0).put("count", 1.5)
        try { inventory.importJson(root.toString(), true); fail("Accepted invalid deck") }
        catch (_: IllegalArgumentException) { }
        assertEquals(before, inventory.exportJson())
    }
    @Test fun sqliteFailureRollsBackCollectionListsAndDecks() {
        seed(); val before = inventory.exportJson()
        inventory.writableDatabase.execSQL("CREATE TRIGGER reject_restore BEFORE INSERT ON decks BEGIN SELECT RAISE(ABORT, 'test'); END")
        try { inventory.importJson(before, true); fail("Restore must fail") }
        catch (_: android.database.SQLException) { }
        assertEquals(before, inventory.exportJson())
    }
    @Test fun oldBackupPreservesCurrentDecksAndMergeKeepsNewerNames() {
        val id = seed(); val old = JSONObject(inventory.exportJson()).put("version", 4).apply { remove("decks") }
        inventory.importJson(old.toString(), true)
        assertEquals(id, store.all().single().id)
        val backup = inventory.exportJson()
        store.save("Nom plus récent", "Premier", deck, id)
        inventory.importJson(backup, false)
        assertEquals("Nom plus récent", store.all().single().name)
        assertEquals(1, store.all().size)
    }
    @Test fun upgradeFromEightMigratesLegacyDecksOnceAndPreservesInventory() {
        inventory.addOrIncrement("Éclairs", "SOR", "100", "Foil")
        inventory.writableDatabase.execSQL("DROP TABLE decks")
        inventory.writableDatabase.execSQL("DROP TABLE deck_import_state")
        inventory.writableDatabase.version = 8
        inventory.close(); store.close()
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath("swu_decks.db"), null).use { old ->
            old.execSQL("CREATE TABLE decks(id TEXT PRIMARY KEY,name TEXT,format TEXT,payload TEXT,updated_at INTEGER)")
            old.execSQL("INSERT INTO decks VALUES(?,?,?,?,?)", arrayOf("legacy", "Ancien deck", "Premier", DeckInterchange.exportJson(deck), 123L))
        }
        inventory = CollectionDatabase(context); store = DeckStore(context)
        assertEquals(12, inventory.readableDatabase.version)
        assertEquals("Foil", inventory.allCards().single().variant)
        assertEquals("legacy", store.all().single().id)
        store.delete("legacy"); store.close(); inventory.close()
        inventory = CollectionDatabase(context); store = DeckStore(context)
        assertTrue(store.all().isEmpty())
        assertTrue(context.getDatabasePath("swu_decks.db").exists())
    }
}

