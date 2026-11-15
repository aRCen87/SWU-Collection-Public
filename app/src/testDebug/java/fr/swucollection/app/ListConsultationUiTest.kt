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
        val card = CatalogCard("SOR", "095", "Carte test", "Unité", "Rare")
        val list = CardList("partial", "Liste en cours", listOf(CardListEntry(card, 2)))
        ui.setContent { MaterialTheme { CardListsPanel(listOf(list), "partial", onOpen = {}, onCreate = {},
            onRename = { _, _ -> changes++ }, onDelete = { changes++ }, onQuantity = { _, _, _ -> changes++ },
            onPreview = { _, _ -> }, owned = emptyList(), catalog = listOf(card), details = CardDetails.empty(),
            rules = LegalityRules.DEFAULT, onFormat = { _, _ -> changes++ }) } }
        ui.onNodeWithText("Carte test").assertExists()
        ui.onNodeWithText("Retirer").assertDoesNotExist()
        ui.runOnIdle { assertEquals(0, changes) }
        ui.onNodeWithText("Modifier la liste").performClick()
        ui.onNodeWithText("Retirer").assertExists()
        ui.onNodeWithContentDescription("Augmenter la quantité de Carte test").performClick()
        ui.runOnIdle { assertEquals(1, changes) }
        ui.onNodeWithText("Terminer les modifications").performClick()
        ui.onNodeWithText("Retirer").assertDoesNotExist()
    }
}
