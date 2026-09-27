@file:Suppress("DEPRECATION") // security-crypto 1.1.0 mendepresiasi API ini; pengganti belum stabil.

package com.example.bismillah.data.preference

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * API key storage (Jetpack Security, AES256-GCM).
 *
 * Rules: key is entered by the user at runtime, kept encrypted at rest,
 * never logged, never hardcoded, never committed. Memory-only while in use.
 */
@Suppress("DEPRECATION") // EncryptedSharedPreferences deprecated di security-crypto 1.1.0, penggantinya belum stabil.
object ApiKeyStore {
    private const val PREFS = "secret_prefs"
    private const val KEY_GEMINI = "gemini_api_key"

    private fun prefs(ctx: Context): EncryptedSharedPreferences {
        val appCtx = ctx.applicationContext
        val masterKey = MasterKey.Builder(appCtx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            appCtx,
            PREFS,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        ) as EncryptedSharedPreferences
    }

    fun has(ctx: Context): Boolean = effectiveKey(ctx).isNotBlank()

    /**
     * Prioritas: key runtime pengguna (terenkripsi) > key bawaan build
     * (local.properties, gitignored). Tidak pernah di-log.
     */
    fun effectiveKey(ctx: Context): String {
        val runtime = get(ctx)
        if (runtime.isNotBlank()) return runtime
        return try {
            com.example.bismillah.BuildConfig.GEMINI_API_KEY.trim()
        } catch (_: Exception) {
            ""
        }
    }

    fun get(ctx: Context): String {
        return try {
            prefs(ctx).getString(KEY_GEMINI, "")?.trim() ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    fun set(ctx: Context, value: String) {
        try {
            prefs(ctx).edit().putString(KEY_GEMINI, value.trim()).apply()
        } catch (_: Exception) {
            // Storage unavailable — caller surfaces the failure.
            throw IllegalStateException("Gagal menyimpan API key")
        }
    }

    fun clear(ctx: Context) {
        try {
            prefs(ctx).edit().remove(KEY_GEMINI).apply()
        } catch (_: Exception) {}
    }
}
