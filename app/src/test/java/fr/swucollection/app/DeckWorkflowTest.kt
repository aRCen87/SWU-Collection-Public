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
class DeckWorkflowTest {
    private lateinit var context: Context
    private lateinit var inventory: CollectionDatabase
    private lateinit var store: DeckStore
    private val deck = PortableDeck("SOR-001", "SOR-024", listOf(DeckEntry("SOR-100", 3)), listOf(DeckEntry("SOR-101", 2)))
    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("swu_decks.db")
        context.deleteDatabase("swu_collection.db")
        inventory = CollectionDatabase(context)
        store = DeckStore(context)
    }
    @After fun cleanup() {
        store.close(); inventory.close()
        context.deleteDatabase("swu_decks.db"); context.deleteDatabase("swu_collection.db")
    }
    @Test fun editExportReopenDeletePreservesInventoryAndList() {
        inventory.addOrIncrement("Éclairs", "SOR", "100", "Foil")
        inventory.addOrIncrement("Éclairs", "SOR", "100", "Normale")
        val listId = inventory.createCardList("Sélection été")
        inventory.putListCard(listId, CatalogCard("SOR", "100", "Éclairs", "Évènement", "Rare"), 3)
        val original = inventory.allCards()
        val lists = inventory.cardLists()
        val id = store.save("L’été d’Élodie", "Premier", deck)
        val imported = DeckInterchange.importJson(DeckInterchange.exportJson(store.all().single().deck))
        val edited = DeckEditor.move(imported, "SOR_100", DeckEditor.Zone.SIDEBOARD, 1)
        store.save("L’été d’Élodie — modifié", "Premier", edited, id)
        store.close(); store = DeckStore(context)
        assertEquals(1, store.all().size)
        assertEquals("L’été d’Élodie — modifié", store.all().single().name)
        assertEquals(edited, store.all().single().deck)
        assertTrue(store.delete(id))
        assertTrue(store.all().isEmpty())
        assertEquals(original, inventory.allCards())
        assertEquals(lists, inventory.cardLists())
    }
    @Test fun failedSaveIsVisibleAndKeepsPreviousDeck() {
        val id = store.save("Avant", "Premier", deck)
        store.writableDatabase.execSQL("CREATE TRIGGER reject_deck BEFORE INSERT ON decks BEGIN SELECT RAISE(ABORT, 'test'); END")
        assertTrue(runCatching { store.save("Après", "Premier", deck.copy(deck = emptyList()), id) }.isFailure)
        assertEquals("Avant", store.all().single().name)
        assertEquals(deck, store.all().single().deck)
    }
    @Test fun quantitiesMustBeIntegersAndAliasesAreSummed() {
        val valid = """{"leader":{"id":"sor_1"},"base":{"id":"SOR-24"},"deck":[{"id":"SOR-100","count":2},{"id":"sor_100","count":1}],"sideboard":[]}"""
        val parsed = DeckInterchange.importJson(valid)
        assertEquals(3, DeckEditor.quantity(parsed, "SOR_100", DeckEditor.Zone.MAIN))
        assertTrue(DeckEditor.missingCards(parsed, mapOf("SOR_100" to 1, "sor-100" to 2)).isEmpty())
        listOf("1.5", "-1", "100", "0", "\"2\"", "true").forEach { invalid ->
            try { DeckInterchange.importJson(valid.replace("\"count\":2", "\"count\":$invalid")); fail("Accepted $invalid") }
            catch (_: IllegalArgumentException) { }
        }
        assertEquals(parsed, DeckInterchange.importJson(DeckInterchange.exportJson(parsed)))
    }
}
