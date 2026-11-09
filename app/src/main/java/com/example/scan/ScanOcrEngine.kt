package com.example.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.example.pdf.BengaliFontOption
import com.example.pdf.BengaliTextNormalizer
import com.example.pdf.PdfBoxMemoryUtil
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.coroutines.resume
import kotlin.math.min

data class OcrTextLine(
    val text: String,
    val leftNorm: Float,
    val topNorm: Float,
    val rightNorm: Float,
    val bottomNorm: Float
)

data class OcrTextBlock(
    val text: String,
    val leftNorm: Float,
    val topNorm: Float,
    val rightNorm: Float,
    val bottomNorm: Float,
    val lines: List<OcrTextLine> = emptyList()
)

data class OcrPageResult(
    val fullText: String,
    val words: List<OcrWordBox>,
    val lines: List<OcrTextLine> = emptyList(),
    val blocks: List<OcrTextBlock> = emptyList(),
    val languagePackUsed: OcrLanguagePack = OcrLanguagePack.AUTO,
    val detectedLanguageLabel: String = ""
)

class ScanOcrEngine(private val context: Context) {

    val languagePackManager = OcrLanguagePackManager(context)

    /**
     * Directory inside app internal storage where scans are saved (`filesDir/Scans`).
     */
    fun getScansDirectory(): File {
        val dir = File(context.filesDir, "Scans")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Lists all saved scans (.pdf, .jpg, .png, .zip) from `filesDir/Scans` sorted newest first.
     */
    fun listSavedScans(): List<SavedScanRecord> {
        val dir = getScansDirectory()
        val files = dir.listFiles { f ->
            f.isFile && (
                f.name.endsWith(".pdf", ignoreCase = true) ||
                    f.name.endsWith(".jpg", ignoreCase = true) ||
                    f.name.endsWith(".jpeg", ignoreCase = true) ||
                    f.name.endsWith(".png", ignoreCase = true) ||
                    f.name.endsWith(".zip", ignoreCase = true)
                )
        } ?: return emptyList()

        return files
            .sortedByDescending { it.lastModified() }
            .map { file ->
                SavedScanRecord(
                    file = file,
                    name = file.name,
                    sizeBytes = file.length(),
                    lastModified = file.lastModified()
                )
            }
    }

    fun renameSavedScan(file: File, newNameInput: String): File? {
        val sanitized = newNameInput.trim().replace(Regex("[^a-zA-Z0-9._\\- ]"), "_")
        if (sanitized.isBlank()) return null
        val origExt = file.extension.ifBlank { "pdf" }
        val finalName = if (sanitized.endsWith(".$origExt", ignoreCase = true)) {
            sanitized
        } else {
            "$sanitized.$origExt"
        }
        val target = File(getScansDirectory(), finalName)
        return if (file.renameTo(target)) target else null
    }

    fun deleteSavedScan(file: File): Boolean {
        return try {
            file.delete()
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Runs on-device ML Kit TextRecognition on an already-rendered Bitmap (does not recycle caller's bitmap),
     * using the specified [languagePack] (or the currently active language pack in [languagePackManager]).
     */
    suspend fun recognizeTextFromBitmap(
        bitmap: Bitmap,
        languagePack: OcrLanguagePack? = null
    ): OcrPageResult = withContext(Dispatchers.IO) {
        val effectivePack = languagePack ?: languagePackManager.selectedLanguagePack.value
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
            return@withContext OcrPageResult("", emptyList(), languagePackUsed = effectivePack)
        }
        try {
            val w = bitmap.width.toFloat().coerceAtLeast(1f)
            val h = bitmap.height.toFloat().coerceAtLeast(1f)
            val inputImage = InputImage.fromBitmap(bitmap, 0)

            val candidateEngines = languagePackManager.resolveActiveEnginesForOcr(effectivePack)
            var bestVisionText: Text? = null
            for (engine in candidateEngines) {
                val visionResult = runMlKitTextRecognizer(inputImage, engine)
                if (visionResult != null && visionResult.text.isNotBlank()) {
                    if (bestVisionText == null || visionResult.text.length > bestVisionText.text.length) {
                        bestVisionText = visionResult
                    }
                    // If a specific non-auto language was requested and produced substantial text, use it immediately
                    if (effectivePack != OcrLanguagePack.AUTO && visionResult.text.length >= 12) {
                        break
                    }
                }
            }

            val finalVisionText = bestVisionText
                ?: return@withContext OcrPageResult(
                    fullText = "",
                    words = emptyList(),
                    languagePackUsed = effectivePack,
                    detectedLanguageLabel = languagePackManager.detectRecognizedLanguageLabel("", effectivePack)
                )

            parseVisionTextToOcrResult(finalVisionText, w, h, effectivePack)
        } catch (e: Exception) {
            OcrPageResult("", emptyList(), languagePackUsed = effectivePack)
        }
    }

    /**
     * Renders a specific page of a PDF (from File or Uri) and extracts selectable text using ML Kit Text Recognition
     * and the selected [languagePack], with automatic multi-script & Bengali/Bijoy normalization.
     */
    suspend fun recognizeTextFromPdfPage(
        pdfFile: File?,
        uri: Uri?,
        pageIndex: Int,
        targetWidth: Int = 1440,
        languagePack: OcrLanguagePack? = null
    ): OcrPageResult = withContext(Dispatchers.IO) {
        val effectivePack = languagePack ?: languagePackManager.selectedLanguagePack.value

        // 1. Check if the PDF page already contains embedded Unicode/non-English or Bijoy glyphs
        val embeddedResult = extractEmbeddedMultiLanguagePdfText(pdfFile, uri, pageIndex, effectivePack)
        if (embeddedResult != null &&
            embeddedResult.fullText.isNotBlank() &&
            (BengaliTextNormalizer.containsBengaliUnicode(embeddedResult.fullText) ||
                containsNonAsciiScriptCharacters(embeddedResult.fullText) ||
                effectivePack != OcrLanguagePack.ENGLISH)
        ) {
            // If embedded text has non-ASCII characters (Bengali, Devanagari, Arabic, CJK, Cyrillic, European accents), return it directly
            if (containsNonAsciiScriptCharacters(embeddedResult.fullText)) {
                return@withContext embeddedResult
            }
        }

        // 2. Render page bitmap for on-device ML Kit OCR
        val bmp = renderPdfPageForOcr(pdfFile, uri, pageIndex, targetWidth)
            ?: return@withContext (embeddedResult ?: OcrPageResult("", emptyList(), languagePackUsed = effectivePack))
        try {
            val ocrRes = recognizeTextFromBitmap(bmp, effectivePack)
            if (ocrRes.fullText.isBlank() && embeddedResult != null && embeddedResult.fullText.isNotBlank()) {
                return@withContext embeddedResult
            }
            val normalizedFull = languagePackManager.postProcessRecognizedText(ocrRes.fullText, effectivePack)
            val detectedLabel = languagePackManager.detectRecognizedLanguageLabel(normalizedFull, effectivePack)
            ocrRes.copy(
                fullText = normalizedFull,
                languagePackUsed = effectivePack,
                detectedLanguageLabel = detectedLabel
            )
        } finally {
            try { bmp.recycle() } catch (_: Exception) {}
        }
    }

    private fun containsNonAsciiScriptCharacters(text: String): Boolean {
        return text.any { it.code > 127 }
    }

    /**
     * Inspects a PDF page via PDFBox for embedded text (including Bengali, Devanagari, Arabic, CJK, Cyrillic,
     * European accents, or legacy Bijoy/ANSI fonts like SutonnyMJ) and normalizes it with bounding boxes.
     */
    private fun extractEmbeddedMultiLanguagePdfText(
        pdfFile: File?,
        uri: Uri?,
        pageIndex: Int,
        languagePack: OcrLanguagePack
    ): OcrPageResult? {
        var pdDoc: PDDocument? = null
        return try {
            pdDoc = PdfBoxMemoryUtil.loadDocument(context, pdfFile, uri) ?: return null
            if (pageIndex !in 0 until pdDoc.numberOfPages) return null
            val page = pdDoc.getPage(pageIndex)
            val mediaBox = page.mediaBox ?: PDRectangle.A4
            val pageW = mediaBox.width.coerceAtLeast(1f)
            val pageH = mediaBox.height.coerceAtLeast(1f)
            val hasBijoyFont = BengaliTextNormalizer.hasLegacyBijoyFontsOnPage(page)

            val lines = mutableListOf<OcrTextLine>()
            val words = mutableListOf<OcrWordBox>()

            val stripper = object : PDFTextStripper() {
                init {
                    sortByPosition = true
                    startPage = pageIndex + 1
                    endPage = pageIndex + 1
                }

                override fun writeString(text: String?, textPositions: MutableList<TextPosition>?) {
                    super.writeString(text, textPositions)
                    if (text.isNullOrBlank() || textPositions.isNullOrEmpty()) return
                    val step1 = if (hasBijoyFont || BengaliTextNormalizer.containsBengaliUnicode(text)) {
                        BengaliTextNormalizer.normalizeExtractedPdfText(
                            rawText = text.trim(),
                            pageHasBijoyFont = hasBijoyFont,
                            fixBrokenMatrasAndNuktas = true,
                            convertBijoyAnsiToUnicode = true
                        )
                    } else {
                        text.trim()
                    }
                    val normalizedLine = languagePackManager.postProcessRecognizedText(step1, languagePack).trim()
                    if (normalizedLine.isEmpty()) return

                    var minX = Float.MAX_VALUE
                    var minY = Float.MAX_VALUE
                    var maxX = -Float.MAX_VALUE
                    var maxY = -Float.MAX_VALUE
                    for (pos in textPositions) {
                        val x = pos.xDirAdj
                        val y = pos.yDirAdj
                        val w = pos.widthDirAdj.coerceAtLeast(2f)
                        val h = pos.heightDir.coerceAtLeast(pos.fontSizeInPt * 0.85f).coerceAtLeast(6f)
                        if (x < minX) minX = x
                        if (y - h < minY) minY = y - h
                        if (x + w > maxX) maxX = x + w
                        if (y + h * 0.2f > maxY) maxY = y + h * 0.2f
                    }
                    if (maxX > minX && maxY > minY) {
                        val lNorm = (minX / pageW).coerceIn(0f, 0.99f)
                        val tNorm = (minY / pageH).coerceIn(0f, 0.99f)
                        val rNorm = (maxX / pageW).coerceIn(lNorm + 0.01f, 1f)
                        val bNorm = (maxY / pageH).coerceIn(tNorm + 0.01f, 1f)
                        lines.add(OcrTextLine(normalizedLine, lNorm, tNorm, rNorm, bNorm))

                        val tokenList = normalizedLine.split(Regex("\\s+")).filter { it.isNotEmpty() }
                        if (tokenList.isNotEmpty()) {
                            val spanW = (rNorm - lNorm) / tokenList.size
                            tokenList.forEachIndexed { idx, token ->
                                val wl = (lNorm + idx * spanW).coerceIn(0f, 0.99f)
                                val wr = (wl + spanW).coerceIn(wl + 0.005f, 1f)
                                words.add(OcrWordBox(token, wl, tNorm, wr, bNorm))
                            }
                        }
                    }
                }
            }

            val rawFull = stripper.getText(pdDoc)?.trim().orEmpty().ifBlank {
                lines.joinToString("\n") { it.text }.trim()
            }
            if (rawFull.isEmpty()) return null
            val step1Full = if (hasBijoyFont || BengaliTextNormalizer.containsBengaliUnicode(rawFull)) {
                BengaliTextNormalizer.normalizeExtractedPdfText(
                    rawText = rawFull,
                    pageHasBijoyFont = hasBijoyFont,
                    fixBrokenMatrasAndNuktas = true,
                    convertBijoyAnsiToUnicode = true
                )
            } else {
                rawFull
            }
            val normalizedFull = languagePackManager.postProcessRecognizedText(step1Full, languagePack).trim()
            val blocks = if (lines.isNotEmpty()) {
                listOf(
                    OcrTextBlock(
                        text = normalizedFull,
                        leftNorm = lines.minOf { it.leftNorm },
                        topNorm = lines.minOf { it.topNorm },
                        rightNorm = lines.maxOf { it.rightNorm },
                        bottomNorm = lines.maxOf { it.bottomNorm },
                        lines = lines
                    )
                )
            } else {
                emptyList()
            }
            OcrPageResult(
                fullText = normalizedFull,
                words = words,
                lines = lines,
                blocks = blocks,
                languagePackUsed = languagePack,
                detectedLanguageLabel = languagePackManager.detectRecognizedLanguageLabel(normalizedFull, languagePack)
            )
        } catch (_: Throwable) {
            null
        } finally {
            try { pdDoc?.close() } catch (_: Exception) {}
        }
    }

    /**
     * Returns total page count of a PDF file or Uri using PdfRenderer with PDFBox fallback.
     */
    suspend fun getPdfPageCount(pdfFile: File?, uri: Uri?): Int = withContext(Dispatchers.IO) {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        var tempCopy: File? = null
        val rendererCount = try {
            pfd = openPdfParcelFileDescriptor(pdfFile, uri) { tempCopy = it }
            if (pfd != null) {
                renderer = PdfRenderer(pfd)
                renderer.pageCount
            } else {
                0
            }
        } catch (_: Throwable) {
            0
        } finally {
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
            try { tempCopy?.delete() } catch (_: Exception) {}
        }

        if (rendererCount > 0) {
            return@withContext rendererCount
        }

        // Fallback to PDFBox if native PdfRenderer returned 0 or is unavailable
        var doc: PDDocument? = null
        try {
            doc = PdfBoxMemoryUtil.loadDocument(context, pdfFile, uri)
            doc?.numberOfPages ?: 0
        } catch (_: Throwable) {
            0
        } finally {
            try { doc?.close() } catch (_: Exception) {}
        }
    }

    private fun openPdfParcelFileDescriptor(
        pdfFile: File?,
        uri: Uri?,
        onTempCreated: (File) -> Unit
    ): ParcelFileDescriptor? {
        if (pdfFile != null && pdfFile.exists()) {
            return ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
        }
        if (uri != null) {
            try {
                context.contentResolver.openFileDescriptor(uri, "r")?.let { return it }
            } catch (_: Exception) {
            }
            val temp = File(context.cacheDir, "ocr_tmp_${System.currentTimeMillis()}.pdf")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(temp).use { out -> input.copyTo(out) }
            }
            if (temp.exists() && temp.length() > 0L) {
                onTempCreated(temp)
                return ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY)
            }
        }
        return null
    }

    private fun renderPdfPageForOcr(
        pdfFile: File?,
        uri: Uri?,
        pageIndex: Int,
        targetWidth: Int
    ): Bitmap? {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        var tempCopy: File? = null
        val renderedByNative = try {
            pfd = openPdfParcelFileDescriptor(pdfFile, uri) { tempCopy = it }
            if (pfd != null) {
                renderer = PdfRenderer(pfd)
                if (pageIndex in 0 until renderer.pageCount) {
                    renderer.openPage(pageIndex).use { page ->
                        val aspect = page.height.toFloat() / page.width.toFloat().coerceAtLeast(1f)
                        val w = targetWidth.coerceIn(720, 2048)
                        val h = (w * aspect).toInt().coerceIn(720, 2800)
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bmp
                    }
                } else {
                    null
                }
            } else {
                null
            }
        } catch (oom: OutOfMemoryError) {
            System.gc()
            null
        } catch (_: Throwable) {
            null
        } finally {
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
            try { tempCopy?.delete() } catch (_: Exception) {}
        }

        if (renderedByNative != null) return renderedByNative

        // Fallback to PDFBox renderer if native PdfRenderer failed
        var doc: PDDocument? = null
        return try {
            doc = PdfBoxMemoryUtil.loadDocument(context, pdfFile, uri) ?: return null
            if (pageIndex !in 0 until doc.numberOfPages) return null
            val pdfBoxRenderer = com.tom_roush.pdfbox.rendering.PDFRenderer(doc)
            pdfBoxRenderer.renderImageWithDPI(pageIndex, 144f, com.tom_roush.pdfbox.rendering.ImageType.RGB)
        } catch (oom: OutOfMemoryError) {
            System.gc()
            null
        } catch (_: Throwable) {
            null
        } finally {
            try { doc?.close() } catch (_: Exception) {}
        }
    }

    private fun parseVisionTextToOcrResult(
        visionText: Text,
        width: Float,
        height: Float,
        languagePack: OcrLanguagePack
    ): OcrPageResult {
        val w = width.coerceAtLeast(1f)
        val h = height.coerceAtLeast(1f)
        val wordBoxes = mutableListOf<OcrWordBox>()
        val lineList = mutableListOf<OcrTextLine>()
        val blockList = mutableListOf<OcrTextBlock>()

        for (block in visionText.textBlocks) {
            val blockLines = mutableListOf<OcrTextLine>()
            for (line in block.lines) {
                val lineRect = line.boundingBox
                val lineText = languagePackManager.postProcessRecognizedText(line.text.trim(), languagePack)
                if (lineRect != null && lineText.isNotBlank()) {
                    val ocrLine = OcrTextLine(
                        text = lineText,
                        leftNorm = (lineRect.left / w).coerceIn(0f, 1f),
                        topNorm = (lineRect.top / h).coerceIn(0f, 1f),
                        rightNorm = (lineRect.right / w).coerceIn(0f, 1f),
                        bottomNorm = (lineRect.bottom / h).coerceIn(0f, 1f)
                    )
                    lineList.add(ocrLine)
                    blockLines.add(ocrLine)
                }
                for (element in line.elements) {
                    val rect = element.boundingBox
                    val text = languagePackManager.postProcessRecognizedText(element.text, languagePack)
                    if (rect != null && text.isNotBlank()) {
                        wordBoxes.add(
                            OcrWordBox(
                                text = text,
                                leftNorm = (rect.left / w).coerceIn(0f, 1f),
                                topNorm = (rect.top / h).coerceIn(0f, 1f),
                                rightNorm = (rect.right / w).coerceIn(0f, 1f),
                                bottomNorm = (rect.bottom / h).coerceIn(0f, 1f)
                            )
                        )
                    }
                }
            }
            val blockRect = block.boundingBox
            val blockText = languagePackManager.postProcessRecognizedText(block.text.trim(), languagePack)
            if (blockRect != null && blockText.isNotBlank()) {
                blockList.add(
                    OcrTextBlock(
                        text = blockText,
                        leftNorm = (blockRect.left / w).coerceIn(0f, 1f),
                        topNorm = (blockRect.top / h).coerceIn(0f, 1f),
                        rightNorm = (blockRect.right / w).coerceIn(0f, 1f),
                        bottomNorm = (blockRect.bottom / h).coerceIn(0f, 1f),
                        lines = blockLines
                    )
                )
            }
        }

        val fullNormalized = languagePackManager.postProcessRecognizedText(visionText.text ?: "", languagePack)
        return OcrPageResult(
            fullText = fullNormalized,
            words = wordBoxes,
            lines = lineList,
            blocks = blockList,
            languagePackUsed = languagePack,
            detectedLanguageLabel = languagePackManager.detectRecognizedLanguageLabel(fullNormalized, languagePack)
        )
    }

    /**
     * Runs on-device ML Kit OCR on a single image File using the selected [languagePack].
     */
    suspend fun recognizeTextFromFile(
        imageFile: File,
        languagePack: OcrLanguagePack? = null
    ): OcrPageResult = withContext(Dispatchers.IO) {
        val bmp = ScanImageProcessor.decodeSafeBitmap(context, null, imageFile, maxDim = 2048)
            ?: return@withContext OcrPageResult("", emptyList())

        try {
            recognizeTextFromBitmap(bmp, languagePack)
        } finally {
            try { bmp.recycle() } catch (ignored: Exception) {}
        }
    }

    /**
     * Runs on-device ML Kit OCR on any gallery image Uri using the selected [languagePack].
     */
    suspend fun recognizeTextFromUri(
        uri: Uri,
        languagePack: OcrLanguagePack? = null
    ): OcrPageResult = withContext(Dispatchers.IO) {
        val bmp = ScanImageProcessor.decodeSafeBitmap(context, uri, null, maxDim = 2048)
            ?: return@withContext OcrPageResult("", emptyList())

        try {
            recognizeTextFromBitmap(bmp, languagePack)
        } finally {
            try { bmp.recycle() } catch (ignored: Exception) {}
        }
    }

    private suspend fun runMlKitTextRecognizer(
        inputImage: InputImage,
        engine: OcrScriptEngine = OcrScriptEngine.LATIN
    ): Text? =
        suspendCancellableCoroutine { cont ->
            try {
                val recognizer = languagePackManager.createMlKitRecognizerForEngine(engine)
                recognizer.process(inputImage)
                    .addOnSuccessListener { result ->
                        try { recognizer.close() } catch (_: Exception) {}
                        if (cont.isActive) cont.resume(result)
                    }
                    .addOnFailureListener {
                        try { recognizer.close() } catch (_: Exception) {}
                        if (cont.isActive) cont.resume(null)
                    }
            } catch (e: Exception) {
                if (cont.isActive) cont.resume(null)
            }
        }

    /**
     * Embeds an invisible searchable/selectable OCR text layer into an existing scanned PDF document
     * using ML Kit TextRecognition (with multi-language & embedded TrueType Bengali font support)
     * and saves the resulting searchable PDF.
     */
    suspend fun embedSearchableOcrIntoPdf(
        sourceFile: File?,
        sourceUri: Uri?,
        outputFileName: String,
        cachedPageResults: Map<Int, OcrPageResult> = emptyMap(),
        bengaliFont: BengaliFontOption = BengaliFontOption.NOTO_SANS_BENGALI,
        languagePack: OcrLanguagePack? = null,
        onProgress: (fraction: Float, message: String) -> Unit = { _, _ -> }
    ): File = withContext(Dispatchers.IO) {
        val scansDir = getScansDirectory()
        val sanitized = outputFileName.trim().ifBlank { "Searchable_Document" }
            .replace(Regex("[^a-zA-Z0-9._\\- ]"), "_")
        val finalName = if (sanitized.endsWith(".pdf", true)) sanitized else "${sanitized}_searchable.pdf"
        val outFile = File(scansDir, finalName)

        val pdDocument = PdfBoxMemoryUtil.loadDocument(context, sourceFile, sourceUri)
            ?: throw IllegalStateException("Unable to load PDF document for OCR embedding")

        try {
            var loadedUnicodeFont: PDFont? = null
            fun getUnicodeFont(): PDFont {
                if (loadedUnicodeFont == null) {
                    loadedUnicodeFont = bengaliFont.loadPdfBoxFont(context, pdDocument)
                }
                return loadedUnicodeFont ?: PDType1Font.HELVETICA
            }

            val totalPages = pdDocument.numberOfPages.coerceAtLeast(1)
            for (pageIdx in 0 until totalPages) {
                currentCoroutineContext().ensureActive()
                onProgress(
                    pageIdx.toFloat() / totalPages,
                    "Extracting & embedding OCR text on page ${pageIdx + 1} of $totalPages..."
                )
                val ocrResult = cachedPageResults[pageIdx]
                    ?: recognizeTextFromPdfPage(sourceFile, sourceUri, pageIndex = pageIdx, languagePack = languagePack)
                if (ocrResult.words.isNotEmpty()) {
                    val page = pdDocument.getPage(pageIdx)
                    val mediaBox = page.mediaBox ?: PDRectangle.A4
                    val contentStream = PDPageContentStream(
                        pdDocument,
                        page,
                        PDPageContentStream.AppendMode.APPEND,
                        true,
                        true
                    )
                    try {
                        writeInvisibleOcrTextLayer(
                            contentStream = contentStream,
                            words = ocrResult.words,
                            drawX = mediaBox.lowerLeftX,
                            drawY = mediaBox.lowerLeftY,
                            drawW = mediaBox.width,
                            drawH = mediaBox.height,
                            unicodeFontProvider = ::getUnicodeFont
                        )
                    } finally {
                        contentStream.close()
                    }
                }
            }
            onProgress(0.95f, "Saving searchable PDF...")
            pdDocument.save(outFile)
            onProgress(1.0f, "Searchable PDF saved!")
            outFile
        } finally {
            try { pdDocument.close() } catch (_: Exception) {}
        }
    }

    /**
     * Compiles scanned pages into a PDF (and optionally a searchable PDF with an invisible text layer
     * over each page image using PdfBox-Android) and saves it to the internal Scans folder.
     */
    suspend fun exportScanToPdf(
        pages: List<ScannedPageItem>,
        documentTitle: String,
        pageSize: ScanExportPageSize,
        jpegQuality: Float, // 0.5f..0.95f
        enableSearchableOcr: Boolean,
        captureMode: ScanCaptureMode,
        languagePack: OcrLanguagePack? = null,
        onProgress: (fraction: Float, message: String) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val scansDir = getScansDirectory()
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val baseTitle = documentTitle.trim().ifBlank { "Scan_$timeStamp" }
            .replace(Regex("[^a-zA-Z0-9._\\- ]"), "_")
        val fileName = if (baseTitle.endsWith(".pdf", true)) baseTitle else "$baseTitle.pdf"
        val outFile = File(scansDir, fileName)

        // If ID_CARD mode and 2+ pages exist, combine pairs (front & back) onto single A4 pages
        val effectivePages: List<ScannedPageItem> = if (captureMode == ScanCaptureMode.ID_CARD && pages.size >= 2) {
            val combinedList = mutableListOf<ScannedPageItem>()
            var idx = 0
            while (idx < pages.size) {
                if (idx + 1 < pages.size) {
                    val combinedFile = ScanImageProcessor.combineIdCardFrontAndBack(
                        frontFile = pages[idx].processedFile,
                        backFile = pages[idx + 1].processedFile,
                        cacheDir = context.cacheDir
                    )
                    combinedList.add(
                        pages[idx].copy(
                            processedFile = combinedFile,
                            recognizedText = "",
                            ocrWords = emptyList()
                        )
                    )
                    idx += 2
                } else {
                    combinedList.add(pages[idx])
                    idx += 1
                }
            }
            combinedList
        } else {
            pages
        }

        val doc = PDDocument()
        try {
            var loadedUnicodeFont: PDFont? = null
            fun getUnicodeFont(): PDFont {
                if (loadedUnicodeFont == null) {
                    loadedUnicodeFont = BengaliFontOption.NOTO_SANS_BENGALI.loadPdfBoxFont(context, doc)
                }
                return loadedUnicodeFont ?: PDType1Font.HELVETICA
            }

            val total = effectivePages.size.coerceAtLeast(1)

            effectivePages.forEachIndexed { index, item ->
                currentCoroutineContext().ensureActive()
                val baseProgress = index.toFloat() / total

                // 1. Run OCR if requested and not yet recognized
                val ocrWords: List<OcrWordBox> = if (enableSearchableOcr) {
                    onProgress(baseProgress + 0.05f / total, "Running OCR on page ${index + 1} of $total...")
                    if (item.ocrWords.isNotEmpty()) {
                        item.ocrWords
                    } else {
                        recognizeTextFromFile(item.processedFile, languagePack).words
                    }
                } else {
                    emptyList()
                }

                onProgress(baseProgress + 0.5f / total, "Building PDF page ${index + 1} of $total...")

                val bmp = ScanImageProcessor.decodeSafeBitmap(
                    context = context,
                    uri = null,
                    file = item.processedFile,
                    maxDim = 2048
                ) ?: return@forEachIndexed

                try {
                    val imgW = bmp.width.toFloat()
                    val imgH = bmp.height.toFloat()

                    val pdRect: PDRectangle = when (pageSize) {
                        ScanExportPageSize.A4 -> PDRectangle.A4
                        ScanExportPageSize.LETTER -> PDRectangle.LETTER
                        ScanExportPageSize.FIT_IMAGE -> PDRectangle(imgW, imgH)
                    }

                    val pdPage = PDPage(pdRect)
                    doc.addPage(pdPage)

                    val pageW = pdRect.width
                    val pageH = pdRect.height

                    val scale = min(pageW / imgW, pageH / imgH)
                    val drawW = imgW * scale
                    val drawH = imgH * scale
                    val drawX = (pageW - drawW) / 2f
                    val drawY = (pageH - drawH) / 2f

                    val pdImage = JPEGFactory.createFromImage(doc, bmp, jpegQuality.coerceIn(0.4f, 0.95f))

                    val contentStream = PDPageContentStream(doc, pdPage)
                    try {
                        // Draw page raster scan image
                        contentStream.drawImage(pdImage, drawX, drawY, drawW, drawH)

                        // Overlay invisible searchable text layer if OCR enabled
                        if (enableSearchableOcr && ocrWords.isNotEmpty()) {
                            writeInvisibleOcrTextLayer(
                                contentStream = contentStream,
                                words = ocrWords,
                                drawX = drawX,
                                drawY = drawY,
                                drawW = drawW,
                                drawH = drawH,
                                unicodeFontProvider = ::getUnicodeFont
                            )
                        }
                    } finally {
                        contentStream.close()
                    }
                } finally {
                    bmp.recycle()
                }
            }

            onProgress(0.95f, "Writing searchable PDF to Scans folder...")
            doc.save(outFile)
            onProgress(1.0f, "Scan saved!")
            outFile
        } finally {
            try { doc.close() } catch (ignored: Exception) {}
        }
    }

    /**
     * Writes an invisible PDF text layer (`RenderingMode.NEITHER`) aligned with the bounding boxes
     * of each recognized word so the PDF is selectable and searchable in any PDF viewer.
     * Supports both Latin (PDType1Font.HELVETICA) and Bengali/Unicode (embedded TrueType PDType0Font).
     */
    private fun writeInvisibleOcrTextLayer(
        contentStream: PDPageContentStream,
        words: List<OcrWordBox>,
        drawX: Float,
        drawY: Float,
        drawW: Float,
        drawH: Float,
        unicodeFontProvider: (() -> PDFont)? = null
    ) {
        val latinFont = PDType1Font.HELVETICA
        contentStream.setRenderingMode(RenderingMode.NEITHER)

        for (word in words) {
            val rawWord = word.text.trim()
            if (rawWord.isEmpty()) continue

            val needsUnicodeFont = rawWord.any { it.code > 255 }
            val activeFont: PDFont = if (needsUnicodeFont && unicodeFontProvider != null) {
                unicodeFontProvider()
            } else {
                latinFont
            }

            val safeText = if (needsUnicodeFont && activeFont !== latinFont) {
                BengaliTextNormalizer.fixUnicodeBengaliOrderAndNuktas(rawWord)
            } else {
                sanitizePdfBoxLatinText(rawWord)
            }
            if (safeText.isBlank()) continue

            val boxLeft = drawX + word.leftNorm * drawW
            val boxRight = drawX + word.rightNorm * drawW
            val boxTopPdf = drawY + (1f - word.topNorm) * drawH
            val boxBottomPdf = drawY + (1f - word.bottomNorm) * drawH

            val boxHeight = (boxTopPdf - boxBottomPdf).coerceAtLeast(6f)
            val boxWidth = (boxRight - boxLeft).coerceAtLeast(6f)

            // Estimate font size so text width matches bounding box
            val rawFontWidth = try {
                (activeFont.getStringWidth(safeText) / 1000f).coerceAtLeast(0.2f)
            } catch (e: Exception) {
                (safeText.length * 0.5f).coerceAtLeast(0.2f)
            }
            val fontSizeByWidth = boxWidth / rawFontWidth
            val fontSize = min(boxHeight * 0.85f, fontSizeByWidth).coerceIn(4f, 48f)

            try {
                contentStream.beginText()
                contentStream.setFont(activeFont, fontSize)
                contentStream.newLineAtOffset(boxLeft, boxBottomPdf + boxHeight * 0.15f)
                contentStream.showText(safeText)
                contentStream.endText()
            } catch (ignored: Exception) {
                try { contentStream.endText() } catch (ignoredEnd: Exception) {}
            }
        }
    }

    /**
     * Filters characters to WinAnsi-compatible ASCII/Latin-1 range for PDType1Font.HELVETICA.
     */
    private fun sanitizePdfBoxLatinText(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            if (ch.code in 32..126 || ch.code in 160..255) {
                sb.append(ch)
            } else {
                sb.append(' ')
            }
        }
        return sb.toString().trim()
    }

    /**
     * Exports scanned pages as individual JPEG images (or a ZIP archive if multiple pages) inside Scans folder.
     */
    suspend fun exportScanAsImages(
        pages: List<ScannedPageItem>,
        baseName: String
    ): File = withContext(Dispatchers.IO) {
        val scansDir = getScansDirectory()
        val cleanName = baseName.trim().ifBlank { "Scan_Images" }
            .replace(Regex("[^a-zA-Z0-9._\\- ]"), "_")

        if (pages.size == 1) {
            val outImg = File(scansDir, "${cleanName}_page1.jpg")
            pages[0].processedFile.copyTo(outImg, overwrite = true)
            return@withContext outImg
        }

        val zipFile = File(scansDir, "${cleanName}_images.zip")
        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
            pages.forEachIndexed { idx, page ->
                val entryName = "${cleanName}_page_${idx + 1}.jpg"
                zos.putNextEntry(ZipEntry(entryName))
                FileInputStream(page.processedFile).use { fis ->
                    fis.copyTo(zos)
                }
                zos.closeEntry()
            }
        }
        zipFile
    }
}
