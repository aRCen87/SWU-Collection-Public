package fr.swucollection.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.min

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScanVisualMatcherTest {
    private fun matches(name: String, landscape: Boolean): List<VisualCardMatch> {
        val card = javaClass.getResourceAsStream("/scan/$name.jpg").use { BitmapFactory.decodeStream(it) }
        val frame = Bitmap.createBitmap(720,1000,Bitmap.Config.ARGB_8888)
        frame.eraseColor(Color.DKGRAY)
        val ratio = if(landscape) 88f/63 else 63f/88
        val h=min(1000f*.78f,720f*.9f/ratio);val w=h*ratio
        Canvas(frame).drawBitmap(card,null,RectF((720-w)/2,(1000-h)/2,(720+w)/2,(1000+h)/2),Paint(Paint.FILTER_BITMAP_FLAG))
        return try { ScanVisualMatcher(RuntimeEnvironment.getApplication()).match(frame,0,ScanViewport(720,1000,landscape)) }
        finally { card.recycle();frame.recycle() }
    }
    @Test fun recognizesAPortraitCardInsideTheCameraGuide() {
        assertTrue(matches("portrait",false).any { it.reference == ScanReference("SOR","236") })
    }
    @Test fun englishArtworkCanMatchAFrenchReferenceWithoutAssigningALanguage() {
        assertTrue(matches("english",false).any { it.reference == ScanReference("SOR","236") })
    }
    @Test fun recognizesALandscapeBaseInsideTheCameraGuide() {
        assertTrue(matches("base",true).any { it.reference == ScanReference("SOR","029") })
    }
    @Test fun retainsTheExactHyperspaceReferenceAmongVisualSuggestions() {
        assertTrue(matches("variant",false).any { it.reference == ScanReference("SOR","317") })
    }
    @Test fun fallbackArtworkDoesNotChangeTheSelectedCardLanguage() {
        val context = RuntimeEnvironment.getApplication()
        val card = CatalogCard("SOR","273","Empereur Palpatine","Leader","Rare",language="FR")
        val exact = CardLanguageArtwork.localized(context,card,"FR")
        val display = CardLanguageArtwork.displayFace(context,card,"FR")
        assertEquals("FR",exact.language)
        assertTrue(display.first.startsWith("https://cdn.starwarsunlimited.com/"))
        assertEquals("EN",display.second)
        assertEquals("FR",card.language)
    }
}
