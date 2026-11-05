package fr.swucollection.app

internal data class ScanRegionPose(val landscape: Boolean, val rotation: Int)

/** Search independently of full-frame OCR cadence, which can otherwise skip a pose forever. */
internal class ScanRegionSearch {
    private var index = 0
    val pose get() = ScanRegionPose(index % 2 == 1, (index / 2) * 90)
    fun observed(reference: ScanReference?) { if (reference == null) index = (index+1) % 8 }
    fun reset() { index = 0 }
}
