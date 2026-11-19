package fr.swucollection.app

import org.junit.Assert.*
import org.junit.Test

class PlaystorePolicyTest {
    @Test fun standaloneEditionKeepsModernLayoutWithoutAccountOrSideloadUpdates() {
        val navigation = AppNavigation()
        listOf("Communauté", "Messagerie", "Échanges").forEach { navigation.navigateTo(it) }
        assertEquals("Accueil", navigation.current.section)
        assertFalse(BuildConfig.SOCIAL_FEATURES)
        assertEquals(AppChannel.PLAYSTORE_PACKAGE, BuildConfig.APPLICATION_ID)
        assertTrue(AppChannel.supportsExperience(BuildConfig.APPLICATION_ID))
        listOf(AppChannel.PLAYSTORE_PACKAGE, AppChannel.OFFICIAL_PACKAGE, AppChannel.TEST_PACKAGE).forEach {
            assertFalse(AppChannel.supportsCommunity(it))
        }
        assertFalse(AppChannel.supportsUpdateChecks(BuildConfig.APPLICATION_ID))
        assertFalse(AppChannel.allowsOfficialUpdates(BuildConfig.APPLICATION_ID))
        assertFalse(CommunityBackendPolicy.accepts(BuildConfig.APPLICATION_ID, "swu-collection-official", "key", "id"))
        assertFalse(CommunityBackendPolicy.accepts(BuildConfig.APPLICATION_ID, "swu-collection-test", "key", "id"))
        assertFalse(bottomShortcutIcons.keys.any { it in setOf("Communauté", "Messagerie", "Échanges") })
        assertFalse(UnifiedCollection.visibleHome(HomeLayoutStore.defaults).contains("ÉCHANGES"))
    }
}
