package fr.swucollection.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Matrix
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
    private fun matches(name: String, landscape: Boolean, rotation: Int = 0,
        cameraWidth: Int = 720, cameraHeight: Int = 1000, automatic: Boolean = false): List<VisualCardMatch> {
        val card = javaClass.getResourceAsStream("/scan/$name.jpg").use { BitmapFactory.decodeStream(it) }
        val frame = Bitmap.createBitmap(cameraWidth,cameraHeight,Bitmap.Config.ARGB_8888)
        frame.eraseColor(Color.DKGRAY)
        val ratio = if(landscape) 88f/63 else 63f/88
        val scale = maxOf(720f/cameraWidth,1000f/cameraHeight)
        val h=min(1000f*.78f,720f*.9f/ratio)/scale;val w=h*ratio
        Canvas(frame).drawBitmap(card,null,RectF((cameraWidth-w)/2,(cameraHeight-h)/2,
            (cameraWidth+w)/2,(cameraHeight+h)/2),Paint(Paint.FILTER_BITMAP_FLAG))
        val sensor = if (rotation == 0) frame else Bitmap.createBitmap(frame,0,0,frame.width,frame.height,
            Matrix().apply { postRotate(-rotation.toFloat()) },true)
        return try {
            val matcher = ScanVisualMatcher(RuntimeEnvironment.getApplication())
            if (automatic) matcher.matchAutomatic(sensor,rotation,ScanViewport(720,1000,false))
            else matcher.match(sensor,rotation,ScanViewport(720,1000,landscape))
        }
        finally { card.recycle();if(sensor !== frame) sensor.recycle();frame.recycle() }
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
    @Test fun recognizesBothFacesOfALeader() {
        assertTrue(matches("leader-0",true).any { it.reference == ScanReference("SOR","001") })
        assertTrue(matches("leader-1",false).any { it.reference == ScanReference("SOR","001") })
    }
    @Test fun cameraRotationAndFillCenterCroppingPreserveTheCard() {
        for (rotation in listOf(0,90,180,270)) {
            val result = matches("portrait",false,rotation,1200,1600)
            assertTrue("Rotation $rotation candidates=${result.map { it.reference to it.score }}", result
                .any { it.reference == ScanReference("SOR","236") })
        }
    }
    @Test fun emptyCameraBackgroundDoesNotOfferAnArbitraryCard() {
        val frame = Bitmap.createBitmap(720,1000,Bitmap.Config.ARGB_8888)
        try {
            frame.eraseColor(Color.DKGRAY)
            assertTrue(ScanVisualMatcher(RuntimeEnvironment.getApplication())
                .match(frame,0,ScanViewport(720,1000,false)).isEmpty())
        } finally { frame.recycle() }
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
    @Test fun automaticLayoutFindsNormalCardsBasesAndBothLeaderFacesWithoutATypeButton() {
        for ((name, landscape, number) in listOf(Triple("portrait",false,"236"),Triple("base",true,"029"),
            Triple("leader-0",true,"001"),Triple("leader-1",false,"001"))) {
            val result = matches(name,landscape,automatic=true)
            if (result.none { it.reference == ScanReference("SOR",number) }) {
                val guided = matches(name,landscape)
                fail("$name automatic=${result.map { Triple(it.reference,it.score,it.landscape) }} guided=${guided.map { Triple(it.reference,it.score,it.landscape) }}")
            }
        }
    }
}
