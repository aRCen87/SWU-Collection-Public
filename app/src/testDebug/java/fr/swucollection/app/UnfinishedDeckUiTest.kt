package fr.swucollection.app

import androidx.compose.material3.MaterialTheme
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
@Config(sdk = [35], qualifiers = "w600dp-h900dp")
class UnfinishedDeckUiTest {
    @get:Rule val ui = createComposeRule()
    private val empty = PortableDeck("SOR-001", "SOR-024", emptyList())

    @Test fun unfinishedDraftCanBeConsultedWithoutStartingAnEdit() {
        var edited = false; var closed = false
        val draft = DeckDraft("À compléter", empty, false, false)
        ui.setContent { MaterialTheme { DeckConsultation(draft, emptyList(), { closed = true }, { edited = true }) } }
        ui.onNodeWithText("Leader à choisir").assertExists()
        ui.onNodeWithText("Base à choisir").assertExists()
        ui.onNodeWithText("Aucune carte pour le moment.").assertExists()
        ui.onNodeWithText("Fermer").performClick()
        ui.runOnIdle { assertTrue(closed); assertFalse(edited) }
    }

    @Test fun savingWithoutLeaderOrBaseKeepsAReopenableDraftAndDoesNotSaveInventory() {
        val context = RuntimeEnvironment.getApplication()
        context.filesDir.resolve("deck-drafts").deleteRecursively()
        var closed = false; var saved = false
        ui.setContent { MaterialTheme {
            DeckBuilderPanel(emptyList(), emptyList(), empty, initialName = "Deck en cours", newDeck = true,
                draftKey = "new:unfinished-test", details = CardDetails.empty(), rules = LegalityRules.DEFAULT,
                onCancel = { closed = true }, onSave = { _, _, _, _ -> saved = true }, onPreview = { _, _ -> })
        } }
        ui.onNodeWithText("Commencer").performClick()
        ui.onNodeWithText("Garder le brouillon").assertIsEnabled().performClick()
        ui.runOnIdle {
            assertTrue(closed); assertFalse(saved)
            val restored = DeckDraftStore(context).newDrafts().single { it.first == "new:unfinished-test" }.second
            assertEquals("Deck en cours", restored.name)
            assertFalse(restored.leaderChosen); assertFalse(restored.baseChosen)
            assertTrue(restored.deck.deck.isEmpty())
        }
    }
}
