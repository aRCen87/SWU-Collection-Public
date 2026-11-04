package fr.swucollection.app

import kotlin.math.sqrt

internal data class VisualCardMatch(
    val reference: ScanReference, val name: String, val imageUrl: String, val score: Double
)
internal data class VisualIndexEntry(
    val reference: ScanReference, val name: String, val imageUrl: String,
    val landscape: Boolean, val signature: ByteArray
)

/** Compact, brightness-normalized full-card signatures. Results are suggestions only. */
internal object ScanVisualRecognition {
    const val WIDTH = 8
    const val HEIGHT = 12
    const val LENGTH = WIDTH * HEIGHT * 3

    fun signature(pixels: IntArray): ByteArray {
        require(pixels.size == WIDTH * HEIGHT)
        val gray = pixels.map { p -> .299 * (p shr 16 and 255) + .587 * (p shr 8 and 255) + .114 * (p and 255) }
        val mean = gray.average()
        val deviation = sqrt(gray.sumOf { (it - mean) * (it - mean) } / gray.size).coerceAtLeast(18.0)
        return ByteArray(LENGTH).also { out -> pixels.forEachIndexed { i, p ->
            val r = p shr 16 and 255; val g = p shr 8 and 255; val b = p and 255
            val total = (r + g + b).coerceAtLeast(1)
            out[i * 3] = kotlin.math.round(128 + (gray[i] - mean) * 48 / deviation).toInt().coerceIn(0,255).toByte()
            out[i * 3 + 1] = kotlin.math.round(255.0 * r / total).toInt().toByte()
            out[i * 3 + 2] = kotlin.math.round(255.0 * b / total).toInt().toByte()
        } }
    }

    fun distance(a: ByteArray, b: ByteArray): Double {
        require(a.size == LENGTH && b.size == LENGTH)
        var total = 0.0
        for (i in a.indices) {
            val delta = kotlin.math.abs((a[i].toInt() and 255) - (b[i].toInt() and 255)) / 255.0
            total += delta * if (i % 3 == 0) .65 else .175
        }
        return total / (WIDTH * HEIGHT)
    }

    fun candidates(signatures: List<ByteArray>, entries: List<VisualIndexEntry>, landscape: Boolean): List<VisualCardMatch> {
        if (signatures.isEmpty()) return emptyList()
        val matches = entries.asSequence().filter { it.landscape == landscape }.map { entry ->
            VisualCardMatch(entry.reference, entry.name, entry.imageUrl, signatures.minOf { distance(it, entry.signature) })
        }.groupBy { it.reference }.values.map { group -> group.minBy { it.score } }.sortedBy { it.score }
        val best = matches.firstOrNull() ?: return emptyList()
        // An unfocused frame or an unrelated object must not propose arbitrary cards.
        if (best.score > .085) return emptyList()
        return matches.takeWhile { it.score <= best.score + .018 && it.score <= .10 }.take(6)
    }
}
