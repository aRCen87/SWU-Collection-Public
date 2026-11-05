package fr.swucollection.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Size
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as DrawSize
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import coil.compose.AsyncImage
import java.util.concurrent.atomic.AtomicReference
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min
import kotlinx.coroutines.delay

private val ScanInk = Color(0xFF050D1A)
private val ScanGold = Color(0xFFFFD47D)
private val ScanCyan = Color(0xFF75C9D6)

private fun Context.scannerActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        val base = current.baseContext
        if (base === current) break
        current = base
    }
    return current as? Activity
}

@Composable
fun CardScannerDialog(
    onDismiss: () -> Unit,
    onDetected: (setCode: String, number: String, rawText: String) -> Boolean,
    paused: Boolean = false,
    lastAdded: String? = null,
    onUndoLast: (() -> Unit)? = null,
    batchCount: Int? = null,
    controls: (@Composable () -> Unit)? = null,
    title: String? = null,
    hint: String? = null,
    inlineControls: (@Composable () -> Unit)? = null,
    destinationControls: (@Composable () -> Unit)? = null
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val activity = context.scannerActivity()
    val owner = activity as? LifecycleOwner
    val latestDetected = rememberUpdatedState(onDetected)
    var hasPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var permissionAsked by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var analyzer by remember { mutableStateOf<LiveScanAnalyzer?>(null) }
    var torch by remember { mutableStateOf(false) }
    var userPaused by remember { mutableStateOf(false) }
    var manualEntry by remember { mutableStateOf(false) }
    var landscapeCard by remember { mutableStateOf(false) }
    var optionsOpen by remember { mutableStateOf(false) }
    val visualViewport = remember { AtomicReference(ScanViewport(0,0,false)) }
    val visualMatcher = remember(context) { ScanVisualMatcher(context.applicationContext) }
    var restart by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("Initialisation de la caméra…") }
    // Camera bindings may change when folding/rotating; a held card is still the
    // same scan session and must not be counted a second time in batch mode.
    val scanGate = remember { StableScanGate(unknownLanguageReads = 5) }
    val externalPause = rememberUpdatedState(paused)
    val pauseCheck = { externalPause.value || userPaused || manualEntry || optionsOpen }
    val effectivelyPaused = paused || userPaused || manualEntry || optionsOpen
    LaunchedEffect(effectivelyPaused) { if (effectivelyPaused) { scanGate.interrupt(); analyzer?.interrupt() } }
    val previewView = remember(context) {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
        }
    }
    SideEffect { visualViewport.set(ScanViewport(previewView.width,previewView.height,landscapeCard)) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted; permissionAsked = true
    }
    val settings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    if (hasPermission && owner != null) {
        DisposableEffect(previewView, owner, restart, configuration.orientation, configuration.screenWidthDp, configuration.screenHeightDp) {
            val active = AtomicBoolean(true)
            val main = ContextCompat.getMainExecutor(context)
            val executor = Executors.newSingleThreadExecutor()
            val reader = LiveScanAnalyzer(main, executor, visualMatcher, { visualViewport.get() },
                { matches -> if (active.get() && !pauseCheck()) matches.firstOrNull()?.let { landscapeCard = it.landscape } }, pauseCheck, { if (active.get()) message = it }, scanGate) { reference, raw ->
                if (!active.get() || pauseCheck()) false
                else latestDetected.value(reference.set, reference.number, raw).also { found ->
                    if (found) previewView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                }
            }
            analyzer = reader; failed = false; ready = false
            message = "Initialisation de la caméra…"
            @Suppress("DEPRECATION")
            val rotation = activity?.windowManager?.defaultDisplay?.rotation ?: android.view.Surface.ROTATION_0
            val preview = Preview.Builder().setTargetRotation(rotation).build().also { it.surfaceProvider = previewView.surfaceProvider }
            val analysis = ImageAnalysis.Builder().setTargetRotation(rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setResolutionSelector(ResolutionSelector.Builder()
                    .setAllowedResolutionMode(ResolutionSelector.PREFER_HIGHER_RESOLUTION_OVER_CAPTURE_RATE)
                    .setResolutionStrategy(
                    ResolutionStrategy(Size(2560, 1920), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)).build())
                .build().also { it.setAnalyzer(executor, reader) }
            var provider: ProcessCameraProvider? = null
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                if (active.get()) {
                    try {
                        provider = future.get()
                        camera = provider!!.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                        ready = true
                        message = "Place une carte dans le cadre · la lecture est automatique"
                    } catch (_: Exception) {
                        failed = true; ready = false
                        message = "Impossible d’ouvrir la caméra. Ferme les autres applications caméra et réessaie."
                    }
                }
            }, main)
            onDispose {
                active.set(false)
                analysis.clearAnalyzer()
                reader.interrupt()
                reader.close()
                runCatching { provider?.unbind(preview, analysis) }
                camera = null; analyzer = null; ready = false; torch = false
            }
        }
    }

    // Refresh focus on the card while scanning, then release it back to continuous AF.
    // Do not meter while a confirmation or a destination chooser covers the camera.
    LaunchedEffect(camera, ready, effectivelyPaused) {
        val current = camera ?: return@LaunchedEffect
        while (ready && !effectivelyPaused) {
            if (previewView.width > 0 && previewView.height > 0) {
                val point = previewView.meteringPointFactory.createPoint(
                    previewView.width / 2f, previewView.height * 0.60f)
                val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
                    .setAutoCancelDuration(2, TimeUnit.SECONDS).build()
                if (current.cameraInfo.isFocusMeteringSupported(action)) {
                    runCatching { current.cameraControl.startFocusAndMetering(action) }
                }
            }
            delay(4000)
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        Surface(color = ScanInk, modifier = Modifier.fillMaxSize()) {
          ScannerAdaptiveLayout(
              header = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("SWU COLLECTION", color = ScanCyan, fontSize = 10.sp, letterSpacing = 2.sp)
                        Text(title ?: if (batchCount != null) "Scanner en série" else "Scanner", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (controls != null) IconButton(onClick = { optionsOpen = true }) {
                        Icon(Icons.Default.Settings, "Options du scanner", tint = ScanGold)
                    }
                    val flash = camera?.cameraInfo?.hasFlashUnit() == true
                    IconButton(enabled = ready && flash, onClick = {
                        val next = !torch
                        val control = camera?.cameraControl ?: return@IconButton
                        torch = next
                        val request = control.enableTorch(next)
                        request.addListener({ runCatching { request.get() }.onFailure { torch = false } }, ContextCompat.getMainExecutor(context))
                    }, modifier = Modifier.background(if (torch) ScanGold.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.06f), CircleShape)) {
                        Icon(if (torch) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            if (torch) "Éteindre la lampe" else "Allumer la lampe", tint = if (torch) ScanGold else Color.White)
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onDismiss) { Text("Terminer", color = ScanGold) }
                }
                destinationControls?.invoke()
                }
              },
              preview = { paneModifier ->
                Box(paneModifier.clip(RoundedCornerShape(26.dp))
                    .background(Color(0xFF0B1B2D)).border(1.dp, ScanGold.copy(alpha = 0.3f), RoundedCornerShape(26.dp))
                    .pointerInput(camera, ready) {
                        detectTransformGestures { _, _, zoom, _ ->
                            val activeCamera = camera
                            val state = activeCamera?.cameraInfo?.zoomState?.value
                            if (state != null && zoom != 1f) activeCamera?.cameraControl?.setZoomRatio(
                                (state.zoomRatio * zoom).coerceIn(state.minZoomRatio,state.maxZoomRatio))
                        }
                    }
                    .pointerInput(camera, ready) {
                        detectTapGestures { tap ->
                            camera?.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(
                                previewView.meteringPointFactory.createPoint(tap.x, tap.y))
                                .setAutoCancelDuration(3, TimeUnit.SECONDS).build())
                        }
                    }, contentAlignment = Alignment.Center) {
                    if (hasPermission && owner != null) {
                        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize().onSizeChanged {
                            visualViewport.set(ScanViewport(it.width,it.height,landscapeCard))
                        })
                        ScannerGuide(active = ready && !effectivelyPaused, landscape = landscapeCard)
                        Surface(color = ScanInk.copy(alpha = 0.78f), shape = RoundedCornerShape(50.dp),
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp)) {
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.size(6.dp).background(if (effectivelyPaused) Color.Gray else ScanGold, CircleShape))
                                Text(if (effectivelyPaused) "Lecture en pause" else "Détection automatique", color = Color.White, fontSize = 12.sp)
                            }
                        }
                        if (!ready) {
                            Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (!failed) CircularProgressIndicator(color = ScanGold, modifier = Modifier.size(28.dp))
                                Text(if (failed) "Caméra indisponible" else "Ouverture de la caméra…", color = Color.White)
                                if (failed) OutlinedButton(onClick = { restart++ }) { Text("Réessayer") }
                            }
                        }
                        Text("Touche pour faire le point · pince pour zoomer", color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp))
                    } else {
                        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Icon(Icons.Default.PhotoCamera, null, tint = ScanGold, modifier = Modifier.size(48.dp))
                            Text("Ta collection commence ici", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 19.sp)
                            Text("Autorise la caméra pour lire la référence de tes cartes.", color = Color(0xFFB4C0D4), textAlign = TextAlign.Center)
                            Button(onClick = {
                                if (permissionAsked && activity?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) == false)
                                    settings.launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                                else permission.launch(Manifest.permission.CAMERA)
                            }) { Text("Autoriser la caméra") }
                        }
                    }
                }
              },
              actions = { paneModifier ->
                Column(verticalArrangement = Arrangement.spacedBy(7.dp), modifier = paneModifier) {
                    Text("Cartes, leaders et bases · format automatique", color = ScanCyan, fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally))
                    Crossfade(targetState = if (userPaused) "Lecture en pause · reprends quand tu es prêt" else message, label = "scan-status") { status ->
                        Text(status, color = Color(0xFFE7EDF7), fontSize = 13.sp, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 38.dp))
                    }
                    lastAdded?.let { Text("Dernier ajout : $it", color = ScanCyan, fontSize = 11.sp, maxLines = 1, modifier = Modifier.align(Alignment.CenterHorizontally)) }
                    inlineControls?.invoke()
                    if(onUndoLast != null) TextButton(onClick=onUndoLast, modifier=Modifier.align(Alignment.CenterHorizontally)) {Text("Annuler le dernier ajout", color=ScanGold)}
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { userPaused = !userPaused }, enabled = ready,
                            modifier = Modifier.widthIn(min = 140.dp).heightIn(min = 48.dp)) {
                            Icon(if (userPaused) Icons.Default.PlayArrow else Icons.Default.Pause, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp)); Text(if (userPaused) "Reprendre" else "Pause", maxLines = 1)
                        }
                        Button(onClick = { userPaused = false; analyzer?.readAgain() }, enabled = ready,
                            modifier = Modifier.widthIn(min = 140.dp).heightIn(min = 48.dp)) {
                            Text("Relancer la lecture")
                        }
                    }
                    TextButton(onClick = { manualEntry = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text("Saisir une référence", color = ScanCyan, fontSize = 12.sp)
                    }
                    Text(hint ?: if (batchCount != null) "$batchCount carte(s) en attente · Terminer pour vérifier le lot" else "Chaque carte est à confirmer avant l’ajout.", color = Color(0xFF879AB6), fontSize = 11.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally))
                }
              }
          )
        }
    }
    if (optionsOpen && controls != null) AlertDialog(
        onDismissRequest = { optionsOpen = false },
        title = { Text("Options du scanner") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { controls() } },
        confirmButton = { TextButton(onClick = { optionsOpen = false }) { Text("Reprendre le scan") } })
    if (manualEntry) ManualScanReference(onDismiss = { manualEntry = false }) { reference, language ->
        latestDetected.value(reference.set, reference.number, "${reference.set} $language ${reference.number}").also { found ->
            if (found) { scanGate.block(reference); manualEntry = false }
        }
    }
}

/** Keeps a usable camera viewport when folded, rotated or using larger text. */
@Composable
private fun ScannerAdaptiveLayout(
    header: @Composable () -> Unit,
    preview: @Composable (Modifier) -> Unit,
    actions: @Composable (Modifier) -> Unit
) {
    Box(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 960.dp).fillMaxHeight().fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            header()
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val availableWidth = maxWidth
                val availableHeight = maxHeight
                if (availableWidth >= 600.dp && availableHeight < 600.dp) {
                    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        preview(Modifier.weight(1f).fillMaxHeight())
                        actions(Modifier.width(availableWidth * 0.4f).fillMaxHeight().verticalScroll(rememberScrollState()))
                    }
                } else {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        preview(Modifier.weight(1f).fillMaxWidth())
                        actions(Modifier.fillMaxWidth().heightIn(max = availableHeight * 0.5f).verticalScroll(rememberScrollState()))
                    }
                }
            }
        }
    }
}

@Composable
private fun ScannerGuide(active: Boolean, landscape: Boolean) {
    val transition = rememberInfiniteTransition(label = "scanner-guide")
    val progress by transition.animateFloat(initialValue = 0.18f, targetValue = 0.82f,
        animationSpec = infiniteRepeatable(tween(1900, easing = LinearEasing), RepeatMode.Reverse), label = "scan-line")
    Canvas(Modifier.fillMaxSize()) {
        val ratio = if (landscape) 88f / 63f else 63f / 88f
        val height = min(size.height * 0.78f, size.width * 0.9f / ratio)
        val width = height * ratio
        val left = (size.width - width) / 2; val top = (size.height - height) / 2
        val right = left + width; val bottom = top + height
        val shade = Color.Black.copy(alpha = 0.38f)
        drawRect(shade, Offset.Zero, DrawSize(size.width, top))
        drawRect(shade, Offset(0f, bottom), DrawSize(size.width, size.height - bottom))
        drawRect(shade, Offset(0f, top), DrawSize(left, height))
        drawRect(shade, Offset(right, top), DrawSize(size.width - right, height))
        val corner = min(width * 0.15f, 32.dp.toPx()); val stroke = 3.dp.toPx()
        val color = if (active) ScanGold else ScanGold.copy(alpha = 0.45f)
        for ((x, direction) in listOf(left to 1, right to -1)) for ((y, vertical) in listOf(top to 1, bottom to -1)) {
            drawLine(color, Offset(x, y), Offset(x + direction * corner, y), stroke, StrokeCap.Round)
            drawLine(color, Offset(x, y), Offset(x, y + vertical * corner), stroke, StrokeCap.Round)
        }
        if (active) {
            val y = top + height * progress
            drawLine(Brush.horizontalGradient(listOf(Color.Transparent, ScanCyan, Color.Transparent), left, right),
                Offset(left + 8.dp.toPx(), y), Offset(right - 8.dp.toPx(), y), 2.dp.toPx(), StrokeCap.Round)
        }
    }
}

@Composable
private fun ManualScanReference(onDismiss: () -> Unit, onRead: (ScanReference, String) -> Boolean) {
    var set by remember { mutableStateOf("") }
    var language by remember { mutableStateOf("FR") }
    var number by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("Référence de la carte") },
        text = { Column(Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState()),verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Le code d’extension et le numéro sont imprimés en bas de la carte.", fontSize = 13.sp)
            OutlinedTextField(value = set, onValueChange = { set = it.uppercase(Locale.ROOT).filter(Char::isLetterOrDigit).take(4); error = null },
                label = { Text("Extension") }, placeholder = { Text("HMW, SOR…") }, singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters))
            OutlinedTextField(value = number, onValueChange = { number = it.filter(Char::isDigit).take(4); error = null },
                label = { Text("Numéro") }, placeholder = { Text("001") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            CardLanguageChoices(language, { language = it })
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
        } },
        confirmButton = { TextButton(onClick = {
            val reference = ScanRecognition.manual(set, number)
            error = if (reference == null) "Vérifie le code et le numéro." else if (!onRead(reference, language)) "Référence introuvable dans le catalogue." else null
        }, enabled = set.isNotBlank() && number.isNotBlank()) { Text("Rechercher") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } })
}




