package com.example.ui.scan

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import com.example.pdf.BengaliFontOption
import com.example.pdf.BengaliTextNormalizer
import com.example.scan.OcrPageResult
import com.example.scan.OcrTextBlock
import com.example.scan.ScanOcrEngine
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtractTextDialog(
    initialFile: File? = null,
    initialUri: Uri? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val ocrEngine = remember(context) { ScanOcrEngine(context) }
    val selectedLanguagePack by ocrEngine.languagePackManager.selectedLanguagePack.collectAsState()
    var showLanguagePacksDialog by remember { mutableStateOf(false) }

    var currentFile by remember { mutableStateOf(initialFile) }
    var currentUri by remember { mutableStateOf(initialUri) }
    var isPdfSource by remember {
        mutableStateOf(
            initialFile?.name?.endsWith(".pdf", ignoreCase = true) == true ||
                initialUri?.toString()?.contains(".pdf", ignoreCase = true) == true
        )
    }
    var currentPageIndex by remember { mutableIntStateOf(0) }
    var totalPdfPages by remember { mutableIntStateOf(1) }

    var extractedText by remember { mutableStateOf("") }
    var recognizedBlocks by remember { mutableStateOf<List<OcrTextBlock>>(emptyList()) }
    var detectedScriptLabel by remember { mutableStateOf("") }
    var wordCount by remember { mutableIntStateOf(0) }
    var isProcessing by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Editable/Selectable Text, 1 = Blocks
    var selectedBengaliFont by remember { mutableStateOf(BengaliFontOption.KALPURUSH) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            currentFile = null
            currentUri = uri
            isPdfSource = false
            currentPageIndex = 0
            totalPdfPages = 1
        }
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            currentFile = null
            currentUri = uri
            isPdfSource = true
            currentPageIndex = 0
        }
    }

    LaunchedEffect(currentFile, currentUri, isPdfSource, currentPageIndex, selectedLanguagePack) {
        val file = currentFile
        val uri = currentUri
        if (file == null && uri == null) return@LaunchedEffect

        isProcessing = true
        val mimeType = uri?.let { context.contentResolver.getType(it) }
        val treatAsPdf = isPdfSource ||
            file?.name?.endsWith(".pdf", ignoreCase = true) == true ||
            mimeType?.contains("pdf", ignoreCase = true) == true

        val result: OcrPageResult = if (treatAsPdf) {
            val count = ocrEngine.getPdfPageCount(file, uri).coerceAtLeast(1)
            totalPdfPages = count
            val safePage = currentPageIndex.coerceIn(0, count - 1)
            ocrEngine.recognizeTextFromPdfPage(file, uri, safePage, languagePack = selectedLanguagePack)
        } else if (file != null) {
            totalPdfPages = 1
            ocrEngine.recognizeTextFromFile(file, languagePack = selectedLanguagePack)
        } else if (uri != null) {
            totalPdfPages = 1
            ocrEngine.recognizeTextFromUri(uri, languagePack = selectedLanguagePack)
        } else {
            OcrPageResult("", emptyList())
        }

        recognizedBlocks = result.blocks
        detectedScriptLabel = result.detectedLanguageLabel
        extractedText = result.fullText.ifBlank {
            "No selectable text detected on this page/image. Try picking a clearer scanned PDF or document photo, or download the matching language pack."
        }
        wordCount = if (result.fullText.isBlank()) 0 else result.fullText.trim().split(Regex("\\s+")).size
        isProcessing = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.TextFields,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = "Extract Text (ML Kit OCR)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Multi-Language On-Device OCR • ${selectedLanguagePack.displayName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    },
                    actions = {
                        OutlinedButton(
                            onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .testTag("ocr_pick_pdf_btn")
                        ) {
                            Icon(
                                Icons.Default.PictureAsPdf,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("PDF")
                        }
                        OutlinedButton(
                            onClick = { imagePickerLauncher.launch("image/*") },
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .testTag("ocr_pick_another_image_btn")
                        ) {
                            Icon(
                                Icons.Default.PhotoLibrary,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Image")
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isProcessing) {
                                    "Recognizing ${selectedLanguagePack.displayName} text on-device..."
                                } else {
                                    "Detected $wordCount words"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (detectedScriptLabel.isNotBlank() && !isProcessing) {
                                Text(
                                    text = "Script: $detectedScriptLabel • Pack: ${selectedLanguagePack.displayName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "100% OFFLINE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                // Multi-Language OCR Language Pack Quick Selector & Manager Launcher
                OcrLanguageQuickSelectorBar(
                    languagePackManager = ocrEngine.languagePackManager,
                    onOpenManagePacksDialog = { showLanguagePacksDialog = true }
                )

                // Multi-page PDF Navigation Controls if a scanned PDF is loaded
                if (totalPdfPages > 1) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { if (currentPageIndex > 0) currentPageIndex-- },
                                    enabled = currentPageIndex > 0 && !isProcessing
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous page")
                                }
                                Text(
                                    text = "Page ${currentPageIndex + 1} of $totalPdfPages",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(
                                    onClick = { if (currentPageIndex + 1 < totalPdfPages) currentPageIndex++ },
                                    enabled = currentPageIndex + 1 < totalPdfPages && !isProcessing
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next page")
                                }
                            }

                            TextButton(
                                onClick = {
                                    val file = currentFile
                                    val uri = currentUri
                                    coroutineScope.launch {
                                        isProcessing = true
                                        val sb = StringBuilder()
                                        val allBlocks = mutableListOf<OcrTextBlock>()
                                        for (p in 0 until totalPdfPages) {
                                            val pageRes = ocrEngine.recognizeTextFromPdfPage(
                                                file,
                                                uri,
                                                p,
                                                languagePack = selectedLanguagePack
                                            )
                                            if (pageRes.fullText.isNotBlank()) {
                                                sb.append("--- Page ${p + 1} ---\n")
                                                sb.append(pageRes.fullText.trim()).append("\n\n")
                                            }
                                            allBlocks.addAll(pageRes.blocks)
                                        }
                                        val combined = sb.toString().trim()
                                        extractedText = combined.ifBlank { "No text recognized across $totalPdfPages pages." }
                                        recognizedBlocks = allBlocks
                                        wordCount = if (combined.isBlank()) 0 else combined.split(Regex("\\s+")).size
                                        isProcessing = false
                                    }
                                },
                                enabled = !isProcessing
                            ) {
                                Text("OCR All $totalPdfPages Pages", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                // Horizontal Bengali Font Selector (26 Popular Fonts) + 1-Tap Bengali Font Fixer
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
                                if (extractedText.isNotBlank()) {
                                    extractedText = BengaliTextNormalizer.normalizeExtractedPdfText(
                                        rawText = extractedText,
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
                            modifier = Modifier.testTag("ocr_fix_bengali_font_btn")
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

                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Selectable Text") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Text Blocks (${recognizedBlocks.size})") }
                    )
                }

                if (isProcessing) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator()
                            Text(
                                text = "Running ML Kit Text Recognition...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                } else if (selectedTab == 0) {
                    OutlinedTextField(
                        value = extractedText,
                        onValueChange = { extractedText = it },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = selectedBengaliFont.toComposeFontFamily()
                        ),
                        label = { Text("Recognized Text (${selectedBengaliFont.fontFamilyName})") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .testTag("ocr_extracted_text_field"),
                        shape = RoundedCornerShape(12.dp)
                    )
                } else {
                    if (recognizedBlocks.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No text blocks detected. Pick a scanned PDF or image above.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            itemsIndexed(recognizedBlocks) { index, block ->
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("OCR Block", block.text))
                                            Toast.makeText(context, "Copied block to clipboard", Toast.LENGTH_SHORT).show()
                                        }
                                        .testTag("extract_dialog_block_$index")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        SelectionContainer(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = block.text,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                clipboard.setPrimaryClip(ClipData.newPlainText("OCR Block", block.text))
                                                Toast.makeText(context, "Copied block to clipboard", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.ContentCopy,
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("OCR Text", extractedText))
                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        enabled = !isProcessing && extractedText.isNotBlank(),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("ocr_copy_text_btn"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Copy Text")
                    }

                    Button(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "Extracted OCR Text")
                                putExtra(Intent.EXTRA_TEXT, extractedText)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share OCR Text"))
                        },
                        enabled = !isProcessing && extractedText.isNotBlank(),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("ocr_share_text_btn"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Share Text")
                    }
                }
            }
        }
    }

    if (showLanguagePacksDialog) {
        OcrLanguagePacksDialog(
            languagePackManager = ocrEngine.languagePackManager,
            onDismiss = { showLanguagePacksDialog = false }
        )
    }
}
