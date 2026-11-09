package com.example.ui.viewer

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CopyAll
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatAlignRight
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.GppBad
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.pdf.BundledEditFont
import com.example.pdf.ContentEditElement
import com.example.pdf.ContentEditTool
import com.example.pdf.ExistingTextLine
import com.example.pdf.NormalizedPoint
import com.example.pdf.NormalizedRect
import com.example.pdf.PdfLinkType
import com.example.pdf.TextAlignmentMode
import com.example.scan.ScanImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

val NotoSansComposeFontFamily = FontFamily(Font(R.font.noto_sans))
val NotoSansBengaliComposeFontFamily = FontFamily(Font(R.font.noto_sans_bengali))

fun BundledEditFont.toComposeFontFamily(): FontFamily = when {
    this == BundledEditFont.NOTO_SANS -> NotoSansComposeFontFamily
    this == BundledEditFont.NOTO_SANS_BENGALI -> NotoSansBengaliComposeFontFamily
    this.customFontResId != null -> try {
        FontFamily(Font(this.customFontResId))
    } catch (_: Exception) {
        NotoSansBengaliComposeFontFamily
    }
    this == BundledEditFont.HELVETICA -> FontFamily.SansSerif
    this == BundledEditFont.TIMES_ROMAN -> FontFamily.Serif
    this == BundledEditFont.COURIER -> FontFamily.Monospace
    else -> FontFamily.SansSerif
}

@Composable
fun ContentEditPageOverlay(
    pageIndex: Int,
    isContentEditActive: Boolean,
    activeTool: ContentEditTool,
    pageElements: List<ContentEditElement>,
    existingTextLines: List<ExistingTextLine>,
    selectedElementId: String?,
    onSelectElement: (String?) -> Unit,
    onTapAddTextAt: (NormalizedPoint) -> Unit,
    onTapExistingTextLine: (ExistingTextLine) -> Unit,
    onTapAddLinkAt: (NormalizedRect) -> Unit,
    onDragAddRedaction: (NormalizedRect) -> Unit,
    onUpdateElement: (ContentEditElement, pushUndo: Boolean) -> Unit,
    onExecuteLink: (ContentEditElement.LinkArea) -> Unit,
    modifier: Modifier = Modifier
) {
    var dragStartNorm by remember { mutableStateOf<NormalizedPoint?>(null) }
    var dragCurrentNorm by remember { mutableStateOf<NormalizedPoint?>(null) }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val boxW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val boxH = constraints.maxHeight.toFloat().coerceAtLeast(1f)

        // 1. Background canvas for tool gestures (Add Text tap, Link/Redaction rectangle drag, deselect)
        if (isContentEditActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(activeTool, pageIndex, existingTextLines) {
                        detectTapGestures { tapOffset ->
                            val normX = (tapOffset.x / boxW).coerceIn(0.02f, 0.92f)
                            val normY = (tapOffset.y / boxH).coerceIn(0.02f, 0.92f)
                            val pt = NormalizedPoint(normX, normY)

                            when (activeTool) {
                                ContentEditTool.ADD_TEXT -> {
                                    onTapAddTextAt(pt)
                                }
                                ContentEditTool.EDIT_TEXT -> {
                                    val hitLine = existingTextLines.firstOrNull { line ->
                                        normX in (line.bounds.left - 0.015f)..(line.bounds.right + 0.015f) &&
                                            normY in (line.bounds.top - 0.015f)..(line.bounds.bottom + 0.015f)
                                    }
                                    if (hitLine != null) {
                                        onTapExistingTextLine(hitLine)
                                    }
                                }
                                ContentEditTool.REDACT -> {
                                    // If user taps an existing text line in Redact mode, redact that line's bounding box
                                    val hitLine = existingTextLines.firstOrNull { line ->
                                        normX in line.bounds.left..line.bounds.right &&
                                            normY in line.bounds.top..line.bounds.bottom
                                    }
                                    if (hitLine != null) {
                                        onDragAddRedaction(hitLine.bounds)
                                    } else {
                                        onDragAddRedaction(
                                            NormalizedRect(
                                                left = normX,
                                                top = normY,
                                                right = (normX + 0.28f).coerceAtMost(0.98f),
                                                bottom = (normY + 0.045f).coerceAtMost(0.98f)
                                            )
                                        )
                                    }
                                }
                                ContentEditTool.ADD_LINK -> {
                                    onTapAddLinkAt(
                                        NormalizedRect(
                                            left = normX,
                                            top = normY,
                                            right = (normX + 0.30f).coerceAtMost(0.98f),
                                            bottom = (normY + 0.05f).coerceAtMost(0.98f)
                                        )
                                    )
                                }
                                else -> {
                                    onSelectElement(null)
                                }
                            }
                        }
                    }
                    .pointerInput(activeTool, pageIndex) {
                        if (activeTool == ContentEditTool.REDACT || activeTool == ContentEditTool.ADD_LINK) {
                            detectDragGestures(
                                onDragStart = { start ->
                                    val pt = NormalizedPoint(
                                        (start.x / boxW).coerceIn(0f, 1f),
                                        (start.y / boxH).coerceIn(0f, 1f)
                                    )
                                    dragStartNorm = pt
                                    dragCurrentNorm = pt
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    dragCurrentNorm = NormalizedPoint(
                                        (change.position.x / boxW).coerceIn(0f, 1f),
                                        (change.position.y / boxH).coerceIn(0f, 1f)
                                    )
                                },
                                onDragEnd = {
                                    val s = dragStartNorm
                                    val e = dragCurrentNorm
                                    dragStartNorm = null
                                    dragCurrentNorm = null
                                    if (s != null && e != null) {
                                        val rect = NormalizedRect(
                                            left = min(s.x, e.x),
                                            top = min(s.y, e.y),
                                            right = max(s.x, e.x).coerceAtLeast(min(s.x, e.x) + 0.05f),
                                            bottom = max(s.y, e.y).coerceAtLeast(min(s.y, e.y) + 0.03f)
                                        )
                                        if (activeTool == ContentEditTool.REDACT) {
                                            onDragAddRedaction(rect)
                                        } else if (activeTool == ContentEditTool.ADD_LINK) {
                                            onTapAddLinkAt(rect)
                                        }
                                    }
                                },
                                onDragCancel = {
                                    dragStartNorm = null
                                    dragCurrentNorm = null
                                }
                            )
                        }
                    }
            )
        }

        // 2. Highlight detected existing text lines when in EDIT_TEXT or REDACT mode
        if (isContentEditActive && (activeTool == ContentEditTool.EDIT_TEXT || activeTool == ContentEditTool.REDACT)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeColor = if (activeTool == ContentEditTool.REDACT) {
                    Color(0xFFD32F2F).copy(alpha = 0.65f)
                } else {
                    Color(0xFF00897B).copy(alpha = 0.65f)
                }
                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                for (line in existingTextLines) {
                    val leftPx = line.bounds.left * size.width
                    val topPx = line.bounds.top * size.height
                    val wPx = (line.bounds.right - line.bounds.left) * size.width
                    val hPx = (line.bounds.bottom - line.bounds.top) * size.height
                    drawRect(
                        color = strokeColor.copy(alpha = 0.08f),
                        topLeft = Offset(leftPx, topPx),
                        size = Size(wPx, hPx)
                    )
                    drawRect(
                        color = strokeColor,
                        topLeft = Offset(leftPx, topPx),
                        size = Size(wPx, hPx),
                        style = Stroke(width = 1.5.dp.toPx(), pathEffect = dashEffect)
                    )
                }
            }
        }

        // 3. Render all ContentEditElements sorted by zIndex (layer order)
        val sortedElements = remember(pageElements) { pageElements.sortedBy { it.zIndex } }
        for (element in sortedElements) {
            val isSelected = isContentEditActive && element.id == selectedElementId
            val leftPx = (element.bounds.left * boxW).roundToInt()
            val topPx = (element.bounds.top * boxH).roundToInt()
            val widthDp = with(density) { ((element.bounds.right - element.bounds.left) * boxW).coerceAtLeast(28f).toDp() }
            val heightDp = with(density) { ((element.bounds.bottom - element.bounds.top) * boxH).coerceAtLeast(22f).toDp() }

            var liveBounds by remember(element.id, element.bounds) { mutableStateOf(element.bounds) }

            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (liveBounds.left * boxW).roundToInt(),
                            (liveBounds.top * boxH).roundToInt()
                        )
                    }
                    .size(
                        width = with(density) { ((liveBounds.right - liveBounds.left) * boxW).coerceAtLeast(28f).toDp() },
                        height = with(density) { ((liveBounds.bottom - liveBounds.top) * boxH).coerceAtLeast(22f).toDp() }
                    )
                    .then(
                        if (isSelected) {
                            Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                        } else if (isContentEditActive) {
                            Modifier.border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), RoundedCornerShape(2.dp))
                        } else {
                            Modifier
                        }
                    )
                    .clickable(enabled = isContentEditActive || element is ContentEditElement.LinkArea) {
                        if (isContentEditActive) {
                            onSelectElement(element.id)
                        } else if (element is ContentEditElement.LinkArea) {
                            onExecuteLink(element)
                        }
                    }
                    .pointerInput(isContentEditActive, element.id, boxW, boxH) {
                        if (isContentEditActive) {
                            detectDragGestures(
                                onDragStart = {
                                    onSelectElement(element.id)
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    val dx = dragAmount.x / boxW
                                    val dy = dragAmount.y / boxH
                                    val w = liveBounds.right - liveBounds.left
                                    val h = liveBounds.bottom - liveBounds.top
                                    val newLeft = (liveBounds.left + dx).coerceIn(0f, 1f - w)
                                    val newTop = (liveBounds.top + dy).coerceIn(0f, 1f - h)
                                    liveBounds = NormalizedRect(
                                        left = newLeft,
                                        top = newTop,
                                        right = newLeft + w,
                                        bottom = newTop + h
                                    )
                                },
                                onDragEnd = {
                                    onUpdateElement(element.withUpdatedBounds(liveBounds), true)
                                }
                            )
                        }
                    }
            ) {
                when (element) {
                    is ContentEditElement.AddedText -> {
                        Text(
                            text = element.text,
                            color = Color(element.colorArgb),
                            fontSize = element.fontSizeSp.sp,
                            fontFamily = element.font.toComposeFontFamily(),
                            fontWeight = if (element.isBold) FontWeight.Bold else FontWeight.Normal,
                            fontStyle = if (element.isItalic) FontStyle.Italic else FontStyle.Normal,
                            textAlign = when (element.alignment) {
                                TextAlignmentMode.LEFT -> TextAlign.Start
                                TextAlignmentMode.CENTER -> TextAlign.Center
                                TextAlignmentMode.RIGHT -> TextAlign.End
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(2.dp)
                        )
                    }

                    is ContentEditElement.ReplacedText -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(element.backgroundColorArgb)),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = element.replacementText,
                                color = Color(element.textColorArgb),
                                fontSize = element.fontSizeSp.sp,
                                fontFamily = element.font.toComposeFontFamily(),
                                fontWeight = if (element.isBold) FontWeight.Bold else FontWeight.Normal,
                                fontStyle = if (element.isItalic) FontStyle.Italic else FontStyle.Normal,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 2.dp)
                            )
                        }
                    }

                    is ContentEditElement.ImageObject -> {
                        ImageObjectPreview(element = element)
                    }

                    is ContentEditElement.LinkArea -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0x221E88E5))
                                .border(1.dp, Color(0xFF1E88E5), RoundedCornerShape(3.dp)),
                            contentAlignment = Alignment.BottomStart
                        ) {
                            if (isContentEditActive) {
                                Surface(
                                    color = Color(0xCC1E88E5),
                                    shape = RoundedCornerShape(topEnd = 4.dp)
                                ) {
                                    val label = when (element.linkType) {
                                        PdfLinkType.WEB_URL -> "URL"
                                        PdfLinkType.EMAIL -> "Mail"
                                        PdfLinkType.PAGE_JUMP -> "P.${element.targetPageIndex + 1}"
                                    }
                                    Text(
                                        text = label,
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 9.sp,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }

                    is ContentEditElement.RedactionMark -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black)
                                .border(1.5.dp, Color(0xFFE53935)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "REDACTED",
                                color = Color(0xFFFF5252),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                maxLines = 1
                            )
                        }
                    }
                }

                // Bottom-Right Resize Handle when selected
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .border(2.dp, Color.White, CircleShape)
                            .pointerInput(element.id, boxW, boxH) {
                                detectDragGestures(
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        val dx = dragAmount.x / boxW
                                        val dy = dragAmount.y / boxH
                                        val newRight = (liveBounds.right + dx).coerceIn(liveBounds.left + 0.04f, 0.99f)
                                        val newBottom = (liveBounds.bottom + dy).coerceIn(liveBounds.top + 0.025f, 0.99f)
                                        liveBounds = liveBounds.copy(right = newRight, bottom = newBottom)
                                    },
                                    onDragEnd = {
                                        onUpdateElement(element.withUpdatedBounds(liveBounds), true)
                                    }
                                )
                            }
                    )
                }
            }
        }

        // 4. Drag preview rectangle for Redaction / Link creation
        val sPt = dragStartNorm
        val ePt = dragCurrentNorm
        if (sPt != null && ePt != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val left = min(sPt.x, ePt.x) * size.width
                val top = min(sPt.y, ePt.y) * size.height
                val w = (max(sPt.x, ePt.x) - min(sPt.x, ePt.x)) * size.width
                val h = (max(sPt.y, ePt.y) - min(sPt.y, ePt.y)) * size.height
                val previewColor = if (activeTool == ContentEditTool.REDACT) Color.Black else Color(0xFF1E88E5)
                drawRect(
                    color = previewColor.copy(alpha = 0.35f),
                    topLeft = Offset(left, top),
                    size = Size(w, h)
                )
                drawRect(
                    color = previewColor,
                    topLeft = Offset(left, top),
                    size = Size(w, h),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }
    }
}

@Composable
private fun ImageObjectPreview(element: ContentEditElement.ImageObject) {
    var bmp by remember(element.imageFile.absolutePath, element.cropRect) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(element.imageFile.absolutePath, element.cropRect) {
        withContext(Dispatchers.IO) {
            val raw = ScanImageProcessor.decodeSafeFile(element.imageFile, maxDim = 800)
            if (raw != null) {
                val cLeft = (element.cropRect.left * raw.width).toInt().coerceIn(0, raw.width - 1)
                val cTop = (element.cropRect.top * raw.height).toInt().coerceIn(0, raw.height - 1)
                val cRight = (element.cropRect.right * raw.width).toInt().coerceIn(cLeft + 1, raw.width)
                val cBottom = (element.cropRect.bottom * raw.height).toInt().coerceIn(cTop + 1, raw.height)
                val cropped = if (cLeft > 0 || cTop > 0 || cRight < raw.width || cBottom < raw.height) {
                    Bitmap.createBitmap(raw, cLeft, cTop, cRight - cLeft, cBottom - cTop)
                } else {
                    raw
                }
                withContext(Dispatchers.Main) {
                    bmp = cropped
                }
            }
        }
    }

    val currentBmp = bmp
    if (currentBmp != null) {
        Image(
            bitmap = currentBmp.asImageBitmap(),
            contentDescription = "Placed Image",
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .fillMaxSize()
                .rotate(element.rotationDegrees)
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.PhotoLibrary, contentDescription = null)
        }
    }
}

/**
 * Selection Toolbar for selected ContentEditElement:
 * Copy, Paste, Duplicate, Bring Forward, Send Backward, Edit Properties, Delete.
 */
@Composable
fun ContentEditSelectionToolbar(
    selectedElement: ContentEditElement?,
    hasClipboardItem: Boolean,
    onEditSelected: () -> Unit,
    onCopy: () -> Unit,
    onPaste: () -> Unit,
    onDuplicate: () -> Unit,
    onBringForward: () -> Unit,
    onSendBackward: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (selectedElement == null && !hasClipboardItem) return

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        tonalElevation = 6.dp,
        shadowElevation = 4.dp,
        modifier = modifier
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .testTag("content_edit_selection_toolbar")
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (selectedElement != null) {
                IconButton(
                    onClick = onEditSelected,
                    modifier = Modifier.testTag("content_selection_edit_btn")
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Properties")
                }
                IconButton(
                    onClick = onCopy,
                    modifier = Modifier.testTag("content_selection_copy_btn")
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                }
                IconButton(
                    onClick = onDuplicate,
                    modifier = Modifier.testTag("content_selection_duplicate_btn")
                ) {
                    Icon(Icons.Default.CopyAll, contentDescription = "Duplicate")
                }
                IconButton(
                    onClick = onBringForward,
                    modifier = Modifier.testTag("content_selection_bring_forward_btn")
                ) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = "Bring Forward")
                }
                IconButton(
                    onClick = onSendBackward,
                    modifier = Modifier.testTag("content_selection_send_backward_btn")
                ) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = "Send Backward")
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("content_selection_delete_btn")
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            if (hasClipboardItem) {
                FilledTonalButton(
                    onClick = onPaste,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("content_selection_paste_btn")
                ) {
                    Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Paste")
                }
            }
        }
    }
}

/**
 * Main Bottom Toolbar for Content Editing Mode.
 */
@Composable
fun ContentEditBottomToolbar(
    activeTool: ContentEditTool,
    canUndo: Boolean,
    canRedo: Boolean,
    editsCount: Int,
    onSelectTool: (ContentEditTool) -> Unit,
    onAddImageFromGallery: () -> Unit,
    onAddImageFromCamera: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSaveClick: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
            .fillMaxWidth()
            .testTag("content_edit_bottom_toolbar")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Top Row: Mode label + Undo / Redo + Save + Close
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close Content Editor")
                    }
                    Text(
                        text = "Content Editor",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onUndo,
                        enabled = canUndo,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("content_edit_undo_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                    }
                    IconButton(
                        onClick = onRedo,
                        enabled = canRedo,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("content_edit_redo_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                    }
                    Button(
                        onClick = onSaveClick,
                        enabled = editsCount > 0,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("content_edit_save_btn")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Apply ($editsCount)")
                    }
                }
            }

            // Tool Contextual Hint
            val hintText = when (activeTool) {
                ContentEditTool.SELECT -> "Tap any added text, image, link, or redaction box to move, resize, reorder, or copy."
                ContentEditTool.ADD_TEXT -> "Tap anywhere on the page to place a new text box (Noto Sans, Bengali বাংলা, Standard fonts)."
                ContentEditTool.EDIT_TEXT -> "Tap any highlighted text line on the page to cover and replace it."
                ContentEditTool.ADD_IMAGE -> "Pick an image from Gallery or Camera to place, move, resize, rotate, or crop."
                ContentEditTool.ADD_LINK -> "Tap or drag a rectangle on the page to create a Web URL, Email, or Page Jump link."
                ContentEditTool.REDACT -> "Tap a text line or drag a rectangle to mark for permanent black-box redaction."
            }
            Text(
                text = hintText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Tool Chips Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = activeTool == ContentEditTool.SELECT,
                    onClick = { onSelectTool(ContentEditTool.SELECT) },
                    label = { Text("Select") },
                    leadingIcon = { Icon(Icons.Default.TouchApp, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("content_tool_select")
                )
                FilterChip(
                    selected = activeTool == ContentEditTool.ADD_TEXT,
                    onClick = { onSelectTool(ContentEditTool.ADD_TEXT) },
                    label = { Text("Add Text") },
                    leadingIcon = { Icon(Icons.Default.TextFields, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("content_tool_add_text")
                )
                FilterChip(
                    selected = activeTool == ContentEditTool.EDIT_TEXT,
                    onClick = { onSelectTool(ContentEditTool.EDIT_TEXT) },
                    label = { Text("Edit Text") },
                    leadingIcon = { Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("content_tool_edit_text")
                )
                FilterChip(
                    selected = activeTool == ContentEditTool.ADD_IMAGE,
                    onClick = {
                        onSelectTool(ContentEditTool.ADD_IMAGE)
                        onAddImageFromGallery()
                    },
                    label = { Text("Add Image") },
                    leadingIcon = { Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("content_tool_add_image")
                )
                OutlinedButton(
                    onClick = {
                        onSelectTool(ContentEditTool.ADD_IMAGE)
                        onAddImageFromCamera()
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("content_tool_camera_image")
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Camera Image")
                }
                FilterChip(
                    selected = activeTool == ContentEditTool.ADD_LINK,
                    onClick = { onSelectTool(ContentEditTool.ADD_LINK) },
                    label = { Text("Link") },
                    leadingIcon = { Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("content_tool_link")
                )
                FilterChip(
                    selected = activeTool == ContentEditTool.REDACT,
                    onClick = { onSelectTool(ContentEditTool.REDACT) },
                    label = { Text("Redact") },
                    leadingIcon = { Icon(Icons.Default.GppBad, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("content_tool_redact")
                )
            }
        }
    }
}

/**
 * 1. Dialog for Adding or Editing a Text Box (supports Noto Sans, Noto Sans Bengali, Helvetica, Times, Courier).
 */
@Composable
fun AddOrEditTextBoxDialog(
    initialElement: ContentEditElement.AddedText?,
    onConfirm: (
        text: String,
        font: BundledEditFont,
        fontSizeSp: Float,
        colorArgb: Int,
        alignment: TextAlignmentMode,
        isBold: Boolean,
        isItalic: Boolean
    ) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialElement?.text ?: "") }
    var selectedFont by remember { mutableStateOf(initialElement?.font ?: BundledEditFont.NOTO_SANS) }
    var fontSizeSp by remember { mutableFloatStateOf(initialElement?.fontSizeSp ?: 16f) }
    var colorArgb by remember { mutableIntStateOf(initialElement?.colorArgb ?: 0xFF111111.toInt()) }
    var alignment by remember { mutableStateOf(initialElement?.alignment ?: TextAlignmentMode.LEFT) }
    var isBold by remember { mutableStateOf(initialElement?.isBold ?: false) }
    var isItalic by remember { mutableStateOf(initialElement?.isItalic ?: false) }

    // Auto-select Noto Sans Bengali if the user types Bengali script
    LaunchedEffect(text) {
        if (BundledEditFont.containsBengaliScript(text) && selectedFont != BundledEditFont.NOTO_SANS_BENGALI) {
            selectedFont = BundledEditFont.NOTO_SANS_BENGALI
        }
    }

    val colorChoices = listOf(
        0xFF111111.toInt(),
        0xFF1565C0.toInt(),
        0xFFC62828.toInt(),
        0xFF2E7D32.toInt(),
        0xFF6A1B9A.toInt(),
        0xFFEF6C00.toInt()
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialElement == null) "Add Text Box" else "Edit Text Box",
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
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Text (English, বাংলা, Unicode)") },
                    placeholder = { Text("Enter text or সোনার বাংলা...") },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_text_input_field")
                )

                Text("Font Family (Embedded Subset)", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(BundledEditFont.entries) { fontOption ->
                        FilterChip(
                            selected = selectedFont == fontOption,
                            onClick = { selectedFont = fontOption },
                            label = { Text(fontOption.displayName) }
                        )
                    }
                }

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Font Size", style = MaterialTheme.typography.labelLarge)
                        Text("${fontSizeSp.toInt()} pt", style = MaterialTheme.typography.labelMedium)
                    }
                    Slider(
                        value = fontSizeSp,
                        onValueChange = { fontSizeSp = it },
                        valueRange = 8f..48f
                    )
                }

                // Style & Alignment Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = isBold,
                            onClick = { isBold = !isBold },
                            label = { Icon(Icons.Default.FormatBold, contentDescription = "Bold", modifier = Modifier.size(18.dp)) }
                        )
                        FilterChip(
                            selected = isItalic,
                            onClick = { isItalic = !isItalic },
                            label = { Icon(Icons.Default.FormatItalic, contentDescription = "Italic", modifier = Modifier.size(18.dp)) }
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = { alignment = TextAlignmentMode.LEFT }) {
                            Icon(
                                Icons.Default.FormatAlignLeft,
                                contentDescription = "Align Left",
                                tint = if (alignment == TextAlignmentMode.LEFT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { alignment = TextAlignmentMode.CENTER }) {
                            Icon(
                                Icons.Default.FormatAlignCenter,
                                contentDescription = "Align Center",
                                tint = if (alignment == TextAlignmentMode.CENTER) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { alignment = TextAlignmentMode.RIGHT }) {
                            Icon(
                                Icons.Default.FormatAlignRight,
                                contentDescription = "Align Right",
                                tint = if (alignment == TextAlignmentMode.RIGHT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Color Picker
                Text("Text Color", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    colorChoices.forEach { c ->
                        val selected = colorArgb == c
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .border(
                                    width = if (selected) 3.dp else 1.dp,
                                    color = if (selected) MaterialTheme.colorScheme.primary else Color.Gray,
                                    shape = CircleShape
                                )
                                .clickable { colorArgb = c }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (text.isNotBlank()) {
                        onConfirm(text, selectedFont, fontSizeSp, colorArgb, alignment, isBold, isItalic)
                    }
                },
                enabled = text.isNotBlank(),
                modifier = Modifier.testTag("add_text_confirm_btn")
            ) {
                Text("Apply Text")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * 2. Dialog for Editing an Existing Text Line (covers old text with background rectangle + draws replacement).
 * Shows a clear warning that the original text may still exist inside the file unless Remove Hidden Text / Sanitize is run.
 */
@Composable
fun EditExistingTextLineDialog(
    originalText: String,
    initialReplacement: String,
    matchedFont: BundledEditFont,
    initialFontSizeSp: Float,
    initialIsBold: Boolean,
    initialIsItalic: Boolean,
    onAskAiExplain: ((String) -> Unit)? = null,
    onAskAiTranslate: ((String) -> Unit)? = null,
    onConfirm: (
        replacementText: String,
        font: BundledEditFont,
        fontSizeSp: Float,
        textColorArgb: Int,
        backgroundColorArgb: Int,
        isBold: Boolean,
        isItalic: Boolean
    ) -> Unit,
    onDismiss: () -> Unit
) {
    var replacementText by remember { mutableStateOf(initialReplacement) }
    var selectedFont by remember { mutableStateOf(matchedFont) }
    var fontSizeSp by remember { mutableFloatStateOf(initialFontSizeSp.coerceIn(8f, 42f)) }
    var textColorArgb by remember { mutableIntStateOf(0xFF000000.toInt()) }
    var backgroundColorArgb by remember { mutableIntStateOf(0xFFFFFFFF.toInt()) }
    var isBold by remember { mutableStateOf(initialIsBold) }
    var isItalic by remember { mutableStateOf(initialIsItalic) }

    LaunchedEffect(replacementText) {
        if (BundledEditFont.containsBengaliScript(replacementText)) {
            selectedFont = BundledEditFont.NOTO_SANS_BENGALI
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Edit or Ask AI About Selected Line", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (onAskAiExplain != null || onAskAiTranslate != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (onAskAiExplain != null) {
                            OutlinedButton(
                                onClick = { onAskAiExplain(originalText) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_line_ai_explain")
                            ) {
                                Text("AI Explain / Rewrite", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        if (onAskAiTranslate != null) {
                            OutlinedButton(
                                onClick = { onAskAiTranslate(originalText) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("btn_line_ai_translate")
                            ) {
                                Text("AI Translate", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                // Mandatory warning notice
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Note: This covers the existing text line with a background rectangle and draws your replacement text. The original text may still exist inside the file unless you run 'Remove Hidden Text / Sanitize' or use Redaction.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                Text(
                    text = "Original line: \"$originalText\"",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = replacementText,
                    onValueChange = { replacementText = it },
                    label = { Text("Replacement Text") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_existing_text_input")
                )

                Text("Closest Matching Font", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(BundledEditFont.entries) { f ->
                        FilterChip(
                            selected = selectedFont == f,
                            onClick = { selectedFont = f },
                            label = { Text(f.displayName) }
                        )
                    }
                }

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Font Size", style = MaterialTheme.typography.labelLarge)
                        Text("${fontSizeSp.toInt()} pt", style = MaterialTheme.typography.labelMedium)
                    }
                    Slider(
                        value = fontSizeSp,
                        onValueChange = { fontSizeSp = it },
                        valueRange = 8f..42f
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = isBold,
                        onClick = { isBold = !isBold },
                        label = { Text("Bold") }
                    )
                    FilterChip(
                        selected = isItalic,
                        onClick = { isItalic = !isItalic },
                        label = { Text("Italic") }
                    )
                }

                Text("Cover Background Color", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(
                        0xFFFFFFFF.toInt() to "White",
                        0xFFF8F9FA.toInt() to "Warm Paper",
                        0xFFFFFDE7.toInt() to "Cream"
                    ).forEach { (bgColor, label) ->
                        FilterChip(
                            selected = backgroundColorArgb == bgColor,
                            onClick = { backgroundColorArgb = bgColor },
                            label = { Text(label) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        replacementText,
                        selectedFont,
                        fontSizeSp,
                        textColorArgb,
                        backgroundColorArgb,
                        isBold,
                        isItalic
                    )
                },
                modifier = Modifier.testTag("edit_existing_text_confirm_btn")
            ) {
                Text("Replace Line")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * 3. Dialog for Image Transform (Rotate, Crop, Replace from Gallery / Camera).
 */
@Composable
fun EditImageObjectDialog(
    element: ContentEditElement.ImageObject,
    onReplaceFromGallery: () -> Unit,
    onReplaceFromCamera: () -> Unit,
    onConfirm: (rotationDegrees: Float, cropRect: NormalizedRect) -> Unit,
    onDismiss: () -> Unit
) {
    var rotationDegrees by remember { mutableFloatStateOf(element.rotationDegrees) }
    var cropLeft by remember { mutableFloatStateOf(element.cropRect.left) }
    var cropTop by remember { mutableFloatStateOf(element.cropRect.top) }
    var cropRight by remember { mutableFloatStateOf(element.cropRect.right) }
    var cropBottom by remember { mutableFloatStateOf(element.cropRect.bottom) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Transform, Crop & Replace Image", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = { rotationDegrees = (rotationDegrees + 90f) % 360f },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.RotateRight, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Rotate 90° (${rotationDegrees.toInt()}°)")
                    }
                }

                Text("Crop Image Margins", style = MaterialTheme.typography.labelLarge)
                Text("Left / Right Crop", style = MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Slider(
                        value = cropLeft,
                        onValueChange = { cropLeft = it.coerceAtMost(cropRight - 0.1f) },
                        valueRange = 0f..0.8f,
                        modifier = Modifier.weight(1f)
                    )
                    Slider(
                        value = cropRight,
                        onValueChange = { cropRight = it.coerceAtLeast(cropLeft + 0.1f) },
                        valueRange = 0.2f..1f,
                        modifier = Modifier.weight(1f)
                    )
                }

                Text("Top / Bottom Crop", style = MaterialTheme.typography.labelSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Slider(
                        value = cropTop,
                        onValueChange = { cropTop = it.coerceAtMost(cropBottom - 0.1f) },
                        valueRange = 0f..0.8f,
                        modifier = Modifier.weight(1f)
                    )
                    Slider(
                        value = cropBottom,
                        onValueChange = { cropBottom = it.coerceAtLeast(cropTop + 0.1f) },
                        valueRange = 0.2f..1f,
                        modifier = Modifier.weight(1f)
                    )
                }

                Text("Replace Image Source", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onReplaceFromGallery,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Gallery")
                    }
                    OutlinedButton(
                        onClick = onReplaceFromCamera,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Camera")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        rotationDegrees,
                        NormalizedRect(cropLeft, cropTop, cropRight, cropBottom)
                    )
                }
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * 4. Dialog for Adding or Editing a Link (Web URL, Email, Jump to Page).
 */
@Composable
fun AddOrEditLinkDialog(
    initialLink: ContentEditElement.LinkArea?,
    totalPages: Int,
    onConfirm: (linkType: PdfLinkType, targetUrlOrEmail: String, targetPageIndex: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var linkType by remember { mutableStateOf(initialLink?.linkType ?: PdfLinkType.WEB_URL) }
    var urlOrEmail by remember { mutableStateOf(initialLink?.targetUrlOrEmail ?: "https://") }
    var pageInput by remember { mutableStateOf(((initialLink?.targetPageIndex ?: 0) + 1).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialLink == null) "Add Interactive Link" else "Edit Link",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PdfLinkType.entries.forEach { t ->
                        FilterChip(
                            selected = linkType == t,
                            onClick = {
                                linkType = t
                                if (t == PdfLinkType.WEB_URL && !urlOrEmail.startsWith("http")) {
                                    urlOrEmail = "https://"
                                } else if (t == PdfLinkType.EMAIL && urlOrEmail.startsWith("http")) {
                                    urlOrEmail = ""
                                }
                            },
                            label = { Text(t.label) }
                        )
                    }
                }

                when (linkType) {
                    PdfLinkType.WEB_URL -> {
                        OutlinedTextField(
                            value = urlOrEmail,
                            onValueChange = { urlOrEmail = it },
                            label = { Text("Web URL (https://...)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    PdfLinkType.EMAIL -> {
                        OutlinedTextField(
                            value = urlOrEmail,
                            onValueChange = { urlOrEmail = it },
                            label = { Text("Email Address") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    PdfLinkType.PAGE_JUMP -> {
                        OutlinedTextField(
                            value = pageInput,
                            onValueChange = { pageInput = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Target Page (1 to ${totalPages.coerceAtLeast(1)})") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedPage = ((pageInput.toIntOrNull() ?: 1) - 1)
                        .coerceIn(0, (totalPages - 1).coerceAtLeast(0))
                    onConfirm(linkType, urlOrEmail, parsedPage)
                },
                modifier = Modifier.testTag("add_link_confirm_btn")
            ) {
                Text("Save Link")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * 5. Dialog for Saving Content Edits & Applying Redactions.
 */
@Composable
fun SaveContentEditsDialog(
    fileName: String,
    edits: List<ContentEditElement>,
    isSaving: Boolean,
    progressMessage: String?,
    onSaveAsCopy: () -> Unit,
    onSaveOverwrite: () -> Unit,
    onDismiss: () -> Unit
) {
    val redactionCount = edits.count { it is ContentEditElement.RedactionMark }
    val replacedTextCount = edits.count { it is ContentEditElement.ReplacedText }
    var confirmOverwrite by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = {
            Text(
                text = "Apply Content Edits & Redactions",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Document: $fileName (${edits.size} item(s))",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )

                if (redactionCount > 0) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.GppBad,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Permanent Redaction ($redactionCount mark(s)): Pages with redactions will have their underlying text streams and resources permanently stripped and rasterized with black redaction boxes burned in so the original content cannot be recovered.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                if (replacedTextCount > 0) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.WarningAmber,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Text Replacement ($replacedTextCount line(s)): Replacement text covers the original line visually. The original text may still exist inside the file unless you run 'Metadata & Sanitize → Remove Hidden Text' or use Redaction.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                if (progressMessage != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = progressMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                if (!isSaving) {
                    Button(
                        onClick = onSaveAsCopy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("save_content_edits_copy_btn")
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save as New Copy (Recommended)")
                    }

                    if (!confirmOverwrite) {
                        OutlinedButton(
                            onClick = { confirmOverwrite = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("save_content_edits_overwrite_ask_btn")
                        ) {
                            Text("Overwrite Original PDF...")
                        }
                    } else {
                        Button(
                            onClick = {
                                confirmOverwrite = false
                                onSaveOverwrite()
                            },
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("save_content_edits_overwrite_confirm_btn")
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Confirm Overwrite Original")
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            if (!isSaving) {
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}

