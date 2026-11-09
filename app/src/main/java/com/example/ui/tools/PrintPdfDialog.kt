package com.example.ui.tools

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Print
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.pdf.PdfPrintHelper
import com.example.pdf.PrintJobConfig
import com.example.pdf.PrintOrientation
import com.example.pdf.PrintPageSelection
import com.example.pdf.PrintPaperSize
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PrintPdfDialog(
    initialUri: Uri?,
    initialFileName: String?,
    initialFile: File? = null,
    currentPageIndex: Int = 0,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val printHelper = remember { PdfPrintHelper(context) }

    var selectedUri by remember { mutableStateOf(initialUri) }
    var selectedFile by remember { mutableStateOf(initialFile) }
    var selectedFileName by remember {
        mutableStateOf(
            initialFileName
                ?: initialFile?.name
                ?: "Select a PDF to Print"
        )
    }

    var pageSelection by remember { mutableStateOf(PrintPageSelection.ALL_PAGES) }
    var customRangeText by remember { mutableStateOf("") }
    var paperSize by remember { mutableStateOf(PrintPaperSize.A4) }
    var orientation by remember { mutableStateOf(PrintOrientation.PORTRAIT) }
    var isColor by remember { mutableStateOf(true) }

    var includePrintWatermark by remember { mutableStateOf(false) }
    var printWatermarkText by remember { mutableStateOf("CONFIDENTIAL") }
    var printWatermarkOpacity by remember { mutableFloatStateOf(0.25f) }
    var includePageNumbers by remember { mutableStateOf(false) }

    var isPreparingPrint by remember { mutableStateOf(false) }
    var progressFraction by remember { mutableFloatStateOf(0f) }
    var progressMessage by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedUri = uri
            selectedFile = null
            selectedFileName = queryToolFileName(context, uri) ?: "Document.pdf"
            errorMessage = null
        }
    }

    val hasSource = selectedUri != null || (selectedFile != null && selectedFile!!.exists())

    AlertDialog(
        onDismissRequest = { if (!isPreparingPrint) onDismiss() },
        modifier = Modifier.testTag("print_pdf_dialog"),
        icon = {
            Icon(
                imageVector = Icons.Default.Print,
                contentDescription = stringResource(R.string.print_pdf_title),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = stringResource(R.string.print_pdf_title),
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
                // Target PDF Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (!isPreparingPrint) filePicker.launch(arrayOf("application/pdf"))
                        }
                        .testTag("btn_select_print_pdf"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
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
                                text = if (hasSource) "Tap to select a different PDF" else "Tap to choose a PDF file",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Page Range Selection
                Text(
                    text = "Pages to Print",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        Pair("All Pages", PrintPageSelection.ALL_PAGES),
                        Pair("Current (#${currentPageIndex + 1})", PrintPageSelection.CURRENT_PAGE),
                        Pair("Odd Only", PrintPageSelection.ODD_PAGES),
                        Pair("Even Only", PrintPageSelection.EVEN_PAGES),
                        Pair("Custom Range", PrintPageSelection.CUSTOM_RANGE)
                    ).forEach { (label, mode) ->
                        FilterChip(
                            selected = pageSelection == mode,
                            onClick = { pageSelection = mode },
                            label = { Text(label, style = MaterialTheme.typography.bodySmall) },
                            modifier = Modifier.testTag("print_range_${mode.name.lowercase()}")
                        )
                    }
                }

                if (pageSelection == PrintPageSelection.CUSTOM_RANGE) {
                    OutlinedTextField(
                        value = customRangeText,
                        onValueChange = { customRangeText = it },
                        label = { Text("Page Range (e.g. 1-3, 5)") },
                        placeholder = { Text("1-3, 5") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_print_custom_range")
                    )
                }

                HorizontalDivider()

                // Paper Size & Orientation
                Text(
                    text = "Paper Size & Layout",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    PrintPaperSize.entries.forEach { size ->
                        FilterChip(
                            selected = paperSize == size,
                            onClick = { paperSize = size },
                            label = { Text(size.name, style = MaterialTheme.typography.bodySmall) },
                            modifier = Modifier.testTag("print_paper_${size.name.lowercase()}")
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrintOrientation.entries.forEach { orient ->
                        FilterChip(
                            selected = orientation == orient,
                            onClick = { orientation = orient },
                            label = { Text(orient.label, style = MaterialTheme.typography.bodySmall) },
                            modifier = Modifier.testTag("print_orient_${orient.name.lowercase()}")
                        )
                    }
                    FilterChip(
                        selected = isColor,
                        onClick = { isColor = true },
                        label = { Text("Color", style = MaterialTheme.typography.bodySmall) },
                        modifier = Modifier.testTag("print_mode_color")
                    )
                    FilterChip(
                        selected = !isColor,
                        onClick = { isColor = false },
                        label = { Text("B&W", style = MaterialTheme.typography.bodySmall) },
                        modifier = Modifier.testTag("print_mode_bw")
                    )
                }

                HorizontalDivider()

                // Optional Print-Time Watermark (without altering original file)
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
                        Icon(
                            imageVector = Icons.Default.WaterDrop,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "Stamp Watermark on Printout",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Adds watermark to printed copy only",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = includePrintWatermark,
                        onCheckedChange = { includePrintWatermark = it },
                        modifier = Modifier.testTag("switch_print_watermark")
                    )
                }

                if (includePrintWatermark) {
                    OutlinedTextField(
                        value = printWatermarkText,
                        onValueChange = { printWatermarkText = it },
                        label = { Text("Print Watermark Text") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_print_watermark_text")
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf("CONFIDENTIAL", "COPY", "DRAFT", "গোপনীয়").forEach { preset ->
                            FilterChip(
                                selected = printWatermarkText == preset,
                                onClick = { printWatermarkText = preset },
                                label = { Text(preset, style = MaterialTheme.typography.bodySmall) }
                            )
                        }
                    }
                    Text(
                        text = "Watermark Opacity: ${(printWatermarkOpacity * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Slider(
                        value = printWatermarkOpacity,
                        onValueChange = { printWatermarkOpacity = it },
                        valueRange = 0.10f..0.75f
                    )
                }

                // Optional Page Numbers on Printout
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Print Page Numbers",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Adds 'Page X of Y' footer on printed pages",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = includePageNumbers,
                        onCheckedChange = { includePageNumbers = it },
                        modifier = Modifier.testTag("switch_print_page_numbers")
                    )
                }

                if (isPreparingPrint) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = { progressFraction },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = progressMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!hasSource) {
                        errorMessage = "Please select a PDF document to print."
                        return@Button
                    }
                    errorMessage = null
                    isPreparingPrint = true

                    val jobConfig = PrintJobConfig(
                        pageSelection = pageSelection,
                        currentPageIndex = currentPageIndex,
                        customRangeText = customRangeText,
                        paperSize = paperSize,
                        orientation = orientation,
                        isColor = isColor,
                        includePrintWatermark = includePrintWatermark,
                        printWatermarkText = printWatermarkText,
                        printWatermarkOpacity = printWatermarkOpacity,
                        includePageNumbers = includePageNumbers
                    )

                    coroutineScope.launch {
                        try {
                            val printFile = printHelper.preparePrintPdfFile(
                                sourceFile = selectedFile,
                                sourceUri = selectedUri,
                                config = jobConfig,
                                onProgress = { frac, msg ->
                                    progressFraction = frac
                                    progressMessage = msg
                                }
                            )
                            val launched = printHelper.launchSystemPrintJob(
                                preparedPdfFile = printFile,
                                documentDisplayName = selectedFileName,
                                config = jobConfig
                            )
                            if (launched) {
                                onDismiss()
                            } else {
                                errorMessage = "Android Print Service is unavailable on this device."
                            }
                        } catch (e: Exception) {
                            errorMessage = "Print failed: ${e.localizedMessage ?: "Unknown error"}"
                            Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
                        } finally {
                            isPreparingPrint = false
                        }
                    }
                },
                enabled = !isPreparingPrint && hasSource,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("btn_confirm_print_pdf")
            ) {
                if (isPreparingPrint) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Preparing...")
                } else {
                    Icon(
                        imageVector = Icons.Default.Print,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.action_print_pdf))
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isPreparingPrint,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("btn_cancel_print_pdf")
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
