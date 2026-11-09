package com.example.ui.viewer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pdf.AnnotationType
import com.example.pdf.NormalizedPoint
import com.example.pdf.NormalizedRect
import com.example.pdf.PdfAnnotation
import com.example.ui.PdfViewModel
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AnnotationCanvasOverlay(
    pageIndex: Int,
    annotations: List<PdfAnnotation>,
    activeTool: AnnotationType?,
    activeColorArgb: Int,
    activeThickness: Float,
    selectedAnnotationId: String?,
    viewModel: PdfViewModel,
    modifier: Modifier = Modifier
) {
    // Current live drawing stroke / shape preview
    val currentPoints = remember { mutableStateListOf<NormalizedPoint>() }
    var dragStartPoint by remember { mutableStateOf<NormalizedPoint?>(null) }
    var dragCurrentPoint by remember { mutableStateOf<NormalizedPoint?>(null) }

    // Dialog state for Sticky Note
    var pendingStickyNotePos by remember { mutableStateOf<NormalizedPoint?>(null) }
    var activeStickyNoteToEdit by remember { mutableStateOf<PdfAnnotation.StickyNote?>(null) }

    // Dialog state for Free Text Box
    var pendingTextBoxBounds by remember { mutableStateOf<NormalizedRect?>(null) }

    // Stylus palm rejection state
    var stylusActive by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(activeTool, activeColorArgb, activeThickness) {
                    if (activeTool == null) {
                        // Pan / Selection mode: detect tap to select or view sticky notes
                        detectTapGestures(
                            onTap = { offset ->
                                val normX = offset.x / size.width
                                val normY = offset.y / size.height

                                // Check if user tapped on a sticky note
                                val tappedNote = annotations.filterIsInstance<PdfAnnotation.StickyNote>()
                                    .firstOrNull { note ->
                                        val dx = kotlin.math.abs(note.position.x - normX) * size.width
                                        val dy = kotlin.math.abs(note.position.y - normY) * size.height
                                        dx < 28f && dy < 28f
                                    }

                                if (tappedNote != null) {
                                    activeStickyNoteToEdit = tappedNote
                                    viewModel.selectAnnotation(tappedNote.id)
                                } else {
                                    // Check any other annotation
                                    val tappedAnnot = annotations.firstOrNull { annot ->
                                        when (annot) {
                                            is PdfAnnotation.TextMarkup -> annot.rects.any { it.contains(normX, normY) }
                                            is PdfAnnotation.FreeTextBox -> annot.bounds.contains(normX, normY)
                                            is PdfAnnotation.GeometricShape -> {
                                                val minX = minOf(annot.startPoint.x, annot.endPoint.x)
                                                val maxX = maxOf(annot.startPoint.x, annot.endPoint.x)
                                                val minY = minOf(annot.startPoint.y, annot.endPoint.y)
                                                val maxY = maxOf(annot.startPoint.y, annot.endPoint.y)
                                                normX in (minX - 0.05f)..(maxX + 0.05f) && normY in (minY - 0.05f)..(maxY + 0.05f)
                                            }
                                            else -> false
                                        }
                                    }
                                    viewModel.selectAnnotation(tappedAnnot?.id)
                                }
                            }
                        )
                    } else if (activeTool == AnnotationType.STICKY_NOTE) {
                        detectTapGestures(
                            onTap = { offset ->
                                pendingStickyNotePos = NormalizedPoint(
                                    x = (offset.x / size.width).coerceIn(0f, 1f),
                                    y = (offset.y / size.height).coerceIn(0f, 1f)
                                )
                            }
                        )
                    } else if (activeTool == AnnotationType.TEXT_BOX) {
                        detectTapGestures(
                            onTap = { offset ->
                                val left = (offset.x / size.width).coerceIn(0f, 0.7f)
                                val top = (offset.y / size.height).coerceIn(0f, 0.9f)
                                pendingTextBoxBounds = NormalizedRect(
                                    left = left,
                                    top = top,
                                    right = (left + 0.28f).coerceAtMost(1f),
                                    bottom = (top + 0.08f).coerceAtMost(1f)
                                )
                            }
                        )
                    } else {
                        // Drawing tools (Pen, Highlighter Pen, Highlight, Underline, Strikeout, Shapes)
                        detectDragGestures(
                            onDragStart = { offset ->
                                val norm = NormalizedPoint(
                                    x = (offset.x / size.width).coerceIn(0f, 1f),
                                    y = (offset.y / size.height).coerceIn(0f, 1f)
                                )
                                dragStartPoint = norm
                                dragCurrentPoint = norm
                                currentPoints.clear()
                                currentPoints.add(norm)
                            },
                            onDrag = { change, _ ->
                                // Palm rejection check: if a stylus is being used, ignore palm touches!
                                if (change.type == PointerType.Stylus) {
                                    stylusActive = true
                                } else if (change.type == PointerType.Touch && stylusActive) {
                                    // Ignore palm touch!
                                    return@detectDragGestures
                                }

                                if (change.type == PointerType.Eraser) {
                                    // Stylus eraser tip acts as eraser directly
                                    val normX = change.position.x / size.width
                                    val normY = change.position.y / size.height
                                    viewModel.eraseAnnotationAt(pageIndex, normX, normY)
                                    return@detectDragGestures
                                }

                                change.consume()
                                val norm = NormalizedPoint(
                                    x = (change.position.x / size.width).coerceIn(0f, 1f),
                                    y = (change.position.y / size.height).coerceIn(0f, 1f)
                                )
                                dragCurrentPoint = norm
                                currentPoints.add(norm)
                            },
                            onDragEnd = {
                                val start = dragStartPoint
                                val end = dragCurrentPoint
                                if (start != null && end != null) {
                                    when (activeTool) {
                                        AnnotationType.PEN, AnnotationType.HIGHLIGHTER_PEN -> {
                                            if (currentPoints.size >= 2) {
                                                viewModel.addAnnotation(
                                                    PdfAnnotation.InkStroke(
                                                        pageIndex = pageIndex,
                                                        type = activeTool,
                                                        colorArgb = activeColorArgb,
                                                        strokeWidth = activeThickness,
                                                        points = currentPoints.toList()
                                                    )
                                                )
                                            }
                                        }
                                        AnnotationType.HIGHLIGHT, AnnotationType.UNDERLINE, AnnotationType.STRIKEOUT -> {
                                            val l = minOf(start.x, end.x)
                                            val r = maxOf(start.x, end.x)
                                            val t = minOf(start.y, end.y)
                                            val b = maxOf(start.y, end.y)
                                            if (kotlin.math.abs(r - l) > 0.01f || kotlin.math.abs(b - t) > 0.005f) {
                                                viewModel.addAnnotation(
                                                    PdfAnnotation.TextMarkup(
                                                        pageIndex = pageIndex,
                                                        type = activeTool,
                                                        colorArgb = activeColorArgb,
                                                        strokeWidth = activeThickness,
                                                        rects = listOf(NormalizedRect(l, t, r, b))
                                                    )
                                                )
                                            }
                                        }
                                        AnnotationType.LINE, AnnotationType.ARROW, AnnotationType.RECTANGLE, AnnotationType.ELLIPSE -> {
                                            viewModel.addAnnotation(
                                                PdfAnnotation.GeometricShape(
                                                    pageIndex = pageIndex,
                                                    type = activeTool,
                                                    colorArgb = activeColorArgb,
                                                    strokeWidth = activeThickness,
                                                    startPoint = start,
                                                    endPoint = end
                                                )
                                            )
                                        }
                                        else -> {}
                                    }
                                }
                                currentPoints.clear()
                                dragStartPoint = null
                                dragCurrentPoint = null
                            },
                            onDragCancel = {
                                currentPoints.clear()
                                dragStartPoint = null
                                dragCurrentPoint = null
                            }
                        )
                    }
                }
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            // 1. Draw existing & committed annotations
            for (annot in annotations) {
                val isSelected = annot.id == selectedAnnotationId
                drawSingleAnnotation(annot, canvasWidth, canvasHeight, isSelected)
            }

            // 2. Draw active live preview while dragging
            if (activeTool != null) {
                val start = dragStartPoint
                val end = dragCurrentPoint
                if (start != null && end != null) {
                    when (activeTool) {
                        AnnotationType.PEN, AnnotationType.HIGHLIGHTER_PEN -> {
                            if (currentPoints.size >= 2) {
                                val path = Path()
                                path.moveTo(currentPoints[0].x * canvasWidth, currentPoints[0].y * canvasHeight)
                                for (i in 1 until currentPoints.size) {
                                    path.lineTo(currentPoints[i].x * canvasWidth, currentPoints[i].y * canvasHeight)
                                }
                                drawPath(
                                    path = path,
                                    color = Color(activeColorArgb).copy(
                                        alpha = if (activeTool == AnnotationType.HIGHLIGHTER_PEN) 0.45f else 1.0f
                                    ),
                                    style = Stroke(
                                        width = activeThickness,
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }
                        }
                        AnnotationType.HIGHLIGHT -> {
                            val l = minOf(start.x, end.x) * canvasWidth
                            val t = minOf(start.y, end.y) * canvasHeight
                            val w = kotlin.math.abs(end.x - start.x) * canvasWidth
                            val h = kotlin.math.abs(end.y - start.y) * canvasHeight
                            drawRect(
                                color = Color(activeColorArgb).copy(alpha = 0.4f),
                                topLeft = Offset(l, t),
                                size = Size(w, h)
                            )
                        }
                        AnnotationType.UNDERLINE -> {
                            val l = minOf(start.x, end.x) * canvasWidth
                            val r = maxOf(start.x, end.x) * canvasWidth
                            val b = maxOf(start.y, end.y) * canvasHeight
                            drawLine(
                                color = Color(activeColorArgb),
                                start = Offset(l, b),
                                end = Offset(r, b),
                                strokeWidth = activeThickness
                            )
                        }
                        AnnotationType.STRIKEOUT -> {
                            val l = minOf(start.x, end.x) * canvasWidth
                            val r = maxOf(start.x, end.x) * canvasWidth
                            val midY = ((start.y + end.y) / 2f) * canvasHeight
                            drawLine(
                                color = Color(activeColorArgb),
                                start = Offset(l, midY),
                                end = Offset(r, midY),
                                strokeWidth = activeThickness
                            )
                        }
                        AnnotationType.RECTANGLE -> {
                            val l = minOf(start.x, end.x) * canvasWidth
                            val t = minOf(start.y, end.y) * canvasHeight
                            val w = kotlin.math.abs(end.x - start.x) * canvasWidth
                            val h = kotlin.math.abs(end.y - start.y) * canvasHeight
                            drawRect(
                                color = Color(activeColorArgb),
                                topLeft = Offset(l, t),
                                size = Size(w, h),
                                style = Stroke(width = activeThickness)
                            )
                        }
                        AnnotationType.ELLIPSE -> {
                            val l = minOf(start.x, end.x) * canvasWidth
                            val t = minOf(start.y, end.y) * canvasHeight
                            val w = kotlin.math.abs(end.x - start.x) * canvasWidth
                            val h = kotlin.math.abs(end.y - start.y) * canvasHeight
                            drawOval(
                                color = Color(activeColorArgb),
                                topLeft = Offset(l, t),
                                size = Size(w, h),
                                style = Stroke(width = activeThickness)
                            )
                        }
                        AnnotationType.LINE, AnnotationType.ARROW -> {
                            val p1 = Offset(start.x * canvasWidth, start.y * canvasHeight)
                            val p2 = Offset(end.x * canvasWidth, end.y * canvasHeight)
                            drawLine(
                                color = Color(activeColorArgb),
                                start = p1,
                                end = p2,
                                strokeWidth = activeThickness,
                                cap = StrokeCap.Round
                            )
                            if (activeTool == AnnotationType.ARROW) {
                                drawArrowHead(p1, p2, Color(activeColorArgb), activeThickness)
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    // Dialog for creating a new Sticky Note
    pendingStickyNotePos?.let { pos ->
        var noteText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pendingStickyNotePos = null },
            title = { Text("Add Sticky Note") },
            text = {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    placeholder = { Text("Type your note here...") },
                    modifier = Modifier.fillMaxWidth().testTag("input_sticky_note")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (noteText.isNotBlank()) {
                            viewModel.addAnnotation(
                                PdfAnnotation.StickyNote(
                                    pageIndex = pageIndex,
                                    position = pos,
                                    noteText = noteText,
                                    colorArgb = activeColorArgb
                                )
                            )
                        }
                        pendingStickyNotePos = null
                    },
                    modifier = Modifier.testTag("btn_save_sticky_note")
                ) {
                    Text("Add Note")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingStickyNotePos = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog for viewing / editing an existing Sticky Note
    activeStickyNoteToEdit?.let { note ->
        var noteText by remember(note.id) { mutableStateOf(note.noteText) }
        AlertDialog(
            onDismissRequest = { activeStickyNoteToEdit = null },
            title = { Text("Sticky Note (Page ${pageIndex + 1})") },
            text = {
                Column {
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        modifier = Modifier.fillMaxWidth().testTag("edit_sticky_note_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateAnnotation(note.copy(noteText = noteText))
                        activeStickyNoteToEdit = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            viewModel.deleteAnnotation(note.id)
                            activeStickyNoteToEdit = null
                        }
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { activeStickyNoteToEdit = null }) {
                        Text("Close")
                    }
                }
            }
        )
    }

    // Dialog for creating a Free Text Box
    pendingTextBoxBounds?.let { bounds ->
        var textBoxContent by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pendingTextBoxBounds = null },
            title = { Text("Add Free Text Box") },
            text = {
                OutlinedTextField(
                    value = textBoxContent,
                    onValueChange = { textBoxContent = it },
                    placeholder = { Text("Type text to display on page...") },
                    modifier = Modifier.fillMaxWidth().testTag("input_free_text")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (textBoxContent.isNotBlank()) {
                            viewModel.addAnnotation(
                                PdfAnnotation.FreeTextBox(
                                    pageIndex = pageIndex,
                                    bounds = bounds,
                                    text = textBoxContent,
                                    colorArgb = activeColorArgb
                                )
                            )
                        }
                        pendingTextBoxBounds = null
                    },
                    modifier = Modifier.testTag("btn_save_free_text")
                ) {
                    Text("Place Text")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingTextBoxBounds = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun DrawScope.drawSingleAnnotation(
    annot: PdfAnnotation,
    w: Float,
    h: Float,
    isSelected: Boolean
) {
    when (annot) {
        is PdfAnnotation.TextMarkup -> {
            for (rect in annot.rects) {
                val l = rect.left * w
                val t = rect.top * h
                val rw = rect.width * w
                val rh = rect.height * h

                when (annot.type) {
                    AnnotationType.HIGHLIGHT -> {
                        drawRect(
                            color = Color(annot.colorArgb).copy(alpha = 0.45f),
                            topLeft = Offset(l, t),
                            size = Size(rw, rh)
                        )
                    }
                    AnnotationType.UNDERLINE -> {
                        drawLine(
                            color = Color(annot.colorArgb),
                            start = Offset(l, t + rh),
                            end = Offset(l + rw, t + rh),
                            strokeWidth = annot.strokeWidth.coerceAtLeast(2f)
                        )
                    }
                    AnnotationType.STRIKEOUT -> {
                        drawLine(
                            color = Color(annot.colorArgb),
                            start = Offset(l, t + (rh / 2f)),
                            end = Offset(l + rw, t + (rh / 2f)),
                            strokeWidth = annot.strokeWidth.coerceAtLeast(2f)
                        )
                    }
                    else -> {}
                }

                if (isSelected) {
                    drawRect(
                        color = Color(0xFF1E88E5),
                        topLeft = Offset(l - 2, t - 2),
                        size = Size(rw + 4, rh + 4),
                        style = Stroke(width = 1.5f)
                    )
                }
            }
        }
        is PdfAnnotation.InkStroke -> {
            if (annot.points.size >= 2) {
                val path = Path()
                path.moveTo(annot.points[0].x * w, annot.points[0].y * h)
                for (i in 1 until annot.points.size) {
                    path.lineTo(annot.points[i].x * w, annot.points[i].y * h)
                }
                drawPath(
                    path = path,
                    color = Color(annot.colorArgb).copy(alpha = annot.alpha),
                    style = Stroke(
                        width = annot.strokeWidth,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }
        is PdfAnnotation.GeometricShape -> {
            val p1 = Offset(annot.startPoint.x * w, annot.startPoint.y * h)
            val p2 = Offset(annot.endPoint.x * w, annot.endPoint.y * h)
            val l = minOf(p1.x, p2.x)
            val t = minOf(p1.y, p2.y)
            val sw = kotlin.math.abs(p2.x - p1.x)
            val sh = kotlin.math.abs(p2.y - p1.y)

            when (annot.type) {
                AnnotationType.RECTANGLE -> {
                    drawRect(
                        color = Color(annot.colorArgb),
                        topLeft = Offset(l, t),
                        size = Size(sw, sh),
                        style = Stroke(width = annot.strokeWidth)
                    )
                }
                AnnotationType.ELLIPSE -> {
                    drawOval(
                        color = Color(annot.colorArgb),
                        topLeft = Offset(l, t),
                        size = Size(sw, sh),
                        style = Stroke(width = annot.strokeWidth)
                    )
                }
                AnnotationType.LINE -> {
                    drawLine(
                        color = Color(annot.colorArgb),
                        start = p1,
                        end = p2,
                        strokeWidth = annot.strokeWidth,
                        cap = StrokeCap.Round
                    )
                }
                AnnotationType.ARROW -> {
                    drawLine(
                        color = Color(annot.colorArgb),
                        start = p1,
                        end = p2,
                        strokeWidth = annot.strokeWidth,
                        cap = StrokeCap.Round
                    )
                    drawArrowHead(p1, p2, Color(annot.colorArgb), annot.strokeWidth)
                }
                else -> {}
            }

            if (isSelected) {
                drawRect(
                    color = Color(0xFF1E88E5),
                    topLeft = Offset(l - 4, t - 4),
                    size = Size(sw + 8, sh + 8),
                    style = Stroke(width = 1.5f)
                )
            }
        }
        is PdfAnnotation.StickyNote -> {
            val cx = annot.position.x * w
            val cy = annot.position.y * h
            // Draw cute sticky note pin icon
            drawCircle(
                color = Color(annot.colorArgb),
                radius = 12f,
                center = Offset(cx, cy)
            )
            drawCircle(
                color = Color(0xFF5D4037),
                radius = 12f,
                center = Offset(cx, cy),
                style = Stroke(width = 1.5f)
            )
            if (isSelected) {
                drawCircle(
                    color = Color(0xFF1E88E5),
                    radius = 16f,
                    center = Offset(cx, cy),
                    style = Stroke(width = 2f)
                )
            }
        }
        is PdfAnnotation.FreeTextBox -> {
            val l = annot.bounds.left * w
            val t = annot.bounds.top * h
            val bw = annot.bounds.width * w
            val bh = annot.bounds.height * h

            drawRoundRect(
                color = Color(annot.backgroundColorArgb),
                topLeft = Offset(l, t),
                size = Size(bw, bh),
                cornerRadius = CornerRadius(4f)
            )
            drawRoundRect(
                color = if (isSelected) Color(0xFF1E88E5) else Color(annot.colorArgb),
                topLeft = Offset(l, t),
                size = Size(bw, bh),
                cornerRadius = CornerRadius(4f),
                style = Stroke(width = if (isSelected) 2f else 1f)
            )

            // Draw text via native canvas
            val paint = android.graphics.Paint().apply {
                color = annot.colorArgb
                textSize = annot.fontSize * (w / 400f).coerceIn(0.8f, 2f)
                isAntiAlias = true
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            drawContext.canvas.nativeCanvas.drawText(
                annot.text,
                l + 6f,
                t + bh - 6f,
                paint
            )
        }
    }
}

private fun DrawScope.drawArrowHead(p1: Offset, p2: Offset, color: Color, strokeWidth: Float) {
    val angle = atan2((p2.y - p1.y).toDouble(), (p2.x - p1.x).toDouble())
    val arrowLength = (strokeWidth * 5f).coerceIn(16f, 32f)
    val arrowAngle = Math.PI / 6.0

    val x1 = p2.x - arrowLength * cos(angle - arrowAngle).toFloat()
    val y1 = p2.y - arrowLength * sin(angle - arrowAngle).toFloat()
    val x2 = p2.x - arrowLength * cos(angle + arrowAngle).toFloat()
    val y2 = p2.y - arrowLength * sin(angle + arrowAngle).toFloat()

    val path = Path().apply {
        moveTo(p2.x, p2.y)
        lineTo(x1, y1)
        moveTo(p2.x, p2.y)
        lineTo(x2, y2)
    }
    drawPath(path = path, color = color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
}
