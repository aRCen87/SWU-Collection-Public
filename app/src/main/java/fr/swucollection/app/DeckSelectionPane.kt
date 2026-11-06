package fr.swucollection.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun DeckSelectionPane(name: String, deck: PortableDeck, catalog: List<CatalogCard>, details: CardDetails,
    zone: DeckEditor.Zone, leaderChosen: Boolean, baseChosen: Boolean, modifier: Modifier,
    onZone: (DeckEditor.Zone) -> Unit, onQuantity: (String, Int, DeckEditor.Zone) -> Unit,
    onPreview: (CatalogCard, List<CatalogCard>) -> Unit) {
    val byId = catalog.associateBy { DeckConstruction.reference(it) }
    val selected = if (zone == DeckEditor.Zone.SIDEBOARD) deck.sideboard else deck.deck
    Card(modifier.testTag("swu_deck_selection_pane")) { Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(name.ifBlank { "Mon deck" }, style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(zone == DeckEditor.Zone.MAIN, { onZone(DeckEditor.Zone.MAIN) }, label = { Text("Deck · ${deck.deck.sumOf { it.count }}") })
            FilterChip(zone == DeckEditor.Zone.SIDEBOARD, { onZone(DeckEditor.Zone.SIDEBOARD) }, label = { Text("Réserve · ${deck.sideboard.sumOf { it.count }}") })
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Text("Leader : " + if (leaderChosen) byId[deck.leader]?.name ?: deck.leader else "À choisir") }
            item { Text("Base : " + if (baseChosen) byId[deck.base]?.name ?: deck.base else "À choisir") }
            if (selected.isEmpty()) item { Text("Ajoute les cartes depuis le catalogue à gauche.") }
            items(selected, key = { it.cardId }) { entry ->
                val card = byId[entry.cardId]
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (card != null) TextButton(onClick = { onPreview(card, selected.mapNotNull { byId[it.cardId] }) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(0.dp)) {
                        CardArtwork(card, Modifier.width(42.dp).height(59.dp))
                        Spacer(Modifier.width(6.dp)); Text(card.name, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    } else Text(entry.cardId, Modifier.weight(1f))
                    TextButton(onClick = { onQuantity(entry.cardId, entry.count - 1, zone) }, contentPadding = PaddingValues(2.dp)) { Text("−") }
                    Text("${entry.count}")
                    val count = (deck.deck + deck.sideboard).filter { other ->
                        if (card == null) other.cardId == entry.cardId else byId[other.cardId]?.let { details.family(it) } == details.family(card)
                    }.sumOf { it.count }
                    TextButton(onClick = { onQuantity(entry.cardId, entry.count + 1, zone) }, enabled = card != null && count < 3 && (zone == DeckEditor.Zone.MAIN || deck.sideboard.sumOf { it.count } < 10), contentPadding = PaddingValues(2.dp)) { Text("+") }
                }
            }
        }
    } }
}
