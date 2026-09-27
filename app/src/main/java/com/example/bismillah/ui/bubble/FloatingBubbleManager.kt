package com.example.bismillah.ui.bubble

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import com.example.bismillah.R
import kotlin.math.abs

/** V4 gesture mapping: single = full translate, double = auto toggle, long-press = radial menu. */
data class BubbleGestures(
    val onSingleTap: () -> Unit,
    val onDoubleTap: () -> Unit,
    val onLongPress: () -> Unit
)

class FloatingBubbleManager(
    private val context: Context,
    private val gestures: () -> BubbleGestures
) {
    private val wm: WindowManager = context.getSystemService(WindowManager::class.java)
    private var bubbleView: CustomBubbleView? = null
    private var params: WindowManager.LayoutParams? = null
    var visible: Boolean = false
        private set

    companion object {
        private const val TAP_SLOP_PX = 10
        private const val DOUBLE_TAP_MS = 300L
        private const val LONG_PRESS_MS = 500L
    }

    @SuppressLint("ClickableViewAccessibility")
    fun show(): Boolean {
        if (bubbleView != null) {
            setVisible(true)
            return visible
        }

        val sizePx = (58 * context.resources.displayMetrics.density).toInt()
        val view = CustomBubbleView(context.applicationContext)

        val p = WindowManager.LayoutParams(
            sizePx, sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 350
        }

        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var isMoved = false
        var longPressFired = false
        var lastTapUp = 0L
        var pendingSingle: Runnable? = null
        val longPressTask = Runnable {
            if (!isMoved && !isProcessing()) {
                longPressFired = true
                android.util.Log.d("ScreenTranslator", "bubble long-press")
                try { gestures().onLongPress() } catch (e: Exception) {
                    android.util.Log.e("ScreenTranslator", "long-press handler failed", e)
                }
            }
        }

        view.setOnTouchListener { _, e ->
            view.resetIdleTimer()
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    startX = p.x
                    startY = p.y
                    isMoved = false
                    longPressFired = false
                    view.animatePress(true)
                    view.postDelayed(longPressTask, LONG_PRESS_MS)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - downX).toInt()
                    val dy = (e.rawY - downY).toInt()
                    if (abs(dx) + abs(dy) > TAP_SLOP_PX) {
                        if (!isMoved) {
                            isMoved = true
                            view.removeCallbacks(longPressTask)
                        }
                    }
                    p.x = startX + dx
                    p.y = startY + dy
                    try { wm.updateViewLayout(view, p) } catch (_: Exception) {}
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.animatePress(false)
                    view.removeCallbacks(longPressTask)
                    if (!isMoved && !longPressFired && e.action == MotionEvent.ACTION_UP) {
                        if (isProcessing()) {
                            android.util.Log.d("ScreenTranslator", "bubble tap ignored: processing (touch guard)")
                            return@setOnTouchListener true
                        }
                        val now = android.os.SystemClock.uptimeMillis()
                        if (now - lastTapUp < DOUBLE_TAP_MS) {
                            pendingSingle?.let { view.removeCallbacks(it) }
                            pendingSingle = null
                            lastTapUp = 0L
                            android.util.Log.d("ScreenTranslator", "bubble double-tap")
                            try { gestures().onDoubleTap() } catch (ex: Exception) {
                                android.util.Log.e("ScreenTranslator", "double-tap handler failed", ex)
                            }
                        } else {
                            lastTapUp = now
                            val task = Runnable {
                                android.util.Log.d("ScreenTranslator", "bubble tap received")
                                try { gestures().onSingleTap() } catch (ex: Exception) {
                                    android.util.Log.e("ScreenTranslator", "single-tap handler failed", ex)
                                }
                            }
                            pendingSingle = task
                            view.postDelayed(task, DOUBLE_TAP_MS)
                        }
                    } else if (isMoved) {
                        animateDocking(p, view, sizePx)
                    }
                }
            }
            true
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                p.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            wm.addView(view, p)
            bubbleView = view
            params = p
            visible = true
            view.resetIdleTimer()
            return true
        } catch (e: Exception) {
            android.util.Log.e("ScreenTranslator", "bubble addView failed (overlay permission?)", e)
            return false
        }
    }

    private fun animateDocking(
        p: WindowManager.LayoutParams,
        view: View,
        bubbleSize: Int
    ) {
        val screenWidth = context.resources.displayMetrics.widthPixels
        val targetX = if (p.x + bubbleSize / 2 < screenWidth / 2) 12 else screenWidth - bubbleSize - 12
        val startX = p.x

        val animator = ValueAnimator.ofInt(startX, targetX).apply {
            duration = 250
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                p.x = animation.animatedValue as Int
                try { wm.updateViewLayout(view, p) } catch (_: Exception) {}
            }
        }
        animator.start()
    }

    fun setVisible(v: Boolean) {
        val view = bubbleView ?: return
        try {
            view.visibility = if (v) View.VISIBLE else View.GONE
            visible = v
            if (v) view.resetIdleTimer()
        } catch (_: Exception) {}
    }

    fun hide() = setVisible(false)

    /** Touch Guard (V4): ignore triggers while PROCESSING to prevent multi-trigger spam. */
    fun isProcessing(): Boolean = try {
        bubbleView?.isProcessing() == true
    } catch (_: Exception) { false }

    fun showSuccess() {
        val view = bubbleView ?: return
        try {
            if (Looper.myLooper() == Looper.getMainLooper()) view.showSuccess()
            else view.post { try { view.showSuccess() } catch (_: Exception) {} }
        } catch (_: Exception) {}
    }

    /** Loading state: badge spins until translate finishes. Main-thread safe. */
    fun setLoading(loading: Boolean) {
        val view = bubbleView ?: return
        try {
            if (Looper.myLooper() == Looper.getMainLooper()) view.setLoading(loading)
            else view.post { try { view.setLoading(loading) } catch (_: Exception) {} }
        } catch (_: Exception) {}
    }

    fun dismiss() {
        bubbleView?.let {
            try { it.setLoading(false) } catch (_: Exception) {}
            it.stopIdleTimer()
            try { wm.removeView(it) } catch (_: Exception) {}
        }
        bubbleView = null
        params = null
        visible = false
    }
}

/**
 * Custom Canvas Drawn Floating Bubble with Modern Design:
 * - Gradient Background (Cyan to Indigo)
 * - Outer Glow & Ring
 * - Center Icon (Translate)
 * - Active Status Indicator Dot (Emerald Green)
 * - Pressed Scale Effect & Auto Dimming on Idle
 */
@SuppressLint("ViewConstructor")
private class CustomBubbleView(context: Context) : View(context) {

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#40000000")
        style = Paint.Style.FILL
    }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.parseColor("#8038BDF8")
    }

    private val statusDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#10B981") // Emerald Green
        style = Paint.Style.FILL
    }

    private val statusDotStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    // V4 PROCESSING: radar arc + neon glow halo.
    private val radarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#38BDF8")
        style = Paint.Style.STROKE
        strokeWidth = 7f
        strokeCap = Paint.Cap.ROUND
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3038BDF8")
        style = Paint.Style.STROKE
        strokeWidth = 14f
    }

    // V4 SUCCESS: ripple ring + check.
    private val successRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8034D399")
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }
    private val successFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#10B981")
        style = Paint.Style.FILL
    }
    private val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 7f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val checkPath = android.graphics.Path()

    private val iconDrawable: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_bubble_translate)
    private val magnifierDrawable: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_magnifier_small)

    private var isLoading = false
    private var loadingAngle = 0f
    private var loadingAnimator: ValueAnimator? = null
    private var showSuccess = false
    @Volatile var loading: Boolean = false
        private set

    private val handler = Handler(Looper.getMainLooper())
    private val idleRunnable = Runnable {
        animate().alpha(0.7f).setDuration(300).start() // V4 IDLE: semi-transparan ~70%
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun resetIdleTimer() {
        alpha = 1.0f
        handler.removeCallbacks(idleRunnable)
        handler.postDelayed(idleRunnable, 3500)
    }

    fun stopIdleTimer() {
        handler.removeCallbacks(idleRunnable)
    }

    fun animatePress(pressed: Boolean) {
        val targetScale = if (pressed) 0.90f else 1.0f
        animate().scaleX(targetScale).scaleY(targetScale).setDuration(120).start()
    }

    /** Spin the center icon while translating. Call from main thread. */
    fun setLoading(loading: Boolean) {
        if (loading == isLoading) return
        isLoading = loading
        showSuccess = false
        if (loading) {
            statusDotPaint.color = Color.parseColor("#F59E0B") // Amber = working
            handler.removeCallbacks(idleRunnable)
            alpha = 1.0f
            loadingAnimator?.cancel()
            loadingAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
                duration = 800 // V4: radar ~800 ms/putaran
                repeatCount = ValueAnimator.INFINITE
                interpolator = android.view.animation.LinearInterpolator()
                addUpdateListener { animation ->
                    loadingAngle = animation.animatedValue as Float
                    invalidate()
                }
            }.also { it.start() }
        } else {
            loadingAnimator?.cancel()
            loadingAnimator = null
            loadingAngle = 0f
            statusDotPaint.color = Color.parseColor("#10B981") // Emerald = idle
            invalidate()
            resetIdleTimer()
        }
    }

    fun isProcessing(): Boolean = isLoading

    /** V4 SUCCESS: ripple centang singkat, otomatis kembali idle. */
    fun showSuccess() {
        loadingAnimator?.cancel()
        loadingAnimator = null
        isLoading = false
        showSuccess = true
        statusDotPaint.color = Color.parseColor("#10B981")
        invalidate()
        handler.removeCallbacks(idleRunnable)
        handler.postDelayed({
            showSuccess = false
            invalidate()
            resetIdleTimer()
        }, 220)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            val gradient = LinearGradient(
                0f, 0f, w.toFloat(), h.toFloat(),
                Color.parseColor("#0EA5E9"), // Cyan
                Color.parseColor("#6366F1"), // Indigo
                Shader.TileMode.CLAMP
            )
            bgPaint.shader = gradient
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = (minOf(width, height) / 2f) - 12f

        // 1. Drop Shadow
        canvas.drawCircle(cx, cy + 4f, radius, shadowPaint)

        // 2. Gradient Circle Background
        canvas.drawCircle(cx, cy, radius, bgPaint)

        // 3. Glowing Border Ring
        canvas.drawCircle(cx, cy, radius, strokePaint)

        // 4. Center Translate Logo (V4: stays always; gentle pulse while processing)
        iconDrawable?.let { drawable ->
            var iconSize = (radius * 1.05f).toInt()
            if (isLoading) {
                val pulse = 1f + 0.1f * kotlin.math.abs(kotlin.math.sin(Math.toRadians(loadingAngle.toDouble() * 2)))
                iconSize = (iconSize * pulse).toInt()
            }
            val left = (cx - iconSize / 2f).toInt()
            val top = (cy - iconSize / 2f).toInt()
            drawable.setBounds(left, top, left + iconSize, top + iconSize)
            drawable.draw(canvas)
        }

        if (isLoading) {
            // 5a. Radar ring: neon arc rotating 360° + glow halo.
            canvas.save()
            canvas.rotate(loadingAngle, cx, cy)
            val radarRect = RectF(cx - radius - 4f, cy - radius - 4f, cx + radius + 4f, cy + radius + 4f)
            canvas.drawArc(radarRect, -90f, 110f, false, radarPaint)
            canvas.restore()
            canvas.drawCircle(cx, cy, radius + 4f, glowPaint)

            // 5b. Small spinning magnifier badge (replaces status dot while working).
            magnifierDrawable?.let { mag ->
                val dotCx = cx + radius * 0.65f
                val dotCy = cy - radius * 0.65f
                val badgeR = radius * 0.30f
                val half = badgeR.toInt()
                canvas.save()
                canvas.rotate(loadingAngle, dotCx, dotCy)
                mag.setBounds(
                    (dotCx - half).toInt(), (dotCy - half).toInt(),
                    (dotCx + half).toInt(), (dotCy + half).toInt()
                )
                mag.draw(canvas)
                canvas.restore()
            }
        } else if (showSuccess) {
            // 5c. SUCCESS ripple: expanding ring + check mark (~150-220 ms).
            canvas.drawCircle(cx, cy, radius + 10f, successRingPaint)
            canvas.drawCircle(cx, cy, radius * 0.45f, successFillPaint)
            val s = radius * 0.45f
            checkPath.reset()
            checkPath.moveTo(cx - s * 0.45f, cy + 0.02f)
            checkPath.lineTo(cx - s * 0.10f, cy + s * 0.35f)
            checkPath.lineTo(cx + s * 0.50f, cy - s * 0.35f)
            canvas.drawPath(checkPath, checkPaint)
        } else {
            // 5d. IDLE: emerald status dot (top right).
            val dotRadius = radius * 0.22f
            val dotCx = cx + radius * 0.65f
            val dotCy = cy - radius * 0.65f
            canvas.drawCircle(dotCx, dotCy, dotRadius, statusDotPaint)
            canvas.drawCircle(dotCx, dotCy, dotRadius, statusDotStroke)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopIdleTimer()
        try { loadingAnimator?.cancel() } catch (_: Exception) {}
        loadingAnimator = null
    }
}
