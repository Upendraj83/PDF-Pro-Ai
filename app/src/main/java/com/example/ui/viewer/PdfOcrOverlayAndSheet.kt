package com.example.ui.viewer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.horizontalScroll
import com.example.pdf.BengaliFontOption
import com.example.pdf.BengaliTextNormalizer
import com.example.scan.OcrLanguagePackManager
import com.example.scan.OcrPageResult
import com.example.scan.OcrTextLine
import com.example.ui.scan.OcrLanguagePacksDialog
import com.example.ui.scan.OcrLanguageQuickSelectorBar

/**
 * Smart non-intrusive banner displayed when the current PDF page has no embedded text
 * (indicating a scanned document image) so the user can extract selectable text with 1 tap.
 */
@Composable
fun ScannedPageOcrPromptBanner(
    onActivateOnPageSelection: () -> Unit,
    onOpenOcrTextSheet: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = 4.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("scanned_page_ocr_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DocumentScanner,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column {
                    Text(
                        text = "Scanned PDF Page (Non-Searchable)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = "Extract selectable text with on-device ML Kit OCR",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TextButton(
                    onClick = onActivateOnPageSelection,
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        .testTag("banner_ocr_select_on_page_btn")
                ) {
                    Text("Select on Page", fontWeight = FontWeight.SemiBold)
                }
                Button(
                    onClick = onOpenOcrTextSheet,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        .testTag("banner_ocr_extract_btn")
                ) {
                    Text("Extract Text")
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss OCR banner",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Interactive overlay rendered directly over the PDF page bitmap when OCR Selection Mode is active.
 * Highlights every ML Kit recognized text line/word box so users can tap directly on scanned text to select & copy it.
 */
@Composable
fun OcrSelectablePageOverlay(
    ocrResult: OcrPageResult?,
    selectedLineTexts: List<String>,
    onToggleLineSelection: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    if (ocrResult == null) return

    // Build effective selectable items from recognized lines (or fallback to words if lines empty)
    val selectableLines: List<OcrTextLine> = remember(ocrResult) {
        if (ocrResult.lines.isNotEmpty()) {
            ocrResult.lines
        } else {
            ocrResult.words.map { w ->
                OcrTextLine(
                    text = w.text,
                    leftNorm = w.leftNorm,
                    topNorm = w.topNorm,
                    rightNorm = w.rightNorm,
                    bottomNorm = w.bottomNorm
                )
            }
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .testTag("ocr_selectable_page_overlay")
            .pointerInput(selectableLines) {
                detectTapGestures { tapOffset ->
                    val w = size.width.toFloat().coerceAtLeast(1f)
                    val h = size.height.toFloat().coerceAtLeast(1f)
                    val normX = tapOffset.x / w
                    val normY = tapOffset.y / h

                    val hitLine = selectableLines.firstOrNull { line ->
                        normX in (line.leftNorm - 0.01f)..(line.rightNorm + 0.01f) &&
                            normY in (line.topNorm - 0.008f)..(line.bottomNorm + 0.008f)
                    }
                    if (hitLine != null) {
                        onToggleLineSelection(hitLine.text)
                        copyTextToClipboard(context, hitLine.text, showToast = false)
                        Toast.makeText(
                            context,
                            "Selected & copied: \"${hitLine.text.take(36)}\"",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
    ) {
        val canvasW = size.width
        val canvasH = size.height

        selectableLines.forEach { line ->
            val isSelected = selectedLineTexts.contains(line.text.trim())
            val left = line.leftNorm * canvasW
            val top = line.topNorm * canvasH
            val width = ((line.rightNorm - line.leftNorm) * canvasW).coerceAtLeast(8f)
            val height = ((line.bottomNorm - line.topNorm) * canvasH).coerceAtLeast(8f)

            val fillColor = if (isSelected) {
                Color(0xFF1976D2).copy(alpha = 0.34f)
            } else {
                Color(0xFF00ACC1).copy(alpha = 0.16f)
            }
            val borderColor = if (isSelected) {
                Color(0xFF1565C0)
            } else {
                Color(0xFF00838F).copy(alpha = 0.65f)
            }

            drawRoundRect(
                color = fillColor,
                topLeft = Offset(left, top),
                size = Size(width, height),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            )
            drawRoundRect(
                color = borderColor,
                topLeft = Offset(left, top),
                size = Size(width, height),
                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                style = Stroke(width = if (isSelected) 2.dp.toPx() else 1.dp.toPx())
            )
        }
    }
}

/**
 * Bottom toolbar displayed when On-Page OCR Text Selection Mode is active.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OcrBottomSelectionToolbar(
    isRunningOcr: Boolean,
    ocrResult: OcrPageResult?,
    selectedLineTexts: List<String>,
    onSelectAllLines: () -> Unit,
    onClearSelection: () -> Unit,
    onOpenFullTextSheet: () -> Unit,
    onMakeSearchablePdf: () -> Unit,
    onCloseMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lineCount = ocrResult?.lines?.size ?: 0
    val wordCount = ocrResult?.words?.size ?: 0

    Surface(
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        shadowElevation = 10.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("ocr_bottom_selection_toolbar")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (isRunningOcr) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = "ML Kit recognizing text on page...",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.TextFields,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = if (lineCount > 0) {
                                    "Tap highlighted text on page to copy ($wordCount words)"
                                } else {
                                    "No text detected on this scanned page"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            if (selectedLineTexts.isNotEmpty()) {
                                Text(
                                    text = "${selectedLineTexts.size} line(s) selected",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                IconButton(
                    onClick = onCloseMode,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("ocr_toolbar_close_btn")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Exit OCR Selection Mode")
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val textToCopy = if (selectedLineTexts.isNotEmpty()) {
                            selectedLineTexts.joinToString("\n")
                        } else {
                            ocrResult?.fullText.orEmpty()
                        }
                        if (textToCopy.isNotBlank()) {
                            copyTextToClipboard(context, textToCopy, showToast = true)
                        } else {
                            Toast.makeText(context, "No recognized text to copy", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = !isRunningOcr && !ocrResult?.fullText.isNullOrBlank(),
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .testTag("ocr_toolbar_copy_btn")
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (selectedLineTexts.isNotEmpty()) {
                            "Copy Selected (${selectedLineTexts.size})"
                        } else {
                            "Copy All Page Text"
                        }
                    )
                }

                FilledTonalButton(
                    onClick = onOpenFullTextSheet,
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .testTag("ocr_toolbar_open_sheet_btn")
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Selectable Text View")
                }

                if (lineCount > 0) {
                    OutlinedButton(
                        onClick = {
                            if (selectedLineTexts.size >= lineCount) {
                                onClearSelection()
                            } else {
                                onSelectAllLines()
                            }
                        },
                        modifier = Modifier.heightIn(min = 44.dp)
                    ) {
                        Icon(Icons.Default.SelectAll, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (selectedLineTexts.size >= lineCount) "Clear" else "Select All")
                    }
                }

                OutlinedButton(
                    onClick = onMakeSearchablePdf,
                    enabled = !isRunningOcr,
                    modifier = Modifier
                        .heightIn(min = 44.dp)
                        .testTag("ocr_toolbar_make_searchable_btn")
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Make Searchable PDF")
                }
            }
        }
    }
}

/**
 * Full-featured Bottom Sheet for viewing, selecting (via native SelectionContainer or editable box),
 * copying individual recognized text blocks, or extracting text across all pages of a scanned PDF.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PdfOcrTextSheet(
    currentPageIndex: Int,
    totalPages: Int,
    isRunningOcr: Boolean,
    currentPageOcrResult: OcrPageResult?,
    onSelectPage: (Int) -> Unit,
    onRescanCurrentPage: () -> Unit,
    onExtractAllPages: ((String) -> Unit) -> Unit,
    onMakeSearchablePdf: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val languagePackManager = remember(context) { OcrLanguagePackManager(context) }
    var showLanguagePacksDialog by remember { mutableStateOf(false) }

    // 0 = Selectable & Editable Text, 1 = Recognized Blocks
    var selectedTab by remember { mutableIntStateOf(0) }
    var isEditMode by remember { mutableStateOf(false) }
    var allPagesCombinedText by remember { mutableStateOf<String?>(null) }
    var selectedBengaliFont by remember { mutableStateOf(BengaliFontOption.KALPURUSH) }

    val activeText = allPagesCombinedText ?: currentPageOcrResult?.fullText.orEmpty()
    var editableText by remember(activeText) { mutableStateOf(activeText) }

    LaunchedEffect(activeText) {
        editableText = activeText
    }

    val wordCount = remember(editableText) {
        if (editableText.isBlank()) 0 else editableText.trim().split(Regex("\\s+")).size
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.testTag("pdf_ocr_text_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DocumentScanner,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Column {
                        Text(
                            text = "ML Kit OCR Text Extractor",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Multi-language on-device OCR • ${currentPageOcrResult?.detectedLanguageLabel?.ifBlank { "Auto" } ?: "Auto"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close OCR Sheet")
                }
            }

            // Multi-Language OCR Language Pack Quick Selector & Manager
            OcrLanguageQuickSelectorBar(
                languagePackManager = languagePackManager,
                onOpenManagePacksDialog = { showLanguagePacksDialog = true },
                onLanguageChanged = {
                    allPagesCombinedText = null
                    onRescanCurrentPage()
                }
            )

            // Page Switcher & All-Pages OCR Bar
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                allPagesCombinedText = null
                                if (currentPageIndex > 0) onSelectPage(currentPageIndex - 1)
                            },
                            enabled = currentPageIndex > 0 && !isRunningOcr,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous page")
                        }
                        Text(
                            text = if (allPagesCombinedText != null) {
                                "All $totalPages Pages"
                            } else {
                                "Page ${currentPageIndex + 1} of $totalPages"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(
                            onClick = {
                                allPagesCombinedText = null
                                if (currentPageIndex + 1 < totalPages) onSelectPage(currentPageIndex + 1)
                            },
                            enabled = currentPageIndex + 1 < totalPages && !isRunningOcr,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next page")
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (totalPages > 1) {
                            TextButton(
                                onClick = {
                                    onExtractAllPages { combined ->
                                        allPagesCombinedText = combined
                                    }
                                },
                                enabled = !isRunningOcr,
                                modifier = Modifier.testTag("ocr_extract_all_pages_btn")
                            ) {
                                Text("OCR All Pages", fontWeight = FontWeight.SemiBold)
                            }
                        }
                        IconButton(
                            onClick = {
                                allPagesCombinedText = null
                                onRescanCurrentPage()
                            },
                            enabled = !isRunningOcr,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Re-run OCR on page",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Bengali Font Selector (26 Popular Fonts) & Font Problem Fixer
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Bangla Font (26+): ${selectedBengaliFont.displayName}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    TextButton(
                        onClick = {
                            if (editableText.isNotBlank()) {
                                editableText = BengaliTextNormalizer.normalizeExtractedPdfText(
                                    rawText = editableText,
                                    pageHasBijoyFont = selectedBengaliFont.isLegacyBijoyFamily,
                                    fixBrokenMatrasAndNuktas = true,
                                    convertBijoyAnsiToUnicode = true
                                )
                                Toast.makeText(
                                    context,
                                    "Fixed Bengali matras & Bijoy->Unicode (${selectedBengaliFont.fontFamilyName})",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        modifier = Modifier.testTag("ocr_sheet_fix_bengali_btn")
                    ) {
                        Text("Fix Bangla Font / Bijoy", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    BengaliFontOption.entries.forEach { fontOption ->
                        FilterChip(
                            selected = selectedBengaliFont == fontOption,
                            onClick = { selectedBengaliFont = fontOption },
                            label = {
                                Text(
                                    text = fontOption.displayName,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        )
                    }
                }
            }

            // Tabs: Selectable Text vs Recognized Blocks
            val blocks = currentPageOcrResult?.blocks.orEmpty()
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Selectable Text ($wordCount words)") },
                    modifier = Modifier.testTag("ocr_tab_selectable_text")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Text Blocks (${blocks.size})") },
                    modifier = Modifier.testTag("ocr_tab_blocks")
                )
            }

            if (isRunningOcr) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = "Running ML Kit Text Recognition on scanned page...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (selectedTab == 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isEditMode) {
                            "Edit or select text below (${selectedBengaliFont.fontFamilyName}):"
                        } else {
                            "Long-press & drag handles to select and copy any phrase:"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FilterChip(
                        selected = isEditMode,
                        onClick = { isEditMode = !isEditMode },
                        label = { Text(if (isEditMode) "Editing Text" else "Edit Mode") },
                        leadingIcon = {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    )
                }

                if (editableText.isBlank()) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No text detected on this page. If this is a scanned document, try zooming or testing another page.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else if (isEditMode) {
                    OutlinedTextField(
                        value = editableText,
                        onValueChange = { editableText = it },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = selectedBengaliFont.toComposeFontFamily()
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .testTag("ocr_editable_text_field"),
                        shape = RoundedCornerShape(12.dp)
                    )
                } else {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(12.dp)
                            )
                    ) {
                        SelectionContainer(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(14.dp)
                                .testTag("ocr_selection_container")
                        ) {
                            Text(
                                text = editableText,
                                fontFamily = selectedBengaliFont.toComposeFontFamily(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            } else {
                // Tab 1: Individual Recognized Text Blocks for 1-Tap Copying
                if (blocks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No text blocks recognized on this page.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(blocks) { index, block ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        copyTextToClipboard(context, block.text, showToast = true)
                                    }
                                    .testTag("ocr_block_card_$index")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    SelectionContainer(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = block.text,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    IconButton(
                                        onClick = {
                                            copyTextToClipboard(context, block.text, showToast = true)
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy block",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            // Bottom Actions: Copy All, Share Text, Make PDF Searchable
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        copyTextToClipboard(context, editableText, showToast = true)
                    },
                    enabled = editableText.isNotBlank() && !isRunningOcr,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("ocr_sheet_copy_all_btn")
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy Text")
                }

                FilledTonalButton(
                    onClick = {
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, editableText)
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Share Extracted OCR Text"))
                    },
                    enabled = editableText.isNotBlank() && !isRunningOcr,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("ocr_sheet_share_btn")
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share")
                }

                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onMakeSearchablePdf()
                    },
                    enabled = !isRunningOcr,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("ocr_sheet_make_searchable_btn")
                ) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Embed OCR Layer (Save as Searchable PDF)",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    if (showLanguagePacksDialog) {
        OcrLanguagePacksDialog(
            languagePackManager = languagePackManager,
            onDismiss = { showLanguagePacksDialog = false },
            onLanguageSelected = {
                allPagesCombinedText = null
                onRescanCurrentPage()
            }
        )
    }
}

private fun copyTextToClipboard(context: Context, text: String, showToast: Boolean) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("Extracted PDF OCR Text", text))
    if (showToast) {
        Toast.makeText(context, "Copied extracted text to clipboard!", Toast.LENGTH_SHORT).show()
    }
}
