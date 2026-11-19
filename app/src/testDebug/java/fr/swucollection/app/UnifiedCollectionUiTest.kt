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
    @Test fun scopeMenuFiltersMissingCardsAndRestoresTheCatalogueWithoutChangingStock() {
        val ownedCard = CatalogCard("SOR", "095", "Carte possédée", "Unité", "Rare")
        val missingCard = ownedCard.copy(number = "096", name = "Carte à trouver")
        val owned = listOf(CollectionCard(1, ownedCard.name, "SOR", "095", "Normale", 2, "FR"))
        var scope by mutableStateOf("Toutes les cartes")
        ui.setContent { MaterialTheme {
            androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
                CollectionScopeMenu(scope) { scope = it }
                UnifiedCollectionCatalog(listOf(ownedCard, missingCard), owned, scope, "Toutes", "Toutes", "", "Liste", CardDetails.empty(),
                    Modifier.weight(1f), onPreview = { _, _ -> }, onActions = {}, showDisplayOptions = false, showFavoritesControl = false)
            }
        } }
        ui.onNodeWithText("Afficher : Toutes ▾").performClick()
        ui.onNodeWithText("Possédées").assertIsDisplayed()
        ui.onNodeWithText("Manquantes").performClick()
        ui.onNodeWithText(ownedCard.name).assertDoesNotExist()
        ui.onNodeWithText(missingCard.name).assertIsDisplayed()
        ui.onNodeWithText("Afficher : Manquantes ▾").performClick()
        ui.onNodeWithText("Toutes les cartes").performClick()
        ui.onNodeWithText(ownedCard.name).assertIsDisplayed()
        assertEquals(2, owned.single().quantity)
    }

    @Test fun standardPhoneKeepsTwoCardsAcrossAndCompactProgressCanExpand() {
        val first = CatalogCard("SOR", "095", "Première carte", "Unité", "Rare")
        val second = first.copy(number = "096", name = "Deuxième carte")
        ui.setContent { MaterialTheme {
            UnifiedCollectionCatalog(listOf(first, second), emptyList(), "Manquantes", "Toutes", "Toutes", "", "Visuel", CardDetails.empty(),
                Modifier.fillMaxSize(), onPreview = { _, _ -> }, onActions = {}, showDisplayOptions = false, showFavoritesControl = false)
        } }
        val left = ui.onNodeWithTag("catalog_artwork_${MarketPricing.key(first.setCode, first.number)}", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val right = ui.onNodeWithTag("catalog_artwork_${MarketPricing.key(second.setCode, second.number)}", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(left.top, right.top, 1f)
        assertTrue(right.left >= left.right)
        ui.onNodeWithText("×0").assertDoesNotExist()
        ui.onNodeWithText("Progression par rareté").performClick()
        ui.onNodeWithText("Rare").assertIsDisplayed()
        ui.onNodeWithText("Masquer la progression par rareté").performClick()
        ui.onNodeWithText("Rare").assertDoesNotExist()
    }

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
        ui.onNodeWithText("Griser les manquantes").performClick().assertIsOff()
        ui.runOnIdle { assertFalse(dimmed); assertEquals(2, owned.single().quantity) }
        ui.onNodeWithText("Griser les manquantes").performClick().assertIsOn()
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
        ui.onNodeWithText("Affichage").performScrollTo().performClick()
        ui.onNodeWithText("Compact").performClick()
        ui.onNodeWithText("Voir les cartes").performClick()
        ui.onNodeWithText("Recherche fixe").assertIsDisplayed()
    }

}
