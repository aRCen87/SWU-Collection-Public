package fr.swucollection.app

import androidx.camera.core.ImageProxy

/** Advisory luminance measurements only. Never alters recognition or decides a foil finish. */
internal object ScanImageQuality {
    fun advice(samples: IntArray): String? {
        if (samples.isEmpty()) return null
        val mean = samples.average()
        val clipped = samples.count { it >= 248 }.toDouble() / samples.size
        val contrast = (samples.maxOrNull() ?: 0) - (samples.minOrNull() ?: 0)
        return when {
            mean < 42 -> "Trop sombre · ajoute une lumière diffuse"
            clipped > .55 -> "Grande zone surexposée · incline légèrement la carte pour réduire un éventuel reflet"
            contrast < 32 -> "Image peu contrastée · rapproche la carte et ajuste la mise au point"
            else -> null
        }
    }
    fun read(image: ImageProxy): String? = runCatching {
        val plane = image.planes.first(); val buffer = plane.buffer
        val area = image.cropRect
        val left = area.left + area.width() / 5; val top = area.top + area.height() / 5
        val width = area.width() * 3 / 5; val height = area.height() * 3 / 5
        advice(IntArray(32 * 32) { index ->
            val x = left + (index % 32) * width / 32; val y = top + (index / 32) * height / 32
            buffer.get(y * plane.rowStride + x * plane.pixelStride).toInt() and 255
        })
    }.getOrNull()
}
