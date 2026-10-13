package com.example.bismillah.engine.onnx

import android.content.Context
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.example.bismillah.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Loader .onnx terpusat: salin sekali dari assets/ ke filesDir, lalu buat
 * [OrtSession] di background thread dengan error handling eksplisit.
 *
 * Default mengikuti pack proyek: model di assets/paddle/, kamus v5
 * (ppocrv5_keys.txt). Engine lama yang masih di assets/models/ tinggal
 * oper assetDir="models".
 * - [OrtEnvironment] dipakai bersama (milik [App] bila ada) agar hemat memori.
 */
class OnnxModelSession(private val appContext: Context) {

    sealed interface LoadResult {
        data class Ready(val session: OrtSession) : LoadResult
        data class Failed(val reason: Reason, val detail: String) : LoadResult
    }

    enum class Reason { ENV_MISSING, ASSET_MISSING, COPY_FAILED, SESSION_FAILED }

    companion object {
        const val DICT_V5 = "ppocrv5_keys.txt"
        const val DICT_LEGACY = "ppocr_keys_v1.txt"
    }

    private val mutex = Mutex()
    private var sharedEnv: OrtEnvironment? = null
    private var ownsEnv = false

    private fun env(): OrtEnvironment? {
        sharedEnv?.let { return it }
        // Prioritas: environment milik App (single instance per proses).
        val fromApp = try {
            (appContext.applicationContext as? App)?.ortEnv
        } catch (_: Exception) { null }
        if (fromApp != null) {
            sharedEnv = fromApp
            return fromApp
        }
        return try {
            OrtEnvironment.getEnvironment().also {
                sharedEnv = it
                ownsEnv = true
            }
        } catch (e: Exception) {
            android.util.Log.e("ScreenTranslator", "onnx: OrtEnvironment gagal dibuat", e)
            null
        }
    }

    /**
     * Muat [assetFileName] dari assets/[assetDir]/ ke filesDir/models/[assetDir]/
     * (sekali saja, dilewati bila sudah ada & > [minBytes]) lalu buat session.
     * WAJIB dipanggil dari coroutine (IO).
     */
    suspend fun load(
        assetDir: String = "paddle",
        assetFileName: String,
        minBytes: Long = 100 * 1024,
        intraThreads: Int = 2,
        enableMemPattern: Boolean = false
    ): LoadResult = mutex.withLock {
        withContext(Dispatchers.IO) {
            val env = env()
            if (env == null) {
                return@withContext LoadResult.Failed(
                    Reason.ENV_MISSING, "OrtEnvironment tidak tersedia"
                )
            }
            val outDir = File(appContext.filesDir, "models/$assetDir").apply { mkdirs() }
            val out = File(outDir, assetFileName)
            try {
                if (!(out.exists() && out.length() >= minBytes)) {
                    try {
                        appContext.assets.open("$assetDir/$assetFileName").use { ins ->
                            out.outputStream().use { outs -> ins.copyTo(outs) }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("ScreenTranslator", "onnx: asset $assetDir/$assetFileName hilang", e)
                        return@withContext LoadResult.Failed(
                            Reason.ASSET_MISSING, "assets/$assetDir/$assetFileName tidak ada"
                        )
                    }
                }
                if (!(out.exists() && out.length() >= minBytes)) {
                    return@withContext LoadResult.Failed(
                        Reason.COPY_FAILED, "Salin ${out.name} gagal (size=${out.length()})"
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("ScreenTranslator", "onnx: salin model gagal", e)
                return@withContext LoadResult.Failed(Reason.COPY_FAILED, e.message ?: "copy gagal")
            }
            try {
                val opts = OrtSession.SessionOptions().apply {
                    setIntraOpNumThreads(intraThreads)
                    setInterOpNumThreads(1)
                    setMemoryPatternOptimization(enableMemPattern)
                }
                val session = env.createSession(out.absolutePath, opts)
                android.util.Log.d(
                    "ScreenTranslator",
                    "onnx: loaded ${out.name} (${out.length() / 1e6} MB) inputs=${session.inputNames}"
                )
                LoadResult.Ready(session)
            } catch (e: Exception) {
                android.util.Log.e("ScreenTranslator", "onnx: createSession ${out.name} gagal", e)
                LoadResult.Failed(Reason.SESSION_FAILED, e.message ?: "session gagal")
            }
        }
    }

    /** Tutup environment HANYA bila dibuat sendiri oleh helper ini. */
    fun close() {
        try {
            if (ownsEnv) sharedEnv?.close()
        } catch (_: Exception) {
        } finally {
            sharedEnv = null
            ownsEnv = false
        }
    }

    /**
     * Muat kamus karakter, default v5 (18.383 entri sesuai inference.yml).
     * Urutan: filesDir/models/dict/ppocrv5_keys.txt → assets/paddle/ →
     * legacy ppocr_keys_v1.txt (filesDir lalu assets). Null bila tak ada.
     */
    suspend fun loadDict(assetDir: String = "paddle"): List<String>? =
        withContext(Dispatchers.IO) {
            val v5 = File(appContext.filesDir, "models/dict/$DICT_V5")
            val legacy = File(appContext.filesDir, "models/dict/$DICT_LEGACY")
            val attempts = listOf(
                { v5.takeIf { it.exists() }?.bufferedReader()?.readLines() },
                { appContext.assets.open("$assetDir/$DICT_V5").bufferedReader().readLines() },
                { legacy.takeIf { it.exists() }?.bufferedReader()?.readLines() },
                { appContext.assets.open("$assetDir/$DICT_LEGACY").bufferedReader().readLines() },
                { appContext.assets.open("dict/$DICT_LEGACY").bufferedReader().readLines() }
            )
            for ((i, fn) in attempts.withIndex()) {
                try {
                    val lines = fn()
                    if (!lines.isNullOrEmpty()) {
                        if (i > 0) android.util.Log.d("ScreenTranslator", "onnx: dict fallback #$i (${lines.size} baris)")
                        return@withContext lines
                    }
                } catch (_: Exception) {
                    // lanjut ke kandidat berikut.
                }
            }
            android.util.Log.w("ScreenTranslator", "onnx: kamus karakter tidak ditemukan")
            null
        }
}
