package fr.swucollection.app

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class MatchAndLoansTest {
    @Test fun playersHaveIndependentDesignsAndLifeNeverExceedsTheirBase() {
        val frame=CounterFrame().player(0) {it.copy(maximum=25,style=CounterStyle.JEDI)}.player(1) {it.copy(maximum=40,style=CounterStyle.ICE)}
        assertEquals(0,frame.player(0) {it.hit(Int.MAX_VALUE)}.players[0].life)
        assertEquals(25,frame.player(0) {it.hit(Int.MIN_VALUE)}.players[0].life)
        assertEquals(CounterStyle.ICE,frame.players[1].style)
        assertEquals(40,frame.players[1].life)
        assertEquals(frame,CounterFrame.read(frame.json()))
    }
    @Test fun undoRestoresInitiativeScoresAndHealthWithoutRewindingTheTimer() {
        val start=CounterSession(since=1000)
        val damaged=start.change(start.frame.player(0) {it.hit(12)},"Dégâts")
        val initiative=damaged.change(damaged.frame.copy(initiative=1),"Initiative")
        val won=initiative.change(initiative.frame.won(1),"Manche")
        assertEquals(listOf(0,1),won.frame.scores);assertEquals(30,won.frame.players[0].life)
        assertEquals(initiative.frame,won.undoLast().frame)
        assertEquals(9000L,won.undoLast().duration(10000))
        assertEquals(start.frame,won.undoLast().undoLast().undoLast().frame)
        assertEquals(won,CounterSession.read(won.json()))
    }
    @Test fun timerPausesAndAClockRollbackNeverCreatesNegativeTime() {
        val paused=CounterSession().toggleTimer(1000).toggleTimer(6000)
        assertEquals(5000L,paused.duration(100000));assertEquals(0L,CounterSession(since=10000).duration(1000))
        assertEquals(6000L,paused.toggleTimer(100000).duration(101000))
    }
    @Test fun loansReserveExactLanguageAndFinishAndReturnsNeverIncreaseCollectionQuantity() {
        val row=CollectionCard(1,"Carte","SOR","001","Foil",5,"FR")
        val book=LoanBook().add(row,"Ami",3,0,1000)
        assertEquals(3,book.reserved()[loanKey(row)])
        assertNull(book.reserved()[loanKey(row.copy(language="EN"))])
        assertNull(book.reserved()[loanKey(row.copy(variant="Normale"))])
        assertThrows(IllegalArgumentException::class.java) {book.add(row,"Autre",3,0,1000)}
        val returned=book.returned(book.rows.first().id,2)
        assertEquals(1,returned.rows.first().outstanding);assertEquals(5,row.quantity)
        assertThrows(IllegalArgumentException::class.java) {returned.returned(returned.rows.first().id,2)}
        assertEquals(returned,LoanBook.read(returned.json()))
        val invalid=book.json();invalid.getJSONArray("items").getJSONObject(0).put("quantity",1.5)
        assertThrows(IllegalArgumentException::class.java) {LoanBook.read(invalid)}
    }
    @Test fun loanedCopiesAreNeverOfferedAndStillCountAsOwnedForSearches() {
        val card=CatalogCard("SOR","030","Carte","Unité","Commune")
        val owned=listOf(CollectionCard(1,"Carte","SOR","030","Normale",5,"FR"))
        val before=TradeProfile.build(listOf(card),owned,mapOf("SOR" to true),CardDetails.empty())
        val after=TradeProfile.build(listOf(card),owned,mapOf("SOR" to true),CardDetails.empty(),reserved=mapOf(loanKey(owned.single()) to 3))
        assertTrue(before.offers.isNotEmpty());assertTrue(after.offers.isEmpty())
        assertEquals(before.needs,after.needs);assertEquals(5,owned.single().quantity)
    }
    @Test fun deckImageIncludesBothIdentitiesAndTheReserveWithoutRepeatingCopies() {
        val deck=PortableDeck("SOR-001","SOR-020",listOf(DeckEntry("SOR-030",3)),listOf(DeckEntry("SOR-031",2)))
        val sections=DeckImagePlan.sections(deck)
        assertEquals(listOf(2,1,1),sections.map {it.entries.size})
        assertEquals(3,sections[1].entries.single().count)
        assertTrue(DeckImagePlan.height(sections)<8000)
        assertThrows(IllegalArgumentException::class.java) {DeckImagePlan.sections(deck.copy(deck=List(121) {DeckEntry("SOR-030",1)}))}
    }
    @Test fun searchAlertsDeduplicateMatchingQuantitiesAndIgnoreEmptyLists() {
        val item=TradeItem("SOR","001","Normale","Carte","family",1,language="FR")
        val matches=listOf(TradeMatch(item,item,1))
        val hash=SearchCardAlertPolicy.fingerprint(matches)
        assertTrue(SearchCardAlertPolicy.changed(null,hash,true))
        assertFalse(SearchCardAlertPolicy.changed(hash,hash,true))
        assertFalse(SearchCardAlertPolicy.changed(null,hash,false))
    }
}
