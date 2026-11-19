package fr.swucollection.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay

@Composable
internal fun DeckBuilderPanel(
    catalog: List<CatalogCard>, owned: List<CollectionCard>, initial: PortableDeck,
    initialName: String = "Mon deck", newDeck: Boolean = false, draftKey: String = "new", details: CardDetails, rules: LegalityRules,
    onCancel: () -> Unit, onSave: (String, PortableDeck, String, List<ScanBatchEntry>) -> Unit,
    onPreview: (CatalogCard, List<CatalogCard>) -> Unit,
    startScanning: Boolean = false, onScanStarted: () -> Unit = {},
    onScanCollection: (() -> Unit)? = null, seedCard: CatalogCard? = null
) {
    CompositionLocalProvider(LocalContentColor provides ListPalette.Text) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val dualPane = AppChannel.supportsExperience(LocalContext.current.packageName) && maxWidth >= 700.dp && maxHeight >= 480.dp
    val draftContext = LocalContext.current.applicationContext
    val draftStore = remember(draftContext) { DeckDraftStore(draftContext) }
    val baseline = remember(initial, initialName) { DeckInterchange.exportJson(initial, initialName) }
    val recoverable = remember(draftKey, baseline) { draftStore.read(draftKey, baseline) }
    var restoring by rememberSaveable { mutableStateOf(recoverable != null) }
    var closing by remember { mutableStateOf(false) }
    val draftWritesAllowed = remember(draftKey) { java.util.concurrent.atomic.AtomicBoolean(true) }
    var draftStarted by rememberSaveable { mutableStateOf(recoverable != null || seedCard != null) }
    var draftMessage by remember { mutableStateOf<String?>(null) }
    var undo by remember { mutableStateOf<List<DeckDraft>>(emptyList()) }
    var name by rememberSaveable(initialName) { mutableStateOf(initialName) }
    var deck by rememberSaveable(initial, stateSaver = Saver<PortableDeck, String>(
        save = { DeckInterchange.exportJson(it) }, restore = { DeckInterchange.importJson(it) }
    )) { mutableStateOf(if (newDeck && seedCard != null) DeckCompanion.seed(initial, seedCard) else initial) }
    var naming by rememberSaveable { mutableStateOf(newDeck && recoverable == null) }
    var resetting by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var leaderChosen by rememberSaveable { mutableStateOf(!newDeck || seedCard?.let { DeckConstruction.type(it) in listOf("leader", "chef") } == true) }
    var baseChosen by rememberSaveable { mutableStateOf(!newDeck || seedCard?.let { DeckConstruction.type(it) == "base" } == true) }
    var collectionEntries by rememberSaveable(stateSaver = Saver<List<ScanBatchEntry>, String>(
        save = { DeckScan.collectionJson(it).toString() },
        restore = { DeckScan.collectionFromJson(org.json.JSONArray(it)) }
    )) { mutableStateOf(emptyList()) }
    var collectionBatchId by rememberSaveable { mutableStateOf(java.util.UUID.randomUUID().toString()) }
    var collectScans by rememberSaveable { mutableStateOf(false) }
    var confirmingSave by remember { mutableStateOf(false) }
    fun snapshot() = DeckDraft(name, deck, leaderChosen, baseChosen, collectionEntries, collectionBatchId)
    fun checkpoint() { val value = snapshot(); if (undo.lastOrNull() != value) undo = (undo + value).takeLast(30) }
    fun dirty(value: DeckDraft) = (newDeck && draftStarted) || value.name != initialName || value.deck != initial || value.leaderChosen != !newDeck || value.baseChosen != !newDeck || value.collectionEntries.isNotEmpty()
    fun storeDraft(value: DeckDraft): Boolean {
        if (!draftWritesAllowed.get()) return true
        return runCatching { if (dirty(value)) draftStore.write(draftKey, baseline, value) else draftStore.clear(draftKey) }
            .onSuccess { draftMessage = if (dirty(value)) "Brouillon sauvegardé sur ce téléphone" else null }
            .onFailure { draftMessage = "Brouillon non sauvegardé : garde le deck ouvert et utilise Enregistrer." }.isSuccess
    }
    val currentSnapshot = snapshot()
    LaunchedEffect(currentSnapshot, restoring, closing, draftStarted) {
        if (!restoring && !closing) { if (dirty(currentSnapshot)) draftMessage = "Sauvegarde du brouillon…"; delay(300); storeDraft(currentSnapshot) }
    }
    val latestSnapshot = rememberUpdatedState { snapshot() }
    val latestActive = rememberUpdatedState { !restoring && !closing }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, draftKey) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE && latestActive.value()) storeDraft(latestSnapshot.value())
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (draftWritesAllowed.get() && latestActive.value()) runCatching {
                val value = latestSnapshot.value()
                if (dirty(value)) draftStore.write(draftKey, baseline, value) else draftStore.clear(draftKey)
            }
        }
    }
    var tab by rememberSaveable { mutableStateOf("Deck") }
    var catalogueOpen by rememberSaveable { mutableStateOf(false) }
    var filters by rememberSaveable(stateSaver = Saver<DeckBrowserFilters, ArrayList<String>>(
        save = { arrayListOf(it.aspect, it.type, it.set, it.rarity, it.cost, it.arena, it.trait, it.stock, it.keywords) },
        restore = { DeckBrowserFilters(it[0], it[1], it[2], it[3], it[4], it[5], it[6], it[7], it.getOrElse(8) { "" }) }
    )) { mutableStateOf(DeckBrowserFilters()) }
    var filtersOpen by remember { mutableStateOf(false) }
    var sort by rememberSaveable { mutableStateOf(DeckBrowser.DEFAULT_SORT) }
    LaunchedEffect(sort) { if (sort !in DeckBrowser.sorts) sort = DeckBrowser.DEFAULT_SORT }
    val gridState = rememberLazyGridState()
    LaunchedEffect(tab, catalogueOpen, query, filters, sort) {
        if (tab in listOf("Leader", "Base")) filters = filters.forIdentity()
        gridState.scrollToItem(0)
    }
    var sortOpen by remember { mutableStateOf(false) }
    var visual by rememberSaveable { mutableStateOf(true) }
    var leaving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showingInsights by remember { mutableStateOf(false) }
    var affinityOnly by rememberSaveable { mutableStateOf(false) }
    var showingReport by remember { mutableStateOf(false) }
    val tileLegality = remember(catalog, rules) { PremierLegality(catalog, rules) }
    val report = remember(deck, owned, catalog, details, rules) {
        DeckConstruction.report(deck, owned, catalog, details, rules)
    }
    var preparing by remember { mutableStateOf(false) }
    val largeText = LocalDensity.current.fontScale > 1.3f
    var exportMenu by remember { mutableStateOf(false) }
    var actionsOpen by remember { mutableStateOf(false) }
    var concentration by rememberSaveable { mutableStateOf(false) }
    var comparingChanges by remember { mutableStateOf(false) }
    var scanning by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(startScanning) {
        if (startScanning) { scanning = true; onScanStarted() }
    }
    var scanReserve by rememberSaveable { mutableStateOf(false) }
    var scanMessage by remember { mutableStateOf<String?>(null) }
    var scanLast by remember { mutableStateOf<String?>(null) }
    var scanUndo by remember { mutableStateOf<List<DeckDraft>>(emptyList()) }
    var pendingIdentity by remember { mutableStateOf<CatalogCard?>(null) }
    var scanMode by rememberCollectionFilter("scanModeV3", "Une par une")
    var scanPending by remember { mutableStateOf<CatalogCard?>(null) }
    var scanReadToken by remember { mutableIntStateOf(0) }
    var scanLastCard by remember { mutableStateOf<ScanBatchEntry?>(null) }
    var correctingScan by remember { mutableStateOf<ScanBatchEntry?>(null) }
    var lastScanCollected by remember { mutableStateOf(false) }
    var pendingFinish by remember { mutableStateOf<String?>(null) }
    fun keepForLater() {
        draftStarted = true
        if (storeDraft(snapshot())) { draftWritesAllowed.set(false); closing = true; onCancel() }
        else error = "Le brouillon n’a pas pu être sauvegardé. Le deck reste ouvert."
    }
    fun saveDeck() {
        draftWritesAllowed.set(false); closing = true
        runCatching { onSave(name.trim(), deck, collectionBatchId, collectionEntries); draftStore.clear(draftKey) }
            .onFailure { draftWritesAllowed.set(true); closing = false; error = "Enregistrement impossible. Ton deck et les ajouts en attente restent ouverts." }
    }
    val gold = ListPalette.Gold
    val byId = remember(catalog) { catalog.associateBy { DeckInterchange.normalizeId(it.setCode + "-" + it.number) } }
    fun family(id: String) = byId[id]?.let(details::family) ?: "unknown:$id"
    val ownedByFamily = remember(owned, catalog, details) {
        owned.mapNotNull { row -> DeckConstruction.stockReference(row.setCode, row.number)?.let { family(it) to row.quantity } }
            .groupBy { it.first }.mapValues { (_, rows) -> rows.sumOf { it.second } }
    }
    val required = (deck.deck + deck.sideboard + listOfNotNull(if (leaderChosen) DeckEntry(deck.leader, 1) else null, if (baseChosen) DeckEntry(deck.base, 1) else null))
        .groupBy { family(it.cardId) }.mapValues { (_, rows) -> rows.sumOf { it.count } }
    val missing = required.mapValues { (key, needed) -> (needed - (ownedByFamily[key] ?: 0)).coerceAtLeast(0) }
    val affinityPool = remember(deck.leader, deck.base, catalog, details, leaderChosen, baseChosen) {
        if (leaderChosen && baseChosen) DeckInsights.affinityPool(deck, catalog, details) else null
    }
    val zone = if (tab == "Réserve") DeckEditor.Zone.SIDEBOARD else DeckEditor.Zone.MAIN
    val entries = if (zone == DeckEditor.Zone.MAIN) deck.deck else deck.sideboard
    fun requestBack() { if (dirty(snapshot())) leaving = true else { draftWritesAllowed.set(false); closing = true; onCancel() } }
    fun discardAndLeave() {
        draftWritesAllowed.set(false); closing = true
        runCatching { draftStore.clear(draftKey) }
            .onSuccess { onCancel() }
            .onFailure { draftWritesAllowed.set(true); closing = false; leaving = false; error = "Impossible de supprimer le brouillon. Le deck reste ouvert." }
    }
    BackHandler { if (!dualPane && (catalogueOpen || tab in listOf("Leader", "Base"))) { catalogueOpen = false; tab = "Deck"; query = "" } else requestBack() }
    val filtered = remember(catalog, query, tab, catalogueOpen, deck, details, filters, sort, ownedByFamily, affinityOnly, affinityPool, dualPane) {
        val candidates = if (tab == "Leader" || tab == "Base" || catalogueOpen || dualPane) catalog else entries.mapNotNull { byId[it.cardId] }
        candidates.filter { card ->
            val type = DeckConstruction.type(card)
            val correctType = when (tab) {
                "Leader" -> type in listOf("leader", "chef")
                "Base" -> type == "base"
                else -> type in listOf("unite", "unit", "evenement", "event", "amelioration", "upgrade")
            }
            val identity = tab == "Leader" || tab == "Base"
            (correctType || (!catalogueOpen && !dualPane && tab in listOf("Deck", "Réserve"))) &&
                (identity || (!catalogueOpen && !dualPane) || !affinityOnly || affinityPool == null || DeckInsights.affinity(card, affinityPool, details) == DeckInsights.Affinity.MATCH) &&
                DeckBrowser.search(card, query) && filters.matches(card, details.get(card), ownedByFamily[details.family(card)] ?: 0, identity)
        }.distinctBy { if (catalogueOpen || dualPane || tab == "Leader" || tab == "Base") details.family(it) else it.setCode + "-" + it.number }
            .sortedWith(DeckBrowser.comparator(sort, details, DeckBrowser.extensionOrder(catalog)))
    }
    Column(Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = 8.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { checkpoint(); naming = true }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp)) {
                Text(name.ifBlank { "Nommer mon deck" }, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium, color = gold)
            }
            TextButton(onClick = { requestBack() }) { Text("Quitter", color = gold) }
        }
        val browsing = (!concentration && dualPane) || catalogueOpen || tab in listOf("Leader", "Base")
        if (!browsing) {
            DeckSelectionPane("", deck, catalog, details, zone, leaderChosen, baseChosen,
                Modifier.weight(1f).fillMaxWidth(),
                onZone = { tab = if (it == DeckEditor.Zone.MAIN) "Deck" else "Réserve" },
                onQuantity = { id, count, target -> runCatching { checkpoint(); deck = DeckEditor.setQuantity(deck, id, count, target) }.onFailure { error = it.message } },
                onPreview = onPreview, onIdentity = { tab = it; query = "" },
                onAdd = { catalogueOpen = true; query = "" }, ownedByFamily = ownedByFamily)
        } else {
        if (!dualPane) TextButton(onClick = { catalogueOpen = false; tab = if (zone == DeckEditor.Zone.SIDEBOARD) "Réserve" else "Deck"; query = "" }) {
            Text("← Revenir au deck · ${deck.deck.sumOf { it.count }} cartes")
        }
        if (!dualPane) FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), maxItemsInEachRow = if (largeText) 2 else 4) {
            listOf("Leader", "Base", "Deck", "Réserve").forEach { label ->
                TextButton(
                    onClick = { tab = label; query = "" },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = if (tab == label) ListPalette.Raised else Color.Transparent,
                        contentColor = if (tab == label) gold else ListPalette.Secondary
                    )
                ) { Text(label, maxLines = 1, style = MaterialTheme.typography.labelMedium) }
            }
        }
        Text(if (tab == "Leader") "Choisis le leader de ton deck." else if (tab == "Base") "Choisis la base de ton deck." else
            "Deck : ${deck.deck.sumOf { it.count }}/50 min. · Réserve : ${deck.sideboard.sumOf { it.count }}/10 max.",
            color = ListPalette.Secondary, style = MaterialTheme.typography.bodyMedium)
        OutlinedTextField(query, { query = it }, label = { Text("Rechercher une carte") },
            modifier = Modifier.fillMaxWidth(), singleLine = true)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { filtersOpen = true }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text(if (filters.activeCount == 0) "Filtres" else "Filtres (${filters.activeCount})") }
            Box {
                OutlinedButton(onClick = { sortOpen = true }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Trier") }
                DropdownMenu(sortOpen, onDismissRequest = { sortOpen = false }) {
                    DeckBrowser.sorts.forEach { order -> DropdownMenuItem(text = { Text(order) }, onClick = { sort = order; sortOpen = false }) }
                }
            }
            FilterChip(visual, onClick = { visual = true }, label = { Text("Cartes") })
            FilterChip(!visual, onClick = { visual = false }, label = { Text("Liste") })
        }
        DeckActiveFilterChips(filters) { filters = it }
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LazyVerticalGrid(state = gridState, columns = if (visual) GridCells.Adaptive(if (largeText) 168.dp else 128.dp) else GridCells.Fixed(1),
            modifier = Modifier.weight(1f).fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
            item(key = "deck-scanner", span = { GridItemSpan(maxLineSpan) }) {
            OutlinedButton(onClick = {
                scanReserve = tab == "Réserve"; scanMessage = null; scanLast = null
                scanUndo = emptyList(); scanning = true
            }, modifier = Modifier.fillMaxWidth()) { Text("Scanner dans ce deck") }
            }
            item(key = "deck-heading", span = { GridItemSpan(maxLineSpan) }) {
                Column {
        draftMessage?.let { Text(it, color = ListPalette.Secondary, style = MaterialTheme.typography.labelSmall) }
        if (collectionEntries.isNotEmpty()) TextButton(onClick = { confirmingSave = true }) {
            Text("${collectionEntries.sumOf { it.quantity }} ajout(s) à la collection à vérifier", color = gold)
        }
                }
            }
            if (!dualPane && !catalogueOpen && query.isBlank() && tab in listOf("Deck", "Réserve")) item(key = "deck-identities", span = { GridItemSpan(maxLineSpan) }) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DeckIdentityCard("Leader", if (leaderChosen) byId[deck.leader] else null, "À choisir", Modifier.weight(1f),
                        onPreview = { byId[deck.leader]?.let { onPreview(it, listOfNotNull(byId[deck.leader], byId[deck.base])) } },
                        onChange = { tab = "Leader"; query = "" })
                    DeckIdentityCard("Base", if (baseChosen) byId[deck.base] else null, "À choisir", Modifier.weight(1f),
                        onPreview = { byId[deck.base]?.let { onPreview(it, listOfNotNull(byId[deck.leader], byId[deck.base])) } },
                        onChange = { tab = "Base"; query = "" })
                }
            }
            if (!dualPane && tab in listOf("Deck", "Réserve")) item(key = "catalogue-toggle", span = { GridItemSpan(maxLineSpan) }) {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 2) {
                    FilterChip(catalogueOpen, onClick = { catalogueOpen = true }, modifier = Modifier.weight(1f), label = { Text("Ajouter des cartes") })
                    FilterChip(!catalogueOpen, onClick = { catalogueOpen = false; affinityOnly = false }, modifier = Modifier.weight(1f), label = { Text(if (tab == "Réserve") "Cartes de réserve" else "Cartes du deck") })
                    FilterChip(affinityOnly && affinityPool != null, enabled = affinityPool != null,
                        onClick = { affinityOnly = !affinityOnly; catalogueOpen = true }, label = { Text("Même affinité") })
                }
            }
            item(key = "result-summary", span = { GridItemSpan(maxLineSpan) }) {
                Text("${filtered.size} carte(s) · $sort", color = ListPalette.Secondary, style = MaterialTheme.typography.labelSmall)
            }
            if (filtered.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Text("Aucune carte dans cette vue.", color = ListPalette.Secondary)
                    TextButton(onClick = { filters = DeckBrowserFilters(); query = ""; affinityOnly = false; catalogueOpen = true }) { Text("Afficher tout le catalogue") }
                }
            }
            gridItems(filtered, key = { it.setCode + "-" + it.number }) { card ->
                val id = DeckInterchange.normalizeId(card.setCode + "-" + card.number)
                val quantity = DeckEditor.quantity(deck, id, zone)
                val key = family(id)
                val slot = tab == "Leader" || tab == "Base"
                DeckCardTile(card, visual, quantity, ownedByFamily[key] ?: 0, missing[key] ?: 0,
                    slot = slot, selected = (leaderChosen && id == deck.leader) || (baseChosen && id == deck.base),
                    canAdd = (deck.deck + deck.sideboard).filter { family(it.cardId) == key }.sumOf { it.count } < 3 &&
                        (zone != DeckEditor.Zone.SIDEBOARD || deck.sideboard.sumOf { it.count } < 10),
                    onPreview = { onPreview(card, filtered) },
                    onChoose = {
                        checkpoint()
                        if (tab == "Leader") {
                            deck = deck.copy(leader = id); leaderChosen = true
                            if (!baseChosen) tab = "Base"
                        } else {
                            deck = deck.copy(base = id); baseChosen = true
                            if (leaderChosen) { tab = "Deck"; catalogueOpen = dualPane }
                        }
                        query = ""; filters = DeckBrowserFilters()
                    },
                    onQuantity = { checkpoint(); deck = DeckEditor.setQuantity(deck, id, it, zone) },
                    moveLabel = if (zone == DeckEditor.Zone.MAIN) "Vers la réserve" else "Vers le deck",
                    canMove = quantity > 0 && (zone == DeckEditor.Zone.SIDEBOARD || deck.sideboard.sumOf { it.count } < 10),
                    onMove = { checkpoint(); deck = DeckEditor.move(deck, id, if (zone == DeckEditor.Zone.MAIN) DeckEditor.Zone.SIDEBOARD else DeckEditor.Zone.MAIN, 1) },
                    status = tileLegality.status(card), overLimit = !slot && (required[family(id)] ?: 0) > 3,
                    guidance = if (slot || affinityPool == null) null else when (DeckInsights.affinity(card, affinityPool, details)) {
                        DeckInsights.Affinity.MATCH -> "✓ Affinités du deck"
                        DeckInsights.Affinity.UNKNOWN -> "Affinités inconnues"
                        else -> null
                    })
            }
            // Unknown imports remain visible and removable in both display modes.
            if (tab in listOf("Deck", "Réserve")) {
                gridItems(entries.filter { it.cardId !in byId }, key = { "unknown:${it.cardId}" }, span = { GridItemSpan(maxLineSpan) }) { entry ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("${entry.cardId} · ${entry.count} · Référence inconnue", Modifier.weight(1f))
                        TextButton(onClick = { checkpoint(); deck = DeckEditor.setQuantity(deck, entry.cardId, 0, zone) }) { Text("Retirer") }
                    }
                }
            }
        }
        if (dualPane) DeckSelectionPane("", deck, catalog, details, zone, leaderChosen, baseChosen,
            Modifier.weight(.8f).fillMaxHeight(), onZone = { tab = if (it == DeckEditor.Zone.MAIN) "Deck" else "Réserve"; query = "" },
            onQuantity = { id, count, target -> runCatching { checkpoint(); deck = DeckEditor.setQuantity(deck, id, count, target) }.onFailure { error = it.message } },
            onPreview = onPreview, onIdentity = { tab = it; query = "" }, ownedByFamily = ownedByFamily)
        }
        }
        error?.let { Text(it, color = ListPalette.Danger) }
        if (browsing && !dualPane) DeckBuildDock(deck, catalog, leaderChosen, baseChosen, valid = leaderChosen && baseChosen && report.issues.isEmpty()) { tab = it; query = "" }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                IconButton(onClick = { actionsOpen = true }) { Icon(Icons.Default.MoreVert, "Actions du deck") }
                DropdownMenu(actionsOpen, onDismissRequest = { actionsOpen = false }) {
                    DropdownMenuItem(text = { Text(if(concentration) "Quitter le mode concentration" else "Mode concentration") }, onClick = { actionsOpen = false; concentration = !concentration; catalogueOpen = false; tab = "Deck" })
                    DropdownMenuItem(text = { Text("Comparer avant / après") }, onClick = { actionsOpen = false; comparingChanges = true })
                    DropdownMenuItem(text = { Text("Vérifier le deck") }, enabled = leaderChosen && baseChosen,
                        onClick = { actionsOpen = false; showingReport = true })
                    DropdownMenuItem(text = { Text("Analyse et budget") }, enabled = leaderChosen && baseChosen,
                        onClick = { actionsOpen = false; showingInsights = true })
                    DropdownMenuItem(text = { Text("Préparer les cartes") }, enabled = leaderChosen && baseChosen,
                        onClick = { actionsOpen = false; preparing = true })
                    DropdownMenuItem(text = { Text("Exporter le deck") }, enabled = leaderChosen && baseChosen,
                        onClick = { actionsOpen = false; exportMenu = true })
                    DropdownMenuItem(text = { Text("Garder un brouillon et quitter") }, enabled = name.isNotBlank(),
                        onClick = { actionsOpen = false; keepForLater() })
                    DropdownMenuItem(text = { Text("Annuler la dernière action") }, enabled = undo.any { it != snapshot() }, onClick = {
                        actionsOpen = false
                        val previous = undo.indexOfLast { it != snapshot() }
                        if (previous >= 0) {
                            val value = undo[previous]; undo = undo.take(previous)
                            name = value.name; deck = value.deck; leaderChosen = value.leaderChosen; baseChosen = value.baseChosen
                            collectionEntries = value.collectionEntries; collectionBatchId = value.collectionBatchId.ifBlank { collectionBatchId }
                            if (!leaderChosen) tab = "Leader" else if (!baseChosen) tab = "Base"
                        }
                    })
                    DropdownMenuItem(text = { Text("Recommencer le deck") }, onClick = { actionsOpen = false; resetting = true })
                }
            }
            Button(onClick = {
                if (!leaderChosen || !baseChosen) keepForLater()
                else confirmingSave = true
            }, modifier = Modifier.weight(1f), enabled = name.isNotBlank()) {
                Text(if (!leaderChosen || !baseChosen) "Garder le brouillon" else "Enregistrer")
            }
        }
    }
    if(comparingChanges) DeckChangesDialog(initial, deck, catalog) { comparingChanges = false }
    correctingScan?.let { entry -> QuickScanCorrection(entry, { correctingScan = null }) { language, finish ->
        if (lastScanCollected) {
            val source = collectionEntries.firstOrNull { it.card.setCode == entry.card.setCode && it.card.number == entry.card.number && it.card.language == entry.card.language && it.variant == entry.variant }
                ?: error("Cet ajout a changé. Vérifie les cartes en attente.")
            val next = resolveScanEntries(collectionEntries, source, language, finish, 1)
            checkpoint(); collectionEntries = next
        }
        scanLastCard = entry.copy(card = entry.card.copy(language = language), variant = finish)
        correctingScan = null; scanReadToken++
    } }
    if (restoring && recoverable != null) AlertDialog(onDismissRequest = { draftWritesAllowed.set(false); closing = true; onCancel() }, title = { Text("Reprendre le brouillon ?") },
        text = { Text("${recoverable.name} · ${recoverable.deck.deck.sumOf { it.count }} cartes. Le deck enregistré reste inchangé tant que tu n’appuies pas sur Enregistrer.") },
        confirmButton = { TextButton(onClick = {
            name = recoverable.name; deck = recoverable.deck; leaderChosen = recoverable.leaderChosen; baseChosen = recoverable.baseChosen
            collectionEntries = recoverable.collectionEntries; collectionBatchId = recoverable.collectionBatchId.ifBlank { collectionBatchId }
            tab = if (!leaderChosen) "Leader" else if (!baseChosen) "Base" else "Deck"
            naming = false; restoring = false
        }) { Text("Reprendre") } }, dismissButton = { TextButton(onClick = { draftStore.clear(draftKey); restoring = false; naming = newDeck }) { Text(if (newDeck) "Recommencer ce brouillon" else "Repartir du deck enregistré") } })
    if (scanning && !restoring && !naming) {
        fun applyScan(card: CatalogCard, variant: String? = null): Boolean {
            return runCatching {
                val updated = DeckScan.add(deck, card, if (scanReserve) DeckEditor.Zone.SIDEBOARD else DeckEditor.Zone.MAIN, catalog, details)
                val nextCollection = if (collectScans) DeckScan.addCollectionCopy(collectionEntries, card, variant.orEmpty()) else collectionEntries
                updated to nextCollection
            }.fold(onSuccess = { (updated, nextCollection) ->
                if (updated != deck || (DeckConstruction.type(card) in listOf("leader", "chef") && !leaderChosen) ||
                    (DeckConstruction.type(card) == "base" && !baseChosen) || collectScans) {
                    scanUndo = (scanUndo + snapshot()).takeLast(100)
                    checkpoint(); deck = updated; collectionEntries = nextCollection
                    when (DeckConstruction.type(card)) {
                        "leader", "chef" -> leaderChosen = true
                        "base" -> baseChosen = true
                    }
                    lastScanCollected = collectScans
                    scanLastCard = ScanBatchEntry(card, variant.orEmpty(), 1)
                    scanLast = "${card.name} · ${CardLanguages.label(card.language)}"
                    scanMessage = null
                } else scanMessage = "Cette carte est déjà sélectionnée."
                true
            }, onFailure = {
                scanMessage = it.message ?: "Ajout impossible. Réessaie."
                false
            })
        }
        fun scanWithPrint(card: CatalogCard): Boolean {
            val variant = ScanVariants.detect(card).variant
            return applyScan(CardLanguageArtwork.localized(draftContext, card, card.language, variant), variant)
        }
        CardScannerDialog(
            onDismiss = {
                scanning = false; pendingIdentity = null; scanPending = null
                tab = if (scanReserve) "Réserve" else "Deck"; catalogueOpen = false; query = ""
            },
            onDetected = detected@{ set, number, raw ->
                if (scanPending != null || pendingIdentity != null || correctingScan != null) return@detected false
                val card = DeckScan.find(catalog, set, number)?.copy(language = ScanRecognition.language(raw) ?: CardLanguages.UNKNOWN)
                if (card == null) { scanMessage = "Référence introuvable : $set $number."; false }
                else {
                    val id = DeckConstruction.reference(card)
                    val replacing = when (DeckConstruction.type(card)) {
                        "leader", "chef" -> leaderChosen && deck.leader != id
                        "base" -> baseChosen && deck.base != id
                        else -> false
                    }
                    if (scanMode == "Une par une") { scanPending = card; scanMessage = null; true }
                    else if (replacing) { pendingFinish = ScanVariants.detect(card).variant; pendingIdentity = card; true }
                    else scanWithPrint(card)
                }
            },
            paused = pendingIdentity != null || scanPending != null || correctingScan != null,
            rescanToken = scanReadToken,
            onUndoLast = if (scanUndo.isEmpty()) null else ({
                val previous = scanUndo.last(); scanUndo = scanUndo.dropLast(1)
                checkpoint(); name = previous.name; deck = previous.deck
                leaderChosen = previous.leaderChosen; baseChosen = previous.baseChosen
                collectionEntries = previous.collectionEntries; collectionBatchId = previous.collectionBatchId.ifBlank { collectionBatchId }
                scanLast = null; scanLastCard = null; scanMessage = "Dernier ajout annulé."
            }),
            destinationControls = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(scannerDestination(name), color = gold, style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = { scanning = false; scanPending = null; onScanCollection?.invoke() },
                        enabled = onScanCollection != null) { Text("Ajouter à ma collection plutôt") }
                    ScannerModeChoices(scanMode, enabled = scanPending == null && pendingIdentity == null) { scanMode = it }
                }
            },
            title = "Scanner",
            hint = "Terminer pour vérifier les cartes de « $name » et enregistrer le deck.",
            inlineControls = {
                val pending = scanPending
                if (pending != null) {
                    ScannerCardConfirmation(pending, pending.language, ScanVariants.detect(pending).variant.orEmpty(),
                        scannerDestination(name), scanMessage,
                        onCancel = { scanPending = null; scanMessage = null; scanReadToken++ },
                        onConfirm = confirm@{ language, finish ->
                            if (scanPending != pending) return@confirm
                            val card = CardLanguageArtwork.localized(draftContext, pending, language, finish)
                            val replacing = when (DeckConstruction.type(card)) {
                                "leader", "chef" -> leaderChosen && deck.leader != DeckConstruction.reference(card)
                                "base" -> baseChosen && deck.base != DeckConstruction.reference(card)
                                else -> false
                            }
                            if (replacing) { pendingFinish = finish; pendingIdentity = card; scanPending = null }
                            else if (applyScan(card, finish)) { scanPending = null; scanReadToken++ }
                        })
                } else scanLastCard?.let { last ->
                    ScannerCardSummary(last.card, last.variant, "Ajoutée à « $name » · brouillon",
                        quantity = (if (scanReserve) deck.sideboard else deck.deck).firstOrNull { it.cardId == DeckConstruction.reference(last.card) }?.count ?: last.quantity)
                    TextButton(onClick = { correctingScan = last }) { Text("Corriger ce scan") }
                }
                Row(Modifier.fillMaxWidth().toggleable(value = collectScans, role = Role.Checkbox, onValueChange = { collectScans = it }), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(collectScans, onCheckedChange = null)
                    Text("Ajouter aussi à ma collection", color = gold, style = MaterialTheme.typography.labelMedium)
                }
                Text("${collectionEntries.sumOf { it.quantity }} ajout(s) à la collection en attente · validation à l’enregistrement du deck",
                    color = ListPalette.Secondary, style = MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(!scanReserve, onClick = { scanReserve = false }, label = { Text("Deck ${deck.deck.sumOf { it.count }}") })
                    FilterChip(scanReserve, onClick = { scanReserve = true }, label = { Text("Réserve ${deck.sideboard.sumOf { it.count }}/10") })
                }
                Text("Leader : ${if (leaderChosen) byId[deck.leader]?.name ?: deck.leader else "à scanner"} · Base : ${if (baseChosen) byId[deck.base]?.name ?: deck.base else "à scanner"}",
                    color = ListPalette.Secondary, style = MaterialTheme.typography.labelSmall)
                scanMessage?.let { Text(it, color = gold, style = MaterialTheme.typography.labelSmall) }
            }
        )
        pendingIdentity?.let { card ->
            AlertDialog(onDismissRequest = { pendingIdentity = null; scanReadToken++ },
                title = { Text(if (DeckConstruction.type(card) == "base") "Remplacer la base ?" else "Remplacer le leader ?") },
                text = { Text(card.name) },
                confirmButton = { TextButton(onClick = { pendingIdentity = null; if (applyScan(card, pendingFinish) && scanMode == "Une par une") scanReadToken++ }) { Text("Remplacer") } },
                dismissButton = { TextButton(onClick = { pendingIdentity = null; scanReadToken++ }) { Text("Annuler") } })
        }
    }
    if (confirmingSave) AlertDialog(onDismissRequest = { confirmingSave = false },
        title = { Text(if (collectionEntries.isEmpty()) "Enregistrer le deck ?" else "Enregistrer le deck et la collection ?") },
        text = {
            LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text(name, style = MaterialTheme.typography.titleMedium)
                    Text("Leader : ${byId[deck.leader]?.name ?: "À choisir"}")
                    Text("Base : ${byId[deck.base]?.name ?: "À choisir"}")
                    Text("Deck principal : ${deck.deck.sumOf { it.count }} · Réserve : ${deck.sideboard.sumOf { it.count }}")
                }
                items(report.issues) { Text(it, color = ListPalette.Danger) }
                if (collectionEntries.isNotEmpty()) item { Text("${collectionEntries.sumOf { it.quantity }} exemplaire(s) seront ajoutés à ta collection.") }
                items(collectionEntries, key = { it.reviewKey }) { entry ->
                    Text("${entry.quantity} × ${entry.card.name} · ${entry.card.setCode} ${entry.card.number} · ${entry.variant.ifBlank { "Finition à vérifier" }} · ${CardLanguages.label(entry.card.language)}")
                    if (!entry.resolved) ScanEntryReview(entry) { language, variant, quantity ->
                        runCatching {
                            val next = resolveScanEntries(collectionEntries, entry, language, variant, quantity)
                            checkpoint(); collectionEntries = next
                        }.onFailure { error = it.message }
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = collectionEntries.all { it.resolved }, onClick = { confirmingSave = false; saveDeck() }) { Text("Tout enregistrer") } },
        dismissButton = { TextButton(onClick = { confirmingSave = false }) { Text("Vérifier encore") } })
    if (preparing) DeckPreparationDialog(name, deck, owned, catalog, details, onDismiss = { preparing = false })
    if (showingInsights) DeckInsightsDialog(deck, owned, catalog, details, onDismiss = { showingInsights = false }, rules = rules, onReplace = { next -> checkpoint(); deck = next; draftStarted = true })
    if (naming && !restoring) AlertDialog(onDismissRequest = { naming = false }, title = { Text(if (newDeck && !leaderChosen) "Créer mon deck" else "Nom du deck") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Format Premier · un leader, une base, au moins 50 cartes et jusqu’à 10 en réserve.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(name, { if (name != it.take(120)) checkpoint(); name = it.take(120) }, label = { Text("Nom du deck") }, singleLine = true)
        } }, confirmButton = { TextButton(onClick = { draftStarted = true; naming = false }, enabled = name.isNotBlank()) { Text(if (newDeck && !leaderChosen) "Commencer" else "Valider") } },
        dismissButton = { TextButton(onClick = { naming = false; if (newDeck && !draftStarted) requestBack() }) { Text(if (newDeck && !draftStarted) "Quitter sans enregistrer" else "Annuler") } })
    if (resetting) AlertDialog(onDismissRequest = { resetting = false }, title = { Text("Recommencer ce deck ?") },
        text = { Text("Le nom est conservé. Choisis à nouveau un leader et une base, puis ajoute tes cartes. La collection reste inchangée. Enregistre pour remplacer le deck sauvegardé.") },
        confirmButton = { TextButton(onClick = { checkpoint(); collectionEntries = emptyList(); collectionBatchId = java.util.UUID.randomUUID().toString(); deck = initial.copy(deck = emptyList(), sideboard = emptyList()); leaderChosen = false; baseChosen = false; tab = "Leader"; query = ""; filters = DeckBrowserFilters(); catalogueOpen = true; resetting = false }) { Text("Recommencer") } },
        dismissButton = { TextButton(onClick = { resetting = false }) { Text("Annuler") } })
    if (filtersOpen) DeckFilterDialog(filters, catalog, details, onChange = { filters = it }, onDismiss = { filtersOpen = false }, identity = tab in listOf("Leader", "Base"))
    if (exportMenu) DeckExportDialog(deck, name, catalog, details, owned = owned, onDismiss = { exportMenu = false })
    if (showingReport) AlertDialog(onDismissRequest = { showingReport = false },
        title = { Text("Vérification et cartes manquantes") },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Text("Format Premier · ${rules.checkedAt.take(10)}", color = gold) }
                if (report.issues.isEmpty()) item { Text("Les règles de construction contrôlées sont respectées.") }
                items(report.issues) { Text(it, color = ListPalette.Danger) }
                item { Text("${report.needs.sumOf { it.missing }} exemplaire(s) à trouver", color = gold) }
                items(report.needs.filter { it.missing > 0 }, key = { it.id }) { need ->
                    Text("${need.name} : ${need.owned} possédée(s) / ${need.required} nécessaire(s) · manque ${need.missing}")
                }
                item { Text("Les langues, les variantes identifiées et les réimpressions sont cumulées. Les contraintes de construction imprimées sur certaines cartes restent à vérifier. La collection reste inchangée.") }
            }
        }, confirmButton = { TextButton(onClick = { showingReport = false }) { Text("Fermer") } })
    if (leaving) DeckExitDialog(onKeep = { keepForLater() }, onDiscard = { discardAndLeave() }, onContinue = { leaving = false })
    }
    }
}

@Composable
internal fun DeckExitDialog(onKeep: () -> Unit, onDiscard: () -> Unit, onContinue: () -> Unit) {
    Dialog(onDismissRequest = onContinue) {
        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
            // Keep text and actions in one bounded scroll area, including on a
            // landscape phone or with large system fonts.
            Column(Modifier.fillMaxWidth()
                .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.85f).dp)
                .verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Quitter le deck ?", style = MaterialTheme.typography.headlineSmall)
                Text("Tu peux reprendre plus tard avec un brouillon, ou abandonner les modifications. Les cartes scannées en attente ne seront pas ajoutées à ta collection.")
                Button(onClick = onKeep, modifier = Modifier.fillMaxWidth()) {
                    Text("Garder un brouillon")
                }
                OutlinedButton(onClick = onDiscard, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ListPalette.Danger)) {
                    Text("Quitter sans enregistrer")
                }
                TextButton(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                    Text("Continuer le deck")
                }
            }
        }
    }
}
