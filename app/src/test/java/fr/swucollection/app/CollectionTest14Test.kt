package fr.swucollection.app

import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.json.JSONObject

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE)
class CollectionTest14Test {
    private val card = CatalogCard("SOR","030","Carte","Unité","Commune", "https://example.test/card_SWH_01FR_030.png")
    private fun fresh(): CollectionDatabase {
        val context = RuntimeEnvironment.getApplication() as Context
        context.deleteDatabase("swu_collection.db")
        return CollectionDatabase(context)
    }
    @Test fun exactGoalCountsOnlyTheRequestedLanguageAndFinishAndKeepsLeaderAtOne() {
        val stock = listOf(CollectionCard(1,"Carte","SOR","030","Normale",2,"FR"), CollectionCard(2,"Carte","SOR","030","Normale",8,"EN"), CollectionCard(3,"Carte","SOR","030","Foil",7,"FR"))
        val rows = printGoalRows(listOf(card, card.copy(number="001",type="Leader")), stock, true, PrintGoal("Normale","FR"))
        assertEquals(2, rows.first().owned); assertEquals(1, rows.first().missing); assertEquals(1, rows.last().target)
        assertEquals(10, printGoalRows(listOf(card),stock,true,PrintGoal("Normale")).single().owned)
    }
    @Test fun bulkMergeIsAtomicKeepsTotalCopiesAndCanBeUndone() {
        fresh().use { db ->
            db.addOrIncrement("Carte","SOR","030","Normale","FR")
            repeat(2) { db.addOrIncrement("Carte","SOR","030","Normale","EN") }
            val before = db.allCards()
            db.bulkCorrect(before.map { it.id }.toSet(),listOf(card),"FR",null)
            assertEquals(1,db.allCards().size);assertEquals(3,db.allCards().single().quantity)
            assertTrue(db.history().first().label.startsWith("Modification par lot"))
            db.undoLastChange(); assertEquals(before, db.allCards())
            val dump=db.exportJson()
            assertThrows(IllegalArgumentException::class.java) { db.bulkCorrect(before.map { it.id }.toSet(),listOf(card),null,"Showcase") }
            assertEquals(dump,db.exportJson())
        }
    }
    @Test fun bulkCannotRelabelAnOutstandingLoanAndPrintGoalsSurviveBackupRestore() {
        fresh().use { db ->
            db.addOrIncrement("Carte","SOR","030","Normale","FR")
            val row=db.allCards().single()
            db.putTool("loans",LoanBook().add(row,"Ami",1,0,100).json())
            assertThrows(IllegalArgumentException::class.java) {db.bulkCorrect(setOf(row.id),listOf(card),"EN",null)}
            db.setPrintGoal("SOR",PrintGoal("Foil","FR"))
            val backup=db.exportJson();db.importJson(backup,true)
            assertEquals(PrintGoal("Foil","FR"),db.printGoals()["SOR"]);assertEquals(1,db.allCards().single().quantity)
            val invalid=JSONObject(backup);invalid.getJSONObject("tools").getJSONObject("printGoals").getJSONObject("SOR").put("language","BAD")
            assertThrows(IllegalArgumentException::class.java) {db.importJson(invalid.toString(),true)}
            assertEquals(backup,db.exportJson())
        }
    }
    @Test fun pendingVisualSuggestionIsNeverCommittedAndCorrectionCanChangeIdentityWithoutGhostCopies() {
        val context=RuntimeEnvironment.getApplication() as Context
        context.getSharedPreferences("scan_batch",0).edit().clear().commit()
        val batch=ScanBatchStore(context);batch.uncertain(card)
        assertFalse(batch.entries.single().resolved)
        val reloaded=ScanBatchStore(context);assertTrue(reloaded.entries.single().needsIdentity)
        fresh().use { db ->
            assertThrows(IllegalArgumentException::class.java) {db.commitScanBatch(batch.id,batch.entries)}
            assertTrue(db.allCards().isEmpty())
            val correct=card.copy(number="031",name="Bonne carte")
            reloaded.replace(reloaded.entries.single(),correct,"EN","Normale",2)
            assertTrue(reloaded.entries.single().resolved)
            db.commitScanBatch(reloaded.id,reloaded.entries);db.commitScanBatch(reloaded.id,reloaded.entries)
            assertEquals("031",db.allCards().single().number);assertEquals(2,db.allCards().single().quantity)
        }
    }
    @Test fun cityNormalizationAndQualityHintsStayDeterministicAndAdvisory() {
        assertEquals("angouleme",PlayerDiscovery.cityKey(" Angoulême "))
        assertEquals(PlayerDiscovery.cityKey("Ruelle-sur-Touvre"),PlayerDiscovery.cityKey("Ruelle sur Touvre"))
        assertNotNull(ScanImageQuality.advice(IntArray(100) {20}))
        assertTrue(ScanImageQuality.advice(IntArray(100) {255})!!.contains("éventuel reflet"))
        assertNull(ScanImageQuality.advice(IntArray(100) {if(it%2==0) 60 else 190}))
        assertThrows(IllegalArgumentException::class.java) { PlayerDiscovery.validate(PlayerProfile("a","Joueur",formats=listOf("Hacked"))) }
    }
    @Test fun privateListAdditionPreservesInventoryAndExistingQuantities() {
        fresh().use { db ->
            db.addOrIncrement("Carte","SOR","030","Normale","FR")
            val list=db.createCardList("Ma liste");db.putListCard(list,card,3)
            val before=db.allCards();db.bulkList(before.map {it.id}.toSet(),list,listOf(card))
            assertEquals(before,db.allCards());assertEquals(3,db.cardLists().single().entries.single().quantity)
        }
    }
}
