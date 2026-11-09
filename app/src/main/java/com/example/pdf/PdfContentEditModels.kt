package com.example.pdf

import java.io.File
import java.util.UUID

import androidx.annotation.FontRes
import com.example.R

enum class ContentEditTool(val label: String) {
    SELECT("Select"),
    ADD_TEXT("Add Text"),
    EDIT_TEXT("Edit Text"),
    ADD_IMAGE("Add Image"),
    ADD_LINK("Link"),
    REDACT("Redact")
}

enum class BundledEditFont(
    val displayName: String,
    val isBengaliCapable: Boolean = false,
    val isSerif: Boolean = false,
    val isMono: Boolean = false,
    @FontRes val customFontResId: Int? = null
) {
    NOTO_SANS("Noto Sans", isBengaliCapable = false, customFontResId = R.font.noto_sans),
    NOTO_SANS_BENGALI("Noto Sans Bengali (বাংলা)", isBengaliCapable = true, customFontResId = R.font.noto_sans_bengali),
    KALPURUSH("Kalpurush (কালপুরুষ)", isBengaliCapable = true, customFontResId = R.font.noto_sans_bengali),
    SOLAIMAN_LIPI("SolaimanLipi (সোলাইমানলিপি)", isBengaliCapable = true, isSerif = true, customFontResId = R.font.noto_serif_bengali),
    NIKOSH("Nikosh (নিকোশ)", isBengaliCapable = true, isSerif = true, customFontResId = R.font.noto_serif_bengali),
    SIYAM_RUPALI("Siyam Rupali (সিয়াম রূপালী)", isBengaliCapable = true, customFontResId = R.font.noto_sans_bengali),
    SUTONNY_MJ("SutonnyMJ (সুতন্বী এমজে)", isBengaliCapable = true, isSerif = true, customFontResId = R.font.tiro_bangla),
    NOTO_SERIF_BENGALI("Noto Serif Bengali (নোটো সেরিফ)", isBengaliCapable = true, isSerif = true, customFontResId = R.font.noto_serif_bengali),
    HIND_SILIGURI("Hind Siliguri (হিন্দ শিলিগুড়ি)", isBengaliCapable = true, customFontResId = R.font.hind_siliguri),
    TIRO_BANGLA("Tiro Bangla (তিরো বাংলা)", isBengaliCapable = true, isSerif = true, customFontResId = R.font.tiro_bangla),
    BALOO_DA_2("Baloo Da 2 (বালু দা ২)", isBengaliCapable = true, customFontResId = R.font.baloo_da_2),
    ANEK_BANGLA("Anek Bangla (অনেক বাংলা)", isBengaliCapable = true, customFontResId = R.font.anek_bangla),
    MINA("Mina (মিনা)", isBengaliCapable = true, customFontResId = R.font.mina),
    GALADA("Galada (গলাদা)", isBengaliCapable = true, customFontResId = R.font.galada),
    ATMA("Atma (আত্মা)", isBengaliCapable = true, customFontResId = R.font.atma),
    VRINDA("Vrinda (বৃন্দা)", isBengaliCapable = true, customFontResId = R.font.noto_sans_bengali),
    NIRMALA_UI("Nirmala UI (নির্মলা ইউআই)", isBengaliCapable = true, customFontResId = R.font.noto_sans_bengali),
    ADARSHA_LIPI("AdorshoLipi (আদর্শলিপি)", isBengaliCapable = true, isSerif = true, customFontResId = R.font.tiro_bangla),
    APONA_LOHIT("AponaLohit (আপনা লোহিত)", isBengaliCapable = true, customFontResId = R.font.noto_sans_bengali),
    MUKTI("Mukti (মুক্তি)", isBengaliCapable = true, customFontResId = R.font.noto_sans_bengali),
    AKAASH("Akaash (আকাশ)", isBengaliCapable = true, customFontResId = R.font.noto_sans_bengali),
    SAGAR("Sagar (সাগর)", isBengaliCapable = true, isSerif = true, customFontResId = R.font.noto_serif_bengali),
    LIKHAN("Likhan (লিখন)", isBengaliCapable = true, isSerif = true, customFontResId = R.font.tiro_bangla),
    EKUSHEY_LOHIT("Ekushey Lohit (একুশে লোহিত)", isBengaliCapable = true, customFontResId = R.font.noto_sans_bengali),
    HELVETICA("Helvetica (Sans)", isBengaliCapable = false),
    TIMES_ROMAN("Times Roman (Serif)", isSerif = true),
    COURIER("Courier (Mono)", isMono = true);

    companion object {
        fun matchClosestFont(pdfFontName: String, sampleText: String = ""): BundledEditFont {
            val lower = pdfFontName.lowercase()
            return when {
                lower.contains("kalpurush") -> KALPURUSH
                lower.contains("solaiman") -> SOLAIMAN_LIPI
                lower.contains("nikosh") -> NIKOSH
                lower.contains("siyam") || lower.contains("rupali") -> SIYAM_RUPALI
                lower.contains("sutonny") || lower.contains("sushree") -> SUTONNY_MJ
                lower.contains("notoserifbengali") -> NOTO_SERIF_BENGALI
                lower.contains("siliguri") -> HIND_SILIGURI
                lower.contains("tirobangla") -> TIRO_BANGLA
                lower.contains("balooda") -> BALOO_DA_2
                lower.contains("anekbangla") -> ANEK_BANGLA
                lower.contains("mina") -> MINA
                lower.contains("galada") -> GALADA
                lower.contains("atma") -> ATMA
                lower.contains("vrinda") -> VRINDA
                lower.contains("nirmala") -> NIRMALA_UI
                lower.contains("adorsho") || lower.contains("adarsha") -> ADARSHA_LIPI
                lower.contains("aponalohit") -> APONA_LOHIT
                lower.contains("mukti") -> MUKTI
                lower.contains("akaash") || lower.contains("akash") -> AKAASH
                lower.contains("sagar") -> SAGAR
                lower.contains("likhan") -> LIKHAN
                lower.contains("ekushey") -> EKUSHEY_LOHIT
                lower.contains("bengali") || containsBengaliScript(sampleText) -> NOTO_SANS_BENGALI
                lower.contains("times") || lower.contains("serif") || lower.contains("georgia") -> TIMES_ROMAN
                lower.contains("courier") || lower.contains("mono") || lower.contains("consolas") -> COURIER
                lower.contains("noto") || lower.contains("roboto") -> NOTO_SANS
                else -> HELVETICA
            }
        }

        fun containsBengaliScript(text: String): Boolean {
            return text.any { ch -> ch.code in 0x0980..0x09FF }
        }
    }
}

enum class TextAlignmentMode(val label: String) {
    LEFT("Left"),
    CENTER("Center"),
    RIGHT("Right")
}

enum class PdfLinkType(val label: String) {
    WEB_URL("Web URL"),
    EMAIL("Email"),
    PAGE_JUMP("Jump to Page")
}

/**
 * Represents an existing text line extracted from a PDF page via PdfBox-Android.
 */
data class ExistingTextLine(
    val id: String = UUID.randomUUID().toString(),
    val pageIndex: Int,
    val bounds: NormalizedRect,
    val text: String,
    val fontName: String,
    val fontSizePt: Float,
    val isBold: Boolean = false,
    val isItalic: Boolean = false
)

/**
 * Result of inspecting a page's existing text lines and checking for missing/unembedded fonts.
 */
data class PageTextInspectionResult(
    val pageIndex: Int,
    val lines: List<ExistingTextLine>,
    val missingFontNames: List<String>
)

/**
 * Sealed hierarchy of interactive content-editing elements placed or modified on a PDF page.
 * Supports zIndex for layer ordering (Bring Forward / Send Backward) and clipboard copy/paste/duplicate.
 */
sealed class ContentEditElement {
    abstract val id: String
    abstract val pageIndex: Int
    abstract val bounds: NormalizedRect
    abstract val zIndex: Int

    abstract fun withUpdatedBounds(newBounds: NormalizedRect): ContentEditElement
    abstract fun withUpdatedZIndex(newZIndex: Int): ContentEditElement
    abstract fun duplicateOnPage(targetPageIndex: Int, newId: String = UUID.randomUUID().toString(), offsetNorm: Float = 0.03f): ContentEditElement

    /**
     * 1. Add Text: A newly placed text box with font, size, color, alignment, bold, and italic.
     */
    data class AddedText(
        override val id: String = UUID.randomUUID().toString(),
        override val pageIndex: Int,
        override val bounds: NormalizedRect,
        override val zIndex: Int = 0,
        val text: String,
        val font: BundledEditFont = BundledEditFont.NOTO_SANS,
        val fontSizeSp: Float = 16f,
        val colorArgb: Int = 0xFF111111.toInt(),
        val alignment: TextAlignmentMode = TextAlignmentMode.LEFT,
        val isBold: Boolean = false,
        val isItalic: Boolean = false
    ) : ContentEditElement() {
        override fun withUpdatedBounds(newBounds: NormalizedRect) = copy(bounds = newBounds)
        override fun withUpdatedZIndex(newZIndex: Int) = copy(zIndex = newZIndex)
        override fun duplicateOnPage(targetPageIndex: Int, newId: String, offsetNorm: Float): ContentEditElement {
            return copy(
                id = newId,
                pageIndex = targetPageIndex,
                bounds = shiftBounds(bounds, offsetNorm)
            )
        }
    }

    /**
     * 2. Edit Existing Text: Covers an existing text line's bounding box with a background-colored
     * rectangle and draws replacement text in the closest matching font.
     */
    data class ReplacedText(
        override val id: String = UUID.randomUUID().toString(),
        override val pageIndex: Int,
        override val bounds: NormalizedRect,
        override val zIndex: Int = 0,
        val originalText: String,
        val replacementText: String,
        val font: BundledEditFont = BundledEditFont.HELVETICA,
        val fontSizeSp: Float = 12f,
        val textColorArgb: Int = 0xFF000000.toInt(),
        val backgroundColorArgb: Int = 0xFFFFFFFF.toInt(),
        val isBold: Boolean = false,
        val isItalic: Boolean = false
    ) : ContentEditElement() {
        override fun withUpdatedBounds(newBounds: NormalizedRect) = copy(bounds = newBounds)
        override fun withUpdatedZIndex(newZIndex: Int) = copy(zIndex = newZIndex)
        override fun duplicateOnPage(targetPageIndex: Int, newId: String, offsetNorm: Float): ContentEditElement {
            return copy(
                id = newId,
                pageIndex = targetPageIndex,
                bounds = shiftBounds(bounds, offsetNorm)
            )
        }
    }

    /**
     * 3. Image Object: Added from gallery or camera; supports move, resize, rotate, crop, replace, delete.
     */
    data class ImageObject(
        override val id: String = UUID.randomUUID().toString(),
        override val pageIndex: Int,
        override val bounds: NormalizedRect,
        override val zIndex: Int = 0,
        val imageFile: File,
        val rotationDegrees: Float = 0f,
        val cropRect: NormalizedRect = NormalizedRect(0f, 0f, 1f, 1f)
    ) : ContentEditElement() {
        override fun withUpdatedBounds(newBounds: NormalizedRect) = copy(bounds = newBounds)
        override fun withUpdatedZIndex(newZIndex: Int) = copy(zIndex = newZIndex)
        override fun duplicateOnPage(targetPageIndex: Int, newId: String, offsetNorm: Float): ContentEditElement {
            return copy(
                id = newId,
                pageIndex = targetPageIndex,
                bounds = shiftBounds(bounds, offsetNorm)
            )
        }
    }

    /**
     * 4. Link Area: Interactive link annotation (Web URL, Email, or Jump to Page).
     */
    data class LinkArea(
        override val id: String = UUID.randomUUID().toString(),
        override val pageIndex: Int,
        override val bounds: NormalizedRect,
        override val zIndex: Int = 0,
        val linkType: PdfLinkType = PdfLinkType.WEB_URL,
        val targetUrlOrEmail: String = "https://",
        val targetPageIndex: Int = 0,
        val isExistingFromPdf: Boolean = false
    ) : ContentEditElement() {
        override fun withUpdatedBounds(newBounds: NormalizedRect) = copy(bounds = newBounds)
        override fun withUpdatedZIndex(newZIndex: Int) = copy(zIndex = newZIndex)
        override fun duplicateOnPage(targetPageIndex: Int, newId: String, offsetNorm: Float): ContentEditElement {
            return copy(
                id = newId,
                pageIndex = targetPageIndex,
                bounds = shiftBounds(bounds, offsetNorm),
                isExistingFromPdf = false
            )
        }
    }

    /**
     * 5. Redaction Mark: Marks text or rectangular areas for permanent redaction (drawn as black boxes
     * and permanently strips/rasterizes underlying content on Apply).
     */
    data class RedactionMark(
        override val id: String = UUID.randomUUID().toString(),
        override val pageIndex: Int,
        override val bounds: NormalizedRect,
        override val zIndex: Int = 100,
        val label: String = "REDACTED"
    ) : ContentEditElement() {
        override fun withUpdatedBounds(newBounds: NormalizedRect) = copy(bounds = newBounds)
        override fun withUpdatedZIndex(newZIndex: Int) = copy(zIndex = newZIndex)
        override fun duplicateOnPage(targetPageIndex: Int, newId: String, offsetNorm: Float): ContentEditElement {
            return copy(
                id = newId,
                pageIndex = targetPageIndex,
                bounds = shiftBounds(bounds, offsetNorm)
            )
        }
    }

    companion object {
        fun shiftBounds(rect: NormalizedRect, delta: Float): NormalizedRect {
            val w = (rect.right - rect.left).coerceIn(0.04f, 0.95f)
            val h = (rect.bottom - rect.top).coerceIn(0.02f, 0.95f)
            val newLeft = (rect.left + delta).coerceIn(0.01f, 0.99f - w)
            val newTop = (rect.top + delta).coerceIn(0.01f, 0.99f - h)
            return NormalizedRect(
                left = newLeft,
                top = newTop,
                right = newLeft + w,
                bottom = newTop + h
            )
        }
    }
}
