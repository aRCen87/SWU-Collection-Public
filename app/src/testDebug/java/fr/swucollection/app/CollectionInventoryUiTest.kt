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
class CollectionInventoryUiTest {
    @get:Rule val ui = createComposeRule()
    private val card = CatalogCard("SOR", "095", "Carte test", "Unité", "Rare")
    @Test fun allStockIsVisibleAndButtonsTargetTheExactLanguageAndFinish() {
        val rows = listOf(CollectionCard(1, card.name, "SOR", "095", "Normale", 3, "FR"),
            CollectionCard(2, card.name, "SOR", "095", "Foil", 1, "FR"),
            CollectionCard(3, card.name, "SOR", "095", "Normale", 2, "EN"))
        var changed: Pair<Long, Int>? = null; var opened = false
        ui.setContent { MaterialTheme { CollectionInventoryCard(rows, mapOf(MarketPricing.key("SOR", "095") to card),
            { _, _ -> opened = true }, { row, delta -> changed = row.id to delta }, {}, {}) } }
        ui.onNodeWithText("6 possédées").assertExists()
        ui.onNodeWithText("FR · Normale").assertExists()
        ui.onNodeWithText("FR · Foil").assertExists()
        ui.onNodeWithText("EN · Normale").assertExists()
        ui.onNodeWithContentDescription("Ajouter un exemplaire : Carte test, EN, Normale").performClick()
        ui.runOnIdle { assertEquals(3L to 1, changed); assertFalse(opened) }
    }
    @Test fun openingSheetDoesNotWriteAndExplicitAddUsesSelectedQuantity() {
        var writes = 0; var added = 0
        ui.setContent { MaterialTheme { CollectionVariantSheet(card, listOf(card), emptyList(), CardDetails.empty(), {},
            { _, language, _, quantity -> assertEquals("FR", language); writes++; added = quantity }) } }
        ui.runOnIdle { assertEquals(0, writes) }
        ui.onNodeWithContentDescription("Ajouter un exemplaire : Carte test").performScrollTo().performClick()
        ui.onNodeWithText("Ajouter à ma collection").performScrollTo().performClick()
        ui.runOnIdle { assertEquals(1, writes); assertEquals(2, added) }
    }
}
