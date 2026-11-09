package com.example.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PdfOperationEngine(private val context: Context) {

    private fun ensurePdfBoxReady() {
        if (!com.tom_roush.pdfbox.android.PDFBoxResourceLoader.isReady()) {
            com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context.applicationContext)
        }
    }

    private fun openUriInputStream(uri: Uri): InputStream? {
        return if (uri.scheme == "file" && uri.path != null) {
            val file = File(uri.path!!)
            if (file.exists()) FileInputStream(file) else null
        } else {
            context.contentResolver.openInputStream(uri)
        }
    }

    private fun openUriFileDescriptor(uri: Uri): ParcelFileDescriptor? {
        return if (uri.scheme == "file" && uri.path != null) {
            val file = File(uri.path!!)
            if (file.exists()) ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY) else null
        } else {
            context.contentResolver.openFileDescriptor(uri, "r")
        }
    }

    /**
     * 1. Merge multiple PDFs and Images into a single PDF.
     */
    suspend fun mergeDocuments(
        items: List<MergeItem>,
        outputName: String = "Merged_Document.pdf",
        onProgress: (fraction: Float, step: String) -> Unit
    ): File = withContext(Dispatchers.IO) {
        ensurePdfBoxReady()
        val tempOut = File(context.cacheDir, "merge_${UUID.randomUUID()}.pdf")
        val mergedDoc = PDDocument()
        val openSourceDocs = mutableListOf<PDDocument>()
        val openStreams = mutableListOf<InputStream>()

        try {
            val totalItems = items.size.coerceAtLeast(1)
            items.forEachIndexed { itemIndex, item ->
                currentCoroutineContext().ensureActive()
                val stepFraction = itemIndex.toFloat() / totalItems
                onProgress(stepFraction, "Processing ${item.fileName} (${itemIndex + 1}/$totalItems)")

                if (item.isImage) {
                    // Add image as a page
                    val bitmap = decodeBitmapSafely(item.uri, maxDimension = 2400)
                    if (bitmap != null) {
                        try {
                            val pdImage = JPEGFactory.createFromImage(mergedDoc, bitmap, 0.88f)
                            val imgW = pdImage.width.toFloat()
                            val imgH = pdImage.height.toFloat()

                            // Fit onto A4 with margin
                            val page = PDPage(PDRectangle.A4)
                            mergedDoc.addPage(page)

                            val margin = 36f
                            val availW = (page.mediaBox.width - margin * 2).coerceAtLeast(50f)
                            val availH = (page.mediaBox.height - margin * 2).coerceAtLeast(50f)

                            val scale = minOf(availW / imgW, availH / imgH, 1.0f)
                            val drawW = imgW * scale
                            val drawH = imgH * scale
                            val drawX = margin + (availW - drawW) / 2f
                            val drawY = margin + (availH - drawH) / 2f

                            val cs = PDPageContentStream(mergedDoc, page)
                            cs.drawImage(pdImage, drawX, drawY, drawW, drawH)
                            cs.close()
                        } finally {
                            bitmap.recycle()
                        }
                    }
                } else {
                    // Import pages from PDF (keep srcDoc open until mergedDoc.save completes)
                    val inputStream = openUriInputStream(item.uri)
                    if (inputStream != null) {
                        openStreams.add(inputStream)
                        val srcDoc = PdfBoxMemoryUtil.loadFromStream(context, inputStream)
                        openSourceDocs.add(srcDoc)
                        val pageIndices = parsePageRanges(item.pageRangeText, srcDoc.numberOfPages)
                        val flatPages = if (pageIndices.isEmpty()) (0 until srcDoc.numberOfPages).toList() else pageIndices.flatten()

                        for (pageIdx in flatPages) {
                            currentCoroutineContext().ensureActive()
                            if (pageIdx in 0 until srcDoc.numberOfPages) {
                                val srcPage = srcDoc.getPage(pageIdx)
                                mergedDoc.importPage(srcPage)
                            }
                        }
                    }
                }
            }

            onProgress(0.95f, "Writing final PDF file...")
            mergedDoc.save(tempOut)
            onProgress(1.0f, "Merge complete!")
            tempOut
        } finally {
            try { mergedDoc.close() } catch (_: Exception) {}
            openSourceDocs.forEach { doc -> try { doc.close() } catch (_: Exception) {} }
            openStreams.forEach { stream -> try { stream.close() } catch (_: Exception) {} }
        }
    }

    /**
     * 2. Split PDF by page ranges, every N pages, or bookmarks.
     */
    suspend fun splitPdf(
        sourceUri: Uri,
        sourceName: String,
        splitMode: SplitMode,
        destination: SplitDestination,
        folderTreeUri: Uri? = null,
        onProgress: (fraction: Float, step: String) -> Unit
    ): Pair<List<File>, File?> = withContext(Dispatchers.IO) {
        ensurePdfBoxReady()
        val inputStream = openUriInputStream(sourceUri)
            ?: throw IllegalArgumentException("Cannot open document: $sourceUri")

        val srcDoc = PdfBoxMemoryUtil.loadFromStream(context, inputStream)
        val generatedFiles = mutableListOf<File>()
        var zipResultFile: File? = null

        try {
            val totalPages = srcDoc.numberOfPages
            val baseName = sourceName.removeSuffix(".pdf")

            // Determine page groupings for split
            val segments: List<Pair<String, List<Int>>> = when (splitMode) {
                is SplitMode.ByRanges -> {
                    val ranges = parsePageRanges(splitMode.rangeExpression, totalPages)
                    ranges.mapIndexed { idx, pageGroup ->
                        val label = if (pageGroup.size == 1) "page_${pageGroup[0] + 1}" else "pages_${pageGroup.first() + 1}-${pageGroup.last() + 1}"
                        "${baseName}_part_${idx + 1}_$label" to pageGroup
                    }
                }
                is SplitMode.EveryNPages -> {
                    val n = splitMode.n.coerceAtLeast(1)
                    val list = mutableListOf<Pair<String, List<Int>>>()
                    var start = 0
                    var partIdx = 1
                    while (start < totalPages) {
                        val end = minOf(start + n, totalPages)
                        val group = (start until end).toList()
                        list.add("${baseName}_part_${partIdx}_pages_${start + 1}-$end" to group)
                        start += n
                        partIdx++
                    }
                    list
                }
                is SplitMode.ByBookmarks -> {
                    extractBookmarkSegments(srcDoc, baseName)
                }
            }

            if (segments.isEmpty()) {
                throw IllegalStateException("No split segments were generated for the specified criteria.")
            }

            val totalSegments = segments.size
            segments.forEachIndexed { segIdx, (partTitle, pages) ->
                currentCoroutineContext().ensureActive()
                val frac = segIdx.toFloat() / totalSegments
                onProgress(frac, "Extracting $partTitle (${segIdx + 1}/$totalSegments)")

                val partDoc = PDDocument()
                try {
                    for (p in pages) {
                        if (p in 0 until totalPages) {
                            partDoc.importPage(srcDoc.getPage(p))
                        }
                    }

                    val partFile = File(context.cacheDir, "${partTitle}_${UUID.randomUUID().toString().take(6)}.pdf")
                    partDoc.save(partFile)
                    generatedFiles.add(partFile)

                    // If user selected folder destination, write directly via DocumentFile
                    if (destination == SplitDestination.FOLDER_TREE && folderTreeUri != null) {
                        writeToDocumentFolder(folderTreeUri, "$partTitle.pdf", partFile)
                    }
                } finally {
                    partDoc.close()
                }
            }

            // Package into ZIP if destination is ZIP
            if (destination == SplitDestination.ZIP_ARCHIVE) {
                onProgress(0.9f, "Creating ZIP archive...")
                val zipFile = File(context.cacheDir, "${baseName}_split_${System.currentTimeMillis()}.zip")
                zipFiles(generatedFiles, zipFile)
                zipResultFile = zipFile
            }

            onProgress(1.0f, "Split complete!")
            Pair(generatedFiles, zipResultFile)
        } finally {
            srcDoc.close()
            inputStream.close()
        }
    }

    /**
     * 3. Extract selected pages into a new clean PDF.
     */
    suspend fun extractPages(
        sourceUri: Uri,
        pageIndices: List<Int>,
        outputName: String = "Extracted_Pages.pdf",
        onProgress: (fraction: Float, step: String) -> Unit
    ): File = withContext(Dispatchers.IO) {
        ensurePdfBoxReady()
        val inputStream = openUriInputStream(sourceUri)
            ?: throw IllegalArgumentException("Cannot open document: $sourceUri")

        val srcDoc = PdfBoxMemoryUtil.loadFromStream(context, inputStream)
        val outDoc = PDDocument()
        val tempOut = File(context.cacheDir, "extracted_${UUID.randomUUID()}.pdf")

        try {
            val total = pageIndices.size.coerceAtLeast(1)
            pageIndices.forEachIndexed { index, p ->
                currentCoroutineContext().ensureActive()
                onProgress(index.toFloat() / total, "Copying page ${p + 1} (${index + 1}/$total)")
                if (p in 0 until srcDoc.numberOfPages) {
                    outDoc.importPage(srcDoc.getPage(p))
                }
            }
            onProgress(0.95f, "Writing extracted PDF...")
            outDoc.save(tempOut)
            onProgress(1.0f, "Extraction complete!")
            tempOut
        } finally {
            outDoc.close()
            srcDoc.close()
            inputStream.close()
        }
    }

    /**
     * 4. Convert multiple images to a PDF.
     */
    suspend fun imagesToPdf(
        imageUris: List<Uri>,
        pageSizePreset: PageSizePreset,
        marginPreset: PageMarginPreset,
        orientationPreset: PageOrientationPreset,
        qualityPreset: ImageQualityPreset,
        onProgress: (fraction: Float, step: String) -> Unit
    ): File = withContext(Dispatchers.IO) {
        ensurePdfBoxReady()
        val tempOut = File(context.cacheDir, "images_to_pdf_${UUID.randomUUID()}.pdf")
        val doc = PDDocument()

        try {
            val total = imageUris.size.coerceAtLeast(1)
            imageUris.forEachIndexed { idx, uri ->
                currentCoroutineContext().ensureActive()
                onProgress(idx.toFloat() / total, "Converting image ${idx + 1}/$total")

                val bitmap = decodeBitmapSafely(uri, qualityPreset.maxDimension)
                if (bitmap != null) {
                    try {
                        val imgW = bitmap.width.toFloat()
                        val imgH = bitmap.height.toFloat()

                        // Determine page dimensions
                        val isImageLandscape = imgW > imgH
                        val orientation = when (orientationPreset) {
                            PageOrientationPreset.PORTRAIT -> false
                            PageOrientationPreset.LANDSCAPE -> true
                            PageOrientationPreset.AUTO -> isImageLandscape
                        }

                        val baseRect = pageSizePreset.rectangle
                        val (pageW, pageH) = if (baseRect != null) {
                            val w = if (orientation) maxOf(baseRect.width, baseRect.height) else minOf(baseRect.width, baseRect.height)
                            val h = if (orientation) minOf(baseRect.width, baseRect.height) else maxOf(baseRect.width, baseRect.height)
                            Pair(w, h)
                        } else {
                            // Fit to image at 72 pt/inch
                            Pair(imgW, imgH)
                        }

                        val page = PDPage(PDRectangle(pageW, pageH))
                        doc.addPage(page)

                        val margin = marginPreset.marginPoints
                        val availW = (pageW - margin * 2).coerceAtLeast(20f)
                        val availH = (pageH - margin * 2).coerceAtLeast(20f)

                        val scale = minOf(availW / imgW, availH / imgH)
                        val drawW = imgW * scale
                        val drawH = imgH * scale
                        val drawX = margin + (availW - drawW) / 2f
                        val drawY = margin + (availH - drawH) / 2f

                        val qualityFloat = qualityPreset.compressionQuality / 100f
                        val pdImage = JPEGFactory.createFromImage(doc, bitmap, qualityFloat)

                        val cs = PDPageContentStream(doc, page)
                        cs.drawImage(pdImage, drawX, drawY, drawW, drawH)
                        cs.close()
                    } finally {
                        bitmap.recycle()
                    }
                }
            }

            onProgress(0.95f, "Writing PDF file...")
            doc.save(tempOut)
            onProgress(1.0f, "Conversion complete!")
            tempOut
        } finally {
            doc.close()
        }
    }

    /**
     * 5. Convert PDF pages to JPG or PNG images at chosen DPI.
     */
    suspend fun pdfToImages(
        sourceUri: Uri,
        sourceName: String,
        pageIndices: List<Int>,
        format: ExportImageFormat,
        dpi: ExportDpi,
        destination: SplitDestination,
        folderTreeUri: Uri? = null,
        onProgress: (fraction: Float, step: String) -> Unit
    ): Pair<List<File>, File?> = withContext(Dispatchers.IO) {
        val pfd = openUriFileDescriptor(sourceUri)
            ?: throw IllegalArgumentException("Cannot open document: $sourceUri")

        val renderer = PdfRenderer(pfd)
        val generatedFiles = mutableListOf<File>()
        var zipResultFile: File? = null

        val baseName = sourceName.removeSuffix(".pdf")
        val scale = dpi.dpi / 72f

        try {
            val total = pageIndices.size.coerceAtLeast(1)
            pageIndices.forEachIndexed { idx, pageIdx ->
                currentCoroutineContext().ensureActive()
                onProgress(idx.toFloat() / total, "Rendering page ${pageIdx + 1} (${idx + 1}/$total)")

                if (pageIdx in 0 until renderer.pageCount) {
                    val page = renderer.openPage(pageIdx)
                    val width = (page.width * scale).toInt().coerceAtLeast(1)
                    val height = (page.height * scale).toInt().coerceAtLeast(1)

                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()

                    val fileName = "${baseName}_page_${String.format(java.util.Locale.US, "%03d", pageIdx + 1)}.${format.extension}"
                    val imgFile = File(context.cacheDir, "${UUID.randomUUID()}_$fileName")
                    val fos = FileOutputStream(imgFile)
                    val compressFormat = if (format == ExportImageFormat.PNG) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                    val compressQuality = if (format == ExportImageFormat.PNG) 100 else 92

                    bitmap.compress(compressFormat, compressQuality, fos)
                    fos.flush()
                    fos.close()
                    bitmap.recycle()

                    generatedFiles.add(imgFile)

                    if (destination == SplitDestination.FOLDER_TREE && folderTreeUri != null) {
                        writeToDocumentFolder(folderTreeUri, fileName, imgFile, format.mimeType)
                    }
                }
            }

            if (destination == SplitDestination.ZIP_ARCHIVE) {
                onProgress(0.9f, "Creating ZIP archive of images...")
                val zipFile = File(context.cacheDir, "${baseName}_images_${System.currentTimeMillis()}.zip")
                zipFiles(generatedFiles, zipFile)
                zipResultFile = zipFile
            }

            onProgress(1.0f, "Image export complete!")
            Pair(generatedFiles, zipResultFile)
        } finally {
            renderer.close()
            pfd.close()
        }
    }

    /**
     * 6. Crop a specific PDF page using a custom normalized bounding box (0f..1f),
     * or optionally apply the same crop to all pages.
     */
    suspend fun cropCustomBoundingBox(
        sourceUri: Uri,
        pageIndex: Int,
        normalizedBox: NormalizedRect,
        applyToAllPages: Boolean = false,
        onProgress: (fraction: Float, step: String) -> Unit = { _, _ -> }
    ): File = withContext(Dispatchers.IO) {
        ensurePdfBoxReady()
        val inputStream = openUriInputStream(sourceUri)
            ?: throw IllegalArgumentException("Cannot open document: $sourceUri")

        val doc = PdfBoxMemoryUtil.loadFromStream(context, inputStream)
        val tempOut = File(context.cacheDir, "cropped_box_${UUID.randomUUID()}.pdf")

        try {
            val total = doc.numberOfPages
            val pagesToCrop = if (applyToAllPages) (0 until total).toList() else listOf(pageIndex.coerceIn(0, (total - 1).coerceAtLeast(0)))

            for ((stepIdx, pIdx) in pagesToCrop.withIndex()) {
                currentCoroutineContext().ensureActive()
                onProgress(stepIdx.toFloat() / pagesToCrop.size, "Cropping page ${pIdx + 1}/$total")

                val page = doc.getPage(pIdx)
                val media = page.mediaBox ?: PDRectangle.A4
                val rotation = (page.rotation % 360 + 360) % 360

                var pdfLeft = media.lowerLeftX + normalizedBox.left * media.width
                var pdfRight = media.lowerLeftX + normalizedBox.right * media.width
                var pdfBottom = media.lowerLeftY + (1f - normalizedBox.bottom) * media.height
                var pdfTop = media.lowerLeftY + (1f - normalizedBox.top) * media.height

                when (rotation) {
                    90 -> {
                        pdfLeft = media.lowerLeftX + normalizedBox.top * media.width
                        pdfRight = media.lowerLeftX + normalizedBox.bottom * media.width
                        pdfBottom = media.lowerLeftY + (1f - normalizedBox.right) * media.height
                        pdfTop = media.lowerLeftY + (1f - normalizedBox.left) * media.height
                    }
                    180 -> {
                        pdfLeft = media.lowerLeftX + (1f - normalizedBox.right) * media.width
                        pdfRight = media.lowerLeftX + (1f - normalizedBox.left) * media.width
                        pdfBottom = media.lowerLeftY + normalizedBox.top * media.height
                        pdfTop = media.lowerLeftY + normalizedBox.bottom * media.height
                    }
                    270 -> {
                        pdfBottom = media.lowerLeftY + normalizedBox.left * media.height
                        pdfTop = media.lowerLeftY + normalizedBox.right * media.height
                        pdfLeft = media.lowerLeftX + (1f - normalizedBox.bottom) * media.width
                        pdfRight = media.lowerLeftX + (1f - normalizedBox.top) * media.width
                    }
                }

                val finalLeft = minOf(pdfLeft, pdfRight)
                val finalRight = maxOf(pdfLeft, pdfRight)
                val finalBottom = minOf(pdfBottom, pdfTop)
                val finalTop = maxOf(pdfBottom, pdfTop)

                val cropW = (finalRight - finalLeft).coerceAtLeast(10f)
                val cropH = (finalTop - finalBottom).coerceAtLeast(10f)

                val newCrop = PDRectangle(finalLeft, finalBottom, cropW, cropH)
                page.cropBox = newCrop
                page.mediaBox = newCrop
            }

            onProgress(0.95f, "Saving cropped PDF...")
            doc.save(tempOut)
            onProgress(1.0f, "Crop complete!")
            tempOut
        } finally {
            doc.close()
            inputStream.close()
        }
    }

    /**
     * 7. Save reorganized pages into a new PDF document.
     */
    suspend fun saveOrganizedPages(
        originalUri: Uri,
        pages: List<OrganizedPage>,
        originalCachedFile: File? = null,
        onProgress: (fraction: Float, step: String) -> Unit
    ): File = withContext(Dispatchers.IO) {
        if (!com.tom_roush.pdfbox.android.PDFBoxResourceLoader.isReady()) {
            com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context.applicationContext)
        }
        val outDoc = PDDocument()
        val tempOut = File(context.cacheDir, "organized_${UUID.randomUUID()}.pdf")

        // Cache open PDDocuments for imported source URIs
        val openDocs = mutableMapOf<Uri, PDDocument>()

        try {
            val originalDoc = if (originalCachedFile != null && originalCachedFile.exists() && originalCachedFile.length() > 0L) {
                PdfBoxMemoryUtil.loadFromFile(context, originalCachedFile)
            } else {
                val originalStream = context.contentResolver.openInputStream(originalUri)
                    ?: throw IllegalArgumentException("Cannot open document: $originalUri")
                PdfBoxMemoryUtil.loadFromStream(context, originalStream)
            }
            openDocs[originalUri] = originalDoc

            val total = pages.size.coerceAtLeast(1)
            pages.forEachIndexed { index, opPage ->
                currentCoroutineContext().ensureActive()
                onProgress(index.toFloat() / total, "Assembling page ${index + 1}/$total")

                if (opPage.isBlank) {
                    // Create a blank page
                    val blankPage = PDPage(PDRectangle(opPage.widthPt, opPage.heightPt))
                    blankPage.rotation = opPage.rotation
                    outDoc.addPage(blankPage)
                } else {
                    val srcUri = opPage.sourceUri ?: originalUri
                    val docToRead = openDocs.getOrPut(srcUri) {
                        if (srcUri == originalUri && originalCachedFile != null && originalCachedFile.exists()) {
                            PdfBoxMemoryUtil.loadFromFile(context, originalCachedFile)
                        } else if (srcUri.scheme == "file" && srcUri.path != null) {
                            PdfBoxMemoryUtil.loadFromFile(context, File(srcUri.path!!))
                        } else {
                            val stream = context.contentResolver.openInputStream(srcUri)
                                ?: throw IllegalArgumentException("Cannot open source document: $srcUri")
                            PdfBoxMemoryUtil.loadFromStream(context, stream)
                        }
                    }

                    if (opPage.sourcePageIndex in 0 until docToRead.numberOfPages) {
                        val srcPage = docToRead.getPage(opPage.sourcePageIndex)
                        val imported = outDoc.importPage(srcPage)
                        imported.rotation = ((srcPage.rotation + opPage.rotation) % 360 + 360) % 360
                    }
                }
            }

            onProgress(0.95f, "Writing organized PDF...")
            outDoc.save(tempOut)
            onProgress(1.0f, "Page organization saved!")
            tempOut
        } finally {
            outDoc.close()
            openDocs.values.forEach { doc ->
                try { doc.close() } catch (_: Exception) {}
            }
        }
    }

    /**
     * 8. Rotate selected PDF pages by 90-degree increments (+90°, -90° / 270°, 180°)
     * and persist the changes to the output file and/or directly to the source document file.
     *
     * @param sourceFile Optional local PDF file on disk.
     * @param sourceUri Optional content/file URI for the PDF document.
     * @param pageRotationDeltas Map of 0-based page index to rotation delta in degrees (snapped to 90° increments).
     * @param outputFile Optional target file to write. If null and [overwriteSourceFile] is true with a valid [sourceFile], writes to [sourceFile].
     * @param overwriteSourceFile When true, writes the rotated PDF back to [sourceFile] and/or [sourceUri] so changes are saved to the document file.
     */
    suspend fun rotateSelectedPages(
        sourceFile: File? = null,
        sourceUri: Uri? = null,
        pageRotationDeltas: Map<Int, Int>,
        outputFile: File? = null,
        overwriteSourceFile: Boolean = false,
        onProgress: (fraction: Float, step: String) -> Unit = { _, _ -> }
    ): File = withContext(Dispatchers.IO) {
        if (!com.tom_roush.pdfbox.android.PDFBoxResourceLoader.isReady()) {
            com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context.applicationContext)
        }

        val resolvedSourceFile: File? = when {
            sourceFile != null && sourceFile.exists() && sourceFile.length() > 0L -> sourceFile
            sourceUri != null && sourceUri.scheme == "file" && sourceUri.path != null -> {
                File(sourceUri.path!!).takeIf { it.exists() && it.length() > 0L }
            }
            else -> null
        }

        var openedInputStream: InputStream? = null
        val doc: PDDocument = when {
            resolvedSourceFile != null -> PdfBoxMemoryUtil.loadFromFile(context, resolvedSourceFile)
            sourceUri != null -> {
                val stream = context.contentResolver.openInputStream(sourceUri)
                    ?: throw IllegalArgumentException("Cannot open PDF document: $sourceUri")
                openedInputStream = stream
                PdfBoxMemoryUtil.loadFromStream(context, stream)
            }
            else -> throw IllegalArgumentException("Either sourceFile or sourceUri must be provided to rotate PDF pages.")
        }

        val tempOut = File(context.cacheDir, "rotated_${UUID.randomUUID()}.pdf")

        try {
            val totalPages = doc.numberOfPages
            val validEntries = pageRotationDeltas.entries
                .filter { (pageIdx, _) -> pageIdx in 0 until totalPages }
                .mapNotNull { (pageIdx, rawDelta) ->
                    val normalizedDelta = RotationIncrement.normalizeTo90Step(rawDelta)
                    if (normalizedDelta != 0) pageIdx to normalizedDelta else null
                }

            val totalToRotate = validEntries.size.coerceAtLeast(1)
            validEntries.forEachIndexed { stepIdx, (pageIdx, normalizedDelta) ->
                currentCoroutineContext().ensureActive()
                onProgress(
                    stepIdx.toFloat() / totalToRotate,
                    "Rotating page ${pageIdx + 1} by ${normalizedDelta}° (${stepIdx + 1}/${validEntries.size})"
                )
                val page = doc.getPage(pageIdx)
                val currentRotation = RotationIncrement.normalizeTo90Step(page.rotation)
                page.rotation = RotationIncrement.normalizeTo90Step(currentRotation + normalizedDelta)
            }

            onProgress(0.90f, "Saving rotated PDF document...")
            doc.save(tempOut)
        } finally {
            try { doc.close() } catch (_: Exception) {}
            try { openedInputStream?.close() } catch (_: Exception) {}
        }

        val finalFile: File = when {
            outputFile != null -> {
                if (tempOut.absolutePath != outputFile.absolutePath) {
                    outputFile.parentFile?.mkdirs()
                    tempOut.copyTo(outputFile, overwrite = true)
                    tempOut.delete()
                }
                outputFile
            }
            overwriteSourceFile && resolvedSourceFile != null -> {
                if (tempOut.absolutePath != resolvedSourceFile.absolutePath) {
                    tempOut.copyTo(resolvedSourceFile, overwrite = true)
                    tempOut.delete()
                }
                resolvedSourceFile
            }
            else -> tempOut
        }

        // If overwriteSourceFile is requested, also persist to sourceFile and/or sourceUri if distinct
        if (overwriteSourceFile) {
            if (resolvedSourceFile != null && finalFile.absolutePath != resolvedSourceFile.absolutePath) {
                finalFile.copyTo(resolvedSourceFile, overwrite = true)
            }
            if (sourceUri != null) {
                if (sourceUri.scheme == "file" && sourceUri.path != null) {
                    val uriTarget = File(sourceUri.path!!)
                    if (uriTarget.absolutePath != finalFile.absolutePath) {
                        finalFile.copyTo(uriTarget, overwrite = true)
                    }
                } else {
                    try {
                        context.contentResolver.openOutputStream(sourceUri, "rwt")?.use { out ->
                            finalFile.inputStream().use { input -> input.copyTo(out) }
                        }
                    } catch (_: Exception) {
                        try {
                            context.contentResolver.openOutputStream(sourceUri, "wt")?.use { out ->
                                finalFile.inputStream().use { input -> input.copyTo(out) }
                            }
                        } catch (_: Exception) {
                        }
                    }
                }
            }
        }

        onProgress(1.0f, "Saved rotated pages to document!")
        finalFile
    }

    /**
     * Convenience overload: Rotate a list of 0-based page indices by a single 90-degree increment
     * (e.g., +90, -90, 180, 270) and save to the document file.
     */
    suspend fun rotateSelectedPages(
        sourceFile: File? = null,
        sourceUri: Uri? = null,
        pageIndices: List<Int>,
        rotationIncrementDegrees: Int,
        outputFile: File? = null,
        overwriteSourceFile: Boolean = false,
        onProgress: (fraction: Float, step: String) -> Unit = { _, _ -> }
    ): File {
        val normalizedStep = RotationIncrement.normalizeTo90Step(rotationIncrementDegrees)
        val deltas = pageIndices.distinct().associateWith { normalizedStep }
        return rotateSelectedPages(
            sourceFile = sourceFile,
            sourceUri = sourceUri,
            pageRotationDeltas = deltas,
            outputFile = outputFile,
            overwriteSourceFile = overwriteSourceFile,
            onProgress = onProgress
        )
    }

    // Helper: Safely downsample bitmap to avoid OOM
    private fun decodeBitmapSafely(uri: Uri, maxDimension: Int): Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openUriInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }

        var sampleSize = 1
        var w = options.outWidth
        var h = options.outHeight
        while (w > maxDimension || h > maxDimension) {
            sampleSize *= 2
            w /= 2
            h /= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565 // Half memory of ARGB_8888
        }

        return openUriInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        }
    }

    // Helper: Parse string range expression like "1-3, 5, 7-9" into 0-based page index groups
    fun parsePageRanges(rangeString: String, totalPages: Int): List<List<Int>> {
        val result = mutableListOf<List<Int>>()
        if (rangeString.isBlank() || totalPages <= 0) return result

        val parts = rangeString.split(",")
        for (part in parts) {
            val trimmed = part.trim()
            if (trimmed.isEmpty()) continue

            if (trimmed.contains("-")) {
                val rangeParts = trimmed.split("-")
                val start = (rangeParts.getOrNull(0)?.trim()?.toIntOrNull() ?: 1) - 1
                val end = (rangeParts.getOrNull(1)?.trim()?.toIntOrNull() ?: totalPages) - 1
                val validStart = start.coerceIn(0, totalPages - 1)
                val validEnd = end.coerceIn(0, totalPages - 1)
                if (validStart <= validEnd) {
                    result.add((validStart..validEnd).toList())
                }
            } else {
                val p = (trimmed.toIntOrNull() ?: 1) - 1
                if (p in 0 until totalPages) {
                    result.add(listOf(p))
                }
            }
        }
        return result
    }

    fun parseFlatPageIndices(rangeString: String, totalPages: Int): List<Int> {
        return parsePageRanges(rangeString, totalPages).flatten().distinct().sorted()
    }

    // Helper: Extract bookmark segments
    private fun extractBookmarkSegments(doc: PDDocument, baseName: String): List<Pair<String, List<Int>>> {
        val list = mutableListOf<Pair<String, List<Int>>>()
        val outline = doc.documentCatalog.documentOutline ?: return fallbackSplit(doc, baseName)

        val bookmarkPages = mutableListOf<Pair<String, Int>>()
        var current: PDOutlineItem? = outline.firstChild
        while (current != null) {
            try {
                val dest = current.destination
                if (dest is PDPageDestination) {
                    val p = doc.pages.indexOf(dest.page)
                    if (p >= 0) {
                        val sanitizedTitle = current.title.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(30)
                        bookmarkPages.add(sanitizedTitle to p)
                    }
                }
            } catch (_: Exception) {}
            current = current.nextSibling
        }

        if (bookmarkPages.isEmpty()) return fallbackSplit(doc, baseName)

        val sorted = bookmarkPages.sortedBy { it.second }
        for (i in sorted.indices) {
            val title = sorted[i].first
            val startPage = sorted[i].second
            val endPage = if (i + 1 < sorted.size) sorted[i + 1].second else doc.numberOfPages
            val group = (startPage until endPage).toList()
            if (group.isNotEmpty()) {
                list.add("${baseName}_${title}" to group)
            }
        }
        return if (list.isNotEmpty()) list else fallbackSplit(doc, baseName)
    }

    private fun fallbackSplit(doc: PDDocument, baseName: String): List<Pair<String, List<Int>>> {
        val list = mutableListOf<Pair<String, List<Int>>>()
        for (i in 0 until doc.numberOfPages) {
            list.add("${baseName}_page_${i + 1}" to listOf(i))
        }
        return list
    }

    // Helper: Write file to DocumentTree SAF folder
    private fun writeToDocumentFolder(
        treeUri: Uri,
        fileName: String,
        sourceFile: File,
        mimeType: String = "application/pdf"
    ) {
        try {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
            val newFileUri = DocumentsContract.createDocument(context.contentResolver, parentUri, mimeType, fileName)
            if (newFileUri != null) {
                context.contentResolver.openOutputStream(newFileUri)?.use { os ->
                    FileInputStream(sourceFile).use { `is` ->
                        `is`.copyTo(os)
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    // Helper: Zip a list of files
    private fun zipFiles(files: List<File>, destinationZip: File) {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(destinationZip))).use { zos ->
            for (file in files) {
                if (!file.exists()) continue
                val entry = ZipEntry(file.name)
                zos.putNextEntry(entry)
                FileInputStream(file).use { fis ->
                    fis.copyTo(zos)
                }
                zos.closeEntry()
            }
        }
    }
}
