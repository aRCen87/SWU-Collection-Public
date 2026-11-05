package fr.swucollection.app

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.text.NumberFormat
import java.util.Locale

@Composable
internal fun CompleteSetPanel(sets: List<CatalogSet>, catalog: Map<String, List<CatalogCard>>,
    cards: List<CollectionCard>, quotes: Map<String, MarketQuote>, modifier: Modifier,
    onPreview: (CatalogCard, List<CatalogCard>) -> Unit, initialSetCode: String? = null, database: CollectionDatabase, onGoalChanged: () -> Unit, details: CardDetails) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("collection_plan", 0) }
    var chosen by rememberSaveable(initialSetCode) { mutableStateOf(initialSetCode ?: prefs.getString("set", "") ?: "") }
    var playset by rememberSaveable(chosen) { mutableStateOf(database.goals()[chosen] ?: true) }
    var acceptVariants by remember(chosen) { mutableStateOf(database.goalVariants()[chosen] ?: false) }
    var showResults by rememberSaveable { mutableStateOf(initialSetCode != null) }
    var menu by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var expensiveFirst by rememberSaveable { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val available = sets.filter { catalog[it.code].orEmpty().any(CollectionPlan::isClassic) }
    val set = available.firstOrNull { it.code == chosen }
    val rows = remember(catalog, chosen, cards, playset, acceptVariants) {
        CollectionObjectives.rows(catalog[chosen].orEmpty(), cards, playset, acceptVariants, details)
    }
    val missing = rows.filter { it.missing > 0 }
    val visible = missing.filter { it.card.name.contains(query, true) || it.card.number.contains(query, true) }
        .let { list -> if (!expensiveFirst) list else list.sortedByDescending {
            quotes[collectionPriceKey(it.card.setCode, it.card.number, "Normale")]?.amount?.times(it.missing) ?: -1.0
        } }
    val budget = remember(rows, quotes) { CollectionPlan.budget(rows, quotes) }
    val money = remember { NumberFormat.getCurrencyInstance(Locale.FRANCE) }
    val csv by rememberUpdatedState(CollectionPlan.csv(rows, emptyList()))
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) message = runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write("\uFEFF" + csv) }
                ?: error("Fichier inaccessible")
            "Liste exportée."
        }.getOrElse { "Export impossible : ${it.message}" }
    }
    BackHandler(enabled = showResults) { showResults = false }
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Text("Compléter mon set", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(if (showResults && set != null) set.name else "Retrouve précisément les cartes qu’il te manque.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!showResults || set == null) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("1 · Choisis ton extension", fontWeight = FontWeight.Bold)
                        Box {
                            OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) {
                                Text((set?.name ?: "Choisir une extension") + "  ▾")
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                available.forEach { candidate ->
                                    DropdownMenuItem(text = { Text(candidate.name) }, onClick = {
                                        chosen = candidate.code; menu = false
                                        prefs.edit().putString("set", chosen).apply()
                                    })
                                }
                            }
                        }
                        if (available.isEmpty()) Text("Le catalogue est en cours de chargement.")
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("2 · Choisis ton objectif", fontWeight = FontWeight.Bold)
                        ObjectiveControls(playset, acceptVariants) { triple, versions ->
                            playset = triple; acceptVariants = versions
                            if (chosen.isNotBlank()) { database.setGoal(chosen, triple, versions); onGoalChanged() }
                        }

                    }
                }
            }
            item {
                Button(onClick = { database.setGoal(chosen,playset);onGoalChanged();query = ""; showResults = true }, enabled = set != null, modifier = Modifier.fillMaxWidth()) {
                    Text("Voir mes cartes manquantes")
                }
            }
        } else {
            item {
                OutlinedButton(onClick = { showResults = false }) { Text("Modifier l’extension ou l’objectif") }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val required = rows.sumOf { it.target }
                        val filled = rows.sumOf { minOf(it.owned, it.target) }
                        Text(if (missing.isEmpty()) "Objectif atteint !" else "${missing.sumOf { it.missing }} exemplaires à trouver",
                            fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("${missing.size} cartes différentes · " + if (playset) "Objectif playset" else "Objectif 1 de chaque", fontSize = 12.sp)
                        LinearProgressIndicator(progress = { if (required == 0) 0f else filled.toFloat() / required },
                            modifier = Modifier.fillMaxWidth())
                        Text("$filled / $required exemplaires · ${if (acceptVariants) "variantes acceptées" else "classiques non foil"}", fontSize = 12.sp)
                        val range = if (budget.minimum == budget.maximum) money.format(budget.minimum)
                            else "${money.format(budget.minimum)} à ${money.format(budget.maximum)}"
                        Text("${if (budget.unpricedCopies > 0) "Sous-total coté" else "Budget estimé"} : $range",
                            fontWeight = FontWeight.SemiBold)
                        if (budget.unpricedCopies > 0) Text("${budget.unpricedCopies} exemplaires sans cote, non inclus.", fontSize = 12.sp)
                        Text("Tendances Cardmarket hors port, sans garantie de prix ni de disponibilité en français.", fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (missing.isNotEmpty()) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            runCatching {
                                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, buildString {
                                        appendLine("SWU Collection — ${set.name}")
                                        appendLine(if (playset) "Playset · leaders et bases ×1" else "Un exemplaire de chaque carte")
                                        missing.forEach { appendLine("${it.missing} × ${it.card.name} · ${it.card.setCode} ${it.card.number}") }
                                    })
                                }, "Partager mes cartes manquantes"))
                            }.onFailure { message = "Partage indisponible." }
                        }) { Text("Partager la liste") }
                        OutlinedButton(onClick = { export.launch("SWU-manquantes-${set.code}.csv") }) { Text("Exporter") }
                    }
                }
                item {
                    OutlinedTextField(query, { query = it }, label = { Text("Rechercher une carte manquante") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true)
                    FilterChip(selected = expensiveFirst, onClick = { expensiveFirst = !expensiveFirst },
                        label = { Text("Les plus coûteuses d’abord") })
                    Text("Touche une carte pour ouvrir sa fiche.", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (visible.isEmpty()) item { Text("Aucune carte manquante ne correspond à cette recherche.") }
                items(visible, key = { "${it.card.setCode}:${it.card.number}" }) { row ->
                    Card(onClick = { onPreview(row.card, visible.map { it.card }) }, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            CardArtwork(row.card, Modifier.size(64.dp, 88.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(row.card.name, fontWeight = FontWeight.SemiBold)
                                Text("N° ${row.card.number} · ${row.owned} possédé(s) sur ${row.target}", fontSize = 12.sp)
                                Text("À trouver : ${row.missing}", fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary)
                                val price = quotes[collectionPriceKey(row.card.setCode, row.card.number, "Normale")]?.amount
                                    ?.takeIf { it.isFinite() && it > 0 }
                                Text(price?.let { "${money.format(it)} / carte · ${money.format(it * row.missing)} au total" }
                                    ?: "Cote indisponible", fontSize = 12.sp)
                            }
                        }
                    }
                }
            } else item {
                Text("Tu possèdes déjà tous les exemplaires nécessaires pour cet objectif.")
            }
        }
        message?.let { item { Text(it, fontSize = 12.sp) } }
    }
}



