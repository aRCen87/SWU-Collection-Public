package fr.swucollection.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
internal fun DeckExportDialog(deck: PortableDeck, name: String, catalog: List<CatalogCard>,
    details: CardDetails, owned: List<CollectionCard> = emptyList(), onDismiss: () -> Unit) {
    val context = LocalContext.current
    var printMode by remember { mutableStateOf("") }
    var printItems by remember { mutableStateOf<List<PrintCard>>(emptyList()) }
    fun beginPrint(mode: String) {
        runCatching { if (mode == "missing") DeckInsights.missingPrints(deck, owned, catalog, details) else CardPdfPlan.fromDeck(deck, catalog) }.onSuccess { if (it.isEmpty()) Toast.makeText(context, "Aucune carte manquante pour ce deck.", Toast.LENGTH_LONG).show() else { printItems = it; printMode = if (mode == "missing") "proxies" else mode } }
            .onFailure { Toast.makeText(context, it.message ?: "Référence inconnue", Toast.LENGTH_LONG).show() }
    }
    var target by rememberSaveable { mutableStateOf<DeckDestination?>(null) }
    var payload by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf<String?>(null) }
    var moreExports by rememberSaveable { mutableStateOf(false) }
    fun copy(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Deck $name", text))
        recordBadgeAction(context,"export",badgeFingerprint(text))
    }
    fun save(uri: Uri?) {
        if (uri == null) return
        runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(payload) }
                ?: throw IllegalArgumentException("Fichier inaccessible")
        }.onSuccess { recordBadgeAction(context,"export",badgeFingerprint(payload)); status = "Export enregistré." }
            .onFailure { status = "Enregistrement impossible. Ton deck reste dans l’appli." }
    }
    val jsonFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { save(it) }
    val textFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { save(it) }
    fun destinationPayload(destination: DeckDestination): String? = runCatching {
        DeckExternalExport.payload(destination, name, deck, catalog, details)
    }.onFailure { status = it.message ?: "Export impossible : vérifie les références des cartes." }.getOrNull()

    fun openDestination(destination: DeckDestination) {
        target = destination; status = null
        destinationPayload(destination)?.let { text ->
            runCatching { copy(text) }.onSuccess {
                status = "Deck copié. Colle-le sur ${destination.title}."
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(destination.url))) }
                    .onSuccess { Toast.makeText(context, "Deck copié : colle-le sur ${destination.title}.", Toast.LENGTH_LONG).show() }
                    .onFailure { status = "Deck copié. Ouvre ${destination.url} dans ton navigateur et colle-le." }
            }.onFailure { status = "Copie impossible. Enregistre plutôt le fichier." }
        }
    }

    if (printMode == "pdf") CardPrintDialog(name, printItems, false, onDismiss = { printMode = "" })
    if (printMode == "proxies") Dialog(onDismissRequest = { printMode = "" }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = ListPalette.Background) {
            ProxyBuilderPanel(catalog, owned, details, initial = printItems, onBack = { printMode = "" })
        }
    }
    if (printMode.isEmpty()) AlertDialog(onDismissRequest = onDismiss, title = { Text("Exporter le deck") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Un appui copie ton deck et ouvre la plateforme. Il te reste à le coller et à le valider.", color = ListPalette.Secondary)
                if (AppChannel.supportsExperience(context.packageName)) DeckImageShareButton(deck, name, catalog)
                DeckDestination.entries.forEach { destination ->
                    Button(onClick = { openDestination(destination) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Copier et ouvrir ${destination.title}")
                    }
                }
                target?.let { destination ->
                    Text(if (destination == DeckDestination.MELEE)
                        "Melee : ouvre Text Editor, colle la liste puis appuie sur Convert. Vérifie ensuite les cartes avant de soumettre."
                    else "Karabast : colle le JSON dans le champ « Deck link / OR paste deck JSON directly » pour utiliser ton deck.")
                    TextButton(onClick = {
                        destinationPayload(destination)?.let { text ->
                            runCatching { copy(text) }.onSuccess { status = "Deck copié pour ${destination.title}." }
                                .onFailure { status = "Copie impossible." }
                        }
                    }) { Text("Copier seulement") }
                    OutlinedButton(onClick = {
                        destinationPayload(destination)?.let { text ->
                            payload = text
                            if (destination == DeckDestination.KARABAST) jsonFile.launch("SWU-Karabast.json")
                            else textFile.launch("SWU-Melee.txt")
                        }
                    }) { Text("Enregistrer pour ${destination.title}") }
                    Text("Un deck incomplet ou non conforme peut être refusé par le site.",
                        style = MaterialTheme.typography.bodySmall, color = ListPalette.Secondary)
                    HorizontalDivider()
                }
                TextButton(onClick = { moreExports = !moreExports }) { Text(if (moreExports) "Masquer les autres exports" else "PDF, proxies et autres exports") }
                if (moreExports) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { beginPrint("pdf") }, modifier = Modifier.weight(1f)) { Text("PDF illustré", style = MaterialTheme.typography.labelMedium) }
                    OutlinedButton(onClick = { beginPrint("proxies") }, modifier = Modifier.weight(1f)) { Text("Proxies", style = MaterialTheme.typography.labelMedium) }
                }
                TextButton(onClick = { beginPrint("missing") }) { Text("Proxies des cartes manquantes") }
                TextButton(onClick = {
                    runCatching { copy(DeckInterchange.exportJson(deck, name)) }
                        .onSuccess { status = "JSON copié." }.onFailure { status = "Copie impossible." }
                }) { Text("Copier le JSON SWUDB / SWUBase") }
                TextButton(onClick = {
                    payload = DeckInterchange.exportJson(deck, name); jsonFile.launch("SWU-deck.json")
                }) { Text("Enregistrer le fichier JSON") }
                TextButton(onClick = {
                    payload = DeckTextExport.render(deck, catalog, details); textFile.launch("SWU-deck.txt")
                }) { Text("Enregistrer la liste texte") }
                }
                status?.let { Text(it, color = ListPalette.Gold) }
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } })
}


