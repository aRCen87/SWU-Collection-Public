package fr.swucollection.app

import androidx.compose.material3.MaterialTheme
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
}
