package fr.swucollection.app

import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlin.math.sin

/** No timer runs off the home screen or while Android has paused the activity. */
@Composable
internal fun rememberHeaderAmbientTime(active: Boolean): State<Float> {
    val seconds = remember { mutableFloatStateOf(0f) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(active, owner) {
        if (!active) { seconds.floatValue = 0f; return@LaunchedEffect }
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous = 0L
            while (ValueAnimator.areAnimatorsEnabled()) {
                withFrameNanos { now ->
                    if (previous != 0L) seconds.floatValue += ((now - previous) / 1_000_000_000f).coerceAtMost(.06f)
                    previous = now
                }
                delay(32)
            }
            seconds.floatValue = 0f
        }
    }
    return seconds
}

/** Window coordinates come from existing illuminated pixels of the approved artwork. */
internal class HomeHeaderAmbientLights {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val destination = RectF()
    private val screen = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    private fun sprite(color: Int): Bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).also {
        val brush = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(32f, 32f, 32f,
                intArrayOf(color, color, color and 0x00FFFFFF),
                floatArrayOf(0f, .16f, 1f), Shader.TileMode.CLAMP)
        }
        Canvas(it).drawCircle(32f, 32f, 32f, brush)
    }
    // Sprites and destination bounds are reused; no bitmap or shader allocation per frame.
    private val warm = sprite(0xFFFFC446.toInt())
    private val core = sprite(0xFFFFF5D2.toInt())
    private val blue = sprite(0xFF99DDFF.toInt())
    private fun glow(canvas: Canvas, x: Float, y: Float, radius: Float, alpha: Float, bitmap: Bitmap) {
        paint.alpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
        paint.xfermode = screen
        destination.set(x - radius, y - radius, x + radius, y + radius)
        canvas.drawBitmap(bitmap, null, destination, paint)
    }
    fun draw(canvas: Canvas, time: Float) {
        // Disabled/reduced motion keeps the unmodified still image.
        if (time <= 0f) return
        val saved = canvas.save()
        val scale = maxOf(475f / 2000f, 185f / 786f)
        canvas.translate((475f - 2000f * scale) / 2f, (185f - 786f * scale) / 2f)
        canvas.scale(scale, scale)
        for (i in 0 until WINDOWS.size / 2) {
            val x = WINDOWS[i * 2]; val y = WINDOWS[i * 2 + 1]
            val wave = .5f + .5f * sin(time * (1.25f + (i % 11) * .12f) + i * 2.399f)
            val alpha = wave * wave
            paint.xfermode = null
            paint.color = 0xFF07131F.toInt()
            paint.alpha = ((1f - alpha) * .78f * 255).toInt()
            canvas.drawRect(x - 3f, y - 2f, x + 3f, y + 2f, paint)
            glow(canvas, x, y, 12f + (i % 4) * 2f, alpha * .92f, warm)
            glow(canvas, x, y, 4f, alpha, core)
        }
        val pulse = .5f + .5f * sin(time * 2.2f)
        glow(canvas, 1141f, 516f, 26f, .12f + .84f * pulse, warm)
        glow(canvas, 1141f, 516f, 6f, .25f + .75f * pulse, core)
        glow(canvas, 1183f, 534f, 23f, .12f + .82f * (.5f + .5f * sin(time * 2f + 1.7f)), warm)
        glow(canvas, 1459f, 476f, 28f, .10f + .80f * pulse, blue)
        glow(canvas, 1459f, 476f, 8f, .18f + .80f * pulse, blue)
        paint.alpha = 255
        paint.xfermode = null
        canvas.restoreToCount(saved)
    }
    private companion object {
        val WINDOWS = floatArrayOf(
            1347.3f, 737.0f,
            1674.2f, 728.0f,
            1806.1f, 746.0f,
            120.9f, 698.0f,
            951.5f, 719.0f,
            591.7f, 731.0f,
            513.7f, 683.0f,
            16.0f, 566.0f,
            111.9f, 725.0f,
            1779.1f, 743.0f,
            1311.3f, 707.0f,
            924.5f, 659.0f,
            924.5f, 725.0f,
            1188.4f, 752.0f,
            729.6f, 665.0f,
            1218.4f, 746.0f,
            378.8f, 722.0f,
            1956.0f, 719.0f,
            720.6f, 692.0f,
            297.9f, 713.0f,
            963.5f, 710.0f,
            768.6f, 695.0f,
            798.6f, 752.0f,
            1326.3f, 698.0f,
            1440.3f, 725.0f,
            750.6f, 677.0f,
            891.6f, 659.0f,
            162.9f, 689.0f,
            414.8f, 722.0f,
            1854.1f, 665.0f,
            732.6f, 737.0f,
            402.8f, 683.0f,
            1713.1f, 647.0f,
            111.9f, 563.0f,
            1236.4f, 683.0f,
            144.9f, 710.0f,
            843.6f, 677.0f,
            1683.2f, 689.0f,
            171.9f, 638.0f,
            1101.4f, 695.0f,
            1152.4f, 668.0f,
            561.7f, 722.0f,
            1584.2f, 533.0f,
            717.6f, 707.0f,
            1815.1f, 701.0f,
            1764.1f, 746.0f,
            1728.1f, 593.0f,
            999.5f, 668.0f,
            429.8f, 725.0f,
            1539.2f, 761.0f,
            1875.1f, 746.0f,
            981.5f, 716.0f,
            579.7f, 668.0f,
            678.7f, 695.0f,
            1644.2f, 461.0f,
            321.8f, 710.0f,
            1767.1f, 701.0f,
            204.9f, 635.0f,
            144.9f, 725.0f,
            1290.4f, 734.0f,
            1581.2f, 686.0f,
            1548.2f, 680.0f,
            168.9f, 710.0f,
            1125.4f, 695.0f,
            1938.0f, 713.0f,
            447.8f, 707.0f,
            1302.3f, 659.0f,
            1737.1f, 728.0f,
            462.8f, 722.0f,
            816.6f, 752.0f,
            1629.2f, 728.0f,
            1344.3f, 698.0f,
            1032.5f, 680.0f,
            666.7f, 671.0f,
            774.6f, 737.0f,
            1614.2f, 629.0f,
            1620.2f, 680.0f,
            606.7f, 728.0f,
            1479.3f, 725.0f,
            630.7f, 731.0f,
            999.5f, 728.0f,
            939.5f, 677.0f,
            654.7f, 731.0f,
            777.6f, 665.0f,
            1056.5f, 680.0f,
            1413.3f, 740.0f,
            699.7f, 665.0f,
            366.8f, 665.0f,
            31.0f, 566.0f,
            100.0f, 584.0f,
            420.8f, 641.0f,
            978.5f, 695.0f,
            1611.2f, 749.0f,
            573.7f, 689.0f,
            88.0f, 566.0f,
            1596.2f, 689.0f,
            963.5f, 659.0f,
            1611.2f, 539.0f,
            1779.1f, 659.0f,
            1017.5f, 659.0f,
            501.7f, 725.0f,
            327.8f, 665.0f,
            1806.1f, 680.0f,
            1776.1f, 683.0f,
            807.6f, 704.0f,
            1782.1f, 728.0f,
            312.8f, 722.0f,
            1668.2f, 683.0f,
            528.7f, 719.0f,
            1479.3f, 743.0f,
            1686.2f, 662.0f,
            858.6f, 659.0f,
            225.9f, 635.0f,
            1221.4f, 719.0f,
            1053.5f, 725.0f,
            690.7f, 710.0f,
            1926.0f, 758.0f,
            1140.4f, 695.0f,
            1614.2f, 731.0f,
            1740.1f, 560.0f,
            1563.2f, 680.0f,
            1356.3f, 758.0f,
            1848.1f, 746.0f,
            1266.4f, 722.0f,
            1875.1f, 689.0f,
            55.0f, 566.0f,
            1611.2f, 458.0f,
            228.9f, 692.0f,
            228.9f, 716.0f,
            339.8f, 725.0f,
            1395.3f, 650.0f,
            1137.4f, 662.0f,
            1794.1f, 758.0f,
            645.7f, 695.0f,
            111.9f, 641.0f,
            1317.3f, 662.0f,
            1659.2f, 731.0f,
            1827.1f, 740.0f,
            390.8f, 701.0f,
            1791.1f, 686.0f,
            543.7f, 683.0f,
            1716.1f, 749.0f,
            1596.2f, 728.0f,
            138.9f, 566.0f,
            1440.3f, 686.0f,
            1947.0f, 680.0f,
            1890.1f, 722.0f,
            34.0f, 713.0f,
            852.6f, 755.0f,
            1638.2f, 689.0f,
            1143.4f, 680.0f,
            1074.5f, 662.0f,
            1830.1f, 668.0f,
            1983.0f, 713.0f,
            1878.1f, 671.0f,
            1173.4f, 749.0f,
            804.6f, 677.0f,
            1944.0f, 662.0f,
            417.8f, 701.0f,
            252.9f, 716.0f,
            822.6f, 680.0f,
            531.7f, 620.0f,
            1308.3f, 683.0f,
            528.7f, 686.0f,
            1197.4f, 662.0f,
            921.5f, 695.0f,
            1923.0f, 710.0f,
            354.8f, 722.0f,
            1323.3f, 731.0f,
            624.7f, 668.0f,
            1896.1f, 704.0f,
            1293.4f, 707.0f,
            882.6f, 758.0f,
            1728.1f, 617.0f,
            576.7f, 728.0f,
            1653.2f, 524.0f,
            363.8f, 683.0f,
            1899.1f, 671.0f,
            300.8f, 686.0f,
            1212.4f, 662.0f,
            861.6f, 677.0f,
            441.8f, 665.0f,
            1266.4f, 698.0f,
            972.5f, 728.0f,
            1068.5f, 692.0f,
            1566.2f, 590.0f,
            702.6f, 692.0f,
            1203.4f, 716.0f,
            294.9f, 728.0f,
            876.6f, 659.0f,
            1092.5f, 671.0f,
            1374.3f, 698.0f,
            1611.2f, 653.0f,
            1824.1f, 689.0f,
            43.0f, 596.0f,
            339.8f, 698.0f,
            477.8f, 668.0f,
            1365.3f, 659.0f,
            1281.4f, 677.0f,
            1920.0f, 692.0f
        )
    }
}
