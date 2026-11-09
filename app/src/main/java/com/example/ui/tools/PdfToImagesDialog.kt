package com.example.ui.tools

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.pdf.ExportDpi
import com.example.pdf.ExportImageFormat
import com.example.pdf.SplitDestination

@Composable
fun PdfToImagesDialog(
    initialUri: Uri? = null,
    initialName: String? = null,
    totalDocPages: Int = 0,
    onExportConfirmed: (
        uri: Uri,
        fileName: String,
        pageIndices: List<Int>,
        format: ExportImageFormat,
        dpi: ExportDpi,
        destination: SplitDestination,
        folderUri: Uri?
    ) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedUri by remember { mutableStateOf(initialUri) }
    var selectedFileName by remember { mutableStateOf(initialName ?: "") }

    var isAllPages by remember { mutableStateOf(true) }
    var customPageRange by remember { mutableStateOf("1-5") }
    var selectedFormat by remember { mutableStateOf(ExportImageFormat.JPG) }
    var selectedDpi by remember { mutableStateOf(ExportDpi.DPI_150) }
    var destination by remember { mutableStateOf(SplitDestination.ZIP_ARCHIVE) }
    var selectedFolderUri by remember { mutableStateOf<Uri?>(null) }
    var folderName by remember { mutableStateOf("") }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedUri = uri
            selectedFileName = queryFileName(context, uri) ?: "Document.pdf"
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            selectedFolderUri = treeUri
            destination = SplitDestination.FOLDER_TREE
            folderName = treeUri.lastPathSegment ?: "Chosen Folder"
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "PDF to Images",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Render pages to high-resolution JPG or PNG",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Source Document Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                Icons.Default.PictureAsPdf,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (selectedUri != null) selectedFileName else "Select a PDF",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        OutlinedButton(
                            onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) }
                        ) {
                            Text(if (selectedUri != null) "Change" else "Pick PDF")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Page Selection
                Text("Pages to Export", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = isAllPages,
                        onClick = { isAllPages = true },
                        label = { Text("All Pages") }
                    )
                    FilterChip(
                        selected = !isAllPages,
                        onClick = { isAllPages = false },
                        label = { Text("Custom Range") }
                    )
                }

                if (!isAllPages) {
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = customPageRange,
                        onValueChange = { customPageRange = it },
                        label = { Text("Page Range (e.g. 1-5, 8)") },
                        modifier = Modifier.fillMaxWidth().testTag("input_pdf_to_images_range")
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Format & DPI Selection
                Text("Image Format", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedFormat == ExportImageFormat.JPG,
                        onClick = { selectedFormat = ExportImageFormat.JPG },
                        label = { Text("JPG (Smaller file)") }
                    )
                    FilterChip(
                        selected = selectedFormat == ExportImageFormat.PNG,
                        onClick = { selectedFormat = ExportImageFormat.PNG },
                        label = { Text("PNG (Lossless)") }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text("Resolution (DPI)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExportDpi.entries.forEach { dpi ->
                        FilterChip(
                            selected = selectedDpi == dpi,
                            onClick = { selectedDpi = dpi },
                            label = { Text("${dpi.dpi} DPI") }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Divider()
                Spacer(modifier = Modifier.height(12.dp))

                // Output Destination
                Text("Save Destination", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = destination == SplitDestination.ZIP_ARCHIVE,
                            onClick = { destination = SplitDestination.ZIP_ARCHIVE },
                            role = Role.RadioButton
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = destination == SplitDestination.ZIP_ARCHIVE, onClick = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("ZIP Archive (All images in one archive)", style = MaterialTheme.typography.bodyMedium)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = destination == SplitDestination.FOLDER_TREE,
                            onClick = { folderPickerLauncher.launch(null) },
                            role = Role.RadioButton
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = destination == SplitDestination.FOLDER_TREE, onClick = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Save directly into folder", style = MaterialTheme.typography.bodyMedium)
                        if (selectedFolderUri != null) {
                            Text("Folder: $folderName", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val uri = selectedUri ?: return@Button
                            val maxPages = totalDocPages.takeIf { it > 0 } ?: 1000
                            val pages = if (isAllPages) {
                                (0 until maxPages).toList()
                            } else {
                                parseToolPageRangeList(customPageRange, maxPages)
                            }
                            onExportConfirmed(uri, selectedFileName, pages, selectedFormat, selectedDpi, destination, selectedFolderUri)
                        },
                        enabled = selectedUri != null,
                        modifier = Modifier.testTag("btn_confirm_pdf_to_images")
                    ) {
                        Text("Export Images")
                    }
                }
            }
        }
    }
}
