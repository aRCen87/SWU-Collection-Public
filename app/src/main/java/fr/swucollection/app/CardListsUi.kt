package fr.swucollection.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

internal object ListPalette {
    val Background = Color(0xFF081827)
    val Raised = Color(0xFF142D43)
    val Text = Color(0xFFF3F5FA)
    val Secondary = Color(0xFFBDCEDF)
    val Gold = Color(0xFFFFD47D)
    val Danger = Color(0xFFFFB4AB)
}

@Composable
fun CardListNameDialog(initial: String = "", onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (initial.isEmpty()) "Nouvelle liste" else "Renommer la liste") },
        text = { OutlinedTextField(value = name, onValueChange = { name = it.take(80) },
            label = { Text("Nom de la liste") }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank()) { Text("Enregistrer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } })
}

@Composable
fun FavoriteListDialog(card: CatalogCard, lists: List<CardList>, onDismiss: () -> Unit,
    onAdd: (String) -> Unit, onCreate: (String) -> Unit) {
    var creating by remember { mutableStateOf(false) }
    if (creating) {
        CardListNameDialog(onDismiss = { creating = false }, onSave = onCreate)
    } else AlertDialog(onDismissRequest = onDismiss, title = { Text("Ajouter à une liste") },
        text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(card.name, fontWeight = FontWeight.Bold)
                Text("Choisis une liste pour cette carte.")
                Button(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) { Text("+ Créer une nouvelle liste") }
                if (lists.isEmpty()) Text("Tes favoris et cartes à rechercher apparaîtront dans Mes listes, onglet Sélections.")
                lists.forEach { list ->
                    val present = list.entries.any { it.card.setCode == card.setCode && it.card.number == card.number }
                    OutlinedButton(onClick = { onAdd(list.id) }, enabled = !present, modifier = Modifier.fillMaxWidth()) {
                        Text(list.name + if (present) " · Déjà ajoutée ✓" else "")
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } })
}

@Composable
private fun ListHero(title: String, detail: String, count: String) {
    Surface(shape = RoundedCornerShape(20.dp), color = ListPalette.Background,
        contentColor = ListPalette.Text, border = BorderStroke(1.dp, ListPalette.Gold.copy(alpha = 0.45f))) {
        Row(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(ListPalette.Raised, ListPalette.Background)))
            .padding(18.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(Icons.Default.CollectionsBookmark, null, tint = ListPalette.Gold, modifier = Modifier.size(34.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, color = ListPalette.Text, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text(detail, color = ListPalette.Secondary, fontSize = 13.sp)
                Text(count, color = ListPalette.Gold, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ListEmptyState(title: String, detail: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Default.Star, null, tint = ListPalette.Gold, modifier = Modifier.size(42.dp))
        Text(title, color = ListPalette.Text, fontWeight = FontWeight.SemiBold)
        Text(detail, color = ListPalette.Secondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            fontSize = 14.sp)
    }
}

@Composable
private fun ListThumbnail(card: CatalogCard, modifier: Modifier = Modifier) {
    Surface(modifier.size(48.dp, 67.dp), shape = RoundedCornerShape(6.dp), color = ListPalette.Raised) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Star, null, tint = ListPalette.Gold.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
            AsyncImage(model = card.imageUrl, contentDescription = null, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)))
        }
    }
}

@Composable
internal fun CardListsPanel(lists: List<CardList>, selectedId: String?, modifier: Modifier = Modifier,
    onOpen: (String?) -> Unit, onCreate: (String) -> Unit, onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit, onQuantity: (String, CatalogCard, Int) -> Unit,
    onPreview: (CatalogCard, List<CatalogCard>) -> Unit,
    owned: List<CollectionCard>, catalog: List<CatalogCard>, details: CardDetails, rules: LegalityRules, onFormat: (String,String) -> Unit) {
    val selected = lists.firstOrNull { it.id == selectedId }
    var naming by remember(selectedId) { mutableStateOf(false) }
    var deleting by remember(selectedId) { mutableStateOf(false) }
    CompositionLocalProvider(LocalContentColor provides ListPalette.Text) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (selected == null) {
                ListHero("Tes sélections", "Favoris et cartes à rechercher", "${lists.size} liste(s)")
                Button(onClick = { naming = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ListPalette.Gold, contentColor = ListPalette.Background)) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Créer une liste", fontWeight = FontWeight.SemiBold)
                }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 20.dp)) {
                    if (lists.isEmpty()) item {
                        ListEmptyState("Ta première liste t’attend", "Ouvre une carte, touche « Ajouter à une liste » puis crée une sélection. Pour construire un deck, ouvre Mes decks.")
                    }
                    items(lists, key = { it.id }) { list ->
                        OutlinedCard(onClick = { onOpen(list.id) }, modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, ListPalette.Gold.copy(alpha = 0.35f)),
                            colors = CardDefaults.outlinedCardColors(containerColor = ListPalette.Background, contentColor = ListPalette.Text)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Text(list.name, color = ListPalette.Text, fontWeight = FontWeight.Bold,
                                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        Text("${list.entries.size} références · ${list.entries.sumOf { it.quantity }} cartes",
                                            color = ListPalette.Secondary, fontSize = 13.sp)
                                    }
                                    Icon(Icons.Default.ChevronRight, null, tint = ListPalette.Gold)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    list.entries.take(3).forEach { ListThumbnail(it.card) }
                                    if (list.entries.isEmpty()) {
                                        Icon(Icons.Default.Star, null, tint = ListPalette.Gold, modifier = Modifier.size(24.dp))
                                        Text("Prête à accueillir tes cartes", color = ListPalette.Secondary, fontSize = 12.sp)
                                    } else if (list.entries.size > 3) {
                                        Text("+${list.entries.size - 3}", color = ListPalette.Gold, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                TextButton(onClick = { onOpen(null) }, colors = ButtonDefaults.textButtonColors(contentColor = ListPalette.Gold)) {
                    Text("← Toutes mes listes")
                }
                ListHero(selected.name, "${selected.entries.size} références", "${selected.entries.sumOf { it.quantity }} cartes")
                DeckSummary(selected,owned,catalog,details,rules,onFormat={onFormat(selected.id,it)})
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { naming = true }, colors = ButtonDefaults.textButtonColors(contentColor = ListPalette.Gold)) { Text("Renommer") }
                    TextButton(onClick = { deleting = true }, colors = ButtonDefaults.textButtonColors(contentColor = ListPalette.Danger)) { Text("Supprimer la liste") }
                }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 20.dp)) {
                    if (selected.entries.isEmpty()) item {
                        ListEmptyState("Cette liste est encore vide", "Ajoute des cartes avec « Ajouter à une liste » depuis le catalogue ou après un scan.")
                    }
                    items(selected.entries, key = { "${it.card.setCode}:${it.card.number}" }) { entry ->
                        OutlinedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, ListPalette.Secondary.copy(alpha = 0.2f)),
                            colors = CardDefaults.outlinedCardColors(containerColor = ListPalette.Background, contentColor = ListPalette.Text)) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(Modifier.fillMaxWidth().clickable { onPreview(entry.card, selected.entries.map { it.card }) },
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    ListThumbnail(entry.card)
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Text(entry.card.name, color = ListPalette.Text, fontWeight = FontWeight.SemiBold)
                                        Text("${entry.card.setCode} · ${entry.card.number}", color = ListPalette.Secondary, fontSize = 12.sp)
                                    }
                                    Icon(Icons.Default.ChevronRight, null, tint = ListPalette.Gold)
                                }
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { onQuantity(selected.id, entry.card, entry.quantity - 1) }, enabled = entry.quantity > 1) {
                                        Icon(Icons.Default.Remove, "Diminuer la quantité de ${entry.card.name}",
                                            tint = if (entry.quantity > 1) ListPalette.Gold else ListPalette.Secondary.copy(alpha = 0.5f))
                                    }
                                    Text("${entry.quantity}", color = ListPalette.Text, fontWeight = FontWeight.Bold)
                                    IconButton(onClick = { onQuantity(selected.id, entry.card, entry.quantity + 1) }, enabled = entry.quantity < 99) {
                                        Icon(Icons.Default.Add, "Augmenter la quantité de ${entry.card.name}",
                                            tint = if (entry.quantity < 99) ListPalette.Gold else ListPalette.Secondary.copy(alpha = 0.5f))
                                    }
                                    Spacer(Modifier.weight(1f))
                                    TextButton(onClick = { onQuantity(selected.id, entry.card, 0) },
                                        colors = ButtonDefaults.textButtonColors(contentColor = ListPalette.Danger)) { Text("Retirer") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (naming) CardListNameDialog(initial = selected?.name ?: "", onDismiss = { naming = false }, onSave = {
        if (selected == null) onCreate(it) else onRename(selected.id, it)
        naming = false
    })
    if (deleting && selected != null) AlertDialog(onDismissRequest = { deleting = false },
        title = { Text("Supprimer « ${selected.name} » ?") }, text = { Text("La collection reste inchangée.") },
        confirmButton = { TextButton(onClick = { onDelete(selected.id); deleting = false }) { Text("Supprimer") } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text("Annuler") } })
}



