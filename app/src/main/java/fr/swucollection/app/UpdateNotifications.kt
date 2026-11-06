package fr.swucollection.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

internal object UpdateNotificationPolicy {
    fun shouldNotify(installed: String, available: String, lastNotified: String?) =
        UpdateFeed.isNewer(available, installed) &&
            (lastNotified == null || UpdateFeed.isNewer(available, lastNotified))
}

internal object UpdateReleaseCache {
    private fun prefs(context: Context) = context.getSharedPreferences("application_updates", Context.MODE_PRIVATE)
    fun save(context: Context, release: AppRelease?) {
        val editor = prefs(context).edit()
        if (release == null) editor.remove("available_release") else {
            val json = JSONObject().put("tag_name", "v${release.version}").put("body", release.notes)
                .put("assets", JSONArray().put(JSONObject().put("name", "SWU-Collection.apk")
                    .put("browser_download_url", release.url).put("size", release.size)
                    .put("digest", "sha256:${release.sha256}")))
            editor.putString("available_release", json.toString())
        }
        editor.apply()
    }
    fun load(context: Context, installed: String): AppRelease? = runCatching {
        prefs(context).getString("available_release", null)?.let { UpdateFeed.parse(it, installed) }
    }.getOrNull()
}

internal object UpdateNotifications {
    const val ACTION_OPEN_UPDATES = "fr.swucollection.app.OPEN_UPDATES"
    internal const val CHANNEL = "application_updates"
    private const val NOTIFICATION_ID = 4701
    private fun prefs(context: Context) = context.getSharedPreferences("application_updates", Context.MODE_PRIVATE)

    fun createChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Mises à jour de l’application", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Signale une nouvelle version finalisée de SWU Collection."
            })
    }

    fun allowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    @Synchronized
    @SuppressLint("MissingPermission")
    fun offer(context: Context, installed: String, release: AppRelease): Boolean {
        createChannel(context)
        if (!allowed(context) || !UpdateNotificationPolicy.shouldNotify(installed, release.version,
                prefs(context).getString("last_notified_version", null))) return false
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_UPDATES
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val pending = PendingIntent.getActivity(context, NOTIFICATION_ID, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_update_notification)
            .setContentTitle("SWU Collection : nouvelle mise à jour")
            .setContentText("La version ${release.version} est disponible. Ouvre les paramètres pour l’installer.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "La version ${release.version} est disponible. Ouvre les paramètres pour l’installer. Une sauvegarde de ta collection sera créée avant l’installation."))
            .setContentIntent(pending).setAutoCancel(true).build()
        return try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            prefs(context).edit().putString("last_notified_version", release.version).apply()
            true
        } catch (_: SecurityException) { false }
    }

    fun clearInstalled(context: Context, installed: String) {
        val last = prefs(context).getString("last_notified_version", null) ?: return
        if (!UpdateFeed.isNewer(last, installed)) NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }
}

class UpdateCheckWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    @Suppress("DEPRECATION")
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!AppChannel.supportsUpdateChecks(applicationContext.packageName)) return@withContext Result.success()
        try {
            if (applicationContext.packageName == AppChannel.TEST_PACKAGE) {
                PreviewUpdateNotifications.check(applicationContext)
                return@withContext Result.success()
            }
            val installed = applicationContext.packageManager.getPackageInfo(applicationContext.packageName, 0).versionName ?: "0.0.0"
            UpdateNotifications.clearInstalled(applicationContext, installed)
            val release = UpdateFeed.fetchLatest(installed)
            UpdateReleaseCache.save(applicationContext, release)
            release?.let { UpdateNotifications.offer(applicationContext, installed, it) }
            Result.success()
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) { Result.retry() }
    }

    companion object {
        private fun constraints() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        fun schedule(context: Context) {
            if (!AppChannel.supportsUpdateChecks(context.packageName)) {
                WorkManager.getInstance(context).cancelUniqueWork("application-update-check")
                WorkManager.getInstance(context).cancelUniqueWork("application-update-check-now")
                return
            }
            UpdateNotifications.createChannel(context)
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(1, TimeUnit.HOURS)
                .setInitialDelay(1, TimeUnit.HOURS).setConstraints(constraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("application-update-check",
                ExistingPeriodicWorkPolicy.KEEP, request)
        }
        fun checkNow(context: Context) {
            if (!AppChannel.supportsUpdateChecks(context.packageName)) return
            val request = OneTimeWorkRequestBuilder<UpdateCheckWorker>().setConstraints(constraints()).build()
            WorkManager.getInstance(context).enqueueUniqueWork("application-update-check-now", ExistingWorkPolicy.KEEP, request)
        }
    }
}
