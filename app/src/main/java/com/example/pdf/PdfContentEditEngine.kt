package com.example.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.scan.ScanImageProcessor
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionGoTo
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionURI
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDBorderStyleDictionary
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageXYZDestination
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

class PdfContentEditEngine(private val context: Context) {

    init {
        try {
            PDFBoxResourceLoader.init(context.applicationContext)
        } catch (ignored: Exception) {
        }
    }

    private val standard14BaseNames = setOf(
        "courier", "courier-bold", "courier-oblique", "courier-boldoblique",
        "helvetica", "helvetica-bold", "helvetica-oblique", "helvetica-boldoblique",
        "times-roman", "times-bold", "times-italic", "times-bolditalic",
        "symbol", "zapfdingbats"
    )

    /**
     * Extracts existing text lines with normalized bounding boxes for a given page,
     * and detects any missing or unembedded non-standard fonts on that page.
     */
    suspend fun inspectPage(
        pdfFile: File?,
        uri: Uri?,
        pageIndex: Int
    ): PageTextInspectionResult = withContext(Dispatchers.IO) {
        var pdDoc: PDDocument? = null
        val extractedLines = mutableListOf<ExistingTextLine>()
        val missingFonts = mutableSetOf<String>()

        try {
            pdDoc = openPdDocument(pdfFile, uri) ?: return@withContext PageTextInspectionResult(
                pageIndex = pageIndex,
                lines = emptyList(),
                missingFontNames = emptyList()
            )
            if (pageIndex !in 0 until pdDoc.numberOfPages) {
                return@withContext PageTextInspectionResult(pageIndex, emptyList(), emptyList())
            }

            val page = pdDoc.getPage(pageIndex)
            val mediaBox = page.mediaBox
            val pageW = mediaBox.width.coerceAtLeast(1f)
            val pageH = mediaBox.height.coerceAtLeast(1f)

            // 1. Check page resources for missing / unembedded fonts
            try {
                val resources = page.resources
                if (resources != null) {
                    for (fontKey in resources.fontNames) {
                        try {
                            val font: PDFont? = resources.getFont(fontKey)
                            if (font != null) {
                                val rawName = (font.name ?: fontKey.name).trim()
                                val normalizedName = rawName.substringAfter('+').lowercase()
                                val isStandard = standard14BaseNames.any { normalizedName.startsWith(it) }
                                if ((font.isDamaged || !font.isEmbedded) && !isStandard) {
                                    missingFonts.add(rawName.substringAfter('+'))
                                }
                            }
                        } catch (e: Exception) {
                            missingFonts.add(fontKey.name)
                        }
                    }
                }
            } catch (ignored: Exception) {
            }

            val pageHasBijoyFont = BengaliTextNormalizer.hasLegacyBijoyFontsOnPage(page)

            // 2. Extract text lines with bounding boxes using custom PDFTextStripper
            val lineStripper = object : PDFTextStripper() {
                init {
                    sortByPosition = true
                    startPage = pageIndex + 1
                    endPage = pageIndex + 1
                }

                override fun writeString(text: String?, textPositions: MutableList<TextPosition>?) {
                    if (text.isNullOrBlank() || textPositions.isNullOrEmpty()) return
                    val rawTrimmed = text.trim()
                    if (rawTrimmed.isEmpty()) return
                    val trimmed = BengaliTextNormalizer.normalizeExtractedPdfText(
                        rawText = rawTrimmed,
                        pageHasBijoyFont = pageHasBijoyFont,
                        fixBrokenMatrasAndNuktas = true,
                        convertBijoyAnsiToUnicode = true
                    ).trim()
                    if (trimmed.isEmpty()) return

                    var minX = Float.MAX_VALUE
                    var minY = Float.MAX_VALUE
                    var maxX = -Float.MAX_VALUE
                    var maxY = -Float.MAX_VALUE
                    var fontSizeSum = 0f
                    var count = 0
                    var sampleFontName = "Helvetica"

                    for (pos in textPositions) {
                        val x = pos.xDirAdj
                        val y = pos.yDirAdj
                        val w = pos.widthDirAdj.coerceAtLeast(2f)
                        val h = pos.heightDir.coerceAtLeast(pos.fontSizeInPt * 0.85f).coerceAtLeast(6f)

                        if (x < minX) minX = x
                        if (y - h < minY) minY = y - h
                        if (x + w > maxX) maxX = x + w
                        if (y + h * 0.25f > maxY) maxY = y + h * 0.25f

                        fontSizeSum += pos.fontSizeInPt.coerceIn(6f, 72f)
                        count++
                        val fn = pos.font?.name
                        if (!fn.isNullOrBlank()) {
                            sampleFontName = fn.substringAfter('+')
                        }
                    }

                    if (count > 0 && maxX > minX && maxY > minY) {
                        val leftNorm = (minX / pageW).coerceIn(0f, 0.98f)
                        val topNorm = (minY / pageH).coerceIn(0f, 0.98f)
                        val rightNorm = (maxX / pageW).coerceIn(leftNorm + 0.02f, 1f)
                        val bottomNorm = (maxY / pageH).coerceIn(topNorm + 0.015f, 1f)

                        val avgFontSize = (fontSizeSum / count).coerceIn(8f, 64f)
                        val lowerFont = sampleFontName.lowercase()
                        extractedLines.add(
                            ExistingTextLine(
                                pageIndex = pageIndex,
                                bounds = NormalizedRect(leftNorm, topNorm, rightNorm, bottomNorm),
                                text = trimmed,
                                fontName = sampleFontName,
                                fontSizePt = avgFontSize,
                                isBold = lowerFont.contains("bold") || lowerFont.contains("black"),
                                isItalic = lowerFont.contains("italic") || lowerFont.contains("oblique")
                            )
                        )
                    }
                }
            }

            lineStripper.getText(pdDoc)
        } catch (oom: OutOfMemoryError) {
            System.gc()
        } catch (ignored: Throwable) {
        } finally {
            try {
                pdDoc?.close()
            } catch (ignored: Throwable) {
            }
        }

        PageTextInspectionResult(
            pageIndex = pageIndex,
            lines = extractedLines,
            missingFontNames = missingFonts.toList()
        )
    }

    /**
     * Loads existing interactive Link annotations (`PDAnnotationLink`) from the PDF so users can view/edit them.
     */
    suspend fun loadExistingLinks(
        pdfFile: File?,
        uri: Uri?
    ): List<ContentEditElement.LinkArea> = withContext(Dispatchers.IO) {
        var pdDoc: PDDocument? = null
        val links = mutableListOf<ContentEditElement.LinkArea>()

        try {
            pdDoc = openPdDocument(pdfFile, uri) ?: return@withContext emptyList()
            val maxPagesToScan = min(pdDoc.numberOfPages, 150)
            for (pageIndex in 0 until maxPagesToScan) {
                val page = pdDoc.getPage(pageIndex)
                val mediaBox = page.mediaBox
                val pageW = mediaBox.width.coerceAtLeast(1f)
                val pageH = mediaBox.height.coerceAtLeast(1f)

                val annots = try { page.annotations } catch (_: Throwable) { null } ?: continue
                for (annot in annots) {
                    if (annot is PDAnnotationLink) {
                        val rect = annot.rectangle ?: continue
                        val left = (rect.lowerLeftX / pageW).coerceIn(0f, 1f)
                        val right = (rect.upperRightX / pageW).coerceIn(0f, 1f)
                        val top = (1f - (rect.upperRightY / pageH)).coerceIn(0f, 1f)
                        val bottom = (1f - (rect.lowerLeftY / pageH)).coerceIn(0f, 1f)

                        var linkType = PdfLinkType.WEB_URL
                        var targetUrl = "https://"
                        var targetPage = 0

                        val action = annot.action
                        if (action is PDActionURI) {
                            val rawUri = action.uri ?: ""
                            if (rawUri.startsWith("mailto:", ignoreCase = true)) {
                                linkType = PdfLinkType.EMAIL
                                targetUrl = rawUri.removePrefix("mailto:").removePrefix("MAILTO:")
                            } else {
                                linkType = PdfLinkType.WEB_URL
                                targetUrl = rawUri
                            }
                        } else if (action is PDActionGoTo) {
                            linkType = PdfLinkType.PAGE_JUMP
                            val dest = action.destination
                            if (dest is PDPageDestination) {
                                val pNum = dest.retrievePageNumber()
                                if (pNum >= 0) targetPage = pNum
                            }
                        } else {
                            val dest = annot.destination
                            if (dest is PDPageDestination) {
                                linkType = PdfLinkType.PAGE_JUMP
                                val pNum = dest.retrievePageNumber()
                                if (pNum >= 0) targetPage = pNum
                            }
                        }

                        links.add(
                            ContentEditElement.LinkArea(
                                id = UUID.randomUUID().toString(),
                                pageIndex = pageIndex,
                                bounds = NormalizedRect(
                                    left = min(left, right),
                                    top = min(top, bottom),
                                    right = max(left, right),
                                    bottom = max(top, bottom)
                                ),
                                linkType = linkType,
                                targetUrlOrEmail = targetUrl,
                                targetPageIndex = targetPage,
                                isExistingFromPdf = true
                            )
                        )
                    }
                }
            }
        } catch (oom: OutOfMemoryError) {
            System.gc()
        } catch (ignored: Throwable) {
        } finally {
            try {
                pdDoc?.close()
            } catch (ignored: Throwable) {
            }
        }

        links
    }

    /**
     * Applies all content edits (AddedText with embedded Noto Sans & Noto Sans Bengali subsets,
     * ReplacedText with background cover, ImageObject with crop & rotation, LinkArea annotations,
     * and permanent RedactionMark content removal / page rasterization) to the PDF and writes a new PDF file.
     */
    suspend fun applyContentEditsToPdf(
        sourceFile: File?,
        sourceUri: Uri?,
        edits: List<ContentEditElement>,
        onProgress: (currentPage: Int, totalPages: Int, message: String) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        var pdDoc: PDDocument? = null
        try {
            // Copy source to a working temp file so we can also open a PdfRenderer if redaction rasterization is needed
            val workingInputFile = File(context.cacheDir, "content_edit_in_${UUID.randomUUID()}.pdf")
            if (sourceFile != null && sourceFile.exists()) {
                sourceFile.copyTo(workingInputFile, overwrite = true)
            } else if (sourceUri != null) {
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    FileOutputStream(workingInputFile).use { output ->
                        input.copyTo(output)
                    }
                } ?: return@withContext null
            } else {
                return@withContext null
            }

            pdDoc = PdfBoxMemoryUtil.loadFromFile(context, workingInputFile)
            val totalPages = pdDoc.numberOfPages

            // Lazily load embedded TrueType font subsets (Noto Sans and Noto Sans Bengali)
            var notoSansFont: PDType0Font? = null
            var notoBengaliFont: PDType0Font? = null

            @android.annotation.SuppressLint("ResourceType")
            fun getNotoSansFont(doc: PDDocument): PDFont {
                if (notoSansFont == null) {
                    notoSansFont = try {
                        context.resources.openRawResource(R.font.noto_sans).use { stream ->
                            PDType0Font.load(doc, stream, true)
                        }
                    } catch (e: Exception) {
                        null
                    }
                }
                return notoSansFont ?: PDType1Font.HELVETICA
            }

            @android.annotation.SuppressLint("ResourceType")
            fun getNotoBengaliFont(doc: PDDocument): PDFont {
                if (notoBengaliFont == null) {
                    notoBengaliFont = try {
                        context.resources.openRawResource(R.font.noto_sans_bengali).use { stream ->
                            PDType0Font.load(doc, stream, true)
                        }
                    } catch (e: Exception) {
                        null
                    }
                }
                return notoBengaliFont ?: getNotoSansFont(doc)
            }

            val editsByPage = edits.groupBy { it.pageIndex }

            for (pageIndex in 0 until totalPages) {
                val pageEdits = editsByPage[pageIndex]?.sortedBy { it.zIndex } ?: continue
                if (pageEdits.isEmpty()) continue

                onProgress(pageIndex + 1, totalPages, "Applying content edits on page ${pageIndex + 1} of $totalPages...")

                val page = pdDoc.getPage(pageIndex)
                val mediaBox = page.mediaBox
                val pageW = mediaBox.width.coerceAtLeast(1f)
                val pageH = mediaBox.height.coerceAtLeast(1f)

                val nonRedactions = pageEdits.filter { it !is ContentEditElement.RedactionMark }
                val redactions = pageEdits.filterIsInstance<ContentEditElement.RedactionMark>()

                // Remove existing PDAnnotationLinks if we are replacing/updating links on this page
                if (nonRedactions.any { it is ContentEditElement.LinkArea }) {
                    try {
                        val existingAnnots = page.annotations?.toMutableList()
                        if (existingAnnots != null) {
                            existingAnnots.removeAll { it is PDAnnotationLink }
                            page.annotations = existingAnnots
                        }
                    } catch (ignored: Exception) {
                    }
                }

                // 1. Apply non-redaction edits (AddedText, ReplacedText, ImageObject, LinkArea)
                if (nonRedactions.isNotEmpty()) {
                    PDPageContentStream(pdDoc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                        for (element in nonRedactions) {
                            when (element) {
                                is ContentEditElement.ReplacedText -> {
                                    drawReplacedTextElement(
                                        doc = pdDoc,
                                        cs = cs,
                                        element = element,
                                        pageW = pageW,
                                        pageH = pageH,
                                        getNotoSans = { getNotoSansFont(pdDoc) },
                                        getNotoBengali = { getNotoBengaliFont(pdDoc) }
                                    )
                                }
                                is ContentEditElement.AddedText -> {
                                    drawAddedTextElement(
                                        doc = pdDoc,
                                        cs = cs,
                                        element = element,
                                        pageW = pageW,
                                        pageH = pageH,
                                        getNotoSans = { getNotoSansFont(pdDoc) },
                                        getNotoBengali = { getNotoBengaliFont(pdDoc) }
                                    )
                                }
                                is ContentEditElement.ImageObject -> {
                                    drawImageElement(
                                        doc = pdDoc,
                                        cs = cs,
                                        element = element,
                                        pageW = pageW,
                                        pageH = pageH
                                    )
                                }
                                is ContentEditElement.LinkArea -> {
                                    addLinkAnnotationToPage(
                                        doc = pdDoc,
                                        page = page,
                                        cs = cs,
                                        element = element,
                                        pageW = pageW,
                                        pageH = pageH
                                    )
                                }
                                is ContentEditElement.RedactionMark -> {}
                            }
                        }
                    }
                }

                // 2. Apply permanent Redactions on this page if any RedactionMark exists
                if (redactions.isNotEmpty()) {
                    onProgress(
                        pageIndex + 1,
                        totalPages,
                        "Permanently redacting & sanitizing page ${pageIndex + 1}..."
                    )
                    applyPermanentRedactionsOnPage(
                        pdDoc = pdDoc,
                        pageIndex = pageIndex,
                        redactions = redactions
                    )
                }
            }

            val outputFile = File(context.cacheDir, "content_edited_${System.currentTimeMillis()}.pdf")
            pdDoc.save(outputFile)
            workingInputFile.delete()
            outputFile
        } catch (e: Exception) {
            null
        } finally {
            try {
                pdDoc?.close()
            } catch (ignored: Exception) {
            }
        }
    }

    private fun drawReplacedTextElement(
        doc: PDDocument,
        cs: PDPageContentStream,
        element: ContentEditElement.ReplacedText,
        pageW: Float,
        pageH: Float,
        getNotoSans: () -> PDFont,
        getNotoBengali: () -> PDFont
    ) {
        val boxLeft = element.bounds.left * pageW
        val boxRight = element.bounds.right * pageW
        val boxTopPdf = (1f - element.bounds.top) * pageH
        val boxBottomPdf = (1f - element.bounds.bottom) * pageH
        val boxW = (boxRight - boxLeft).coerceAtLeast(8f)
        val boxH = (boxTopPdf - boxBottomPdf).coerceAtLeast(8f)

        // 1. Cover original text with background-colored rectangle
        val bgR = Color.red(element.backgroundColorArgb) / 255f
        val bgG = Color.green(element.backgroundColorArgb) / 255f
        val bgB = Color.blue(element.backgroundColorArgb) / 255f
        cs.setNonStrokingColor(bgR, bgG, bgB)
        cs.addRect(boxLeft - 1.5f, boxBottomPdf - 1.5f, boxW + 3f, boxH + 3f)
        cs.fill()

        // 2. Draw replacement text in closest matching font
        if (element.replacementText.isNotBlank()) {
            val asAdded = ContentEditElement.AddedText(
                id = element.id,
                pageIndex = element.pageIndex,
                bounds = element.bounds,
                zIndex = element.zIndex,
                text = element.replacementText,
                font = element.font,
                fontSizeSp = element.fontSizeSp,
                colorArgb = element.textColorArgb,
                alignment = TextAlignmentMode.LEFT,
                isBold = element.isBold,
                isItalic = element.isItalic
            )
            drawAddedTextElement(
                doc = doc,
                cs = cs,
                element = asAdded,
                pageW = pageW,
                pageH = pageH,
                getNotoSans = getNotoSans,
                getNotoBengali = getNotoBengali
            )
        }
    }

    private fun drawAddedTextElement(
        doc: PDDocument,
        cs: PDPageContentStream,
        element: ContentEditElement.AddedText,
        pageW: Float,
        pageH: Float,
        getNotoSans: () -> PDFont,
        getNotoBengali: () -> PDFont
    ) {
        if (element.text.isBlank()) return

        val boxLeft = element.bounds.left * pageW
        val boxRight = element.bounds.right * pageW
        val boxTopPdf = (1f - element.bounds.top) * pageH
        val boxBottomPdf = (1f - element.bounds.bottom) * pageH
        val boxW = (boxRight - boxLeft).coerceAtLeast(16f)
        val boxH = (boxTopPdf - boxBottomPdf).coerceAtLeast(12f)

        val usesBengali = element.font.isBengaliCapable ||
            BundledEditFont.containsBengaliScript(element.text)

        if (usesBengali) {
            val normalizedText = BengaliTextNormalizer.fixUnicodeBengaliOrderAndNuktas(element.text)
            // 1. Embed the Noto Sans Bengali TrueType subset and write an invisible searchable Unicode text stream
            val bengaliFont = getNotoBengali()
            try {
                cs.saveGraphicsState()
                cs.setRenderingMode(RenderingMode.NEITHER)
                cs.beginText()
                cs.setFont(bengaliFont, element.fontSizeSp.coerceIn(8f, 48f))
                cs.newLineAtOffset(boxLeft, (boxTopPdf - element.fontSizeSp).coerceAtLeast(boxBottomPdf))
                cs.showText(normalizedText.replace("\n", " "))
                cs.endText()
                cs.restoreGraphicsState()
            } catch (ignored: Exception) {
                try { cs.endText() } catch (_: Exception) {}
                try { cs.restoreGraphicsState() } catch (_: Exception) {}
            }

            // 2. Render complex Bengali conjuncts (যুক্তাক্ষর) via Android HarfBuzz StaticLayout onto a high-DPI transparent layer
            val activeBengaliFont = if (element.font.isBengaliCapable) element.font else BundledEditFont.NOTO_SANS_BENGALI
            val shapedBitmap = renderShapedTextBitmap(
                text = normalizedText,
                font = activeBengaliFont,
                fontSizePt = element.fontSizeSp,
                colorArgb = element.colorArgb,
                alignment = element.alignment,
                isBold = element.isBold,
                isItalic = element.isItalic,
                boxWidthPt = boxW,
                boxHeightPt = boxH
            )
            if (shapedBitmap != null) {
                try {
                    val pdImage = LosslessFactory.createFromImage(doc, shapedBitmap)
                    cs.drawImage(pdImage, boxLeft, boxBottomPdf, boxW, boxH)
                } finally {
                    shapedBitmap.recycle()
                }
            }
            return
        }

        // Standard Latin / Noto Sans subset embedding
        val pdFont: PDFont = when {
            element.font == BundledEditFont.NOTO_SANS -> getNotoSans()
            element.font.isBengaliCapable -> getNotoBengali()
            element.font == BundledEditFont.HELVETICA -> when {
                element.isBold && element.isItalic -> PDType1Font.HELVETICA_BOLD_OBLIQUE
                element.isBold -> PDType1Font.HELVETICA_BOLD
                element.isItalic -> PDType1Font.HELVETICA_OBLIQUE
                else -> PDType1Font.HELVETICA
            }
            element.font == BundledEditFont.TIMES_ROMAN -> when {
                element.isBold && element.isItalic -> PDType1Font.TIMES_BOLD_ITALIC
                element.isBold -> PDType1Font.TIMES_BOLD
                element.isItalic -> PDType1Font.TIMES_ITALIC
                else -> PDType1Font.TIMES_ROMAN
            }
            element.font == BundledEditFont.COURIER -> when {
                element.isBold && element.isItalic -> PDType1Font.COURIER_BOLD_OBLIQUE
                element.isBold -> PDType1Font.COURIER_BOLD
                element.isItalic -> PDType1Font.COURIER_OBLIQUE
                else -> PDType1Font.COURIER
            }
            else -> getNotoSans()
        }

        val r = Color.red(element.colorArgb) / 255f
        val g = Color.green(element.colorArgb) / 255f
        val b = Color.blue(element.colorArgb) / 255f
        cs.setNonStrokingColor(r, g, b)

        val fontSize = element.fontSizeSp.coerceIn(6f, 64f)
        val lineHeight = fontSize * 1.25f
        val rawLines = element.text.split("\n")

        var cursorY = boxTopPdf - fontSize
        for (line in rawLines) {
            if (cursorY < boxBottomPdf - fontSize * 0.5f) break
            val safeLine = line.trimEnd()
            if (safeLine.isEmpty()) {
                cursorY -= lineHeight
                continue
            }

            try {
                val textWidthPt = try {
                    (pdFont.getStringWidth(safeLine) / 1000f) * fontSize
                } catch (_: Exception) {
                    safeLine.length * fontSize * 0.5f
                }

                val startX = when (element.alignment) {
                    TextAlignmentMode.LEFT -> boxLeft
                    TextAlignmentMode.CENTER -> boxLeft + ((boxW - textWidthPt) / 2f).coerceAtLeast(0f)
                    TextAlignmentMode.RIGHT -> boxLeft + (boxW - textWidthPt).coerceAtLeast(0f)
                }

                cs.beginText()
                cs.setFont(pdFont, fontSize)
                cs.newLineAtOffset(startX, cursorY)
                cs.showText(safeLine)
                cs.endText()
            } catch (encodingError: Exception) {
                try { cs.endText() } catch (_: Exception) {}
                // Fallback to Noto Sans embedded subset if a character wasn't supported by Type1 WinAnsiEncoding
                try {
                    val fallbackFont = getNotoSans()
                    cs.beginText()
                    cs.setFont(fallbackFont, fontSize)
                    cs.newLineAtOffset(boxLeft, cursorY)
                    cs.showText(safeLine)
                    cs.endText()
                } catch (_: Exception) {
                    try { cs.endText() } catch (_: Exception) {}
                }
            }

            cursorY -= lineHeight
        }
    }

    private fun renderShapedTextBitmap(
        text: String,
        font: BundledEditFont,
        fontSizePt: Float,
        colorArgb: Int,
        alignment: TextAlignmentMode,
        isBold: Boolean,
        isItalic: Boolean,
        boxWidthPt: Float,
        boxHeightPt: Float
    ): Bitmap? {
        return try {
            val scale = 3f // 3x supersampling for crisp vector-grade text rendering
            val bmpW = (boxWidthPt * scale).toInt().coerceIn(32, 2048)
            val bmpH = (boxHeightPt * scale).toInt().coerceIn(24, 2048)

            val baseTypeface = when {
                font.customFontResId != null ->
                    try {
                        ResourcesCompat.getFont(context, font.customFontResId)
                    } catch (_: Exception) {
                        null
                    } ?: if (font.isSerif) Typeface.SERIF else Typeface.DEFAULT
                font == BundledEditFont.TIMES_ROMAN -> Typeface.SERIF
                font == BundledEditFont.COURIER -> Typeface.MONOSPACE
                else -> Typeface.SANS_SERIF
            }

            val style = when {
                isBold && isItalic -> Typeface.BOLD_ITALIC
                isBold -> Typeface.BOLD
                isItalic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }
            val finalTypeface = Typeface.create(baseTypeface, style)

            val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                color = colorArgb
                textSize = fontSizePt * scale
                typeface = finalTypeface
                isFakeBoldText = isBold
                textSkewX = if (isItalic) -0.2f else 0f
            }

            val layoutAlign = when (alignment) {
                TextAlignmentMode.LEFT -> Layout.Alignment.ALIGN_NORMAL
                TextAlignmentMode.CENTER -> Layout.Alignment.ALIGN_CENTER
                TextAlignmentMode.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
            }

            val staticLayout = StaticLayout.Builder
                .obtain(text, 0, text.length, textPaint, bmpW)
                .setAlignment(layoutAlign)
                .setIncludePad(false)
                .build()

            val finalH = max(bmpH, staticLayout.height.coerceAtMost(2048))
            val bitmap = Bitmap.createBitmap(bmpW, finalH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            staticLayout.draw(canvas)
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    private fun drawImageElement(
        doc: PDDocument,
        cs: PDPageContentStream,
        element: ContentEditElement.ImageObject,
        pageW: Float,
        pageH: Float
    ) {
        val decoded = ScanImageProcessor.decodeSafeFile(element.imageFile, maxDim = 1800) ?: return
        try {
            // 1. Apply normalized cropRect
            val cLeft = (element.cropRect.left * decoded.width).toInt().coerceIn(0, decoded.width - 1)
            val cTop = (element.cropRect.top * decoded.height).toInt().coerceIn(0, decoded.height - 1)
            val cRight = (element.cropRect.right * decoded.width).toInt().coerceIn(cLeft + 1, decoded.width)
            val cBottom = (element.cropRect.bottom * decoded.height).toInt().coerceIn(cTop + 1, decoded.height)

            val cropped = if (cLeft > 0 || cTop > 0 || cRight < decoded.width || cBottom < decoded.height) {
                val sub = Bitmap.createBitmap(decoded, cLeft, cTop, cRight - cLeft, cBottom - cTop)
                if (sub != decoded) decoded.recycle()
                sub
            } else {
                decoded
            }

            // 2. Apply rotation if non-zero
            val finalBmp = if (element.rotationDegrees % 360f != 0f) {
                val matrix = Matrix().apply { postRotate(element.rotationDegrees) }
                val rot = Bitmap.createBitmap(cropped, 0, 0, cropped.width, cropped.height, matrix, true)
                if (rot != cropped) cropped.recycle()
                rot
            } else {
                cropped
            }

            try {
                val pdImage = JPEGFactory.createFromImage(doc, finalBmp, 0.88f)
                val drawX = element.bounds.left * pageW
                val drawW = ((element.bounds.right - element.bounds.left) * pageW).coerceAtLeast(10f)
                val drawH = ((element.bounds.bottom - element.bounds.top) * pageH).coerceAtLeast(10f)
                val drawY = (1f - element.bounds.bottom) * pageH

                cs.drawImage(pdImage, drawX, drawY, drawW, drawH)
            } finally {
                finalBmp.recycle()
            }
        } catch (ignored: Exception) {
            try {
                if (!decoded.isRecycled) decoded.recycle()
            } catch (_: Exception) {
            }
        }
    }

    private fun addLinkAnnotationToPage(
        doc: PDDocument,
        page: PDPage,
        cs: PDPageContentStream,
        element: ContentEditElement.LinkArea,
        pageW: Float,
        pageH: Float
    ) {
        val left = element.bounds.left * pageW
        val right = element.bounds.right * pageW
        val topPdf = (1f - element.bounds.top) * pageH
        val bottomPdf = (1f - element.bounds.bottom) * pageH

        val linkAnnot = PDAnnotationLink()
        linkAnnot.rectangle = PDRectangle(left, bottomPdf, (right - left).coerceAtLeast(8f), (topPdf - bottomPdf).coerceAtLeast(8f))

        val borderStyle = PDBorderStyleDictionary().apply {
            width = 1f
            style = PDBorderStyleDictionary.STYLE_UNDERLINE
        }
        linkAnnot.borderStyle = borderStyle

        when (element.linkType) {
            PdfLinkType.WEB_URL -> {
                val raw = element.targetUrlOrEmail.trim()
                val formatted = if (raw.startsWith("http://", true) || raw.startsWith("https://", true)) {
                    raw
                } else {
                    "https://$raw"
                }
                linkAnnot.action = PDActionURI().apply { uri = formatted }
            }
            PdfLinkType.EMAIL -> {
                val raw = element.targetUrlOrEmail.trim().removePrefix("mailto:")
                linkAnnot.action = PDActionURI().apply { uri = "mailto:$raw" }
            }
            PdfLinkType.PAGE_JUMP -> {
                val targetIdx = element.targetPageIndex.coerceIn(0, (doc.numberOfPages - 1).coerceAtLeast(0))
                val dest = PDPageXYZDestination().apply {
                    this.page = doc.getPage(targetIdx)
                }
                linkAnnot.action = PDActionGoTo().apply {
                    destination = dest
                }
            }
        }

        val currentAnnots = try { page.annotations?.toMutableList() } catch (_: Exception) { null } ?: mutableListOf()
        currentAnnots.add(linkAnnot)
        page.annotations = currentAnnots

        // Draw a subtle blue link underline on the page so the user can see clickable area
        cs.setStrokingColor(0.12f, 0.45f, 0.88f)
        cs.setLineWidth(1f)
        cs.moveTo(left, bottomPdf)
        cs.lineTo(right, bottomPdf)
        cs.stroke()
    }

    /**
     * Permanently removes content under redactions by rasterizing the page with solid black redaction
     * boxes burned in and replacing the page's content stream & resources so redacted text can never be recovered.
     */
    private fun applyPermanentRedactionsOnPage(
        pdDoc: PDDocument,
        pageIndex: Int,
        redactions: List<ContentEditElement.RedactionMark>
    ) {
        val tempPreRedactFile = File(context.cacheDir, "redact_stage_${UUID.randomUUID()}.pdf")
        try {
            // Save intermediate document state so PdfRenderer renders all non-redacted edits accurately
            pdDoc.save(tempPreRedactFile)

            val page = pdDoc.getPage(pageIndex)
            val mediaBox = page.mediaBox
            val pageW = mediaBox.width.coerceAtLeast(1f)
            val pageH = mediaBox.height.coerceAtLeast(1f)

            // Render at ~160 DPI (max 1600px on longest side for 2 GB RAM safety)
            val scale = min(1600f / max(pageW, pageH), 2.2f).coerceAtLeast(1.2f)
            val bmpW = (pageW * scale).toInt().coerceAtLeast(200)
            val bmpH = (pageH * scale).toInt().coerceAtLeast(200)

            val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)

            var renderedViaAndroid = false
            try {
                ParcelFileDescriptor.open(tempPreRedactFile, ParcelFileDescriptor.MODE_READ_ONLY)?.use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        if (pageIndex in 0 until renderer.pageCount) {
                            renderer.openPage(pageIndex).use { renderPage ->
                                renderPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                renderedViaAndroid = true
                            }
                        }
                    }
                }
            } catch (ignored: Throwable) {
            }

            if (!renderedViaAndroid) {
                // Fallback for JVM unit tests where native PdfRenderer is stubbed
                try {
                    val pdfBoxRenderer = com.tom_roush.pdfbox.rendering.PDFRenderer(pdDoc)
                    val fbBmp = pdfBoxRenderer.renderImageWithDPI(pageIndex, 144f)
                    val destRect = android.graphics.Rect(0, 0, bmpW, bmpH)
                    canvas.drawBitmap(fbBmp, null, destRect, null)
                    fbBmp.recycle()
                } catch (ignored: Throwable) {
                }
            }

            // Burn solid black redaction boxes permanently onto the rasterized page bitmap
            val redactPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                style = Paint.Style.FILL
            }
            for (mark in redactions) {
                val leftPx = mark.bounds.left * bmpW
                val topPx = mark.bounds.top * bmpH
                val rightPx = mark.bounds.right * bmpW
                val bottomPx = mark.bounds.bottom * bmpH
                canvas.drawRect(leftPx, topPx, rightPx, bottomPx, redactPaint)
            }

            // Strip all annotations on the redacted page that overlap any redaction box
            try {
                val remainingAnnots = page.annotations?.filter { annot ->
                    val r = annot.rectangle ?: return@filter false
                    val aLeft = r.lowerLeftX / pageW
                    val aRight = r.upperRightX / pageW
                    val aTop = 1f - (r.upperRightY / pageH)
                    val aBottom = 1f - (r.lowerLeftY / pageH)
                    redactions.none { mark ->
                        aLeft < mark.bounds.right && aRight > mark.bounds.left &&
                            aTop < mark.bounds.bottom && aBottom > mark.bounds.top
                    }
                } ?: emptyList()
                page.annotations = remainingAnnots
            } catch (ignored: Exception) {
            }

            // Clear existing page resources (fonts, old XObjects) and overwrite content stream
            page.resources = com.tom_roush.pdfbox.pdmodel.PDResources()
            val redactedImage = JPEGFactory.createFromImage(pdDoc, bitmap, 0.90f)
            bitmap.recycle()

            PDPageContentStream(pdDoc, page, PDPageContentStream.AppendMode.OVERWRITE, true, true).use { cs ->
                cs.drawImage(redactedImage, 0f, 0f, pageW, pageH)
            }
        } finally {
            tempPreRedactFile.delete()
        }
    }

    private fun openPdDocument(pdfFile: File?, uri: Uri?): PDDocument? {
        return PdfBoxMemoryUtil.loadDocument(context, pdfFile, uri)
    }
}
