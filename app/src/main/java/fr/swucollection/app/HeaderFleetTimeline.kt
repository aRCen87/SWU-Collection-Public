package fr.swucollection.app

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

internal data class HeaderShip(
    val x: Float, val y: Float, val scale: Float, val tie: Boolean,
    val heading: Float = 0f, val bank: Float = 0f
)
internal data class HeaderBolt(val x: Float, val y: Float, val angle: Float, val alpha: Float)

internal object HeaderFleetTimeline {
    const val FLIGHT_MS = 5200
    const val PERIOD_MS = 25000L
    private const val BOLT_SECONDS = 0.34f
    private val firingTimes = floatArrayOf(1.18f, 1.36f, 2.12f, 2.30f, 3.12f, 3.30f)

    fun ships(progress: Float): List<HeaderShip> {
        if (!progress.isFinite() || progress <= 0f || progress >= 1f) return emptyList()
        val x = 550f - progress * 680f
        val y = 142f - 9f * progress
        val turn = sin(progress * 18f)
        return listOf(
            HeaderShip(x, y + turn * 6f, 0.9f, false,
                heading = (-atan2(-9f + 108f * cos(progress * 18f), 680f) * 180f / Math.PI).toFloat(),
                bank = turn * 0.22f),
            HeaderShip(x + 49f, y - 15f + sin(progress * 15f - 0.4f) * 2.5f, 0.64f, true,
                heading = -9f + turn * 4f, bank = turn * 0.12f),
            HeaderShip(x + 74f, y + 11f + sin(progress * 15f - 1f) * 3f, 0.54f, true,
                heading = 9f + turn * 3f, bank = -turn * 0.15f)
        )
    }

    /** Each bolt keeps its launch point and aim: it never follows the target after firing. */
    fun bolts(progress: Float): List<HeaderBolt> {
        if (!progress.isFinite() || progress <= 0f || progress >= 1f) return emptyList()
        val seconds = progress * FLIGHT_MS / 1000f
        return buildList {
            for (fighter in 1..2) for (shot in firingTimes) {
                val launch = shot + (fighter - 1) * 0.24f
                val age = seconds - launch
                if (age < 0f || age >= BOLT_SECONDS) continue
                val source = ships(launch * 1000f / FLIGHT_MS)[fighter]
                val target = ships((launch + BOLT_SECONDS) * 1000f / FLIGHT_MS)[0]
                val heading = Math.toRadians(source.heading.toDouble()).toFloat()
                val startX = source.x - cos(heading) * 4f * source.scale
                val startY = source.y - sin(heading) * 4f * source.scale
                // A slight miss lets the X-wing dodge the incoming fire.
                val targetY = target.y + if (fighter == 1) -5f else 6f
                val dx = target.x - startX
                val dy = targetY - startY
                val fraction = age / BOLT_SECONDS
                add(HeaderBolt(startX + dx * fraction, startY + dy * fraction,
                    atan2(dy, dx), (1f - fraction * fraction).coerceIn(0f, 1f)))
            }
        }
    }
}
