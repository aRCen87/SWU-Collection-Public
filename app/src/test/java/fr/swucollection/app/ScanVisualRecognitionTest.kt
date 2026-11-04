package fr.swucollection.app

import org.junit.Assert.*
import org.junit.Test

class ScanVisualRecognitionTest {
    private val pixels = IntArray(96) { i ->
        val v = 30 + (i * 23 % 160)
        (255 shl 24) or (v shl 16) or (v shl 8) or v
    }
    @Test fun brightnessChangesKeepTheSameSignature() {
        val brighter = pixels.map { p ->
            val v = (p and 255) + 20
            (255 shl 24) or (v shl 16) or (v shl 8) or v
        }.toIntArray()
        assertEquals(0.0, ScanVisualRecognition.distance(ScanVisualRecognition.signature(pixels),
            ScanVisualRecognition.signature(brighter)), .003)
    }
    @Test fun equalIllustrationsKeepTheirExactVariantReferencesAsChoices() {
        val signature = ScanVisualRecognition.signature(pixels)
        val first = VisualIndexEntry(ScanReference("JTL","551"), "Carte", "url", false, signature)
        val other = first.copy(reference = ScanReference("JTL","552"))
        val result = ScanVisualRecognition.candidates(listOf(signature),listOf(first,other),false)
        assertEquals(setOf(first.reference,other.reference),result.map { it.reference }.toSet())
        assertTrue(ScanVisualRecognition.candidates(listOf(signature),listOf(first),true).isEmpty())
    }
    @Test fun unrelatedFramesDoNotProposeACard() {
        val signature = ScanVisualRecognition.signature(pixels)
        val entry = VisualIndexEntry(ScanReference("SOR","001"),"Carte","url",false,signature)
        val uniform = ScanVisualRecognition.signature(IntArray(96) { 0xff888888.toInt() })
        assertTrue(ScanVisualRecognition.candidates(listOf(uniform),listOf(entry),false).isEmpty())
    }
    @Test fun frontAndBackAreDeduplicatedByExactReference() {
        val signature = ScanVisualRecognition.signature(pixels)
        val entry = VisualIndexEntry(ScanReference("SOR","001"),"Carte","front",false,signature)
        assertEquals(1,ScanVisualRecognition.candidates(listOf(signature),listOf(entry,entry.copy(imageUrl="back")),false).size)
    }
}
