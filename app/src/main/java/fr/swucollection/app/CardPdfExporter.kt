package fr.swucollection.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.graphics.drawable.toBitmap
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

/** Writes a temporary complete PDF; a failed image load never produces a blank proxy sheet. */
internal object CardPdfExporter {
    suspend fun create(context: Context, title: String, pages: List<List<PdfPlacement>>, proxies: Boolean,
        progress: (Int, Int) -> Unit = { _, _ -> }): File = withContext(Dispatchers.IO) {
        val output = File.createTempFile("swu-print-", ".pdf", context.cacheDir)
        val document = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        try {
            var done = 0
            val total = pages.sumOf { it.size }
            pages.forEachIndexed { index, items ->
                coroutineContext.ensureActive()
                val page = document.startPage(PdfDocument.PageInfo.Builder(CardPdfPlan.PAGE_WIDTH, CardPdfPlan.PAGE_HEIGHT, index + 1).create())
                try {
                val canvas = page.canvas
                canvas.drawColor(Color.WHITE)
                paint.color = Color.BLACK
                paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
                paint.textSize = if (proxies) 7f else 13f
                val heading = if (proxies) "PROXIES · $title" else title
                canvas.drawText(heading.take(75), 22f, if (proxies) 15f else 23f, paint)
                if (!proxies) {
                    paint.textSize = 8f; paint.typeface = Typeface.DEFAULT
                    canvas.drawText("Deck illustré · leader, base, deck principal et réserve · quantités indiquées sous les cartes", 22f, 39f, paint)
                }
                items.forEach { place ->
                    coroutineContext.ensureActive()
                    val url = if (place.item.back) place.item.card.backImageUrl else place.item.card.imageUrl
                    require(url.startsWith("https://")) { "Visuel indisponible : ${place.item.card.name}. Actualise le catalogue puis réessaie." }
                    val request = ImageRequest.Builder(context).data(OfflineImages.source(context, url)).allowHardware(false).size(900).build()
                    val result = Coil.imageLoader(context).execute(request)
                    check(result is SuccessResult) { "Impossible de charger ${place.item.card.name}. Vérifie ta connexion puis réessaie." }
                    val bitmap = result.drawable.toBitmap()
                    drawCard(canvas, bitmap, place, paint, proxies)
                    if (proxies) cropMarks(canvas, place, paint) else {
                        paint.color = Color.BLACK; paint.typeface = Typeface.DEFAULT; paint.textSize = 8f
                        val line = "${place.item.section} · ×${place.item.quantity} · ${place.item.card.setCode} ${place.item.card.number}"
                        canvas.drawText(line, place.x, place.y + place.height + 10f, paint)
                        paint.textSize = 7f
                        canvas.drawText(place.item.card.name.take(38), place.x, place.y + place.height + 20f, paint)
                    }
                    progress(++done, total)
                }
                paint.color = Color.DKGRAY; paint.typeface = Typeface.DEFAULT; paint.textSize = 7f
                canvas.drawText(if (proxies) "PROXIES · impression à 100 % / taille réelle · repères de découpe" else "SWU Collection · $title".take(80), 22f, 832f, paint)
                canvas.drawText("${index + 1}/${pages.size}", 548f, 832f, paint)
                } finally { document.finishPage(page) }
            }
            output.outputStream().use(document::writeTo)
            output
        } catch (error: Throwable) {
            output.delete()
            throw error
        } finally { document.close() }
    }
    private fun drawCard(canvas: Canvas, bitmap: Bitmap, p: PdfPlacement, paint: Paint, physical: Boolean) {
        canvas.save()
        canvas.translate(p.x + p.width / 2, p.y + p.height / 2)
        val rotated = physical && bitmap.width > bitmap.height
        if (rotated) canvas.rotate(90f)
        val boxW = if (rotated) p.height else p.width
        val boxH = if (rotated) p.width else p.height
        val ratio = minOf(boxW / bitmap.width, boxH / bitmap.height)
        val w = if (physical) boxW else bitmap.width * ratio
        val h = if (physical) boxH else bitmap.height * ratio
        canvas.drawBitmap(bitmap, null, RectF(-w / 2, -h / 2, w / 2, h / 2), paint)
        canvas.restore()
    }
    private fun cropMarks(canvas: Canvas, p: PdfPlacement, paint: Paint) {
        paint.color = Color.GRAY; paint.strokeWidth = .35f
        for (x in listOf(p.x, p.x + p.width)) for (y in listOf(p.y, p.y + p.height)) {
            val dx = if (x == p.x) -1 else 1
            val dy = if (y == p.y) -1 else 1
            canvas.drawLine(x + dx, y, x + 3 * dx, y, paint)
            canvas.drawLine(x, y + dy, x, y + 3 * dy, paint)
        }
    }
}
