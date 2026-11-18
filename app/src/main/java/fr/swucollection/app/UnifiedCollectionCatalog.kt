@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package fr.swucollection.app

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
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
    onActions: (CatalogCard) -> Unit, dimMissing: Boolean = true, header: (@Composable () -> Unit)? = null, sets: List<CatalogSet> = emptyList()) {
    val visible = remember(catalog, owned, scope, set, language, query, details) {
        UnifiedCollection.catalogCards(catalog, owned, scope, set, language, query, details)
    }
    val quantities = remember(owned, language) { owned.filter { it.quantity > 0 && (language == "Toutes" || it.language == language) }
        .groupBy { MarketPricing.key(it.setCode, it.number) }.mapValues { (_, rows) -> rows.sumOf { it.quantity } } }
    var cardScale by rememberSaveable { mutableFloatStateOf(1f) }
    val totalsBySet = remember(catalog) { catalog.groupingBy { it.setCode }.eachCount() }
    val collectedBySet = remember(catalog, quantities) {
        catalog.filter { (quantities[MarketPricing.key(it.setCode, it.number)] ?: 0) > 0 }.groupingBy { it.setCode }.eachCount()
    }
    var silhouettes by rememberSaveable { mutableStateOf(false) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val gridState = rememberLazyGridState()
    val coroutine = rememberCoroutineScope()
    val groups = remember(visible) { visible.groupBy { it.setCode } }
    @Composable fun sectionTitle(code: String) {
        val total = totalsBySet[code] ?: 0
        val collected = collectedBySet[code] ?: 0
        Surface(color = ListPalette.Raised, shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.fillMaxWidth().height(72.dp).clipToBounds()) {
                    DeckCoverArtwork(catalog.firstOrNull { it.setCode == code && DeckConstruction.type(it) in listOf("leader", "chef") } ?: catalog.firstOrNull { it.setCode == code }, .35f, Modifier.fillMaxSize())
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SetLogo(code)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Surface(color = ListPalette.Background, shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)) {
                        Text(code, Modifier.padding(8.dp), color = ListPalette.Gold, style = MaterialTheme.typography.titleSmall)
                    }
                    Text(sets.firstOrNull { it.code == code }?.name ?: code, style = MaterialTheme.typography.titleSmall)
                    Text("$collected / $total références possédées", style = MaterialTheme.typography.bodySmall)
                    }
                }
                LinearProgressIndicator(progress = { if (total > 0) collected.toFloat() / total else 0f }, modifier = Modifier.fillMaxWidth())
                ExtensionProgressPanel(code, catalog, quantities)
            }
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${visible.size} référence(s) · $scope", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
            if(scope != "Possédées") TextButton(onClick = { silhouettes = !silhouettes }) { Text(if(silhouettes) "Visuels" else "Silhouettes") }
        }
        if(groups.size > 1) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            groups.keys.forEach { code -> TextButton(onClick = {
                val prior = groups.entries.takeWhile { it.key != code }
                val index = (if(header != null) 1 else 0) + prior.sumOf { 1 + it.value.size }
                coroutine.launch { if(view == "Liste") listState.animateScrollToItem(index) else gridState.animateScrollToItem(index) }
            }) { Text(code) } }
        }
        if (view == "Liste") LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (header != null) item(key = "catalogue-controls") { header() }
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
                        if(silhouettes && quantity == 0) MissingSilhouette(card, Modifier.size(52.dp, 74.dp)) else CardArtwork(card, Modifier.size(52.dp, 74.dp), language = language.takeIf { it in CardLanguages.supported } ?: card.language, grayscale = dimMissing && quantity == 0)
                        Column(Modifier.weight(1f)) {
                            Text(card.name)
                            Text("${card.setCode} · ${card.number}", style = MaterialTheme.typography.labelSmall)
                            Text(if (quantity > 0) "$quantity exemplaire(s)" else "Manquante", color = ListPalette.Gold)
                        }
                        IconButton(onClick = { onActions(card) }) { Icon(Icons.Default.Add, "Ajouter ou gérer ${card.name}") }
                    }
                }
            }
            }
        } else BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val miniatures = view == "Miniatures"
            val columns = ((maxWidth.value + 6f) / (110f * cardScale)).toInt().coerceAtLeast(2)
            LazyVerticalGrid(state = gridState, columns = if (miniatures) GridCells.Fixed(columns) else GridCells.Adaptive((135f * cardScale).dp),
                modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.count { it.pressed } >= 2) {
                                cardScale = (cardScale * event.calculateZoom()).coerceIn(.8f, 1.7f)
                                event.changes.forEach { it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }.testTag("swu_unified_catalog"), horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
                if (header != null) item(key = "catalogue-controls", span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) { header() }
                }
                if (visible.isEmpty()) item(key = "empty-catalogue", span = { GridItemSpan(maxLineSpan) }) {
                    CollectorEmptyState("Aucune carte trouvée", "Essaie un autre mot ou retire un filtre pour élargir ta recherche.")
                }
                groups.forEach { (code, group) ->
                item(key = "set-$code", span = { GridItemSpan(maxLineSpan) }) { sectionTitle(code) }
                gridItems(group, key = { MarketPricing.key(it.setCode, it.number) }) { card ->
                    val quantity = quantities[MarketPricing.key(card.setCode, card.number)] ?: 0
                    Column(Modifier.fillMaxWidth().combinedClickable(onClick = { onPreview(card, visible) },
                        onLongClickLabel = "Gérer cette carte", onLongClick = { onActions(card) })
                        .semantics { contentDescription = "${card.name}, $quantity exemplaire(s)" }) {
                        if(silhouettes && quantity == 0) MissingSilhouette(card, Modifier.fillMaxWidth().aspectRatio(5f / 7f)) else CardArtwork(card, Modifier.fillMaxWidth().aspectRatio(5f / 7f), language = language.takeIf { it in CardLanguages.supported } ?: card.language,
                            showLanguageLabel = !miniatures, grayscale = dimMissing && quantity == 0)
                        if (!miniatures) {
                            Text(card.name, maxLines = 2, style = MaterialTheme.typography.labelMedium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(if (quantity > 0) "×$quantity possédée(s)" else "Manquante", Modifier.weight(1f), color = ListPalette.Gold, style = MaterialTheme.typography.labelSmall)
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

@Composable
internal fun MissingCardDimmingToggle(selected: Boolean, onChange: (Boolean) -> Unit) {
    FilterChip(selected = selected, onClick = { onChange(!selected) }, label = { Text("Griser les non possédées") })
}
