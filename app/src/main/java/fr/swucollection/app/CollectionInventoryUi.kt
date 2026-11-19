@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package fr.swucollection.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Every stored print/language has its own visible controls; the image alone opens the viewer. */
@Composable
internal fun CollectionInventoryCard(rows: List<CollectionCard>, catalog: Map<String, CatalogCard>,
    onPreview: (CollectionCard, CatalogCard) -> Unit, onQuantity: (CollectionCard, Int) -> Unit,
    onLanguage: (CollectionCard) -> Unit, onAdd: (CatalogCard) -> Unit, quotes: Map<String, MarketQuote> = emptyMap()) {
    val displayRows = remember(rows) { rows.sortedWith(compareBy<CollectionCard>(
        { (listOf("FR", "EN", "DE", "ES", "IT") + CardLanguages.UNKNOWN).indexOf(it.language).let { rank -> if (rank < 0) Int.MAX_VALUE else rank } },
        { ScanVariants.options.indexOf(it.variant).let { rank -> if (rank < 0) Int.MAX_VALUE else rank } },
        { it.setCode }, { it.number })) }
    val first = displayRows.first()
    val card = catalog[MarketPricing.key(first.setCode, first.number)]
    Surface(shape = RoundedCornerShape(12.dp), color = PremiumNavy,
        border = BorderStroke(1.dp, Color(0xFF59738E)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.background(PremiumPanel).padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(if (displayRows.size > 1) 120.dp else 94.dp).clip(RoundedCornerShape(8.dp)).background(PremiumNavy)
                    .border(1.5.dp, PremiumGold, RoundedCornerShape(8.dp)).clickable(enabled = card != null) { card?.let { onPreview(first, it) } }
                    .semantics { contentDescription = "Agrandir ${first.name}" }) {
                    card?.let { BadgeIllustration(it, false, Modifier.fillMaxSize()) }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(first.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 22.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Surface(color = PremiumGold, shape = RoundedCornerShape(9.dp)) {
                        Text("${rows.sumOf { it.quantity }} possédées", Modifier.background(PremiumMetal).padding(horizontal = 10.dp, vertical = 3.dp),
                            color = PremiumNavy, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
            displayRows.forEach { row ->
                Surface(color = Color(0xFF1A2A40), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Color(0xFF334B64))) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).clickable { onLanguage(row) }.padding(6.dp)
                            .semantics { contentDescription = "Modifier la langue : ${CardLanguages.label(row.language)}, ${row.variant}" }) {
                            Text("${row.language} · ${row.variant}", color = Color.White)
                            if (rows.map { it.setCode to it.number }.distinct().size > 1)
                                Text("${row.setCode} · ${row.number}", style = MaterialTheme.typography.labelSmall, color = ListPalette.Secondary)
                        }
                        InventoryQuantity(row.quantity, { onQuantity(row, -1) }, { onQuantity(row, 1) },
                            label = "${row.name}, ${row.language}, ${row.variant}", plusEnabled = row.quantity < 9999)
                    }
                }
            }
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 36.dp) {
            OutlinedButton(onClick = { card?.let(onAdd) }, enabled = card != null,
                modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, PremiumGold)) {
                Text("＋ Ajouter une variante", color = PremiumGold)
            }
            }
        }
    }
}

@Composable
private fun InventoryQuantity(quantity: Int, minus: () -> Unit, plus: () -> Unit, label: String,
    plusEnabled: Boolean = true, minusEnabled: Boolean = true, large: Boolean = false) {
    InventoryQuantityButton("−", minusEnabled, "Retirer un exemplaire : $label", minus, large)
    Text(quantity.toString(), Modifier.widthIn(min = if (large) 64.dp else 32.dp).testTag("inventory_quantity"), textAlign = TextAlign.Center,
        color = Color.White, fontWeight = FontWeight.Bold, fontSize = if (large) 26.sp else 20.sp)
    InventoryQuantityButton("+", plusEnabled, "Ajouter un exemplaire : $label", plus, large)
}

@Composable
private fun InventoryQuantityButton(symbol: String, enabled: Boolean, description: String, onClick: () -> Unit, large: Boolean) {
    Box(Modifier.size(if (large) 56.dp else 40.dp).clickable(enabled = enabled, onClick = onClick)
        .semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Box(Modifier.size(if (large) 48.dp else 32.dp).clip(RoundedCornerShape(7.dp)).background(PremiumMetal)
            .padding(1.dp).background(Color(0xFF152238), RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
            Text(symbol, color = if (enabled) PremiumGold else Color(0xFF738098), fontSize = if (large) 30.sp else 22.sp)
        }
    }
}

/** Treatment choices resolve to the actual collector reference before any stock is written. */
@Composable
internal fun CollectionVariantSheet(card: CatalogCard, catalog: List<CatalogCard>, owned: List<CollectionCard>,
    details: CardDetails, onDismiss: () -> Unit, onConfirm: (CatalogCard, String, String, Int) -> Unit) {
    val prints = remember(card, catalog, owned) { familyPrints(card, catalog, owned, details).filter { it.variant in ScanVariants.detect(it.card).choices } }
    var selected by remember(card) { mutableStateOf(prints.firstOrNull { it.card.setCode == card.setCode && it.card.number == card.number } ?: prints.firstOrNull()) }
    var language by remember(card) { mutableStateOf(card.language.takeIf { it in CardLanguages.supported } ?: "FR") }
    var quantity by remember(card) { mutableIntStateOf(1) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF203145), contentColor = Color.White) {
        Column(Modifier.fillMaxWidth().background(PremiumPanel).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding()
            .imePadding().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Ajouter une variante", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 32.dp) {
                    IconButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Close, "Fermer") }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(64.dp).clip(RoundedCornerShape(7.dp)).border(1.dp, PremiumGold, RoundedCornerShape(7.dp))) {
                    BadgeIllustration(selected?.card ?: card, false, Modifier.fillMaxSize())
                }
                Text(card.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            var otherLanguages by remember { mutableStateOf(language !in listOf("FR", "EN")) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Langue", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp)
                Text(if (otherLanguages) "Masquer" else "Autres…", color = PremiumGold, fontSize = 12.sp,
                    modifier = Modifier.clickable(enabled = !busy) { otherLanguages = !otherLanguages }.padding(4.dp))
            }
            InventoryChoices(listOf("FR", "EN"), language, !busy, CardLanguages::label) { language = it }
            if (otherLanguages) InventoryChoices(CardLanguages.supported - setOf("FR", "EN"), language, !busy, CardLanguages::label) { language = it }
            Text("Finition", fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp)
            InventoryChoices(prints.map { it.variant }.distinct(), selected?.variant, !busy, { it }) { finish ->
                selected = prints.first { it.variant == finish }
            }
            val references = prints.filter { it.variant == selected?.variant }
            if (references.size > 1) {
                Text("Édition et numéro", fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    references.forEach { print -> FilterChip(selected == print, { selected = print }, enabled = !busy,
                        label = { Text("${print.card.setCode} · ${print.card.number}") }) }
                }
            }
            Text("Quantité", fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 20.sp)
            Row(Modifier.align(Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                InventoryQuantity(quantity, { quantity-- }, { quantity++ }, card.name, !busy && quantity < 9999, !busy && quantity > 1, large = true)
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = {
                selected?.let { print ->
                    busy = true
                    runCatching { onConfirm(print.card, language, print.variant, quantity) }
                        .onSuccess { onDismiss() }.onFailure { busy = false; error = it.message ?: "Ajout non enregistré. Réessaie." }
                }
            }, enabled = selected != null && !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).background(PremiumMetal, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = PremiumNavy)) {
                Text("Ajouter à ma collection", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Equal-width choices wrap on narrow screens and large fonts without clipping labels. */
@Composable
private fun InventoryChoices(options: List<String>, selected: String?, enabled: Boolean,
    label: (String) -> String, onSelect: (String) -> Unit) {
    val largeText = LocalDensity.current.fontScale > 1.3f
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp), maxItemsInEachRow = if (largeText) 2 else 4) {
        options.forEach { option ->
            Surface(onClick = { onSelect(option) }, enabled = enabled,
                modifier = Modifier.weight(1f).heightIn(min = if (options.size > 2) 44.dp else 36.dp).semantics { this.selected = selected == option }, shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, if (selected == option) PremiumGold else Color(0xFF78869A)),
                color = if (selected == option) PremiumGold else PremiumNavy,
                contentColor = if (selected == option) PremiumNavy else Color.White) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                    Text(label(option), fontSize = if (options.size > 2) 12.sp else 14.sp, textAlign = TextAlign.Center,
                        lineHeight = if (options.size > 2) 16.sp else 20.sp,
                        fontWeight = if (selected == option) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}
