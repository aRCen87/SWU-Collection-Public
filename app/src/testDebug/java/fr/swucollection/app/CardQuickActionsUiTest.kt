package fr.swucollection.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp")
class CardQuickActionsUiTest {
    @get:Rule val ui = createComposeRule()
    private val card = CatalogCard("SOR", "095", "Carte test", "Unité", "Rare")

    @Test fun longPressOpensMenuWithoutChangingDeckAndRemovalNeedsConfirmation() {
        var result = 2
        ui.setContent { MaterialTheme {
            var quantity by remember { mutableIntStateOf(2) }
            DeckCardTile(card, true, quantity, 0, 0, false, false, quantity < 3,
                onPreview = {}, onChoose = {}, onQuantity = { quantity = it; result = it })
        } }
        ui.onNodeWithContentDescription("Agrandir Carte test").performTouchInput { longClick() }
        ui.onNodeWithText("Retirer la carte").assertIsDisplayed().performClick()
        ui.runOnIdle { assertEquals(2, result) }
        ui.onNodeWithText("Annuler").performClick()
        ui.runOnIdle { assertEquals(2, result) }
        ui.onNodeWithText("Retirer la carte").performClick()
        ui.onNodeWithText("Retirer", useUnmergedTree = true).performClick()
        ui.runOnIdle { assertEquals(0, result) }
    }

    @Test fun destinationChoiceDoesNotMutateAnythingUntilChosen() {
        var actions = 0
        ui.setContent { MaterialTheme {
            CardDestinationDialog(card, {}, { actions++ }, { actions++ }, { actions++ })
        } }
        ui.runOnIdle { assertEquals(0, actions) }
        ui.onNodeWithText("Une liste").performClick()
        ui.runOnIdle { assertEquals(1, actions) }
    }
}
