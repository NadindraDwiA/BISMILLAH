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

    @Volatile
    var active: Boolean = false
        private set

    @Synchronized
    fun start(context: Context, gestures: BubbleGestures): Boolean {
        this.gestures = gestures
        val m = manager ?: FloatingBubbleManager(context.applicationContext) {
            gestures()
        }.also { manager = it }
        val ok = try { m.show() } catch (e: Exception) {
            android.util.Log.e("ScreenTranslator", "bubble show failed", e)
            false
        }
        active = ok
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

    @Synchronized
    fun stop() {
        try { manager?.dismiss() } catch (_: Exception) {}
        manager = null
        gestures = null
        active = false
    }
}
