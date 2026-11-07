package fr.swucollection.app

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

internal object FirstSteps {
    fun completed(context: Context) = context.getSharedPreferences("first_steps", 0).getBoolean("completed", false)
    fun complete(context: Context) { context.getSharedPreferences("first_steps", 0).edit().putBoolean("completed", true).apply() }
}

@Composable
internal fun FirstStepsCard(emptyCollection: Boolean, onGuide: () -> Unit) {
    val context = LocalContext.current
    var hidden by remember { mutableStateOf(FirstSteps.completed(context)) }
    if (!emptyCollection || hidden || FirstSteps.completed(context)) return
    OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Ta première carte en trois étapes", style = MaterialTheme.typography.titleMedium)
        Text("Un guide rapide pour scanner, distinguer les versions et retrouver ta carte.")
        Row { Button(onClick = onGuide) { Text("Découvrir") }; TextButton(onClick = { FirstSteps.complete(context); hidden = true }) { Text("Plus tard") } }
    } }
}

@Composable
internal fun FirstStepsDialog(onClose: () -> Unit, onScan: () -> Unit) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableIntStateOf(0) }
    val titles = listOf("Scanner ta première carte", "Reconnaître les versions", "Vérifier et retrouver ta carte")
    val descriptions = listOf(
        "Pose une carte à plat, dans une lumière diffuse. Place-la dans le cadre : le scan se lance automatiquement. En cas d’hésitation, choisis parmi les images proposées.",
        "Normale : illustration et bordure classiques. Foil : surface brillante. Hyperspace : illustration étendue. Hyperspace Foil combine les deux. Un même numéro peut avoir plusieurs finitions : confirme celle que tu as en main. Vérifie aussi la langue.",
        "En mode Une par une, confirme la carte pour l’ajouter. En Rafale, touche Terminer puis vérifie et valide le lot : il n’est ajouté qu’à cette étape. Retrouve ensuite ta carte dans Ma collection. Avec un compte connecté, la sauvegarde privée indique sa date et les changements en attente."
    )
    StableCollectorDialog(onDismissRequest = onClose, title = { Text("${step + 1}/3 · ${titles[step]}") },
        text = { Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            LinearProgressIndicator(progress = { (step + 1) / 3f }, modifier = Modifier.fillMaxWidth())
            Text(descriptions[step])
            Text("Tu pourras rouvrir ce guide depuis Paramètres.", style = MaterialTheme.typography.bodySmall)
        } },
        confirmButton = { Button(onClick = { if (step < 2) step++ else { FirstSteps.complete(context); onScan() } }) { Text(if (step < 2) "Suivant" else "Scanner ma première carte") } },
        dismissButton = { TextButton(onClick = { if (step > 0) step-- else onClose() }) { Text(if (step > 0) "Précédent" else "Fermer") } })
}
