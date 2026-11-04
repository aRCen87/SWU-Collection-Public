package fr.swucollection.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun rememberInternetAvailable(): Boolean {
    val context = LocalContext.current.applicationContext
    val manager = remember(context) { context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager }
    fun current() = runCatching { manager.getNetworkCapabilities(manager.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true }.getOrDefault(false)
    var available by remember { mutableStateOf(current()) }
    DisposableEffect(manager) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { available = current() }
            override fun onLost(network: Network) { available = current() }
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) { available = current() }
        }
        val registered = runCatching { manager.registerDefaultNetworkCallback(callback) }.isSuccess
        onDispose { if (registered) runCatching { manager.unregisterNetworkCallback(callback) } }
    }
    return available
}

@Composable
internal fun OfflineImagesPanel(catalog: List<CatalogCard>, sets: List<CatalogSet>, decks: List<DeckStore.SavedDeck>, online: Boolean) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var selected by rememberSaveable { mutableStateOf(decks.firstOrNull()?.let { "deck:${it.id}" } ?: "set:${sets.firstOrNull()?.code.orEmpty()}") }
    var choosing by remember { mutableStateOf(false) }
    var chooseDecks by remember { mutableStateOf(decks.isNotEmpty()) }
    var targetQuery by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var clearing by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    var generation by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf<String?>(null) }
    val targets = sets.map { "set:${it.code}" to it.name } + decks.map { "deck:${it.id}" to "Deck · ${it.name}" }
    val chosenCards = remember(selected, catalog, decks) {
        if (selected.startsWith("set:")) catalog.filter { it.setCode == selected.removePrefix("set:") }
        else decks.firstOrNull { it.id == selected.removePrefix("deck:") }?.let { saved ->
            val ids = DeckCompanion.rows(saved.deck).map { it.cardId }.toSet()
            catalog.filter { DeckConstruction.reference(it) in ids }
        }.orEmpty()
    }
    val urls = remember(chosenCards) { OfflineImages.urls(chosenCards.flatMap { card -> CardLanguageArtwork.allFaces(context,card) }) }
    val stored = remember(urls, generation) { urls.count { OfflineImages.contains(context, it) } }
    val megabytes = remember(generation) { OfflineImages.bytes(context) / (1024.0 * 1024.0) }
    Text(if (online) "Connexion Internet disponible" else "Hors connexion", color = ListPalette.Gold)
    Text("Le catalogue, la collection et les decks sont consultables sur ce téléphone. Les cotes déjà chargées restent consultables ; leur actualisation nécessite Internet.")
    Text("Conserve les images d’un deck ou d’une extension pour les consulter et préparer tes PDF sans connexion. Rectos et versos disponibles sont inclus.")
    OutlinedButton(onClick = { targetQuery = ""; chooseDecks = selected.startsWith("deck:"); choosing = true }, enabled = !busy,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Choisir les images hors connexion" }) {
        Text(targets.firstOrNull { it.first == selected }?.second ?: "Choisir un deck ou une extension")
    }
    if (choosing) AlertDialog(onDismissRequest = { choosing = false }, title = { Text("Choisir les images") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(chooseDecks, onClick = { chooseDecks = true; targetQuery = "" }, enabled = decks.isNotEmpty(), label = { Text("Mes decks") })
                FilterChip(!chooseDecks, onClick = { chooseDecks = false; targetQuery = "" }, label = { Text("Extensions") })
            }
            OutlinedTextField(targetQuery, { targetQuery = it }, label = { Text("Rechercher dans la sélection") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            val options = targets.filter { (it.first.startsWith("deck:") == chooseDecks) && DeckBrowser.searchKey(it.second).contains(DeckBrowser.searchKey(targetQuery.trim())) }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 260.dp)) {
                items(options, key = { it.first }) { (id, title) ->
                    TextButton(onClick = { selected = id; choosing = false; status = null }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(title) }
                }
                if (options.isEmpty()) item { Text("Aucun résultat dans cette sélection.") }
            }
        }
    }, confirmButton = { TextButton(onClick = { choosing = false }) { Text("Fermer") } })
    if (!busy) Text("$stored / ${urls.size} visuel(s) conservé(s) pour cette sélection")
    if (chosenCards.any { it.imageUrl.isBlank() }) Text("Certaines cartes n’ont pas encore de visuel disponible dans le catalogue.", style = MaterialTheme.typography.bodySmall)
    if (!busy) Text("Images conservées : ${String.format(java.util.Locale.FRANCE, "%.1f", megabytes)} Mo sur ce téléphone", style = MaterialTheme.typography.bodySmall)
    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    status?.let { Text(it) }
    if (busy) TextButton(onClick = { job?.cancel() }) { Text("Interrompre le téléchargement") }
    else Button(enabled = online && urls.isNotEmpty() && stored < urls.size, modifier = Modifier.fillMaxWidth(), onClick = {
        busy = true
        job = scope.launch {
            var failures = 0
            try {
                urls.forEachIndexed { index, url ->
                    ensureActive()
                    try { OfflineImages.download(context, url) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { failures++ }
                    status = "Préparation : ${index + 1}/${urls.size}"
                }
                if(failures == 0 && urls.isNotEmpty()) withContext(Dispatchers.IO) {
                    recordBadgeAction(context,"offline",badgeFingerprint(urls.sorted().joinToString(";")))
                }
                status = if (failures == 0) "Images prêtes hors connexion." else "$failures visuel(s) non téléchargé(s). Tu peux réessayer ; les autres sont conservés."
            } catch (cancelled: CancellationException) { status = "Téléchargement interrompu. Les images terminées sont conservées."; throw cancelled }
            finally { busy = false; generation++ }
        }
    }) { Text(if (stored == 0) "Télécharger les images" else "Compléter les images") }
    TextButton(onClick = { clearing = true }, enabled = !busy && megabytes > 0) { Text("Libérer les images téléchargées") }
    Text("Ces images restent sur ce téléphone et ne sont pas incluses dans la sauvegarde de collection.", style = MaterialTheme.typography.bodySmall)
    if (clearing) AlertDialog(onDismissRequest = { clearing = false }, title = { Text("Libérer les images ?") }, text = { Text("Seules les images téléchargées pour le hors connexion seront supprimées. Tu pourras les télécharger à nouveau.") }, confirmButton = {
        TextButton(onClick = {
            clearing = false; busy = true
            job = scope.launch {
                try { withContext(Dispatchers.IO) { OfflineImages.clear(context) }; status = "Images téléchargées supprimées." }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) { status = error.message }
                finally { busy = false; generation++ }
            }
        }) { Text("Supprimer les images") }
    }, dismissButton = { TextButton(onClick = { clearing = false }) { Text("Annuler") } })
}

