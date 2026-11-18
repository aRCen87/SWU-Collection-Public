package fr.swucollection.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Disposable production-component layouts; no Firebase and no collection writes. */
class Design35PresentationTest {
 @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
 private val cards=listOf(CatalogCard("SOR","100","Alpha","Unité","Rare"),CatalogCard("SHD","101","Beta","Événement","Commune"))
 @Composable private fun PreviewTheme(content:@Composable ()->Unit) {
  MaterialTheme(colorScheme=darkColorScheme(primary=PremiumGold,onPrimary=ListPalette.Background,
   secondaryContainer=ListPalette.Raised,onSecondaryContainer=ListPalette.Gold,
   surface=PremiumNavy,onSurface=ListPalette.Text,onBackground=ListPalette.Text)) {
   CompositionLocalProvider(LocalContentColor provides ListPalette.Text){content()}
  }
 }
 private fun capture(name:String){File(ui.activity.getExternalFilesDir(null),"home-test-design35-$name.png").outputStream().use{ui.onAllNodes(isRoot()).onLast().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}}
 @Test fun tableGroupingAndFixedDockDoNotChangeDeckQuantities(){
  val deck=PortableDeck("","",listOf(DeckEntry("SOR-100",2),DeckEntry("SHD-101",1)))
  var edits=0
  ui.setContent{PreviewTheme{Column(Modifier.fillMaxSize()){
   DeckSelectionPane("Maquette Mes decks",deck,cards,CardDetails.empty(),DeckEditor.Zone.MAIN,false,false,Modifier.weight(1f),{}, {_,_,_->edits++},{_,_->})
   DeckBuildDock(deck,cards,false,false){}
  }}}
  ui.onNodeWithText("Table · Type").performClick()
  ui.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Événements · 1"))
  ui.onNodeWithText("Événements · 1").assertIsDisplayed()
  ui.onNodeWithTag("design_deck_dock").assertIsDisplayed()
  assertEquals(0,edits);assertEquals(3,deck.deck.sumOf{it.count})
  capture("deck")
 }
 @Test fun silhouetteAndExtensionJumpPreserveReadingActions(){
  var preview=0;var actions=0
  ui.setContent{PreviewTheme{Surface(Modifier.fillMaxSize(),color=ListPalette.Background){
   UnifiedCollectionCatalog(cards,emptyList(),"Toutes les cartes","Toutes","Toutes","","Visuel",CardDetails.empty(),Modifier.fillMaxSize(),{_,_->preview++},{actions++})
  }}}
  ui.onNodeWithText("Silhouettes").performClick()
  ui.onAllNodesWithText("À trouver").onFirst().assertExists()
  ui.onAllNodesWithText("SHD").onFirst().performClick()
  ui.waitForIdle()
  assertEquals(0,preview);assertEquals(0,actions)
  capture("collection")
 }
 @Test fun comparisonFindsSecondCardWithoutAddingAnything(){
  ui.setContent{PreviewTheme{CompareCardsDialog(cards[0],cards,CardDetails.empty()) {}}}
  ui.onNodeWithText("Choisir la seconde carte").performTextInput("Beta")
  ui.onNodeWithText("Beta · SHD 101").performClick()
  ui.onNodeWithText("Alpha").assertExists();ui.onNode(hasText("Beta") and !hasSetTextAction()).assertExists()
  capture("compare")
 }
 @Test fun changeSummaryKeepsReserveTransfersVisible(){
  val before=PortableDeck("","",listOf(DeckEntry("SOR-100",2)))
  val after=before.copy(deck=listOf(DeckEntry("SOR-100",1)),sideboard=listOf(DeckEntry("SOR-100",1)))
  ui.setContent{PreviewTheme{DeckChangesDialog(before,after,cards){}}}
  ui.onNodeWithText("2 → 1").assertExists();ui.onNodeWithText("0 → 1").assertExists();ui.onNodeWithText("Réserve").assertExists()
  capture("changes")
 }
}
