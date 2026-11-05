package fr.swucollection.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
internal fun TradeQrPanel(profile: TradeProfile, catalog: List<CatalogCard>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var shared by remember { mutableStateOf(false) }
    var confirmingTrade by remember { mutableStateOf(false) }
    var confirmedTrades by remember { mutableStateOf(emptySet<String>()) }
    var resultTab by remember { mutableIntStateOf(0) }
    var advanced by remember { mutableStateOf(false) }
    var peer by remember { mutableStateOf<TradeProfile?>(null) }
    var frozen by remember { mutableStateOf(profile) }
    var snapshotReady by remember { mutableStateOf(false) }
    var scan by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var codes by remember { mutableStateOf<List<String>?>(null) }
    var allMatches by remember { mutableStateOf<List<TradeMatch>?>(null) }
    var receiver by remember { mutableStateOf(OfflineTradeReceiver()) }
    var progress by remember { mutableStateOf("Cadre le QR affiché dans Échanges sur l’autre téléphone.") }
    val latestProfile by rememberUpdatedState(profile)
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) message = runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(latestProfile.json()) }
                ?: error("Fichier inaccessible")
            "Profil d’échange exporté."
        }.getOrElse { "Export impossible." }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            runCatching { withContext(Dispatchers.IO) {
                val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        require(output.size() + n <= 4 * 1024 * 1024)
                        output.write(buffer, 0, n)
                    }
                    output.toByteArray()
                } ?: error("Fichier inaccessible")
                TradeProfile.parse(bytes.toString(Charsets.UTF_8))
            } }.onSuccess {
                frozen = latestProfile; snapshotReady = true; peer = it
                message = "Profil importé. La comparaison apparaît sur ce téléphone."
            }.onFailure { message = it.message ?: "Profil invalide." }
            busy = false
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val step = if (peer != null && shared) 4 else if (peer != null || shared) 3 else 2
            Text("Étape $step sur 4 · " + when {
                peer != null && shared -> "Nos propositions d’échange"
                peer != null -> "À ton tour de partager tes codes"
                shared -> "Scanne maintenant les codes de ton ami"
                else -> "Échangez vos codes"
            }, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            LinearProgressIndicator(progress = { step / 4f }, modifier = Modifier.fillMaxWidth())
            Text(when {
                peer != null && shared -> "Chaque téléphone peut maintenant consulter les deux sens de l’échange."
                peer != null -> "Tu as reçu son profil. Affiche tes codes pour qu’il obtienne aussi les résultats."
                shared -> "Ton ami affiche ses codes. Scanne-les tous jusqu’à la confirmation."
                else -> "Un téléphone affiche ses codes, l’autre les scanne. Vous inverserez ensuite les rôles, sans Internet ni Wi-Fi."
            })
            Text("Pour conserver la langue des cartes, les deux téléphones doivent utiliser la version 0.8.6 ou suivante. Les anciens profils reçus restent lisibles avec une langue à préciser.", style = MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !busy, onClick = {
                    busy = true; message = null
                    // After a scan, reuse the same snapshot so both comparisons agree.
                    if (!snapshotReady) { frozen = latestProfile; snapshotReady = true }
                    val snapshot = frozen
                    scope.launch {
                        runCatching { withContext(Dispatchers.Default) { OfflineTradeQr.frames(snapshot) } }
                            .onSuccess { codes = it }.onFailure { message = it.message ?: "Profil impossible à afficher." }
                        busy = false
                    }
                }) { Text("Afficher mes QR codes") }
                OutlinedButton(enabled = !busy, onClick = {
                    if (!snapshotReady) { frozen = latestProfile; snapshotReady = true }
                    receiver = OfflineTradeReceiver(); progress = "Cadre le premier code de ton ami, puis laisse défiler les suivants."
                    message = null; scan = true
                }) { Text("Scanner ceux d’un ami") }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("Gros profil : plusieurs codes défilent automatiquement. Tu peux mettre en pause et choisir un code manquant.", style = MaterialTheme.typography.bodySmall)
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            peer?.let { other ->
                val give = remember(frozen, other) { TradeComparison.matching(frozen, other) }
                val receive = remember(frozen, other) { TradeComparison.matching(other, frozen) }
                TabRow(selectedTabIndex = resultTab) {
                    Tab(resultTab == 0, onClick = { resultTab = 0 }, text = { Text("Je donne (${give.sumOf { it.quantity }})") })
                    Tab(resultTab == 1, onClick = { resultTab = 1 }, text = { Text("Je reçois (${receive.sumOf { it.quantity }})") })
                }
                val matches = if (resultTab == 0) give else receive
                if (matches.isEmpty()) Text(if (resultTab == 0) "Aucun de mes surplus ne correspond à ses recherches." else "Aucun de ses surplus ne correspond à mes recherches.")
                matches.take(10).forEach { TradeMatchCard(it, catalog) }
                if (matches.size > 10) TextButton(onClick = { allMatches = matches }) { Text("Voir les ${matches.size} propositions") }
                Text("Suggestions à vérifier ensemble ; les valeurs ne sont pas forcément équivalentes. Les quantités ne changent pas automatiquement.", style = MaterialTheme.typography.bodySmall)
                val tradeKey = badgeFingerprint(listOf(frozen.json(),other.json()).sorted().joinToString("|"))
                if(give.isNotEmpty() || receive.isNotEmpty()) OutlinedButton(enabled = tradeKey !in confirmedTrades,
                    onClick = { confirmingTrade = true },modifier=Modifier.fillMaxWidth()) {
                    Text(if(tradeKey in confirmedTrades) "Échange enregistré" else "Confirmer un échange effectué")
                }
            }

            if (snapshotReady) TextButton(enabled = !busy, onClick = {
                peer = null; shared = false; codes = null; snapshotReady = false; frozen = latestProfile; message = null
            }) { Text("Nouvelle comparaison") }
            TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "Masquer les options de fichier" else "Échanger par fichier ▾") }
            if (advanced) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(enabled = !busy, onClick = { export.launch("SWU-profil-echange.json") }) { Text("Exporter le profil") }
                TextButton(enabled = !busy, onClick = { importer.launch(arrayOf("application/json", "text/*")) }) { Text("Importer un profil") }
            }
        }
    }
    if(confirmingTrade && peer != null) AlertDialog(onDismissRequest={confirmingTrade=false},
        title={Text("Échange réellement effectué ?")},text={Text("Confirme uniquement après avoir remis les cartes à ton ami et reçu les tiennes. Cet échange compte pour tes hauts faits ; ajuste ensuite les quantités dans ta collection. Une même comparaison ne compte qu’une fois.")},
        confirmButton={TextButton(onClick={
            val key=badgeFingerprint(listOf(frozen.json(),peer!!.json()).sorted().joinToString("|"))
            runCatching { recordBadgeAction(context,"trade",key) }.onSuccess {
                confirmedTrades=confirmedTrades+key;confirmingTrade=false;message="Échange enregistré dans tes hauts faits."
            }.onFailure { message="Enregistrement impossible. Réessaie." }
        }){Text("Oui, échange effectué")}},dismissButton={TextButton(onClick={confirmingTrade=false}){Text("Pas encore")}})
    codes?.let { frames -> OfflineTradeCodeDisplay(frames, onDismiss = { codes = null }, onCompleted = {
        codes = null; shared = true
        if (peer == null) { receiver = OfflineTradeReceiver(); progress = "À ton tour : scanne tous les codes de ton ami."; scan = true }
    }) }
    allMatches?.let { rows -> AlertDialog(onDismissRequest = { allMatches = null }, title = { Text("Cartes proposées") }, text = {
        LazyColumn(Modifier.heightIn(max = 440.dp)) {
            items(rows) { TradeMatchCard(it, catalog) }
        }
    }, confirmButton = { TextButton(onClick = { allMatches = null }) { Text("Fermer") } }) }
    if (scan) TradeQrScanner(onDismiss = { scan = false }, progress = progress, onReset = {
        receiver = OfflineTradeReceiver(); progress = "Réception remise à zéro. Scanne les codes de ton ami."
    }, onRead = { value ->
        runCatching { receiver.accept(value) }.onSuccess { other ->
            if (other != null) {
                scan = false; peer = other
                recordBadgeAction(context,"qr",badgeFingerprint(other.json()))
                message = if (shared) "Les deux profils sont prêts. Consulte les propositions ci-dessous." else "Profil reçu. Affiche maintenant tes codes pour ton ami."
            } else {
                progress = "${receiver.received} codes reçus sur ${receiver.total} · Manquants : ${receiver.missing.take(12).joinToString()}" +
                    if (receiver.missing.size > 12) "…" else ""
            }
        }.onFailure { progress = it.message ?: "QR invalide. Réessaie ou recommence le scan." }
    })
}

@Composable
private fun OfflineTradeCodeDisplay(frames: List<String>, onDismiss: () -> Unit, onCompleted: () -> Unit) {
    var index by remember(frames) { mutableIntStateOf(0) }
    var playing by remember(frames) { mutableStateOf(frames.size > 1) }
    val owner = LocalLifecycleOwner.current
    val lifecycleState by owner.lifecycle.currentStateFlow.collectAsState()
    LaunchedEffect(playing, frames, lifecycleState) {
        if (playing && frames.size > 1 && lifecycleState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
            while (true) { kotlinx.coroutines.delay(1800); index = (index + 1) % frames.size }
        }
    }
    val view = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(view) {
        val previous = view.keepScreenOn
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = previous }
    }
    val value = frames[index]
    val bitmap = remember(value) {
        runCatching {
            val matrix = QRCodeWriter().encode(value, BarcodeFormat.QR_CODE, 900, 900,
                mapOf(com.google.zxing.EncodeHintType.ERROR_CORRECTION to com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.M,
                    com.google.zxing.EncodeHintType.MARGIN to 4))
            Bitmap.createBitmap(900, 900, Bitmap.Config.ARGB_8888).apply {
                setPixels(IntArray(900 * 900) { i -> if (matrix[i % 900, i / 900]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }, 0, 900, 0, 0, 900, 900)
            }.asImageBitmap()
        }.getOrNull()
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Mon profil d’échange", style = MaterialTheme.typography.titleLarge)
                Text("Code ${index + 1} / ${frames.size}", style = MaterialTheme.typography.titleMedium)
                if (bitmap != null) Image(bitmap, "QR du profil, code ${index + 1} sur ${frames.size}",
                    Modifier.widthIn(max = 440.dp).fillMaxWidth().aspectRatio(1f).background(Color.White),
                    filterQuality = androidx.compose.ui.graphics.FilterQuality.None)
                else Text("QR indisponible. Ferme puis réessaie, ou exporte le profil.")
                Text("Ton ami scanne tous les codes. Le défilement recommence en boucle pour récupérer les codes manqués.")
                if (frames.size > 1) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { playing = false; index = (index + frames.size - 1) % frames.size }) { Text("Précédent") }
                        OutlinedButton(onClick = { playing = false; index = (index + 1) % frames.size }) { Text("Suivant") }
                    }
                    TextButton(onClick = { playing = !playing }) { Text(if (playing) "Mettre en pause" else "Défilement automatique") }
                    Slider(value = index.toFloat(), onValueChange = { playing = false; index = it.toInt() }, valueRange = 0f..frames.lastIndex.toFloat(),
                        modifier = Modifier.semantics { contentDescription = "Choisir le numéro du code" })
                }
                Text("À partager uniquement avec la personne choisie : ces codes contiennent les surplus et manquantes sélectionnés.", style = MaterialTheme.typography.bodySmall)
                Button(onClick = onCompleted) { Text("Mon ami a scanné tous mes codes") }
                TextButton(onClick = onDismiss) { Text("Fermer sans terminer") }
            }
        }
    }
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
internal fun TradeQrScanner(onDismiss:()->Unit,progress: String,onReset:()->Unit,onRead:(String)->Unit) {
    val context=LocalContext.current;val owner=LocalLifecycleOwner.current
    var allowed by remember {mutableStateOf(ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {allowed=it}
    val preview=remember {PreviewView(context)}
    var lastRead by remember {mutableStateOf<String?>(null)}
    var lastReadAt by remember {mutableLongStateOf(0L)}
    val latest by rememberUpdatedState(onRead)
    var error by remember {mutableStateOf<String?>(null)}
    if(allowed) DisposableEffect(preview,owner) {
        val active=AtomicBoolean(true);val processing=AtomicBoolean(false);val executor=Executors.newSingleThreadExecutor()
        val scanner=BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
        val providerFuture=ProcessCameraProvider.getInstance(context);var provider:ProcessCameraProvider?=null
        val cameraPreview=Preview.Builder().build().also {it.surfaceProvider=preview.surfaceProvider}
        val analysis=ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
        analysis.setAnalyzer(executor) { proxy ->
            val media=proxy.image
            if(!active.get()||media==null||!processing.compareAndSet(false,true)) proxy.close()
            else scanner.process(InputImage.fromMediaImage(media,proxy.imageInfo.rotationDegrees))
                .addOnSuccessListener(ContextCompat.getMainExecutor(context)) {rows ->
                    if(active.get()) rows.firstOrNull()?.rawValue?.let { value ->
                        val now = android.os.SystemClock.elapsedRealtime()
                        if (value != lastRead || now - lastReadAt > 1500) { lastRead = value; lastReadAt = now; latest(value) }
                    }
                }.addOnCompleteListener {proxy.close();processing.set(false);if(!active.get())scanner.close()}
        }
        providerFuture.addListener({if(active.get())runCatching {provider=providerFuture.get();provider!!.bindToLifecycle(owner,CameraSelector.DEFAULT_BACK_CAMERA,cameraPreview,analysis)}.onFailure {error="Caméra indisponible."}},ContextCompat.getMainExecutor(context))
        onDispose {active.set(false);analysis.clearAnalyzer();runCatching {provider?.unbind(cameraPreview,analysis)};executor.shutdown();if(!processing.get())scanner.close()}
    }
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize()) {Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("Scanner le QR d’un ami",style=MaterialTheme.typography.titleLarge)
            if(allowed) AndroidView(factory={preview},modifier=Modifier.weight(1f).fillMaxWidth())
            else Button(onClick={permission.launch(Manifest.permission.CAMERA)}) {Text("Autoriser la caméra")}
            error?.let {Text(it)}
            Text(progress)
            TextButton(onClick={lastRead=null;onReset()}) {Text("Recommencer le scan")}
            OutlinedButton(onClick=onDismiss) {Text("Fermer")}
        }}
    }
}



@Composable
private fun TradeMatchCard(match: TradeMatch, catalog: List<CatalogCard>) {
    val offered = match.offered
    val card = remember(offered.set, offered.number, catalog) { catalog.firstOrNull {
        MarketPricing.key(it.setCode,it.number) == MarketPricing.key(offered.set,offered.number)
    } }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (card != null) CardArtwork(card, Modifier.size(56.dp,78.dp), language=offered.language, variant=offered.variant)
            Column(Modifier.weight(1f)) {
                Text("${match.quantity} × ${offered.name}", style = MaterialTheme.typography.titleSmall)
                Text("${offered.set} · ${offered.number} · ${offered.variant} · ${CardLanguages.label(offered.language)}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

