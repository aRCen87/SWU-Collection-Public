package fr.swucollection.app

import androidx.activity.ComponentActivity
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Real controls, local presentation only: no account or collection writes. */
class VisualRefreshTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private val card = CatalogCard("SOR", "001", "Carte de contrôle", "Unité", "Rare")

    @Test fun keywordsRemainCombinedWithExistingFilters() {
        var result = DeckBrowserFilters(trait = "Rebelle")
        var dismissed = false
        ui.setContent {
            var filters by remember { mutableStateOf(result) }
            MaterialTheme(colorScheme = darkColorScheme()) {
                DeckFilterDialog(filters, listOf(card), CardDetails.empty(), {
                    filters = it; result = it
                }, { dismissed = true })
            }
        }
        ui.onNodeWithText("Mots-clés et capacités").performTextInput("sentinelle")
        ui.runOnIdle {
            assertEquals("sentinelle", result.keywords)
            assertEquals("Rebelle", result.trait)
            assertEquals(2, result.activeCount)
        }
        ui.onNodeWithText("Voir les cartes").performClick()
        ui.runOnIdle { assertTrue(dismissed) }
    }

    @Test fun selectedCardShowsQuantityAndAddingRequiresAnExplicitButton() {
        var count = 2
        var previews = 0
        ui.setContent {
            var quantity by remember { mutableIntStateOf(count) }
            MaterialTheme(colorScheme = darkColorScheme()) {
                DeckCardTile(card, true, quantity, 4, 0, false, false, true,
                    onPreview = { previews++ }, onChoose = {}, onQuantity = { quantity = it; count = it })
            }
        }
        ui.onNodeWithText("✓ ×2").assertExists()
        ui.onNodeWithContentDescription("Agrandir Carte de contrôle").performClick()
        ui.runOnIdle { assertEquals(2, count); assertEquals(1, previews) }
        ui.onNodeWithContentDescription("Ajouter Carte de contrôle").performClick()
        ui.onNodeWithText("✓ ×3").assertExists()
    }
}
