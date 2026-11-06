package fr.swucollection.app

/** Only the stable package may contact or install from the public release feed. */
internal object AppChannel {
    const val OFFICIAL_PACKAGE = "fr.swucollection.app"
    fun allowsOfficialUpdates(packageName: String): Boolean = packageName == OFFICIAL_PACKAGE
}
