package fr.swucollection.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import com.google.android.gms.tasks.TaskCompletionSource
import java.util.concurrent.Executor
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.min
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScanOcrRegionsTest {
    private val direct = Executor { it.run() }

    @Test fun asynchronousOcrKeepsBothCropsAliveAndPreservesEachSource() {
        val title = Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888)
        val footer = Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888)
        val first = TaskCompletionSource<String>(); val second = TaskCompletionSource<String>()
        val task = ScanOcrRegions.read(title to footer,"SOR FR 001",direct,{true}) { crop ->
            assertFalse(crop.isRecycled)
            if (crop === title) first.task else second.task
        }
        assertFalse(task.isComplete)
        first.setResult("Directeur Krennic")
        assertFalse(title.isRecycled);assertFalse(footer.isRecycled)
        second.setResult("SHD FR 002")
        assertTrue(task.isComplete)
        val result = task.result
        assertEquals("Directeur Krennic",result.title)
        assertNull(result.reference)
        assertTrue(title.isRecycled);assertTrue(footer.isRecycled)
    }

    @Test fun pausingAfterTheTitleSkipsTheFooterAndReleasesBothCrops() {
        val title = Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888)
        val footer = Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888)
        val first = TaskCompletionSource<String>()
        var active = true; var calls = 0
        val task = ScanOcrRegions.read(title to footer,"",direct,{active}) { calls++;first.task }
        active = false;first.setResult("Directeur Krennic")
        assertEquals(1,calls)
        assertNull(task.result.reference)
        assertTrue(title.isRecycled);assertTrue(footer.isRecycled)
    }

    @Test fun aFailedRegionalReadKeepsTheOriginalEvidenceAndReleasesImages() {
        val title = Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888)
        val footer = Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888)
        val task = ScanOcrRegions.read(title to footer,"SOR EN 236",direct,{true}) {
            throw IllegalStateException("OCR unavailable")
        }
        assertEquals(ScanReference("SOR","236"),task.result.reference)
        assertTrue(title.isRecycled);assertTrue(footer.isRecycled)
    }

    @Test fun readsSeparateNativeResolutionTitleAndFooterForBothCardFormatsAndSensorRotations() {
        for (landscape in listOf(false,true)) for (rotation in listOf(0,90,180,270)) {
            val frame = Bitmap.createBitmap(1200,1600,Bitmap.Config.ARGB_8888)
            frame.eraseColor(Color.DKGRAY)
            val scale = maxOf(720.0/1200,1000.0/1600)
            val ratio = if (landscape) 88.0/63 else 63.0/88
            val h = (min(1000*.78,720*.9/ratio)/scale).roundToInt()
            val w = (h*ratio).roundToInt()
            val left = (1200-w)/2; val top = (1600-h)/2
            val canvas = Canvas(frame); val paint = Paint()
            paint.color = Color.RED
            canvas.drawRect(left.toFloat(),top.toFloat(),(left+w).toFloat(),(top+h*.4).toFloat(),paint)
            paint.color = Color.BLUE
            canvas.drawRect(left.toFloat(),(top+h*.7).toFloat(),(left+w).toFloat(),(top+h).toFloat(),paint)
            val sensor = if (rotation == 0) frame else Bitmap.createBitmap(frame,0,0,1200,1600,
                Matrix().apply { postRotate(-rotation.toFloat()) },true)
            try {
                val (title,footer) = ScanOcrRegions.crops(sensor,rotation,ScanViewport(720,1000,false),landscape,0)!!
                try {
                    assertEquals(w,title.width)
                    assertEquals(w,footer.width)
                    assertEquals(Color.RED,title.getPixel(title.width/2,title.height/2))
                    assertEquals(Color.BLUE,footer.getPixel(footer.width/2,footer.height/2))
                    assertFalse(sensor.isRecycled)
                } finally { title.recycle();footer.recycle() }
            } finally { if (sensor !== frame) sensor.recycle();frame.recycle() }
        }
    }

    @Test fun anUnmeasuredViewportDoesNotProduceMisleadingCrops() {
        val frame = Bitmap.createBitmap(100,100,Bitmap.Config.ARGB_8888)
        try { assertNull(ScanOcrRegions.crops(frame,0,ScanViewport(0,0,false),false,0)) }
        finally { frame.recycle() }
    }
}
