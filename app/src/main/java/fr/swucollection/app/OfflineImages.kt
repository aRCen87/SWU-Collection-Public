package fr.swucollection.app

import android.content.Context
import android.graphics.Bitmap
import android.util.AtomicFile
import androidx.core.graphics.drawable.toBitmap
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest

/** Explicit image downloads survive cache cleanup and updates, but do not bloat collection backups. */
internal object OfflineImages {
    private fun directory(context: Context) = File(context.noBackupFilesDir, "offline-card-images")
    private fun file(context: Context, url: String): File {
        val key = MessageDigest.getInstance("SHA-256").digest(url.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        return File(directory(context), "$key.jpg")
    }
    fun urls(cards: List<CatalogCard>): List<String> = cards.flatMap { listOf(it.imageUrl, it.backImageUrl) }.filter { it.startsWith("https://") }.distinct()
    fun contains(context: Context, url: String) = file(context, url).let { it.isFile && it.length() > 0 }
    fun source(context: Context, url: String): Any = if (contains(context, url)) file(context, url) else url
    fun bytes(context: Context): Long = directory(context).listFiles()?.sumOf { it.length() } ?: 0L
    fun clear(context: Context) {
        directory(context).listFiles()?.forEach { check(it.delete()) { "Certaines images n’ont pas pu être supprimées." } }
    }
    internal fun saveBytes(context: Context, url: String, bytes: ByteArray) {
        require(url.startsWith("https://") && bytes.isNotEmpty())
        val root = directory(context); check(root.exists() || root.mkdirs())
        val atomic = AtomicFile(file(context, url)); val stream = atomic.startWrite()
        try { stream.write(bytes); atomic.finishWrite(stream) } catch (error: Throwable) { atomic.failWrite(stream); throw error }
    }
    suspend fun download(context: Context, url: String) = withContext(Dispatchers.IO) {
        if (!contains(context, url)) {
            val request = ImageRequest.Builder(context).data(url).allowHardware(false).size(900).build()
            val result = Coil.imageLoader(context).execute(request)
            check(result is SuccessResult) { "Visuel indisponible" }
            val bytes = ByteArrayOutputStream().use { output ->
                check(result.drawable.toBitmap().compress(Bitmap.CompressFormat.JPEG, 95, output))
                output.toByteArray()
            }
            saveBytes(context, url, bytes)
        }
    }
}
