package fr.swucollection.app

import android.util.Base64
import java.io.StringReader
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ScanVisualIndexTest {
    private fun index(version: Int = 2, signatureSize: Int = ScanVisualRecognition.LENGTH,
        detailWidth: Int = ScanVisualRecognition.DETAIL_WIDTH): String {
        val signature = Base64.encodeToString(ByteArray(signatureSize) { 128.toByte() },Base64.NO_WRAP)
        val detail = Base64.encodeToString(ByteArray(ScanVisualRecognition.DETAIL_LENGTH) { 127.toByte() },Base64.NO_WRAP)
        val extra = if (version == 2) ",\"$detail\",\"Director Krennic\"" else ""
        // Metadata deliberately follows rows: object order must not change decoding.
        return """{"entries":[["SOR","001","Directeur Krennic","https://example.test/card.png",0,true,"$signature"$extra]],"format":"SWU_VISUAL_INDEX","version":$version,"sampling":"area-v1","detailWidth":$detailWidth,"detailHeight":24}"""
    }

    @Test fun streamingReaderPreservesExactReferenceNamesAndBothSignatures() {
        val entry = ScanVisualIndex.read(StringReader(index())).single()
        assertEquals(ScanReference("SOR","001"),entry.reference)
        assertEquals("Directeur Krennic",entry.name)
        assertEquals("Director Krennic",entry.englishName)
        assertTrue(entry.landscape)
        assertArrayEquals(ByteArray(ScanVisualRecognition.LENGTH) { 128.toByte() },entry.signature)
        assertArrayEquals(ByteArray(ScanVisualRecognition.DETAIL_LENGTH) { 127.toByte() },entry.detail)
    }

    @Test fun originalIndexWithoutDetailedSignaturesRemainsSupported() {
        val entry = ScanVisualIndex.read(StringReader(index(version=1))).single()
        assertNull(entry.detail)
        assertEquals("",entry.englishName)
        assertEquals(ScanReference("SOR","001"),entry.reference)
    }

    @Test fun invalidSamplingDimensionsAndTruncatedSignaturesAreRejected() {
        for (invalid in listOf(index(signatureSize=1),index(detailWidth=8),
            index().replace("area-v1","unknown"),index(version=3))) {
            assertThrows(IllegalArgumentException::class.java) { ScanVisualIndex.read(StringReader(invalid)) }
        }
    }
}
