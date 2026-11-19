@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package fr.swucollection.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Full card artwork is the primary view of a deck, on phones and wide screens. */
@Composable
internal fun DeckSelectionPane(name: String, deck: PortableDeck, catalog: List<CatalogCard>, details: CardDetails,
    zone: DeckEditor.Zone, leaderChosen: Boolean, baseChosen: Boolean, modifier: Modifier,
    onZone: (DeckEditor.Zone) -> Unit, onQuantity: (String, Int, DeckEditor.Zone) -> Unit,
    onPreview: (CatalogCard, List<CatalogCard>) -> Unit, onIdentity: (String) -> Unit = {},
    onAdd: (() -> Unit)? = null, ownedByFamily: Map<String, Int> = emptyMap()) {
    val byId = remember(catalog) { catalog.associateBy(DeckConstruction::reference) }
    val selected = (if (zone == DeckEditor.Zone.SIDEBOARD) deck.sideboard else deck.deck)
        .sortedWith(compareBy({ byId[it.cardId]?.let { card -> details.get(card)?.cost } ?: Int.MAX_VALUE },
            { byId[it.cardId]?.name ?: it.cardId }))
    var grouping by rememberSaveable { mutableStateOf("Cartes") }
    var displayMenu by remember { mutableStateOf(false) }
    val groups = remember(selected, grouping, details, byId) { if(grouping == "Cartes") mapOf("" to selected) else selected.groupBy { DesignDeckPlan.group(it, byId, details, grouping) } }
    var managing by remember(zone) { mutableStateOf<String?>(null) }
    Card(modifier.testTag("swu_deck_selection_pane"), colors = CardDefaults.cardColors(containerColor = ListPalette.Background)) {
        Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (name.isNotBlank()) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                onAdd?.let { TextButton(onClick = it) { Text("Ajouter des cartes") } }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(zone == DeckEditor.Zone.MAIN, { onZone(DeckEditor.Zone.MAIN) }, label = { Text("Deck · ${deck.deck.sumOf { it.count }}/50") })
                FilterChip(zone == DeckEditor.Zone.SIDEBOARD, { onZone(DeckEditor.Zone.SIDEBOARD) }, label = { Text("Réserve · ${deck.sideboard.sumOf { it.count }}/10") })
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (name.isBlank()) onAdd?.let { TextButton(onClick = it) { Text("Ajouter des cartes") } }
            Box {
                TextButton(onClick = { displayMenu = true }) {
                    Text("Affichage : ${if (grouping == "Cartes") "Grille" else "Table · $grouping"} ▾")
                }
                DropdownMenu(expanded = displayMenu, onDismissRequest = { displayMenu = false }) {
                    listOf("Cartes", "Coût", "Type").forEach { mode ->
                        DropdownMenuItem(text = { Text(if (mode == "Cartes") "Grille" else "Table · $mode") },
                            onClick = { grouping = mode; displayMenu = false })
                    }
                }
            }
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            LazyVerticalGrid(columns = if (maxWidth < 480.dp) GridCells.Fixed(3) else GridCells.Adaptive(100.dp), modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 12.dp)) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "identities") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DeckIdentityCard("Leader", if (leaderChosen) byId[deck.leader] else null, "À choisir", Modifier.weight(1f),
                            onPreview = { if (leaderChosen) byId[deck.leader]?.let { onPreview(it, listOf(it)) } else onIdentity("Leader") },
                            onChange = { onIdentity("Leader") })
                        DeckIdentityCard("Base", if (baseChosen) byId[deck.base] else null, "À choisir", Modifier.weight(1f),
                            onPreview = { if (baseChosen) byId[deck.base]?.let { onPreview(it, listOf(it)) } else onIdentity("Base") },
                            onChange = { onIdentity("Base") })
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }, key = "help") {
                    Text("Cartes entières · Tri par coût · Appui long pour modifier", style = MaterialTheme.typography.labelSmall,
                        color = ListPalette.Secondary)
                }
                if (selected.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                    Text("Ton deck est encore vide. Touche Ajouter des cartes pour commencer.", Modifier.padding(12.dp))
                }
                groups.forEach { (heading, entries) ->
                if(heading.isNotBlank()) item(span = { GridItemSpan(maxLineSpan) }, key = "heading:$heading") { Text("$heading · ${entries.sumOf { it.count }}", color = ListPalette.Secondary, style = MaterialTheme.typography.titleSmall) }
                items(entries, key = { it.cardId }) { entry ->
                    val card = byId[entry.cardId]
                    Column(Modifier.fillMaxWidth()) {
                        Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, ListPalette.Gold.copy(alpha = .5f)),
                            modifier = Modifier.fillMaxWidth().combinedClickable(
                                onClick = { card?.let { onPreview(it, selected.mapNotNull { row -> byId[row.cardId] }) } },
                                onLongClickLabel = "Modifier la quantité", onLongClick = { managing = entry.cardId })) {
                            Box(Modifier.fillMaxWidth().aspectRatio(5f / 7f).testTag("swu_deck_full_card")) {
                                CardArtwork(card, Modifier.fillMaxSize())
                                Surface(Modifier.align(Alignment.TopEnd).padding(4.dp), color = ListPalette.Gold,
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)) {
                                    Text("×${entry.count}", Modifier.padding(horizontal = 6.dp, vertical = 3.dp), color = ListPalette.Background)
                                }
                            }
                        }
                        val family = card?.let(details::family)
                        val required = (deck.deck + deck.sideboard).filter { row ->
                            byId[row.cardId]?.let(details::family) == family && family != null
                        }.sumOf { it.count }
                        if (family != null && required > (ownedByFamily[family] ?: 0))
                            Text("À compléter", color = ListPalette.Gold, style = MaterialTheme.typography.labelSmall)
                        if (card == null) Text(entry.cardId, style = MaterialTheme.typography.labelSmall)
                    }
                }
                }
            }
            }
        }
    }
    managing?.let { id -> selected.firstOrNull { it.cardId == id }?.let { entry ->
        val card = byId[id]
        val count = (deck.deck + deck.sideboard).filter { row ->
            if (card == null) row.cardId == id else byId[row.cardId]?.let(details::family) == details.family(card)
        }.sumOf { it.count }
        val canAdd = card != null && count < 3 && (zone == DeckEditor.Zone.MAIN || deck.sideboard.sumOf { it.count } < 10)
        CardQuantityDialog(card?.name ?: id, entry.count, { managing = null },
            { onQuantity(id, it, zone) }, maximum = if (canAdd) entry.count + 1 else entry.count)
    } }
}
