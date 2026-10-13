package com.example.bismillah.engine.ocr

import android.graphics.Bitmap
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/** Hasil [Bitmap.toDetectorTensor]: buffer siap OnnxTensor + metadata skala. */
data class DetectorTensor(
    /** Direct FloatBuffer CHW BGR ternormalisasi, bentuk [1, 3, H, W]. */
    val buffer: FloatBuffer,
    val shape: LongArray,
    /** Dimensi tensor (kelipatan 32). */
    val width: Int,
    val height: Int,
    /** Faktor skala bitmap_asli -> tensor (untuk petakan box kembali). */
    val scaleX: Float,
    val scaleY: Float
)

/**
 * Siapkan input model deteksi DBNet ONNX dari Bitmap:
 * 1. Resize proporsional: sisi terpanjang = kelipatan 32 terdekat dari [targetLongSize].
 * 2. RGB (Bitmap) -> planar BGR (CHW).
 * 3. Normalisasi (pixel/255 - mean)/std, mean=[0.485,0.456,0.406], std=[0.229,0.224,0.225].
 * 4. Direct FloatBuffer [1,3,H,W] (heap buffer ditolak OnnxTensor).
 */
fun Bitmap.toDetectorTensor(targetLongSize: Int = 1280): DetectorTensor {
    val longSide = maxOf(width, height).coerceAtLeast(1)
    var nw = ((width.toFloat() * targetLongSize / longSide / 32).toInt() * 32).coerceAtLeast(32)
    var nh = ((height.toFloat() * targetLongSize / longSide / 32).toInt() * 32).coerceAtLeast(32)
    // Jaga: sisi terpanjang hasil harus kelipatan 32 terdekat target.
    val resized = if (nw == width && nh == height) {
        this
    } else {
        Bitmap.createScaledBitmap(this, nw, nh, true)
    }
    nw = resized.width
    nh = resized.height

    val pixels = IntArray(nw * nh)
    resized.getPixels(pixels, 0, nw, 0, 0, nw, nh)
    val buf = ByteBuffer.allocateDirect(3 * nw * nh * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
    val plane = FloatArray(nw * nh)
    val means = floatArrayOf(0.485f, 0.456f, 0.406f)
    val stds = floatArrayOf(0.229f, 0.224f, 0.225f)
    // Planar B,G,R.
    for (c in 0..2) {
        for (i in pixels.indices) {
            val p = pixels[i]
            val v = when (c) {
                0 -> p and 0xFF
                1 -> (p shr 8) and 0xFF
                else -> (p shr 16) and 0xFF
            }
            plane[i] = (v / 255f - means[c]) / stds[c]
        }
        buf.put(plane)
    }
    buf.rewind()
    return DetectorTensor(
        buffer = buf,
        shape = longArrayOf(1, 3, nh.toLong(), nw.toLong()),
        width = nw,
        height = nh,
        scaleX = nw.toFloat() / width.toFloat(),
        scaleY = nh.toFloat() / height.toFloat()
    )
}
