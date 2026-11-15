package fr.swucollection.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
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
@Config(sdk = [35], qualifiers = "w360dp-h600dp")
class ProfileEditorUiTest {
    @get:Rule val ui = createComposeRule()
    @Test fun newMemberCanScrollThroughTheMessagingProfileFormAndSaveTheEnteredPseudo() {
        var saved: PlayerProfile? = null
        ui.setContent { MaterialTheme { Column(Modifier.fillMaxSize()) {
            ProfileEditor(PlayerProfile("me", ""), emptyList(), emptyList(), false,
                onSave = { saved = it }, onCancel = null, scroll = true)
        } } }
        ui.onNodeWithText("Pseudo").performScrollTo().performTextInput("Nouveau membre")
        ui.onNodeWithText("Enregistrer mon profil").performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
        ui.runOnIdle {
            assertEquals("Nouveau membre", saved?.name)
            assertFalse(saved!!.discoverable)
            assertEquals("friends", saved?.messagePolicy)
        }
    }
}
