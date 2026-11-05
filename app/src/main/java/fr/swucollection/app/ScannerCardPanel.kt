package fr.swucollection.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

internal fun scannerDestination(deckName: String? = null): String =
    if (deckName == null) "Ajouter à ma collection" else "Ajouter à « ${deckName.ifBlank { "Mon deck" }} »"

@Composable
internal fun ScannerModeChoices(mode: String, enabled: Boolean = true, onChange: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Une par une", "Rafale").forEach { option ->
            FilterChip(mode == option, enabled = enabled, onClick = { onChange(option) }, label = { Text(option) })
        }
    }
}

/** Uses the already refreshed price cache: recognition never waits on a network call. */
@Composable
internal fun ScannerCardSummary(card: CatalogCard, variant: String, status: String? = null) {
    val context = LocalContext.current.applicationContext
    val quote by produceState<MarketQuote?>(null, card.setCode, card.number, variant) {
        value = if (variant !in ScanVariants.options) MarketQuote(null) else withContext(Dispatchers.IO) {
            runCatching { CardmarketPrices.quote(context, card, variant, false, false) }.getOrElse { MarketQuote(null) }
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CardArtwork(card, Modifier.width(58.dp).height(82.dp), language = card.language, variant = variant)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(card.name, color = ListPalette.Text, fontWeight = FontWeight.SemiBold)
            Text("${card.setCode} ${card.number} · ${CardLanguages.label(card.language)} · ${variant.ifBlank { "Finition à vérifier" }}",
                color = ListPalette.Secondary, style = MaterialTheme.typography.labelSmall)
            val amount = quote?.amount
            val money = remember { NumberFormat.getCurrencyInstance(Locale.FRANCE) }
            Text(when {
                variant !in ScanVariants.options -> "Cote : confirmer la finition"
                quote == null -> "Cote en cours…"
                amount == null -> "Cote indisponible"
                quote?.maxAmount != null && quote!!.maxAmount!! > amount -> "Cote : ${money.format(amount)} – ${money.format(quote!!.maxAmount)}"
                else -> "Cote : ${money.format(amount)}"
            }, color = ListPalette.Gold, style = MaterialTheme.typography.labelMedium)
            status?.let { Text(it, color = ListPalette.Secondary, style = MaterialTheme.typography.labelSmall) }
        }
    }
}

@Composable
internal fun ScannerCardConfirmation(card: CatalogCard, initialLanguage: String, initialVariant: String,
    destination: String, error: String? = null, onCancel: () -> Unit, onConfirm: (String, String) -> Unit) {
    var language by remember(card, initialLanguage) { mutableStateOf(initialLanguage) }
    var variant by remember(card, initialVariant) { mutableStateOf(initialVariant) }
    var correcting by remember(card) { mutableStateOf(false) }
    val print = remember(card) { ScanVariants.detect(card) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ScannerCardSummary(card.copy(language = language), variant, "Carte reconnue · à confirmer")
        if (correcting || language !in CardLanguages.supported) CardLanguageChoices(language, { language = it })
        if (variant !in print.choices || (correcting && print.variant == null)) {
            Text("Choisir la finition", color = ListPalette.Gold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                print.choices.forEach { option ->
                    FilterChip(variant == option, onClick = { variant = option }, label = { Text(option) })
                }
            }
        }
        error?.let { Text(it, color = ListPalette.Danger) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onCancel) { Text("Annuler") }
            Button(onClick = { onConfirm(language, variant) },
                enabled = language in CardLanguages.supported && variant in print.choices,
                modifier = Modifier.weight(1f)) { Text("Valider · $destination") }
        }
        TextButton(onClick = { correcting = !correcting }) { Text(if (correcting) "Masquer les détails" else "Corriger la langue ou la finition") }
    }
}
