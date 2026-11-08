package fr.swucollection.app

import kotlin.math.PI
import kotlin.math.sin

internal data class PremiumOrbitFrame(val planetX: Float, val planetY: Float, val moonX: Float, val moonY: Float)
/** Fractions of source artwork: the moon has its own orbit, not a second copy of the whole scene. */
internal object PremiumOrbit {
    const val PLANET_SECONDS = 160f
    const val MOON_SECONDS = 113f
    fun frame(seconds: Float): PremiumOrbitFrame {
        val t=if(seconds.isFinite()) seconds.coerceAtLeast(0f) else 0f
        val planet=t*(2f*PI.toFloat()/PLANET_SECONDS)
        val moon=t*(2f*PI.toFloat()/MOON_SECONDS)
        return PremiumOrbitFrame(sin(planet)*.022f,sin(planet*.5f)*.009f,
            -sin(moon*.5f)*.006f,sin(moon)*.026f)
    }
}
