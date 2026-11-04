package fr.swucollection.app

import android.app.NotificationManager
import android.content.Context
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class UpdateSafetyTest {
    private lateinit var context: Context
    private lateinit var database: CollectionDatabase
    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("swu_collection.db")
        database = CollectionDatabase(context)
        context.getSharedPreferences("leader_view", Context.MODE_PRIVATE).edit().putString("mode", "stack").commit()
        database.addOrIncrement("Éclairs de Force", "SOR", "138", "Foil")
        database.addOrIncrement("Éclairs de Force", "SOR", "138", "Foil")
        database.addOrIncrement("Asajj Ventress", "HMW", "001", "Normale")
    }
    @After fun cleanup() { database.close(); context.deleteDatabase("swu_collection.db") }
    private fun quantities() = database.allCards().associate { "${it.setCode}:${it.number}:${it.variant}" to it.quantity }

    @Test fun databaseReopenKeepsCardsQuantitiesAccentsAndPreferences() {
        val before = quantities()
        database.close(); database = CollectionDatabase(context)
        assertEquals(before, quantities())
        assertEquals("Éclairs de Force", database.allCards().first { it.number == "138" }.name)
        assertEquals("stack", context.getSharedPreferences("leader_view", Context.MODE_PRIVATE).getString("mode", null))
    }
    @Test fun atomicBackupRestoresExactQuantitiesAndSettingsWithoutAddingDuplicates() {
        val before = quantities()
        UpdateBackups.create(context, database)
        assertTrue(UpdateBackups.exists(context))
        assertTrue(UpdateBackups.read(context).contains("Éclairs de Force"))
        database.addOrIncrement("Éclairs de Force", "SOR", "138", "Foil")
        database.addOrIncrement("Nouvelle carte", "SHD", "001", "Normale")
        context.getSharedPreferences("leader_view", Context.MODE_PRIVATE).edit().putString("mode", "flip").commit()
        assertEquals(2, UpdateBackups.restore(context, database))
        assertEquals(before, quantities())
        assertEquals("stack", context.getSharedPreferences("leader_view", Context.MODE_PRIVATE).getString("mode", null))
        UpdateBackups.restore(context, database)
        assertEquals(before, quantities())
    }
    @Test fun invalidSnapshotCannotEraseTheExistingCollection() {
        val before = quantities()
        val root = JSONObject(database.exportJson())
        root.getJSONArray("cards").getJSONObject(0).put("quantity", -1)
        try { database.importJson(root.toString(), replace = true); fail("Invalid backup accepted") }
        catch (_: IllegalArgumentException) { }
        assertEquals(before, quantities())
    }
    @Test fun legacyExternalImportStillMergesWithoutReplacingOtherCards() {
        val backup = database.exportJson()
        database.addOrIncrement("Nouvelle carte", "SHD", "001", "Normale")
        database.importJson(backup)
        assertEquals(4, quantities().getValue("SOR:138:Foil"))
        assertEquals(1, quantities().getValue("SHD:001:Normale"))
    }
    @Test fun deniedOrRepeatedVersionDoesNotCreateRepeatedNotifications() {
        val release = AppRelease("0.3.4", "Nouveautés", "https://github.com/aRCen87/SWU-Collection-Releases/releases/download/v0.3.4/SWU-Collection.apk", "a".repeat(64), 100)
        context.getSharedPreferences("application_updates", Context.MODE_PRIVATE).edit().clear().commit()
        val notificationShadow = Shadows.shadowOf(context.getSystemService(NotificationManager::class.java))
        notificationShadow.setNotificationsEnabled(false)
        assertFalse(UpdateNotifications.offer(context, "0.3.3", release))
        notificationShadow.setNotificationsEnabled(true)
        assertTrue(UpdateNotifications.offer(context, "0.3.3", release))
        assertFalse(UpdateNotifications.offer(context, "0.3.3", release))
        val manager = context.getSystemService(NotificationManager::class.java)
        assertEquals(1, manager.activeNotifications.size)
        val notification = manager.activeNotifications.single().notification
        assertEquals(UpdateNotifications.ACTION_OPEN_UPDATES, Shadows.shadowOf(notification.contentIntent).savedIntent.action)
        UpdateNotifications.clearInstalled(context, "0.3.4")
        assertEquals(0, manager.activeNotifications.size)
    }
    @Test fun availableReleaseCacheSurvivesReopeningButNeverOffersADowngrade() {
        val release = AppRelease("0.3.4", "Nouveautés", "https://github.com/aRCen87/SWU-Collection-Releases/releases/download/v0.3.4/SWU-Collection.apk", "a".repeat(64), 100)
        UpdateReleaseCache.save(context, release)
        assertEquals(release, UpdateReleaseCache.load(context, "0.3.3"))
        assertNull(UpdateReleaseCache.load(context, "0.3.4"))
        assertNull(UpdateReleaseCache.load(context, "0.4.0"))
    }
}
