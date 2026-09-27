package com.example.bismillah.data.model

data class BoundingBox(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
) {
    val centerX: Int get() = x + width / 2
    val centerY: Int get() = y + height / 2
    val ratioHW: Float get() = if (width == 0) 0f else height.toFloat() / width.toFloat()
}

data class TextBlock(
    val box: BoundingBox,
    val text: String,
    val confidence: Float = 0f,
    val isVertical: Boolean = false
)
