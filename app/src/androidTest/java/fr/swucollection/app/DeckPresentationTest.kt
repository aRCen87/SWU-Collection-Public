package fr.swucollection.app

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import java.io.File

/** Exercises production dialogs with disposable local cards; no account or inventory writes. */
class DeckPresentationTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private val cards = listOf(
        CatalogCard("SOR", "100", "Alpha<Return> — Première carte", "Unité", "Rare"),
        CatalogCard("SOR", "101", "Beta — Deuxième carte", "Unité", "Rare"),
        CatalogCard("SOR", "102", "Gamma — Troisième carte", "Unité", "Rare"))
    private fun capture(tag: String, filename: String) {
        File(ui.activity.getExternalFilesDir(null), filename).outputStream().use {
            ui.onNodeWithTag(tag).captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    @Test fun overviewOpensAnOrderedZoomableReaderWithoutEditingTheDeck() {
        var modifications = 0
        ui.activityRule.scenario.onActivity { it.enableEdgeToEdge() }
        ui.setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = ListPalette.Gold)) {
                DeckConsultation(DeckDraft("Deck de test", PortableDeck("", "", cards.map { DeckEntry(DeckConstruction.reference(it), 2) }), false, false), cards, {}, {}, onChange = { modifications++ })
            }
        }
        val first = "deck_overview_card_SOR-100"
        ui.onNodeWithTag("deck_overview_grid").performScrollToNode(hasTestTag(first))
        val a = ui.onNodeWithTag(first).getUnclippedBoundsInRoot()
        val b = ui.onNodeWithTag("deck_overview_card_SOR-101").getUnclippedBoundsInRoot()
        val c = ui.onNodeWithTag("deck_overview_card_SOR-102").getUnclippedBoundsInRoot()
        assertEquals(a.top.value, b.top.value, 2f)
        assertEquals(a.top.value, c.top.value, 2f)
        assertTrue("Card thumbnails are too narrow", a.width.value >= 96f)
        ui.onNodeWithTag(first).performClick()
        ui.onNodeWithTag("deck_gallery_title").assertTextEquals("Alpha — Première carte")
        ui.onNodeWithTag("deck_gallery_previous").assertIsNotEnabled()
        ui.onNodeWithTag("deck_gallery_zoom_in").performClick()
        ui.onNodeWithTag("deck_gallery_zoom").assertTextEquals("150 %")
        ui.onNodeWithTag("deck_gallery_next").performClick()
        ui.onNodeWithTag("deck_gallery_title").assertTextEquals("Beta — Deuxième carte")
        ui.onNodeWithTag("deck_gallery_zoom").assertTextEquals("100 %")
        ui.onNodeWithTag("deck_gallery_next").performClick()
        ui.onNodeWithTag("deck_gallery_next").assertIsNotEnabled()
        ui.onNodeWithTag("deck_gallery_previous").performClick()
        ui.onNodeWithTag("deck_gallery_title").assertTextEquals("Beta — Deuxième carte")
        ui.onNodeWithContentDescription("Fermer la carte").performClick()
        ui.onNodeWithTag("deck_overview_grid").assertExists()
        assertEquals(0, modifications)
        capture("swu_deck_consultation_fullscreen", "home-test-deck-overview.png")
    }
    @Test fun pickerSeparatesReadingFromAddingAndKeepsSearchAvailable() {
        var additions = 0
        ui.setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = ListPalette.Gold)) {
                InlineCardPicker(cards, "Deck", {}, onChoose = { additions++ })
            }
        }
        ui.onNodeWithTag("deck_picker_grid").performScrollToNode(hasTestTag("deck_picker_card_SOR-100"))
        val image = ui.onNodeWithTag("deck_picker_card_SOR-100").getUnclippedBoundsInRoot()
        assertTrue("Picker artwork must be comfortably larger than overview thumbnails", image.width.value >= 140f)
        ui.onNodeWithTag("deck_picker_card_SOR-100").performClick()
        ui.onNodeWithTag("deck_gallery_next").performClick()
        ui.onNodeWithTag("deck_gallery_title").assertTextEquals("Beta — Deuxième carte")
        ui.onNodeWithContentDescription("Fermer la carte").performClick()
        assertEquals(0, additions)
        ui.onNodeWithTag("deck_picker_grid").performScrollToNode(hasTestTag("deck_picker_add_SOR-100"))
        ui.onNodeWithTag("deck_picker_add_SOR-100").performClick()
        assertEquals(1, additions)
        ui.onNodeWithText("+1 ajouté(s)").assertExists()
        ui.onNodeWithText("Rechercher une carte").performTextInput("Beta")
        ui.onNodeWithText("Rechercher une carte").performImeAction()
        ui.waitUntil(5000) { runCatching { ui.onNodeWithTag("deck_picker_card_SOR-101").fetchSemanticsNode().boundsInRoot.height > 0 }.getOrDefault(false) }
        ui.onNodeWithTag("deck_picker_card_SOR-100").assertDoesNotExist()
        ui.onNodeWithTag("deck_picker_grid").performScrollToNode(hasTestTag("deck_picker_card_SOR-101"))
        ui.onNodeWithTag("deck_picker_card_SOR-101").assertExists()
        ui.waitForIdle()
        capture("deck_card_picker", "home-test-deck-picker.png")
    }
    @Test fun aRejectedAddIsVisibleAndIsNotReportedAsSuccessful() {
        ui.setContent {
            MaterialTheme { InlineCardPicker(cards, "Deck", {}, onChoose = { throw IllegalArgumentException("Limite atteinte") }) }
        }
        ui.onNodeWithTag("deck_picker_grid").performScrollToNode(hasTestTag("deck_picker_add_SOR-100"))
        ui.onNodeWithTag("deck_picker_add_SOR-100").performClick()
        ui.onNodeWithText("Limite atteinte").assertExists()
        ui.onNodeWithText("+1 ajouté(s)").assertDoesNotExist()
        ui.onNodeWithTag("deck_card_picker").assertExists()
    }
}
