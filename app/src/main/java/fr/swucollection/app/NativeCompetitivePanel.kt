package fr.swucollection.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private fun displayDate(value: String) = runCatching { LocalDate.parse(value.take(10)).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) }.getOrDefault(value.take(10))

@Composable
internal fun NativeCompetitivePanel(tab: Int, catalog: List<CatalogCard>, owned: List<CollectionCard>, details: CardDetails,
    existing: List<DeckStore.SavedDeck>, modifier: Modifier = Modifier, onSave: (String, PortableDeck) -> String,
    onShow: (String, PortableDeck) -> Unit) {
    val context = LocalContext.current
    val store = remember { CompetitiveFeedStore(context.applicationContext) }
    var feed by remember { mutableStateOf<CompetitiveFeed?>(null) }
    var refreshing by remember { mutableStateOf(true) }
    var retry by remember { mutableIntStateOf(0) }
    var offline by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var eventId by rememberSaveable { mutableStateOf<String?>(null) }
    var saved by rememberSaveable { mutableStateOf(listOf<String>()) }
    LaunchedEffect(retry) {
        refreshing = true
        if (feed == null) feed = withContext(Dispatchers.IO) { store.local() }
        try { if (retry > 0 || store.needsRefresh()) feed = withContext(Dispatchers.IO) { store.refresh() }; offline = false }
        catch (_: Exception) { offline = true }
        finally { refreshing = false }
    }
    BackHandler(tab == 0 && eventId != null) { eventId = null }
    val snapshot = feed
    val byId = remember(catalog) { catalog.associateBy(DeckConstruction::reference) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (tab == 1) "Les decks du moment" else "Tournois récents", color = ListPalette.Gold,
                style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { retry++ }, enabled = !refreshing) { Text("Actualiser") }
        }
        if (snapshot == null) {
            Text(if (refreshing) "Chargement des decks…" else "Les decks sont indisponibles pour le moment. Réessaie avec une connexion Internet.", color = ListPalette.Secondary)
            return@Column
        }
        Text("Du ${displayDate(snapshot.from)} au ${displayDate(snapshot.to)} · ${snapshot.listCount} listes compatibles", style = MaterialTheme.typography.bodySmall, color = ListPalette.Secondary)
        if (offline) Text("Actualisation indisponible · dernière sélection conservée", color = ListPalette.Gold, style = MaterialTheme.typography.bodySmall)
        message?.let { Text(it, color = ListPalette.Gold) }
        val lists = remember(snapshot) { snapshot.decks.associateBy { it.id } }
        val events = remember(snapshot) { snapshot.events.associateBy { it.id } }
        @Composable fun deckCard(d: MetaList) {
            val stock = remember(d, owned, catalog, details) { CompetitiveDecks.readiness(d.deck, owned, catalog, details) }
            val alreadySaved = d.id in saved || existing.any { it.deck == d.deck }
            val known = stock.unknown == 0 && byId[d.deck.leader]?.let(DeckConstruction::type) in setOf("leader", "chef") && byId[d.deck.base]?.let(DeckConstruction::type) == "base"
            OutlinedCard(colors = CardDefaults.outlinedCardColors(containerColor = ListPalette.Raised, contentColor = ListPalette.Text)) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(d.deck.leader, d.deck.base).forEach { id ->
                            Column(Modifier.weight(1f)) {
                                CardArtwork(byId[id], Modifier.fillMaxWidth().height(78.dp).clickable { onShow(d.name, d.deck) })
                                Text(byId[id]?.name ?: id, style = MaterialTheme.typography.labelMedium, maxLines = 3)
                            }
                        }
                    }
                    events[d.eventId]?.let { e ->
                        Text(listOfNotNull(d.placement?.let { "#$it" }, e.name, displayDate(e.date)).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = ListPalette.Secondary)
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(d.deck.deck.take(6), key = { it.cardId }) { entry ->
                            Column(Modifier.width(46.dp).clickable { onShow(d.name, d.deck) }) {
                                CardArtwork(byId[entry.cardId], Modifier.size(46.dp, 64.dp))
                                Text("×${entry.count}", style = MaterialTheme.typography.labelSmall, color = ListPalette.Gold)
                            }
                        }
                    }
                    Text(if (!known) "Catalogue à actualiser pour comparer ce deck" else if (stock.ready) "Toutes les cartes possédées" else "${stock.missing} carte(s) manquante(s)", color = ListPalette.Gold, style = MaterialTheme.typography.bodyMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText(d.name, DeckInterchange.exportJson(d.deck, d.name)))
                            message = "Deck copié · leader, base et réserve compris."
                        }) { Text("Copier") }
                        Button(enabled = known && !alreadySaved, onClick = {
                            runCatching { onSave(d.name, d.deck) }
                                .onSuccess { saved = saved + d.id; message = "Deck enregistré dans Mes decks." }
                                .onFailure { message = it.message ?: "Enregistrement impossible." }
                        }) { Text(if (alreadySaved) "Enregistré ✓" else "Enregistrer") }
                        TextButton(onClick = { onShow(d.name, d.deck) }) { Text("Voir les cartes") }
                    }
                }
            }
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
            if (tab == 1) {
                item { Text("Classés par présence dans les listes disponibles des grands tournois Premier. Ce classement ne mesure pas un taux de victoire.", style = MaterialTheme.typography.bodySmall, color = ListPalette.Secondary) }
                items(snapshot.groups, key = { it.leader + it.base }) { group ->
                    var expanded by rememberSaveable(group.leader, group.base) { mutableStateOf(false) }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("${group.count} liste(s) publiée(s)", color = ListPalette.Gold, fontWeight = FontWeight.SemiBold)
                        group.deckIds.take(if (expanded) 3 else 1).mapNotNull { lists[it] }.forEach { deckCard(it) }
                        if (group.deckIds.size > 1) TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Réduire" else "Voir d’autres listes") }
                    }
                }
            } else {
                val selected = events[eventId]
                if (selected != null) {
                    item { TextButton(onClick = { eventId = null }) { Text("Retour aux tournois") }; Text(selected.name, color = ListPalette.Gold) }
                    if (selected.deckIds.isEmpty()) item { Text("Aucune liste complète compatible disponible pour ce tournoi.") }
                    items(selected.deckIds.mapNotNull { lists[it] }, key = { it.id }) { deckCard(it) }
                } else items(snapshot.events.filter { it.deckIds.isNotEmpty() }, key = { it.id }) { event ->
                    OutlinedCard(onClick = { eventId = event.id }, colors = CardDefaults.outlinedCardColors(containerColor = ListPalette.Raised, contentColor = ListPalette.Text)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(event.name, color = ListPalette.Gold)
                            Text("${displayDate(event.date)} · ${event.tier}" + (event.players?.let { " · $it joueurs" } ?: ""), style = MaterialTheme.typography.bodySmall)
                            Text("Voir les decks ›", color = ListPalette.Secondary)
                        }
                    }
                }
            }
            item { Text("Source : SWU API · actualisé le ${displayDate(snapshot.updatedAt)}. Listes disponibles et compatibles avec le catalogue ; couverture partielle des tournois." + if (snapshot.excludedCount > 0) " ${snapshot.excludedCount} liste(s) incomplète(s) ou non reconnue(s) exclue(s)." else "", style = MaterialTheme.typography.bodySmall, color = ListPalette.Secondary) }
        }
    }
}
