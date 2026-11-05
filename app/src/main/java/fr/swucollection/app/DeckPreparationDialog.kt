package fr.swucollection.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun DeckPreparationDialog(name: String, deck: PortableDeck, owned: List<CollectionCard>, catalog: List<CatalogCard>, details: CardDetails, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var proxies by rememberSaveable { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val missing = remember(deck, owned, catalog, details) { runCatching { DeckInsights.missingPrints(deck, owned, catalog, details) } }
    val rows = missing.getOrDefault(emptyList())
    val payload = remember(deck, owned, catalog, details, name) { runCatching { "Cartes à trouver — $name\n\n" + DeckCompanion.shopping(deck, owned, catalog, details) + "\n\nQuantités calculées pour ce deck, réserve comprise, toutes versions reconnues de la collection comptées ensemble.\n" } }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) {
            saving = true
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val stream = context.contentResolver.openOutputStream(uri, "wt") ?: error("Emplacement inaccessible")
                        stream.bufferedWriter(Charsets.UTF_8).use { it.write(payload.getOrThrow()) }
                    }
                    status = "Liste d’achat enregistrée."
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { status = "Enregistrement impossible. Tu peux réessayer." }
                finally { saving = false }
            }
        }
    }
    val proxyState = rememberProxyBuilderState(rows)
    val density = LocalDensity.current
    // Recreate the native window when display metrics change; keep the workspace above this key.
    key(density.density, density.fontScale) {
    Dialog(
        onDismissRequest = { if (proxies) proxies = false else if (!saving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        if (proxies) {
            Surface(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), color = ListPalette.Background) {
                ProxyBuilderPanel(catalog, owned, details, initial = rows, state = proxyState, onBack = { proxies = false })
            }
        } else {
            BoxWithConstraints(Modifier.fillMaxWidth().safeDrawingPadding().imePadding().padding(24.dp), contentAlignment = Alignment.Center) {
                Surface(
                    Modifier.widthIn(max = 560.dp).fillMaxWidth().heightIn(max = maxHeight),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Préparer mon deck", style = MaterialTheme.typography.headlineSmall)
                        Text(name, color = ListPalette.Gold)
                        missing.exceptionOrNull()?.let { Text(it.message ?: "Certaines références sont inconnues. Vérifie le deck.", color = ListPalette.Danger) }
                        if (missing.isSuccess) Text(if (rows.isEmpty()) "Tu possèdes toutes les cartes nécessaires." else "${rows.sumOf { it.quantity }} exemplaire(s) à trouver")
                        Text("Disponibilité calculée pour ce deck, réserve comprise, toutes versions reconnues confondues.", style = MaterialTheme.typography.bodySmall)
                        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 240.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(rows, key = { "${it.section}:${DeckConstruction.reference(it.card)}" }) { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    CardArtwork(row.card, Modifier.width(42.dp).height(58.dp))
                                    Column(Modifier.weight(1f)) { Text("${row.quantity} × ${row.card.name}"); Text("${row.card.setCode} ${row.card.number} · ${row.section}", style = MaterialTheme.typography.bodySmall) }
                                }
                            }
                        }
                        Button(enabled = !saving && payload.isSuccess && rows.isNotEmpty(), modifier = Modifier.fillMaxWidth(), onClick = { save.launch("SWU-manquantes.txt") }) { Text("Exporter la liste d’achat") }
                        OutlinedButton(enabled = !saving && missing.isSuccess && rows.isNotEmpty(), modifier = Modifier.fillMaxWidth(), onClick = { proxies = true }) { Text("Imprimer les proxies manquants") }
                        status?.let { Text(it) }
                        if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
                        TextButton(onClick = onDismiss, enabled = !saving, modifier = Modifier.fillMaxWidth()) { Text("Fermer") }
                    }
                }
            }
        }
    }
    }
}
