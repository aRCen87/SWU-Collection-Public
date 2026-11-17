package fr.swucollection.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Opening actions never changes stock. Each mutation needs an explicit action. */
@Composable
internal fun CardDestinationDialog(card: CatalogCard, onDismiss: () -> Unit,
    onCollection: () -> Unit, onDeck: () -> Unit, onList: () -> Unit, deckEnabled: Boolean = true) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(card.name) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Où veux-tu ajouter ou gérer cette carte ?")
            OutlinedButton(onClick = onCollection, modifier = Modifier.fillMaxWidth()) { Text("Ma collection") }
            OutlinedButton(onClick = onDeck, enabled = deckEnabled, modifier = Modifier.fillMaxWidth()) { Text("Un deck") }
            OutlinedButton(onClick = onList, modifier = Modifier.fillMaxWidth()) { Text("Une liste") }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } })
}

@Composable
internal fun CardQuantityDialog(name: String, quantity: Int, onDismiss: () -> Unit,
    onQuantity: (Int) -> Unit, maximum: Int = 99) {
    var removing by remember(name) { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(name) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("$quantity exemplaire(s)")
            OutlinedButton(onClick = { onQuantity(quantity + 1); onDismiss() }, enabled = quantity < maximum,
                modifier = Modifier.fillMaxWidth()) { Text("+1 exemplaire") }
            OutlinedButton(onClick = { onQuantity(quantity - 1); onDismiss() }, enabled = quantity > 1,
                modifier = Modifier.fillMaxWidth()) { Text("−1 exemplaire") }
            TextButton(onClick = { removing = true }) { Text("Retirer la carte") }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } })
    if (removing) AlertDialog(onDismissRequest = { removing = false },
        title = { Text("Retirer cette carte ?") }, text = { Text("Les $quantity exemplaire(s) seront retirés de cet emplacement.") },
        confirmButton = { TextButton(onClick = { onQuantity(0); onDismiss() }) { Text("Retirer") } },
        dismissButton = { TextButton(onClick = { removing = false }) { Text("Annuler") } })
}
