package fr.swucollection.app

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlin.math.cos
import kotlin.math.sin

/** Dedicated navy/gold emblems: a tournament trophy and a collection of earned medals. */
@Composable
internal fun HomeFeatureArtwork(tournament: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        withTransform({ scale(size.width / 150f, size.height / 90f, Offset.Zero) }) {
            val gold = Color(0xFFE6C17A)
            val light = Color(0xFFFFEDBA)
            val cyan = Color(0xFF4DBFF4)
            val metal = Brush.linearGradient(listOf(Color(0xFF715124), gold, light, gold, Color(0xFF9C7132)), Offset(45f, 5f), Offset(130f, 85f))
            drawCircle(Brush.radialGradient(listOf(cyan.copy(alpha = .22f), Color.Transparent), Offset(99f, 46f), 69f), 69f, Offset(99f, 46f))
            for (i in 0..12) {
                val x = 25f + (i * 29 % 119)
                val y = 8f + (i * 19 % 73)
                drawCircle(if (i % 3 == 0) gold.copy(alpha = .6f) else cyan.copy(alpha = .45f), if (i % 4 == 0) 1f else .55f, Offset(x, y))
            }
            fun star(x: Float, y: Float, radius: Float): Path = Path().apply {
                for (i in 0..9) {
                    val angle = -Math.PI / 2 + i * Math.PI / 5
                    val r = if (i % 2 == 0) radius else radius * .45f
                    val px = x + cos(angle).toFloat() * r
                    val py = y + sin(angle).toFloat() * r
                    if (i == 0) moveTo(px, py) else lineTo(px, py)
                }
                close()
            }
            if (tournament) {
                for (i in 0..1) withTransform({ rotate(if (i == 0) -16f else 14f, Offset(103f, 46f)) }) {
                    drawRoundRect(Color(0xFF0D3453), Offset(83f + i * 9, 16f), Size(36f, 52f), androidx.compose.ui.geometry.CornerRadius(4f))
                    drawRoundRect(cyan.copy(alpha = .5f), Offset(83f + i * 9, 16f), Size(36f, 52f), androidx.compose.ui.geometry.CornerRadius(4f), style = Stroke(.8f))
                }
                val handles = Path().apply {
                    moveTo(75f, 25f); cubicTo(51f, 17f, 54f, 52f, 83f, 48f)
                    moveTo(111f, 25f); cubicTo(135f, 17f, 132f, 52f, 103f, 48f)
                }
                drawPath(handles, gold, style = Stroke(4f))
                val bowl = Path().apply { moveTo(73f, 18f); lineTo(113f, 18f); lineTo(109f, 42f); quadraticTo(105f, 57f, 93f, 58f); quadraticTo(81f, 57f, 77f, 42f); close() }
                drawPath(bowl, metal)
                drawPath(bowl, light.copy(alpha = .8f), style = Stroke(.8f))
                drawRect(metal, Offset(89f, 55f), Size(8f, 15f))
                drawRoundRect(metal, Offset(77f, 70f), Size(32f, 6f), androidx.compose.ui.geometry.CornerRadius(1.5f))
                drawRoundRect(Color(0xFF13314B), Offset(70f, 77f), Size(46f, 8f), androidx.compose.ui.geometry.CornerRadius(2f))
                drawLine(gold, Offset(72f, 77f), Offset(114f, 77f), 1.5f)
                drawPath(star(93f, 34f, 9f), Color(0xFF6B4B22))
                drawLine(light.copy(alpha = .75f), Offset(79f, 21f), Offset(83f, 41f), 1.5f)
            } else {
                for ((index, x) in listOf(65f, 119f, 92f).withIndex()) {
                    val y = if (index == 2) 36f else 39f
                    val r = if (index == 2) 21f else 16f
                    val ribbon = Path().apply { moveTo(x - r * .7f, y + 8f); lineTo(x - r, 79f); lineTo(x, 70f); lineTo(x + r, 79f); lineTo(x + r * .7f, y + 8f); close() }
                    drawPath(ribbon, if (index == 2) Color(0xFF24709E) else Color(0xFF173C59))
                    drawPath(ribbon, gold.copy(alpha = .6f), style = Stroke(.8f))
                    drawCircle(Color(0xFF010914), r + 3f, Offset(x, y + 2f))
                    drawCircle(metal, r, Offset(x, y))
                    drawCircle(Color(0xFF0A2339), r * .81f, Offset(x, y))
                    drawCircle(gold.copy(alpha = .8f), r * .71f, Offset(x, y), style = Stroke(.8f))
                    drawPath(star(x, y, r * .52f), metal)
                    drawArc(light.copy(alpha = .85f), 205f, 95f, false, Offset(x - r + 2f, y - r + 2f), Size(r * 2f - 4f, r * 2f - 4f), style = Stroke(1f))
                }
                drawPath(star(92f, 8f, 3.8f), light)
            }
        }
    }
}
