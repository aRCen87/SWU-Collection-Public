package fr.swucollection.app

import org.junit.Assert.*
import org.junit.Test

class StableScanGateTest {
    private val first = ScanReference("HMW", "001")
    private val second = ScanReference("HMW", "002")
    @Test fun needsTwoConsecutiveConsistentFrames() {
        val gate = StableScanGate()
        assertNull(gate.observe(first, 1000))
        assertEquals(first, gate.observe(first, 1500))
    }
    @Test fun aHeldCardIsDeliveredOnlyOnce() {
        val gate = StableScanGate()
        gate.observe(first, 1000);gate.observe(first, 1500)
        for (t in 2000L..8000L step 500) assertNull(gate.observe(first, t))
    }
    @Test fun aPauseWithoutFramesDoesNotCountTheHeldCardAgain() {
        val gate = StableScanGate()
        gate.observe(first, 1000); gate.observe(first, 1500)
        // Camera reconnects can leave a long gap without a single absent frame.
        assertNull(gate.observe(first, 30000))
        assertNull(gate.observe(first, 30500))
        gate.observe(null, 31000); gate.observe(null, 32000)
        assertNull(gate.observe(first, 32500))
        assertEquals(first, gate.observe(first, 33000))
    }
    @Test fun changingCardsOrManuallyResettingAllowsANewConfirmation() {
        val gate = StableScanGate()
        gate.observe(first, 1000);gate.observe(first, 1500)
        assertNull(gate.observe(second, 2000));assertEquals(second, gate.observe(second, 2500))
        gate.reset()
        assertNull(gate.observe(second, 3000));assertEquals(second, gate.observe(second, 3500))
    }
    @Test fun briefUnreadableFramesDoNotReconfirmTheSameCard() {
        val gate = StableScanGate()
        gate.observe(first, 1000);gate.observe(first, 1500)
        gate.observe(null, 2000);gate.observe(null, 2450)
        assertNull(gate.observe(first, 2500));assertNull(gate.observe(first, 3000))
    }
    @Test fun removingACardRearmsScanning() {
        val gate = StableScanGate()
        gate.observe(first, 1000);gate.observe(first, 1500)
        gate.observe(null, 2000);gate.observe(null, 3000)
        assertNull(gate.observe(first, 3500));assertEquals(first, gate.observe(first, 4000))
    }
    @Test fun interveningReadingsOrLongGapsDiscardUnstableResults() {
        val gate = StableScanGate()
        assertNull(gate.observe(first, 1000));assertNull(gate.observe(second, 1500))
        assertNull(gate.observe(first, 2000));assertNull(gate.observe(null, 2500))
        assertNull(gate.observe(first, 3000));assertNull(gate.observe(first, 6000))
        assertEquals(first, gate.observe(first, 6500))
    }
    @Test fun burstCadenceKeepsHeldCardsBlockedAndAcceptsTheNextCard() {
        val gate = StableScanGate()
        assertNull(gate.observe(first, 1000, "FR"))
        assertEquals(first, gate.observe(first, 1300, "FR"))
        assertNull(gate.observe(first, 1600, "FR"))
        assertNull(gate.observe(null, 1900))
        assertNull(gate.observe(first, 2200, "FR"))
        assertNull(gate.observe(second, 2500, "EN"))
        assertEquals(second, gate.observe(second, 2800, "EN"))
        assertNull(gate.observe(second, 3100, "EN"))
    }
    @Test fun pauseDiscardsPartialReadsAndNeverUsesPausedTimeAsRemoval() {
        val gate = StableScanGate()
        gate.observe(first, 1000)
        gate.interrupt()
        assertNull(gate.observe(first, 1500))
        assertEquals(first, gate.observe(first, 2000))
        gate.observe(null, 2500)
        gate.interrupt()
        gate.observe(null, 30000)
        assertNull(gate.observe(first, 30500))
        assertNull(gate.observe(first, 31000))
    }
    @Test fun manualConfirmationBlocksTheSameCardUntilRemovalOrExplicitRetry() {
        val gate = StableScanGate()
        gate.block(first)
        assertNull(gate.observe(first, 1000))
        assertNull(gate.observe(first, 1500))
        gate.reset()
        assertNull(gate.observe(first, 2000))
        assertEquals(first, gate.observe(first, 2500))
    }
    @Test fun anInterveningBlockedCardDiscardsAPartialReadOfTheNextCard() {
        val gate = StableScanGate()
        gate.observe(first,1000);gate.observe(first,1500)
        assertNull(gate.observe(second,2000))
        assertNull(gate.observe(first,2450))
        assertNull(gate.observe(second,2900))
        assertEquals(second,gate.observe(second,3350))
    }
}
