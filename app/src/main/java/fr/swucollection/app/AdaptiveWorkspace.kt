package fr.swucollection.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun AdaptiveWorkspace(enabled: Boolean, hasDetail: Boolean, onClose: () -> Unit,
    detail: @Composable () -> Unit, dismissOnOutside: Boolean = true, content: @Composable BoxScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF030A17)).semantics { testTagsAsResourceId = true }) {
        val wide = enabled && maxWidth >= 720.dp && maxHeight >= 480.dp
        BackHandler(enabled = wide && hasDetail, onBack = onClose)
        if (wide) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(.56f).fillMaxHeight(), content = content)
            Box(Modifier.weight(.44f).fillMaxHeight().testTag("swu_detail_pane"), contentAlignment = Alignment.Center) {
                if (hasDetail) detail() else Card(Modifier.padding(16.dp).fillMaxWidth()) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Fiche de la carte", style = MaterialTheme.typography.titleLarge)
                        Text("Choisis une carte à gauche pour consulter ses versions, sa cote et tes exemplaires ici.")
                    }
                }
            }
        } else {
            Box(Modifier.fillMaxSize(), content = content)
            if (hasDetail) Dialog(onDismissRequest = onClose, properties = DialogProperties(
                usePlatformDefaultWidth = false, dismissOnClickOutside = dismissOnOutside)) { detail() }
        }
    }
}
