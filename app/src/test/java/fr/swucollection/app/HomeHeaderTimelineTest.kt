package fr.swucollection.app

import org.junit.Assert.*
import org.junit.Test

class HomeHeaderTimelineTest {
    private fun at(milliseconds: Float) = HomeHeaderTimeline.frame(milliseconds / HomeHeaderTimeline.DURATION_MS)

    @Test fun backdropRemainsUnchangedThroughoutTheIntro() {
        for (i in 0..100) assertEquals(0f, HomeHeaderTimeline.frame(i / 100f).night, 0f)
        assertFalse(at(400f).starVisible)
        assertTrue(at(600f).starVisible)
    }
    @Test fun starArrivesFromTheRightAndLandsExactlyInTheLogo() {
        val start = at(500f)
        val middle = at(1600f)
        val arrived = at(2600f)
        assertTrue(start.star.x > 475f)
        assertTrue(middle.star.x < start.star.x && middle.star.x > arrived.star.x)
        assertTrue(middle.star.y > start.star.y && middle.star.y < arrived.star.y)
        assertTrue(middle.tail.x > middle.star.x)
        assertTrue(middle.tail.y < middle.star.y)
        assertEquals(HomeHeaderTimeline.target, arrived.star)
    }
    @Test fun finalStarIsFixedAndItsTrailAndGlowDisappear() {
        val end = at(HomeHeaderTimeline.DURATION_MS.toFloat())
        assertTrue(end.starVisible)
        assertEquals(HomeHeaderTimeline.target, end.star)
        assertEquals(0f, end.trailAlpha, 0f)
        assertEquals(0f, end.glow, 0f)
        assertEquals(end, at(10000f))
        assertTrue(at(2900f).trailAlpha < at(2600f).trailAlpha)
    }
    @Test fun progressionIsBoundedAndFinite() {
        for (i in 0..100) {
            val frame = HomeHeaderTimeline.frame(i / 100f)
            assertTrue(frame.trailAlpha in 0f..1f)
            assertTrue(frame.glow in 0f..1f)
            assertTrue(frame.star.x.isFinite() && frame.star.y.isFinite())
        }
        assertEquals(at(0f), HomeHeaderTimeline.frame(Float.NaN))
        assertEquals(at(0f), HomeHeaderTimeline.frame(-1f))
    }
}
