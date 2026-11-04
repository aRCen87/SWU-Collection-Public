package fr.swucollection.app

import org.junit.Assert.*
import org.junit.Test

class ScanFrameRecognitionTest {
    private fun word(text: String, x: Int, y: Int = 200, width: Int = 30) = ScanOcrWord(text, x, y, x + width, y + 16)

    @Test fun reconstructsALandscapeBaseFooterSplitAcrossOcrBlocks() {
        val text = ScanFrameRecognition.text("SOR FR\nSCARIF\n019/252", listOf(
            word("019/252", 150), word("SCARIF", 40, 140), word("FR", 50), word("SOR", 0), word("R", 100)))
        assertEquals(ScanReference("SOR", "019"), ScanRecognition.extract(text))
        assertEquals("FR", ScanRecognition.language(text))
    }

    @Test fun keepsExactHyperspaceAndFourDigitReferencesAndLanguage() {
        listOf("317" to "SOR", "788" to "JTL", "1093" to "JTL").forEach { (number, set) ->
            val text = ScanFrameRecognition.text("$set EN\nSTAR WARS\n$number", listOf(
                word(number, 150), word("EN", 50), word(set, 0), word("L", 100)))
            assertEquals(ScanReference(set, number), ScanRecognition.extract(text))
            assertEquals("EN", ScanRecognition.language(text))
        }
    }

    @Test fun readsFooterLettersSplitIntoIndividualOcrWords() {
        val letters = "SORFRR019/252".mapIndexed { i, character -> word(character.toString(), i * 15, width = 10) }
        val text = ScanFrameRecognition.text("SCARIF\n0 1 9\nS O R F R", letters)
        assertEquals(ScanReference("SOR", "019"), ScanRecognition.extract(text))
        assertEquals("FR", ScanRecognition.language(text))
    }

    @Test fun neverJoinsDifferentRowsOrDistantCards() {
        val raw = "SOR FR\nTexte\n019"
        assertNull(ScanRecognition.extract(ScanFrameRecognition.text(raw, listOf(word("SOR FR", 0), word("019", 80, 240)))))
        assertNull(ScanRecognition.extract(ScanFrameRecognition.text(raw, listOf(word("SOR FR", 0), word("019", 700)))))
    }

    @Test fun rejectsConflictingFootersInsteadOfPickingOneCard() {
        val text = ScanFrameRecognition.text("SOR FR 019/252\nASH FR 614", listOf(word("SOR FR", 0), word("019/252", 80)))
        assertNull(ScanRecognition.extract(text))
        val fragmented = ScanFrameRecognition.text("SOR FR\nTEXTE\n019/252\nASH FR\nTEXTE\n614", listOf(
            word("SOR FR", 0), word("019/252", 80), word("ASH FR", 0, 250), word("614", 80, 250)))
        assertNull(ScanRecognition.extract(fragmented))
    }

    @Test fun serialStampDoesNotReplaceReconstructedCollectorNumber() {
        val text = ScanFrameRecognition.text("JTL FR\nTEXTE\n1093\n002/250", listOf(
            word("JTL", 0), word("FR", 50), word("R", 100), word("1093", 150), word("002/250", 0, 250)))
        assertEquals(ScanReference("JTL", "1093"), ScanRecognition.extract(text))
    }

    @Test fun orientationSearchRetainsSuccessfulAngleUntilReadIsLost() {
        val orientation = ScanOrientation()
        val reference = ScanReference("SOR", "019")
        assertEquals(0, orientation.degrees)
        orientation.observed(null)
        assertEquals(90, orientation.degrees)
        orientation.observed(reference)
        orientation.observed(reference)
        assertEquals(90, orientation.degrees)
        repeat(3) { orientation.observed(null) }
        assertEquals(0, orientation.degrees)
        orientation.observed(null); orientation.reset()
        assertEquals(0, orientation.degrees)
    }

    @Test fun orientationRetriesDoNotCountAHeldCardTwiceInBatchMode() {
        val gate = StableScanGate()
        val orientation = ScanOrientation()
        val card = ScanReference("SOR", "019")
        fun read(reference: ScanReference?, time: Long): ScanReference? {
            val result = if (reference != null || orientation.checksAbsence) gate.observe(reference, time) else null
            orientation.observed(reference)
            return result
        }
        assertNull(read(card, 1000)); assertEquals(card, read(card, 1500))
        for (time in listOf(2000L, 2450L, 2900L, 3350L)) assertNull(read(null, time))
        assertNull(read(card, 3800)); assertNull(read(card, 4250))
        // Actual removal across a complete orientation sweep still rearms it.
        for (time in listOf(4700L, 5150L, 5600L, 6050L, 6500L)) assertNull(read(null, time))
        assertNull(read(card, 6950)); assertEquals(card, read(card, 7400))
    }
}
