package fr.swucollection.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as NativeCanvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext

/** Draw source layers on one canvas; bitmap, paths and blending masks are retained between frames. */
internal class PremiumSpacePainter(private val art: Bitmap) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val moonClip=Path().apply {addCircle(185f,661f,32f,Path.Direction.CW)}
    private val patch=Bitmap.createBitmap(96,96,Bitmap.Config.ARGB_8888).also { b ->
        val canvas=NativeCanvas(b)
        canvas.drawBitmap(art,-(80f-48f),-(661f-48f),paint)
        val mask=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader=RadialGradient(48f,48f,48f,intArrayOf(-1,-1,0x00FFFFFF),floatArrayOf(0f,.72f,1f),Shader.TileMode.CLAMP)
            xfermode=PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        canvas.drawRect(0f,0f,96f,96f,mask)
    }
    fun draw(canvas: NativeCanvas,width: Float,height: Float,seconds: Float) {
        val orbit=PremiumOrbit.frame(seconds)
        val fit=PremiumOrbit.fit(width,height,art.width.toFloat(),art.height.toFloat())
        val scale=fit.scale
        val save=canvas.save()
        canvas.translate(fit.left+orbit.planetX*width, fit.top+orbit.planetY*height)
        canvas.scale(scale,scale)
        canvas.drawBitmap(art,0f,0f,paint)
        // Cover only the baked moon with neighbouring star field, feathered outside its limb.
        canvas.drawBitmap(patch,185f-48f,661f-48f,paint)
        val moonSave=canvas.save()
        canvas.translate(orbit.moonX*art.width,orbit.moonY*art.height)
        canvas.clipPath(moonClip)
        canvas.drawBitmap(art,0f,0f,paint)
        canvas.restoreToCount(moonSave)
        canvas.restoreToCount(save)
    }
}

@Composable
internal fun PremiumSpaceArtwork(time: State<Float>, modifier: Modifier = Modifier) {
    val resources=LocalContext.current.resources
    val painter=remember(resources) {PremiumSpacePainter(BitmapFactory.decodeResource(resources,R.drawable.home_premium_space))}
    Canvas(modifier) {painter.draw(drawContext.canvas.nativeCanvas,size.width,size.height,time.value)}
}
