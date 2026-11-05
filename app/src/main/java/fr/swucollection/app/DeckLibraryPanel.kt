package fr.swucollection.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert

@Composable
internal fun DeckLibraryPanel(
    decks: List<DeckStore.SavedDeck>, modifier: Modifier = Modifier, catalog: List<CatalogCard> = emptyList(),
    pinned: Set<String> = emptySet(), onPin: (DeckStore.SavedDeck) -> Unit = {}, onPrepare: (DeckStore.SavedDeck) -> Unit = {},
    onCreate: () -> Unit, onResumeDraft: (String) -> Unit, onImport: () -> Unit, onOpen: (DeckStore.SavedDeck) -> Unit,
    onDuplicate: (DeckStore.SavedDeck) -> Unit, onDelete: (DeckStore.SavedDeck) -> Unit,
    onExport: (DeckStore.SavedDeck) -> Unit = {}
) {
    val context = LocalContext.current.applicationContext
    val draftStore = remember(context) { DeckDraftStore(context) }
    var draftRevision by remember { mutableStateOf(0) }
    val drafts = remember(decks, draftRevision) { draftStore.newDrafts() }
    var deletingDraft by remember { mutableStateOf<Pair<String, DeckDraft>?>(null) }
    var draftError by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<DeckStore.SavedDeck?>(null) }
    val byId = remember(catalog) { catalog.associateBy(DeckConstruction::reference) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onCreate) { Text("+ Créer un deck") }
            OutlinedButton(onClick = onImport) { Text("Importer") }
        }
        draftError?.let { Text(it, color = ListPalette.Danger) }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(drafts, key = { "draft:${it.first}" }) { (key, draft) ->
                OutlinedCard(onClick = { onResumeDraft(key) }, modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.outlinedCardColors(containerColor = ListPalette.Background, contentColor = ListPalette.Text)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(draft.name.ifBlank { "Mon deck" }, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium, color = ListPalette.Gold)
                        Text("Brouillon · ${draft.deck.deck.sumOf { it.count }} cartes · ${draft.deck.sideboard.sumOf { it.count }} en réserve",
                            style = MaterialTheme.typography.bodySmall, color = ListPalette.Secondary)
                        Text(listOfNotNull(if (!draft.leaderChosen) "Leader à choisir" else null,
                            if (!draft.baseChosen) "Base à choisir" else null).joinToString(" · ").ifBlank { "Deck en cours" },
                            style = MaterialTheme.typography.bodySmall)
                        if (draft.collectionEntries.isNotEmpty()) Text(
                            "${draft.collectionEntries.sumOf { it.quantity }} ajout(s) à la collection en attente",
                            style = MaterialTheme.typography.bodySmall, color = ListPalette.Secondary)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { onResumeDraft(key) }) { Text("Modifier") }
                            TextButton(onClick = { deletingDraft = key to draft }) { Text("Supprimer", color = ListPalette.Danger) }
                        }
                    }
                }
            }
            if (decks.isEmpty() && drafts.isEmpty()) item { Text("Crée ton premier deck ou importe un fichier JSON.", color = ListPalette.Secondary) }
            items(decks, key = { it.id }) { saved ->
                var more by remember(saved.id) { mutableStateOf(false) }
                OutlinedCard(onClick = { onOpen(saved) }, modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.outlinedCardColors(containerColor = ListPalette.Background, contentColor = ListPalette.Text)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Column(Modifier.width(72.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                CardArtwork(byId[saved.deck.leader], Modifier.fillMaxWidth().height(48.dp))
                                CardArtwork(byId[saved.deck.base], Modifier.fillMaxWidth().height(48.dp))
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(saved.name, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.titleMedium, color = ListPalette.Gold)
                                Text(byId[saved.deck.leader]?.name ?: saved.deck.leader, maxLines = 2,
                                    overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                                Text("${saved.deck.deck.sumOf { it.count }} cartes · ${saved.deck.sideboard.sumOf { it.count }} en réserve",
                                    style = MaterialTheme.typography.bodySmall, color = ListPalette.Secondary)
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { onOpen(saved) }) { Text("Modifier") }
                            TextButton(onClick = { onPin(saved) }) { Text(if (saved.id in pinned) "★ Épinglé" else "☆ Épingler") }
                            TextButton(onClick = { onPrepare(saved) }) { Text("Préparer") }
                            TextButton(onClick = { onExport(saved) }) { Text("Exporter") }
                            Box {
                                IconButton(onClick = { more = true }) {
                                    Icon(Icons.Default.MoreVert, "Autres actions pour ${saved.name}")
                                }
                                DropdownMenu(expanded = more, onDismissRequest = { more = false }) {
                                    DropdownMenuItem(text = { Text("Dupliquer") }, onClick = { more = false; onDuplicate(saved) })
                                    DropdownMenuItem(text = { Text("Supprimer", color = ListPalette.Danger) },
                                        onClick = { more = false; deleting = saved })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    deletingDraft?.let { (key, draft) ->
        AlertDialog(onDismissRequest = { deletingDraft = null }, title = { Text("Supprimer « ${draft.name.ifBlank { "Mon deck" }} » ?") },
            text = { Text("Ce brouillon et ses ajouts en attente seront supprimés. Les cartes déjà dans ta collection restent conservées.") },
            confirmButton = { TextButton(onClick = {
                runCatching { draftStore.clear(key) }
                    .onSuccess { draftRevision++; draftError = null; deletingDraft = null }
                    .onFailure { draftError = "Impossible de supprimer le brouillon. Réessaie." }
            }) { Text("Supprimer") } },
            dismissButton = { TextButton(onClick = { deletingDraft = null }) { Text("Annuler") } })
    }
    deleting?.let { saved ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Supprimer « ${saved.name} » ?") },
            text = { Text("Ce deck sera supprimé. Tes cartes restent dans ta collection.") },
            confirmButton = { TextButton(onClick = { onDelete(saved); deleting = null }) { Text("Supprimer") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Annuler") } })
    }
}


