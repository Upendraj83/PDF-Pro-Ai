package com.example.ui.tools

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.pdf.HeaderFooterConfig
import com.example.pdf.PageNumberPosition
import com.example.pdf.PdfOptimizationEngine
import com.example.pdf.WatermarkConfig
import com.example.pdf.WatermarkPosition
import com.example.ui.PdfViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WatermarkDialog(
    initialUri: Uri?,
    initialFileName: String?,
    viewModel: PdfViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val optimizationEngine = remember { PdfOptimizationEngine(context) }

    var selectedUri by remember { mutableStateOf(initialUri) }
    var selectedFileName by remember { mutableStateOf(initialFileName ?: "Select a PDF") }

    var selectedTabIndex by remember { mutableIntStateOf(0) }

    // Watermark State
    var enableWatermark by remember { mutableStateOf(true) }
    var isTextWatermark by remember { mutableStateOf(true) }
    var watermarkText by remember { mutableStateOf("CONFIDENTIAL") }
    var watermarkImageUri by remember { mutableStateOf<Uri?>(null) }
    var watermarkColor by remember { mutableIntStateOf(AndroidColor.RED) }
    var watermarkOpacity by remember { mutableFloatStateOf(0.30f) }
    var watermarkRotation by remember { mutableFloatStateOf(45f) }
    var watermarkFontSize by remember { mutableFloatStateOf(42f) }
    var watermarkPosition by remember { mutableStateOf(WatermarkPosition.CENTER) }
    var isTiled by remember { mutableStateOf(false) }
    var pageRangeMode by remember { mutableStateOf("all") } // "all", "odd", "even", "custom"
    var customPageRange by remember { mutableStateOf("") }

    // Header & Footer State
    var enableHeaderFooter by remember { mutableStateOf(false) }
    var showPageNumbers by remember { mutableStateOf(true) }
    var pageNumberPos by remember { mutableStateOf(PageNumberPosition.BOTTOM_CENTER) }
    var pageNumberFormat by remember { mutableStateOf("Page {page} of {total}") }
    var headerText by remember { mutableStateOf("") }
    var footerText by remember { mutableStateOf("") }

    var isProcessing by remember { mutableStateOf(false) }
    var progressFraction by remember { mutableFloatStateOf(0f) }
    var progressStatus by remember { mutableStateOf("") }
    var processedFile by remember { mutableStateOf<File?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showPrintProcessedDialog by remember { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedUri = uri
            selectedFileName = queryToolFileName(context, uri) ?: "Document.pdf"
            processedFile = null
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            watermarkImageUri = uri
            isTextWatermark = false
        }
    }

    val createDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destinationUri ->
        if (destinationUri != null && processedFile != null) {
            coroutineScope.launch {
                try {
                    context.contentResolver.openOutputStream(destinationUri)?.use { out ->
                        FileInputStream(processedFile!!).use { it.copyTo(out) }
                    }
                    Toast.makeText(context, "Watermarked PDF saved!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        modifier = Modifier.testTag("watermark_dialog"),
        icon = {
            Icon(
                imageVector = Icons.Default.WaterDrop,
                contentDescription = "Watermark & Numbers",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = "Watermark & Page Numbers",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // File Selector Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { if (!isProcessing) filePicker.launch(arrayOf("application/pdf")) }
                        .testTag("btn_select_watermark_pdf"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selectedFileName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (selectedUri != null) "Tap to change target PDF" else "Choose PDF to watermark",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (processedFile == null) {
                    // Live Page Preview Card
                    WatermarkLivePreviewCard(
                        enableWatermark = enableWatermark,
                        isTextWatermark = isTextWatermark,
                        watermarkText = watermarkText.ifBlank { "CONFIDENTIAL" },
                        hasLogoImage = watermarkImageUri != null,
                        watermarkColor = Color(watermarkColor),
                        watermarkOpacity = watermarkOpacity,
                        watermarkRotation = watermarkRotation,
                        watermarkPosition = watermarkPosition,
                        isTiled = isTiled || watermarkPosition == WatermarkPosition.TILED_GRID,
                        enableHeaderFooter = enableHeaderFooter,
                        headerText = headerText,
                        footerText = footerText,
                        showPageNumbers = showPageNumbers,
                        pageNumberFormat = pageNumberFormat
                    )

                    TabRow(selectedTabIndex = selectedTabIndex) {
                        Tab(
                            selected = selectedTabIndex == 0,
                            onClick = { selectedTabIndex = 0 },
                            text = { Text("Watermark") },
                            icon = { Icon(Icons.Default.WaterDrop, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                        Tab(
                            selected = selectedTabIndex == 1,
                            onClick = { selectedTabIndex = 1 },
                            text = { Text("Headers & Pages") },
                            icon = { Icon(Icons.Default.Numbers, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                    }

                    if (selectedTabIndex == 0) {
                        // Watermark Tab
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Apply Watermark", fontWeight = FontWeight.SemiBold)
                            Switch(checked = enableWatermark, onCheckedChange = { enableWatermark = it })
                        }

                        if (enableWatermark) {
                            // Text vs Image toggle
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = isTextWatermark,
                                    onClick = { isTextWatermark = true },
                                    label = { Text("Text Watermark") },
                                    leadingIcon = { Icon(Icons.Default.FormatColorText, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                )
                                FilterChip(
                                    selected = !isTextWatermark,
                                    onClick = {
                                        isTextWatermark = false
                                        if (watermarkImageUri == null) {
                                            imagePicker.launch("image/*")
                                        }
                                    },
                                    label = { Text("Image Logo") },
                                    leadingIcon = { Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                )
                            }

                            if (isTextWatermark) {
                                OutlinedTextField(
                                    value = watermarkText,
                                    onValueChange = { watermarkText = it },
                                    label = { Text("Watermark Text") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("input_watermark_text")
                                )

                                // Presets chips (English + Bengali)
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    listOf("CONFIDENTIAL", "DRAFT", "DO NOT COPY", "COPY", "গোপনীয়", "খসড়া").forEach { sample ->
                                        FilterChip(
                                            selected = watermarkText == sample,
                                            onClick = { watermarkText = sample },
                                            label = { Text(sample, style = MaterialTheme.typography.bodySmall) }
                                        )
                                    }
                                }

                                // Color picker chips
                                Text("Color", style = MaterialTheme.typography.titleSmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    val colors = listOf(
                                        AndroidColor.RED,
                                        AndroidColor.GRAY,
                                        AndroidColor.BLUE,
                                        AndroidColor.DKGRAY,
                                        AndroidColor.rgb(0, 128, 96),
                                        AndroidColor.rgb(180, 0, 0)
                                    )
                                    colors.forEach { c ->
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(Color(c))
                                                .border(
                                                    width = if (watermarkColor == c) 3.dp else 1.dp,
                                                    color = if (watermarkColor == c) MaterialTheme.colorScheme.primary else Color.LightGray,
                                                    shape = CircleShape
                                                )
                                                .clickable { watermarkColor = c }
                                        )
                                    }
                                }

                                // Font Size slider
                                Text("Font Size: ${watermarkFontSize.toInt()} pt", style = MaterialTheme.typography.titleSmall)
                                Slider(
                                    value = watermarkFontSize,
                                    onValueChange = { watermarkFontSize = it },
                                    valueRange = 24f..84f
                                )
                            } else {
                                OutlinedButton(
                                    onClick = { imagePicker.launch("image/*") },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(if (watermarkImageUri != null) "Change Logo Image" else "Select Logo Image")
                                }
                            }

                            // Placement / Position chips
                            Text("Placement & Pattern", style = MaterialTheme.typography.titleSmall)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                WatermarkPosition.entries.forEach { pos ->
                                    FilterChip(
                                        selected = watermarkPosition == pos,
                                        onClick = {
                                            watermarkPosition = pos
                                            isTiled = (pos == WatermarkPosition.TILED_GRID)
                                        },
                                        label = { Text(pos.label, style = MaterialTheme.typography.bodySmall) },
                                        modifier = Modifier.testTag("watermark_pos_${pos.name.lowercase()}")
                                    )
                                }
                            }

                            // Opacity slider
                            Text("Opacity: ${(watermarkOpacity * 100).toInt()}%", style = MaterialTheme.typography.titleSmall)
                            Slider(
                                value = watermarkOpacity,
                                onValueChange = { watermarkOpacity = it },
                                valueRange = 0.10f..0.85f
                            )

                            // Rotation slider
                            Text("Rotation: ${watermarkRotation.toInt()}°", style = MaterialTheme.typography.titleSmall)
                            Slider(
                                value = watermarkRotation,
                                onValueChange = { watermarkRotation = it },
                                valueRange = -90f..90f
                            )

                            // Tiled vs Single toggle
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Tiled Pattern", fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "Repeat watermark in a 3×3 grid across the page",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = isTiled,
                                    onCheckedChange = {
                                        isTiled = it
                                        if (it) watermarkPosition = WatermarkPosition.TILED_GRID
                                        else if (watermarkPosition == WatermarkPosition.TILED_GRID) {
                                            watermarkPosition = WatermarkPosition.CENTER
                                        }
                                    }
                                )
                            }

                            HorizontalDivider()

                            // Target Pages selection
                            Text("Apply to Pages", style = MaterialTheme.typography.titleSmall)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf(
                                    Pair("All Pages", "all"),
                                    Pair("Odd Pages", "odd"),
                                    Pair("Even Pages", "even"),
                                    Pair("Custom Range", "custom")
                                ).forEach { (lbl, mode) ->
                                    FilterChip(
                                        selected = pageRangeMode == mode,
                                        onClick = { pageRangeMode = mode },
                                        label = { Text(lbl, style = MaterialTheme.typography.bodySmall) }
                                    )
                                }
                            }
                            if (pageRangeMode == "custom") {
                                OutlinedTextField(
                                    value = customPageRange,
                                    onValueChange = { customPageRange = it },
                                    label = { Text("Page Range (e.g. 1-3, 5)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    } else {
                        // Header / Footer Tab
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Add Page Numbers & Headers", fontWeight = FontWeight.SemiBold)
                            Switch(checked = enableHeaderFooter, onCheckedChange = { enableHeaderFooter = it })
                        }

                        if (enableHeaderFooter) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Page Numbering", style = MaterialTheme.typography.bodyMedium)
                                Switch(checked = showPageNumbers, onCheckedChange = { showPageNumbers = it })
                            }

                            if (showPageNumbers) {
                                Text("Position", style = MaterialTheme.typography.titleSmall)
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    listOf(
                                        Pair("Bottom Center", PageNumberPosition.BOTTOM_CENTER),
                                        Pair("Bottom Right", PageNumberPosition.BOTTOM_RIGHT),
                                        Pair("Top Center", PageNumberPosition.TOP_CENTER),
                                        Pair("Top Right", PageNumberPosition.TOP_RIGHT)
                                    ).forEach { (lbl, pos) ->
                                        FilterChip(
                                            selected = pageNumberPos == pos,
                                            onClick = { pageNumberPos = pos },
                                            label = { Text(lbl, style = MaterialTheme.typography.bodySmall) }
                                        )
                                    }
                                }

                                OutlinedTextField(
                                    value = pageNumberFormat,
                                    onValueChange = { pageNumberFormat = it },
                                    label = { Text("Format Pattern") },
                                    supportingText = { Text("Use {page} for number, {total} for count") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            HorizontalDivider()

                            OutlinedTextField(
                                value = headerText,
                                onValueChange = { headerText = it },
                                label = { Text("Header Text (Top Left)") },
                                placeholder = { Text("e.g. Confidential Project Report") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = footerText,
                                onValueChange = { footerText = it },
                                label = { Text("Footer Text (Bottom Left)") },
                                placeholder = { Text("e.g. Internal Use Only") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    if (isProcessing) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            LinearProgressIndicator(progress = { progressFraction }, modifier = Modifier.fillMaxWidth())
                            Text(progressStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    if (errorMessage != null) {
                        Text(
                            text = errorMessage ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                "Watermark & Headers Applied!",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Your watermarked PDF is ready to open in the viewer, print, save as a copy, or share.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val file = processedFile ?: return@OutlinedButton
                                        viewModel.overwriteOriginalWithSavedFile(file)
                                        viewModel.reloadEditedPdfInViewer(file)
                                        Toast.makeText(context, "Opened watermarked PDF in viewer", Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("btn_open_watermarked_in_viewer")
                                ) {
                                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("View")
                                }

                                OutlinedButton(
                                    onClick = {
                                        showPrintProcessedDialog = true
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp)
                                        .testTag("btn_print_watermarked_pdf")
                                ) {
                                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Print")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (processedFile == null) {
                Button(
                    onClick = {
                        val uri = selectedUri
                        if (uri == null) {
                            errorMessage = "Please choose a PDF document."
                            return@Button
                        }
                        if (!enableWatermark && !enableHeaderFooter) {
                            errorMessage = "Please enable Watermark or Headers/Footers."
                            return@Button
                        }
                        errorMessage = null
                        isProcessing = true

                        coroutineScope.launch {
                            try {
                                val resolvedRange = when (pageRangeMode) {
                                    "odd" -> "odd"
                                    "even" -> "even"
                                    "custom" -> customPageRange.trim()
                                    else -> ""
                                }

                                val watermarkCfg = if (enableWatermark) {
                                    WatermarkConfig(
                                        isText = isTextWatermark,
                                        text = watermarkText,
                                        imageUri = watermarkImageUri,
                                        color = watermarkColor,
                                        opacity = watermarkOpacity,
                                        rotationDegrees = watermarkRotation,
                                        isTiled = isTiled || watermarkPosition == WatermarkPosition.TILED_GRID,
                                        fontSize = watermarkFontSize,
                                        position = watermarkPosition,
                                        pageRange = resolvedRange
                                    )
                                } else null

                                val headerCfg = if (enableHeaderFooter) {
                                    HeaderFooterConfig(
                                        showPageNumbers = showPageNumbers,
                                        pageNumberPosition = pageNumberPos,
                                        pageNumberFormat = pageNumberFormat,
                                        headerText = headerText,
                                        footerText = footerText
                                    )
                                } else null

                                val file = optimizationEngine.applyWatermarkAndDecorations(
                                    pdfFile = null,
                                    uri = uri,
                                    watermarkConfig = watermarkCfg,
                                    headerFooterConfig = headerCfg,
                                    onProgress = { fraction, step ->
                                        progressFraction = fraction
                                        progressStatus = step
                                    }
                                )
                                processedFile = file
                            } catch (e: Exception) {
                                errorMessage = "Failed: ${e.localizedMessage}"
                            } finally {
                                isProcessing = false
                            }
                        }
                    },
                    enabled = !isProcessing && selectedUri != null,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("btn_apply_watermark")
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Applying...")
                    } else {
                        Text("Apply & Save")
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val shareUri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                processedFile!!
                            )
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(Intent.EXTRA_STREAM, shareUri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Watermarked PDF"))
                        },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share")
                    }

                    Button(
                        onClick = {
                            createDocLauncher.launch("Watermarked_${selectedFileName.removeSuffix(".pdf")}.pdf")
                        },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("Save as Copy")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isProcessing,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(if (processedFile != null) "Done" else "Cancel")
            }
        }
    )

    if (showPrintProcessedDialog && processedFile != null) {
        PrintPdfDialog(
            initialUri = null,
            initialFileName = "Watermarked_$selectedFileName",
            initialFile = processedFile,
            onDismiss = { showPrintProcessedDialog = false }
        )
    }
}

@Composable
private fun WatermarkLivePreviewCard(
    enableWatermark: Boolean,
    isTextWatermark: Boolean,
    watermarkText: String,
    hasLogoImage: Boolean,
    watermarkColor: Color,
    watermarkOpacity: Float,
    watermarkRotation: Float,
    watermarkPosition: WatermarkPosition,
    isTiled: Boolean,
    enableHeaderFooter: Boolean,
    headerText: String,
    footerText: String,
    showPageNumbers: Boolean,
    pageNumberFormat: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(148.dp)
            .testTag("watermark_live_preview"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(12.dp))
                .padding(10.dp)
        ) {
            // Simulated document lines
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 18.dp, horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(5) { idx ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(if (idx == 4) 0.55f else 0.92f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xFFEEEEEE))
                    )
                }
            }

            // Header / Footer preview
            if (enableHeaderFooter) {
                if (headerText.isNotBlank()) {
                    Text(
                        text = headerText,
                        fontSize = 8.sp,
                        color = Color.DarkGray,
                        maxLines = 1,
                        modifier = Modifier.align(Alignment.TopStart)
                    )
                }
                if (footerText.isNotBlank()) {
                    Text(
                        text = footerText,
                        fontSize = 8.sp,
                        color = Color.DarkGray,
                        maxLines = 1,
                        modifier = Modifier.align(Alignment.BottomStart)
                    )
                }
                if (showPageNumbers) {
                    Text(
                        text = pageNumberFormat.replace("{page}", "1").replace("{total}", "5"),
                        fontSize = 8.sp,
                        color = Color.DarkGray,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }

            // Watermark Layer Preview
            if (enableWatermark) {
                val previewLabel = if (isTextWatermark) {
                    watermarkText
                } else if (hasLogoImage) {
                    "[LOGO IMAGE]"
                } else {
                    "[SELECT LOGO]"
                }

                if (isTiled) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceEvenly,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        repeat(2) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                repeat(3) {
                                    Text(
                                        text = previewLabel,
                                        color = watermarkColor,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        modifier = Modifier
                                            .alpha(watermarkOpacity)
                                            .rotate(-watermarkRotation)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    val boxAlign = when (watermarkPosition) {
                        WatermarkPosition.TOP_LEFT -> Alignment.TopStart
                        WatermarkPosition.TOP_RIGHT -> Alignment.TopEnd
                        WatermarkPosition.BOTTOM_LEFT -> Alignment.BottomStart
                        WatermarkPosition.BOTTOM_RIGHT -> Alignment.BottomEnd
                        else -> Alignment.Center
                    }
                    Text(
                        text = previewLabel,
                        color = watermarkColor,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        modifier = Modifier
                            .align(boxAlign)
                            .padding(8.dp)
                            .alpha(watermarkOpacity)
                            .rotate(-watermarkRotation)
                    )
                }
            }
        }
    }
}
