package com.example.bismillah.engine.document

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.example.bismillah.App
import com.example.bismillah.engine.ocr.ImagePreprocessor
import com.example.bismillah.util.BitmapUtils
import com.example.bismillah.util.ModelDownloader
import java.util.Collections

/**
 * V5 Step 1: Page Orientation (PP-LCNet_doc_ori) — deteksi rotasi halaman
 * 0°/90°/180°/270° lalu putar ke tegak. Tanpa model → gambar apa adanya.
 */
class PageRotator(private val context: Context) {
    private val preprocessor = ImagePreprocessor()
    private var session: OrtSession? = null

    private fun env(): OrtEnvironment? =
        (context.applicationContext as? App)?.ortEnv

    private fun ensureSession(): OrtSession? {
        session?.let { return it }
        val env = env() ?: return null
        return try {
            val f = ModelDownloader.docOriFile(context)
            val path = when {
                f.exists() -> f.absolutePath
                else -> copyAsset()?.absolutePath ?: run {
                    android.util.Log.w("ScreenTranslator", "doc_ori: model tidak ada, lewati rotate")
                    return null
                }
            }
            env.createSession(path, OrtSession.SessionOptions()).also { session = it }
        } catch (e: Exception) {
            android.util.Log.w("ScreenTranslator", "doc_ori: load gagal, lewati rotate", e)
            null
        }
    }

    private fun copyAsset(): java.io.File? {
        return try {
            val out = ModelDownloader.docOriFile(context)
            if (out.exists()) return out
            context.assets.open("paddle/${ModelDownloader.DOC_ORI}").use { ins ->
                out.parentFile?.mkdirs()
                out.outputStream().use { ins.copyTo(it) }
            }
            out
        } catch (_: Exception) { null }
    }

    /** @return derajat rotasi yang terdeteksi (0/90/180/270). */
    fun detectRotation(src: Bitmap): Int {
        val s = ensureSession() ?: return 0
        val env = env() ?: return 0
        // yml doc_ori: ResizeImage resize_short=256 + CropImage 224.
        val short = minOf(src.width, src.height)
        val scale = 256f / short.coerceAtLeast(1)
        val rw = (src.width * scale).toInt().coerceAtLeast(224)
        val rh = (src.height * scale).toInt().coerceAtLeast(224)
        val scaled = Bitmap.createScaledBitmap(src, rw, rh, true)
        val left = (rw - 224) / 2
        val top = (rh - 224) / 2
        val cropped = Bitmap.createBitmap(scaled, left, top, 224, 224)
        if (scaled !== cropped) BitmapUtils.safeRecycle(scaled)
        return try {
            val (buf, shape) = preprocessor.toChwFloat(cropped)
            OnnxTensor.createTensor(env, buf, shape).use { input ->
                s.run(Collections.singletonMap(s.inputNames.first(), input)).use { res ->
                    argmax(res[0].value)
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("ScreenTranslator", "doc_ori: inferensi gagal", e)
            0
        } finally {
            BitmapUtils.safeRecycle(cropped)
        }.let { cls -> intArrayOf(0, 90, 180, 270).getOrElse(cls) { 0 } }
    }

    /** Putar bitmap ke tegak; bitmap asal di-recycle bila menghasilkan bitmap baru. */
    fun upright(src: Bitmap): Bitmap {
        val deg = try { detectRotation(src) } catch (_: Exception) { 0 }
        if (deg == 0) return src
        return try {
            val m = Matrix().apply { postRotate(deg.toFloat()) }
            val out = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
            BitmapUtils.safeRecycle(src)
            out
        } catch (_: Exception) { src }
    }

    private fun argmax(raw: Any?): Int {
        return try {
            val arr = when (raw) {
                is Array<*> -> (raw[0] as? FloatArray) ?: return 0
                is FloatArray -> raw
                else -> return 0
            }
            var best = 0
            for (i in arr.indices) if (arr[i] > arr[best]) best = i
            best
        } catch (_: Exception) { 0 }
    }

    fun close() {
        try { session?.close() } catch (_: Exception) {}
        session = null
    }
}
