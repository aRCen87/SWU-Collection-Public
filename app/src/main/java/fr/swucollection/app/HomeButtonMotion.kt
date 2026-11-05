package fr.swucollection.app

import android.animation.ValueAnimator
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.math.sin

/** Native touch feedback only: no idle animation, input interception or navigation delay. */
@Composable
internal fun Modifier.homeButtonMotion(interactions: MutableInteractionSource): Modifier {
    val pressed by interactions.collectIsPressedAsState()
    val enabled = ValueAnimator.areAnimatorsEnabled()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.976f else 1f,
        animationSpec = if (pressed) tween(110) else spring(dampingRatio = 0.65f, stiffness = 550f),
        label = "homeButtonPress"
    )
    val light = remember { Animatable(1f) }
    LaunchedEffect(interactions) {
        var sweep: Job? = null
        interactions.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    sweep?.cancel()
                    sweep = launch {
                        light.snapTo(0f)
                        if (ValueAnimator.areAnimatorsEnabled()) light.animateTo(1f, tween(1050, easing = LinearEasing))
                        else light.snapTo(1f)
                    }
                }
                is PressInteraction.Cancel -> { sweep?.cancel(); light.snapTo(1f) }
                // Release must not interrupt the soft gold tail. The Surface
                // still handles activation itself, immediately and exactly once.
                else -> Unit
            }
        }
    }
    return graphicsLayer {
        scaleX = if (ValueAnimator.areAnimatorsEnabled()) scale else 1f
        scaleY = scaleX
    }.drawWithCache {
        val inset = 1.5.dp.toPx()
        val radius = 18.dp.toPx().coerceAtMost(size.height / 2f)
        val outline = Path().apply {
            addRoundRect(inset, inset, size.width - inset, size.height - inset, radius, radius, Path.Direction.CW)
        }
        val perimeter = PathMeasure(outline, true)
        val segment = Path()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            color = 0xFFFFE4A0.toInt()
        }
        val length = perimeter.length
        onDrawWithContent {
            drawContent()
            val progress = light.value
            if (progress > 0f && progress < 1f && ValueAnimator.areAnimatorsEnabled() && length > 0f) {
                val intensity = sin(Math.PI * progress).toFloat()
                val canvas = drawContext.canvas.nativeCanvas
                // Geometry and paint are cached. Only a narrow outline is lit;
                // titles and artwork remain crisp and readable throughout.
                for (index in 0 until 28) {
                    val end = progress * length - index * length * 0.004f
                    if (end <= 0f) continue
                    segment.rewind()
                    perimeter.getSegment((end - length * 0.0045f).coerceAtLeast(0f), end.coerceAtMost(length), segment, true)
                    val alpha = intensity * (1f - index / 28f)
                    paint.strokeWidth = 4.dp.toPx()
                    paint.alpha = (alpha * 44).toInt()
                    canvas.drawPath(segment, paint)
                    paint.strokeWidth = 1.5.dp.toPx()
                    paint.alpha = (alpha * 235).toInt()
                    canvas.drawPath(segment, paint)
                }
            }
        }
    }
}
