package fr.swucollection.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Import is a preview; persistence happens only when the user saves in the editor. */
@Composable
internal fun DeckImportDialog(catalog: List<CatalogCard>, details: CardDetails, onDismiss: () -> Unit, onImport: (String, PortableDeck) -> Unit) {
    val context = LocalContext.current
    var raw by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(output.size() + count <= 1_000_000)
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            } ?: throw IllegalArgumentException("Fichier illisible")
            raw = bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
            error = null
        }.onFailure { error = "Fichier illisible ou trop volumineux (1 Mo maximum)." }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Importer un deck") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Choisis un fichier JSON ou texte exporté de SWUDB / SWUBase, ou colle son contenu. Le deck s’ouvrira pour vérification avant enregistrement.")
                OutlinedButton(onClick = { picker.launch(arrayOf("application/json", "text/*", "application/octet-stream")) }) {
                    Text("Choisir un fichier")
                }
                OutlinedButton(onClick = {
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val text = clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
                    if (text.isNullOrBlank()) error = "Le presse-papiers est vide."
                    else if (text.length > 1_000_000) error = "Liste trop volumineuse (1 Mo maximum)."
                    else { raw = text; error = null }
                }) { Text("Coller une liste copiée") }
                OutlinedTextField(raw, { if (it.length <= 1_000_000) { raw = it; error = null } },
                    label = { Text("JSON ou texte du deck") }, modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp))
                error?.let { Text(it, color = ListPalette.Danger) }
            }
        }, confirmButton = {
            TextButton(onClick = {
                runCatching {
                    val input = raw.removePrefix("\uFEFF").trim()
                    val isJson = input.startsWith("{")
                    val deck = if (isJson) DeckInterchange.importJson(input) else DeckTextImport.parse(input, catalog, details)
                    val metadata = if (isJson) org.json.JSONObject(input).optJSONObject("metadata") else null
                    val name = metadata?.optString("name")?.trim()?.take(120)?.takeIf { it.isNotEmpty() } ?: "Deck importé"
                    onImport(name, deck)
                }.onFailure { error = if (it is IllegalArgumentException) it.message ?: "Import impossible." else "Fichier invalide : vérifie le JSON, le leader, la base et les quantités." }
            }, enabled = raw.isNotBlank()) { Text("Ouvrir le deck") }
        }, dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } })
}

