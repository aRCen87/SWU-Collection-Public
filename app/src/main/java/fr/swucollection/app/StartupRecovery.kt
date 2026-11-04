package fr.swucollection.app

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@Composable
internal fun StartupRecoveryPanel(database: CollectionDatabase, reason: String, canContinue: Boolean,
    onContinue: () -> Unit, onRetry: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val updates = remember { AppUpdates(context, database) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val archive = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            busy = true
            message = runCatching {
                withContext(Dispatchers.IO) {
                    // Close before copying; preserve the DB and any WAL together, without rewriting them.
                    database.close()
                    val file = context.getDatabasePath("swu_collection.db")
                    val sources = listOf(file, java.io.File(file.path + "-wal"), java.io.File(file.path + "-shm")).filter { it.isFile }
                    check(sources.isNotEmpty()) { "Aucune base locale à exporter." }
                    val output = context.contentResolver.openOutputStream(uri) ?: error("Fichier inaccessible.")
                    ZipOutputStream(output).use { zip -> sources.forEach { source ->
                        zip.putNextEntry(ZipEntry(source.name)); source.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
                    } }
                }
                "Copie de récupération enregistrée. Conserve ce fichier pour une récupération accompagnée."
            }.getOrElse { "Copie impossible : ${it.message}" }
            busy = false
        }
    }
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("SWU COLLECTION", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        OutlinedCard(border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Récupérer l’ouverture", style = MaterialTheme.typography.titleMedium)
                Text(reason)
                Text("Ne désinstalle pas l’application et n’efface pas ses données.")
                Button(onClick = onRetry, enabled = !busy) { Text("Réessayer") }
                if (canContinue) OutlinedButton(onClick = onContinue, enabled = !busy) { Text("Continuer en mode essentiel") }
            }
        }
        if (canContinue) PortableBackupPanel(database, onRetry)
        else OutlinedButton(onClick = { archive.launch("SWU-recuperation.zip") }, enabled = !busy) { Text("Conserver une copie de récupération") }
        OutlinedButton(onClick = { scope.launch { updates.check() } }, enabled = !updates.busy) { Text("Rechercher une mise à jour") }
        updates.message?.let { Text(it) }
        if (updates.release != null) {
            if (canContinue) Button(enabled = !updates.busy, onClick = { scope.launch {
                if (updates.readyToInstall) updates.install() else { updates.download(); if (updates.readyToInstall) updates.install() }
            } }) { Text("Installer le correctif disponible") }
            else OutlinedButton(onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/aRCen87/SWU-Collection-Releases/releases/latest"))) }
                    .onFailure { message = "Ouvre la page des versions depuis ton navigateur." }
            }) { Text("Ouvrir les versions disponibles") }
        }
        if (busy || updates.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        message?.let { Text(it) }
    }
}
