package com.example.pdf

import android.graphics.Bitmap

/**
 * Supported AcroForm field types detected from PDF AcroForm structures.
 */
enum class FormFieldType {
    TEXT,
    CHECKBOX,
    RADIO,
    DROPDOWN,
    LIST,
    DATE
}

/**
 * Represents a detected AcroForm field in the document.
 */
data class FormFieldItem(
    val id: String,
    val name: String,
    val fullyQualifiedName: String,
    val pageIndex: Int, // 0-indexed
    val bounds: NormalizedRect,
    val type: FormFieldType,
    val currentValue: String = "",
    val options: List<String> = emptyList(),
    val isRequired: Boolean = false,
    val isReadOnly: Boolean = false
)

/**
 * Supported types of user-placed stamps and quick-fill elements.
 */
enum class PlacedStampType {
    SIGNATURE,
    INITIALS,
    TEXT,
    CHECKMARK,
    CROSS,
    DATE,
    SNIPPET
}

/**
 * Represents a user-placed signature, initials, quick checkmark, cross, date, or text stamp.
 */
data class PlacedStampItem(
    val id: String,
    val type: PlacedStampType,
    val pageIndex: Int, // 0-indexed
    val normX: Float, // 0.0f .. 1.0f (left)
    val normY: Float, // 0.0f .. 1.0f (top)
    val normWidth: Float, // relative width
    val normHeight: Float, // relative height
    val textValue: String? = null,
    val imageBitmap: Bitmap? = null,
    val colorArgb: Int = 0xFF1976D2.toInt(),
    val fontSizeSp: Float = 16f
)

/**
 * Container for saved signatures and initials.
 */
data class SavedSignature(
    val id: String,
    val slotIndex: Int, // 0..2
    val isInitials: Boolean,
    val bitmap: Bitmap,
    val createdAt: Long = System.currentTimeMillis()
)
