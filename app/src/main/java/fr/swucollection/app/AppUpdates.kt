package fr.swucollection.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

internal data class AppRelease(val version: String, val notes: String, val url: String, val sha256: String, val size: Long)

/** Only stable releases with a GitHub-provided SHA-256 and an APK from our public repo. */
internal object UpdateFeed {
    const val REPOSITORY = "aRCen87/SWU-Collection-Releases"
    const val ENDPOINT = "https://api.github.com/repos/$REPOSITORY/releases/latest"

    fun versionParts(version: String): List<Int>? =
        Regex("^v?(\\d+)\\.(\\d+)\\.(\\d+)$").matchEntire(version.trim())
            ?.groupValues?.drop(1)?.map { it.toIntOrNull() ?: return null }

    fun isNewer(candidate: String, installed: String): Boolean {
        val next = versionParts(candidate) ?: return false
        val current = versionParts(installed) ?: return false
        for (i in next.indices) if (next[i] != current[i]) return next[i] > current[i]
        return false
    }

    fun parse(text: String, installed: String): AppRelease? {
        val json = JSONObject(text)
        if (json.optBoolean("draft") || json.optBoolean("prerelease")) return null
        val tag = json.getString("tag_name")
        if (!isNewer(tag, installed)) return null
        val assets = json.getJSONArray("assets")
        val matches = (0 until assets.length()).map { assets.getJSONObject(it) }
            .filter { it.optString("name") == "SWU-Collection.apk" }
        require(matches.size == 1) { "Le fichier de mise à jour est absent ou ambigu." }
        val asset = matches.single()
        val url = asset.getString("browser_download_url")
        val parsed = URL(url)
        require(parsed.protocol == "https" && parsed.host == "github.com" && parsed.port == -1 &&
            parsed.userInfo == null && parsed.path.startsWith("/$REPOSITORY/releases/download/") &&
            parsed.path.endsWith("/SWU-Collection.apk")) { "Source de mise à jour non reconnue." }
        val hash = asset.optString("digest").removePrefix("sha256:")
        require(Regex("[a-fA-F0-9]{64}").matches(hash)) { "La vérification du fichier n'est pas encore disponible." }
        val size = asset.getLong("size")
        require(size in 1..MAX_APK_BYTES) { "Taille de mise à jour invalide." }
        return AppRelease(tag.removePrefix("v"), json.optString("body").take(6000), url, hash.lowercase(), size)
    }

    fun fetchLatest(installed: String): AppRelease? {
        val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 30000
            setRequestProperty("User-Agent", "SWU-Collection/$installed")
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        return try {
            when (connection.responseCode) {
                404 -> null
                200 -> {
                    val text = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                        val result = StringBuilder()
                        val buffer = CharArray(4096)
                        while (true) {
                            val count = reader.read(buffer)
                            if (count < 0) break
                            require(result.length + count <= 1024 * 1024) { "Réponse trop volumineuse." }
                            result.append(buffer, 0, count)
                        }
                        result.toString()
                    }
                    parse(text, installed)
                }
                else -> error("Serveur indisponible (${connection.responseCode}).")
            }
        } finally { connection.disconnect() }
    }

    const val MAX_APK_BYTES = 200L * 1024 * 1024
}

internal class AppUpdates(private val context: Context, private val database: CollectionDatabase) {
    @Suppress("DEPRECATION")
    private val installed = context.packageManager.getPackageInfo(context.packageName, 0)
    val installedVersion: String = installed.versionName ?: "0.0.0"
    var release by mutableStateOf<AppRelease?>(null)
        private set
    var previewRelease by mutableStateOf<PreviewRelease?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    private var readyFile: File? = null
    private var readyRelease: AppRelease? = null
    val readyToInstall get() = readyFile?.isFile == true && readyRelease == release

    suspend fun check() {
        if (context.packageName == AppChannel.TEST_PACKAGE) { checkPreview(); return }
        if (!AppChannel.allowsOfficialUpdates(context.packageName)) {
            release = null
            message = "Version de test privée. Les mises à jour sont fournies séparément après validation."
            return
        }
        if (busy) return
        busy = true
        message = "Recherche d’une mise à jour…"
        try {
            if (release == null) release = UpdateReleaseCache.load(context, installedVersion)
            val found = withContext(Dispatchers.IO) {
                UpdateNotifications.clearInstalled(context, installedVersion)
                val available = UpdateFeed.fetchLatest(installedVersion)
                UpdateReleaseCache.save(context, available)
                available?.let { UpdateNotifications.offer(context, installedVersion, it) }
                available
            }
            release = found
            message = if (found == null) "Aucune nouvelle version publiée." else "Version ${found.version} disponible."
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (error: Exception) {
            message = "Vérification impossible. Réessaie avec une connexion Internet."
        } finally { busy = false }
    }

    private suspend fun checkPreview() {
        if (busy) return
        busy = true
        message = "Recherche d’une mise à jour Test…"
        try {
            if (previewRelease == null) previewRelease = PreviewUpdateNotifications.cached(context, PreviewUpdateNotifications.installedCode(context))
            previewRelease = withContext(Dispatchers.IO) { PreviewUpdateNotifications.check(context) }
            message = previewRelease?.let { "Version Test ${it.version} disponible." } ?: "Ton application Test est à jour."
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) { message = "Vérification impossible. Réessaie avec une connexion Internet."
        } finally { busy = false }
    }

    suspend fun openPrivateDownload() {
        if (context.packageName != AppChannel.TEST_PACKAGE || busy) return
        val target = previewRelease ?: return
        if (!PreviewUpdateFeed.isPrivateDownload(target.url)) return
        busy = true
        try {
            withContext(Dispatchers.IO) { UpdateBackups.create(context, database) }
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            message = "Téléchargement privé ouvert. Connecte-toi à ton compte ChatGPT, puis installe le fichier par-dessus l’application Test."
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) { message = "Impossible d’ouvrir le téléchargement privé. Réessaie."
        } finally { busy = false }
    }

    suspend fun download() {
        if (!AppChannel.allowsOfficialUpdates(context.packageName)) return
        if (busy) return
        val target = release ?: return
        busy = true
        readyFile = null
        readyRelease = null
        message = "Téléchargement et vérification…"
        try {
            val apk = withContext(Dispatchers.IO) {
                val directory = File(context.cacheDir, "updates").apply { mkdirs() }
                val partial = File(directory, "update.part")
                val output = File(directory, "SWU-Collection.apk")
                output.delete()
                val connection = open(target.url)
                try {
                    require(connection.responseCode == 200 && connection.url.protocol == "https") { "Téléchargement impossible." }
                    val digest = MessageDigest.getInstance("SHA-256")
                    var total = 0L
                    connection.inputStream.use { input ->
                        partial.outputStream().use { stream ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                total += count
                                require(total <= target.size && total <= UpdateFeed.MAX_APK_BYTES) { "Fichier trop volumineux." }
                                digest.update(buffer, 0, count)
                                stream.write(buffer, 0, count)
                            }
                        }
                    }
                    require(total == target.size) { "Téléchargement incomplet." }
                    val hash = digest.digest().joinToString("") { "%02x".format(it) }
                    require(hash == target.sha256) { "Le fichier téléchargé est corrompu." }
                    verifyApk(partial, target)
                    check(partial.renameTo(output)) { "Impossible de préparer le fichier." }
                    output
                } finally { partial.delete(); connection.disconnect() }
            }
            readyFile = apk
            readyRelease = target
            message = "Mise à jour vérifiée. Prête à installer."
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (error: Exception) { message = error.message ?: "Téléchargement impossible. Réessaie."
        } finally { busy = false }
    }

    suspend fun install() {
        if (!AppChannel.allowsOfficialUpdates(context.packageName)) return
        if (busy || !readyToInstall) return
        val apk = readyFile ?: return
        busy = true
        try {
            // Keep the permanent database and signing identity; installation replaces the app in place.
            withContext(Dispatchers.IO) {
                verifyApk(apk, readyRelease ?: error("Version introuvable."))
                UpdateBackups.create(context, database)
            }
            if (!context.packageManager.canRequestPackageInstalls()) {
                message = "Autorise cette source, puis reviens ici et appuie sur Installer la mise à jour."
                context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")))
            } else {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
                context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                })
                message = "Confirme la mise à jour dans la fenêtre Android. Ta sauvegarde locale a été créée."
            }
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (error: Exception) { message = error.message ?: "Impossible de lancer l’installation."
        } finally { busy = false }
    }

    @Suppress("DEPRECATION")
    private fun verifyApk(file: File, release: AppRelease) {
        // Recheck the hash immediately before handing the file to Android, including after permission settings.
        val hash = file.inputStream().use { stream ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
        require(hash == release.sha256) { "Fichier modifié : télécharge à nouveau la mise à jour." }
        val pm = context.packageManager
        val apk = pm.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNATURES)
            ?: error("APK invalide.")
        require(apk.packageName == context.packageName && PackageInfoCompat.getLongVersionCode(apk) > PackageInfoCompat.getLongVersionCode(installed) &&
            apk.versionName == release.version) { "Cette version ne correspond pas à l’application." }
        val current = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
        val expected = current.signatures?.map { it.toCharsString() }?.toSet().orEmpty()
        val actual = apk.signatures?.map { it.toCharsString() }?.toSet().orEmpty()
        require(expected.isNotEmpty() && actual == expected) { "Signature différente : installation refusée pour protéger ta collection." }
    }

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 30_000
        setRequestProperty("User-Agent", "SWU-Collection/$installedVersion")
        setRequestProperty("Accept", if (url == UpdateFeed.ENDPOINT) "application/vnd.github+json" else "application/octet-stream")
    }
}
