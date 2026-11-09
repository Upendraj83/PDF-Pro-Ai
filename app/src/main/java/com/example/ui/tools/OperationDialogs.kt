package com.example.ui.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.pdf.OperationProgress
import java.io.File

@Composable
fun OperationProgressDialog(
    progress: OperationProgress.Running,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* Modal: require cancel click */ },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.5.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = progress.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (progress.progressFraction >= 0f) {
                    LinearProgressIndicator(
                        progress = { progress.progressFraction },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                    )
                    Text(
                        text = "${(progress.progressFraction * 100).toInt()}% completed",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                    )
                }

                Text(
                    text = progress.stepDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            if (progress.canCancel) {
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.testTag("btn_cancel_operation")
                ) {
                    Text("Cancel", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    )
}

@Composable
fun OperationResultDialog(
    result: OperationProgress.Success,
    onOpenPdf: (File) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val primary = result.primaryFile ?: result.outputFiles.firstOrNull()
    val primaryMime = rememberMimeType(primary, result.isZip)

    val saveCopyLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(primaryMime)
    ) { destUri: Uri? ->
        if (destUri != null && primary != null && primary.exists()) {
            try {
                context.contentResolver.openOutputStream(destUri)?.use { out ->
                    primary.inputStream().use { input ->
                        input.copyTo(out)
                    }
                }
                Toast.makeText(context, "Saved ${primary.name} to device", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Save failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xFF2E7D32).copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Success",
                        tint = Color(0xFF2E7D32),
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = result.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Operation Finished",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = result.message,
                    style = MaterialTheme.typography.bodyMedium
                )

                if (primary != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val isPdf = primary.name.endsWith(".pdf", ignoreCase = true)
                            Icon(
                                imageVector = when {
                                    result.isZip -> Icons.Default.FolderZip
                                    isPdf -> Icons.Default.PictureAsPdf
                                    else -> Icons.Default.Description
                                },
                                contentDescription = null,
                                tint = if (result.isZip) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = primary.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = formatFileSize(primary.length()),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(
                                onClick = { saveCopyLauncher.launch(primary.name) },
                                modifier = Modifier.testTag("btn_save_copy_result")
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Save")
                            }
                        }
                    }
                }

                if (result.outputFiles.size > 1) {
                    Text(
                        text = "${result.outputFiles.size} files generated in total.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (primary != null) {
                    // Share button
                    OutlinedButton(
                        onClick = { shareGeneratedFile(context, primary, result.isZip) },
                        modifier = Modifier.testTag("btn_share_result")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share")
                    }

                    // Open button (opens PDF, Word .docx, Excel .xlsx, PPTX, JPEG/PNG, CSV, RTF, TXT in built-in viewer)
                    Button(
                        onClick = {
                            if (!result.isZip && !primary.name.endsWith(".zip", ignoreCase = true)) {
                                onOpenPdf(primary)
                            } else {
                                openWithSystem(context, primary)
                            }
                            onDismiss()
                        },
                        modifier = Modifier.testTag("btn_open_result")
                    ) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
fun OperationFailureDialog(
    failure: OperationProgress.Failure,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = "Error",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(failure.title, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Text(failure.error, style = MaterialTheme.typography.bodyMedium)
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}

private fun rememberMimeType(file: File?, isZip: Boolean): String {
    if (isZip) return "application/zip"
    val ext = file?.extension?.lowercase(java.util.Locale.US).orEmpty()
    return when (ext) {
        "pdf" -> "application/pdf"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "rtf" -> "application/rtf"
        "csv" -> "text/csv"
        "html", "htm" -> "text/html"
        "md" -> "text/markdown"
        "txt" -> "text/plain"
        "zip" -> "application/zip"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        else -> "*/*"
    }
}

private fun shareGeneratedFile(context: Context, file: File, isZip: Boolean) {
    try {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val mimeType = rememberMimeType(file, isZip)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share ${file.name}"))
    } catch (_: Exception) {
    }
}

private fun openWithSystem(context: Context, file: File) {
    try {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val mimeType = rememberMimeType(file, file.name.endsWith(".zip", true))
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(viewIntent, "Open ${file.name}"))
    } catch (_: Exception) {
    }
}

private fun formatFileSize(bytes: Long): String = com.example.ui.common.formatFileSize(bytes)

internal fun queryFileName(context: Context, uri: Uri): String? = queryToolFileName(context, uri)

internal fun queryToolFileName(context: Context, uri: Uri): String? {
    return try {
        var name: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        name = cursor.getString(nameIndex)
                    }
                }
            }
        }
        if (name == null) {
            name = uri.path
            val cut = name?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                name = name?.substring(cut + 1)
            }
        }
        name ?: uri.lastPathSegment
    } catch (_: Exception) {
        uri.lastPathSegment
    }
}

internal fun parseToolPageRangeList(text: String, maxPages: Int = 10000): List<Int> {
    if (text.isBlank() || maxPages <= 0) return emptyList()
    val list = mutableListOf<Int>()
    for (part in text.split(",")) {
        val trimmed = part.trim()
        if (trimmed.isEmpty()) continue
        if (trimmed.contains("-")) {
            val range = trimmed.split("-")
            val start = (range.getOrNull(0)?.trim()?.toIntOrNull() ?: 1) - 1
            val end = (range.getOrNull(1)?.trim()?.toIntOrNull() ?: (start + 1)) - 1
            val validStart = start.coerceIn(0, maxPages - 1)
            val validEnd = end.coerceIn(0, maxPages - 1)
            if (validStart <= validEnd) {
                for (p in validStart..validEnd) list.add(p)
            }
        } else {
            val p = (trimmed.toIntOrNull() ?: 1) - 1
            if (p in 0 until maxPages) list.add(p)
        }
    }
    return list.distinct()
}

