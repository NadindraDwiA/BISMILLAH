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
                // Pack v5 terbaru (filesDir dulu, lalu assets).
                val path = ModelDownloader.detCandidates(context).firstOrNull { it.exists() }?.absolutePath
                    ?: copyAssetIfNeeded("paddle/${ModelDownloader.DET_V5}")
                    ?: return false
                detSession = env.createSession(path, OrtSession.SessionOptions())
            }
            if (recSession == null) {
                val path = ModelDownloader.recCandidates(context).firstOrNull { it.exists() }?.absolutePath
                    ?: copyAssetIfNeeded("paddle/${ModelDownloader.REC_V5}")
                    ?: return false
                recSession = env.createSession(path, OrtSession.SessionOptions())
            }
            if (recKeys == null) {
                // Dict v5 sesuai inference.yml; fallback legacy.
                val v5 = ModelDownloader.dictV5File(context)
                recKeys = try {
                    when {
                        v5.exists() -> v5.bufferedReader().readLines()
                        else -> context.assets.open("paddle/${ModelDownloader.DICT_V5}").bufferedReader().readLines()
                    }
                } catch (_: Exception) {
                    try {
                        val legacy = ModelDownloader.dictFile(context)
                        if (legacy.exists()) legacy.bufferedReader().readLines()
                        else context.assets.open("paddle/ppocr_keys_v1.txt").bufferedReader().readLines()
                    } catch (_: Exception) {
                        try {
                            context.assets.open("dict/ppocr_keys_v1.txt").bufferedReader().readLines()
                        } catch (_: Exception) { null }
                    }
                }
                if (recKeys == null) {
                    android.util.Log.w("ScreenTranslator", "ocr: kamus karakter tidak ditemukan")
                }
            }
            val ok = detSession != null && recSession != null && recKeys != null
            if (!ok) {
                android.util.Log.w(
                    "ScreenTranslator",
                    "ocr: sessions tidak lengkap det=${detSession != null} rec=${recSession != null} keys=${recKeys != null}"
                )
            }
            ok
        } catch (e: Exception) {
            android.util.Log.e("ScreenTranslator", "ocr: ensureSessions gagal", e)
            false
        }
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
            // Migrasi: tensor via Bitmap.toDetectorTensor (BGR planar, direct buffer).
            val tensor = prepared.toDetectorTensor(1280)
            val boxes = runDetector(tensor)
            // Petakan box koordinat tensor -> koordinat prepared via scaleX/scaleY.
            // Crop tiap box lalu recognizer (sequential agar hemat memori).
            // Blok lemah (conf rendah) diulang sekali dengan upscale + autocontrast.
            val out = mutableListOf<TextBlock>()
            for ((idx, b) in boxes.withIndex()) {
                val mapped = BoundingBox(
                    x = (b.x / tensor.scaleX).toInt(),
                    y = (b.y / tensor.scaleY).toInt(),
                    width = (b.width / tensor.scaleX).toInt(),
                    height = (b.height / tensor.scaleY).toInt()
                )
                val crop = safeCrop(prepared, mapped) ?: continue
                var (text, conf) = runRecognizer(crop)
                if (text.isNotBlank() && conf < RETRY_CONF) {
                    val retry = retryCrop(crop)
                    if (retry != null) {
                        val (text2, conf2) = runRecognizer(retry)
                        BitmapUtils.safeRecycle(retry)
                        if (text2.isNotBlank() && conf2 > conf) {
                            text = text2
                            conf = conf2
                        }
                    }
                }
                BitmapUtils.safeRecycle(crop)
                if (text.isNotBlank()) {
                    out.add(TextBlock(mapped, text, conf))
                    android.util.Log.d(
                        "ScreenTranslator",
                        "ocr: blok#$idx box=${mapped.x},${mapped.y},${mapped.width}x${mapped.height} " +
                            "conf=${"%.2f".format(conf)} teks=${text.take(24)}"
                    )
                }
            }
            android.util.Log.d(
                "ScreenTranslator",
                "ocr: ${out.size}/${boxes.size} blok terbaca " +
                    "(boxThresh=$DB_BOX_THRESH, detLong=$DET_LONG_SIDE)"
            )
            sortMangaReadingOrder(out)
        } catch (e: Exception) {
            android.util.Log.e("ScreenTranslator", "ocr: recognize gagal", e)
            emptyList()
        } finally {
            if (prepared !== bitmap) BitmapUtils.safeRecycle(prepared)
        }
    }

    private fun runDetector(tensor: DetectorTensor): List<BoundingBox> {
        val env = env() ?: return emptyList()
        val det = detSession ?: return emptyList()
        OnnxTensor.createTensor(env, tensor.buffer, tensor.shape).use { input ->
            det.run(Collections.singletonMap(det.inputNames.first(), input)).use { res ->
                // PP-OCR DB head: output [1,1,H,W] prob map. Threshold 0.3 lalu komponen terhubung.
                val raw = (res[0].value as? Array<*>) ?: return fallbackGrid(tensor.width, tensor.height)
                return decodeDbMap(raw, tensor.width, tensor.height)
            }
        }
    }

    /** yml DBPostProcess: thresh=0.3, box_thresh=0.6, unclip_ratio=1.5, max_candidates=1000. */
    companion object {
        const val DB_THRESH = 0.3f
        /** Diturunkan 0.6→0.45 untuk recall (banyak teks terlewat). Naikkan bila noise. */
        var DB_BOX_THRESH = 0.45f
        const val DB_UNCLIP_RATIO = 1.5f
        const val DB_MAX_CANDIDATES = 1000
        const val DB_MIN_PIXELS = 4
        /** Sisi panjang deteksi: 1280 untuk teks kecil (yml 960). */
        var DET_LONG_SIDE = 1280
        /** Ambang retry pass-2. */
        const val RETRY_CONF = 0.5f
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
            // 1. Binarisasi pada thresh.
            val labels = IntArray(mw * mh) { -1 }
            var nComp = 0
            for (y in 0 until mh) {
                for (x in 0 until mw) {
                    if (rows[y][x] < DB_THRESH) continue
                    val left = if (x > 0) labels[y * mw + x - 1] else -1
                    val up = if (y > 0) labels[(y - 1) * mw + x] else -1
                    when {
                        left < 0 && up < 0 -> {
                            labels[y * mw + x] = nComp
                            nComp++
                        }
                        left < 0 -> labels[y * mw + x] = up
                        up < 0 -> labels[y * mw + x] = left
                        left == up -> labels[y * mw + x] = left
                        else -> {
                            // union: relabel up menjadi left.
                            labels[y * mw + x] = left
                            for (i in labels.indices) if (labels[i] == up) labels[i] = left
                        }
                    }
                }
            }
            if (nComp == 0) return emptyList()
            // 2. Kumpulkan statistik komponen.
            val minX = IntArray(nComp) { Int.MAX_VALUE }
            val minY = IntArray(nComp) { Int.MAX_VALUE }
            val maxX = IntArray(nComp) { Int.MIN_VALUE }
            val maxY = IntArray(nComp) { Int.MIN_VALUE }
            val sum = FloatArray(nComp)
            val cnt = IntArray(nComp)
            for (y in 0 until mh) {
                for (x in 0 until mw) {
                    val c = labels[y * mw + x]
                    if (c < 0) continue
                    if (x < minX[c]) minX[c] = x
                    if (y < minY[c]) minY[c] = y
                    if (x > maxX[c]) maxX[c] = x
                    if (y > maxY[c]) maxY[c] = y
                    sum[c] += rows[y][x]
                    cnt[c]++
                }
            }
            // 3. box_thresh + unclip + skala ke piksel + cap kandidat.
            val boxes = mutableListOf<Pair<Float, BoundingBox>>()
            var nSmall = 0
            var nLowScore = 0
            for (c in 0 until nComp) {
                if (cnt[c] < 4) {
                    nSmall++
                    continue
                }
                val mean = sum[c] / cnt[c]
                if (mean < DB_BOX_THRESH) {
                    nLowScore++
                    continue
                }
                val sx = w.toFloat() / mw
                val sy = h.toFloat() / mh
                var bx0 = minX[c] * sx
                var by0 = minY[c] * sy
                var bx1 = (maxX[c] + 1) * sx
                var by1 = (maxY[c] + 1) * sy
                // Unclip: kembangkan dari titik tengah.
                val cx = (bx0 + bx1) / 2f
                val cy = (by0 + by1) / 2f
                val hw = (bx1 - bx0) / 2f * DB_UNCLIP_RATIO
                val hh = (by1 - by0) / 2f * DB_UNCLIP_RATIO
                bx0 = (cx - hw).coerceIn(0f, w.toFloat())
                by0 = (cy - hh).coerceIn(0f, h.toFloat())
                bx1 = (cx + hw).coerceIn(0f, w.toFloat())
                by1 = (cy + hh).coerceIn(0f, h.toFloat())
                val bw = (bx1 - bx0).toInt()
                val bh = (by1 - by0).toInt()
                if (bw < 4 || bh < 4) continue
                boxes.add(mean to BoundingBox(bx0.toInt(), by0.toInt(), bw, bh))
            }
            boxes.sortedByDescending { it.first }
                .take(DB_MAX_CANDIDATES)
                .map { it.second }
                .also {
                    android.util.Log.d(
                        "ScreenTranslator",
                        "ocr: det comp=$nComp kept=${it.size} kecil=$nSmall skorRendah=$nLowScore map=${mw}x$mh"
                    )
                }
                .ifEmpty { fallbackGrid(w, h) }
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

    private fun runRecognizer(crop: Bitmap): Pair<String, Float> {
        val env = env() ?: return "" to 0f
        val rec = recSession ?: return "" to 0f
        // yml RecResizeImg [3,48,320]: tinggi 48, lebar proporsional dibatasi 320 (kelipatan 32).
        val th = 48
        val scale = th.toFloat() / crop.height.toFloat()
        var tw = (crop.width * scale).toInt().coerceAtLeast(32)
        tw = minOf(320, ((tw + 31) / 32) * 32).coerceAtLeast(32)
        val resized = Bitmap.createScaledBitmap(crop, tw, th, true)
        return try {
            // yml rec v5 TANPA NormalizeImage → umpan 0..1 (bukan mean/std).
            val (buf, shape) = preprocessor.toChw01(resized)
            OnnxTensor.createTensor(env, buf, shape).use { input ->
                rec.run(Collections.singletonMap(rec.inputNames.first(), input)).use { res ->
                    ctcDecode(res[0].value)
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("ScreenTranslator", "ocr: recognizer gagal pada crop ${crop.width}x${crop.height}", e)
            "" to 0f
        } finally {
            BitmapUtils.safeRecycle(resized)
        }
    }

    /** Second-pass: upscale 2x + autocontrast untuk crop lemah. Caller recycle hasilnya. */
    private fun retryCrop(crop: Bitmap): Bitmap? {
        return try {
            val big = Bitmap.createScaledBitmap(crop, crop.width * 2, crop.height * 2, true)
            preprocessor.autocontrast(big)
        } catch (_: Exception) { null }
    }

    @Suppress("UNCHECKED_CAST")
    private fun ctcDecode(raw: Any?): Pair<String, Float> {
        return try {
            // Ekspektasi [1,T,C] float logits. Decode via CtcDecode.kt (blank=0, skip repeat).
            val batch = (raw as? Array<*>)?.get(0) as? Array<FloatArray> ?: return "" to 0f
            val keys = recKeys ?: return "" to 0f
            val text = decodeCtcOutput(arrayOf(batch), keys)
            // Confidence = rata-rata probabilitas (softmax kasar) frame non-blank.
            var confSum = 0.0
            var confN = 0
            var prev = -1
            for (logits in batch) {
                var best = 0
                var bestV = Float.NEGATIVE_INFINITY
                var sumExp = 0.0
                for (v in logits) {
                    val e = kotlin.math.exp(v.coerceIn(-30f, 30f).toDouble())
                    sumExp += e
                    if (v > bestV) bestV = v
                }
                for (i in logits.indices) {
                    if (logits[i] == bestV) { best = i; break }
                }
                if (best != 0 && best != prev && best < keys.size + 1) {
                    val maxExp = kotlin.math.exp(bestV.coerceIn(-30f, 30f).toDouble())
                    confSum += maxExp / sumExp.coerceAtLeast(1e-9)
                    confN++
                }
                prev = best
            }
            val conf = if (confN > 0) (confSum / confN).toFloat() else 0f
            if (text.isEmpty()) {
                android.util.Log.d(
                    "ScreenTranslator",
                    "ocr: ctc kosong (T=${batch.size}, classes=${batch.firstOrNull()?.size}, keys=${keys.size})"
                )
            }
            text to conf
        } catch (_: Exception) { "" to 0f }
    }

    /** Manga: kolom kanan -> kiri, dalam kolom atas -> bawah. Dokumen biasa: per baris (toleransi) atas -> bawah. */
    fun sortMangaReadingOrder(blocks: List<TextBlock>): List<TextBlock> {
        if (blocks.isEmpty()) return blocks
        val verticalCount = blocks.count { it.box.ratioHW > 1.35f }
        return if (verticalCount * 2 >= blocks.size) {
            blocks.sortedWith(compareByDescending<TextBlock> { it.box.x }.thenBy { it.box.y })
        } else {
            // Toleransi baris: box sebaris (selisih y < setengah median tinggi) diurut kiri -> kanan.
            val hs = blocks.map { it.box.height }.sorted()
            val tol = maxOf(8, hs[hs.size / 2] / 2)
            blocks.sortedWith(
                compareBy<TextBlock> { it.box.y / tol }.thenBy { it.box.x }
            )
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
