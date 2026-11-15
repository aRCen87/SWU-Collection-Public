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
@Config(sdk = [35], qualifiers = "w600dp-h900dp")
class MessengerInboxUiTest {
    @get:Rule val ui = createComposeRule()
    @Test fun compactInboxShowsPseudoLatestSentOrReceivedOnlineStatusAndOpensTheSelectedThread() {
        val now = System.currentTimeMillis()
        val received = PlayerConversation("received", listOf("me", "alice"), now, "alice", "Carte disponible")
        val sent = PlayerConversation("sent", listOf("me", "bob"), now - 1000, "me", "Merci !")
        var opened: String? = null
        ui.setContent { MaterialTheme { Column(Modifier.fillMaxSize()) {
            MessengerInbox("me", listOf(received, sent), emptyMap(), listOf(PlayerProfile("alice", "Alice"), PlayerProfile("bob", "Bob")),
                presence = { id -> PlayerPresence(id == "alice", now) }, onNew = {}, onFriends = {},
                onOpen = { thread, _ -> opened = thread.id })
        } } }
        ui.onNodeWithText("Carte disponible").assertExists()
        ui.onNodeWithText("Toi : Merci !").assertExists()
        ui.onNodeWithText("Nouveau").assertExists()
        ui.onNodeWithContentDescription("Alice en ligne").assertExists()
        ui.onNodeWithContentDescription("Bob hors ligne").assertExists()
        ui.onNodeWithText("Bob").performClick()
        ui.runOnIdle { assertEquals("sent", opened) }
    }
}
