package fr.swucollection.app

import java.util.Locale
import java.text.Normalizer
import kotlin.math.abs

internal data class ScanReference(val set: String, val number: String)

internal object ScanRecognition {
    private val codes = listOf("HMWP", "JTLP", "LOFP", "SECP", "LAWP", "ASHP", "TS26", "MV26",
        "SOR", "SHD", "TWI", "JTL", "LOF", "SEC", "LAW", "ASH", "IBH", "HMW", "C24", "C25", "C26", "P25", "P26", "J24", "J25", "G25", "GG")
    private val codeAlternatives = codes.joinToString("|")
    private const val separator = "[\\s•·:|—–\\-●○◊◆◇★☆#©®]*"
    private const val digits = "[0-9OIL]"
    private val sets = Regex("\\b($codeAlternatives)(?=\\b|(?:FR|EN|DE|ES|IT)(?:\\b|[CURSL]?[0-9OIL])|[0-9])")
    private val mainSets = setOf("SOR","SHD","TWI","JTL","LOF","SEC","LAW","ASH","HMW")
    // Main-set collector denominators exceed 250. Smaller totals belong to serial stamps.
    private fun validDenominator(set: String, total: Int) = total >= 10 && (set !in mainSets || total > 250)
    private val fractions = Regex("\\b($digits{1,4})\\s*/\\s*($digits{2,4})\\b")
    // Collector-numbered variants omit the denominator. Their rarity symbol may be
    // read as C/U/R/S/L, a dot, or a copyright-like circle by OCR.
    private val printed = Regex("\\b($codeAlternatives)$separator(?:(?:FR|EN|DE|ES|IT)$separator)?" +
        "(?:[CURSL]$separator)?($digits{1,4})(?![0-9OILA-Z])")
    // Correct expansion OCR only in a language-labelled footer, never in a title
    // or in an isolated serial stamp. Collector numbers keep their own rules.
    private val expansionOcr = mapOf("S0R" to "SOR", "5OR" to "SOR", "5HD" to "SHD",
        "TW1" to "TWI", "JT1" to "JTL", "JTI" to "JTL", "L0F" to "LOF", "5EC" to "SEC", "A5H" to "ASH")
    private val expansionFooter = Regex("\\b(${expansionOcr.keys.joinToString("|")})(?=$separator(?:FR|EN|DE|ES|IT)$separator(?:[CURSL]$separator)?$digits)")

    private fun number(text: String) = text.replace('O', '0').replace('I', '1').replace('L', '1').toIntOrNull()

    fun manual(set: String, number: String): ScanReference? {
        val code = set.trim().uppercase(Locale.ROOT)
        val n = number.trim().toIntOrNull() ?: return null
        if (code !in codes || n !in 1..9999) return null
        return ScanReference(code, n.toString().padStart(3, '0'))
    }

    fun language(raw: String): String? {
        val normalized = Normalizer.normalize(raw, Normalizer.Form.NFKC).uppercase(Locale.ROOT)
        val text = expansionFooter.replace(normalized) { expansionOcr.getValue(it.value) }
        val matches = Regex("\\b($codeAlternatives)$separator(FR|EN|DE|ES|IT)(?=$separator(?:[CURSL]$separator)?$digits)")
            .findAll(text).map { it.groupValues[2] }.toSet()
        return matches.singleOrNull()?.takeIf { it in CardLanguages.supported }
    }

    fun extract(raw: String): ScanReference? = references(raw).singleOrNull()

    internal fun references(raw: String): Set<ScanReference> {
        val normalized = Normalizer.normalize(raw, Normalizer.Form.NFKC).uppercase(Locale.ROOT)
            .replace('／', '/').replace('⁄', '/').replace('∕', '/')
        val text = expansionFooter.replace(normalized) { expansionOcr.getValue(it.value) }
        val footers = printed.findAll(text).mapNotNull { match ->
            val n = number(match.groupValues[2]) ?: return@mapNotNull null
            val tail = text.substring(match.range.last + 1).trimStart()
            if (tail.startsWith('/')) {
                val denominator = Regex("^/\\s*($digits{2,4})(?![0-9OILA-Z])").find(tail)
                    ?: return@mapNotNull null
                if (!validDenominator(match.groupValues[1],number(denominator.groupValues[1]) ?: 0)) return@mapNotNull null
            }
            manual(match.groupValues[1], n.toString())
        }.toSet()
        // A separate serial stamp such as 002/250 must not replace the collector
        // number explicitly attached to the expansion/language footer.
        if (footers.isNotEmpty()) return footers
        if (Regex("\\b($codeAlternatives)$separator(?:FR|EN|DE|ES|IT)").containsMatchIn(text)) return emptySet()
        val setMatches = sets.findAll(text).toList()
        return fractions.findAll(text).mapNotNull { match ->
            val n = number(match.groupValues[1]) ?: return@mapNotNull null
            val total = number(match.groupValues[2]) ?: return@mapNotNull null
            if (n !in 1..9999 || total < 10) return@mapNotNull null
            val nearest = setMatches.minByOrNull { abs(it.range.first - match.range.first) } ?: return@mapNotNull null
            if (abs(nearest.range.first - match.range.first) > 64 || !validDenominator(nearest.value,total)) return@mapNotNull null
            manual(nearest.value, n.toString())
        }.toSet()
    }
}

/** Require consecutive consistent reads; a held card never triggers another confirmation. */
internal class StableScanGate(private val maxGapMs: Long = 2500, private val removalMs: Long = 900) {
    private var candidate: ScanReference? = null
    private var reads = 0
    private var candidateLanguage: String? = null
    private var lastSeen = 0L
    private var absentSince: Long? = null
    var blockedReference: ScanReference? = null
        private set

    /** Pausing is not proof of removal, and an old frame must not complete a new scan. */
    fun interrupt() { candidate = null; candidateLanguage = null; reads = 0; lastSeen = 0L; absentSince = null }

    fun block(reference: ScanReference) { interrupt(); blockedReference = reference }
    fun reset() { interrupt(); blockedReference = null }

    fun observe(reference: ScanReference?, now: Long, language: String? = null): ScanReference? {
        if (reference == null) {
            candidate = null; reads = 0
            val firstAbsent = absentSince ?: now.also { absentSince = it }
            if (now - firstAbsent >= removalMs) blockedReference = null
            return null
        }
        absentSince = null
        if (reference == blockedReference) return null
        if (reference != candidate || language != candidateLanguage || now - lastSeen > maxGapMs) { candidate = reference; candidateLanguage = language; reads = 1 }
        else reads++
        lastSeen = now
        if (reads < 2) return null
        blockedReference = reference
        candidate = null; reads = 0
        return reference
    }
}

