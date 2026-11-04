package fr.swucollection.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun CardLegalityBadge(status: PremierStatus?) {
    if (status == null) return
    val snapshot by LegalitySync.state.collectAsState()
    var showExplanation by remember(status) { mutableStateOf(false) }
    val suspended = status == PremierStatus.SUSPENDED
    val label = if (suspended) "Suspendue · Premier" else "Hors rotation · Premier"
    val tint = if (suspended) Color(0xFFFFA6A6) else Color(0xFFFFD47D)
    Surface(
        onClick = { showExplanation = true },
        modifier = Modifier.padding(top = 4.dp),
        shape = RoundedCornerShape(6.dp),
        color = if (suspended) Color(0xFF44232D) else Color(0xFF342C20),
        contentColor = tint,
        border = BorderStroke(1.dp, tint.copy(alpha = 0.45f))
    ) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(if (suspended) Icons.Default.Block else Icons.Default.History,
                contentDescription = null, modifier = Modifier.size(13.dp))
            Text(label, fontSize = 10.sp)
        }
    }
    if (showExplanation) {
        AlertDialog(onDismissRequest = { showExplanation = false },
            title = { Text(label) },
            text = {
                Text((if (suspended)
                    "Cette carte est suspendue en format Premier. Toutes ses variantes sont concernées. Une suspension peut être levée par l’éditeur."
                else
                    "Cette carte est sortie de la rotation du format Premier et aucune réédition dans un set Premier actuel n’est identifiée dans le catalogue. Elle reste jouable en Eternal et en Twin Suns, selon les règles de ces formats.") +
                    "\n\nLa carte reste dans ta collection et peut toujours être ajoutée ou scannée.\n\nContrôle officiel : ${snapshot.rules.checkedAt.take(10).split("-").reversed().joinToString("/")}.\n${snapshot.message}\nCe statut concerne uniquement Premier et dépend du catalogue disponible.")
            },
            confirmButton = { TextButton(onClick = { showExplanation = false }) { Text("Compris") } })
    }
}
