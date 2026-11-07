package fr.swucollection.app

import android.os.SystemClock
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.text.Text
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

internal class LiveScanAnalyzer(
    private val callbackExecutor: Executor,
    private val analysisExecutor: ExecutorService,
    private val visualMatcher: ScanVisualMatcher,
    private val viewport: () -> ScanViewport,
    private val onVisualCandidates: (List<VisualCardMatch>) -> Boolean,
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
    private val generation = AtomicLong(0)
    private var lastAnalysis = SystemClock.elapsedRealtime() // Allow positioning after opening the camera.
    @Volatile private var rejected: ScanReference? = null
    private val orientation = ScanOrientation()
    private var lastRegionAnalysis = 0L
    private var regionalReference: ScanReference? = null
    private val regionSearch = ScanRegionSearch()
    private val regionEvidence = ScanRegionEvidence()
    private var lastVisualAnalysis = 0L
    private val visualEvidence = StableVisualScanEvidence()
    private var visualKey: List<ScanReference> = emptyList()
    private var visualReads = 0
    private var lastVisualRead = 0L
    private var visualAbsentSince: Long? = null
    private var blockedVisualKey: List<ScanReference> = emptyList()
    private var reviewedVisualKey: List<ScanReference> = emptyList()

    fun readAgain() { generation.incrementAndGet(); force.set(true) }
    fun interrupt() {
        generation.incrementAndGet()
        gate.interrupt(); regionEvidence.interrupt(); visualEvidence.interrupt(); visualKey = emptyList(); visualReads = 0; visualAbsentSince = null
    }
    fun close() { closed.set(true); releaseIfIdle() }
    private fun releaseIfIdle() {
        if (closed.get() && !processing.get() && released.compareAndSet(false, true)) {
            recognizer.close(); analysisExecutor.shutdown()
        }
    }

    private fun readingText(text: Text): String {
        val words = text.textBlocks.flatMap { it.lines }.flatMap { it.elements }.mapNotNull { element ->
            element.boundingBox?.let { bounds -> ScanOcrWord(element.text,bounds.left,bounds.top,bounds.right,bounds.bottom) }
        }
        return ScanFrameRecognition.text(text.text,words)
    }

    private fun readRegions(image: ImageProxy, viewport: ScanViewport, textRotation: Int,
        landscape: Boolean, full: String, active: () -> Boolean): Task<ScanOcrReading> {
        val crops = runCatching {
            val frame = image.toBitmap()
            try { ScanOcrRegions.crops(frame,image.imageInfo.rotationDegrees,viewport,landscape,textRotation) }
            finally { frame.recycle() }
        }.getOrNull() ?: return Tasks.forResult(ScanOcrReading(full,regionsAttempted=true))
        return ScanOcrRegions.read(crops,full,analysisExecutor,active) { crop ->
            recognizer.process(InputImage.fromBitmap(crop,0)).continueWith(analysisExecutor) { task ->
                if (task.isSuccessful) readingText(task.result) else ""
            }
        }
    }

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(image: ImageProxy) {
        val now = SystemClock.elapsedRealtime()
        if (paused()) { image.close(); return }
        if (closed.get() || (!force.get() && now - lastAnalysis < 300) || !processing.compareAndSet(false, true)) {
            image.close(); return
        }
        if (closed.get()) { image.close(); processing.set(false); releaseIfIdle(); return }
        lastAnalysis = now
        val retry = force.getAndSet(false)
        val frameGeneration = generation.get()
        try {
            val media = image.image
            if (media == null) { image.close(); processing.set(false); releaseIfIdle(); return }
            if (retry) {
                orientation.reset(); regionSearch.reset(); regionalReference = null
                callbackExecutor.execute {
                    gate.reset(); regionEvidence.interrupt(); visualEvidence.interrupt(); rejected = null; visualKey = emptyList(); visualReads = 0; blockedVisualKey = emptyList(); reviewedVisualKey = emptyList()
                }
            }
            val qualityAdvice = ScanImageQuality.read(image)
            val currentViewport = viewport()
            val rotation = (image.imageInfo.rotationDegrees + orientation.degrees) % 360
            recognizer.process(InputImage.fromMediaImage(media, rotation))
                .continueWithTask(analysisExecutor) { result ->
                    if (!result.isSuccessful) Tasks.forException<ScanOcrReading>(result.exception ?: IllegalStateException("Lecture interrompue"))
                    else {
                        val full = readingText(result.result)
                        val fullReference = ScanRecognition.extract(full)
                        if (fullReference != null) regionalReference = null
                        // Once a footer is found, confirm it promptly. Expensive
                        // searches without any candidate keep their slower cadence.
                        val regionInterval = if (regionalReference != null) 600L else 1800L
                        val regional = fullReference == null && ScanRecognition.references(full).size <= 1 &&
                            SystemClock.elapsedRealtime() - lastRegionAnalysis >= regionInterval &&
                            !closed.get() && !paused() && generation.get() == frameGeneration
                        if (regional) {
                            lastRegionAnalysis = SystemClock.elapsedRealtime()
                            val pose = regionSearch.pose
                            readRegions(image,currentViewport,pose.rotation,pose.landscape,full) {
                                !closed.get() && !paused() && generation.get() == frameGeneration
                            }.continueWith(analysisExecutor) { regionTask ->
                                val reading = if (regionTask.isSuccessful) regionTask.result else ScanOcrReading(full,regionsAttempted=true)
                                regionalReference = reading.reference
                                regionSearch.observed(reading.reference)
                                reading
                            }
                        } else Tasks.forResult(ScanOcrReading(full))
                    }
                }
                .addOnSuccessListener(analysisExecutor) { reading ->
                    if (!closed.get() && !paused() && generation.get() == frameGeneration) {
                        val raw = reading.raw
                        val reference = reading.reference
                        val checksAbsence = orientation.checksAbsence
                        orientation.observed(ScanRecognition.extract(reading.full))
                        val visualAttempted = (reference == null || reference == rejected) && reading.references.size <= 1 &&
                            SystemClock.elapsedRealtime() - lastVisualAnalysis >= 1800
                        val visual = if (visualAttempted) {
                            lastVisualAnalysis = SystemClock.elapsedRealtime()
                            runCatching {
                                val bitmap = image.toBitmap()
                                try { visualMatcher.matchAutomatic(bitmap, image.imageInfo.rotationDegrees, currentViewport,
                                    ScanVisualRecognition.partialFooter(reading.footer.ifBlank { raw })?.first) }
                                finally { bitmap.recycle() }
                            }.getOrDefault(emptyList())
                        } else emptyList()
                        callbackExecutor.execute {
                        if (closed.get() || paused() || generation.get() != frameGeneration) return@execute
                        val regional = regionEvidence.observe(reading,SystemClock.elapsedRealtime())
                        if (regional != null && gate.blockedReference != regional.first && onDetected(regional.first,raw)) {
                            gate.block(regional.first); regionEvidence.confirmed(); visualEvidence.interrupt(); rejected = null
                            onMessage("Carte reconnue · référence confirmée")
                            return@execute
                        }
                        if (reference != null) visualEvidence.interrupt()
                        val corroborated = if (visualAttempted) visualEvidence.observe(
                            ScanVisualRecognition.corroborated(raw, visual, reading.title), SystemClock.elapsedRealtime()
                        ) else null
                        if (corroborated != null && gate.blockedReference != corroborated.first) {
                            val (exact, language) = corroborated
                            if (onDetected(exact, "${exact.set} $language ${exact.number}")) {
                                gate.block(exact)
                                visualEvidence.interrupt()
                                rejected = null
                                onMessage("Carte reconnue par l’image et sa référence")
                                return@execute
                            }
                        }
                        if (visual.isNotEmpty()) {
                            visualAbsentSince = null
                            val key = visual.map { it.reference }.sortedBy { it.set + it.number }
                            val visualNow = SystemClock.elapsedRealtime()
                            visualReads = if (key == visualKey && visualNow-lastVisualRead <= 6000) visualReads + 1 else 1
                            visualKey = key; lastVisualRead = visualNow
                            if (reference == null && visualReads >= 2 && key != blockedVisualKey && gate.blockedReference !in key) {
                                val queuedForReview = onVisualCandidates(visual)
                                blockedVisualKey = key
                                if (queuedForReview) { reviewedVisualKey = key; gate.block(visual.first().reference) }
                            }
                        } else if (reference != null || visualAttempted) {
                            visualKey = emptyList(); visualReads = 0
                            if (visualAttempted) {
                                val visualNow = SystemClock.elapsedRealtime()
                                val firstAbsent = visualAbsentSince ?: visualNow.also { visualAbsentSince = it }
                                if (visualNow-firstAbsent >= 3600) { blockedVisualKey = emptyList(); reviewedVisualKey = emptyList() }
                            } else visualAbsentSince = null
                        }
                        // A visual review already accounts for this held card. A later
                        // OCR recovery of one of its candidate references must not add it twice.
                        if (reference != null && reference in reviewedVisualKey) {
                            onMessage("Carte mise de côté · présente la suivante pour continuer")
                            return@execute
                        }
                        // A sideways retry is not evidence that the held card was
                        // removed. Otherwise one failed read can rearm batch mode.
                        val stable = if (reference != null || (checksAbsence && visualKey.isEmpty() &&
                            !regionEvidence.visible(SystemClock.elapsedRealtime())))
                            gate.observe(reference, SystemClock.elapsedRealtime(), ScanRecognition.language(raw)) else null
                        when {
                            stable != null -> {
                                force.set(false)
                                if (onDetected(stable, raw)) {
                                    rejected = null
                                    onMessage("Carte reconnue · scan pris en compte")
                                } else {
                                    rejected = stable
                                    onMessage("Référence introuvable · recentre le bas de la carte ou saisis-la manuellement")
                                }
                            }
                            reference == null && visual.isNotEmpty() -> onMessage("Illustration repérée · garde la carte immobile")
                            reference == null -> onMessage(qualityAdvice ?: "Garde la carte entière dans le cadre, avec son numéro bien visible")
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
