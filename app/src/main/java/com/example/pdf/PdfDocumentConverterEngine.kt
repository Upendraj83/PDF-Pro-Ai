package com.example.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.util.Base64
import com.example.scan.ScanOcrEngine
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.rendering.ImageType
import com.tom_roush.pdfbox.rendering.PDFRenderer
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class PdfExportFormat(
    val displayName: String,
    val badge: String,
    val extension: String,
    val mimeType: String,
    val description: String
) {
    WORD_DOCX(
        displayName = "MS Word (.docx)",
        badge = "DOCX",
        extension = "docx",
        mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        description = "Editable Microsoft Word document with headings, paragraphs, detected tables, and optional page snapshots."
    ),
    EXCEL_XLSX(
        displayName = "MS Excel (.xlsx)",
        badge = "XLSX",
        extension = "xlsx",
        mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        description = "Microsoft Excel workbook organizing tabular PDF lines and columns into spreadsheet rows."
    ),
    POWERPOINT_PPTX(
        displayName = "MS PowerPoint (.pptx)",
        badge = "PPTX",
        extension = "pptx",
        mimeType = "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        description = "Microsoft PowerPoint slide deck creating one structured presentation slide per PDF page."
    ),
    RTF_DOCUMENT(
        displayName = "Rich Text (.rtf)",
        badge = "RTF",
        extension = "rtf",
        mimeType = "application/rtf",
        description = "Universal Rich Text Format compatible with MS Word, WordPad, LibreOffice, and macOS TextEdit."
    ),
    CSV_SPREADSHEET(
        displayName = "CSV Spreadsheet (.csv)",
        badge = "CSV",
        extension = "csv",
        mimeType = "text/csv",
        description = "UTF-8 comma-separated values spreadsheet for Excel, Google Sheets, and data analysis."
    ),
    HTML_WEBPAGE(
        displayName = "Web Page (.html)",
        badge = "HTML5",
        extension = "html",
        mimeType = "text/html",
        description = "Responsive styled HTML5 document with page cards, typography, and optional embedded page images."
    ),
    MARKDOWN_MD(
        displayName = "Markdown (.md)",
        badge = "MD",
        extension = "md",
        mimeType = "text/markdown",
        description = "Clean structured Markdown with headings, bullet lists, and Markdown tables for Notion, GitHub, or Obsidian."
    ),
    PLAIN_TEXT_TXT(
        displayName = "Plain Text (.txt)",
        badge = "TXT",
        extension = "txt",
        mimeType = "text/plain",
        description = "Clean UTF-8 plain text with optional page headers."
    )
}

data class PdfConversionConfig(
    val format: PdfExportFormat = PdfExportFormat.WORD_DOCX,
    val pageRangeText: String = "", // Empty = All Pages
    val autoOcrScannedPages: Boolean = true,
    val ocrLanguagePack: com.example.scan.OcrLanguagePack = com.example.scan.OcrLanguagePack.AUTO,
    val includePageImages: Boolean = false,
    val includePageHeaders: Boolean = true,
    val detectTablesAndHeadings: Boolean = true,
    val targetBengaliFont: BengaliFontOption = BengaliFontOption.KALPURUSH,
    val fixBengaliFontEncoding: Boolean = true,
    val convertBijoyToUnicode: Boolean = true,
    val outputBaseName: String = ""
)

data class ExtractedPageData(
    val pageNumber: Int, // 1-based
    val rawText: String,
    val usedOcr: Boolean,
    val jpegBytes: ByteArray? = null,
    val imageWidthPx: Int = 0,
    val imageHeightPx: Int = 0
)

class PdfDocumentConverterEngine(private val context: Context) {

    private val ocrEngine = ScanOcrEngine(context.applicationContext)

    init {
        try {
            PDFBoxResourceLoader.init(context.applicationContext)
        } catch (_: Exception) {
        }
    }

    fun getOutputDirectory(): File {
        val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        val outDir = File(baseDir, "PDF_Exports")
        if (!outDir.exists()) outDir.mkdirs()
        return outDir
    }

    /**
     * Parses a 1-based page range string like "1-3, 5" into 0-based page indices.
     * If blank, returns all pages `0 until totalPages`.
     */
    fun resolvePageIndices(rangeText: String, totalPages: Int): List<Int> {
        if (totalPages <= 0) return emptyList()
        val trimmed = rangeText.trim()
        if (trimmed.isEmpty()) return (0 until totalPages).toList()

        val result = sortedSetOf<Int>()
        val parts = trimmed.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        for (part in parts) {
            if (part.contains("-")) {
                val bounds = part.split("-").map { it.trim() }
                if (bounds.size == 2) {
                    val start = bounds[0].toIntOrNull()
                    val end = bounds[1].toIntOrNull()
                    if (start != null && end != null) {
                        val s = minOf(start, end).coerceIn(1, totalPages)
                        val e = maxOf(start, end).coerceIn(1, totalPages)
                        for (p in s..e) {
                            result.add(p - 1)
                        }
                    }
                }
            } else {
                val single = part.toIntOrNull()
                if (single != null && single in 1..totalPages) {
                    result.add(single - 1)
                }
            }
        }
        return if (result.isEmpty()) (0 until totalPages).toList() else result.toList()
    }

    /**
     * Converts a PDF document (from [sourceFile] or [sourceUri]) into the target [PdfConversionConfig.format]
     * (MS Word .docx, MS Excel .xlsx, MS PowerPoint .pptx, RTF, CSV, HTML, Markdown, or TXT).
     */
    suspend fun convertPdfDocument(
        sourceFile: File?,
        sourceUri: Uri?,
        sourceFileName: String,
        config: PdfConversionConfig,
        onProgress: (fraction: Float, step: String) -> Unit = { _, _ -> }
    ): File = withContext(Dispatchers.IO) {
        onProgress(0.04f, "Opening PDF document...")

        val pdDocument: PDDocument = PdfBoxMemoryUtil.loadDocument(context, sourceFile, sourceUri)
            ?: throw IllegalStateException("Could not open the selected PDF file for conversion.")

        try {
            val totalDocPages = pdDocument.numberOfPages
            if (totalDocPages <= 0) {
                throw IllegalStateException("The selected PDF document contains no pages.")
            }

            val targetIndices = resolvePageIndices(config.pageRangeText, totalDocPages)
            val totalSelected = targetIndices.size.coerceAtLeast(1)

            val stripper = PDFTextStripper().apply {
                sortByPosition = true
            }

            val extractedPages = ArrayList<ExtractedPageData>(totalSelected)

            for ((idxInSelection, pageIndex) in targetIndices.withIndex()) {
                currentCoroutineContext().ensureActive()
                val pageNum = pageIndex + 1
                val progressBase = 0.08f + (idxInSelection.toFloat() / totalSelected) * 0.65f
                onProgress(
                    progressBase,
                    "Extracting content from page $pageNum (${idxInSelection + 1} of $totalSelected)..."
                )

                val pdPage = try { pdDocument.getPage(pageIndex) } catch (_: Throwable) { null }
                val pageHasBijoyFont = BengaliTextNormalizer.hasLegacyBijoyFontsOnPage(pdPage)

                stripper.startPage = pageNum
                stripper.endPage = pageNum
                val embeddedText = try {
                    stripper.getText(pdDocument)?.trim().orEmpty()
                } catch (_: Throwable) {
                    ""
                }

                var finalPageText = embeddedText
                var usedOcr = false

                // Automatic ML Kit OCR fallback if the page has no searchable text (e.g., scanned PDF)
                if (finalPageText.length < 10 && config.autoOcrScannedPages) {
                    onProgress(
                        progressBase + 0.02f,
                        "Running ML Kit OCR (${config.ocrLanguagePack.displayName}) on scanned page $pageNum..."
                    )
                    val ocrRes = try {
                        ocrEngine.recognizeTextFromPdfPage(
                            sourceFile,
                            sourceUri,
                            pageIndex,
                            targetWidth = 1440,
                            languagePack = config.ocrLanguagePack
                        )
                    } catch (_: Throwable) {
                        null
                    }
                    if (ocrRes != null && ocrRes.fullText.isNotBlank()) {
                        finalPageText = ocrRes.fullText.trim()
                        usedOcr = true
                    }
                }

                // Fix broken Bengali vowel signs (matras/kars), nuktas, and legacy Bijoy/ANSI font encoding
                if (finalPageText.isNotBlank() && (config.fixBengaliFontEncoding || config.convertBijoyToUnicode)) {
                    finalPageText = BengaliTextNormalizer.normalizeExtractedPdfText(
                        rawText = finalPageText,
                        pageHasBijoyFont = pageHasBijoyFont || config.targetBengaliFont.isLegacyBijoyFamily,
                        fixBrokenMatrasAndNuktas = config.fixBengaliFontEncoding,
                        convertBijoyAnsiToUnicode = config.convertBijoyToUnicode
                    )
                }

                // Optionally render page snapshot JPEG for DOCX or HTML hybrid export
                var jpegBytes: ByteArray? = null
                var imgW = 0
                var imgH = 0
                if (config.includePageImages &&
                    (config.format == PdfExportFormat.WORD_DOCX || config.format == PdfExportFormat.HTML_WEBPAGE)
                ) {
                    val bmp = renderPageBitmapSafe(pdDocument, sourceFile, sourceUri, pageIndex, targetWidth = 1080)
                    if (bmp != null) {
                        try {
                            imgW = bmp.width
                            imgH = bmp.height
                            val baos = ByteArrayOutputStream()
                            bmp.compress(Bitmap.CompressFormat.JPEG, 82, baos)
                            jpegBytes = baos.toByteArray()
                        } finally {
                            try { bmp.recycle() } catch (_: Exception) {}
                        }
                    }
                }

                extractedPages.add(
                    ExtractedPageData(
                        pageNumber = pageNum,
                        rawText = finalPageText,
                        usedOcr = usedOcr,
                        jpegBytes = jpegBytes,
                        imageWidthPx = imgW,
                        imageHeightPx = imgH
                    )
                )
            }

            val cleanBase = sanitizeBaseFileName(
                config.outputBaseName.ifBlank {
                    sourceFileName.removeSuffix(".pdf").removeSuffix(".PDF")
                }
            )
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val outFileName = "${cleanBase}_${timestamp}.${config.format.extension}"
            val outFile = File(getOutputDirectory(), outFileName)

            onProgress(0.80f, "Building ${config.format.displayName} file...")

            when (config.format) {
                PdfExportFormat.WORD_DOCX -> writeDocxPackage(outFile, cleanBase, extractedPages, config)
                PdfExportFormat.EXCEL_XLSX -> writeXlsxPackage(outFile, cleanBase, extractedPages, config)
                PdfExportFormat.POWERPOINT_PPTX -> writePptxPackage(outFile, cleanBase, extractedPages, config)
                PdfExportFormat.RTF_DOCUMENT -> writeRtfDocument(outFile, cleanBase, extractedPages, config)
                PdfExportFormat.CSV_SPREADSHEET -> writeCsvDocument(outFile, extractedPages, config)
                PdfExportFormat.HTML_WEBPAGE -> writeHtmlDocument(outFile, cleanBase, extractedPages, config)
                PdfExportFormat.MARKDOWN_MD -> writeMarkdownDocument(outFile, cleanBase, extractedPages, config)
                PdfExportFormat.PLAIN_TEXT_TXT -> writePlainTextDocument(outFile, cleanBase, extractedPages, config)
            }

            onProgress(1.0f, "Conversion complete!")
            outFile
        } finally {
            try {
                pdDocument.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun renderPageBitmapSafe(
        pdDocument: PDDocument,
        sourceFile: File?,
        sourceUri: Uri?,
        pageIndex: Int,
        targetWidth: Int
    ): Bitmap? {
        // Try Android native PdfRenderer first
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        try {
            pfd = when {
                sourceFile != null && sourceFile.exists() ->
                    ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY)
                sourceUri != null ->
                    context.contentResolver.openFileDescriptor(sourceUri, "r")
                else -> null
            }
            if (pfd != null) {
                renderer = PdfRenderer(pfd)
                if (pageIndex in 0 until renderer.pageCount) {
                    renderer.openPage(pageIndex).use { page ->
                        val aspect = page.height.toFloat() / page.width.toFloat().coerceAtLeast(1f)
                        val w = targetWidth.coerceIn(600, 1440)
                        val h = (w * aspect).toInt().coerceIn(600, 2048)
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        return bmp
                    }
                }
            }
        } catch (_: Throwable) {
        } finally {
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }

        // Fallback to PDFBox renderer
        return try {
            val pdfRenderer = PDFRenderer(pdDocument)
            pdfRenderer.renderImageWithDPI(pageIndex, 120f, ImageType.RGB)
        } catch (_: Throwable) {
            null
        }
    }

    // =========================================================================
    // 1. MS Word (.docx) — OpenXML WordprocessingML Generator
    // =========================================================================
    private fun writeDocxPackage(
        outFile: File,
        docTitle: String,
        pages: List<ExtractedPageData>,
        config: PdfConversionConfig
    ) {
        val hasImages = pages.any { it.jpegBytes != null }
        val bengaliFontName = escapeXml(config.targetBengaliFont.fontFamilyName)

        ZipOutputStream(BufferedOutputStream(FileOutputStream(outFile))).use { zos ->
            // 1. [Content_Types].xml
            val contentTypesXml = buildString {
                append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
                append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
                append("""<Default Extension="xml" ContentType="application/xml"/>""")
                if (hasImages) {
                    append("""<Default Extension="jpg" ContentType="image/jpeg"/>""")
                }
                append("""<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>""")
                append("""<Override PartName="/word/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml"/>""")
                append("""<Override PartName="/word/fontTable.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.fontTable+xml"/>""")
                append("""<Override PartName="/docProps/core.xml" ContentType="application/vnd.openxmlformats-package.core-properties+xml"/>""")
                append("""<Override PartName="/docProps/app.xml" ContentType="application/vnd.openxmlformats-officedocument.extended-properties+xml"/>""")
                append("""</Types>""")
            }
            putXmlEntry(zos, "[Content_Types].xml", contentTypesXml)

            // 2. _rels/.rels
            val rootRelsXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties" Target="docProps/core.xml"/>
  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties" Target="docProps/app.xml"/>
</Relationships>"""
            putXmlEntry(zos, "_rels/.rels", rootRelsXml)

            // 3. docProps/core.xml & docProps/app.xml
            val isoDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())
            val coreXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<cp:coreProperties xmlns:cp="http://schemas.openxmlformats.org/package/2006/metadata/core-properties"
  xmlns:dc="http://purl.org/dc/elements/1.1/"
  xmlns:dcterms="http://purl.org/dc/terms/"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <dc:title>${escapeXml(docTitle)}</dc:title>
  <dc:creator>PDF ALL IN ONE Pro+Ai</dc:creator>
  <dcterms:created xsi:type="dcterms:W3CDTF">$isoDate</dcterms:created>
  <dcterms:modified xsi:type="dcterms:W3CDTF">$isoDate</dcterms:modified>
</cp:coreProperties>"""
            putXmlEntry(zos, "docProps/core.xml", coreXml)

            val appXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Properties xmlns="http://schemas.openxmlformats.org/officeDocument/2006/extended-properties">
  <Application>PDF ALL IN ONE Pro+Ai</Application>
  <Pages>${pages.size}</Pages>
</Properties>"""
            putXmlEntry(zos, "docProps/app.xml", appXml)

            // 4. word/_rels/document.xml.rels
            val docRelsXml = buildString {
                append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
                append("""<Relationship Id="rIdStyles" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
                append("""<Relationship Id="rIdFontTable" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/fontTable" Target="fontTable.xml"/>""")
                pages.forEachIndexed { idx, page ->
                    if (page.jpegBytes != null) {
                        append("""<Relationship Id="rIdImg${idx + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="media/page_${page.pageNumber}.jpg"/>""")
                    }
                }
                append("""</Relationships>""")
            }
            putXmlEntry(zos, "word/_rels/document.xml.rels", docRelsXml)

            // 5. Write media images if requested
            pages.forEach { page ->
                val bytes = page.jpegBytes
                if (bytes != null) {
                    zos.putNextEntry(ZipEntry("word/media/page_${page.pageNumber}.jpg"))
                    zos.write(bytes)
                    zos.closeEntry()
                }
            }

            // 6. word/fontTable.xml (26 popular Bengali fonts with fallback definitions)
            val fontTableXml = BengaliTextNormalizer.buildDocxFontTableXml(config.targetBengaliFont)
            putXmlEntry(zos, "word/fontTable.xml", fontTableXml)

            // 7. word/styles.xml
            val stylesXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:docDefaults>
    <w:rPrDefault>
      <w:rPr>
        <w:rFonts w:ascii="$bengaliFontName" w:hAnsi="Calibri" w:eastAsia="$bengaliFontName" w:cs="$bengaliFontName"/>
        <w:sz w:val="22"/>
        <w:szCs w:val="24"/>
        <w:lang w:val="en-US" w:eastAsia="bn-BD" w:bidi="bn-BD"/>
      </w:rPr>
    </w:rPrDefault>
  </w:docDefaults>
  <w:style w:type="paragraph" w:default="1" w:styleId="Normal">
    <w:name w:val="Normal"/>
    <w:qFormat/>
    <w:pPr><w:spacing w:after="140" w:line="276" w:lineRule="auto"/></w:pPr>
    <w:rPr><w:rFonts w:ascii="$bengaliFontName" w:hAnsi="Calibri" w:eastAsia="$bengaliFontName" w:cs="$bengaliFontName"/></w:rPr>
  </w:style>
  <w:style w:type="paragraph" w:styleId="Heading1">
    <w:name w:val="heading 1"/>
    <w:basedOn w:val="Normal"/>
    <w:qFormat/>
    <w:pPr><w:spacing w:before="240" w:after="120"/></w:pPr>
    <w:rPr><w:rFonts w:ascii="$bengaliFontName" w:hAnsi="Calibri" w:eastAsia="$bengaliFontName" w:cs="$bengaliFontName"/><w:b/><w:color w:val="1565C0"/><w:sz w:val="32"/><w:szCs w:val="32"/></w:rPr>
  </w:style>
  <w:style w:type="paragraph" w:styleId="Heading2">
    <w:name w:val="heading 2"/>
    <w:basedOn w:val="Normal"/>
    <w:qFormat/>
    <w:pPr><w:spacing w:before="180" w:after="80"/></w:pPr>
    <w:rPr><w:rFonts w:ascii="$bengaliFontName" w:hAnsi="Calibri" w:eastAsia="$bengaliFontName" w:cs="$bengaliFontName"/><w:b/><w:color w:val="2E3B4E"/><w:sz w:val="26"/><w:szCs w:val="26"/></w:rPr>
  </w:style>
</w:styles>"""
            putXmlEntry(zos, "word/styles.xml", stylesXml)

            // 8. word/document.xml
            val documentXml = buildDocxDocumentXml(docTitle, pages, config)
            putXmlEntry(zos, "word/document.xml", documentXml)
        }
    }

    private fun buildDocxRunPropertiesXml(
        text: String,
        bengaliFontName: String,
        bold: Boolean = false
    ): String {
        val hasBn = BengaliTextNormalizer.containsBengaliUnicode(text)
        if (!hasBn && !bold) return ""
        val sb = StringBuilder("<w:rPr>")
        if (hasBn) {
            sb.append("""<w:rFonts w:ascii="$bengaliFontName" w:hAnsi="$bengaliFontName" w:eastAsia="$bengaliFontName" w:cs="$bengaliFontName"/>""")
            sb.append("""<w:lang w:val="bn-BD" w:eastAsia="bn-BD" w:bidi="bn-BD"/>""")
        }
        if (bold) {
            sb.append("<w:b/><w:bCs/>")
        }
        sb.append("</w:rPr>")
        return sb.toString()
    }

    private fun buildDocxDocumentXml(
        docTitle: String,
        pages: List<ExtractedPageData>,
        config: PdfConversionConfig
    ): String {
        val bengaliFontName = escapeXml(config.targetBengaliFont.fontFamilyName)
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append(
            """<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main" """ +
                """xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" """ +
                """xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing" """ +
                """xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" """ +
                """xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">"""
        )
        sb.append("<w:body>")

        // Document Title Heading
        val titleRPr = buildDocxRunPropertiesXml(docTitle, bengaliFontName)
        sb.append(
            """<w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr>""" +
                """<w:r>$titleRPr<w:t>${escapeXml(docTitle)}</w:t></w:r></w:p>"""
        )

        pages.forEachIndexed { pageIdx, page ->
            if (config.includePageHeaders) {
                val ocrTag = if (page.usedOcr) " (ML Kit OCR)" else ""
                sb.append(
                    """<w:p><w:pPr><w:pStyle w:val="Heading2"/></w:pPr>""" +
                        """<w:r><w:t>Page ${page.pageNumber}$ocrTag</w:t></w:r></w:p>"""
                )
            }

            // Optional embedded page snapshot image
            if (page.jpegBytes != null && page.imageWidthPx > 0 && page.imageHeightPx > 0) {
                val relId = "rIdImg${pageIdx + 1}"
                // 5.5 inches width in EMUs (1 inch = 914400 EMUs)
                val cx = 5029200L
                val cy = (cx.toDouble() * page.imageHeightPx.toDouble() / page.imageWidthPx.coerceAtLeast(1)).toLong()
                val docPrId = pageIdx + 10
                sb.append(
                    """<w:p><w:r><w:drawing><wp:inline distT="0" distB="0" distL="0" distR="0">""" +
                        """<wp:extent cx="$cx" cy="$cy"/>""" +
                        """<wp:docPr id="$docPrId" name="Page_${page.pageNumber}"/>""" +
                        """<a:graphic xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">""" +
                        """<a:graphicData uri="http://schemas.openxmlformats.org/drawingml/2006/picture">""" +
                        """<pic:pic xmlns:pic="http://schemas.openxmlformats.org/drawingml/2006/picture">""" +
                        """<pic:nvPicPr><pic:cNvPr id="$docPrId" name="page_${page.pageNumber}.jpg"/><pic:cNvPicPr/></pic:nvPicPr>""" +
                        """<pic:blipFill><a:blip r:embed="$relId"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>""" +
                        """<pic:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="$cx" cy="$cy"/></a:xfrm>""" +
                        """<a:prstGeom prst="rect"><a:avLst/></a:prstGeom></pic:spPr>""" +
                        """</pic:pic></a:graphicData></a:graphic></wp:inline></w:drawing></w:r></w:p>"""
                )
            }

            val rawLines = page.rawText.lines()
            if (rawLines.all { it.isBlank() }) {
                sb.append(
                    """<w:p><w:r><w:rPr><w:i/><w:color w:val="757575"/></w:rPr>""" +
                        """<w:t>[No text detected on Page ${page.pageNumber}]</w:t></w:r></w:p>"""
                )
            } else {
                var lineCursor = 0
                while (lineCursor < rawLines.size) {
                    val line = rawLines[lineCursor].trim()
                    if (line.isEmpty()) {
                        lineCursor++
                        continue
                    }

                    // Check if consecutive lines form a tabular block (2+ columns separated by 2+ spaces or tabs or pipes)
                    if (config.detectTablesAndHeadings && isTabularLine(line)) {
                        val tableRows = mutableListOf<List<String>>()
                        while (lineCursor < rawLines.size) {
                            val candidate = rawLines[lineCursor].trim()
                            if (candidate.isNotEmpty() && isTabularLine(candidate)) {
                                tableRows.add(splitTabularColumns(candidate))
                                lineCursor++
                            } else {
                                break
                            }
                        }
                        if (tableRows.size >= 2) {
                            appendDocxTable(sb, tableRows, bengaliFontName)
                            continue
                        } else if (tableRows.size == 1) {
                            val joined = tableRows[0].joinToString("    ")
                            val rPr = buildDocxRunPropertiesXml(joined, bengaliFontName)
                            sb.append("""<w:p><w:r>$rPr<w:t xml:space="preserve">${escapeXml(joined)}</w:t></w:r></w:p>""")
                            continue
                        }
                    }

                    // Check if line looks like a section heading
                    val isHeading = config.detectTablesAndHeadings &&
                        line.length in 3..72 &&
                        !line.endsWith(".") &&
                        (line == line.uppercase(Locale.getDefault()) || line.matches(Regex("^(Chapter|Section|Part|Article|\\d+\\.\\s+[A-Z]).*")))

                    val rPr = buildDocxRunPropertiesXml(line, bengaliFontName)
                    if (isHeading) {
                        sb.append(
                            """<w:p><w:pPr><w:pStyle w:val="Heading2"/></w:pPr>""" +
                                """<w:r>$rPr<w:t xml:space="preserve">${escapeXml(line)}</w:t></w:r></w:p>"""
                        )
                    } else {
                        sb.append(
                            """<w:p><w:r>$rPr<w:t xml:space="preserve">${escapeXml(line)}</w:t></w:r></w:p>"""
                        )
                    }
                    lineCursor++
                }
            }

            // Insert page break between pages
            if (pageIdx < pages.lastIndex) {
                sb.append("""<w:p><w:r><w:br w:type="page"/></w:r></w:p>""")
            }
        }

        sb.append(
            """<w:sectPr><w:pgSz w:w="11906" w:h="16838"/>""" +
                """<w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440"/></w:sectPr>"""
        )
        sb.append("</w:body></w:document>")
        return sb.toString()
    }

    private fun appendDocxTable(
        sb: StringBuilder,
        rows: List<List<String>>,
        bengaliFontName: String
    ) {
        val maxCols = rows.maxOfOrNull { it.size }?.coerceAtLeast(1) ?: 1
        sb.append("<w:tbl>")
        sb.append(
            """<w:tblPr><w:tblW w:w="5000" w:type="pct"/>""" +
                """<w:tblBorders>""" +
                """<w:top w:val="single" w:sz="4" w:space="0" w:color="BDBDBD"/>""" +
                """<w:left w:val="single" w:sz="4" w:space="0" w:color="BDBDBD"/>""" +
                """<w:bottom w:val="single" w:sz="4" w:space="0" w:color="BDBDBD"/>""" +
                """<w:right w:val="single" w:sz="4" w:space="0" w:color="BDBDBD"/>""" +
                """<w:insideH w:val="single" w:sz="4" w:space="0" w:color="E0E0E0"/>""" +
                """<w:insideV w:val="single" w:sz="4" w:space="0" w:color="E0E0E0"/>""" +
                """</w:tblBorders></w:tblPr>"""
        )
        rows.forEachIndexed { rowIdx, cols ->
            sb.append("<w:tr>")
            for (c in 0 until maxCols) {
                val cellText = cols.getOrNull(c)?.trim().orEmpty()
                val rPr = buildDocxRunPropertiesXml(cellText, bengaliFontName, bold = (rowIdx == 0))
                sb.append(
                    """<w:tc><w:tcPr><w:tcW w:w="${5000 / maxCols}" w:type="pct"/></w:tcPr>""" +
                        """<w:p><w:r>$rPr<w:t xml:space="preserve">${escapeXml(cellText)}</w:t></w:r></w:p></w:tc>"""
                )
            }
            sb.append("</w:tr>")
        }
        sb.append("</w:tbl>")
    }

    // =========================================================================
    // 2. MS Excel (.xlsx) — OpenXML SpreadsheetML Generator
    // =========================================================================
    private fun writeXlsxPackage(
        outFile: File,
        docTitle: String,
        pages: List<ExtractedPageData>,
        config: PdfConversionConfig
    ) {
        val spreadsheetFontName = escapeXml(config.targetBengaliFont.fontFamilyName)
        ZipOutputStream(BufferedOutputStream(FileOutputStream(outFile))).use { zos ->
            val contentTypesXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>"""
            putXmlEntry(zos, "[Content_Types].xml", contentTypesXml)

            val rootRelsXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""
            putXmlEntry(zos, "_rels/.rels", rootRelsXml)

            val safeSheetName = docTitle.replace(Regex("[\\\\/?*\\[\\]:]"), " ").take(28).ifBlank { "PDF_Data" }
            val workbookXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
  xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="${escapeXml(safeSheetName)}" sheetId="1" r:id="rId1"/>
  </sheets>
</workbook>"""
            putXmlEntry(zos, "xl/workbook.xml", workbookXml)

            val wbRelsXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""
            putXmlEntry(zos, "xl/_rels/workbook.xml.rels", wbRelsXml)

            val stylesXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="2">
    <font><sz val="11"/><name val="$spreadsheetFontName"/></font>
    <font><b/><sz val="11"/><name val="$spreadsheetFontName"/></font>
  </fonts>
  <fills count="2">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
  </fills>
  <borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
  <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
  <cellXfs count="2">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>
  </cellXfs>
</styleSheet>"""
            putXmlEntry(zos, "xl/styles.xml", stylesXml)

            // Build worksheet rows
            val sheetSb = StringBuilder()
            sheetSb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            sheetSb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
            sheetSb.append("<sheetData>")

            var rowNumber = 1
            // Header row
            sheetSb.append("""<row r="$rowNumber">""")
            sheetSb.append(xlsxCell(rowNumber, 0, "Page", styleIdx = 1))
            sheetSb.append(xlsxCell(rowNumber, 1, "Line #", styleIdx = 1))
            sheetSb.append(xlsxCell(rowNumber, 2, "Column 1 / Content", styleIdx = 1))
            sheetSb.append(xlsxCell(rowNumber, 3, "Column 2", styleIdx = 1))
            sheetSb.append(xlsxCell(rowNumber, 4, "Column 3", styleIdx = 1))
            sheetSb.append(xlsxCell(rowNumber, 5, "Column 4", styleIdx = 1))
            sheetSb.append("</row>")
            rowNumber++

            for (page in pages) {
                val nonEmptyLines = page.rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }
                if (nonEmptyLines.isEmpty()) {
                    sheetSb.append("""<row r="$rowNumber">""")
                    sheetSb.append(xlsxCell(rowNumber, 0, "Page ${page.pageNumber}"))
                    sheetSb.append(xlsxCell(rowNumber, 1, "1"))
                    sheetSb.append(xlsxCell(rowNumber, 2, "[Empty / Image Page]"))
                    sheetSb.append("</row>")
                    rowNumber++
                } else {
                    nonEmptyLines.forEachIndexed { lineIdx, line ->
                        val cols = if (config.detectTablesAndHeadings && isTabularLine(line)) {
                            splitTabularColumns(line)
                        } else {
                            listOf(line)
                        }
                        sheetSb.append("""<row r="$rowNumber">""")
                        sheetSb.append(xlsxCell(rowNumber, 0, "Page ${page.pageNumber}"))
                        sheetSb.append(xlsxCell(rowNumber, 1, (lineIdx + 1).toString()))
                        cols.take(12).forEachIndexed { colOffset, colText ->
                            sheetSb.append(xlsxCell(rowNumber, colOffset + 2, colText))
                        }
                        sheetSb.append("</row>")
                        rowNumber++
                    }
                }
            }

            sheetSb.append("</sheetData></worksheet>")
            putXmlEntry(zos, "xl/worksheets/sheet1.xml", sheetSb.toString())
        }
    }

    private fun xlsxCell(rowNumber: Int, zeroBasedCol: Int, text: String, styleIdx: Int = 0): String {
        val colName = excelColumnName(zeroBasedCol)
        val styleAttr = if (styleIdx > 0) """ s="$styleIdx"""" else ""
        return """<c r="$colName$rowNumber" t="inlineStr"$styleAttr><is><t xml:space="preserve">${escapeXml(text)}</t></is></c>"""
    }

    private fun excelColumnName(zeroBasedCol: Int): String {
        var c = zeroBasedCol
        val sb = StringBuilder()
        while (c >= 0) {
            sb.insert(0, ('A'.code + (c % 26)).toChar())
            c = (c / 26) - 1
        }
        return sb.toString()
    }

    // =========================================================================
    // 3. MS PowerPoint (.pptx) — OpenXML PresentationML Generator
    // =========================================================================
    private fun writePptxPackage(
        outFile: File,
        docTitle: String,
        pages: List<ExtractedPageData>,
        config: PdfConversionConfig
    ) {
        val pptxFontName = escapeXml(config.targetBengaliFont.fontFamilyName)
        ZipOutputStream(BufferedOutputStream(FileOutputStream(outFile))).use { zos ->
            // [Content_Types].xml
            val contentTypes = buildString {
                append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
                append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
                append("""<Default Extension="xml" ContentType="application/xml"/>""")
                append("""<Override PartName="/ppt/presentation.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml"/>""")
                append("""<Override PartName="/ppt/slideMasters/slideMaster1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml"/>""")
                append("""<Override PartName="/ppt/slideLayouts/slideLayout1.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slideLayout+xml"/>""")
                for (i in pages.indices) {
                    append("""<Override PartName="/ppt/slides/slide${i + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.presentationml.slide+xml"/>""")
                }
                append("""</Types>""")
            }
            putXmlEntry(zos, "[Content_Types].xml", contentTypes)

            // _rels/.rels
            val rootRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="ppt/presentation.xml"/>
</Relationships>"""
            putXmlEntry(zos, "_rels/.rels", rootRels)

            // ppt/presentation.xml
            val presXml = buildString {
                append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                append("""<p:presentation xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">""")
                append("""<p:sldMasterIdLst><p:sldMasterId id="2147483648" r:id="rIdMaster1"/></p:sldMasterIdLst>""")
                append("""<p:sldIdLst>""")
                for (i in pages.indices) {
                    append("""<p:sldId id="${256 + i}" r:id="rIdSlide${i + 1}"/>""")
                }
                append("""</p:sldIdLst>""")
                append("""<p:sldSz cx="12192000" cy="6858000"/>""")
                append("""<p:notesSz cx="6858000" cy="9144000"/>""")
                append("""</p:presentation>""")
            }
            putXmlEntry(zos, "ppt/presentation.xml", presXml)

            // ppt/_rels/presentation.xml.rels
            val presRels = buildString {
                append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
                append("""<Relationship Id="rIdMaster1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster" Target="slideMasters/slideMaster1.xml"/>""")
                for (i in pages.indices) {
                    append("""<Relationship Id="rIdSlide${i + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" Target="slides/slide${i + 1}.xml"/>""")
                }
                append("""</Relationships>""")
            }
            putXmlEntry(zos, "ppt/_rels/presentation.xml.rels", presRels)

            // Slide Master & Layout
            val slideMasterXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sldMaster xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
  <p:cSld><p:spTree><p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/></p:spTree></p:cSld>
  <p:clrMap bg1="lt1" tx1="dk1" bg2="lt2" tx2="dk2" accent1="accent1" accent2="accent2" accent3="accent3" accent4="accent4" accent5="accent5" accent6="accent6" hlink="hlink" folHlink="folHlink"/>
  <p:sldLayoutIdLst><p:sldLayoutId id="2147483649" r:id="rIdLayout1"/></p:sldLayoutIdLst>
</p:sldMaster>"""
            putXmlEntry(zos, "ppt/slideMasters/slideMaster1.xml", slideMasterXml)

            val slideMasterRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rIdLayout1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/>
</Relationships>"""
            putXmlEntry(zos, "ppt/slideMasters/_rels/slideMaster1.xml.rels", slideMasterRels)

            val slideLayoutXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<p:sldLayout xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" type="blank">
  <p:cSld><p:spTree><p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/></p:spTree></p:cSld>
</p:sldLayout>"""
            putXmlEntry(zos, "ppt/slideLayouts/slideLayout1.xml", slideLayoutXml)

            val slideLayoutRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rIdMaster1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster" Target="../slideMasters/slideMaster1.xml"/>
</Relationships>"""
            putXmlEntry(zos, "ppt/slideLayouts/_rels/slideLayout1.xml.rels", slideLayoutRels)

            // Individual slides
            val slideRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rIdLayout1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout" Target="../slideLayouts/slideLayout1.xml"/>
</Relationships>"""

            pages.forEachIndexed { idx, page ->
                val slideNum = idx + 1
                val lines = page.rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }
                val slideTitle = if (config.includePageHeaders) {
                    "$docTitle — Page ${page.pageNumber}"
                } else {
                    lines.firstOrNull()?.take(80) ?: "Slide $slideNum"
                }
                val bodyLines = if (lines.isEmpty()) {
                    listOf("[Scanned / Image Page ${page.pageNumber}]")
                } else {
                    lines.take(18)
                }

                val slideXml = buildString {
                    append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                    append("""<p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">""")
                    append("""<p:cSld><p:spTree>""")
                    append("""<p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr/>""")
                    // Title Shape
                    append("""<p:sp><p:nvSpPr><p:cNvPr id="2" name="Title"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr>""")
                    append("""<p:spPr><a:xfrm><a:off x="685800" y="457200"/><a:ext cx="10820400" cy="914400"/></a:xfrm></p:spPr>""")
                    append("""<p:txBody><a:bodyPr/><a:lstStyle/>""")
                    append("""<a:p><a:r><a:rPr lang="bn-BD" altLang="en-US" sz="2800" b="1"><a:latin typeface="$pptxFontName"/><a:ea typeface="$pptxFontName"/><a:cs typeface="$pptxFontName"/></a:rPr><a:t>${escapeXml(slideTitle)}</a:t></a:r></a:p>""")
                    append("""</p:txBody></p:sp>""")

                    // Body Content Shape
                    append("""<p:sp><p:nvSpPr><p:cNvPr id="3" name="Content"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr>""")
                    append("""<p:spPr><a:xfrm><a:off x="685800" y="1524000"/><a:ext cx="10820400" cy="4876800"/></a:xfrm></p:spPr>""")
                    append("""<p:txBody><a:bodyPr wrap="square"/><a:lstStyle/>""")
                    for (bLine in bodyLines) {
                        append("""<a:p><a:r><a:rPr lang="bn-BD" altLang="en-US" sz="1600"><a:latin typeface="$pptxFontName"/><a:ea typeface="$pptxFontName"/><a:cs typeface="$pptxFontName"/></a:rPr><a:t>${escapeXml(bLine)}</a:t></a:r></a:p>""")
                    }
                    append("""</p:txBody></p:sp>""")
                    append("""</p:spTree></p:cSld></p:sld>""")
                }
                putXmlEntry(zos, "ppt/slides/slide$slideNum.xml", slideXml)
                putXmlEntry(zos, "ppt/slides/_rels/slide$slideNum.xml.rels", slideRels)
            }
        }
    }

    // =========================================================================
    // 4. Rich Text Format (.rtf) Generator (Full Unicode + 26 Bengali Fonts support)
    // =========================================================================
    private fun writeRtfDocument(
        outFile: File,
        docTitle: String,
        pages: List<ExtractedPageData>,
        config: PdfConversionConfig
    ) {
        val primaryBnFont = config.targetBengaliFont.fontFamilyName
        val fallbackBnFont = config.targetBengaliFont.fallbackFontName
        val sb = StringBuilder()
        sb.append("{\\rtf1\\ansi\\ansicpg1252\\uc1\\deff1")
        sb.append("{\\fonttbl{\\f0\\fswiss\\fcharset0 Calibri;}{\\f1\\fnil\\fcharset0 $primaryBnFont;}{\\f2\\fnil\\fcharset0 $fallbackBnFont;}{\\f3\\fnil\\fcharset0 Kalpurush;}{\\f4\\fnil\\fcharset0 SolaimanLipi;}{\\f5\\fnil\\fcharset0 Nikosh;}}")
        sb.append("{\\colortbl ;\\red21\\green101\\blue192;\\red46\\green59\\blue78;}\n")
        sb.append("\\viewkind4\\uc1\\pard\\sa200\\sl276\\slmult1\\f1\\fs36\\b\\cf1 ")
        sb.append(escapeRtfUnicode(docTitle))
        sb.append("\\b0\\cf0\\fs22\\par\n")

        pages.forEachIndexed { idx, page ->
            if (config.includePageHeaders) {
                val ocrLabel = if (page.usedOcr) " (ML Kit OCR)" else ""
                sb.append("\\pard\\sa140\\f0\\b\\cf2\\fs26 ")
                sb.append(escapeRtfUnicode("Page ${page.pageNumber}$ocrLabel"))
                sb.append("\\b0\\cf0\\fs22\\par\n")
            }

            val lines = page.rawText.lines()
            if (lines.all { it.isBlank() }) {
                sb.append("\\pard\\sa120\\f0\\i ")
                sb.append(escapeRtfUnicode("[No text detected on Page ${page.pageNumber}]"))
                sb.append("\\i0\\par\n")
            } else {
                for (line in lines) {
                    val trimmed = line.trimEnd()
                    sb.append("\\pard\\sa100\\f1 ")
                    sb.append(escapeRtfUnicode(trimmed))
                    sb.append("\\par\n")
                }
            }

            if (idx < pages.lastIndex) {
                sb.append("\\page\n")
            }
        }
        sb.append("}")
        outFile.writeText(sb.toString(), Charsets.UTF_8)
    }

    private fun escapeRtfUnicode(input: String): String {
        val sb = StringBuilder(input.length * 2)
        for (ch in input) {
            when (ch) {
                '\\' -> sb.append("\\\\")
                '{' -> sb.append("\\{")
                '}' -> sb.append("\\}")
                '\n' -> sb.append("\\line ")
                '\t' -> sb.append("\\tab ")
                else -> {
                    if (ch.code in 32..126) {
                        sb.append(ch)
                    } else {
                        // Signed 16-bit integer for RTF \uN?
                        val signedCode = ch.code.toShort()
                        sb.append("\\u").append(signedCode.toInt()).append('?')
                    }
                }
            }
        }
        return sb.toString()
    }

    // =========================================================================
    // 5. CSV Spreadsheet (.csv) Generator
    // =========================================================================
    private fun writeCsvDocument(
        outFile: File,
        pages: List<ExtractedPageData>,
        config: PdfConversionConfig
    ) {
        val sb = StringBuilder()
        // UTF-8 BOM so Microsoft Excel opens Unicode/Bengali properly
        sb.append('\uFEFF')
        sb.append("Page,Line,Column 1,Column 2,Column 3,Column 4\r\n")

        for (page in pages) {
            val nonEmpty = page.rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (nonEmpty.isEmpty()) {
                sb.append("${page.pageNumber},1,\"[Empty / Scanned Page]\"\r\n")
            } else {
                nonEmpty.forEachIndexed { lineIdx, line ->
                    val cols = if (config.detectTablesAndHeadings && isTabularLine(line)) {
                        splitTabularColumns(line)
                    } else {
                        listOf(line)
                    }
                    sb.append("${page.pageNumber},${lineIdx + 1}")
                    for (c in cols) {
                        sb.append(',').append(escapeCsvField(c))
                    }
                    sb.append("\r\n")
                }
            }
        }
        outFile.writeText(sb.toString(), Charsets.UTF_8)
    }

    private fun escapeCsvField(value: String): String {
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }

    // =========================================================================
    // 6. Responsive Styled Web Page (.html) Generator
    // =========================================================================
    private fun writeHtmlDocument(
        outFile: File,
        docTitle: String,
        pages: List<ExtractedPageData>,
        config: PdfConversionConfig
    ) {
        val primaryBnFont = escapeXml(config.targetBengaliFont.fontFamilyName)
        val fallbackBnFont = escapeXml(config.targetBengaliFont.fallbackFontName)
        val sb = StringBuilder()
        sb.append(
            """<!DOCTYPE html>
<html lang="bn">
<head>
<meta charset="UTF-8"/>
<meta name="viewport" content="width=device-width, initial-scale=1.0"/>
<title>${escapeXml(docTitle)}</title>
<style>
  body { font-family: "$primaryBnFont", "$fallbackBnFont", "Kalpurush", "SolaimanLipi", "Nikosh", "Siyam Rupali", "Noto Sans Bengali", "Hind Siliguri", -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: #F4F6F9; color: #1E293B; margin: 0; padding: 24px; line-height: 1.7; }
  .container { max-width: 860px; margin: 0 auto; }
  h1.doc-title { color: #0F172A; font-size: 1.8rem; margin-bottom: 16px; }
  .page-card { background: #FFFFFF; border-radius: 14px; padding: 28px; margin-bottom: 24px; box-shadow: 0 2px 10px rgba(15, 23, 42, 0.07); }
  .page-header { font-size: 0.9rem; font-weight: 700; color: #1565C0; text-transform: uppercase; letter-spacing: 0.05em; border-bottom: 1px solid #E2E8F0; padding-bottom: 8px; margin-bottom: 16px; }
  .page-img { max-width: 100%; height: auto; border-radius: 8px; border: 1px solid #E2E8F0; margin-bottom: 16px; display: block; }
  table.pdf-table { width: 100%; border-collapse: collapse; margin: 14px 0; font-size: 0.95rem; }
  table.pdf-table td, table.pdf-table th { border: 1px solid #CBD5E1; padding: 8px 12px; text-align: left; }
  table.pdf-table tr:first-child { background: #F1F5F9; font-weight: 600; }
  p { margin: 0 0 10px 0; white-space: pre-wrap; }
</style>
</head>
<body>
<div class="container">
<h1 class="doc-title">${escapeXml(docTitle)}</h1>
"""
        )

        for (page in pages) {
            sb.append("""<section class="page-card" id="page-${page.pageNumber}">""")
            if (config.includePageHeaders) {
                val ocrTag = if (page.usedOcr) " • ML Kit OCR" else ""
                sb.append("""<div class="page-header">Page ${page.pageNumber}$ocrTag</div>""")
            }
            if (page.jpegBytes != null) {
                val b64 = Base64.encodeToString(page.jpegBytes, Base64.NO_WRAP)
                sb.append("""<img class="page-img" src="data:image/jpeg;base64,$b64" alt="Page ${page.pageNumber}"/>""")
            }
            val lines = page.rawText.lines()
            if (lines.all { it.isBlank() }) {
                sb.append("""<p><em>[No text detected on Page ${page.pageNumber}]</em></p>""")
            } else {
                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty()) {
                        sb.append("<p>${escapeXml(trimmed)}</p>\n")
                    }
                }
            }
            sb.append("</section>\n")
        }

        sb.append("</div>\n</body>\n</html>")
        outFile.writeText(sb.toString(), Charsets.UTF_8)
    }

    // =========================================================================
    // 7. Markdown (.md) Generator
    // =========================================================================
    private fun writeMarkdownDocument(
        outFile: File,
        docTitle: String,
        pages: List<ExtractedPageData>,
        config: PdfConversionConfig
    ) {
        val sb = StringBuilder()
        sb.append("# ").append(docTitle).append("\n\n")

        pages.forEachIndexed { idx, page ->
            if (config.includePageHeaders) {
                val ocrNote = if (page.usedOcr) " *(ML Kit OCR)*" else ""
                sb.append("## Page ${page.pageNumber}$ocrNote\n\n")
            }
            if (page.rawText.isBlank()) {
                sb.append("_[No text detected on Page ${page.pageNumber}]_\n\n")
            } else {
                sb.append(page.rawText.trim()).append("\n\n")
            }
            if (idx < pages.lastIndex) {
                sb.append("---\n\n")
            }
        }
        outFile.writeText(sb.toString(), Charsets.UTF_8)
    }

    // =========================================================================
    // 8. Plain Text (.txt) Generator
    // =========================================================================
    private fun writePlainTextDocument(
        outFile: File,
        docTitle: String,
        pages: List<ExtractedPageData>,
        config: PdfConversionConfig
    ) {
        val sb = StringBuilder()
        sb.append(docTitle).append("\n")
        sb.append("=".repeat(docTitle.length.coerceIn(10, 60))).append("\n\n")

        pages.forEachIndexed { idx, page ->
            if (config.includePageHeaders) {
                val ocrNote = if (page.usedOcr) " [ML Kit OCR]" else ""
                sb.append("--- Page ${page.pageNumber}$ocrNote ---\n")
            }
            if (page.rawText.isBlank()) {
                sb.append("[No text detected on Page ${page.pageNumber}]\n")
            } else {
                sb.append(page.rawText.trim()).append("\n")
            }
            if (idx < pages.lastIndex) {
                sb.append("\n")
            }
        }
        outFile.writeText(sb.toString(), Charsets.UTF_8)
    }

    // =========================================================================
    // Helper Utilities
    // =========================================================================
    private fun isTabularLine(line: String): Boolean {
        if (line.contains("\t") || line.contains(" | ")) return true
        // 2 or more chunks separated by 3+ spaces
        return line.split(Regex("\\s{3,}")).filter { it.isNotBlank() }.size >= 2
    }

    private fun splitTabularColumns(line: String): List<String> {
        return when {
            line.contains("\t") -> line.split("\t").map { it.trim() }.filter { it.isNotEmpty() }
            line.contains(" | ") -> line.split("|").map { it.trim() }.filter { it.isNotEmpty() }
            else -> line.split(Regex("\\s{3,}")).map { it.trim() }.filter { it.isNotEmpty() }
        }
    }

    private fun putXmlEntry(zos: ZipOutputStream, entryPath: String, xmlContent: String) {
        zos.putNextEntry(ZipEntry(entryPath))
        zos.write(xmlContent.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
    }

    private fun escapeXml(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            // Strip illegal XML 1.0 control characters
            val code = ch.code
            val validXmlChar = code == 0x9 || code == 0xA || code == 0xD ||
                (code in 0x20..0xD7FF) || (code in 0xE000..0xFFFD)
            if (!validXmlChar) continue
            when (ch) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&apos;")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    private fun sanitizeBaseFileName(raw: String): String {
        val cleaned = raw.trim().replace(Regex("[^a-zA-Z0-9._\\- ]"), "_").trim()
        return cleaned.ifBlank { "Converted_Document" }
    }
}
