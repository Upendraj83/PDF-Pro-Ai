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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.pdf.PdfMetadataItem
import com.example.pdf.PdfOptimizationEngine
import com.example.ui.PdfViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream

@Composable
fun MetadataEditorDialog(
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

    var title by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var keywords by remember { mutableStateOf("") }
    var creator by remember { mutableStateOf("") }
    var producer by remember { mutableStateOf("") }

    var isLoadingMeta by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var progressStatus by remember { mutableStateOf("") }
    var savedFile by remember { mutableStateOf<File?>(null) }
    var showSanitizeDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedUri = uri
            selectedFileName = queryToolFileName(context, uri) ?: "Document.pdf"
            savedFile = null
        }
    }

    // Load metadata whenever selectedUri changes
    LaunchedEffect(selectedUri) {
        val uri = selectedUri ?: return@LaunchedEffect
        isLoadingMeta = true
        errorMessage = null
        try {
            val meta = optimizationEngine.getMetadata(null, uri)
            title = meta.title
            author = meta.author
            subject = meta.subject
            keywords = meta.keywords
            creator = meta.creator
            producer = meta.producer
        } catch (e: Exception) {
            errorMessage = "Failed to read metadata: ${e.message}"
        } finally {
            isLoadingMeta = false
        }
    }

    val createDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destinationUri ->
        if (destinationUri != null && savedFile != null) {
            coroutineScope.launch {
                try {
                    context.contentResolver.openOutputStream(destinationUri)?.use { out ->
                        FileInputStream(savedFile!!).use { it.copyTo(out) }
                    }
                    Toast.makeText(context, "Saved successfully!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    if (showSanitizeDialog) {
        SanitizeConfirmDialog(
            uri = selectedUri,
            fileName = selectedFileName,
            optimizationEngine = optimizationEngine,
            onDismiss = { showSanitizeDialog = false },
            onSanitized = { file ->
                savedFile = file
                showSanitizeDialog = false
            }
        )
    }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        modifier = Modifier.testTag("metadata_editor_dialog"),
        icon = {
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = "Document Metadata",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = "Document Properties & Metadata",
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
                        .clickable { if (!isSaving) filePicker.launch(arrayOf("application/pdf")) }
                        .testTag("btn_select_metadata_pdf"),
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
                                text = if (selectedUri != null) "Tap to inspect another PDF" else "Choose PDF to view properties",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (isLoadingMeta) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                } else {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_meta_title")
                    )

                    OutlinedTextField(
                        value = author,
                        onValueChange = { author = it },
                        label = { Text("Author / Creator") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_meta_author")
                    )

                    OutlinedTextField(
                        value = subject,
                        onValueChange = { subject = it },
                        label = { Text("Subject") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_meta_subject")
                    )

                    OutlinedTextField(
                        value = keywords,
                        onValueChange = { keywords = it },
                        label = { Text("Keywords (comma separated)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_meta_keywords")
                    )

                    if (producer.isNotBlank() || creator.isNotBlank()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (creator.isNotBlank()) {
                                    Text(
                                        text = "Created by: $creator",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (producer.isNotBlank()) {
                                    Text(
                                        text = "Software Producer: $producer",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider()

                    // Quick Sanitize Button
                    OutlinedButton(
                        onClick = { showSanitizeDialog = true },
                        modifier = Modifier.fillMaxWidth().testTag("btn_open_sanitize_dialog")
                    ) {
                        Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sanitize Before Sharing...")
                    }

                    if (isSaving) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    if (errorMessage != null) {
                        Text(
                            text = errorMessage ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (savedFile == null) {
                Button(
                    onClick = {
                        val uri = selectedUri
                        if (uri == null) return@Button
                        isSaving = true
                        errorMessage = null

                        coroutineScope.launch {
                            try {
                                val updated = optimizationEngine.updateMetadata(
                                    pdfFile = null,
                                    uri = uri,
                                    newMetadata = PdfMetadataItem(
                                        title = title,
                                        author = author,
                                        subject = subject,
                                        keywords = keywords
                                    ),
                                    onProgress = { _, step -> progressStatus = step }
                                )
                                savedFile = updated
                                Toast.makeText(context, "Metadata updated!", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                errorMessage = "Failed to update metadata: ${e.localizedMessage}"
                            } finally {
                                isSaving = false
                            }
                        }
                    },
                    enabled = !isSaving && selectedUri != null && !isLoadingMeta,
                    modifier = Modifier.testTag("btn_save_metadata")
                ) {
                    Text("Save Changes")
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val shareUri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                savedFile!!
                            )
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(Intent.EXTRA_STREAM, shareUri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Document"))
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share")
                    }

                    Button(
                        onClick = {
                            createDocLauncher.launch("Updated_${selectedFileName.removeSuffix(".pdf")}.pdf")
                        }
                    ) {
                        Text("Save as Copy")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text(if (savedFile != null) "Done" else "Cancel")
            }
        }
    )
}

@Composable
fun SanitizeConfirmDialog(
    uri: Uri?,
    fileName: String,
    optimizationEngine: PdfOptimizationEngine,
    onDismiss: () -> Unit,
    onSanitized: (File) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var removeMetadata by remember { mutableStateOf(true) }
    var removeAttachments by remember { mutableStateOf(true) }
    var removeHiddenXmp by remember { mutableStateOf(true) }
    var removeAnnotations by remember { mutableStateOf(false) }

    var isSanitizing by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isSanitizing) onDismiss() },
        modifier = Modifier.testTag("sanitize_confirm_dialog"),
        icon = {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = "Sanitize PDF",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text("Sanitize Before Sharing", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Clean sensitive metadata, hidden streams, and embedded data before sending to clients or external parties:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                SanitizeOptionRow(
                    label = "Document Properties & Metadata",
                    description = "Wipes author, title, software producer, and dates",
                    checked = removeMetadata,
                    onCheckedChange = { removeMetadata = it }
                )

                SanitizeOptionRow(
                    label = "Hidden XMP Stream & Revision Logs",
                    description = "Removes background XML streams and version history",
                    checked = removeHiddenXmp,
                    onCheckedChange = { removeHiddenXmp = it }
                )

                SanitizeOptionRow(
                    label = "Embedded Attachments & Files",
                    description = "Removes attached spreadsheets, images, and raw files",
                    checked = removeAttachments,
                    onCheckedChange = { removeAttachments = it }
                )

                SanitizeOptionRow(
                    label = "Comments & Annotations",
                    description = "Strips user highlights, sticky notes, and drawing marks",
                    checked = removeAnnotations,
                    onCheckedChange = { removeAnnotations = it }
                )

                if (isSanitizing) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }

                if (errorMsg != null) {
                    Text(
                        text = errorMsg ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (uri == null) return@Button
                    isSanitizing = true
                    errorMsg = null

                    coroutineScope.launch {
                        try {
                            val sanitized = optimizationEngine.sanitizePdf(
                                pdfFile = null,
                                uri = uri,
                                removeMetadata = removeMetadata,
                                removeAttachments = removeAttachments,
                                removeHiddenXmp = removeHiddenXmp,
                                removeAnnotations = removeAnnotations,
                                onProgress = { _, _ -> }
                            )
                            Toast.makeText(context, "PDF successfully sanitized!", Toast.LENGTH_SHORT).show()
                            onSanitized(sanitized)
                        } catch (e: Exception) {
                            errorMsg = "Sanitize failed: ${e.localizedMessage}"
                        } finally {
                            isSanitizing = false
                        }
                    }
                },
                enabled = !isSanitizing && uri != null,
                modifier = Modifier.testTag("btn_confirm_sanitize")
            ) {
                Text("Sanitize & Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSanitizing) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun SanitizeOptionRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Spacer(modifier = Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
