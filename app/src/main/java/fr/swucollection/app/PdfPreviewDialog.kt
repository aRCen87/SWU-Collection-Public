package fr.swucollection.app

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun PdfPreviewDialog(file: File, saving: Boolean, status: String?, onSave: () -> Unit, onDismiss: () -> Unit) {
    var index by remember { mutableIntStateOf(0) }
    var pages by remember { mutableIntStateOf(1) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var failure by remember { mutableStateOf<String?>(null) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    LaunchedEffect(file, index) {
        failure = null; bitmap = null; zoom = 1f; pan = Offset.Zero
        try {
            val result = withContext(Dispatchers.IO) {
                PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
                    val count = renderer.pageCount
                    val image = renderer.openPage(index).use { page ->
                        Bitmap.createBitmap(1000, (1000f * page.height / page.width).toInt(), Bitmap.Config.ARGB_8888).also { image ->
                            image.eraseColor(android.graphics.Color.WHITE)
                            page.render(image, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        }
                    }
                    count to image
                }
            }
            pages = result.first; bitmap = result.second
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failure = "Aperçu indisponible. Tu peux réessayer ou enregistrer le PDF préparé." }
    }
    Dialog(onDismissRequest = { if (!saving) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = ListPalette.Background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Aperçu PDF", color = ListPalette.Gold, style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { index-- }, enabled = index > 0) { Text("Précédente") }
                    Text("${index + 1} / $pages", Modifier.padding(top = 12.dp))
                    TextButton(onClick = { index++ }, enabled = index + 1 < pages) { Text("Suivante") }
                }
                Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().background(ListPalette.Raised)
                    .pointerInput(index) { detectTransformGestures { _, delta, scale, _ -> zoom = (zoom * scale).coerceIn(1f, 4f); pan = if (zoom == 1f) Offset.Zero else pan + delta } }) {
                    bitmap?.let { Image(it.asImageBitmap(), "Page ${index + 1} du PDF", Modifier.fillMaxSize().graphicsLayer {
                        scaleX = zoom; scaleY = zoom; translationX = pan.x; translationY = pan.y
                    }, contentScale = ContentScale.Fit) }
                    if (bitmap == null && failure == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                failure?.let { Text(it) }
                Text(status ?: "Pince pour zoomer. L’impression conserve les dimensions du PDF.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { zoom = 1f; pan = Offset.Zero }) { Text("Zoom initial") }
                    TextButton(onClick = onDismiss, enabled = !saving) { Text("Fermer l’aperçu") }
                }
                Button(onClick = onSave, enabled = !saving && (bitmap != null || failure != null), modifier = Modifier.fillMaxWidth()) { Text("Enregistrer le PDF") }
            }
        }
    }
}
