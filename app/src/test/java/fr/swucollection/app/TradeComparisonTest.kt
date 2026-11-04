package fr.swucollection.app

import org.junit.Assert.*
import org.junit.Test

class TradeComparisonTest {
    private fun item(n: String,q: Int=1,family: String="same",variant: String="Normale")=TradeItem("JTL",n,variant,"Carte été",family,q)
    @Test fun exactPrintsAreReservedAndEachCopyAllocatedOnlyOnce() {
        val giver=TradeProfile(listOf(item("054",2),item("788",1,variant="Hyperspace Foil")),emptyList())
        val receiver=TradeProfile(emptyList(),listOf(item("316",3),item("054",2)),true)
        val matches=TradeComparison.matching(giver,receiver)
        assertEquals(3,matches.sumOf {it.quantity});assertEquals(2,matches.filter {it.wanted.number=="054"}.sumOf {it.quantity})
        assertEquals(2,matches.filter {it.offered.number=="054"}.sumOf {it.quantity})
        assertTrue(TradeComparison.matching(giver,receiver.copy(acceptVariants=false)).all {it.offered.key==it.wanted.key})
        assertEquals(2,TradeComparison.matching(giver,receiver.copy(acceptVariants=false)).sumOf {it.quantity})
        assertTrue(TradeComparison.matching(TradeProfile(listOf(item("054",family="unknown:JTL:054")),emptyList()),TradeProfile(emptyList(),listOf(item("788",family="unknown:JTL:054")),true)).isEmpty())
    }
    @Test fun portableProfilesRoundTripAndRejectDuplicatesIncludingPadding() {
        val original=TradeProfile(listOf(item("054")),listOf(item("055",2)),true)
        assertEquals(original,TradeProfile.parse(original.json()))
        val duplicate=original.copy(offers=listOf(item("054"),item("54")))
        try{TradeProfile.parse(duplicate.json());fail("Duplicate accepted")}catch(_:IllegalArgumentException){}
        try{TradeProfile.parse(original.copy(needs=listOf(item("055",0))).json());fail("Zero accepted")}catch(_:IllegalArgumentException){}
    }
}
