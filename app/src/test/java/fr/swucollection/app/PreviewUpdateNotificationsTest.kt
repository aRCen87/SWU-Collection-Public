package fr.swucollection.app

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class PreviewUpdateNotificationsTest {
    private fun context(packageId: String): Context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
        override fun getPackageName() = packageId
    }
    private fun release() = PreviewRelease(63, "0.8.19-test.4", "Essais privés",
        "https://chatgpt.com/library?file_id=libfile_example&account_id=personal", "a".repeat(64), 83000000)
    @Test fun notificationRequestsOnePrivateDownloadAndDeduplicates() {
        val context = context(AppChannel.TEST_PACKAGE)
        context.getSharedPreferences("private_preview_updates", Context.MODE_PRIVATE).edit().clear().commit()
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancelAll()
        assertTrue(PreviewUpdateNotifications.offer(context, 62, release()))
        val notification = manager.activeNotifications.single().notification
        assertEquals("SWU Collection Test : mise à jour", notification.extras.getString(Notification.EXTRA_TITLE))
        val click = Shadows.shadowOf(notification.contentIntent).savedIntent
        assertEquals(UpdateNotifications.ACTION_DOWNLOAD_TEST_UPDATE, click.action)
        assertFalse(PreviewUpdateNotifications.consumeDownloadRequest(context, android.content.Intent(UpdateNotifications.ACTION_DOWNLOAD_TEST_UPDATE)))
        assertTrue(PreviewUpdateNotifications.consumeDownloadRequest(context, click))
        assertFalse(PreviewUpdateNotifications.consumeDownloadRequest(context, click))
        assertFalse(PreviewUpdateNotifications.offer(context, 62, release()))
        assertEquals(1, manager.activeNotifications.size)
    }
    @Test fun cachedPrivateVersionIsAvailableToTheBannerButNeverToTheOfficialPackage() {
        val testContext = context(AppChannel.TEST_PACKAGE)
        val info = android.content.pm.PackageInfo().apply {
            packageName = AppChannel.TEST_PACKAGE; versionCode = 62; versionName = "0.8.19-test.3"
            applicationInfo = android.content.pm.ApplicationInfo().apply { packageName = AppChannel.TEST_PACKAGE }
        }
        Shadows.shadowOf(testContext.packageManager).installPackage(info)
        testContext.getSharedPreferences("private_preview_updates", Context.MODE_PRIVATE).edit()
            .putString("available_release", PreviewUpdateFeed.encode(release())).commit()
        val database = CollectionDatabase(testContext)
        try { assertEquals("0.8.19-test.4", AppUpdates(testContext, database).availableVersion) } finally { database.close() }
        val official = context(AppChannel.OFFICIAL_PACKAGE)
        info.packageName = AppChannel.OFFICIAL_PACKAGE
        info.applicationInfo = android.content.pm.ApplicationInfo().apply { packageName = AppChannel.OFFICIAL_PACKAGE }
        Shadows.shadowOf(official.packageManager).installPackage(info)
        val officialDatabase = CollectionDatabase(official)
        try { assertNull(AppUpdates(official, officialDatabase).availableVersion) } finally { officialDatabase.close() }
    }
    @Test fun officialPackageCannotOfferPrivateUpdates() {
        assertFalse(PreviewUpdateNotifications.offer(context(AppChannel.OFFICIAL_PACKAGE), 62, release()))
    }
}
