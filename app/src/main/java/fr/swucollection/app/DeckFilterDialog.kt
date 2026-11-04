package fr.swucollection.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun DeckChoice(label: String, value: String, choices: List<Pair<String, String>>, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label : ${choices.firstOrNull { it.first == value }?.second ?: value}", maxLines = 2)
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 280.dp)) {
            choices.forEach { (key, title) ->
                DropdownMenuItem(text = { Text(title) }, onClick = { onChange(key); expanded = false })
            }
        }
    }
}

@Composable
internal fun DeckFilterDialog(filters: DeckBrowserFilters, catalog: List<CatalogCard>, details: CardDetails,
    onChange: (DeckBrowserFilters) -> Unit, onDismiss: () -> Unit, identity: Boolean = false) {
    val facets = remember(catalog, details) { catalog.mapNotNull(details::get) }
    fun values(items: List<String>) = listOf("" to "Tous") + items.filter { it.isNotBlank() }.distinct().sorted().map { it to it }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Filtres du catalogue") }, text = {
        Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DeckChoice("Affinité", filters.aspect, listOf("" to "Toutes") + aspectNames.map { it.key to it.value }) { onChange(filters.copy(aspect = it)) }
            if (!identity) {
                DeckChoice("Type", filters.type, values(catalog.filter(DeckConstruction::isDraw).map { it.type })) { onChange(filters.copy(type = it)) }
                DeckChoice("Coût", filters.cost, listOf("" to "Tous") + (0..7).map { it.toString() to it.toString() } + ("8+" to "8 ou plus")) { onChange(filters.copy(cost = it)) }
            }
            DeckChoice("Extension", filters.set, listOf("" to "Toutes") + DeckBrowser.extensionOrder(catalog).map { it to it }) { onChange(filters.copy(set = it)) }
            if (!identity) {
            DeckChoice("Arène", filters.arena, listOf("" to "Toutes") + facets.map { it.arena }.filter { it.isNotBlank() }.distinct().sorted().map {
                it to when (it.lowercase()) { "ground", "ground arena", "terrestre" -> "Terrestre"; "space", "space arena", "spatiale" -> "Spatiale"; else -> it }
            }) { onChange(filters.copy(arena = it)) }
            }
            DeckChoice("Trait", filters.trait, values(facets.flatMap { it.traits })) { onChange(filters.copy(trait = it)) }
            DeckChoice("Rareté", filters.rarity, values(catalog.map { it.rarity })) { onChange(filters.copy(rarity = it)) }
            DeckChoice("Collection", filters.stock, listOf("" to "Toutes les cartes", "owned" to "Déjà possédées", "absent" to "Absentes de ma collection")) { onChange(filters.copy(stock = it)) }
            Text("Les filtres s’appliquent au catalogue et à la sélection. Une carte sans donnée connue ne correspond pas à ce filtre.", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Voir les cartes") } },
        dismissButton = { TextButton(onClick = { onChange(DeckBrowserFilters()) }) { Text("Tout effacer") } })
}
