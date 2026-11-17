package fr.swucollection.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w600dp-h900dp")
class ListConsultationUiTest {
    @get:Rule val ui = createComposeRule()
    @Test fun incompleteListOpensForConsultationAndEditingIsOptional() {
        var changes = 0
        var preview: CatalogCard? = null
        val card = CatalogCard("SOR", "095", "Carte test", "Unité", "Rare")
        val list = CardList("partial", "Liste en cours", listOf(CardListEntry(card, 2)))
        ui.setContent { MaterialTheme { CardListsPanel(listOf(list), "partial", onOpen = {}, onCreate = {},
            onRename = { _, _ -> changes++ }, onDelete = { changes++ }, onQuantity = { _, _, _ -> changes++ },
            onPreview = { selected, _ -> preview = selected }, owned = emptyList(), catalog = listOf(card), details = CardDetails.empty(),
            rules = LegalityRules.DEFAULT, onFormat = { _, _ -> changes++ }) } }
        ui.onNodeWithText("Carte test").performClick()
        ui.runOnIdle { assertEquals(card, preview) }
        ui.onNodeWithText("Retirer").assertDoesNotExist()
        ui.runOnIdle { assertEquals(0, changes) }
        ui.onNodeWithText("Modifier la liste").performClick()
        ui.onNodeWithText("Retirer").assertExists()
        ui.onNodeWithContentDescription("Augmenter la quantité de Carte test").performClick()
        ui.runOnIdle { assertEquals(1, changes) }
        ui.onNodeWithText("Terminer les modifications").performClick()
        ui.onNodeWithText("Retirer").assertDoesNotExist()
    }
    @Test fun emptyListCanBeCompletedDirectlyWithTheInlineCardPicker() {
        val card = CatalogCard("SOR", "095", "Carte test", "Unité", "Rare")
        var selected: CatalogCard? = null
        var quantity = 0
        ui.setContent { MaterialTheme { CardListsPanel(listOf(CardList("empty", "À compléter", emptyList())), "empty",
            onOpen = {}, onCreate = {}, onRename = { _, _ -> }, onDelete = {},
            onQuantity = { _, added, count -> selected = added; quantity = count }, onPreview = { _, _ -> },
            owned = emptyList(), catalog = listOf(card), details = CardDetails.empty(), rules = LegalityRules.DEFAULT,
            onFormat = { _, _ -> }) } }
        ui.onNodeWithText("Modifier la liste").performClick()
        ui.onNodeWithText("Ajouter des cartes").performClick()
        ui.onNodeWithTag("deck_picker_card_SOR-095").performClick()
        ui.onNodeWithTag("deck_gallery_title").assertTextEquals("Carte test")
        ui.runOnIdle { assertNull(selected); assertEquals(0, quantity) }
        ui.onNodeWithContentDescription("Fermer la carte").performClick()
        ui.onNodeWithTag("deck_picker_add_SOR-095").performClick()
        ui.runOnIdle { assertEquals(card, selected); assertEquals(1, quantity) }
        ui.onNodeWithText("Terminer").performClick()
        ui.onNodeWithText("Terminer les modifications").assertExists()
    }

}
