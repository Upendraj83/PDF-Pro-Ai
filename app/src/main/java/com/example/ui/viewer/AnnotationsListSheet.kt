package com.example.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pdf.AnnotationType
import com.example.pdf.PdfAnnotation
import com.example.ui.PdfViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnotationsListSheet(
    viewModel: PdfViewModel,
    isNightMode: Boolean,
    onJumpToPage: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val annotations by viewModel.allAnnotations.collectAsStateWithLifecycle()

    var selectedFilter by remember { mutableStateOf("ALL") }

    val filtered = remember(annotations, selectedFilter) {
        when (selectedFilter) {
            "HIGHLIGHT" -> annotations.filter { it is PdfAnnotation.TextMarkup }
            "NOTES" -> annotations.filter { it is PdfAnnotation.StickyNote || it is PdfAnnotation.FreeTextBox }
            "DRAWINGS" -> annotations.filter { it is PdfAnnotation.InkStroke }
            "SHAPES" -> annotations.filter { it is PdfAnnotation.GeometricShape }
            else -> annotations
        }
    }

    val groupedByPage = remember(filtered) {
        filtered.groupBy { it.pageIndex }.toSortedMap()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = if (isNightMode) Color(0xFF1E1E1E) else MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Annotations (${annotations.size})",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isNightMode) Color.White else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Tap to jump",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "ALL" to "All",
                    "HIGHLIGHT" to "Highlights",
                    "NOTES" to "Notes",
                    "DRAWINGS" to "Drawings",
                    "SHAPES" to "Shapes"
                ).forEach { (key, label) ->
                    FilterChip(
                        selected = selectedFilter == key,
                        onClick = { selectedFilter = key },
                        label = { Text(label) }
                    )
                }
            }

            if (annotations.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No annotations in this document yet.\nUse the toolbar at the bottom to add highlights, notes, or ink.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(440.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    groupedByPage.forEach { (pageIndex, pageAnnots) ->
                        item {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            ) {
                                Text(
                                    text = "Page ${pageIndex + 1} (${pageAnnots.size})",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        items(pageAnnots, key = { it.id }) { annot ->
                            AnnotationListItem(
                                annot = annot,
                                isNightMode = isNightMode,
                                onClick = {
                                    viewModel.selectAnnotation(annot.id)
                                    onJumpToPage(annot.pageIndex)
                                    onDismiss()
                                },
                                onDelete = { viewModel.deleteAnnotation(annot.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnnotationListItem(
    annot: PdfAnnotation,
    isNightMode: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val (icon, title, previewText) = getAnnotationMetadata(annot)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("annotation_item_${annot.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isNightMode) Color(0xFF252525) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
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
                // Color & Icon indicator
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(annot.colorArgb).copy(alpha = 0.2f))
                        .border(1.5.dp, Color(annot.colorArgb), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color(annot.colorArgb),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isNightMode) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                        if (annot.isExistingFromPdf) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = "From PDF",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }

                    if (previewText.isNotBlank()) {
                        Text(
                            text = previewText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag("btn_delete_annot_${annot.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete annotation",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private fun getAnnotationMetadata(annot: PdfAnnotation): Triple<ImageVector, String, String> {
    return when (annot) {
        is PdfAnnotation.TextMarkup -> {
            val icon = when (annot.type) {
                AnnotationType.HIGHLIGHT -> Icons.Default.Highlight
                AnnotationType.UNDERLINE -> Icons.Default.FormatUnderlined
                AnnotationType.STRIKEOUT -> Icons.Default.FormatStrikethrough
                else -> Icons.Default.Highlight
            }
            val title = when (annot.type) {
                AnnotationType.HIGHLIGHT -> "Highlight"
                AnnotationType.UNDERLINE -> "Underline"
                AnnotationType.STRIKEOUT -> "Strikeout"
                else -> "Markup"
            }
            Triple(icon, title, annot.selectedText)
        }
        is PdfAnnotation.StickyNote -> {
            Triple(Icons.Default.ChatBubble, "Sticky Note", annot.noteText.ifBlank { "Personal note" })
        }
        is PdfAnnotation.FreeTextBox -> {
            Triple(Icons.Default.TextFields, "Text Box", annot.text)
        }
        is PdfAnnotation.InkStroke -> {
            val title = if (annot.type == AnnotationType.HIGHLIGHTER_PEN) "Highlighter Pen" else "Pen Drawing"
            Triple(Icons.Default.Brush, title, "${annot.points.size} points")
        }
        is PdfAnnotation.GeometricShape -> {
            val title = when (annot.type) {
                AnnotationType.RECTANGLE -> "Rectangle"
                AnnotationType.ELLIPSE -> "Ellipse"
                AnnotationType.LINE -> "Line"
                AnnotationType.ARROW -> "Arrow"
                else -> "Shape"
            }
            Triple(Icons.Default.Category, title, "")
        }
    }
}
