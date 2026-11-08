package fr.swucollection.app

/** Only the stable package may contact or install from the public release feed. */
internal object AppChannel {
    const val OFFICIAL_PACKAGE = "fr.swucollection.app"
    const val TEST_PACKAGE = "fr.swucollection.app.test"
    fun supportsExperience(packageName: String): Boolean = packageName == OFFICIAL_PACKAGE || packageName == TEST_PACKAGE
    fun supportsUpdateChecks(packageName: String): Boolean = allowsOfficialUpdates(packageName) || packageName == TEST_PACKAGE
    fun allowsOfficialUpdates(packageName: String): Boolean = packageName == OFFICIAL_PACKAGE
}
