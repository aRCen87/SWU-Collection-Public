package fr.swucollection.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp")
class VisualDeckOverviewUiTest {
    @get:Rule val ui = createComposeRule()
    @Test fun fullArtworkOpensPreviewAndLongPressChangesOnlyAfterExplicitChoice() {
        val card = CatalogCard("SOR", "095", "Carte test", "Unité", "Rare")
        var result = 2
        var preview = false
        ui.setContent { MaterialTheme {
            var deck by remember { mutableStateOf(PortableDeck("SOR-001", "SOR-024", listOf(DeckEntry("SOR-095", 2)))) }
            DeckSelectionPane("Mon deck", deck, listOf(card), CardDetails.empty(), DeckEditor.Zone.MAIN, false, false,
                Modifier.fillMaxSize(), onZone = {}, onQuantity = { id, count, zone -> result = count; deck = DeckEditor.setQuantity(deck, id, count, zone) },
                onPreview = { _, _ -> preview = true }, onAdd = {})
        } }
        val artwork = ui.onNodeWithTag("swu_deck_full_card", useUnmergedTree = true)
        val bounds = artwork.fetchSemanticsNode().boundsInRoot
        assertEquals(5f / 7f, bounds.width / bounds.height, 0.02f)
        artwork.performClick()
        ui.runOnIdle { assertTrue(preview); assertEquals(2, result) }
        artwork.performTouchInput { longClick() }
        ui.runOnIdle { assertEquals(2, result) }
        ui.onNodeWithText("+1 exemplaire").performClick()
        ui.onNodeWithText("×3").assertExists()
        ui.runOnIdle { assertEquals(3, result) }
        ui.onNodeWithText("＋ Cartes").assertExists()
    }
}
