package fr.swucollection.app

import android.graphics.Bitmap
import android.graphics.Matrix
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.Executor
import kotlin.math.min
import kotlin.math.roundToInt

internal data class ScanOcrReading(val full: String, val title: String = "", val footer: String = "",
    val regionsAttempted: Boolean = false) {
    // Preserve all sources: a cropped read must never hide a contradictory full-frame reference.
    private val sources get() = listOf(full, title, footer).filter { it.isNotBlank() }
    val raw get() = sources.joinToString("\n[zone]\n")
    // Never assemble an expansion from one source with unrelated digits in another.
    val references get() = sources.flatMap { ScanRecognition.references(it) }.toSet()
    val reference get() = references.singleOrNull()
}

/** Native-resolution crops use the same FILL_CENTER guide as the camera preview. */
internal object ScanOcrRegions {
    /** Keep both crops alive until OCR has completed, including interruption and failure paths. */
    fun read(crops: Pair<Bitmap,Bitmap>, full: String, executor: Executor, active: () -> Boolean,
        recognize: (Bitmap) -> Task<String>): Task<ScanOcrReading> {
        val (title,footer) = crops
        return try {
            recognize(title).continueWithTask(executor) { titleTask ->
                val titleText = if (titleTask.isSuccessful) titleTask.result else ""
                if (active()) recognize(footer).continueWith(executor) { footerTask ->
                    ScanOcrReading(full,titleText,if (footerTask.isSuccessful) footerTask.result else "",true)
                } else Tasks.forResult(ScanOcrReading(full,regionsAttempted=true))
            }.continueWith(executor) { task ->
                if (task.isSuccessful) task.result else ScanOcrReading(full,regionsAttempted=true)
            }.addOnCompleteListener(executor) { title.recycle();footer.recycle() }
        } catch (_: Exception) {
            title.recycle();footer.recycle()
            Tasks.forResult(ScanOcrReading(full,regionsAttempted=true))
        }
    }

    fun crops(frame: Bitmap, rotation: Int, viewport: ScanViewport, landscape: Boolean,
        textRotation: Int): Pair<Bitmap, Bitmap>? {
        if (viewport.width <= 0 || viewport.height <= 0) return null
        val upright = if (rotation == 0) frame else Bitmap.createBitmap(frame, 0, 0, frame.width, frame.height,
            Matrix().apply { postRotate(rotation.toFloat()) }, true)
        try {
            val scale = maxOf(viewport.width.toDouble()/upright.width, viewport.height.toDouble()/upright.height)
            val ratio = if (landscape) 88.0/63 else 63.0/88
            val height = (min(viewport.height*.78, viewport.width*.9/ratio)/scale).roundToInt()
                .coerceAtMost(upright.height)
            val width = (height*ratio).roundToInt().coerceAtMost(upright.width)
            if (width < 32 || height < 32) return null
            val card = Bitmap.createBitmap(upright,(upright.width-width)/2,(upright.height-height)/2,width,height)
            val oriented = if (textRotation == 0) card else Bitmap.createBitmap(card,0,0,card.width,card.height,
                Matrix().apply { postRotate(textRotation.toFloat()) },true)
            try {
                val title = Bitmap.createBitmap(oriented,0,0,oriented.width,(oriented.height*.35).roundToInt().coerceAtLeast(1))
                try {
                    val top = (oriented.height*.73).roundToInt()
                    val footer = Bitmap.createBitmap(oriented,0,top,oriented.width,oriented.height-top)
                    return title to footer
                } catch (error: Exception) { title.recycle(); throw error }
            } finally {
                if (oriented !== card) oriented.recycle()
                if (card !== upright) card.recycle()
            }
        } finally { if (upright !== frame) upright.recycle() }
    }
}
