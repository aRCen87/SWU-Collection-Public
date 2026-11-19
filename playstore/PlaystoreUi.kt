package fr.swucollection.app

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
internal fun PlaystoreHomeControls(onMenu: () -> Unit, onCounter: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.End) {
        IconButton(onClick = onCounter) { Icon(Icons.Default.Favorite, "Compteur de PV", tint = PremiumGold) }
        IconButton(onClick = onMenu) { Icon(Icons.Default.Settings, "Paramètres", tint = PremiumGold) }
    }
}

@Composable
internal fun PlaystoreUpdatePanel() {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("SWU Collection Playstore", style = MaterialTheme.typography.titleMedium)
            Text("Les mises à jour de cette application sont distribuées par Google Play.")
            OutlinedButton(onClick = {
                val uri = Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}")
                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            }) { Text("Ouvrir Google Play") }
        }
    }
}
