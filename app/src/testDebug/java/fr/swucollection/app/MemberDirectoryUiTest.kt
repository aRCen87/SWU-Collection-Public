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
@Config(sdk = [35])
class MemberDirectoryUiTest {
    @get:Rule val ui = createComposeRule()

    @Test fun browsingIsImmediateAndPseudoSearchReachesLaterPagesAndInvitesOnlyTheSelectedMember() {
        val pages = mutableListOf<String?>()
        var invited: String? = null
        ui.setContent { MaterialTheme { Column(Modifier.fillMaxSize()) {
            MemberDirectory("me", emptyList(), false, { cursor ->
                pages += cursor
                if (cursor == null) MemberPage(listOf(PlayerProfile("first", "Premier", discoverable = true)) + (1..49).map { PlayerProfile("member$it", "Z membre $it", discoverable = true) }, "first", true)
                else MemberPage(listOf(PlayerProfile("last", "aRCen", discoverable = true)), "last", false)
            }, { invited = it }, {}, fillHeight = true)
        } } }
        ui.onNodeWithText("Premier").assertExists()
        ui.runOnIdle { assertEquals(listOf<String?>(null), pages) }
        ui.onNodeWithText("Rechercher un pseudo").performTextInput("ARCEN")
        ui.waitUntil(5000) { runCatching { ui.onNodeWithText("aRCen").assertExists(); true }.getOrDefault(false) }
        ui.onNodeWithText("Ajouter").performClick()
        ui.runOnIdle {
            assertEquals(listOf(null, "first"), pages)
            assertEquals("last", invited)
        }
        ui.onNodeWithText("Rechercher un pseudo").performTextClearance()
        ui.onNodeWithText("Premier").assertExists()
    }
    @Test fun scrollingLoadsTheNextPageWithoutRequiringAPseudoSearch() {
        var pageCount = 0
        ui.setContent { MaterialTheme { Column(Modifier.fillMaxSize()) {
            MemberDirectory("me", emptyList(), false, { cursor ->
                pageCount++
                if (cursor == null) MemberPage((1..50).map { PlayerProfile("id$it", "Membre %02d".format(it), discoverable = true) }, "id50", true)
                else MemberPage(listOf(PlayerProfile("last", "Dernier membre", discoverable = true)), "last", false)
            }, {}, {}, fillHeight = true)
        } } }
        ui.onNodeWithTag("member_directory").performScrollToIndex(49)
        ui.waitUntil(5000) { pageCount == 2 }
        ui.onNodeWithTag("member_directory").performScrollToIndex(0)
        ui.onNodeWithText("Dernier membre").assertExists()
    }

}
