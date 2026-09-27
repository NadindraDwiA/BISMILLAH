package com.example.bismillah.feature.screentranslator

import android.content.Context
import com.example.bismillah.data.model.LangCode
import com.example.bismillah.data.preference.ApiKeyStore
import com.example.bismillah.data.preference.AppPreferences
import com.example.bismillah.engine.classifier.LanguageIdentifier
import com.example.bismillah.engine.classifier.OrientationClassifier
import com.example.bismillah.engine.gemini.GeminiTranslator
import com.example.bismillah.engine.ocr.PaddleOcrEngine
import com.example.bismillah.service.ScreenCaptureManager
import com.example.bismillah.ui.bubble.BubbleGestures
import com.example.bismillah.ui.bubble.BubbleSession
import com.example.bismillah.ui.menu.RadialMenuManager
import com.example.bismillah.ui.overlay.TranslationOverlayManager
import com.example.bismillah.util.BitmapUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TranslatorController(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val capture = ScreenCaptureManager(context)
    private val ocr = PaddleOcrEngine(context)
    private val orientation = OrientationClassifier()
    private val langId = LanguageIdentifier()
    private val translator = GeminiTranslator()
    private val overlay = TranslationOverlayManager(context)
    private val radial = RadialMenuManager(context)

    var onState: ((String) -> Unit)? = null
    private var running = false
    private var autoJob: Job? = null

    fun isAuto(): Boolean = autoJob?.isActive == true

    fun start() {
        val ok = BubbleSession.start(
            context,
            BubbleGestures(
                onSingleTap = { runOnce() },
                onDoubleTap = { toggleAuto() },
                onLongPress = { showRadial() }
            )
        )
        if (ok) onState?.invoke("Bubble aktif")
        else onState?.invoke("Bubble gagal tampil — izinkan overlay dulu")
    }

    fun stop() {
        autoJob?.cancel()
        autoJob = null
        radial.dismiss()
        BubbleSession.stop()
        overlay.dismiss()
    }

    /** V4 double-tap: automatic translation loop per interval. */
    fun toggleAuto() {
        if (isAuto()) {
            autoJob?.cancel()
            autoJob = null
            onState?.invoke("Auto-translate mati")
            return
        }
        autoJob = scope.launch {
            onState?.invoke("Auto-translate aktif (tiap 10 dtk)")
            while (isActive) {
                runOnce()
                delay(10_000)
            }
        }
    }

    /** V4 long-press: radial quick menu. */
    fun showRadial() {
        BubbleSession.setLoading(false)
        radial.show(
            onTranslate = { runOnce() },
            onMessage = { onState?.invoke(it) }
        )
    }

    fun runOnce() {
        if (running) {
            android.util.Log.d("ScreenTranslator", "runOnce ignored: already running")
            return
        }
        running = true
        BubbleSession.setLoading(true)
        android.util.Log.d("ScreenTranslator", "runOnce started")
        scope.launch {
            onState?.invoke("Menangkap layar…")
            BubbleSession.setVisible(false)
            try {
                val bmp = withContext(Dispatchers.IO) { capture.captureOnce() }
                BubbleSession.setVisible(true)
                if (bmp == null) {
                    android.util.Log.w("ScreenTranslator", "capture returned null (MediaProjection missing?)")
                    onState?.invoke("Gagal capture — izinkan MediaProjection dulu")
                    // Tampilkan kartu error di overlay agar user di app lain tahu sebabnya.
                    val metrics0 = context.resources.displayMetrics
                    val errBlock = ocr.stubDemoBlock(metrics0.widthPixels, metrics0.heightPixels)
                    withContext(Dispatchers.Main) {
                        try {
                            overlay.show(
                                listOf(errBlock to "Izin tangkap layar belum aktif. Buka BISMILLAH → Translator → Aktifkan."),
                                null
                            )
                        } catch (e: Exception) {
                            android.util.Log.e("ScreenTranslator", "error overlay failed", e)
                        }
                    }
                    return@launch
                }
                val prefs = try {
                    AppPreferences.observe(context).first()
                } catch (_: Exception) {
                    AppPreferences.Settings()
                }
                onState?.invoke("OCR…")
                val blocks = withContext(Dispatchers.Default) { ocr.recognize(bmp) }
                android.util.Log.d("ScreenTranslator", "ocr blocks=${blocks.size}")
                val metrics = context.resources.displayMetrics
                val effective = if (blocks.isEmpty()) {
                    listOf(ocr.stubDemoBlock(metrics.widthPixels, metrics.heightPixels))
                } else blocks
                onState?.invoke("Menerjemahkan ${effective.size} blok…")
                val out = effective.map { b ->
                    val vertical = orientation.isVertical(b.box)
                    val src = when (prefs.recognitionLang) {
                        AppPreferences.REC_AUTO -> try {
                            langId.identify(b.text.ifBlank { "これは漫画です" })
                        } catch (_: Exception) { LangCode.JAPAN }
                        AppPreferences.REC_JA -> LangCode.JAPAN
                        AppPreferences.REC_ZH_HANS -> LangCode.ZH_HANS
                        AppPreferences.REC_ZH_HANT -> LangCode.ZH_HANT
                        AppPreferences.REC_KO -> LangCode.KOREAN
                        else -> LangCode.JAPAN
                    }
                    val key = ApiKeyStore.effectiveKey(context)
                    val tr = if (key.isBlank()) {
                        "[API key Gemini belum diisi — buka Pengaturan]"
                    } else try {
                        translator.translate(b.text.ifBlank { b.text }, src, prefs.targetLang, key)
                    } catch (e: Exception) {
                        android.util.Log.e("ScreenTranslator", "translate failed", e)
                        b.text
                    }
                    b.copy(isVertical = vertical) to tr
                }
                val shown = withContext(Dispatchers.Main) {
                    try {
                        // V4 SUCCESS: ripple centang singkat sebelum kartu muncul.
                        BubbleSession.showSuccess()
                        delay(150)
                        overlay.show(out, prefs)
                    } catch (e: Exception) {
                        android.util.Log.e("ScreenTranslator", "overlay show failed", e)
                        false
                    }
                }
                if (shown) onState?.invoke("Selesai — ketuk overlay untuk menutup")
                else onState?.invoke("Gagal tampil overlay — izinkan overlay dulu")
                BitmapUtils.safeRecycle(bmp)
            } catch (e: Exception) {
                android.util.Log.e("ScreenTranslator", "runOnce failed", e)
                onState?.invoke("Gagal: ${e.message}")
            } finally {
                BubbleSession.setVisible(true)
                BubbleSession.setLoading(false)
                running = false
            }
        }
    }

    fun dismissOverlay() = overlay.dismiss()

    /**
     * Release pipeline resources when the UI goes away.
     * The bubble is intentionally left running (owned by [BubbleSession])
     * so translating keeps working after leaving this screen.
     * Call [stop] explicitly to kill the bubble.
     */
    fun close() {
        try { autoJob?.cancel() } catch (_: Exception) {}
        autoJob = null
        try { scope.cancel() } catch (_: Exception) {}
        try { ocr.close() } catch (_: Exception) {}
    }
}
