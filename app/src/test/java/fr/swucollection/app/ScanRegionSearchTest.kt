package fr.swucollection.app

import org.junit.Assert.*
import org.junit.Test

class ScanRegionSearchTest {
    @Test fun allEightPosesAreVisitedRegardlessOfTheFullFrameRotationAtTheAttemptTime() {
        val search = ScanRegionSearch()
        val seen = mutableSetOf<ScanRegionPose>()
        // At a 300 ms full-frame cadence and 1800 ms regional cadence, a coupled
        // search could keep seeing only 0 and 180 degrees and miss a layout.
        repeat(8) { seen += search.pose; search.observed(null) }
        assertEquals((listOf(false,true).flatMap { landscape ->
            listOf(0,90,180,270).map { ScanRegionPose(landscape,it) }
        }).toSet(),seen)
        assertEquals(ScanRegionPose(false,0),search.pose)
    }

    @Test fun aReadablePoseIsRetainedForTheConsecutiveConfirmationAndTheNextCard() {
        val search = ScanRegionSearch()
        search.observed(null)
        val reference = ScanReference("SOR","029")
        repeat(4) { search.observed(reference);assertEquals(ScanRegionPose(true,0),search.pose) }
        search.observed(null)
        assertEquals(ScanRegionPose(false,90),search.pose)
        search.reset()
        assertEquals(ScanRegionPose(false,0),search.pose)
    }
}
