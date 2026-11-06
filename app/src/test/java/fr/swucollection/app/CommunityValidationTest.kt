package fr.swucollection.app

import org.junit.Assert.*
import org.junit.Test

class CommunityValidationTest {
    @Test fun conversationIdentityIsStableAndRejectsSelfAndPathInjection() {
        assertEquals(CommunityValidation.pair("alice", "bob"), CommunityValidation.pair("bob", "alice"))
        for (peer in listOf("alice", "", "bad/path")) {
            assertTrue(runCatching { CommunityValidation.pair("alice", peer) }.isFailure)
        }
    }
    @Test fun messagesAndProfilesAreBoundedWithoutSilentTruncation() {
        assertEquals("Bonjour", CommunityValidation.text(" Bonjour "))
        assertTrue(runCatching { CommunityValidation.text(" ") }.isFailure)
        assertTrue(runCatching { CommunityValidation.text("x".repeat(2001)) }.isFailure)
        assertTrue(runCatching { CommunityValidation.name("x".repeat(33)) }.isFailure)
        assertTrue(runCatching { CommunityValidation.name("bad\nname") }.isFailure)
        assertEquals("aR_Cen", CommunityValidation.name(" aR_Cen "))
    }
    @Test fun avatarCannotBecomeAnExternalTrackingUrlOrUnboundedPayload() {
        assertEquals("preset:star", CommunityValidation.avatar("preset:star"))
        assertTrue(runCatching { CommunityValidation.avatar("https://example.com/track") }.isFailure)
        assertTrue(runCatching { CommunityValidation.avatar("jpeg:" + "A".repeat(18000)) }.isFailure)
        assertTrue(runCatching { CommunityValidation.avatar("preset:unknown") }.isFailure)
    }
}
