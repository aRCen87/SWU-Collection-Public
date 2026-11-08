package fr.swucollection.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImmersiveArtworkTest {
    @Test fun sourceLayersCoverPhoneFoldAndLandscapeAtMotionExtremes() {
        val art=BitmapFactory.decodeResource(RuntimeEnvironment.getApplication().resources,R.drawable.home_premium_space)
        val painter=PremiumSpacePainter(art)
        listOf(360 to 270,750 to 270,960 to 270).forEach { (w,h) ->
            listOf(0f,12f,40f,80f,120f).forEach {t ->
                val bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
                painter.draw(Canvas(bitmap),w.toFloat(),h.toFloat(),t)
                listOf(0 to 0,w-1 to 0,0 to h-1,w-1 to h-1).forEach { (x,y) ->
                    val color=bitmap.getPixel(x,y);assertEquals(255,color ushr 24);assertTrue(color and 0xFFFFFF != 0)
                }
                bitmap.recycle()
            }
        }
        val out=File("build/reports/home-test21").apply {mkdirs()}
        listOf(0f,12f).forEach {t ->val b=Bitmap.createBitmap(720,540,Bitmap.Config.ARGB_8888)
            val c=Canvas(b);painter.draw(c,720f,540f,t)
            c.save();c.translate(164f,40f);PremiumLogoPainter().draw(c,392f,236f,1f,if(t==0f) 0f else .5f);c.restore()
            File(out,"header-${t.toInt()}.png").outputStream().use {b.compress(Bitmap.CompressFormat.PNG,100,it)}
            b.recycle()
        }
    }
}
