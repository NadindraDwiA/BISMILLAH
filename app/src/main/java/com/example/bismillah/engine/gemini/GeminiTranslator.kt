package com.example.bismillah.engine.gemini

import com.example.bismillah.data.model.LangCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Cloud translator via Gemini 2.5 Flash (Generative Language API).
 *
 * The API key is supplied per call from [com.example.bismillah.data.preference.ApiKeyStore]
 * (user-entered, encrypted at rest). It is sent ONLY as the x-goog-api-key header —
 * never in URLs, logs, or persisted state here.
 */
class GeminiTranslator {
    companion object {
        const val MODEL = "gemini-2.5-flash"
        /** Fallback bila model utama 404 di API version/key tersebut. */
        private val MODEL_FALLBACKS = listOf("gemini-2.5-flash", "gemini-2.0-flash", "gemini-1.5-flash")
        private const val ENDPOINT_BASE = "https://generativelanguage.googleapis.com/v1beta/models"
    }

    suspend fun translate(
        text: String,
        srcLang: String = LangCode.JAPAN,
        tgtLang: String = LangCode.INDONESIA,
        apiKey: String
    ): String = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext ""
        if (apiKey.isBlank()) return@withContext "[API key Gemini belum diisi — buka Pengaturan]"

        val prompt = buildString {
            append("You are a professional translator. Translate the following ")
            append(langName(srcLang))
            append(" text to ")
            append(langName(tgtLang))
            append(". Output ONLY the translation, no explanations, no quotes:\n\n")
            append(text)
        }

        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
            .put("generationConfig", JSONObject().put("temperature", 0.2).put("maxOutputTokens", 1024))
            .toString()

        var conn: HttpsURLConnection? = null
        var lastError = "[Gemini: model tidak ditemukan]"
        try {
            for (model in MODEL_FALLBACKS) {
                conn?.disconnect()
                conn = null
                try {
                    conn = (URL("$ENDPOINT_BASE/$model:generateContent").openConnection() as HttpsURLConnection).apply {
                        requestMethod = "POST"
                        connectTimeout = 15_000
                        readTimeout = 30_000
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json")
                        // Header, bukan query param — key tidak bocor ke log URL.
                        setRequestProperty("x-goog-api-key", apiKey)
                    }
                    conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

                    val code = conn.responseCode
                    val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                    val raw = stream?.bufferedReader()?.readText() ?: ""
                    if (code in 200..299) {
                        return@withContext parseText(raw) ?: "[Gemini: respons kosong]"
                    }
                    // 404 pada model utama → coba fallback; error lain langsung kembali.
                    if (code == 404 && model != MODEL_FALLBACKS.last()) {
                        android.util.Log.w("ScreenTranslator", "Gemini $model 404, fallback berikutnya")
                        continue
                    }
                    return@withContext mapError(code, raw)
                } finally {
                    // koneksi ditutup di akhir; loop lanjut bila fallback.
                }
            }
            return@withContext lastError
        } catch (e: java.net.UnknownHostException) {
            "[Gemini: tidak ada internet]"
        } catch (e: java.net.SocketTimeoutException) {
            "[Gemini: timeout, coba lagi]"
        } catch (e: Exception) {
            "[Gemini gagal: ${e.message}]"
        } finally {
            try { conn?.disconnect() } catch (_: Exception) {}
        }
    }

    private fun parseText(raw: String): String? {
        return try {
            val parts = JSONObject(raw)
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
            val sb = StringBuilder()
            for (i in 0 until parts.length()) {
                sb.append(parts.getJSONObject(i).optString("text", ""))
            }
            sb.toString().trim().ifBlank { null }
        } catch (_: Exception) {
            null
        }
    }

    private fun mapError(code: Int, raw: String): String {
        val detail = try {
            JSONObject(raw).optJSONObject("error")?.optString("message", "")?.take(160)
        } catch (_: Exception) { "" }
        val suffix = if (detail.isNullOrBlank()) "" else " — $detail"
        return when (code) {
            400 -> "[Gemini: request ditolak ($code)$suffix]"
            401, 403 -> "[Gemini: API key salah/kedaluwarsa — periksa di Pengaturan$suffix]"
            404 -> "[Gemini: model tidak ditemukan$suffix]"
            429 -> "[Gemini: kuota habis — coba lagi nanti$suffix]"
            in 500..599 -> "[Gemini: server sibuk ($code)$suffix]"
            else -> "[Gemini: HTTP $code$suffix]"
        }
    }

    private fun langName(code: String): String = when (code) {
        LangCode.JAPAN -> "Japanese"
        LangCode.ZH_HANS -> "Simplified Chinese"
        LangCode.ZH_HANT -> "Traditional Chinese"
        LangCode.KOREAN -> "Korean"
        LangCode.INDONESIA -> "Indonesian"
        "eng_Latn" -> "English"
        else -> "Japanese"
    }
}
