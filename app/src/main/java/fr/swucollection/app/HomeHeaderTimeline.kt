package fr.swucollection.app

import kotlin.math.pow
import kotlin.math.sin

internal data class HeaderPoint(val x: Float, val y: Float)
internal data class HomeHeaderFrame(
    val night: Float, val starVisible: Boolean, val star: HeaderPoint,
    val tail: HeaderPoint, val trailAlpha: Float, val glow: Float
)

internal object HomeHeaderTimeline {
    const val DURATION_MS = 3800
    val target = HeaderPoint(153.5f, 117.5f)

    fun frame(progress: Float): HomeHeaderFrame {
        val time = (if (progress.isFinite()) progress else 0f).coerceIn(0f, 1f) * DURATION_MS
        val flight = ((time - 500f) / 2100f).coerceIn(0f, 1f)
        val eased = 1f - (1f - flight).pow(1.25f)
        val star = if (flight >= 1f) target else HeaderPoint(
            500f + (target.x - 500f) * eased, 17f + (target.y - 17f) * eased)
        val trailLength = (eased * 420f).coerceAtMost(165f)
        val dx = 500f - target.x
        val dy = 17f - target.y
        val length = kotlin.math.hypot(dx, dy)
        val settle = ((time - 2600f) / 650f).coerceIn(0f, 1f)
        return HomeHeaderFrame(
            night = 0f, starVisible = time >= 500f,
            star = star, tail = HeaderPoint(star.x + dx / length * trailLength, star.y + dy / length * trailLength),
            trailAlpha = if (time < 500f) 0f else (1f - (time - 2600f).coerceAtLeast(0f) / 650f).coerceIn(0f, 1f),
            glow = if (time < 2600f || time >= 3250f) 0f else sin(settle * Math.PI.toFloat()).coerceAtLeast(0f)
        )
    }
}
