package com.example.ui.viewer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pdf.SignatureManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class StrokePoint(val x: Float, val y: Float)
private data class DrawnStroke(val points: List<StrokePoint>, val color: Color, val strokeWidth: Float)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignatureCreationSheet(
    signatureManager: SignatureManager,
    isNightMode: Boolean,
    onSignatureCreated: (Bitmap, Boolean, Int) -> Unit, // bitmap, isInitials, slotIndex
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Draw, 1: Type, 2: Import
    var isInitialsMode by remember { mutableStateOf(false) }
    var selectedSlot by remember { mutableIntStateOf(0) }

    // DRAW TAB STATE
    val strokes = remember { mutableStateListOf<DrawnStroke>() }
    var currentStrokePoints by remember { mutableStateOf<List<StrokePoint>>(emptyList()) }
    var selectedColor by remember { mutableStateOf(Color(0xFF0D47A1)) } // Deep Ink Blue
    var strokeThickness by remember { mutableFloatStateOf(6f) }

    // TYPE TAB STATE
    var typedName by remember { mutableStateOf("") }
    var typedStyleIndex by remember { mutableIntStateOf(0) }

    // IMPORT TAB STATE
    var importedUri by remember { mutableStateOf<Uri?>(null) }
    var rawImportedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var processedImportedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var removeBackground by remember { mutableStateOf(true) }
    var bgThreshold by remember { mutableFloatStateOf(215f) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            importedUri = uri
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val bmp = BitmapFactory.decodeStream(stream)
                        rawImportedBitmap = bmp
                        processedImportedBitmap = if (removeBackground && bmp != null) {
                            signatureManager.removeWhiteBackground(bmp, bgThreshold.toInt())
                        } else {
                            bmp
                        }
                    }
                } catch (ignored: Exception) {
                }
            }
        }
    }

    // Recalculate imported preview when threshold or toggle changes
    LaunchedEffect(rawImportedBitmap, removeBackground, bgThreshold) {
        val raw = rawImportedBitmap ?: return@LaunchedEffect
        withContext(Dispatchers.Default) {
            processedImportedBitmap = if (removeBackground) {
                signatureManager.removeWhiteBackground(raw, bgThreshold.toInt())
            } else {
                raw
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = if (isNightMode) Color(0xFF1E1E1E) else MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isInitialsMode) "Create Initials" else "Create Signature",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (isNightMode) Color.White else MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Signature vs Initials Toggle Chips & Slot Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !isInitialsMode,
                        onClick = { isInitialsMode = false },
                        label = { Text("Signature") },
                        modifier = Modifier.testTag("chip_mode_signature")
                    )
                    FilterChip(
                        selected = isInitialsMode,
                        onClick = { isInitialsMode = true },
                        label = { Text("Initials") },
                        modifier = Modifier.testTag("chip_mode_initials")
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Slot:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    (0..2).forEach { slot ->
                        FilterChip(
                            selected = selectedSlot == slot,
                            onClick = { selectedSlot = slot },
                            label = { Text("${slot + 1}") },
                            modifier = Modifier.testTag("chip_slot_$slot")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Creation Mode Tabs (Draw, Type, Import)
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Draw") },
                    icon = { Icon(Icons.Default.Draw, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.testTag("tab_draw_signature")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Type") },
                    icon = { Icon(Icons.Default.TextFields, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.testTag("tab_type_signature")
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Import") },
                    icon = { Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.testTag("tab_import_signature")
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (selectedTab) {
                // ==================== TAB 0: DRAW ====================
                0 -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Drawing Canvas Box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White)
                                .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                                .pointerInput(selectedColor, strokeThickness) {
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            currentStrokePoints = listOf(StrokePoint(offset.x, offset.y))
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            val pt = StrokePoint(change.position.x, change.position.y)
                                            currentStrokePoints = currentStrokePoints + pt
                                        },
                                        onDragEnd = {
                                            if (currentStrokePoints.isNotEmpty()) {
                                                strokes.add(
                                                    DrawnStroke(
                                                        points = currentStrokePoints,
                                                        color = selectedColor,
                                                        strokeWidth = strokeThickness
                                                    )
                                                )
                                                currentStrokePoints = emptyList()
                                            }
                                        },
                                        onDragCancel = {
                                            currentStrokePoints = emptyList()
                                        }
                                    )
                                }
                                .testTag("canvas_draw_signature")
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                // Draw baseline guide
                                drawLine(
                                    color = Color(0xFFE0E0E0),
                                    start = Offset(24f, size.height * 0.75f),
                                    end = Offset(size.width - 24f, size.height * 0.75f),
                                    strokeWidth = 2f
                                )

                                for (stroke in strokes) {
                                    if (stroke.points.size > 1) {
                                        val path = Path()
                                        path.moveTo(stroke.points[0].x, stroke.points[0].y)
                                        for (i in 1 until stroke.points.size) {
                                            path.lineTo(stroke.points[i].x, stroke.points[i].y)
                                        }
                                        drawPath(
                                            path = path,
                                            color = stroke.color,
                                            style = Stroke(
                                                width = stroke.strokeWidth,
                                                cap = StrokeCap.Round,
                                                join = StrokeJoin.Round
                                            )
                                        )
                                    }
                                }

                                if (currentStrokePoints.size > 1) {
                                    val path = Path()
                                    path.moveTo(currentStrokePoints[0].x, currentStrokePoints[0].y)
                                    for (i in 1 until currentStrokePoints.size) {
                                        path.lineTo(currentStrokePoints[i].x, currentStrokePoints[i].y)
                                    }
                                    drawPath(
                                        path = path,
                                        color = selectedColor,
                                        style = Stroke(
                                            width = strokeThickness,
                                            cap = StrokeCap.Round,
                                            join = StrokeJoin.Round
                                        )
                                    )
                                }
                            }

                            if (strokes.isEmpty() && currentStrokePoints.isEmpty()) {
                                Text(
                                    text = "Sign with finger or stylus here",
                                    color = Color.LightGray,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Controls: Color pickers, Undo, Clear
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val colors = listOf(
                                    Color(0xFF0D47A1), // Navy Blue
                                    Color(0xFF1B1B1B), // Crisp Black
                                    Color(0xFFB71C1C)  // Signature Red
                                )
                                colors.forEach { color ->
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                            .border(
                                                width = if (selectedColor == color) 3.dp else 1.dp,
                                                color = if (selectedColor == color) MaterialTheme.colorScheme.primary else Color.LightGray,
                                                shape = CircleShape
                                            )
                                            .clickable { selectedColor = color }
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex) },
                                    enabled = strokes.isNotEmpty(),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("btn_undo_signature_stroke")
                                ) {
                                    Icon(Icons.Default.Undo, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Undo", fontSize = 12.sp)
                                }

                                OutlinedButton(
                                    onClick = { strokes.clear() },
                                    enabled = strokes.isNotEmpty(),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("btn_clear_signature_canvas")
                                ) {
                                    Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Clear", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                // ==================== TAB 1: TYPE ====================
                1 -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        OutlinedTextField(
                            value = typedName,
                            onValueChange = { typedName = it },
                            label = { Text(if (isInitialsMode) "Type your initials (e.g. JD)" else "Type your full name") },
                            placeholder = { Text("e.g. Alex Morgan") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_type_signature_name")
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Select Cursive Style",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Start)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Preview Cards for Cursive Styles
                        val previewText = typedName.ifBlank { if (isInitialsMode) "AM" else "Alex Morgan" }
                        val styles = listOf("Elegant Script", "Modern Brush", "Executive Cursive", "Formal Classic")

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            styles.forEachIndexed { index, styleName ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { typedStyleIndex = index }
                                        .testTag("card_signature_style_$index"),
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (typedStyleIndex == index) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    } else {
                                        if (isNightMode) Color(0xFF282828) else Color(0xFFF7F7F7)
                                    },
                                    border = if (typedStyleIndex == index) {
                                        androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                                    } else {
                                        androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = styleName,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = previewText,
                                                fontSize = 24.sp,
                                                fontStyle = FontStyle.Italic,
                                                fontFamily = when (index) {
                                                    0 -> FontFamily.Serif
                                                    1 -> FontFamily.Cursive
                                                    2 -> FontFamily.SansSerif
                                                    else -> FontFamily.Monospace
                                                },
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (isNightMode) Color.White else Color(0xFF0D47A1)
                                            )
                                        }

                                        if (typedStyleIndex == index) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ==================== TAB 2: IMPORT ====================
                2 -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Button(
                            onClick = {
                                photoPickerLauncher.launch(
                                    androidx.activity.result.PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_pick_signature_image")
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Choose Photo from Gallery")
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (processedImportedBitmap != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFE8EEF5))
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = processedImportedBitmap!!.asImageBitmap(),
                                    contentDescription = "Imported Signature Preview",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(12.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Remove paper background",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                androidx.compose.material3.Switch(
                                    checked = removeBackground,
                                    onCheckedChange = { removeBackground = it }
                                )
                            }

                            if (removeBackground) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        text = "Filter Sensitivity: ${bgThreshold.toInt()}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Slider(
                                        value = bgThreshold,
                                        onValueChange = { bgThreshold = it },
                                        valueRange = 140f..250f
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "Take a photo of your signature on paper, and we'll automatically remove the white background to make it transparent.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons: Cancel and Save & Use
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }

                Button(
                    onClick = {
                        coroutineScope.launch {
                            val bitmap: Bitmap? = when (selectedTab) {
                                0 -> {
                                    if (strokes.isNotEmpty()) {
                                        renderStrokesToBitmap(strokes)
                                    } else null
                                }
                                1 -> {
                                    val name = typedName.ifBlank { if (isInitialsMode) "JD" else "John Doe" }
                                    signatureManager.createTypeSignatureBitmap(
                                        name = name,
                                        styleIndex = typedStyleIndex,
                                        colorArgb = android.graphics.Color.BLACK
                                    )
                                }
                                2 -> processedImportedBitmap
                                else -> null
                            }

                            if (bitmap != null) {
                                signatureManager.saveSignature(selectedSlot, bitmap, isInitialsMode)
                                onSignatureCreated(bitmap, isInitialsMode, selectedSlot)
                                onDismiss()
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_save_signature_to_slot")
                ) {
                    Text("Save & Place")
                }
            }
        }
    }
}

private fun renderStrokesToBitmap(strokes: List<DrawnStroke>): Bitmap {
    val width = 600
    val height = 300
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)

    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
    }

    // Find bounding box to scale and center nicely
    var minX = Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxX = Float.MIN_VALUE
    var maxY = Float.MIN_VALUE

    for (s in strokes) {
        for (p in s.points) {
            if (p.x < minX) minX = p.x
            if (p.y < minY) minY = p.y
            if (p.x > maxX) maxX = p.x
            if (p.y > maxY) maxY = p.y
        }
    }

    val strokeW = (maxX - minX).coerceAtLeast(10f)
    val strokeH = (maxY - minY).coerceAtLeast(10f)
    val scale = (width * 0.8f / strokeW).coerceAtMost(height * 0.8f / strokeH).coerceIn(0.5f, 2.5f)

    val offsetX = (width - strokeW * scale) / 2f - minX * scale
    val offsetY = (height - strokeH * scale) / 2f - minY * scale

    for (s in strokes) {
        if (s.points.size > 1) {
            paint.color = s.color.toArgb()
            paint.strokeWidth = s.strokeWidth * scale
            val path = android.graphics.Path()
            path.moveTo(s.points[0].x * scale + offsetX, s.points[0].y * scale + offsetY)
            for (i in 1 until s.points.size) {
                path.lineTo(s.points[i].x * scale + offsetX, s.points[i].y * scale + offsetY)
            }
            canvas.drawPath(path, paint)
        }
    }

    return bitmap
}
