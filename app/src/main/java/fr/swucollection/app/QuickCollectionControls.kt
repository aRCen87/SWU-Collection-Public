package fr.swucollection.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

/** Choices never move existing stock. Only the separate +/- controls write a copy. */
@Composable
internal fun QuickVariantSelectors(card: CatalogCard, language: String, finish: String,
    onLanguage: (String) -> Unit, onFinish: (String) -> Unit, modifier: Modifier = Modifier, compact: Boolean = false) {
    var languagesOpen by remember { mutableStateOf(false) }
    var finishesOpen by remember { mutableStateOf(false) }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box {
            TextButton(onClick = { languagesOpen = true }, contentPadding = PaddingValues(horizontal = 4.dp),
                modifier = (if (compact) Modifier.width(32.dp) else Modifier).semantics { contentDescription = "Langue de la carte : ${CardLanguages.label(language)}" }) {
                Text("${if (language in CardLanguages.supported) language else "Langue"} ▾", maxLines = 1, style = MaterialTheme.typography.labelSmall)
            }
            DropdownMenu(languagesOpen, { languagesOpen = false }) {
                CardLanguages.supported.forEach { code -> DropdownMenuItem(text = { Text(CardLanguages.label(code)) },
                    onClick = { onLanguage(code); languagesOpen = false }) }
            }
        }
        Box {
            TextButton(onClick = { finishesOpen = true }, contentPadding = PaddingValues(horizontal = 4.dp),
                modifier = (if (compact) Modifier.width(64.dp) else Modifier).semantics { contentDescription = "Finition de la carte : $finish" }) {
                Text("$finish ▾", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall)
            }
            DropdownMenu(finishesOpen, { finishesOpen = false }) {
                ScanVariants.detect(card).choices.forEach { value -> DropdownMenuItem(text = { Text(value) },
                    onClick = { onFinish(value); finishesOpen = false }) }
            }
        }
    }
}

@Composable
internal fun CompactCardQuote(card: CatalogCard, finish: String, modifier: Modifier = Modifier, refreshKey: Any? = null) {
    val context = LocalContext.current.applicationContext
    val quote by produceState<MarketQuote?>(null, card.setCode, card.number, finish, refreshKey) {
        value = withContext(Dispatchers.IO) {
            runCatching { CardmarketPrices.quote(context, card, finish, false, allowNetwork = false) }.getOrNull()
        }
    }
    val format = remember { NumberFormat.getCurrencyInstance(Locale.FRANCE) }
    val text = quote?.amount?.let { format.format(it) + if (quote?.maxAmount != null && quote?.maxAmount != it) "+" else "" } ?: "— €"
    Text(text, modifier.semantics { contentDescription = if (quote?.amount == null) "Cote indisponible" else "Cote $text, toutes langues" },
        color = ListPalette.Gold, maxLines = 1, style = MaterialTheme.typography.labelSmall)
}

@Composable
internal fun QuickStockBar(quantity: Int, valid: Boolean, onChange: (Int) -> Unit, detailed: Boolean = false) {
    Surface(color = ListPalette.Background, shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, ListPalette.Secondary.copy(alpha = .45f))) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onChange(-1) }, enabled = valid && quantity > 0,
                modifier = Modifier.size(48.dp).semantics { contentDescription = "Retirer un exemplaire" }, contentPadding = PaddingValues(0.dp)) { Text("−") }
            Text(if (detailed) "$quantity " + if (quantity > 1) "possédées" else "possédée" else quantity.toString(),
                Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = MaterialTheme.typography.titleSmall)
            Button(onClick = { onChange(1) }, enabled = valid && quantity < 9999,
                modifier = Modifier.size(48.dp).semantics { contentDescription = "Ajouter un exemplaire" },
                shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ListPalette.Gold, contentColor = ListPalette.Background)) { Text("+") }
        }
    }
}

internal fun quickStockQuantity(owned: List<CollectionCard>, card: CatalogCard, language: String, finish: String): Int =
    owned.filter { MarketPricing.key(it.setCode, it.number) == MarketPricing.key(card.setCode, card.number) &&
        it.language == language && it.variant == finish }.sumOf { it.quantity }

/** Read latest SQLite rows for each tap, including taps before recomposition. */
internal fun changeQuickStock(db: CollectionDatabase, card: CatalogCard, language: String, finish: String, delta: Int): Long? {
    require(language in CardLanguages.supported && finish in ScanVariants.detect(card).choices)
    require(delta == 1 || delta == -1)
    val row = db.allCards().firstOrNull { MarketPricing.key(it.setCode, it.number) == MarketPricing.key(card.setCode, card.number) && it.language == language && it.variant == finish }
    if (delta < 0 && (row?.quantity ?: 0) == 0) return null
    if (delta > 0) db.addOrIncrement(card.name, row?.setCode ?: card.setCode, row?.number ?: card.number, finish, language)
    else db.changeQuantity(row!!.id, -1)
    return db.history().firstOrNull { !it.undone }?.id
}
