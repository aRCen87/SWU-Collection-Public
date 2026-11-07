package fr.swucollection.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

internal fun scannerVisualChoices(references: List<Pair<String, String>>, catalog: List<CatalogCard>): List<CatalogCard> {
    val byKey = catalog.associateBy { MarketPricing.key(it.setCode, it.number) }
    return references.mapNotNull { (set, number) -> byKey[MarketPricing.key(set, number)] }
        .distinctBy { MarketPricing.key(it.setCode, it.number) }.take(2)
}

@Composable
internal fun ScannerVisualChoiceDialog(cards: List<CatalogCard>, error: String?, onRetry: () -> Unit, onChoose: (CatalogCard) -> Unit) {
    StableCollectorDialog(onDismissRequest = onRetry,
        title = { Text("Quelle est ta carte ?") },
        text = {
            Column(Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("L’image ressemble à ces cartes. Choisis la bonne, puis confirme sa langue et sa finition. Aucun ajout avant ton choix.")
                cards.forEach { card -> OutlinedCard(onClick = { onChoose(card) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CardArtwork(card, Modifier.size(78.dp, 110.dp))
                        Column(Modifier.weight(1f)) {
                            Text(card.name, style = MaterialTheme.typography.titleSmall)
                            Text("${card.setCode} · ${card.number}")
                            Text("${card.type} · ${card.rarity}", style = MaterialTheme.typography.bodySmall)
                            Text("Choisir cette carte", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                } }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }, confirmButton = {}, dismissButton = { TextButton(onClick = onRetry) { Text("Aucune ne correspond · Relire") } })
}
