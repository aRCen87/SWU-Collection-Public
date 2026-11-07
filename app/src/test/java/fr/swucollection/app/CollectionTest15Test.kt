package fr.swucollection.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE)
class CollectionTest15Test {
    private val a=CatalogCard("SOR","030","Unité A","Unité","Commune","https://example.test/card_0101030_FR.png")
    private val b=a.copy(number="031",name="Unité B",imageUrl="https://example.test/card_0101031_FR.png")
    private val leader=a.copy(number="001",name="Leader",type="Leader")
    private val base=a.copy(number="024",name="Base",type="Base")
    private val catalog=listOf(a,b,leader,base)
    private fun details(): CardDetails = CardDetails(catalog.associate { MarketPricing.key(it.setCode,it.number) to
        CardDetail(it.name,it.name,3,if(it==base) listOf("Aggression") else if(it==leader) listOf("Heroism") else listOf("Aggression"),listOf("Trooper"),"Ground") })
    private fun fresh(block: (CollectionDatabase)->Unit) {
        val c=RuntimeEnvironment.getApplication() as Context;c.deleteDatabase("swu_collection.db")
        CollectionDatabase(c).use(block)
    }
    private fun trade(offer: List<TradeSelection> = listOf(TradeSelection("SOR-030",1,"FR","Foil")),
        wanted: List<TradeSelection> = listOf(TradeSelection("SOR-031",1,"EN","Classique"))) = PlayerTrade("one",listOf("alice","bob"),"alice","accepted",CommunitySocial.encodeTrade(offer),CommunitySocial.encodeTrade(wanted),100)
    @Test fun naturalSearchUnderstandsCostAspectsAndAccents() {
        val q=NaturalCardSearch.parse("unités rouges à 3 ressources")
        assertEquals("",q.text);assertEquals("Aggression",q.aspect);assertEquals(3,q.cost);assertEquals("unite",q.type)
        val index=GlobalSearchIndex(catalog,emptyList(),emptyList(),details())
        assertTrue(q.matches(a,details().get(a),emptyList(),index));assertFalse(q.matches(leader,details().get(leader),emptyList(),index))
        val unknown=NaturalCardSearch.parse("le sabre de Luke")
        assertNull(unknown.language);assertTrue(unknown.text.contains("luke"))
    }
    @Test fun naturalSearchKeepsEnglishDoublesSeparateAndFindsMissingLegendaries() {
        val stock=listOf(CollectionCard(1,a.name,"SOR","030","Normale",1,"EN"),CollectionCard(2,a.name,"SOR","030","Foil",5,"FR"))
        val index=GlobalSearchIndex(catalog,stock,emptyList(),details())
        val q=NaturalCardSearch.parse("mes doubles anglais")
        assertEquals("",q.text);assertFalse(q.matches(a,details().get(a),stock,index))
        val double=stock.map {if(it.language=="EN")it.copy(quantity=2) else it}
        assertTrue(q.matches(a,details().get(a),double,GlobalSearchIndex(catalog,double,emptyList(),details())))
        val missing=NaturalCardSearch.parse("légendaires manquantes")
        assertTrue(missing.matches(b.copy(rarity="Légendaire"),details().get(b),stock,index))
        assertFalse(missing.matches(a.copy(rarity="Légendaire"),details().get(a),stock,index))
    }
    @Test fun alternativesUseAvailableStockAndRespectAspectsAndFamilyLimit() {
        val deck=PortableDeck("SOR-001","SOR-024",listOf(DeckEntry("SOR-030",2)),listOf(DeckEntry("SOR-031",2)))
        val stock=listOf(CollectionCard(1,b.name,"SOR","031","Normale",4,"EN"))
        val choices=DeckAlternatives.suggest(a,deck,stock,catalog,details(),LegalityRules.DEFAULT.copy(activeSets=setOf("SOR"),rotatedSets=emptySet()))
        assertEquals(1,choices.size);assertEquals(1,choices.single().available)
        val next=DeckAlternatives.replaceOne(deck,a,b,DeckEditor.Zone.MAIN)
        assertEquals(1,DeckEditor.quantity(next,"SOR-030",DeckEditor.Zone.MAIN));assertEquals(1,DeckEditor.quantity(next,"SOR-031",DeckEditor.Zone.MAIN))
        assertEquals(deck.sideboard,next.sideboard)
        val full=deck.copy(sideboard=listOf(DeckEntry("SOR-031",3)))
        assertTrue(DeckAlternatives.suggest(a,full,stock,catalog,details(),LegalityRules.DEFAULT.copy(activeSets=setOf("SOR"),rotatedSets=emptySet())).isEmpty())
        val badDetails=CardDetails(catalog.associate {MarketPricing.key(it.setCode,it.number) to details().get(it)!!.copy(aspects=if(it==b)listOf("Cunning") else details().get(it)!!.aspects)})
        assertTrue(DeckAlternatives.suggest(a,deck,stock,catalog,badDetails,LegalityRules.DEFAULT.copy(activeSets=setOf("SOR"),rotatedSets=emptySet())).isEmpty())
    }
    @Test fun confirmedTradeUpdatesExactPrintsOnceAndCanBeUndoneWithoutReplay() = fresh { db ->
        db.addOrIncrement(a.name,a.setCode,a.number,"Foil","FR");db.addOrIncrement(a.name,a.setCode,a.number,"Foil","FR")
        db.addOrIncrement(a.name,a.setCode,a.number,"Normale","FR")
        val before=db.allCards()
        assertTrue(db.applyConfirmedTrade("alice",trade(),catalog));assertFalse(db.applyConfirmedTrade("alice",trade(),catalog))
        assertEquals(1,db.allCards().single {it.number=="030" && it.variant=="Foil"}.quantity)
        assertEquals("EN",db.allCards().single {it.number=="031"}.language)
        db.undoLastChange();assertEquals(before,db.allCards());assertFalse(db.applyConfirmedTrade("alice",trade(),catalog))
    }
    @Test fun recipientGetsReverseDirectionAndUnacceptedTradesCannotApply() = fresh {db ->
        db.addOrIncrement(b.name,b.setCode,b.number,"Normale","EN")
        assertThrows(IllegalArgumentException::class.java) {db.applyConfirmedTrade("bob",trade().copy(status="pending"),catalog)}
        assertTrue(db.applyConfirmedTrade("bob",trade(),catalog));assertEquals("Foil",db.allCards().single().variant);assertEquals("FR",db.allCards().single().language)
    }
    @Test fun duplicatedOutgoingSelectionsCannotOverdrawStockAndFailureIsAtomic() = fresh {db ->
        db.addOrIncrement(a.name,a.setCode,a.number,"Foil","FR")
        val before=db.exportJson();val duplicated=trade(offer=listOf(TradeSelection("SOR-030",1,"FR","Foil"),TradeSelection("SOR-030",1,"FR","Foil")))
        assertThrows(IllegalArgumentException::class.java) {db.applyConfirmedTrade("alice",duplicated,catalog)}
        assertEquals(before,db.exportJson());assertFalse(db.tradeApplied("alice","one"))
    }
    @Test fun loansCannotBeGivenAwayAndFailedTradeHasNoHistoryOrReceipt() = fresh {db ->
        db.addOrIncrement(a.name,a.setCode,a.number,"Foil","FR")
        db.putTool("loans",LoanBook().add(db.allCards().single(),"Ami",1,0,100).json())
        val before=db.exportJson();val history=db.history()
        assertThrows(IllegalArgumentException::class.java) {db.applyConfirmedTrade("alice",trade(),catalog)}
        assertEquals(before,db.exportJson());assertEquals(history,db.history())
    }
    @Test fun receiptsGoalsAndMatchReportsSurviveRestoreAndOldMatchesRemainReadable() = fresh {db ->
        db.addOrIncrement(a.name,a.setCode,a.number,"Foil","FR");db.applyConfirmedTrade("alice",trade(),catalog)
        db.setPrintGoal("SOR",PrintGoal("Hyperspace","FR"));db.setGoal("SOR",true)
        val store=MatchCounterStore(db)
        val report=MatchReport(deckId="deck1",deckName="Mon deck",opponentLeader="Vador",result="win",note="Bonne sortie")
        store.finish(CounterSession(),100,report);store.finish(CounterSession(),200)
        val backup=db.exportJson();db.importJson(backup,true)
        assertTrue(db.tradeApplied("alice","one"));assertEquals(PrintGoal("Hyperspace","FR"),db.printGoals()["SOR"])
        assertEquals(2,store.history().size);assertEquals(MatchStats(1,1,0,0),matchStats(store.history()))
        assertEquals(report,MatchReport.read(store.history()[1].getJSONObject("report")))
        assertEquals(0,matchStats(store.history(),opponent="Luke").played)
    }
    @Test fun invalidMatchReportCannotOverwriteOngoingSession() = fresh {db ->
        val store=MatchCounterStore(db);val session=CounterSession(frame=CounterFrame(scores=listOf(2,1)));store.save(session)
        assertThrows(IllegalArgumentException::class.java) {store.finish(session,100,MatchReport(result="invented"))}
        assertEquals(session,store.read());assertTrue(store.history().isEmpty())
        assertEquals("win",MatchReport.fromScores(session).result);assertEquals("loss",MatchReport.fromScores(session,1).result)
    }
    @Test fun failedBackupReportRestoreLeavesInventoryAndReportsUntouched() = fresh {db ->
        db.addOrIncrement(a.name,a.setCode,a.number,"Normale","FR")
        MatchCounterStore(db).finish(CounterSession(),100,MatchReport(note="À conserver"))
        val before=db.exportJson();val broken=JSONObject(before)
        broken.getJSONObject("tools").getJSONObject("matches").getJSONArray("items").getJSONObject(0).getJSONObject("report").put("result","bad")
        assertThrows(IllegalArgumentException::class.java) {db.importJson(broken.toString(),true)}
        assertEquals(before,db.exportJson())
    }
    @Test fun mergedBackupKeepsAllTradeReceiptsAndOverflowRollsBackBothSides() = fresh {db ->
        val keyA=tradeReceiptKey("alice","a");val keyB=tradeReceiptKey("alice","b")
        db.putTool("tradeReceipts",JSONObject().put(keyA,100))
        val incoming=JSONObject(db.exportJson());incoming.getJSONObject("tools").put("tradeReceipts",JSONObject().put(keyB,200))
        db.importJson(incoming.toString(),false)
        assertTrue(db.tradeApplied("alice","a"));assertTrue(db.tradeApplied("alice","b"))
        db.addOrIncrement(a.name,a.setCode,a.number,"Foil","FR");db.addOrIncrement(b.name,b.setCode,b.number,"Normale","EN")
        db.changeQuantity(db.allCards().single {it.number=="031"}.id,9998)
        val before=db.exportJson();val history=db.history()
        assertThrows(IllegalArgumentException::class.java) {db.applyConfirmedTrade("alice",trade(),catalog)}
        assertEquals(before,db.exportJson());assertEquals(history,db.history());assertFalse(db.tradeApplied("alice","one"))
    }

}
