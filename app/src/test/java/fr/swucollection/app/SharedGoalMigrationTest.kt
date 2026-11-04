package fr.swucollection.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class SharedGoalMigrationTest {
    @Test fun upgradeFromSevenKeepsCardsListsHistoryAndGoalsAndBacksUpNewPreference() {
        val context: Context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("swu_collection.db")
        var db = CollectionDatabase(context)
        val card = CatalogCard("JTL","501","Éclaireur été","Unité","Commune")
        try {
            db.addOrIncrement(card.name,card.setCode,card.number,"Hyperspace Foil")
            db.addOrIncrement(card.name,card.setCode,card.number,"Hyperspace Foil")
            val list = db.createCardList("Deck préféré"); db.putListCard(list,card,3)
            db.setGoal("JTL",true)
            val before = db.allCards(); val lists = db.cardLists(); val history = db.history()
            db.writableDatabase.execSQL("DROP TABLE collection_goals")
            db.writableDatabase.execSQL("CREATE TABLE collection_goals (set_code TEXT PRIMARY KEY,playset INTEGER NOT NULL)")
            db.writableDatabase.execSQL("INSERT INTO collection_goals VALUES ('JTL',1)")
            db.writableDatabase.version = 7
            db.close(); db = CollectionDatabase(context)
            assertEquals(before,db.allCards()); assertEquals(lists,db.cardLists()); assertEquals(history,db.history())
            assertEquals(true,db.goals()["JTL"]); assertEquals(false,db.goalVariants()["JTL"])
            db.setGoal("JTL",true,true)
            db.setGoal("JTL",false)
            assertEquals(true,db.goalVariants()["JTL"])
            val backup = db.exportJson(); validateBackup(backup)
            db.importJson(backup,true); db.importJson(backup,true)
            assertEquals(before.map { it.copy(id=0) },db.allCards().map { it.copy(id=0) })
            assertEquals(lists,db.cardLists()); assertEquals(true,db.goalVariants()["JTL"])
        } finally { db.close(); context.deleteDatabase("swu_collection.db") }
    }
}
