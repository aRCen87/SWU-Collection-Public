package fr.swucollection.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Base64
import org.json.JSONObject
import java.util.zip.GZIPInputStream
import kotlin.math.min
import kotlin.math.roundToInt

internal data class ScanViewport(val width: Int, val height: Int, val landscape: Boolean)

/** Runs on the analysis executor; photographs and signatures never leave the phone. */
internal class ScanVisualMatcher(private val context: Context) {
    private val entries by lazy {
        runCatching {
            context.assets.open("scan-visual-index.json.gz").use { input ->
                val json = GZIPInputStream(input).bufferedReader().use { JSONObject(it.readText()) }
                require(json.getString("format") == "SWU_VISUAL_INDEX" && json.getInt("version") == 1)
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

    fun match(frame: Bitmap, rotation: Int, viewport: ScanViewport): List<VisualCardMatch> {
        if (viewport.width <= 0 || viewport.height <= 0 || entries.isEmpty()) return emptyList()
        val upright = if (rotation == 0) frame else Bitmap.createBitmap(frame,0,0,frame.width,frame.height,
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
                val small = Bitmap.createScaledBitmap(crop,ScanVisualRecognition.WIDTH,ScanVisualRecognition.HEIGHT,true)
                try {
                    val pixels=IntArray(ScanVisualRecognition.WIDTH*ScanVisualRecognition.HEIGHT)
                    small.getPixels(pixels,0,ScanVisualRecognition.WIDTH,0,0,ScanVisualRecognition.WIDTH,ScanVisualRecognition.HEIGHT)
                    signatures.add(ScanVisualRecognition.signature(pixels))
                    signatures.add(ScanVisualRecognition.signature(pixels.reversedArray()))
                } finally { if(small !== crop) small.recycle(); if(crop !== upright) crop.recycle() }
            }
            return ScanVisualRecognition.candidates(signatures,entries,viewport.landscape)
        } finally { if (upright !== frame) upright.recycle() }
    }
}
