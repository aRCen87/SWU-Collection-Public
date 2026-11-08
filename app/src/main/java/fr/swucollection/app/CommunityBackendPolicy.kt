package fr.swucollection.app

/** Fail closed if a build accidentally receives the other channel's configuration. */
internal object CommunityBackendPolicy {
    const val OFFICIAL_PROJECT = "swu-collection-official"
    fun accepts(packageName: String, project: String, apiKey: String, appId: String): Boolean {
        if (apiKey.isBlank() || appId.isBlank()) return false
        return when (packageName) {
            AppChannel.OFFICIAL_PACKAGE -> project == OFFICIAL_PROJECT
            AppChannel.TEST_PACKAGE -> project.endsWith("-test") && project != OFFICIAL_PROJECT
            else -> false
        }
    }
}
