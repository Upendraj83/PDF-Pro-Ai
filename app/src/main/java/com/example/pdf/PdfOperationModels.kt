package com.example.pdf

import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import java.io.File
import java.util.UUID

/**
 * Representation of a page within the Page Organizer.
 */
data class OrganizedPage(
    val id: String = UUID.randomUUID().toString(),
    val sourceUri: Uri? = null,
    val sourceFile: File? = null,
    val sourcePageIndex: Int = 0,
    val rotation: Int = 0, // 0, 90, 180, 270
    val isBlank: Boolean = false,
    val widthPt: Float = PDRectangle.A4.width,
    val heightPt: Float = PDRectangle.A4.height
)

/**
 * An item to be merged (either a PDF with optional page range or an Image).
 */
data class MergeItem(
    val id: String = UUID.randomUUID().toString(),
    val uri: Uri,
    val fileName: String,
    val isImage: Boolean,
    val pageRangeText: String = "", // e.g., "1-3, 5" or blank for all
    val fileSize: Long = 0L
)

/**
 * Split operation options.
 */
sealed interface SplitMode {
    data class ByRanges(val rangeExpression: String) : SplitMode // e.g. "1-3, 5, 7-9"
    data class EveryNPages(val n: Int) : SplitMode // e.g. every 1 page, every 2 pages
    data object ByBookmarks : SplitMode // Split on top-level bookmark destinations
}

enum class SplitDestination {
    ZIP_ARCHIVE,
    FOLDER_TREE
}

/**
 * Image to PDF options.
 */
enum class PageSizePreset(val displayName: String, val rectangle: PDRectangle?) {
    A4("A4 (210 × 297 mm)", PDRectangle.A4),
    LETTER("US Letter (8.5 × 11 in)", PDRectangle.LETTER),
    LEGAL("US Legal", PDRectangle.LEGAL),
    FIT_IMAGE("Fit to Image Size", null)
}

enum class PageMarginPreset(val displayName: String, val marginPoints: Float) {
    NONE("No Margin (0 pt)", 0f),
    SMALL("Small Margin (18 pt / 0.25 in)", 18f),
    MEDIUM("Medium Margin (36 pt / 0.5 in)", 36f),
    LARGE("Large Margin (54 pt / 0.75 in)", 54f)
}

enum class PageOrientationPreset(val displayName: String) {
    AUTO("Auto-detect"),
    PORTRAIT("Portrait"),
    LANDSCAPE("Landscape")
}

enum class ImageQualityPreset(val displayName: String, val compressionQuality: Int, val maxDimension: Int) {
    HIGH("High Quality (95%)", 95, 2400),
    MEDIUM("Medium (80%)", 80, 1800),
    LOW("Small File Size (60%)", 60, 1200)
}

/**
 * PDF to Image export options.
 */
enum class ExportImageFormat(val extension: String, val mimeType: String) {
    JPG("jpg", "image/jpeg"),
    PNG("png", "image/png")
}

enum class ExportDpi(val dpi: Int, val label: String) {
    DPI_72(72, "72 DPI (Fast / Screen)"),
    DPI_150(150, "150 DPI (Balanced)"),
    DPI_300(300, "300 DPI (High Resolution / Print)")
}

/**
 * 90-degree increment page rotation options.
 */
enum class RotationIncrement(val degreesDelta: Int, val displayName: String, val shortBadge: String) {
    CLOCKWISE_90(90, "90° Clockwise (Right)", "+90°"),
    COUNTER_CLOCKWISE_90(-90, "90° Counter-Clockwise (Left)", "-90°"),
    FLIP_180(180, "180° Upside-Down Flip", "180°");

    companion object {
        /**
         * Snaps any rotation angle to a valid 90-degree PDF rotation step (0, 90, 180, 270).
         */
        fun normalizeTo90Step(degrees: Int): Int {
            val mod = ((degrees % 360) + 360) % 360
            return ((Math.round(mod / 90.0).toInt() * 90) % 360 + 360) % 360
        }
    }
}

enum class RotatePagesScope(val displayName: String) {
    SELECTED_PAGES("Selected Pages"),
    ALL_PAGES("All Pages"),
    CUSTOM_RANGE("Custom Page Range"),
    ODD_PAGES("Odd Pages (1, 3, 5...)"),
    EVEN_PAGES("Even Pages (2, 4, 6...)")
}

enum class RotatePagesSaveMode(val displayName: String, val subtitle: String) {
    SAVE_TO_DOCUMENT_FILE(
        "Save to Document File (Overwrite)",
        "Applies 90° page rotations directly to the document file"
    ),
    SAVE_AS_NEW_COPY(
        "Save as New PDF File",
        "Creates a new rotated PDF document while keeping the original unchanged"
    )
}

/**
 * Reactive state of any running background operation.
 */
sealed interface OperationProgress {
    data object Idle : OperationProgress
    data class Running(
        val title: String,
        val stepDescription: String,
        val progressFraction: Float, // 0.0 to 1.0, or negative for indeterminate
        val canCancel: Boolean = true
    ) : OperationProgress
    data class Success(
        val title: String,
        val message: String,
        val outputFiles: List<File>,
        val primaryFile: File?,
        val isZip: Boolean = false
    ) : OperationProgress
    data class Failure(
        val title: String,
        val error: String
    ) : OperationProgress
}
