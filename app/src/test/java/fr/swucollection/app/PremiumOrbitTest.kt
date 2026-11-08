package fr.swucollection.app

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class PremiumOrbitTest {
    @Test fun planetIsOnlySlightlyFasterAndMoonHasAnotherPeriodAndAxis() {
        assertTrue(PremiumOrbit.PLANET_SECONDS in 115f..135f)
        assertNotEquals(PremiumOrbit.PLANET_SECONDS,PremiumOrbit.MOON_SECONDS)
        val f=PremiumOrbit.frame(12f)
        assertTrue(abs(f.planetX)>abs(f.planetY))
        assertTrue(abs(f.moonY)>abs(f.moonX))
        assertTrue(f.planetX>0f && f.moonX<0f)
        assertEquals(0f,PremiumOrbit.frame(PremiumOrbit.PLANET_SECONDS).planetX,.00001f)
        assertTrue(abs(PremiumOrbit.frame(PremiumOrbit.PLANET_SECONDS).moonY)>.001f)
    }
    @Test fun motionStaysInsideOverscanAndRejectsNonFiniteTime() {
        assertEquals(PremiumOrbit.frame(0f),PremiumOrbit.frame(Float.NaN))
        assertEquals(PremiumOrbit.frame(0f),PremiumOrbit.frame(-100f))
        (0..2000).forEach { t ->val f=PremiumOrbit.frame(t.toFloat())
            assertTrue(abs(f.planetX)<=.02201f && abs(f.planetY)<=.00901f)
            assertTrue(abs(f.moonX)<=.00601f && abs(f.moonY)<=.02601f)
        }
    }
    @Test fun buttonPassesAreStaggeredAndSleepBetweenSweeps() {
        assertEquals(0f,homeTilePhase(0f,0),0f);assertEquals(0f,homeTilePhase(1f,0),0f)
        assertEquals(0f,homeTilePhase(Float.NaN,4),0f)
        assertTrue(homeTilePhase(.4f,0)>homeTilePhase(.4f,7))
    }
    @Test fun smallMoonStaysCompletelyVisibleAndSceneCoversEveryEdge() {
        listOf(320f to 252f,360f to 244f,360f to 270f,750f to 270f,960f to 270f,1280f to 270f).forEach { (w,h) ->
            val fit=PremiumOrbit.fit(w,h,1610f,977f)
            (0..1000).forEach {t ->
                val f=PremiumOrbit.frame(t.toFloat());val left=fit.left+f.planetX*w;val top=fit.top+f.planetY*h
                assertTrue(left<=0f && top<=0f)
                assertTrue(left+1610f*fit.scale>=w && top+977f*fit.scale>=h)
                val moonX=left+(185f+f.moonX*1610f)*fit.scale
                val moonY=top+(661f+f.moonY*977f)*fit.scale;val radius=32f*fit.scale
                assertTrue("Moon clipped at $w x $h / $t",moonX-radius>=0f && moonY-radius>=0f && moonX+radius<=w && moonY+radius<=h)
            }
        }
    }

}
