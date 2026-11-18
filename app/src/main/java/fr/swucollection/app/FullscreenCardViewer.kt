package fr.swucollection.app

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Temporary presentation state: never changes the collection or selected print. */
@Composable
internal fun FullscreenCardViewer(card: CatalogCard, language: String, variant: String?,
    hasPrevious: Boolean, hasNext: Boolean, onMove: (Int) -> Unit, onClose: () -> Unit) {
    var controls by remember { mutableStateOf(true) }
    var scale by remember(card) { mutableFloatStateOf(1f) }
    var reverse by remember(card) { mutableStateOf(false) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color(0xFF02090F)).safeDrawingPadding()) {
            CardArtwork(card, Modifier.fillMaxSize().padding(8.dp)
                .cardSwipeNavigation(card, scale == 1f, { if (hasPrevious) onMove(-1) }, { if (hasNext) onMove(1) })
                .pointerInput(card) { detectTapGestures(onTap = { controls = !controls }, onDoubleTap = { scale = if (scale > 1f) 1f else 2f }) }
                .pointerInput(card) { detectTransformGestures { _, _, zoom, _ -> scale = (scale * zoom).coerceIn(1f, 3f) } }
                .graphicsLayer { scaleX = scale; scaleY = scale }, language = language, variant = variant, reverse = reverse)
            if (controls) {
                Surface(Modifier.align(Alignment.TopCenter).fillMaxWidth(), color = Color(0xE608121F)) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(card.name, Modifier.weight(1f), color = Color.White)
                        if (card.backImageUrl.isNotBlank()) IconButton(onClick = { reverse = !reverse }) { Icon(Icons.Default.Flip, "Retourner la carte", tint = PremiumGold) }
                        IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Fermer le plein écran", tint = Color.White) }
                    }
                }
                Surface(Modifier.align(Alignment.BottomCenter), color = Color(0xE608121F)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onMove(-1) }, enabled = hasPrevious) { Icon(Icons.Default.ChevronLeft, "Carte précédente") }
                        TextButton(onClick = { scale = 1f }) { Text("${(scale * 100).toInt()} %") }
                        IconButton(onClick = { onMove(1) }, enabled = hasNext) { Icon(Icons.Default.ChevronRight, "Carte suivante") }
                    }
                }
            }
        }
    }
}
