package fr.swucollection.app

import android.content.Intent
import android.net.Uri

/** Open a normal browser page; never call the private file API from the app. */
internal object PrivateUpdateBrowser {
    fun intent(pageUrl: String, browserPackage: String? = null): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(PreviewUpdateFeed.downloadPageUrl(pageUrl))).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            // Use the installed browser when available instead of a Custom Tab.
            // If none exposes Custom Tabs, Android can choose an HTTPS handler.
            if (browserPackage != null) setPackage(browserPackage)
        }
}
