package fr.swucollection.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

internal data class CollectionAddRequest(val card: CatalogCard, val variant: String, val language: String)

/** Selecting or opening a card never writes stock; only the labelled confirmation does. */
@Composable
internal fun CollectionAddConfirmation(request: CollectionAddRequest, onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit) {
    val finishes = ScanVariants.detect(request.card).choices
    var finish by remember(request) { mutableStateOf(request.variant.takeIf { it in finishes } ?: finishes.firstOrNull().orEmpty()) }
    var language by remember(request) { mutableStateOf(request.language.takeIf { it in CardLanguages.supported } ?: "FR") }
    var error by remember(request) { mutableStateOf<String?>(null) }
    var confirming by remember(request) { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Ajouter 1 exemplaire ?") }, text = {
        Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(request.card.name, style = MaterialTheme.typography.titleMedium)
            Text("${request.card.setCode} · ${request.card.number}")
            CardArtwork(request.card, Modifier.fillMaxWidth().height(160.dp), language = language, variant = finish)
            CardLanguageChoices(language, { language = it })
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                finishes.forEach { option -> FilterChip(finish == option, onClick = { finish = option }, label = { Text(option) }) }
            }
            Text("${CardLanguages.label(language)} · $finish · +1 dans ta collection")
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { Button(enabled = !confirming && language in CardLanguages.supported && finish in finishes,
        onClick = {
            confirming = true
            runCatching { onConfirm(language, finish) }.onSuccess { onDismiss() }
                .onFailure { confirming = false; error = it.message ?: "Ajout non enregistré. Réessaie." }
        }) { Text("Confirmer l’ajout") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !confirming) { Text("Annuler") } })
}
