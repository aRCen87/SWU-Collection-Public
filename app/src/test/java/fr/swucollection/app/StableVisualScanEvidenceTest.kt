package fr.swucollection.app

import org.junit.Assert.*
import org.junit.Test

class StableVisualScanEvidenceTest {
    private val french = ScanReference("SOR", "317") to "FR"
    private val english = french.first to "EN"

    @Test fun requiresTheSameReferenceAndLanguageTwice() {
        val gate = StableVisualScanEvidence()
        assertNull(gate.observe(french, 1000))
        assertNull(gate.observe(english, 2800))
        assertEquals(english, gate.observe(english, 4600))
    }

    @Test fun changingTheCardDiscardsThePreviousPartialRead() {
        val gate = StableVisualScanEvidence()
        val other = ScanReference("SOR", "236") to "FR"
        assertNull(gate.observe(french, 1000))
        assertNull(gate.observe(other, 2800))
        assertNull(gate.observe(french, 4600))
        assertEquals(french, gate.observe(french, 6400))
    }

    @Test fun missingOrContradictoryFooterBreaksTheSequence() {
        val gate = StableVisualScanEvidence()
        gate.observe(french, 1000)
        assertNull(gate.observe(null, 2800))
        assertNull(gate.observe(french, 4600))
        assertEquals(french, gate.observe(french, 6400))
    }

    @Test fun interruptionAndLongGapsRequireFreshEvidence() {
        val gate = StableVisualScanEvidence()
        gate.observe(french, 1000)
        gate.interrupt()
        assertNull(gate.observe(french, 2800))
        assertNull(gate.observe(french, 9000))
        assertEquals(french, gate.observe(french, 10800))
    }

    @Test fun nearbyVisualSuggestionsCanChangeWhileTheExactPrintingStaysStable() {
        val exact = VisualCardMatch(french.first, "R2-D2", "art", .025)
        val similar = exact.copy(reference = ScanReference("SOR", "236"))
        val gate = StableVisualScanEvidence()
        assertNull(gate.observe(ScanVisualRecognition.corroborated("FR C 317", listOf(exact, similar)), 1000))
        assertEquals(french, gate.observe(ScanVisualRecognition.corroborated("FR C 317", listOf(exact)), 2800))
    }
}
