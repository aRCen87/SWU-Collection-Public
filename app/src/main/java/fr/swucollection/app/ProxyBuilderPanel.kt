package fr.swucollection.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import org.json.JSONObject

internal class ProxyBuilderState(
    val selectionJson: MutableState<String>,
    val query: MutableState<String>,
    val selectionOnly: MutableState<Boolean>,
    val filters: MutableState<DeckBrowserFilters>
)

@Composable
internal fun rememberProxyBuilderState(initial: List<PrintCard>? = null): ProxyBuilderState {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("proxy_print_selection", 0) }
    val seeded = remember(initial) { initial?.groupBy { DeckConstruction.reference(it.card) }?.mapValues { (_, rows) -> rows.sumOf { it.quantity }.coerceAtMost(99) } }
    val selection = rememberSaveable { mutableStateOf(if (seeded != null) JSONObject(seeded).toString() else prefs.getString("cards", "{}") ?: "{}") }
    val query = rememberSaveable { mutableStateOf("") }
    val selectionOnly = rememberSaveable { mutableStateOf(false) }
    val filters = remember { mutableStateOf(DeckBrowserFilters()) }
    return remember(selection, query, selectionOnly, filters) { ProxyBuilderState(selection, query, selectionOnly, filters) }
}

@Composable
internal fun ProxyBuilderPanel(catalog: List<CatalogCard>, owned: List<CollectionCard>, details: CardDetails,
    initial: List<PrintCard>? = null, state: ProxyBuilderState = rememberProxyBuilderState(initial), onBack: () -> Unit) {
    val context = LocalContext.current
    val largeText = LocalDensity.current.fontScale > 1.3f
    val prefs = remember { context.getSharedPreferences("proxy_print_selection", 0) }
    val byId = remember(catalog) { catalog.associateBy(DeckConstruction::reference) }
    var selectionJson by state.selectionJson
    val selected = remember(selectionJson, byId) {
        runCatching { val json = JSONObject(selectionJson); json.keys().asSequence().filter { it in byId }
            .mapNotNull { key -> json.optInt(key).takeIf { it in 1..99 }?.let { key to it } }.toMap() }.getOrDefault(emptyMap())
    }
    LaunchedEffect(selectionJson) { prefs.edit().putString("cards", selectionJson).apply() }
    fun update(id: String, count: Int) {
        val next = selected.toMutableMap()
        if (count <= 0) next.remove(id) else next[id] = count.coerceAtMost(99)
        selectionJson = JSONObject(next as Map<*, *>).toString()
        prefs.edit().putString("cards", selectionJson).apply()
    }
    val stock = remember(owned, details, byId) { owned.mapNotNull { row -> DeckConstruction.stockReference(row.setCode, row.number)?.let { byId[it] }?.let { details.family(it) to row.quantity } }
        .groupBy { it.first }.mapValues { (_, rows) -> rows.sumOf { it.second } } }
    var preview by remember { mutableStateOf<CatalogCard?>(null) }
    var reverse by remember { mutableStateOf(false) }
    var query by state.query
    var selectionOnly by state.selectionOnly
    var filters by state.filters
    var filtering by remember { mutableStateOf(false) }
    var printing by remember { mutableStateOf(false) }
    var clearing by remember { mutableStateOf(false) }
    val filtered = remember(catalog, selected, selectionOnly, query, filters, stock, details) {
        catalog.filter { card -> (!selectionOnly || DeckConstruction.reference(card) in selected) && DeckBrowser.search(card, query) &&
            filters.matches(card, details.get(card), stock[details.family(card)] ?: 0, false) }
            .let { rows -> if (selectionOnly) rows else rows.distinctBy(details::family) }
            .sortedWith(DeckBrowser.comparator(DeckBrowser.DEFAULT_SORT, details, DeckBrowser.extensionOrder(catalog)))
    }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onBack) { Text("Retour") }
            Text("Proxies", Modifier.weight(1f).padding(top = 12.dp), color = ListPalette.Gold)
            TextButton(onClick = { clearing = true }, enabled = selected.isNotEmpty()) { Text("Vider") }
        }
        OutlinedTextField(query, { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), label = { Text("Rechercher une carte") })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(!selectionOnly, onClick = { selectionOnly = false }, label = { Text("Catalogue") })
            FilterChip(selectionOnly, onClick = { selectionOnly = true; query = ""; filters = DeckBrowserFilters() }, label = { Text("Sélection (${selected.size})") })
            TextButton(onClick = { filtering = true }) { Text("Filtres") }
        }
        LazyVerticalGrid(GridCells.Adaptive(if (largeText) 168.dp else 128.dp), modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (filtered.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                Column { Text("Aucune carte dans cette vue.")
                    TextButton(onClick = { query = ""; filters = DeckBrowserFilters(); selectionOnly = false }) { Text("Afficher tout le catalogue") } }
            }
            items(filtered, key = DeckConstruction::reference) { card ->
                val id = DeckConstruction.reference(card)
                DeckCardTile(card, true, selected[id] ?: 0, stock[details.family(card)] ?: 0, 0, false, false,
                    canAdd = (selected[id] ?: 0) < 99 && selected.values.sum() < CardPdfPlan.MAX_CARDS,
                    onPreview = { preview = card; reverse = false }, onChoose = {}, onQuantity = { update(id, it) })
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { printing = true }, enabled = selected.isNotEmpty()) { Text("Créer le PDF") }
            Text("${selected.values.sum()} exemplaire(s)", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodySmall)
        }
    }
    preview?.let { card -> AlertDialog(onDismissRequest = { preview = null }, title = { Text(card.name) },
        text = { Column {
            CardArtwork(if (reverse) card.backImageUrl else card.imageUrl, Modifier.fillMaxWidth().heightIn(max = 380.dp).aspectRatio(if (DeckConstruction.type(card) in setOf("leader", "chef", "base") && !reverse) 1.4f else 5f / 7f))
            if (card.backImageUrl.isNotBlank()) TextButton(onClick = { reverse = !reverse }) { Text("Retourner") }
        } }, confirmButton = { TextButton(onClick = { preview = null }) { Text("Fermer la carte") } }) }
    if (filtering) DeckFilterDialog(filters, catalog, details, onChange = { filters = it }, onDismiss = { filtering = false })
    if (printing) CardPrintDialog("Ma sélection", selected.map { PrintCard(byId.getValue(it.key), it.value) }, true, onDismiss = { printing = false })
    if (clearing) AlertDialog(onDismissRequest = { clearing = false }, title = { Text("Vider la sélection de proxies ?") },
        text = { Text("Tes decks et ta collection restent inchangés.") },
        confirmButton = { TextButton(onClick = { selectionJson = "{}"; prefs.edit().remove("cards").apply(); clearing = false }) { Text("Vider") } },
        dismissButton = { TextButton(onClick = { clearing = false }) { Text("Annuler") } })
}
