package fr.swucollection.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.fillMaxSize
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
class UnifiedCollectionUiTest {
    @get:Rule val ui = createComposeRule()
    @Test fun catalogueOpensActionsWithoutChangingAnyStock() {
        val card = CatalogCard("SOR", "095", "Carte A", "Unité", "Rare")
        var action: CatalogCard? = null
        val owned = listOf(CollectionCard(1, "Carte A", "SOR", "095", "Foil", 2, "FR"))
        ui.setContent { MaterialTheme {
            UnifiedCollectionCatalog(listOf(card), owned, "Toutes les cartes", "Toutes", "Toutes", "", "Liste", CardDetails.empty(),
                Modifier.fillMaxSize(), onPreview = { _, _ -> }, onActions = { action = it })
        } }
        ui.onNodeWithContentDescription("Ajouter ou gérer Carte A").performClick()
        ui.runOnIdle { assertEquals(card, action); assertEquals(2, owned.single().quantity) }
    }
    @Test fun missingDimmingCanBeToggledWithoutChangingTheCollection() {
        var dimmed = true
        val owned = listOf(CollectionCard(1, "Carte A", "SOR", "095", "Foil", 2, "FR"))
        ui.setContent { MaterialTheme {
            var selected by remember { mutableStateOf(true) }
            MissingCardDimmingToggle(selected) { selected = it; dimmed = it }
        } }
        ui.onNodeWithText("Griser les non possédées").performClick().assertIsNotSelected()
        ui.runOnIdle { assertFalse(dimmed); assertEquals(2, owned.single().quantity) }
        ui.onNodeWithText("Griser les non possédées").performClick().assertIsSelected()
        ui.runOnIdle { assertTrue(dimmed) }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w640dp-h360dp-land")
    fun fixedControlsLeaveCardsReachableInLandscapeWithLargeText() {
        val card = CatalogCard("SOR", "095", "Carte paysage", "Unité", "Rare")
        var opened = false
        ui.setContent {
            val density = androidx.compose.ui.platform.LocalDensity.current
            CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, 2f)) {
                MaterialTheme {
                    UnifiedCollectionCatalog(listOf(card), emptyList(), "Toutes les cartes", "Toutes", "Toutes", "", "Miniatures", CardDetails.empty(),
                        Modifier.fillMaxSize(), onPreview = { _, _ -> opened = true }, onActions = {},
                        header = { androidx.compose.material3.Text("Recherche fixe") })
                }
            }
        }
        val target = hasContentDescription("Carte paysage, 0 exemplaire(s)")
        ui.onNodeWithTag("swu_unified_catalog").performScrollToNode(target)
        ui.onNode(target).assertIsDisplayed().performClick()
        ui.runOnIdle { assertTrue(opened) }
        ui.onNodeWithText("Recherche fixe").assertIsDisplayed()
        ui.onNodeWithContentDescription("Taille des cartes").performScrollTo().performClick()
        ui.onNodeWithText("Compact").performClick()
        ui.onNodeWithText("Recherche fixe").assertIsDisplayed()
    }

}
