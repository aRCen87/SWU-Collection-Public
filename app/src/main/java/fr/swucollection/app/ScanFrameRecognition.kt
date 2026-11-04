package fr.swucollection.app

import kotlin.math.abs
import kotlin.math.min

/** OCR blocks are ordered independently; rebuild short rows before reading the footer. */
internal data class ScanOcrWord(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val height get() = bottom - top
    val centerY get() = (top + bottom) / 2.0
}

internal object ScanFrameRecognition {
    fun text(raw: String, words: List<ScanOcrWord>): String {
        val rows = mutableListOf<MutableList<ScanOcrWord>>()
        words.filter { it.height > 0 && it.right > it.left && it.text.isNotBlank() }
            .sortedWith(compareBy<ScanOcrWord> { it.top }.thenBy { it.left }).forEach { word ->
                val row = rows.firstOrNull { existing ->
                    val anchor = existing.first()
                    abs(anchor.centerY - word.centerY) <= min(anchor.height, word.height) * 0.5
                }
                if (row == null) rows.add(mutableListOf(word)) else row.add(word)
            }
        val stitched = rows.flatMap { row ->
            val parts = mutableListOf<String>()
            var previous: ScanOcrWord? = null
            val segment = mutableListOf<String>()
            row.sortedBy { it.left }.forEach { word ->
                val last = previous
                // Never bridge a distant card or the whole camera frame.
                if (last != null && word.left - last.right > min(last.height, word.height) * 12) {
                    parts.add(segment.joinToString(" ")); segment.clear()
                }
                segment.add(word.text); previous = word
            }
            if (segment.isNotEmpty()) parts.add(segment.joinToString(" "))
            parts
        }
        // Keep the original text as evidence too: conflicting card references must
        // still be rejected, rather than picking the first reconstructed footer.
        // Append complete references only. Appending bare fragments would put
        // unrelated rows next to each other again (e.g. "SOR FR" above a stat).
        return (listOf(raw) + stitched.filter { ScanRecognition.references(it).isNotEmpty() }).joinToString("\n")
    }
}

/** Search all card orientations, retaining one orientation for consecutive stable reads. */
internal class ScanOrientation {
    var degrees: Int = 0
        private set
    private var preferredDegrees = 0
    val checksAbsence get() = degrees == preferredDegrees
    fun observed(reference: ScanReference?) {
        if (reference == null) degrees = (degrees + 90) % 360
        else preferredDegrees = degrees
    }
    fun reset() { degrees = 0; preferredDegrees = 0 }
}
