package fr.swucollection.app

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPOutputStream
import org.junit.Assert.*
import org.junit.Test

class OfflineTradeQrTest {
    private fun item(n: Int, quantity: Int = 1) = TradeItem("JTL", n.toString().padStart(3, '0'),
        if (n % 2 == 0) "Hyperspace Foil" else "Normale", "Éclaireur de l’Empire n° $n", "famille:$n", quantity)
    private fun rejected(block: () -> Unit) {
        try { block(); fail("Invalid profile accepted") } catch (_: Exception) { }
    }
    private fun rawFrames(bytes: ByteArray): List<String> {
        val packed = ByteArrayOutputStream().also { output -> GZIPOutputStream(output).use { it.write(bytes) } }.toByteArray()
        val chunks = Base64.getUrlEncoder().withoutPadding().encodeToString(packed).chunked(OfflineTradeQr.CHUNK_LENGTH)
        return chunks.mapIndexed { index, chunk -> "${OfflineTradeQr.PREFIX}${OfflineTradeQr.fingerprint(packed)}:${chunks.size}:$index:$chunk" }
    }
    @Test fun historicalCompactVersionsPreserveQuantitiesWithoutInventingALanguage() {
        for (version in 1..2) {
            val preference = if (version == 2) ",true" else ""
            val compact = """{"v":$version,"d":["SOR","236","Normale","R2-D2","r2"],"o":[[0,1,2,3,4,2$preference]],"n":[[0,1,2,3,4,3$preference]],"a":true}"""
            val receiver = OfflineTradeReceiver()
            var restored: TradeProfile? = null
            rawFrames(compact.toByteArray(Charsets.UTF_8)).reversed().forEach { restored = receiver.accept(it) ?: restored }
            val offer = TradeItem("SOR","236","Normale","R2-D2","r2",2,version == 2,CardLanguages.UNKNOWN)
            assertEquals(TradeProfile(listOf(offer),listOf(offer.copy(quantity=3)),true),restored)
            val wantsFrench = TradeProfile(emptyList(),listOf(offer.copy(language="FR")))
            assertTrue(TradeComparison.matching(restored!!,wantsFrench).isEmpty())
        }
    }
    @Test fun singleCodePreservesAccentsQuantitiesAndVariantPreferences() {
        val profile = TradeProfile(listOf(item(1, 7)), listOf(item(2, 3)), true)
        val frames = OfflineTradeQr.frames(profile)
        assertEquals(1, frames.size)
        assertEquals(profile, OfflineTradeReceiver().accept(frames.single()))
        val empty = TradeProfile(emptyList(), emptyList())
        assertEquals(empty, OfflineTradeReceiver().accept(OfflineTradeQr.frames(empty).single()))
    }
    @Test fun wholeCatalogueWorksWithUnorderedRepeatedCodesAndNoNetwork() {
        val profile = TradeProfile((1..8453).map { item(it, 3) }, emptyList(), true)
        val frames = OfflineTradeQr.frames(profile)
        assertTrue(frames.size > 1)
        val receiver = OfflineTradeReceiver()
        // Receive all but one in reverse order; duplicates must not advance reception.
        for (frame in frames.drop(1).reversed()) {
            assertNull(receiver.accept(frame))
            val count = receiver.received
            assertNull(receiver.accept(frame))
            assertEquals(count, receiver.received)
        }
        assertEquals(listOf(1), receiver.missing)
        assertEquals(profile, receiver.accept(frames.first()))
        assertEquals(frames.size, receiver.received)
        assertEquals(profile, receiver.accept(frames.last()))
    }
    @Test fun actualQrImagesCanBeDecodedAtTheDisplaySize() {
        val profile = TradeProfile((1..250).map { item(it) }, listOf(item(8000)))
        val frames = OfflineTradeQr.frames(profile)
        val receiver = OfflineTradeReceiver()
        var result: TradeProfile? = null
        for (frame in frames) {
            val matrix = QRCodeWriter().encode(frame, BarcodeFormat.QR_CODE, 900, 900,
                mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 4))
            val pixels = IntArray(900 * 900) { i -> if (matrix[i % 900, i / 900]) 0xff000000.toInt() else 0xffffffff.toInt() }
            val image = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(900, 900, pixels)))
            val decoded = QRCodeReader().decode(image, mapOf(DecodeHintType.PURE_BARCODE to true)).text
            assertEquals(frame, decoded)
            result = receiver.accept(decoded)
        }
        assertEquals(profile, result)
    }
    @Test fun exchangingBothSnapshotsGivesReciprocalResultsWithoutChangingEitherProfile() {
        val a = TradeProfile(listOf(item(1, 3)), listOf(item(2, 2)))
        val b = TradeProfile(listOf(item(2, 1)), listOf(item(1, 2)))
        fun transfer(profile: TradeProfile): TradeProfile {
            val receiver = OfflineTradeReceiver()
            return OfflineTradeQr.frames(profile).mapNotNull { receiver.accept(it) }.single()
        }
        val onA = transfer(b)
        val onB = transfer(a)
        assertEquals(TradeComparison.matching(a, onA), TradeComparison.matching(onB, b))
        assertEquals(TradeComparison.matching(onA, a), TradeComparison.matching(b, onB))
        assertEquals(2, TradeComparison.matching(a, onA).sumOf { it.quantity })
        assertEquals(1, TradeComparison.matching(onA, a).sumOf { it.quantity })
        assertEquals(a, onB); assertEquals(b, onA)
    }
    @Test fun anotherProfileCannotPolluteAnIncompleteReception() {
        val a = TradeProfile((1..250).map { item(it) }, emptyList())
        val frames = OfflineTradeQr.frames(a)
        assertTrue(frames.size > 1)
        val receiver = OfflineTradeReceiver()
        assertNull(receiver.accept(frames.first()))
        val other = OfflineTradeQr.frames(TradeProfile(listOf(item(999)), emptyList())).first()
        rejected { receiver.accept(other) }
        assertEquals(1, receiver.received)
        var result: TradeProfile? = null
        for (frame in frames.drop(1)) result = receiver.accept(frame)
        assertEquals(a, result)
    }
    @Test fun malformedLegacyTruncatedAndDamagedCodesAreRejected() {
        val valid = OfflineTradeQr.frames(TradeProfile(listOf(item(1)), emptyList())).single()
        val prefix = "${OfflineTradeQr.PREFIX}${"a".repeat(64)}:"
        listOf("https://example.org", "SWUTRADE1:{}", prefix + "0:0:abc", prefix + "1025:0:abc",
            prefix + "1:1:abc", prefix + "1:-1:abc", prefix + "1:0:!", prefix + "2:0:abc", valid.dropLast(5)).forEach { text ->
            rejected { OfflineTradeReceiver().accept(text) }
        }
        val offset = OfflineTradeQr.PREFIX.length
        val damaged = valid.replaceRange(offset, offset + 1, if (valid[offset] == 'a') "b" else "a")
        rejected { OfflineTradeReceiver().accept(damaged) }
        assertEquals(0, OfflineTradeReceiver().received)
    }
    @Test fun checksumAloneDoesNotBypassDecompressionAndSchemaLimits() {
        val bomb = rawFrames(ByteArray(4 * 1024 * 1024 + 1) { 'a'.code.toByte() })
        rejected { val receiver = OfflineTradeReceiver(); bomb.forEach { receiver.accept(it) } }
        val badDictionary = rawFrames("""{"v":1,"d":[],"o":[[0,0,0,0,0,1]],"n":[],"a":false}""".toByteArray())
        rejected { val receiver = OfflineTradeReceiver(); badDictionary.forEach { receiver.accept(it) } }
        val duplicate = TradeProfile(listOf(item(1), item(1)), emptyList())
        rejected { OfflineTradeQr.frames(duplicate) }
    }
}
