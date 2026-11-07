package fr.swucollection.app

import org.junit.Assert.*
import org.junit.Test

class MessengerTest {
    @Test fun messagesAndFriendsNavigateToTheirOwnScreenWhileAccountAndExchangesRemainSeparate() {
        assertEquals("Messagerie", communityDestination("Messages"))
        assertEquals("Messagerie", communityDestination("Amis"))
        assertEquals("Communauté", communityDestination("Mon profil"))
        assertEquals("Communauté", communityDestination("Échanges"))
        val nav = AppNavigation()
        nav.navigateTo(communityDestination("Messages")); assertEquals("Messagerie", nav.current.section)
        nav.back(); assertEquals("Accueil", nav.current.section)
    }
    @Test fun inboxUsesOnlyParticipantThreadsSortsRecentFirstAndKeepsUnreadPerConversation() {
        val a = PlayerConversation("a", listOf("me", "alice"), 100, "alice")
        val b = PlayerConversation("b", listOf("me", "bob"), 200, "me")
        val other = PlayerConversation("private", listOf("alice", "bob"), 300, "alice")
        val players = listOf(PlayerProfile("alice", "Alice"), PlayerProfile("bob", "Bob"))
        val rows = messengerThreads("me", listOf(a, b, other), emptyMap(), players, "")
        assertEquals(listOf("b", "a"), rows.map { it.conversation.id })
        assertFalse(rows[0].unread); assertTrue(rows[1].unread)
        assertFalse(messengerThreads("me", listOf(a), mapOf("a" to 100L), players, "").single().unread)
        assertEquals("alice", messengerThreads("me", listOf(a, b), emptyMap(), players, " ALI ").single().peer.id)
        assertTrue(messengerThreads("me", listOf(a, b), emptyMap(), players, "nobody").isEmpty())
    }
    @Test fun unknownProfilesDoNotExposeIdentifiersAsDisplayNamesAndEmptyThreadsAreNotUnread() {
        val thread = PlayerConversation("a", listOf("me", "private-id"), 100)
        val row = messengerThreads("me", listOf(thread), emptyMap(), emptyList(), "").single()
        assertEquals("Conversation privée", row.peer.name); assertFalse(row.unread)
        assertTrue(messengerThreads("me", listOf(thread), emptyMap(), emptyList(), "private-id").isEmpty())
    }
}
