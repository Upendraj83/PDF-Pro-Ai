package com.example.ui.tools

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.pdf.NormalizedRect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

private enum class DragHandle {
    NONE,
    INSIDE,
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    EDGE_LEFT,
    EDGE_TOP,
    EDGE_RIGHT,
    EDGE_BOTTOM
}

@Composable
fun PdfCropDialog(
    initialUri: Uri? = null,
    initialName: String? = null,
    initialPageIndex: Int = 0,
    totalPageCount: Int = 1,
    cachedFile: File? = null,
    getPageBitmap: suspend (pageIndex: Int, targetWidth: Int) -> Bitmap?,
    onApplyCrop: (
        pageIndex: Int,
        normalizedBox: NormalizedRect,
        applyToAllPages: Boolean,
        onDone: (File) -> Unit
    ) -> Unit,
    onDismiss: () -> Unit,
    onOpenInViewer: ((File) -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedPageIndex by remember { mutableIntStateOf(initialPageIndex.coerceIn(0, (totalPageCount - 1).coerceAtLeast(0))) }
    var applyToAllPages by remember { mutableStateOf(false) }

    // Normalized Bounding Box in 0f..1f (screen display space: 0,0 top-left, 1,1 bottom-right)
    var cropLeft by remember { mutableFloatStateOf(0.08f) }
    var cropTop by remember { mutableFloatStateOf(0.08f) }
    var cropRight by remember { mutableFloatStateOf(0.92f) }
    var cropBottom by remember { mutableFloatStateOf(0.92f) }

    var showAdvancedSliders by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var croppedResultFile by remember { mutableStateOf<File?>(null) }
    var showPrintDialogForCropped by remember { mutableStateOf(false) }

    // Page preview bitmap state
    var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isPageBitmapLoading by remember { mutableStateOf(true) }

    // File exporter for saving as copy
    val saveCopyLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destUri: Uri? ->
        if (destUri != null && croppedResultFile != null) {
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(destUri)?.use { out ->
                        croppedResultFile!!.inputStream().use { input -> input.copyTo(out) }
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Cropped PDF saved successfully", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Save failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // Load preview bitmap when selected page changes
    LaunchedEffect(selectedPageIndex) {
        isPageBitmapLoading = true
        pageBitmap = getPageBitmap(selectedPageIndex, 900)
        isPageBitmapLoading = false
    }

    Dialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Crop,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Crop & Margin Focus",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = initialName ?: "Current Document",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        enabled = !isProcessing,
                        modifier = Modifier.testTag("btn_close_crop_dialog")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                // If already cropped, show the Result View
                if (croppedResultFile != null) {
                    CropSuccessResultView(
                        resultFile = croppedResultFile!!,
                        selectedPage = selectedPageIndex + 1,
                        appliedToAll = applyToAllPages,
                        onOpenInViewer = { file ->
                            onOpenInViewer?.invoke(file)
                            onDismiss()
                        },
                        onPrint = { showPrintDialogForCropped = true },
                        onShare = {
                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                croppedResultFile!!
                            )
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share Cropped PDF"))
                        },
                        onSaveAsCopy = {
                            val baseName = (initialName ?: "document").removeSuffix(".pdf")
                            saveCopyLauncher.launch("${baseName}_cropped_p${selectedPageIndex + 1}.pdf")
                        },
                        onDone = onDismiss
                    )
                } else {
                    // Page Selector (if more than 1 page)
                    if (totalPageCount > 1) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = {
                                        if (selectedPageIndex > 0) selectedPageIndex--
                                    },
                                    enabled = selectedPageIndex > 0 && !isProcessing
                                ) {
                                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Page")
                                }

                                Text(
                                    text = "Target Page: ${selectedPageIndex + 1} of $totalPageCount",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )

                                IconButton(
                                    onClick = {
                                        if (selectedPageIndex < totalPageCount - 1) selectedPageIndex++
                                    },
                                    enabled = selectedPageIndex < totalPageCount - 1 && !isProcessing
                                ) {
                                    Icon(Icons.Default.ChevronRight, contentDescription = "Next Page")
                                }
                            }
                        }
                    }

                    // Interactive Crop Canvas Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(340.dp)
                            .testTag("crop_interactive_canvas"),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isPageBitmapLoading) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Text(
                                        text = "Rendering page preview…",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.LightGray
                                    )
                                }
                            } else {
                                InteractiveCropCanvas(
                                    bitmap = pageBitmap,
                                    cropLeft = cropLeft,
                                    cropTop = cropTop,
                                    cropRight = cropRight,
                                    cropBottom = cropBottom,
                                    onCropChanged = { l, t, r, b ->
                                        cropLeft = l
                                        cropTop = t
                                        cropRight = r
                                        cropBottom = b
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }

                    // Bounding Box Statistics & Dimensions Badge
                    val croppedWPercent = ((cropRight - cropLeft) * 100).toInt()
                    val croppedHPercent = ((cropBottom - cropTop) * 100).toInt()
                    val trimLeftPercent = (cropLeft * 100).toInt()
                    val trimRightPercent = ((1f - cropRight) * 100).toInt()
                    val trimTopPercent = (cropTop * 100).toInt()
                    val trimBottomPercent = ((1f - cropBottom) * 100).toInt()

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Active Crop Area: $croppedWPercent% × $croppedHPercent%",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Trim: Left $trimLeftPercent% • Right $trimRightPercent% • Top $trimTopPercent% • Bottom $trimBottomPercent%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            TextButton(
                                onClick = { showAdvancedSliders = !showAdvancedSliders }
                            ) {
                                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (showAdvancedSliders) "Hide Sliders" else "Fine Tune", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    // Quick Crop Presets Row
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Quick Presets",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 1. Trim Margins Preset (removes standard 8% scanned/printed borders)
                            FilterChip(
                                selected = cropLeft == 0.08f && cropRight == 0.92f && cropTop == 0.08f && cropBottom == 0.92f,
                                onClick = {
                                    cropLeft = 0.08f
                                    cropTop = 0.08f
                                    cropRight = 0.92f
                                    cropBottom = 0.92f
                                },
                                label = { Text("Trim Margins", style = MaterialTheme.typography.labelSmall) },
                                leadingIcon = { Icon(Icons.Default.CropFree, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                modifier = Modifier.testTag("preset_trim_margins")
                            )

                            // 2. Focus Center / Content (60% center focus)
                            FilterChip(
                                selected = cropLeft == 0.20f && cropRight == 0.80f && cropTop == 0.20f && cropBottom == 0.80f,
                                onClick = {
                                    cropLeft = 0.20f
                                    cropTop = 0.20f
                                    cropRight = 0.80f
                                    cropBottom = 0.80f
                                },
                                label = { Text("Focus Center", style = MaterialTheme.typography.labelSmall) },
                                leadingIcon = { Icon(Icons.Default.CenterFocusStrong, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                modifier = Modifier.testTag("preset_focus_center")
                            )

                            // 3. Reset to Full Page
                            FilterChip(
                                selected = cropLeft == 0f && cropRight == 1f && cropTop == 0f && cropBottom == 1f,
                                onClick = {
                                    cropLeft = 0f
                                    cropTop = 0f
                                    cropRight = 1f
                                    cropBottom = 1f
                                },
                                label = { Text("Full Page", style = MaterialTheme.typography.labelSmall) },
                                leadingIcon = { Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                modifier = Modifier.testTag("preset_reset_crop")
                            )
                        }
                    }

                    // Optional Fine-Tuning Sliders
                    AnimatedVisibility(visible = showAdvancedSliders) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MarginSliderRow(
                                    label = "Left Margin",
                                    value = cropLeft,
                                    maxValue = (cropRight - 0.1f).coerceIn(0.1f, 0.45f),
                                    onValueChange = { cropLeft = it }
                                )
                                MarginSliderRow(
                                    label = "Right Margin",
                                    value = 1f - cropRight,
                                    maxValue = (1f - (cropLeft + 0.1f)).coerceIn(0.1f, 0.45f),
                                    onValueChange = { cropRight = 1f - it }
                                )
                                MarginSliderRow(
                                    label = "Top Margin",
                                    value = cropTop,
                                    maxValue = (cropBottom - 0.1f).coerceIn(0.1f, 0.45f),
                                    onValueChange = { cropTop = it }
                                )
                                MarginSliderRow(
                                    label = "Bottom Margin",
                                    value = 1f - cropBottom,
                                    maxValue = (1f - (cropTop + 0.1f)).coerceIn(0.1f, 0.45f),
                                    onValueChange = { cropBottom = 1f - it }
                                )
                            }
                        }
                    }

                    // Scope Selection (This Page Only vs All Pages)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Apply Scope",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = !applyToAllPages,
                                onClick = { applyToAllPages = false },
                                label = { Text("This Page Only (p.${selectedPageIndex + 1})") },
                                modifier = Modifier.testTag("crop_scope_single_page")
                            )
                            if (totalPageCount > 1) {
                                FilterChip(
                                    selected = applyToAllPages,
                                    onClick = { applyToAllPages = true },
                                    label = { Text("All $totalPageCount Pages") },
                                    modifier = Modifier.testTag("crop_scope_all_pages")
                                )
                            }
                        }
                    }

                    // Footer Buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            enabled = !isProcessing
                        ) {
                            Text("Cancel")
                        }

                        Spacer(Modifier.width(8.dp))

                        Button(
                            onClick = {
                                isProcessing = true
                                val normalizedRect = NormalizedRect(
                                    left = cropLeft,
                                    top = cropTop,
                                    right = cropRight,
                                    bottom = cropBottom
                                )
                                onApplyCrop(selectedPageIndex, normalizedRect, applyToAllPages) { result ->
                                    isProcessing = false
                                    croppedResultFile = result
                                }
                            },
                            enabled = !isProcessing && (cropRight > cropLeft + 0.05f) && (cropBottom > cropTop + 0.05f),
                            modifier = Modifier.testTag("btn_confirm_crop_page")
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Cropping…")
                            } else {
                                Icon(Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Apply Crop")
                            }
                        }
                    }
                }
            }
        }
    }

    // Print Dialog for Cropped PDF if requested
    if (showPrintDialogForCropped && croppedResultFile != null) {
        PrintPdfDialog(
            initialUri = Uri.fromFile(croppedResultFile),
            initialFileName = croppedResultFile!!.name,
            initialFile = croppedResultFile,
            currentPageIndex = selectedPageIndex,
            onDismiss = { showPrintDialogForCropped = false }
        )
    }
}

@Composable
private fun MarginSliderRow(
    label: String,
    value: Float,
    maxValue: Float,
    onValueChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(90.dp)
        )
        Slider(
            value = value.coerceIn(0f, maxValue),
            onValueChange = onValueChange,
            valueRange = 0f..maxValue,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "${(value * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(36.dp),
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun InteractiveCropCanvas(
    bitmap: Bitmap?,
    cropLeft: Float,
    cropTop: Float,
    cropRight: Float,
    cropBottom: Float,
    onCropChanged: (l: Float, t: Float, r: Float, b: Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var activeHandle by remember { mutableStateOf(DragHandle.NONE) }

    Canvas(
        modifier = modifier
            .pointerInput(cropLeft, cropTop, cropRight, cropBottom, bitmap) {
                val canvasW = size.width.toFloat()
                val canvasH = size.height.toFloat()

                // Calculate fitted page rect
                val bmpW = bitmap?.width?.toFloat() ?: 1000f
                val bmpH = bitmap?.height?.toFloat() ?: 1414f
                val scale = minOf(canvasW / bmpW, canvasH / bmpH) * 0.92f
                val pageW = bmpW * scale
                val pageH = bmpH * scale
                val pageX = (canvasW - pageW) / 2f
                val pageY = (canvasH - pageH) / 2f

                detectDragGestures(
                    onDragStart = { downOffset ->
                        val touchRadius = 40.dp.toPx()
                        val cropBoxPixelLeft = pageX + cropLeft * pageW
                        val cropBoxPixelRight = pageX + cropRight * pageW
                        val cropBoxPixelTop = pageY + cropTop * pageH
                        val cropBoxPixelBottom = pageY + cropBottom * pageH

                        val distTL = (downOffset - Offset(cropBoxPixelLeft, cropBoxPixelTop)).getDistance()
                        val distTR = (downOffset - Offset(cropBoxPixelRight, cropBoxPixelTop)).getDistance()
                        val distBL = (downOffset - Offset(cropBoxPixelLeft, cropBoxPixelBottom)).getDistance()
                        val distBR = (downOffset - Offset(cropBoxPixelRight, cropBoxPixelBottom)).getDistance()

                        activeHandle = when {
                            distTL <= touchRadius -> DragHandle.TOP_LEFT
                            distTR <= touchRadius -> DragHandle.TOP_RIGHT
                            distBL <= touchRadius -> DragHandle.BOTTOM_LEFT
                            distBR <= touchRadius -> DragHandle.BOTTOM_RIGHT
                            downOffset.x in (cropBoxPixelLeft - touchRadius)..(cropBoxPixelLeft + touchRadius) &&
                                downOffset.y in cropBoxPixelTop..cropBoxPixelBottom -> DragHandle.EDGE_LEFT
                            downOffset.x in (cropBoxPixelRight - touchRadius)..(cropBoxPixelRight + touchRadius) &&
                                downOffset.y in cropBoxPixelTop..cropBoxPixelBottom -> DragHandle.EDGE_RIGHT
                            downOffset.y in (cropBoxPixelTop - touchRadius)..(cropBoxPixelTop + touchRadius) &&
                                downOffset.x in cropBoxPixelLeft..cropBoxPixelRight -> DragHandle.EDGE_TOP
                            downOffset.y in (cropBoxPixelBottom - touchRadius)..(cropBoxPixelBottom + touchRadius) &&
                                downOffset.x in cropBoxPixelLeft..cropBoxPixelRight -> DragHandle.EDGE_BOTTOM
                            downOffset.x in cropBoxPixelLeft..cropBoxPixelRight &&
                                downOffset.y in cropBoxPixelTop..cropBoxPixelBottom -> DragHandle.INSIDE
                            else -> DragHandle.NONE
                        }
                    },
                    onDragEnd = {
                        activeHandle = DragHandle.NONE
                    },
                    onDragCancel = {
                        activeHandle = DragHandle.NONE
                    },
                    onDrag = { _, dragAmount ->
                        if (pageW <= 0f || pageH <= 0f) return@detectDragGestures
                        val deltaNormX = dragAmount.x / pageW
                        val deltaNormY = dragAmount.y / pageH
                        val minSize = 0.06f

                        var newL = cropLeft
                        var newT = cropTop
                        var newR = cropRight
                        var newB = cropBottom

                        when (activeHandle) {
                            DragHandle.TOP_LEFT -> {
                                newL = (cropLeft + deltaNormX).coerceIn(0f, cropRight - minSize)
                                newT = (cropTop + deltaNormY).coerceIn(0f, cropBottom - minSize)
                            }
                            DragHandle.TOP_RIGHT -> {
                                newR = (cropRight + deltaNormX).coerceIn(cropLeft + minSize, 1f)
                                newT = (cropTop + deltaNormY).coerceIn(0f, cropBottom - minSize)
                            }
                            DragHandle.BOTTOM_LEFT -> {
                                newL = (cropLeft + deltaNormX).coerceIn(0f, cropRight - minSize)
                                newB = (cropBottom + deltaNormY).coerceIn(cropTop + minSize, 1f)
                            }
                            DragHandle.BOTTOM_RIGHT -> {
                                newR = (cropRight + deltaNormX).coerceIn(cropLeft + minSize, 1f)
                                newB = (cropBottom + deltaNormY).coerceIn(cropTop + minSize, 1f)
                            }
                            DragHandle.EDGE_LEFT -> {
                                newL = (cropLeft + deltaNormX).coerceIn(0f, cropRight - minSize)
                            }
                            DragHandle.EDGE_RIGHT -> {
                                newR = (cropRight + deltaNormX).coerceIn(cropLeft + minSize, 1f)
                            }
                            DragHandle.EDGE_TOP -> {
                                newT = (cropTop + deltaNormY).coerceIn(0f, cropBottom - minSize)
                            }
                            DragHandle.EDGE_BOTTOM -> {
                                newB = (cropBottom + deltaNormY).coerceIn(cropTop + minSize, 1f)
                            }
                            DragHandle.INSIDE -> {
                                val boxW = cropRight - cropLeft
                                val boxH = cropBottom - cropTop
                                val shiftedL = (cropLeft + deltaNormX).coerceIn(0f, 1f - boxW)
                                val shiftedT = (cropTop + deltaNormY).coerceIn(0f, 1f - boxH)
                                newL = shiftedL
                                newR = shiftedL + boxW
                                newT = shiftedT
                                newB = shiftedT + boxH
                            }
                            DragHandle.NONE -> {}
                        }

                        onCropChanged(newL, newT, newR, newB)
                    }
                )
            }
    ) {
        val canvasW = size.width
        val canvasH = size.height

        val bmpW = bitmap?.width?.toFloat() ?: 1000f
        val bmpH = bitmap?.height?.toFloat() ?: 1414f
        val scale = minOf(canvasW / bmpW, canvasH / bmpH) * 0.92f
        val pageW = bmpW * scale
        val pageH = bmpH * scale
        val pageX = (canvasW - pageW) / 2f
        val pageY = (canvasH - pageH) / 2f

        // Draw page bitmap centered
        if (bitmap != null && !bitmap.isRecycled) {
            drawImage(
                image = bitmap.asImageBitmap(),
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(bitmap.width, bitmap.height),
                dstOffset = IntOffset(pageX.toInt(), pageY.toInt()),
                dstSize = IntSize(pageW.toInt(), pageH.toInt())
            )
        } else {
            // Draw placeholder white paper card
            drawRect(
                color = Color.White,
                topLeft = Offset(pageX, pageY),
                size = Size(pageW, pageH)
            )
        }

        // Compute crop box coordinates in canvas pixels
        val boxPixelLeft = pageX + cropLeft * pageW
        val boxPixelRight = pageX + cropRight * pageW
        val boxPixelTop = pageY + cropTop * pageH
        val boxPixelBottom = pageY + cropBottom * pageH
        val boxPixelWidth = boxPixelRight - boxPixelLeft
        val boxPixelHeight = boxPixelBottom - boxPixelTop

        // Dim outer margins outside the crop box
        val dimColor = Color.Black.copy(alpha = 0.58f)
        val outerPath = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(pageX, pageY, pageX + pageW, pageY + pageH))
            addRect(Rect(boxPixelLeft, boxPixelTop, boxPixelRight, boxPixelBottom))
        }
        drawPath(outerPath, color = dimColor)

        // Draw Crop Box Border (Bright Primary)
        val cropBorderColor = Color(0xFF2979FF)
        drawRect(
            color = cropBorderColor,
            topLeft = Offset(boxPixelLeft, boxPixelTop),
            size = Size(boxPixelWidth, boxPixelHeight),
            style = Stroke(width = 2.5f)
        )

        // Draw Rule-of-Thirds Grid Lines
        val gridColor = Color.White.copy(alpha = 0.35f)
        val oneThirdW = boxPixelWidth / 3f
        val oneThirdH = boxPixelHeight / 3f
        // Vertical lines
        drawLine(gridColor, Offset(boxPixelLeft + oneThirdW, boxPixelTop), Offset(boxPixelLeft + oneThirdW, boxPixelBottom), strokeWidth = 1f)
        drawLine(gridColor, Offset(boxPixelLeft + oneThirdW * 2f, boxPixelTop), Offset(boxPixelLeft + oneThirdW * 2f, boxPixelBottom), strokeWidth = 1f)
        // Horizontal lines
        drawLine(gridColor, Offset(boxPixelLeft, boxPixelTop + oneThirdH), Offset(boxPixelRight, boxPixelTop + oneThirdH), strokeWidth = 1f)
        drawLine(gridColor, Offset(boxPixelLeft, boxPixelTop + oneThirdH * 2f), Offset(boxPixelRight, boxPixelTop + oneThirdH * 2f), strokeWidth = 1f)

        // Draw Corner Handles (Crisp white squares with border)
        val cornerHandleSize = 14f
        val cornerColor = Color.White
        val cornerBorder = Color(0xFF0039CB)

        listOf(
            Offset(boxPixelLeft, boxPixelTop),
            Offset(boxPixelRight, boxPixelTop),
            Offset(boxPixelLeft, boxPixelBottom),
            Offset(boxPixelRight, boxPixelBottom)
        ).forEach { center ->
            drawCircle(
                color = cornerBorder,
                radius = cornerHandleSize + 2f,
                center = center
            )
            drawCircle(
                color = cornerColor,
                radius = cornerHandleSize,
                center = center
            )
            drawCircle(
                color = cropBorderColor,
                radius = cornerHandleSize * 0.45f,
                center = center
            )
        }

        // Draw Edge Grab Indicator pills
        val edgeIndicatorLen = 22f
        val edgeIndicatorThick = 4f
        // Left
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(boxPixelLeft - edgeIndicatorThick / 2f, boxPixelTop + boxPixelHeight / 2f - edgeIndicatorLen / 2f),
            size = Size(edgeIndicatorThick, edgeIndicatorLen)
        )
        // Right
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(boxPixelRight - edgeIndicatorThick / 2f, boxPixelTop + boxPixelHeight / 2f - edgeIndicatorLen / 2f),
            size = Size(edgeIndicatorThick, edgeIndicatorLen)
        )
        // Top
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(boxPixelLeft + boxPixelWidth / 2f - edgeIndicatorLen / 2f, boxPixelTop - edgeIndicatorThick / 2f),
            size = Size(edgeIndicatorLen, edgeIndicatorThick)
        )
        // Bottom
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(boxPixelLeft + boxPixelWidth / 2f - edgeIndicatorLen / 2f, boxPixelBottom - edgeIndicatorThick / 2f),
            size = Size(edgeIndicatorLen, edgeIndicatorThick)
        )
    }
}

@Composable
private fun CropSuccessResultView(
    resultFile: File,
    selectedPage: Int,
    appliedToAll: Boolean,
    onOpenInViewer: (File) -> Unit,
    onPrint: () -> Unit,
    onShare: () -> Unit,
    onSaveAsCopy: () -> Unit,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = Color(0xFF2E7D32),
            modifier = Modifier.size(54.dp)
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Crop Successfully Applied!",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (appliedToAll) "Applied custom margins to all pages" else "Cropped page $selectedPage to defined content box",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val sizeKb = resultFile.length() / 1024
            Text(
                text = "File size: ${sizeKb} KB • Ready to view, share or print",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // Primary Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = { onOpenInViewer(resultFile) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("btn_crop_open_viewer")
            ) {
                Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Open in Viewer")
            }

            OutlinedButton(
                onClick = onPrint,
                modifier = Modifier
                    .weight(1f)
                    .testTag("btn_crop_print")
            ) {
                Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Print")
            }
        }

        // Secondary Actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onShare,
                modifier = Modifier
                    .weight(1f)
                    .testTag("btn_crop_share")
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Share")
            }

            OutlinedButton(
                onClick = onSaveAsCopy,
                modifier = Modifier
                    .weight(1f)
                    .testTag("btn_crop_save_copy")
            ) {
                Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Save Copy")
            }
        }

        TextButton(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Done")
        }
    }
}
