package fr.swucollection.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun SettingsOverview(onNavigate: (String) -> Unit, updateAvailable: String?) {
    Text("Retrouve ici les réglages et l’entretien de ton application.",
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    listOf(
        Triple("Mon compte", "Profil, connexion et préférences personnelles", "Communauté"),
        Triple("Hors connexion", "Images des decks et extensions sur ce téléphone", "Hors connexion"),
        Triple("Apparence", "Accueil et reflets des bordures dorées", "Apparence"),
        Triple("Mises à jour", updateAvailable?.let { "Version $it disponible" }
            ?: "Application, catalogue et rotation des cartes", "Mises à jour"),
        Triple("Sauvegarder et restaurer", "Collection, listes et changement de téléphone", "Sauvegardes")
    ).forEach { (title, detail, destination) ->
        OutlinedCard(onClick = { onNavigate(destination) }, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(when (destination) {
                        "Communauté" -> Icons.Default.AccountCircle
                        "Hors connexion" -> Icons.Default.CloudOff
                        "Apparence" -> Icons.Default.Palette
                        "Mises à jour" -> Icons.Default.SystemUpdate
                        else -> Icons.Default.Backup
                    }, null, tint = MaterialTheme.colorScheme.primary)
                    Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ChevronRight, null)
                }
                Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
