package fr.swucollection.app

import android.os.SystemClock
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

internal class LiveScanAnalyzer(
    private val callbackExecutor: Executor,
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
    private var rejected: ScanReference? = null
    private val orientation = ScanOrientation()

    fun readAgain() { force.set(true) }
    fun close() { closed.set(true); releaseIfIdle() }
    private fun releaseIfIdle() {
        if (closed.get() && !processing.get() && released.compareAndSet(false, true)) recognizer.close()
    }

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(image: ImageProxy) {
        val now = SystemClock.elapsedRealtime()
        if (closed.get() || paused() || (!force.get() && now - lastAnalysis < 450) || !processing.compareAndSet(false, true)) {
            image.close(); return
        }
        if (closed.get()) { image.close(); processing.set(false); releaseIfIdle(); return }
        lastAnalysis = now
        val retry = force.getAndSet(false)
        try {
            val media = image.image
            if (media == null) { image.close(); processing.set(false); releaseIfIdle(); return }
            if (retry) { gate.reset(); rejected = null; orientation.reset() }
            val rotation = (image.imageInfo.rotationDegrees + orientation.degrees) % 360
            recognizer.process(InputImage.fromMediaImage(media, rotation))
                .addOnSuccessListener(callbackExecutor) { text ->
                    if (!closed.get() && !paused()) {
                        val words = text.textBlocks.flatMap { it.lines }.flatMap { it.elements }.mapNotNull { element ->
                            element.boundingBox?.let { bounds ->
                                ScanOcrWord(element.text, bounds.left, bounds.top, bounds.right, bounds.bottom)
                            }
                        }
                        val raw = ScanFrameRecognition.text(text.text, words)
                        val reference = ScanRecognition.extract(raw)
                        orientation.observed(reference)
                        val stable = gate.observe(reference, SystemClock.elapsedRealtime(), ScanRecognition.language(raw))
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
                            reference == null -> onMessage("Garde la carte entière dans le cadre, avec son numéro bien visible")
                            reference == rejected -> Unit
                            reference == gate.blockedReference -> onMessage("Présente la carte suivante, ou relance pour lire la même carte")
                            else -> onMessage("Référence repérée · garde la carte immobile")
                        }
                    }
                }
                .addOnFailureListener(callbackExecutor) {
                    if (!closed.get() && !paused()) onMessage("Ajuste la lumière ou rapproche légèrement la carte")
                }
                .addOnCompleteListener(callbackExecutor) {
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
