package fr.swucollection.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun SettingsOverview(onNavigate: (String) -> Unit, updateAvailable: String?) {
    Text("Retrouve ici les réglages et l’entretien de ton application.",
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    listOf(
        Triple("Hors connexion", "Images des decks et extensions sur ce téléphone", "Hors connexion"),
        Triple("Apparence", "Accueil et reflets des bordures dorées", "Apparence"),
        Triple("Mises à jour", updateAvailable?.let { "Version $it disponible" }
            ?: "Application, catalogue et rotation des cartes", "Mises à jour"),
        Triple("Sauvegarder et restaurer", "Collection, listes et changement de téléphone", "Sauvegardes")
    ).forEach { (title, detail, destination) ->
        OutlinedCard(onClick = { onNavigate(destination) }, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("$title  ›", fontWeight = FontWeight.SemiBold)
                Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
