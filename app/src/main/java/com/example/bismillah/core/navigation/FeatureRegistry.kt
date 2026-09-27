package com.example.bismillah.core.navigation

enum class FeatureStatus { READY, NEED_PERMISSION, NEED_MODEL, SOON }

data class FeatureItem(
    val id: String,
    val title: String,
    val desc: String,
    val route: String,
    val status: FeatureStatus
)

object FeatureRegistry {
    fun items(
        hasOverlayPermission: Boolean,
        translatorReady: Boolean
    ): List<FeatureItem> {
        val translatorStatus = when {
            !hasOverlayPermission -> FeatureStatus.NEED_PERMISSION
            !translatorReady -> FeatureStatus.NEED_MODEL
            else -> FeatureStatus.READY
        }
        // Beranda hanya menampilkan Screen Translator.
        // Pengaturan model OCR tetap ada sebagai layar internal
        // (via Setup gate / layar Translator).
        return listOf(
            FeatureItem(
                id = "screen_translator",
                title = "Screen Translator",
                desc = "Bubble melayang, capture layar, OCR CJK + Gemini ke Indonesia",
                route = Routes.TRANSLATOR_SETUP,
                status = translatorStatus
            )
        )
    }
}
