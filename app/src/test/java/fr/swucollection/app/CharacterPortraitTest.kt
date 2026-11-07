package fr.swucollection.app

import android.graphics.BitmapFactory
import android.util.Base64
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CharacterPortraitTest {
    @Test fun everySelectablePortraitLoadsOfflineAndFitsTheProfileUploadLimit() {
        val context = RuntimeEnvironment.getApplication()
        assertEquals(36, CommunitySocial.characters.map { it.cardId }.distinct().size)
        for (entry in CommunitySocial.characters) {
            val raw = CharacterPortrait.encoded(context, entry.cardId)
            assertTrue(entry.name, raw.length <= 18000)
            val bytes = Base64.decode(raw.removePrefix("jpeg:"), Base64.NO_WRAP)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            assertNotNull(entry.name, bitmap)
            assertEquals(128, bitmap.width)
            assertEquals(128, bitmap.height)
            bitmap.recycle()
        }
    }
}
