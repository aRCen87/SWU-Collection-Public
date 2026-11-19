package fr.swucollection.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun SettingsOverview(onNavigate: (String) -> Unit, updateAvailable: String?) {
    listOf(
        "Personnalisation" to listOf(
            Triple("Mon compte", "Profil et confidentialité", "Communauté"),
            Triple("Apparence", "Accueil et effets visuels", "Apparence")),
        "Mes données" to listOf(
            Triple("Sauvegarder et restaurer", "Collection, decks et listes", "Sauvegardes"),
            Triple("Hors connexion", "Images sur ce téléphone", "Hors connexion")),
        "Application" to listOf(
            Triple("Mises à jour", updateAvailable?.let { "Version $it disponible" }
                ?: "Application et catalogue", "Mises à jour"))
    ).forEach { (section, entries) ->
    Text(section, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
    entries.forEachIndexed { index, (title, detail, destination) ->
        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .4f))
        Row(Modifier.fillMaxWidth().clickable(onClick = { onNavigate(destination) }).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(when (destination) {
                        "Communauté" -> Icons.Default.AccountCircle
                        "Hors connexion" -> Icons.Default.CloudOff
                        "Apparence" -> Icons.Default.Palette
                        "Mises à jour" -> Icons.Default.SystemUpdate
                        else -> Icons.Default.Backup
                    }, null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(title, fontWeight = FontWeight.SemiBold)
                        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    }
    }
}
