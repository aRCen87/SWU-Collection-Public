package fr.swucollection.app

import org.junit.Assert.*
import org.junit.Test

class ScanRegionEvidenceTest {
    private val french = ScanOcrReading("", "Directeur Krennic", "SOR FR 001", true)

    @Test fun neutralFramesDoNotDiscardTwoConsistentRegionalReads() {
        val evidence = ScanRegionEvidence()
        assertNull(evidence.observe(french,1000))
        assertTrue(evidence.visible(1000))
        assertNull(evidence.observe(ScanOcrReading("Texte illisible"),1300))
        assertEquals(ScanReference("SOR","001") to "FR", evidence.observe(french,2800))
    }

    @Test fun changingLanguageOrPrintingRequiresTwoFreshReads() {
        val evidence = ScanRegionEvidence()
        evidence.observe(french,1000)
        val english = french.copy(footer="SOR EN 001")
        assertNull(evidence.observe(english,2800))
        assertEquals("EN",evidence.observe(english,4600)?.second)
        assertNull(evidence.observe(french.copy(footer="SOR FR 002"),6400))
    }

    @Test fun fullFrameConflictsRemainConflictsAndNeverCreateAnAutomaticPrinting() {
        val evidence = ScanRegionEvidence()
        assertNull(evidence.observe(french.copy(full="SHD FR 002"),1000))
        assertFalse(evidence.visible(1000))
        assertNull(evidence.observe(french.copy(full="SHD FR 002"),2800))
    }

    @Test fun partialReferencesInDifferentRegionsAreNeverJoinedIntoAnInventedPrinting() {
        val reading = ScanOcrReading("SOR FR", "Unité 30", "001/252",true)
        assertNull(reading.reference)
        val evidence = ScanRegionEvidence()
        assertNull(evidence.observe(reading,1000))
        assertNull(evidence.observe(reading,2800))
        assertFalse(evidence.visible(2800))
    }

    @Test fun aHeldCardRemainsVisibleAfterConfirmationAndCannotRearmTheMainGate() {
        val evidence = ScanRegionEvidence()
        val gate = StableScanGate()
        evidence.observe(french,1000)
        val reference = evidence.observe(french,2800)!!.first
        gate.block(reference); evidence.confirmed()
        for (time in 3100L..4300L step 300) {
            evidence.observe(ScanOcrReading(""),time)
            if (!evidence.visible(time)) gate.observe(null,time)
        }
        assertEquals(reference,gate.blockedReference)
        assertNull(evidence.observe(french,4600))
        assertEquals(reference,evidence.observe(french,6400)?.first)
        assertEquals(reference,gate.blockedReference)
    }

    @Test fun removalPauseAndNewFullFrameDiscardTheOldRegionalEvidence() {
        val evidence = ScanRegionEvidence()
        evidence.observe(french,1000)
        evidence.observe(ScanOcrReading("",regionsAttempted=true),2800)
        assertFalse(evidence.visible(2800))
        assertNull(evidence.observe(french,4600))
        evidence.interrupt()
        assertNull(evidence.observe(french,6400))
        evidence.observe(ScanOcrReading("SOR EN 002"),6700)
        assertFalse(evidence.visible(6700))
        assertNull(evidence.observe(french,8200))
        assertFalse(evidence.visible(15000))
    }
}
