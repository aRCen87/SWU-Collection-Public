package fr.swucollection.app

import org.junit.Assert.*
import org.junit.Test

class CounterSoloTest {
    @Test fun soloModePersistsAndChangingViewsNeverDropsEitherPlayersHealthOrDesign() {
        val frame=CounterFrame().player(0) { it.copy(damage=7,style=CounterStyle.JEDI) }.player(1) { it.copy(damage=12,style=CounterStyle.ICE) }
        val two=CounterSession(frame=frame)
        val solo=two.withPlayerCount(1)
        assertEquals(frame,solo.frame);assertEquals(1,solo.playerCount)
        assertEquals(solo,CounterSession.read(solo.json()))
        assertEquals(two,solo.withPlayerCount(2))
        assertThrows(IllegalArgumentException::class.java) { two.withPlayerCount(3) }
    }
    @Test fun olderSavedCountersRemainTwoPlayerAndInvalidModesAreRejected() {
        val old=CounterSession().json().apply { remove("playerCount") }
        assertEquals(2,CounterSession.read(old).playerCount)
        assertThrows(IllegalArgumentException::class.java) { CounterSession.read(CounterSession().json().put("playerCount",1.5)) }
        assertThrows(IllegalArgumentException::class.java) { CounterSession.read(CounterSession().json().put("playerCount",0)) }
    }
    @Test fun soloRoundResetsOnlyMyLifeAndCanBeUndoneWithoutChangingHiddenOpponent() {
        val start=CounterSession(frame=CounterFrame().player(0) {it.copy(damage=10,shields=2)}.player(1) {it.copy(damage=13,style=CounterStyle.NEBULA)},playerCount=1)
        val next=start.nextSoloRound()
        assertEquals(30,next.frame.players[0].life);assertEquals(0,next.frame.players[0].shields)
        assertEquals(start.frame.players[1],next.frame.players[1]);assertEquals(start.frame.scores,next.frame.scores)
        assertEquals(start.frame,next.undoLast().frame);assertEquals(1,next.undoLast().playerCount)
        assertEquals(2,next.frame.round)
    }
    @Test fun soloReportDoesNotInventAWinFromAnUntrackedOpponentScore() {
        val solo=CounterSession(frame=CounterFrame(scores=listOf(9,0)),playerCount=1)
        assertEquals("draw",MatchReport.fromScores(solo).result)
        assertThrows(IllegalArgumentException::class.java) {MatchReport.fromScores(solo,1)}
    }
}
