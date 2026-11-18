package fr.swucollection.app

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.JsonReader
import android.util.JsonToken
import android.util.Log
import java.io.Reader
import java.util.zip.GZIPInputStream
import java.io.FileNotFoundException
import kotlin.math.min
import kotlin.math.roundToInt

internal data class ScanViewport(val width: Int, val height: Int, val landscape: Boolean)

/** Runs on the analysis executor; photographs and signatures never leave the phone. */
internal class ScanVisualMatcher(private val context: Context) {
    private val entries by lazy {
        try {
            val input = try { context.assets.open("scan-visual-index.json") }
                catch (_: FileNotFoundException) { context.assets.open("scan-visual-index.json.gz") }
            input.buffered().use { buffered ->
                buffered.mark(2); val compressed = buffered.read() == 0x1f && buffered.read() == 0x8b; buffered.reset()
                val decoded = if (compressed) GZIPInputStream(buffered) else buffered
                ScanVisualIndex.read(decoded.reader(Charsets.UTF_8))
            }
        } catch (error: Exception) {
            // A malformed/missing optional index disables suggestions, not OCR.
            // Fatal VM errors must never masquerade as an empty recognition result.
            Log.w("ScanVisualMatcher", "Visual index unavailable", error)
            emptyList()
        }
    }

    fun matchAutomatic(frame: Bitmap, rotation: Int, viewport: ScanViewport, numberHint: Int? = null): List<VisualCardMatch> {
        if (viewport.width <= 0 || viewport.height <= 0 || entries.isEmpty()) return emptyList()
        val upright = prepare(frame, rotation)
        val rawMatches = try { listOf(false, true).flatMap { landscape -> matchUpright(upright, viewport.copy(landscape = landscape),numberHint) } }
            finally { if (upright !== frame) upright.recycle() }
        val matches = rawMatches.groupBy { it.reference }.values.map { it.minBy { match -> match.score } }.sortedBy { it.score }
        val best = matches.firstOrNull() ?: return emptyList()
        return matches.takeWhile { it.score <= best.score + .018 }.take(6)
    }

    fun match(frame: Bitmap, rotation: Int, viewport: ScanViewport, numberHint: Int? = null): List<VisualCardMatch> {
        if (viewport.width <= 0 || viewport.height <= 0 || entries.isEmpty()) return emptyList()
        val upright = prepare(frame, rotation)
        return try { matchUpright(upright, viewport, numberHint) }
            finally { if (upright !== frame) upright.recycle() }
    }

    private fun prepare(frame: Bitmap, rotation: Int): Bitmap {
        if (rotation % 360 == 0 && maxOf(frame.width, frame.height) <= 960) return frame
        val pixels = IntArray(frame.width * frame.height)
        frame.getPixels(pixels, 0, frame.width, 0, 0, frame.width, frame.height)
        val sampled = ScanFrameSampling.upright(pixels, frame.width, frame.height, rotation)
        return Bitmap.createBitmap(sampled.pixels, sampled.width, sampled.height, Bitmap.Config.ARGB_8888)
    }

    private fun matchUpright(upright: Bitmap, viewport: ScanViewport, numberHint: Int?): List<VisualCardMatch> {
        if (viewport.width <= 0 || viewport.height <= 0 || entries.isEmpty()) return emptyList()
        run {
            // Same FILL_CENTER transform and guide dimensions as PreviewView/ScannerGuide.
            val scale = maxOf(viewport.width.toDouble() / upright.width, viewport.height.toDouble() / upright.height)
            val ratio = if (viewport.landscape) 88.0 / 63 else 63.0 / 88
            val guideHeight = min(viewport.height * .78, viewport.width * .9 / ratio) / scale
            val guideWidth = guideHeight * ratio
            val signatures = mutableListOf<ByteArray>()
            val details = mutableListOf<ByteArray>()
            // Small positioning errors are expected while hand-holding a card.
            for (size in listOf(.94,1.0,1.06)) {
                val w = guideWidth * size * .88; val h = guideHeight * size * .88
                val left = ((upright.width-w)/2).roundToInt(); val top = ((upright.height-h)/2).roundToInt()
                val width = w.roundToInt(); val height = h.roundToInt()
                if (width < ScanVisualRecognition.DETAIL_WIDTH || height < ScanVisualRecognition.DETAIL_HEIGHT || left < 0 || top < 0 || left+width > upright.width || top+height > upright.height) continue
                val crop = Bitmap.createBitmap(upright,left,top,width,height)
                try {
                    val pixels=IntArray(width*height)
                    crop.getPixels(pixels,0,width,0,0,width,height)
                    val sampled=ScanVisualRecognition.downsample(pixels,width,height)
                    signatures.add(ScanVisualRecognition.signature(sampled))
                    signatures.add(ScanVisualRecognition.signature(sampled.reversedArray()))
                    val detailed = ScanVisualRecognition.downsample(pixels,width,height,
                        ScanVisualRecognition.DETAIL_WIDTH,ScanVisualRecognition.DETAIL_HEIGHT)
                    details.add(ScanVisualRecognition.signature(detailed))
                    details.add(ScanVisualRecognition.signature(detailed.reversedArray()))
                } finally { if(crop !== upright) crop.recycle() }
            }
            return ScanVisualRecognition.candidates(signatures,entries,viewport.landscape,details,numberHint)
        }
    }
}

/** Decode one row at a time: never retain the 20 MB JSON text/tree beside its decoded signatures. */
internal object ScanVisualIndex {
    fun read(input: Reader): List<VisualIndexEntry> = JsonReader(input).use { json ->
        var format: String? = null
        var version = 0
        var sampling: String? = null
        var detailWidth = 0
        var detailHeight = 0
        var entries: List<VisualIndexEntry>? = null
        json.beginObject()
        while (json.hasNext()) {
            when (json.nextName()) {
                "format" -> format = json.nextString()
                "version" -> version = json.nextInt()
                "sampling" -> sampling = json.nextString()
                "detailWidth" -> detailWidth = json.nextInt()
                "detailHeight" -> detailHeight = json.nextInt()
                "entries" -> {
                    require(entries == null) { "Duplicate visual index entries" }
                    val rows = ArrayList<VisualIndexEntry>()
                    json.beginArray()
                    while (json.hasNext()) {
                        json.beginArray()
                        val reference = ScanReference(json.nextString(), json.nextString())
                        val name = json.nextString()
                        val imageUrl = json.nextString()
                        json.skipValue() // artwork face index; both faces retain their reference
                        val landscape = json.nextBoolean()
                        val signature = Base64.decode(json.nextString(), Base64.DEFAULT)
                        require(signature.size == ScanVisualRecognition.LENGTH)
                        val detail = if (json.hasNext()) Base64.decode(json.nextString(), Base64.DEFAULT).also {
                            require(it.size == ScanVisualRecognition.DETAIL_LENGTH)
                        } else null
                        val englishName = if (detail != null) json.nextString() else ""
                        while (json.hasNext()) json.skipValue()
                        json.endArray()
                        rows.add(VisualIndexEntry(reference,name,imageUrl,landscape,signature,detail,englishName))
                    }
                    json.endArray()
                    entries = rows
                }
                else -> json.skipValue()
            }
        }
        json.endObject()
        require(json.peek() == JsonToken.END_DOCUMENT)
        require(format == "SWU_VISUAL_INDEX" && version in 1..2 && sampling == "area-v1")
        if (version == 2) require(detailWidth == ScanVisualRecognition.DETAIL_WIDTH &&
            detailHeight == ScanVisualRecognition.DETAIL_HEIGHT)
        val result = requireNotNull(entries)
        require(result.all { (it.detail != null) == (version == 2) })
        result
    }
}
