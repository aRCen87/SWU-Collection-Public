package fr.swucollection.app
import org.junit.Assert.*
import org.junit.Test
class CommunitySocialTest {
    @Test fun friendCodeRejectsUrlsAndUnknownVersions() {
        assertEquals("alice_123", CommunitySocial.friendId(CommunitySocial.friendQr("alice_123")))
        for (bad in listOf("https://example.com", "swu-friend:v2:alice", "../alice", "", "a".repeat(129)))
            assertTrue(bad, runCatching { CommunitySocial.friendId(bad) }.isFailure)
    }
    @Test fun unreadExcludesOwnMessagesAndReadThreads() {
        val threads = listOf(PlayerConversation("read", listOf("a", "b"), 20, "b"), PlayerConversation("unread", listOf("a", "b"), 20, "b"), PlayerConversation("own", listOf("a", "b"), 30, "a"))
        assertEquals(1, CommunitySocial.unread("a", threads, mapOf("read" to 20L, "unread" to 19L)))
    }
    @Test fun tradeRoundTripRetainsLanguageFinishAndQuantity() {
        val items = listOf(TradeSelection("SOR-005", 3, "EN", "Hyperspace foil"), TradeSelection("SHD-018"))
        assertEquals(items, CommunitySocial.decodeTrade(CommunitySocial.encodeTrade(items)))
        for (bad in listOf(emptyList(), List(21) { items[0] }, listOf(items[0].copy(quantity = 0)), listOf(items[0].copy(language = "ZZ"))))
            assertTrue(runCatching { CommunitySocial.encodeTrade(bad) }.isFailure)
        assertTrue(runCatching { CommunitySocial.decodeTrade(" ".repeat(8001)) }.isFailure)
    }
}
