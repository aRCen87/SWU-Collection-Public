package fr.swucollection.app

/** Frames without a regional attempt are neutral; two matching regional reads are required. */
internal class ScanRegionEvidence {
    private val stable = StableVisualScanEvidence()
    private var reference: ScanReference? = null
    private var lastAttempt = 0L

    fun interrupt() { stable.interrupt(); reference = null; lastAttempt = 0L }
    fun confirmed() { stable.interrupt() }
    fun visible(now: Long) = reference != null && now-lastAttempt in 0..6000

    fun observe(reading: ScanOcrReading, now: Long): Pair<ScanReference,String>? {
        if (ScanRecognition.extract(reading.full) != null) interrupt()
        if (!reading.regionsAttempted) return null
        reference = reading.reference
        lastAttempt = now
        return stable.observe(reference?.let { it to (ScanRecognition.language(reading.raw) ?: CardLanguages.UNKNOWN) },now)
    }
}
