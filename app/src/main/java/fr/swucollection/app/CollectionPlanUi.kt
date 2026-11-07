package fr.swucollection.app

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun ExchangePanel(sets: List<CatalogSet>, catalog: Map<String, List<CatalogCard>>,
    cards: List<CollectionCard>, modifier: Modifier, database: CollectionDatabase, details: CardDetails) {
    val context = LocalContext.current
    val legacy = remember { context.getSharedPreferences("collection_plan", 0) }
    val prefs = remember { context.getSharedPreferences("exchange_plan", 0) }
    var chosen by remember { mutableStateOf(prefs.getString("set", legacy.getString("set", "Toutes")) ?: "Toutes") }
    var goalRevision by remember { mutableStateOf(0) }
    val goals = remember(goalRevision) { database.goals() }
    val versions = remember(goalRevision) { database.goalVariants() }
    val preciseGoals = remember(goalRevision) { database.printGoals() }
    var qrOpen by remember { mutableStateOf(false) }
    val acceptVariants = versions[chosen] ?: false
    var query by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(chosen) { prefs.edit().putString("set", chosen).apply() }
    val effectiveSet = chosen.takeIf { code -> sets.any { it.code == code } } ?: "Toutes"
    val allCatalog = remember(catalog) { catalog.values.flatten() }
    val playset = goals[effectiveSet] ?: true
    val reserved = database.loans().reserved()
    val profile = remember(cards, allCatalog, effectiveSet, goals, versions, preciseGoals, reserved) {
        TradeProfile.build(allCatalog, cards, goals, details, effectiveSet, variantsBySet = versions, reserved = reserved, printGoals = preciseGoals)
    }
    val offers = profile.offers.associate { it.key to it.quantity }
    val extras = cards.mapNotNull { row -> offers[loanKey(row)]?.takeIf { it > 0 }?.let { row.copy(quantity = it) } }
    val visible = extras.filter { it.name.contains(query, true) || it.number.contains(query, true) }
    val csv by rememberUpdatedState(CollectionPlan.csv(emptyList(), extras))
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) message = runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write("\uFEFF" + csv) }
                ?: error("Fichier inaccessible")
            "Liste des cartes disponibles exportée."
        }.getOrElse { "Export impossible : ${it.message}" }
    }
    LazyColumn(modifier, verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Text("Mes cartes disponibles", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text("Choisis les exemplaires que tu souhaites garder. Seul le surplus sera proposé à l’échange.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Box {
                OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(sets.firstOrNull { it.code == effectiveSet }?.name ?: "Toutes les extensions")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Toutes les extensions") }, onClick = { chosen = "Toutes"; menu = false })
                    sets.forEach { set -> DropdownMenuItem(text = { Text(set.name) }, onClick = { chosen = set.code; menu = false }) }
                }
            }
            if (effectiveSet != "Toutes") {
                PrintGoalControls(preciseGoals[effectiveSet]) { goal -> database.setPrintGoal(effectiveSet, goal); goalRevision++ }
                ObjectiveControls(playset, acceptVariants, precise = preciseGoals[effectiveSet] != null) { triple, accept ->
                    database.setGoal(effectiveSet, triple, accept); goalRevision++
                }
            } else Text("Les objectifs et versions acceptées sont ceux enregistrés pour chaque extension. Choisis une extension pour les modifier.", style = MaterialTheme.typography.bodySmall)
        }
        item {
            Text("1 · Choisir les extensions", style = MaterialTheme.typography.titleSmall)
            Text("${if (effectiveSet == "Toutes") "Extensions commencées et objectifs enregistrés" else sets.firstOrNull { it.code == effectiveSet }?.name.orEmpty()}", style = MaterialTheme.typography.bodySmall)
            Button(onClick={qrOpen=true},modifier=Modifier.fillMaxWidth()) {Text("Comparer nos collections")}
        }

        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${extras.sumOf { it.quantity }} exemplaires disponibles", fontWeight = FontWeight.Bold)
                    Text("${extras.size} références et variantes · aucune carte n’est retirée de ta collection.", fontSize = 12.sp)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = extras.isNotEmpty(), onClick = {
                    runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"; putExtra(Intent.EXTRA_TEXT, CollectionPlan.shareAvailable(extras))
                    }, "Partager mes cartes disponibles")) }.onFailure { message = "Partage indisponible." }
                }) { Text("Partager mes disponibles") }
                OutlinedButton(enabled = extras.isNotEmpty(), onClick = { export.launch("SWU-disponibles.csv") }) { Text("Exporter") }
            }
            message?.let { Text(it, fontSize = 12.sp) }
        }
        item {
            OutlinedTextField(query, { query = it }, label = { Text("Rechercher parmi mes disponibles") },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
        }
        if (visible.isEmpty()) item {
            Text(if (extras.isEmpty()) "Aucun surplus pour cet objectif. Tes exemplaires sont conservés pour ta collection."
                else "Aucune carte disponible ne correspond à cette recherche.")
        }
        items(visible, key = { it.id }) { card ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${card.quantity} × ${card.name}", fontWeight = FontWeight.SemiBold)
                    Text("${card.setCode} · ${card.number} · ${card.variant} · ${CardLanguages.label(card.language)}", fontSize = 12.sp)
                }
            }
        }
    }
    if(qrOpen) Dialog(onDismissRequest={qrOpen=false},properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize()) {Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Text("Comparer nos collections",style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f))
                TextButton(onClick={qrOpen=false}) {Text("Fermer")}
            }
            Text(sets.firstOrNull {it.code==effectiveSet}?.name?:"Extensions commencées et objectifs enregistrés")
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                TradeQrPanel(profile, allCatalog)
            }
        }}
    }
}


