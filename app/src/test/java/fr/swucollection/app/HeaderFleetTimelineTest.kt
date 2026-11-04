package fr.swucollection.app

import org.junit.Assert.*
import org.junit.Test

class HeaderFleetTimelineTest {
    @Test fun quietIntervalsAndInvalidValuesDrawNothing() {
        for (value in listOf(0f, 1f, -1f, 2f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertTrue(HeaderFleetTimeline.ships(value).isEmpty())
        }
    }

    @Test fun wholeFormationEntersAndExitsBeyondTheBanner() {
        assertTrue(HeaderFleetTimeline.ships(0.001f).all { it.x > 475f + 20f })
        assertTrue(HeaderFleetTimeline.ships(0.999f).all { it.x < -20f })
        val before = HeaderFleetTimeline.ships(0.4f)
        val after = HeaderFleetTimeline.ships(0.5f)
        assertEquals(3, before.size)
        assertEquals(2, before.count { it.tie })
        before.zip(after).forEach { (a, b) ->
            assertTrue(b.x < a.x)
            assertTrue(a.y in 120f..160f)
        }
    }

    @Test fun formationCrossesThePlanetWithAQuietInterval() {
        assertTrue(HeaderFleetTimeline.ships(0.3f).any { it.x in 280f..420f })
        assertTrue(HeaderFleetTimeline.PERIOD_MS - HeaderFleetTimeline.FLIGHT_MS > 15000)
    }
}
