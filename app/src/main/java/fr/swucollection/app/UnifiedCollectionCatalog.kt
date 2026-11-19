@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package fr.swucollection.app

import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clipToBounds
import kotlinx.coroutines.launch
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

internal object UnifiedCollection {
    fun section(section: String) = if (section == "Extensions") "Collection" else section
    fun visibleHome(order: List<String>) = order.filter { it != "EXTENSIONS" }
    fun catalogCards(catalog: List<CatalogCard>, owned: List<CollectionCard>, scope: String,
        set: String, language: String, query: String, details: CardDetails): List<CatalogCard> {
        val keys = owned.filter { it.quantity > 0 && (language == "Toutes" || it.language == language) }
            .map { MarketPricing.key(it.setCode, it.number) }.toSet()
        val search = DeckBrowser.searchKey(query)
        return catalog.filter { card ->
            (set == "Toutes" || card.setCode.equals(set, true)) &&
            (scope != "Manquantes" || MarketPricing.key(card.setCode, card.number) !in keys) &&
            (query.isBlank() || DeckBrowser.searchKey(card.name + " " + card.setCode + " " + card.number + " " + (details.get(card)?.englishName ?: "")).contains(search))
        }
    }
}

/** Catalogue and owned stock share the same screen, extension filter and action menu. */
@Composable
internal fun UnifiedCollectionCatalog(catalog: List<CatalogCard>, owned: List<CollectionCard>,
    scope: String, set: String, language: String, query: String, view: String, details: CardDetails,
    modifier: Modifier = Modifier, onPreview: (CatalogCard, List<CatalogCard>) -> Unit,
    onActions: (CatalogCard) -> Unit, dimMissing: Boolean = true, header: (@Composable () -> Unit)? = null, sets: List<CatalogSet> = emptyList(),
    favoritesOnlyOverride: Boolean? = null, onFavoritesOnlyChange: ((Boolean) -> Unit)? = null,
    cardScaleOverride: Float? = null, onCardScaleChange: ((Float) -> Unit)? = null,
    silhouettesOverride: Boolean? = null, onSilhouettesChange: ((Boolean) -> Unit)? = null,
    showDisplayOptions: Boolean = true, showFavoritesControl: Boolean = true,
    onQuickChange: ((CatalogCard, String, String, Int) -> Unit)? = null, favoriteRevision: Int = 0, priceRefreshKey: Any? = null) {
    val favoriteContext = LocalContext.current.applicationContext
    val favoriteDatabase = remember(favoriteContext) { CollectionDatabase(favoriteContext) }
    DisposableEffect(favoriteDatabase) { onDispose { favoriteDatabase.close() } }
    var favorites by remember(favoriteRevision) { mutableStateOf(favoriteDatabase.favoriteCardKeys()) }
    var localFavoritesOnly by rememberSaveable { mutableStateOf(false) }
    val favoritesOnly = favoritesOnlyOverride ?: localFavoritesOnly
    fun changeFavoritesOnly(value: Boolean) { onFavoritesOnlyChange?.invoke(value) ?: run { localFavoritesOnly = value } }
    var favoriteError by remember { mutableStateOf<String?>(null) }
    @Composable fun favoriteButton(card: CatalogCard) {
        val key = MarketPricing.key(card.setCode, card.number)
        IconToggleButton(modifier = Modifier.size(40.dp), checked = key in favorites, onCheckedChange = { selected ->
            runCatching { favoriteDatabase.setFavoriteCard(card, selected) }
                .onSuccess { favorites = favoriteDatabase.favoriteCardKeys(); favoriteError = null }
                .onFailure { favoriteError = "Impossible de modifier les favoris. Réessaie." }
        }) { Icon(if (key in favorites) Icons.Default.Star else Icons.Default.StarBorder,
            if (key in favorites) "Retirer ${card.name} des favoris" else "Ajouter ${card.name} aux favoris", tint = ListPalette.Gold) }
    }
    val visible = remember(catalog, owned, scope, set, language, query, details, favoritesOnly, favorites) {
        UnifiedCollection.catalogCards(catalog, owned, scope, set, language, query, details).filter { !favoritesOnly || MarketPricing.key(it.setCode, it.number) in favorites }
    }
    val quantities = remember(owned, language) { owned.filter { it.quantity > 0 && (language == "Toutes" || it.language == language) }
        .groupBy { MarketPricing.key(it.setCode, it.number) }.mapValues { (_, rows) -> rows.sumOf { it.quantity } } }
    var localCardScale by rememberSaveable { mutableFloatStateOf(1f) }
    val cardScale = cardScaleOverride ?: localCardScale
    val currentScale by rememberUpdatedState(cardScale)
    fun changeCardScale(value: Float) { onCardScaleChange?.invoke(value) ?: run { localCardScale = value } }
    val totalsBySet = remember(catalog) { catalog.groupingBy { it.setCode }.eachCount() }
    val collectedBySet = remember(catalog, quantities) {
        catalog.filter { (quantities[MarketPricing.key(it.setCode, it.number)] ?: 0) > 0 }.groupingBy { it.setCode }.eachCount()
    }
    var localSilhouettes by rememberSaveable { mutableStateOf(false) }
    val silhouettes = silhouettesOverride ?: localSilhouettes
    fun changeSilhouettes(value: Boolean) { onSilhouettesChange?.invoke(value) ?: run { localSilhouettes = value } }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val gridState = rememberLazyGridState()
    val coroutine = rememberCoroutineScope()
    val groups = remember(visible) { visible.groupBy { it.setCode } }
    val compactSetHeader = LocalConfiguration.current.screenHeightDp < 480
    @Composable fun sectionTitle(code: String) {
        val total = totalsBySet[code] ?: 0
        val collected = collectedBySet[code] ?: 0
        Box(Modifier.fillMaxWidth().padding(top = 20.dp)) {
        Surface(modifier = Modifier.testTag("extension_frame_$code"), color = ListPalette.Raised,
            border = androidx.compose.foundation.BorderStroke(1.dp, ListPalette.Gold.copy(alpha = .25f)), shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Spacer(Modifier.weight(1f).height(44.dp))
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${if (total > 0) collected * 100 / total else 0} %", color = ListPalette.Gold, style = MaterialTheme.typography.titleLarge)
                        Text("$collected / $total", style = MaterialTheme.typography.labelMedium)
                    }
                }
                ExtensionProgressPanel(code, catalog, quantities, compactTitle = sets.firstOrNull { it.code == code }?.name ?: code)
            }
        }
        SetLogo(code, Modifier.align(Alignment.TopStart).padding(start = 10.dp).offset(y = (-20).dp)
            .fillMaxWidth(.68f).height(76.dp))
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (header != null) Column(Modifier.fillMaxWidth().heightIn(max = if (compactSetHeader) 64.dp else 160.dp)
            .verticalScroll(rememberScrollState())) { header() }
        favoriteError?.let { Text(it, color = ListPalette.Danger) }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            if (showFavoritesControl) FilterChip(favoritesOnly, onClick = { changeFavoritesOnly(!favoritesOnly) }, label = { Text("★ Favoris") })
            Text("${visible.size} cartes", Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.labelMedium)
            if (showDisplayOptions) {
                var displayOpen by remember { mutableStateOf(false) }
                Box {
                    TextButton(onClick = { displayOpen = true }) { Text("Affichage") }
                    if (displayOpen) AlertDialog(onDismissRequest = { displayOpen = false }, title = { Text("Affichage") },
                        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                            CollectionCardDisplayOptions(cardScale, ::changeCardScale, silhouettes, ::changeSilhouettes,
                                showSize = view != "Liste", showSilhouettes = dimMissing && scope !in listOf("Possédées", "Doublons"))
                        } }, confirmButton = { TextButton(onClick = { displayOpen = false }) { Text("Voir les cartes") } })
                    }
            }
            if (compactSetHeader && groups.size > 1) groups.keys.forEach { code ->
                TextButton(onClick = {
                    val index = groups.entries.takeWhile { it.key != code }.sumOf { 1 + it.value.size }
                    coroutine.launch { if (view == "Liste") listState.animateScrollToItem(index) else gridState.animateScrollToItem(index) }
                }) { Text(code) }
            }
        }
        if(groups.size > 1 && !compactSetHeader) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            groups.keys.forEach { code -> TextButton(onClick = {
                val prior = groups.entries.takeWhile { it.key != code }
                val index = prior.sumOf { 1 + it.value.size }
                coroutine.launch { if(view == "Liste") listState.animateScrollToItem(index) else gridState.animateScrollToItem(index) }
            }) { Text(code) } }
        }
        if (view == "Liste") LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (visible.isEmpty()) item(key = "empty-catalogue") {
                CollectorEmptyState("Aucune carte trouvée", "Essaie un autre mot ou retire un filtre pour élargir ta recherche.")
            }
            groups.forEach { (code, group) ->
            item(key = "set-$code") { sectionTitle(code) }
            items(group, key = { MarketPricing.key(it.setCode, it.number) }) { card ->
                val quantity = quantities[MarketPricing.key(card.setCode, card.number)] ?: 0
                Surface(color = ListPalette.Background, modifier = Modifier.fillMaxWidth().combinedClickable(
                    onClick = { onPreview(card, visible) }, onLongClickLabel = "Gérer cette carte", onLongClick = { onActions(card) })) {
                    Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        if(dimMissing && silhouettes && quantity == 0) MissingSilhouette(card, Modifier.size(52.dp, 74.dp)) else CardArtwork(card, Modifier.size(52.dp, 74.dp), language = language.takeIf { it in CardLanguages.supported } ?: card.language, grayscale = dimMissing && quantity == 0)
                        Column(Modifier.weight(1f)) {
                            Text(card.name)
                            Text("${card.setCode} · ${card.number}", style = MaterialTheme.typography.labelSmall)
                            Text(if (quantity > 0) "$quantity exemplaire(s)" else "Manquante", color = ListPalette.Gold)
                        }
                        favoriteButton(card)
                        IconButton(onClick = { onActions(card) }) { Icon(Icons.Default.Add, "Ajouter ou gérer ${card.name}") }
                    }
                }
            }
            }
        } else BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val miniatures = view == "Miniatures"
            val columns = ((maxWidth.value + 6f) / (110f * cardScale)).toInt().coerceAtLeast(2)
            LazyVerticalGrid(state = gridState, columns = if (miniatures) GridCells.Fixed(columns)
                else if (maxWidth < 600.dp && cardScale in .95f..1.05f) GridCells.Fixed(2) else GridCells.Adaptive((135f * cardScale).dp),
                modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.count { it.pressed } >= 2) {
                                changeCardScale((currentScale * event.calculateZoom()).coerceIn(.8f, 1.5f))
                                event.changes.forEach { it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }.testTag("swu_unified_catalog"), horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
                if (visible.isEmpty()) item(key = "empty-catalogue", span = { GridItemSpan(maxLineSpan) }) {
                    CollectorEmptyState("Aucune carte trouvée", "Essaie un autre mot ou retire un filtre pour élargir ta recherche.")
                }
                groups.forEach { (code, group) ->
                item(key = "set-$code", span = { GridItemSpan(maxLineSpan) }) { sectionTitle(code) }
                gridItems(group, key = { MarketPricing.key(it.setCode, it.number) }) { card ->
                    val quantity = quantities[MarketPricing.key(card.setCode, card.number)] ?: 0
                                val prefs = remember { favoriteContext.getSharedPreferences("quick_collection_choices", 0) }
                                var quickLanguage by rememberSaveable(card.setCode, card.number, language) {
                                    mutableStateOf(language.takeIf { it in CardLanguages.supported }
                                        ?: prefs.getString("language", "FR")!!.takeIf { it in CardLanguages.supported } ?: "FR")
                                }
                                val finishes = ScanVariants.detect(card).choices
                                var quickFinish by rememberSaveable(card.setCode, card.number) {
                                    mutableStateOf(prefs.getString("finish", "Normale")!!.takeIf { it in finishes }
                                        ?: ScanVariants.detect(card).variant ?: finishes.firstOrNull().orEmpty())
                                }
                    Column(Modifier.fillMaxWidth().combinedClickable(onClick = { onPreview(card, visible) },
                        onLongClickLabel = "Gérer cette carte", onLongClick = { onActions(card) })
                        .semantics { contentDescription = "${card.name}, $quantity exemplaire(s)" }) {
                        if(dimMissing && silhouettes && quantity == 0) MissingSilhouette(card, Modifier.fillMaxWidth().aspectRatio(if (DeckConstruction.type(card) in listOf("leader", "chef", "base")) 1.4f else 5f / 7f)) else CardArtwork(card, Modifier.fillMaxWidth().aspectRatio(if (DeckConstruction.type(card) in listOf("leader", "chef", "base")) 1.4f else 5f / 7f).testTag("catalog_artwork_${MarketPricing.key(card.setCode, card.number)}"), language = if (!miniatures && onQuickChange != null) quickLanguage else language.takeIf { it in CardLanguages.supported } ?: card.language,
                            variant = if (!miniatures && onQuickChange != null) quickFinish else null, showLanguageLabel = false, grayscale = dimMissing && quantity == 0)
                        if (miniatures) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (quantity > 0) "×$quantity" else "Manquante", Modifier.weight(1f), color = ListPalette.Gold, style = MaterialTheme.typography.labelSmall)
                            favoriteButton(card)
                        }
                        if (!miniatures) {
                            Text(card.name, Modifier.fillMaxWidth().heightIn(min = 32.dp), maxLines = 2, style = MaterialTheme.typography.labelMedium)
                            if (onQuickChange != null) {
                                // Wrap only on narrow windows or large text; touch areas remain 48 dp.
                                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                                    itemVerticalAlignment = Alignment.CenterVertically) {
                                    QuickVariantSelectors(card, quickLanguage, quickFinish,
                                        { quickLanguage = it; prefs.edit().putString("language", it).apply() },
                                        { quickFinish = it; prefs.edit().putString("finish", it).apply() }, compact = true)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CompactCardQuote(card, quickFinish, refreshKey = priceRefreshKey)
                                        favoriteButton(card)
                                    }
                                }
                                val exactQuantity = quickStockQuantity(owned, card, quickLanguage, quickFinish)
                                QuickStockBar(exactQuantity, quickLanguage in CardLanguages.supported && quickFinish in finishes,
                                    { delta -> onQuickChange(card, quickLanguage, quickFinish, delta) })
                            } else {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(if (quantity > 0) "×$quantity" else "Manquante", Modifier.weight(1f), color = ListPalette.Gold)
                                    favoriteButton(card)
                                    IconButton(onClick = { onActions(card) }) { Icon(Icons.Default.Add, "Ajouter ou gérer ${card.name}") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
internal fun CollectionScopeMenu(selected: String, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text("Afficher : ${if (selected == "Toutes les cartes") "Toutes" else selected} ▾") }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            listOf("Toutes les cartes", "Possédées", "Manquantes").forEach { scope ->
                DropdownMenuItem(text = { Text(scope) }, onClick = { onChange(scope); expanded = false })
            }
        }
    }
}

@Composable
internal fun CollectionCardDisplayOptions(scale: Float, onScale: (Float) -> Unit, silhouettes: Boolean,
    onSilhouettes: (Boolean) -> Unit, showSize: Boolean = true, showSilhouettes: Boolean = true) {
    if (showSize) {
        Text("Taille des cartes", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Grandes cartes" to 1.5f, "Équilibré" to 1f, "Compact" to .8f).forEach { (label, value) ->
                FilterChip(kotlin.math.abs(scale - value) < .05f, onClick = { onScale(value) }, label = { Text(label) })
            }
        }
    }
    if (showSilhouettes) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Silhouettes des manquantes", Modifier.weight(1f))
        Switch(silhouettes, onCheckedChange = onSilhouettes)
    }
}

@Composable
internal fun MissingCardDimmingToggle(selected: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(value = selected, role = Role.Switch, onValueChange = onChange)
        .padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Griser les manquantes", Modifier.weight(1f))
        Switch(checked = selected, onCheckedChange = null)
    }
}
