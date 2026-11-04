package fr.swucollection.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val primaryDestinations = listOf("Accueil", "Collection", "Extensions", "Mes listes", "Échanges")

@Composable
internal fun CollectionBottomBar(section: String, onNavigate: (String) -> Unit) {
    val selected = when (section) {
        "Compléter mon set", "Recherche" -> "Extensions"
        "Ajouter", "Historique", "Scanner" -> "Collection"
        else -> section
    }
    val icons = listOf(Icons.Default.Home, Icons.Default.CollectionsBookmark, Icons.Default.AutoStories,
        Icons.Default.Star, Icons.Default.SwapHoriz)
    Surface(color = Color(0xFF0B1B2D), border = BorderStroke(1.dp, Color(0xFF9C7941)),
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)) {
        NavigationBar(containerColor = Color.Transparent, tonalElevation = 0.dp, windowInsets = WindowInsets(0,0,0,0)) {
            primaryDestinations.forEachIndexed { index, destination ->
                NavigationBarItem(selected = selected == destination, onClick = { onNavigate(destination) },
                    icon = { Icon(icons[index], destination) }, alwaysShowLabel = true,
                    label = { Text(if (destination == "Mes listes") "Mes listes" else destination, fontSize = 10.sp, maxLines = 1) },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Color(0xFFFFD47D),
                        selectedTextColor = Color(0xFFFFD47D), indicatorColor = Color(0xFF213951),
                        unselectedIconColor = Color(0xFFB4C0D4), unselectedTextColor = Color(0xFFB4C0D4)))
            }
        }
    }
}

@Composable
internal fun ObjectiveControls(playset: Boolean, acceptVariants: Boolean, onChange: (Boolean, Boolean) -> Unit) {
    Text("Mon objectif pour cette extension", fontWeight = FontWeight.SemiBold)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(!playset, { onChange(false, acceptVariants) }, label = { Text("1 de chaque") })
        FilterChip(playset, { onChange(true, acceptVariants) }, label = { Text("Playset") })
    }
    Text("Playset : 3 exemplaires, sauf leaders et bases en 1 exemplaire.", style = MaterialTheme.typography.bodySmall)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = acceptVariants, onCheckedChange = { onChange(playset, it) })
        Text("Les variantes comptent dans mon objectif", modifier = Modifier.weight(1f))
    }
    Text(if (acceptVariants) "Les versions d’une même carte de cette extension sont additionnées. Les classiques sont conservées en priorité ; le surplus peut inclure des variantes."
        else "Les manquantes concernent les classiques non foil. Les autres versions sont conservées séparément avant de proposer leurs surplus.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("Les langues sont cumulées pour cet objectif, commun aux cartes manquantes et aux échanges.", color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.bodySmall)
}

@Composable
internal fun ResumeCollectionPanel(database: CollectionDatabase, cards: List<CollectionCard>,
    catalog: Map<String, List<CatalogCard>>, sets: List<CatalogSet>, details: CardDetails,
    quotes: Map<String, MarketQuote>, batch: ScanBatchStore, onScan: () -> Unit, onSet: (String) -> Unit) {
    val goals = database.goals()
    val variants = database.goalVariants()
    val started = cards.map { it.setCode }.toSet() + goals.keys
    val suggested = sets.firstOrNull { it.code in started && CollectionObjectives.rows(catalog[it.code].orEmpty(), cards,
        goals[it.code] ?: true, variants[it.code] ?: false, details).any { row -> row.missing > 0 } }
    OutlinedCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Ma collection en un regard", fontWeight = FontWeight.Bold)
            Text("${cards.sumOf { it.quantity }} exemplaires · ${cards.map { MarketPricing.key(it.setCode,it.number) }.distinct().size} cartes différentes")
            if (quotes.isNotEmpty()) {
                val value = collectionValue(cards, quotes)
                val money = java.text.NumberFormat.getCurrencyInstance(java.util.Locale.FRANCE)
                Text((if (value.unpriced > 0) "Sous-total coté : " else "Valeur estimée : ") + money.format(value.low) +
                    if (value.high != value.low) " à ${money.format(value.high)}" else "", color = MaterialTheme.colorScheme.primary)
                if (value.unpriced > 0) Text("${value.unpriced} exemplaires sans cote, non inclus", style = MaterialTheme.typography.bodySmall)
                Text("Cardmarket · toutes langues · hors port", style = MaterialTheme.typography.bodySmall)
            }
            if (batch.entries.isNotEmpty()) Button(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
                Text("Vérifier mon lot · ${batch.entries.sumOf { it.quantity }} en attente")
            }
            suggested?.let { set ->
                val rows = CollectionObjectives.rows(catalog[set.code].orEmpty(), cards, goals[set.code] ?: true,
                    variants[set.code] ?: false, details)
                val target = rows.sumOf { it.target }
                val missing = rows.sumOf { it.missing }
                Text(set.name, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                LinearProgressIndicator(progress = { if (target == 0) 0f else (target - missing).toFloat() / target }, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = { onSet(set.code) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Continuer cette extension · $missing à trouver")
                }
            }
        }
    }
}

@Composable
internal fun ActiveCollectionFilters(set: String, scope: String, sort: CollectionPriceSort, search: String,
    onSet: () -> Unit, onScope: () -> Unit, onSort: () -> Unit, onSearch: () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (set != "Toutes") InputChip(true, onSet, label = { Text("$set ×") })
        if (scope == "Doublons") InputChip(true, onScope, label = { Text("Doublons ×") })
        if (sort != CollectionPriceSort.CATALOG) InputChip(true, onSort, label = { Text("${sort.label} ×") })
        if (search.isNotBlank()) InputChip(true, onSearch, label = { Text("$search ×", maxLines = 1) })
    }
}

internal fun groupOwnedVersions(cards: List<CollectionCard>, catalog: List<CatalogCard>, details: CardDetails): List<List<CollectionCard>> {
    val byKey = catalog.associateBy { MarketPricing.key(it.setCode,it.number) }
    return cards.groupBy { row -> byKey[MarketPricing.key(row.setCode,row.number)]?.let { CollectionObjectives.family(it,details) }
        ?: "unknown:${MarketPricing.key(row.setCode,row.number)}" }.values.toList()
}

@Composable
internal fun GroupedOwnedCard(rows: List<CollectionCard>, catalog: List<CatalogCard>, quotes: Map<String, MarketQuote>,
    onPreview: (CollectionCard, CatalogCard) -> Unit, onQuantity: (Long, Int) -> Unit, onLanguage: (CollectionCard) -> Unit = {}) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var expanded by remember(rows.first().id) { mutableStateOf(false) }
    val first = rows.first()
    OutlinedCard(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                val image = catalog.firstOrNull { MarketPricing.key(it.setCode,it.number) == MarketPricing.key(first.setCode,first.number) }
                coil.compose.AsyncImage(image?.let { CardLanguageArtwork.localized(context,it,first.language,first.variant).imageUrl }, first.name, Modifier.size(48.dp, 68.dp), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
                Column(Modifier.weight(1f)) {
                    Text(first.name, fontWeight = FontWeight.Bold)
                    Text("${first.setCode} · ${rows.size} versions · ${rows.sumOf { it.quantity }} exemplaires")
                    val prices = rows.mapNotNull { quotes[collectionPriceKey(it.setCode,it.number,it.variant)]?.amount?.takeIf { price -> price.isFinite() && price > 0 } }
                    if (prices.isNotEmpty()) {
                        val money = java.text.NumberFormat.getCurrencyInstance(java.util.Locale.FRANCE)
                        Text("Cote unitaire : " + money.format(prices.minOrNull()) + if (prices.minOrNull() != prices.maxOrNull()) " à ${money.format(prices.maxOrNull())}" else "", style = MaterialTheme.typography.bodySmall)
                    }
                    Text(if (expanded) "Masquer les versions ▴" else "Voir les versions ▾", color = MaterialTheme.colorScheme.primary)
                }
            }
            if (expanded) rows.forEach { row ->
                val card = catalog.firstOrNull { MarketPricing.key(it.setCode,it.number) == MarketPricing.key(row.setCode,row.number) }
                HorizontalDivider()
                Text("N° ${row.number} · ${row.variant}")
                TextButton(onClick = { onLanguage(row) }) { Text(CardLanguages.label(row.language)) }
                CollectionPriceLabel(quotes[collectionPriceKey(row.setCode,row.number,row.variant)], quotes.isNotEmpty())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { if (card != null) onPreview(row,card) }, enabled = card != null, modifier = Modifier.weight(1f)) { Text("Voir la carte") }
                    TextButton(onClick = { onQuantity(row.id,-1) }) { Text("−") }
                    Text("${row.quantity}")
                    TextButton(onClick = { onQuantity(row.id,1) }) { Text("+") }
                }
            }
        }
    }
}

