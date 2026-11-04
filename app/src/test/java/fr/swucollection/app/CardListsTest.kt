package fr.swucollection.app

import android.content.Context
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
class CardListsTest {
    private lateinit var context: Context
    private lateinit var db: CollectionDatabase
    private val card = CatalogCard("SOR", "138", "Éclairs de Force", "Événement", "Rare")
    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("swu_collection.db")
        db = CollectionDatabase(context)
        db.addOrIncrement(card.name, card.setCode, card.number, "Foil")
    }
    @After fun cleanup() { db.close(); context.deleteDatabase("swu_collection.db") }

    @Test fun migrationFromVersionFourPreservesCollectionAndCatalogue() {
        db.replaceCatalog(listOf(CatalogSet("SOR", "Étincelle", "", 1)), listOf(card))
        val sqlite = db.writableDatabase
        sqlite.execSQL("DROP TABLE card_list_entries")
        sqlite.execSQL("DROP TABLE card_lists")
        sqlite.version = 4
        db.close()
        db = CollectionDatabase(context)
        assertTrue(db.cardLists().isEmpty())
        assertEquals(1, db.allCards().single().quantity)
        assertEquals("Foil", db.allCards().single().variant)
        assertEquals(card, db.catalogCardsForSet("SOR").single())
        db.createCardList("Après migration")
    }

    @Test fun independentListsSurviveReopenAndCatalogueReplacement() {
        val first = db.createCardList("Deck été")
        val second = db.createCardList("À chercher")
        db.putListCard(first, card, 3)
        db.putListCard(second, card)
        db.replaceCatalog(emptyList(), emptyList())
        db.close(); db = CollectionDatabase(context)
        assertEquals(3, db.cardLists().first { it.id == first }.entries.single().quantity)
        db.renameCardList(first, "Deck préféré")
        db.deleteCardList(second)
        assertEquals("Deck préféré", db.cardLists().single().name)
        assertEquals(1, db.allCards().single().quantity)
        db.putListCard(first, card, 0)
        assertTrue(db.cardLists().single().entries.isEmpty())
    }

    @Test fun backupRestoreIsExactRepeatableAndExternalImportDoesNotDuplicateLists() {
        val id = db.createCardList("Deck préféré")
        db.putListCard(id, card, 3)
        val before = db.cardLists()
        UpdateBackups.create(context, db)
        db.putListCard(id, card, 1)
        db.createCardList("Temporaire")
        UpdateBackups.restore(context, db)
        assertEquals(before, db.cardLists())
        UpdateBackups.restore(context, db)
        assertEquals(before, db.cardLists())
        db.importJson(db.exportJson())
        assertEquals(before, db.cardLists())
    }

    @Test fun invalidListCannotEraseExistingCollectionOrLists() {
        val id = db.createCardList("Échange")
        db.putListCard(id, card, 2)
        val lists = db.cardLists()
        val cards = db.allCards()
        val root = JSONObject(db.exportJson())
        root.getJSONArray("lists").getJSONObject(0).getJSONArray("entries").getJSONObject(0).put("quantity", 0)
        try { db.importJson(root.toString(), replace = true); fail("Invalid list accepted") }
        catch (_: IllegalArgumentException) { }
        assertEquals(lists, db.cardLists())
        assertEquals(cards, db.allCards())
    }

    @Test fun legacyBackupMergesWithoutRemovingLists() {
        val id = db.createCardList("Anciennes cartes")
        db.putListCard(id, card)
        val legacy = JSONObject(db.exportJson()).apply { remove("lists"); put("version", 1) }
        db.importJson(legacy.toString())
        assertEquals(id, db.cardLists().single().id)
    }

    @Test fun backFromDeckReturnsToListOverviewThenHome() {
        val navigation = AppNavigation()
        navigation.navigateTo("Mes listes")
        navigation.navigateTo("Mes listes", "deck-id")
        assertEquals("deck-id", navigation.current.setCode)
        navigation.back()
        assertEquals(AppDestination("Mes listes"), navigation.current)
        navigation.back()
        assertEquals(AppDestination(), navigation.current)
    }
}
