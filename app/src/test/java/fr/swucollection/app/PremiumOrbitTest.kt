package fr.swucollection.app

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class PremiumOrbitTest {
    @Test fun planetIsOnlySlightlyFasterAndMoonHasAnotherPeriodAndAxis() {
        assertTrue(PremiumOrbit.PLANET_SECONDS in 150f..175f)
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
}
