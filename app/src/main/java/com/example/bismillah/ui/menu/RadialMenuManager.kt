package com.example.bismillah.ui.menu

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.view.WindowManager
import com.example.bismillah.core.navigation.Routes

/** Hosts [RadialMenuView] as a TYPE_APPLICATION_OVERLAY window. */
class RadialMenuManager(private val context: Context) {
    private var view: RadialMenuView? = null
    private val wm: WindowManager = context.getSystemService(WindowManager::class.java)

    fun show(onTranslate: () -> Unit, onMessage: (String) -> Unit = {}) {
        dismiss()
        val v = RadialMenuView(context.applicationContext) { action ->
            dismiss()
            when (action) {
                RadialAction.TRANSLATE -> onTranslate()
                RadialAction.SETUP -> openAppSettings()
                RadialAction.AREA_SOON -> onMessage("Area Translation segera hadir")
                RadialAction.IMAGE_SOON -> onMessage("Image Translation segera hadir")
                RadialAction.SUBTITLE_SOON -> onMessage("Subtitle Translation segera hadir")
                RadialAction.DISMISSED -> {}
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            wm.addView(v, params)
            view = v
        } catch (e: Exception) {
            android.util.Log.e("ScreenTranslator", "radial menu addView failed", e)
            onMessage("Menu gagal tampil — izinkan overlay dulu")
        }
    }

    private fun openAppSettings() {
        try {
            val appCtx = context.applicationContext
            val launch = appCtx.packageManager.getLaunchIntentForPackage(appCtx.packageName)
                ?: return
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            launch.putExtra("route", Routes.SETTINGS)
            appCtx.startActivity(launch)
        } catch (e: Exception) {
            android.util.Log.e("ScreenTranslator", "open settings failed", e)
        }
    }

    fun dismiss() {
        view?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        view = null
    }
}
