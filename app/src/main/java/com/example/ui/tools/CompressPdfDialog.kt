package com.example.ui.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.pdf.CompressionPreset
import com.example.pdf.CompressionResult
import com.example.pdf.PdfOptimizationEngine
import com.example.ui.PdfViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

@Composable
fun CompressPdfDialog(
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

    var selectedPreset by remember { mutableStateOf(CompressionPreset.MEDIUM) }
    var targetSizeInput by remember { mutableStateOf("1.0") }
    var targetSizeUnit by remember { mutableStateOf("MB") }
    var aggressiveScanMode by remember { mutableStateOf(false) }

    var isProcessing by remember { mutableStateOf(false) }
    var progressFraction by remember { mutableFloatStateOf(0f) }
    var progressStatus by remember { mutableStateOf("") }
    var compressionResult by remember { mutableStateOf<CompressionResult?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedUri = uri
            selectedFileName = queryToolFileName(context, uri) ?: "Selected_Document.pdf"
            compressionResult = null
        }
    }

    val createDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destinationUri ->
        if (destinationUri != null && compressionResult != null) {
            coroutineScope.launch {
                try {
                    context.contentResolver.openOutputStream(destinationUri)?.use { out ->
                        FileInputStream(compressionResult!!.outputFile).use { input ->
                            input.copyTo(out)
                        }
                    }
                    Toast.makeText(context, "Saved successfully!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        modifier = Modifier.testTag("compress_pdf_dialog"),
        icon = {
            Icon(
                imageVector = Icons.Default.Compress,
                contentDescription = "Compress PDF",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = "Compress PDF",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // File Selector Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { if (!isProcessing) filePicker.launch(arrayOf("application/pdf")) }
                        .testTag("btn_select_compress_pdf"),
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
                                text = if (selectedUri != null) "Tap to choose a different PDF" else "Tap to choose PDF from device",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (compressionResult == null) {
                    // Presets
                    Text(
                        text = "Compression Level",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        PresetOptionRow(
                            title = "Medium Compression (Recommended)",
                            subtitle = "Balanced quality & ~50-70% size reduction",
                            selected = selectedPreset == CompressionPreset.MEDIUM,
                            onClick = { selectedPreset = CompressionPreset.MEDIUM }
                        )
                        PresetOptionRow(
                            title = "Low Compression",
                            subtitle = "Highest visual fidelity, slight optimization (~20-40%)",
                            selected = selectedPreset == CompressionPreset.LOW,
                            onClick = { selectedPreset = CompressionPreset.LOW }
                        )
                        PresetOptionRow(
                            title = "High Compression",
                            subtitle = "Smallest file size, best for email & messaging (~70-85%)",
                            selected = selectedPreset == CompressionPreset.HIGH,
                            onClick = { selectedPreset = CompressionPreset.HIGH }
                        )
                        PresetOptionRow(
                            title = "Target Size",
                            subtitle = "Specify maximum desired file threshold (e.g. < 1 MB)",
                            selected = selectedPreset == CompressionPreset.TARGET_SIZE,
                            onClick = { selectedPreset = CompressionPreset.TARGET_SIZE }
                        )
                    }

                    // Target size input
                    AnimatedVisibility(visible = selectedPreset == CompressionPreset.TARGET_SIZE) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = targetSizeInput,
                                onValueChange = { targetSizeInput = it },
                                label = { Text("Maximum Target") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                FilterChip(
                                    selected = targetSizeUnit == "MB",
                                    onClick = { targetSizeUnit = "MB" },
                                    label = { Text("MB") }
                                )
                                FilterChip(
                                    selected = targetSizeUnit == "KB",
                                    onClick = { targetSizeUnit = "KB" },
                                    label = { Text("KB") }
                                )
                            }
                        }
                    }

                    HorizontalDivider()

                    // Scanned document aggressive mode
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Aggressive Scan Mode",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Rasterize whole pages into downsampled scans (shrinks heavy vector charts)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = aggressiveScanMode,
                            onCheckedChange = { aggressiveScanMode = it }
                        )
                    }

                    // Progress indicator if active
                    if (isProcessing) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            LinearProgressIndicator(
                                progress = { progressFraction },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = progressStatus,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
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
                } else {
                    // Result Card Display
                    val res = compressionResult!!
                    val percentSaved = if (res.originalSize > 0) {
                        val diff = res.originalSize - res.compressedSize
                        ((diff.toFloat() / res.originalSize) * 100).toInt().coerceAtLeast(0)
                    } else 0

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = "Compression Completed!",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Original: ${formatFileSize(res.originalSize)}  →  Compressed: ${formatFileSize(res.compressedSize)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Size reduced by $percentSaved%",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )

                            if (res.bestEffortMessage != null) {
                                Text(
                                    text = res.bestEffortMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (compressionResult == null) {
                Button(
                    onClick = {
                        val uri = selectedUri
                        if (uri == null) {
                            errorMessage = "Please choose a PDF document first."
                            return@Button
                        }
                        errorMessage = null
                        isProcessing = true

                        coroutineScope.launch {
                            try {
                                val targetBytes = if (selectedPreset == CompressionPreset.TARGET_SIZE) {
                                    val num = targetSizeInput.toDoubleOrNull() ?: 1.0
                                    if (targetSizeUnit == "MB") (num * 1024 * 1024).toLong()
                                    else (num * 1024).toLong()
                                } else null

                                val result = optimizationEngine.compressPdf(
                                    pdfFile = null,
                                    uri = uri,
                                    preset = selectedPreset,
                                    targetSizeBytes = targetBytes,
                                    aggressiveScanMode = aggressiveScanMode,
                                    onProgress = { fraction, step ->
                                        progressFraction = fraction
                                        progressStatus = step
                                    }
                                )
                                compressionResult = result
                            } catch (e: Exception) {
                                errorMessage = "Optimization error: ${e.localizedMessage}"
                            } finally {
                                isProcessing = false
                            }
                        }
                    },
                    enabled = !isProcessing && selectedUri != null,
                    modifier = Modifier.testTag("btn_start_compression")
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Optimizing...")
                    } else {
                        Text("Compress Document")
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val file = compressionResult!!.outputFile
                            val shareUri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file
                            )
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(Intent.EXTRA_STREAM, shareUri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Compressed PDF"))
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share")
                    }

                    Button(
                        onClick = {
                            createDocLauncher.launch("Compressed_${selectedFileName.removeSuffix(".pdf")}.pdf")
                        }
                    ) {
                        Text("Save as Copy")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isProcessing
            ) {
                Text(if (compressionResult != null) "Done" else "Cancel")
            }
        }
    )
}

@Composable
private fun PresetOptionRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(modifier = Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.2f MB", bytes / (1024f * 1024f))
        bytes >= 1024 -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1024f)
        else -> "$bytes B"
    }
}
