package com.example.bismillah.engine.ocr

import android.graphics.Bitmap
import com.example.bismillah.util.BitmapUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class ImagePreprocessor {
    fun prepare(src: Bitmap): Bitmap = BitmapUtils.upscaleIfSmall(src, 32)

    /** Resize agar sisi panjang = maxSide dan kedua sisi kelipatan 32 (syarat DB detector). */
    fun resizeForDetector(src: Bitmap, maxSide: Int = 1280): Pair<Bitmap, Float> {
        val w = src.width
        val h = src.height
        val scale = maxSide.toFloat() / maxOf(w, h).toFloat().coerceAtLeast(1f)
        val s = if (scale >= 1f) 1f else scale
        var nw = (w * s).toInt()
        var nh = (h * s).toInt()
        nw = ((nw + 31) / 32) * 32
        nh = ((nh + 31) / 32) * 32
        nw = nw.coerceAtLeast(32)
        nh = nh.coerceAtLeast(32)
        if (nw == w && nh == h) return src to 1f
        val out = Bitmap.createScaledBitmap(src, nw, nh, true)
        return out to (nw.toFloat() / w.toFloat())
    }

    /**
     * Bitmap ARGB -> CHW float normalized.
     * yml DecodeImage img_mode=BGR: urutan kanal WAJIB B,G,R (bukan R,G,B),
     * dengan mean/std [0.485,0.456,0.406]/[0.229,0.224,0.225] per urutan itu.
     * Buffer WAJIB direct: OnnxTensor.createTensor menolak heap buffer.
     */
    fun toChwFloat(bitmap: Bitmap): Pair<FloatBuffer, LongArray> {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val buf = ByteBuffer.allocateDirect(3 * w * h * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        val plane = FloatArray(w * h)
        // Kanal 0 = B.
        for (i in pixels.indices) {
            val p = pixels[i]
            plane[i] = ((p and 0xFF) / 255f - 0.485f) / 0.229f
        }
        buf.put(plane)
        // Kanal 1 = G.
        for (i in pixels.indices) {
            val p = pixels[i]
            plane[i] = (((p shr 8) and 0xFF) / 255f - 0.456f) / 0.224f
        }
        buf.put(plane)
        // Kanal 2 = R.
        for (i in pixels.indices) {
            val p = pixels[i]
            plane[i] = (((p shr 16) and 0xFF) / 255f - 0.406f) / 0.225f
        }
        buf.put(plane)
        buf.rewind()
        return buf to longArrayOf(1, 3, h.toLong(), w.toLong())
    }

    /** V5 rec (yml tanpa NormalizeImage): CHW float 0..1 urutan B,G,R, buffer direct. */
    fun toChw01(bitmap: Bitmap): Pair<FloatBuffer, LongArray> {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val buf = ByteBuffer.allocateDirect(3 * w * h * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        val plane = FloatArray(w * h)
        for (i in pixels.indices) plane[i] = (pixels[i] and 0xFF) / 255f
        buf.put(plane)
        for (i in pixels.indices) plane[i] = (((pixels[i] shr 8) and 0xFF)) / 255f
        buf.put(plane)
        for (i in pixels.indices) plane[i] = (((pixels[i] shr 16) and 0xFF)) / 255f
        buf.put(plane)
        buf.rewind()
        return buf to longArrayOf(1, 3, h.toLong(), w.toLong())
    }
    /** Autocontrast per kanal untuk second-pass blok lemah. */
    fun autocontrast(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        var loR = 255
        var hiR = 0
        var loG = 255
        var hiG = 0
        var loB = 255
        var hiB = 0
        for (p in pixels) {
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            if (r < loR) loR = r
            if (r > hiR) hiR = r
            if (g < loG) loG = g
            if (g > hiG) hiG = g
            if (b < loB) loB = b
            if (b > hiB) hiB = b
        }
        // Kontras sudah lebar → kembalikan asal.
        if (hiR - loR >= 200 && hiG - loG >= 200 && hiB - loB >= 200) return src
        val out = IntArray(w * h)
        val rRange = maxOf(1, hiR - loR)
        val gRange = maxOf(1, hiG - loG)
        val bRange = maxOf(1, hiB - loB)
        for (i in pixels.indices) {
            val p = pixels[i]
            val a = (p ushr 24) and 0xFF
            val r = (((p shr 16) and 0xFF) - loR) * 255 / rRange
            val g = (((p shr 8) and 0xFF) - loG) * 255 / gRange
            val b = ((p and 0xFF) - loB) * 255 / bRange
            out[i] = (a shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
        }
        return try {
            Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
        } catch (_: Exception) {
            src
        }
    }
}
