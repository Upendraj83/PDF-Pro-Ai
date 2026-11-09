package com.example.ui.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.LowPriority
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SaveAs
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.pdf.OrganizedPage
import com.example.pdf.PdfBoxMemoryUtil
import com.example.ui.PdfViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Deterministic state operations for drag-and-drop reordering, rotating, duplicating,
 * and deleting pages in an open PDF document.
 */
object PageOrganizerStateHelper {

    fun reorderPage(
        pages: List<OrganizedPage>,
        fromIndex: Int,
        toIndex: Int
    ): List<OrganizedPage> {
        if (pages.size <= 1) return pages
        if (fromIndex !in pages.indices || toIndex !in pages.indices || fromIndex == toIndex) {
            return pages
        }
        val mutable = pages.toMutableList()
        val moved = mutable.removeAt(fromIndex)
        mutable.add(toIndex, moved)
        return mutable
    }

    fun rotatePageAt(
        pages: List<OrganizedPage>,
        index: Int,
        clockwise: Boolean
    ): List<OrganizedPage> {
        if (index !in pages.indices) return pages
        val delta = if (clockwise) 90 else 270
        return pages.mapIndexed { idx, page ->
            if (idx == index) page.copy(rotation = (page.rotation + delta) % 360) else page
        }
    }

    fun rotatePages(
        pages: List<OrganizedPage>,
        targetIds: Set<String>,
        clockwise: Boolean
    ): List<OrganizedPage> {
        if (pages.isEmpty()) return pages
        val effectiveIds = if (targetIds.isNotEmpty()) targetIds else pages.map { it.id }.toSet()
        val delta = if (clockwise) 90 else 270
        return pages.map { page ->
            if (page.id in effectiveIds) {
                page.copy(rotation = (page.rotation + delta) % 360)
            } else {
                page
            }
        }
    }

    fun deletePageAt(
        pages: List<OrganizedPage>,
        index: Int
    ): List<OrganizedPage> {
        if (pages.size <= 1 || index !in pages.indices) return pages
        val mutable = pages.toMutableList()
        mutable.removeAt(index)
        return mutable
    }

    fun deletePages(
        pages: List<OrganizedPage>,
        targetIds: Set<String>
    ): List<OrganizedPage> {
        if (targetIds.isEmpty() || pages.size <= 1) return pages
        val remaining = pages.filterNot { it.id in targetIds }
        return if (remaining.isNotEmpty()) remaining else listOf(pages.first())
    }

    fun duplicatePages(
        pages: List<OrganizedPage>,
        targetIds: Set<String>
    ): List<OrganizedPage> {
        if (targetIds.isEmpty()) return pages
        val out = mutableListOf<OrganizedPage>()
        for (page in pages) {
            out.add(page)
            if (page.id in targetIds) {
                out.add(page.copy(id = UUID.randomUUID().toString()))
            }
        }
        return out
    }

    fun reversePageOrder(pages: List<OrganizedPage>): List<OrganizedPage> {
        return pages.reversed()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PageOrganizerScreen(
    docUri: Uri,
    docName: String,
    viewModel: PdfViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()

    // Page state and History for Undo / Redo
    var pages by remember { mutableStateOf<List<OrganizedPage>>(emptyList()) }
    var historyUndoStack by remember { mutableStateOf<List<List<OrganizedPage>>>(emptyList()) }
    var historyRedoStack by remember { mutableStateOf<List<List<OrganizedPage>>>(emptyList()) }

    var isInitialLoading by remember { mutableStateOf(true) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isMultiSelectMode by remember { mutableStateOf(false) }

    // Drag-and-Drop interactive state
    var draggedPageId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var preDragSnapshot by remember { mutableStateOf<List<OrganizedPage>?>(null) }

    // Dialog states
    var showInsertBlankDialog by remember { mutableStateOf(false) }
    var showConfirmOverwriteDialog by remember { mutableStateOf(false) }
    var pageIndexForMoveDialog by remember { mutableStateOf<Int?>(null) }
    var tempSavedFilePendingExport by remember { mutableStateOf<File?>(null) }

    val cachedSourceFile = remember(docUri, viewModel.currentViewingUri, viewModel.currentViewingCachedFile) {
        if (docUri == viewModel.currentViewingUri) viewModel.currentViewingCachedFile else null
    }

    // Save as copy SAF launcher
    val createDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destUri ->
        if (destUri != null && tempSavedFilePendingExport != null) {
            viewModel.exportSavedFileToUri(tempSavedFilePendingExport!!, destUri)
            Toast.makeText(context, "Exported organized PDF copy successfully", Toast.LENGTH_SHORT).show()
        }
    }

    // Insert from another PDF launcher
    val insertPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { pickedUri ->
        if (pickedUri != null) {
            coroutineScope.launch {
                val newPages = loadPagesFromUri(context, pickedUri, null)
                if (newPages.isNotEmpty()) {
                    historyUndoStack = historyUndoStack + listOf(pages)
                    historyRedoStack = emptyList()
                    pages = pages + newPages
                    Toast.makeText(context, "Inserted ${newPages.size} pages from document", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Load initial pages from open document
    LaunchedEffect(docUri, cachedSourceFile) {
        withContext(Dispatchers.IO) {
            val loaded = loadPagesFromUri(context, docUri, cachedSourceFile)
            pages = loaded
            isInitialLoading = false
        }
    }

    fun pushHistoryState(newPages: List<OrganizedPage>) {
        if (newPages == pages) return
        historyUndoStack = historyUndoStack + listOf(pages)
        historyRedoStack = emptyList()
        pages = newPages
    }

    fun undo() {
        if (historyUndoStack.isNotEmpty()) {
            val previous = historyUndoStack.last()
            historyUndoStack = historyUndoStack.dropLast(1)
            historyRedoStack = historyRedoStack + listOf(pages)
            pages = previous
        }
    }

    fun redo() {
        if (historyRedoStack.isNotEmpty()) {
            val next = historyRedoStack.last()
            historyRedoStack = historyRedoStack.dropLast(1)
            historyUndoStack = historyUndoStack + listOf(pages)
            pages = next
        }
    }

    // Handle drag-and-drop position updates across the LazyVerticalGrid
    fun onDragMove(pageId: String, delta: Offset) {
        dragOffset += delta
        val currentIndex = pages.indexOfFirst { it.id == pageId }
        if (currentIndex == -1) return

        val layoutInfo = gridState.layoutInfo
        val currentItemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == currentIndex } ?: return

        val draggedCenterX = currentItemInfo.offset.x + currentItemInfo.size.width / 2f + dragOffset.x
        val draggedCenterY = currentItemInfo.offset.y + currentItemInfo.size.height / 2f + dragOffset.y

        // Auto-scroll when dragging near the top or bottom edge of the grid viewport
        val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
        val edgeThreshold = 110f
        if (draggedCenterY < layoutInfo.viewportStartOffset + edgeThreshold) {
            coroutineScope.launch { gridState.scrollBy(-18f) }
        } else if (draggedCenterY > layoutInfo.viewportStartOffset + viewportHeight - edgeThreshold) {
            coroutineScope.launch { gridState.scrollBy(18f) }
        }

        val targetItem: LazyGridItemInfo? = layoutInfo.visibleItemsInfo.firstOrNull { item ->
            item.index != currentIndex &&
                draggedCenterX >= item.offset.x &&
                draggedCenterX <= (item.offset.x + item.size.width) &&
                draggedCenterY >= item.offset.y &&
                draggedCenterY <= (item.offset.y + item.size.height)
        }

        if (targetItem != null && targetItem.index in pages.indices) {
            val oldOffset = currentItemInfo.offset
            val newOffset = targetItem.offset
            pages = PageOrganizerStateHelper.reorderPage(pages, currentIndex, targetItem.index)
            // Compensate dragOffset so the card remains smoothly anchored under the finger
            dragOffset += Offset(
                x = (oldOffset.x - newOffset.x).toFloat(),
                y = (oldOffset.y - newOffset.y).toFloat()
            )
        }
    }

    fun onDragStart(pageId: String) {
        preDragSnapshot = pages
        draggedPageId = pageId
        dragOffset = Offset.Zero
    }

    fun onDragFinish() {
        val snapshot = preDragSnapshot
        if (snapshot != null && snapshot != pages) {
            historyUndoStack = historyUndoStack + listOf(snapshot)
            historyRedoStack = emptyList()
        }
        preDragSnapshot = null
        draggedPageId = null
        dragOffset = Offset.Zero
    }

    BackHandler {
        onClose()
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("page_organizer_screen"),
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Organize Pages",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "$docName • ${pages.size} page(s)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("btn_close_page_organizer")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        // Undo
                        IconButton(
                            onClick = { undo() },
                            enabled = historyUndoStack.isNotEmpty(),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("btn_organizer_undo")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Undo,
                                contentDescription = "Undo",
                                tint = if (historyUndoStack.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        }

                        // Redo
                        IconButton(
                            onClick = { redo() },
                            enabled = historyRedoStack.isNotEmpty(),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("btn_organizer_redo")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Redo,
                                contentDescription = "Redo",
                                tint = if (historyRedoStack.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        }

                        // Save As Copy
                        IconButton(
                            onClick = {
                                viewModel.executeSaveOrganizedPages(docUri, pages) { savedFile ->
                                    tempSavedFilePendingExport = savedFile
                                    val copyName = docName.removeSuffix(".pdf") + "_organized.pdf"
                                    createDocLauncher.launch(copyName)
                                }
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("btn_organizer_save_as_copy")
                        ) {
                            Icon(Icons.Default.SaveAs, contentDescription = "Save as Copy")
                        }

                        // Save & Apply to Open Document
                        Button(
                            onClick = { showConfirmOverwriteDialog = true },
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .heightIn(min = 40.dp)
                                .testTag("btn_organizer_save_overwrite")
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Apply")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                // Instruction & Drag Status Banner
                Surface(
                    color = if (draggedPageId != null) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DragIndicator,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            val draggedIdx = pages.indexOfFirst { it.id == draggedPageId }
                            Text(
                                text = if (draggedIdx >= 0) {
                                    "Dragging Page #${draggedIdx + 1} — drop on any page slot to reorder"
                                } else {
                                    "Drag handle or long-press any page to reorder • Rotate or delete below"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (draggedIdx >= 0) FontWeight.Bold else FontWeight.Medium,
                                color = if (draggedIdx >= 0) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }

                        if (historyUndoStack.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                            ) {
                                Text(
                                    text = "${historyUndoStack.size} edit(s)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            // Sticky Action Bar for Page Operations
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    // Selection & Batch Actions Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FilterChip(
                                selected = isMultiSelectMode,
                                onClick = {
                                    isMultiSelectMode = !isMultiSelectMode
                                    if (!isMultiSelectMode) selectedIds = emptySet()
                                },
                                label = {
                                    Text(
                                        if (isMultiSelectMode) "Selected (${selectedIds.size})"
                                        else if (selectedIds.isNotEmpty()) "1 Selected"
                                        else "Select"
                                    )
                                },
                                leadingIcon = {
                                    if (isMultiSelectMode || selectedIds.isNotEmpty()) {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("chip_organizer_multiselect")
                            )

                            if (isMultiSelectMode) {
                                Spacer(modifier = Modifier.width(4.dp))
                                TextButton(
                                    onClick = { selectedIds = pages.map { it.id }.toSet() },
                                    modifier = Modifier.testTag("btn_organizer_select_all")
                                ) {
                                    Text("All")
                                }
                                if (selectedIds.isNotEmpty()) {
                                    TextButton(
                                        onClick = { selectedIds = emptySet() },
                                        modifier = Modifier.testTag("btn_organizer_clear_selection")
                                    ) {
                                        Text("Clear")
                                    }
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            // Rotate 90 CCW
                            IconButton(
                                onClick = {
                                    pushHistoryState(
                                        PageOrganizerStateHelper.rotatePages(pages, selectedIds, clockwise = false)
                                    )
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("btn_organizer_rotate_ccw")
                            ) {
                                Icon(Icons.Default.RotateLeft, contentDescription = "Rotate 90° Counter-Clockwise")
                            }

                            // Rotate 90 CW
                            IconButton(
                                onClick = {
                                    pushHistoryState(
                                        PageOrganizerStateHelper.rotatePages(pages, selectedIds, clockwise = true)
                                    )
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("btn_organizer_rotate_cw")
                            ) {
                                Icon(Icons.Default.RotateRight, contentDescription = "Rotate 90° Clockwise")
                            }

                            // Reverse Page Order
                            IconButton(
                                onClick = {
                                    if (pages.size > 1) {
                                        pushHistoryState(PageOrganizerStateHelper.reversePageOrder(pages))
                                        Toast.makeText(context, "Reversed page order", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                enabled = pages.size > 1,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("btn_organizer_reverse")
                            ) {
                                Icon(Icons.Default.SwapVert, contentDescription = "Reverse Page Order")
                            }

                            // Duplicate Selected
                            IconButton(
                                onClick = {
                                    if (selectedIds.isNotEmpty()) {
                                        pushHistoryState(PageOrganizerStateHelper.duplicatePages(pages, selectedIds))
                                    }
                                },
                                enabled = selectedIds.isNotEmpty(),
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("btn_organizer_duplicate")
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate selected")
                            }

                            // Delete Selected
                            IconButton(
                                onClick = {
                                    if (selectedIds.isNotEmpty() && pages.size > 1) {
                                        val nextPages = PageOrganizerStateHelper.deletePages(pages, selectedIds)
                                        pushHistoryState(nextPages)
                                        selectedIds = emptySet()
                                    }
                                },
                                enabled = selectedIds.isNotEmpty() && pages.size > 1 && pages.size > selectedIds.size,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("btn_organizer_delete")
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete selected",
                                    tint = if (selectedIds.isNotEmpty() && pages.size > selectedIds.size) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.outline
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Insertion row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showInsertBlankDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("btn_organizer_insert_blank")
                        ) {
                            Icon(Icons.Default.NoteAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("+ Blank Page")
                        }

                        FilledTonalButton(
                            onClick = { insertPdfLauncher.launch(arrayOf("application/pdf")) },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("btn_organizer_insert_pdf")
                        ) {
                            Icon(Icons.Default.PostAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("+ From PDF")
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        if (isInitialLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.testTag("organizer_loading_spinner"))
            }
        } else {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = 152.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .testTag("organizer_pages_grid"),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                itemsIndexed(pages, key = { _, item -> item.id }) { index, page ->
                    val isSelected = page.id in selectedIds
                    val isDragging = draggedPageId == page.id
                    val currentOnDragStart by rememberUpdatedState(newValue = { onDragStart(page.id) })
                    val currentOnDragMove by rememberUpdatedState(newValue = { delta: Offset -> onDragMove(page.id, delta) })
                    val currentOnDragFinish by rememberUpdatedState(newValue = { onDragFinish() })

                    OrganizedPageThumbnailCard(
                        page = page,
                        pageDisplayIndex = index + 1,
                        totalPages = pages.size,
                        isSelected = isSelected,
                        isDragging = isDragging,
                        dragOffset = if (isDragging) dragOffset else Offset.Zero,
                        canMoveLeft = index > 0,
                        canMoveRight = index < pages.size - 1,
                        canDelete = pages.size > 1,
                        viewModel = viewModel,
                        cachedSourceFile = cachedSourceFile,
                        onCardClick = {
                            if (isMultiSelectMode) {
                                selectedIds = if (isSelected) selectedIds - page.id else selectedIds + page.id
                            } else {
                                selectedIds = if (isSelected) emptySet() else setOf(page.id)
                            }
                        },
                        onOpenMoveToDialog = {
                            pageIndexForMoveDialog = index
                        },
                        onDragStart = { currentOnDragStart() },
                        onDragMove = { delta -> currentOnDragMove(delta) },
                        onDragEnd = { currentOnDragFinish() },
                        onRotateCcw = {
                            pushHistoryState(
                                PageOrganizerStateHelper.rotatePageAt(pages, index, clockwise = false)
                            )
                        },
                        onRotateCw = {
                            pushHistoryState(
                                PageOrganizerStateHelper.rotatePageAt(pages, index, clockwise = true)
                            )
                        },
                        onMoveLeft = {
                            if (index > 0) {
                                pushHistoryState(
                                    PageOrganizerStateHelper.reorderPage(pages, index, index - 1)
                                )
                            }
                        },
                        onMoveRight = {
                            if (index < pages.size - 1) {
                                pushHistoryState(
                                    PageOrganizerStateHelper.reorderPage(pages, index, index + 1)
                                )
                            }
                        },
                        onDelete = {
                            if (pages.size > 1) {
                                selectedIds = selectedIds - page.id
                                pushHistoryState(
                                    PageOrganizerStateHelper.deletePageAt(pages, index)
                                )
                            }
                        },
                        modifier = Modifier
                            .zIndex(if (isDragging) 10f else 0f)
                            .then(if (isDragging) Modifier else Modifier.animateItem())
                    )
                }
            }
        }
    }

    // Move Page to Specific Position Dialog
    pageIndexForMoveDialog?.let { fromIndex ->
        if (fromIndex in pages.indices) {
            MovePageToPositionDialog(
                currentPageNumber = fromIndex + 1,
                totalPages = pages.size,
                onMoveConfirmed = { targetPageNumber ->
                    val toIndex = (targetPageNumber - 1).coerceIn(0, pages.lastIndex.coerceAtLeast(0))
                    if (toIndex != fromIndex && pages.isNotEmpty()) {
                        pushHistoryState(
                            PageOrganizerStateHelper.reorderPage(pages, fromIndex, toIndex)
                        )
                    }
                    pageIndexForMoveDialog = null
                },
                onDismiss = { pageIndexForMoveDialog = null }
            )
        } else {
            pageIndexForMoveDialog = null
        }
    }

    // Insert Blank Page Dialog
    if (showInsertBlankDialog) {
        InsertBlankPageDialog(
            totalPages = pages.size,
            onInsert = { atIndex ->
                val list = pages.toMutableList()
                val blank = OrganizedPage(
                    id = UUID.randomUUID().toString(),
                    isBlank = true
                )
                val safeIndex = atIndex.coerceIn(0, list.size)
                list.add(safeIndex, blank)
                pushHistoryState(list)
                showInsertBlankDialog = false
            },
            onDismiss = { showInsertBlankDialog = false }
        )
    }

    // Apply & Save Confirmation Dialog
    if (showConfirmOverwriteDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmOverwriteDialog = false },
            title = { Text("Apply Page Changes to $docName?") },
            text = {
                Text(
                    "This will apply your reordered, rotated, and deleted page layout (${pages.size} pages) to the open PDF document."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmOverwriteDialog = false
                        viewModel.executeSaveOrganizedPages(docUri, pages) { savedFile ->
                            viewModel.overwriteOriginalWithSavedFile(savedFile)
                            Toast.makeText(context, "Updated $docName (${pages.size} pages)", Toast.LENGTH_SHORT).show()
                            onClose()
                        }
                    },
                    modifier = Modifier.testTag("btn_confirm_apply_organized_pages")
                ) {
                    Text("Apply & Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmOverwriteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun OrganizedPageThumbnailCard(
    page: OrganizedPage,
    pageDisplayIndex: Int,
    totalPages: Int,
    isSelected: Boolean,
    isDragging: Boolean,
    dragOffset: Offset,
    canMoveLeft: Boolean,
    canMoveRight: Boolean,
    canDelete: Boolean,
    viewModel: PdfViewModel,
    cachedSourceFile: File?,
    onCardClick: () -> Unit,
    onOpenMoveToDialog: () -> Unit,
    onDragStart: () -> Unit,
    onDragMove: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onRotateCcw: () -> Unit,
    onRotateCw: () -> Unit,
    onMoveLeft: () -> Unit,
    onMoveRight: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val animatedRotation by animateFloatAsState(
        targetValue = page.rotation.toFloat(),
        label = "page_rotation_anim"
    )
    val animatedScale by animateFloatAsState(
        targetValue = if (isDragging) 1.06f else 1f,
        label = "drag_scale_anim"
    )

    var thumbnailBitmap by remember(page.sourceUri, page.sourcePageIndex, page.isBlank) {
        mutableStateOf<Bitmap?>(null)
    }

    LaunchedEffect(page.sourceUri, page.sourcePageIndex, page.isBlank) {
        if (!page.isBlank && page.sourceUri != null) {
            withContext(Dispatchers.IO) {
                // Fast path if page belongs to the currently open document in ViewModel
                val fromVm = if (page.sourceUri == viewModel.currentViewingUri) {
                    viewModel.getThumbnailBitmap(page.sourcePageIndex)
                } else {
                    null
                }
                thumbnailBitmap = fromVm ?: renderThumbnail(
                    context = context,
                    uri = page.sourceUri,
                    cachedFile = cachedSourceFile,
                    pageIndex = page.sourcePageIndex
                )
            }
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationX = dragOffset.x
                translationY = dragOffset.y
                scaleX = animatedScale
                scaleY = animatedScale
            }
            .shadow(
                elevation = if (isDragging) 16.dp else if (isSelected) 4.dp else 1.dp,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable { onCardClick() }
            .pointerInput(page.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { onDragStart() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDragMove(dragAmount)
                    },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() }
                )
            }
            .border(
                width = when {
                    isDragging -> 3.dp
                    isSelected -> 2.5.dp
                    else -> 1.dp
                },
                color = when {
                    isDragging -> MaterialTheme.colorScheme.primary
                    isSelected -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.outlineVariant
                },
                shape = RoundedCornerShape(14.dp)
            )
            .testTag("organizer_page_card_$pageDisplayIndex"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isDragging -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Row: Page Number Badge (tap to jump-move) + Original Page Indicator + Drag Handle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isSelected || isDragging) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        },
                        modifier = Modifier
                            .clickable(enabled = totalPages > 1) { onOpenMoveToDialog() }
                            .testTag("organizer_page_badge_$pageDisplayIndex")
                    ) {
                        Text(
                            text = "#$pageDisplayIndex",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected || isDragging) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            },
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }

                    if (!page.isBlank && page.sourcePageIndex + 1 != pageDisplayIndex) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Text(
                                text = "Orig ${page.sourcePageIndex + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Selected",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Dedicated 1-finger Drag Handle Pill
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f))
                            .pointerInput(page.id) {
                                detectDragGestures(
                                    onDragStart = { onDragStart() },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        onDragMove(dragAmount)
                                    },
                                    onDragEnd = { onDragEnd() },
                                    onDragCancel = { onDragEnd() }
                                )
                            }
                            .testTag("organizer_drag_handle_$pageDisplayIndex"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DragIndicator,
                            contentDescription = "Drag to reorder page $pageDisplayIndex",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Thumbnail Preview Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.707f) // A4 ratio
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .border(0.5.dp, Color.LightGray, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (page.isBlank) {
                    Text(
                        text = "BLANK PAGE",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray,
                        fontWeight = FontWeight.Bold
                    )
                } else if (thumbnailBitmap != null) {
                    Image(
                        bitmap = thumbnailBitmap!!.asImageBitmap(),
                        contentDescription = "Page $pageDisplayIndex",
                        modifier = Modifier
                            .fillMaxSize()
                            .rotate(animatedRotation),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "P.${page.sourcePageIndex + 1}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.DarkGray,
                            modifier = Modifier.rotate(animatedRotation)
                        )
                    }
                }

                if (page.rotation != 0) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color.Black.copy(alpha = 0.75f),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                    ) {
                        Text(
                            text = "${page.rotation}°",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Per-Page Quick Action Row: Move Left | Rotate CCW | Rotate CW | Move Right | Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onMoveLeft,
                    enabled = canMoveLeft,
                    modifier = Modifier
                        .size(30.dp)
                        .testTag("btn_card_move_left_$pageDisplayIndex")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Move Earlier",
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = onRotateCcw,
                    modifier = Modifier
                        .size(30.dp)
                        .testTag("btn_card_rotate_ccw_$pageDisplayIndex")
                ) {
                    Icon(
                        Icons.Default.RotateLeft,
                        contentDescription = "Rotate 90° Counter-Clockwise",
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = onRotateCw,
                    modifier = Modifier
                        .size(30.dp)
                        .testTag("btn_card_rotate_cw_$pageDisplayIndex")
                ) {
                    Icon(
                        Icons.Default.RotateRight,
                        contentDescription = "Rotate 90° Clockwise",
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = onMoveRight,
                    enabled = canMoveRight,
                    modifier = Modifier
                        .size(30.dp)
                        .testTag("btn_card_move_right_$pageDisplayIndex")
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Move Later",
                        modifier = Modifier.size(16.dp)
                    )
                }

                IconButton(
                    onClick = onDelete,
                    enabled = canDelete,
                    modifier = Modifier
                        .size(30.dp)
                        .testTag("btn_card_delete_$pageDisplayIndex")
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete Page $pageDisplayIndex",
                        tint = if (canDelete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MovePageToPositionDialog(
    currentPageNumber: Int,
    totalPages: Int,
    onMoveConfirmed: (targetPageNumber: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var targetInput by remember { mutableStateOf(currentPageNumber.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.LowPriority,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text("Move Page #$currentPageNumber") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Enter the new page position (1 to $totalPages):",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = targetInput,
                    onValueChange = { value ->
                        targetInput = value.filter { it.isDigit() }
                    },
                    label = { Text("Target position (1–$totalPages)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_move_page_position")
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = targetInput == "1",
                        onClick = { targetInput = "1" },
                        label = { Text("Move to First (1)") }
                    )
                    FilterChip(
                        selected = targetInput == totalPages.toString(),
                        onClick = { targetInput = totalPages.toString() },
                        label = { Text("Move to Last ($totalPages)") }
                    )
                }
            }
        },
        confirmButton = {
            val parsed = targetInput.toIntOrNull()
            Button(
                onClick = {
                    if (parsed != null) {
                        onMoveConfirmed(parsed.coerceIn(1, totalPages.coerceAtLeast(1)))
                    }
                },
                enabled = parsed != null && parsed in 1..totalPages,
                modifier = Modifier.testTag("btn_confirm_move_page")
            ) {
                Text("Move Page")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun InsertBlankPageDialog(
    totalPages: Int,
    onInsert: (index: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var insertPosition by remember { mutableStateOf("end") } // "start", "end"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Insert Blank Page") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Select where to insert the new blank page:", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = insertPosition == "start",
                        onClick = { insertPosition = "start" },
                        label = { Text("At Beginning") }
                    )
                    FilterChip(
                        selected = insertPosition == "end",
                        onClick = { insertPosition = "end" },
                        label = { Text("At End") }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val idx = if (insertPosition == "start") 0 else totalPages
                    onInsert(idx)
                }
            ) {
                Text("Insert")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun renderThumbnail(
    context: Context,
    uri: Uri,
    cachedFile: File?,
    pageIndex: Int
): Bitmap? {
    try {
        val pfd: ParcelFileDescriptor? = when {
            cachedFile != null && cachedFile.exists() ->
                ParcelFileDescriptor.open(cachedFile, ParcelFileDescriptor.MODE_READ_ONLY)
            uri.scheme == "file" && uri.path != null ->
                ParcelFileDescriptor.open(File(uri.path!!), ParcelFileDescriptor.MODE_READ_ONLY)
            else -> context.contentResolver.openFileDescriptor(uri, "r")
        }
        pfd?.use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                if (pageIndex in 0 until renderer.pageCount) {
                    val page = renderer.openPage(pageIndex)
                    val width = 200
                    val height = (page.height.toFloat() / page.width.toFloat() * width).toInt().coerceAtLeast(100)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    return bitmap
                }
            }
        }
    } catch (_: Exception) {}
    return null
}

internal fun loadPagesFromUri(
    context: Context,
    uri: Uri,
    cachedFile: File? = null
): List<OrganizedPage> {
    val list = mutableListOf<OrganizedPage>()
    try {
        val pfd: ParcelFileDescriptor? = when {
            cachedFile != null && cachedFile.exists() ->
                ParcelFileDescriptor.open(cachedFile, ParcelFileDescriptor.MODE_READ_ONLY)
            uri.scheme == "file" && uri.path != null ->
                ParcelFileDescriptor.open(File(uri.path!!), ParcelFileDescriptor.MODE_READ_ONLY)
            else -> context.contentResolver.openFileDescriptor(uri, "r")
        }
        pfd?.use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                for (i in 0 until renderer.pageCount) {
                    list.add(
                        OrganizedPage(
                            sourceUri = uri,
                            sourcePageIndex = i
                        )
                    )
                }
            }
        }
    } catch (_: Exception) {}

    // Fallback to PDFBox page count if PdfRenderer is unavailable or returns 0 (e.g. JVM / Robolectric)
    if (list.isEmpty()) {
        try {
            val doc = PdfBoxMemoryUtil.loadDocument(context, cachedFile, uri)
            if (doc != null) {
                doc.use { pdDoc ->
                    for (i in 0 until pdDoc.numberOfPages) {
                        list.add(
                            OrganizedPage(
                                sourceUri = uri,
                                sourcePageIndex = i
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {}
    }
    return list
}
