package fr.swucollection.app

import org.junit.Assert.*
import org.junit.Test

class MemberDirectoryTest {
    @Test fun directoryIncludesAllPagesAndSearchesPseudosWithoutShowingHiddenOrOwnProfiles() {
        val members = (1..120).map { PlayerProfile("$it", "Joueur $it", discoverable = true) }
        assertEquals(119, matchingMembers(members, "1", "").size)
        assertEquals(listOf("120"), matchingMembers(members, "1", "  JOUEUR 120 ").map { it.id })
        val hidden = PlayerProfile("hidden", "Joueur caché", discoverable = false)
        assertFalse(matchingMembers(members + hidden, "1", "").any { it.id == "hidden" })
        assertEquals(119, matchingMembers(members + members, "1", "").size)
    }
}
