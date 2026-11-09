package com.example.ui.scan

import android.graphics.Bitmap
import android.graphics.PointF
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.scan.ScanCaptureMode
import com.example.scan.ScanExportPageSize
import com.example.scan.ScanFilter
import com.example.scan.ScanImageProcessor
import com.example.scan.ScanOcrEngine
import com.example.scan.ScannedPageItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.hypot

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanCropFilterScreen(
    initialRawFiles: List<File>,
    captureMode: ScanCaptureMode,
    onAddMoreFromCamera: () -> Unit,
    onExportFinished: (File, Boolean) -> Unit,
    onClose: () -> Unit
) {
    androidx.activity.compose.BackHandler {
        onClose()
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ocrEngine = remember(context) { ScanOcrEngine(context) }

    val pages = remember { mutableStateListOf<ScannedPageItem>() }
    var selectedPageIndex by remember { mutableIntStateOf(0) }
    var isEditingCrop by remember { mutableStateOf(false) }
    var isProcessingPage by remember { mutableStateOf(true) }
    var displayBitmap by remember { mutableStateOf<Bitmap?>(null) }

    var activeCorners by remember {
        mutableStateOf(ScannedPageItem.defaultCorners())
    }

    var showOcrDialogForCurrentPage by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }

    var isExporting by remember { mutableStateOf(false) }
    var exportStatusText by remember { mutableStateOf("") }
    var exportProgress by remember { mutableFloatStateOf(0f) }

    // Initialize pages from initialRawFiles (handling Book Spread & ID Card modes automatically)
    LaunchedEffect(initialRawFiles) {
        if (pages.isEmpty() && initialRawFiles.isNotEmpty()) {
            isProcessingPage = true
            withContext(Dispatchers.Default) {
                val workDir = File(context.cacheDir, "scan_session").apply { mkdirs() }
                val effectiveRawFiles = mutableListOf<File>()

                if (captureMode == ScanCaptureMode.BOOK) {
                    for (raw in initialRawFiles) {
                        val spreadBmp = ScanImageProcessor.decodeSafeBitmap(context, null, raw, 2048)
                        if (spreadBmp != null) {
                            val splitPair = ScanImageProcessor.splitBookSpread(spreadBmp, workDir)
                            spreadBmp.recycle()
                            effectiveRawFiles.add(splitPair.first)
                            effectiveRawFiles.add(splitPair.second)
                        } else {
                            effectiveRawFiles.add(raw)
                        }
                    }
                } else if (captureMode == ScanCaptureMode.ID_CARD && initialRawFiles.size >= 2) {
                    val combined = ScanImageProcessor.combineIdCardFrontAndBack(
                        frontFile = initialRawFiles[0],
                        backFile = initialRawFiles[1],
                        cacheDir = workDir
                    )
                    effectiveRawFiles.add(combined)
                    if (initialRawFiles.size > 2) {
                        effectiveRawFiles.addAll(initialRawFiles.drop(2))
                    }
                } else {
                    effectiveRawFiles.addAll(initialRawFiles)
                }

                val builtItems = effectiveRawFiles.map { rawFile ->
                    val bmp = ScanImageProcessor.decodeSafeBitmap(context, null, rawFile, 900)
                    val corners = if (bmp != null) {
                        val c = ScanImageProcessor.detectDocumentCorners(bmp)
                        bmp.recycle()
                        c
                    } else {
                        ScannedPageItem.defaultCorners()
                    }
                    val id = UUID.randomUUID().toString()
                    val procFile = File(workDir, "proc_$id.jpg")
                    val initialFilter = ScanFilter.AUTO_ENHANCE
                    ScanImageProcessor.processPageImage(
                        rawFile = rawFile,
                        corners = corners,
                        rotationDegrees = 0,
                        filter = initialFilter,
                        outputFile = procFile
                    )
                    ScannedPageItem(
                        id = id,
                        rawFile = rawFile,
                        processedFile = procFile,
                        corners = corners,
                        rotationDegrees = 0,
                        filter = initialFilter
                    )
                }
                withContext(Dispatchers.Main) {
                    pages.addAll(builtItems)
                    selectedPageIndex = 0
                    if (builtItems.isNotEmpty()) {
                        activeCorners = builtItems[0].corners
                    }
                }
            }
            isProcessingPage = false
        }
    }

    val currentPage = pages.getOrNull(selectedPageIndex)

    LaunchedEffect(
        currentPage?.id,
        currentPage?.filter,
        currentPage?.rotationDegrees,
        currentPage?.processedFile?.lastModified(),
        isEditingCrop
    ) {
        val page = currentPage ?: return@LaunchedEffect
        activeCorners = page.corners
        withContext(Dispatchers.IO) {
            val targetFile = if (isEditingCrop) page.rawFile else page.processedFile
            val decoded = ScanImageProcessor.decodeSafeBitmap(context, null, targetFile, 1400)
            withContext(Dispatchers.Main) {
                displayBitmap = decoded
            }
        }
    }

    fun reprocessCurrentPage(
        newCorners: List<PointF> = currentPage?.corners ?: activeCorners,
        newRotation: Int = currentPage?.rotationDegrees ?: 0,
        newFilter: ScanFilter = currentPage?.filter ?: ScanFilter.AUTO_ENHANCE
    ) {
        val page = currentPage ?: return
        val idx = selectedPageIndex
        scope.launch {
            isProcessingPage = true
            withContext(Dispatchers.Default) {
                val workDir = File(context.cacheDir, "scan_session").apply { mkdirs() }
                val updatedProcFile = File(workDir, "proc_${page.id}_${System.currentTimeMillis()}.jpg")
                ScanImageProcessor.processPageImage(
                    rawFile = page.rawFile,
                    corners = newCorners,
                    rotationDegrees = newRotation,
                    filter = newFilter,
                    outputFile = updatedProcFile
                )
                withContext(Dispatchers.Main) {
                    if (idx in pages.indices) {
                        pages[idx] = page.copy(
                            processedFile = updatedProcFile,
                            corners = newCorners,
                            rotationDegrees = newRotation,
                            filter = newFilter
                        )
                    }
                }
            }
            isProcessingPage = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (isEditingCrop) "Adjust 4 Corners & Perspective" else "Crop, Filter & Organize",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (pages.isNotEmpty()) {
                            Text(
                                text = "Page ${selectedPageIndex + 1} of ${pages.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (currentPage != null) {
                        IconButton(
                            onClick = { showOcrDialogForCurrentPage = true },
                            modifier = Modifier.testTag("scan_crop_ocr_btn")
                        ) {
                            Icon(Icons.Default.TextFields, contentDescription = "Extract Text (OCR)")
                        }
                    }
                    Button(
                        onClick = { showExportDialog = true },
                        enabled = pages.isNotEmpty() && !isProcessingPage,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("scan_export_open_dialog_btn")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Scan")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF141619))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                val bmp = displayBitmap
                if (bmp != null) {
                    var boxSize by remember { mutableStateOf(IntSize.Zero) }
                    var draggingCornerIndex by remember { mutableIntStateOf(-1) }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .onSizeChanged { boxSize = it },
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Scanned page preview",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )

                        if (isEditingCrop && boxSize.width > 0 && boxSize.height > 0) {
                            val imgAspect = bmp.width.toFloat() / bmp.height.toFloat()
                            val boxAspect = boxSize.width.toFloat() / boxSize.height.toFloat()
                            val drawW: Float
                            val drawH: Float
                            if (imgAspect > boxAspect) {
                                drawW = boxSize.width.toFloat()
                                drawH = drawW / imgAspect
                            } else {
                                drawH = boxSize.height.toFloat()
                                drawW = drawH * imgAspect
                            }
                            val offsetX = (boxSize.width - drawW) / 2f
                            val offsetY = (boxSize.height - drawH) / 2f

                            Canvas(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(drawW, drawH, offsetX, offsetY) {
                                        detectDragGestures(
                                            onDragStart = { touch ->
                                                var bestIdx = -1
                                                var bestDist = Float.MAX_VALUE
                                                activeCorners.forEachIndexed { index, pt ->
                                                    val cx = offsetX + pt.x * drawW
                                                    val cy = offsetY + pt.y * drawH
                                                    val dist = hypot(touch.x - cx, touch.y - cy)
                                                    if (dist < bestDist && dist < 120f) {
                                                        bestDist = dist
                                                        bestIdx = index
                                                    }
                                                }
                                                draggingCornerIndex = bestIdx
                                            },
                                            onDrag = { change, _ ->
                                                val idx = draggingCornerIndex
                                                if (idx in 0..3) {
                                                    change.consume()
                                                    val nx = ((change.position.x - offsetX) / drawW).coerceIn(0.01f, 0.99f)
                                                    val ny = ((change.position.y - offsetY) / drawH).coerceIn(0.01f, 0.99f)
                                                    val updated = activeCorners.toMutableList()
                                                    updated[idx] = PointF(nx, ny)
                                                    activeCorners = updated
                                                }
                                            },
                                            onDragEnd = {
                                                draggingCornerIndex = -1
                                            }
                                        )
                                    }
                            ) {
                                val pts = activeCorners.map {
                                    Offset(offsetX + it.x * drawW, offsetY + it.y * drawH)
                                }
                                if (pts.size == 4) {
                                    val path = Path().apply {
                                        moveTo(pts[0].x, pts[0].y)
                                        lineTo(pts[1].x, pts[1].y)
                                        lineTo(pts[2].x, pts[2].y)
                                        lineTo(pts[3].x, pts[3].y)
                                        close()
                                    }
                                    drawPath(
                                        path = path,
                                        color = Color(0x3300E676)
                                    )
                                    drawPath(
                                        path = path,
                                        color = Color(0xFF00E676),
                                        style = Stroke(width = 3.dp.toPx())
                                    )
                                    pts.forEach { pt ->
                                        drawCircle(
                                            color = Color.White,
                                            radius = 12.dp.toPx(),
                                            center = pt
                                        )
                                        drawCircle(
                                            color = Color(0xFF00C853),
                                            radius = 9.dp.toPx(),
                                            center = pt
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (isProcessingPage) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Black.copy(alpha = 0.7f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Text("Applying perspective & filter...", color = Color.White)
                        }
                    }
                }
            }

            Surface(
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isEditingCrop) {
                            Button(
                                onClick = {
                                    isEditingCrop = false
                                    reprocessCurrentPage(newCorners = activeCorners)
                                },
                                modifier = Modifier.testTag("scan_apply_crop_btn")
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Apply Perspective Crop")
                            }
                            OutlinedButton(
                                onClick = {
                                    activeCorners = listOf(
                                        PointF(0.02f, 0.02f),
                                        PointF(0.98f, 0.02f),
                                        PointF(0.98f, 0.98f),
                                        PointF(0.02f, 0.98f)
                                    )
                                }
                            ) {
                                Text("Full Page")
                            }
                        } else {
                            FilledTonalButton(
                                onClick = { isEditingCrop = true },
                                modifier = Modifier.testTag("scan_edit_corners_btn")
                            ) {
                                Icon(Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("4-Corner Crop")
                            }

                            FilledTonalButton(
                                onClick = {
                                    val page = currentPage ?: return@FilledTonalButton
                                    val nextRot = (page.rotationDegrees + 90) % 360
                                    reprocessCurrentPage(newRotation = nextRot)
                                },
                                modifier = Modifier.testTag("scan_rotate_90_btn")
                            ) {
                                Icon(Icons.Default.RotateRight, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Rotate 90°")
                            }

                            OutlinedButton(
                                onClick = {
                                    val page = currentPage ?: return@OutlinedButton
                                    val idx = selectedPageIndex
                                    scope.launch {
                                        isProcessingPage = true
                                        withContext(Dispatchers.Default) {
                                            val workDir = File(context.cacheDir, "scan_session").apply { mkdirs() }
                                            val srcBmp = ScanImageProcessor.decodeSafeBitmap(
                                                context,
                                                null,
                                                page.processedFile,
                                                2048
                                            )
                                            if (srcBmp != null) {
                                                val splitPair = ScanImageProcessor.splitBookSpread(srcBmp, workDir)
                                                srcBmp.recycle()
                                                val fullCorners = ScannedPageItem.defaultCorners()
                                                val leftItem = ScannedPageItem(
                                                    rawFile = splitPair.first,
                                                    processedFile = splitPair.first,
                                                    corners = fullCorners,
                                                    filter = page.filter
                                                )
                                                val rightItem = ScannedPageItem(
                                                    rawFile = splitPair.second,
                                                    processedFile = splitPair.second,
                                                    corners = fullCorners,
                                                    filter = page.filter
                                                )
                                                withContext(Dispatchers.Main) {
                                                    pages.removeAt(idx)
                                                    pages.add(idx, rightItem)
                                                    pages.add(idx, leftItem)
                                                    Toast.makeText(
                                                        context,
                                                        "Split facing pages into 2 pages",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }
                                        }
                                        isProcessingPage = false
                                    }
                                }
                            ) {
                                Icon(Icons.Default.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Split Book Page")
                            }

                            if (pages.size >= 2) {
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            isProcessingPage = true
                                            withContext(Dispatchers.Default) {
                                                val workDir = File(context.cacheDir, "scan_session").apply { mkdirs() }
                                                val p1 = pages[0].processedFile
                                                val p2 = pages[1].processedFile
                                                val combinedFile = ScanImageProcessor.combineIdCardFrontAndBack(
                                                    frontFile = p1,
                                                    backFile = p2,
                                                    cacheDir = workDir
                                                )
                                                val item = ScannedPageItem(
                                                    rawFile = combinedFile,
                                                    processedFile = combinedFile,
                                                    corners = ScannedPageItem.defaultCorners(),
                                                    filter = ScanFilter.ORIGINAL
                                                )
                                                withContext(Dispatchers.Main) {
                                                    pages.removeAt(1)
                                                    pages.removeAt(0)
                                                    pages.add(0, item)
                                                    selectedPageIndex = 0
                                                    Toast.makeText(
                                                        context,
                                                        "Combined Front & Back on 1 A4 Page",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }
                                            isProcessingPage = false
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.Badge, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("ID Front+Back A4")
                                }
                            }
                        }
                    }

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(ScanFilter.entries) { _, filter ->
                            val isSelected = currentPage?.filter == filter
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    reprocessCurrentPage(newFilter = filter)
                                },
                                label = { Text(filter.label) },
                                leadingIcon = if (isSelected) {
                                    {
                                        Icon(
                                            Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else null,
                                modifier = Modifier.testTag("scan_filter_${filter.name.lowercase()}")
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            pages.forEachIndexed { index, _ ->
                                val selected = index == selectedPageIndex
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier
                                        .clickable { selectedPageIndex = index }
                                        .padding(2.dp)
                                ) {
                                    Text(
                                        text = "P${index + 1}",
                                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                            IconButton(
                                onClick = onAddMoreFromCamera,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(Icons.Default.AddAPhoto, contentDescription = "Add Page")
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    val idx = selectedPageIndex
                                    if (idx > 0) {
                                        val item = pages.removeAt(idx)
                                        pages.add(idx - 1, item)
                                        selectedPageIndex = idx - 1
                                    }
                                },
                                enabled = selectedPageIndex > 0
                            ) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Move page earlier")
                            }
                            IconButton(
                                onClick = {
                                    val idx = selectedPageIndex
                                    if (idx < pages.size - 1) {
                                        val item = pages.removeAt(idx)
                                        pages.add(idx + 1, item)
                                        selectedPageIndex = idx + 1
                                    }
                                },
                                enabled = selectedPageIndex < pages.size - 1
                            ) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Move page later")
                            }
                            IconButton(
                                onClick = {
                                    if (pages.size <= 1) {
                                        onClose()
                                    } else {
                                        pages.removeAt(selectedPageIndex)
                                        selectedPageIndex = selectedPageIndex.coerceAtMost(pages.size - 1)
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete page",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showOcrDialogForCurrentPage && currentPage != null) {
        ExtractTextDialog(
            initialFile = currentPage.processedFile,
            onDismiss = { showOcrDialogForCurrentPage = false }
        )
    }

    if (showExportDialog) {
        val defaultName = remember {
            val fmt = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
            "Scan_${fmt.format(Date())}"
        }
        var fileName by remember { mutableStateOf(defaultName) }
        var selectedPageSize by remember { mutableStateOf(ScanExportPageSize.A4) }
        var jpegQuality by remember { mutableFloatStateOf(85f) }
        var enableSearchableOcr by remember { mutableStateOf(true) }

        AlertDialog(
            onDismissRequest = { if (!isExporting) showExportDialog = false },
            title = {
                Text(
                    text = "Export Scan (${pages.size} ${if (pages.size == 1) "page" else "pages"})",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = fileName,
                        onValueChange = { fileName = it },
                        label = { Text("Scan Name") },
                        singleLine = true,
                        enabled = !isExporting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("scan_export_filename_input")
                    )

                    Text(
                        text = "Page Size",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ScanExportPageSize.entries.forEach { sizeOption ->
                            FilterChip(
                                selected = selectedPageSize == sizeOption,
                                onClick = { selectedPageSize = sizeOption },
                                enabled = !isExporting,
                                label = { Text(sizeOption.label) }
                            )
                        }
                    }

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Image Quality", style = MaterialTheme.typography.labelLarge)
                            Text("${jpegQuality.toInt()}%", style = MaterialTheme.typography.labelMedium)
                        }
                        Slider(
                            value = jpegQuality,
                            onValueChange = { jpegQuality = it },
                            valueRange = 40f..100f,
                            enabled = !isExporting
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Searchable PDF (Multi-Language OCR)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Adds invisible text layer with ML Kit + PdfBox",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = enableSearchableOcr,
                            onCheckedChange = { enableSearchableOcr = it },
                            enabled = !isExporting,
                            modifier = Modifier.testTag("scan_export_ocr_switch")
                        )
                    }

                    if (enableSearchableOcr) {
                        var showExportLangDialog by remember { mutableStateOf(false) }
                        OcrLanguageQuickSelectorBar(
                            languagePackManager = ocrEngine.languagePackManager,
                            onOpenManagePacksDialog = { showExportLangDialog = true }
                        )
                        if (showExportLangDialog) {
                            OcrLanguagePacksDialog(
                                languagePackManager = ocrEngine.languagePackManager,
                                onDismiss = { showExportLangDialog = false }
                            )
                        }
                    }

                    if (isExporting) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            LinearProgressIndicator(
                                progress = { exportProgress },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = exportStatusText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = {
                            scope.launch {
                                isExporting = true
                                exportStatusText = "Exporting scan images..."
                                exportProgress = 0.5f
                                val exported = ocrEngine.exportScanAsImages(
                                    pages = pages.toList(),
                                    baseName = fileName.ifBlank { defaultName }
                                )
                                isExporting = false
                                showExportDialog = false
                                Toast.makeText(
                                    context,
                                    "Exported ${exported.name}",
                                    Toast.LENGTH_LONG
                                ).show()
                                onExportFinished(exported, false)
                            }
                        },
                        enabled = !isExporting
                    ) {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (pages.size > 1) "Images (ZIP)" else "Image")
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                isExporting = true
                                val outPdf = ocrEngine.exportScanToPdf(
                                    pages = pages.toList(),
                                    documentTitle = fileName.ifBlank { defaultName },
                                    pageSize = selectedPageSize,
                                    jpegQuality = jpegQuality / 100f,
                                    enableSearchableOcr = enableSearchableOcr,
                                    captureMode = captureMode,
                                    onProgress = { fraction, msg ->
                                        exportProgress = fraction
                                        exportStatusText = msg
                                    }
                                )
                                isExporting = false
                                showExportDialog = false
                                Toast.makeText(
                                    context,
                                    "Saved ${outPdf.name} in Scans",
                                    Toast.LENGTH_SHORT
                                ).show()
                                onExportFinished(outPdf, true)
                            }
                        },
                        enabled = !isExporting,
                        modifier = Modifier.testTag("scan_confirm_save_pdf_btn")
                    ) {
                        Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save PDF")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showExportDialog = false },
                    enabled = !isExporting
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
