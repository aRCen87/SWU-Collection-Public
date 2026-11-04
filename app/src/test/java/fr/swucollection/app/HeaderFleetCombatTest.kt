package fr.swucollection.app

import org.junit.Assert.*
import org.junit.Test

class HeaderFleetCombatTest {
    @Test fun noShotsInQuietIntervalsOrInvalidFrames() {
        for (p in listOf(0f, 1f, -1f, 2f, Float.NaN, Float.POSITIVE_INFINITY, 0.1f, 0.9f)) {
            assertTrue(HeaderFleetTimeline.bolts(p).isEmpty())
        }
    }

    @Test fun eachFighterFiresFromItsOwnPositionAndBoltsTravelForward() {
        for ((fighter, time) in listOf(1 to 1.18f, 2 to 1.42f)) {
            val source = HeaderFleetTimeline.ships(time / 5.2f)[fighter]
            val fired = HeaderFleetTimeline.bolts((time + 0.001f) / 5.2f)
                .minBy { kotlin.math.abs(it.x - source.x) }
            assertEquals(source.x, fired.x, 4f)
            assertEquals(source.y, fired.y, 2f)
        }
        val first = HeaderFleetTimeline.bolts(1.20f / 5.2f).first()
        val later = HeaderFleetTimeline.bolts(1.26f / 5.2f).first()
        assertTrue(later.x < first.x)
        assertTrue(later.alpha < first.alpha)
        assertTrue(first.angle.isFinite())
    }

    @Test fun dodgingStaysBelowLogoAndPursuersStayBehind() {
        for (i in 1..999) {
            val ships = HeaderFleetTimeline.ships(i / 1000f)
            assertTrue(ships.all { it.y in 116f..160f && it.heading.isFinite() })
            assertTrue(ships.drop(1).all { it.x > ships.first().x })
            assertTrue(HeaderFleetTimeline.bolts(i / 1000f).all { it.alpha in 0f..1f && it.x.isFinite() })
        }
    }
}
