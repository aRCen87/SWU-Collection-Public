package fr.swucollection.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp

@Composable
internal fun PinnedDecksPanel(decks: List<DeckStore.SavedDeck>, owned: List<CollectionCard>, catalog: List<CatalogCard>, details: CardDetails, onOpen: (DeckStore.SavedDeck) -> Unit) {
    if (decks.isEmpty()) return
    val byId = remember(catalog) { catalog.associateBy(DeckConstruction::reference) }
    Text("Mes decks favoris", color = ListPalette.Gold, style = MaterialTheme.typography.titleMedium)
    decks.forEach { saved ->
        val missing = remember(saved, owned, catalog, details) { runCatching { DeckInsights.missingPrints(saved.deck, owned, catalog, details).sumOf { it.quantity } } }
        OutlinedCard(onClick = { onOpen(saved) }, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CardArtwork(byId[saved.deck.leader], Modifier.width(80.dp).height(56.dp))
                Column(Modifier.weight(1f)) {
                    Text(saved.name, color = ListPalette.Gold)
                    Text(missing.getOrNull()?.let { if (it == 0) "Toutes les cartes sont disponibles" else "$it exemplaire(s) manquant(s)" } ?: "Références à vérifier", style = MaterialTheme.typography.bodySmall)
                }
                Text("›", color = ListPalette.Gold)
            }
        }
    }
}

@Composable
internal fun CardDeckUsagePanel(card: CatalogCard, decks: List<DeckStore.SavedDeck>, catalog: List<CatalogCard>, details: CardDetails, enabled: Boolean, onAdd: () -> Unit, onOpen: (DeckStore.SavedDeck) -> Unit) {
    val usages = remember(card, decks, catalog, details) { DeckCompanion.usages(card, decks, catalog, details) }
    val supported = DeckConstruction.isDraw(card) || DeckConstruction.type(card) in listOf("leader", "chef", "base")
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Utilisée dans mes decks", color = ListPalette.Gold)
            if (usages.isEmpty()) Text("Aucun deck pour le moment.", style = MaterialTheme.typography.bodySmall)
            usages.forEach { (saved, count) -> TextButton(onClick = { onOpen(saved) }, modifier = Modifier.fillMaxWidth()) { Text("${saved.name} · ×$count ›") } }
            Button(onClick = onAdd, enabled = enabled && supported, modifier = Modifier.fillMaxWidth()) { Text("Ajouter à un deck") }
            if (!supported) Text("Ce type de carte ne peut pas être ajouté à un deck.", style = MaterialTheme.typography.bodySmall)
            else if (!enabled) Text("Utilise les quantités du constructeur pour modifier le deck ouvert.", style = MaterialTheme.typography.bodySmall)
            Text("Les versions et réimpressions reconnues sont comptées ensemble.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun AddCardToDeckDialog(card: CatalogCard, decks: List<DeckStore.SavedDeck>, onDismiss: () -> Unit, onAdd: (DeckStore.SavedDeck, DeckEditor.Zone) -> String?, onCreate: () -> Unit) {
    var selected by remember { mutableStateOf<DeckStore.SavedDeck?>(null) }
    var reserve by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val type = DeckConstruction.type(card)
    val identity = type in listOf("leader", "chef", "base")
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Ajouter à un deck") }, text = {
        Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(card.name, color = ListPalette.Gold)
            if (identity) Text("Le ${if (type == "base") "choix de base" else "leader"} du deck choisi sera remplacé après confirmation.")
            if (decks.isEmpty()) Text("Crée d’abord ton premier deck.")
            if (!identity) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!reserve, onClick = { reserve = false }, label = { Text("Deck principal") })
                FilterChip(reserve, onClick = { reserve = true }, label = { Text("Réserve") })
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 260.dp)) {
                items(decks, key = { it.id }) { saved ->
                    FilterChip(selected?.id == saved.id, onClick = { selected = saved; error = null }, label = { Text(saved.name) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp))
                }
            }
            if (decks.isNotEmpty()) TextButton(onClick = onCreate) { Text("Créer un nouveau deck avec cette carte") }
            error?.let { Text(it, color = ListPalette.Danger) }
        }
    }, confirmButton = {
        if (decks.isEmpty()) TextButton(onClick = onCreate) { Text("Créer un deck") }
        else TextButton(enabled = selected != null, onClick = {
            selected?.let { saved ->
                error = onAdd(saved, if (reserve) DeckEditor.Zone.SIDEBOARD else DeckEditor.Zone.MAIN)
                if (error == null) onDismiss()
            }
        }) { Text(if (identity) "Confirmer le remplacement" else "Ajouter un exemplaire") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } })
}
