package com.example.bismillah.feature.ocrphoto

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.bismillah.engine.document.PageRotator
import com.example.bismillah.engine.ocr.PaddleOcrEngine
import com.example.bismillah.util.BitmapUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class PhotoOcrUiState(
    val imageUri: Uri? = null,
    val preview: Bitmap? = null,
    val running: Boolean = false,
    val text: String = "",
    val blockCount: Int = 0,
    val message: String = ""
)

class PhotoOcrViewModel(app: Application) : AndroidViewModel(app) {
    private val ocr = PaddleOcrEngine(app.applicationContext)
    private val rotator = PageRotator(app.applicationContext)

    private val _ui = MutableStateFlow(PhotoOcrUiState())
    val ui: StateFlow<PhotoOcrUiState> = _ui.asStateFlow()

    /** URI file temp untuk TakePicture (FileProvider). */
    fun newCameraUri(): Uri {
        val ctx = getApplication<Application>()
        val dir = File(ctx.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "ocr_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
    }

    fun recognize(uri: Uri) {
        if (_ui.value.running) return
        _ui.update { it.copy(running = true, message = "Membaca teks...", text = "", blockCount = 0) }
        viewModelScope.launch {
            try {
                val bmp = withContext(Dispatchers.IO) { loadDownscaled(uri, 2048) }
                if (bmp == null) {
                    _ui.update { it.copy(running = false, message = "Gagal membuka gambar.") }
                    return@launch
                }
                val old = _ui.value.preview
                _ui.update { it.copy(imageUri = uri, preview = bmp) }
                BitmapUtils.safeRecycle(old)
                // V5: auto-rotate halaman (doc_ori) → OCR Paddle v5.
                val upright = withContext(Dispatchers.Default) { rotator.upright(bmp) }
                val blocks = withContext(Dispatchers.Default) { ocr.recognize(upright) }
                val ordered = ocr.sortMangaReadingOrder(blocks)
                // Mode debug: gambar box deteksi (merah) di pratinjau.
                val annotated = withContext(Dispatchers.Default) {
                    annotateBoxes(upright, ordered.map { it.box })
                }
                _ui.update { it.copy(preview = annotated) }
                if (annotated !== upright) BitmapUtils.safeRecycle(upright)
                if (bmp !== annotated && bmp !== upright) BitmapUtils.safeRecycle(bmp)
                // Kosong = model belum terpasang atau memang tidak ada teks.
                if (ordered.isEmpty()) {
                    _ui.update {
                        it.copy(
                            running = false,
                            message = "Tidak ada teks terbaca. Model OCR belum lengkap? (Kelola Model OCR)"
                        )
                    }
                } else {
                    val text = ordered.joinToString("\n") { it.text }
                    _ui.update {
                        it.copy(running = false, text = text, blockCount = ordered.size, message = "")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ScreenTranslator", "photo ocr failed", e)
                _ui.update { it.copy(running = false, message = "Gagal: ${e.message}") }
            }
        }
    }

    fun updateText(v: String) {
        _ui.update { it.copy(text = v) }
    }

    /** Salin bitmap + gambar kotak deteksi merah untuk debug visual. */
    private fun annotateBoxes(
        src: Bitmap,
        boxes: List<com.example.bismillah.data.model.BoundingBox>
    ): Bitmap {
        return try {
            val out = src.copy(Bitmap.Config.ARGB_8888, true) ?: return src
            val c = android.graphics.Canvas(out)
            val p = android.graphics.Paint().apply {
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = (src.width / 400f).coerceAtLeast(2f)
                color = android.graphics.Color.RED
            }
            for (b in boxes) {
                c.drawRect(
                    b.x.toFloat(), b.y.toFloat(),
                    (b.x + b.width).toFloat(), (b.y + b.height).toFloat(), p
                )
            }
            out
        } catch (_: Exception) {
            src
        }
    }

    private fun loadDownscaled(uri: Uri, maxSide: Int): Bitmap? {
        val ctx = getApplication<Application>()
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            var sample = 1
            val w = bounds.outWidth
            val h = bounds.outHeight
            if (w <= 0 || h <= 0) return null
            while (maxOf(w / sample, h / sample) > maxSide) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            ctx.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun shareIntent(text: String): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }

    override fun onCleared() {
        try { ocr.close() } catch (_: Exception) {}
        try { rotator.close() } catch (_: Exception) {}
        BitmapUtils.safeRecycle(_ui.value.preview)
        super.onCleared()
    }

    @Suppress("UNCHECKED_CAST")
    class Factory(private val app: Application) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PhotoOcrViewModel(app) as T
    }
}
