package fr.swucollection.app

import org.junit.Assert.*
import org.junit.Test

class ScanFrameSamplingTest {
    @Test fun everyCameraOrientationProducesIdenticalUprightPixelsBeforeRecognition() {
        for ((width, height) in listOf(31 to 47, 32 to 48, 47 to 31, 600 to 800)) {
            val pixels = IntArray(width * height) { i ->
                (255 shl 24) or ((i * 17 % 256) shl 16) or ((i * 37 % 256) shl 8) or (i * 61 % 256)
            }
            val expected = ScanFrameSampling.upright(pixels, width, height, 0, 23)
            for (rotation in listOf(0, 90, 180, 270)) {
                val sensor = ScanFrameSampling.upright(pixels, width, height, (360 - rotation) % 360, maxOf(width, height))
                val actual = ScanFrameSampling.upright(sensor.pixels, sensor.width, sensor.height, rotation, 23)
                assertEquals(expected.width, actual.width)
                assertEquals(expected.height, actual.height)
                assertArrayEquals("$width × $height, rotation $rotation", expected.pixels, actual.pixels)
            }
        }
    }

    @Test fun areaReductionRetainsUniformColoursAndDoesNotModifyTheCameraBuffer() {
        val pixels = IntArray(192) { 0xFF123456.toInt() }
        val original = pixels.copyOf()
        val result = ScanFrameSampling.upright(pixels, 12, 16, 180, 8)
        assertEquals(6, result.width); assertEquals(8, result.height)
        assertTrue(result.pixels.all { it == 0xFF123456.toInt() })
        assertArrayEquals(original, pixels)
    }
}
