package com.example.ui.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.pdf.RotatePagesSaveMode
import com.example.pdf.RotatePagesScope
import com.example.pdf.RotationIncrement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private data class PagePreviewMeta(
    val pageIndex: Int,
    val widthPt: Int,
    val heightPt: Int,
    val thumbnail: Bitmap?
) {
    val isLandscape: Boolean
        get() = widthPt > heightPt
}

@Composable
fun RotatePdfPagesDialog(
    initialUri: Uri?,
    initialName: String?,
    initialCachedFile: File? = null,
    initialCurrentPage: Int = 0,
    onRotateConfirmed: (
        sourceFile: File?,
        sourceUri: Uri,
        sourceName: String,
        pageRotationDeltas: Map<Int, Int>,
        overwriteOriginal: Boolean,
        outputName: String
    ) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    var selectedUri by remember { mutableStateOf(initialUri) }
    var selectedName by remember { mutableStateOf(initialName ?: "") }
    var activeLocalFile by remember {
        mutableStateOf(
            if (initialUri != null && initialCachedFile != null && initialCachedFile.exists()) {
                initialCachedFile
            } else {
                null
            }
        )
    }

    var totalPages by remember { mutableIntStateOf(0) }
    var isLoadingPages by remember { mutableStateOf(false) }
    var pagePreviews by remember { mutableStateOf<List<PagePreviewMeta>>(emptyList()) }

    // Selection scope & state
    var selectionScope by remember { mutableStateOf(RotatePagesScope.SELECTED_PAGES) }
    var selectedPageSet by remember {
        mutableStateOf(setOf(initialCurrentPage.coerceAtLeast(0)))
    }
    var customRangeInput by remember { mutableStateOf("1") }

    // Map of 0-based pageIndex -> cumulative rotation delta in 90-degree increments (0, 90, 180, 270)
    val pageRotationDeltas = remember { mutableStateMapOf<Int, Int>() }

    // Save mode & output filename
    var saveMode by remember { mutableStateOf(RotatePagesSaveMode.SAVE_TO_DOCUMENT_FILE) }
    var outputFileName by remember(selectedName) {
        val base = selectedName.removeSuffix(".pdf").ifBlank { "Document" }
        mutableStateOf("${base}_rotated.pdf")
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedUri = uri
            selectedName = queryToolFileName(context, uri) ?: "Document.pdf"
            activeLocalFile = null
            pageRotationDeltas.clear()
        }
    }

    // Load PDF page count and lightweight thumbnails when selectedUri changes
    LaunchedEffect(selectedUri) {
        val uri = selectedUri ?: return@LaunchedEffect
        isLoadingPages = true
        withContext(Dispatchers.IO) {
            try {
                val fileToRender: File? = when {
                    activeLocalFile != null && activeLocalFile!!.exists() && activeLocalFile!!.length() > 0L ->
                        activeLocalFile
                    uri.scheme == "file" && uri.path != null ->
                        File(uri.path!!).takeIf { it.exists() }
                    else -> {
                        val temp = File(context.cacheDir, "rotate_preview_${System.currentTimeMillis()}.pdf")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            FileOutputStream(temp).use { output -> input.copyTo(output) }
                        }
                        if (temp.exists() && temp.length() > 0L) temp else null
                    }
                }

                if (fileToRender != null && fileToRender.exists()) {
                    if (activeLocalFile == null) {
                        activeLocalFile = fileToRender
                    }
                    val previews = mutableListOf<PagePreviewMeta>()
                    var count = 0
                    try {
                        ParcelFileDescriptor.open(fileToRender, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                            PdfRenderer(pfd).use { renderer ->
                                count = renderer.pageCount
                                val maxThumbnails = count.coerceAtMost(60)
                                for (i in 0 until count) {
                                    renderer.openPage(i).use { page ->
                                        val w = page.width.coerceAtLeast(1)
                                        val h = page.height.coerceAtLeast(1)
                                        val bmp = if (i < maxThumbnails) {
                                            val scale = 220f / maxOf(w, h).toFloat()
                                            val bmpW = (w * scale).toInt().coerceAtLeast(40)
                                            val bmpH = (h * scale).toInt().coerceAtLeast(40)
                                            val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                                            Canvas(bitmap).drawColor(AndroidColor.WHITE)
                                            page.render(
                                                bitmap,
                                                null,
                                                null,
                                                PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                                            )
                                            bitmap
                                        } else {
                                            null
                                        }
                                        previews.add(PagePreviewMeta(i, w, h, bmp))
                                    }
                                }
                            }
                        }
                    } catch (_: Throwable) {
                    }

                    // Fallback to PDFBox page metadata if PdfRenderer returned 0 pages (e.g. JVM/Robolectric or unsupported stream)
                    if (count <= 0) {
                        try {
                            if (!com.tom_roush.pdfbox.android.PDFBoxResourceLoader.isReady()) {
                                com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context.applicationContext)
                            }
                            com.example.pdf.PdfBoxMemoryUtil.loadFromFile(context, fileToRender).use { doc ->
                                count = doc.numberOfPages
                                for (i in 0 until count) {
                                    val page = doc.getPage(i)
                                    val media = page.mediaBox
                                    val rot = RotationIncrement.normalizeTo90Step(page.rotation)
                                    val rawW = (media?.width ?: 595f).toInt().coerceAtLeast(1)
                                    val rawH = (media?.height ?: 842f).toInt().coerceAtLeast(1)
                                    val w = if (rot == 90 || rot == 270) rawH else rawW
                                    val h = if (rot == 90 || rot == 270) rawW else rawH
                                    previews.add(PagePreviewMeta(i, w, h, null))
                                }
                            }
                        } catch (_: Throwable) {
                        }
                    }

                    withContext(Dispatchers.Main) {
                        totalPages = count
                        pagePreviews = previews
                        val validInitial = initialCurrentPage.coerceIn(0, (count - 1).coerceAtLeast(0))
                        selectedPageSet = setOf(validInitial)
                        customRangeInput = if (count > 1) "1-$count" else "1"
                    }
                }
            } catch (_: Exception) {
            } finally {
                withContext(Dispatchers.Main) {
                    isLoadingPages = false
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // Do not recycle bitmaps while composition might still draw them during exit animation
        }
    }

    // Resolve target page indices based on current scope
    val effectiveTargetPages: Set<Int> = remember(
        selectionScope,
        selectedPageSet,
        customRangeInput,
        totalPages
    ) {
        if (totalPages <= 0) {
            emptySet()
        } else {
            when (selectionScope) {
                RotatePagesScope.SELECTED_PAGES -> selectedPageSet.filter { it in 0 until totalPages }.toSet()
                RotatePagesScope.ALL_PAGES -> (0 until totalPages).toSet()
                RotatePagesScope.CUSTOM_RANGE -> parseToolPageRangeList(customRangeInput, totalPages).toSet()
                RotatePagesScope.ODD_PAGES -> (0 until totalPages).filter { it % 2 == 0 }.toSet()
                RotatePagesScope.EVEN_PAGES -> (0 until totalPages).filter { it % 2 == 1 }.toSet()
            }
        }
    }

    fun apply90IncrementToPages(targetPages: Iterable<Int>, deltaDegrees: Int) {
        for (pageIdx in targetPages) {
            if (pageIdx in 0 until totalPages) {
                val existing = pageRotationDeltas[pageIdx] ?: 0
                val updated = RotationIncrement.normalizeTo90Step(existing + deltaDegrees)
                if (updated == 0) {
                    pageRotationDeltas.remove(pageIdx)
                } else {
                    pageRotationDeltas[pageIdx] = updated
                }
            }
        }
    }

    val modifiedPagesCount = pageRotationDeltas.count { (_, deg) ->
        RotationIncrement.normalizeTo90Step(deg) != 0
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .heightIn(max = 760.dp)
                .testTag("rotate_pages_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.RotateRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Column {
                            Text(
                                text = "Rotate PDF Pages (90° Increments)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Rotate selected pages by 90°, 180°, or 270° and save to file",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("rotate_dialog_close_btn")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close Rotate Pages tool")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 1. Source PDF Document Selector Card
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
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
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Column {
                                    Text(
                                        text = if (selectedUri != null) selectedName.ifBlank { "Selected_Document.pdf" }
                                        else "No PDF document selected",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (totalPages > 0) "$totalPages page(s) • $modifiedPagesCount page(s) queued for rotation"
                                        else "Choose a PDF document to rotate pages",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            FilledTonalButton(
                                onClick = { pdfPickerLauncher.launch(arrayOf("application/pdf")) },
                                modifier = Modifier.testTag("rotate_select_pdf_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (selectedUri == null) "Select PDF" else "Change")
                            }
                        }
                    }

                    if (isLoadingPages) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                                Text(
                                    text = "Loading PDF pages...",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    } else if (selectedUri != null && totalPages > 0) {
                        // 2. Page Selection Scope Chips
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "1. Select Pages to Rotate (${effectiveTargetPages.size} targeted)",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    TextButton(
                                        onClick = {
                                            selectionScope = RotatePagesScope.SELECTED_PAGES
                                            selectedPageSet = (0 until totalPages).toSet()
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.testTag("rotate_select_all_pages_btn")
                                    ) {
                                        Text("Select All", style = MaterialTheme.typography.labelSmall)
                                    }
                                    TextButton(
                                        onClick = {
                                            selectionScope = RotatePagesScope.SELECTED_PAGES
                                            selectedPageSet = emptySet()
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.testTag("rotate_clear_selection_btn")
                                    ) {
                                        Text("Clear", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                RotatePagesScope.entries.forEach { scope ->
                                    FilterChip(
                                        selected = selectionScope == scope,
                                        onClick = {
                                            selectionScope = scope
                                            when (scope) {
                                                RotatePagesScope.ALL_PAGES ->
                                                    selectedPageSet = (0 until totalPages).toSet()
                                                RotatePagesScope.ODD_PAGES ->
                                                    selectedPageSet = (0 until totalPages).filter { it % 2 == 0 }.toSet()
                                                RotatePagesScope.EVEN_PAGES ->
                                                    selectedPageSet = (0 until totalPages).filter { it % 2 == 1 }.toSet()
                                                else -> {}
                                            }
                                        },
                                        label = { Text(scope.displayName) },
                                        modifier = Modifier.testTag("rotate_scope_chip_${scope.name}")
                                    )
                                }

                                // Orientation quick filters
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        selectionScope = RotatePagesScope.SELECTED_PAGES
                                        selectedPageSet = pagePreviews.filter { it.isLandscape }.map { it.pageIndex }.toSet()
                                    },
                                    label = { Text("Landscape Only") },
                                    modifier = Modifier.testTag("rotate_filter_landscape_btn")
                                )
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        selectionScope = RotatePagesScope.SELECTED_PAGES
                                        selectedPageSet = pagePreviews.filter { !it.isLandscape }.map { it.pageIndex }.toSet()
                                    },
                                    label = { Text("Portrait Only") },
                                    modifier = Modifier.testTag("rotate_filter_portrait_btn")
                                )
                            }

                            if (selectionScope == RotatePagesScope.CUSTOM_RANGE) {
                                OutlinedTextField(
                                    value = customRangeInput,
                                    onValueChange = { customRangeInput = it },
                                    label = { Text("Page Range (e.g., 1-3, 5, 7-9)") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("rotate_custom_range_input")
                                )
                            }
                        }

                        // 3. Batch 90-Degree Increment Controls
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "2. Apply 90° Rotation Step to Target Pages",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilledTonalButton(
                                    onClick = {
                                        apply90IncrementToPages(
                                            effectiveTargetPages,
                                            RotationIncrement.COUNTER_CLOCKWISE_90.degreesDelta
                                        )
                                    },
                                    enabled = effectiveTargetPages.isNotEmpty(),
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("rotate_batch_ccw_90_btn"),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.RotateLeft,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Left -90°", style = MaterialTheme.typography.labelMedium)
                                }

                                Button(
                                    onClick = {
                                        apply90IncrementToPages(
                                            effectiveTargetPages,
                                            RotationIncrement.CLOCKWISE_90.degreesDelta
                                        )
                                    },
                                    enabled = effectiveTargetPages.isNotEmpty(),
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("rotate_batch_cw_90_btn"),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.RotateRight,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Right +90°", style = MaterialTheme.typography.labelMedium)
                                }

                                FilledTonalButton(
                                    onClick = {
                                        apply90IncrementToPages(
                                            effectiveTargetPages,
                                            RotationIncrement.FLIP_180.degreesDelta
                                        )
                                    },
                                    enabled = effectiveTargetPages.isNotEmpty(),
                                    modifier = Modifier
                                        .weight(1f)
                                        .minimumInteractiveComponentSize()
                                        .testTag("rotate_batch_180_btn"),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FlipCameraAndroid,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Flip 180°", style = MaterialTheme.typography.labelMedium)
                                }
                            }

                            if (modifiedPagesCount > 0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "$modifiedPagesCount page(s) rotated in 90° increments",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    TextButton(
                                        onClick = { pageRotationDeltas.clear() },
                                        modifier = Modifier.testTag("rotate_reset_all_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Reset All Rotations", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }

                        // 4. Interactive Page Thumbnail Grid with Per-Page 90° Controls
                        Text(
                            text = "Tap pages to select or use per-page -90° / +90° buttons:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 112.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                .padding(8.dp)
                                .testTag("rotate_pages_thumbnail_grid"),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(pagePreviews, key = { it.pageIndex }) { pageMeta ->
                                val pageIdx = pageMeta.pageIndex
                                val isSelected = effectiveTargetPages.contains(pageIdx)
                                val deltaDeg = RotationIncrement.normalizeTo90Step(
                                    pageRotationDeltas[pageIdx] ?: 0
                                )

                                RotatePageThumbnailCard(
                                    pageMeta = pageMeta,
                                    isSelected = isSelected,
                                    rotationDeltaDegrees = deltaDeg,
                                    onToggleSelect = {
                                        selectionScope = RotatePagesScope.SELECTED_PAGES
                                        selectedPageSet = if (selectedPageSet.contains(pageIdx)) {
                                            selectedPageSet - pageIdx
                                        } else {
                                            selectedPageSet + pageIdx
                                        }
                                    },
                                    onRotateCcw90 = {
                                        apply90IncrementToPages(listOf(pageIdx), -90)
                                    },
                                    onRotateCw90 = {
                                        apply90IncrementToPages(listOf(pageIdx), 90)
                                    }
                                )
                            }
                        }

                        HorizontalDivider()

                        // 5. Save Destination Mode (Overwrite Original Document File vs Save as New PDF File)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "3. Save Changes to Document File",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )

                            RotatePagesSaveMode.entries.forEach { mode ->
                                val selected = saveMode == mode
                                val tag = if (mode == RotatePagesSaveMode.SAVE_TO_DOCUMENT_FILE) {
                                    "rotate_save_mode_overwrite"
                                } else {
                                    "rotate_save_mode_new_copy"
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { saveMode = mode }
                                        .padding(vertical = 4.dp, horizontal = 4.dp)
                                        .testTag(tag),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selected,
                                        onClick = { saveMode = mode }
                                    )
                                    Column(modifier = Modifier.padding(start = 6.dp)) {
                                        Text(
                                            text = mode.displayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                        )
                                        Text(
                                            text = mode.subtitle,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            if (saveMode == RotatePagesSaveMode.SAVE_AS_NEW_COPY) {
                                OutlinedTextField(
                                    value = outputFileName,
                                    onValueChange = { outputFileName = it },
                                    label = { Text("Output PDF File Name") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("rotate_output_filename_input")
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("rotate_cancel_btn")
                    ) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = {
                            val uri = selectedUri ?: return@Button
                            // If user hasn't clicked a batch/per-page button yet, apply +90° CW to effectiveTargetPages automatically
                            val finalDeltas: Map<Int, Int> = if (modifiedPagesCount > 0) {
                                pageRotationDeltas.toMap()
                            } else {
                                effectiveTargetPages.associateWith { 90 }
                            }
                            if (finalDeltas.isNotEmpty()) {
                                val cleanOutName = if (outputFileName.endsWith(".pdf", ignoreCase = true)) {
                                    outputFileName
                                } else {
                                    "${outputFileName.ifBlank { "Rotated_Document" }}.pdf"
                                }
                                onRotateConfirmed(
                                    activeLocalFile,
                                    uri,
                                    selectedName.ifBlank { "Document.pdf" },
                                    finalDeltas,
                                    saveMode == RotatePagesSaveMode.SAVE_TO_DOCUMENT_FILE,
                                    cleanOutName
                                )
                            }
                        },
                        enabled = selectedUri != null && (modifiedPagesCount > 0 || effectiveTargetPages.isNotEmpty()),
                        modifier = Modifier.testTag("rotate_confirm_save_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (modifiedPagesCount > 0) {
                                "Save Rotated PDF ($modifiedPagesCount pg)"
                            } else {
                                "Rotate +90° & Save (${effectiveTargetPages.size} pg)"
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RotatePageThumbnailCard(
    pageMeta: PagePreviewMeta,
    isSelected: Boolean,
    rotationDeltaDegrees: Int,
    onToggleSelect: () -> Unit,
    onRotateCcw90: () -> Unit,
    onRotateCw90: () -> Unit
) {
    val pageIdx = pageMeta.pageIndex
    val isRotated = rotationDeltaDegrees != 0
    val borderColor = when {
        isRotated -> MaterialTheme.colorScheme.primary
        isSelected -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val borderWidth = if (isRotated || isSelected) 2.dp else 1.dp

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(borderWidth, borderColor, RoundedCornerShape(10.dp))
            .clickable { onToggleSelect() }
            .testTag("rotate_page_card_$pageIdx"),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Top bar: Page number & Selection icon + Rotation badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = if (isSelected) "Page ${pageIdx + 1} selected" else "Select page ${pageIdx + 1}",
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "P.${pageIdx + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (isRotated) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "+${rotationDeltaDegrees}°",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            // Thumbnail preview with live 90° rotation
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.78f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                val bmp = pageMeta.thumbnail
                if (bmp != null && !bmp.isRecycled) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Page ${pageIdx + 1} preview rotated by ${rotationDeltaDegrees} degrees",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp)
                            .graphicsLayer {
                                rotationZ = rotationDeltaDegrees.toFloat()
                            }
                    )
                } else {
                    Text(
                        text = "Page ${pageIdx + 1}\n(${rotationDeltaDegrees}°)",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.DarkGray,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Per-page 90° CCW & CW quick buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onRotateCcw90,
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("rotate_page_ccw_$pageIdx")
                ) {
                    Icon(
                        imageVector = Icons.Default.RotateLeft,
                        contentDescription = "Rotate page ${pageIdx + 1} counter-clockwise 90 degrees",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "${rotationDeltaDegrees}°",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isRotated) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isRotated) FontWeight.Bold else FontWeight.Normal
                )
                IconButton(
                    onClick = onRotateCw90,
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("rotate_page_cw_$pageIdx")
                ) {
                    Icon(
                        imageVector = Icons.Default.RotateRight,
                        contentDescription = "Rotate page ${pageIdx + 1} clockwise 90 degrees",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

