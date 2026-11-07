package fr.swucollection.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import android.content.Context
import android.net.Uri
import java.io.InputStream

internal data class BackupSummary(val references: Int, val copies: Int, val lists: Int, val decks: Int = 0)
internal fun backupInteger(row: JSONObject, key: String, default: Int, range: IntRange): Int {
    val value = if (row.has(key)) row.get(key) else default
    require(value is Int || value is Long) { "Valeur entière attendue : $key." }
    val number = (value as Number).toLong()
    require(number in range.first.toLong()..range.last.toLong()) { "Valeur invalide : $key." }
    return number.toInt()
}

internal fun readPortableBackup(input: InputStream): String {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        require(output.size() + count <= 16 * 1024 * 1024) { "Fichier trop volumineux (16 Mo maximum)." }
        output.write(buffer, 0, count)
    }
    return Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(output.toByteArray())).toString()
}

internal fun writeVerifiedPortableBackup(context: Context, uri: Uri, json: String) {
    validateBackup(json)
    require(json.toByteArray(Charsets.UTF_8).size <= 16 * 1024 * 1024) { "Sauvegarde trop volumineuse (16 Mo maximum)." }
    context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use { it.write(json) }
        ?: error("Fichier inaccessible")
    val readBack = context.contentResolver.openInputStream(uri)?.use(::readPortableBackup)
        ?: error("Le fichier enregistré ne peut pas être relu. Choisis un autre emplacement.")
    check(readBack == json) { "Le fichier enregistré est incomplet. Réessaie dans un autre emplacement." }
    validateBackup(readBack)
}

internal fun validateBackup(json: String): BackupSummary {
    val root = JSONObject(json)
    require(root.optString("format") == "SWU_COLLECTION_BACKUP") { "Fichier de sauvegarde invalide." }
    val version = backupInteger(root, "version", 1, 1..8)
    require(!root.has("badges") || root.opt("badges") is JSONObject) { "Hauts faits invalides." }
    root.optJSONObject("badges")?.let(::validateBadgeBackup)
    require(version < 6 || root.has("badges")) { "Hauts faits absents de la sauvegarde." }
    require(!root.has("tools") || root.get("tools") is JSONObject) { "Outils invalides dans la sauvegarde." }
    require(version < 8 || root.has("tools")) { "Outils absents de la sauvegarde." }
    root.optJSONObject("tools")?.let(::validateTools)
    val lists = root.optJSONArray("lists") ?: org.json.JSONArray()
    require(!root.has("lists") || root.get("lists") is org.json.JSONArray)
    validateCardLists(lists)
    require(!root.has("decks") || root.get("decks") is org.json.JSONArray) { "Decks invalides dans la sauvegarde." }
    val decks = root.optJSONArray("decks") ?: org.json.JSONArray()
    require(version < 5 || root.has("decks")) { "Decks absents de la sauvegarde." }
    validateDeckRows(decks)
    root.optJSONArray("goals")?.let(::validateGoals)
    require(!root.has("goals") || root.get("goals") is org.json.JSONArray)
    val rows = root.getJSONArray("cards")
    require(rows.length() <= 100000) { "Sauvegarde trop volumineuse." }
    val keys = mutableSetOf<String>()
    var copies = 0
    for (i in 0 until rows.length()) {
        val row = rows.getJSONObject(i)
        require(listOf("name", "setCode", "number").all { row.opt(it) is String && row.getString(it).trim().isNotEmpty() }) { "Carte incomplète." }
        val quantity = backupInteger(row, "quantity", 1, 1..9999)
        require(!row.has("variant") || row.get("variant") is String) { "Variante invalide." }
        val variant = row.optString("variant", "Normale")
        require(variant.isNotBlank()) { "Variante invalide." }
        val language = CardLanguages.read(row)
        require(version < 7 || row.has("language")) { "Langue absente de la sauvegarde." }
        require(keys.add(MarketPricing.key(row.getString("setCode"), row.getString("number")) + ":" + variant + ":" + language)) { "Référence en double dans la sauvegarde." }
        copies += quantity
    }
    return BackupSummary(rows.length(), copies, lists.length(), decks.length())
}

@Composable
internal fun PortableBackupPanel(database: CollectionDatabase, onRestored: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<String?>(null) }
    var summary by remember { mutableStateOf<BackupSummary?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            busy = true
            message = runCatching { withContext(Dispatchers.IO) {
                val json = database.exportJson()
                writeVerifiedPortableBackup(context, uri, json)
                database.recordBadgeEvent("backup", java.util.UUID.randomUUID().toString())
            }; "Sauvegarde enregistrée et vérifiée : collection, variantes, quantités, listes, decks et hauts faits." }
                .getOrElse { "Sauvegarde impossible : ${it.message}" }
            busy = false
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            runCatching { withContext(Dispatchers.IO) {
                val json = context.contentResolver.openInputStream(uri)?.use(::readPortableBackup)
                    ?: error("Fichier inaccessible")
                json to validateBackup(json)
            } }.onSuccess { (json, info) -> pending = json; summary = info }
                .onFailure { message = "Restauration impossible : ${it.message}" }
            busy = false
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Sauvegarde et changement de téléphone", fontWeight = FontWeight.Bold)
            Text("Conserve le fichier dans un endroit accessible sur ton prochain téléphone, puis restaure-le ici.")
            OutlinedButton(onClick = { export.launch("SWU-Collection-sauvegarde-${java.text.SimpleDateFormat("yyyy-MM-dd-HHmmss", java.util.Locale.ROOT).format(java.util.Date())}.json") }, enabled = !busy,
                modifier = Modifier.fillMaxWidth()) { Text("Sauvegarder collection, listes et decks") }
            OutlinedButton(onClick = { open.launch(arrayOf("application/json", "text/*", "application/octet-stream")) },
                enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Restaurer un fichier") }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            message?.let { Text(it) }
        }
    }
    if (pending != null && summary != null) {
        fun restore(replace: Boolean) {
            val json = pending ?: return
            pending = null; summary = null
            scope.launch {
                busy = true
                message = runCatching { withContext(Dispatchers.IO) {
                    // Keep a verified recovery copy of the current collection before any mutation.
                    UpdateBackups.create(context, database)
                    database.importJson(json, replace)
                }; onRestored(); if (replace) "Collection, listes et decks restaurés." else "Sauvegarde fusionnée." }
                    .getOrElse { "Restauration impossible : ${it.message}" }
                busy = false
            }
        }
        AlertDialog(onDismissRequest = { pending = null; summary = null },
            title = { Text("Vérifier la sauvegarde") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${summary!!.references} référence(s), ${summary!!.copies} exemplaire(s), ${summary!!.lists} liste(s), ${summary!!.decks} deck(s).")
                Text("Remplacer restaure exactement ce fichier. Fusionner additionne les quantités : importer deux fois ajoute deux fois les cartes. Les listes et decks déjà présents sont conservés. Les anciennes sauvegardes sans decks conservent tes decks actuels.")
                Text("Une copie de récupération sera créée avant l’import. Elle reste accessible dans les options secondaires de cet écran.")
            } },
            confirmButton = { TextButton(onClick = { restore(true) }) { Text("Remplacer") } },
            dismissButton = { Row {
                TextButton(onClick = { restore(false) }) { Text("Fusionner") }
                TextButton(onClick = { pending = null; summary = null }) { Text("Annuler") }
            } })
    }
}





