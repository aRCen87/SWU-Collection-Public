package fr.swucollection.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** A native companion to the live source, with local previews and stock comparisons. */
@Composable
internal fun CompetitiveHub(catalog: List<CatalogCard>, owned: List<CollectionCard>, details: CardDetails,
    rules: LegalityRules, decks: List<DeckStore.SavedDeck>, modifier: Modifier = Modifier,
    onSave: (String, PortableDeck) -> String, onOpen: (String) -> Unit,
    onPreview: (CatalogCard, List<CatalogCard>) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var importing by rememberSaveable { mutableStateOf(false) }
    var previewJson by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var savedId by rememberSaveable { mutableStateOf<String?>(null) }
    var readyOnly by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var status by rememberSaveable { mutableStateOf<String?>(null) }
    val uri = LocalUriHandler.current
    fun browse(url: String) {
        runCatching { uri.openUri(url) }.onFailure { status = "Impossible d’ouvrir SWUBase. Vérifie ta connexion ou ton navigateur." }
    }
    if (importing) DeckImportDialog(catalog, details, onDismiss = { importing = false }, onImport = { title, deck ->
        name = title; previewJson = DeckInterchange.exportJson(deck, title)
        savedId = null; status = null; importing = false
    })
    val preview = remember(previewJson) { previewJson?.let { runCatching { DeckInterchange.importJson(it) }.getOrNull() } }
    BackHandler(preview != null) { previewJson = null; status = null }
    if (preview != null) {
        CompetitiveDeckPreview(preview, name, { name = it }, savedId, status, catalog, owned, details, rules, modifier,
            onBack = { previewJson = null; status = null }, onPreview = onPreview,
            onSave = {
                runCatching { onSave(name.trim(), preview) }
                    .onSuccess { savedId = it; status = "Deck enregistré dans Mes decks." }
                    .onFailure { status = it.message ?: "Enregistrement impossible. Réessaie." }
            }, onOpen = { savedId?.let(onOpen) }, onStatus = { status = it })
        return
    }
    val readiness = remember(decks, owned, catalog, details) {
        decks.associate { it.id to CompetitiveDecks.readiness(it.deck, owned, catalog, details) }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Les résultats du circuit et les listes pour ta collection.", color = ListPalette.Secondary)
        TabRow(selectedTabIndex = tab, containerColor = ListPalette.Background, contentColor = ListPalette.Gold) {
            listOf("Tournois", "Méta", "Decks").forEachIndexed { index, label ->
                Tab(selected = tab == index, onClick = { tab = index }, text = { Text(label) })
            }
        }
        status?.let { Text(it, color = ListPalette.Gold) }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
            if (tab == 0) {
                item { CompetitiveSourceCard("Résultats des tournois", "Classements, participants, Top 8 et listes publiées. Le circuit Premier, des PQ aux grands événements.", Icons.Default.EmojiEvents,
                    "Consulter les tournois", { browse(CompetitiveDecks.TOURNAMENTS) }) }
                item { Text("Sur SWUBase, sélectionne la saison, le niveau du tournoi et la région, puis ouvre l’événement. Les résultats et les listes restent ceux publiés par la source.", color = ListPalette.Secondary) }
                item { CompetitiveImportCard { importing = true } }
            } else if (tab == 1) {
                item { CompetitiveSourceCard("Méta du moment", "Leaders et bases les plus joués, présence en Top 8, confrontations et évolution selon la période sélectionnée.", Icons.Default.Insights,
                    "Explorer la méta", { browse(CompetitiveDecks.META) }) }
                item { CompetitiveSourceCard("Les decks vainqueurs", "Retrouve les leaders et bases des champions dans la sélection de tournois SWUBase.", Icons.Default.MilitaryTech,
                    "Voir les champions", { browse(CompetitiveDecks.CHAMPIONS) }) }
                item { Text("Popularité et performance se lisent séparément. Vérifie la période, le format et le nombre de matchs avant de comparer deux decks.", color = ListPalette.Secondary) }
                item { CompetitiveImportCard { importing = true } }
            } else {
                item { CompetitiveSourceCard("D’un tournoi à Mes decks", "Choisis une liste sur SWUBase et exporte-la, puis importe le fichier JSON ou colle la liste copiée ici.", Icons.Default.Layers,
                    "Trouver une liste", { browse(CompetitiveDecks.DECKS) }) }
                item { CompetitiveImportCard { importing = true } }
                item { OutlinedTextField(query, { query = it.take(120) }, label = { Text("Rechercher dans Mes decks") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
                item { FilterChip(readyOnly, { readyOnly = !readyOnly }, label = { Text("Toutes les cartes possédées") }) }
                item { Text("Mes decks · comparaison avec ta collection", style = MaterialTheme.typography.titleSmall, color = ListPalette.Gold) }
                val visible = decks.filter { it.name.contains(query, true) && (!readyOnly || readiness[it.id]?.ready == true) }
                if (visible.isEmpty()) item { Text(if (decks.isEmpty()) "Aucun deck enregistré. Importe ta première liste pour voir les cartes manquantes." else "Aucun deck ne correspond à ces filtres.", color = ListPalette.Secondary) }
                items(visible, key = { it.id }) { saved ->
                    val stock = readiness.getValue(saved.id)
                    OutlinedCard(onClick = {
                        name = saved.name; previewJson = DeckInterchange.exportJson(saved.deck, saved.name)
                        savedId = saved.id; status = null
                    }, colors = CardDefaults.outlinedCardColors(containerColor = ListPalette.Background, contentColor = ListPalette.Text)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(saved.name, color = ListPalette.Gold, fontWeight = FontWeight.SemiBold)
                            Text(when { stock.unknown > 0 -> "${stock.unknown} référence(s) inconnue(s) · comparaison incomplète"
                                stock.ready -> "Toutes les cartes possédées · réserve comprise"
                                else -> "${stock.missing} exemplaire(s) manquant(s) sur ${stock.required}" })
                            Text("Voir la liste et les manquantes ›", style = MaterialTheme.typography.labelMedium, color = ListPalette.Secondary)
                        }
                    }
                }
                item { TextButton(onClick = { browse(CompetitiveDecks.CONVERTER) }) { Text("Convertir une liste Melee avec SWUBase") } }
            }
            item { Text("Source : SWUBase · les résultats et statistiques s’ouvrent dans ton navigateur. Les decks enregistrés restent accessibles hors connexion.", style = MaterialTheme.typography.bodySmall, color = ListPalette.Secondary) }
        }
    }
}

@Composable
private fun CompetitiveImportCard(onImport: () -> Unit) {
    OutlinedCard(colors = CardDefaults.outlinedCardColors(containerColor = ListPalette.Raised, contentColor = ListPalette.Text)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Jouer ce deck avec ma collection", color = ListPalette.Gold, fontWeight = FontWeight.SemiBold)
            Text("Importe la liste exportée pour voir les manquantes, estimer le budget et l’enregistrer dans Mes decks.")
            Button(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.FileDownload, null); Spacer(Modifier.width(8.dp)); Text("Importer une liste") }
        }
    }
}

@Composable
private fun CompetitiveSourceCard(title: String, text: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    action: String, onClick: () -> Unit) {
    OutlinedCard(shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, ListPalette.Gold.copy(alpha = .5f)),
        colors = CardDefaults.outlinedCardColors(containerColor = ListPalette.Background, contentColor = ListPalette.Text)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, null, tint = ListPalette.Gold, modifier = Modifier.size(36.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, color = ListPalette.Gold)
            Text(text)
            OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text(action); Spacer(Modifier.width(6.dp)); Icon(Icons.Default.OpenInNew, "Ouvrir SWUBase dans le navigateur", Modifier.size(18.dp)) }
        }
    }
}

@Composable
private fun CompetitiveDeckPreview(deck: PortableDeck, name: String, onName: (String) -> Unit, savedId: String?,
    status: String?, catalog: List<CatalogCard>, owned: List<CollectionCard>, details: CardDetails, rules: LegalityRules,
    modifier: Modifier, onBack: () -> Unit, onPreview: (CatalogCard, List<CatalogCard>) -> Unit,
    onSave: () -> Unit, onOpen: () -> Unit, onStatus: (String) -> Unit) {
    val context = LocalContext.current
    var section by rememberSaveable(deck) { mutableIntStateOf(0) }
    var budget by remember { mutableStateOf(false) }
    val byId = remember(catalog) { catalog.associateBy(DeckConstruction::reference) }
    val report = remember(deck, owned, catalog, details, rules) { DeckConstruction.report(deck, owned, catalog, details, rules) }
    val needs = report.needs
    val missing = needs.sumOf { it.missing }
    val required = needs.sumOf { it.required }
    val known = report.unknown == 0
    val identitiesValid = byId[deck.leader]?.let(DeckConstruction::type) in setOf("leader", "chef") && byId[deck.base]?.let(DeckConstruction::type) == "base"
    val visibleCards = remember(deck, catalog) { (listOf(deck.leader, deck.base) + (deck.deck + deck.sideboard).map { it.cardId }).distinct().mapNotNull { byId[it] } }
    if (budget) DeckInsightsDialog(deck, owned, catalog, details, onDismiss = { budget = false })
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null); Text("Retour aux tournois et à la méta") }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
            item { OutlinedTextField(name, { onName(it.take(120)) }, enabled = savedId == null, singleLine = true, label = { Text("Nom du deck") }, modifier = Modifier.fillMaxWidth()) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(deck.leader, deck.base).forEach { id ->
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            val card = byId[id]
                            CardArtwork(card, Modifier.fillMaxWidth().height(85.dp).clickable(enabled = card != null) { card?.let { onPreview(it, visibleCards) } })
                            Text(card?.name ?: id, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            item {
                Text(if (!known) "${report.unknown} référence(s) inconnue(s) · comparaison incomplète" else if (missing == 0) "Toutes les cartes possédées" else "$missing exemplaire(s) à trouver sur $required", color = ListPalette.Gold, fontWeight = FontWeight.Bold)
                if (known) LinearProgressIndicator(progress = { if (required == 0) 0f else (required - missing).toFloat() / required }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                Text("Leader, base et réserve compris. Les langues, variantes et rééditions reconnues sont additionnées pour ce deck.", style = MaterialTheme.typography.bodySmall, color = ListPalette.Secondary)
            }
            item {
                if (savedId == null) Button(onClick = onSave, enabled = name.isNotBlank() && known && identitiesValid, modifier = Modifier.fillMaxWidth()) { Text("Enregistrer dans Mes decks") }
                else Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) { Text("Ouvrir dans Mes decks") }
                status?.let { Text(it, color = ListPalette.Gold) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText(name, DeckInterchange.exportJson(deck, name)))
                        onStatus("Liste copiée au format JSON, leader et réserve compris.")
                    }) { Icon(Icons.Default.ContentCopy, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Copier le deck") }
                    OutlinedButton(onClick = { budget = true }, enabled = known) { Text("Budget et analyse") }
                }
            }
            if (report.issues.isNotEmpty()) item {
                Text("À vérifier pour le Premier actuel", color = ListPalette.Gold, fontWeight = FontWeight.SemiBold)
                report.issues.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = ListPalette.Secondary) }
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Principal", "Réserve", "Manquantes").forEachIndexed { i, label -> FilterChip(section == i, { section = i }, label = { Text(label) }) }
                }
            }
            val rows = when (section) {
                1 -> deck.sideboard
                2 -> needs.filter { it.missing > 0 }.map { DeckEntry(it.id, it.missing) }
                else -> deck.deck
            }
            if (rows.isEmpty()) item { Text(if (section == 2) "Aucune carte manquante parmi les références reconnues." else "Aucune carte dans cette section.", color = ListPalette.Secondary) }
            items(rows, key = { it.cardId }) { entry ->
                val card = byId[entry.cardId]
                val need = needs.firstOrNull { it.id == entry.cardId } ?: card?.let { current -> needs.firstOrNull { byId[it.id]?.let(details::family) == details.family(current) } }
                OutlinedCard(onClick = { card?.let { onPreview(it, visibleCards) } }, colors = CardDefaults.outlinedCardColors(containerColor = ListPalette.Background, contentColor = ListPalette.Text)) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CardArtwork(card, Modifier.size(52.dp, 70.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${entry.count} × ${card?.name ?: entry.cardId}", fontWeight = FontWeight.SemiBold)
                            Text(if (section == 2) "${entry.count} à trouver" else "Possédées : ${need?.owned ?: 0} · besoin total : ${need?.required ?: entry.count}", style = MaterialTheme.typography.bodySmall, color = ListPalette.Secondary)
                        }
                    }
                }
            }
        }
    }
}
