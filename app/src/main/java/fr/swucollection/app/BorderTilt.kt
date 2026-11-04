package fr.swucollection.app

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.sqrt

internal data class BorderTilt(val x: Float = 0f, val y: Float = 0f)

/** Relative to the way the phone was held when the home screen became active. */
internal class BorderTiltFilter {
    private var origin: Pair<Float, Float>? = null
    private var rotation = -1
    private var previousTime = 0L
    private var current = BorderTilt()

    fun reset() {
        origin = null
        rotation = -1
        previousTime = 0L
        current = BorderTilt()
    }

    fun update(x: Float, y: Float, z: Float, displayRotation: Int, timeNanos: Long): BorderTilt? {
        if (!x.isFinite() || !y.isFinite() || !z.isFinite()) return null
        val magnitude = sqrt(x * x + y * y + z * z)
        // Ignore invalid readings and large acceleration spikes rather than flashing the frame.
        if (magnitude !in 6f..14f) return null
        val (screenX, screenY) = when (displayRotation) {
            1 -> -y to x
            2 -> -x to -y
            3 -> y to -x
            else -> x to y
        }
        val roll = atan2(screenX, sqrt(screenY * screenY + z * z))
        val pitch = atan2(screenY, z)
        if (origin == null || rotation != displayRotation) {
            origin = roll to pitch
            rotation = displayRotation
            previousTime = timeNanos
            current = BorderTilt()
            return current
        }
        if (timeNanos <= previousTime) return null
        val baseline = requireNotNull(origin)
        fun relative(angle: Float, start: Float): Float {
            val difference = atan2(kotlin.math.sin(angle - start), kotlin.math.cos(angle - start))
            return (difference / (PI.toFloat() / 6f)).coerceIn(-1f, 1f)
        }
        val target = BorderTilt(relative(roll, baseline.first), relative(pitch, baseline.second))
        val seconds = ((timeNanos - previousTime) / 1_000_000_000f).coerceAtMost(0.1f)
        val alpha = 1f - exp(-seconds / 0.14f)
        previousTime = timeNanos
        current = BorderTilt(current.x + alpha * (target.x - current.x), current.y + alpha * (target.y - current.y))
        return current
    }
}
