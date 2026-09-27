package com.example.bismillah.engine.ocr

import android.content.Context
import android.graphics.Bitmap
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.example.bismillah.App
import com.example.bismillah.data.model.BoundingBox
import com.example.bismillah.data.model.TextBlock
import com.example.bismillah.util.BitmapUtils
import com.example.bismillah.util.ModelDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Collections

class PaddleOcrEngine(private val context: Context) {
    private val preprocessor = ImagePreprocessor()

    private var detSession: OrtSession? = null
    private var recSession: OrtSession? = null
    private var recKeys: List<String>? = null

    private fun env(): OrtEnvironment? =
        (context.applicationContext as? App)?.ortEnv

    private fun ensureSessions(): Boolean {
        val env = env() ?: return false
        return try {
            if (detSession == null) {
                val f = ModelDownloader.paddleFiles(context).getOrNull(0)
                val path = when {
                    f != null && f.exists() -> f.absolutePath
                    else -> copyAssetIfNeeded("paddle/ch_ppocr_mobile_det.onnx") ?: return false
                }
                detSession = env.createSession(path, OrtSession.SessionOptions())
            }
            if (recSession == null) {
                val f = ModelDownloader.paddleFiles(context).getOrNull(1)
                val path = when {
                    f != null && f.exists() -> f.absolutePath
                    else -> copyAssetIfNeeded("paddle/ch_ppocr_mobile_rec.onnx") ?: return false
                }
                recSession = env.createSession(path, OrtSession.SessionOptions())
            }
            if (recKeys == null) {
                recKeys = try {
                    context.assets.open("dict/ppocr_keys_v1.txt").bufferedReader().readLines()
                } catch (_: Exception) { null }
            }
            detSession != null && recSession != null
        } catch (_: Exception) { false }
    }

    private fun copyAssetIfNeeded(assetPath: String): String? {
        return try {
            val out = java.io.File(context.filesDir, "models/$assetPath")
            if (out.exists()) return out.absolutePath
            context.assets.open(assetPath).use { ins ->
                out.parentFile?.mkdirs()
                out.outputStream().use { ins.copyTo(it) }
            }
            out.absolutePath
        } catch (_: Exception) { null }
    }

    suspend fun recognize(bitmap: Bitmap): List<TextBlock> = withContext(Dispatchers.Default) {
        val prepared = preprocessor.prepare(bitmap)
        try {
            if (!ensureSessions()) return@withContext emptyList()
            val (resized, _) = preprocessor.resizeForDetector(prepared)
            val boxes = runDetector(resized)
            if (resized !== prepared) BitmapUtils.safeRecycle(resized)
            // Crop tiap box lalu recognizer (sequential agar hemat memori).
            val out = mutableListOf<TextBlock>()
            for (b in boxes) {
                val crop = safeCrop(prepared, b) ?: continue
                val text = runRecognizer(crop)
                BitmapUtils.safeRecycle(crop)
                if (text.isNotBlank()) out.add(TextBlock(b, text, 0.8f))
            }
            sortMangaReadingOrder(out)
        } catch (_: Exception) {
            emptyList()
        } finally {
            if (prepared !== bitmap) BitmapUtils.safeRecycle(prepared)
        }
    }

    private fun runDetector(resized: Bitmap): List<BoundingBox> {
        val env = env() ?: return emptyList()
        val det = detSession ?: return emptyList()
        val (buf, shape) = preprocessor.toChwFloat(resized)
        OnnxTensor.createTensor(env, buf, shape).use { input ->
            det.run(Collections.singletonMap(det.inputNames.first(), input)).use { res ->
                // PP-OCR DB head: output [1,1,H,W] prob map. Threshold 0.3 lalu ambil connected area kasar.
                val raw = (res[0].value as? Array<*>) ?: return fallbackGrid(resized)
                return decodeDbMap(raw, resized.width, resized.height)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun decodeDbMap(raw: Array<*>, w: Int, h: Int): List<BoundingBox> {
        return try {
            val batch = raw[0] as? Array<*> ?: return fallbackGrid(w, h)
            val ch = batch[0] as? Array<*> ?: return fallbackGrid(w, h)
            val rows = ch as? Array<FloatArray> ?: return fallbackGrid(w, h)
            val mh = rows.size
            if (mh == 0) return emptyList()
            val mw = rows[0].size
            // Downsample kasar: bagi map jadi grid 3x4, sel dengan mean > 0.3 jadi box.
            val gw = 4
            val gh = 3
            val boxes = mutableListOf<BoundingBox>()
            for (gy in 0 until gh) {
                for (gx in 0 until gw) {
                    var sum = 0f
                    var cnt = 0
                    for (y in (gy * mh / gh) until ((gy + 1) * mh / gh)) {
                        for (x in (gx * mw / gw) until ((gx + 1) * mw / gw)) {
                            sum += rows[y][x]
                            cnt++
                        }
                    }
                    if (cnt > 0 && sum / cnt > 0.3f) {
                        boxes.add(
                            BoundingBox(
                                x = gx * w / gw + 4,
                                y = gy * h / gh + 4,
                                width = w / gw - 8,
                                height = h / gh - 8
                            )
                        )
                    }
                }
            }
            boxes.ifEmpty { fallbackGrid(w, h) }
        } catch (_: Exception) {
            fallbackGrid(w, h)
        }
    }

    private fun fallbackGrid(w: Int, h: Int): List<BoundingBox> =
        listOf(BoundingBox(w / 4, h / 4, w / 2, h / 4))

    private fun fallbackGrid(bmp: Bitmap): List<BoundingBox> =
        fallbackGrid(bmp.width, bmp.height)

    private fun safeCrop(src: Bitmap, box: BoundingBox): Bitmap? {
        return try {
            val x = box.x.coerceIn(0, src.width - 1)
            val y = box.y.coerceIn(0, src.height - 1)
            val w = minOf(box.width, src.width - x).coerceAtLeast(8)
            val h = minOf(box.height, src.height - y).coerceAtLeast(8)
            Bitmap.createBitmap(src, x, y, w, h)
        } catch (_: Exception) { null }
    }

    private fun runRecognizer(crop: Bitmap): String {
        val env = env() ?: return ""
        val rec = recSession ?: return ""
        // Resize ke tinggi 48, lebar kelipatan 32 (CRNN PP-OCR).
        val th = 48
        val scale = th.toFloat() / crop.height.toFloat()
        var tw = (crop.width * scale).toInt().coerceAtLeast(32)
        tw = ((tw + 31) / 32) * 32
        val resized = Bitmap.createScaledBitmap(crop, tw, th, true)
        return try {
            val (buf, shape) = preprocessor.toChwFloat(resized)
            OnnxTensor.createTensor(env, buf, shape).use { input ->
                rec.run(Collections.singletonMap(rec.inputNames.first(), input)).use { res ->
                    ctcDecode(res[0].value)
                }
            }
        } catch (_: Exception) { "" } finally {
            BitmapUtils.safeRecycle(resized)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun ctcDecode(raw: Any?): String {
        return try {
            // Ekspektasi [1,T,C] float logits.
            val batch = (raw as? Array<*>)?.get(0) as? Array<*> ?: return ""
            val keys = recKeys ?: return ""
            val sb = StringBuilder()
            var prev = -1
            for (t in batch) {
                val logits = t as? FloatArray ?: continue
                var best = 0
                var bestV = Float.NEGATIVE_INFINITY
                for (i in logits.indices) {
                    if (logits[i] > bestV) { bestV = logits[i]; best = i }
                }
                // blank = 0
                if (best != 0 && best != prev && best < keys.size + 1) {
                    sb.append(keys[best - 1])
                }
                prev = best
            }
            sb.toString()
        } catch (_: Exception) { "" }
    }

    /** Manga: kolom kanan -> kiri, dalam kolom atas -> bawah. Dokumen biasa: atas -> bawah. */
    fun sortMangaReadingOrder(blocks: List<TextBlock>): List<TextBlock> {
        if (blocks.isEmpty()) return blocks
        val verticalCount = blocks.count { it.box.ratioHW > 1.35f }
        return if (verticalCount * 2 >= blocks.size) {
            blocks.sortedWith(compareByDescending<TextBlock> { it.box.x }.thenBy { it.box.y })
        } else {
            blocks.sortedWith(compareBy<TextBlock> { it.box.y }.thenBy { it.box.x })
        }
    }

    fun stubDemoBlock(screenW: Int, screenH: Int): TextBlock = TextBlock(
        box = BoundingBox(screenW / 4, screenH / 3, screenW / 2, screenH / 5),
        text = "DEMO: letakkan model PaddleOCR di assets/paddle untuk hasil nyata",
        confidence = 0f
    )

    fun close() {
        try { detSession?.close() } catch (_: Exception) {}
        try { recSession?.close() } catch (_: Exception) {}
        detSession = null
        recSession = null
    }
}
