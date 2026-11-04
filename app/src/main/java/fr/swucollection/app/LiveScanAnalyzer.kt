package fr.swucollection.app

import android.os.SystemClock
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean

internal class LiveScanAnalyzer(
    private val callbackExecutor: Executor,
    private val analysisExecutor: ExecutorService,
    private val visualMatcher: ScanVisualMatcher,
    private val viewport: () -> ScanViewport,
    private val onVisualCandidates: (List<VisualCardMatch>) -> Unit,
    private val paused: () -> Boolean,
    private val onMessage: (String) -> Unit,
    private val gate: StableScanGate,
    private val onDetected: (ScanReference, String) -> Boolean
) : ImageAnalysis.Analyzer {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val closed = AtomicBoolean(false)
    private val processing = AtomicBoolean(false)
    private val released = AtomicBoolean(false)
    private val force = AtomicBoolean(false)
    private var lastAnalysis = SystemClock.elapsedRealtime() // Allow positioning after opening the camera.
    @Volatile private var rejected: ScanReference? = null
    private val orientation = ScanOrientation()
    private var lastVisualAnalysis = 0L
    private var visualKey: List<ScanReference> = emptyList()
    private var visualReads = 0
    private var lastVisualRead = 0L
    private var visualAbsentSince: Long? = null
    private var blockedVisualKey: List<ScanReference> = emptyList()

    fun readAgain() { force.set(true) }
    fun interrupt() {
        gate.interrupt(); visualKey = emptyList(); visualReads = 0; visualAbsentSince = null
    }
    fun close() { closed.set(true); releaseIfIdle() }
    private fun releaseIfIdle() {
        if (closed.get() && !processing.get() && released.compareAndSet(false, true)) {
            recognizer.close(); analysisExecutor.shutdown()
        }
    }

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(image: ImageProxy) {
        val now = SystemClock.elapsedRealtime()
        if (paused()) { image.close(); return }
        if (closed.get() || (!force.get() && now - lastAnalysis < 450) || !processing.compareAndSet(false, true)) {
            image.close(); return
        }
        if (closed.get()) { image.close(); processing.set(false); releaseIfIdle(); return }
        lastAnalysis = now
        val retry = force.getAndSet(false)
        try {
            val media = image.image
            if (media == null) { image.close(); processing.set(false); releaseIfIdle(); return }
            if (retry) {
                orientation.reset()
                callbackExecutor.execute {
                    gate.reset(); rejected = null; visualKey = emptyList(); visualReads = 0; blockedVisualKey = emptyList()
                }
            }
            val currentViewport = viewport()
            val rotation = (image.imageInfo.rotationDegrees + orientation.degrees) % 360
            recognizer.process(InputImage.fromMediaImage(media, rotation))
                .addOnSuccessListener(analysisExecutor) { text ->
                    if (!closed.get() && !paused()) {
                        val words = text.textBlocks.flatMap { it.lines }.flatMap { it.elements }.mapNotNull { element ->
                            element.boundingBox?.let { bounds ->
                                ScanOcrWord(element.text, bounds.left, bounds.top, bounds.right, bounds.bottom)
                            }
                        }
                        val raw = ScanFrameRecognition.text(text.text, words)
                        val reference = ScanRecognition.extract(raw)
                        val checksAbsence = orientation.checksAbsence
                        orientation.observed(reference)
                        val visualAttempted = (reference == null || reference == rejected) && ScanRecognition.references(raw).size <= 1 &&
                            SystemClock.elapsedRealtime() - lastVisualAnalysis >= 1800
                        val visual = if (visualAttempted) {
                            lastVisualAnalysis = SystemClock.elapsedRealtime()
                            runCatching {
                                val bitmap = image.toBitmap()
                                try { visualMatcher.match(bitmap, image.imageInfo.rotationDegrees, currentViewport) }
                                finally { bitmap.recycle() }
                            }.getOrDefault(emptyList())
                        } else emptyList()
                        callbackExecutor.execute {
                        if (closed.get() || paused()) return@execute
                        if (visual.isNotEmpty()) {
                            visualAbsentSince = null
                            val key = visual.map { it.reference }.sortedBy { it.set + it.number }
                            val visualNow = SystemClock.elapsedRealtime()
                            visualReads = if (key == visualKey && visualNow-lastVisualRead <= 6000) visualReads + 1 else 1
                            visualKey = key; lastVisualRead = visualNow
                            if (visualReads >= 2 && key != blockedVisualKey && gate.blockedReference !in key) {
                                blockedVisualKey = key
                                onVisualCandidates(visual)
                                return@execute
                            }
                        } else if (reference != null || visualAttempted) {
                            visualKey = emptyList(); visualReads = 0
                            if (visualAttempted) {
                                val visualNow = SystemClock.elapsedRealtime()
                                val firstAbsent = visualAbsentSince ?: visualNow.also { visualAbsentSince = it }
                                if (visualNow-firstAbsent >= 3600) blockedVisualKey = emptyList()
                            } else visualAbsentSince = null
                        }
                        // A sideways retry is not evidence that the held card was
                        // removed. Otherwise one failed read can rearm batch mode.
                        val stable = if (reference != null || checksAbsence)
                            gate.observe(reference, SystemClock.elapsedRealtime(), ScanRecognition.language(raw)) else null
                        when {
                            stable != null -> {
                                force.set(false)
                                if (onDetected(stable, raw)) {
                                    rejected = null
                                    onMessage("Carte reconnue · confirme son ajout")
                                } else {
                                    rejected = stable
                                    onMessage("Référence introuvable · recentre le bas de la carte ou saisis-la manuellement")
                                }
                            }
                            reference == null && visual.isNotEmpty() -> onMessage("Illustration repérée · garde la carte immobile")
                            reference == null -> onMessage("Garde la carte entière dans le cadre, avec son numéro bien visible")
                            reference == rejected -> Unit
                            reference == gate.blockedReference -> onMessage("Présente la carte suivante, ou relance pour lire la même carte")
                            else -> onMessage("Référence repérée · garde la carte immobile")
                        }
                        }
                    }
                }
                .addOnFailureListener(callbackExecutor) {
                    if (!closed.get() && !paused()) onMessage("Ajuste la lumière ou rapproche légèrement la carte")
                }
                .addOnCompleteListener(analysisExecutor) {
                    image.close()
                    processing.set(false)
                    releaseIfIdle()
                }
        } catch (_: Exception) {
            image.close(); processing.set(false); releaseIfIdle()
            callbackExecutor.execute { if (!closed.get()) onMessage("Lecture interrompue · relance la lecture") }
        }
    }
}
