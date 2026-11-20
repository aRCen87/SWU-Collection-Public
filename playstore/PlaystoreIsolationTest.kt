package fr.swucollection.app

import org.junit.Assert.*
import org.junit.Test

class PlaystoreIsolationTest {
    @Test fun playstoreHasModernLayoutAndCannotUseApkFeeds() {
        val id = "fr.swucollection.app.playstore"
        assertTrue(AppChannel.supportsExperience(id))
        assertFalse(AppChannel.supportsUpdateChecks(id))
        assertFalse(AppChannel.allowsOfficialUpdates(id))
    }
    @Test fun shortcutsCannotExposeAccountsOrMessaging() {
        assertFalse(validBottomShortcuts(listOf("Communauté")))
        assertFalse(validBottomShortcuts(listOf("Messagerie")))
        assertTrue(validBottomShortcuts(listOf("Collection", "Mes decks", "Mes proxies")))
    }
}
