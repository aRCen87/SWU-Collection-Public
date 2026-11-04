package fr.swucollection.app

import org.junit.Assert.*
import org.junit.Test

class UpdateNotificationPolicyTest {
    @Test fun notifiesOnlyANewStableVersionOnce() {
        assertTrue(UpdateNotificationPolicy.shouldNotify("0.3.3", "0.3.4", null))
        assertFalse(UpdateNotificationPolicy.shouldNotify("0.3.3", "0.3.4", "0.3.4"))
        assertTrue(UpdateNotificationPolicy.shouldNotify("0.3.3", "0.3.5", "0.3.4"))
        assertFalse(UpdateNotificationPolicy.shouldNotify("0.3.3", "0.3.4", "0.3.5"))
        assertFalse(UpdateNotificationPolicy.shouldNotify("0.3.4", "0.3.4", null))
        assertFalse(UpdateNotificationPolicy.shouldNotify("0.3.4", "0.3.3", null))
        assertFalse(UpdateNotificationPolicy.shouldNotify("0.3.4", "0.3.5-beta", null))
    }
}
