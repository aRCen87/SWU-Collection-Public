package fr.swucollection.app

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure

/** Traces the three letters from the unchanged 475 x 185 home artwork. */
internal class HomeLogoContour {
    private val contours = listOf(
        Path().apply {
            moveTo(58.00f, 17.75f)
            lineTo(90.50f, 17.75f)
            lineTo(93.00f, 18.75f)
            lineTo(95.00f, 17.75f)
            lineTo(98.00f, 18.75f)
            lineTo(100.00f, 17.75f)
            lineTo(102.75f, 18.00f)
            lineTo(103.75f, 22.00f)
            lineTo(103.75f, 29.50f)
            lineTo(103.00f, 30.25f)
            lineTo(66.50f, 29.75f)
            lineTo(62.00f, 30.25f)
            lineTo(58.75f, 33.00f)
            lineTo(59.25f, 35.00f)
            lineTo(61.00f, 36.75f)
            lineTo(92.50f, 36.75f)
            lineTo(99.50f, 39.75f)
            lineTo(103.75f, 44.50f)
            lineTo(105.25f, 49.50f)
            lineTo(104.75f, 58.00f)
            lineTo(102.75f, 62.00f)
            lineTo(100.00f, 64.75f)
            lineTo(94.00f, 67.75f)
            lineTo(45.75f, 67.50f)
            lineTo(46.00f, 55.25f)
            lineTo(88.00f, 55.25f)
            lineTo(91.25f, 53.00f)
            lineTo(91.25f, 51.00f)
            lineTo(89.00f, 48.75f)
            lineTo(57.00f, 48.75f)
            lineTo(54.00f, 47.75f)
            lineTo(48.75f, 43.50f)
            lineTo(45.75f, 37.50f)
            lineTo(45.75f, 29.00f)
            lineTo(46.75f, 26.00f)
            lineTo(49.50f, 24.75f)
            lineTo(54.00f, 20.75f)
            lineTo(56.50f, 19.75f)
            close()
        },
        Path().apply {
            moveTo(148.50f, 17.75f)
            lineTo(150.50f, 17.75f)
            lineTo(152.50f, 19.25f)
            lineTo(154.50f, 19.25f)
            lineTo(158.75f, 21.00f)
            lineTo(160.75f, 27.00f)
            lineTo(163.75f, 32.50f)
            lineTo(164.75f, 36.50f)
            lineTo(167.75f, 42.00f)
            lineTo(168.75f, 46.00f)
            lineTo(170.00f, 47.25f)
            lineTo(171.75f, 45.50f)
            lineTo(171.75f, 44.00f)
            lineTo(173.75f, 41.00f)
            lineTo(176.75f, 32.50f)
            lineTo(179.25f, 30.50f)
            lineTo(178.25f, 29.50f)
            lineTo(182.00f, 21.25f)
            lineTo(186.50f, 18.75f)
            lineTo(192.50f, 18.75f)
            lineTo(196.75f, 21.00f)
            lineTo(193.75f, 24.50f)
            lineTo(193.75f, 28.00f)
            lineTo(192.75f, 29.00f)
            lineTo(190.75f, 35.50f)
            lineTo(182.75f, 53.00f)
            lineTo(182.75f, 54.50f)
            lineTo(179.75f, 60.00f)
            lineTo(178.75f, 64.00f)
            lineTo(176.50f, 67.75f)
            lineTo(164.00f, 67.75f)
            lineTo(160.75f, 62.00f)
            lineTo(155.75f, 49.00f)
            lineTo(152.50f, 43.75f)
            lineTo(150.75f, 45.50f)
            lineTo(141.50f, 67.75f)
            lineTo(129.00f, 67.75f)
            lineTo(128.25f, 67.00f)
            lineTo(115.75f, 37.50f)
            lineTo(115.75f, 33.50f)
            lineTo(114.75f, 32.50f)
            lineTo(114.75f, 31.00f)
            lineTo(111.25f, 26.50f)
            lineTo(112.25f, 25.50f)
            lineTo(109.75f, 21.50f)
            lineTo(111.50f, 19.75f)
            lineTo(113.50f, 19.25f)
            lineTo(117.00f, 19.75f)
            lineTo(120.00f, 18.75f)
            lineTo(121.50f, 19.75f)
            lineTo(121.75f, 22.50f)
            lineTo(124.75f, 24.50f)
            lineTo(125.75f, 27.00f)
            lineTo(124.75f, 28.00f)
            lineTo(125.25f, 30.50f)
            lineTo(127.50f, 31.75f)
            lineTo(128.75f, 33.50f)
            lineTo(128.75f, 35.00f)
            lineTo(133.75f, 46.00f)
            lineTo(135.50f, 47.25f)
            lineTo(136.75f, 46.00f)
            lineTo(138.75f, 40.00f)
            lineTo(143.75f, 30.00f)
            lineTo(141.75f, 25.50f)
            lineTo(142.75f, 23.00f)
            lineTo(141.75f, 22.00f)
            lineTo(141.25f, 19.50f)
            lineTo(142.50f, 18.25f)
            lineTo(147.00f, 19.25f)
            close()
        },
        Path().apply {
            moveTo(251.50f, 17.75f)
            lineTo(259.50f, 17.75f)
            lineTo(262.25f, 20.50f)
            lineTo(261.75f, 24.00f)
            lineTo(262.75f, 27.00f)
            lineTo(262.75f, 52.00f)
            lineTo(261.75f, 54.00f)
            lineTo(261.75f, 56.50f)
            lineTo(258.75f, 61.50f)
            lineTo(254.00f, 65.75f)
            lineTo(248.50f, 67.75f)
            lineTo(216.50f, 67.75f)
            lineTo(210.00f, 64.75f)
            lineTo(205.75f, 60.00f)
            lineTo(203.25f, 53.00f)
            lineTo(203.25f, 28.50f)
            lineTo(204.75f, 27.00f)
            lineTo(204.75f, 22.00f)
            lineTo(203.25f, 20.50f)
            lineTo(205.50f, 18.75f)
            lineTo(214.50f, 18.75f)
            lineTo(215.75f, 19.50f)
            lineTo(215.75f, 27.00f)
            lineTo(217.25f, 28.50f)
            lineTo(217.25f, 50.00f)
            lineTo(220.50f, 53.75f)
            lineTo(242.50f, 54.25f)
            lineTo(245.50f, 53.75f)
            lineTo(248.75f, 50.00f)
            lineTo(249.25f, 23.50f)
            lineTo(250.25f, 18.50f)
            close()
        }
    ).map { PathMeasure(it, true) }
    private val segment = Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    /** Called with the existing reflection clock; idle and reduced-motion frames draw nothing. */
    fun draw(canvas: Canvas, progress: Float) {
        if (progress <= 0f || progress >= 1f) return
        val envelope = minOf(1f, progress * 14f, (1f - progress) * 14f)
        for (contour in contours) {
            val length = contour.length
            if (length <= 0f) continue
            val head = progress * length
            val trail = length * 0.13f
            for (index in 0 until 24) {
                val strength = (index + 1) / 24f
                val start = head - trail + trail * index / 24f
                val end = head - trail + trail * (index + 1) / 24f
                segment.rewind()
                if (start < 0f) {
                    contour.getSegment((start + length).coerceAtLeast(0f),
                        (end + length).coerceAtMost(length), segment, true)
                    if (end > 0f) contour.getSegment(0f, end, segment, true)
                } else contour.getSegment(start, end, segment, true)
                paint.color = 0xFFFFDA8A.toInt()
                paint.strokeWidth = 4f
                paint.alpha = (28f * strength * strength * envelope).toInt()
                canvas.drawPath(segment, paint)
                paint.strokeWidth = 2f
                paint.alpha = (70f * strength * strength * envelope).toInt()
                canvas.drawPath(segment, paint)
                paint.color = 0xFFFFF5D7.toInt()
                paint.strokeWidth = 0.8f
                paint.alpha = (230f * strength * strength * envelope).toInt()
                canvas.drawPath(segment, paint)
            }
        }
    }
}
