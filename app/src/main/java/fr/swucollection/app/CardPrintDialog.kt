package fr.swucollection.app

import android.net.Uri
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun CardPrintDialog(title: String, items: List<PrintCard>, proxies: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var advanced by remember { mutableStateOf(false) }
    var backs by remember { mutableStateOf(false) }
    var large by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf(false) }
    var prepared by remember { mutableStateOf<File?>(null) }
    val plan = remember(items, proxies, backs, large) { runCatching { CardPdfPlan.pages(items, proxies, backs, large) } }
    DisposableEffect(Unit) { onDispose { prepared?.delete() } }
    fun write(uri: Uri?) {
        if (uri == null) { status = "PDF prêt. Tu peux l’enregistrer quand tu veux."; return }
        val file = prepared ?: return
        busy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val stream = context.contentResolver.openOutputStream(uri, "wt") ?: error("Fichier inaccessible")
                    stream.use { output -> file.inputStream().use { it.copyTo(output) } }
                }
                withContext(Dispatchers.IO) {
                    val fingerprint = badgeFingerprint(items.sortedBy { DeckConstruction.reference(it.card) }.joinToString(";") { DeckConstruction.reference(it.card)+":"+it.quantity })
                    recordBadgeAction(context,if(proxies)"proxy" else "export",fingerprint)
                }
                status = "PDF enregistré."
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { status = "Enregistrement impossible. Réessaie en choisissant un autre emplacement." }
            finally { busy = false }
        }
    }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { write(it) }
    fun savePrepared() = save.launch(if (proxies) "SWU-Proxies.pdf" else "SWU-Deck-illustre.pdf")
    if (preview && prepared != null) PdfPreviewDialog(prepared!!, busy, status, onSave = { savePrepared() }, onDismiss = { preview = false })
    if (!preview) AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(if (proxies) "Proxies à imprimer" else "PDF illustré du deck") },
        text = { Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            plan.getOrNull()?.let { pages -> Text("${pages.sumOf { it.size }} ${if (proxies) "face(s)" else "carte(s) distincte(s)"} · ${pages.size} page(s) A4") }
            if (proxies) {
                Text("9 cartes par page, avec repères de découpe. Imprime à 100 % ou « taille réelle ».")
                Text("Format : ${if (large) "63,5 × 88,9" else "63 × 88"} mm")
                TextButton(onClick = { advanced = !advanced }, enabled = !busy) { Text("Options d’impression") }
                if (advanced) Row { Checkbox(large, onCheckedChange = if (busy) null else { value -> large = value; prepared?.delete(); prepared = null })
                    Text("Utiliser 63,5 × 88,9 mm", Modifier.padding(top = 12.dp)) }
                if (advanced && items.any { it.card.backImageUrl.isNotBlank() }) Row {
                    Checkbox(backs, onCheckedChange = if (busy) null else { value -> backs = value; prepared?.delete(); prepared = null })
                    Text("Ajouter les faces arrière sur des cartes séparées", Modifier.padding(top = 8.dp))
                }
            } else Text("Leader, base, deck et réserve, avec visuels et quantités. Les cartes ne sont pas répétées pour chaque exemplaire.")
            if (prepared != null) TextButton(onClick = { preview = true }, enabled = !busy) { Text("Voir l’aperçu") }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(status ?: plan.exceptionOrNull()?.message ?: "Les images déjà en cache sont réutilisées ; une connexion est nécessaire pour les autres.", style = MaterialTheme.typography.bodySmall)
        } }, confirmButton = {
            TextButton(enabled = !busy && plan.isSuccess, onClick = {
                if (prepared != null) savePrepared() else {
                    busy = true
                    scope.launch {
                        try {
                            prepared = CardPdfExporter.create(context, title, plan.getOrThrow(), proxies) { done, total -> status = "Préparation des images : $done/$total" }
                            status = "PDF prêt."; preview = true
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (error: Exception) { status = error.message ?: "Création du PDF impossible. Réessaie." }
                        finally { busy = false }
                    }
                }
            }) { Text(if (prepared == null) "Créer le PDF" else "Enregistrer le PDF") }
        }, dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Fermer") } })
}

