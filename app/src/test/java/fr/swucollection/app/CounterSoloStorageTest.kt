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
class CounterSoloStorageTest {
    @Test fun soloModeBothDesignsAndHealthSurviveRestoreAndNewMatchKeepsTheSelectedMode() {
        val context=RuntimeEnvironment.getApplication() as Context
        context.deleteDatabase("swu_collection.db")
        val db=CollectionDatabase(context)
        try {
            val store=MatchCounterStore(db)
            val session=CounterSession(frame=CounterFrame().player(0) {it.copy(damage=8,style=CounterStyle.JEDI)}.player(1) {it.copy(damage=15,style=CounterStyle.ICE)},playerCount=1)
            store.save(session)
            val raw=db.exportJson();db.importJson(raw,true)
            assertEquals(session,store.read())
            val bad=JSONObject(raw);bad.getJSONObject("tools").getJSONObject("counter").put("playerCount",3)
            assertThrows(IllegalArgumentException::class.java) {db.importJson(bad.toString(),true)}
            assertEquals(session,store.read())
            val next=store.finish(session,100,MatchReport(result="win"))
            assertEquals(1,next.playerCount);assertEquals(1,store.history().first().getInt("playerCount"))
            assertEquals(CounterStyle.ICE,next.frame.players[1].style)
            assertThrows(IllegalArgumentException::class.java) {store.finish(next,200,MatchReport(player=1))}
        } finally {db.close()}
    }
}
