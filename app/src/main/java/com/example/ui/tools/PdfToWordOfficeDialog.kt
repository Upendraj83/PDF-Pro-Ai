package com.example.ui.tools

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.horizontalScroll
import com.example.pdf.BengaliFontOption
import com.example.pdf.PdfConversionConfig
import com.example.pdf.PdfExportFormat
import com.example.scan.OcrLanguagePackManager
import com.example.ui.scan.OcrLanguagePacksDialog
import com.example.ui.scan.OcrLanguageQuickSelectorBar
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PdfToWordOfficeDialog(
    initialUri: Uri?,
    initialFileName: String?,
    initialCachedFile: File? = null,
    initialFormat: PdfExportFormat = PdfExportFormat.WORD_DOCX,
    onConvertConfirmed: (sourceFile: File?, sourceUri: Uri, sourceName: String, config: PdfConversionConfig) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val languagePackManager = remember(context) { OcrLanguagePackManager(context) }
    val selectedOcrLanguagePack by languagePackManager.selectedLanguagePack.collectAsState()
    var showOcrLanguagePacksDialog by remember { mutableStateOf(false) }

    var selectedUri by remember { mutableStateOf(initialUri) }
    var selectedFile by remember { mutableStateOf(initialCachedFile) }
    var selectedName by remember {
        mutableStateOf(initialFileName ?: initialCachedFile?.name ?: "")
    }

    var selectedFormat by remember { mutableStateOf(initialFormat) }
    var outputBaseName by remember(selectedName) {
        val clean = selectedName.removeSuffix(".pdf").removeSuffix(".PDF").ifBlank { "Converted_Document" }
        mutableStateOf(clean)
    }

    var useCustomPageRange by remember { mutableStateOf(false) }
    var customPageRange by remember { mutableStateOf("1-5") }
    var autoOcrScannedPages by remember { mutableStateOf(true) }
    var detectTablesAndHeadings by remember { mutableStateOf(true) }
    var includePageHeaders by remember { mutableStateOf(true) }
    var includePageImages by remember { mutableStateOf(false) }

    // Bengali Font (26+ Most Popular Fonts) & Font Encoding Repair options
    var selectedBengaliFont by remember { mutableStateOf(BengaliFontOption.KALPURUSH) }
    var selectedFontCategory by remember { mutableStateOf("All (26)") }
    var showAllBengaliFonts by remember { mutableStateOf(false) }
    var fixBengaliFontEncoding by remember { mutableStateOf(true) }
    var convertBijoyToUnicode by remember { mutableStateOf(true) }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedUri = uri
            selectedFile = null
            val queried = queryPdfFileName(context, uri) ?: "Selected_Document.pdf"
            selectedName = queried
            outputBaseName = queried.removeSuffix(".pdf").removeSuffix(".PDF")
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("pdf_to_word_office_dialog"),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = formatIcon(selectedFormat),
                    contentDescription = null,
                    tint = formatColor(selectedFormat),
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Convert PDF to Word & Office",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Export to DOCX, XLSX, PPTX, RTF, CSV, HTML, MD & TXT",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Source PDF Picker Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (selectedUri != null) {
                                        selectedName.ifBlank { "Selected_Document.pdf" }
                                    } else {
                                        "No PDF Selected"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (selectedUri != null) "Ready to convert" else "Tap Select PDF to choose a file",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        OutlinedButton(
                            onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("pdf_converter_pick_pdf_btn")
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (selectedUri == null) "Select PDF" else "Change")
                        }
                    }
                }

                // 2. Output Format Selector Grid
                Text(
                    text = "Target Format",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PdfExportFormat.entries.chunked(2).forEach { rowFormats ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowFormats.forEach { format ->
                                val isSelected = selectedFormat == format
                                val accent = formatColor(format)
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { selectedFormat = format }
                                        .testTag("format_option_${format.extension}"),
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) {
                                        accent.copy(alpha = 0.14f)
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    },
                                    border = BorderStroke(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) accent else MaterialTheme.colorScheme.outlineVariant
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = formatIcon(format),
                                            contentDescription = null,
                                            tint = accent,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = format.displayName,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = ".${format.extension.uppercase()}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = accent
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Selected Format Description Banner
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = formatColor(selectedFormat).copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = selectedFormat.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                // 3. Output File Name
                OutlinedTextField(
                    value = outputBaseName,
                    onValueChange = { outputBaseName = it },
                    label = { Text("Output File Name (.${selectedFormat.extension})") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pdf_converter_output_name_input")
                )

                // 4. Page Range Selection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = !useCustomPageRange,
                        onClick = { useCustomPageRange = false },
                        label = { Text("All Pages") },
                        modifier = Modifier.testTag("pdf_converter_all_pages_chip")
                    )
                    FilterChip(
                        selected = useCustomPageRange,
                        onClick = { useCustomPageRange = true },
                        label = { Text("Custom Range") },
                        modifier = Modifier.testTag("pdf_converter_custom_range_chip")
                    )
                }

                if (useCustomPageRange) {
                    OutlinedTextField(
                        value = customPageRange,
                        onValueChange = { customPageRange = it },
                        label = { Text("Page Range (e.g., 1-3, 5)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pdf_converter_range_input")
                    )
                }

                HorizontalDivider()

                // 5. Bengali Font Support (26+ Popular Bangla Fonts) & Font Fixer
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Bengali Font (বাংলা ফন্ট — 26 Popular Fonts)",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Selected: ${selectedBengaliFont.displayName} • ${selectedBengaliFont.category}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(
                            onClick = { showAllBengaliFonts = !showAllBengaliFonts },
                            modifier = Modifier.testTag("pdf_converter_toggle_bengali_fonts_btn")
                        ) {
                            Text(if (showAllBengaliFonts) "Compact" else "Browse All 26")
                        }
                    }

                    // Live Bengali Sample Preview in the selected font
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pdf_converter_bengali_preview_card")
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = selectedBengaliFont.bengaliSample,
                                fontFamily = selectedBengaliFont.toComposeFontFamily(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Word/Office Font: ${selectedBengaliFont.fontFamilyName} (Fallback: ${selectedBengaliFont.fallbackFontName})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Category filter row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        BengaliFontOption.categories().forEach { category ->
                            FilterChip(
                                selected = selectedFontCategory == category,
                                onClick = { selectedFontCategory = category },
                                label = {
                                    Text(
                                        text = category,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            )
                        }
                    }

                    val filteredFonts = remember(selectedFontCategory, showAllBengaliFonts) {
                        val list = if (selectedFontCategory.startsWith("All")) {
                            BengaliFontOption.entries
                        } else {
                            BengaliFontOption.entries.filter { it.category == selectedFontCategory }
                        }
                        if (showAllBengaliFonts || !selectedFontCategory.startsWith("All")) {
                            list
                        } else {
                            list.take(8)
                        }
                    }

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        filteredFonts.forEach { fontOption ->
                            val isSelected = selectedBengaliFont == fontOption
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedBengaliFont = fontOption },
                                label = {
                                    Text(
                                        text = fontOption.displayName,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                modifier = Modifier.testTag("bengali_font_chip_${fontOption.name.lowercase()}")
                            )
                        }
                    }

                    // Bengali Font Problem Fix Toggles
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Fix Broken Bengali Matras (ি, ে, ৈ) & Nuktas",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Repairs visual-order vowel signs, ো/ৌ splits, and ড়/ঢ়/য় in Word/RTF",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = fixBengaliFontEncoding,
                            onCheckedChange = { fixBengaliFontEncoding = it },
                            modifier = Modifier.testTag("pdf_converter_fix_bengali_switch")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Convert Bijoy / ANSI (SutonnyMJ) to Unicode",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Fixes garbled ASCII text when converting legacy Bijoy Bangla PDFs",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = convertBijoyToUnicode,
                            onCheckedChange = { convertBijoyToUnicode = it },
                            modifier = Modifier.testTag("pdf_converter_bijoy_unicode_switch")
                        )
                    }
                }

                HorizontalDivider()

                // 6. Intelligent Conversion Options
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DocumentScanner,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Auto-OCR Scanned Pages (ML Kit)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Extracts editable text from scanned image pages automatically",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = autoOcrScannedPages,
                        onCheckedChange = { autoOcrScannedPages = it },
                        modifier = Modifier.testTag("pdf_converter_auto_ocr_switch")
                    )
                }

                if (autoOcrScannedPages) {
                    OcrLanguageQuickSelectorBar(
                        languagePackManager = languagePackManager,
                        onOpenManagePacksDialog = { showOcrLanguagePacksDialog = true }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Detect Tables & Section Headings",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Formats multi-column rows as structured Word/Excel tables",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = detectTablesAndHeadings,
                        onCheckedChange = { detectTablesAndHeadings = it },
                        modifier = Modifier.testTag("pdf_converter_detect_tables_switch")
                    )
                }

                if (selectedFormat == PdfExportFormat.WORD_DOCX || selectedFormat == PdfExportFormat.HTML_WEBPAGE) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Embed Page Snapshots (Hybrid Layout)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Includes visual page image alongside editable text",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = includePageImages,
                            onCheckedChange = { includePageImages = it },
                            modifier = Modifier.testTag("pdf_converter_embed_images_switch")
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Include Page Number Headers",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Adds Page 1, Page 2 markers between converted sections",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = includePageHeaders,
                        onCheckedChange = { includePageHeaders = it },
                        modifier = Modifier.testTag("pdf_converter_page_headers_switch")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val uri = selectedUri ?: return@Button
                    val config = PdfConversionConfig(
                        format = selectedFormat,
                        pageRangeText = if (useCustomPageRange) customPageRange.trim() else "",
                        autoOcrScannedPages = autoOcrScannedPages,
                        ocrLanguagePack = selectedOcrLanguagePack,
                        includePageImages = includePageImages,
                        includePageHeaders = includePageHeaders,
                        detectTablesAndHeadings = detectTablesAndHeadings,
                        targetBengaliFont = selectedBengaliFont,
                        fixBengaliFontEncoding = fixBengaliFontEncoding,
                        convertBijoyToUnicode = convertBijoyToUnicode,
                        outputBaseName = outputBaseName.trim()
                    )
                    onConvertConfirmed(
                        selectedFile,
                        uri,
                        selectedName.ifBlank { "Document.pdf" },
                        config
                    )
                },
                enabled = selectedUri != null,
                modifier = Modifier.testTag("pdf_converter_confirm_btn")
            ) {
                Text("Convert to .${selectedFormat.extension.uppercase()}")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("pdf_converter_cancel_btn")
            ) {
                Text("Cancel")
            }
        }
    )

    if (showOcrLanguagePacksDialog) {
        OcrLanguagePacksDialog(
            languagePackManager = languagePackManager,
            onDismiss = { showOcrLanguagePacksDialog = false }
        )
    }
}

private fun formatIcon(format: PdfExportFormat): ImageVector {
    return when (format) {
        PdfExportFormat.WORD_DOCX -> Icons.Default.Description
        PdfExportFormat.EXCEL_XLSX -> Icons.Default.TableChart
        PdfExportFormat.POWERPOINT_PPTX -> Icons.Default.Slideshow
        PdfExportFormat.RTF_DOCUMENT -> Icons.Default.Article
        PdfExportFormat.CSV_SPREADSHEET -> Icons.Default.TableChart
        PdfExportFormat.HTML_WEBPAGE -> Icons.Default.Code
        PdfExportFormat.MARKDOWN_MD -> Icons.Default.Article
        PdfExportFormat.PLAIN_TEXT_TXT -> Icons.Default.TextFields
    }
}

private fun formatColor(format: PdfExportFormat): Color {
    return when (format) {
        PdfExportFormat.WORD_DOCX -> Color(0xFF1565C0)      // Word Royal Blue
        PdfExportFormat.EXCEL_XLSX -> Color(0xFF2E7D32)     // Excel Forest Green
        PdfExportFormat.POWERPOINT_PPTX -> Color(0xFFD84315) // PowerPoint Warm Orange-Red
        PdfExportFormat.RTF_DOCUMENT -> Color(0xFF5E35B1)   // Deep Purple
        PdfExportFormat.CSV_SPREADSHEET -> Color(0xFF00796B) // Teal
        PdfExportFormat.HTML_WEBPAGE -> Color(0xFFE65100)   // HTML Amber
        PdfExportFormat.MARKDOWN_MD -> Color(0xFF37474F)    // Slate
        PdfExportFormat.PLAIN_TEXT_TXT -> Color(0xFF455A64) // Blue Grey
    }
}

private fun queryPdfFileName(context: Context, uri: Uri): String? = queryToolFileName(context, uri)

