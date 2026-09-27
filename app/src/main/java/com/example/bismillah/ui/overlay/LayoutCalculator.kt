package com.example.bismillah.ui.overlay

import kotlin.math.max
import kotlin.math.min

data class RenderBox(val x: Int, val y: Int, val w: Int, val h: Int)

/**
 * V3 Horizontal-Lock & Center-Anchor geometry (pure math, unit-testable).
 * Measurement (StaticLayout) lives in [TranslationOverlayView]; placement here.
 */
object LayoutCalculator {
    const val MARGIN = 16
    const val VERTICAL_RATIO = 1.35f
    const val MICRO_MAX_W = 140
    const val DIALOGUE_MAX_W = 460
    const val DIALOGUE_W_FRAC = 0.8f
    const val PAD_MICRO = 12
    const val PAD_DIALOGUE = 16

    /** Micro Card: angka bab / kata pendek (<=4 karakter, tanpa spasi). */
    fun isMicroCard(text: String): Boolean {
        val t = text.trim()
        return t.length <= 4 && !t.contains(' ') && !t.contains('\n')
    }

    fun maxWidth(text: String, screenW: Int): Int =
        if (isMicroCard(text)) MICRO_MAX_W
        else min(DIALOGUE_MAX_W, (screenW * DIALOGUE_W_FRAC).toInt())

    fun padding(text: String): Int =
        if (isMicroCard(text)) PAD_MICRO else PAD_DIALOGUE

    /**
     * Posisikan kartu horizontal pada titik tengah box asli, jepit ke batas layar.
     * Rasio vertikal box asli sengaja diabaikan (horizontal-lock).
     */
    fun place(
        centerX: Int,
        centerY: Int,
        cardW: Int,
        cardH: Int,
        screenW: Int,
        screenH: Int
    ): RenderBox {
        val xr = max(MARGIN, min(centerX - cardW / 2, screenW - cardW - MARGIN))
        val yr = max(MARGIN, min(centerY - cardH / 2, screenH - cardH - MARGIN))
        return RenderBox(xr, yr, cardW, cardH)
    }
}
