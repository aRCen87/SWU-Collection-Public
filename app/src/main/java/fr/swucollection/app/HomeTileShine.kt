package fr.swucollection.app

import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.sin

internal fun homeTilePhase(progress: Float,index: Int): Float =
    if(!progress.isFinite() || progress<=0f || progress>=1f) 0f else (progress*1.42f-index.coerceIn(0,7)*.06f).coerceIn(0f,1f)

/** One shared, paused-between-passes clock, consumed only during drawing. */
internal fun Modifier.homeTileShine(clock: State<Float>, index: Int): Modifier = drawWithCache {
    val inset=1.dp.toPx();val radius=18.dp.toPx();val stroke=Stroke(1.6.dp.toPx())
    onDrawWithContent {
        drawContent()
        val p=homeTilePhase(clock.value,index)
        if(p>0f && p<1f) {
            val x=(-.3f+p*1.6f)*size.width
            val band=Brush.linearGradient(listOf(Color.Transparent,Color(0xFFFFF0BF),Color.Transparent),Offset(x-size.width*.18f,0f),Offset(x+size.width*.18f,size.height))
            drawRoundRect(band,Offset(inset,inset),androidx.compose.ui.geometry.Size(size.width-inset*2,size.height-inset*2),CornerRadius(radius,radius),style=stroke)
        }
    }
}
internal fun Modifier.homeIllustrationShine(key: String,clock: State<Float>,index: Int): Modifier = drawWithCache {
    onDrawWithContent {
        drawContent()
        val p=homeTilePhase(clock.value,index)
        if(p>0f && p<1f) {
            val intensity=sin(p*Math.PI).toFloat()
            if(key=="SCANNER") {
                val y=p*size.height
                drawLine(Brush.horizontalGradient(listOf(Color.Transparent,Color(0xFF8DE9FF).copy(alpha=intensity*.8f),Color.Transparent)),
                    Offset(size.width*.48f,y),Offset(size.width,y),1.4.dp.toPx())
            } else {
                val x=size.width*(.3f+p*.9f)
                drawRect(Brush.linearGradient(listOf(Color.Transparent,Color(0xFFFFE6A0).copy(alpha=intensity*.16f),Color.Transparent),
                    Offset(x-size.width*.12f,0f),Offset(x+size.width*.12f,size.height)))
                if(key=="BADGES" || key=="TOURNOIS & MÉTA") {
                    val center=Offset(size.width*.84f,size.height*.28f)
                    val color=Color(0xFFFFE4A0).copy(alpha=intensity*.7f)
                    drawLine(color,center-Offset(6.dp.toPx(),0f),center+Offset(6.dp.toPx(),0f),1.dp.toPx())
                    drawLine(color,center-Offset(0f,6.dp.toPx()),center+Offset(0f,6.dp.toPx()),1.dp.toPx())
                }
            }
        }
    }
}
