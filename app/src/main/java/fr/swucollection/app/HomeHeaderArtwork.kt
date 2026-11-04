package fr.swucollection.app

import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/** Retained above the home screen so navigation never starts an endless opening loop. */
@Composable
internal fun rememberHomeHeaderProgress(active: Boolean): State<Float> {
    var completed by rememberSaveable { mutableStateOf(false) }
    val progress = remember { Animatable(if (completed) 1f else 0f) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(active, owner) {
        if (active) owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (!completed) {
                if (ValueAnimator.areAnimatorsEnabled()) {
                    val remaining = (HomeHeaderTimeline.DURATION_MS * (1f - progress.value)).roundToInt().coerceAtLeast(1)
                    progress.animateTo(1f, tween(remaining, easing = LinearEasing))
                } else progress.snapTo(1f)
                completed = true
            }
        }
    }
    return progress.asState()
}

/** Reflections sleep between passes and stop whenever the home screen is inactive. */
@Composable
internal fun rememberHeaderReflectionProgress(active: Boolean): State<Float> {
    val progress = remember { Animatable(0f) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(active, owner) {
        progress.snapTo(0f)
        if (active) owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            progress.snapTo(0f)
            delay(5000)
            while (ValueAnimator.areAnimatorsEnabled()) {
                progress.animateTo(1f, tween(2400, easing = LinearEasing))
                progress.snapTo(0f)
                delay(11000)
            }
        }
    }
    return progress.asState()
}

@Composable
internal fun HomeHeaderArtwork(progress: State<Float>, reflection: State<Float>, modifier: Modifier = Modifier) {
    val resources = LocalContext.current.resources
    val artwork = remember(resources) {
        val sheet = BitmapFactory.decodeResource(resources, R.drawable.collection_design_reference)
        val original = Bitmap.createBitmap(sheet, 550, 80, 475, 185)
        val withoutStar = original.copy(Bitmap.Config.ARGB_8888, true)
        // Repair only the gold star pixels, using the background at the same height.
        // The complete untouched artwork is restored at arrival: no rectangular patch.
        for (y in 107..127) {
            val left = original.getPixel(142, y)
            val right = original.getPixel(163, y)
            for (x in 144..161) {
                val pixel = original.getPixel(x, y)
                val red = (pixel ushr 16) and 255
                val green = (pixel ushr 8) and 255
                val blue = pixel and 255
                if (red > 65 && red > blue * 1.2f && green > blue) {
                    val t = (x - 142) / 21f
                    var color = 0xFF000000.toInt()
                    for (shift in intArrayOf(16, 8, 0)) {
                        val l = (left ushr shift) and 255
                        val r = (right ushr shift) and 255
                        color = color or ((l + (r - l) * t).roundToInt() shl shift)
                    }
                    withoutStar.setPixel(x, y, color)
                }
            }
        }
        sheet.recycle()
        val logoMask = Bitmap.createBitmap(475, 185, Bitmap.Config.ARGB_8888)
        for (y in 10..126) for (x in 38..272) {
            val pixel = original.getPixel(x, y)
            val red = (pixel ushr 16) and 255
            val green = (pixel ushr 8) and 255
            val blue = pixel and 255
            if (red > 100 && red > blue * 1.18f && green > blue * 1.1f) {
                val alpha = ((red - blue) * 2).coerceIn(0, 255)
                logoMask.setPixel(x, y, (alpha shl 24) or 0x00FFFFFF)
            }
        }
        Triple(original, withoutStar, logoMask)
    }
    Canvas(modifier.clipToBounds().semantics { contentDescription = "SWU Collection" }) {
        val frame = HomeHeaderTimeline.frame(progress.value)
        val canvas = drawContext.canvas.nativeCanvas
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val saved = canvas.save()
        canvas.scale(size.width / 475f, size.height / 185f)
        canvas.clipRect(0f, 0f, 475f, 185f)
        val arrived = frame.star == HomeHeaderTimeline.target
        canvas.drawBitmap(if (arrived) artwork.first else artwork.second, 0f, 0f, paint)

        // Light is added only to existing gold pixels; the original logo is never recolored.
        if (reflection.value > 0f && reflection.value < 1f) {
            val layer = canvas.saveLayer(0f, 0f, 475f, 185f, null)
            canvas.drawBitmap(artwork.third, 0f, 0f, paint)
            val x = -65f + reflection.value * 430f
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            paint.shader = LinearGradient(x - 40f, 100f, x + 40f, 25f,
                intArrayOf(0x00FFE6A8, 0x35FFE6A8, 0xA0FFF3CE.toInt(), 0x35FFE6A8, 0x00FFE6A8),
                floatArrayOf(0f, 0.35f, 0.5f, 0.65f, 1f), Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, 475f, 185f, paint)
            paint.shader = null
            paint.xfermode = null
            canvas.restoreToCount(layer)
        }
        if (frame.starVisible && (!arrived || frame.trailAlpha > 0f)) {
            val star = frame.star
            val dx = frame.tail.x - star.x
            val dy = frame.tail.y - star.y
            val length = kotlin.math.hypot(dx, dy).coerceAtLeast(1f)
            val nx = -dy / length
            val ny = dx / length
            // A tapered luminous wake with a white core, rather than a flying logo.
            for (width in floatArrayOf(2.5f, 0.9f)) {
                paint.shader = LinearGradient(star.x, star.y, frame.tail.x, frame.tail.y,
                    intArrayOf(((frame.trailAlpha * (if (width > 2f) 115 else 250)).roundToInt() shl 24) or 0x00E7F5FF,
                        ((frame.trailAlpha * 60).roundToInt() shl 24) or 0x0089BEFF, 0x0089BEFF),
                    floatArrayOf(0f, 0.3f, 1f), Shader.TileMode.CLAMP)
                val trail = Path().apply {
                    moveTo(star.x + nx * width, star.y + ny * width)
                    lineTo(frame.tail.x, frame.tail.y)
                    lineTo(star.x - nx * width, star.y - ny * width)
                    close()
                }
                canvas.drawPath(trail, paint)
            }
            paint.shader = RadialGradient(star.x, star.y, 13f,
                intArrayOf(((frame.trailAlpha * 210).roundToInt() shl 24) or 0x00D9EEFF, 0x0089BEFF),
                null, Shader.TileMode.CLAMP)
            canvas.drawCircle(star.x, star.y, 13f, paint)
            paint.shader = null
            paint.color = 0xFFFFFFFF.toInt()
            paint.alpha = (frame.trailAlpha * 255).roundToInt()
            canvas.drawCircle(star.x, star.y, 1.3f, paint)
            paint.alpha = 255
        }
        if (frame.glow > 0f) {
            paint.shader = RadialGradient(HomeHeaderTimeline.target.x, HomeHeaderTimeline.target.y, 24f,
                intArrayOf(((frame.glow * 140).roundToInt() shl 24) or 0x00FFE8B0, 0x00FFE8B0),
                null, Shader.TileMode.CLAMP)
            canvas.drawCircle(HomeHeaderTimeline.target.x, HomeHeaderTimeline.target.y, 24f, paint)
            paint.shader = null
        }
        canvas.restoreToCount(saved)
    }
}
