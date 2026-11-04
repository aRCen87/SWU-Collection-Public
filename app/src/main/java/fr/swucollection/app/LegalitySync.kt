package fr.swucollection.app

import android.content.Context
import android.util.AtomicFile
import androidx.work.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.concurrent.TimeUnit

internal data class LegalitySnapshot(
    val rules: LegalityRules,
    val message: String = "Dernières règles enregistrées",
    val syncing: Boolean = false
)

internal object LegalitySync {
    private const val FEED = "https://raw.githubusercontent.com/aRCen87/SWU-Collection-Releases/main/legality.json"
    private const val SIX_HOURS = 6 * 60 * 60 * 1000L
    private val mutable = MutableStateFlow(LegalitySnapshot(LegalityRules.DEFAULT))
    val state = mutable.asStateFlow()
    private var initialized = false
    private fun file(context: Context) = AtomicFile(File(context.filesDir, "legality.json"))

    @Synchronized fun initialize(context: Context) {
        if (initialized) return
        val cached = runCatching { file(context).openRead().bufferedReader().use { LegalityRules.parse(it.readText()) } }.getOrNull()
        val bundled = runCatching { context.assets.open("legality.json").bufferedReader().use { LegalityRules.parse(it.readText()) } }.getOrNull()
        val latest = listOfNotNull(cached, bundled, LegalityRules.DEFAULT).maxBy { Instant.parse(it.checkedAt) }
        mutable.value = LegalitySnapshot(latest)
        initialized = true
    }

    @Synchronized internal fun acceptDownloaded(context: Context, text: String): LegalityRules {
        initialize(context)
        val rules = LegalityRules.parse(text)
        check(!Instant.parse(rules.checkedAt).isBefore(Instant.parse(mutable.value.rules.checkedAt)))
        val atomic = file(context)
        val output = atomic.startWrite()
        try { output.write(text.toByteArray(Charsets.UTF_8)); atomic.finishWrite(output) }
        catch (error: Exception) { atomic.failWrite(output); throw error }
        mutable.value = LegalitySnapshot(rules)
        return rules
    }

    /** Atomic validated cache; HTTP, parsing or write failures never discard the last good rules. */
    @Synchronized fun refresh(context: Context, force: Boolean = false): Boolean {
        initialize(context)
        val prefs = context.getSharedPreferences("legality_sync", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val lastSuccess = prefs.getLong("last_success", 0)
        val lastAttempt = prefs.getLong("last_attempt", 0)
        if (!force && now >= lastSuccess && now - lastSuccess < SIX_HOURS) return true
        if (!force && now >= lastAttempt && now - lastAttempt < 15 * 60 * 1000L) return false
        prefs.edit().putLong("last_attempt", now).apply()
        mutable.value = mutable.value.copy(syncing = true)
        return try {
            val connection = (URL(FEED).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 20000
                setRequestProperty("User-Agent", "SWU-Collection-Android")
                setRequestProperty("Accept", "application/json")
                useCaches = false
            }
            val text = try {
                check(connection.responseCode == 200)
                connection.inputStream.use { input ->
                    val bytes = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        check(bytes.size() + count <= 256 * 1024)
                        bytes.write(buffer, 0, count)
                    }
                    bytes.toString("UTF-8")
                }
            } finally { connection.disconnect() }
            val rules = acceptDownloaded(context, text)
            prefs.edit().putLong("last_success", now).apply()
            val stale = Instant.parse(rules.checkedAt).isBefore(Instant.now().minusSeconds(2 * 86400))
            mutable.value = LegalitySnapshot(rules, if (stale)
                "Le contrôle officiel date de plus de 48 h. Dernières règles conservées."
                else "Statuts actualisés automatiquement")
            true
        } catch (_: Exception) {
            mutable.value = mutable.value.copy(syncing = false,
                message = "Actualisation indisponible. Dernières règles conservées ; réessaie plus tard.")
            false
        }
    }
}

class LegalitySyncWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result = if (LegalitySync.refresh(applicationContext)) Result.success() else Result.retry()
    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<LegalitySyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "swu-legality-sync", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
