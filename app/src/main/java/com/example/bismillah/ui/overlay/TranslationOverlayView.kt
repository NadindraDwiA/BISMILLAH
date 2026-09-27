package com.example.bismillah.ui.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.MotionEvent
import android.view.View
import com.example.bismillah.data.model.TextBlock

@SuppressLint("ViewConstructor")
class TranslationOverlayView(
    context: Context,
    private val blocks: List<Pair<TextBlock, String>>,
    private val prefs: com.example.bismillah.data.preference.AppPreferences.Settings? = null,
    private val onDismiss: () -> Unit
) : View(context) {

    // V3 Dimmed Card: abu/hitam semi-transparan #CC121212, radius 16px.
    private val cardBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CC121212")
        style = Paint.Style.FILL
    }

    // V3 Typography: putih bersih, rata kiri, spasi 1.15x.
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFFFF")
        textSize = 42f
    }

    init {
        setBackgroundColor(Color.TRANSPARENT) // V3: layer overlay transparan penuh
        isClickable = true
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    private fun buildLayout(text: String, maxTextW: Int, sizePx: Float): StaticLayout {
        val paint = TextPaint(textPaint).apply { textSize = sizePx }
        return StaticLayout.Builder.obtain(
            text, 0, text.length, paint, maxTextW
        ).setAlignment(Layout.Alignment.ALIGN_NORMAL) // rata kiri
            .setLineSpacing(0f, 1.15f)
            .setIncludePad(false)
            .build()
    }

    private fun maxLineWidth(layout: StaticLayout): Float {
        var m = 0f
        for (i in 0 until layout.lineCount) m = maxOf(m, layout.getLineWidth(i))
        return m
    }

    override fun performClick(): Boolean {
        super.performClick()
        onDismiss()
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            performClick()
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val sw = width
        val sh = height

        for ((block, translated) in blocks) {
            val text = translated.ifBlank { block.text }
            if (text.isBlank()) continue
            // V3 typology: Micro (<=4 chars, no spaces) vs Dialogue.
            val maxTextW = LayoutCalculator.maxWidth(text, sw)
            val pad = LayoutCalculator.padding(text)
            // V4 auto-adapt: shrink font until text fits, floored at min_text_size (default 17sp).
            val metrics = resources.displayMetrics
            val minPx = android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_SP,
                (prefs?.minTextSp ?: 17).toFloat(), metrics
            )
            var sizePx = 42f
            var layout = buildLayout(text, maxTextW, sizePx)
            if (prefs?.autoFontSize != false) {
                while ((layout.lineCount > 6 || maxLineWidth(layout) > maxTextW) && sizePx > minPx) {
                    sizePx = (sizePx - 2f).coerceAtLeast(minPx)
                    layout = buildLayout(text, maxTextW, sizePx)
                    if (sizePx <= minPx) break
                }
            }
            val cardW = (maxLineWidth(layout) + pad * 2).toInt().coerceAtLeast(48)
            val cardH = layout.height + pad * 2
            val rb = LayoutCalculator.place(
                block.box.centerX, block.box.centerY, cardW, cardH, sw, sh
            )
            val rect = RectF(
                rb.x.toFloat(),
                rb.y.toFloat(),
                (rb.x + rb.w).toFloat(),
                (rb.y + rb.h).toFloat()
            )

            // V3: rounded dimmed box + teks horizontal putih.
            canvas.drawRoundRect(rect, 16f, 16f, cardBgPaint)

            canvas.save()
            canvas.translate((rb.x + pad).toFloat(), (rb.y + pad).toFloat())
            layout.draw(canvas)
            canvas.restore()
        }
    }
}
