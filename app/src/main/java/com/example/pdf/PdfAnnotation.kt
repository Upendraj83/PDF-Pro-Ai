package com.example.pdf

import android.graphics.PointF
import java.util.UUID

enum class AnnotationType {
    HIGHLIGHT,
    UNDERLINE,
    STRIKEOUT,
    PEN,
    HIGHLIGHTER_PEN,
    STICKY_NOTE,
    TEXT_BOX,
    LINE,
    ARROW,
    RECTANGLE,
    ELLIPSE
}

data class NormalizedPoint(val x: Float, val y: Float)

data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)

    fun contains(px: Float, py: Float): Boolean {
        return px in left..right && py in top..bottom
    }
}

sealed class PdfAnnotation {
    abstract val id: String
    abstract val pageIndex: Int
    abstract val type: AnnotationType
    abstract val colorArgb: Int
    abstract val strokeWidth: Float
    abstract val isExistingFromPdf: Boolean
    abstract val createdAt: Long

    data class TextMarkup(
        override val id: String = UUID.randomUUID().toString(),
        override val pageIndex: Int,
        override val type: AnnotationType, // HIGHLIGHT, UNDERLINE, STRIKEOUT
        override val colorArgb: Int,
        override val strokeWidth: Float = 2f,
        override val isExistingFromPdf: Boolean = false,
        override val createdAt: Long = System.currentTimeMillis(),
        val rects: List<NormalizedRect>,
        val selectedText: String = ""
    ) : PdfAnnotation()

    data class InkStroke(
        override val id: String = UUID.randomUUID().toString(),
        override val pageIndex: Int,
        override val type: AnnotationType, // PEN or HIGHLIGHTER_PEN
        override val colorArgb: Int,
        override val strokeWidth: Float,
        override val isExistingFromPdf: Boolean = false,
        override val createdAt: Long = System.currentTimeMillis(),
        val points: List<NormalizedPoint>,
        val alpha: Float = if (type == AnnotationType.HIGHLIGHTER_PEN) 0.45f else 1.0f
    ) : PdfAnnotation()

    data class StickyNote(
        override val id: String = UUID.randomUUID().toString(),
        override val pageIndex: Int,
        override val type: AnnotationType = AnnotationType.STICKY_NOTE,
        override val colorArgb: Int = 0xFFFFD54F.toInt(), // Warm yellow default
        override val strokeWidth: Float = 1f,
        override val isExistingFromPdf: Boolean = false,
        override val createdAt: Long = System.currentTimeMillis(),
        val position: NormalizedPoint,
        val noteText: String = "",
        val author: String = "User"
    ) : PdfAnnotation()

    data class FreeTextBox(
        override val id: String = UUID.randomUUID().toString(),
        override val pageIndex: Int,
        override val type: AnnotationType = AnnotationType.TEXT_BOX,
        override val colorArgb: Int = 0xFF000000.toInt(),
        override val strokeWidth: Float = 1f,
        override val isExistingFromPdf: Boolean = false,
        override val createdAt: Long = System.currentTimeMillis(),
        val bounds: NormalizedRect,
        val text: String,
        val fontSize: Float = 14f,
        val backgroundColorArgb: Int = 0x33FFFFFF
    ) : PdfAnnotation()

    data class GeometricShape(
        override val id: String = UUID.randomUUID().toString(),
        override val pageIndex: Int,
        override val type: AnnotationType, // LINE, ARROW, RECTANGLE, ELLIPSE
        override val colorArgb: Int,
        override val strokeWidth: Float,
        override val isExistingFromPdf: Boolean = false,
        override val createdAt: Long = System.currentTimeMillis(),
        val startPoint: NormalizedPoint,
        val endPoint: NormalizedPoint,
        val fillArgb: Int? = null
    ) : PdfAnnotation()
}
