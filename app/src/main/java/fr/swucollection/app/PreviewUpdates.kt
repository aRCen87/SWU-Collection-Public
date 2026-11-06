package fr.swucollection.app

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.pm.PackageInfoCompat
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal data class PreviewRelease(val code: Long, val version: String, val notes: String,
    val url: String, val sha256: String, val size: Long)

/** Public announcement metadata only; APK access stays with the owner's private Library. */
internal object PreviewUpdateFeed {
    fun directDownloadUrl(pageUrl: String): String {
        require(isPrivateDownload(pageUrl)) { "Lien de téléchargement privé invalide." }
        val file = pageUrl.substringAfter("file_id=").substringBefore('&')
        return "https://chatgpt.com/api/library/files/$file/download"
    }
    private const val ENDPOINT = "https://raw.githubusercontent.com/aRCen87/SWU-Collection-Public/preview/private-sandbox/docs/preview-update.json"
    private const val MAX_FEED_BYTES = 64 * 1024
    fun isPrivateDownload(url: String) = Regex("https://chatgpt\\.com/library\\?file_id=libfile_[A-Za-z0-9_-]+&account_id=personal").matches(url)
    fun parse(raw: String, installed: Long): PreviewRelease? {
        require(raw.length <= MAX_FEED_BYTES)
        val data = JSONObject(raw)
        require(data.getString("channel") == "private-test" && data.getString("applicationId") == AppChannel.TEST_PACKAGE)
        val code = data.getLong("versionCode")
        require(code in 1..Int.MAX_VALUE.toLong())
        val version = data.getString("versionName")
        require(Regex("[0-9]+\\.[0-9]+\\.[0-9]+-test\\.[0-9]+").matches(version))
        val url = data.getString("privateDownloadUrl")
        require(isPrivateDownload(url)) { "Lien de téléchargement privé invalide." }
        val sha = data.getString("sha256")
        require(Regex("[a-f0-9]{64}").matches(sha))
        val size = data.getLong("size")
        require(size in 1..UpdateFeed.MAX_APK_BYTES)
        val notes = data.optString("notes")
        require(notes.length <= 6000)
        return if (code <= installed) null else PreviewRelease(code, version, notes, url, sha, size)
    }
    fun encode(release: PreviewRelease): String = JSONObject().put("channel", "private-test")
        .put("applicationId", AppChannel.TEST_PACKAGE).put("versionCode", release.code)
        .put("versionName", release.version).put("notes", release.notes).put("privateDownloadUrl", release.url)
        .put("sha256", release.sha256).put("size", release.size).toString()
    fun fetchLatest(installed: Long): PreviewRelease? {
        val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000; readTimeout = 30000; instanceFollowRedirects = false
            setRequestProperty("User-Agent", "SWU-Collection-Test/$installed")
            setRequestProperty("Cache-Control", "no-cache")
        }
        return try {
            when (connection.responseCode) {
                404 -> null
                200 -> {
                    val bytes = connection.inputStream.use { input ->
                        val result = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(4096)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            require(result.size() + count <= MAX_FEED_BYTES)
                            result.write(buffer, 0, count)
                        }
                        result.toByteArray()
                    }
                    require(bytes.size <= MAX_FEED_BYTES)
                    parse(bytes.toString(Charsets.UTF_8), installed)
                }
                else -> error("Vérification privée indisponible (${connection.responseCode}).")
            }
        } finally { connection.disconnect() }
    }
}

internal object PreviewUpdatePolicy {
    fun shouldNotify(installed: Long, available: Long, lastNotified: Long) = available > installed && available > lastNotified
}

internal object PreviewUpdateNotifications {
    private const val ID = 4702
    private const val DOWNLOAD_TOKEN = "private_download_token"
    private const val TOKEN_EXTRA = "private_update_download_token"
    @Synchronized
    fun consumeDownloadRequest(context: Context, intent: Intent): Boolean {
        if (context.packageName != AppChannel.TEST_PACKAGE || intent.action != UpdateNotifications.ACTION_DOWNLOAD_TEST_UPDATE) return false
        val expected = prefs(context).getString(DOWNLOAD_TOKEN, null) ?: return false
        if (intent.getStringExtra(TOKEN_EXTRA) != expected) return false
        prefs(context).edit().remove(DOWNLOAD_TOKEN).commit()
        return true
    }
    private fun prefs(context: Context) = context.getSharedPreferences("private_preview_updates", Context.MODE_PRIVATE)
    @Suppress("DEPRECATION")
    fun installedCode(context: Context): Long = PackageInfoCompat.getLongVersionCode(
        context.packageManager.getPackageInfo(context.packageName, 0))
    fun cached(context: Context, installed: Long): PreviewRelease? = runCatching {
        prefs(context).getString("available_release", null)?.let { PreviewUpdateFeed.parse(it, installed) }
    }.getOrNull()
    fun check(context: Context): PreviewRelease? {
        require(context.packageName == AppChannel.TEST_PACKAGE)
        val installed = installedCode(context)
        if (prefs(context).getLong("last_notified_code", 0) <= installed)
            NotificationManagerCompat.from(context).cancel(ID)
        val release = PreviewUpdateFeed.fetchLatest(installed)
        prefs(context).edit().apply {
            if (release == null) remove("available_release") else putString("available_release", PreviewUpdateFeed.encode(release))
        }.apply()
        release?.let { offer(context, installed, it) }
        return release
    }
    @Synchronized
    @SuppressLint("MissingPermission")
    fun offer(context: Context, installed: Long, release: PreviewRelease): Boolean {
        if (context.packageName != AppChannel.TEST_PACKAGE || !PreviewUpdateFeed.isPrivateDownload(release.url)) return false
        UpdateNotifications.createChannel(context)
        if (!UpdateNotifications.allowed(context) || !PreviewUpdatePolicy.shouldNotify(installed, release.code,
                prefs(context).getLong("last_notified_code", 0))) return false
        val token = java.util.UUID.randomUUID().toString()
        val intent = Intent(context, MainActivity::class.java).apply {
            action = UpdateNotifications.ACTION_DOWNLOAD_TEST_UPDATE
            putExtra(TOKEN_EXTRA, token)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pending = PendingIntent.getActivity(context, ID, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, UpdateNotifications.CHANNEL)
            .setSmallIcon(R.drawable.ic_update_notification)
            .setContentTitle("SWU Collection Test : mise à jour")
            .setContentText("La version ${release.version} est disponible. Touchez pour lancer le téléchargement privé.")
            .setContentIntent(pending).setAutoCancel(true).build()
        return try {
            NotificationManagerCompat.from(context).notify(ID, notification)
            prefs(context).edit().putLong("last_notified_code", release.code).putString(DOWNLOAD_TOKEN, token).apply()
            true
        } catch (_: SecurityException) { false }
    }
}
