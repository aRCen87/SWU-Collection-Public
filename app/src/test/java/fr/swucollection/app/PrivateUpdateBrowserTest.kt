package fr.swucollection.app

import android.content.Intent
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PrivateUpdateBrowserTest {
    private val page = "https://chatgpt.com/library?file_id=libfile_example&account_id=personal"
    @Test fun usesARegularBrowserPageWithoutCustomTabSessionOrInjectedReferrer() {
        val intent = PrivateUpdateBrowser.intent(page, "com.android.chrome")
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(page, intent.dataString)
        assertEquals("com.android.chrome", intent.`package`)
        assertTrue(intent.hasCategory(Intent.CATEGORY_BROWSABLE))
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertFalse(intent.hasExtra("android.support.customtabs.extra.SESSION"))
        assertFalse(intent.hasExtra(Intent.EXTRA_REFERRER))
    }
    @Test fun anHttpsHandlerCanBeChosenWhenNoCustomTabsBrowserExists() {
        val intent = PrivateUpdateBrowser.intent(page)
        assertEquals(page, intent.dataString)
        assertNull(intent.`package`)
    }
    @Test fun directApiAndUntrustedTargetsAreRejectedBeforeOpeningABrowser() {
        for (url in listOf("https://chatgpt.com/api/library/files/libfile_example/download",
            "https://evil.test/library?file_id=libfile_example&account_id=personal")) {
            assertTrue(runCatching { PrivateUpdateBrowser.intent(url) }.isFailure)
        }
    }
}
