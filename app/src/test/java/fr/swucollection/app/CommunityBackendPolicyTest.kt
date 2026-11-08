package fr.swucollection.app

import org.junit.Assert.*
import org.junit.Test

class CommunityBackendPolicyTest {
    @Test fun channelsCannotUseEachOthersAccounts() {
        assertTrue(CommunityBackendPolicy.accepts(AppChannel.OFFICIAL_PACKAGE, "swu-collection-official", "key", "app"))
        assertFalse(CommunityBackendPolicy.accepts(AppChannel.OFFICIAL_PACKAGE, "swu-collection-test", "key", "app"))
        assertTrue(CommunityBackendPolicy.accepts(AppChannel.TEST_PACKAGE, "swu-collection-test", "key", "app"))
        assertFalse(CommunityBackendPolicy.accepts(AppChannel.TEST_PACKAGE, "swu-collection-official", "key", "app"))
        assertFalse(CommunityBackendPolicy.accepts("other.app", "swu-collection-official", "key", "app"))
        assertFalse(CommunityBackendPolicy.accepts(AppChannel.OFFICIAL_PACKAGE, "swu-collection-official", "", "app"))
    }
    @Test fun bothPackagesExposeTheSameExperienceWithoutSharingUpdateFeeds() {
        assertTrue(AppChannel.supportsExperience(AppChannel.OFFICIAL_PACKAGE))
        assertTrue(AppChannel.supportsExperience(AppChannel.TEST_PACKAGE))
        assertFalse(AppChannel.supportsExperience("other.app"))
        assertTrue(AppChannel.allowsOfficialUpdates(AppChannel.OFFICIAL_PACKAGE))
        assertFalse(AppChannel.allowsOfficialUpdates(AppChannel.TEST_PACKAGE))
    }
}
