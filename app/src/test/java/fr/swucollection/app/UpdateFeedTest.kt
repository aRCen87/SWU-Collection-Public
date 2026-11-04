package fr.swucollection.app

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class UpdateFeedTest {
    private fun feed(tag: String = "v0.3.2"): JSONObject = JSONObject()
        .put("tag_name", tag).put("body", "Nouveautés")
        .put("draft", false).put("prerelease", false)
        .put("assets", JSONArray().put(JSONObject()
            .put("name", "SWU-Collection.apk")
            .put("browser_download_url", "https://github.com/aRCen87/SWU-Collection-Releases/releases/download/$tag/SWU-Collection.apk")
            .put("digest", "sha256:" + "a".repeat(64)).put("size", 58_000_000)))

    private fun rejected(json: JSONObject) {
        try { UpdateFeed.parse(json.toString(), "0.3.1"); fail("Unsafe update accepted") }
        catch (_: IllegalArgumentException) { }
    }

    @Test fun versionsUseNumericOrderingAndNeverDowngrade() {
        assertTrue(UpdateFeed.isNewer("v0.10.0", "0.9.9"))
        assertFalse(UpdateFeed.isNewer("v0.3.1", "0.3.1"))
        assertFalse(UpdateFeed.isNewer("v0.2.0", "0.3.1"))
        assertFalse(UpdateFeed.isNewer("v0.4.0-beta", "0.3.1"))
        assertFalse(UpdateFeed.isNewer("v999999999999.0.0", "0.3.1"))
    }
    @Test fun stableReleaseContainsVerifiedAssetAndNotes() {
        val release = UpdateFeed.parse(feed().toString(), "0.3.1")!!
        assertEquals("0.3.2", release.version)
        assertEquals(58_000_000L, release.size)
        assertEquals("Nouveautés", release.notes)
        assertEquals("a".repeat(64), release.sha256)
    }
    @Test fun draftPrereleaseAndOldReleaseAreIgnored() {
        assertNull(UpdateFeed.parse(feed().put("draft", true).toString(), "0.3.1"))
        assertNull(UpdateFeed.parse(feed().put("prerelease", true).toString(), "0.3.1"))
        assertNull(UpdateFeed.parse(feed("v0.2.0").toString(), "0.3.1"))
    }
    @Test fun missingDigestCannotBeInstalled() {
        val json = feed(); json.getJSONArray("assets").getJSONObject(0).remove("digest")
        rejected(json)
    }
    @Test fun foreignRepositoryAndNonHttpsAreRejected() {
        for (url in listOf(
            "https://github.com/other/project/releases/download/v0.3.2/SWU-Collection.apk",
            "http://github.com/aRCen87/SWU-Collection-Releases/releases/download/v0.3.2/SWU-Collection.apk",
            "https://github.com.evil.test/aRCen87/SWU-Collection-Releases/releases/download/v0.3.2/SWU-Collection.apk")) {
            val json = feed(); json.getJSONArray("assets").getJSONObject(0).put("browser_download_url", url)
            rejected(json)
        }
    }
    @Test fun duplicateMissingAndOversizeAssetsAreRejected() {
        val duplicate = feed(); val assets = duplicate.getJSONArray("assets")
        assets.put(JSONObject(assets.getJSONObject(0).toString())); rejected(duplicate)
        rejected(feed().put("assets", JSONArray()))
        val large = feed(); large.getJSONArray("assets").getJSONObject(0).put("size", UpdateFeed.MAX_APK_BYTES + 1)
        rejected(large)
    }
}
