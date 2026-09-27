package com.example.bismillah.data.model

object LangCode {
    const val JAPAN = "jpn_Jpan"
    const val ZH_HANS = "zho_Hans"
    const val ZH_HANT = "zho_Hant"
    const val KOREAN = "kor_Hang"
    const val INDONESIA = "ind_Latn"

    fun fromMlKit(code: String): String = when (code) {
        "ja" -> JAPAN
        "zh" -> ZH_HANS
        "ko" -> KOREAN
        else -> JAPAN
    }
}
