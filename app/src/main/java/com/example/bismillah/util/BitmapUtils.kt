package com.example.bismillah.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix

object BitmapUtils {
    fun upscaleIfSmall(src: Bitmap, minHeight: Int = 32): Bitmap {
        if (src.height >= minHeight) return src
        val scale = minHeight.toFloat() / src.height.toFloat()
        val m = Matrix().apply { postScale(scale, scale) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    fun safeRecycle(b: Bitmap?) {
        try { if (b != null && !b.isRecycled) b.recycle() } catch (_: Exception) {}
    }
}
