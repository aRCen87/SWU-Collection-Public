package fr.swucollection.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Opening a saved deck or a partial draft never starts or changes an edit session. */
@Composable
internal fun DeckConsultation(draft: DeckDraft, catalog: List<CatalogCard>, onClose: () -> Unit, onEdit: () -> Unit) {
    val byId = remember(catalog) { catalog.associateBy(DeckConstruction::reference) }
    AlertDialog(onDismissRequest = onClose, title = { Text(draft.name.ifBlank { "Mon deck" }) }, text = {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text("${draft.deck.deck.sumOf { it.count }} cartes · ${draft.deck.sideboard.sumOf { it.count }} en réserve")
                if (!draft.leaderChosen || !draft.baseChosen || draft.deck.deck.sumOf { it.count } < 50) Text("Deck en cours · tu peux le compléter plus tard.")
            }
            item { if (draft.leaderChosen) DeckConsultationCard(DeckEntry(draft.deck.leader, 1), byId) else Text("Leader à choisir") }
            item { if (draft.baseChosen) DeckConsultationCard(DeckEntry(draft.deck.base, 1), byId) else Text("Base à choisir") }
            item { Text("Deck principal", style = MaterialTheme.typography.titleSmall) }
            if (draft.deck.deck.isEmpty()) item { Text("Aucune carte pour le moment.") }
            items(draft.deck.deck) { DeckConsultationCard(it, byId) }
            if (draft.deck.sideboard.isNotEmpty()) {
                item { Text("Réserve", style = MaterialTheme.typography.titleSmall) }
                items(draft.deck.sideboard) { DeckConsultationCard(it, byId) }
            }
            if (draft.collectionEntries.isNotEmpty()) item { Text("${draft.collectionEntries.sumOf { it.quantity }} ajout(s) à la collection en attente de validation.") }
        }
    }, confirmButton = { TextButton(onClick = onEdit) { Text("Modifier") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Fermer") } })
}

@Composable
private fun DeckConsultationCard(entry: DeckEntry, cards: Map<String, CatalogCard>) {
    val card = cards[entry.cardId]
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CardArtwork(card, Modifier.size(48.dp, 64.dp))
        Text("${entry.count} × ${card?.name ?: entry.cardId}")
    }
}
