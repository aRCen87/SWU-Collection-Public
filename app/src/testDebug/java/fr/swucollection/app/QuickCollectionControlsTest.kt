package fr.swucollection.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp")
class QuickCollectionControlsTest {
    @get:Rule val ui = createComposeRule()
    private val card = CatalogCard("SOR", "095", "Carte test", "Unité", "Rare")

    @Test fun choosingLanguageAndFinishDoesNotWriteAndPlusUsesExactSelection() {
        var changes = 0
        ui.setContent { MaterialTheme {
            var language by remember { mutableStateOf("FR") }
            var finish by remember { mutableStateOf("Normale") }
            androidx.compose.foundation.layout.Column {
                QuickVariantSelectors(card, language, finish, { language = it }, { finish = it })
                QuickStockBar(0, true, { delta ->
                    assertEquals("EN", language); assertEquals("Foil", finish); assertEquals(1, delta); changes++
                })
            }
        } }
        ui.onNodeWithContentDescription("Langue de la carte : Français").performClick()
        ui.onNodeWithText("Anglais").performClick()
        ui.onNodeWithContentDescription("Finition de la carte : Normale").performClick()
        ui.onNodeWithText("Foil").performClick()
        ui.runOnIdle { assertEquals(0, changes) }
        ui.onNodeWithContentDescription("Ajouter un exemplaire").performClick()
        ui.runOnIdle { assertEquals(1, changes) }
        ui.onNodeWithContentDescription("Retirer un exemplaire").assertIsNotEnabled()
    }

    @Test fun rapidAddsAndUndoLastRemovalPreserveOtherLanguagesAndFinishes() {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("swu_collection.db")
        CollectionDatabase(context).use { db ->
            db.addOrIncrement(card.name, "SOR", "095", "Normale", "FR", 3)
            db.addOrIncrement(card.name, "SOR", "095", "Foil", "FR", 2)
            changeQuickStock(db, card, "EN", "Normale", 1)
            changeQuickStock(db, card, "EN", "Normale", 1)
            assertEquals(2, db.allCards().single { it.language == "EN" }.quantity)
            changeQuickStock(db, card, "EN", "Normale", -1)
            changeQuickStock(db, card, "EN", "Normale", -1)
            assertFalse(db.allCards().any { it.language == "EN" })
            db.undoLastChange()
            assertEquals(1, db.allCards().single { it.language == "EN" }.quantity)
            assertEquals(3, db.allCards().single { it.language == "FR" && it.variant == "Normale" }.quantity)
            assertEquals(2, db.allCards().single { it.language == "FR" && it.variant == "Foil" }.quantity)
        }
    }
}
