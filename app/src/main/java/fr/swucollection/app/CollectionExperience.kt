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
internal fun ObjectiveControls(playset: Boolean, acceptVariants: Boolean, precise: Boolean = false, onChange: (Boolean, Boolean) -> Unit) {
    Text("Mon objectif pour cette extension", fontWeight = FontWeight.SemiBold)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(!playset, { onChange(false, acceptVariants) }, label = { Text("1 de chaque") })
        FilterChip(playset, { onChange(true, acceptVariants) }, label = { Text("Playset") })
    }
    Text("Playset : 3 exemplaires, sauf leaders et bases en 1 exemplaire.", style = MaterialTheme.typography.bodySmall)
    if (!precise) Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = acceptVariants, onCheckedChange = { onChange(playset, it) })
        Text("Les variantes comptent dans mon objectif", modifier = Modifier.weight(1f))
    }
    if (!precise) Text(if (acceptVariants) "Les versions d’une même carte de cette extension sont additionnées. Les classiques sont conservées en priorité ; le surplus peut inclure des variantes."
        else "Les manquantes concernent les classiques non foil. Les autres versions sont conservées séparément avant de proposer leurs surplus.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (!precise) Text("Les langues sont cumulées pour cet objectif, commun aux cartes manquantes et aux échanges.", color = MaterialTheme.colorScheme.primary,
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
        goals[it.code] ?: true, variants[it.code] ?: false, details, database.printGoals()[it.code]).any { row -> row.missing > 0 } }
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
                    variants[set.code] ?: false, details, database.printGoals()[set.code])
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

internal fun groupOwnedVersions(cards: List<CollectionCard>, catalog: List<CatalogCard>, details: CardDetails, acrossSets: Boolean = false): List<List<CollectionCard>> {
    val byKey = catalog.associateBy { MarketPricing.key(it.setCode,it.number) }
    return cards.groupBy { row -> byKey[MarketPricing.key(row.setCode,row.number)]?.let { if (acrossSets) details.family(it) else CollectionObjectives.family(it,details) }
        ?: "unknown:${MarketPricing.key(row.setCode,row.number)}" }.values.toList()
}

/** One collector reference and finish, with independent stock rows for each language. */
internal fun groupOwnedPrints(rows: List<CollectionCard>): List<List<CollectionCard>> =
    rows.groupBy { Triple(it.setCode, it.number, it.variant) }.values.toList()

/** Top arrows browse distinct cards; print selection stays inside the family sheet. */
internal fun familyPreviewEntries(groups: List<List<CollectionCard>>, clicked: CollectionCard,
    catalogByKey: Map<String, CatalogCard>): List<CardPreviewEntry> = groups.mapNotNull { group ->
    val row = group.firstOrNull { it.id == clicked.id } ?: group.first()
    catalogByKey[MarketPricing.key(row.setCode, row.number)]?.let { CardPreviewEntry(it, row.variant, row.language) }
}

@Composable
internal fun GroupedOwnedCard(rows: List<CollectionCard>, catalog: List<CatalogCard>, quotes: Map<String, MarketQuote>,
    onPreview: (CollectionCard, CatalogCard) -> Unit, onQuantity: (Long, Int) -> Unit, onLanguage: (CollectionCard) -> Unit = {}, openDirect: Boolean = false) {
    val prints = remember(rows) { groupOwnedPrints(rows) }
    var expanded by remember(rows.first().id) { mutableStateOf(false) }
    val first = rows.first()
    val representative = catalog.firstOrNull { MarketPricing.key(it.setCode,it.number) == MarketPricing.key(first.setCode,first.number) }
    OutlinedCard(onClick = { if (openDirect && representative != null) onPreview(first, representative) else expanded = !expanded }, modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                val image = catalog.firstOrNull { MarketPricing.key(it.setCode,it.number) == MarketPricing.key(first.setCode,first.number) }
                CardArtwork(image, Modifier.size(48.dp,68.dp), language=first.language, variant=first.variant)
                Column(Modifier.weight(1f)) {
                    Text(first.name, fontWeight = FontWeight.Bold)
                    Text("${first.setCode} · ${prints.size} versions · ${rows.sumOf { it.quantity }} exemplaires")
                    val prices = rows.mapNotNull { quotes[collectionPriceKey(it.setCode,it.number,it.variant)]?.amount?.takeIf { price -> price.isFinite() && price > 0 } }
                    if (prices.isNotEmpty()) {
                        val money = java.text.NumberFormat.getCurrencyInstance(java.util.Locale.FRANCE)
                        Text("Cote unitaire : " + money.format(prices.minOrNull()) + if (prices.minOrNull() != prices.maxOrNull()) " à ${money.format(prices.maxOrNull())}" else "", style = MaterialTheme.typography.bodySmall)
                    }
                    Text(if (openDirect) "Ouvrir la fiche et ses versions ›" else if (expanded) "Masquer les versions ▴" else "Voir les versions ▾", color = MaterialTheme.colorScheme.primary)
                }
            }
            if (openDirect) TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Masquer les quantités par version" else "Modifier les quantités par version") }
            if (expanded) prints.forEach { languages ->
                val print = languages.first()
                val card = catalog.firstOrNull { MarketPricing.key(it.setCode,it.number) == MarketPricing.key(print.setCode,print.number) }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("N° ${print.number} · ${print.variant}", modifier = Modifier.weight(1f))
                    TextButton(onClick = { if (card != null) onPreview(print,card) }, enabled = card != null) { Text("Voir la carte") }
                }
                languages.forEach { row ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { onLanguage(row) }, modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(0.dp)) {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                                CollectionPriceLabel(quotes[collectionPriceKey(row.setCode,row.number,row.variant)],
                                    quotes.isNotEmpty(), language = row.language)
                            }
                        }
                        TextButton(onClick = { onQuantity(row.id,-1) }, modifier = Modifier.size(48.dp),
                            contentPadding = PaddingValues(0.dp)) { Text("−") }
                        Text("${row.quantity}", modifier = Modifier.widthIn(min = 24.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { onQuantity(row.id,1) }, modifier = Modifier.size(48.dp),
                            contentPadding = PaddingValues(0.dp)) { Text("+") }
                    }
                }
            }
        }
    }
}

