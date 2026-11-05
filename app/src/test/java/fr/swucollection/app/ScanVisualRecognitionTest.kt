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

    @Test fun fineDetailsSeparateCardsWhoseCoarseColourSignaturesAreIdentical() {
        val coarse = ScanVisualRecognition.signature(IntArray(96) { 0xff888888.toInt() })
        val detailed = IntArray(16*24) { i ->
            if ((i%16+i/16)%2 == 0) 0xff444444.toInt() else 0xffcccccc.toInt()
        }
        val right = ScanVisualRecognition.signature(detailed)
        val wrong = ScanVisualRecognition.signature(detailed.map { if (it == 0xff444444.toInt()) 0xffcccccc.toInt() else 0xff444444.toInt() }.toIntArray())
        val first = VisualIndexEntry(ScanReference("SOR","001"),"Premier","url",false,coarse,wrong)
        val second = first.copy(reference=ScanReference("SOR","002"), detail=right)
        val matches = ScanVisualRecognition.candidates(listOf(coarse),listOf(first,second),false,listOf(right))
        assertEquals(second.reference,matches.first().reference)
        assertEquals(0.0,matches.first().score,0.00001)
    }

    @Test fun exactTitleCanResolveAnOtherwiseAmbiguousNumberButNeverOverridesAConflict() {
        val first = VisualCardMatch(ScanReference("SOR","317"),"Directeur Krennic — Aspire au pouvoir","url",.025,
            englishName="Director Krennic — Aspiring to Authority")
        val second = first.copy(reference=ScanReference("SHD","317"),name="Grand Amiral Thrawn",englishName="Grand Admiral Thrawn")
        val matches = listOf(first,second)
        assertNull(ScanVisualRecognition.corroborated("FR C 317",matches))
        assertEquals(first.reference to "FR",ScanVisualRecognition.corroborated("FR C 317",matches,"DIRECTEUR KRENNIC"))
        assertEquals(first.reference to "EN",ScanVisualRecognition.corroborated("EN C 317",matches,"Director Krennic"))
        assertNull(ScanVisualRecognition.corroborated("FR C 317",matches,"Krennic"))
        assertNull(ScanVisualRecognition.corroborated("SOR FR 317\nSHD FR 317",matches,"Directeur Krennic"))
        assertNull(ScanVisualRecognition.corroborated("FR C 317",listOf(first.copy(reference=ScanReference("SOR","236")),second),"Directeur Krennic"))
    }

    @Test fun detailedComparisonPairsEachCropWithItsOwnCoarseSignature() {
        val coarse = ScanVisualRecognition.signature(pixels)
        val detail = ScanVisualRecognition.signature(IntArray(16*24) { pixels[it%96] })
        val entry = VisualIndexEntry(ScanReference("SOR","001"),"Carte","url",false,coarse,detail)
        assertEquals(0.0,ScanVisualRecognition.candidates(listOf(coarse),listOf(entry),false,listOf(detail)).single().score,.00001)
        assertThrows(IllegalArgumentException::class.java) {
            ScanVisualRecognition.candidates(listOf(coarse,coarse),listOf(entry),false,listOf(detail))
        }
    }

    @Test fun detailedSimilarityCannotRescueAnUnrelatedGlobalImage() {
        val coarse = ScanVisualRecognition.signature(pixels)
        val uniform = ScanVisualRecognition.signature(IntArray(96) { 0xff888888.toInt() })
        val fine = ScanVisualRecognition.signature(IntArray(16*24) { 0xff888888.toInt() })
        val entry = VisualIndexEntry(ScanReference("SOR","001"),"Carte","url",false,coarse,fine)
        assertTrue(ScanVisualRecognition.candidates(listOf(uniform),listOf(entry),false,listOf(fine)).isEmpty())
    }

    @Test fun partialNumberRetainsTheExactPrintingEvenWithManyIdenticalIllustrations() {
        val signature = ScanVisualRecognition.signature(pixels)
        val entries = (1..12).map { n -> VisualIndexEntry(ScanReference("SOR",n.toString().padStart(3,'0')),
            "Même illustration","url",false,signature) }
        val reference = ScanReference("SOR","012")
        assertFalse(ScanVisualRecognition.candidates(listOf(signature),entries,false).any { it.reference == reference })
        assertEquals(reference,ScanVisualRecognition.candidates(listOf(signature),entries,false,numberHint=12).single().reference)
        assertTrue(ScanVisualRecognition.candidates(listOf(signature),entries,false,numberHint=317).isEmpty())
        assertEquals(317 to "EN",ScanVisualRecognition.partialFooter("ＥＮ Ⓒ ３１７"))
        assertNull(ScanVisualRecognition.partialFooter("FR C 000"))
        assertNull(ScanVisualRecognition.partialFooter("FR C 317\nEN C 317"))
    }
}
