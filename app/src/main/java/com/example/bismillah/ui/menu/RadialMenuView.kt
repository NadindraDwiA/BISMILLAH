package com.example.bismillah.ui.menu

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.text.TextPaint
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot

enum class RadialAction { TRANSLATE, SETUP, AREA_SOON, IMAGE_SOON, SUBTITLE_SOON, DISMISSED }

/** V4 radial quick-menu: T center + Area/Image/Subtitle/Setup satellites. */
@SuppressLint("ViewConstructor")
class RadialMenuView(
    context: Context,
    private val onAction: (RadialAction) -> Unit
) : View(context) {

    private data class Node(val x: Float, val y: Float, val r: Float, val label: String, val sub: String, val enabled: Boolean, val action: RadialAction)

    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#99000000") }
    private val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#CC121212") }
    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#0EA5E9") }
    private val disabledPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#66121212") }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3f; color = Color.parseColor("#8038BDF8")
    }
    private val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 44f; textAlign = Paint.Align.CENTER
    }
    private val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B0BEC5"); textSize = 26f; textAlign = Paint.Align.CENTER
    }

    private var nodes: List<Node> = emptyList()
    private var dismissed = false

    init {
        setBackgroundColor(Color.TRANSPARENT)
        isClickable = true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val cx = w / 2f
        val cy = h / 2f
        val orbit = minOf(w, h) * 0.27f
        val r = orbit * 0.34f
        nodes = listOf(
            Node(cx, cy, r * 1.1f, "T", "Translate", true, RadialAction.TRANSLATE),
            Node(cx, cy - orbit, r, "A", "Area", false, RadialAction.AREA_SOON),
            Node(cx + orbit, cy, r, "I", "Image", false, RadialAction.IMAGE_SOON),
            Node(cx - orbit, cy, r, "S", "Subtitle", false, RadialAction.SUBTITLE_SOON),
            Node(cx, cy + orbit, r, "⚙", "Setup", true, RadialAction.SETUP)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)
        for (n in nodes) {
            val fill = when {
                n.action == RadialAction.TRANSLATE -> centerPaint
                n.enabled -> nodePaint
                else -> disabledPaint
            }
            canvas.drawCircle(n.x, n.y, n.r, fill)
            canvas.drawCircle(n.x, n.y, n.r, ringPaint)
            canvas.drawText(n.label, n.x, n.y + 14f, labelPaint)
            canvas.drawText(n.sub, n.x, n.y + n.r + 30f, subPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP && !dismissed) {
            dismissed = true
            val hit = nodes.firstOrNull {
                hypot(event.x - it.x, event.y - it.y) <= it.r + 30f
            }
            onAction(hit?.action ?: RadialAction.DISMISSED)
        }
        return true
    }
}
