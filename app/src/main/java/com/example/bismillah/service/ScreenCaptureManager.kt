package com.example.bismillah.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class ScreenCaptureManager(private val context: Context) {
    suspend fun captureOnce(): Bitmap? = suspendCancellableCoroutine { cont ->
        val mp = MediaProjectionHolder.mediaProjection
        if (mp == null) {
            Log.w("ScreenTranslator", "capture: no MediaProjection (permission not granted this session?)")
            cont.resume(null)
            return@suspendCancellableCoroutine
        }

        val metrics = context.resources.displayMetrics
        val w = metrics.widthPixels
        val h = metrics.heightPixels
        val density = metrics.densityDpi

        val thread = HandlerThread("capture").apply { start() }
        val handler = Handler(thread.looper)
        var vd: VirtualDisplay? = null
        var reader: ImageReader? = null
        var settled = false

        // Android 14+ (API 34): wajib register callback SEBELUM createVirtualDisplay,
        // kalau tidak: IllegalStateException "Must register a callback before starting capture".
        val callback = object : MediaProjection.Callback() {
            override fun onStop() {
                Log.w("ScreenTranslator", "capture: MediaProjection stopped")
            }
        }

        fun cleanup() {
            try { mp.unregisterCallback(callback) } catch (_: Exception) {}
            try { vd?.release() } catch (_: Exception) {}
            try { reader?.close() } catch (_: Exception) {}
            thread.quitSafely()
        }

        fun settle(value: Bitmap?) {
            if (settled) return
            settled = true
            cleanup()
            if (cont.isActive) cont.resume(value)
        }

        try {
            mp.registerCallback(callback, handler)
        } catch (e: Exception) {
            Log.e("ScreenTranslator", "capture: registerCallback failed", e)
            cleanup()
            if (cont.isActive) cont.resume(null)
            return@suspendCancellableCoroutine
        }

        try {
            reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        } catch (e: Exception) {
            Log.e("ScreenTranslator", "capture: ImageReader failed", e)
            cleanup()
            if (cont.isActive) cont.resume(null)
            return@suspendCancellableCoroutine
        }

        reader.setOnImageAvailableListener({ r ->
            try {
                val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
                val plane = image.planes[0]
                val rs = plane.rowStride
                val ps = plane.pixelStride
                val tmpW = rs / ps
                val tmp = Bitmap.createBitmap(tmpW, h, Bitmap.Config.ARGB_8888)
                tmp.copyPixelsFromBuffer(plane.buffer)
                image.close()
                val out = Bitmap.createBitmap(tmp, 0, 0, w, h)
                if (tmp != out) tmp.recycle()
                settle(out)
            } catch (e: Exception) {
                Log.e("ScreenTranslator", "capture: decode frame failed", e)
                settle(null)
            }
        }, handler)

        try {
            vd = mp.createVirtualDisplay(
                "screen-translator", w, h, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.surface, null, handler
            )
        } catch (e: Exception) {
            Log.e("ScreenTranslator", "capture: createVirtualDisplay failed", e)
            settle(null)
        }
        cont.invokeOnCancellation {
            if (!settled) {
                settled = true
                cleanup()
            }
        }
    }
}
