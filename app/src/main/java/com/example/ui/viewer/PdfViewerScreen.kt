package com.example.ui.viewer

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Brightness7
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Pin
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.ViewSidebar
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import com.example.pdf.OperationProgress
import com.example.ui.tools.CompressPdfDialog
import com.example.ui.tools.OperationFailureDialog
import com.example.ui.tools.OperationProgressDialog
import com.example.ui.tools.OperationResultDialog
import com.example.ui.tools.PdfCropDialog
import com.example.ui.tools.PdfSecurityDialogMode
import com.example.ui.tools.PdfToWordOfficeDialog
import com.example.ui.tools.ProtectPdfDialog
import com.example.ui.tools.MetadataEditorDialog
import com.example.ui.tools.PrintPdfDialog
import com.example.ui.tools.RotatePdfPagesDialog
import com.example.ui.tools.UnlockPdfDialog
import com.example.ui.tools.WatermarkDialog
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Checkbox
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.WarningAmber
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai.AiToolTab
import com.example.data.ReadingMode
import com.example.ui.ai.AiConsentDialog
import com.example.ui.ai.AiPasswordProtectedConfirmDialog
import com.example.ui.ai.PdfAiAssistantSheet
import com.example.pdf.AnnotationType
import com.example.pdf.BundledEditFont
import com.example.pdf.ContentEditElement
import com.example.pdf.ContentEditTool
import com.example.pdf.ExistingTextLine
import com.example.pdf.NormalizedPoint
import com.example.pdf.NormalizedRect
import com.example.pdf.PdfAnnotation
import com.example.pdf.PdfLinkType
import com.example.pdf.FormFieldItem
import com.example.pdf.FormFieldType
import com.example.pdf.PlacedStampItem
import com.example.pdf.PlacedStampType
import com.example.ui.DocumentSection
import com.example.ui.PdfViewModel
import com.example.ui.ThumbnailFilterMode
import com.example.ui.ThumbnailNavigatorHelper
import com.example.ui.ViewerState
import com.example.ui.common.ShareToCloudDriveDialog
import com.example.ui.common.sharePdfToCloudOrApps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    viewModel: PdfViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val viewerState by viewModel.viewerState.collectAsStateWithLifecycle()
    val isNightMode by viewModel.isNightMode.collectAsStateWithLifecycle()
    val isThumbnailSheetOpen by viewModel.isThumbnailSheetOpen.collectAsStateWithLifecycle()
    val isThumbnailBottomDrawerOpen by viewModel.isThumbnailBottomDrawerOpen.collectAsStateWithLifecycle()
    val isJumpDialogOpen by viewModel.isJumpDialogOpen.collectAsStateWithLifecycle()
    val isOutlineSheetOpen by viewModel.isOutlineSheetOpen.collectAsStateWithLifecycle()
    val readingMode by viewModel.readingMode.collectAsStateWithLifecycle()
    val isFullScreen by viewModel.isFullScreen.collectAsStateWithLifecycle()
    val isSearchVisible by viewModel.isSearchVisible.collectAsStateWithLifecycle()
    val isTtsBarVisible by viewModel.isTtsBarVisible.collectAsStateWithLifecycle()
    val isAutoScrollActive by viewModel.isAutoScrollActive.collectAsStateWithLifecycle()
    val autoScrollSpeed by viewModel.autoScrollSpeed.collectAsStateWithLifecycle()
    val currentPage by viewModel.currentPage.collectAsStateWithLifecycle()

    // Annotation states
    val isAnnotationToolbarVisible by viewModel.isAnnotationToolbarVisible.collectAsStateWithLifecycle()
    val isAnnotationsListOpen by viewModel.isAnnotationsListOpen.collectAsStateWithLifecycle()
    val isSaveDialogVisible by viewModel.isSaveDialogVisible.collectAsStateWithLifecycle()
    val saveProgress by viewModel.saveProgress.collectAsStateWithLifecycle()
    val activeAnnotationTool by viewModel.activeAnnotationTool.collectAsStateWithLifecycle()
    val activeAnnotationColor by viewModel.activeAnnotationColor.collectAsStateWithLifecycle()
    val activeAnnotationThickness by viewModel.activeAnnotationThickness.collectAsStateWithLifecycle()
    val selectedAnnotationId by viewModel.selectedAnnotationId.collectAsStateWithLifecycle()
    val allAnnotations by viewModel.allAnnotations.collectAsStateWithLifecycle()

    var showReadingModeDialog by remember { mutableStateOf(false) }
    var cloudShareTarget by remember { mutableStateOf<Pair<Uri, String>?>(null) }

    // SAF CreateDocument launcher for "Save as copy" (Annotations)
    var tempFilePendingExport by remember { mutableStateOf<File?>(null) }
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { targetUri: Uri? ->
        val file = tempFilePendingExport
        if (targetUri != null && file != null) {
            viewModel.exportSavedFileToUri(file, targetUri)
            Toast.makeText(context, "Saved copy to device", Toast.LENGTH_SHORT).show()
        }
        tempFilePendingExport = null
    }

    // SAF CreateDocument launcher for "Save as copy" (Form Filling & Signatures)
    var formTempFilePendingExport by remember { mutableStateOf<File?>(null) }
    val createFormDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { targetUri: Uri? ->
        val file = formTempFilePendingExport
        if (targetUri != null && file != null) {
            viewModel.exportSavedFormFileToUri(file, targetUri)
            Toast.makeText(context, "Saved signed copy to device", Toast.LENGTH_SHORT).show()
        }
        formTempFilePendingExport = null
    }

    // SAF CreateDocument launcher for "Save as copy" (Content Edits)
    var contentEditTempFilePendingExport by remember { mutableStateOf<File?>(null) }
    val createContentEditDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { targetUri: Uri? ->
        val file = contentEditTempFilePendingExport
        if (targetUri != null && file != null) {
            viewModel.exportSavedFileToUri(file, targetUri)
            Toast.makeText(context, "Saved edited PDF copy to device", Toast.LENGTH_SHORT).show()
        }
        contentEditTempFilePendingExport = null
    }

    BackHandler {
        if (isFullScreen) {
            viewModel.setFullScreen(false)
        } else if (viewModel.isOcrSelectionModeActive.value) {
            viewModel.setOcrSelectionModeActive(false)
        } else if (viewModel.isContentEditActive.value) {
            viewModel.setContentEditActive(false)
        } else if (viewModel.isFillAndSignActive.value) {
            viewModel.setFillAndSignActive(false)
        } else if (isAnnotationToolbarVisible) {
            viewModel.toggleAnnotationToolbar()
        } else if (isSearchVisible) {
            viewModel.closeSearch()
        } else if (isTtsBarVisible) {
            viewModel.closeTts()
        } else if (isThumbnailBottomDrawerOpen) {
            viewModel.setThumbnailBottomDrawerOpen(false)
        } else {
            viewModel.closeViewer()
        }
    }

    when (val state = viewerState) {
        is ViewerState.Idle -> {}
        is ViewerState.Loading -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("viewer_loading_spinner"),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Opening document...",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }
        is ViewerState.PasswordRequired -> {
            PasswordRequiredDialog(
                uri = state.uri,
                fileName = state.fileName,
                viewModel = viewModel,
                onUnlock = { password, removePerm ->
                    viewModel.unlockCurrentPdf(password, removePerm)
                },
                onDismiss = { viewModel.closeViewer() }
            )
        }
        is ViewerState.Error -> {
            ViewerErrorView(
                message = state.message,
                onBack = { viewModel.closeViewer() },
                modifier = modifier
            )
        }
        is ViewerState.Viewing -> {
            ViewingContent(
                state = state,
                viewModel = viewModel,
                isNightMode = isNightMode,
                readingMode = readingMode,
                isFullScreen = isFullScreen,
                isSearchVisible = isSearchVisible,
                isTtsBarVisible = isTtsBarVisible,
                isAutoScrollActive = isAutoScrollActive,
                autoScrollSpeed = autoScrollSpeed,
                currentPage = currentPage,
                isAnnotationToolbarVisible = isAnnotationToolbarVisible,
                activeAnnotationTool = activeAnnotationTool,
                activeAnnotationColor = activeAnnotationColor,
                activeAnnotationThickness = activeAnnotationThickness,
                selectedAnnotationId = selectedAnnotationId,
                allAnnotations = allAnnotations,
                isThumbnailSheetOpen = isThumbnailSheetOpen,
                isJumpDialogOpen = isJumpDialogOpen,
                isOutlineSheetOpen = isOutlineSheetOpen,
                isAnnotationsListOpen = isAnnotationsListOpen,
                isSaveDialogVisible = isSaveDialogVisible,
                onBack = { viewModel.closeViewer() },
                onShare = { cloudShareTarget = state.uri to state.fileName },
                onOpenReadingModeDialog = { showReadingModeDialog = true },
                onSaveAsCopyRequested = {
                    viewModel.saveDocumentWithAnnotations(flatten = false) { savedFile ->
                        tempFilePendingExport = savedFile
                        val nameWithoutExt = state.fileName.removeSuffix(".pdf")
                        createDocumentLauncher.launch("${nameWithoutExt}_annotated.pdf")
                    }
                },
                onSaveOverwriteRequested = {
                    viewModel.saveDocumentWithAnnotations(flatten = false) { savedFile ->
                        viewModel.overwriteOriginalWithSavedFile(savedFile)
                        Toast.makeText(context, "Saved changes to ${state.fileName}", Toast.LENGTH_SHORT).show()
                    }
                },
                onSaveFlattenedRequested = {
                    viewModel.saveDocumentWithAnnotations(flatten = true) { savedFile ->
                        tempFilePendingExport = savedFile
                        val nameWithoutExt = state.fileName.removeSuffix(".pdf")
                        createDocumentLauncher.launch("${nameWithoutExt}_flattened.pdf")
                    }
                },
                onSaveFormAsCopyRequested = { flatten ->
                    viewModel.saveFormAndStamps(flatten = flatten) { savedFile ->
                        formTempFilePendingExport = savedFile
                        val nameWithoutExt = state.fileName.removeSuffix(".pdf")
                        createFormDocumentLauncher.launch("${nameWithoutExt}_signed.pdf")
                    }
                },
                onSaveFormOverwriteRequested = { flatten ->
                    viewModel.saveFormAndStamps(flatten = flatten) { savedFile ->
                        viewModel.overwriteOriginalWithSavedFormFile(savedFile)
                        Toast.makeText(context, "Saved form changes to ${state.fileName}", Toast.LENGTH_SHORT).show()
                    }
                },
                onSaveContentEditsAsCopyRequested = {
                    viewModel.saveContentEdits { savedFile ->
                        contentEditTempFilePendingExport = savedFile
                        val nameWithoutExt = state.fileName.removeSuffix(".pdf")
                        createContentEditDocumentLauncher.launch("${nameWithoutExt}_edited.pdf")
                    }
                },
                onSaveContentEditsOverwriteRequested = {
                    viewModel.saveContentEdits { savedFile ->
                        viewModel.overwriteOriginalWithSavedFile(savedFile)
                        viewModel.reloadEditedPdfInViewer(savedFile)
                        Toast.makeText(context, "Applied content edits to ${state.fileName}", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = modifier
            )
        }
    }

    if (showReadingModeDialog) {
        ReadingModeSelectorDialog(
            currentMode = readingMode,
            isFullScreen = isFullScreen,
            onSelectMode = { viewModel.setReadingMode(it) },
            onToggleFullScreen = { viewModel.toggleFullScreen() },
            onDismiss = { showReadingModeDialog = false }
        )
    }

    cloudShareTarget?.let { (targetUri, targetName) ->
        ShareToCloudDriveDialog(
            pdfUri = targetUri,
            fileName = targetName,
            onDismiss = { cloudShareTarget = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewingContent(
    state: ViewerState.Viewing,
    viewModel: PdfViewModel,
    isNightMode: Boolean,
    readingMode: ReadingMode,
    isFullScreen: Boolean,
    isSearchVisible: Boolean,
    isTtsBarVisible: Boolean,
    isAutoScrollActive: Boolean,
    autoScrollSpeed: Float,
    currentPage: Int,
    isAnnotationToolbarVisible: Boolean,
    activeAnnotationTool: AnnotationType?,
    activeAnnotationColor: Int,
    activeAnnotationThickness: Float,
    selectedAnnotationId: String?,
    allAnnotations: List<PdfAnnotation>,
    isThumbnailSheetOpen: Boolean,
    isJumpDialogOpen: Boolean,
    isOutlineSheetOpen: Boolean,
    isAnnotationsListOpen: Boolean,
    isSaveDialogVisible: Boolean,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onOpenReadingModeDialog: () -> Unit,
    onSaveAsCopyRequested: () -> Unit,
    onSaveOverwriteRequested: () -> Unit,
    onSaveFlattenedRequested: () -> Unit,
    onSaveFormAsCopyRequested: (flatten: Boolean) -> Unit,
    onSaveFormOverwriteRequested: (flatten: Boolean) -> Unit,
    onSaveContentEditsAsCopyRequested: () -> Unit,
    onSaveContentEditsOverwriteRequested: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val isFillAndSignActive by viewModel.isFillAndSignActive.collectAsStateWithLifecycle()
    val formFields by viewModel.formFields.collectAsStateWithLifecycle()
    val focusedFieldId by viewModel.focusedFieldId.collectAsStateWithLifecycle()
    val activeQuickFillType by viewModel.activeQuickFillType.collectAsStateWithLifecycle()
    val isSignaturePickerOpen by viewModel.isSignaturePickerOpen.collectAsStateWithLifecycle()
    val isSignatureCreationOpen by viewModel.isSignatureCreationOpen.collectAsStateWithLifecycle()
    val isAddFieldDialogOpen by viewModel.isAddFieldDialogOpen.collectAsStateWithLifecycle()
    val isSaveFormDialogOpen by viewModel.isSaveFormDialogOpen.collectAsStateWithLifecycle()
    val isSavingForm by viewModel.isSavingForm.collectAsStateWithLifecycle()
    val saveFormCompletedMessage by viewModel.saveFormCompletedMessage.collectAsStateWithLifecycle()
    val appSettings by viewModel.appSettings.collectAsStateWithLifecycle()
    val protectedDocs by viewModel.biometricProtectedUris.collectAsStateWithLifecycle()
    val isThumbnailSidebarOpen by viewModel.isThumbnailSidebarOpen.collectAsStateWithLifecycle()
    val isThumbnailBottomDrawerOpen by viewModel.isThumbnailBottomDrawerOpen.collectAsStateWithLifecycle()
    val thumbnailFilterMode by viewModel.thumbnailFilterMode.collectAsStateWithLifecycle()
    val selectedSectionId by viewModel.selectedSectionId.collectAsStateWithLifecycle()
    val outlineItems by viewModel.outlineItems.collectAsStateWithLifecycle()
    val currentBookmarks by viewModel.currentBookmarks.collectAsStateWithLifecycle()
    val bookmarkedPageSet = remember(currentBookmarks) { currentBookmarks.map { it.pageNumber }.toSet() }
    val annotatedPageCounts = remember(allAnnotations) {
        allAnnotations.groupingBy { it.pageIndex }.eachCount()
    }
    val documentSections = remember(state.pageCount, outlineItems) {
        ThumbnailNavigatorHelper.buildDocumentSections(
            totalPages = state.pageCount,
            outlineItems = outlineItems
        )
    }
    val activeSection = remember(documentSections, currentPage) {
        ThumbnailNavigatorHelper.findActiveSection(documentSections, currentPage)
    }

    // Content Editing States
    val isContentEditActive by viewModel.isContentEditActive.collectAsStateWithLifecycle()
    val activeContentTool by viewModel.activeContentTool.collectAsStateWithLifecycle()
    val contentEdits by viewModel.contentEdits.collectAsStateWithLifecycle()
    val selectedContentElementId by viewModel.selectedContentElementId.collectAsStateWithLifecycle()
    val clipboardContentElement by viewModel.clipboardContentElement.collectAsStateWithLifecycle()
    val pageExistingTextLines by viewModel.pageExistingTextLines.collectAsStateWithLifecycle()
    val missingFontWarningMessage by viewModel.missingFontWarningMessage.collectAsStateWithLifecycle()
    val canUndoContentEdit by viewModel.canUndoContentEdit.collectAsStateWithLifecycle()
    val canRedoContentEdit by viewModel.canRedoContentEdit.collectAsStateWithLifecycle()
    val isSaveContentEditsDialogOpen by viewModel.isSaveContentEditsDialogOpen.collectAsStateWithLifecycle()
    val isSavingContentEdits by viewModel.isSavingContentEdits.collectAsStateWithLifecycle()
    val contentEditProgressMessage by viewModel.contentEditProgressMessage.collectAsStateWithLifecycle()

    // Gemini AI States
    val isAiSheetOpen by viewModel.isAiSheetOpen.collectAsStateWithLifecycle()
    val isAiConsentDialogOpen by viewModel.isAiConsentDialogOpen.collectAsStateWithLifecycle()
    val isAiPasswordConfirmDialogOpen by viewModel.isAiPasswordConfirmDialogOpen.collectAsStateWithLifecycle()

    // ML Kit OCR Selectable Text States
    val isOcrSelectionModeActive by viewModel.isOcrSelectionModeActive.collectAsStateWithLifecycle()
    val isOcrSheetOpen by viewModel.isOcrSheetOpen.collectAsStateWithLifecycle()
    val pageOcrResults by viewModel.pageOcrResults.collectAsStateWithLifecycle()
    val isOcrRunningForPage by viewModel.isOcrRunningForPage.collectAsStateWithLifecycle()
    val isCurrentPageScannedWithoutText by viewModel.isCurrentPageScannedWithoutText.collectAsStateWithLifecycle()
    val selectedOcrLineTexts by viewModel.selectedOcrLineTexts.collectAsStateWithLifecycle()

    // Content Edit Dialog States
    var pendingAddTextPoint by remember { mutableStateOf<Pair<Int, NormalizedPoint>?>(null) }
    var editingAddedTextElement by remember { mutableStateOf<ContentEditElement.AddedText?>(null) }
    var pendingEditExistingLine by remember { mutableStateOf<ExistingTextLine?>(null) }
    var editingReplacedTextElement by remember { mutableStateOf<ContentEditElement.ReplacedText?>(null) }
    var editingImageElement by remember { mutableStateOf<ContentEditElement.ImageObject?>(null) }
    var pendingAddLinkRect by remember { mutableStateOf<Pair<Int, NormalizedRect>?>(null) }
    var editingLinkElement by remember { mutableStateOf<ContentEditElement.LinkArea?>(null) }
    var replacingImageElementId by remember { mutableStateOf<String?>(null) }

    // Gallery Image Picker for Content Edit (Add or Replace Image)
    val contentImageGalleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val imgFile = withContext(Dispatchers.IO) {
                    try {
                        val dest = File(context.cacheDir, "content_img_${System.currentTimeMillis()}.jpg")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            FileOutputStream(dest).use { output -> input.copyTo(output) }
                        }
                        if (dest.exists() && dest.length() > 0L) dest else null
                    } catch (_: Exception) {
                        null
                    }
                }
                if (imgFile != null) {
                    val replaceId = replacingImageElementId
                    if (replaceId != null) {
                        val existing = contentEdits.filterIsInstance<ContentEditElement.ImageObject>()
                            .firstOrNull { it.id == replaceId }
                        if (existing != null) {
                            viewModel.updateContentEditElement(existing.copy(imageFile = imgFile))
                        }
                        replacingImageElementId = null
                        editingImageElement = null
                    } else {
                        viewModel.addContentEditElement(
                            ContentEditElement.ImageObject(
                                pageIndex = currentPage,
                                bounds = NormalizedRect(0.22f, 0.25f, 0.72f, 0.55f),
                                imageFile = imgFile
                            )
                        )
                    }
                    viewModel.setActiveContentTool(ContentEditTool.SELECT)
                }
            }
        }
    }

    fun addOrReplaceImageFromCamera() {
        coroutineScope.launch {
            val generatedFile = withContext(Dispatchers.IO) {
                val bmp = Bitmap.createBitmap(640, 420, Bitmap.Config.ARGB_8888)
                val c = android.graphics.Canvas(bmp)
                c.drawColor(android.graphics.Color.rgb(230, 242, 255))
                val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.rgb(21, 101, 192)
                    textSize = 36f
                    isFakeBoldText = true
                }
                c.drawText("Captured Camera Stamp / Image", 48f, 190f, p)
                c.drawText("PDF ALL IN ONE Pro+Ai", 48f, 250f, p)
                val dest = File(context.cacheDir, "cam_img_${System.currentTimeMillis()}.jpg")
                FileOutputStream(dest).use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                bmp.recycle()
                dest
            }
            val replaceId = replacingImageElementId
            if (replaceId != null) {
                val existing = contentEdits.filterIsInstance<ContentEditElement.ImageObject>()
                    .firstOrNull { it.id == replaceId }
                if (existing != null) {
                    viewModel.updateContentEditElement(existing.copy(imageFile = generatedFile))
                }
                replacingImageElementId = null
                editingImageElement = null
            } else {
                viewModel.addContentEditElement(
                    ContentEditElement.ImageObject(
                        pageIndex = currentPage,
                        bounds = NormalizedRect(0.24f, 0.28f, 0.74f, 0.52f),
                        imageFile = generatedFile
                    )
                )
            }
            viewModel.setActiveContentTool(ContentEditTool.SELECT)
        }
    }

    // Lazy List state for Continuous Scroll mode
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = state.initialPage)

    // Horizontal Pager state for Single Page mode
    val singlePagerState = rememberPagerState(
        initialPage = state.initialPage.coerceIn(0, (state.pageCount - 1).coerceAtLeast(0)),
        pageCount = { state.pageCount.coerceAtLeast(1) }
    )

    // Horizontal Pager state for Two-Page Spread mode
    val spreadCount = (state.pageCount + 1) / 2
    val spreadPagerState = rememberPagerState(
        initialPage = (state.initialPage / 2).coerceIn(0, (spreadCount - 1).coerceAtLeast(0)),
        pageCount = { spreadCount.coerceAtLeast(1) }
    )

    // Sync active page with ViewModel based on active reading mode
    LaunchedEffect(readingMode, listState, singlePagerState, spreadPagerState) {
        when (readingMode) {
            ReadingMode.CONTINUOUS -> {
                snapshotFlow { listState.firstVisibleItemIndex }.collect { page ->
                    viewModel.onPageChanged(page)
                }
            }
            ReadingMode.SINGLE_PAGE -> {
                snapshotFlow { singlePagerState.currentPage }.collect { page ->
                    viewModel.onPageChanged(page)
                }
            }
            ReadingMode.TWO_PAGE_SPREAD -> {
                snapshotFlow { spreadPagerState.currentPage }.collect { spread ->
                    viewModel.onPageChanged(spread * 2)
                }
            }
        }
    }

    // Auto-scroll logic for Continuous mode
    LaunchedEffect(isAutoScrollActive, autoScrollSpeed, readingMode) {
        if (isAutoScrollActive && readingMode == ReadingMode.CONTINUOUS) {
            while (isActive) {
                val scrollPixels = autoScrollSpeed * 2.5f
                listState.scrollBy(scrollPixels)
                delay(16) // ~60 FPS
            }
        }
    }

    // Zoom and pan state
    var scale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    var showViewerMoreMenu by remember { mutableStateOf(false) }
    var showViewerCompressDialog by remember { mutableStateOf(false) }
    var showViewerProtectDialog by remember { mutableStateOf(false) }
    var viewerProtectInitialMode by remember { mutableStateOf(PdfSecurityDialogMode.ENCRYPT) }
    var showViewerUnlockDialog by remember { mutableStateOf(false) }
    var showViewerMetadataDialog by remember { mutableStateOf(false) }
    var showViewerWatermarkDialog by remember { mutableStateOf(false) }
    var showViewerPrintDialog by remember { mutableStateOf(false) }
    var showViewerCropDialog by remember { mutableStateOf(false) }
    var showViewerRotatePagesDialog by remember { mutableStateOf(false) }
    var showViewerConvertOfficeDialog by remember { mutableStateOf(false) }
    val operationProgress by viewModel.operationProgress.collectAsStateWithLifecycle()

    // Color matrix for inverting colors in Night Mode
    val nightModeColorFilter = remember {
        ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    -1.0f,  0.0f,  0.0f, 0.0f, 255.0f,
                     0.0f, -1.0f,  0.0f, 0.0f, 255.0f,
                     0.0f,  0.0f, -1.0f, 0.0f, 255.0f,
                     0.0f,  0.0f,  0.0f, 1.0f,   0.0f
                )
            )
        )
    }

    val backgroundColor = if (isNightMode) Color(0xFF121212) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            if (!isFullScreen) {
                Column {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = state.fileName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Page ${currentPage + 1} of ${state.pageCount}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.testTag("viewer_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back to list"
                                )
                            }
                        },
                        actions = {
                            // Gemini AI Assistant Button (Summary, Chat, Translate, Explain)
                            IconButton(
                                onClick = { viewModel.openAiAssistant(AiToolTab.SUMMARY) },
                                modifier = Modifier.testTag("viewer_ai_assistant_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = "Gemini AI Assistant (Summary, Chat, Translate, Explain)",
                                    tint = if (isAiSheetOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                                )
                            }
                            // Extract Selectable Text from Scanned Page (ML Kit OCR)
                            IconButton(
                                onClick = { viewModel.openOcrTextSheet(currentPage) },
                                modifier = Modifier.testTag("viewer_ocr_extract_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DocumentScanner,
                                    contentDescription = "Extract selectable text from scanned page (OCR)",
                                    tint = if (isOcrSheetOpen || isOcrSelectionModeActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            // Annotation Tools Toggle (Freehand Draw, Highlight, Notes)
                            IconButton(
                                onClick = { viewModel.toggleAnnotationToolbar() },
                                modifier = Modifier.testTag("viewer_annotation_tools_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Draw,
                                    contentDescription = "Annotate PDF",
                                    tint = if (isAnnotationToolbarVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            // Text Search
                            IconButton(
                                onClick = { viewModel.toggleSearch() },
                                modifier = Modifier.testTag("viewer_search_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search text in document",
                                    tint = if (isSearchVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            // Night mode
                            IconButton(
                                onClick = { viewModel.toggleNightMode() },
                                modifier = Modifier.testTag("viewer_night_mode_button")
                            ) {
                                Icon(
                                    imageVector = if (isNightMode) Icons.Default.Brightness7 else Icons.Default.Brightness4,
                                    contentDescription = "Toggle night mode"
                                )
                            }
                            // Share
                            IconButton(
                                onClick = onShare,
                                modifier = Modifier.testTag("viewer_share_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Share PDF"
                                )
                            }
                            // More Tools Menu (each secondary option listed once, no duplicates with top bar)
                            Box {
                                IconButton(
                                    onClick = { showViewerMoreMenu = true },
                                    modifier = Modifier.testTag("viewer_more_tools_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "More tools"
                                    )
                                }
                                DropdownMenu(
                                    expanded = showViewerMoreMenu,
                                    onDismissRequest = { showViewerMoreMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(if (isOcrSelectionModeActive) "Exit On-Page OCR Selection" else "Select Scanned Text on Page (OCR)") },
                                        leadingIcon = { Icon(Icons.Default.TextFields, contentDescription = null) },
                                        onClick = {
                                            showViewerMoreMenu = false
                                            viewModel.toggleOcrSelectionMode()
                                        },
                                        modifier = Modifier.testTag("viewer_ocr_select_mode_btn")
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Edit Content & Redact") },
                                        leadingIcon = { Icon(Icons.Default.EditNote, contentDescription = null) },
                                        onClick = {
                                            showViewerMoreMenu = false
                                            viewModel.toggleContentEditMode()
                                        },
                                        modifier = Modifier.testTag("viewer_content_edit_btn")
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Fill & Sign PDF") },
                                        leadingIcon = { Icon(Icons.Default.Assignment, contentDescription = null) },
                                        onClick = {
                                            showViewerMoreMenu = false
                                            viewModel.toggleFillAndSignMode()
                                        },
                                        modifier = Modifier.testTag("viewer_fill_sign_btn")
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Crop Page & Margins") },
                                        leadingIcon = { Icon(Icons.Default.Crop, contentDescription = null) },
                                        onClick = {
                                            showViewerMoreMenu = false
                                            showViewerCropDialog = true
                                        },
                                        modifier = Modifier.testTag("viewer_crop_page_button")
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Organize Pages") },
                                        leadingIcon = { Icon(Icons.Default.ViewCarousel, contentDescription = null) },
                                        onClick = {
                                            showViewerMoreMenu = false
                                            viewModel.setPageOrganizerOpen(true)
                                        },
                                        modifier = Modifier.testTag("viewer_page_organizer_button")
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Thumbnail Bottom Sheet") },
                                        leadingIcon = { Icon(Icons.Default.GridView, contentDescription = null) },
                                        onClick = {
                                            showViewerMoreMenu = false
                                            viewModel.setThumbnailSheetOpen(true)
                                        },
                                        modifier = Modifier.testTag("viewer_thumbnails_button")
                                    )
                                    DropdownMenuItem(
                                        text = { Text(if (isThumbnailBottomDrawerOpen) "Hide Bottom Thumbnail Drawer" else "Bottom Thumbnail Drawer") },
                                        leadingIcon = { Icon(Icons.Default.ViewDay, contentDescription = null) },
                                        onClick = {
                                            showViewerMoreMenu = false
                                            viewModel.toggleThumbnailBottomDrawer()
                                        },
                                        modifier = Modifier.testTag("viewer_thumbnail_bottom_drawer_button")
                                    )
                                    DropdownMenuItem(
                                        text = { Text(if (isThumbnailSidebarOpen) "Hide Thumbnail Sidebar" else "Show Thumbnail Sidebar") },
                                        leadingIcon = { Icon(Icons.Default.ViewSidebar, contentDescription = null) },
                                        onClick = {
                                            showViewerMoreMenu = false
                                            viewModel.toggleThumbnailSidebar()
                                        },
                                        modifier = Modifier.testTag("viewer_thumbnail_sidebar_button")
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Outline & Bookmarks") },
                                        leadingIcon = { Icon(Icons.Default.MenuBook, contentDescription = null) },
                                        onClick = {
                                            showViewerMoreMenu = false
                                            viewModel.setOutlineSheetOpen(true)
                                        },
                                        modifier = Modifier.testTag("viewer_outline_btn")
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Reading Mode") },
                                        leadingIcon = { Icon(Icons.Default.ViewDay, contentDescription = null) },
                                        onClick = {
                                            showViewerMoreMenu = false
                                            onOpenReadingModeDialog()
                                        },
                                        modifier = Modifier.testTag("viewer_reading_mode_btn")
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Read Aloud (TTS)") },
                                        leadingIcon = { Icon(Icons.Default.RecordVoiceOver, contentDescription = null) },
                                        onClick = {
                                            showViewerMoreMenu = false
                                            viewModel.toggleTtsBar()
                                        },
                                        modifier = Modifier.testTag("viewer_tts_btn")
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Rotate Selected Pages (90° Tool)") },
                                        leadingIcon = { Icon(Icons.Default.RotateRight, contentDescription = null) },
                                        onClick = {
                                            showViewerMoreMenu = false
                                            showViewerRotatePagesDialog = true
                                        },
                                        modifier = Modifier.testTag("viewer_rotate_pages_tool_btn")
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Rotate Clockwise (90°)") },
                                        leadingIcon = { Icon(Icons.Default.RotateRight, contentDescription = null) },
                                        onClick = {
                                            showViewerMoreMenu = false
                                            viewModel.rotatePageInViewer(currentPage, clockwise = true) {
                                                Toast.makeText(context, "Page ${currentPage + 1} rotated 90° clockwise", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.testTag("menu_rotate_page_cw")
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Rotate Counter-Clockwise (90°)") },
                                        leadingIcon = { Icon(Icons.Default.RotateLeft, contentDescription = null) },
                                        onClick = {
                                            showViewerMoreMenu = false
                                            viewModel.rotatePageInViewer(currentPage, clockwise = false) {
                                                Toast.makeText(context, "Page ${currentPage + 1} rotated 90° CCW", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.testTag("menu_rotate_page_ccw")
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Watermark & Numbers") },
                                        leadingIcon = { Icon(Icons.Default.WaterDrop, contentDescription = null) },
                                        onClick = {
                                            showViewerMoreMenu = false
                                            showViewerWatermarkDialog = true
                                        },
                                        modifier = Modifier.testTag("viewer_watermark_button")
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Print PDF") },
                                        leadingIcon = { Icon(Icons.Default.Print, contentDescription = null) },
                                        onClick = {
                                            showViewerMoreMenu = false
                                            showViewerPrintDialog = true
                                        },
                                        modifier = Modifier.testTag("viewer_print_button")
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Convert to Word / Office (.docx, .xlsx...)") },
                                        leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) },
                                        onClick = {
                                            showViewerMoreMenu = false
                                            showViewerConvertOfficeDialog = true
                                        },
                                        modifier = Modifier.testTag("viewer_convert_office_btn")
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Compress PDF") },
                                        onClick = {
                                            showViewerMoreMenu = false
                                            showViewerCompressDialog = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Password Protect (Encrypt PDF)") },
                                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                                        onClick = {
                                            showViewerMoreMenu = false
                                            viewerProtectInitialMode = PdfSecurityDialogMode.ENCRYPT
                                            showViewerProtectDialog = true
                                        },
                                        modifier = Modifier.testTag("viewer_protect_pdf_btn")
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Decrypt / Unlock PDF") },
                                        leadingIcon = { Icon(Icons.Default.LockOpen, contentDescription = null) },
                                        onClick = {
                                            showViewerMoreMenu = false
                                            showViewerUnlockDialog = true
                                        },
                                        modifier = Modifier.testTag("viewer_unlock_pdf_btn")
                                    )
                                    val isDocBiometricProtected = protectedDocs.contains(state.uri.toString())
                                    DropdownMenuItem(
                                        text = { Text(if (isDocBiometricProtected) "Remove Biometric Lock" else "Protect with Biometrics") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Fingerprint,
                                                contentDescription = null,
                                                tint = if (isDocBiometricProtected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                        },
                                        onClick = {
                                            showViewerMoreMenu = false
                                            val nowProtected = viewModel.toggleDocumentBiometricProtection(state.uri.toString())
                                            Toast.makeText(
                                                context,
                                                if (nowProtected) "Document protected with Fingerprint / Face Unlock" else "Biometric protection removed",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        },
                                        modifier = Modifier.testTag("viewer_biometric_protect_item")
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Document Properties") },
                                        onClick = {
                                            showViewerMoreMenu = false
                                            showViewerMetadataDialog = true
                                        }
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = if (isNightMode) Color(0xFF1E1E1E) else MaterialTheme.colorScheme.surface
                        )
                    )

                    // In-viewer Search Bar
                    AnimatedVisibility(
                        visible = isSearchVisible,
                        enter = slideInVertically() + fadeIn(),
                        exit = slideOutVertically() + fadeOut()
                    ) {
                        PdfSearchBar(
                            viewModel = viewModel,
                            isNightMode = isNightMode
                        )
                    }

                    // One-Time Missing Font Substitution Warning Banner
                    if (missingFontWarningMessage != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("missing_font_warning_banner")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.WarningAmber,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = missingFontWarningMessage ?: "",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.dismissMissingFontWarning() },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Dismiss font notice",
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Smart Scanned Page (Non-Searchable) OCR Prompt Banner
                    AnimatedVisibility(
                        visible = isCurrentPageScannedWithoutText && !isOcrSelectionModeActive && !isOcrSheetOpen,
                        enter = slideInVertically() + fadeIn(),
                        exit = slideOutVertically() + fadeOut()
                    ) {
                        ScannedPageOcrPromptBanner(
                            onActivateOnPageSelection = { viewModel.setOcrSelectionModeActive(true) },
                            onOpenOcrTextSheet = { viewModel.openOcrTextSheet(currentPage) },
                            onDismiss = { viewModel.dismissScannedPageOcrBanner() }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isFullScreen) PaddingValues(0.dp) else innerPadding)
                .background(backgroundColor)
        ) {
            val constraintsWidth = maxWidth
            val isTablet = constraintsWidth >= 600.dp
            val contentWidthModifier = if (isTablet) Modifier.widthIn(max = 840.dp) else Modifier.fillMaxWidth()

            // Main Reader Container with Pinch & Double-tap zoom
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(activeAnnotationTool) {
                        // Only enable zoom/pan gestures on background canvas if no drawing tool is active
                        if (activeAnnotationTool == null) {
                            detectTapGestures(
                                onTap = {
                                    if (isFullScreen) {
                                        viewModel.setFullScreen(false)
                                    }
                                },
                                onDoubleTap = { tapOffset ->
                                    if (scale > 1.2f) {
                                        scale = 1f
                                        panOffset = Offset.Zero
                                    } else {
                                        scale = 2.2f
                                        panOffset = Offset(
                                            x = -tapOffset.x * 0.5f,
                                            y = 0f
                                        )
                                    }
                                }
                            )
                        }
                    }
                    .pointerInput(activeAnnotationTool, isContentEditActive) {
                        if (activeAnnotationTool == null && !isContentEditActive) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 4f)
                                if (scale > 1f) {
                                    val maxPanX = (size.width * (scale - 1f)) / 2f
                                    val maxPanY = (size.height * (scale - 1f)) / 2f
                                    panOffset = Offset(
                                        x = (panOffset.x + pan.x).coerceIn(-maxPanX, maxPanX),
                                        y = (panOffset.y + pan.y).coerceIn(-maxPanY, maxPanY)
                                    )
                                } else {
                                    panOffset = Offset.Zero
                                }
                            }
                        }
                    }
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = panOffset.x,
                        translationY = panOffset.y
                    ),
                contentAlignment = Alignment.TopCenter
            ) {
                val handleExecuteLink: (ContentEditElement.LinkArea) -> Unit = { link ->
                    when (link.linkType) {
                        PdfLinkType.WEB_URL -> {
                            runCatching {
                                val raw = link.targetUrlOrEmail.trim()
                                val url = if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "https://$raw"
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            }
                        }
                        PdfLinkType.EMAIL -> {
                            runCatching {
                                val mail = link.targetUrlOrEmail.removePrefix("mailto:").trim()
                                context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$mail")))
                            }
                        }
                        PdfLinkType.PAGE_JUMP -> {
                            val target = link.targetPageIndex.coerceIn(0, (state.pageCount - 1).coerceAtLeast(0))
                            coroutineScope.launch {
                                when (readingMode) {
                                    ReadingMode.CONTINUOUS -> listState.scrollToItem(target)
                                    ReadingMode.SINGLE_PAGE -> singlePagerState.scrollToPage(target)
                                    ReadingMode.TWO_PAGE_SPREAD -> spreadPagerState.scrollToPage(target / 2)
                                }
                                viewModel.onPageChanged(target)
                            }
                        }
                    }
                }

                when (readingMode) {
                    ReadingMode.CONTINUOUS -> {
                        // Continuous vertical scroll
                        LazyColumn(
                            state = listState,
                            modifier = contentWidthModifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(
                                count = state.pageCount,
                                key = { it }
                            ) { pageIndex ->
                                PdfPageCardWithAnnotations(
                                    pageIndex = pageIndex,
                                    viewModel = viewModel,
                                    annotations = allAnnotations.filter { it.pageIndex == pageIndex },
                                    activeTool = activeAnnotationTool,
                                    activeColorArgb = activeAnnotationColor,
                                    activeThickness = activeAnnotationThickness,
                                    selectedAnnotationId = selectedAnnotationId,
                                    isNightMode = isNightMode,
                                    colorFilter = if (isNightMode) nightModeColorFilter else null,
                                    onTapAddTextAt = { pIdx, pt -> pendingAddTextPoint = pIdx to pt },
                                    onTapExistingTextLine = { line -> pendingEditExistingLine = line },
                                    onTapAddLinkAt = { pIdx, rect -> pendingAddLinkRect = pIdx to rect },
                                    onExecuteLink = handleExecuteLink
                                )
                            }
                        }
                    }
                    ReadingMode.SINGLE_PAGE -> {
                        // Single page swipe
                        HorizontalPager(
                            state = singlePagerState,
                            modifier = contentWidthModifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
                            pageSpacing = 12.dp,
                            userScrollEnabled = activeAnnotationTool == null && !isContentEditActive
                        ) { pageIndex ->
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                PdfPageCardWithAnnotations(
                                    pageIndex = pageIndex,
                                    viewModel = viewModel,
                                    annotations = allAnnotations.filter { it.pageIndex == pageIndex },
                                    activeTool = activeAnnotationTool,
                                    activeColorArgb = activeAnnotationColor,
                                    activeThickness = activeAnnotationThickness,
                                    selectedAnnotationId = selectedAnnotationId,
                                    isNightMode = isNightMode,
                                    colorFilter = if (isNightMode) nightModeColorFilter else null,
                                    onTapAddTextAt = { pIdx, pt -> pendingAddTextPoint = pIdx to pt },
                                    onTapExistingTextLine = { line -> pendingEditExistingLine = line },
                                    onTapAddLinkAt = { pIdx, rect -> pendingAddLinkRect = pIdx to rect },
                                    onExecuteLink = handleExecuteLink
                                )
                            }
                        }
                    }
                    ReadingMode.TWO_PAGE_SPREAD -> {
                        // Two-page spread side-by-side
                        HorizontalPager(
                            state = spreadPagerState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 16.dp),
                            pageSpacing = 12.dp,
                            userScrollEnabled = activeAnnotationTool == null && !isContentEditActive
                        ) { spreadIndex ->
                            val leftPage = spreadIndex * 2
                            val rightPage = leftPage + 1

                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    if (leftPage < state.pageCount) {
                                        PdfPageCardWithAnnotations(
                                            pageIndex = leftPage,
                                            viewModel = viewModel,
                                            annotations = allAnnotations.filter { it.pageIndex == leftPage },
                                            activeTool = activeAnnotationTool,
                                            activeColorArgb = activeAnnotationColor,
                                            activeThickness = activeAnnotationThickness,
                                            selectedAnnotationId = selectedAnnotationId,
                                            isNightMode = isNightMode,
                                            colorFilter = if (isNightMode) nightModeColorFilter else null,
                                            onTapAddTextAt = { pIdx, pt -> pendingAddTextPoint = pIdx to pt },
                                            onTapExistingTextLine = { line -> pendingEditExistingLine = line },
                                            onTapAddLinkAt = { pIdx, rect -> pendingAddLinkRect = pIdx to rect },
                                            onExecuteLink = handleExecuteLink
                                        )
                                    }
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    if (rightPage < state.pageCount) {
                                        PdfPageCardWithAnnotations(
                                            pageIndex = rightPage,
                                            viewModel = viewModel,
                                            annotations = allAnnotations.filter { it.pageIndex == rightPage },
                                            activeTool = activeAnnotationTool,
                                            activeColorArgb = activeAnnotationColor,
                                            activeThickness = activeAnnotationThickness,
                                            selectedAnnotationId = selectedAnnotationId,
                                            isNightMode = isNightMode,
                                            colorFilter = if (isNightMode) nightModeColorFilter else null,
                                            onTapAddTextAt = { pIdx, pt -> pendingAddTextPoint = pIdx to pt },
                                            onTapExistingTextLine = { line -> pendingEditExistingLine = line },
                                            onTapAddLinkAt = { pIdx, rect -> pendingAddLinkRect = pIdx to rect },
                                            onExecuteLink = handleExecuteLink
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Floating AcroForm Field Navigator (Prev / Next Field)
            if (formFields.isNotEmpty() && !isFullScreen) {
                val currentField = formFields.find { it.id == focusedFieldId } ?: formFields.firstOrNull()
                val currentIdx = formFields.indexOf(currentField).coerceAtLeast(0)
                FormNavigationFab(
                    currentFieldIndex = currentIdx,
                    totalFields = formFields.size,
                    currentFieldName = currentField?.name ?: "Field",
                    onNavigateNext = {
                        viewModel.navigateField(forward = true) { targetPage ->
                            val clamped = targetPage.coerceIn(0, (state.pageCount - 1).coerceAtLeast(0))
                            coroutineScope.launch {
                                when (readingMode) {
                                    ReadingMode.CONTINUOUS -> listState.scrollToItem(clamped)
                                    ReadingMode.SINGLE_PAGE -> singlePagerState.scrollToPage(clamped)
                                    ReadingMode.TWO_PAGE_SPREAD -> spreadPagerState.scrollToPage(clamped / 2)
                                }
                                viewModel.onPageChanged(clamped)
                            }
                        }
                    },
                    onNavigatePrev = {
                        viewModel.navigateField(forward = false) { targetPage ->
                            val clamped = targetPage.coerceIn(0, (state.pageCount - 1).coerceAtLeast(0))
                            coroutineScope.launch {
                                when (readingMode) {
                                    ReadingMode.CONTINUOUS -> listState.scrollToItem(clamped)
                                    ReadingMode.SINGLE_PAGE -> singlePagerState.scrollToPage(clamped)
                                    ReadingMode.TWO_PAGE_SPREAD -> spreadPagerState.scrollToPage(clamped / 2)
                                }
                                viewModel.onPageChanged(clamped)
                            }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 16.dp, end = 16.dp)
                )
            }

            // Quick Expand Handle for Scrollable Thumbnail Sidebar (when collapsed)
            if (!isFullScreen && !isThumbnailSidebarOpen && state.pageCount > 0) {
                Surface(
                    onClick = { viewModel.setThumbnailSidebarOpen(true) },
                    shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
                    color = if (isNightMode) Color(0xEE262626) else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.94f),
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(bottom = 64.dp)
                        .testTag("btn_expand_thumbnail_sidebar")
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = "Open Page Thumbnails Sidebar",
                            tint = if (isNightMode) Color.White else MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Icon(
                            imageVector = Icons.Default.ViewSidebar,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "${currentPage + 1}/${state.pageCount}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isNightMode) Color.White else MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            // Dockable & Collapsible Scrollable Page Thumbnail Sidebar
            AnimatedVisibility(
                visible = !isFullScreen && isThumbnailSidebarOpen,
                enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                ThumbnailNavigationSidebar(
                    totalPages = state.pageCount,
                    currentPage = currentPage,
                    filterMode = thumbnailFilterMode,
                    bookmarkedPages = bookmarkedPageSet,
                    annotatedPageCounts = annotatedPageCounts,
                    viewModel = viewModel,
                    isNightMode = isNightMode,
                    onSelectFilterMode = { viewModel.setThumbnailFilterMode(it) },
                    onSelectPage = { targetPage ->
                        val clamped = targetPage.coerceIn(0, (state.pageCount - 1).coerceAtLeast(0))
                        coroutineScope.launch {
                            when (readingMode) {
                                ReadingMode.CONTINUOUS -> listState.scrollToItem(clamped)
                                ReadingMode.SINGLE_PAGE -> singlePagerState.scrollToPage(clamped)
                                ReadingMode.TWO_PAGE_SPREAD -> spreadPagerState.scrollToPage(clamped / 2)
                            }
                            viewModel.onPageChanged(clamped)
                        }
                    },
                    onExpandToBottomSheet = { viewModel.switchThumbnailViewToBottomSheet() },
                    onCloseSidebar = { viewModel.setThumbnailSidebarOpen(false) }
                )
            }

            // Fullscreen Exit Floating Button
            if (isFullScreen) {
                Surface(
                    onClick = { viewModel.setFullScreen(false) },
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.6f),
                    contentColor = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .testTag("btn_exit_fullscreen")
                ) {
                    Icon(
                        imageVector = Icons.Default.FullscreenExit,
                        contentDescription = "Exit Full Screen",
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            // Bottom Controls (Page indicator, Auto-scroll pill, TTS Bar, Annotation Toolbar, Content Editor)
            if (!isFullScreen) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Auto Scroll Pill (if active)
                    if (isAutoScrollActive) {
                        AutoScrollControlPill(
                            viewModel = viewModel,
                            isNightMode = isNightMode,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }

                    // Selection Toolbar for Content Editing (Copy, Paste, Duplicate, Layer Order, Delete)
                    if (isContentEditActive) {
                        val selectedContentElement = contentEdits.firstOrNull { it.id == selectedContentElementId }
                        ContentEditSelectionToolbar(
                            selectedElement = selectedContentElement,
                            hasClipboardItem = clipboardContentElement != null,
                            onEditSelected = {
                                when (selectedContentElement) {
                                    is ContentEditElement.AddedText -> editingAddedTextElement = selectedContentElement
                                    is ContentEditElement.ReplacedText -> editingReplacedTextElement = selectedContentElement
                                    is ContentEditElement.ImageObject -> editingImageElement = selectedContentElement
                                    is ContentEditElement.LinkArea -> editingLinkElement = selectedContentElement
                                    else -> {}
                                }
                            },
                            onCopy = { viewModel.copySelectedContentElement() },
                            onPaste = { viewModel.pasteContentElement(currentPage) },
                            onDuplicate = { viewModel.duplicateSelectedContentElement() },
                            onBringForward = { viewModel.bringSelectedElementForward() },
                            onSendBackward = { viewModel.sendSelectedElementBackward() },
                            onDelete = {
                                selectedContentElement?.let { viewModel.deleteContentEditElement(it.id) }
                            }
                        )
                    }

                    // Floating Page Indicator Pill
                    AnimatedVisibility(
                        visible = scale == 1f && !isTtsBarVisible && !isAutoScrollActive && !isAnnotationToolbarVisible && !isContentEditActive && !isOcrSelectionModeActive,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier.padding(bottom = 20.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = if (isNightMode) Color(0xFF2A2A2A) else MaterialTheme.colorScheme.inverseSurface,
                            shadowElevation = 6.dp,
                            modifier = Modifier.testTag("viewer_page_control_bar")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                // Rotate Counter-Clockwise (CCW)
                                IconButton(
                                    onClick = {
                                        viewModel.rotatePageInViewer(currentPage, clockwise = false) {
                                            Toast.makeText(context, "Page ${currentPage + 1} rotated 90° CCW", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("rotate_page_ccw_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.RotateLeft,
                                        contentDescription = "Rotate page 90° counter-clockwise",
                                        tint = if (isNightMode) Color.White else MaterialTheme.colorScheme.inverseOnSurface,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                // Jump / Page Indicator
                                Surface(
                                    onClick = { viewModel.setJumpDialogOpen(true) },
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color.Transparent,
                                    modifier = Modifier.testTag("viewer_page_indicator_pill")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = "${currentPage + 1} / ${state.pageCount}",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isNightMode) Color.White else MaterialTheme.colorScheme.inverseOnSurface
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(4.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary)
                                        )
                                        Text(
                                            text = "Jump",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                // Rotate Clockwise (CW)
                                IconButton(
                                    onClick = {
                                        viewModel.rotatePageInViewer(currentPage, clockwise = true) {
                                            Toast.makeText(context, "Page ${currentPage + 1} rotated 90° CW", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("rotate_page_cw_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.RotateRight,
                                        contentDescription = "Rotate page 90° clockwise",
                                        tint = if (isNightMode) Color.White else MaterialTheme.colorScheme.inverseOnSurface,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                // Crop Page
                                IconButton(
                                    onClick = { showViewerCropDialog = true },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("crop_page_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Crop,
                                        contentDescription = "Crop Page & Margins",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                // Page Thumbnails Bottom Sheet (1-tap quick navigation)
                                IconButton(
                                    onClick = { viewModel.setThumbnailSheetOpen(true) },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("thumbnails_pill_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.GridView,
                                        contentDescription = "Open Page Thumbnails Bottom Sheet",
                                        tint = if (isThumbnailSheetOpen) MaterialTheme.colorScheme.primary
                                        else if (isNightMode) Color.White
                                        else MaterialTheme.colorScheme.inverseOnSurface,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                // Dockable Bottom Thumbnail Drawer Toggle (scrollable thumbnails & section jumper)
                                IconButton(
                                    onClick = { viewModel.toggleThumbnailBottomDrawer() },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("thumbnail_bottom_drawer_pill_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ViewDay,
                                        contentDescription = "Toggle Bottom Thumbnail Drawer",
                                        tint = if (isThumbnailBottomDrawerOpen) MaterialTheme.colorScheme.primary
                                        else if (isNightMode) Color.White
                                        else MaterialTheme.colorScheme.inverseOnSurface,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                // Page Thumbnails Sidebar Toggle (1-tap side strip)
                                IconButton(
                                    onClick = { viewModel.toggleThumbnailSidebar() },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("thumbnail_sidebar_pill_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ViewSidebar,
                                        contentDescription = "Toggle Page Thumbnails Sidebar",
                                        tint = if (isThumbnailSidebarOpen) MaterialTheme.colorScheme.primary
                                        else if (isNightMode) Color.White
                                        else MaterialTheme.colorScheme.inverseOnSurface,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                // Organize Pages (Drag-and-Drop Reorder, Rotate & Delete)
                                IconButton(
                                    onClick = { viewModel.setPageOrganizerOpen(true) },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("organize_pages_pill_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ViewCarousel,
                                        contentDescription = "Reorder, Rotate & Delete Pages",
                                        tint = if (isNightMode) Color.White else MaterialTheme.colorScheme.inverseOnSurface,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                // Password Protect & Decrypt PDF
                                IconButton(
                                    onClick = {
                                        viewerProtectInitialMode = PdfSecurityDialogMode.ENCRYPT
                                        showViewerProtectDialog = true
                                    },
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("security_protect_pill_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Encrypt or Decrypt PDF Password",
                                        tint = if (isNightMode) Color.White else MaterialTheme.colorScheme.inverseOnSurface,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Dockable Bottom Thumbnail Drawer with Section Navigation for Long Documents
                    AnimatedVisibility(
                        visible = isThumbnailBottomDrawerOpen && !isFullScreen,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                    ) {
                        ThumbnailBottomDrawer(
                            totalPages = state.pageCount,
                            currentPage = currentPage,
                            filterMode = thumbnailFilterMode,
                            sections = documentSections,
                            activeSection = activeSection,
                            selectedSectionId = selectedSectionId,
                            bookmarkedPages = bookmarkedPageSet,
                            annotatedPageCounts = annotatedPageCounts,
                            viewModel = viewModel,
                            isNightMode = isNightMode,
                            onSelectFilterMode = { viewModel.setThumbnailFilterMode(it) },
                            onSelectSection = { section ->
                                if (section == null) {
                                    viewModel.setSelectedSectionId(null)
                                } else {
                                    val newId = if (selectedSectionId == section.id) null else section.id
                                    viewModel.setSelectedSectionId(newId)
                                    val targetPage = section.startPage.coerceIn(0, (state.pageCount - 1).coerceAtLeast(0))
                                    coroutineScope.launch {
                                        when (readingMode) {
                                            ReadingMode.CONTINUOUS -> listState.scrollToItem(targetPage)
                                            ReadingMode.SINGLE_PAGE -> singlePagerState.scrollToPage(targetPage)
                                            ReadingMode.TWO_PAGE_SPREAD -> spreadPagerState.scrollToPage(targetPage / 2)
                                        }
                                        viewModel.onPageChanged(targetPage)
                                    }
                                }
                            },
                            onSelectPage = { page ->
                                val targetPage = page.coerceIn(0, (state.pageCount - 1).coerceAtLeast(0))
                                coroutineScope.launch {
                                    when (readingMode) {
                                        ReadingMode.CONTINUOUS -> listState.scrollToItem(targetPage)
                                        ReadingMode.SINGLE_PAGE -> singlePagerState.scrollToPage(targetPage)
                                        ReadingMode.TWO_PAGE_SPREAD -> spreadPagerState.scrollToPage(targetPage / 2)
                                    }
                                    viewModel.onPageChanged(targetPage)
                                }
                            },
                            onExpandToSheet = { viewModel.switchBottomDrawerToSheet() },
                            onDockToSidebar = { viewModel.switchThumbnailViewToSidebar() },
                            onClose = { viewModel.setThumbnailBottomDrawerOpen(false) }
                        )
                    }

                    // Text-To-Speech Player Bar
                    AnimatedVisibility(
                        visible = isTtsBarVisible,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                    ) {
                        PdfTtsBar(
                            viewModel = viewModel,
                            currentPage = currentPage,
                            isNightMode = isNightMode
                        )
                    }

                    // Annotation Toolbar
                    AnimatedVisibility(
                        visible = isAnnotationToolbarVisible,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                    ) {
                        AnnotationToolbar(
                            viewModel = viewModel,
                            isNightMode = isNightMode
                        )
                    }

                    // Fill & Sign Toolbar
                    AnimatedVisibility(
                        visible = isFillAndSignActive,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                    ) {
                        FillAndSignToolbar(
                            activeQuickFillType = activeQuickFillType,
                            settings = appSettings,
                            isNightMode = isNightMode,
                            onSelectQuickFillType = { viewModel.setActiveQuickFillType(it) },
                            onSnippetSelected = { viewModel.setPendingSnippetText(it) },
                            onOpenSignaturesSheet = { viewModel.setSignaturePickerOpen(true) },
                            onOpenAddFieldDialog = { viewModel.setAddFieldDialogOpen(true) },
                            onOpenSaveDialog = { viewModel.setSaveFormDialogOpen(true) },
                            onCloseToolbar = { viewModel.setFillAndSignActive(false) }
                        )
                    }

                    // Content Edit & Redaction Bottom Toolbar
                    AnimatedVisibility(
                        visible = isContentEditActive,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                    ) {
                        ContentEditBottomToolbar(
                            activeTool = activeContentTool,
                            canUndo = canUndoContentEdit,
                            canRedo = canRedoContentEdit,
                            editsCount = contentEdits.size,
                            onSelectTool = { viewModel.setActiveContentTool(it) },
                            onAddImageFromGallery = {
                                replacingImageElementId = null
                                contentImageGalleryLauncher.launch("image/*")
                            },
                            onAddImageFromCamera = {
                                replacingImageElementId = null
                                addOrReplaceImageFromCamera()
                            },
                            onUndo = { viewModel.undoContentEdit() },
                            onRedo = { viewModel.redoContentEdit() },
                            onSaveClick = { viewModel.setSaveContentEditsDialogOpen(true) },
                            onClose = { viewModel.setContentEditActive(false) }
                        )
                    }

                    // ML Kit On-Page OCR Text Selection Bottom Toolbar
                    AnimatedVisibility(
                        visible = isOcrSelectionModeActive,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                    ) {
                        OcrBottomSelectionToolbar(
                            isRunningOcr = isOcrRunningForPage,
                            ocrResult = pageOcrResults[currentPage],
                            selectedLineTexts = selectedOcrLineTexts,
                            onSelectAllLines = { viewModel.selectAllOcrLinesOnPage(currentPage) },
                            onClearSelection = { viewModel.clearSelectedOcrLines() },
                            onOpenFullTextSheet = { viewModel.openOcrTextSheet(currentPage) },
                            onMakeSearchablePdf = { viewModel.executeEmbedSearchableOcrInCurrentPdf() },
                            onCloseMode = { viewModel.setOcrSelectionModeActive(false) }
                        )
                    }
                }
            }
        }
    }

    // Jump to Page Dialog
    if (isJumpDialogOpen) {
        JumpToPageDialog(
            currentPage = currentPage + 1,
            totalPages = state.pageCount,
            onDismiss = { viewModel.setJumpDialogOpen(false) },
            onJump = { targetPage ->
                viewModel.setJumpDialogOpen(false)
                val targetIndex = (targetPage - 1).coerceIn(0, (state.pageCount - 1).coerceAtLeast(0))
                coroutineScope.launch {
                    when (readingMode) {
                        ReadingMode.CONTINUOUS -> listState.scrollToItem(targetIndex)
                        ReadingMode.SINGLE_PAGE -> singlePagerState.scrollToPage(targetIndex)
                        ReadingMode.TWO_PAGE_SPREAD -> spreadPagerState.scrollToPage(targetIndex / 2)
                    }
                    viewModel.onPageChanged(targetIndex)
                }
            }
        )
    }

    // Table of Contents & Bookmarks Bottom Sheet
    if (isOutlineSheetOpen) {
        PdfOutlineSheet(
            viewModel = viewModel,
            currentPage = currentPage,
            isNightMode = isNightMode,
            onJumpToPage = { targetPage ->
                val clamped = targetPage.coerceIn(0, (state.pageCount - 1).coerceAtLeast(0))
                coroutineScope.launch {
                    when (readingMode) {
                        ReadingMode.CONTINUOUS -> listState.scrollToItem(clamped)
                        ReadingMode.SINGLE_PAGE -> singlePagerState.scrollToPage(clamped)
                        ReadingMode.TWO_PAGE_SPREAD -> spreadPagerState.scrollToPage(clamped / 2)
                    }
                    viewModel.onPageChanged(clamped)
                }
            },
            onDismiss = { viewModel.setOutlineSheetOpen(false) }
        )
    }

    // Annotations List Sheet
    if (isAnnotationsListOpen) {
        AnnotationsListSheet(
            viewModel = viewModel,
            isNightMode = isNightMode,
            onJumpToPage = { targetPage ->
                val clamped = targetPage.coerceIn(0, (state.pageCount - 1).coerceAtLeast(0))
                coroutineScope.launch {
                    when (readingMode) {
                        ReadingMode.CONTINUOUS -> listState.scrollToItem(clamped)
                        ReadingMode.SINGLE_PAGE -> singlePagerState.scrollToPage(clamped)
                        ReadingMode.TWO_PAGE_SPREAD -> spreadPagerState.scrollToPage(clamped / 2)
                    }
                    viewModel.onPageChanged(clamped)
                }
            },
            onDismiss = { viewModel.setAnnotationsListOpen(false) }
        )
    }

    // Save Annotations Dialog
    val saveProgressState by viewModel.saveProgress.collectAsStateWithLifecycle()
    if (isSaveDialogVisible || saveProgressState != null) {
        SaveAnnotationsDialog(
            fileName = state.fileName,
            saveProgress = saveProgressState,
            onSaveOverwrite = onSaveOverwriteRequested,
            onSaveAsCopy = onSaveAsCopyRequested,
            onSaveFlattened = onSaveFlattenedRequested,
            onDismiss = {
                viewModel.setSaveDialogVisible(false)
                viewModel.clearSaveProgress()
            }
        )
    }

    // Thumbnail Grid Sheet
    if (isThumbnailSheetOpen) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { viewModel.setThumbnailSheetOpen(false) },
            sheetState = sheetState,
            containerColor = if (isNightMode) Color(0xFF1E1E1E) else MaterialTheme.colorScheme.surface,
            modifier = Modifier.testTag("thumbnail_bottom_sheet")
        ) {
            ThumbnailSheetContent(
                totalPages = state.pageCount,
                currentPage = currentPage,
                filterMode = thumbnailFilterMode,
                sections = documentSections,
                activeSection = activeSection,
                selectedSectionId = selectedSectionId,
                bookmarkedPages = bookmarkedPageSet,
                annotatedPageCounts = annotatedPageCounts,
                viewModel = viewModel,
                isNightMode = isNightMode,
                onSelectFilterMode = { viewModel.setThumbnailFilterMode(it) },
                onSelectSection = { section ->
                    if (section == null) {
                        viewModel.setSelectedSectionId(null)
                    } else {
                        val newId = if (selectedSectionId == section.id) null else section.id
                        viewModel.setSelectedSectionId(newId)
                        val targetPage = section.startPage.coerceIn(0, (state.pageCount - 1).coerceAtLeast(0))
                        coroutineScope.launch {
                            when (readingMode) {
                                ReadingMode.CONTINUOUS -> listState.scrollToItem(targetPage)
                                ReadingMode.SINGLE_PAGE -> singlePagerState.scrollToPage(targetPage)
                                ReadingMode.TWO_PAGE_SPREAD -> spreadPagerState.scrollToPage(targetPage / 2)
                            }
                            viewModel.onPageChanged(targetPage)
                        }
                    }
                },
                onDockToBottomDrawer = { viewModel.switchSheetToBottomDrawer() },
                onDockToSidebar = { viewModel.switchThumbnailViewToSidebar() },
                onSelectPage = { page ->
                    val clamped = page.coerceIn(0, (state.pageCount - 1).coerceAtLeast(0))
                    viewModel.setThumbnailSheetOpen(false)
                    coroutineScope.launch {
                        when (readingMode) {
                            ReadingMode.CONTINUOUS -> listState.scrollToItem(clamped)
                            ReadingMode.SINGLE_PAGE -> singlePagerState.scrollToPage(clamped)
                            ReadingMode.TWO_PAGE_SPREAD -> spreadPagerState.scrollToPage(clamped / 2)
                        }
                        viewModel.onPageChanged(clamped)
                    }
                }
            )
        }
    }

    // Signature Picker Sheet
    if (isSignaturePickerOpen) {
        SignaturePickerSheet(
            signatureManager = viewModel.signatureManager,
            isNightMode = isNightMode,
            onSignatureSelected = { bitmap, isInitials ->
                viewModel.placeStamp(
                    pageIndex = currentPage,
                    normX = 0.35f,
                    normY = 0.45f,
                    type = if (isInitials) PlacedStampType.INITIALS else PlacedStampType.SIGNATURE,
                    imageBitmap = bitmap
                )
            },
            onCreateNewRequested = { viewModel.setSignatureCreationOpen(true) },
            onDismiss = { viewModel.setSignaturePickerOpen(false) }
        )
    }

    // Signature Creation Sheet (Draw, Type, Import)
    if (isSignatureCreationOpen) {
        SignatureCreationSheet(
            signatureManager = viewModel.signatureManager,
            isNightMode = isNightMode,
            onSignatureCreated = { bitmap, isInitials, _ ->
                viewModel.placeStamp(
                    pageIndex = currentPage,
                    normX = 0.35f,
                    normY = 0.45f,
                    type = if (isInitials) PlacedStampType.INITIALS else PlacedStampType.SIGNATURE,
                    imageBitmap = bitmap
                )
            },
            onDismiss = { viewModel.setSignatureCreationOpen(false) }
        )
    }

    // Form Field Creator Dialog
    if (isAddFieldDialogOpen) {
        FormFieldCreatorDialog(
            currentPage = currentPage,
            onAddField = { type, name, defaultVal, isReq ->
                viewModel.addFormFieldToCurrentPage(
                    pageIndex = currentPage,
                    fieldType = type,
                    fieldName = name,
                    defaultValue = defaultVal,
                    isRequired = isReq
                )
            },
            onDismiss = { viewModel.setAddFieldDialogOpen(false) }
        )
    }

    // Save Form & Signatures Dialog
    if (isSaveFormDialogOpen || isSavingForm) {
        SaveFormDialog(
            fileName = state.fileName,
            isSaving = isSavingForm,
            saveCompletedMessage = saveFormCompletedMessage,
            onSaveOverwrite = { flatten ->
                onSaveFormOverwriteRequested(flatten)
            },
            onSaveAsCopy = { flatten ->
                onSaveFormAsCopyRequested(flatten)
            },
            onDismiss = { viewModel.setSaveFormDialogOpen(false) }
        )
    }

    // Content Edit Dialog 1: Add or Edit Text Box
    if (pendingAddTextPoint != null || editingAddedTextElement != null) {
        AddOrEditTextBoxDialog(
            initialElement = editingAddedTextElement,
            onConfirm = { text, font, fontSizeSp, colorArgb, alignment, isBold, isItalic ->
                val existing = editingAddedTextElement
                val ptPair = pendingAddTextPoint
                if (existing != null) {
                    viewModel.updateContentEditElement(
                        existing.copy(
                            text = text,
                            font = font,
                            fontSizeSp = fontSizeSp,
                            colorArgb = colorArgb,
                            alignment = alignment,
                            isBold = isBold,
                            isItalic = isItalic
                        )
                    )
                } else if (ptPair != null) {
                    val (pIdx, pt) = ptPair
                    viewModel.addContentEditElement(
                        ContentEditElement.AddedText(
                            pageIndex = pIdx,
                            bounds = NormalizedRect(
                                left = pt.x.coerceIn(0.02f, 0.72f),
                                top = pt.y.coerceIn(0.02f, 0.88f),
                                right = (pt.x + 0.36f).coerceAtMost(0.98f),
                                bottom = (pt.y + 0.08f).coerceAtMost(0.98f)
                            ),
                            text = text,
                            font = font,
                            fontSizeSp = fontSizeSp,
                            colorArgb = colorArgb,
                            alignment = alignment,
                            isBold = isBold,
                            isItalic = isItalic
                        )
                    )
                }
                pendingAddTextPoint = null
                editingAddedTextElement = null
                viewModel.setActiveContentTool(ContentEditTool.SELECT)
            },
            onDismiss = {
                pendingAddTextPoint = null
                editingAddedTextElement = null
            }
        )
    }

    // Content Edit Dialog 2: Edit Existing Text Line (cover + replace)
    if (pendingEditExistingLine != null || editingReplacedTextElement != null) {
        val line = pendingEditExistingLine
        val existingRep = editingReplacedTextElement
        EditExistingTextLineDialog(
            originalText = existingRep?.originalText ?: line?.text ?: "",
            initialReplacement = existingRep?.replacementText ?: line?.text ?: "",
            matchedFont = existingRep?.font
                ?: line?.let { BundledEditFont.matchClosestFont(it.fontName, it.text) }
                ?: BundledEditFont.NOTO_SANS,
            initialFontSizeSp = existingRep?.fontSizeSp ?: line?.fontSizePt ?: 12f,
            initialIsBold = existingRep?.isBold ?: line?.isBold ?: false,
            initialIsItalic = existingRep?.isItalic ?: line?.isItalic ?: false,
            onAskAiExplain = { selectedLineText ->
                pendingEditExistingLine = null
                editingReplacedTextElement = null
                viewModel.openAiAssistant(AiToolTab.EXPLAIN, preselectedText = selectedLineText)
            },
            onAskAiTranslate = { selectedLineText ->
                pendingEditExistingLine = null
                editingReplacedTextElement = null
                viewModel.openAiAssistant(AiToolTab.TRANSLATE, preselectedText = selectedLineText)
            },
            onConfirm = { replacementText, font, fontSizeSp, textColorArgb, bgColorArgb, isBold, isItalic ->
                if (existingRep != null) {
                    viewModel.updateContentEditElement(
                        existingRep.copy(
                            replacementText = replacementText,
                            font = font,
                            fontSizeSp = fontSizeSp,
                            textColorArgb = textColorArgb,
                            backgroundColorArgb = bgColorArgb,
                            isBold = isBold,
                            isItalic = isItalic
                        )
                    )
                } else if (line != null) {
                    viewModel.addContentEditElement(
                        ContentEditElement.ReplacedText(
                            pageIndex = line.pageIndex,
                            bounds = NormalizedRect(
                                left = (line.bounds.left - 0.005f).coerceAtLeast(0f),
                                top = (line.bounds.top - 0.004f).coerceAtLeast(0f),
                                right = (line.bounds.right + 0.015f).coerceAtMost(1f),
                                bottom = (line.bounds.bottom + 0.006f).coerceAtMost(1f)
                            ),
                            originalText = line.text,
                            replacementText = replacementText,
                            font = font,
                            fontSizeSp = fontSizeSp,
                            textColorArgb = textColorArgb,
                            backgroundColorArgb = bgColorArgb,
                            isBold = isBold,
                            isItalic = isItalic
                        )
                    )
                }
                pendingEditExistingLine = null
                editingReplacedTextElement = null
                viewModel.setActiveContentTool(ContentEditTool.SELECT)
            },
            onDismiss = {
                pendingEditExistingLine = null
                editingReplacedTextElement = null
            }
        )
    }

    // Content Edit Dialog 3: Transform, Rotate, Crop, Replace Image
    val imgToEdit = editingImageElement
    if (imgToEdit != null) {
        EditImageObjectDialog(
            element = imgToEdit,
            onReplaceFromGallery = {
                replacingImageElementId = imgToEdit.id
                contentImageGalleryLauncher.launch("image/*")
            },
            onReplaceFromCamera = {
                replacingImageElementId = imgToEdit.id
                addOrReplaceImageFromCamera()
            },
            onConfirm = { rotDeg, cropRect ->
                viewModel.updateContentEditElement(
                    imgToEdit.copy(
                        rotationDegrees = rotDeg,
                        cropRect = cropRect
                    )
                )
                editingImageElement = null
            },
            onDismiss = { editingImageElement = null }
        )
    }

    // Content Edit Dialog 4: Add or Edit Link Area
    if (pendingAddLinkRect != null || editingLinkElement != null) {
        val existingLink = editingLinkElement
        val rectPair = pendingAddLinkRect
        AddOrEditLinkDialog(
            initialLink = existingLink,
            totalPages = state.pageCount,
            onConfirm = { linkType, targetUrlOrEmail, targetPageIndex ->
                if (existingLink != null) {
                    viewModel.updateContentEditElement(
                        existingLink.copy(
                            linkType = linkType,
                            targetUrlOrEmail = targetUrlOrEmail,
                            targetPageIndex = targetPageIndex
                        )
                    )
                } else if (rectPair != null) {
                    val (pIdx, rect) = rectPair
                    viewModel.addContentEditElement(
                        ContentEditElement.LinkArea(
                            pageIndex = pIdx,
                            bounds = rect,
                            linkType = linkType,
                            targetUrlOrEmail = targetUrlOrEmail,
                            targetPageIndex = targetPageIndex
                        )
                    )
                }
                pendingAddLinkRect = null
                editingLinkElement = null
                viewModel.setActiveContentTool(ContentEditTool.SELECT)
            },
            onDismiss = {
                pendingAddLinkRect = null
                editingLinkElement = null
            }
        )
    }

    // Content Edit Dialog 5: Save Content Edits & Apply Redactions
    if (isSaveContentEditsDialogOpen || isSavingContentEdits) {
        SaveContentEditsDialog(
            fileName = state.fileName,
            edits = contentEdits,
            isSaving = isSavingContentEdits,
            progressMessage = contentEditProgressMessage,
            onSaveAsCopy = onSaveContentEditsAsCopyRequested,
            onSaveOverwrite = onSaveContentEditsOverwriteRequested,
            onDismiss = {
                viewModel.setSaveContentEditsDialogOpen(false)
            }
        )
    }

    if (showViewerCompressDialog) {
        CompressPdfDialog(
            initialUri = state.uri,
            initialFileName = state.fileName,
            viewModel = viewModel,
            onDismiss = { showViewerCompressDialog = false }
        )
    }

    if (showViewerProtectDialog) {
        ProtectPdfDialog(
            initialUri = state.uri,
            initialFileName = state.fileName,
            viewModel = viewModel,
            onDismiss = { showViewerProtectDialog = false },
            initialCachedFile = viewModel.currentViewingCachedFile,
            initialMode = viewerProtectInitialMode
        )
    }

    if (showViewerUnlockDialog) {
        UnlockPdfDialog(
            initialUri = state.uri,
            initialFileName = state.fileName,
            viewModel = viewModel,
            onDismiss = { showViewerUnlockDialog = false },
            initialCachedFile = viewModel.currentViewingCachedFile
        )
    }

    if (showViewerMetadataDialog) {
        MetadataEditorDialog(
            initialUri = state.uri,
            initialFileName = state.fileName,
            viewModel = viewModel,
            onDismiss = { showViewerMetadataDialog = false }
        )
    }

    if (showViewerWatermarkDialog) {
        WatermarkDialog(
            initialUri = state.uri,
            initialFileName = state.fileName,
            viewModel = viewModel,
            onDismiss = { showViewerWatermarkDialog = false }
        )
    }

    if (showViewerPrintDialog) {
        PrintPdfDialog(
            initialUri = state.uri,
            initialFileName = state.fileName,
            initialFile = viewModel.currentViewingCachedFile,
            currentPageIndex = currentPage,
            onDismiss = { showViewerPrintDialog = false }
        )
    }

    if (showViewerConvertOfficeDialog) {
        PdfToWordOfficeDialog(
            initialUri = state.uri,
            initialFileName = state.fileName,
            initialCachedFile = viewModel.currentViewingCachedFile,
            onConvertConfirmed = { srcFile, srcUri, srcName, config ->
                showViewerConvertOfficeDialog = false
                viewModel.executePdfToWordOrOffice(srcFile, srcUri, srcName, config)
            },
            onDismiss = { showViewerConvertOfficeDialog = false }
        )
    }

    if (operationProgress is OperationProgress.Running) {
        OperationProgressDialog(
            progress = operationProgress as OperationProgress.Running,
            onCancel = { viewModel.cancelActiveOperation() }
        )
    }

    if (operationProgress is OperationProgress.Success) {
        OperationResultDialog(
            result = operationProgress as OperationProgress.Success,
            onOpenPdf = { file -> viewModel.openPdf(Uri.fromFile(file)) },
            onDismiss = { viewModel.dismissOperationDialog() }
        )
    }

    if (operationProgress is OperationProgress.Failure) {
        OperationFailureDialog(
            failure = operationProgress as OperationProgress.Failure,
            onDismiss = { viewModel.dismissOperationDialog() }
        )
    }

    if (showViewerCropDialog) {
        PdfCropDialog(
            initialUri = state.uri,
            initialName = state.fileName,
            initialPageIndex = currentPage,
            totalPageCount = state.pageCount,
            cachedFile = viewModel.currentViewingCachedFile,
            getPageBitmap = { pIdx, w -> viewModel.getPageBitmap(pIdx, w) },
            onApplyCrop = { pageIdx, normalizedBox, applyToAll, onDone ->
                viewModel.cropPageInViewer(pageIdx, normalizedBox, applyToAll) { croppedFile ->
                    Toast.makeText(context, "Page ${pageIdx + 1} cropped successfully", Toast.LENGTH_SHORT).show()
                    onDone(croppedFile)
                }
            },
            onOpenInViewer = { croppedFile ->
                viewModel.reloadEditedPdfInViewer(croppedFile)
            },
            onDismiss = { showViewerCropDialog = false }
        )
    }

    if (showViewerRotatePagesDialog) {
        RotatePdfPagesDialog(
            initialUri = state.uri,
            initialName = state.fileName,
            initialCachedFile = viewModel.currentViewingCachedFile,
            initialCurrentPage = currentPage,
            onRotateConfirmed = { srcFile, srcUri, srcName, deltas, overwrite, outName ->
                showViewerRotatePagesDialog = false
                viewModel.executeRotateSelectedPages(
                    sourceFile = srcFile,
                    sourceUri = srcUri,
                    sourceName = srcName,
                    pageRotationDeltas = deltas,
                    overwriteOriginal = overwrite,
                    outputName = outName
                )
            },
            onDismiss = { showViewerRotatePagesDialog = false }
        )
    }

    // First-use Cloud AI Consent Dialog
    if (isAiConsentDialogOpen) {
        AiConsentDialog(
            onAcceptConsent = { viewModel.acceptAiConsent() },
            onDeclineAndTurnOff = { viewModel.declineAiConsentAndTurnOff() },
            onDismiss = { viewModel.dismissAiConsentDialog() }
        )
    }

    // Extra Confirmation Dialog for Password-Protected PDFs before sending text to Cloud AI
    if (isAiPasswordConfirmDialogOpen) {
        AiPasswordProtectedConfirmDialog(
            fileName = state.fileName,
            onConfirmSendProtected = { viewModel.confirmPasswordProtectedAiUse() },
            onDismiss = { viewModel.dismissPasswordProtectedAiDialog() }
        )
    }

    // Gemini AI Assistant Sheet (Summary, Chat with PDF, Translate, Explain/Rewrite)
    if (isAiSheetOpen) {
        PdfAiAssistantSheet(
            viewModel = viewModel,
            onJumpToPage = { targetPageIdx ->
                val clamped = targetPageIdx.coerceIn(0, (state.pageCount - 1).coerceAtLeast(0))
                coroutineScope.launch {
                    when (readingMode) {
                        ReadingMode.CONTINUOUS -> listState.scrollToItem(clamped)
                        ReadingMode.SINGLE_PAGE -> singlePagerState.scrollToPage(clamped)
                        ReadingMode.TWO_PAGE_SPREAD -> spreadPagerState.scrollToPage(clamped / 2)
                    }
                    viewModel.onPageChanged(clamped)
                }
            },
            onDismiss = { viewModel.closeAiAssistantSheet() }
        )
    }

    // ML Kit OCR Selectable Text Extractor Sheet
    if (isOcrSheetOpen) {
        PdfOcrTextSheet(
            currentPageIndex = currentPage,
            totalPages = state.pageCount,
            isRunningOcr = isOcrRunningForPage,
            currentPageOcrResult = pageOcrResults[currentPage],
            onSelectPage = { targetPage ->
                val clamped = targetPage.coerceIn(0, (state.pageCount - 1).coerceAtLeast(0))
                coroutineScope.launch {
                    when (readingMode) {
                        ReadingMode.CONTINUOUS -> listState.scrollToItem(clamped)
                        ReadingMode.SINGLE_PAGE -> singlePagerState.scrollToPage(clamped)
                        ReadingMode.TWO_PAGE_SPREAD -> spreadPagerState.scrollToPage(clamped / 2)
                    }
                    viewModel.onPageChanged(clamped)
                }
            },
            onRescanCurrentPage = { viewModel.runOcrOnPage(currentPage, forceRefresh = true) },
            onExtractAllPages = { onDone -> viewModel.runOcrOnAllDocumentPages(onDone) },
            onMakeSearchablePdf = { viewModel.executeEmbedSearchableOcrInCurrentPdf() },
            onDismiss = { viewModel.closeOcrTextSheet() }
        )
    }
}

@Composable
private fun PdfPageCardWithAnnotations(
    pageIndex: Int,
    viewModel: PdfViewModel,
    annotations: List<PdfAnnotation>,
    activeTool: AnnotationType?,
    activeColorArgb: Int,
    activeThickness: Float,
    selectedAnnotationId: String?,
    isNightMode: Boolean,
    colorFilter: ColorFilter?,
    onTapAddTextAt: (pageIndex: Int, point: NormalizedPoint) -> Unit,
    onTapExistingTextLine: (ExistingTextLine) -> Unit,
    onTapAddLinkAt: (pageIndex: Int, rect: NormalizedRect) -> Unit,
    onExecuteLink: (ContentEditElement.LinkArea) -> Unit,
    modifier: Modifier = Modifier
) {
    val renderVersion by viewModel.documentRenderVersion.collectAsStateWithLifecycle()
    var bitmap by remember(pageIndex, renderVersion) { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember(pageIndex, renderVersion) { mutableStateOf(true) }

    LaunchedEffect(pageIndex, renderVersion) {
        isLoading = true
        bitmap = viewModel.getPageBitmap(pageIndex)
        isLoading = false
    }

    val pageAspectRatio = bitmap?.let {
        (it.width.toFloat() / it.height.toFloat().coerceAtLeast(1f)).coerceIn(0.2f, 5.0f)
    } ?: 0.707f

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isNightMode) Color(0xFF1F1F1F) else Color.White
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(pageAspectRatio)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap!!.asImageBitmap(),
                        contentDescription = "Page ${pageIndex + 1}",
                        colorFilter = colorFilter,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Interactive Annotation Canvas Overlay
                    AnnotationCanvasOverlay(
                        pageIndex = pageIndex,
                        annotations = annotations,
                        activeTool = activeTool,
                        activeColorArgb = activeColorArgb,
                        activeThickness = activeThickness,
                        selectedAnnotationId = selectedAnnotationId,
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Interactive AcroForm & Fill & Sign Overlay
                    val formFields by viewModel.formFields.collectAsStateWithLifecycle()
                    val placedStamps by viewModel.placedStamps.collectAsStateWithLifecycle()
                    val focusedFieldId by viewModel.focusedFieldId.collectAsStateWithLifecycle()
                    val selectedStampId by viewModel.selectedStampId.collectAsStateWithLifecycle()
                    val isFillAndSignActive by viewModel.isFillAndSignActive.collectAsStateWithLifecycle()
                    val activeQuickFillType by viewModel.activeQuickFillType.collectAsStateWithLifecycle()
                    val pendingSnippetText by viewModel.pendingSnippetText.collectAsStateWithLifecycle()

                    FormOverlayLayer(
                        pageIndex = pageIndex,
                        formFields = formFields.filter { it.pageIndex == pageIndex },
                        placedStamps = placedStamps.filter { it.pageIndex == pageIndex },
                        focusedFieldId = focusedFieldId,
                        selectedStampId = selectedStampId,
                        activeQuickFillType = if (isFillAndSignActive) activeQuickFillType else null,
                        pendingSnippetText = pendingSnippetText,
                        onFieldFocused = { viewModel.setFocusedField(it) },
                        onFieldValueChanged = { name, value -> viewModel.updateFieldValue(name, value) },
                        onStampSelected = { viewModel.selectStamp(it) },
                        onStampUpdated = { viewModel.updatePlacedStamp(it) },
                        onStampDeleted = { viewModel.deletePlacedStamp(it) },
                        onPlaceStampAt = { pIdx, normX, normY, type, txt ->
                            val textVal = when (type) {
                                PlacedStampType.DATE -> java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
                                PlacedStampType.TEXT -> "Text"
                                else -> txt
                            }
                            viewModel.placeStamp(
                                pageIndex = pIdx,
                                normX = normX,
                                normY = normY,
                                type = type,
                                textValue = textVal,
                                imageBitmap = null
                            )
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Interactive Content Editing & Redaction Overlay
                    val isContentEditActive by viewModel.isContentEditActive.collectAsStateWithLifecycle()
                    val activeContentTool by viewModel.activeContentTool.collectAsStateWithLifecycle()
                    val contentEdits by viewModel.contentEdits.collectAsStateWithLifecycle()
                    val selectedContentElementId by viewModel.selectedContentElementId.collectAsStateWithLifecycle()
                    val pageExistingTextLines by viewModel.pageExistingTextLines.collectAsStateWithLifecycle()

                    LaunchedEffect(isContentEditActive, pageIndex) {
                        if (isContentEditActive) {
                            viewModel.inspectPageForContentEdit(pageIndex)
                        }
                    }

                    ContentEditPageOverlay(
                        pageIndex = pageIndex,
                        isContentEditActive = isContentEditActive,
                        activeTool = activeContentTool,
                        pageElements = contentEdits.filter { it.pageIndex == pageIndex },
                        existingTextLines = pageExistingTextLines[pageIndex].orEmpty(),
                        selectedElementId = selectedContentElementId,
                        onSelectElement = { viewModel.selectContentElement(it) },
                        onTapAddTextAt = { pt -> onTapAddTextAt(pageIndex, pt) },
                        onTapExistingTextLine = { line -> onTapExistingTextLine(line) },
                        onTapAddLinkAt = { rect -> onTapAddLinkAt(pageIndex, rect) },
                        onDragAddRedaction = { rect ->
                            viewModel.addContentEditElement(
                                ContentEditElement.RedactionMark(
                                    pageIndex = pageIndex,
                                    bounds = rect
                                )
                            )
                        },
                        onUpdateElement = { updated, pushUndo ->
                            viewModel.updateContentEditElement(updated, pushUndo)
                        },
                        onExecuteLink = onExecuteLink,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Interactive On-Page ML Kit OCR Selectable Text Overlay
                    val isOcrSelectionModeActive by viewModel.isOcrSelectionModeActive.collectAsStateWithLifecycle()
                    val pageOcrResults by viewModel.pageOcrResults.collectAsStateWithLifecycle()
                    val selectedOcrLineTexts by viewModel.selectedOcrLineTexts.collectAsStateWithLifecycle()

                    LaunchedEffect(isOcrSelectionModeActive, pageIndex) {
                        if (isOcrSelectionModeActive) {
                            viewModel.runOcrOnPage(pageIndex)
                        }
                    }

                    if (isOcrSelectionModeActive) {
                        OcrSelectablePageOverlay(
                            ocrResult = pageOcrResults[pageIndex],
                            selectedLineTexts = selectedOcrLineTexts,
                            onToggleLineSelection = { viewModel.toggleSelectOcrLine(it) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(if (isNightMode) Color(0xFF282828) else Color(0xFFF0F0F0)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(36.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 3.dp
                            )
                        } else {
                            Text(
                                text = "Page ${pageIndex + 1}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Text(
                text = "${pageIndex + 1}",
                style = MaterialTheme.typography.labelSmall,
                color = if (isNightMode) Color.LightGray else Color.Gray,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun ThumbnailNavigationSidebar(
    totalPages: Int,
    currentPage: Int,
    filterMode: ThumbnailFilterMode,
    bookmarkedPages: Set<Int>,
    annotatedPageCounts: Map<Int, Int>,
    viewModel: PdfViewModel,
    isNightMode: Boolean,
    onSelectFilterMode: (ThumbnailFilterMode) -> Unit,
    onSelectPage: (Int) -> Unit,
    onExpandToBottomSheet: () -> Unit,
    onCloseSidebar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val filteredPages = remember(totalPages, filterMode, bookmarkedPages, annotatedPageCounts) {
        ThumbnailNavigatorHelper.filterThumbnailPages(
            totalPages = totalPages,
            filterMode = filterMode,
            bookmarkedPages = bookmarkedPages,
            annotatedPages = annotatedPageCounts.keys
        )
    }
    val sidebarListState = rememberLazyListState()

    // Auto-scroll sidebar list to keep the active page centered/visible
    LaunchedEffect(currentPage, filteredPages) {
        val targetIdx = filteredPages.indexOf(currentPage)
        if (targetIdx >= 0) {
            sidebarListState.animateScrollToItem((targetIdx - 1).coerceAtLeast(0))
        }
    }

    Surface(
        shape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp),
        color = if (isNightMode) Color(0xF51A1A1A) else MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
        tonalElevation = 8.dp,
        shadowElevation = 12.dp,
        modifier = modifier
            .width(156.dp)
            .fillMaxHeight()
            .padding(top = 8.dp, bottom = 76.dp)
            .testTag("thumbnail_navigation_sidebar")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            // Sidebar Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Thumbnails",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isNightMode) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${currentPage + 1} of $totalPages",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(
                    onClick = onExpandToBottomSheet,
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("btn_sidebar_to_bottom_sheet")
                ) {
                    Icon(
                        imageVector = Icons.Default.GridView,
                        contentDescription = "Open Full Grid Bottom Sheet",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = onCloseSidebar,
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("btn_close_thumbnail_sidebar")
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Collapse Thumbnail Sidebar",
                        tint = if (isNightMode) Color.LightGray else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Compact Filter Selector Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ThumbnailFilterMode.entries.forEach { mode ->
                    val isActive = filterMode == mode
                    val shortLabel = when (mode) {
                        ThumbnailFilterMode.ALL_PAGES -> "All"
                        ThumbnailFilterMode.BOOKMARKED -> "★"
                        ThumbnailFilterMode.ANNOTATED -> "✎"
                    }
                    Surface(
                        onClick = { onSelectFilterMode(mode) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isActive) MaterialTheme.colorScheme.primaryContainer
                        else if (isNightMode) Color(0xFF2B2B2B)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("sidebar_filter_${mode.name.lowercase()}")
                    ) {
                        Text(
                            text = shortLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                            color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer
                            else if (isNightMode) Color.LightGray
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(bottom = 6.dp))

            if (filteredPages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when (filterMode) {
                            ThumbnailFilterMode.BOOKMARKED -> "No bookmarked pages yet"
                            ThumbnailFilterMode.ANNOTATED -> "No annotated pages yet"
                            ThumbnailFilterMode.ALL_PAGES -> "No pages"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    state = sidebarListState,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("thumbnail_sidebar_list")
                ) {
                    items(
                        items = filteredPages,
                        key = { it }
                    ) { pageIndex ->
                        SidebarThumbnailItem(
                            pageIndex = pageIndex,
                            isSelected = pageIndex == currentPage,
                            isBookmarked = pageIndex in bookmarkedPages,
                            annotationCount = annotatedPageCounts[pageIndex] ?: 0,
                            viewModel = viewModel,
                            isNightMode = isNightMode,
                            onClick = { onSelectPage(pageIndex) },
                            onToggleBookmark = { viewModel.togglePageBookmark(pageIndex) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SidebarThumbnailItem(
    pageIndex: Int,
    isSelected: Boolean,
    isBookmarked: Boolean,
    annotationCount: Int,
    viewModel: PdfViewModel,
    isNightMode: Boolean,
    onClick: () -> Unit,
    onToggleBookmark: () -> Unit,
    modifier: Modifier = Modifier
) {
    val renderVersion by viewModel.documentRenderVersion.collectAsStateWithLifecycle()
    var thumbnail by remember(pageIndex, renderVersion) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(pageIndex, renderVersion) {
        thumbnail = viewModel.getThumbnailBitmap(pageIndex)
    }

    val thumbAspectRatio = thumbnail?.let {
        (it.width.toFloat() / it.height.toFloat().coerceAtLeast(1f)).coerceIn(0.45f, 2.2f)
    } ?: 0.707f

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .testTag("sidebar_thumbnail_item_${pageIndex + 1}")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(thumbAspectRatio)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isNightMode) Color(0xFF2C2C2C) else Color(0xFFECEFF4))
                .border(
                    width = if (isSelected) 2.5.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(8.dp)
                )
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail!!.asImageBitmap(),
                    contentDescription = "Sidebar Thumbnail Page ${pageIndex + 1}",
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = "${pageIndex + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.Gray
                )
            }

            // Bookmark Toggle Badge (Top-End)
            Surface(
                onClick = onToggleBookmark,
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.45f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(22.dp)
                    .testTag("sidebar_bookmark_toggle_${pageIndex + 1}")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = if (isBookmarked) "Remove bookmark page ${pageIndex + 1}" else "Bookmark page ${pageIndex + 1}",
                        tint = if (isBookmarked) Color(0xFFFFCA28) else Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            // Annotation Count Badge (Bottom-Start)
            if (annotationCount > 0) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.92f),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                ) {
                    Text(
                        text = "✎ $annotationCount",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
        }

        // Page Number Pill under Thumbnail
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
            modifier = Modifier.padding(top = 3.dp)
        ) {
            Text(
                text = "Page ${pageIndex + 1}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                else if (isNightMode) Color.LightGray
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThumbnailBottomDrawer(
    totalPages: Int,
    currentPage: Int,
    filterMode: ThumbnailFilterMode,
    sections: List<DocumentSection>,
    activeSection: DocumentSection?,
    selectedSectionId: String?,
    bookmarkedPages: Set<Int>,
    annotatedPageCounts: Map<Int, Int>,
    viewModel: PdfViewModel,
    isNightMode: Boolean,
    onSelectFilterMode: (ThumbnailFilterMode) -> Unit,
    onSelectSection: (DocumentSection?) -> Unit,
    onSelectPage: (Int) -> Unit,
    onExpandToSheet: () -> Unit,
    onDockToSidebar: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedSection = remember(sections, selectedSectionId) {
        sections.firstOrNull { it.id == selectedSectionId }
    }
    val filteredPages = remember(totalPages, filterMode, bookmarkedPages, annotatedPageCounts, selectedSection) {
        ThumbnailNavigatorHelper.filterThumbnailPages(
            totalPages = totalPages,
            filterMode = filterMode,
            bookmarkedPages = bookmarkedPages,
            annotatedPages = annotatedPageCounts.keys,
            sectionFilter = selectedSection
        )
    }

    val rowListState = rememberLazyListState()
    val sectionRowState = rememberLazyListState()

    // Auto-scroll horizontal thumbnail strip to currentPage
    LaunchedEffect(currentPage, filteredPages) {
        val idx = filteredPages.indexOf(currentPage)
        if (idx >= 0) {
            runCatching { rowListState.scrollToItem((idx - 1).coerceAtLeast(0)) }
        }
    }

    // Auto-scroll section row to active section (index + 1 because index 0 is "All")
    LaunchedEffect(activeSection?.id, sections) {
        val activeIdx = sections.indexOfFirst { it.id == activeSection?.id }
        if (activeIdx >= 0) {
            runCatching { sectionRowState.scrollToItem(activeIdx + 1) }
        }
    }

    Surface(
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        color = if (isNightMode) Color(0xFF1E1E1E) else MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        shadowElevation = 14.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("thumbnail_bottom_drawer")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // Drag Handle Pill
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 6.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
            )

            // Header Row: Active Section & Page Info + View Switchers + Close
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = activeSection?.title ?: "Page Thumbnails",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isNightMode) Color.White else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (activeSection != null) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = activeSection.subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = "Page ${currentPage + 1} of $totalPages • ${sections.size} ${if (sections.size == 1) "section" else "sections"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TextButton(
                        onClick = onExpandToSheet,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("btn_drawer_expand_to_sheet")
                    ) {
                        Icon(
                            imageVector = Icons.Default.GridView,
                            contentDescription = "Expand to full thumbnail grid",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Grid", style = MaterialTheme.typography.labelMedium)
                    }
                    IconButton(
                        onClick = onDockToSidebar,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("btn_drawer_dock_to_sidebar")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ViewSidebar,
                            contentDescription = "Dock thumbnails to sidebar",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("btn_close_thumbnail_bottom_drawer")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close bottom thumbnail drawer",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Section Jump Bar for Long Documents
            LazyRow(
                state = sectionRowState,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .testTag("thumbnail_drawer_section_bar")
            ) {
                item(key = "all_sections") {
                    FilterChip(
                        selected = selectedSectionId == null,
                        onClick = { onSelectSection(null) },
                        label = {
                            Text(
                                text = "All ($totalPages)",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        modifier = Modifier.testTag("drawer_section_chip_all")
                    )
                }
                itemsIndexed(
                    items = sections,
                    key = { _, sec -> sec.id }
                ) { index, section ->
                    val isSelectedSection = selectedSectionId == section.id ||
                        (selectedSectionId == null && activeSection?.id == section.id)
                    FilterChip(
                        selected = isSelectedSection,
                        onClick = { onSelectSection(section) },
                        label = {
                            Text(
                                text = "${section.title} (${section.subtitle})",
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        modifier = Modifier.testTag("drawer_section_chip_$index")
                    )
                }
            }

            // Fast Page Scrubber & Filter Row
            if (totalPages > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val activeSecIdx = sections.indexOfFirst { it.id == activeSection?.id }
                    IconButton(
                        onClick = {
                            if (activeSecIdx > 0) {
                                onSelectSection(sections[activeSecIdx - 1])
                            } else {
                                onSelectPage((currentPage - 1).coerceAtLeast(0))
                            }
                        },
                        enabled = currentPage > 0,
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("btn_drawer_prev_section")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = "Previous section",
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Slider(
                        value = currentPage.toFloat().coerceIn(0f, (totalPages - 1).coerceAtLeast(1).toFloat()),
                        onValueChange = { newValue ->
                            val target = newValue.toInt().coerceIn(0, totalPages - 1)
                            if (target != currentPage) {
                                onSelectPage(target)
                            }
                        },
                        valueRange = 0f..(totalPages - 1).coerceAtLeast(1).toFloat(),
                        modifier = Modifier
                            .weight(1f)
                            .height(24.dp)
                            .testTag("thumbnail_drawer_scrubber")
                    )

                    IconButton(
                        onClick = {
                            if (activeSecIdx >= 0 && activeSecIdx + 1 < sections.size) {
                                onSelectSection(sections[activeSecIdx + 1])
                            } else {
                                onSelectPage((currentPage + 1).coerceAtMost(totalPages - 1))
                            }
                        },
                        enabled = currentPage < totalPages - 1,
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("btn_drawer_next_section")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Next section",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Quick Filter Chips (All / Bookmarked / Annotated)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = filterMode == ThumbnailFilterMode.ALL_PAGES,
                    onClick = { onSelectFilterMode(ThumbnailFilterMode.ALL_PAGES) },
                    label = { Text("All Pages", style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.testTag("drawer_filter_all")
                )
                FilterChip(
                    selected = filterMode == ThumbnailFilterMode.BOOKMARKED,
                    onClick = { onSelectFilterMode(ThumbnailFilterMode.BOOKMARKED) },
                    label = { Text("Saved (${bookmarkedPages.size})", style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.testTag("drawer_filter_bookmarked")
                )
                FilterChip(
                    selected = filterMode == ThumbnailFilterMode.ANNOTATED,
                    onClick = { onSelectFilterMode(ThumbnailFilterMode.ANNOTATED) },
                    label = { Text("Marked (${annotatedPageCounts.size})", style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.testTag("drawer_filter_annotated")
                )
            }

            // Horizontal Scrollable Thumbnail Strip
            if (filteredPages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when (filterMode) {
                            ThumbnailFilterMode.BOOKMARKED -> "No bookmarked pages in this section."
                            ThumbnailFilterMode.ANNOTATED -> "No annotated pages in this section."
                            ThumbnailFilterMode.ALL_PAGES -> "No pages in this section."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyRow(
                    state = rowListState,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(136.dp)
                        .testTag("thumbnail_bottom_drawer_list")
                ) {
                    items(
                        items = filteredPages,
                        key = { it }
                    ) { pageIndex ->
                        DrawerThumbnailCard(
                            pageIndex = pageIndex,
                            isSelected = pageIndex == currentPage,
                            isBookmarked = pageIndex in bookmarkedPages,
                            annotationCount = annotatedPageCounts[pageIndex] ?: 0,
                            viewModel = viewModel,
                            isNightMode = isNightMode,
                            onClick = { onSelectPage(pageIndex) },
                            onToggleBookmark = { viewModel.togglePageBookmark(pageIndex) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerThumbnailCard(
    pageIndex: Int,
    isSelected: Boolean,
    isBookmarked: Boolean,
    annotationCount: Int,
    viewModel: PdfViewModel,
    isNightMode: Boolean,
    onClick: () -> Unit,
    onToggleBookmark: () -> Unit,
    modifier: Modifier = Modifier
) {
    val renderVersion by viewModel.documentRenderVersion.collectAsStateWithLifecycle()
    var thumbnail by remember(pageIndex, renderVersion) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(pageIndex, renderVersion) {
        thumbnail = viewModel.getThumbnailBitmap(pageIndex)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(82.dp)
            .testTag("drawer_thumbnail_item_${pageIndex + 1}")
    ) {
        Box(
            modifier = Modifier
                .width(78.dp)
                .height(104.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isNightMode) Color(0xFF2C2C2C) else Color(0xFFECEFF4))
                .border(
                    width = if (isSelected) 2.5.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(8.dp)
                )
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail!!.asImageBitmap(),
                    contentDescription = "Bottom Drawer Thumbnail Page ${pageIndex + 1}",
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = "${pageIndex + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.Gray
                )
            }

            // Bookmark Toggle Badge (Top-End)
            Surface(
                onClick = onToggleBookmark,
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.45f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(3.dp)
                    .size(20.dp)
                    .testTag("drawer_bookmark_toggle_${pageIndex + 1}")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = if (isBookmarked) "Remove bookmark page ${pageIndex + 1}" else "Bookmark page ${pageIndex + 1}",
                        tint = if (isBookmarked) Color(0xFFFFCA28) else Color.White,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            // Annotation Count Badge (Bottom-Start)
            if (annotationCount > 0) {
                Surface(
                    shape = RoundedCornerShape(5.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.92f),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(3.dp)
                ) {
                    Text(
                        text = "✎ $annotationCount",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
        }

        // Page Number Label
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
            modifier = Modifier.padding(top = 3.dp)
        ) {
            Text(
                text = "P.${pageIndex + 1}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                else if (isNightMode) Color.LightGray
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThumbnailSheetContent(
    totalPages: Int,
    currentPage: Int,
    filterMode: ThumbnailFilterMode,
    sections: List<DocumentSection>,
    activeSection: DocumentSection?,
    selectedSectionId: String?,
    bookmarkedPages: Set<Int>,
    annotatedPageCounts: Map<Int, Int>,
    viewModel: PdfViewModel,
    isNightMode: Boolean,
    onSelectFilterMode: (ThumbnailFilterMode) -> Unit,
    onSelectSection: (DocumentSection?) -> Unit,
    onDockToBottomDrawer: () -> Unit,
    onDockToSidebar: () -> Unit,
    onSelectPage: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var pageSearchQuery by remember { mutableStateOf("") }
    val selectedSection = remember(sections, selectedSectionId) {
        sections.firstOrNull { it.id == selectedSectionId }
    }
    val filteredPages = remember(totalPages, filterMode, bookmarkedPages, annotatedPageCounts, pageSearchQuery, selectedSection) {
        ThumbnailNavigatorHelper.filterThumbnailPages(
            totalPages = totalPages,
            filterMode = filterMode,
            bookmarkedPages = bookmarkedPages,
            annotatedPages = annotatedPageCounts.keys,
            pageQuery = pageSearchQuery,
            sectionFilter = selectedSection
        )
    }

    val gridState = rememberLazyGridState()

    // Auto-scroll thumbnail grid to current page when sheet opens or section changes
    LaunchedEffect(currentPage, selectedSectionId) {
        val idx = filteredPages.indexOf(currentPage)
        if (idx >= 0) {
            gridState.scrollToItem((idx - 2).coerceAtLeast(0))
        } else if (filteredPages.isNotEmpty()) {
            gridState.scrollToItem(0)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Header Row: Title + Dock as Bottom Drawer / Sidebar + Drag & Drop Reorder
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Page Thumbnails ($totalPages)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isNightMode) Color.White else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Page ${currentPage + 1} • ${activeSection?.let { "${it.title} (${it.subtitle})" } ?: "Tap any thumbnail to jump"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = onDockToBottomDrawer,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("btn_switch_to_thumbnail_bottom_drawer")
                ) {
                    Icon(
                        imageVector = Icons.Default.ViewDay,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Bottom Bar", style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(
                    onClick = onDockToSidebar,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("btn_switch_to_thumbnail_sidebar")
                ) {
                    Icon(
                        imageVector = Icons.Default.ViewSidebar,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Sidebar", style = MaterialTheme.typography.labelMedium)
                }
                FilledTonalButton(
                    onClick = {
                        viewModel.setThumbnailSheetOpen(false)
                        viewModel.setPageOrganizerOpen(true)
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("btn_thumbnail_sheet_organize")
                ) {
                    Icon(
                        imageVector = Icons.Default.ViewCarousel,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reorder", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        // Document Section Navigation Bar for Long Documents
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .testTag("thumbnail_sheet_section_bar")
        ) {
            item(key = "sheet_all_sections") {
                FilterChip(
                    selected = selectedSectionId == null,
                    onClick = { onSelectSection(null) },
                    label = { Text("All Sections ($totalPages)", style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.testTag("sheet_section_chip_all")
                )
            }
            itemsIndexed(
                items = sections,
                key = { _, sec -> sec.id }
            ) { index, section ->
                val isSelectedSection = selectedSectionId == section.id ||
                    (selectedSectionId == null && activeSection?.id == section.id)
                FilterChip(
                    selected = isSelectedSection,
                    onClick = { onSelectSection(section) },
                    label = {
                        Text(
                            text = "${section.title} (${section.subtitle})",
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    modifier = Modifier.testTag("sheet_section_chip_$index")
                )
            }
        }

        // Filter Chips + Quick Page Number Filter
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = filterMode == ThumbnailFilterMode.ALL_PAGES,
                onClick = { onSelectFilterMode(ThumbnailFilterMode.ALL_PAGES) },
                label = { Text("All ($totalPages)") },
                modifier = Modifier.testTag("chip_thumb_filter_all")
            )
            FilterChip(
                selected = filterMode == ThumbnailFilterMode.BOOKMARKED,
                onClick = { onSelectFilterMode(ThumbnailFilterMode.BOOKMARKED) },
                label = { Text("Bookmarked (${bookmarkedPages.size})") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Bookmark,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                },
                modifier = Modifier.testTag("chip_thumb_filter_bookmarked")
            )
            FilterChip(
                selected = filterMode == ThumbnailFilterMode.ANNOTATED,
                onClick = { onSelectFilterMode(ThumbnailFilterMode.ANNOTATED) },
                label = { Text("Annotated (${annotatedPageCounts.size})") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Draw,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                },
                modifier = Modifier.testTag("chip_thumb_filter_annotated")
            )
        }

        // Quick Page Scrubber / Search Bar for Multi-page Documents
        if (totalPages > 4) {
            OutlinedTextField(
                value = pageSearchQuery,
                onValueChange = { pageSearchQuery = it },
                placeholder = { Text("Filter by page number (1–$totalPages)...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (pageSearchQuery.isNotEmpty()) {
                        IconButton(onClick = { pageSearchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear page filter",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .testTag("thumbnail_sheet_page_search_input")
            )
        }

        if (filteredPages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.GridView,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = when (filterMode) {
                            ThumbnailFilterMode.BOOKMARKED -> "No bookmarked pages in this PDF yet."
                            ThumbnailFilterMode.ANNOTATED -> "No annotated pages in this PDF yet."
                            ThumbnailFilterMode.ALL_PAGES -> "No matching pages found."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = 104.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(440.dp)
                    .testTag("thumbnail_bottom_sheet_grid")
            ) {
                items(
                    items = filteredPages,
                    key = { it }
                ) { pageIndex ->
                    ThumbnailItem(
                        pageIndex = pageIndex,
                        totalPages = totalPages,
                        isSelected = pageIndex == currentPage,
                        isBookmarked = pageIndex in bookmarkedPages,
                        annotationCount = annotatedPageCounts[pageIndex] ?: 0,
                        viewModel = viewModel,
                        isNightMode = isNightMode,
                        onClick = { onSelectPage(pageIndex) },
                        onToggleBookmark = { viewModel.togglePageBookmark(pageIndex) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ThumbnailItem(
    pageIndex: Int,
    totalPages: Int,
    isSelected: Boolean,
    isBookmarked: Boolean,
    annotationCount: Int,
    viewModel: PdfViewModel,
    isNightMode: Boolean,
    onClick: () -> Unit,
    onToggleBookmark: () -> Unit,
    modifier: Modifier = Modifier
) {
    val renderVersion by viewModel.documentRenderVersion.collectAsStateWithLifecycle()
    var thumbnail by remember(pageIndex, renderVersion) { mutableStateOf<Bitmap?>(null) }
    val context = LocalContext.current

    LaunchedEffect(pageIndex, renderVersion) {
        thumbnail = viewModel.getThumbnailBitmap(pageIndex)
    }

    val thumbAspectRatio = thumbnail?.let {
        (it.width.toFloat() / it.height.toFloat().coerceAtLeast(1f)).coerceIn(0.3f, 3.0f)
    } ?: 0.707f

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.testTag("sheet_thumbnail_item_${pageIndex + 1}")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(thumbAspectRatio)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isNightMode) Color(0xFF2C2C2C) else Color(0xFFE5E5E5))
                .border(
                    width = if (isSelected) 3.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp)
                )
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            if (thumbnail != null) {
                Image(
                    bitmap = thumbnail!!.asImageBitmap(),
                    contentDescription = "Thumbnail ${pageIndex + 1}",
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = "${pageIndex + 1}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }

            // Current Page Badge (Top-Start)
            if (isSelected) {
                Surface(
                    shape = RoundedCornerShape(bottomEnd = 8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = "CURRENT",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Bookmark Toggle Button (Top-End)
            Surface(
                onClick = onToggleBookmark,
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.45f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(24.dp)
                    .testTag("btn_thumbnail_bookmark_${pageIndex + 1}")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = if (isBookmarked) "Remove bookmark page ${pageIndex + 1}" else "Bookmark page ${pageIndex + 1}",
                        tint = if (isBookmarked) Color(0xFFFFCA28) else Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // Annotation Count Badge (Bottom-Start)
            if (annotationCount > 0) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.92f),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                ) {
                    Text(
                        text = "✎ $annotationCount",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    viewModel.rotatePageInViewer(pageIndex, clockwise = false) {
                        Toast.makeText(context, "Page ${pageIndex + 1} rotated 90° CCW", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .size(24.dp)
                    .testTag("btn_thumbnail_rotate_ccw_${pageIndex + 1}")
            ) {
                Icon(
                    imageVector = Icons.Default.RotateLeft,
                    contentDescription = "Rotate CCW",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = "${pageIndex + 1}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )

            IconButton(
                onClick = {
                    viewModel.rotatePageInViewer(pageIndex, clockwise = true) {
                        Toast.makeText(context, "Page ${pageIndex + 1} rotated 90° CW", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .size(24.dp)
                    .testTag("btn_thumbnail_rotate_cw_${pageIndex + 1}")
            ) {
                Icon(
                    imageVector = Icons.Default.RotateRight,
                    contentDescription = "Rotate CW",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (totalPages > 1) {
                IconButton(
                    onClick = {
                        viewModel.deletePageInViewer(pageIndex) {
                            Toast.makeText(context, "Deleted page ${pageIndex + 1}", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .size(24.dp)
                        .testTag("btn_thumbnail_delete_${pageIndex + 1}")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete page ${pageIndex + 1}",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun JumpToPageDialog(
    currentPage: Int,
    totalPages: Int,
    onDismiss: () -> Unit,
    onJump: (Int) -> Unit
) {
    var targetPage by remember { mutableIntStateOf(currentPage) }
    var inputText by remember { mutableStateOf(currentPage.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Jump to Page") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "Page $targetPage of $totalPages",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                if (totalPages > 1) {
                    Slider(
                        value = targetPage.toFloat(),
                        onValueChange = {
                            targetPage = it.toInt()
                            inputText = it.toInt().toString()
                        },
                        valueRange = 1f..totalPages.toFloat(),
                        steps = if (totalPages > 2) (totalPages - 2).coerceAtMost(50) else 0
                    )
                }

                OutlinedTextField(
                    value = inputText,
                    onValueChange = { value ->
                        val digits = value.filter { it.isDigit() }
                        inputText = digits
                        digits.toIntOrNull()?.let { num ->
                            if (num in 1..totalPages) {
                                targetPage = num
                            }
                        }
                    },
                    label = { Text("Enter page number (1-$totalPages)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { onJump(targetPage) }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("jump_page_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onJump(targetPage) },
                modifier = Modifier.testTag("jump_page_confirm")
            ) {
                Text("Go to page")
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
private fun PasswordRequiredDialog(
    uri: Uri,
    fileName: String,
    viewModel: PdfViewModel,
    onUnlock: (password: String, removePermanently: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val uriString = uri.toString()
    val hasSavedBiometric = remember(uriString) { viewModel.hasSavedBiometricPassword(uriString) }

    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var removePermanently by remember { mutableStateOf(false) }
    var rememberWithBiometrics by remember { mutableStateOf(hasSavedBiometric) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Password protected",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = "Password Protected PDF",
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (hasSavedBiometric) {
                    Button(
                        onClick = {
                            viewModel.unlockWithSavedBiometricPassword(activity, uriString)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("btn_biometric_unlock_doc")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Unlock with Biometrics")
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f))
                        Text(
                            text = " or enter password ",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f))
                    }
                } else {
                    Text(
                        text = "This document is encrypted. Enter password to view.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("viewer_password_input"),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle password visibility"
                            )
                        }
                    }
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { rememberWithBiometrics = !rememberWithBiometrics }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = rememberWithBiometrics,
                        onCheckedChange = { rememberWithBiometrics = it }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Remember with Biometrics (Fingerprint unlock next time)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { removePermanently = !removePermanently }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = removePermanently,
                        onCheckedChange = { removePermanently = it }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Remove password permanently",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (password.isNotBlank()) {
                        if (rememberWithBiometrics && !removePermanently) {
                            viewModel.saveBiometricPasswordForDocument(uriString, password)
                        } else if (hasSavedBiometric && removePermanently) {
                            viewModel.removeBiometricPasswordForDocument(uriString)
                        }
                        onUnlock(password, removePermanently)
                    }
                },
                enabled = password.isNotBlank(),
                modifier = Modifier.testTag("password_dialog_unlock_btn")
            ) {
                Text("Unlock")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("password_dialog_cancel")
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ViewerErrorView(
    message: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.9f),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = "Error",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(54.dp)
                )
                Text(
                    text = "Unable to Read PDF",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Button(
                    onClick = onBack,
                    modifier = Modifier.testTag("error_back_button")
                ) {
                    Text("Return to Library")
                }
            }
        }
    }
}
