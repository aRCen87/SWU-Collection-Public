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
@Config(sdk = [35], qualifiers = "w360dp-h800dp")
class CollectionAddConfirmationUiTest {
    @get:Rule val ui = createComposeRule()
    private val card = CatalogCard("SOR", "095", "Carte test", "Unité", "Rare")
    @Test fun openingAndCancellingNeverChangesStock() {
        var writes = 0; var closed = false
        ui.setContent { MaterialTheme { CollectionAddConfirmation(CollectionAddRequest(card, "Normale", "FR"),
            { closed = true }, { _, _ -> writes++ }) } }
        ui.runOnIdle { assertEquals(0, writes) }
        ui.onNodeWithText("Annuler").performClick()
        ui.runOnIdle { assertTrue(closed); assertEquals(0, writes) }
    }
    @Test fun confirmationWritesOnceAndShowsAnErrorIfTheWriteFails() {
        var writes = 0; var closed = false
        ui.setContent { MaterialTheme { CollectionAddConfirmation(CollectionAddRequest(card, "Normale", "FR"),
            { closed = true }, { language, finish ->
                assertEquals("FR", language); assertTrue(finish in ScanVariants.detect(card).choices)
                writes++; error("Ajout refusé")
            }) } }
        ui.onNodeWithText("Confirmer l’ajout").performClick()
        ui.onNodeWithText("Ajout refusé").assertExists()
        ui.runOnIdle { assertEquals(1, writes); assertFalse(closed) }
    }
    @Test fun successfulConfirmationClosesAndDoesNotAllowASecondWrite() {
        var writes = 0; var closed = false
        ui.setContent { MaterialTheme { CollectionAddConfirmation(CollectionAddRequest(card, "Normale", "FR"),
            { closed = true }, { _, _ -> writes++ }) } }
        ui.onNodeWithText("Confirmer l’ajout").performClick()
        ui.onNodeWithText("Confirmer l’ajout").assertIsNotEnabled()
        ui.runOnIdle { assertEquals(1, writes); assertTrue(closed) }
    }
}
