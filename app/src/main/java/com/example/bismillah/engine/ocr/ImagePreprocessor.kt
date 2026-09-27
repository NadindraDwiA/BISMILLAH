package com.example.bismillah.engine.ocr

import android.graphics.Bitmap
import com.example.bismillah.util.BitmapUtils
import java.nio.FloatBuffer

class ImagePreprocessor {
    fun prepare(src: Bitmap): Bitmap = BitmapUtils.upscaleIfSmall(src, 32)

    /** Resize agar sisi panjang = maxSide dan kedua sisi kelipatan 32 (syarat DB detector). */
    fun resizeForDetector(src: Bitmap, maxSide: Int = 960): Pair<Bitmap, Float> {
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

    /** Bitmap ARGB -> CHW float normalized (mean/std ImageNet). */
    fun toChwFloat(bitmap: Bitmap): Pair<FloatBuffer, LongArray> {
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val buf = FloatBuffer.allocate(3 * w * h)
        val r = FloatArray(w * h)
        val g = FloatArray(w * h)
        val b = FloatArray(w * h)
        for (i in pixels.indices) {
            val p = pixels[i]
            r[i] = (((p shr 16) and 0xFF) / 255f - 0.485f) / 0.229f
            g[i] = (((p shr 8) and 0xFF) / 255f - 0.456f) / 0.224f
            b[i] = ((p and 0xFF) / 255f - 0.406f) / 0.225f
        }
        buf.put(r)
        buf.put(g)
        buf.put(b)
        buf.rewind()
        return buf to longArrayOf(1, 3, h.toLong(), w.toLong())
    }
}
