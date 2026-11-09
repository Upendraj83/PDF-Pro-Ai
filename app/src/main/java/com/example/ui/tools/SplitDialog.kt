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
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
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
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.pdf.SplitDestination
import com.example.pdf.SplitMode

@Composable
fun SplitDialog(
    initialUri: Uri? = null,
    initialName: String? = null,
    onSplitConfirmed: (
        uri: Uri,
        fileName: String,
        mode: SplitMode,
        destination: SplitDestination,
        folderUri: Uri?
    ) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var selectedUri by remember { mutableStateOf(initialUri) }
    var selectedFileName by remember { mutableStateOf(initialName ?: "") }

    var selectedModeType by remember { mutableIntStateOf(0) } // 0: Ranges, 1: Every N, 2: Bookmarks
    var rangeExpression by remember { mutableStateOf("1-3, 5, 7-9") }
    var everyNPages by remember { mutableIntStateOf(1) }
    var destination by remember { mutableStateOf(SplitDestination.ZIP_ARCHIVE) }
    var selectedFolderUri by remember { mutableStateOf<Uri?>(null) }
    var folderName by remember { mutableStateOf("") }

    // Picker for source PDF if not provided
    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedUri = uri
            selectedFileName = queryFileName(context, uri) ?: "Selected_Document.pdf"
        }
    }

    // Picker for target Folder
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
                                imageVector = Icons.Default.CallSplit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Split PDF",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Separate into smaller files or chapters",
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

                // Document Selection Card
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
                                text = if (selectedUri != null) selectedFileName else "No document chosen",
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

                Text(
                    text = "Split Method",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Mode 0: Custom Ranges
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = selectedModeType == 0,
                            onClick = { selectedModeType = 0 },
                            role = Role.RadioButton
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = selectedModeType == 0, onClick = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("By Custom Page Ranges", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("e.g. 1-3, 5, 7-9 creates three separate PDF files", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                if (selectedModeType == 0) {
                    OutlinedTextField(
                        value = rangeExpression,
                        onValueChange = { rangeExpression = it },
                        label = { Text("Page Ranges (1-indexed)") },
                        placeholder = { Text("1-3, 5, 7-9") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 36.dp, top = 4.dp, bottom = 8.dp)
                            .testTag("input_split_ranges")
                    )
                }

                // Mode 1: Every N Pages
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = selectedModeType == 1,
                            onClick = { selectedModeType = 1 },
                            role = Role.RadioButton
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = selectedModeType == 1, onClick = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Every N Pages", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("Split document into equal chunks of N pages", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                if (selectedModeType == 1) {
                    Row(
                        modifier = Modifier
                            .padding(start = 36.dp, top = 4.dp, bottom = 8.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Pages per file:")
                        listOf(1, 2, 5, 10).forEach { n ->
                            Button(
                                onClick = { everyNPages = n },
                                colors = if (everyNPages == n) androidx.compose.material3.ButtonDefaults.buttonColors()
                                else androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
                            ) {
                                Text("$n")
                            }
                        }
                    }
                }

                // Mode 2: By Bookmarks
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = selectedModeType == 2,
                            onClick = { selectedModeType = 2 },
                            role = Role.RadioButton
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = selectedModeType == 2, onClick = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("By Bookmarks / Table of Contents", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("Splits chapters based on outline bookmarks", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Divider()
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Save Destination",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

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
                    Text("Package as single ZIP file (easy sharing & opening)", style = MaterialTheme.typography.bodyMedium)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = destination == SplitDestination.FOLDER_TREE,
                            onClick = {
                                folderPickerLauncher.launch(null)
                            },
                            role = Role.RadioButton
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = destination == SplitDestination.FOLDER_TREE, onClick = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Save to folder via file picker", style = MaterialTheme.typography.bodyMedium)
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
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val uri = selectedUri ?: return@Button
                            val mode = when (selectedModeType) {
                                0 -> SplitMode.ByRanges(rangeExpression)
                                1 -> SplitMode.EveryNPages(everyNPages)
                                else -> SplitMode.ByBookmarks
                            }
                            onSplitConfirmed(uri, selectedFileName, mode, destination, selectedFolderUri)
                        },
                        enabled = selectedUri != null,
                        modifier = Modifier.testTag("btn_confirm_split")
                    ) {
                        Text("Split PDF")
                    }
                }
            }
        }
    }
}

