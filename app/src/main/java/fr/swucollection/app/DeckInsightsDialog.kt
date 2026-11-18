package fr.swucollection.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

@Composable
internal fun DeckInsightsDialog(deck: PortableDeck, owned: List<CollectionCard>, catalog: List<CatalogCard>,
    details: CardDetails, onDismiss: () -> Unit, rules: LegalityRules = LegalityRules.DEFAULT, onReplace: ((PortableDeck) -> Unit)? = null) {
    val context = LocalContext.current.applicationContext
    val availableOwned = remember(owned) { val db=CollectionDatabase(context); try { val reserved=db.loans().reserved(); owned.map {it.copy(quantity=(it.quantity-(reserved[loanKey(it)] ?: 0)).coerceAtLeast(0))}.filter {it.quantity>0} } finally {db.close()} }
    val currency = remember { NumberFormat.getCurrencyInstance(Locale.FRANCE) }
    var selection by remember { mutableStateOf<Pair<String, List<PrintCard>>?>(null) }
    var selectedCard by remember { mutableStateOf<CatalogCard?>(null) }
    var reserve by remember { mutableStateOf(false) }
    val analysis = remember(deck, reserve, catalog, details) { DeckInsights.analyse(if (reserve) deck.sideboard else deck.deck, catalog, details) }
    val all = remember(deck, catalog) { runCatching { CardPdfPlan.fromDeck(deck, catalog) }.getOrDefault(emptyList()) }
    val missing = remember(deck, owned, catalog, details) { runCatching { DeckInsights.missingPrints(deck, owned, catalog, details) }.getOrDefault(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val quotes by produceState<Map<String, MarketQuote>>(emptyMap(), all) {
        suspend fun read(network: Boolean) = withContext(Dispatchers.IO) {
            all.distinctBy { DeckConstruction.reference(it.card) }.associate { item ->
                DeckConstruction.reference(item.card) to runCatching { CardmarketPrices.quote(context, item.card, MarketPricing.CATALOGUE, false, network) }.getOrElse { MarketQuote(null) }
            }
        }
        value = read(false); loading = false; value = read(true)
    }
    val budget = remember(all, missing, quotes) { DeckInsights.budget(all, missing, quotes) }
    fun money(estimate: DeckInsights.Estimate): String = if (estimate.priced == 0 && estimate.unknown > 0) "Cotes indisponibles" else
        currency.format(estimate.low) + (if (estimate.high > estimate.low + .005) " à ${currency.format(estimate.high)}" else "") +
            if (estimate.unknown > 0) " · partiel (${estimate.unknown} non coté(s))" else ""
    fun selectMetric(kind: String, label: String) {
        val byId = catalog.associateBy(DeckConstruction::reference)
        val rows = (if (reserve) deck.sideboard else deck.deck).mapNotNull { row ->
            val card = byId[row.cardId] ?: return@mapNotNull null
            val single = DeckInsights.analyse(listOf(row), catalog, details)
            val values = when (kind) { "cost" -> single.costs; "type" -> single.types; "arena" -> single.arenas; else -> single.aspects }
            if ((values[label] ?: 0) > 0) PrintCard(card, row.count) else null
        }
        selection = label to rows
    }
    selection?.let { (title, rows) ->
        AlertDialog(onDismissRequest = { selection = null }, title = { Text(title) }, text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (rows.isEmpty()) Text("Aucune carte dans cette catégorie.")
                rows.forEach { row ->
                    Surface(onClick = { selectedCard = row.card }, color = ListPalette.Raised, shape = RoundedCornerShape(12.dp)) {
                        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            CardArtwork(row.card, Modifier.size(56.dp, 80.dp))
                            Text(row.card.name, Modifier.weight(1f))
                            Text("×${row.quantity}", color = ListPalette.Gold)
                        }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { selection = null }) { Text("Retour à l’analyse") } })
    }
    selectedCard?.let { card ->
        AlertDialog(onDismissRequest = { selectedCard = null }, title = { Text(card.name) }, text = {
            CardArtwork(card, Modifier.fillMaxWidth().heightIn(min = 280.dp, max = 440.dp))
        }, confirmButton = { TextButton(onClick = { selectedCard = null }) { Text("Fermer") } })
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Analyse du deck") }, text = {
        Column(Modifier.heightIn(max = 470.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!reserve, onClick = { reserve = false }, label = { Text("Deck principal") })
                FilterChip(reserve, onClick = { reserve = true }, label = { Text("Réserve") })
            }
            Text("${analysis.total} exemplaire(s) analysé(s)", color = ListPalette.Gold)
            if (analysis.unknownCards > 0) Text("Références inconnues : ${analysis.unknownCards}", style = MaterialTheme.typography.bodySmall)
            InsightsBars("Courbe des coûts", analysis.costs) { selectMetric("cost", it) }
            if (analysis.unknownCosts > 0) Text("Coût inconnu : ${analysis.unknownCosts}", style = MaterialTheme.typography.bodySmall)
            InsightsBars("Types de cartes", analysis.types) { selectMetric("type", it) }
            InsightsBars("Unités par arène", analysis.arenas) { selectMetric("arena", it) }
            InsightsBars("Affinités", analysis.aspects) { selectMetric("aspect", it) }
            Text("Une carte à plusieurs affinités figure dans chacune d’elles. Le leader et la base ne sont pas comptés dans ces graphiques.", style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            if (AppChannel.supportsExperience(context.packageName)) {
                DeckAlternativesPanel(deck, availableOwned, catalog, details, rules, onReplace)
                HorizontalDivider()
            }
            Text("Budget estimé", color = ListPalette.Gold)
            if (all.isEmpty()) Text("Des références sont inconnues : estimation indisponible.") else {
                Text("Deck complet : ${money(budget.total)}")
                Text("À compléter : ${money(budget.missing)}")
                TextButton(onClick = { selection = "Cartes manquantes" to missing }) {
                    Text("${missing.sumOf { it.quantity }} exemplaire(s) manquant(s) · Voir les cartes")
                }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("Finition de chaque référence · tendances Cardmarket, hors frais de port. Les cotes absentes ne sont jamais remplacées par un prix inventé.", style = MaterialTheme.typography.bodySmall)
            if (budget.date.isNotBlank()) Text("Cotes à partir du ${MarketPricing.displayDate(budget.date)}${if (budget.cached) " · cache utilisé" else ""}", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } })
}

@Composable
private fun InsightsBars(title: String, values: Map<String, Int>, onSelect: (String) -> Unit) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = ListPalette.Gold)
    if (values.isEmpty()) Text("Aucune carte dans cette catégorie.", style = MaterialTheme.typography.bodySmall)
    val max = values.values.maxOrNull()?.coerceAtLeast(1) ?: 1
    values.forEach { (label, count) ->
        Column(Modifier.fillMaxWidth().clickable(onClickLabel = "Voir les cartes : $label") { onSelect(label) }.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row { Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall); Text(count.toString(), style = MaterialTheme.typography.labelSmall) }
            Box(Modifier.fillMaxWidth().height(6.dp).background(ListPalette.Background, RoundedCornerShape(3.dp))) {
                if (count > 0) Box(Modifier.fillMaxWidth(count.toFloat() / max).height(6.dp).background(ListPalette.Gold, RoundedCornerShape(3.dp)))
            }
        }
    }
}
