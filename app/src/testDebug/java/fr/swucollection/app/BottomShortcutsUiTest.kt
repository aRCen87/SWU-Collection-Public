package fr.swucollection.app

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp")
class BottomShortcutsUiTest {
    @get:Rule val ui = createComposeRule()
    private val prefs get() = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("bottom_shortcuts", Context.MODE_PRIVATE)
    @Before fun reset() { prefs.edit().clear().commit() }

    @Test fun longPressEditsWithoutNavigatingAndCancelPreservesChoices() {
        var navigations = 0
        ui.setContent { MaterialTheme { CollectionBottomBar("Accueil") { navigations++ } } }
        ui.onNodeWithTag("bottom_shortcut_Mes decks").performTouchInput { longClick() }
        ui.onNodeWithText("Mes raccourcis").assertIsDisplayed()
        ui.runOnIdle { assertEquals(0, navigations) }
        ui.onNodeWithText("5/5 raccourcis").assertIsDisplayed()
        ui.onNodeWithText("Scanner").assertIsNotEnabled()
        ui.onNodeWithContentDescription("Retirer Mes listes").performClick()
        ui.onNodeWithText("4/5 raccourcis").assertIsDisplayed()
        ui.onNodeWithText("Annuler").performClick()
        ui.onNodeWithTag("bottom_shortcut_Mes listes").assertExists()
        ui.runOnIdle { assertNull(prefs.getString("order", null)) }
    }

    @Test fun orderIsSavedAndRestoredAndShortTapNavigates() {
        prefs.edit().putString("order", "Scanner|Mes listes").commit()
        var destination = ""
        ui.setContent { MaterialTheme { CollectionBottomBar("Mes listes") { destination = it } } }
        ui.onNodeWithTag("bottom_shortcut_Mes listes").assertIsSelected()
        ui.onNodeWithTag("bottom_shortcut_Scanner").performClick()
        ui.runOnIdle { assertEquals("Scanner", destination) }
        ui.onNodeWithTag("bottom_shortcut_Mes listes").performTouchInput { longClick() }
        ui.onNodeWithContentDescription("Monter Mes listes").performClick()
        ui.onNodeWithText("Enregistrer").performClick()
        ui.runOnIdle { assertEquals("Mes listes|Scanner", prefs.getString("order", null)) }
    }

    @Test fun invalidSelectionsAreRejectedAndEveryDestinationHasAnIcon() {
        assertFalse(validBottomShortcuts(emptyList()))
        assertFalse(validBottomShortcuts(listOf("Accueil", "Accueil")))
        assertFalse(validBottomShortcuts(bottomShortcutIcons.keys.take(6)))
        assertFalse(validBottomShortcuts(listOf("Inconnue")))
        assertTrue(validBottomShortcuts(listOf("Mes decks", "Mes proxies", "Mes listes", "Compteur", "Scanner")))
        val legacy = HomeLayoutStore.defaults - setOf("MES LISTES", "MES PROXIES")
        val restored = HomeLayoutStore.parse(HomeLayoutStore.json(HomeLayout(legacy, "SOR")))
        assertEquals(legacy, restored.order.take(legacy.size))
        assertEquals("SOR", restored.set)
        assertEquals(HomeLayoutStore.defaults.toSet(), restored.order.toSet())
    }
}
