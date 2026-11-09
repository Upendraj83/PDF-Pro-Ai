package com.example.ui.common

import android.text.format.DateUtils
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.RecentPdfEntity

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "Unknown size"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(java.util.Locale.US, "%.2f GB", gb)
        mb >= 1.0 -> String.format(java.util.Locale.US, "%.1f MB", mb)
        kb >= 1.0 -> String.format(java.util.Locale.US, "%.0f KB", kb)
        else -> "$bytes B"
    }
}

fun formatRelativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 60_000 -> "Just now"
        diff < 3600_000 -> "${diff / 60_000}m ago"
        diff < 86400_000 -> "${diff / 3600_000}h ago"
        else -> DateUtils.getRelativeTimeSpanString(timestamp, now, DateUtils.DAY_IN_MILLIS).toString()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecentPdfItemCard(
    pdf: RecentPdfEntity,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onAssignFolder: (() -> Unit)? = null,
    onWatermark: (() -> Unit)? = null,
    onPrint: (() -> Unit)? = null,
    isBiometricProtected: Boolean = false,
    onToggleBiometricProtection: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    val lowerName = pdf.fileName.lowercase(java.util.Locale.US)
    val (fileIcon, fileBgColor, fileIconTint, formatBadgeLabel) = when {
        isBiometricProtected -> listOf(
            Icons.Default.Fingerprint,
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
            MaterialTheme.colorScheme.error,
            "LOCKED"
        )
        lowerName.endsWith(".docx") || lowerName.endsWith(".doc") || lowerName.endsWith(".rtf") -> listOf(
            Icons.Default.Description,
            Color(0xFFE3F2FD),
            Color(0xFF1565C0),
            if (lowerName.endsWith(".rtf")) "RTF" else "WORD"
        )
        lowerName.endsWith(".xlsx") || lowerName.endsWith(".xls") || lowerName.endsWith(".csv") || lowerName.endsWith(".tsv") -> listOf(
            Icons.Default.TableChart,
            Color(0xFFE8F5E9),
            Color(0xFF2E7D32),
            if (lowerName.endsWith(".csv") || lowerName.endsWith(".tsv")) "CSV" else "EXCEL"
        )
        lowerName.endsWith(".pptx") || lowerName.endsWith(".ppt") -> listOf(
            Icons.Default.Slideshow,
            Color(0xFFFBE9E7),
            Color(0xFFD84315),
            "PPTX"
        )
        lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") || lowerName.endsWith(".png") || lowerName.endsWith(".webp") -> listOf(
            Icons.Default.Image,
            Color(0xFFFFF3E0),
            Color(0xFFE65100),
            if (lowerName.endsWith(".png")) "PNG" else "JPEG"
        )
        else -> listOf(
            Icons.Default.PictureAsPdf,
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
            "PDF"
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("recent_pdf_${pdf.fileName}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // File Format Icon with badge
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(fileBgColor as Color),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = fileIcon as ImageVector,
                    contentDescription = null,
                    tint = fileIconTint as Color,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // File Info (flexible height & FlowRow so 200% font scale never clips text)
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = pdf.fileName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (formatBadgeLabel != "PDF" && formatBadgeLabel != "LOCKED") {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = (fileBgColor as Color)
                        ) {
                            Text(
                                text = formatBadgeLabel as String,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = (fileIconTint as Color),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (isBiometricProtected) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Fingerprint,
                                    contentDescription = "Protected",
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "Protected",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = formatFileSize(pdf.fileSizeBytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatRelativeTime(pdf.lastOpenedTimestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (pdf.folderName.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = pdf.folderName,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }
                }

                if (pdf.pageCount > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    val readPage = (pdf.lastPage + 1).coerceAtMost(pdf.pageCount)
                    val progressPercent = ((readPage.toFloat() / pdf.pageCount.toFloat()) * 100).toInt()
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = stringResource(R.string.pdf_page_progress, readPage, pdf.pageCount),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = stringResource(R.string.pdf_percent_read, progressPercent),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }

            // Favorite button (min 48dp touch target + TalkBack description)
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("fav_button_${pdf.fileName}")
            ) {
                val tint by animateColorAsState(
                    targetValue = if (pdf.isFavorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline,
                    label = "fav_color"
                )
                Icon(
                    imageVector = if (pdf.isFavorite) Icons.Default.Star else Icons.Outlined.StarBorder,
                    contentDescription = if (pdf.isFavorite) {
                        stringResource(R.string.cd_favorite_remove, pdf.fileName)
                    } else {
                        stringResource(R.string.cd_favorite_add, pdf.fileName)
                    },
                    tint = tint
                )
            }

            // More Options Menu (min 48dp touch target + TalkBack description)
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("more_menu_${pdf.fileName}")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.cd_more_options, pdf.fileName),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_share)) },
                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onShare()
                        }
                    )
                    if (onAssignFolder != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_move_to_folder)) },
                            leadingIcon = {
                                Icon(Icons.Default.CreateNewFolder, contentDescription = null)
                            },
                            onClick = {
                                showMenu = false
                                onAssignFolder()
                            }
                        )
                    }
                    if (onWatermark != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_watermark_pdf)) },
                            leadingIcon = { Icon(Icons.Default.WaterDrop, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onWatermark()
                            }
                        )
                    }
                    if (onPrint != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_print_pdf)) },
                            leadingIcon = { Icon(Icons.Default.Print, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onPrint()
                            }
                        )
                    }
                    if (onToggleBiometricProtection != null) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (isBiometricProtected) "Remove Biometric Lock"
                                    else "Protect with Biometrics"
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (isBiometricProtected) Icons.Default.LockOpen else Icons.Default.Fingerprint,
                                    contentDescription = null,
                                    tint = if (isBiometricProtected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary
                                )
                            },
                            onClick = {
                                showMenu = false
                                onToggleBiometricProtection()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(R.string.action_remove_from_recents),
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AssignFolderDialog(
    pdfFileName: String,
    currentFolder: String,
    availableFolders: List<String>,
    onAssignFolder: (String) -> Unit,
    onCreateAndAssignFolder: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newFolderInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.folder_assign_title),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = pdfFileName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = currentFolder.isBlank(),
                        onClick = {
                            onAssignFolder("")
                            onDismiss()
                        },
                        label = { Text(stringResource(R.string.folder_remove_from_folder)) },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    )

                    availableFolders.forEach { folder ->
                        FilterChip(
                            selected = currentFolder.equals(folder, ignoreCase = true),
                            onClick = {
                                onAssignFolder(folder)
                                onDismiss()
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Folder,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = { Text(folder) },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        )
                    }
                }

                OutlinedTextField(
                    value = newFolderInput,
                    onValueChange = { newFolderInput = it },
                    label = { Text(stringResource(R.string.folder_name_hint)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_assign_new_folder")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = newFolderInput.trim()
                    if (trimmed.isNotEmpty()) {
                        onCreateAndAssignFolder(trimmed)
                    }
                    onDismiss()
                },
                enabled = newFolderInput.trim().isNotEmpty(),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

