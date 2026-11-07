package fr.swucollection.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType

internal fun replaceBatchEntry(entries: List<ScanBatchEntry>, original: ScanBatchEntry, card: CatalogCard, language: String, finish: String, quantity: Int): List<ScanBatchEntry> {
    require(original in entries && quantity in 1..9999 && language in CardLanguages.supported && finish in ScanVariants.detect(card).choices)
    val replacement = ScanBatchEntry(card.copy(language = language), finish, quantity)
    val rest = entries.toMutableList().apply { remove(original) }
    val existing = rest.indexOfFirst { !it.needsIdentity && it.card.setCode == card.setCode && it.card.number == card.number && it.card.language == language && it.variant == finish }
    if (existing < 0) rest += replacement else {
        val total = rest[existing].quantity.toLong() + quantity; require(total <= 9999)
        rest[existing] = replacement.copy(quantity = total.toInt())
    }
    return rest
}
@Composable
internal fun ScanBatchCorrection(entry: ScanBatchEntry, catalog: List<CatalogCard>, close: () -> Unit, apply: (CatalogCard, String, String, Int) -> Unit) {
    var selected by remember(entry) { mutableStateOf(entry.card) }; var query by remember { mutableStateOf("") }
    var language by remember { mutableStateOf(entry.card.language) }; var finish by remember { mutableStateOf(entry.variant) }
    var quantity by remember { mutableStateOf(entry.quantity.toString()) }; var menu by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = close, title = { Text(if (entry.needsIdentity) "Identifier la carte en attente" else "Corriger le scan") }, text = { Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ScannerCardSummary(selected, finish)
        if (entry.needsIdentity) Text("L’image donne une suggestion, pas une identification certaine. Confirme la bonne référence avant l’ajout.")
        OutlinedTextField(query, { query = it.take(100) }, label = { Text("Autre carte : nom, extension ou numéro") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (query.isNotBlank()) catalog.filter { it.name.contains(query, true) || "${it.setCode} ${it.number}".contains(query, true) }.take(15).forEach { card ->
            TextButton(onClick = { selected = card; finish = ""; query = "" }) { Text("${card.name} · ${card.setCode} ${card.number}") }
        }
        Text("Langue de l’exemplaire")
        CardLanguageChoices(language, { language = it })
        Box { OutlinedButton(onClick = { menu = true }) { Text(finish.ifBlank { "Confirmer la finition" }) }; DropdownMenu(menu, { menu = false }) {
            ScanVariants.detect(selected).choices.forEach { value -> DropdownMenuItem(text = { Text(value) }, onClick = { finish = value; menu = false }) }
        } }
        Text("Un reflet ne suffit pas à identifier une foil. Choisis la finition de ta carte.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(quantity, { quantity = it.filter(Char::isDigit).take(4) }, label = { Text("Quantité de cette ligne") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    } }, confirmButton = { TextButton(onClick = { runCatching { apply(selected, language, finish, quantity.toInt()) }.onFailure { error = it.message } }, enabled = language in CardLanguages.supported && finish in ScanVariants.detect(selected).choices && (quantity.toIntOrNull() ?: 0) in 1..9999) { Text("Confirmer cette ligne") } }, dismissButton = { TextButton(onClick = close) { Text("Annuler") } })
}
