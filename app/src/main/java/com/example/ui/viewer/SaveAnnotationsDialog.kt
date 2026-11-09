package com.example.ui.viewer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.LayersClear
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pdf.SaveAnnotationProgress

@Composable
fun SaveAnnotationsDialog(
    fileName: String,
    saveProgress: SaveAnnotationProgress?,
    onSaveOverwrite: () -> Unit,
    onSaveAsCopy: () -> Unit,
    onSaveFlattened: () -> Unit,
    onDismiss: () -> Unit
) {
    var showOverwriteWarning by remember { mutableStateOf(false) }

    if (saveProgress is SaveAnnotationProgress.Processing) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Saving Document") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Text(
                        text = saveProgress.message,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (saveProgress.totalPages > 0) {
                        LinearProgressIndicator(
                            progress = { saveProgress.currentPage.toFloat() / saveProgress.totalPages.toFloat() },
                            modifier = Modifier.fillMaxWidth().height(4.dp)
                        )
                    }
                }
            },
            confirmButton = {}
        )
        return
    }

    if (showOverwriteWarning) {
        AlertDialog(
            onDismissRequest = { showOverwriteWarning = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = { Text("Overwrite Original File?") },
            text = {
                Text("This will permanently write your new annotations into '$fileName'. This action cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showOverwriteWarning = false
                        onSaveOverwrite()
                    },
                    modifier = Modifier.testTag("btn_confirm_overwrite")
                ) {
                    Text("Overwrite Original")
                }
            },
            dismissButton = {
                TextButton(onClick = { showOverwriteWarning = false }) {
                    Text("Cancel")
                }
            }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Save Annotations",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Select how you would like to save your document:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                SaveOptionCard(
                    title = "Save Changes",
                    subtitle = "Embed vector annotations into the original file",
                    icon = Icons.Default.Save,
                    onClick = { showOverwriteWarning = true },
                    testTag = "option_save_overwrite"
                )

                SaveOptionCard(
                    title = "Save as Copy",
                    subtitle = "Export to a new PDF document via file picker",
                    icon = Icons.Default.ContentCopy,
                    onClick = {
                        onDismiss()
                        onSaveAsCopy()
                    },
                    testTag = "option_save_as_copy"
                )

                SaveOptionCard(
                    title = "Flatten Annotations",
                    subtitle = "Burn drawings & notes directly into page pixels permanently",
                    icon = Icons.Default.LayersClear,
                    onClick = {
                        onDismiss()
                        onSaveFlattened()
                    },
                    testTag = "option_save_flatten"
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun SaveOptionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    testTag: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag(testTag),
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
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
