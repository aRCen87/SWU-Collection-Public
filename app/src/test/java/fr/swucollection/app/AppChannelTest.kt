package fr.swucollection.app

import org.junit.Assert.*
import org.junit.Test

class AppChannelTest {
    @Test fun onlyOfficialIdentityMayUsePublicUpdates() {
        assertTrue(AppChannel.allowsOfficialUpdates("fr.swucollection.app"))
        assertFalse(AppChannel.allowsOfficialUpdates("fr.swucollection.app.test"))
        assertFalse(AppChannel.allowsOfficialUpdates("fr.swucollection.app.test.debug"))
        assertFalse(AppChannel.allowsOfficialUpdates("other.app"))
    }
}
