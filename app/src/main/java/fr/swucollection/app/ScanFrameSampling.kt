package fr.swucollection.app

/** Integer camera rotations and area averages have the same pixels in every orientation. */
internal object ScanFrameSampling {
    data class Frame(val width: Int, val height: Int, val pixels: IntArray)

    fun upright(pixels: IntArray, width: Int, height: Int, rotation: Int, maximum: Int = 960): Frame {
        require(width > 0 && height > 0 && pixels.size == width * height && maximum > 0)
        val turn = ((rotation % 360) + 360) % 360
        require(turn in listOf(0, 90, 180, 270))
        val uprightWidth = if (turn % 180 == 0) width else height
        val uprightHeight = if (turn % 180 == 0) height else width
        val ratio = minOf(1.0, maximum.toDouble() / maxOf(uprightWidth, uprightHeight))
        val outputWidth = kotlin.math.round(uprightWidth * ratio).toInt().coerceAtLeast(1)
        val outputHeight = kotlin.math.round(uprightHeight * ratio).toInt().coerceAtLeast(1)
        fun edge(index: Int, size: Int, count: Int) = ((index.toLong() * size + count / 2) / count).toInt()
        val output = IntArray(outputWidth * outputHeight)
        for (y in 0 until outputHeight) {
            val top = edge(y, uprightHeight, outputHeight)
            val bottom = edge(y + 1, uprightHeight, outputHeight)
            for (x in 0 until outputWidth) {
                val left = edge(x, uprightWidth, outputWidth)
                val right = edge(x + 1, uprightWidth, outputWidth)
                var red = 0L; var green = 0L; var blue = 0L
                for (v in top until bottom) for (u in left until right) {
                    val index = when (turn) {
                        0 -> v * width + u
                        90 -> (height - 1 - u) * width + v
                        180 -> (height - 1 - v) * width + width - 1 - u
                        else -> u * width + width - 1 - v
                    }
                    val pixel = pixels[index]
                    red += pixel shr 16 and 255; green += pixel shr 8 and 255; blue += pixel and 255
                }
                val count = (right - left) * (bottom - top)
                output[y * outputWidth + x] = (255 shl 24) or
                    (((red + count / 2) / count).toInt() shl 16) or
                    (((green + count / 2) / count).toInt() shl 8) or ((blue + count / 2) / count).toInt()
            }
        }
        return Frame(outputWidth, outputHeight, output)
    }
}
