@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package fr.swucollection.app

import androidx.compose.foundation.combinedClickable
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
    onActions: (CatalogCard) -> Unit, dimMissing: Boolean = true, header: (@Composable () -> Unit)? = null) {
    val visible = remember(catalog, owned, scope, set, language, query, details) {
        UnifiedCollection.catalogCards(catalog, owned, scope, set, language, query, details)
    }
    val quantities = remember(owned, language) { owned.filter { it.quantity > 0 && (language == "Toutes" || it.language == language) }
        .groupBy { MarketPricing.key(it.setCode, it.number) }.mapValues { (_, rows) -> rows.sumOf { it.quantity } } }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("${visible.size} référence(s) · $scope", style = MaterialTheme.typography.labelMedium)
        if (visible.isEmpty()) Text("Aucune carte ne correspond à ces filtres.")
        if (view == "Liste") LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(visible, key = { MarketPricing.key(it.setCode, it.number) }) { card ->
                val quantity = quantities[MarketPricing.key(card.setCode, card.number)] ?: 0
                Surface(color = ListPalette.Background, modifier = Modifier.fillMaxWidth().combinedClickable(
                    onClick = { onPreview(card, visible) }, onLongClickLabel = "Gérer cette carte", onLongClick = { onActions(card) })) {
                    Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        CardArtwork(card, Modifier.size(52.dp, 74.dp), language = language.takeIf { it in CardLanguages.supported } ?: card.language, grayscale = dimMissing && quantity == 0)
                        Column(Modifier.weight(1f)) {
                            Text(card.name)
                            Text("${card.setCode} · ${card.number}", style = MaterialTheme.typography.labelSmall)
                            Text(if (quantity > 0) "$quantity exemplaire(s)" else "Manquante", color = ListPalette.Gold)
                        }
                        IconButton(onClick = { onActions(card) }) { Icon(Icons.Default.Add, "Ajouter ou gérer ${card.name}") }
                    }
                }
            }
        } else BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val miniatures = view == "Miniatures"
            val columns = ((maxWidth.value + 6f) / 110f).toInt().coerceAtLeast(3)
            LazyVerticalGrid(columns = if (miniatures) GridCells.Fixed(columns) else GridCells.Adaptive(135.dp),
                modifier = Modifier.fillMaxSize().testTag("swu_unified_catalog"), horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
                if (header != null) item(key = "catalogue-controls", span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) { header() }
                }
                gridItems(visible, key = { MarketPricing.key(it.setCode, it.number) }) { card ->
                    val quantity = quantities[MarketPricing.key(card.setCode, card.number)] ?: 0
                    Column(Modifier.fillMaxWidth().combinedClickable(onClick = { onPreview(card, visible) },
                        onLongClickLabel = "Gérer cette carte", onLongClick = { onActions(card) })
                        .semantics { contentDescription = "${card.name}, $quantity exemplaire(s)" }) {
                        CardArtwork(card, Modifier.fillMaxWidth().aspectRatio(5f / 7f), language = language.takeIf { it in CardLanguages.supported } ?: card.language,
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

@Composable
internal fun MissingCardDimmingToggle(selected: Boolean, onChange: (Boolean) -> Unit) {
    FilterChip(selected = selected, onClick = { onChange(!selected) }, label = { Text("Griser les non possédées") })
}
