package fr.swucollection.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
internal fun TestReleaseHighlights(catalog: List<CatalogCard>) {
    val context = LocalContext.current
    if (context.packageName != AppChannel.TEST_PACKAGE) return
    val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }
    val prefs = remember { context.getSharedPreferences("release_highlights", android.content.Context.MODE_PRIVATE) }
    var visible by remember(version) { mutableStateOf(prefs.getString("seen", "") != version) }
    if (!visible) return
    Surface(Modifier.fillMaxWidth(), color = ListPalette.Raised, shape = MaterialTheme.shapes.large) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CardArtwork(catalog.firstOrNull { DeckConstruction.type(it) in listOf("leader", "chef") }, Modifier.size(72.dp, 52.dp))
                Column(Modifier.weight(1f)) {
                    Text("Nouveautés · $version", style = MaterialTheme.typography.titleSmall, color = ListPalette.Gold)
                    Text("Un accueil et une collection plus lisibles", style = MaterialTheme.typography.bodySmall)
                }
            }
            Text("★ Favoris et choix de taille des cartes\n▤ Reprise du dernier deck et résumé avant enregistrement\n◈ Derniers scans visibles et fenêtre de sortie adaptée", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { prefs.edit().putString("seen", version).apply(); visible = false }) { Text("C’est noté") }
        }
    }
}
