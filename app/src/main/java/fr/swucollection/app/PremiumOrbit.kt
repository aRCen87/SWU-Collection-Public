package fr.swucollection.app

import kotlin.math.PI
import kotlin.math.sin

internal data class PremiumSpaceFit(val scale: Float, val left: Float, val top: Float)
internal data class PremiumOrbitFrame(val planetX: Float, val planetY: Float, val moonX: Float, val moonY: Float)
/** Fractions of source artwork: the moon has its own orbit, not a second copy of the whole scene. */
internal object PremiumOrbit {
    const val PLANET_SECONDS = 125f
    const val MOON_SECONDS = 86f
    fun fit(width: Float,height: Float,sourceWidth: Float,sourceHeight: Float): PremiumSpaceFit {
        val scale=maxOf(width/sourceWidth,height/sourceHeight)*1.07f
        val moonLeft=(185f/1610f*sourceWidth-sourceWidth*.006f-32f/977f*sourceHeight)*scale
        val left=maxOf((width-sourceWidth*scale)*.4f,width*.022f+4f-moonLeft)
            .coerceIn(width-sourceWidth*scale+width*.022f,-width*.022f)
        // Keep the entire small moon in frame even on very wide windows.
        val moonBottom=(661f/977f+.026f+32f/977f)*sourceHeight*scale
        val top=minOf((height-sourceHeight*scale)*.56f,height-moonBottom-height*.009f-4f)
            .coerceIn(height-sourceHeight*scale+height*.009f,-height*.009f)
        return PremiumSpaceFit(scale,left,top)
    }
    fun frame(seconds: Float): PremiumOrbitFrame {
        val t=if(seconds.isFinite()) seconds.coerceAtLeast(0f) else 0f
        val planet=t*(2f*PI.toFloat()/PLANET_SECONDS)
        val moon=t*(2f*PI.toFloat()/MOON_SECONDS)
        return PremiumOrbitFrame(sin(planet)*.022f,sin(planet*.5f)*.009f,
            -sin(moon*.5f)*.006f,sin(moon)*.026f)
    }
}
