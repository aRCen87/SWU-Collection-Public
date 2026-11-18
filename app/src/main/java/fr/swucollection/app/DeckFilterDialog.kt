package fr.swucollection.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun DeckChoice(label: String, value: String, choices: List<Pair<String, String>>, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Text("$label : ${choices.firstOrNull { it.first == value }?.second ?: value}", modifier = Modifier.weight(1f))
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 280.dp)) {
            choices.forEach { (key, title) ->
                DropdownMenuItem(text = { Text(title) }, onClick = { onChange(key); expanded = false })
            }
        }
    }
}

@Composable
internal fun DeckMultiChoice(label: String, value: String, choices: List<Pair<String, String>>, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = value.split("|").filter { it.isNotEmpty() }.toSet()
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            val titles = choices.filter { it.first in selected }.map { it.second }
            Text("$label : ${if (titles.isEmpty()) "Tous" else titles.joinToString(", ")}", modifier = Modifier.weight(1f))
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 320.dp)) {
            DropdownMenuItem(text = { Text("Tout désélectionner") }, onClick = { onChange("") })
            choices.filter { it.first.isNotEmpty() }.forEach { (key, title) ->
                DropdownMenuItem(text = { if (label == "Couleurs") AspectBadge(key, showColorName = true) else if (label == "Raretés") RarityFilterLabel(title) else Text(title) }, leadingIcon = { Checkbox(checked = key in selected, onCheckedChange = null) },
                    onClick = { onChange((if (key in selected) selected - key else selected + key).joinToString("|")) })
            }
            DropdownMenuItem(text = { Text("Terminer") }, onClick = { expanded = false })
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
            OutlinedTextField(value = filters.keywords, onValueChange = { onChange(filters.copy(keywords = it)) },
                label = { Text("Mots-clés et capacités") }, placeholder = { Text("Sentinelle, défausse, bouclier…") },
                supportingText = { Text("Tous les mots saisis sont recherchés. Se combine avec les autres filtres ; couverture selon les textes disponibles.") },
                modifier = Modifier.fillMaxWidth(), maxLines = 3)
            DeckMultiChoice("Couleurs", filters.aspect, listOf(
                "Vigilance" to "Bleu · Vigilance", "Command" to "Vert · Commandement",
                "Aggression" to "Rouge · Agressivité", "Cunning" to "Jaune · Ruse",
                "Heroism" to "Blanc · Héroïsme", "Villainy" to "Noir · Infâmie")) { onChange(filters.copy(aspect = it)) }
            if (!identity) {
                DeckChoice("Type", filters.type, values(catalog.filter(DeckConstruction::isDraw).map { it.type })) { onChange(filters.copy(type = it)) }
                DeckChoice("Coût", filters.cost, listOf("" to "Tous") + (0..7).map { it.toString() to it.toString() } + ("8+" to "8 ou plus")) { onChange(filters.copy(cost = it)) }
            }
            DeckMultiChoice("Extensions", filters.set, listOf("" to "Toutes") + DeckBrowser.extensionOrder(catalog).map { it to it }) { onChange(filters.copy(set = it)) }
            if (!identity) {
            DeckMultiChoice("Unités", filters.arena, listOf("ground" to "Terrestres", "space" to "Spatiales")) { onChange(filters.copy(arena = it)) }
            }
            DeckMultiChoice("Traits", filters.trait, values(facets.flatMap { it.traits })) { onChange(filters.copy(trait = it)) }
            DeckMultiChoice("Raretés", filters.rarity, values(catalog.map { it.rarity })) { onChange(filters.copy(rarity = it)) }
            DeckChoice("Collection", filters.stock, listOf("" to "Toutes les cartes", "owned" to "Déjà possédées", "absent" to "Absentes de ma collection")) { onChange(filters.copy(stock = it)) }
            Text("Plusieurs choix possibles : une des valeurs de chaque catégorie sélectionnée. Les catégories se combinent. Une donnée inconnue ne correspond pas au filtre.", style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Voir les cartes") } },
        dismissButton = { TextButton(onClick = { onChange(DeckBrowserFilters()) }) { Text("Tout effacer") } })
}

@Composable
private fun RarityFilterLabel(title: String) {
    val key = title.lowercase(java.util.Locale.ROOT)
    val symbol = when {
        key.contains("legend") || key.contains("légend") -> Icons.Default.AutoAwesome
        key.contains("uncommon") || key.contains("peu commune") -> Icons.Default.Diamond
        key.contains("rare") -> Icons.Default.Star
        else -> Icons.Default.Circle
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Icon(symbol, null, tint = if (symbol == Icons.Default.AutoAwesome) ListPalette.Gold else ListPalette.Secondary, modifier = Modifier.size(16.dp))
        Text(title)
    }
}

@Composable
internal fun DeckActiveFilterChips(filters: DeckBrowserFilters, onChange: (DeckBrowserFilters) -> Unit) {
    if (filters.activeCount == 0) return
    val choices = listOf(
        Triple("Couleurs", filters.aspect, { onChange(filters.copy(aspect = "")) }),
        Triple("Type", filters.type, { onChange(filters.copy(type = "")) }),
        Triple("Extensions", filters.set, { onChange(filters.copy(set = "")) }),
        Triple("Raretés", filters.rarity, { onChange(filters.copy(rarity = "")) }),
        Triple("Coût", filters.cost, { onChange(filters.copy(cost = "")) }),
        Triple("Unités", filters.arena, { onChange(filters.copy(arena = "")) }),
        Triple("Traits", filters.trait, { onChange(filters.copy(trait = "")) }),
        Triple("Collection", filters.stock, { onChange(filters.copy(stock = "")) }),
        Triple("Mots-clés", filters.keywords, { onChange(filters.copy(keywords = "")) })
    )
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        choices.filter { it.second.isNotEmpty() }.forEach { (label, value, clear) ->
            InputChip(true, onClick = clear, label = { Text("$label · ${value.split('|').size} ×") })
        }
    }
}
