package fr.swucollection.app

import org.junit.Assert.*
import org.junit.Test

class BadgeArtCropTest {
    @Test fun everyBadgeAspectRemainsInsideTheIllustration() {
        val faces = listOf(
            Triple("SOR-236", "Unité", 286f to 400f),
            Triple("LOF-056", "Amélioration", 286f to 400f),
            Triple("SOR-005", "Leader", 400f to 286f),
            Triple("SOR-024", "Base", 400f to 286f),
            Triple("SHD-265", "Leader", 400f to 286f),
            Triple("SHD-266", "Leader", 400f to 286f),
            Triple("SHD-276", "Leader", 400f to 286f),
            Triple("NEW-001", "Leader", 286f to 400f)
        )
        for ((reference, type, dimensions) in faces) {
            val art = BadgeArtCrop.illustration(reference, type, dimensions.first, dimensions.second)
            for (aspect in listOf(.12f, .26f, .39f, .78f, 1f, 2f, 4f)) {
                val crop = art.fit(aspect)
                assertTrue(reference, crop.left >= art.left - .001f && crop.right <= art.right + .001f)
                assertTrue(reference, crop.top >= art.top - .001f && crop.bottom <= art.bottom + .001f)
                assertEquals(aspect, crop.width / crop.height, .001f)
            }
        }
    }
    @Test fun standardLeaderNeverSamplesTheRulesOnTheRight() {
        val art = BadgeArtCrop.illustration("SOR-005", "Leader", 400f, 286f)
        assertTrue(art.right <= 172f)
        assertTrue(art.bottom < 232f) // traits printed below the portrait
    }
    @Test fun portraitRulesAndUpgradeTextStayOutsideTheSource() {
        val unit = BadgeArtCrop.illustration("SOR-045", "Unité", 286f, 400f)
        val upgrade = BadgeArtCrop.illustration("LOF-056", "Amélioration", 286f, 400f)
        assertEquals(68f, unit.top, .001f)
        assertTrue(unit.bottom < 240f)
        assertTrue(upgrade.bottom <= 212f)
    }
    @Test fun sourceCoordinatesScaleWithDownloadedResolution() {
        val small = BadgeArtCrop.illustration("SHD-265", "Leader", 400f, 286f)
        val large = BadgeArtCrop.illustration("SHD-265", "Leader", 1200f, 858f)
        assertEquals(small.left * 3f, large.left, .001f)
        assertEquals(small.bottom * 3f, large.bottom, .001f)
        // Showcase art is in the centre; a standard left-side crop would show its rules.
        assertTrue(small.left >= 132f)
    }
}
