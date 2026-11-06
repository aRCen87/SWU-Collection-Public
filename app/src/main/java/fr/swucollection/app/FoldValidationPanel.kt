package fr.swucollection.app

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Physical fold and camera checks must be performed by the owner on their own device. */
@Composable
internal fun ColumnScope.FoldValidationPanel(onCollection: () -> Unit, onScan: () -> Unit) {
    val context = LocalContext.current; val configuration = LocalConfiguration.current
    val version = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }
    val prefs = remember { context.getSharedPreferences("fold_validation", 0) }
    val checks = listOf("Déplier puis replier en gardant la même carte affichée",
        "Passer à la carte suivante puis précédente sans fermer la fiche",
        "Saisir une recherche avec le clavier ouvert, puis déplier et replier",
        "Tourner le téléphone et utiliser les boutons en paysage",
        "Pendant un scan, déplier et replier : la caméra revient sans compter deux fois la même carte",
        "Utiliser l’appli à côté d’une autre fenêtre sans texte ou boutons coupés")
    var checked by remember(version) { mutableStateOf(checks.indices.filter { prefs.getBoolean("$version.$it", false) }.toSet()) }
    var copied by remember { mutableStateOf(false) }
    Text("Vérification sur mon écran pliant", style = MaterialTheme.typography.titleLarge)
    Text("Les formats simulés sont contrôlés automatiquement. Cette liste permet de vérifier la charnière, le clavier et la caméra sur ton vrai téléphone.", style = MaterialTheme.typography.bodySmall)
    Text("${Build.MODEL} · $version · fenêtre ${configuration.screenWidthDp} × ${configuration.screenHeightDp}")
    Text("${checked.size} / ${checks.size} vérifications cochées par toi")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(onClick = onCollection) { Text("Essayer la collection") }
        OutlinedButton(onClick = onScan) { Text("Essayer le scanner") }
    }
    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(checks.size) { i -> OutlinedCard(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(i in checked, { value ->
                    checked = if (value) checked + i else checked - i
                    prefs.edit().putBoolean("$version.$i", value).apply()
                })
                Text(checks[i], Modifier.weight(1f))
            }
        } }
        item { OutlinedButton(onClick = {
            val report = "SWU Collection Test — vérification pliant\n${Build.MODEL} · $version\nFenêtre ${configuration.screenWidthDp} × ${configuration.screenHeightDp}\n" +
                checks.mapIndexed { i, text -> (if (i in checked) "Vérifié : " else "À essayer : ") + text }.joinToString("\n")
            context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Vérification pliant", report)); copied = true
        }) { Text(if (copied) "Compte rendu copié" else "Copier mon compte rendu") } }
    }
}
