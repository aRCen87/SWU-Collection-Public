package fr.swucollection.app

import android.content.Context
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE)
class CollectionImprovementsTest {
    private lateinit var context: Context
    private lateinit var db: CollectionDatabase
    private val card=CatalogCard("JTL","054","Été — carte","Unité","Rare","https://cdn.starwarsunlimited.com/card_04010054_FR.png")
    @Before fun before(){context=RuntimeEnvironment.getApplication();context.deleteDatabase("swu_collection.db");db=CollectionDatabase(context)}
    @After fun after(){db.close();context.deleteDatabase("swu_collection.db")}
    @Test fun sixToSevenPreservesStocksListsCatalogueAndBatchLedger() {
        db.addOrIncrement(card.name,card.setCode,card.number,"Hyperspace Foil")
        val id=db.createCardList("Équipe");db.putListCard(id,card,3)
        db.replaceCatalog(listOf(CatalogSet("JTL","Saut","",1)),listOf(card))
        db.commitScanBatch("already",listOf(ScanBatchEntry(card,"Normale",2)))
        for(table in listOf("collection_history","collection_goals","list_preferences"))db.writableDatabase.execSQL("DROP TABLE $table")
        db.writableDatabase.version=6;db.close();db=CollectionDatabase(context)
        assertEquals(3,db.allCards().sumOf {it.quantity});assertEquals(3,db.cardLists().single().entries.single().quantity)
        assertEquals(card,db.catalogCardsForSet("JTL").single());assertTrue(db.isScanBatchCommitted("already"));assertTrue(db.history().isEmpty())
        db.setGoal("JTL",false);assertEquals(false,db.goals()["JTL"])
    }
    @Test fun quantitiesDeletionAndBatchAreReversibleWithoutReplayingBatch() {
        db.addOrIncrement(card.name,card.setCode,card.number,"Foil")
        val original=db.allCards().single();db.changeQuantity(original.id,-1);assertTrue(db.allCards().isEmpty())
        db.undoLastChange();assertEquals(original,db.allCards().single())
        db.commitScanBatch("batch",listOf(ScanBatchEntry(card,"Normale",3),ScanBatchEntry(card.copy(number="055"),"Normale",2)))
        assertEquals(6,db.allCards().sumOf {it.quantity});db.undoLastChange();assertEquals(listOf(original),db.allCards())
        db.commitScanBatch("batch",listOf(ScanBatchEntry(card,"Normale",3)));assertEquals(listOf(original),db.allCards())
        db.undoLastChange();assertTrue(db.allCards().isEmpty())
    }
    @Test fun backupRoundTripsGoalsFormatsAndBlocksStaleUndo() {
        db.addOrIncrement(card.name,card.setCode,card.number,"Foil");db.setGoal("JTL",false)
        val id=db.createCardList("Deck été");db.putListCard(id,card,2);db.setListFormat(id,"Twin Suns")
        val backup=db.exportJson();db.setGoal("JTL",true);db.setListFormat(id,"Premier")
        db.importJson(backup,true);db.importJson(backup,true)
        assertEquals(false,db.goals()["JTL"]);assertEquals("Twin Suns",db.cardLists().single().format);assertEquals(1,db.allCards().single().quantity)
        try{db.undoLastChange();fail("Restore must be a history boundary")}catch(_:IllegalStateException){}
        db.addOrIncrement(card.name,card.setCode,card.number,"Foil");db.undoLastChange();assertEquals(1,db.allCards().single().quantity)
    }
    @Test fun staleUndoAndInvalidBackupNeverAlterCollection() {
        db.addOrIncrement(card.name,card.setCode,card.number,"Normale")
        db.writableDatabase.execSQL("UPDATE collection_cards SET quantity=9")
        try{db.undoLastChange();fail("Stale undo accepted")}catch(_:IllegalStateException){}
        assertEquals(9,db.allCards().single().quantity)
        val before=db.exportJson();val root=org.json.JSONObject(before).put("goals",org.json.JSONArray().put(org.json.JSONObject().put("set","JTL").put("playset","yes")))
        try{db.importJson(root.toString(),true);fail("Invalid goal accepted")}catch(_:IllegalArgumentException){}
        assertEquals(before,db.exportJson())
    }
}
