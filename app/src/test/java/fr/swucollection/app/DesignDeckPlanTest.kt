package fr.swucollection.app
import org.junit.Test
import org.junit.Assert.*

class DesignDeckPlanTest {
 @Test fun comparisonTracksIdentityAndReserveWithoutCancellingZoneTransfers(){
  val old=PortableDeck("SOR-001","SOR-002",listOf(DeckEntry("SOR-003",3)),emptyList())
  val next=old.copy(leader="SOR-004",deck=listOf(DeckEntry("SOR-003",2)),sideboard=listOf(DeckEntry("SOR-003",1)))
  val changes=DesignDeckPlan.changes(old,next)
  assertEquals(4,changes.size)
  assertTrue(changes.contains(DeckChange("Leader","SOR-001",1,0)))
  assertTrue(changes.contains(DeckChange("Réserve","SOR-003",0,1)))
  assertTrue(changes.contains(DeckChange("Deck","SOR-003",3,2)))
  assertEquals(old,old.copy())
 }
 @Test fun rarityProgressCountsReferencesRatherThanCopiesAndIgnoresOtherSets(){
  val rows=listOf(CatalogCard("SOR","001","A","Unit","Rare"),CatalogCard("SOR","002","B","Unit","Rare"))
  assertEquals(listOf(RarityProgress("Rare",1,2)),rarityProgress(rows,mapOf(MarketPricing.key("SOR","001") to 9,MarketPricing.key("SHD","001") to 1)))
 }
 @Test fun unknownReferencesRemainVisibleOnTheTable(){
  assertEquals("Références à vérifier",DesignDeckPlan.group(DeckEntry("X-001",1),emptyMap(),CardDetails.empty(),"Type"))
 }
}
