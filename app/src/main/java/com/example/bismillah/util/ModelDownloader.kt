package com.example.bismillah.util

import android.content.Context
import java.io.File

/**
 * Model files on device. NLLB removed: translation is 100% cloud (Gemini 2.5 Flash).
 * Only the small on-device PaddleOCR pack remains here.
 */
object ModelDownloader {
    const val DET_V5 = "ppocrv5_det.onnx"
    const val REC_V5 = "ppocrv5_rec.onnx"
    const val LINE_ORI = "PPLCNet_texline_ori_infer.onnx"
    const val DOC_ORI = "PPLCNet_doc_ori_infer.onnx"
    const val DICT_V5 = "ppocrv5_keys.txt"

    fun paddleDir(ctx: Context): File = File(ctx.filesDir, "models/paddle")

    /** Pack v5 saja (ch_mobile + cls dihapus total). */
    fun paddleFiles(ctx: Context): List<File> {
        val dir = paddleDir(ctx)
        return listOf(DET_V5, REC_V5).map { File(dir, it) }
    }

    fun detCandidates(ctx: Context): List<File> =
        listOf(File(paddleDir(ctx), DET_V5))

    fun recCandidates(ctx: Context): List<File> =
        listOf(File(paddleDir(ctx), REC_V5))

    fun lineOriCandidates(ctx: Context): List<File> =
        listOf(File(paddleDir(ctx), LINE_ORI))

    fun docOriFile(ctx: Context): File = File(paddleDir(ctx), DOC_ORI)

    fun dictFile(ctx: Context): File = File(ctx.filesDir, "models/dict/ppocr_keys_v1.txt")

    fun dictV5File(ctx: Context): File = File(ctx.filesDir, "models/dict/$DICT_V5")

    fun hasPaddle(ctx: Context): Boolean {
        // det+rec v5 utama, fallback ch_mobile.
        val detOk = detCandidates(ctx).any { it.exists() && it.length() > 100 * 1024 }
        val recOk = recCandidates(ctx).any { it.exists() && it.length() > 100 * 1024 }
        return detOk && recOk
    }

    /** Copy model kecil Paddle + dict dari assets ke filesDir (cepat, tanpa download). */
    suspend fun ensurePaddleFromAssets(ctx: Context): Boolean {
        return try {
            val pairs = listOf(
                // Pack v5 terbaru saja.
                "paddle/$DET_V5" to File(paddleDir(ctx), DET_V5),
                "paddle/$REC_V5" to File(paddleDir(ctx), REC_V5),
                "paddle/$LINE_ORI" to File(paddleDir(ctx), LINE_ORI),
                "paddle/$DOC_ORI" to docOriFile(ctx),
                // Dict v5 (utama) + legacy.
                "paddle/$DICT_V5" to dictV5File(ctx),
                "paddle/ppocr_keys_v1.txt" to dictFile(ctx),
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
                    // Fallback/varian boleh absen sebagian; hasPaddle() yang menentukan.
                }
            }
            hasPaddle(ctx)
        } catch (_: Exception) { false }
    }
}
