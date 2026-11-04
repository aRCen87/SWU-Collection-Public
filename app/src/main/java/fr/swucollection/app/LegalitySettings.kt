package fr.swucollection.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun LegalitySettingsPanel() {
    val snapshot by LegalitySync.state.collectAsState()
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Rotation et suspensions", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("Actualisation automatique à l’ouverture et environ toutes les 6 h en arrière-plan, avec Internet.", fontSize = 12.sp)
            Text("Dernier contrôle officiel : " + snapshot.rules.checkedAt.take(10).split("-").reversed().joinToString("/"), fontSize = 12.sp)
            Text(snapshot.message, fontSize = 12.sp)
            if (snapshot.syncing) LinearProgressIndicator(Modifier.fillMaxWidth())
            OutlinedButton(enabled = !snapshot.syncing, onClick = {
                scope.launch { withContext(Dispatchers.IO) { LegalitySync.refresh(context, force = true) } }
            }, modifier = Modifier.fillMaxWidth()) { Text("Actualiser les statuts") }
            TextButton(onClick = { uri.openUri("https://starwarsunlimited.com/how-to-play?chapter=rules") }) {
                Text("Consulter la liste officielle")
            }
        }
    }
}
