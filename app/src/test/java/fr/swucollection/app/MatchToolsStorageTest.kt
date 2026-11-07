package fr.swucollection.app

import android.content.Context
import android.view.View
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE)
class MatchToolsStorageTest {
    @Test fun keepAwakeIsRestoredWhenTheCounterCloses() {
        val view=View(RuntimeEnvironment.getApplication())
        assertFalse(view.keepScreenOn)
        val lease=CounterAwake(view);assertTrue(view.keepScreenOn);lease.close();assertFalse(view.keepScreenOn)
        view.keepScreenOn=true;CounterAwake(view).close();assertTrue(view.keepScreenOn)
    }
    @Test fun upgradingFromElevenAndRestoringToolsPreserveCardsAndExactQuantities() {
        val context=RuntimeEnvironment.getApplication() as Context
        context.deleteDatabase("swu_collection.db")
        var db=CollectionDatabase(context)
        db.addOrIncrement("Carte","SOR","001","Foil","FR")
        val cards=db.allCards();db.writableDatabase.execSQL("DROP TABLE app_tools");db.writableDatabase.version=11;db.close()
        db=CollectionDatabase(context)
        assertEquals(12,db.readableDatabase.version);assertEquals(cards,db.allCards())
        val loan=LoanBook().add(cards.single(),"Joueur",1,0,1000);db.putTool("loans",loan.json())
        val session=CounterSession().change(CounterFrame().player(1) {it.hit(7)},"Dégâts")
        MatchCounterStore(db).save(session)
        val backup=db.exportJson();db.close();db=CollectionDatabase(context)
        assertEquals(23,MatchCounterStore(db).read().frame.players[1].life)
        db.importJson(backup,true)
        assertEquals(loan,db.loans());assertEquals(session,MatchCounterStore(db).read());assertEquals(1,db.allCards().single().quantity)
        val invalid=JSONObject(backup);invalid.getJSONObject("tools").getJSONObject("loans").getJSONArray("items").getJSONObject(0).put("returned",2)
        assertThrows(IllegalArgumentException::class.java) {db.importJson(invalid.toString(),true)}
        assertEquals(backup,db.exportJson())
        db.close();context.deleteDatabase("swu_collection.db")
    }
}
