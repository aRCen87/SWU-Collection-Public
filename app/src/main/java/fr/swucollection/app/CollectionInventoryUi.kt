@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package fr.swucollection.app

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Every stored print/language has its own visible controls; the image alone opens the viewer. */
@Composable
internal fun CollectionInventoryCard(rows: List<CollectionCard>, catalog: Map<String, CatalogCard>,
    onPreview: (CollectionCard, CatalogCard) -> Unit, onQuantity: (CollectionCard, Int) -> Unit,
    onLanguage: (CollectionCard) -> Unit, onAdd: (CatalogCard) -> Unit, quotes: Map<String, MarketQuote> = emptyMap()) {
    val first = rows.first()
    val card = catalog[MarketPricing.key(first.setCode, first.number)]
    Surface(shape = RoundedCornerShape(18.dp), color = PremiumNavy,
        border = BorderStroke(1.dp, Color(0xFF59738E)), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.background(PremiumPanel).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.width(90.dp).clickable(enabled = card != null) { card?.let { onPreview(first, it) } }
                    .semantics { contentDescription = "Agrandir ${first.name}" }) {
                    CardArtwork(card, Modifier.fillMaxWidth().height(126.dp), language = first.language, variant = first.variant, showLanguageLabel = false)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(first.name, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Surface(color = PremiumGold, shape = RoundedCornerShape(9.dp)) {
                        Text("${rows.sumOf { it.quantity }} possédées", Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            color = PremiumNavy, fontWeight = FontWeight.Bold)
                    }
                    CollectionPriceLabel(quotes[collectionPriceKey(first.setCode, first.number, first.variant)], quotes.isNotEmpty())
                }
            }
            rows.forEach { row ->
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
            OutlinedButton(onClick = { card?.let(onAdd) }, enabled = card != null,
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, PremiumGold)) {
                Text("＋ Ajouter une variante", color = PremiumGold)
            }
        }
    }
}

@Composable
private fun InventoryQuantity(quantity: Int, minus: () -> Unit, plus: () -> Unit, label: String,
    plusEnabled: Boolean = true, minusEnabled: Boolean = true) {
    OutlinedButton(onClick = minus, enabled = minusEnabled, contentPadding = PaddingValues(0.dp),
        modifier = Modifier.size(48.dp).semantics { contentDescription = "Retirer un exemplaire : $label" },
        shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, PremiumGold)) { Text("−", color = PremiumGold) }
    Text(quantity.toString(), Modifier.padding(horizontal = 10.dp).testTag("inventory_quantity"), color = Color.White, fontWeight = FontWeight.Bold)
    OutlinedButton(onClick = plus, enabled = plusEnabled, contentPadding = PaddingValues(0.dp),
        modifier = Modifier.size(48.dp).semantics { contentDescription = "Ajouter un exemplaire : $label" },
        shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, PremiumGold)) { Text("+", color = PremiumGold) }
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
        containerColor = PremiumNavy, contentColor = Color.White) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding()
            .imePadding().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Ajouter une variante", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CardArtwork(selected?.card ?: card, Modifier.size(64.dp, 90.dp), language = language, variant = selected?.variant ?: "Normale", showLanguageLabel = false)
                Text(card.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                TextButton(onClick = onDismiss, enabled = !busy) { Text("Fermer") }
            }
            Text("Langue", fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CardLanguages.supported.forEach { code -> FilterChip(language == code, { language = code }, enabled = !busy, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = PremiumGold, selectedLabelColor = PremiumNavy), label = { Text(CardLanguages.label(code)) }) }
            }
            Text("Finition", fontWeight = FontWeight.Bold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                prints.map { it.variant }.distinct().forEach { finish -> FilterChip(selected?.variant == finish,
                    { selected = prints.first { it.variant == finish } }, enabled = !busy, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = PremiumGold, selectedLabelColor = PremiumNavy), label = { Text(finish) }) }
            }
            val references = prints.filter { it.variant == selected?.variant }
            if (references.size > 1) {
                Text("Édition et numéro", fontWeight = FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    references.forEach { print -> FilterChip(selected == print, { selected = print }, enabled = !busy,
                        label = { Text("${print.card.setCode} · ${print.card.number}") }) }
                }
            }
            selected?.let { Text("${it.card.setCode} · ${it.card.number} · ${it.variant}", color = ListPalette.Secondary) }
            Text("Quantité", fontWeight = FontWeight.Bold)
            Row(Modifier.align(Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                InventoryQuantity(quantity, { quantity-- }, { quantity++ }, card.name, !busy && quantity < 9999, !busy && quantity > 1)
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = {
                selected?.let { print ->
                    busy = true
                    runCatching { onConfirm(print.card, language, print.variant, quantity) }
                        .onSuccess { onDismiss() }.onFailure { busy = false; error = it.message ?: "Ajout non enregistré. Réessaie." }
                }
            }, enabled = selected != null && !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = PremiumGold, contentColor = PremiumNavy)) {
                Text("Ajouter à ma collection", fontWeight = FontWeight.Bold)
            }
        }
    }
}
