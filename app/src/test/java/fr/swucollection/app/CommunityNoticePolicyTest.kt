package fr.swucollection.app

import org.junit.Assert.*
import org.junit.Test

class CommunityNoticePolicyTest {
    @Test fun onlyUnreadIncomingMessagesCreateNotices() {
        val threads = listOf(PlayerConversation("incoming", listOf("alice", "bob"), 200, "bob"),
            PlayerConversation("read", listOf("alice", "carol"), 100, "carol"),
            PlayerConversation("mine", listOf("alice", "dave"), 300, "alice"),
            PlayerConversation("empty", listOf("alice", "eve"), 400, ""))
        assertEquals(listOf(CommunityNotice("message:incoming", "Messages", 200)),
            CommunityNoticePolicy.messages("alice", threads, mapOf("read" to 100L)))
        assertTrue(CommunityNoticePolicy.messages("alice", threads.take(1), mapOf("incoming" to 200L)).isEmpty())
    }
    @Test fun pollingDoesNotRepeatANotificationAndNewActivityCanNotifyAgain() {
        val notice = CommunityNotice("friend:alice__bob", "Amis", 300)
        assertTrue(CommunityNoticePolicy.shouldNotify(notice, 0))
        assertFalse(CommunityNoticePolicy.shouldNotify(notice, 300))
        assertFalse(CommunityNoticePolicy.shouldNotify(notice, 400))
        assertTrue(CommunityNoticePolicy.shouldNotify(notice.copy(version = 500), 300))
        assertFalse(CommunityNoticePolicy.shouldNotify(notice.copy(version = 0), 0))
    }
}
