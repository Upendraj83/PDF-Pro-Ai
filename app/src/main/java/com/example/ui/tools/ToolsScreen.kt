package com.example.ui.tools

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.GTranslate
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.WaterDrop
import com.example.ai.AiToolTab
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pdf.OperationProgress
import com.example.pdf.PdfExportFormat
import com.example.ui.PdfViewModel
import com.example.ui.scan.ExtractTextDialog
import com.example.viewer.UniversalFileParserEngine
import java.io.File

enum class ToolActionType {
    OPEN_UNIVERSAL_FILE,
    CONTENT_EDIT,
    FILL_AND_SIGN,
    ROTATE_PAGES,
    ORGANIZE_PAGES,
    MERGE,
    SPLIT,
    EXTRACT,
    PDF_TO_WORD,
    PDF_TO_EXCEL,
    PDF_TO_PPT,
    PDF_TO_TEXT_HTML,
    IMAGE_TO_PDF,
    PDF_TO_IMAGES,
    CROP_RESIZE,
    COMPRESS,
    PROTECT,
    UNLOCK,
    METADATA,
    WATERMARK,
    PRINT_PDF,
    OCR_EXTRACT,
    AI_CHAT,
    AI_SUMMARY,
    AI_TRANSLATE,
    AI_EXPLAIN
}

data class ToolDefinition(
    val title: String,
    val description: String,
    val category: String,
    val badge: String,
    val icon: ImageVector,
    val actionType: ToolActionType,
    val isAi: Boolean = false
)

@Composable
fun ToolsScreen(
    viewModel: PdfViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }

    // Dialog state controllers
    var showMergeDialog by remember { mutableStateOf(false) }
    var showSplitDialog by remember { mutableStateOf(false) }
    var showExtractDialog by remember { mutableStateOf(false) }
    var showRotatePagesDialog by remember { mutableStateOf(false) }
    var showImageToPdfDialog by remember { mutableStateOf(false) }
    var showPdfToImagesDialog by remember { mutableStateOf(false) }
    var showCropResizeDialog by remember { mutableStateOf(false) }
    var showCompressDialog by remember { mutableStateOf(false) }
    var showProtectDialog by remember { mutableStateOf(false) }
    var showUnlockDialog by remember { mutableStateOf(false) }
    var showMetadataDialog by remember { mutableStateOf(false) }
    var showWatermarkDialog by remember { mutableStateOf(false) }
    var showPrintDialog by remember { mutableStateOf(false) }
    var showOcrExtractDialog by remember { mutableStateOf(false) }
    var showPdfToOfficeDialog by remember { mutableStateOf(false) }
    var initialOfficeFormat by remember { mutableStateOf(PdfExportFormat.WORD_DOCX) }

    // Standalone Page Organizer launcher for Tools tab
    var organizerUri by remember { mutableStateOf<Uri?>(null) }
    var organizerName by remember { mutableStateOf<String?>(null) }

    val pickPdfForOrganizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            organizerUri = uri
            organizerName = queryFileName(context, uri) ?: "Selected_Document.pdf"
        }
    }

    val pickPdfForSignLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.openPdf(uri)
            viewModel.setFillAndSignActive(true)
        }
    }

    val pickPdfForContentEditLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.openPdf(uri)
            viewModel.setContentEditActive(true)
        }
    }

    var pendingAiTabForPicker by remember { mutableStateOf(AiToolTab.SUMMARY) }
    val pickPdfForAiLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.openPdfAndLaunchAiTab(uri, pendingAiTabForPicker)
        }
    }

    val pickUniversalFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.openUniversalOrPdfFile(uri)
        }
    }

    val operationProgress by viewModel.operationProgress.collectAsStateWithLifecycle()

    val toolsList = remember {
        listOf(
            ToolDefinition(
                title = "Word, Excel & JPEG Viewer",
                description = "Open and view MS Word (.docx/.rtf), MS Excel (.xlsx/.csv) spreadsheets, PowerPoint (.pptx), and JPEG/PNG images with 1-tap Save as PDF.",
                category = "Page Tools",
                badge = "DOCX • XLSX • JPG",
                icon = Icons.Default.MenuBook,
                actionType = ToolActionType.OPEN_UNIVERSAL_FILE
            ),
            ToolDefinition(
                title = "Edit Content & Redact",
                description = "Add text (Noto Sans, বাংলা Bengali, Standard fonts), replace existing text lines, add/crop images, links, and permanently redact.",
                category = "Page Tools",
                badge = "Pro Editor",
                icon = Icons.Default.EditNote,
                actionType = ToolActionType.CONTENT_EDIT
            ),
            ToolDefinition(
                title = "Fill & Sign",
                description = "Fill interactive AcroForm fields, place signatures, initials, checkmarks, and custom stamps.",
                category = "Page Tools",
                badge = "Interactive",
                icon = Icons.Default.Draw,
                actionType = ToolActionType.FILL_AND_SIGN
            ),
            ToolDefinition(
                title = "Rotate PDF Pages",
                description = "Rotate selected pages, odd/even pages, or custom ranges by 90° increments (+90°, -90°, 180°) and save to file.",
                category = "Page Tools",
                badge = "90° Step",
                icon = Icons.Default.RotateRight,
                actionType = ToolActionType.ROTATE_PAGES
            ),
            ToolDefinition(
                title = "Organize Pages",
                description = "Reorder, rotate, delete, duplicate, insert blank pages, and combine pages.",
                category = "Page Tools",
                badge = "Interactive",
                icon = Icons.Default.ViewCarousel,
                actionType = ToolActionType.ORGANIZE_PAGES
            ),
            ToolDefinition(
                title = "Merge Documents",
                description = "Combine multiple PDF files and images into one document with custom ranges.",
                category = "Organize",
                badge = "Batch",
                icon = Icons.Default.CallMerge,
                actionType = ToolActionType.MERGE
            ),
            ToolDefinition(
                title = "Split Pages",
                description = "Split by page ranges, every N pages, or bookmarks into ZIP or folders.",
                category = "Organize",
                badge = "Flexible",
                icon = Icons.Default.CallSplit,
                actionType = ToolActionType.SPLIT
            ),
            ToolDefinition(
                title = "Extract Pages",
                description = "Isolate and save selected page numbers as a new clean PDF.",
                category = "Page Tools",
                badge = "Quick",
                icon = Icons.Default.ContentCopy,
                actionType = ToolActionType.EXTRACT
            ),
            ToolDefinition(
                title = "PDF to Word (.docx)",
                description = "Convert PDF pages into editable MS Word (.docx) documents with headings, tables, and ML Kit OCR.",
                category = "Convert",
                badge = "MS Word",
                icon = Icons.Default.Description,
                actionType = ToolActionType.PDF_TO_WORD
            ),
            ToolDefinition(
                title = "PDF to Excel & CSV",
                description = "Extract tables and structured rows into Microsoft Excel (.xlsx) workbooks or UTF-8 CSV.",
                category = "Convert",
                badge = "XLSX / CSV",
                icon = Icons.Default.TableChart,
                actionType = ToolActionType.PDF_TO_EXCEL
            ),
            ToolDefinition(
                title = "PDF to PowerPoint (.pptx)",
                description = "Transform PDF pages into widescreen Microsoft PowerPoint (.pptx) presentation slides.",
                category = "Convert",
                badge = "PPTX",
                icon = Icons.Default.Slideshow,
                actionType = ToolActionType.PDF_TO_PPT
            ),
            ToolDefinition(
                title = "PDF to RTF, HTML & Text",
                description = "Export PDF content to Rich Text (.rtf), styled Web Page (.html), Markdown (.md), or Plain Text (.txt).",
                category = "Convert",
                badge = "Multi-Format",
                icon = Icons.Default.Article,
                actionType = ToolActionType.PDF_TO_TEXT_HTML
            ),
            ToolDefinition(
                title = "Images to PDF",
                description = "Convert gallery photos and camera snapshots into a standardized PDF.",
                category = "Convert",
                badge = "High Res",
                icon = Icons.Default.Image,
                actionType = ToolActionType.IMAGE_TO_PDF
            ),
            ToolDefinition(
                title = "PDF to Images",
                description = "Render and export document pages as JPG or PNG images at 72, 150, or 300 DPI.",
                category = "Convert",
                badge = "Lossless",
                icon = Icons.Default.Image,
                actionType = ToolActionType.PDF_TO_IMAGES
            ),
            ToolDefinition(
                title = "Crop & Resize",
                description = "Trim page borders and standardize page dimensions (A4, Letter, Legal).",
                category = "Page Tools",
                badge = "Trim",
                icon = Icons.Default.Crop,
                actionType = ToolActionType.CROP_RESIZE
            ),
            ToolDefinition(
                title = "AI PDF Chat",
                description = "Chat with your document using chunked retrieval and interactive [Page N] jump references.",
                category = "AI Intelligence",
                badge = "Gemini AI",
                icon = Icons.Default.Chat,
                actionType = ToolActionType.AI_CHAT,
                isAi = true
            ),
            ToolDefinition(
                title = "AI Executive Summary",
                description = "Summarize the whole PDF or a page range with key points in English or Bengali (বাংলা).",
                category = "AI Intelligence",
                badge = "Gemini AI",
                icon = Icons.Default.AutoAwesome,
                actionType = ToolActionType.AI_SUMMARY,
                isAi = true
            ),
            ToolDefinition(
                title = "AI Side-by-Side Translate",
                description = "Translate selected text or page ranges between English and Bengali, exportable as PDF or TXT.",
                category = "AI Intelligence",
                badge = "EN ↔ বাংলা",
                icon = Icons.Default.GTranslate,
                actionType = ToolActionType.AI_TRANSLATE,
                isAi = true
            ),
            ToolDefinition(
                title = "AI Explain & Rewrite",
                description = "Select text in the viewer and ask AI to explain it simply or rewrite it clearly.",
                category = "AI Intelligence",
                badge = "Gemini AI",
                icon = Icons.Default.Psychology,
                actionType = ToolActionType.AI_EXPLAIN,
                isAi = true
            ),
            ToolDefinition(
                title = "AI OCR & Text Extract",
                description = "Extract selectable and searchable text from scanned pages and receipts.",
                category = "AI Intelligence",
                badge = "Accurate",
                icon = Icons.Default.TextFields,
                actionType = ToolActionType.OCR_EXTRACT,
                isAi = true
            ),
            ToolDefinition(
                title = "Compress PDF",
                description = "Shrink bloated PDF documents with presets (Low, Medium, High) or custom target size.",
                category = "Organize",
                badge = "Save Space",
                icon = Icons.Default.Compress,
                actionType = ToolActionType.COMPRESS
            ),
            ToolDefinition(
                title = "Encrypt & Protect",
                description = "Set passwords and fine-grained permissions (print, copy, edit) to secure documents.",
                category = "Organize",
                badge = "AES-128",
                icon = Icons.Default.Lock,
                actionType = ToolActionType.PROTECT
            ),
            ToolDefinition(
                title = "Unlock PDF",
                description = "Decrypt password-protected PDFs or permanently remove security.",
                category = "Organize",
                badge = "Decrypt",
                icon = Icons.Default.LockOpen,
                actionType = ToolActionType.UNLOCK
            ),
            ToolDefinition(
                title = "Metadata & Sanitize",
                description = "Inspect and edit document properties, or sanitize metadata and attachments before sharing.",
                category = "Organize",
                badge = "Privacy",
                icon = Icons.Default.Description,
                actionType = ToolActionType.METADATA
            ),
            ToolDefinition(
                title = "Watermark & Numbers",
                description = "Stamp custom text/image watermarks, add headers, footers, and page numbering.",
                category = "Page Tools",
                badge = "Branding",
                icon = Icons.Default.WaterDrop,
                actionType = ToolActionType.WATERMARK
            ),
            ToolDefinition(
                title = "Print PDF",
                description = "Print all pages or custom page ranges with paper size, color mode, and optional print watermark.",
                category = "Page Tools",
                badge = "System Print",
                icon = Icons.Default.Print,
                actionType = ToolActionType.PRINT_PDF
            )
        )
    }

    val categories = remember {
        listOf("All", "Page Tools", "Organize", "Convert", "AI Intelligence")
    }

    val filteredTools = remember(searchQuery, selectedCategory) {
        toolsList.filter { tool ->
            val matchesCategory = selectedCategory == "All" || tool.category == selectedCategory
            val matchesSearch = searchQuery.isBlank() ||
                tool.title.contains(searchQuery, ignoreCase = true) ||
                tool.description.contains(searchQuery, ignoreCase = true)
            matchesCategory && matchesSearch
        }
    }

    // Active Standalone Page Organizer launched from Tools tab
    if (organizerUri != null && organizerName != null) {
        PageOrganizerScreen(
            docUri = organizerUri!!,
            docName = organizerName!!,
            viewModel = viewModel,
            onClose = {
                organizerUri = null
                organizerName = null
            }
        )
        return
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 160.dp),
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header & Search Bar
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(modifier = Modifier.padding(bottom = 4.dp)) {
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.example.R.string.tools_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.example.R.string.tools_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    // Search input
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(androidx.compose.ui.res.stringResource(com.example.R.string.tools_search_placeholder)) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("tools_search_input")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Video-Style Underline Category Tabs (All | Page Tools | Organize | Convert | AI Intelligence)
                    val selectedCatIdx = categories.indexOf(selectedCategory).coerceAtLeast(0)
                    ScrollableTabRow(
                        selectedTabIndex = selectedCatIdx,
                        edgePadding = 0.dp,
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.primary
                    ) {
                        categories.forEach { cat ->
                            val isSelected = selectedCategory == cat
                            Tab(
                                selected = isSelected,
                                onClick = { selectedCategory = cat },
                                text = {
                                    Text(
                                        text = cat,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("category_chip_$cat")
                            )
                        }
                    }
                }
            }

            // Grid Items
            items(filteredTools) { tool ->
                ToolCard(
                    tool = tool,
                    onClick = {
                        when (tool.actionType) {
                            ToolActionType.OPEN_UNIVERSAL_FILE -> {
                                pickUniversalFileLauncher.launch(UniversalFileParserEngine.ALL_SUPPORTED_OPEN_MIME_TYPES)
                            }
                            ToolActionType.CONTENT_EDIT -> {
                                val currentUri = viewModel.currentViewingUri
                                if (currentUri != null) {
                                    viewModel.setContentEditActive(true)
                                } else {
                                    pickPdfForContentEditLauncher.launch(arrayOf("application/pdf"))
                                }
                            }
                            ToolActionType.FILL_AND_SIGN -> {
                                val currentUri = viewModel.currentViewingUri
                                if (currentUri != null) {
                                    viewModel.setFillAndSignActive(true)
                                } else {
                                    pickPdfForSignLauncher.launch(arrayOf("application/pdf"))
                                }
                            }
                            ToolActionType.ROTATE_PAGES -> {
                                showRotatePagesDialog = true
                            }
                            ToolActionType.ORGANIZE_PAGES -> {
                                val currentUri = viewModel.currentViewingUri
                                val currentName = viewModel.currentViewingName
                                if (currentUri != null && currentName != null) {
                                    organizerUri = currentUri
                                    organizerName = currentName
                                } else {
                                    pickPdfForOrganizerLauncher.launch(arrayOf("application/pdf"))
                                }
                            }
                            ToolActionType.MERGE -> {
                                showMergeDialog = true
                            }
                            ToolActionType.SPLIT -> {
                                showSplitDialog = true
                            }
                            ToolActionType.EXTRACT -> {
                                showExtractDialog = true
                            }
                            ToolActionType.PDF_TO_WORD -> {
                                initialOfficeFormat = PdfExportFormat.WORD_DOCX
                                showPdfToOfficeDialog = true
                            }
                            ToolActionType.PDF_TO_EXCEL -> {
                                initialOfficeFormat = PdfExportFormat.EXCEL_XLSX
                                showPdfToOfficeDialog = true
                            }
                            ToolActionType.PDF_TO_PPT -> {
                                initialOfficeFormat = PdfExportFormat.POWERPOINT_PPTX
                                showPdfToOfficeDialog = true
                            }
                            ToolActionType.PDF_TO_TEXT_HTML -> {
                                initialOfficeFormat = PdfExportFormat.RTF_DOCUMENT
                                showPdfToOfficeDialog = true
                            }
                            ToolActionType.IMAGE_TO_PDF -> {
                                showImageToPdfDialog = true
                            }
                            ToolActionType.PDF_TO_IMAGES -> {
                                showPdfToImagesDialog = true
                            }
                            ToolActionType.CROP_RESIZE -> {
                                showCropResizeDialog = true
                            }
                            ToolActionType.COMPRESS -> {
                                showCompressDialog = true
                            }
                            ToolActionType.PROTECT -> {
                                showProtectDialog = true
                            }
                            ToolActionType.UNLOCK -> {
                                showUnlockDialog = true
                            }
                            ToolActionType.METADATA -> {
                                showMetadataDialog = true
                            }
                            ToolActionType.WATERMARK -> {
                                showWatermarkDialog = true
                            }
                            ToolActionType.PRINT_PDF -> {
                                showPrintDialog = true
                            }
                            ToolActionType.OCR_EXTRACT -> {
                                showOcrExtractDialog = true
                            }
                            ToolActionType.AI_CHAT -> {
                                val currentUri = viewModel.currentViewingUri
                                if (currentUri != null) {
                                    viewModel.openAiAssistant(AiToolTab.CHAT)
                                } else {
                                    pendingAiTabForPicker = AiToolTab.CHAT
                                    pickPdfForAiLauncher.launch(arrayOf("application/pdf"))
                                }
                            }
                            ToolActionType.AI_SUMMARY -> {
                                val currentUri = viewModel.currentViewingUri
                                if (currentUri != null) {
                                    viewModel.openAiAssistant(AiToolTab.SUMMARY)
                                } else {
                                    pendingAiTabForPicker = AiToolTab.SUMMARY
                                    pickPdfForAiLauncher.launch(arrayOf("application/pdf"))
                                }
                            }
                            ToolActionType.AI_TRANSLATE -> {
                                val currentUri = viewModel.currentViewingUri
                                if (currentUri != null) {
                                    viewModel.openAiAssistant(AiToolTab.TRANSLATE)
                                } else {
                                    pendingAiTabForPicker = AiToolTab.TRANSLATE
                                    pickPdfForAiLauncher.launch(arrayOf("application/pdf"))
                                }
                            }
                            ToolActionType.AI_EXPLAIN -> {
                                val currentUri = viewModel.currentViewingUri
                                if (currentUri != null) {
                                    viewModel.openAiAssistant(AiToolTab.EXPLAIN)
                                } else {
                                    pendingAiTabForPicker = AiToolTab.EXPLAIN
                                    pickPdfForAiLauncher.launch(arrayOf("application/pdf"))
                                }
                            }
                        }
                    }
                )
            }
        }
    }

    // Merge Dialog
    if (showMergeDialog) {
        MergeDialog(
            initialPdfUri = viewModel.currentViewingUri,
            initialPdfName = viewModel.currentViewingName,
            onMergeConfirmed = { items, name ->
                showMergeDialog = false
                viewModel.executeMerge(items, name)
            },
            onDismiss = { showMergeDialog = false }
        )
    }

    // Split Dialog
    if (showSplitDialog) {
        SplitDialog(
            initialUri = viewModel.currentViewingUri,
            initialName = viewModel.currentViewingName,
            onSplitConfirmed = { uri, name, mode, dest, folderUri ->
                showSplitDialog = false
                viewModel.executeSplit(uri, name, mode, dest, folderUri)
            },
            onDismiss = { showSplitDialog = false }
        )
    }

    // Extract Pages Dialog
    if (showExtractDialog) {
        ExtractPagesDialog(
            initialUri = viewModel.currentViewingUri,
            initialName = viewModel.currentViewingName,
            totalDocPages = viewModel.currentViewingPageCount,
            onExtractConfirmed = { uri, indices, name ->
                showExtractDialog = false
                viewModel.executeExtract(uri, indices, name)
            },
            onDismiss = { showExtractDialog = false }
        )
    }

    // Rotate PDF Pages Dialog (90-degree increments)
    if (showRotatePagesDialog) {
        RotatePdfPagesDialog(
            initialUri = viewModel.currentViewingUri,
            initialName = viewModel.currentViewingName,
            initialCachedFile = viewModel.currentViewingCachedFile,
            initialCurrentPage = viewModel.currentPage.value,
            onRotateConfirmed = { srcFile, srcUri, srcName, deltas, overwrite, outName ->
                showRotatePagesDialog = false
                viewModel.executeRotateSelectedPages(
                    sourceFile = srcFile,
                    sourceUri = srcUri,
                    sourceName = srcName,
                    pageRotationDeltas = deltas,
                    overwriteOriginal = overwrite,
                    outputName = outName
                )
            },
            onDismiss = { showRotatePagesDialog = false }
        )
    }

    // Images to PDF Dialog
    if (showImageToPdfDialog) {
        ImageToPdfDialog(
            onConvertConfirmed = { imgs, size, margin, orient, qual ->
                showImageToPdfDialog = false
                viewModel.executeImagesToPdf(imgs, size, margin, orient, qual)
            },
            onDismiss = { showImageToPdfDialog = false }
        )
    }

    // PDF to Images Dialog
    if (showPdfToImagesDialog) {
        PdfToImagesDialog(
            initialUri = viewModel.currentViewingUri,
            initialName = viewModel.currentViewingName,
            totalDocPages = viewModel.currentViewingPageCount,
            onExportConfirmed = { uri, name, indices, format, dpi, dest, folderUri ->
                showPdfToImagesDialog = false
                viewModel.executePdfToImages(uri, name, indices, format, dpi, dest, folderUri)
            },
            onDismiss = { showPdfToImagesDialog = false }
        )
    }

    // PDF to MS Word (.docx), Excel (.xlsx), PowerPoint (.pptx), RTF, CSV, HTML, MD & TXT Dialog
    if (showPdfToOfficeDialog) {
        PdfToWordOfficeDialog(
            initialUri = viewModel.currentViewingUri,
            initialFileName = viewModel.currentViewingName,
            initialCachedFile = viewModel.currentViewingCachedFile,
            initialFormat = initialOfficeFormat,
            onConvertConfirmed = { srcFile, srcUri, srcName, config ->
                showPdfToOfficeDialog = false
                viewModel.executePdfToWordOrOffice(srcFile, srcUri, srcName, config)
            },
            onDismiss = { showPdfToOfficeDialog = false }
        )
    }

    // Crop & Custom Bounding Box Dialog
    if (showCropResizeDialog) {
        PdfCropDialog(
            initialUri = viewModel.currentViewingUri,
            initialName = viewModel.currentViewingName,
            initialPageIndex = viewModel.currentPage.value,
            totalPageCount = viewModel.currentViewingPageCount.coerceAtLeast(1),
            cachedFile = viewModel.currentViewingCachedFile,
            getPageBitmap = { pIdx, w -> viewModel.getPageBitmap(pIdx, w) },
            onApplyCrop = { pageIdx, normalizedBox, applyToAll, onDone ->
                val srcUri = viewModel.currentViewingUri
                if (srcUri != null) {
                    viewModel.cropDocumentExternal(srcUri, pageIdx, normalizedBox, applyToAll) { croppedFile ->
                        onDone(croppedFile)
                    }
                } else {
                    showCropResizeDialog = false
                }
            },
            onOpenInViewer = { croppedFile ->
                showCropResizeDialog = false
                viewModel.reloadEditedPdfInViewer(croppedFile)
            },
            onDismiss = { showCropResizeDialog = false }
        )
    }

    // Compress PDF Dialog
    if (showCompressDialog) {
        CompressPdfDialog(
            initialUri = viewModel.currentViewingUri,
            initialFileName = viewModel.currentViewingName,
            viewModel = viewModel,
            onDismiss = { showCompressDialog = false }
        )
    }

    // Protect PDF Dialog
    if (showProtectDialog) {
        ProtectPdfDialog(
            initialUri = viewModel.currentViewingUri,
            initialFileName = viewModel.currentViewingName,
            viewModel = viewModel,
            onDismiss = { showProtectDialog = false }
        )
    }

    // Unlock PDF Dialog
    if (showUnlockDialog) {
        UnlockPdfDialog(
            initialUri = viewModel.currentViewingUri,
            initialFileName = viewModel.currentViewingName,
            viewModel = viewModel,
            onDismiss = { showUnlockDialog = false }
        )
    }

    // Metadata & Sanitize Dialog
    if (showMetadataDialog) {
        MetadataEditorDialog(
            initialUri = viewModel.currentViewingUri,
            initialFileName = viewModel.currentViewingName,
            viewModel = viewModel,
            onDismiss = { showMetadataDialog = false }
        )
    }

    // Watermark & Numbers Dialog
    if (showWatermarkDialog) {
        WatermarkDialog(
            initialUri = viewModel.currentViewingUri,
            initialFileName = viewModel.currentViewingName,
            viewModel = viewModel,
            onDismiss = { showWatermarkDialog = false }
        )
    }

    // Print PDF Dialog
    if (showPrintDialog) {
        PrintPdfDialog(
            initialUri = viewModel.currentViewingUri,
            initialFileName = viewModel.currentViewingName,
            initialFile = viewModel.currentViewingCachedFile,
            currentPageIndex = viewModel.currentPage.value,
            onDismiss = { showPrintDialog = false }
        )
    }

    // ML Kit OCR Extract Text Dialog (supports Scanned PDFs & Images)
    if (showOcrExtractDialog) {
        ExtractTextDialog(
            onDismiss = { showOcrExtractDialog = false }
        )
    }

    // Operation Running Progress Modal
    if (operationProgress is OperationProgress.Running) {
        OperationProgressDialog(
            progress = operationProgress as OperationProgress.Running,
            onCancel = { viewModel.cancelActiveOperation() }
        )
    }

    // Operation Success Result Modal with Open & Share
    if (operationProgress is OperationProgress.Success) {
        OperationResultDialog(
            result = operationProgress as OperationProgress.Success,
            onOpenPdf = { file ->
                viewModel.openPdf(Uri.fromFile(file))
            },
            onDismiss = { viewModel.dismissOperationDialog() }
        )
    }

    // Operation Failure Dialog
    if (operationProgress is OperationProgress.Failure) {
        OperationFailureDialog(
            failure = operationProgress as OperationProgress.Failure,
            onDismiss = { viewModel.dismissOperationDialog() }
        )
    }
}

@Composable
private fun ToolCard(
    tool: ToolDefinition,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (pastelBg, iconTint) = when {
        tool.isAi -> Color(0xFFF3E5F5) to Color(0xFF7B1FA2)
        tool.actionType == ToolActionType.FILL_AND_SIGN -> Color(0xFFFFF3E0) to Color(0xFFE65100)
        tool.actionType == ToolActionType.CONTENT_EDIT -> Color(0xFFFFEBEE) to Color(0xFFD32F2F)
        tool.actionType == ToolActionType.CROP_RESIZE -> Color(0xFFE0F7FA) to Color(0xFF00838F)
        tool.category == "Convert" -> Color(0xFFFBE9E7) to Color(0xFFD84315)
        tool.category == "Organize" -> Color(0xFFEDE7F6) to Color(0xFF5E35B1)
        else -> Color(0xFFE3F2FD) to Color(0xFF1565C0)
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("tool_card_${tool.title}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(pastelBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = tool.icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (tool.isAi) Color(0xFFEDE7F6) else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = tool.badge,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (tool.isAi) Color(0xFF5E35B1) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Text(
                text = tool.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = tool.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
    }
}

