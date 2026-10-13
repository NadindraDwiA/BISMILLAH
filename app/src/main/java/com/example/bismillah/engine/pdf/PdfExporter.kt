package com.example.bismillah.engine.pdf

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.File

/**
 * Ekspor daftar paragraf teks ke PDF A4 (595 x 842 points) memakai
 * native [PdfDocument]. Halaman ditambah dinamis bila teks meluber.
 */
object PdfExporter {
    const val PAGE_W = 595
    const val PAGE_H = 842
    const val MARGIN = 48
    const val TEXT_SIZE = 12f
    const val LINE_SPACING_MULT = 1.25f
    const val PARAGRAPH_GAP = 10f

    fun exportTextToPdf(context: Context, textList: List<String>, outputFile: File) {
        val doc = PdfDocument()
        try {
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF000000.toInt()
                textSize = TEXT_SIZE
            }
            val contentW = PAGE_W - MARGIN * 2
            var pageNum = 1
            var page = doc.startPage(pageInfo(pageNum))
            var y = MARGIN.toFloat()

            fun newPage() {
                doc.finishPage(page)
                pageNum++
                page = doc.startPage(pageInfo(pageNum))
                y = MARGIN.toFloat()
            }

            for (para in textList) {
                val text = para.ifBlank { " " }
                var offset = 0
                while (offset < text.length) {
                    val availH = PAGE_H - MARGIN - y
                    if (availH < TEXT_SIZE * 2) {
                        newPage()
                        continue
                    }
                    // StaticLayout mengukur + wrap otomatis selebar konten.
                    val layout = StaticLayout.Builder.obtain(
                        text, offset, text.length, paint, contentW
                    ).setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setLineSpacing(0f, LINE_SPACING_MULT)
                        .setIncludePad(true)
                        .build()
                    // Cari baris terakhir yang muat di sisa halaman.
                    var fit = layout.lineCount
                    for (i in 0 until layout.lineCount) {
                        if (layout.getLineTop(i + 1) > availH) {
                            fit = i
                            break
                        }
                    }
                    if (fit <= 0) {
                        newPage()
                        continue
                    }
                    val end = layout.getLineEnd(fit - 1)
                    page.canvas.save()
                    page.canvas.translate(MARGIN.toFloat(), y)
                    page.canvas.clipRect(0f, 0f, contentW.toFloat(), layout.getLineTop(fit).toFloat())
                    layout.draw(page.canvas)
                    page.canvas.restore()
                    y += layout.getLineTop(fit)
                    offset = end
                    // Lewati newline ganda antar paragraf sudah via gap.
                    while (offset < text.length && text[offset] == '\n') offset++
                }
                y += PARAGRAPH_GAP
                if (y > PAGE_H - MARGIN) newPage()
            }

            doc.finishPage(page)
            outputFile.parentFile?.mkdirs()
            outputFile.outputStream().use { doc.writeTo(it) }
        } finally {
            try { doc.close() } catch (_: Exception) {}
        }
    }

    private fun pageInfo(pageNum: Int): PdfDocument.PageInfo =
        PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create()
}
