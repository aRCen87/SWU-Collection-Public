package fr.swucollection.app

import android.graphics.Canvas as NativeCanvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.hypot

internal class PremiumLogoPainter {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {typeface=Typeface.create("sans-serif-black",Typeface.BOLD);textSize=98f}
    private val letters=mutableListOf<Path>()
    private val all=Path()
    private val star=Path().apply {
        moveTo(150f,149f);lineTo(153f,157f);lineTo(161f,160f);lineTo(153f,163f)
        lineTo(150f,171f);lineTo(147f,163f);lineTo(139f,160f);lineTo(147f,157f);close()
    }
    private val trail=Path()
    private val metal=LinearGradient(0f,15f,0f,150f,intArrayOf(0xFFFFF0B8.toInt(),0xFFBC8D35.toInt(),0xFFFFD98C.toInt()),null,Shader.TileMode.CLAMP)
    private val shine=LinearGradient(-24f,0f,24f,0f,intArrayOf(0x00FFFFFF,0xEEFFF6DA.toInt(),0x00FFFFFF),null,Shader.TileMode.CLAMP)
    private val matrix=Matrix()
    private val contour: HomeLogoContour
    init {
        val widths="SWU".map {paint.measureText(it.toString())};var x=(300f-widths.sum()-8f)/2f
        "SWU".forEachIndexed {i,c -> val path=Path();paint.getTextPath(c.toString(),0,1,x,105f,path);letters+=path;all.addPath(path);x+=widths[i]+4f}
        paint.typeface=Typeface.create("sans-serif",Typeface.BOLD);paint.textSize=19f
        val label="C O L L E C T I O N";val path=Path();paint.getTextPath(label,0,label.length,(300f-paint.measureText(label))/2f,135f,path);all.addPath(path)
        contour=HomeLogoContour(letterPaths=letters)
    }
    fun draw(canvas: NativeCanvas,width: Float,height: Float,intro: Float,reflection: Float) {
        val save=canvas.save();val scale=minOf(width/300f,height/180f)
        canvas.translate((width-300f*scale)/2f,0f);canvas.scale(scale,scale)
        paint.style=Paint.Style.FILL;paint.shader=metal;paint.alpha=255;canvas.drawPath(all,paint)
        paint.shader=null;paint.color=0xFF8C6B35.toInt();paint.style=Paint.Style.STROKE;paint.strokeWidth=.55f;canvas.drawPath(all,paint)
        paint.style=Paint.Style.FILL
        if(reflection>0f && reflection<1f) {
            matrix.setTranslate(-50f+reflection*410f,0f);shine.setLocalMatrix(matrix);paint.shader=shine;canvas.drawPath(all,paint);paint.shader=null
            contour.draw(canvas,reflection)
        }
        paint.color=0xFFD8B46C.toInt();paint.strokeWidth=1f
        canvas.drawLine(80f,160f,133f,160f,paint);canvas.drawLine(167f,160f,220f,160f,paint)
        val frame=HomeHeaderTimeline.frame(intro)
        val arrived=frame.star==HomeHeaderTimeline.target
        if(arrived) {paint.shader=metal;canvas.drawPath(star,paint);paint.shader=null}
        if(frame.starVisible && frame.trailAlpha>0f) {
            val x=150f+(frame.star.x-HomeHeaderTimeline.target.x)*.7f
            val y=160f+(frame.star.y-HomeHeaderTimeline.target.y)*.7f
            val tx=150f+(frame.tail.x-HomeHeaderTimeline.target.x)*.7f
            val ty=160f+(frame.tail.y-HomeHeaderTimeline.target.y)*.7f
            val length=hypot(tx-x,ty-y).coerceAtLeast(1f);val nx=-(ty-y)/length;val ny=(tx-x)/length
            trail.rewind();trail.moveTo(x+nx*1.7f,y+ny*1.7f);trail.lineTo(tx,ty);trail.lineTo(x-nx*1.7f,y-ny*1.7f);trail.close()
            paint.shader=LinearGradient(x,y,tx,ty,intArrayOf(0xFFEEF8FF.toInt(),0x0068C9FF),null,Shader.TileMode.CLAMP)
            paint.alpha=(frame.trailAlpha*255).toInt();canvas.drawPath(trail,paint);paint.shader=null
            paint.color=0xFFE1F5FF.toInt();canvas.drawCircle(x,y,2.2f,paint);paint.alpha=255
        }
        if(frame.glow>0f) {
            paint.color=0xFFFFD884.toInt();paint.alpha=(frame.glow*50).toInt();canvas.drawCircle(150f,160f,19f,paint)
            paint.alpha=(frame.glow*150).toInt();canvas.drawCircle(150f,160f,5f,paint);paint.alpha=255
        }
        canvas.restoreToCount(save)
    }
}
@Composable
internal fun PremiumLogoArtwork(intro: State<Float>, reflection: State<Float>, modifier: Modifier = Modifier) {
    val painter=remember {PremiumLogoPainter()}
    Canvas(modifier.semantics {contentDescription="SWU Collection"}) {painter.draw(drawContext.canvas.nativeCanvas,size.width,size.height,intro.value,reflection.value)}
}
