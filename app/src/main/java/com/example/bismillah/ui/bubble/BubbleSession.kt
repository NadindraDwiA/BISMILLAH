package com.example.bismillah.ui.bubble

import android.content.Context

/**
 * Single owner of the floating bubble overlay (V4 gesture engine).
 *
 * Single tap = full translation, double tap = auto-translate toggle,
 * long-press (>=500ms) = radial quick menu. [start] is idempotent and
 * handlers are always refreshed to the latest caller.
 */
object BubbleSession {
    private var manager: FloatingBubbleManager? = null
    private var gestures: BubbleGestures? = null
    private var lastContext: Context? = null

    @Volatile
    var active: Boolean = false
        private set

    @Synchronized
    fun start(context: Context, gestures: BubbleGestures): Boolean {
        this.gestures = gestures
        lastContext = context.applicationContext
        val m = manager ?: FloatingBubbleManager(context.applicationContext) {
            gestures()
        }.also { manager = it }
        val ok = try { m.show() } catch (e: Exception) {
            android.util.Log.e("ScreenTranslator", "bubble show failed", e)
            false
        }
        active = ok
        android.util.Log.d("ScreenTranslator", "bubble start ok=$ok attached=${m.isAttached()}")
        return ok
    }

    private fun gestures(): BubbleGestures =
        gestures ?: BubbleGestures({}, {}, {})

    @Synchronized
    fun setVisible(visible: Boolean) {
        manager?.setVisible(visible)
    }

    @Synchronized
    fun setLoading(loading: Boolean) {
        try { manager?.setLoading(loading) } catch (_: Exception) {}
    }

    /** V4 SUCCESS ripple (~150-220ms). Call on Main thread before showing the card. */
    @Synchronized
    fun showSuccess() {
        try { manager?.showSuccess() } catch (_: Exception) {}
    }

    @Synchronized
    fun isShowing(): Boolean = manager?.visible == true

    /**
     * Health-check + auto-recover: bila view lepas dari window (mis. dicabut
     * sistem), buat ulang otomatis dari gestures terakhir. Kembalikan true
     * bila bubble tampil setelah panggilan ini.
     */
    @Synchronized
    fun ensureVisible(): Boolean {
        val m = manager
        val attached = m?.isAttached() == true
        android.util.Log.d(
            "ScreenTranslator",
            "bubble ensureVisible active=$active attached=$attached showing=${m?.visible}"
        )
        if (m != null && attached) {
            if (m.visible != true) m.setVisible(true)
            active = true
            return true
        }
        // View lepas / manager hilang → recreate sekali.
        val ctx = lastContext
        val g = gestures
        if (ctx == null || g == null) {
            active = false
            return false
        }
        try { m?.dismiss() } catch (_: Exception) {}
        manager = null
        val fresh = FloatingBubbleManager(ctx) { gestures() }
        manager = fresh
        val ok = try { fresh.show() } catch (e: Exception) {
            android.util.Log.e("ScreenTranslator", "bubble recover failed", e)
            false
        }
        active = ok
        android.util.Log.d("ScreenTranslator", "bubble recovered ok=$ok")
        return ok
    }

    @Synchronized
    fun stop() {
        try { manager?.dismiss() } catch (_: Exception) {}
        manager = null
        gestures = null
        active = false
    }
}
