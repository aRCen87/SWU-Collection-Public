package fr.swucollection.app
import org.junit.Assert.*
import org.junit.Test
class MessengerPrivacyTest {
 @Test fun deletionHidesOnlyExistingHistoryAndNewIncomingMessageRestoresThread(){
  val state=MessengerThreadState(hiddenBefore=100)
  assertFalse(messengerVisible(PlayerConversation("t",listOf("a","b"),100,"b"),state))
  assertTrue(messengerVisible(PlayerConversation("t",listOf("a","b"),101,"b"),state))
  assertTrue(messengerVisible(PlayerConversation("t",listOf("a","b"),100,"b"),MessengerThreadState()))
 }
 @Test fun aCrashedAppNeverLeavesAFriendPermanentlyOnline(){
  val p=PlayerPresence(true,100_000)
  assertEquals("En ligne",p.label(110_000));assertNotEquals("En ligne",p.label(200_000))
  assertEquals("Statut masqué",PlayerPresence().label(200_000))
 }
 @Test fun cardSharingKeepsExactPrintAndLanguageWithoutIncludingCollectionStock(){
  val card=SharedPlayerCard("SOR","005","FR","Hyperspace foil")
  assertEquals(card,SharedPlayerCard.parse(card.json()))
  assertFalse(card.json().contains("quantity"))
  assertTrue(runCatching{SharedPlayerCard.parse("{\"set\":\"https://tracker\",\"number\":\"5\",\"language\":\"FR\",\"variant\":\"Foil\"}")}.isFailure)
 }
}
