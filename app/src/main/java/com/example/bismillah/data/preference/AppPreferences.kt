package com.example.bismillah.data.preference

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsStore by preferencesDataStore(name = "settings")

/** V4 In-App Settings storage. Bilingual-ready keys, survives reinstall config. */
object AppPreferences {
    // Recognition language: auto | ja | zh-Hans | zh-Hant | ko
    private val REC_LANG = stringPreferencesKey("recognition_lang")
    // Target language FLORES code: ind_Latn | eng_Latn
    private val TGT_LANG = stringPreferencesKey("target_lang")
    private val AUTO_FONT = booleanPreferencesKey("auto_font_size")
    private val MIN_TEXT_SP = intPreferencesKey("min_text_sp")
    private val IDLE_ALPHA = floatPreferencesKey("bubble_idle_alpha")

    const val REC_AUTO = "auto"
    const val REC_JA = "ja"
    const val REC_ZH_HANS = "zh-Hans"
    const val REC_ZH_HANT = "zh-Hant"
    const val REC_KO = "ko"

    const val TGT_ID = "ind_Latn"
    const val TGT_EN = "eng_Latn"

    const val DEFAULT_MIN_SP = 17

    data class Settings(
        val recognitionLang: String = REC_AUTO,
        val targetLang: String = TGT_ID,
        val autoFontSize: Boolean = true,
        val minTextSp: Int = DEFAULT_MIN_SP,
        val idleAlpha: Float = 0.55f
    )

    fun observe(ctx: Context): Flow<Settings> =
        ctx.applicationContext.settingsStore.data.map {
            Settings(
                recognitionLang = it[REC_LANG] ?: REC_AUTO,
                targetLang = it[TGT_LANG] ?: TGT_ID,
                autoFontSize = it[AUTO_FONT] ?: true,
                minTextSp = it[MIN_TEXT_SP] ?: DEFAULT_MIN_SP,
                idleAlpha = it[IDLE_ALPHA] ?: 0.55f
            )
        }

    suspend fun setRecognitionLang(ctx: Context, v: String) {
        ctx.applicationContext.settingsStore.edit { it[REC_LANG] = v }
    }

    suspend fun setTargetLang(ctx: Context, v: String) {
        ctx.applicationContext.settingsStore.edit { it[TGT_LANG] = v }
    }

    suspend fun setAutoFont(ctx: Context, v: Boolean) {
        ctx.applicationContext.settingsStore.edit { it[AUTO_FONT] = v }
    }

    suspend fun setMinTextSp(ctx: Context, v: Int) {
        ctx.applicationContext.settingsStore.edit { it[MIN_TEXT_SP] = v.coerceIn(10, 32) }
    }
}
