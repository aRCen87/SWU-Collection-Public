package fr.swucollection.app

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.work.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

internal enum class AutomaticBackupDecision { SAVE, UNCHANGED, CONFLICT }
internal object AutomaticBackupPolicy {
    fun canUpload(boundOwner: String?, uid: String) = boundOwner == null || boundOwner == uid
    fun remainingDelay(lastSaved: Long, now: Long) = if (now < lastSaved) 0L else (5 * 60_000L - (now - lastSaved)).coerceAtLeast(0L)
    fun decide(initialized: Boolean, knownHead: String?, remoteHead: String?, lastHash: String?, hash: String): AutomaticBackupDecision = when {
        !initialized && remoteHead != null -> AutomaticBackupDecision.CONFLICT
        initialized && knownHead != remoteHead -> AutomaticBackupDecision.CONFLICT
        initialized && lastHash == hash -> AutomaticBackupDecision.UNCHANGED
        else -> AutomaticBackupDecision.SAVE
    }
}

/** The baseline belongs to one account on one device; a new login never overwrites a remote backup. */
internal object AutomaticAccountBackup {
    private val mutex = Mutex()
    suspend fun <T> exclusive(block: suspend () -> T): T = mutex.withLock { block() }
    private fun prefs(context: Context) = context.getSharedPreferences("automatic_account_backup", Context.MODE_PRIVATE)
    fun enabled(context: Context, uid: String) = prefs(context).getBoolean("$uid.enabled", true)
    fun setEnabled(context: Context, uid: String, enabled: Boolean) {
        prefs(context).edit().putBoolean("$uid.enabled", enabled).apply()
        if (enabled) AutomaticBackupWorker.enqueue(context)
    }
    fun status(context: Context, uid: String) = prefs(context).getString("$uid.status", "La sauvegarde automatique sera vérifiée quand tu seras connecté.").orEmpty()
    fun acknowledge(context: Context, uid: String, head: String?, raw: String, pending: Boolean = false) {
        check(prefs(context).edit().putString("device_owner", uid).putBoolean("$uid.initialized", true)
            .putString("$uid.head", head).putString("$uid.hash", if (pending) "" else PrivatePayload.hash(raw.toByteArray(Charsets.UTF_8)))
            .putLong("$uid.savedAt", if (pending) 0L else System.currentTimeMillis())
            .putString("$uid.status", if (pending) "Restauration terminée ; sauvegarde automatique en attente." else "Sauvegarde du compte enregistrée et vérifiée.").commit())
        if (pending) AutomaticBackupWorker.enqueue(context)
    }
    suspend fun run(context: Context, service: CommunityService) = mutex.withLock {
        val user = service.auth.currentUser ?: return@withLock
        if (!user.isEmailVerified || !enabled(context, user.uid)) return@withLock
        val uid = user.uid; val p = prefs(context)
        fun status(text: String) { p.edit().putString("$uid.status", text).apply() }
        val deviceOwner = p.getString("device_owner", null)
        if (!AutomaticBackupPolicy.canUpload(deviceOwner, uid)) {
            status("Sauvegarde en pause : ce téléphone a été associé à un autre compte. Vérifie les données puis sauvegarde manuellement pour l’associer à ce compte.")
            return@withLock
        }
        val raw = CollectionDatabase(context.applicationContext).use { accountBackupJson(context, it) }
        val hash = PrivatePayload.hash(raw.toByteArray(Charsets.UTF_8))
        if (p.getBoolean("$uid.initialized", false) && hash == p.getString("$uid.hash", null)) return@withLock
        if (!service.allowed()) { status("Compte désactivé : sauvegarde automatique en pause."); return@withLock }
        check(service.auth.currentUser?.uid == uid)
        val remote = service.backupHead()
        when (AutomaticBackupPolicy.decide(p.getBoolean("$uid.initialized", false), p.getString("$uid.head", null), remote, p.getString("$uid.hash", null), hash)) {
            AutomaticBackupDecision.CONFLICT -> {
                status("Sauvegarde en pause : une sauvegarde existe sur un autre téléphone ou a changé. Ouvre Sauvegarde pour choisir de la restaurer ou de conserver les données de ce téléphone.")
                return@withLock
            }
            AutomaticBackupDecision.UNCHANGED -> return@withLock
            AutomaticBackupDecision.SAVE -> Unit
        }
        val remaining = AutomaticBackupPolicy.remainingDelay(p.getLong("$uid.savedAt", 0L), System.currentTimeMillis())
        if (remaining > 0) {
            status("Modifications en attente de sauvegarde automatique.")
            AutomaticBackupWorker.enqueue(context, remaining)
            return@withLock
        }
        status("Sauvegarde automatique en cours…")
        val id = service.saveAccountBackup(raw, remote, uid)
        check(service.auth.currentUser?.uid == uid)
        acknowledge(context, uid, id, raw)
    }
    fun failed(context: Context, uid: String) {
        prefs(context).edit().putString("$uid.status", "Sauvegarde automatique en attente : vérifie ta connexion et ton compte. Les données restent disponibles sur ce téléphone.").apply()
    }
}

class AutomaticBackupWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val service = CommunityService.create(applicationContext) ?: return@withContext Result.success()
        val uid = service.auth.currentUser?.uid ?: return@withContext Result.success()
        try { AutomaticAccountBackup.run(applicationContext, service); Result.success() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { AutomaticAccountBackup.failed(applicationContext, uid); Result.retry() }
    }
    companion object {
        private fun constraints() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build()
        fun schedule(context: Context) {
            if (context.packageName != AppChannel.TEST_PACKAGE) return
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("account-auto-backup", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<AutomaticBackupWorker>(15, TimeUnit.MINUTES).setConstraints(constraints()).build())
        }
        fun enqueue(context: Context, delay: Long = 0) {
            if (context.packageName != AppChannel.TEST_PACKAGE) return
            WorkManager.getInstance(context).enqueueUniqueWork(if (delay > 0) "account-auto-backup-delayed" else "account-auto-backup-now", ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<AutomaticBackupWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS)
                    .setConstraints(constraints()).setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
        }
        fun cancel(context: Context) {
            listOf("account-auto-backup", "account-auto-backup-now", "account-auto-backup-delayed").forEach { WorkManager.getInstance(context).cancelUniqueWork(it) }
        }
    }
}

@Composable
internal fun AutomaticBackupControls(uid: String) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var enabled by remember(uid) { mutableStateOf(AutomaticAccountBackup.enabled(context, uid)) }
    var status by remember(uid) { mutableStateOf(AutomaticAccountBackup.status(context, uid)) }
    LaunchedEffect(uid, lifecycleOwner) { lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        while (true) { status = AutomaticAccountBackup.status(context, uid); delay(3000) }
    } }
    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text("Sauvegarde automatique", Modifier.weight(1f))
        Switch(enabled, { enabled = it; AutomaticAccountBackup.setEnabled(context, uid, it) })
    }
    Text(if (enabled) status else "Sauvegarde automatique désactivée sur ce téléphone.", style = MaterialTheme.typography.bodySmall)
    Text("Les modifications sont regroupées avant l’envoi. Sur un autre téléphone, choisis une sauvegarde à restaurer : aucun remplacement ni cumul de quantités n’est automatique.", style = MaterialTheme.typography.bodySmall)
}
