package com.example.pdf

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

enum class PrintPageSelection {
    ALL_PAGES,
    CURRENT_PAGE,
    ODD_PAGES,
    EVEN_PAGES,
    CUSTOM_RANGE
}

enum class PrintPaperSize(val label: String) {
    A4("ISO A4 (210 × 297 mm)"),
    LETTER("US Letter (8.5 × 11 in)"),
    LEGAL("US Legal (8.5 × 14 in)")
}

enum class PrintOrientation(val label: String) {
    PORTRAIT("Portrait"),
    LANDSCAPE("Landscape")
}

data class PrintJobConfig(
    val pageSelection: PrintPageSelection = PrintPageSelection.ALL_PAGES,
    val currentPageIndex: Int = 0,
    val customRangeText: String = "",
    val paperSize: PrintPaperSize = PrintPaperSize.A4,
    val orientation: PrintOrientation = PrintOrientation.PORTRAIT,
    val isColor: Boolean = true,
    val copies: Int = 1,
    val includePrintWatermark: Boolean = false,
    val printWatermarkText: String = "CONFIDENTIAL",
    val printWatermarkOpacity: Float = 0.25f,
    val includePageNumbers: Boolean = false
)

class PdfPrintHelper(private val context: Context) {

    init {
        if (!PDFBoxResourceLoader.isReady()) {
            PDFBoxResourceLoader.init(context.applicationContext)
        }
    }

    /**
     * Parses a 1-based page range string such as "1-3, 5" into 0-based page indices.
     */
    fun resolveSelectedPages(
        totalPages: Int,
        config: PrintJobConfig
    ): List<Int> {
        if (totalPages <= 0) return emptyList()
        return when (config.pageSelection) {
            PrintPageSelection.ALL_PAGES -> (0 until totalPages).toList()
            PrintPageSelection.CURRENT_PAGE -> listOf(config.currentPageIndex.coerceIn(0, totalPages - 1))
            PrintPageSelection.ODD_PAGES -> (0 until totalPages).filter { (it + 1) % 2 == 1 }
            PrintPageSelection.EVEN_PAGES -> (0 until totalPages).filter { (it + 1) % 2 == 0 }
            PrintPageSelection.CUSTOM_RANGE -> {
                val parsed = parseCustomPageRange(config.customRangeText, totalPages)
                parsed.ifEmpty { (0 until totalPages).toList() }
            }
        }
    }

    fun parseCustomPageRange(rangeText: String, totalPages: Int): List<Int> {
        if (rangeText.isBlank() || totalPages <= 0) return emptyList()
        val result = sortedSetOf<Int>()
        val parts = rangeText.split(",", ";")
        for (rawPart in parts) {
            val part = rawPart.trim()
            if (part.isEmpty()) continue
            if (part.contains("-")) {
                val bounds = part.split("-").map { it.trim() }
                if (bounds.size == 2) {
                    val start = bounds[0].toIntOrNull()
                    val end = bounds[1].toIntOrNull()
                    if (start != null && end != null) {
                        val low = minOf(start, end).coerceIn(1, totalPages)
                        val high = maxOf(start, end).coerceIn(1, totalPages)
                        for (p in low..high) {
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
        return result.toList()
    }

    /**
     * Prepares a print-ready PDF file on disk applying any requested page filtering,
     * print-time watermark, or page numbering without modifying the user's original file.
     */
    suspend fun preparePrintPdfFile(
        sourceFile: File?,
        sourceUri: Uri?,
        config: PrintJobConfig,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): File = withContext(Dispatchers.IO) {
        onProgress(0.1f, "Loading document for print...")

        val baseTempFile = File(context.cacheDir, "print_source_${System.currentTimeMillis()}.pdf")
        if (sourceFile != null && sourceFile.exists()) {
            sourceFile.copyTo(baseTempFile, overwrite = true)
        } else if (sourceUri != null) {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(baseTempFile).use { output ->
                    input.copyTo(output)
                }
            } ?: throw IllegalArgumentException("Unable to read source PDF for printing.")
        } else {
            throw IllegalArgumentException("No PDF source provided for printing.")
        }

        val needsPageFiltering = config.pageSelection != PrintPageSelection.ALL_PAGES
        val needsWatermarkOrNumbers = config.includePrintWatermark || config.includePageNumbers

        if (!needsPageFiltering && !needsWatermarkOrNumbers) {
            onProgress(1.0f, "Ready to print!")
            return@withContext baseTempFile
        }

        var workingFile = baseTempFile
        if (needsPageFiltering) {
            onProgress(0.35f, "Selecting pages for print...")
            val filteredFile = File(context.cacheDir, "print_filtered_${System.currentTimeMillis()}.pdf")
            PdfBoxMemoryUtil.loadFromFile(context, workingFile).use { sourceDoc ->
                val total = sourceDoc.numberOfPages
                val targetIndices = resolveSelectedPages(total, config)
                PDDocument().use { outDoc ->
                    for (idx in targetIndices) {
                        if (idx in 0 until total) {
                            outDoc.importPage(sourceDoc.getPage(idx))
                        }
                    }
                    outDoc.save(filteredFile)
                }
            }
            if (workingFile != sourceFile) {
                workingFile.delete()
            }
            workingFile = filteredFile
        }

        if (needsWatermarkOrNumbers) {
            onProgress(0.65f, "Applying print watermark & page numbers...")
            val optimizationEngine = PdfOptimizationEngine(context)
            val wmConfig = if (config.includePrintWatermark) {
                WatermarkConfig(
                    isText = true,
                    text = config.printWatermarkText.ifBlank { "CONFIDENTIAL" },
                    color = Color.DKGRAY,
                    opacity = config.printWatermarkOpacity.coerceIn(0.10f, 0.85f),
                    rotationDegrees = 45f,
                    isTiled = false,
                    fontSize = 44f
                )
            } else null

            val hfConfig = if (config.includePageNumbers) {
                HeaderFooterConfig(
                    showPageNumbers = true,
                    pageNumberPosition = PageNumberPosition.BOTTOM_CENTER,
                    pageNumberFormat = "Page {page} of {total}"
                )
            } else null

            val decoratedFile = optimizationEngine.applyWatermarkAndDecorations(
                pdfFile = workingFile,
                uri = null,
                watermarkConfig = wmConfig,
                headerFooterConfig = hfConfig,
                onProgress = { frac, msg ->
                    onProgress(0.65f + frac * 0.30f, msg)
                }
            )
            if (workingFile != sourceFile) {
                workingFile.delete()
            }
            workingFile = decoratedFile
        }

        onProgress(1.0f, "Ready to print!")
        workingFile
    }

    /**
     * Launches the system Android Print Manager dialog for a prepared PDF file.
     */
    fun launchSystemPrintJob(
        preparedPdfFile: File,
        documentDisplayName: String,
        config: PrintJobConfig
    ): Boolean {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            ?: return false

        val cleanName = documentDisplayName.removeSuffix(".pdf").ifBlank { "Document" }
        val jobName = "PDF_Print_$cleanName"

        val baseMediaSize = when (config.paperSize) {
            PrintPaperSize.A4 -> PrintAttributes.MediaSize.ISO_A4
            PrintPaperSize.LETTER -> PrintAttributes.MediaSize.NA_LETTER
            PrintPaperSize.LEGAL -> PrintAttributes.MediaSize.NA_LEGAL
        }
        val orientedMediaSize = when (config.orientation) {
            PrintOrientation.PORTRAIT -> baseMediaSize.asPortrait()
            PrintOrientation.LANDSCAPE -> baseMediaSize.asLandscape()
        }

        val attributes = PrintAttributes.Builder()
            .setMediaSize(orientedMediaSize)
            .setColorMode(
                if (config.isColor) PrintAttributes.COLOR_MODE_COLOR
                else PrintAttributes.COLOR_MODE_MONOCHROME
            )
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()

        val adapter = PdfFilePrintDocumentAdapter(
            pdfFile = preparedPdfFile,
            fileName = "$cleanName.pdf"
        )

        printManager.print(jobName, adapter, attributes)
        return true
    }
}

class PdfFilePrintDocumentAdapter(
    private val pdfFile: File,
    private val fileName: String
) : PrintDocumentAdapter() {

    private var totalPages: Int = PrintDocumentInfo.PAGE_COUNT_UNKNOWN

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback?,
        extras: Bundle?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback?.onLayoutCancelled()
            return
        }

        if (!pdfFile.exists() || pdfFile.length() == 0L) {
            callback?.onLayoutFailed("PDF file is empty or missing.")
            return
        }

        try {
            PDDocument.load(pdfFile, com.tom_roush.pdfbox.io.MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                totalPages = doc.numberOfPages.coerceAtLeast(1)
            }
        } catch (_: Throwable) {
            totalPages = PrintDocumentInfo.PAGE_COUNT_UNKNOWN
        }

        val info = PrintDocumentInfo.Builder(fileName)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(totalPages)
            .build()

        callback?.onLayoutFinished(info, newAttributes != oldAttributes)
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor?,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback?.onWriteCancelled()
            return
        }
        if (destination == null) {
            callback?.onWriteFailed("Destination descriptor is null.")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                FileInputStream(pdfFile).use { input ->
                    FileOutputStream(destination.fileDescriptor).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } >= 0) {
                            if (cancellationSignal?.isCanceled == true) {
                                callback?.onWriteCancelled()
                                return@launch
                            }
                            output.write(buffer, 0, bytesRead)
                        }
                        output.flush()
                    }
                }
                callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (e: Exception) {
                callback?.onWriteFailed(e.localizedMessage ?: "Failed to write PDF to print spooler.")
            }
        }
    }
}
