package com.example.bismillah.ui.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.WindowManager
import com.example.bismillah.data.model.TextBlock

class TranslationOverlayManager(private val context: Context) {
    private var view: TranslationOverlayView? = null
    private val wm: WindowManager = context.getSystemService(WindowManager::class.java)

    /** @return true bila overlay benar-benar terpasang. */
    fun show(
        blocks: List<Pair<TextBlock, String>>,
        prefs: com.example.bismillah.data.preference.AppPreferences.Settings? = null
    ): Boolean {
        dismiss()
        val v = TranslationOverlayView(context.applicationContext, blocks, prefs) { dismiss() }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            wm.addView(v, params)
            view = v
            true
        } catch (e: Exception) {
            android.util.Log.e("ScreenTranslator", "overlay addView failed", e)
            false
        }
    }

    fun dismiss() {
        view?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        view = null
    }
}
