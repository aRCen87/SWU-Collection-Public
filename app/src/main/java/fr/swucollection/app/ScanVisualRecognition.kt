package fr.swucollection.app

import kotlin.math.sqrt

internal data class VisualCardMatch(
    val reference: ScanReference, val name: String, val imageUrl: String, val score: Double, val landscape: Boolean = false,
    val englishName: String = ""
)
internal data class VisualIndexEntry(
    val reference: ScanReference, val name: String, val imageUrl: String,
    val landscape: Boolean, val signature: ByteArray, val detail: ByteArray? = null, val englishName: String = ""
)

/** Skipped image analyses are neutral; conflicting or absent evidence breaks a read. */
internal class StableVisualScanEvidence(private val maxGapMs: Long = 6000) {
    private var candidate: Pair<ScanReference, String>? = null
    private var lastSeen = 0L

    fun interrupt() { candidate = null; lastSeen = 0L }

    fun observe(evidence: Pair<ScanReference, String>?, now: Long): Pair<ScanReference, String>? {
        if (evidence == null) { interrupt(); return null }
        val stable = evidence == candidate && now - lastSeen in 0..maxGapMs
        candidate = evidence; lastSeen = now
        return evidence.takeIf { stable }
    }
}

/** Compact, brightness-normalized full-card signatures. Results are suggestions only. */
internal object ScanVisualRecognition {
    const val WIDTH = 8
    const val HEIGHT = 12
    const val LENGTH = WIDTH * HEIGHT * 3
    const val DETAIL_WIDTH = 16
    const val DETAIL_HEIGHT = 24
    const val DETAIL_LENGTH = DETAIL_WIDTH * DETAIL_HEIGHT * 3

    /** Area averages avoid aliasing fine card text when shrinking camera frames. */
    fun downsample(pixels: IntArray, width: Int, height: Int, columns: Int = WIDTH, rows: Int = HEIGHT): IntArray {
        require(columns > 0 && rows > 0 && width >= columns && height >= rows && pixels.size == width * height)
        return IntArray(columns * rows) { cell ->
            val x = cell % columns; val y = cell / columns
            val left = kotlin.math.round(x * width.toDouble() / columns).toInt()
            val right = kotlin.math.round((x+1) * width.toDouble() / columns).toInt()
            val top = kotlin.math.round(y * height.toDouble() / rows).toInt()
            val bottom = kotlin.math.round((y+1) * height.toDouble() / rows).toInt()
            var r=0L;var g=0L;var b=0L
            for (row in top until bottom) for (column in left until right) {
                val pixel=pixels[row*width+column]
                r+=pixel shr 16 and 255;g+=pixel shr 8 and 255;b+=pixel and 255
            }
            val count=(right-left)*(bottom-top)
            (255 shl 24) or (kotlin.math.round(r.toDouble()/count).toInt() shl 16) or
                (kotlin.math.round(g.toDouble()/count).toInt() shl 8) or kotlin.math.round(b.toDouble()/count).toInt()
        }
    }

    fun signature(pixels: IntArray): ByteArray {
        require(pixels.size == WIDTH * HEIGHT || pixels.size == DETAIL_WIDTH * DETAIL_HEIGHT)
        val gray = pixels.map { p -> .299 * (p shr 16 and 255) + .587 * (p shr 8 and 255) + .114 * (p and 255) }
        val mean = gray.average()
        val deviation = sqrt(gray.sumOf { (it - mean) * (it - mean) } / gray.size).coerceAtLeast(18.0)
        return ByteArray(pixels.size * 3).also { out -> pixels.forEachIndexed { i, p ->
            val r = p shr 16 and 255; val g = p shr 8 and 255; val b = p and 255
            val total = (r + g + b).coerceAtLeast(1)
            out[i * 3] = kotlin.math.round(128 + (gray[i] - mean) * 48 / deviation).toInt().coerceIn(0,255).toByte()
            out[i * 3 + 1] = kotlin.math.round(255.0 * r / total).toInt().toByte()
            out[i * 3 + 2] = kotlin.math.round(255.0 * b / total).toInt().toByte()
        } }
    }

    fun distance(a: ByteArray, b: ByteArray): Double {
        require(a.size == b.size && (a.size == LENGTH || a.size == DETAIL_LENGTH))
        var total = 0.0
        for (i in a.indices) {
            val delta = kotlin.math.abs((a[i].toInt() and 255) - (b[i].toInt() and 255)) / 255.0
            total += delta * if (i % 3 == 0) .65 else .175
        }
        return total / (a.size / 3)
    }

    /** Local luminance differences preserve edges lost in the coarse colour grid. */
    fun detailDistance(a: ByteArray, b: ByteArray): Double {
        require(a.size == DETAIL_LENGTH && b.size == DETAIL_LENGTH)
        var edges = 0.0; var count = 0
        fun luminance(bytes: ByteArray, index: Int) = bytes[index * 3].toInt() and 255
        for (y in 0 until DETAIL_HEIGHT) for (x in 0 until DETAIL_WIDTH) {
            val i = y * DETAIL_WIDTH + x
            for (axis in 0..1) {
                if ((axis == 0 && x+1 == DETAIL_WIDTH) || (axis == 1 && y+1 == DETAIL_HEIGHT)) continue
                val j = if (axis == 0) i+1 else i+DETAIL_WIDTH
                edges += kotlin.math.abs((luminance(a,i)-luminance(a,j)) -
                    (luminance(b,i)-luminance(b,j))) / 510.0
                count++
            }
        }
        return distance(a,b) * .8 + edges / count * .2
    }

    fun titleMatches(title: String, match: VisualCardMatch): Boolean {
        fun normalize(value: String) = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").lowercase(java.util.Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), " ").trim()
        val lines = title.lines().map(::normalize).filter { it.length >= 5 }
        return listOf(match.name, match.englishName).filter { it.isNotBlank() }.any { name ->
            val primary = normalize(name.split('—', '–').first())
            primary.length >= 5 && lines.any { line -> line == primary || line.startsWith("$primary ") }
        }
    }

    fun partialFooter(raw: String): Pair<Int,String>? {
        val normalized = java.text.Normalizer.normalize(raw,java.text.Normalizer.Form.NFKC)
        val footers = Regex("\\b(FR|EN|DE|ES|IT)[\\s•·:|—–\\-●○◊◆◇★☆#©®]*(?:[CURSL][\\s•·:|—–\\-●○◊◆◇★☆#©®]*)?([0-9OIL]{3,4})(?![0-9A-Z])")
            .findAll(normalized.uppercase(java.util.Locale.ROOT)).map { match ->
                match.groupValues[2].replace('O','0').replace('I','1').replace('L','1').toInt() to match.groupValues[1]
            }.toSet()
        return footers.singleOrNull()?.takeIf { it.first in 1..9999 }
    }

    /** A visual resemblance alone never identifies an exact printing or language. */
    fun corroborated(raw: String, matches: List<VisualCardMatch>, title: String = ""): Pair<ScanReference, String>? {
        if (ScanRecognition.references(raw).isNotEmpty()) return null
        val footer = partialFooter(raw) ?: return null
        val candidates = matches.filter { it.reference.number.toIntOrNull() == footer.first && it.score <= .06 }
        val named = matches.filter { titleMatches(title,it) }
        // A recognized title belonging to a different candidate contradicts the footer.
        if (named.isNotEmpty() && candidates.none { it in named }) return null
        val match = candidates.singleOrNull() ?: candidates.filter { it in named }.singleOrNull() ?: return null
        return match.reference to footer.second
    }

    fun candidates(signatures: List<ByteArray>, entries: List<VisualIndexEntry>, landscape: Boolean,
        details: List<ByteArray> = emptyList(), numberHint: Int? = null): List<VisualCardMatch> {
        if (signatures.isEmpty()) return emptyList()
        require(details.isEmpty() || details.size == signatures.size)
        // Keep both faces through refinement: the back may be a better detailed match.
        val coarse = entries.asSequence().filter { it.landscape == landscape &&
            (numberHint == null || it.reference.number.toIntOrNull() == numberHint) }.map { entry ->
            entry to signatures.minOf { distance(it, entry.signature) }
        }.sortedBy { it.second }.take(48).toList()
        if (coarse.isEmpty() || coarse.first().second > .085) return emptyList()
        val matches = coarse.map { (entry, coarseScore) ->
            val detail = entry.detail
            val score = if (detail == null || details.isEmpty()) coarseScore else signatures.indices.minOf { i ->
                .35 * distance(signatures[i],entry.signature) + .65 * detailDistance(details[i],detail)
            }
            VisualCardMatch(entry.reference, entry.name, entry.imageUrl, score, entry.landscape, entry.englishName)
        }.groupBy { it.reference }.values.map { group -> group.minBy { it.score } }.sortedBy { it.score }
        val best = matches.firstOrNull() ?: return emptyList()
        // An unfocused frame or an unrelated object must not propose arbitrary cards.
        if (best.score > .085) return emptyList()
        return matches.takeWhile { it.score <= best.score + .018 && it.score <= .10 }.take(6)
    }
}
