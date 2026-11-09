package com.example.ui.viewer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Brightness7
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.ui.common.formatFileSize
import com.example.viewer.ParsedUniversalFile
import com.example.viewer.UniversalFileType
import com.example.viewer.WordDocBlock
import kotlinx.coroutines.launch
import java.io.File

sealed interface UniversalViewerState {
    data object Idle : UniversalViewerState
    data class Loading(val fileName: String) : UniversalViewerState
    data class Viewing(val parsedFile: ParsedUniversalFile) : UniversalViewerState
    data class Error(val fileName: String, val message: String) : UniversalViewerState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UniversalFileViewerScreen(
    state: UniversalViewerState,
    onClose: () -> Unit,
    onConvertAndOpenAsPdf: (ParsedUniversalFile) -> Unit,
    onRunOcrOnBitmap: suspend (Bitmap) -> String,
    modifier: Modifier = Modifier
) {
    BackHandler {
        onClose()
    }

    val context = LocalContext.current
    var isDarkViewer by remember { mutableStateOf(false) }
    var isSearchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var textScale by remember { mutableFloatStateOf(1.0f) }
    var isConvertingToPdf by remember { mutableStateOf(false) }

    when (state) {
        is UniversalViewerState.Idle -> Unit
        is UniversalViewerState.Loading -> {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(state.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        navigationIcon = {
                            IconButton(
                                onClick = onClose,
                                modifier = Modifier.testTag("universal_viewer_back_btn")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    )
                }
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Opening ${state.fileName}...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
        is UniversalViewerState.Error -> {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(state.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        navigationIcon = {
                            IconButton(
                                onClick = onClose,
                                modifier = Modifier.testTag("universal_viewer_back_btn")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    )
                }
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Unable to Open File",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(onClick = onClose) {
                            Text("Go Back")
                        }
                    }
                }
            }
        }
        is UniversalViewerState.Viewing -> {
            val parsed = state.parsedFile
            val accentColor = fileTypeAccentColor(parsed.fileType)

            Scaffold(
                modifier = modifier
                    .fillMaxSize()
                    .testTag("universal_file_viewer_screen"),
                topBar = {
                    Column {
                        TopAppBar(
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = accentColor.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = parsed.fileType.badge,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = accentColor,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = parsed.fileName,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${parsed.fileType.label} • ${formatFileSize(parsed.fileSizeBytes)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            },
                            navigationIcon = {
                                IconButton(
                                    onClick = onClose,
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("universal_viewer_back_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Close viewer"
                                    )
                                }
                            },
                            actions = {
                                if (parsed !is ParsedUniversalFile.ImageDocument) {
                                    IconButton(
                                        onClick = { isSearchOpen = !isSearchOpen },
                                        modifier = Modifier.testTag("universal_viewer_search_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = "Search in document",
                                            tint = if (isSearchOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    IconButton(
                                        onClick = { textScale = (textScale - 0.15f).coerceAtLeast(0.75f) },
                                        modifier = Modifier.testTag("universal_viewer_zoom_out_btn")
                                    ) {
                                        Icon(Icons.Default.ZoomOut, contentDescription = "Decrease text size")
                                    }
                                    IconButton(
                                        onClick = { textScale = (textScale + 0.15f).coerceAtMost(1.85f) },
                                        modifier = Modifier.testTag("universal_viewer_zoom_in_btn")
                                    ) {
                                        Icon(Icons.Default.ZoomIn, contentDescription = "Increase text size")
                                    }
                                }
                                IconButton(
                                    onClick = { isDarkViewer = !isDarkViewer },
                                    modifier = Modifier.testTag("universal_viewer_night_btn")
                                ) {
                                    Icon(
                                        imageVector = if (isDarkViewer) Icons.Default.Brightness7 else Icons.Default.Brightness4,
                                        contentDescription = "Toggle dark viewer mode"
                                    )
                                }
                                // Convert to PDF button
                                IconButton(
                                    onClick = {
                                        if (!isConvertingToPdf) {
                                            isConvertingToPdf = true
                                            onConvertAndOpenAsPdf(parsed)
                                        }
                                    },
                                    modifier = Modifier.testTag("universal_viewer_to_pdf_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PictureAsPdf,
                                        contentDescription = "Convert & Open as PDF",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                // Share button
                                IconButton(
                                    onClick = { shareUniversalFile(context, parsed) },
                                    modifier = Modifier.testTag("universal_viewer_share_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = "Share file"
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = if (isDarkViewer) Color(0xFF1E1E1E) else MaterialTheme.colorScheme.surface,
                                titleContentColor = if (isDarkViewer) Color.White else MaterialTheme.colorScheme.onSurface,
                                actionIconContentColor = if (isDarkViewer) Color.White else MaterialTheme.colorScheme.onSurface,
                                navigationIconContentColor = if (isDarkViewer) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        )

                        AnimatedVisibility(visible = isSearchOpen && parsed !is ParsedUniversalFile.ImageDocument) {
                            Surface(
                                color = if (isDarkViewer) Color(0xFF252525) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = { Text("Search text or cells...") },
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                    trailingIcon = {
                                        if (searchQuery.isNotEmpty()) {
                                            IconButton(onClick = { searchQuery = "" }) {
                                                Icon(Icons.Default.Clear, contentDescription = "Clear search")
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                        .testTag("universal_viewer_search_input")
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .background(
                            if (isDarkViewer) Color(0xFF121212)
                            else Color(0xFFF3F5F8)
                        )
                ) {
                    when (parsed) {
                        is ParsedUniversalFile.WordDocument -> {
                            WordDocumentViewerContent(
                                doc = parsed,
                                searchQuery = searchQuery,
                                textScale = textScale,
                                isDarkViewer = isDarkViewer,
                                onConvertToPdf = { onConvertAndOpenAsPdf(parsed) }
                            )
                        }
                        is ParsedUniversalFile.ExcelWorkbook -> {
                            ExcelWorkbookViewerContent(
                                workbook = parsed,
                                searchQuery = searchQuery,
                                textScale = textScale,
                                isDarkViewer = isDarkViewer,
                                onConvertToPdf = { onConvertAndOpenAsPdf(parsed) }
                            )
                        }
                        is ParsedUniversalFile.PowerPointDeck -> {
                            PowerPointDeckViewerContent(
                                deck = parsed,
                                searchQuery = searchQuery,
                                textScale = textScale,
                                isDarkViewer = isDarkViewer,
                                onConvertToPdf = { onConvertAndOpenAsPdf(parsed) }
                            )
                        }
                        is ParsedUniversalFile.ImageDocument -> {
                            JpegAndImageViewerContent(
                                imgDoc = parsed,
                                isDarkViewer = isDarkViewer,
                                onRunOcrOnBitmap = onRunOcrOnBitmap,
                                onConvertToPdf = { onConvertAndOpenAsPdf(parsed) }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// 1. MS Word (.docx / .rtf / .txt / .md / .html) Viewer Content
// ============================================================================
@Composable
private fun WordDocumentViewerContent(
    doc: ParsedUniversalFile.WordDocument,
    searchQuery: String,
    textScale: Float,
    isDarkViewer: Boolean,
    onConvertToPdf: () -> Unit
) {
    val context = LocalContext.current
    val paperBg = if (isDarkViewer) Color(0xFF1E1E1E) else Color.White
    val textColor = if (isDarkViewer) Color(0xFFEAEAEA) else Color(0xFF1E293B)
    val headingColor = if (isDarkViewer) Color(0xFF90CAF9) else Color(0xFF1565C0)

    val filteredBlocks = remember(doc.blocks, searchQuery) {
        if (searchQuery.isBlank()) {
            doc.blocks
        } else {
            doc.blocks.filter { block ->
                when (block) {
                    is WordDocBlock.Heading -> block.text.contains(searchQuery, ignoreCase = true)
                    is WordDocBlock.Paragraph -> block.text.contains(searchQuery, ignoreCase = true)
                    is WordDocBlock.Table -> block.rows.any { r -> r.any { c -> c.contains(searchQuery, ignoreCase = true) } }
                    is WordDocBlock.EmbeddedImage -> true
                    WordDocBlock.PageBreak -> false
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Document Summary & Quick Actions Bar
        Surface(
            color = if (isDarkViewer) Color(0xFF222222) else MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${doc.wordCount} words • ${doc.paragraphCount} paragraphs" +
                        if (doc.tableCount > 0) " • ${doc.tableCount} tables" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isDarkViewer) Color.LightGray else MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = {
                            copyToClipboard(context, "Word Document Text", doc.plainText)
                            Toast.makeText(context, "Copied document text", Toast.LENGTH_SHORT).show()
                        },
                        label = { Text("Copy Text") },
                        leadingIcon = {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        modifier = Modifier.testTag("word_viewer_copy_all_btn")
                    )
                    AssistChip(
                        onClick = onConvertToPdf,
                        label = { Text("Save as PDF") },
                        leadingIcon = {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        modifier = Modifier.testTag("word_viewer_save_pdf_chip")
                    )
                }
            }
        }

        SelectionContainer {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 820.dp),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = paperBg),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            filteredBlocks.forEach { block ->
                                when (block) {
                                    is WordDocBlock.Heading -> {
                                        val fontSize = when (block.level) {
                                            1 -> (22f * textScale).sp
                                            2 -> (18f * textScale).sp
                                            else -> (16f * textScale).sp
                                        }
                                        Text(
                                            text = block.text,
                                            fontSize = fontSize,
                                            fontWeight = FontWeight.Bold,
                                            color = headingColor,
                                            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                                        )
                                    }
                                    is WordDocBlock.Paragraph -> {
                                        val prefix = if (block.isBullet) "•  " else ""
                                        Text(
                                            text = prefix + block.text,
                                            fontSize = (15f * textScale).sp,
                                            lineHeight = (23f * textScale).sp,
                                            fontWeight = if (block.isBold) FontWeight.Bold else FontWeight.Normal,
                                            fontStyle = if (block.isItalic) FontStyle.Italic else FontStyle.Normal,
                                            color = textColor,
                                            modifier = Modifier.padding(
                                                start = if (block.isBullet) 12.dp else 0.dp
                                            )
                                        )
                                    }
                                    is WordDocBlock.Table -> {
                                        WordTableCard(
                                            rows = block.rows,
                                            textScale = textScale,
                                            isDarkViewer = isDarkViewer
                                        )
                                    }
                                    is WordDocBlock.EmbeddedImage -> {
                                        Card(
                                            shape = RoundedCornerShape(10.dp),
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Image(
                                                bitmap = block.bitmap.asImageBitmap(),
                                                contentDescription = block.name,
                                                contentScale = ContentScale.FillWidth,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                    WordDocBlock.PageBreak -> {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            HorizontalDivider(modifier = Modifier.weight(1f))
                                            Text(
                                                text = "  PAGE BREAK  ",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                            HorizontalDivider(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WordTableCard(
    rows: List<List<String>>,
    textScale: Float,
    isDarkViewer: Boolean
) {
    val borderColor = if (isDarkViewer) Color(0xFF424242) else Color(0xFFCBD5E1)
    val headerBg = if (isDarkViewer) Color(0xFF2C3E50) else Color(0xFFEFF6FF)
    val cellTextColor = if (isDarkViewer) Color.White else Color(0xFF1E293B)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
    ) {
        rows.forEachIndexed { rowIdx, cols ->
            Row(
                modifier = Modifier.background(if (rowIdx == 0) headerBg else Color.Transparent)
            ) {
                cols.forEach { cell ->
                    Box(
                        modifier = Modifier
                            .widthIn(min = 110.dp, max = 260.dp)
                            .border(0.5.dp, borderColor)
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = cell,
                            fontSize = (13.5f * textScale).sp,
                            fontWeight = if (rowIdx == 0) FontWeight.Bold else FontWeight.Normal,
                            color = cellTextColor
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// 2. MS Excel (.xlsx / .csv) Multi-Sheet Interactive Grid Viewer
// ============================================================================
@Composable
private fun ExcelWorkbookViewerContent(
    workbook: ParsedUniversalFile.ExcelWorkbook,
    searchQuery: String,
    textScale: Float,
    isDarkViewer: Boolean,
    onConvertToPdf: () -> Unit
) {
    val context = LocalContext.current
    var selectedSheetIdx by remember { mutableIntStateOf(0) }
    val activeSheet = workbook.sheets.getOrElse(selectedSheetIdx) { workbook.sheets.first() }

    var selectedCellCoord by remember(selectedSheetIdx) { mutableStateOf("A1") }
    var selectedCellValue by remember(selectedSheetIdx) {
        mutableStateOf(activeSheet.rows.firstOrNull()?.firstOrNull().orEmpty())
    }

    val filteredRowsWithOriginalIndex = remember(activeSheet, searchQuery) {
        activeSheet.rows.mapIndexed { idx, row -> idx to row }.filter { (_, row) ->
            searchQuery.isBlank() || row.any { it.contains(searchQuery, ignoreCase = true) }
        }
    }

    val gridBg = if (isDarkViewer) Color(0xFF1A1A1A) else Color.White
    val headerBg = if (isDarkViewer) Color(0xFF2D3748) else Color(0xFFE8F5E9)
    val gridBorder = if (isDarkViewer) Color(0xFF383838) else Color(0xFFD0D7DE)
    val cellTextColor = if (isDarkViewer) Color(0xFFEAEAEA) else Color(0xFF1E293B)

    Column(modifier = Modifier.fillMaxSize()) {
        // 1. Sheet Tabs (if multiple worksheets) + Quick Actions
        Surface(
            color = if (isDarkViewer) Color(0xFF222222) else MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${activeSheet.rows.size} rows × ${activeSheet.maxColumns} cols",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isDarkViewer) Color.LightGray else MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(
                            onClick = {
                                val csv = activeSheet.rows.joinToString("\n") { r -> r.joinToString(",") }
                                copyToClipboard(context, activeSheet.sheetName, csv)
                                Toast.makeText(context, "Copied sheet as CSV", Toast.LENGTH_SHORT).show()
                            },
                            label = { Text("Copy CSV") },
                            leadingIcon = {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            modifier = Modifier.testTag("excel_viewer_copy_csv_btn")
                        )
                        AssistChip(
                            onClick = onConvertToPdf,
                            label = { Text("Save as PDF") },
                            leadingIcon = {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            modifier = Modifier.testTag("excel_viewer_save_pdf_chip")
                        )
                    }
                }

                // Formula / Active Cell Value Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isDarkViewer) Color(0xFF181818) else Color(0xFFF8FAFC))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF2E7D32).copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = selectedCellCoord,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF2E7D32),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = selectedCellValue.ifBlank { "(empty cell)" },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDarkViewer) Color.White else MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (selectedCellValue.isNotBlank()) {
                        TextButton(
                            onClick = {
                                copyToClipboard(context, "Cell $selectedCellCoord", selectedCellValue)
                                Toast.makeText(context, "Copied $selectedCellCoord", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            Text("Copy")
                        }
                    }
                }

                if (workbook.sheets.size > 1) {
                    ScrollableTabRow(
                        selectedTabIndex = selectedSheetIdx,
                        edgePadding = 8.dp,
                        containerColor = Color.Transparent
                    ) {
                        workbook.sheets.forEachIndexed { idx, sheet ->
                            Tab(
                                selected = selectedSheetIdx == idx,
                                onClick = { selectedSheetIdx = idx },
                                text = {
                                    Text(
                                        text = sheet.sheetName,
                                        fontWeight = if (selectedSheetIdx == idx) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                modifier = Modifier.testTag("excel_sheet_tab_$idx")
                            )
                        }
                    }
                }
            }
        }

        // 2. 2D Scrollable Spreadsheet Grid
        val horizontalScrollState = rememberScrollState()
        val cellWidth = (135f * textScale).dp
        val rowNumWidth = 52.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(gridBg)
                .horizontalScroll(horizontalScrollState)
        ) {
            // Sticky Column Letter Header Row (A, B, C, D...)
            Row(
                modifier = Modifier
                    .background(headerBg)
                    .border(0.5.dp, gridBorder)
            ) {
                Box(
                    modifier = Modifier
                        .width(rowNumWidth)
                        .border(0.5.dp, gridBorder)
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "#",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color(0xFF2E7D32)
                    )
                }
                for (colIdx in 0 until activeSheet.maxColumns) {
                    val colLetter = excelColLetter(colIdx)
                    Box(
                        modifier = Modifier
                            .width(cellWidth)
                            .border(0.5.dp, gridBorder)
                            .padding(vertical = 8.dp, horizontal = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = colLetter,
                            fontWeight = FontWeight.Bold,
                            fontSize = (13f * textScale).sp,
                            color = Color(0xFF2E7D32)
                        )
                    }
                }
            }

            // Data Rows
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(
                    items = filteredRowsWithOriginalIndex,
                    key = { it.first }
                ) { (originalRowIdx, rowCells) ->
                    val rowNumber = originalRowIdx + 1
                    val isFirstHeaderRow = originalRowIdx == 0
                    Row(
                        modifier = Modifier.background(
                            if (isFirstHeaderRow) {
                                if (isDarkViewer) Color(0xFF252D38) else Color(0xFFF1F5F9)
                            } else {
                                gridBg
                            }
                        )
                    ) {
                        // Row number indicator cell
                        Box(
                            modifier = Modifier
                                .width(rowNumWidth)
                                .heightIn(min = 38.dp)
                                .background(headerBg.copy(alpha = 0.6f))
                                .border(0.5.dp, gridBorder)
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = rowNumber.toString(),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDarkViewer) Color.LightGray else Color(0xFF475569)
                            )
                        }

                        // Row cells
                        for (colIdx in 0 until activeSheet.maxColumns) {
                            val cellVal = rowCells.getOrNull(colIdx).orEmpty()
                            val coord = "${excelColLetter(colIdx)}$rowNumber"
                            val isSelected = selectedCellCoord == coord

                            Box(
                                modifier = Modifier
                                    .width(cellWidth)
                                    .heightIn(min = 38.dp)
                                    .background(
                                        if (isSelected) Color(0xFF2E7D32).copy(alpha = 0.16f)
                                        else Color.Transparent
                                    )
                                    .border(
                                        width = if (isSelected) 1.5.dp else 0.5.dp,
                                        color = if (isSelected) Color(0xFF2E7D32) else gridBorder
                                    )
                                    .clickable {
                                        selectedCellCoord = coord
                                        selectedCellValue = cellVal
                                    }
                                    .padding(horizontal = 8.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = cellVal,
                                    fontSize = (13f * textScale).sp,
                                    fontWeight = if (isFirstHeaderRow) FontWeight.Bold else FontWeight.Normal,
                                    color = cellTextColor,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun excelColLetter(zeroBasedCol: Int): String {
    var c = zeroBasedCol
    val sb = StringBuilder()
    while (c >= 0) {
        sb.insert(0, ('A'.code + (c % 26)).toChar())
        c = (c / 26) - 1
    }
    return sb.toString()
}

// ============================================================================
// 3. MS PowerPoint (.pptx) Slide Deck Viewer Content
// ============================================================================
@Composable
private fun PowerPointDeckViewerContent(
    deck: ParsedUniversalFile.PowerPointDeck,
    searchQuery: String,
    textScale: Float,
    isDarkViewer: Boolean,
    onConvertToPdf: () -> Unit
) {
    val filteredSlides = remember(deck.slides, searchQuery) {
        if (searchQuery.isBlank()) {
            deck.slides
        } else {
            deck.slides.filter { s ->
                s.title.contains(searchQuery, ignoreCase = true) ||
                    s.bulletPoints.any { it.contains(searchQuery, ignoreCase = true) }
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 840.dp),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${deck.slides.size} Presentation Slides",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isDarkViewer) Color.White else MaterialTheme.colorScheme.onSurface
                )
                AssistChip(
                    onClick = onConvertToPdf,
                    label = { Text("Save Deck as PDF") },
                    leadingIcon = {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                )
            }
        }

        itemsIndexed(filteredSlides) { _, slide ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDarkViewer) Color(0xFF1E1E1E) else Color.White
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFD84315).copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = "Slide ${slide.slideNumber}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFD84315),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Text(
                        text = slide.title,
                        fontSize = (20f * textScale).sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDarkViewer) Color.White else Color(0xFF0F172A)
                    )

                    HorizontalDivider()

                    slide.bulletPoints.forEach { point ->
                        Text(
                            text = "•  $point",
                            fontSize = (15f * textScale).sp,
                            lineHeight = (22f * textScale).sp,
                            color = if (isDarkViewer) Color(0xFFE2E8F0) else Color(0xFF334155)
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// 4. JPEG / PNG / WebP High-Resolution Image Viewer with Zoom, Rotate & OCR
// ============================================================================
@Composable
private fun JpegAndImageViewerContent(
    imgDoc: ParsedUniversalFile.ImageDocument,
    isDarkViewer: Boolean,
    onRunOcrOnBitmap: suspend (Bitmap) -> String,
    onConvertToPdf: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentBitmap by remember(imgDoc) { mutableStateOf(imgDoc.bitmap) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var rotationDegrees by remember { mutableIntStateOf(0) }

    var isRunningOcr by remember { mutableStateOf(false) }
    var extractedOcrText by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Main Zoomable & Panning Image Canvas
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(if (isDarkViewer) Color(0xFF0D0D0D) else Color(0xFF1E293B))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            if (scale > 1.2f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 2.5f
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        if (scale > 1f) {
                            val maxX = (size.width * (scale - 1f)) / 2f
                            val maxY = (size.height * (scale - 1f)) / 2f
                            offset = Offset(
                                x = (offset.x + pan.x).coerceIn(-maxX, maxX),
                                y = (offset.y + pan.y).coerceIn(-maxY, maxY)
                            )
                        } else {
                            offset = Offset.Zero
                        }
                    }
                }
                .testTag("jpeg_image_viewer_canvas"),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = currentBitmap.asImageBitmap(),
                contentDescription = imgDoc.fileName,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
            )

            // Floating Zoom & Rotation Pill
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.65f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 14.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = {
                            rotationDegrees = (rotationDegrees - 90 + 360) % 360
                            currentBitmap = rotateBitmap90(currentBitmap, -90f)
                        },
                        modifier = Modifier.size(36.dp).testTag("jpeg_viewer_rotate_ccw_btn")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.RotateLeft,
                            contentDescription = "Rotate Counter-Clockwise",
                            tint = Color.White
                        )
                    }
                    Text(
                        text = "${(scale * 100).toInt()}%",
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable {
                                scale = 1f
                                offset = Offset.Zero
                            }
                            .padding(horizontal = 6.dp)
                    )
                    IconButton(
                        onClick = {
                            rotationDegrees = (rotationDegrees + 90) % 360
                            currentBitmap = rotateBitmap90(currentBitmap, 90f)
                        },
                        modifier = Modifier.size(36.dp).testTag("jpeg_viewer_rotate_cw_btn")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.RotateRight,
                            contentDescription = "Rotate Clockwise",
                            tint = Color.White
                        )
                    }
                }
            }
        }

        // Bottom Image Info & Actions Bar (ML Kit OCR + Convert to PDF)
        Surface(
            color = if (isDarkViewer) Color(0xFF1E1E1E) else MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${imgDoc.formatLabel} Image • ${imgDoc.widthPx} × ${imgDoc.heightPx} px",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isDarkViewer) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Pinch or double-tap to zoom • ${formatFileSize(imgDoc.fileSizeBytes)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isDarkViewer) Color.LightGray else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            if (!isRunningOcr) {
                                isRunningOcr = true
                                scope.launch {
                                    val text = onRunOcrOnBitmap(currentBitmap)
                                    isRunningOcr = false
                                    extractedOcrText = text.ifBlank { "[No text recognized in this image]" }
                                }
                            }
                        },
                        modifier = Modifier.testTag("jpeg_viewer_extract_ocr_btn")
                    ) {
                        if (isRunningOcr) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.DocumentScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Extract Text (OCR)")
                    }

                    Button(
                        onClick = onConvertToPdf,
                        modifier = Modifier.testTag("jpeg_viewer_convert_pdf_btn")
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("To PDF")
                    }
                }
            }
        }
    }

    // Dialog displaying selectable ML Kit OCR text extracted from the viewed JPEG/PNG image
    if (extractedOcrText != null) {
        val text = extractedOcrText!!
        AlertDialog(
            onDismissRequest = { extractedOcrText = null },
            title = {
                Text("Extracted Image Text (ML Kit OCR)", fontWeight = FontWeight.Bold)
            },
            text = {
                SelectionContainer {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        copyToClipboard(context, "Image OCR Text", text)
                        Toast.makeText(context, "Copied extracted text to clipboard", Toast.LENGTH_SHORT).show()
                        extractedOcrText = null
                    }
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy All")
                }
            },
            dismissButton = {
                TextButton(onClick = { extractedOcrText = null }) {
                    Text("Close")
                }
            }
        )
    }
}

private fun rotateBitmap90(source: Bitmap, degrees: Float): Bitmap {
    return try {
        val matrix = Matrix().apply { postRotate(degrees) }
        Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    } catch (_: Throwable) {
        source
    }
}

private fun fileTypeAccentColor(type: UniversalFileType): Color {
    return when (type) {
        UniversalFileType.WORD -> Color(0xFF1565C0)
        UniversalFileType.EXCEL, UniversalFileType.CSV -> Color(0xFF2E7D32)
        UniversalFileType.POWERPOINT -> Color(0xFFD84315)
        UniversalFileType.IMAGE -> Color(0xFFE65100)
        UniversalFileType.RTF -> Color(0xFF5E35B1)
        UniversalFileType.TEXT -> Color(0xFF37474F)
        UniversalFileType.PDF -> Color(0xFFC62828)
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    cm?.setPrimaryClip(ClipData.newPlainText(label, text))
}

private fun shareUniversalFile(context: Context, parsed: ParsedUniversalFile) {
    try {
        val file = parsed.cachedFile
        val uri = if (file != null && file.exists()) {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } else {
            parsed.sourceUri ?: return
        }
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, parsed.fileName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share ${parsed.fileName}"))
    } catch (_: Exception) {
    }
}
