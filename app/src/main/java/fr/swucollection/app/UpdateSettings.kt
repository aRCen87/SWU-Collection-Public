package fr.swucollection.app

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun UpdateNotificationPermissionRequest() {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) UpdateCheckWorker.checkNow(context)
    }
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("application_updates", Context.MODE_PRIVATE)
        if (Build.VERSION.SDK_INT >= 33 && !UpdateNotifications.allowed(context) &&
            !prefs.getBoolean("notification_permission_asked", false)) {
            prefs.edit().putBoolean("notification_permission_asked", true).apply()
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

private fun Context.updateLifecycleOwner(): androidx.lifecycle.LifecycleOwner? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is androidx.lifecycle.LifecycleOwner) return current
        val base = current.baseContext
        if (base === current) break
        current = base
    }
    return current as? androidx.lifecycle.LifecycleOwner
}

@Composable
internal fun UpdateNotificationControls() {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(UpdateNotifications.allowed(context)) }
    val owner = context.updateLifecycleOwner()
    DisposableEffect(owner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) allowed = UpdateNotifications.allowed(context)
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        allowed = UpdateNotifications.allowed(context)
        if (allowed) UpdateCheckWorker.checkNow(context)
    }
    Text(if (allowed) "Notifications de mise à jour activées."
        else "Autorise les notifications pour être prévenu d’une nouvelle version.", fontSize = 12.sp)
    if (!allowed) OutlinedButton(onClick = {
        launcher.launch(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
    }, modifier = Modifier.fillMaxWidth()) { Text("Activer les notifications") }
    Text("Vérification à l’ouverture et périodiquement en arrière-plan. Android peut retarder les notifications pour économiser la batterie.", fontSize = 12.sp)
}

@Composable
internal fun UpdateSafetyPanel(database: CollectionDatabase, onRestored: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                withContext(Dispatchers.IO) {
                    val json = UpdateBackups.read(context)
                    val output = context.contentResolver.openOutputStream(uri) ?: error("Impossible d’écrire le fichier.")
                    output.bufferedWriter(Charsets.UTF_8).use { it.write(json) }
                }
                message = "Copie de la sauvegarde enregistrée."
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Exception) { message = error.message ?: "Export impossible."
            } finally { busy = false }
        }
    }
    if (UpdateBackups.exists(context)) {
        HorizontalDivider()
        Text("Copie de récupération locale")
        Text("Cette copie protège l’état enregistré avant une mise à jour ou une restauration de fichier. Exporte la sauvegarde complète pour conserver tes derniers ajouts.", fontSize = 12.sp)
        OutlinedButton(onClick = { export.launch("SWU-Collection-recuperation.json") },
            enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Exporter la copie de récupération") }
        OutlinedButton(onClick = { confirm = true }, enabled = !busy,
            modifier = Modifier.fillMaxWidth()) { Text("Restaurer la copie de récupération") }
        message?.let { Text(it, fontSize = 12.sp) }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false },
        title = { Text("Restaurer la sauvegarde ?") },
        text = { Text("Ta collection actuelle sera remplacée par la copie locale créée avant la dernière installation ou restauration. Les ajouts faits depuis seront retirés. Sauvegarde d’abord ta collection actuelle si tu veux les conserver.") },
        confirmButton = { TextButton(onClick = {
            confirm = false
            scope.launch {
                busy = true
                try {
                    val count = withContext(Dispatchers.IO) { UpdateBackups.restore(context, database) }
                    onRestored()
                    message = "$count référence(s) restaurée(s), sans ajouter de doublons."
                } catch (cancelled: CancellationException) { throw cancelled
                } catch (error: Exception) { message = error.message ?: "Restauration impossible."
                } finally { busy = false }
            }
        }) { Text("Restaurer") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Annuler") } })
}

