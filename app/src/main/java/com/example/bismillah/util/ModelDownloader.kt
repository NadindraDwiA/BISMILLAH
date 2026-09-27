package com.example.bismillah.util

import android.content.Context
import java.io.File

/**
 * Model files on device. NLLB removed: translation is 100% cloud (Gemini 2.5 Flash).
 * Only the small on-device PaddleOCR pack remains here.
 */
object ModelDownloader {
    fun paddleFiles(ctx: Context): List<File> {
        val dir = File(ctx.filesDir, "models/paddle")
        return listOf("ch_ppocr_mobile_det.onnx", "ch_ppocr_mobile_rec.onnx", "ch_ppocr_mobile_cls.onnx")
            .map { File(dir, it) }
    }

    fun dictFile(ctx: Context): File = File(ctx.filesDir, "models/dict/ppocr_keys_v1.txt")

    fun hasPaddle(ctx: Context): Boolean {
        val files = paddleFiles(ctx)
        // det + rec wajib; cls opsional.
        return files.take(2).all { it.exists() && it.length() > 100 * 1024 }
    }

    /** Copy model kecil Paddle + dict dari assets ke filesDir (cepat, tanpa download). */
    suspend fun ensurePaddleFromAssets(ctx: Context): Boolean {
        return try {
            val pairs = listOf(
                "paddle/ch_ppocr_mobile_det.onnx" to paddleFiles(ctx)[0],
                "paddle/ch_ppocr_mobile_rec.onnx" to paddleFiles(ctx)[1],
                "paddle/ch_ppocr_mobile_cls.onnx" to paddleFiles(ctx)[2],
                "dict/ppocr_keys_v1.txt" to dictFile(ctx)
            )
            for ((asset, out) in pairs) {
                if (out.exists() && out.length() > 0) continue
                try {
                    ctx.assets.open(asset).use { ins ->
                        out.parentFile?.mkdirs()
                        out.outputStream().use { ins.copyTo(it) }
                    }
                } catch (_: Exception) {
                    // cls + dict boleh absen; det/rec wajib.
                    if (asset.contains("det.onnx") || asset.contains("rec.onnx")) return false
                }
            }
            hasPaddle(ctx)
        } catch (_: Exception) { false }
    }
}
