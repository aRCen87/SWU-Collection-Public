package fr.swucollection.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
@Config(sdk = [35])
class ScanFinishSelectionTest {
    @get:Rule val ui = createComposeRule()

    @Test fun finishesStayIndependentWhenRowsLeaveTheViewportAndAnotherRowIsApplied() {
        val pending = (1..15).map { ScanBatchEntry(CatalogCard("SOR", "$it", "Carte $it", "Unité", "Rare", language = "FR"), "", 1) }
        var rows by mutableStateOf(pending)
        ui.setContent { MaterialTheme {
            LazyColumn(Modifier.height(180.dp).testTag("reviews")) {
                items(rows, key = { it.reviewKey }) { entry ->
                    Column(Modifier.testTag("row-${entry.card.number}")) {
                        ScanEntryReview(entry) { language, variant, quantity -> rows = resolveScanEntries(rows, entry, language, variant, quantity) }
                    }
                }
            }
        } }
        fun choose(row: String, finish: String) {
            ui.onNode(hasText("Choisir la finition") and hasAnyAncestor(hasTestTag("row-$row"))).performClick()
            ui.onNodeWithText(finish).performClick()
        }
        choose("1", "Foil")
        ui.onNodeWithTag("reviews").performScrollToIndex(14)
        choose("15", "Normale")
        ui.onNode(hasText("Appliquer à 1 exemplaire") and hasAnyAncestor(hasTestTag("row-15"))).performClick()
        ui.onNodeWithTag("reviews").performScrollToIndex(0)
        ui.onNode(hasText("Foil") and hasAnyAncestor(hasTestTag("row-1"))).assertExists()
        ui.onNode(hasText("Appliquer à 1 exemplaire") and hasAnyAncestor(hasTestTag("row-1"))).assertIsEnabled().performClick()
        ui.runOnIdle {
            assertEquals("Foil", rows.single { it.card.number == "1" }.variant)
            assertEquals("Normale", rows.single { it.card.number == "15" }.variant)
            assertEquals(15, rows.sumOf { it.quantity })
        }
    }
}
