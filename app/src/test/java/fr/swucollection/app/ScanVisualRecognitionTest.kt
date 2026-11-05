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
    @Test fun cameraDownsamplingAveragesFinePrintInsteadOfAliasingIt() {
        val width=80;val height=120
        val fine=IntArray(width*height) { i -> if ((i%width+i/width)%2==0) 0xff000000.toInt() else 0xffffffff.toInt() }
        val sampled=ScanVisualRecognition.downsample(fine,width,height)
        assertTrue(sampled.all { (it and 255) in 127..128 })
    }
    @Test fun partialFooterAndImageTogetherIdentifyTheExactPrintingAndLanguage() {
        val matches = listOf(VisualCardMatch(ScanReference("SOR","236"),"R2-D2","fr-art",.025),
            VisualCardMatch(ScanReference("SOR","317"),"R2-D2","fr-art",.028))
        assertEquals(ScanReference("SOR","317") to "EN", ScanVisualRecognition.corroborated("R2-D2\nEN C 317",matches))
        assertNull(ScanVisualRecognition.corroborated("R2-D2",matches))
        assertNull(ScanVisualRecognition.corroborated("EN C 317\nFR C 236",matches))
        assertNull(ScanVisualRecognition.corroborated("EN C 317",matches + matches[1].copy(reference=ScanReference("SHD","317"))))
        assertNull(ScanVisualRecognition.corroborated("SOR FR 236\nEN C 317",matches))
    }
}
