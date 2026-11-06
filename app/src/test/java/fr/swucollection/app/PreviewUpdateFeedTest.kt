package fr.swucollection.app

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PreviewUpdateFeedTest {
    private fun announcement(code: Long = 63) = JSONObject().put("channel", "private-test")
        .put("applicationId", "fr.swucollection.app.test").put("versionCode", code)
        .put("versionName", "0.8.19-test.4").put("notes", "Essais privés")
        .put("privateDownloadUrl", "https://chatgpt.com/library?file_id=libfile_example&account_id=personal")
        .put("sha256", "a".repeat(64)).put("size", 83000000)
    @Test fun onlyNewerAndroidCodesAreOffered() {
        assertEquals(63L, PreviewUpdateFeed.parse(announcement().toString(), 62)!!.code)
        assertNull(PreviewUpdateFeed.parse(announcement().toString(), 63))
        assertNull(PreviewUpdateFeed.parse(announcement().toString(), 64))
        val release = PreviewUpdateFeed.parse(announcement().toString(), 62)!!
        assertEquals(release, PreviewUpdateFeed.parse(PreviewUpdateFeed.encode(release), 62))
    }
    @Test fun stableAndForeignAnnouncementsAreRejected() {
        for ((key, value) in listOf("channel" to "stable", "applicationId" to "fr.swucollection.app", "versionName" to "0.8.20")) {
            assertTrue(runCatching { PreviewUpdateFeed.parse(announcement().put(key, value).toString(), 62) }.isFailure)
        }
    }
    @Test fun linksCannotEscapeThePrivateRecipientLibrary() {
        for (url in listOf("http://chatgpt.com/library?file_id=libfile_x&account_id=personal",
            "https://chatgpt.com.evil.test/library?file_id=libfile_x&account_id=personal",
            "https://chatgpt.com@evil.test/library?file_id=libfile_x&account_id=personal",
            "https://github.com/owner/repo/releases/download/test/Test.apk",
            "https://chatgpt.com/library?file_id=libfile_x&account_id=personal&redirect=elsewhere")) {
            assertTrue(runCatching { PreviewUpdateFeed.parse(announcement().put("privateDownloadUrl", url).toString(), 62) }.isFailure)
        }
    }
    @Test fun malformedAndUnboundedAnnouncementsAreRejected() {
        for ((key,value) in listOf("sha256" to "bad", "size" to 0, "size" to 210000000,
            "versionCode" to -1, "notes" to "a".repeat(6001))) {
            assertTrue(runCatching { PreviewUpdateFeed.parse(announcement().put(key,value).toString(), 62) }.isFailure)
        }
        assertTrue(runCatching { PreviewUpdateFeed.parse("a".repeat(65537), 62) }.isFailure)
    }
    @Test fun notificationsUseCodesAndNeverRepeatAnAlreadyOfferedVersion() {
        assertTrue(PreviewUpdatePolicy.shouldNotify(62, 63, 0))
        assertFalse(PreviewUpdatePolicy.shouldNotify(62, 63, 63))
        assertFalse(PreviewUpdatePolicy.shouldNotify(63, 63, 0))
        assertFalse(PreviewUpdatePolicy.shouldNotify(64, 63, 0))
        assertTrue(PreviewUpdatePolicy.shouldNotify(62, 64, 63))
    }
    @Test fun onlyKnownPackagesMayScheduleChecksAndTestNeverInstallsOfficialFeed() {
        assertTrue(AppChannel.supportsUpdateChecks(AppChannel.OFFICIAL_PACKAGE))
        assertTrue(AppChannel.supportsUpdateChecks(AppChannel.TEST_PACKAGE))
        assertFalse(AppChannel.supportsUpdateChecks("fr.swucollection.app.test.debug"))
        assertFalse(AppChannel.allowsOfficialUpdates(AppChannel.TEST_PACKAGE))
    }
}
