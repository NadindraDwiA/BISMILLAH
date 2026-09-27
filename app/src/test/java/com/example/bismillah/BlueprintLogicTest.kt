package com.example.bismillah

import com.example.bismillah.data.model.BoundingBox
import com.example.bismillah.data.model.LangCode
import com.example.bismillah.engine.classifier.OrientationClassifier
import com.example.bismillah.ui.overlay.LayoutCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlueprintLogicTest {
    @Test
    fun verticalDetectedByRatio() {
        val clf = OrientationClassifier()
        assertTrue(clf.isVertical(BoundingBox(0, 0, 100, 200)))
        assertFalse(clf.isVertical(BoundingBox(0, 0, 300, 100)))
    }

    @Test
    fun langMapping() {
        assertEquals(LangCode.JAPAN, LangCode.fromMlKit("ja"))
        assertEquals(LangCode.ZH_HANS, LangCode.fromMlKit("zh"))
        assertEquals(LangCode.KOREAN, LangCode.fromMlKit("ko"))
    }

    @Test
    fun layoutClampedInsideScreen() {
        val box = BoundingBox(1000, 1800, 120, 400)
        val rb = LayoutCalculator.place(box.centerX, box.centerY, 300, 332, 1080, 1920)
        assertTrue(rb.x >= 16 && rb.x + rb.w <= 1080 - 16)
        assertTrue(rb.y >= 16 && rb.y + rb.h <= 1920 - 16)
    }

    @Test
    fun microVsDialogueTypology() {
        assertTrue(LayoutCalculator.isMicroCard("8"))
        assertTrue(LayoutCalculator.isMicroCard("ドン"))
        assertFalse(LayoutCalculator.isMicroCard("おはよう世界"))
        assertEquals(140, LayoutCalculator.maxWidth("12", 1080))
        assertEquals(460, LayoutCalculator.maxWidth("kalimat panjang", 1080))
        assertEquals((500 * 0.8f).toInt(), LayoutCalculator.maxWidth("kalimat panjang", 500))
    }
}
