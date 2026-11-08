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
class OfficialUpgradeTest {
    @Test fun officialVersionElevenKeepsCollectionLanguagesPrintingsDecksListsAndHistory() {
        val context: Context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("swu_collection.db")
        context.deleteDatabase("swu_decks.db")
        var db = CollectionDatabase(context)
        try {
            val card = CatalogCard("SOR", "236", "R2-D2", "Unité", "Spéciale")
            repeat(3) { db.addOrIncrement(card.name, card.setCode, card.number, "Foil", "FR") }
            repeat(2) { db.addOrIncrement(card.name, card.setCode, card.number, "Normale", "EN") }
            val list = db.createCardList("Favoris conservés")
            db.putListCard(list, card, 2)
            db.setGoal("SOR", true, true)
            DeckStore(context).use { it.save("Deck conservé", "Premier", PortableDeck("SOR-005", "SOR-024", emptyList(), emptyList())) }
            val before = db.allCards()
            val lists = db.cardLists()
            val history = db.history()
            // Official 0.8.19 uses schema 11; schema 12 adds only app_tools.
            db.writableDatabase.execSQL("DROP TABLE app_tools")
            db.writableDatabase.version = 11
            db.close()
            db = CollectionDatabase(context)
            assertEquals(12, db.readableDatabase.version)
            assertEquals(before, db.allCards())
            assertEquals(5, db.allCards().sumOf { it.quantity })
            assertEquals(lists, db.cardLists())
            assertEquals(history, db.history())
            assertEquals(true, db.goals()["SOR"])
            assertEquals(true, db.goalVariants()["SOR"])
            DeckStore(context).use { assertEquals("Deck conservé", it.all().single().name) }
            validateBackup(db.exportJson())
        } finally {
            db.close()
            context.deleteDatabase("swu_collection.db")
            context.deleteDatabase("swu_decks.db")
        }
    }
}
