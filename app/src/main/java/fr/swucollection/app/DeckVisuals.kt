package fr.swucollection.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun DeckIdentityCard(label: String, card: CatalogCard?, reference: String,
    modifier: Modifier = Modifier, onPreview: () -> Unit, onChange: () -> Unit) {
    Surface(modifier, shape = RoundedCornerShape(12.dp), color = ListPalette.Background,
        border = BorderStroke(1.dp, ListPalette.Gold.copy(alpha = .5f))) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, color = ListPalette.Gold, style = MaterialTheme.typography.labelLarge)
            CardArtwork(card?.imageUrl.orEmpty(), Modifier.fillMaxWidth().height(74.dp)
                .clip(RoundedCornerShape(6.dp)).clickable(enabled = card != null, onClick = onPreview)
                .semantics { contentDescription = "Agrandir $label" })
            Text(card?.name ?: reference, maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = ListPalette.Text, style = MaterialTheme.typography.labelMedium)
            TextButton(onClick = onChange, contentPadding = PaddingValues(horizontal = 4.dp)) {
                Text("Changer $label", maxLines = 1, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
internal fun DeckCardTile(card: CatalogCard, visual: Boolean, quantity: Int, owned: Int, missing: Int,
    slot: Boolean, selected: Boolean, canAdd: Boolean, onPreview: () -> Unit,
    onChoose: () -> Unit, onQuantity: (Int) -> Unit, moveLabel: String = "", canMove: Boolean = false, onMove: () -> Unit = {}, guidance: String? = null) {
    Surface(shape = RoundedCornerShape(12.dp), color = ListPalette.Raised,
        border = BorderStroke(1.dp, ListPalette.Gold.copy(alpha = if (selected || quantity > 0) .8f else .28f))) {
        @Composable fun artwork(modifier: Modifier) {
            Box(modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onPreview)
                .semantics { contentDescription = "Agrandir ${card.name}" }) {
                CardArtwork(card.imageUrl, Modifier.fillMaxSize())
                if (!slot && quantity > 0) Surface(Modifier.align(Alignment.TopEnd).padding(4.dp),
                    shape = RoundedCornerShape(7.dp), color = ListPalette.Gold) {
                    Text("×$quantity", Modifier.padding(horizontal = 6.dp, vertical = 2.dp), color = ListPalette.Background)
                }
            }
        }
        @Composable fun information(modifier: Modifier = Modifier) {
            Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(card.name, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    color = ListPalette.Text, style = MaterialTheme.typography.labelLarge)
                Text("${card.setCode} ${card.number}", color = ListPalette.Secondary, style = MaterialTheme.typography.labelSmall)
                guidance?.let { Text(it, color = ListPalette.Gold, style = MaterialTheme.typography.labelSmall) }
                Text("Possédées : $owned", color = ListPalette.Secondary, style = MaterialTheme.typography.labelSmall)
                if (missing > 0) Text("Manquantes : $missing", color = ListPalette.Gold, style = MaterialTheme.typography.labelSmall)
            }
        }
        @Composable fun controls() {
            if (slot) {
                TextButton(onClick = onChoose, modifier = Modifier.fillMaxWidth()) {
                    Text(if (selected) "✓ Sélectionné" else "Choisir", color = ListPalette.Gold)
                }
            } else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = { onQuantity(quantity - 1) }, enabled = quantity > 0, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Remove, contentDescription = "Retirer ${card.name}")
                }
                Text(quantity.toString(), color = ListPalette.Gold)
                IconButton(onClick = { onQuantity(quantity + 1) }, enabled = canAdd, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Add, contentDescription = "Ajouter ${card.name}")
                }
            }
        }
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (visual) {
                artwork(Modifier.fillMaxWidth().aspectRatio(if (DeckConstruction.type(card) in setOf("leader", "chef", "base")) 1.4f else 5f / 7f))
                information()
            } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                artwork(Modifier.size(58.dp, 82.dp))
                information(Modifier.weight(1f))
            }
            controls()
            if (!slot && quantity > 0) TextButton(onClick = onMove, enabled = canMove,
                modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
                Text(moveLabel, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
