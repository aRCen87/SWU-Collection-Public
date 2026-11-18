package fr.swucollection.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Production menus at twice the normal font size, using disposable local data only. */
class Test33AccessibilityTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private fun capture(name: String) {
        File(ui.activity.getExternalFilesDir(null), "home-test-font-$name.png").outputStream().use {
            ui.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    private fun assertUnclippedText(text: String) {
        val results = mutableListOf<TextLayoutResult>()
        ui.onNodeWithText(text).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        assertTrue("Missing text layout: $text", results.isNotEmpty())
        assertFalse("Clipped text: $text; ${results.map { "${it.size}, lines=${it.lineCount}, width=${it.didOverflowWidth}, height=${it.didOverflowHeight}" }}", results.any { it.hasVisualOverflow })
    }
    @Composable private fun LargeText(content: @Composable () -> Unit) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
            MaterialTheme(colorScheme = darkColorScheme()) { content() }
        }
    }
    @Test fun selectedFilterLabelsWrapAndTheConfirmationRemainsReachable() {
        var closed = false
        var showDialog by mutableStateOf(true)
        val label = "Traits : Chasseur de primes, République galactique, Séparatiste"
        ui.setContent { LargeText {
            if (showDialog) DeckFilterDialog(DeckBrowserFilters(trait = "Chasseur de primes|République galactique|Séparatiste"),
                emptyList(), CardDetails.empty(), {}, { closed = true; showDialog = false })
            else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
                DeckMultiChoice("Traits", "a|b|c", listOf("a" to "Chasseur de primes", "b" to "République galactique", "c" to "Séparatiste")) {}
            }
        } }
        // The production multi-choice control must preserve arbitrarily long labels.
        ui.onNodeWithText("Voir les cartes").assertIsDisplayed().performClick()
        assertTrue(closed)
        capture("filters")
        assertUnclippedText(label)
    }
    @Test fun scannerShowsTheExactQuantityAndBothModesWithLargeText() {
        ui.setContent { LargeText {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
                ScannerCardSummary(CatalogCard("SOR", "001", "Une carte au nom suffisamment long pour revenir à la ligne", "Leader", "Rare"),
                    "Finition à vérifier", "En attente de validation", quantity = 12)
                ScannerModeChoices("Rafale") {}
            }
        } }
        capture("scanner")
        assertUnclippedText("Quantité : 12")
        ui.onNodeWithText("Une par une").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("Rafale").performScrollTo().assertIsDisplayed()
    }
    @Test fun leaderCanBeTurnedAndClosedWithReducedMotionAndLargeText() {
        var closed = false
        ui.setContent { LargeText {
            CompositionLocalProvider(LocalReducedMotion provides true) {
                FullscreenCardViewer(CatalogCard("SOR", "001", "Leader recto verso", "Leader", "Rare", backImageUrl = "https://example.invalid/back.png"),
                    "FR", "Standard", false, false, {}, { closed = true })
            }
        } }
        ui.onNodeWithTag("fullscreen_card_front").assertExists()
        ui.onNodeWithContentDescription("Retourner la carte").assertIsDisplayed().performClick()
        ui.onNodeWithTag("fullscreen_card_back").assertExists()
        ui.onNodeWithContentDescription("Retourner la carte").performClick()
        ui.onNodeWithTag("fullscreen_card_front").assertExists()
        ui.onNodeWithContentDescription("Fermer le plein écran").assertIsDisplayed().performClick()
        assertTrue(closed)
    }
    @Test fun setLogosKeepPromoIdentityAndWorkWithoutNetwork() {
        assertEquals(setLogoResource("HMW"), setLogoResource("HMWP"))
        assertNotEquals(setLogoResource("SOR"), setLogoResource("HMW"))
        ui.setContent { LargeText { SetLogo("HMWP") } }
        ui.onNodeWithContentDescription("Logo de l’extension HMWP").assertIsDisplayed()
    }
    @Test fun settingsAndFiveBottomShortcutsRemainReadableAndReachable() {
        var destination = ""
        ui.setContent { LargeText {
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(12.dp)) {
                    SettingsOverview({ destination = it }, null)
                }
                CollectionBottomBar("Collection", { destination = it })
            }
        } }
        ui.onNodeWithText("Sauvegarder et restaurer").performScrollTo().assertIsDisplayed().performClick()
        assertEquals("Sauvegardes", destination)
        assertUnclippedText("Sauvegarder et restaurer")
        ui.onNodeWithTag("bottom_shortcut_Mes proxies").assertIsDisplayed().performClick()
        assertEquals("Mes proxies", destination)
        capture("settings")
        assertUnclippedText("Mes proxies")
    }
}
