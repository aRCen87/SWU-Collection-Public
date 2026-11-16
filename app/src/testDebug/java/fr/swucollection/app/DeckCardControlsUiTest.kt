package fr.swucollection.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp")
class DeckCardControlsUiTest {
    @get:Rule val ui = createComposeRule()
    private val card = CatalogCard("SOR", "095", "Carte test", "Unité", "Rare")

    @Test fun addStaysBelowArtworkAndAboveDetailsDuringRepeatedTaps() {
        var quantity = 0
        var opened = false
        ui.setContent { MaterialTheme { Box(Modifier.width(128.dp)) {
            var count by remember { mutableIntStateOf(0) }
            DeckCardTile(card, true, count, 0, 0, false, false, count < 3,
                onPreview = { opened = true }, onChoose = {}, onQuantity = { count = it; quantity = it })
        } } }
        val add = ui.onNodeWithContentDescription("Ajouter Carte test")
        val before = add.fetchSemanticsNode().boundsInRoot
        val picture = ui.onNodeWithContentDescription("Agrandir Carte test").fetchSemanticsNode().boundsInRoot
        val title = ui.onNodeWithText("Carte test").fetchSemanticsNode().boundsInRoot
        assertTrue(before.top >= picture.bottom)
        assertTrue(before.bottom <= title.top)
        add.assertIsDisplayed().assertIsEnabled().performClick()
        val after = add.fetchSemanticsNode().boundsInRoot
        assertEquals(before, after)
        add.performClick().performClick().assertIsNotEnabled()
        ui.runOnIdle { assertEquals(3, quantity); assertFalse(opened) }
        ui.onNodeWithContentDescription("Retirer Carte test").performClick()
        add.assertIsEnabled()
        ui.runOnIdle { assertEquals(2, quantity); assertFalse(opened) }
    }
}
