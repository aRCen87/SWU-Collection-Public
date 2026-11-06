package fr.swucollection.app

import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class AutomaticAccountStateTest {
    @Test fun baselinesContainOnlyAnOwnerPointerAndHashWhilePreferencesRemainPerAccount() {
        val context = RuntimeEnvironment.getApplication() as Context
        val prefs = context.getSharedPreferences("automatic_account_backup", 0)
        prefs.edit().clear().commit()
        AutomaticAccountBackup.acknowledge(context, "alice", "snapshot-a", "private collection payload")
        assertEquals("alice", prefs.getString("device_owner", null))
        assertEquals("snapshot-a", prefs.getString("alice.head", null))
        assertEquals(PrivatePayload.hash("private collection payload".toByteArray()), prefs.getString("alice.hash", null))
        assertFalse(prefs.all.values.any { it == "private collection payload" })
        AutomaticAccountBackup.setEnabled(context, "alice", false)
        assertFalse(AutomaticAccountBackup.enabled(context, "alice"))
        assertTrue(AutomaticAccountBackup.enabled(context, "bob"))
        assertFalse(prefs.getBoolean("bob.initialized", false))
        prefs.edit().clear().commit()
    }
}
