package fr.swucollection.app

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
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
        ui.onNodeWithTag("swu_deck_consultation_fullscreen").assertIsDisplayed()
        val screen = ui.onNodeWithTag("swu_deck_consultation_fullscreen").getUnclippedBoundsInRoot()
        assertTrue((screen.right - screen.left).value >= 590f)
        assertTrue((screen.bottom - screen.top).value >= 850f)
        ui.onNodeWithText("Leader").assertExists()
        ui.onNodeWithText("Base").assertExists()
        ui.onNodeWithText("Aucune carte. Choisis Modifier pour compléter le deck.").assertExists()
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
    @Test fun incompleteDeckSupportsCardPreviewAndInlineQuantityChangesWithoutLosingPendingScans() {
        val card = CatalogCard("SOR", "095", "Carte test", "Unité", "Rare")
        val original = DeckDraft("À compléter", empty.copy(deck = listOf(DeckEntry("SOR-095", 1))), false, false,
            listOf(ScanBatchEntry(card, "Foil", 2)), "pending-scan")
        var latest = original
        var preview: CatalogCard? = null
        ui.setContent { MaterialTheme {
            var value by remember { mutableStateOf(original) }
            DeckConsultation(value, listOf(card), {}, {}, onChange = { value = it; latest = it },
                onPreview = { selected, _ -> preview = selected })
        } }
        ui.onNodeWithTag("deck_overview_grid").performScrollToNode(hasTestTag("deck_overview_card_SOR-095"))
        ui.onNodeWithTag("deck_overview_card_SOR-095").performClick()
        ui.onNodeWithTag("deck_gallery_title").assertTextEquals("Carte test")
        ui.runOnIdle { assertNull(preview); assertEquals(1, latest.deck.deck.single().count) }
        ui.onNodeWithContentDescription("Ouvrir la fiche complète").performClick()
        ui.runOnIdle { assertEquals(card, preview) }
        ui.onNodeWithTag("deck_overview_grid").performScrollToNode(hasContentDescription("Modifier la quantité de Carte test"))
        ui.onNodeWithContentDescription("Modifier la quantité de Carte test").performClick()
        ui.onNodeWithText("+1 exemplaire").performClick()
        ui.runOnIdle {
            assertEquals(2, latest.deck.deck.single().count)
            assertFalse(latest.leaderChosen); assertFalse(latest.baseChosen)
            assertEquals(original.collectionEntries, latest.collectionEntries)
            assertEquals("pending-scan", latest.collectionBatchId)
        }
        ui.onNodeWithContentDescription("Modifier la quantité de Carte test").performClick()
        ui.onNodeWithText("−1 exemplaire").performClick()
        ui.runOnIdle { assertEquals(1, latest.deck.deck.single().count) }
        ui.onNodeWithTag("deck_overview_grid").performScrollToNode(hasContentDescription("Modifier la quantité de Carte test"))
        ui.onNodeWithContentDescription("Modifier la quantité de Carte test").performClick()
        ui.onNodeWithText("Retirer la carte").performClick()
        ui.onNodeWithText("Retirer", useUnmergedTree = true).performClick()
        ui.onNodeWithTag("deck_overview_grid").performScrollToNode(hasText("Ajoute tes premières cartes avec le bouton +."))
        ui.onNodeWithText("Ajoute tes premières cartes avec le bouton +.").assertExists()
        ui.runOnIdle { assertTrue(latest.deck.deck.isEmpty()) }
    }

    @Test fun openingACardHidesTheDeckDialogAndClosingTheCardReturnsToTheUpdatedDraft() {
        val context = RuntimeEnvironment.getApplication()
        context.filesDir.resolve("deck-drafts").deleteRecursively()
        val card = CatalogCard("SOR", "095", "Carte test", "Unité", "Rare")
        val draft = DeckDraft("Mon brouillon", empty.copy(deck = listOf(DeckEntry("SOR-095", 1))), false, false)
        DeckDraftStore(context).write("new:preview", "baseline", draft)
        ui.setContent { MaterialTheme { Column(Modifier.fillMaxSize()) {
            var preview by remember { mutableStateOf(false) }
            DeckLibraryPanel(emptyList(), Modifier.weight(1f), catalog = listOf(card),
                onCreate = {}, onResumeDraft = {}, onImport = {}, onOpen = {}, onDuplicate = {}, onDelete = {},
                onPreview = { _, _ -> preview = true }, previewOpen = preview)
            if (preview) Button(onClick = { preview = false }) { Text("Fermer la fiche") }
        } } }
        ui.onNodeWithText("Mon brouillon").performClick()
        ui.onNodeWithTag("deck_overview_grid").performScrollToNode(hasContentDescription("Modifier la quantité de Carte test"))
        ui.onNodeWithContentDescription("Modifier la quantité de Carte test").performClick()
        ui.onNodeWithText("+1 exemplaire").performClick()
        ui.onNodeWithTag("deck_overview_card_SOR-095").performClick()
        ui.onNodeWithTag("deck_gallery_title").assertTextEquals("Carte test")
        ui.onNodeWithContentDescription("Ouvrir la fiche complète").performClick()
        ui.onNodeWithText("Fermer la fiche").assertExists()
        ui.onNodeWithTag("swu_deck_consultation_fullscreen").assertDoesNotExist()
        ui.onNodeWithText("Fermer la fiche").performClick()
        ui.onNodeWithTag("deck_overview_grid").performScrollToNode(hasTestTag("deck_overview_card_SOR-095"))
        ui.onNodeWithText("×2").assertExists()
        ui.runOnIdle {
            val restored = DeckDraftStore(context).read("new:preview", "baseline")!!
            assertEquals(2, restored.deck.deck.single().count)
            assertFalse(restored.leaderChosen); assertFalse(restored.baseChosen)
        }
    }

    @Test fun quittingAtTheNamePromptDoesNotSaveOrKeepADraft() {
        val context = RuntimeEnvironment.getApplication()
        context.filesDir.resolve("deck-drafts").deleteRecursively()
        var closed = false; var saved = false
        ui.setContent { MaterialTheme {
            DeckBuilderPanel(emptyList(), emptyList(), empty, newDeck = true,
                draftKey = "new:cancel-name", details = CardDetails.empty(), rules = LegalityRules.DEFAULT,
                onCancel = { closed = true }, onSave = { _, _, _, _ -> saved = true }, onPreview = { _, _ -> })
        } }
        ui.onNodeWithText("Quitter sans enregistrer").performClick()
        ui.runOnIdle {
            assertTrue(closed); assertFalse(saved)
            assertTrue(DeckDraftStore(context).newDrafts().isEmpty())
        }
    }

    @Test fun discardingAResumedDraftDoesNotRecreateItOnDisposalOrSavePendingScans() {
        val context = RuntimeEnvironment.getApplication()
        context.filesDir.resolve("deck-drafts").deleteRecursively()
        val card = CatalogCard("SOR", "095", "Carte en attente", "Unité", "Rare")
        val baseline = DeckInterchange.exportJson(empty, "Mon deck enregistré")
        val key = "new:discard-scans"
        DeckDraftStore(context).write(key, baseline, DeckDraft("Modifications", empty.copy(deck = listOf(DeckEntry("SOR-095", 2))),
            true, true, listOf(ScanBatchEntry(card, "Foil", 2)), "uncommitted-scans"))
        var closed = false; var saved = false
        ui.setContent { MaterialTheme {
            var open by remember { mutableStateOf(true) }
            if (open) DeckBuilderPanel(listOf(card), emptyList(), empty, initialName = "Mon deck enregistré",
                draftKey = key, details = CardDetails.empty(), rules = LegalityRules.DEFAULT,
                onCancel = { closed = true; open = false }, onSave = { _, _, _, _ -> saved = true }, onPreview = { _, _ -> })
        } }
        ui.onNodeWithText("Reprendre").performClick()
        ui.onNodeWithText("Quitter").performClick()
        ui.onNodeWithText("Continuer le deck").performClick()
        ui.onNodeWithText("Quitter").assertExists().performClick()
        ui.onNodeWithText("Quitter sans enregistrer").performClick()
        ui.mainClock.advanceTimeBy(1000)
        ui.runOnIdle {
            assertTrue(closed); assertFalse(saved)
            assertNull(DeckDraftStore(context).read(key, baseline))
        }
    }

}
