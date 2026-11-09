package com.example.ui.viewer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoFixNormal
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pdf.AnnotationType
import com.example.ui.PdfViewModel

@Composable
fun AnnotationToolbar(
    viewModel: PdfViewModel,
    isNightMode: Boolean,
    modifier: Modifier = Modifier
) {
    val activeTool by viewModel.activeAnnotationTool.collectAsStateWithLifecycle()
    val activeColor by viewModel.activeAnnotationColor.collectAsStateWithLifecycle()
    val activeThickness by viewModel.activeAnnotationThickness.collectAsStateWithLifecycle()
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by viewModel.canRedo.collectAsStateWithLifecycle()
    val selectedId by viewModel.selectedAnnotationId.collectAsStateWithLifecycle()

    var showColorPicker by remember { mutableStateOf(false) }
    var showThicknessPicker by remember { mutableStateOf(false) }
    var showShapeSelector by remember { mutableStateOf(false) }

    val presetColors = remember {
        listOf(
            0xFFFFEB3B.toInt(), // Yellow
            0xFFE53935.toInt(), // Red
            0xFF43A047.toInt(), // Green
            0xFF1E88E5.toInt(), // Blue
            0xFF8E24AA.toInt(), // Purple
            0xFFFB8C00.toInt(), // Orange
            0xFF212121.toInt(), // Dark
            0xFFFFFFFF.toInt()  // White
        )
    }

    val presetThicknesses = listOf(1f, 2f, 4f, 8f, 16f)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = if (isNightMode) Color(0xFF1E1E1E) else MaterialTheme.colorScheme.surface,
        shadowElevation = 12.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            // Secondary Options Drawer (Color / Thickness / Shapes)
            AnimatedVisibility(
                visible = showColorPicker,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Color:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    presetColors.forEach { colorArgb ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(colorArgb))
                                .border(
                                    width = if (activeColor == colorArgb) 3.dp else 1.dp,
                                    color = if (activeColor == colorArgb) MaterialTheme.colorScheme.primary else Color.LightGray,
                                    shape = CircleShape
                                )
                                .clickable {
                                    viewModel.setAnnotationColor(colorArgb)
                                    showColorPicker = false
                                }
                                .testTag("color_picker_$colorArgb")
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = showThicknessPicker,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Thickness:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    presetThicknesses.forEach { thickness ->
                        FilterChip(
                            selected = activeThickness == thickness,
                            onClick = {
                                viewModel.setAnnotationThickness(thickness)
                                showThicknessPicker = false
                            },
                            label = { Text("${thickness.toInt()}pt") }
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = showShapeSelector,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(
                        AnnotationType.RECTANGLE to "Rectangle",
                        AnnotationType.ELLIPSE to "Ellipse",
                        AnnotationType.LINE to "Line",
                        AnnotationType.ARROW to "Arrow"
                    ).forEach { (shape, name) ->
                        FilterChip(
                            selected = activeTool == shape,
                            onClick = {
                                viewModel.setActiveAnnotationTool(shape)
                                showShapeSelector = false
                            },
                            label = { Text(name) }
                        )
                    }
                }
            }

            // Primary Tools Row (Horizontally Scrollable)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pan / View mode
                ToolIconButton(
                    icon = Icons.Default.PanTool,
                    label = "Pan",
                    isSelected = activeTool == null,
                    onClick = {
                        viewModel.setActiveAnnotationTool(null)
                        showColorPicker = false
                        showThicknessPicker = false
                    },
                    testTag = "tool_pan"
                )

                // Highlight
                ToolIconButton(
                    icon = Icons.Default.Highlight,
                    label = "Highlight",
                    isSelected = activeTool == AnnotationType.HIGHLIGHT,
                    onClick = {
                        viewModel.setActiveAnnotationTool(AnnotationType.HIGHLIGHT)
                        viewModel.setAnnotationColor(0x88FFEB3B.toInt()) // Default semi-transparent yellow
                    },
                    testTag = "tool_highlight"
                )

                // Underline
                ToolIconButton(
                    icon = Icons.Default.FormatUnderlined,
                    label = "Underline",
                    isSelected = activeTool == AnnotationType.UNDERLINE,
                    onClick = {
                        viewModel.setActiveAnnotationTool(AnnotationType.UNDERLINE)
                        viewModel.setAnnotationColor(0xFFE53935.toInt())
                    },
                    testTag = "tool_underline"
                )

                // Strikeout
                ToolIconButton(
                    icon = Icons.Default.FormatStrikethrough,
                    label = "Strikeout",
                    isSelected = activeTool == AnnotationType.STRIKEOUT,
                    onClick = {
                        viewModel.setActiveAnnotationTool(AnnotationType.STRIKEOUT)
                        viewModel.setAnnotationColor(0xFFE53935.toInt())
                    },
                    testTag = "tool_strikeout"
                )

                // Freehand Pen
                ToolIconButton(
                    icon = Icons.Default.Edit,
                    label = "Pen",
                    isSelected = activeTool == AnnotationType.PEN,
                    onClick = {
                        viewModel.setActiveAnnotationTool(AnnotationType.PEN)
                        showThicknessPicker = true
                    },
                    testTag = "tool_pen"
                )

                // Highlighter Pen
                ToolIconButton(
                    icon = Icons.Default.Brush,
                    label = "Highlighter",
                    isSelected = activeTool == AnnotationType.HIGHLIGHTER_PEN,
                    onClick = {
                        viewModel.setActiveAnnotationTool(AnnotationType.HIGHLIGHTER_PEN)
                        viewModel.setAnnotationThickness(16f)
                        viewModel.setAnnotationColor(0x77FFEB3B.toInt())
                    },
                    testTag = "tool_highlighter_pen"
                )

                // Eraser
                ToolIconButton(
                    icon = Icons.Default.AutoFixNormal,
                    label = "Eraser",
                    isSelected = false,
                    onClick = {
                        // Triggers eraser action or tool
                        viewModel.eraseLastOrActive()
                    },
                    testTag = "tool_eraser"
                )

                // Sticky Note
                ToolIconButton(
                    icon = Icons.Default.ChatBubble,
                    label = "Note",
                    isSelected = activeTool == AnnotationType.STICKY_NOTE,
                    onClick = {
                        viewModel.setActiveAnnotationTool(AnnotationType.STICKY_NOTE)
                    },
                    testTag = "tool_sticky_note"
                )

                // Free Text Box
                ToolIconButton(
                    icon = Icons.Default.TextFields,
                    label = "Text",
                    isSelected = activeTool == AnnotationType.TEXT_BOX,
                    onClick = {
                        viewModel.setActiveAnnotationTool(AnnotationType.TEXT_BOX)
                    },
                    testTag = "tool_text_box"
                )

                // Shapes
                ToolIconButton(
                    icon = Icons.Default.Category,
                    label = "Shapes",
                    isSelected = activeTool in listOf(AnnotationType.RECTANGLE, AnnotationType.ELLIPSE, AnnotationType.LINE, AnnotationType.ARROW),
                    onClick = {
                        showShapeSelector = !showShapeSelector
                    },
                    testTag = "tool_shapes"
                )

                // Color picker toggle
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(activeColor))
                        .border(1.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        .clickable { showColorPicker = !showColorPicker }
                        .testTag("tool_color_toggle")
                )

                Spacer(modifier = Modifier.width(4.dp))

                // Undo
                IconButton(
                    onClick = { viewModel.undo() },
                    enabled = canUndo,
                    modifier = Modifier.testTag("tool_undo")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                }

                // Redo
                IconButton(
                    onClick = { viewModel.redo() },
                    enabled = canRedo,
                    modifier = Modifier.testTag("tool_redo")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                }

                // Delete Selected
                if (selectedId != null) {
                    IconButton(
                        onClick = { viewModel.deleteSelectedAnnotation() },
                        modifier = Modifier.testTag("tool_delete_selected")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete annotation",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }

                // All Annotations List Screen
                IconButton(
                    onClick = { viewModel.setAnnotationsListOpen(true) },
                    modifier = Modifier.testTag("tool_annotations_list")
                ) {
                    Icon(Icons.Default.FormatListBulleted, contentDescription = "View all annotations")
                }

                // Save PDF with Annotations
                IconButton(
                    onClick = { viewModel.setSaveDialogVisible(true) },
                    modifier = Modifier.testTag("tool_save_annotations")
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = "Save PDF with annotations",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                // Close Toolbar
                IconButton(
                    onClick = { viewModel.toggleAnnotationToolbar() },
                    modifier = Modifier.testTag("tool_close_toolbar")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close annotation tools")
                }
            }
        }
    }
}

@Composable
private fun ToolIconButton(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.testTag(testTag)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1
            )
        }
    }
}
