package fr.swucollection.app

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class BorderTiltTest {
    private val gravity = 9.81f

    @Test fun firstReadingCalibratesToTheCurrentGrip() {
        val filter = BorderTiltFilter()
        assertEquals(BorderTilt(), filter.update(1f, 9f, 3f, 0, 1L))
        assertEquals(BorderTilt(), filter.update(1f, 9f, 3f, 0, 33_000_001L))
    }

    @Test fun movementIsSmoothAndBounded() {
        val filter = BorderTiltFilter()
        filter.update(0f, 0f, gravity, 0, 0L)
        val first = requireNotNull(filter.update(8f, 0f, 6f, 0, 33_000_000L))
        assertTrue(first.x > 0f && first.x < 0.4f)
        var last = first
        for (i in 2..60) last = requireNotNull(filter.update(8f, 0f, 6f, 0, i * 33_000_000L))
        assertTrue(last.x in 0.99f..1f)
        assertEquals(0f, last.y, 0.001f)
    }

    @Test fun screenRotationRemapsHorizontalMovementAndRecalibrates() {
        val portrait = BorderTiltFilter()
        val landscape = BorderTiltFilter()
        portrait.update(0f, 0f, gravity, 0, 0L)
        landscape.update(0f, 0f, gravity, 1, 0L)
        val a = requireNotNull(portrait.update(2f, 0f, 9.6f, 0, 33_000_000L))
        val b = requireNotNull(landscape.update(0f, -2f, 9.6f, 1, 33_000_000L))
        assertEquals(a.x, b.x, 0.001f)
        assertEquals(a.y, b.y, 0.001f)
        assertEquals(BorderTilt(), portrait.update(2f, 0f, 9.6f, 1, 66_000_000L))
    }

    @Test fun pitchWrapDoesNotProduceAFullStrengthFlash() {
        val filter = BorderTiltFilter()
        filter.update(0f, 0.2f, -9.8f, 0, 0L)
        val next = requireNotNull(filter.update(0f, -0.2f, -9.8f, 0, 33_000_000L))
        assertTrue(abs(next.y) < 0.2f)
    }

    @Test fun malformedReadingsAndAccelerationSpikesAreIgnored() {
        val filter = BorderTiltFilter()
        assertNull(filter.update(Float.NaN, 0f, gravity, 0, 0L))
        assertNull(filter.update(0f, 0f, 0f, 0, 0L))
        assertNull(filter.update(30f, 0f, gravity, 0, 0L))
        assertEquals(BorderTilt(), filter.update(0f, 0f, gravity, 0, 1L))
        assertNull(filter.update(1f, 0f, gravity, 0, 1L))
    }

    @Test fun pauseResetRecalibratesWithoutCarryingPreviousMovement() {
        val filter = BorderTiltFilter()
        filter.update(0f, 0f, gravity, 0, 0L)
        filter.update(3f, 0f, 9f, 0, 33_000_000L)
        filter.reset()
        assertEquals(BorderTilt(), filter.update(3f, 0f, 9f, 0, 66_000_000L))
    }

    @Test fun smoothingIsIndependentOfSensorSampleRate() {
        fun result(step: Long, count: Int): BorderTilt {
            val filter = BorderTiltFilter()
            filter.update(0f, 0f, gravity, 0, 0L)
            var last = BorderTilt()
            for (i in 1..count) last = requireNotNull(filter.update(2f, 0f, 9.6f, 0, i * step))
            return last
        }
        assertEquals(result(33_000_000L, 20).x, result(66_000_000L, 10).x, 0.001f)
    }
}
