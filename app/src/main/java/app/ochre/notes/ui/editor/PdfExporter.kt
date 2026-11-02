package app.ochre.notes.ui.editor

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.res.ResourcesCompat
import app.ochre.notes.R
import app.ochre.notes.ui.components.MdBlock
import app.ochre.notes.ui.components.parseBlocks
import app.ochre.notes.ui.util.Formatting
import java.io.File
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PdfExporter(private val context: Context) {

    suspend fun export(
        title: String,
        content: String,
        images: List<File>,
        accentColor: Int?,
    ): File = withContext(Dispatchers.IO) {
        val document = PdfDocument()
        val renderer = PdfRenderer(
            document = document,
            serifTypeface = ResourcesCompat.getFont(context, R.font.fraunces) ?: Typeface.SERIF,
            sansTypeface = ResourcesCompat.getFont(context, R.font.inter) ?: Typeface.SANS_SERIF,
            accent = accentColor ?: FALLBACK_ACCENT,
        )
        try {
            renderer.drawTitle(if (title.isBlank()) "Untitled" else title)
            parseBlocks(content).forEach { block ->
                when (block) {
                    is MdBlock.Heading -> renderer.drawHeading(block.level, block.text)
                    is MdBlock.Paragraph -> renderer.drawBody(block.text)
                    is MdBlock.Quote -> renderer.drawQuote(block.text)
                    is MdBlock.Bullet -> renderer.drawBullet(block.text, block.marker, block.checked, block.level)
                    is MdBlock.Code -> renderer.drawCode(block.text)
                    is MdBlock.Table -> renderer.drawTable(block)
                    MdBlock.Divider -> renderer.drawDivider()
                }
            }
            images.forEach { renderer.drawImage(it) }
            renderer.finish()
            val directory = File(context.cacheDir, "shared").apply { mkdirs() }
            val file = File(directory, "Ochre-${slug(title)}-${System.currentTimeMillis()}.pdf")
            file.outputStream().use { document.writeTo(it) }
            file
        } finally {
            document.close()
        }
    }

    private fun slug(title: String): String {
        val cleaned = title.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}]+"), "-")
            .trim('-')
            .take(40)
        return cleaned.ifBlank { "note" }
    }

    private companion object {
        const val FALLBACK_ACCENT = 0xFFA45F2C.toInt()
    }
}

private class PdfRenderer(
    private val document: PdfDocument,
    serifTypeface: Typeface,
    sansTypeface: Typeface,
    private val accent: Int,
) {
    private val pageWidth = 595
    private val pageHeight = 842
    private val margin = 48f
    private val right = pageWidth - margin
    private val bottomLimit = pageHeight - 54f

    private val serif = Typeface.create(serifTypeface, Typeface.BOLD)
    private val sans = sansTypeface
    private val sansBold = Typeface.create(sansTypeface, Typeface.BOLD)
    private val sansItalic = Typeface.create(sansTypeface, Typeface.ITALIC)

    private val ink = 0xFF211C16.toInt()
    private val muted = 0xFF6A6054.toInt()
    private val surface = 0xFFF3ECE1.toInt()
    private val border = 0xFFDCD1C1.toInt()

    private val titlePaint = textPaint(24f, ink, serif)
    private val metaPaint = textPaint(9f, muted, sans)
    private val bodyPaint = textPaint(10.8f, ink, sans)
    private val quotePaint = textPaint(10.8f, muted, sansItalic)
    private val bulletPaint = textPaint(10.8f, ink, sans)
    private val markerPaint = textPaint(10.8f, accent, sansBold)
    private val codePaint = textPaint(9f, ink, Typeface.MONOSPACE)
    private val footerPaint = textPaint(8f, muted, sans)
    private val cellPaint = textPaint(9.5f, ink, sans)
    private val cellBoldPaint = textPaint(9.5f, ink, sansBold)
    private val accentFill = fillPaint(accent)
    private val surfaceFill = fillPaint(surface)
    private val borderFill = fillPaint(border)
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var pageNumber = 0
    private var page: PdfDocument.Page? = null
    private var canvas: Canvas? = null
    private var y = margin

    private fun textPaint(size: Float, color: Int, typeface: Typeface) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        this.typeface = typeface
    }

    private fun fillPaint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }

    fun drawTitle(title: String) {
        ensure(0f)
        wrapLines(plainInline(title), titlePaint, right - margin).forEach { line ->
            ensure(30f)
            canvas!!.drawText(line, margin, y + 24f, titlePaint)
            y += 30f
        }
        y += 2f
        ensure(13f)
        canvas!!.drawText("Ochre · ${Formatting.dateTime(System.currentTimeMillis())}", margin, y + 9f, metaPaint)
        y += 20f
        canvas!!.drawRect(margin, y, margin + 46f, y + 3f, accentFill)
        y += 20f
    }

    fun drawHeading(level: Int, raw: String) {
        val size = when (level) {
            1 -> 21f
            2 -> 17f
            3 -> 14.5f
            else -> 12.5f
        }
        val paint = textPaint(size, ink, serif)
        val before = when (level) {
            1 -> 20f
            2 -> 16f
            3 -> 14f
            else -> 12f
        }
        y += before
        val lineHeight = size * 1.35f
        wrapLines(plainInline(raw), paint, right - margin).forEach { line ->
            ensure(lineHeight)
            canvas!!.drawText(line, margin, y + size, paint)
            y += lineHeight
        }
        y += 6f
    }

    fun drawBody(text: String) {
        drawParagraphs(plainInline(text), bodyPaint, 16.2f, before = 2f, after = 4f)
    }

    fun drawQuote(text: String) {
        if (text.isBlank()) return
        y += 6f
        val lines = text.split('\n').flatMap { line ->
            wrapLines(plainInline(line), quotePaint, right - margin - 20f)
        }
        if (lines.isEmpty()) return
        val lineHeight = 16f
        val height = lines.size * lineHeight + 14f
        ensure(height)
        val top = y
        canvas!!.drawRect(margin, top, margin + 3f, top + height, accentFill)
        var lineY = top + 7f
        lines.forEach { line ->
            canvas!!.drawText(line, margin + 16f, lineY + 10.8f, quotePaint)
            lineY += lineHeight
        }
        y = top + height + 8f
    }

    fun drawBullet(text: String, marker: String, checked: Boolean?, level: Int) {
        val indent = margin + level * 14f
        val textLeft = indent + 16f
        val paint = if (checked == true) quotePaint else bulletPaint
        val lines = wrapLines(plainInline(text), paint, right - textLeft)
        if (lines.isEmpty()) return
        val glyph = when (checked) {
            null -> marker
            true -> "☑"
            false -> "☐"
        }
        lines.forEachIndexed { index, line ->
            ensure(16f)
            if (index == 0) {
                canvas!!.drawText(glyph, indent, y + 10.8f, markerPaint)
            }
            canvas!!.drawText(line, textLeft, y + 10.8f, paint)
            y += 16f
        }
        y += 2f
    }

    fun drawCode(text: String) {
        y += 6f
        val content = if (text.isBlank()) " " else text
        val lines = content.split('\n').flatMap { line ->
            wrapLines(line.ifBlank { " " }, codePaint, right - margin - 20f)
        }
        lines.forEach { line ->
            ensure(15f)
            canvas!!.drawRect(margin, y, right, y + 15f, surfaceFill)
            canvas!!.drawText(line, margin + 10f, y + 11f, codePaint)
            y += 15f
        }
        y += 8f
    }

    fun drawTable(table: MdBlock.Table) {
        val columns = max(1, table.header.size)
        val columnWidth = (right - margin) / columns
        y += 6f
        drawTableRow(table.header, cellBoldPaint, columnWidth, columns, header = true)
        table.rows.forEach { row ->
            drawTableRow(row, cellPaint, columnWidth, columns, header = false)
        }
        y += 10f
    }

    private fun drawTableRow(
        cells: List<String>,
        paint: Paint,
        columnWidth: Float,
        columns: Int,
        header: Boolean,
    ) {
        val wrapped = List(columns) { column ->
            wrapLines(
                plainInline(cells.getOrElse(column) { "" }).ifBlank { " " },
                paint,
                columnWidth - 12f,
            )
        }
        if (wrapped.all { it.isEmpty() }) return
        val rowHeight = wrapped.maxOf { it.size }.coerceAtLeast(1) * 12.5f + 10f
        ensure(rowHeight + 1f)
        if (header) {
            canvas!!.drawRect(margin, y, right, y + rowHeight, surfaceFill)
        }
        var x = margin
        wrapped.forEach { lines ->
            lines.forEachIndexed { index, line ->
                canvas!!.drawText(line, x + 6f, y + 10f + index * 12.5f, paint)
            }
            x += columnWidth
        }
        canvas!!.drawRect(margin, y + rowHeight, right, y + rowHeight + 0.8f, borderFill)
        y += rowHeight
    }

    fun drawDivider() {
        y += 8f
        ensure(10f)
        canvas!!.drawRect(margin, y, right, y + 1f, borderFill)
        y += 12f
    }

    fun drawImage(file: File) {
        if (!file.exists()) return
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return
        val maxWidth = (right - margin).toInt()
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxWidth && bounds.outHeight / (sample * 2) >= 400) {
            sample *= 2
        }
        val bitmap = BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: return
        try {
            var width = maxWidth.toFloat()
            var height = width * bitmap.height / bitmap.width
            val maxHeight = pageHeight - margin - 70f
            if (height > maxHeight) {
                height = maxHeight
                width = height * bitmap.width / bitmap.height
            }
            ensure(height + 12f)
            val top = y
            canvas!!.drawBitmap(bitmap, null, RectF(margin, top, margin + width, top + height), imagePaint)
            y = top + height + 14f
        } finally {
            bitmap.recycle()
        }
    }

    fun finish() {
        finishPage()
    }

    private fun drawParagraphs(
        text: String,
        paint: Paint,
        lineHeight: Float,
        before: Float,
        after: Float,
    ) {
        if (text.isBlank()) return
        y += before
        text.split('\n').forEach { rawLine ->
            val lines = if (rawLine.isBlank()) listOf("") else wrapLines(rawLine, paint, right - margin)
            lines.forEach { line ->
                ensure(lineHeight)
                if (line.isNotEmpty()) {
                    canvas!!.drawText(line, margin, y + paint.textSize, paint)
                }
                y += lineHeight
            }
        }
        y += after
    }

    private fun ensure(height: Float) {
        if (canvas == null) newPage()
        if (y + height > bottomLimit) newPage()
    }

    private fun newPage() {
        finishPage()
        pageNumber += 1
        val info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        val newPage = document.startPage(info)
        newPage.canvas.drawColor(AndroidColor.WHITE)
        page = newPage
        canvas = newPage.canvas
        y = margin
    }

    private fun finishPage() {
        val current = page ?: return
        val currentCanvas = current.canvas
        currentCanvas.drawText("Ochre", margin, pageHeight - 28f, footerPaint)
        val label = "Page $pageNumber"
        currentCanvas.drawText(label, right - footerPaint.measureText(label), pageHeight - 28f, footerPaint)
        document.finishPage(current)
        page = null
        canvas = null
    }

    private fun wrapLines(text: String, paint: Paint, width: Float): List<String> {
        if (text.isBlank()) return emptyList()
        val words = text.trim().split(Regex("\\s+"))
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        fun flush() {
            if (current.isNotEmpty()) {
                lines += current.toString()
                current = StringBuilder()
            }
        }
        words.forEach { word ->
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= width) {
                current = StringBuilder(candidate)
            } else {
                flush()
                if (paint.measureText(word) > width) {
                    var chunk = StringBuilder()
                    word.forEach { character ->
                        if (paint.measureText(chunk.toString() + character) > width && chunk.isNotEmpty()) {
                            lines += chunk.toString()
                            chunk = StringBuilder()
                        }
                        chunk.append(character)
                    }
                    current = chunk
                } else {
                    current = StringBuilder(word)
                }
            }
        }
        flush()
        return lines
    }

    private fun plainInline(text: String): String {
        var output = text
        output = output.replace(Regex("`([^`]*)`"), "$1")
        output = output.replace(Regex("\\*\\*(.+?)\\*\\*|__(.+?)__")) { match ->
            match.groupValues[1].ifBlank { match.groupValues[2] }
        }
        output = output.replace(Regex("~~(.+?)~~"), "$1")
        output = output.replace(Regex("\\[\\[([^]]+)]]"), "$1")
        output = output.replace(Regex("\\[([^]]+)]\\([^)]*\\)"), "$1")
        output = output.replace(Regex("\\*(.+?)\\*|_(.+?)_")) { match ->
            match.groupValues[1].ifBlank { match.groupValues[2] }
        }
        return output
    }
}
