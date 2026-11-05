package fr.swucollection.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Base64
import org.json.JSONObject
import java.util.zip.GZIPInputStream
import java.io.FileNotFoundException
import kotlin.math.min
import kotlin.math.roundToInt

internal data class ScanViewport(val width: Int, val height: Int, val landscape: Boolean)

/** Runs on the analysis executor; photographs and signatures never leave the phone. */
internal class ScanVisualMatcher(private val context: Context) {
    private val entries by lazy {
        runCatching {
            val input = try { context.assets.open("scan-visual-index.json") }
                catch (_: FileNotFoundException) { context.assets.open("scan-visual-index.json.gz") }
            input.buffered().use { buffered ->
                buffered.mark(2); val compressed = buffered.read() == 0x1f && buffered.read() == 0x8b; buffered.reset()
                val decoded = if (compressed) GZIPInputStream(buffered) else buffered
                val json = decoded.bufferedReader().use { JSONObject(it.readText()) }
                require(json.getString("format") == "SWU_VISUAL_INDEX" && json.getInt("version") == 1 && json.getString("sampling") == "area-v1")
                val rows = json.getJSONArray("entries")
                (0 until rows.length()).map { i ->
                    val row = rows.getJSONArray(i)
                    val signature = Base64.decode(row.getString(6), Base64.DEFAULT)
                    require(signature.size == ScanVisualRecognition.LENGTH)
                    VisualIndexEntry(ScanReference(row.getString(0), row.getString(1)), row.getString(2), row.getString(3), row.getBoolean(5), signature)
                }
            }
        }.getOrDefault(emptyList())
    }

    fun matchAutomatic(frame: Bitmap, rotation: Int, viewport: ScanViewport): List<VisualCardMatch> {
        val matches = listOf(false, true).flatMap { landscape -> match(frame, rotation, viewport.copy(landscape = landscape)) }
            .groupBy { it.reference }.values.map { it.minBy { match -> match.score } }.sortedBy { it.score }
        val best = matches.firstOrNull() ?: return emptyList()
        return matches.takeWhile { it.score <= best.score + .018 }.take(6)
    }

    fun match(frame: Bitmap, rotation: Int, viewport: ScanViewport): List<VisualCardMatch> {
        if (viewport.width <= 0 || viewport.height <= 0 || entries.isEmpty()) return emptyList()
        // OCR keeps the full camera resolution. Image signatures need only a
        // small working copy, avoiding repeated multi-megapixel crops/arrays.
        val reduction = min(1.0,960.0 / maxOf(frame.width,frame.height))
        val working = if (reduction == 1.0) frame else Bitmap.createScaledBitmap(frame,
            (frame.width*reduction).roundToInt(),(frame.height*reduction).roundToInt(),true)
        val upright = if (rotation == 0) working else Bitmap.createBitmap(working,0,0,working.width,working.height,
            Matrix().apply { postRotate(rotation.toFloat()) },true)
        try {
            // Same FILL_CENTER transform and guide dimensions as PreviewView/ScannerGuide.
            val scale = maxOf(viewport.width.toDouble() / upright.width, viewport.height.toDouble() / upright.height)
            val ratio = if (viewport.landscape) 88.0 / 63 else 63.0 / 88
            val guideHeight = min(viewport.height * .78, viewport.width * .9 / ratio) / scale
            val guideWidth = guideHeight * ratio
            val signatures = mutableListOf<ByteArray>()
            // Small positioning errors are expected while hand-holding a card.
            for (size in listOf(.94,1.0,1.06)) {
                val w = guideWidth * size * .88; val h = guideHeight * size * .88
                val left = ((upright.width-w)/2).roundToInt(); val top = ((upright.height-h)/2).roundToInt()
                val width = w.roundToInt(); val height = h.roundToInt()
                if (width < 8 || height < 12 || left < 0 || top < 0 || left+width > upright.width || top+height > upright.height) continue
                val crop = Bitmap.createBitmap(upright,left,top,width,height)
                try {
                    val pixels=IntArray(width*height)
                    crop.getPixels(pixels,0,width,0,0,width,height)
                    val sampled=ScanVisualRecognition.downsample(pixels,width,height)
                    signatures.add(ScanVisualRecognition.signature(sampled))
                    signatures.add(ScanVisualRecognition.signature(sampled.reversedArray()))
                } finally { if(crop !== upright) crop.recycle() }
            }
            return ScanVisualRecognition.candidates(signatures,entries,viewport.landscape)
        } finally {
            if (upright !== working) upright.recycle()
            if (working !== frame) working.recycle()
        }
    }
}
