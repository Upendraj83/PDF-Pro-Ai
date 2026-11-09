package com.example.viewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Xml
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Locale
import java.util.zip.ZipFile
import kotlin.math.min

enum class UniversalFileType(
    val label: String,
    val badge: String
) {
    PDF("PDF Document", "PDF"),
    WORD("MS Word Document", "DOCX"),
    EXCEL("MS Excel Spreadsheet", "XLSX"),
    POWERPOINT("MS PowerPoint Presentation", "PPTX"),
    IMAGE("JPEG / PNG Image", "IMG"),
    CSV("CSV Spreadsheet", "CSV"),
    RTF("Rich Text Document", "RTF"),
    TEXT("Text / Markdown / HTML", "TXT")
}

sealed interface WordDocBlock {
    data class Heading(val text: String, val level: Int = 1) : WordDocBlock
    data class Paragraph(
        val text: String,
        val isBold: Boolean = false,
        val isItalic: Boolean = false,
        val isBullet: Boolean = false
    ) : WordDocBlock
    data class Table(val rows: List<List<String>>) : WordDocBlock
    data class EmbeddedImage(val name: String, val bitmap: Bitmap) : WordDocBlock
    data object PageBreak : WordDocBlock
}

data class ExcelSheetData(
    val sheetName: String,
    val rows: List<List<String>>,
    val maxColumns: Int
)

data class PowerPointSlideData(
    val slideNumber: Int,
    val title: String,
    val bulletPoints: List<String>
)

sealed interface ParsedUniversalFile {
    val fileName: String
    val fileSizeBytes: Long
    val fileType: UniversalFileType
    val sourceUri: Uri?
    val cachedFile: File?

    data class WordDocument(
        override val fileName: String,
        override val fileSizeBytes: Long,
        override val fileType: UniversalFileType,
        override val sourceUri: Uri?,
        override val cachedFile: File?,
        val blocks: List<WordDocBlock>,
        val plainText: String,
        val wordCount: Int,
        val paragraphCount: Int,
        val tableCount: Int
    ) : ParsedUniversalFile

    data class ExcelWorkbook(
        override val fileName: String,
        override val fileSizeBytes: Long,
        override val fileType: UniversalFileType,
        override val sourceUri: Uri?,
        override val cachedFile: File?,
        val sheets: List<ExcelSheetData>
    ) : ParsedUniversalFile

    data class PowerPointDeck(
        override val fileName: String,
        override val fileSizeBytes: Long,
        override val fileType: UniversalFileType = UniversalFileType.POWERPOINT,
        override val sourceUri: Uri?,
        override val cachedFile: File?,
        val slides: List<PowerPointSlideData>
    ) : ParsedUniversalFile

    data class ImageDocument(
        override val fileName: String,
        override val fileSizeBytes: Long,
        override val fileType: UniversalFileType = UniversalFileType.IMAGE,
        override val sourceUri: Uri?,
        override val cachedFile: File?,
        val bitmap: Bitmap,
        val widthPx: Int,
        val heightPx: Int,
        val formatLabel: String
    ) : ParsedUniversalFile
}

class UniversalFileParserEngine(private val context: Context) {

    init {
        try {
            PDFBoxResourceLoader.init(context.applicationContext)
        } catch (_: Exception) {
        }
    }

    companion object {
        val ALL_SUPPORTED_OPEN_MIME_TYPES = arrayOf(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.ms-powerpoint",
            "application/rtf",
            "text/rtf",
            "text/csv",
            "text/comma-separated-values",
            "text/plain",
            "text/markdown",
            "text/html",
            "image/jpeg",
            "image/png",
            "image/webp"
        )

        fun detectFileType(context: Context, uri: Uri?, fileName: String?): UniversalFileType {
            val resolvedName = (fileName ?: uri?.let { queryDisplayName(context, it) } ?: uri?.lastPathSegment ?: "")
                .lowercase(Locale.US)
            val mime = uri?.let {
                try { context.contentResolver.getType(it)?.lowercase(Locale.US) } catch (_: Exception) { null }
            }.orEmpty()

            return when {
                resolvedName.endsWith(".pdf") || mime == "application/pdf" ->
                    UniversalFileType.PDF
                resolvedName.endsWith(".docx") || resolvedName.endsWith(".doc") ||
                    mime.contains("wordprocessingml") || mime == "application/msword" ->
                    UniversalFileType.WORD
                resolvedName.endsWith(".xlsx") || resolvedName.endsWith(".xls") ||
                    mime.contains("spreadsheetml") || mime == "application/vnd.ms-excel" ->
                    UniversalFileType.EXCEL
                resolvedName.endsWith(".csv") || resolvedName.endsWith(".tsv") ||
                    mime.contains("csv") || mime.contains("comma-separated") ->
                    UniversalFileType.CSV
                resolvedName.endsWith(".pptx") || resolvedName.endsWith(".ppt") ||
                    mime.contains("presentationml") || mime == "application/vnd.ms-powerpoint" ->
                    UniversalFileType.POWERPOINT
                resolvedName.endsWith(".jpg") || resolvedName.endsWith(".jpeg") ||
                    resolvedName.endsWith(".png") || resolvedName.endsWith(".webp") ||
                    resolvedName.endsWith(".bmp") || mime.startsWith("image/") ->
                    UniversalFileType.IMAGE
                resolvedName.endsWith(".rtf") || mime.contains("rtf") ->
                    UniversalFileType.RTF
                resolvedName.endsWith(".txt") || resolvedName.endsWith(".md") ||
                    resolvedName.endsWith(".html") || resolvedName.endsWith(".htm") ||
                    mime.startsWith("text/") ->
                    UniversalFileType.TEXT
                else -> UniversalFileType.PDF
            }
        }

        fun queryDisplayName(context: Context, uri: Uri): String? {
            if (uri.scheme == "file") {
                return uri.path?.let { File(it).name }
            }
            return try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx != -1 && cursor.moveToFirst()) cursor.getString(idx) else null
                }
            } catch (_: Exception) {
                null
            }
        }

        fun queryFileSize(context: Context, uri: Uri): Long {
            if (uri.scheme == "file") {
                return uri.path?.let { File(it).length() } ?: 0L
            }
            return try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (idx != -1 && cursor.moveToFirst()) cursor.getLong(idx) else 0L
                } ?: 0L
            } catch (_: Exception) {
                0L
            }
        }
    }

    /**
     * Parses a non-PDF file (MS Word .docx, MS Excel .xlsx/.csv, PowerPoint .pptx, JPEG/PNG image, RTF, TXT/MD/HTML)
     * into a structured [ParsedUniversalFile] ready for on-screen rendering.
     */
    suspend fun parseFile(
        uri: Uri?,
        directFile: File? = null,
        explicitFileName: String? = null
    ): ParsedUniversalFile = withContext(Dispatchers.IO) {
        val displayName = explicitFileName
            ?: directFile?.name
            ?: uri?.let { queryDisplayName(context, it) }
            ?: "Document"

        val cachedFile = when {
            directFile != null && directFile.exists() -> directFile
            uri != null && uri.scheme == "file" && uri.path != null -> File(uri.path!!)
            uri != null -> copyUriToCache(uri, displayName)
            else -> throw IllegalArgumentException("No valid file or URI provided")
        }

        val fileSize = if (cachedFile.exists()) cachedFile.length() else (uri?.let { queryFileSize(context, it) } ?: 0L)
        val fileType = detectFileType(context, uri, displayName)

        when (fileType) {
            UniversalFileType.WORD -> parseWordDocument(cachedFile, uri, displayName, fileSize)
            UniversalFileType.EXCEL -> parseExcelXlsx(cachedFile, uri, displayName, fileSize)
            UniversalFileType.CSV -> parseCsvSpreadsheet(cachedFile, uri, displayName, fileSize)
            UniversalFileType.POWERPOINT -> parsePowerPointPptx(cachedFile, uri, displayName, fileSize)
            UniversalFileType.IMAGE -> parseImageDocument(cachedFile, uri, displayName, fileSize)
            UniversalFileType.RTF -> parseRtfDocument(cachedFile, uri, displayName, fileSize)
            UniversalFileType.TEXT, UniversalFileType.PDF -> parsePlainTextOrHtmlDocument(cachedFile, uri, displayName, fileSize, fileType)
        }
    }

    private fun copyUriToCache(uri: Uri, displayName: String): File {
        val ext = displayName.substringAfterLast('.', "tmp")
        val out = File(context.cacheDir, "univ_view_${System.currentTimeMillis()}.$ext")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(out).use { output ->
                input.copyTo(output)
            }
        }
        return out
    }

    // =========================================================================
    // 1. MS Word (.docx) Parser
    // =========================================================================
    fun parseWordDocument(
        file: File,
        uri: Uri?,
        fileName: String,
        fileSize: Long
    ): ParsedUniversalFile.WordDocument {
        val blocks = mutableListOf<WordDocBlock>()
        var tableCount = 0
        var paragraphCount = 0

        try {
            ZipFile(file).use { zip ->
                // 1. Parse word/_rels/document.xml.rels to map rId -> media/image file
                val relMap = mutableMapOf<String, String>()
                val relsEntry = zip.getEntry("word/_rels/document.xml.rels")
                if (relsEntry != null) {
                    zip.getInputStream(relsEntry).use { relStream ->
                        val parser = Xml.newPullParser()
                        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                        parser.setInput(relStream, "UTF-8")
                        var event = parser.eventType
                        while (event != XmlPullParser.END_DOCUMENT) {
                            if (event == XmlPullParser.START_TAG && parser.name.endsWith("Relationship")) {
                                val id = parser.getAttributeValue(null, "Id")
                                val target = parser.getAttributeValue(null, "Target")
                                if (!id.isNullOrBlank() && !target.isNullOrBlank()) {
                                    relMap[id] = target.removePrefix("/")
                                }
                            }
                            event = parser.next()
                        }
                    }
                }

                // 2. Parse word/document.xml
                val docEntry = zip.getEntry("word/document.xml")
                if (docEntry != null) {
                    zip.getInputStream(docEntry).use { docStream ->
                        val parser = Xml.newPullParser()
                        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                        parser.setInput(docStream, "UTF-8")

                        var inTable = false
                        val currentTableRows = mutableListOf<List<String>>()
                        var currentRowCells = mutableListOf<String>()
                        val currentCellText = StringBuilder()

                        var inParagraph = false
                        val currentParagraphText = StringBuilder()
                        var currentParagraphStyle = ""
                        var currentRunBold = false
                        var currentRunItalic = false
                        var isBulletParagraph = false
                        var pendingImageRelId: String? = null

                        var event = parser.eventType
                        while (event != XmlPullParser.END_DOCUMENT) {
                            val tag = parser.name?.substringAfter(':') ?: ""
                            when (event) {
                                XmlPullParser.START_TAG -> {
                                    when (tag) {
                                        "tbl" -> {
                                            inTable = true
                                            currentTableRows.clear()
                                        }
                                        "tr" -> {
                                            if (inTable) {
                                                currentRowCells = mutableListOf()
                                            }
                                        }
                                        "tc" -> {
                                            if (inTable) {
                                                currentCellText.clear()
                                            }
                                        }
                                        "p" -> {
                                            inParagraph = true
                                            currentParagraphText.clear()
                                            currentParagraphStyle = ""
                                            currentRunBold = false
                                            currentRunItalic = false
                                            isBulletParagraph = false
                                            pendingImageRelId = null
                                        }
                                        "pStyle" -> {
                                            val styleVal = getAttrIgnoreNamespace(parser, "val").orEmpty()
                                            currentParagraphStyle = styleVal
                                        }
                                        "numPr" -> {
                                            isBulletParagraph = true
                                        }
                                        "b" -> {
                                            val v = getAttrIgnoreNamespace(parser, "val")
                                            if (v == null || v != "0" && v != "false") {
                                                currentRunBold = true
                                            }
                                        }
                                        "i" -> {
                                            val v = getAttrIgnoreNamespace(parser, "val")
                                            if (v == null || v != "0" && v != "false") {
                                                currentRunItalic = true
                                            }
                                        }
                                        "t" -> {
                                            val txt = parser.nextText()
                                            if (inTable) {
                                                currentCellText.append(txt)
                                            } else if (inParagraph) {
                                                currentParagraphText.append(txt)
                                            }
                                        }
                                        "tab" -> {
                                            if (inTable) currentCellText.append("   ")
                                            else if (inParagraph) currentParagraphText.append("    ")
                                        }
                                        "br" -> {
                                            val brType = getAttrIgnoreNamespace(parser, "type")
                                            if (brType == "page" && !inTable) {
                                                blocks.add(WordDocBlock.PageBreak)
                                            } else if (inTable) {
                                                currentCellText.append("\n")
                                            } else if (inParagraph) {
                                                currentParagraphText.append("\n")
                                            }
                                        }
                                        "blip" -> {
                                            val embedId = getAttrIgnoreNamespace(parser, "embed")
                                            if (!embedId.isNullOrBlank()) {
                                                pendingImageRelId = embedId
                                            }
                                        }
                                    }
                                }
                                XmlPullParser.END_TAG -> {
                                    when (tag) {
                                        "p" -> {
                                            if (inTable) {
                                                if (currentCellText.isNotEmpty() && !currentCellText.endsWith("\n")) {
                                                    currentCellText.append(" ")
                                                }
                                            } else if (inParagraph) {
                                                val pText = currentParagraphText.toString().trim()
                                                if (pText.isNotEmpty()) {
                                                    paragraphCount++
                                                    val styleLower = currentParagraphStyle.lowercase(Locale.US)
                                                    when {
                                                        styleLower.contains("heading1") || styleLower.contains("title") ->
                                                            blocks.add(WordDocBlock.Heading(pText, level = 1))
                                                        styleLower.contains("heading2") || styleLower.contains("subtitle") ->
                                                            blocks.add(WordDocBlock.Heading(pText, level = 2))
                                                        styleLower.contains("heading") ->
                                                            blocks.add(WordDocBlock.Heading(pText, level = 3))
                                                        else ->
                                                            blocks.add(
                                                                WordDocBlock.Paragraph(
                                                                    text = pText,
                                                                    isBold = currentRunBold,
                                                                    isItalic = currentRunItalic,
                                                                    isBullet = isBulletParagraph
                                                                )
                                                            )
                                                    }
                                                }
                                                val imgRel = pendingImageRelId
                                                if (imgRel != null) {
                                                    val targetPath = relMap[imgRel]
                                                    if (targetPath != null) {
                                                        val zipPath = if (targetPath.startsWith("word/")) targetPath else "word/$targetPath"
                                                        val imgEntry = zip.getEntry(zipPath)
                                                        if (imgEntry != null) {
                                                            val bmp = zip.getInputStream(imgEntry).use { imgStream ->
                                                                decodeSampledBitmap(imgStream.readBytes(), maxDim = 1280)
                                                            }
                                                            if (bmp != null) {
                                                                blocks.add(WordDocBlock.EmbeddedImage(zipPath.substringAfterLast('/'), bmp))
                                                            }
                                                        }
                                                    }
                                                }
                                                inParagraph = false
                                            }
                                        }
                                        "tc" -> {
                                            if (inTable) {
                                                currentRowCells.add(currentCellText.toString().trim())
                                                currentCellText.clear()
                                            }
                                        }
                                        "tr" -> {
                                            if (inTable && currentRowCells.isNotEmpty()) {
                                                currentTableRows.add(currentRowCells.toList())
                                            }
                                        }
                                        "tbl" -> {
                                            if (inTable && currentTableRows.isNotEmpty()) {
                                                tableCount++
                                                blocks.add(WordDocBlock.Table(currentTableRows.toList()))
                                            }
                                            inTable = false
                                        }
                                    }
                                }
                            }
                            event = parser.next()
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // Fallback for legacy .doc or non-zipped files: extract readable text strings
            val rawText = extractReadableStringsFromFile(file)
            rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
                paragraphCount++
                blocks.add(WordDocBlock.Paragraph(line))
            }
        }

        if (blocks.isEmpty()) {
            blocks.add(WordDocBlock.Paragraph("[Empty Word Document]"))
        }

        val fullPlain = buildString {
            for (b in blocks) {
                when (b) {
                    is WordDocBlock.Heading -> append(b.text).append("\n\n")
                    is WordDocBlock.Paragraph -> append(if (b.isBullet) "• ${b.text}" else b.text).append("\n")
                    is WordDocBlock.Table -> {
                        b.rows.forEach { row -> append(row.joinToString(" | ")).append("\n") }
                        append("\n")
                    }
                    is WordDocBlock.EmbeddedImage -> append("[Image: ${b.name}]\n")
                    WordDocBlock.PageBreak -> append("\n--- Page Break ---\n\n")
                }
            }
        }.trim()

        val wordCount = fullPlain.split(Regex("\\s+")).count { it.isNotBlank() }

        return ParsedUniversalFile.WordDocument(
            fileName = fileName,
            fileSizeBytes = fileSize,
            fileType = UniversalFileType.WORD,
            sourceUri = uri,
            cachedFile = file,
            blocks = blocks,
            plainText = fullPlain,
            wordCount = wordCount,
            paragraphCount = paragraphCount,
            tableCount = tableCount
        )
    }

    // =========================================================================
    // 2. MS Excel (.xlsx) & CSV Parser
    // =========================================================================
    fun parseExcelXlsx(
        file: File,
        uri: Uri?,
        fileName: String,
        fileSize: Long
    ): ParsedUniversalFile.ExcelWorkbook {
        val sheets = mutableListOf<ExcelSheetData>()
        try {
            ZipFile(file).use { zip ->
                // 1. Parse xl/sharedStrings.xml if present
                val sharedStrings = mutableListOf<String>()
                val sharedEntry = zip.getEntry("xl/sharedStrings.xml")
                if (sharedEntry != null) {
                    zip.getInputStream(sharedEntry).use { ssStream ->
                        val parser = Xml.newPullParser()
                        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                        parser.setInput(ssStream, "UTF-8")
                        var inSi = false
                        val siBuilder = StringBuilder()
                        var event = parser.eventType
                        while (event != XmlPullParser.END_DOCUMENT) {
                            val tag = parser.name?.substringAfter(':') ?: ""
                            when (event) {
                                XmlPullParser.START_TAG -> {
                                    if (tag == "si") {
                                        inSi = true
                                        siBuilder.clear()
                                    } else if (inSi && tag == "t") {
                                        siBuilder.append(parser.nextText())
                                    }
                                }
                                XmlPullParser.END_TAG -> {
                                    if (tag == "si") {
                                        sharedStrings.add(siBuilder.toString())
                                        inSi = false
                                    }
                                }
                            }
                            event = parser.next()
                        }
                    }
                }

                // 2. Parse xl/workbook.xml & xl/_rels/workbook.xml.rels for sheet names & paths
                val relsMap = mutableMapOf<String, String>()
                val wbRelsEntry = zip.getEntry("xl/_rels/workbook.xml.rels")
                if (wbRelsEntry != null) {
                    zip.getInputStream(wbRelsEntry).use { relStream ->
                        val parser = Xml.newPullParser()
                        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                        parser.setInput(relStream, "UTF-8")
                        var event = parser.eventType
                        while (event != XmlPullParser.END_DOCUMENT) {
                            if (event == XmlPullParser.START_TAG && parser.name.endsWith("Relationship")) {
                                val id = parser.getAttributeValue(null, "Id")
                                val target = parser.getAttributeValue(null, "Target")
                                if (!id.isNullOrBlank() && !target.isNullOrBlank()) {
                                    relsMap[id] = target.removePrefix("/")
                                }
                            }
                            event = parser.next()
                        }
                    }
                }

                val sheetDefs = mutableListOf<Pair<String, String>>() // (sheetName, zipEntryPath)
                val wbEntry = zip.getEntry("xl/workbook.xml")
                if (wbEntry != null) {
                    zip.getInputStream(wbEntry).use { wbStream ->
                        val parser = Xml.newPullParser()
                        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                        parser.setInput(wbStream, "UTF-8")
                        var sheetIdx = 1
                        var event = parser.eventType
                        while (event != XmlPullParser.END_DOCUMENT) {
                            val tag = parser.name?.substringAfter(':') ?: ""
                            if (event == XmlPullParser.START_TAG && tag == "sheet") {
                                val name = getAttrIgnoreNamespace(parser, "name") ?: "Sheet$sheetIdx"
                                val rId = getAttrIgnoreNamespace(parser, "id")
                                val relTarget = rId?.let { relsMap[it] }
                                val entryPath = when {
                                    relTarget == null -> "xl/worksheets/sheet$sheetIdx.xml"
                                    relTarget.startsWith("xl/") -> relTarget
                                    else -> "xl/$relTarget"
                                }
                                sheetDefs.add(name to entryPath)
                                sheetIdx++
                            }
                            event = parser.next()
                        }
                    }
                }

                if (sheetDefs.isEmpty()) {
                    sheetDefs.add("Sheet 1" to "xl/worksheets/sheet1.xml")
                }

                // 3. Parse each worksheet XML
                for ((sheetName, entryPath) in sheetDefs) {
                    val sheetEntry = zip.getEntry(entryPath) ?: continue
                    val rows = mutableListOf<List<String>>()
                    var maxCols = 1

                    zip.getInputStream(sheetEntry).use { sheetStream ->
                        val parser = Xml.newPullParser()
                        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                        parser.setInput(sheetStream, "UTF-8")

                        var currentRowMap = sortedMapOf<Int, String>()
                        var currentCellCol = 0
                        var currentCellType = ""
                        val currentCellVal = StringBuilder()

                        var event = parser.eventType
                        while (event != XmlPullParser.END_DOCUMENT) {
                            val tag = parser.name?.substringAfter(':') ?: ""
                            when (event) {
                                XmlPullParser.START_TAG -> {
                                    when (tag) {
                                        "row" -> {
                                            currentRowMap = sortedMapOf()
                                        }
                                        "c" -> {
                                            val ref = getAttrIgnoreNamespace(parser, "r").orEmpty()
                                            currentCellCol = if (ref.isNotEmpty()) {
                                                parseExcelColumnIndex(ref)
                                            } else {
                                                (currentRowMap.keys.maxOrNull() ?: -1) + 1
                                            }
                                            currentCellType = getAttrIgnoreNamespace(parser, "t").orEmpty()
                                            currentCellVal.clear()
                                        }
                                        "v", "t" -> {
                                            currentCellVal.append(parser.nextText())
                                        }
                                    }
                                }
                                XmlPullParser.END_TAG -> {
                                    when (tag) {
                                        "c" -> {
                                            val raw = currentCellVal.toString()
                                            val resolved = if (currentCellType == "s") {
                                                val idx = raw.trim().toIntOrNull()
                                                if (idx != null && idx in sharedStrings.indices) {
                                                    sharedStrings[idx]
                                                } else {
                                                    raw
                                                }
                                            } else {
                                                raw
                                            }
                                            currentRowMap[currentCellCol] = resolved
                                        }
                                        "row" -> {
                                            val highestCol = currentRowMap.keys.maxOrNull() ?: -1
                                            if (highestCol >= 0) {
                                                val colCount = (highestCol + 1).coerceAtMost(50)
                                                if (colCount > maxCols) maxCols = colCount
                                                val rowList = (0 until colCount).map { colIdx ->
                                                    currentRowMap[colIdx].orEmpty()
                                                }
                                                rows.add(rowList)
                                            }
                                        }
                                    }
                                }
                            }
                            event = parser.next()
                        }
                    }

                    // Normalize row lengths to maxCols
                    val normalizedRows = rows.map { row ->
                        if (row.size < maxCols) row + List(maxCols - row.size) { "" } else row
                    }
                    sheets.add(
                        ExcelSheetData(
                            sheetName = sheetName,
                            rows = if (normalizedRows.isEmpty()) listOf(listOf("[Empty Sheet]")) else normalizedRows,
                            maxColumns = maxCols.coerceAtLeast(1)
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // Fallback to CSV-style line parsing
            return parseCsvSpreadsheet(file, uri, fileName, fileSize)
        }

        if (sheets.isEmpty()) {
            sheets.add(ExcelSheetData("Sheet 1", listOf(listOf("[Empty Workbook]")), 1))
        }

        return ParsedUniversalFile.ExcelWorkbook(
            fileName = fileName,
            fileSizeBytes = fileSize,
            fileType = UniversalFileType.EXCEL,
            sourceUri = uri,
            cachedFile = file,
            sheets = sheets
        )
    }

    fun parseCsvSpreadsheet(
        file: File,
        uri: Uri?,
        fileName: String,
        fileSize: Long
    ): ParsedUniversalFile.ExcelWorkbook {
        val rawText = try {
            file.readText(Charsets.UTF_8).removePrefix("\uFEFF")
        } catch (_: Exception) {
            ""
        }
        val delimiter = if (fileName.endsWith(".tsv", true) || rawText.lines().firstOrNull()?.contains('\t') == true) '\t' else ','
        val rows = mutableListOf<List<String>>()
        var maxCols = 1

        for (line in rawText.lines()) {
            if (line.isBlank()) continue
            val parsedRow = parseCsvLine(line, delimiter)
            if (parsedRow.size > maxCols) maxCols = parsedRow.size
            rows.add(parsedRow)
        }

        val normalized = rows.map { r ->
            if (r.size < maxCols) r + List(maxCols - r.size) { "" } else r
        }

        val sheet = ExcelSheetData(
            sheetName = fileName.substringBeforeLast('.').take(28).ifBlank { "CSV_Data" },
            rows = if (normalized.isEmpty()) listOf(listOf("[Empty CSV]")) else normalized,
            maxColumns = maxCols
        )

        return ParsedUniversalFile.ExcelWorkbook(
            fileName = fileName,
            fileSizeBytes = fileSize,
            fileType = UniversalFileType.CSV,
            sourceUri = uri,
            cachedFile = file,
            sheets = listOf(sheet)
        )
    }

    private fun parseCsvLine(line: String, delimiter: Char): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        sb.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                ch == delimiter && !inQuotes -> {
                    result.add(sb.toString().trim())
                    sb.clear()
                }
                else -> sb.append(ch)
            }
            i++
        }
        result.add(sb.toString().trim())
        return result
    }

    private fun parseExcelColumnIndex(cellRef: String): Int {
        var col = 0
        for (ch in cellRef) {
            if (ch in 'A'..'Z') {
                col = col * 26 + (ch.code - 'A'.code + 1)
            } else if (ch in 'a'..'z') {
                col = col * 26 + (ch.code - 'a'.code + 1)
            } else {
                break
            }
        }
        return (col - 1).coerceAtLeast(0)
    }

    // =========================================================================
    // 3. MS PowerPoint (.pptx) Parser
    // =========================================================================
    fun parsePowerPointPptx(
        file: File,
        uri: Uri?,
        fileName: String,
        fileSize: Long
    ): ParsedUniversalFile.PowerPointDeck {
        val slides = mutableListOf<PowerPointSlideData>()
        try {
            ZipFile(file).use { zip ->
                val slideEntries = zip.entries().asSequence()
                    .filter { !it.isDirectory && it.name.matches(Regex("ppt/slides/slide\\d+\\.xml")) }
                    .sortedBy {
                        it.name.substringAfter("slide").substringBefore(".xml").toIntOrNull() ?: 0
                    }
                    .toList()

                slideEntries.forEachIndexed { idx, entry ->
                    val paragraphs = mutableListOf<String>()
                    zip.getInputStream(entry).use { slideStream ->
                        val parser = Xml.newPullParser()
                        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                        parser.setInput(slideStream, "UTF-8")
                        var inP = false
                        val pBuilder = StringBuilder()
                        var event = parser.eventType
                        while (event != XmlPullParser.END_DOCUMENT) {
                            val tag = parser.name?.substringAfter(':') ?: ""
                            when (event) {
                                XmlPullParser.START_TAG -> {
                                    if (tag == "p") {
                                        inP = true
                                        pBuilder.clear()
                                    } else if (inP && tag == "t") {
                                        pBuilder.append(parser.nextText())
                                    }
                                }
                                XmlPullParser.END_TAG -> {
                                    if (tag == "p" && inP) {
                                        val txt = pBuilder.toString().trim()
                                        if (txt.isNotEmpty()) paragraphs.add(txt)
                                        inP = false
                                    }
                                }
                            }
                            event = parser.next()
                        }
                    }
                    val slideTitle = paragraphs.firstOrNull() ?: "Slide ${idx + 1}"
                    val bullets = if (paragraphs.size > 1) paragraphs.drop(1) else emptyList()
                    slides.add(
                        PowerPointSlideData(
                            slideNumber = idx + 1,
                            title = slideTitle,
                            bulletPoints = bullets
                        )
                    )
                }
            }
        } catch (_: Exception) {
        }

        if (slides.isEmpty()) {
            slides.add(PowerPointSlideData(1, fileName, listOf("[No slide text found]")))
        }

        return ParsedUniversalFile.PowerPointDeck(
            fileName = fileName,
            fileSizeBytes = fileSize,
            sourceUri = uri,
            cachedFile = file,
            slides = slides
        )
    }

    // =========================================================================
    // 4. JPEG / PNG / WebP Image Document Parser
    // =========================================================================
    fun parseImageDocument(
        file: File,
        uri: Uri?,
        fileName: String,
        fileSize: Long
    ): ParsedUniversalFile.ImageDocument {
        // Read full dimensions first
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, boundsOpts)
        val rawW = boundsOpts.outWidth.coerceAtLeast(1)
        val rawH = boundsOpts.outHeight.coerceAtLeast(1)
        val mime = boundsOpts.outMimeType ?: "image/jpeg"
        val formatLabel = mime.substringAfter('/').uppercase(Locale.US)

        // Decode safely up to 2048px
        val bytes = file.readBytes()
        val bmp = decodeSampledBitmap(bytes, maxDim = 2048)
            ?: Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.LTGRAY) }

        return ParsedUniversalFile.ImageDocument(
            fileName = fileName,
            fileSizeBytes = fileSize,
            sourceUri = uri,
            cachedFile = file,
            bitmap = bmp,
            widthPx = rawW,
            heightPx = rawH,
            formatLabel = formatLabel
        )
    }

    // =========================================================================
    // 5. RTF & Text / Markdown / HTML Parser
    // =========================================================================
    private fun parseRtfDocument(
        file: File,
        uri: Uri?,
        fileName: String,
        fileSize: Long
    ): ParsedUniversalFile.WordDocument {
        val raw = try { file.readText(Charsets.UTF_8) } catch (_: Exception) { "" }
        val plain = stripRtfToPlainText(raw)
        val blocks = plain.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { WordDocBlock.Paragraph(it) }
            .ifEmpty { listOf(WordDocBlock.Paragraph("[Empty RTF Document]")) }

        return ParsedUniversalFile.WordDocument(
            fileName = fileName,
            fileSizeBytes = fileSize,
            fileType = UniversalFileType.RTF,
            sourceUri = uri,
            cachedFile = file,
            blocks = blocks,
            plainText = plain,
            wordCount = plain.split(Regex("\\s+")).count { it.isNotBlank() },
            paragraphCount = blocks.size,
            tableCount = 0
        )
    }

    private fun parsePlainTextOrHtmlDocument(
        file: File,
        uri: Uri?,
        fileName: String,
        fileSize: Long,
        fileType: UniversalFileType
    ): ParsedUniversalFile.WordDocument {
        val raw = try { file.readText(Charsets.UTF_8) } catch (_: Exception) { "" }
        val cleaned = if (fileName.endsWith(".html", true) || fileName.endsWith(".htm", true)) {
            raw.replace(Regex("<style[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
                .replace(Regex("<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
                .replace(Regex("</(p|div|h1|h2|h3|li|tr|section)>", RegexOption.IGNORE_CASE), "\n")
                .replace(Regex("<[^>]+>"), "")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
        } else {
            raw
        }

        val blocks = mutableListOf<WordDocBlock>()
        for (line in cleaned.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            when {
                trimmed.startsWith("# ") -> blocks.add(WordDocBlock.Heading(trimmed.removePrefix("# ").trim(), 1))
                trimmed.startsWith("## ") -> blocks.add(WordDocBlock.Heading(trimmed.removePrefix("## ").trim(), 2))
                trimmed.startsWith("### ") -> blocks.add(WordDocBlock.Heading(trimmed.removePrefix("### ").trim(), 3))
                trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("• ") ->
                    blocks.add(WordDocBlock.Paragraph(trimmed.drop(2).trim(), isBullet = true))
                else -> blocks.add(WordDocBlock.Paragraph(trimmed))
            }
        }
        if (blocks.isEmpty()) {
            blocks.add(WordDocBlock.Paragraph("[Empty Document]"))
        }

        return ParsedUniversalFile.WordDocument(
            fileName = fileName,
            fileSizeBytes = fileSize,
            fileType = fileType,
            sourceUri = uri,
            cachedFile = file,
            blocks = blocks,
            plainText = cleaned.trim(),
            wordCount = cleaned.split(Regex("\\s+")).count { it.isNotBlank() },
            paragraphCount = blocks.size,
            tableCount = 0
        )
    }

    private fun stripRtfToPlainText(rtf: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < rtf.length) {
            val c = rtf[i]
            if (c == '\\') {
                if (i + 1 < rtf.length) {
                    val next = rtf[i + 1]
                    if (next == '\\' || next == '{' || next == '}') {
                        sb.append(next)
                        i += 2
                        continue
                    }
                    // Check \uN?
                    val sub = rtf.substring(i + 1)
                    val uMatch = Regex("^u(-?\\d+)\\??").find(sub)
                    if (uMatch != null) {
                        val code = uMatch.groupValues[1].toIntOrNull()
                        if (code != null) {
                            val charCode = if (code < 0) code + 65536 else code
                            sb.append(charCode.toChar())
                        }
                        i += 1 + uMatch.value.length
                        continue
                    }
                    val ctrlMatch = Regex("^[a-zA-Z]+(-?\\d+)?\\s?").find(sub)
                    if (ctrlMatch != null) {
                        val word = ctrlMatch.value.trim()
                        if (word == "par" || word == "line" || word == "page") {
                            sb.append('\n')
                        } else if (word == "tab") {
                            sb.append('\t')
                        }
                        i += 1 + ctrlMatch.value.length
                        continue
                    }
                }
                i++
            } else if (c == '{' || c == '}') {
                i++
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
            .replace(Regex("fonttbl[^\\n]*"), "")
            .replace(Regex("colortbl[^\\n]*"), "")
            .trim()
    }

    /**
     * Converts any currently viewed Word (.docx), Excel (.xlsx/.csv), PowerPoint (.pptx), or JPEG/PNG image
     * into a standardized PDF file so the user can immediately open or share it as a PDF!
     */
    suspend fun exportViewedFileToPdf(parsed: ParsedUniversalFile): File = withContext(Dispatchers.IO) {
        val outDir = File(context.cacheDir, "converted_pdfs").apply { mkdirs() }
        val baseName = parsed.fileName.substringBeforeLast('.').ifBlank { "Converted" }
        val outFile = File(outDir, "${baseName}_converted.pdf")

        PDDocument().use { doc ->
            when (parsed) {
                is ParsedUniversalFile.ImageDocument -> {
                    val bmp = parsed.bitmap
                    val pdRect = PDRectangle.A4
                    val page = PDPage(pdRect)
                    doc.addPage(page)
                    val scale = min(pdRect.width / bmp.width.coerceAtLeast(1), pdRect.height / bmp.height.coerceAtLeast(1))
                    val drawW = bmp.width * scale
                    val drawH = bmp.height * scale
                    val drawX = (pdRect.width - drawW) / 2f
                    val drawY = (pdRect.height - drawH) / 2f
                    val pdImg = JPEGFactory.createFromImage(doc, bmp, 0.9f)
                    PDPageContentStream(doc, page).use { cs ->
                        cs.drawImage(pdImg, drawX, drawY, drawW, drawH)
                    }
                }
                is ParsedUniversalFile.WordDocument -> {
                    writeLinesToPdfPages(doc, parsed.fileName, parsed.plainText.lines())
                }
                is ParsedUniversalFile.ExcelWorkbook -> {
                    val lines = mutableListOf<String>()
                    for (sheet in parsed.sheets) {
                        lines.add("=== Sheet: ${sheet.sheetName} ===")
                        sheet.rows.forEach { row ->
                            lines.add(row.filter { it.isNotBlank() }.joinToString("   |   "))
                        }
                        lines.add("")
                    }
                    writeLinesToPdfPages(doc, parsed.fileName, lines)
                }
                is ParsedUniversalFile.PowerPointDeck -> {
                    val lines = mutableListOf<String>()
                    for (slide in parsed.slides) {
                        lines.add("Slide ${slide.slideNumber}: ${slide.title}")
                        slide.bulletPoints.forEach { b -> lines.add("  • $b") }
                        lines.add("")
                    }
                    writeLinesToPdfPages(doc, parsed.fileName, lines)
                }
            }
            doc.save(outFile)
        }
        outFile
    }

    private fun writeLinesToPdfPages(doc: PDDocument, title: String, rawLines: List<String>) {
        val wrappedLines = mutableListOf<String>()
        wrappedLines.add(title)
        wrappedLines.add("")
        for (line in rawLines) {
            val safe = sanitizeWinAnsi(line)
            if (safe.length <= 85) {
                wrappedLines.add(safe)
            } else {
                safe.chunked(85).forEach { wrappedLines.add(it) }
            }
        }

        val linesPerPage = 44
        val chunks = wrappedLines.chunked(linesPerPage).ifEmpty { listOf(listOf(sanitizeWinAnsi(title))) }
        for (pageLines in chunks) {
            val page = PDPage(PDRectangle.A4)
            doc.addPage(page)
            PDPageContentStream(doc, page).use { cs ->
                var y = 785f
                for (line in pageLines) {
                    cs.beginText()
                    cs.setFont(PDType1Font.HELVETICA, 11f)
                    cs.newLineAtOffset(45f, y)
                    cs.showText(sanitizeWinAnsi(line))
                    cs.endText()
                    y -= 16f
                }
            }
        }
    }

    private fun sanitizeWinAnsi(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            if (ch.code in 32..126 || ch.code in 160..255) {
                sb.append(ch)
            } else {
                sb.append(' ')
            }
        }
        return sb.toString()
    }

    private fun decodeSampledBitmap(bytes: ByteArray, maxDim: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sampleSize = 1
            while (bounds.outWidth / sampleSize > maxDim || bounds.outHeight / sampleSize > maxDim) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
        } catch (_: Throwable) {
            null
        }
    }

    private fun extractReadableStringsFromFile(file: File): String {
        return try {
            val bytes = FileInputStream(file).use { it.readBytes() }
            val sb = StringBuilder()
            val currentRun = StringBuilder()
            for (b in bytes) {
                val code = b.toInt() and 0xFF
                if (code in 32..126 || code == 10 || code == 13) {
                    currentRun.append(code.toChar())
                } else {
                    if (currentRun.length >= 5) {
                        sb.append(currentRun).append('\n')
                    }
                    currentRun.clear()
                }
            }
            if (currentRun.length >= 5) sb.append(currentRun)
            sb.toString()
        } catch (_: Exception) {
            ""
        }
    }

    private fun getAttrIgnoreNamespace(parser: XmlPullParser, attrLocalName: String): String? {
        for (i in 0 until parser.attributeCount) {
            val name = parser.getAttributeName(i)?.substringAfter(':') ?: ""
            if (name.equals(attrLocalName, ignoreCase = true)) {
                return parser.getAttributeValue(i)
            }
        }
        return null
    }
}
