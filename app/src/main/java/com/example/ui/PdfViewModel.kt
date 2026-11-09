package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.AppLanguage
import com.example.data.AppSettings
import com.example.data.AppThemeMode
import com.example.data.BookmarkEntity
import com.example.data.HomeSortOrder
import com.example.data.PdfRepository
import com.example.data.RecentPdfEntity
import com.example.data.ReadingMode
import com.example.data.SettingsManager
import com.example.ui.common.GlobalErrorManager
import com.example.ui.common.GlobalErrorReport
import com.example.pdf.AnnotationType
import com.example.pdf.ExportDpi
import com.example.pdf.ExportImageFormat
import com.example.pdf.ImageQualityPreset
import com.example.pdf.MergeItem
import com.example.pdf.OperationProgress
import com.example.pdf.OrganizedPage
import com.example.pdf.PageMarginPreset
import com.example.pdf.PageOrientationPreset
import com.example.pdf.PageSizePreset
import com.example.pdf.PdfAnnotation
import com.example.pdf.PdfAnnotationEngine
import com.example.pdf.PdfBoxHelper
import com.example.pdf.PdfDocumentManager
import com.example.pdf.PdfLoadResult
import com.example.pdf.PdfOperationEngine
import com.example.pdf.PdfOperationService
import com.example.pdf.PdfOutlineEntry
import com.example.pdf.SplitDestination
import com.example.pdf.SplitMode
import com.example.pdf.PdfTtsManager
import com.example.pdf.SaveAnnotationProgress
import com.example.pdf.SearchMatch
import com.example.pdf.SearchProgress
import com.example.pdf.FormFieldItem
import com.example.pdf.FormFieldType
import com.example.pdf.NormalizedRect
import com.example.pdf.PdfFormEngine
import com.example.pdf.PlacedStampItem
import com.example.pdf.PlacedStampType
import com.example.pdf.SavedSignature
import com.example.pdf.SignatureManager
import com.example.pdf.BundledEditFont
import com.example.pdf.ContentEditElement
import com.example.pdf.ContentEditTool
import com.example.pdf.ExistingTextLine
import com.example.pdf.PdfContentEditEngine
import com.example.pdf.PdfConversionConfig
import com.example.pdf.PdfDocumentConverterEngine
import com.example.pdf.PdfLinkType
import com.example.pdf.TextAlignmentMode
import com.example.ai.AiChatMessage
import com.example.ai.AiExplainMode
import com.example.ai.AiExplainResult
import com.example.ai.AiOutputLanguage
import com.example.ai.AiSummaryResult
import com.example.ai.AiToolTab
import com.example.ai.AiTranslationResult
import com.example.ai.PdfAiAssistantEngine
import com.example.ai.PdfTextChunk
import com.example.scan.OcrPageResult
import com.example.scan.ScanOcrEngine
import com.example.ui.viewer.UniversalViewerState
import com.example.viewer.ParsedUniversalFile
import com.example.viewer.UniversalFileParserEngine
import com.example.viewer.UniversalFileType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

enum class BottomNavTab {
    HOME, FILES, TOOLS, SCAN, PROFILE
}

enum class ThumbnailFilterMode(val label: String) {
    ALL_PAGES("All Pages"),
    BOOKMARKED("Bookmarked"),
    ANNOTATED("Annotated")
}

data class DocumentSection(
    val id: String,
    val title: String,
    val subtitle: String,
    val startPage: Int,
    val endPage: Int,
    val isOutlineChapter: Boolean = false
) {
    val pageCount: Int get() = (endPage - startPage + 1).coerceAtLeast(1)
    val pageRangeLabel: String
        get() = if (startPage == endPage) "P.${startPage + 1}" else "P.${startPage + 1}–${endPage + 1}"
}

object ThumbnailNavigatorHelper {
    fun filterThumbnailPages(
        totalPages: Int,
        filterMode: ThumbnailFilterMode,
        bookmarkedPages: Set<Int>,
        annotatedPages: Set<Int>,
        pageQuery: String = "",
        sectionFilter: DocumentSection? = null
    ): List<Int> {
        if (totalPages <= 0) return emptyList()
        val rangePages = if (sectionFilter != null) {
            val start = sectionFilter.startPage.coerceIn(0, totalPages - 1)
            val end = sectionFilter.endPage.coerceIn(start, totalPages - 1)
            (start..end).toList()
        } else {
            (0 until totalPages).toList()
        }
        val basePages = when (filterMode) {
            ThumbnailFilterMode.ALL_PAGES -> rangePages
            ThumbnailFilterMode.BOOKMARKED -> rangePages.filter { it in bookmarkedPages }
            ThumbnailFilterMode.ANNOTATED -> rangePages.filter { it in annotatedPages }
        }
        val trimmedQuery = pageQuery.trim()
        if (trimmedQuery.isEmpty()) return basePages
        return basePages.filter { pageIdx ->
            val displayPageNumber = (pageIdx + 1).toString()
            displayPageNumber.contains(trimmedQuery, ignoreCase = true)
        }
    }

    fun buildDocumentSections(
        totalPages: Int,
        outlineItems: List<PdfOutlineEntry> = emptyList(),
        maxPagesPerChunk: Int = 10
    ): List<DocumentSection> {
        if (totalPages <= 0) return emptyList()

        val sortedOutline = outlineItems
            .map { it.copy(pageIndex = it.pageIndex.coerceIn(0, totalPages - 1)) }
            .sortedBy { it.pageIndex }
            .distinctBy { it.pageIndex }

        if (sortedOutline.isNotEmpty()) {
            val sections = mutableListOf<DocumentSection>()
            if (sortedOutline.first().pageIndex > 0) {
                val introEnd = (sortedOutline.first().pageIndex - 1).coerceAtLeast(0)
                sections.add(
                    DocumentSection(
                        id = "outline_intro_0",
                        title = "Front Matter",
                        subtitle = if (introEnd == 0) "P.1" else "P.1–${introEnd + 1}",
                        startPage = 0,
                        endPage = introEnd,
                        isOutlineChapter = true
                    )
                )
            }
            for (i in sortedOutline.indices) {
                val entry = sortedOutline[i]
                val start = entry.pageIndex
                val end = if (i + 1 < sortedOutline.size) {
                    (sortedOutline[i + 1].pageIndex - 1).coerceAtLeast(start)
                } else {
                    totalPages - 1
                }
                sections.add(
                    DocumentSection(
                        id = "outline_${i}_$start",
                        title = entry.title.ifBlank { "Section ${i + 1}" },
                        subtitle = if (start == end) "P.${start + 1}" else "P.${start + 1}–${end + 1}",
                        startPage = start,
                        endPage = end,
                        isOutlineChapter = true
                    )
                )
            }
            if (sections.size > 1) {
                return sections
            }
        }

        val effectiveChunkSize = when {
            totalPages > maxPagesPerChunk -> maxPagesPerChunk.coerceAtLeast(2)
            totalPages >= 4 -> ((totalPages + 2) / 3).coerceAtLeast(2)
            else -> totalPages.coerceAtLeast(1)
        }

        val chunkedSections = mutableListOf<DocumentSection>()
        var start = 0
        var sectionIndex = 1
        while (start < totalPages) {
            val end = (start + effectiveChunkSize - 1).coerceAtMost(totalPages - 1)
            val rangeText = if (start == end) "P.${start + 1}" else "P.${start + 1}–${end + 1}"
            chunkedSections.add(
                DocumentSection(
                    id = "chunk_${sectionIndex}_${start}_$end",
                    title = "Sec $sectionIndex",
                    subtitle = rangeText,
                    startPage = start,
                    endPage = end,
                    isOutlineChapter = false
                )
            )
            start = end + 1
            sectionIndex++
        }
        return chunkedSections
    }

    fun findActiveSection(
        sections: List<DocumentSection>,
        currentPage: Int
    ): DocumentSection? {
        if (sections.isEmpty()) return null
        return sections.firstOrNull { currentPage in it.startPage..it.endPage }
            ?: sections.lastOrNull { it.startPage <= currentPage }
            ?: sections.first()
    }
}

sealed interface ViewerState {
    data object Idle : ViewerState
    data object Loading : ViewerState
    data class Viewing(
        val uri: Uri,
        val fileName: String,
        val fileSize: Long,
        val pageCount: Int,
        val initialPage: Int
    ) : ViewerState
    data class PasswordRequired(val uri: Uri, val fileName: String) : ViewerState
    data class Error(val message: String, val uri: Uri?) : ViewerState
}

class PdfViewModel(
    application: Application,
    private val repository: PdfRepository,
    val settingsManager: SettingsManager
) : AndroidViewModel(application) {

    private val documentManager = PdfDocumentManager(application.applicationContext)
    private val pdfBoxHelper = PdfBoxHelper(application.applicationContext)
    private val ttsManager = PdfTtsManager(application.applicationContext)
    private val annotationEngine = PdfAnnotationEngine(application.applicationContext)
    private val operationEngine = PdfOperationEngine(application.applicationContext)
    private val formEngine = PdfFormEngine(application.applicationContext)
    private val contentEditEngine = PdfContentEditEngine(application.applicationContext)
    private val converterEngine = PdfDocumentConverterEngine(application.applicationContext)
    val universalParserEngine = UniversalFileParserEngine(application.applicationContext)
    private val aiEngine = PdfAiAssistantEngine(application.applicationContext)
    val ocrEngine = ScanOcrEngine(application.applicationContext)
    val signatureManager = SignatureManager(application.applicationContext)
    val appLockManager = com.example.security.AppLockManager(application.applicationContext)

    private var activeOperationJob: Job? = null
    private val _operationProgress = MutableStateFlow<OperationProgress>(OperationProgress.Idle)
    val operationProgress: StateFlow<OperationProgress> = _operationProgress.asStateFlow()

    private val _isPageOrganizerOpen = MutableStateFlow(false)
    val isPageOrganizerOpen: StateFlow<Boolean> = _isPageOrganizerOpen.asStateFlow()

    val currentViewingUri: Uri?
        get() = (_viewerState.value as? ViewerState.Viewing)?.uri
    val currentViewingName: String?
        get() = (_viewerState.value as? ViewerState.Viewing)?.fileName
    val currentViewingPageCount: Int
        get() = (_viewerState.value as? ViewerState.Viewing)?.pageCount ?: 0
    val currentViewingCachedFile: File?
        get() = documentManager.cachedFile

    private val _documentRenderVersion = MutableStateFlow(0)
    val documentRenderVersion: StateFlow<Int> = _documentRenderVersion.asStateFlow()

    private val _isRotatingPage = MutableStateFlow(false)
    val isRotatingPage: StateFlow<Boolean> = _isRotatingPage.asStateFlow()

    private val _isCroppingPage = MutableStateFlow(false)
    val isCroppingPage: StateFlow<Boolean> = _isCroppingPage.asStateFlow()

    fun setPageOrganizerOpen(open: Boolean) {
        _isPageOrganizerOpen.value = open
    }

    // Current navigation state
    private val _currentTab = MutableStateFlow(BottomNavTab.HOME)
    val currentTab: StateFlow<BottomNavTab> = _currentTab.asStateFlow()

    // Viewer UI state
    private val _viewerState = MutableStateFlow<ViewerState>(ViewerState.Idle)
    val viewerState: StateFlow<ViewerState> = _viewerState.asStateFlow()

    // Universal Office & Image Viewer UI state (MS Word, MS Excel, PowerPoint, JPEG/PNG, RTF, CSV, TXT)
    private val _universalViewerState = MutableStateFlow<UniversalViewerState>(UniversalViewerState.Idle)
    val universalViewerState: StateFlow<UniversalViewerState> = _universalViewerState.asStateFlow()

    private val _currentPage = MutableStateFlow(0)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    private val _isNightMode = MutableStateFlow(false)
    val isNightMode: StateFlow<Boolean> = _isNightMode.asStateFlow()

    private val _isThumbnailSheetOpen = MutableStateFlow(false)
    val isThumbnailSheetOpen: StateFlow<Boolean> = _isThumbnailSheetOpen.asStateFlow()

    private val _isThumbnailBottomDrawerOpen = MutableStateFlow(false)
    val isThumbnailBottomDrawerOpen: StateFlow<Boolean> = _isThumbnailBottomDrawerOpen.asStateFlow()

    private val _isThumbnailSidebarOpen = MutableStateFlow(false)
    val isThumbnailSidebarOpen: StateFlow<Boolean> = _isThumbnailSidebarOpen.asStateFlow()

    private val _thumbnailFilterMode = MutableStateFlow(ThumbnailFilterMode.ALL_PAGES)
    val thumbnailFilterMode: StateFlow<ThumbnailFilterMode> = _thumbnailFilterMode.asStateFlow()

    private val _selectedSectionId = MutableStateFlow<String?>(null)
    val selectedSectionId: StateFlow<String?> = _selectedSectionId.asStateFlow()

    private val _isJumpDialogOpen = MutableStateFlow(false)
    val isJumpDialogOpen: StateFlow<Boolean> = _isJumpDialogOpen.asStateFlow()

    // Reading Modes: CONTINUOUS, SINGLE_PAGE, TWO_PAGE_SPREAD
    private val _readingMode = MutableStateFlow(settingsManager.settings.value.defaultReadingMode)
    val readingMode: StateFlow<ReadingMode> = _readingMode.asStateFlow()

    // Full-screen mode (hides all chrome and system bars)
    private val _isFullScreen = MutableStateFlow(false)
    val isFullScreen: StateFlow<Boolean> = _isFullScreen.asStateFlow()

    // Auto-Scroll state
    private val _isAutoScrollActive = MutableStateFlow(false)
    val isAutoScrollActive: StateFlow<Boolean> = _isAutoScrollActive.asStateFlow()

    private val _autoScrollSpeed = MutableStateFlow(2.0f) // 1.0f to 5.0f
    val autoScrollSpeed: StateFlow<Float> = _autoScrollSpeed.asStateFlow()

    // In-viewer Text Search state
    private val _isSearchVisible = MutableStateFlow(false)
    val isSearchVisible: StateFlow<Boolean> = _isSearchVisible.asStateFlow()

    private val _pdfSearchQuery = MutableStateFlow("")
    val pdfSearchQuery: StateFlow<String> = _pdfSearchQuery.asStateFlow()

    private val _searchProgress = MutableStateFlow<SearchProgress>(SearchProgress.Complete(emptyList()))
    val searchProgress: StateFlow<SearchProgress> = _searchProgress.asStateFlow()

    private val _searchMatches = MutableStateFlow<List<SearchMatch>>(emptyList())
    val searchMatches: StateFlow<List<SearchMatch>> = _searchMatches.asStateFlow()

    private val _selectedMatchIndex = MutableStateFlow(-1)
    val selectedMatchIndex: StateFlow<Int> = _selectedMatchIndex.asStateFlow()

    private var searchJob: Job? = null

    // Outline / Table of Contents
    private val _outlineItems = MutableStateFlow<List<PdfOutlineEntry>>(emptyList())
    val outlineItems: StateFlow<List<PdfOutlineEntry>> = _outlineItems.asStateFlow()

    private val _isOutlineSheetOpen = MutableStateFlow(false)
    val isOutlineSheetOpen: StateFlow<Boolean> = _isOutlineSheetOpen.asStateFlow()

    // Active document URI string for observing bookmarks
    private val _activeUriString = MutableStateFlow<String?>(null)
    val activeUriString: StateFlow<String?> = _activeUriString.asStateFlow()

    // Bookmarks for currently open PDF
    val currentBookmarks: StateFlow<List<BookmarkEntity>> = _activeUriString
        .flatMapLatest { uri ->
            if (uri != null) repository.getBookmarks(uri)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Text-To-Speech (TTS)
    private val _isTtsBarVisible = MutableStateFlow(false)
    val isTtsBarVisible: StateFlow<Boolean> = _isTtsBarVisible.asStateFlow()

    val isTtsPlaying: StateFlow<Boolean> = ttsManager.isPlaying
    val ttsSpeed: StateFlow<Float> = ttsManager.speechRate

    private val _ttsCurrentText = MutableStateFlow("")
    val ttsCurrentText: StateFlow<String> = _ttsCurrentText.asStateFlow()

    private val _isTtsLoading = MutableStateFlow(false)
    val isTtsLoading: StateFlow<Boolean> = _isTtsLoading.asStateFlow()

    // ==========================================
    // Annotations State
    // ==========================================
    private val _allAnnotations = MutableStateFlow<List<PdfAnnotation>>(emptyList())
    val allAnnotations: StateFlow<List<PdfAnnotation>> = _allAnnotations.asStateFlow()

    private val _isAnnotationToolbarVisible = MutableStateFlow(false)
    val isAnnotationToolbarVisible: StateFlow<Boolean> = _isAnnotationToolbarVisible.asStateFlow()

    private val _activeAnnotationTool = MutableStateFlow<AnnotationType?>(null)
    val activeAnnotationTool: StateFlow<AnnotationType?> = _activeAnnotationTool.asStateFlow()

    private val _activeAnnotationColor = MutableStateFlow(0xFFFFEB3B.toInt()) // Yellow default
    val activeAnnotationColor: StateFlow<Int> = _activeAnnotationColor.asStateFlow()

    private val _activeAnnotationThickness = MutableStateFlow(3f)
    val activeAnnotationThickness: StateFlow<Float> = _activeAnnotationThickness.asStateFlow()

    private val _selectedAnnotationId = MutableStateFlow<String?>(null)
    val selectedAnnotationId: StateFlow<String?> = _selectedAnnotationId.asStateFlow()

    // Undo / Redo stacks
    private val undoStack = mutableListOf<List<PdfAnnotation>>()
    private val redoStack = mutableListOf<List<PdfAnnotation>>()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    // Annotations List Sheet
    private val _isAnnotationsListOpen = MutableStateFlow(false)
    val isAnnotationsListOpen: StateFlow<Boolean> = _isAnnotationsListOpen.asStateFlow()

    // Save with Annotations Dialog
    private val _isSaveDialogVisible = MutableStateFlow(false)
    val isSaveDialogVisible: StateFlow<Boolean> = _isSaveDialogVisible.asStateFlow()

    private val _saveProgress = MutableStateFlow<SaveAnnotationProgress?>(null)
    val saveProgress: StateFlow<SaveAnnotationProgress?> = _saveProgress.asStateFlow()

    private var lastSavedTempFile: File? = null

    // ==========================================
    // Form Filling & Signatures State
    // ==========================================
    private val _formFields = MutableStateFlow<List<FormFieldItem>>(emptyList())
    val formFields: StateFlow<List<FormFieldItem>> = _formFields.asStateFlow()

    private val _placedStamps = MutableStateFlow<List<PlacedStampItem>>(emptyList())
    val placedStamps: StateFlow<List<PlacedStampItem>> = _placedStamps.asStateFlow()

    private val _focusedFieldId = MutableStateFlow<String?>(null)
    val focusedFieldId: StateFlow<String?> = _focusedFieldId.asStateFlow()

    private val _selectedStampId = MutableStateFlow<String?>(null)
    val selectedStampId: StateFlow<String?> = _selectedStampId.asStateFlow()

    private val _isFillAndSignActive = MutableStateFlow(false)
    val isFillAndSignActive: StateFlow<Boolean> = _isFillAndSignActive.asStateFlow()

    private val _activeQuickFillType = MutableStateFlow<PlacedStampType?>(null)
    val activeQuickFillType: StateFlow<PlacedStampType?> = _activeQuickFillType.asStateFlow()

    private val _pendingSnippetText = MutableStateFlow<String?>(null)
    val pendingSnippetText: StateFlow<String?> = _pendingSnippetText.asStateFlow()

    private val _isSignaturePickerOpen = MutableStateFlow(false)
    val isSignaturePickerOpen: StateFlow<Boolean> = _isSignaturePickerOpen.asStateFlow()

    private val _isSignatureCreationOpen = MutableStateFlow(false)
    val isSignatureCreationOpen: StateFlow<Boolean> = _isSignatureCreationOpen.asStateFlow()

    private val _isAddFieldDialogOpen = MutableStateFlow(false)
    val isAddFieldDialogOpen: StateFlow<Boolean> = _isAddFieldDialogOpen.asStateFlow()

    private val _isSaveFormDialogOpen = MutableStateFlow(false)
    val isSaveFormDialogOpen: StateFlow<Boolean> = _isSaveFormDialogOpen.asStateFlow()

    private val _isSavingForm = MutableStateFlow(false)
    val isSavingForm: StateFlow<Boolean> = _isSavingForm.asStateFlow()

    private val _saveFormCompletedMessage = MutableStateFlow<String?>(null)
    val saveFormCompletedMessage: StateFlow<String?> = _saveFormCompletedMessage.asStateFlow()

    // ==========================================
    // Content Editing Tools State
    // ==========================================
    private val _isContentEditActive = MutableStateFlow(false)
    val isContentEditActive: StateFlow<Boolean> = _isContentEditActive.asStateFlow()

    private val _activeContentTool = MutableStateFlow(ContentEditTool.SELECT)
    val activeContentTool: StateFlow<ContentEditTool> = _activeContentTool.asStateFlow()

    private val _contentEdits = MutableStateFlow<List<ContentEditElement>>(emptyList())
    val contentEdits: StateFlow<List<ContentEditElement>> = _contentEdits.asStateFlow()

    private val _selectedContentElementId = MutableStateFlow<String?>(null)
    val selectedContentElementId: StateFlow<String?> = _selectedContentElementId.asStateFlow()

    private val _clipboardContentElement = MutableStateFlow<ContentEditElement?>(null)
    val clipboardContentElement: StateFlow<ContentEditElement?> = _clipboardContentElement.asStateFlow()

    private val _pageExistingTextLines = MutableStateFlow<Map<Int, List<ExistingTextLine>>>(emptyMap())
    val pageExistingTextLines: StateFlow<Map<Int, List<ExistingTextLine>>> = _pageExistingTextLines.asStateFlow()

    private val _missingFontWarningMessage = MutableStateFlow<String?>(null)
    val missingFontWarningMessage: StateFlow<String?> = _missingFontWarningMessage.asStateFlow()
    private var hasShownMissingFontWarningForCurrentDoc = false

    private val contentUndoStack = mutableListOf<List<ContentEditElement>>()
    private val contentRedoStack = mutableListOf<List<ContentEditElement>>()

    private val _canUndoContentEdit = MutableStateFlow(false)
    val canUndoContentEdit: StateFlow<Boolean> = _canUndoContentEdit.asStateFlow()

    private val _canRedoContentEdit = MutableStateFlow(false)
    val canRedoContentEdit: StateFlow<Boolean> = _canRedoContentEdit.asStateFlow()

    private val _isSaveContentEditsDialogOpen = MutableStateFlow(false)
    val isSaveContentEditsDialogOpen: StateFlow<Boolean> = _isSaveContentEditsDialogOpen.asStateFlow()

    private val _isSavingContentEdits = MutableStateFlow(false)
    val isSavingContentEdits: StateFlow<Boolean> = _isSavingContentEdits.asStateFlow()

    private val _contentEditProgressMessage = MutableStateFlow("")
    val contentEditProgressMessage: StateFlow<String> = _contentEditProgressMessage.asStateFlow()

    // ==========================================
    // Gemini Cloud AI Assistant State
    // ==========================================
    private val _isAiSheetOpen = MutableStateFlow(false)
    val isAiSheetOpen: StateFlow<Boolean> = _isAiSheetOpen.asStateFlow()

    private val _activeAiTab = MutableStateFlow(AiToolTab.SUMMARY)
    val activeAiTab: StateFlow<AiToolTab> = _activeAiTab.asStateFlow()

    private val _isAiConsentDialogOpen = MutableStateFlow(false)
    val isAiConsentDialogOpen: StateFlow<Boolean> = _isAiConsentDialogOpen.asStateFlow()

    private val _isAiPasswordConfirmDialogOpen = MutableStateFlow(false)
    val isAiPasswordConfirmDialogOpen: StateFlow<Boolean> = _isAiPasswordConfirmDialogOpen.asStateFlow()

    private var hasConfirmedPasswordProtectedAiForCurrentDoc: Boolean = false
    private var pendingAiActionAfterConsent: (() -> Unit)? = null
    private var pendingTabAfterPdfOpen: AiToolTab? = null
    private var activeAiJob: Job? = null

    private val _isAiRequestRunning = MutableStateFlow(false)
    val isAiRequestRunning: StateFlow<Boolean> = _isAiRequestRunning.asStateFlow()

    private val _aiStatusMessage = MutableStateFlow("")
    val aiStatusMessage: StateFlow<String> = _aiStatusMessage.asStateFlow()

    private val _aiErrorMessage = MutableStateFlow<String?>(null)
    val aiErrorMessage: StateFlow<String?> = _aiErrorMessage.asStateFlow()

    private val _aiSummaryResult = MutableStateFlow<AiSummaryResult?>(null)
    val aiSummaryResult: StateFlow<AiSummaryResult?> = _aiSummaryResult.asStateFlow()

    private val _aiChatMessages = MutableStateFlow<List<AiChatMessage>>(emptyList())
    val aiChatMessages: StateFlow<List<AiChatMessage>> = _aiChatMessages.asStateFlow()

    private val _aiTranslationResult = MutableStateFlow<AiTranslationResult?>(null)
    val aiTranslationResult: StateFlow<AiTranslationResult?> = _aiTranslationResult.asStateFlow()

    private val _aiExplainResult = MutableStateFlow<AiExplainResult?>(null)
    val aiExplainResult: StateFlow<AiExplainResult?> = _aiExplainResult.asStateFlow()

    private val _selectedTextForAi = MutableStateFlow("")
    val selectedTextForAi: StateFlow<String> = _selectedTextForAi.asStateFlow()

    private var cachedDocumentChunks: List<PdfTextChunk>? = null

    val isCurrentPdfPasswordProtected: Boolean
        get() = documentManager.isCurrentPdfPasswordProtected

    // ==========================================
    // ML Kit OCR Selectable Text State (for Scanned / Non-Searchable PDFs)
    // ==========================================
    private val _isOcrSelectionModeActive = MutableStateFlow(false)
    val isOcrSelectionModeActive: StateFlow<Boolean> = _isOcrSelectionModeActive.asStateFlow()

    private val _isOcrSheetOpen = MutableStateFlow(false)
    val isOcrSheetOpen: StateFlow<Boolean> = _isOcrSheetOpen.asStateFlow()

    private val _pageOcrResults = MutableStateFlow<Map<Int, OcrPageResult>>(emptyMap())
    val pageOcrResults: StateFlow<Map<Int, OcrPageResult>> = _pageOcrResults.asStateFlow()

    private val _isOcrRunningForPage = MutableStateFlow(false)
    val isOcrRunningForPage: StateFlow<Boolean> = _isOcrRunningForPage.asStateFlow()

    private val _isCurrentPageScannedWithoutText = MutableStateFlow(false)
    val isCurrentPageScannedWithoutText: StateFlow<Boolean> = _isCurrentPageScannedWithoutText.asStateFlow()

    private val _selectedOcrLineTexts = MutableStateFlow<List<String>>(emptyList())
    val selectedOcrLineTexts: StateFlow<List<String>> = _selectedOcrLineTexts.asStateFlow()

    private var ocrScanCheckJob: Job? = null

    // Search, Folder filter, and Sort in Files / Home
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFolderFilter = MutableStateFlow<String?>(null)
    val selectedFolderFilter: StateFlow<String?> = _selectedFolderFilter.asStateFlow()

    // Polish navigation states: About Screen, Onboarding Tour Replay, Default PDF Reader Guide, Global Error Screen
    private val _isAboutScreenOpen = MutableStateFlow(false)
    val isAboutScreenOpen: StateFlow<Boolean> = _isAboutScreenOpen.asStateFlow()

    private val _isOnboardingReplayOpen = MutableStateFlow(false)
    val isOnboardingReplayOpen: StateFlow<Boolean> = _isOnboardingReplayOpen.asStateFlow()

    private val _isDefaultPdfGuideOpen = MutableStateFlow(false)
    val isDefaultPdfGuideOpen: StateFlow<Boolean> = _isDefaultPdfGuideOpen.asStateFlow()

    private val _globalErrorReport = MutableStateFlow<GlobalErrorReport?>(null)
    val globalErrorReport: StateFlow<GlobalErrorReport?> = _globalErrorReport.asStateFlow()

    val appSettings: StateFlow<AppSettings> = settingsManager.settings

    init {
        documentManager.setLowMemoryMode(settingsManager.settings.value.lowMemoryModeEnabled)
        GlobalErrorManager.consumePendingCrashReport(application.applicationContext)?.let { pending ->
            _globalErrorReport.value = pending
        }
    }

    private fun sortAndFilterPdfs(
        list: List<RecentPdfEntity>,
        query: String,
        folderFilter: String?,
        sortOrder: HomeSortOrder
    ): List<RecentPdfEntity> {
        val afterQuery = if (query.isBlank()) {
            list
        } else {
            list.filter {
                it.fileName.contains(query, ignoreCase = true) ||
                    it.folderName.contains(query, ignoreCase = true)
            }
        }
        val afterFolder = when {
            folderFilter == null -> afterQuery
            folderFilter.isEmpty() -> afterQuery.filter { it.folderName.isBlank() }
            else -> afterQuery.filter { it.folderName.equals(folderFilter, ignoreCase = true) }
        }
        return when (sortOrder) {
            HomeSortOrder.DATE -> afterFolder.sortedByDescending { it.lastOpenedTimestamp }
            HomeSortOrder.NAME -> afterFolder.sortedBy { it.fileName.lowercase() }
            HomeSortOrder.SIZE -> afterFolder.sortedByDescending { it.fileSizeBytes }
        }
    }

    // Flow of recent PDFs filtered by search, folder, and sorted by Date/Name/Size
    val recentPdfs: StateFlow<List<RecentPdfEntity>> = combine(
        repository.allRecents,
        _searchQuery,
        _selectedFolderFilter,
        appSettings
    ) { recents, query, folder, settings ->
        sortAndFilterPdfs(recents, query, folder, settings.homeSortOrder)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoritePdfs: StateFlow<List<RecentPdfEntity>> = combine(
        repository.favorites,
        _searchQuery,
        _selectedFolderFilter,
        appSettings
    ) { favs, query, folder, settings ->
        sortAndFilterPdfs(favs, query, folder, settings.homeSortOrder)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var savePageJob: Job? = null

    fun selectTab(tab: BottomNavTab) {
        _currentTab.value = tab
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedFolderFilter(folder: String?) {
        _selectedFolderFilter.value = folder
    }

    fun setHomeSortOrder(order: HomeSortOrder) {
        settingsManager.setHomeSortOrder(order)
    }

    fun createFolder(folderName: String): Boolean {
        return settingsManager.addCustomFolder(folderName)
    }

    fun deleteFolder(folderName: String) {
        settingsManager.removeCustomFolder(folderName)
        if (_selectedFolderFilter.value.equals(folderName, ignoreCase = true)) {
            _selectedFolderFilter.value = null
        }
        viewModelScope.launch {
            repository.removeFolderAndUnassign(folderName)
        }
    }

    fun assignPdfToFolder(uri: String, folderName: String) {
        if (folderName.isNotBlank()) {
            settingsManager.addCustomFolder(folderName)
        }
        viewModelScope.launch {
            repository.assignToFolder(uri, folderName)
        }
    }

    fun setAppLanguage(language: AppLanguage) {
        settingsManager.setAppLanguage(language)
    }

    fun setLowMemoryMode(enabled: Boolean) {
        settingsManager.setLowMemoryMode(enabled)
        documentManager.setLowMemoryMode(enabled)
    }

    fun setAnalyticsOptIn(optIn: Boolean) {
        settingsManager.setAnalyticsOptIn(optIn)
    }

    fun completeOnboarding() {
        settingsManager.setOnboardingCompleted(true)
        _isOnboardingReplayOpen.value = false
    }

    fun setOnboardingReplayOpen(open: Boolean) {
        _isOnboardingReplayOpen.value = open
    }

    fun setDefaultPdfGuideOpen(open: Boolean) {
        _isDefaultPdfGuideOpen.value = open
    }

    fun setAboutScreenOpen(open: Boolean) {
        _isAboutScreenOpen.value = open
    }

    fun reportGlobalError(throwable: Throwable, contextInfo: String = "Application Error") {
        _globalErrorReport.value = GlobalErrorManager.formatThrowableReport(throwable, contextInfo)
    }

    fun setGlobalErrorReport(report: com.example.ui.common.GlobalErrorReport) {
        _globalErrorReport.value = report
    }

    fun triggerDiagnosticErrorScreen() {
        _globalErrorReport.value = GlobalErrorManager.formatThrowableReport(
            IllegalStateException("Sample diagnostic error check (user-triggered from Settings)"),
            "Diagnostic Verification"
        )
    }

    fun dismissGlobalError() {
        _globalErrorReport.value = null
    }

    fun importPdfUrisToLibrary(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val appContext = getApplication<Application>().applicationContext
        viewModelScope.launch {
            uris.forEach { uri ->
                var displayName = uri.lastPathSegment?.substringAfterLast('/') ?: "Document.pdf"
                var sizeBytes = 0L
                try {
                    appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        val sizeIdx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                        if (cursor.moveToFirst()) {
                            if (nameIdx != -1) {
                                cursor.getString(nameIdx)?.takeIf { it.isNotBlank() }?.let { displayName = it }
                            }
                            if (sizeIdx != -1 && !cursor.isNull(sizeIdx)) {
                                sizeBytes = cursor.getLong(sizeIdx)
                            }
                        }
                    }
                } catch (_: Exception) {
                }
                repository.recordPdfOpened(
                    uri = uri.toString(),
                    fileName = displayName,
                    fileSizeBytes = sizeBytes,
                    pageCount = 0
                )
            }
        }
    }

    private var metadataLoadJob: kotlinx.coroutines.Job? = null

    fun openPdf(uri: Uri) {
        val detectedType = UniversalFileParserEngine.detectFileType(getApplication(), uri, null)
        if (detectedType != UniversalFileType.PDF) {
            openUniversalOrPdfFile(uri)
            return
        }
        _universalViewerState.value = UniversalViewerState.Idle
        metadataLoadJob?.cancel()
        viewModelScope.launch {
            _viewerState.value = ViewerState.Loading
            _activeUriString.value = uri.toString()
            stopAutoScroll()
            closeSearch()
            closeTts()
            _isAnnotationToolbarVisible.value = false
            _activeAnnotationTool.value = null
            _allAnnotations.value = emptyList()
            _formFields.value = emptyList()
            _placedStamps.value = emptyList()
            _isFillAndSignActive.value = false
            _activeQuickFillType.value = null
            _pendingSnippetText.value = null
            _focusedFieldId.value = null
            _selectedStampId.value = null
            undoStack.clear()
            redoStack.clear()
            _canUndo.value = false
            _canRedo.value = false
            _isContentEditActive.value = false
            _activeContentTool.value = ContentEditTool.SELECT
            _contentEdits.value = emptyList()
            _selectedContentElementId.value = null
            _pageExistingTextLines.value = emptyMap()
            _missingFontWarningMessage.value = null
            hasShownMissingFontWarningForCurrentDoc = false
            contentUndoStack.clear()
            contentRedoStack.clear()
            _canUndoContentEdit.value = false
            _canRedoContentEdit.value = false

            val savedPdf = repository.getPdf(uri.toString())
            val initialPage = savedPdf?.lastPage ?: 0

            when (val result = documentManager.openPdf(uri)) {
                is PdfLoadResult.Success -> {
                    _currentPage.value = initialPage.coerceIn(0, (result.pageCount - 1).coerceAtLeast(0))
                    _viewerState.value = ViewerState.Viewing(
                        uri = uri,
                        fileName = result.fileName,
                        fileSize = result.fileSize,
                        pageCount = result.pageCount,
                        initialPage = _currentPage.value
                    )
                    // Record in Room
                    repository.recordPdfOpened(
                        uri = uri.toString(),
                        fileName = result.fileName,
                        fileSizeBytes = result.fileSize,
                        pageCount = result.pageCount
                    )
                    // Load Outline, Existing Annotations, Form Fields & Links sequentially to avoid concurrent PDDocument OOM
                    loadDocumentMetadataSequentially(uri, _currentPage.value)
                    resetOcrStateForNewDocument()
                    checkIfPageIsScannedWithoutText(_currentPage.value)
                    resetAiSessionForNewDocument()
                    pendingTabAfterPdfOpen?.let { targetTab ->
                        pendingTabAfterPdfOpen = null
                        openAiAssistant(targetTab)
                    }
                }
                is PdfLoadResult.PasswordRequired -> {
                    _viewerState.value = ViewerState.PasswordRequired(uri, documentManager.currentFileName)
                }
                is PdfLoadResult.Error -> {
                    pendingTabAfterPdfOpen = null
                    _viewerState.value = ViewerState.Error(result.message, uri)
                }
            }
        }
    }

    fun unlockCurrentPdf(password: String, removePermanently: Boolean = false) {
        val currentUri = when (val s = _viewerState.value) {
            is ViewerState.PasswordRequired -> s.uri
            is ViewerState.Viewing -> s.uri
            is ViewerState.Error -> s.uri
            else -> null
        } ?: return

        viewModelScope.launch {
            _viewerState.value = ViewerState.Loading
            when (val result = documentManager.openPdfWithPassword(currentUri, password, removePermanently)) {
                is PdfLoadResult.Success -> {
                    _currentPage.value = 0
                    _viewerState.value = ViewerState.Viewing(
                        uri = currentUri,
                        fileName = result.fileName,
                        fileSize = result.fileSize,
                        pageCount = result.pageCount,
                        initialPage = 0
                    )
                    repository.recordPdfOpened(
                        uri = currentUri.toString(),
                        fileName = result.fileName,
                        fileSizeBytes = result.fileSize,
                        pageCount = result.pageCount
                    )
                    loadDocumentMetadataSequentially(currentUri, 0)
                    resetAiSessionForNewDocument()
                    pendingTabAfterPdfOpen?.let { targetTab ->
                        pendingTabAfterPdfOpen = null
                        openAiAssistant(targetTab)
                    }
                }
                is PdfLoadResult.PasswordRequired -> {
                    _viewerState.value = ViewerState.PasswordRequired(currentUri, documentManager.currentFileName)
                }
                is PdfLoadResult.Error -> {
                    _viewerState.value = ViewerState.Error(result.message, currentUri)
                }
            }
        }
    }

    val biometricProtectedUris: StateFlow<Set<String>> = appLockManager.protectedDocuments

    fun isDocumentBiometricProtected(uri: String): Boolean =
        appLockManager.isDocumentBiometricProtected(uri)

    fun toggleDocumentBiometricProtection(uri: String): Boolean =
        appLockManager.toggleDocumentBiometricProtection(uri)

    fun saveBiometricPasswordForDocument(uri: String, password: String) =
        appLockManager.saveDocumentPasswordForBiometrics(uri, password)

    fun hasSavedBiometricPassword(uri: String): Boolean =
        appLockManager.hasSavedDocumentPasswordForBiometrics(uri)

    fun removeBiometricPasswordForDocument(uri: String) =
        appLockManager.removeDocumentPasswordForBiometrics(uri)

    fun openPdfWithBiometricCheck(
        activity: FragmentActivity?,
        uri: Uri,
        fileName: String = "",
        onAuthFailed: ((String) -> Unit)? = null
    ) {
        if (appLockManager.isDocumentBiometricProtected(uri.toString())) {
            if (activity != null && appLockManager.canAuthenticateWithBiometrics() && appLockManager.isBiometricsEnabled()) {
                val displayName = fileName.ifBlank { uri.lastPathSegment ?: "Document" }
                appLockManager.promptBiometricsForDocument(
                    activity = activity,
                    fileName = displayName,
                    onSuccess = {
                        openUniversalOrPdfFile(uri, displayName)
                    },
                    onError = { error ->
                        onAuthFailed?.invoke(error)
                    }
                )
                return
            }
        }
        openUniversalOrPdfFile(uri, fileName.ifBlank { null })
    }

    fun openUniversalOrPdfFile(
        uri: Uri,
        explicitFileName: String? = null,
        directFile: File? = null
    ) {
        val appContext = getApplication<Application>().applicationContext
        val resolvedName = explicitFileName
            ?: directFile?.name
            ?: UniversalFileParserEngine.queryDisplayName(appContext, uri)
        val fileType = UniversalFileParserEngine.detectFileType(appContext, uri, resolvedName)

        if (fileType == UniversalFileType.PDF) {
            openPdf(uri)
            return
        }

        _viewerState.value = ViewerState.Idle
        val displayTitle = resolvedName ?: "Document"
        _universalViewerState.value = UniversalViewerState.Loading(displayTitle)

        viewModelScope.launch {
            try {
                val parsed = universalParserEngine.parseFile(
                    uri = uri,
                    directFile = directFile,
                    explicitFileName = resolvedName
                )
                repository.recordPdfOpened(
                    uri = uri.toString(),
                    fileName = parsed.fileName,
                    fileSizeBytes = parsed.fileSizeBytes,
                    pageCount = when (parsed) {
                        is ParsedUniversalFile.ExcelWorkbook -> parsed.sheets.size
                        is ParsedUniversalFile.PowerPointDeck -> parsed.slides.size
                        else -> 1
                    }
                )
                val targetAiTab = pendingTabAfterPdfOpen
                if (targetAiTab != null) {
                    val pdfFile = universalParserEngine.exportViewedFileToPdf(parsed)
                    _universalViewerState.value = UniversalViewerState.Idle
                    openPdf(Uri.fromFile(pdfFile))
                } else {
                    _universalViewerState.value = UniversalViewerState.Viewing(parsed)
                }
            } catch (e: Exception) {
                pendingTabAfterPdfOpen = null
                _universalViewerState.value = UniversalViewerState.Error(
                    fileName = displayTitle,
                    message = e.localizedMessage ?: "Could not parse $displayTitle"
                )
            }
        }
    }

    fun closeUniversalViewer() {
        _universalViewerState.value = UniversalViewerState.Idle
    }

    fun convertViewedUniversalFileToPdf(parsed: ParsedUniversalFile, launchAiTab: AiToolTab? = null) {
        viewModelScope.launch {
            try {
                val pdfFile = universalParserEngine.exportViewedFileToPdf(parsed)
                _universalViewerState.value = UniversalViewerState.Idle
                if (launchAiTab != null) {
                    openPdfAndLaunchAiTab(Uri.fromFile(pdfFile), launchAiTab)
                } else {
                    openPdf(Uri.fromFile(pdfFile))
                }
            } catch (_: Exception) {
            }
        }
    }

    fun unlockWithSavedBiometricPassword(
        activity: FragmentActivity?,
        uri: Uri,
        fileName: String = "",
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val savedPassword = appLockManager.getSavedDocumentPasswordForBiometrics(uri.toString())
        if (savedPassword.isNullOrBlank()) {
            onError("No saved biometric password found")
            return
        }
        if (activity == null) {
            unlockCurrentPdf(savedPassword, removePermanently = false)
            onSuccess()
            return
        }
        appLockManager.promptBiometrics(
            activity = activity,
            title = "Unlock Encrypted PDF",
            subtitle = if (fileName.isNotBlank()) "Scan fingerprint or face to open \"$fileName\"" else "Scan fingerprint or face to open document",
            negativeButtonText = "Use Password",
            onSuccess = {
                unlockCurrentPdf(savedPassword, removePermanently = false)
                onSuccess()
            },
            onError = onError
        )
    }

    fun unlockWithSavedBiometricPassword(
        activity: FragmentActivity?,
        uriString: String,
        fileName: String = "",
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        unlockWithSavedBiometricPassword(activity, Uri.parse(uriString), fileName, onSuccess, onError)
    }

    private fun loadDocumentMetadataSequentially(uri: Uri, initialPage: Int) {
        metadataLoadJob?.cancel()
        metadataLoadJob = viewModelScope.launch {
            val cached = documentManager.cachedFile
            try {
                val outline = pdfBoxHelper.extractOutline(cached, uri)
                _outlineItems.value = outline
            } catch (oom: OutOfMemoryError) {
                System.gc()
            } catch (_: Throwable) {
            }

            try {
                val existing = annotationEngine.loadExistingAnnotations(cached, uri)
                _allAnnotations.value = existing
            } catch (oom: OutOfMemoryError) {
                System.gc()
            } catch (_: Throwable) {
            }

            try {
                val fields = formEngine.extractFormFields(cached, uri)
                _formFields.value = fields
            } catch (oom: OutOfMemoryError) {
                System.gc()
            } catch (_: Throwable) {
            }

            try {
                val existingLinks = contentEditEngine.loadExistingLinks(cached, uri)
                if (existingLinks.isNotEmpty()) {
                    _contentEdits.value = existingLinks
                }
            } catch (oom: OutOfMemoryError) {
                System.gc()
            } catch (_: Throwable) {
            }

            if (_isContentEditActive.value) {
                inspectPageForContentEdit(initialPage)
            }
        }
    }

    // ==========================================
    // Content Editing Tools Actions
    // ==========================================
    fun toggleContentEditMode() {
        setContentEditActive(!_isContentEditActive.value)
    }

    fun setContentEditActive(active: Boolean) {
        _isContentEditActive.value = active
        if (active) {
            _isAnnotationToolbarVisible.value = false
            _activeAnnotationTool.value = null
            _isFillAndSignActive.value = false
            inspectPageForContentEdit(_currentPage.value)
        } else {
            _selectedContentElementId.value = null
        }
    }

    fun setActiveContentTool(tool: ContentEditTool) {
        _activeContentTool.value = tool
        if (tool == ContentEditTool.EDIT_TEXT || tool == ContentEditTool.REDACT) {
            inspectPageForContentEdit(_currentPage.value)
        }
    }

    fun inspectPageForContentEdit(pageIndex: Int) {
        if (_pageExistingTextLines.value.containsKey(pageIndex)) return
        val uri = documentManager.currentUri ?: return
        viewModelScope.launch {
            try {
                val result = contentEditEngine.inspectPage(
                    pdfFile = documentManager.cachedFile,
                    uri = uri,
                    pageIndex = pageIndex
                )
                val updated = _pageExistingTextLines.value.toMutableMap()
                updated[pageIndex] = result.lines
                _pageExistingTextLines.value = updated

                if (result.missingFontNames.isNotEmpty() && !hasShownMissingFontWarningForCurrentDoc) {
                    hasShownMissingFontWarningForCurrentDoc = true
                    val sampleFonts = result.missingFontNames.take(2).joinToString(", ")
                    _missingFontWarningMessage.value =
                        "Missing/unembedded PDF font ($sampleFonts) substituted automatically with Noto Sans / Helvetica."
                }
            } catch (oom: OutOfMemoryError) {
                System.gc()
            } catch (_: Throwable) {
            }
        }
    }

    fun dismissMissingFontWarning() {
        _missingFontWarningMessage.value = null
    }

    private fun pushContentUndoState() {
        contentUndoStack.add(_contentEdits.value)
        contentRedoStack.clear()
        _canUndoContentEdit.value = true
        _canRedoContentEdit.value = false
    }

    fun addContentEditElement(element: ContentEditElement) {
        pushContentUndoState()
        val maxZ = (_contentEdits.value.maxOfOrNull { it.zIndex } ?: 0) + 1
        val withZ = element.withUpdatedZIndex(maxZ)
        _contentEdits.value = _contentEdits.value + withZ
        _selectedContentElementId.value = withZ.id
    }

    fun updateContentEditElement(updated: ContentEditElement, pushUndo: Boolean = true) {
        if (pushUndo) {
            pushContentUndoState()
        }
        _contentEdits.value = _contentEdits.value.map {
            if (it.id == updated.id) updated else it
        }
    }

    fun deleteContentEditElement(id: String) {
        pushContentUndoState()
        _contentEdits.value = _contentEdits.value.filterNot { it.id == id }
        if (_selectedContentElementId.value == id) {
            _selectedContentElementId.value = null
        }
    }

    fun deleteSelectedContentElement() {
        val id = _selectedContentElementId.value ?: return
        deleteContentEditElement(id)
    }

    fun selectContentElement(id: String?) {
        _selectedContentElementId.value = id
    }

    fun copySelectedContentElement() {
        val id = _selectedContentElementId.value ?: return
        val elem = _contentEdits.value.firstOrNull { it.id == id } ?: return
        _clipboardContentElement.value = elem
    }

    fun pasteContentElement(targetPageIndex: Int = _currentPage.value) {
        val clip = _clipboardContentElement.value ?: return
        val duplicated = clip.duplicateOnPage(targetPageIndex = targetPageIndex)
        addContentEditElement(duplicated)
    }

    fun duplicateSelectedContentElement() {
        val id = _selectedContentElementId.value ?: return
        val elem = _contentEdits.value.firstOrNull { it.id == id } ?: return
        val duplicated = elem.duplicateOnPage(targetPageIndex = elem.pageIndex)
        addContentEditElement(duplicated)
    }

    fun bringSelectedElementForward() {
        val id = _selectedContentElementId.value ?: return
        val elem = _contentEdits.value.firstOrNull { it.id == id } ?: return
        val maxZ = (_contentEdits.value.maxOfOrNull { it.zIndex } ?: 0) + 1
        updateContentEditElement(elem.withUpdatedZIndex(maxZ), pushUndo = true)
    }

    fun sendSelectedElementBackward() {
        val id = _selectedContentElementId.value ?: return
        val elem = _contentEdits.value.firstOrNull { it.id == id } ?: return
        val minZ = (_contentEdits.value.minOfOrNull { it.zIndex } ?: 0) - 1
        updateContentEditElement(elem.withUpdatedZIndex(minZ), pushUndo = true)
    }

    fun undoContentEdit() {
        if (contentUndoStack.isNotEmpty()) {
            val prev = contentUndoStack.removeAt(contentUndoStack.size - 1)
            contentRedoStack.add(_contentEdits.value)
            _contentEdits.value = prev
            _canUndoContentEdit.value = contentUndoStack.isNotEmpty()
            _canRedoContentEdit.value = true
            _selectedContentElementId.value = null
        }
    }

    fun redoContentEdit() {
        if (contentRedoStack.isNotEmpty()) {
            val next = contentRedoStack.removeAt(contentRedoStack.size - 1)
            contentUndoStack.add(_contentEdits.value)
            _contentEdits.value = next
            _canUndoContentEdit.value = true
            _canRedoContentEdit.value = contentRedoStack.isNotEmpty()
            _selectedContentElementId.value = null
        }
    }

    fun setSaveContentEditsDialogOpen(open: Boolean) {
        _isSaveContentEditsDialogOpen.value = open
    }

    fun saveContentEdits(onSuccess: (File) -> Unit) {
        viewModelScope.launch {
            _isSavingContentEdits.value = true
            _contentEditProgressMessage.value = "Preparing content edits..."
            val savedFile = contentEditEngine.applyContentEditsToPdf(
                sourceFile = documentManager.cachedFile,
                sourceUri = documentManager.currentUri,
                edits = _contentEdits.value,
                onProgress = { _, _, msg ->
                    _contentEditProgressMessage.value = msg
                }
            )
            _isSavingContentEdits.value = false
            if (savedFile != null && savedFile.exists()) {
                lastSavedTempFile = savedFile
                onSuccess(savedFile)
            }
        }
    }

    fun reloadEditedPdfInViewer(savedFile: File) {
        viewModelScope.launch {
            val uri = android.net.Uri.fromFile(savedFile)
            openPdf(uri)
        }
    }

    fun rotatePageInViewer(
        pageIndex: Int,
        clockwise: Boolean,
        onDone: ((File) -> Unit)? = null
    ) {
        viewModelScope.launch {
            _isRotatingPage.value = true
            try {
                val updatedFile = documentManager.rotatePageInDocument(pageIndex, clockwise)
                if (updatedFile != null) {
                    overwriteOriginalWithSavedFile(updatedFile)
                    _pageOcrResults.value = _pageOcrResults.value - pageIndex
                    _pageExistingTextLines.value = _pageExistingTextLines.value - pageIndex
                    cachedDocumentChunks = null
                    _documentRenderVersion.value++
                    onDone?.invoke(updatedFile)
                }
            } catch (e: Exception) {
                _operationProgress.value = OperationProgress.Failure("Rotation Error", e.localizedMessage ?: "Rotation failed")
            } finally {
                _isRotatingPage.value = false
            }
        }
    }

    fun deletePageInViewer(
        pageIndex: Int,
        onDone: ((File) -> Unit)? = null
    ) {
        viewModelScope.launch {
            _isRotatingPage.value = true
            try {
                val updatedFile = documentManager.deletePageInDocument(pageIndex)
                if (updatedFile != null) {
                    overwriteOriginalWithSavedFile(updatedFile)
                    val newCount = documentManager.totalPages.coerceAtLeast(1)
                    val clampedPage = _currentPage.value.coerceIn(0, newCount - 1)
                    _currentPage.value = clampedPage
                    val currentState = _viewerState.value
                    if (currentState is ViewerState.Viewing) {
                        _viewerState.value = currentState.copy(
                            pageCount = newCount,
                            fileSize = updatedFile.length(),
                            initialPage = clampedPage
                        )
                    }
                    _documentRenderVersion.value++
                    onDone?.invoke(updatedFile)
                }
            } catch (e: Exception) {
                _operationProgress.value = OperationProgress.Failure("Delete Page Error", e.localizedMessage ?: "Could not delete page")
            } finally {
                _isRotatingPage.value = false
            }
        }
    }

    fun cropPageInViewer(
        pageIndex: Int,
        normalizedBox: NormalizedRect,
        applyToAllPages: Boolean,
        onDone: ((File) -> Unit)? = null
    ) {
        viewModelScope.launch {
            _isCroppingPage.value = true
            try {
                val updatedFile = documentManager.cropPageInDocument(pageIndex, normalizedBox, applyToAllPages)
                if (updatedFile != null) {
                    overwriteOriginalWithSavedFile(updatedFile)
                    _documentRenderVersion.value++
                    onDone?.invoke(updatedFile)
                }
            } catch (e: Exception) {
                _operationProgress.value = OperationProgress.Failure("Crop Error", e.localizedMessage ?: "Crop failed")
            } finally {
                _isCroppingPage.value = false
            }
        }
    }

    fun cropDocumentExternal(
        sourceUri: Uri,
        pageIndex: Int,
        normalizedBox: NormalizedRect,
        applyToAllPages: Boolean,
        onDone: (File) -> Unit
    ) {
        viewModelScope.launch {
            _isCroppingPage.value = true
            _operationProgress.value = OperationProgress.Running("Crop Document", "Cropping page...", 0.1f)
            try {
                val croppedFile = operationEngine.cropCustomBoundingBox(
                    sourceUri = sourceUri,
                    pageIndex = pageIndex,
                    normalizedBox = normalizedBox,
                    applyToAllPages = applyToAllPages,
                    onProgress = { fraction, step ->
                        _operationProgress.value = OperationProgress.Running("Crop Document", step, fraction)
                    }
                )
                _operationProgress.value = OperationProgress.Success(
                    title = "Crop Complete",
                    message = "Cropped PDF generated successfully!",
                    outputFiles = listOf(croppedFile),
                    primaryFile = croppedFile
                )
                onDone(croppedFile)
            } catch (e: Exception) {
                _operationProgress.value = OperationProgress.Failure("Crop Error", e.localizedMessage ?: "Crop failed")
            } finally {
                _isCroppingPage.value = false
            }
        }
    }

    // ==========================================
    // Form Filling & Signature Actions
    // ==========================================
    fun toggleFillAndSignMode() {
        _isFillAndSignActive.value = !_isFillAndSignActive.value
        if (_isFillAndSignActive.value) {
            _isAnnotationToolbarVisible.value = false
            _activeAnnotationTool.value = null
        } else {
            _activeQuickFillType.value = null
            _pendingSnippetText.value = null
            _selectedStampId.value = null
        }
    }

    fun setFillAndSignActive(active: Boolean) {
        _isFillAndSignActive.value = active
        if (active) {
            _isAnnotationToolbarVisible.value = false
            _activeAnnotationTool.value = null
        }
    }

    fun setActiveQuickFillType(type: PlacedStampType?) {
        _activeQuickFillType.value = type
        if (type != PlacedStampType.SNIPPET) {
            _pendingSnippetText.value = null
        }
    }

    fun setPendingSnippetText(text: String?) {
        _pendingSnippetText.value = text
        _activeQuickFillType.value = if (text != null) PlacedStampType.SNIPPET else null
    }

    fun setSignaturePickerOpen(open: Boolean) {
        _isSignaturePickerOpen.value = open
    }

    fun setSignatureCreationOpen(open: Boolean) {
        _isSignatureCreationOpen.value = open
    }

    fun setAddFieldDialogOpen(open: Boolean) {
        _isAddFieldDialogOpen.value = open
    }

    fun setSaveFormDialogOpen(open: Boolean) {
        _isSaveFormDialogOpen.value = open
        if (!open) {
            _saveFormCompletedMessage.value = null
        }
    }

    fun updateFieldValue(fieldName: String, value: String) {
        val current = _formFields.value.toMutableList()
        val index = current.indexOfFirst { it.fullyQualifiedName == fieldName }
        if (index >= 0) {
            current[index] = current[index].copy(currentValue = value)
            _formFields.value = current
        }
    }

    fun setFocusedField(fieldId: String?) {
        _focusedFieldId.value = fieldId
    }

    fun navigateField(forward: Boolean, onScrollToPage: (Int) -> Unit) {
        val fields = _formFields.value
        if (fields.isEmpty()) return

        val currentId = _focusedFieldId.value
        val currentIndex = fields.indexOfFirst { it.id == currentId }

        val targetIndex = if (forward) {
            if (currentIndex < 0) 0 else (currentIndex + 1).coerceAtMost(fields.size - 1)
        } else {
            if (currentIndex <= 0) 0 else currentIndex - 1
        }

        val target = fields[targetIndex]
        _focusedFieldId.value = target.id
        onScrollToPage(target.pageIndex)
    }

    fun selectStamp(stampId: String?) {
        _selectedStampId.value = stampId
    }

    fun placeStamp(
        pageIndex: Int,
        normX: Float,
        normY: Float,
        type: PlacedStampType,
        textValue: String? = null,
        imageBitmap: Bitmap? = null
    ) {
        val stampWidth = when (type) {
            PlacedStampType.SIGNATURE -> 0.30f
            PlacedStampType.INITIALS -> 0.15f
            PlacedStampType.CHECKMARK, PlacedStampType.CROSS -> 0.08f
            PlacedStampType.DATE -> 0.22f
            PlacedStampType.SNIPPET -> (0.05f + (textValue?.length ?: 10) * 0.015f).coerceIn(0.15f, 0.6f)
            PlacedStampType.TEXT -> 0.25f
        }
        val stampHeight = when (type) {
            PlacedStampType.SIGNATURE -> 0.10f
            PlacedStampType.INITIALS -> 0.08f
            PlacedStampType.CHECKMARK, PlacedStampType.CROSS -> 0.06f
            PlacedStampType.DATE -> 0.04f
            PlacedStampType.SNIPPET, PlacedStampType.TEXT -> 0.045f
        }

        val color = when (type) {
            PlacedStampType.CHECKMARK -> 0xFF2E7D32.toInt() // Green
            PlacedStampType.CROSS -> 0xFFC62828.toInt() // Red
            else -> 0xFF0D47A1.toInt() // Classic Blue Ink
        }

        val newStamp = PlacedStampItem(
            id = "stamp_${System.currentTimeMillis()}_${(100..999).random()}",
            type = type,
            pageIndex = pageIndex,
            normX = normX,
            normY = normY,
            normWidth = stampWidth,
            normHeight = stampHeight,
            textValue = textValue,
            imageBitmap = imageBitmap,
            colorArgb = color
        )

        _placedStamps.value = _placedStamps.value + newStamp
        _selectedStampId.value = newStamp.id
        _activeQuickFillType.value = null
        _pendingSnippetText.value = null
    }

    fun updatePlacedStamp(updated: PlacedStampItem) {
        _placedStamps.value = _placedStamps.value.map {
            if (it.id == updated.id) updated else it
        }
    }

    fun deletePlacedStamp(stampId: String) {
        _placedStamps.value = _placedStamps.value.filterNot { it.id == stampId }
        if (_selectedStampId.value == stampId) {
            _selectedStampId.value = null
        }
    }

    fun addFormFieldToCurrentPage(
        pageIndex: Int,
        fieldType: FormFieldType,
        fieldName: String,
        defaultValue: String,
        isRequired: Boolean
    ) {
        viewModelScope.launch {
            val uri = documentManager.currentUri ?: return@launch
            val tempOutput = File(getApplication<Application>().cacheDir, "form_edit_${System.currentTimeMillis()}.pdf")
            val bounds = NormalizedRect(
                left = 0.15f,
                top = 0.25f,
                right = when (fieldType) {
                    FormFieldType.CHECKBOX, FormFieldType.RADIO -> 0.22f
                    else -> 0.65f
                },
                bottom = when (fieldType) {
                    FormFieldType.CHECKBOX, FormFieldType.RADIO -> 0.30f
                    else -> 0.30f
                }
            )

            val success = formEngine.addNewFormField(
                sourceFile = documentManager.cachedFile,
                sourceUri = uri,
                pageIndex = pageIndex,
                bounds = bounds,
                fieldType = fieldType,
                fieldName = fieldName,
                defaultValue = defaultValue,
                isRequired = isRequired,
                outputFile = tempOutput
            )

            if (success) {
                val newFieldItem = FormFieldItem(
                    id = "field_${System.currentTimeMillis()}",
                    name = fieldName,
                    fullyQualifiedName = fieldName,
                    pageIndex = pageIndex,
                    bounds = bounds,
                    type = fieldType,
                    currentValue = defaultValue,
                    isRequired = isRequired
                )
                _formFields.value = _formFields.value + newFieldItem
                _focusedFieldId.value = newFieldItem.id
            }
        }
    }

    fun saveFormAndStamps(
        flatten: Boolean,
        onSuccess: (File) -> Unit
    ) {
        viewModelScope.launch {
            _isSavingForm.value = true
            _saveFormCompletedMessage.value = null
            val uri = documentManager.currentUri
            val tempOutput = File(getApplication<Application>().cacheDir, "form_saved_${System.currentTimeMillis()}.pdf")

            val fieldValuesMap = _formFields.value.associate { it.fullyQualifiedName to it.currentValue }

            val success = formEngine.saveFormAndStamps(
                sourceFile = documentManager.cachedFile,
                sourceUri = uri,
                fieldValues = fieldValuesMap,
                placedStamps = _placedStamps.value,
                flatten = flatten,
                outputFile = tempOutput
            )

            _isSavingForm.value = false
            if (success) {
                lastSavedTempFile = tempOutput
                onSuccess(tempOutput)
            }
        }
    }

    fun overwriteOriginalWithSavedFormFile(savedFile: File) {
        overwriteOriginalWithSavedFile(savedFile, forceReloadViewer = true)
        _saveFormCompletedMessage.value = "Original file updated successfully!"
    }

    fun exportSavedFormFileToUri(savedFile: File, targetUri: Uri) {
        viewModelScope.launch {
            try {
                getApplication<Application>().contentResolver.openOutputStream(targetUri)?.use { out ->
                    savedFile.inputStream().use { input -> input.copyTo(out) }
                }
                _saveFormCompletedMessage.value = "New copy saved successfully!"
            } catch (ignored: Exception) {
            }
        }
    }

    // ==========================================
    // Annotations Management (Undo/Redo/CRUD)
    // ==========================================
    private fun pushUndoState() {
        undoStack.add(_allAnnotations.value)
        redoStack.clear()
        _canUndo.value = true
        _canRedo.value = false
    }

    fun addAnnotation(annotation: PdfAnnotation) {
        pushUndoState()
        _allAnnotations.value = _allAnnotations.value + annotation
        _selectedAnnotationId.value = annotation.id
    }

    fun updateAnnotation(annotation: PdfAnnotation) {
        pushUndoState()
        _allAnnotations.value = _allAnnotations.value.map {
            if (it.id == annotation.id) annotation else it
        }
    }

    fun deleteAnnotation(id: String) {
        pushUndoState()
        _allAnnotations.value = _allAnnotations.value.filter { it.id != id }
        if (_selectedAnnotationId.value == id) {
            _selectedAnnotationId.value = null
        }
    }

    fun deleteSelectedAnnotation() {
        val id = _selectedAnnotationId.value ?: return
        deleteAnnotation(id)
    }

    fun eraseAnnotationAt(pageIndex: Int, normX: Float, normY: Float) {
        val target = _allAnnotations.value.filter { it.pageIndex == pageIndex }.lastOrNull { annot ->
            when (annot) {
                is PdfAnnotation.TextMarkup -> annot.rects.any { it.contains(normX, normY) }
                is PdfAnnotation.InkStroke -> annot.points.any {
                    kotlin.math.abs(it.x - normX) < 0.04f && kotlin.math.abs(it.y - normY) < 0.04f
                }
                is PdfAnnotation.StickyNote -> {
                    kotlin.math.abs(annot.position.x - normX) < 0.05f && kotlin.math.abs(annot.position.y - normY) < 0.05f
                }
                is PdfAnnotation.FreeTextBox -> annot.bounds.contains(normX, normY)
                is PdfAnnotation.GeometricShape -> {
                    val minX = minOf(annot.startPoint.x, annot.endPoint.x)
                    val maxX = maxOf(annot.startPoint.x, annot.endPoint.x)
                    val minY = minOf(annot.startPoint.y, annot.endPoint.y)
                    val maxY = maxOf(annot.startPoint.y, annot.endPoint.y)
                    normX in (minX - 0.03f)..(maxX + 0.03f) && normY in (minY - 0.03f)..(maxY + 0.03f)
                }
            }
        }

        if (target != null) {
            deleteAnnotation(target.id)
        }
    }

    fun eraseLastOrActive() {
        val page = _currentPage.value
        val lastOnPage = _allAnnotations.value.filter { it.pageIndex == page }.lastOrNull()
        if (lastOnPage != null) {
            deleteAnnotation(lastOnPage.id)
        }
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            val previous = undoStack.removeAt(undoStack.size - 1)
            redoStack.add(_allAnnotations.value)
            _allAnnotations.value = previous
            _canUndo.value = undoStack.isNotEmpty()
            _canRedo.value = true
            _selectedAnnotationId.value = null
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            val next = redoStack.removeAt(redoStack.size - 1)
            undoStack.add(_allAnnotations.value)
            _allAnnotations.value = next
            _canUndo.value = true
            _canRedo.value = redoStack.isNotEmpty()
            _selectedAnnotationId.value = null
        }
    }

    fun selectAnnotation(id: String?) {
        _selectedAnnotationId.value = id
    }

    fun setActiveAnnotationTool(tool: AnnotationType?) {
        _activeAnnotationTool.value = tool
        _selectedAnnotationId.value = null
    }

    fun setAnnotationColor(colorArgb: Int) {
        _activeAnnotationColor.value = colorArgb
    }

    fun setAnnotationThickness(thickness: Float) {
        _activeAnnotationThickness.value = thickness
    }

    fun toggleAnnotationToolbar() {
        _isAnnotationToolbarVisible.value = !_isAnnotationToolbarVisible.value
        if (!_isAnnotationToolbarVisible.value) {
            _activeAnnotationTool.value = null
        }
    }

    fun setAnnotationsListOpen(open: Boolean) {
        _isAnnotationsListOpen.value = open
    }

    fun setSaveDialogVisible(visible: Boolean) {
        _isSaveDialogVisible.value = visible
    }

    // Save PDF with Annotations
    fun saveDocumentWithAnnotations(
        flatten: Boolean,
        onSuccess: (File) -> Unit
    ) {
        viewModelScope.launch {
            annotationEngine.saveDocumentWithAnnotations(
                sourceFile = documentManager.cachedFile,
                sourceUri = documentManager.currentUri,
                annotations = _allAnnotations.value,
                flatten = flatten
            ) { progress ->
                _saveProgress.value = progress
                if (progress is SaveAnnotationProgress.Success) {
                    lastSavedTempFile = progress.savedFile
                    onSuccess(progress.savedFile)
                }
            }
        }
    }

    fun overwriteOriginalWithSavedFile(savedFile: File, forceReloadViewer: Boolean = false) {
        viewModelScope.launch {
            val uri = documentManager.currentUri ?: return@launch
            try {
                if (uri.scheme == "file" && uri.path != null) {
                    val targetFile = File(uri.path!!)
                    if (targetFile.absolutePath != savedFile.absolutePath) {
                        savedFile.copyTo(targetFile, overwrite = true)
                    }
                } else {
                    getApplication<Application>().contentResolver.openOutputStream(uri, "rwt")?.use { out ->
                        savedFile.inputStream().use { input -> input.copyTo(out) }
                    }
                }
            } catch (e: Exception) {
                try {
                    getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use { out ->
                        savedFile.inputStream().use { input -> input.copyTo(out) }
                    }
                } catch (ignored: Exception) {
                }
            }

            if (forceReloadViewer || documentManager.cachedFile?.absolutePath != savedFile.absolutePath) {
                val newPageCount = documentManager.replaceOpenDocumentWithFile(savedFile)
                val safeCount = newPageCount.coerceAtLeast(1)
                val clampedPage = _currentPage.value.coerceIn(0, safeCount - 1)
                _currentPage.value = clampedPage
                val currentState = _viewerState.value
                if (currentState is ViewerState.Viewing) {
                    _viewerState.value = currentState.copy(
                        pageCount = safeCount,
                        fileSize = savedFile.length(),
                        initialPage = clampedPage
                    )
                    repository.recordPdfOpened(
                        uri = uri.toString(),
                        fileName = currentState.fileName,
                        fileSizeBytes = savedFile.length(),
                        pageCount = safeCount
                    )
                }
                _pageOcrResults.value = emptyMap()
                _pageExistingTextLines.value = emptyMap()
                cachedDocumentChunks = null
                _documentRenderVersion.value++
            }

            _saveProgress.value = null
            _isSaveDialogVisible.value = false
        }
    }

    fun exportSavedFileToUri(savedFile: File, targetUri: Uri) {
        viewModelScope.launch {
            try {
                getApplication<Application>().contentResolver.openOutputStream(targetUri)?.use { out ->
                    savedFile.inputStream().use { input -> input.copyTo(out) }
                }
            } catch (ignored: Exception) {
            }
            _saveProgress.value = null
            _isSaveDialogVisible.value = false
        }
    }

    fun clearSaveProgress() {
        _saveProgress.value = null
    }

    fun onPageChanged(pageIndex: Int) {
        _currentPage.value = pageIndex
        if (_isContentEditActive.value) {
            inspectPageForContentEdit(pageIndex)
        }
        if (_isOcrSelectionModeActive.value || _isOcrSheetOpen.value) {
            runOcrOnPage(pageIndex)
        } else {
            checkIfPageIsScannedWithoutText(pageIndex)
        }
        val uriStr = _activeUriString.value ?: return

        // Debounce database update
        savePageJob?.cancel()
        savePageJob = viewModelScope.launch {
            delay(400)
            repository.updateLastPage(uriStr, pageIndex)
        }
    }

    fun jumpToPage(pageIndex: Int) {
        val total = documentManager.totalPages
        if (total > 0) {
            val clamped = pageIndex.coerceIn(0, total - 1)
            onPageChanged(clamped)
        }
    }

    fun setReadingMode(mode: ReadingMode) {
        _readingMode.value = mode
        settingsManager.setReadingMode(mode)
    }

    fun toggleFullScreen() {
        _isFullScreen.value = !_isFullScreen.value
    }

    fun setFullScreen(full: Boolean) {
        _isFullScreen.value = full
    }

    fun toggleNightMode() {
        _isNightMode.value = !_isNightMode.value
    }

    fun setThumbnailSheetOpen(open: Boolean) {
        _isThumbnailSheetOpen.value = open
    }

    fun setThumbnailBottomDrawerOpen(open: Boolean) {
        _isThumbnailBottomDrawerOpen.value = open
    }

    fun toggleThumbnailBottomDrawer() {
        _isThumbnailBottomDrawerOpen.value = !_isThumbnailBottomDrawerOpen.value
    }

    fun switchBottomDrawerToSheet() {
        _isThumbnailBottomDrawerOpen.value = false
        _isThumbnailSheetOpen.value = true
    }

    fun switchSheetToBottomDrawer() {
        _isThumbnailSheetOpen.value = false
        _isThumbnailBottomDrawerOpen.value = true
    }

    fun setSelectedSectionId(sectionId: String?) {
        _selectedSectionId.value = sectionId
    }

    fun setThumbnailSidebarOpen(open: Boolean) {
        _isThumbnailSidebarOpen.value = open
    }

    fun toggleThumbnailSidebar() {
        _isThumbnailSidebarOpen.value = !_isThumbnailSidebarOpen.value
    }

    fun switchThumbnailViewToSidebar() {
        _isThumbnailSheetOpen.value = false
        _isThumbnailBottomDrawerOpen.value = false
        _isThumbnailSidebarOpen.value = true
    }

    fun switchThumbnailViewToBottomSheet() {
        _isThumbnailSidebarOpen.value = false
        _isThumbnailBottomDrawerOpen.value = false
        _isThumbnailSheetOpen.value = true
    }

    fun setThumbnailFilterMode(mode: ThumbnailFilterMode) {
        _thumbnailFilterMode.value = mode
    }

    fun setJumpDialogOpen(open: Boolean) {
        _isJumpDialogOpen.value = open
    }

    fun setOutlineSheetOpen(open: Boolean) {
        _isOutlineSheetOpen.value = open
    }

    // Auto-Scroll Controls
    fun toggleAutoScroll() {
        _isAutoScrollActive.value = !_isAutoScrollActive.value
    }

    fun setAutoScrollSpeed(speed: Float) {
        _autoScrollSpeed.value = speed.coerceIn(0.5f, 5.0f)
    }

    fun stopAutoScroll() {
        _isAutoScrollActive.value = false
    }

    // Text Search Controls
    fun toggleSearch() {
        _isSearchVisible.value = !_isSearchVisible.value
        if (!_isSearchVisible.value) {
            closeSearch()
        }
    }

    fun onPdfSearchQueryChanged(query: String) {
        _pdfSearchQuery.value = query
        searchJob?.cancel()

        if (query.isBlank()) {
            _searchMatches.value = emptyList()
            _selectedMatchIndex.value = -1
            _searchProgress.value = SearchProgress.Complete(emptyList())
            return
        }

        searchJob = viewModelScope.launch {
            delay(300) // Debounce typing
            val uri = documentManager.currentUri
            val file = documentManager.cachedFile

            pdfBoxHelper.searchDocument(file, uri, query).collect { progress ->
                _searchProgress.value = progress
                if (progress is SearchProgress.Complete) {
                    _searchMatches.value = progress.matches
                    _selectedMatchIndex.value = if (progress.matches.isNotEmpty()) 0 else -1
                    if (progress.matches.isNotEmpty()) {
                        jumpToPage(progress.matches[0].pageIndex)
                    }
                }
            }
        }
    }

    fun nextSearchMatch() {
        val matches = _searchMatches.value
        if (matches.isEmpty()) return
        val current = _selectedMatchIndex.value
        val next = (current + 1) % matches.size
        _selectedMatchIndex.value = next
        jumpToPage(matches[next].pageIndex)
    }

    fun previousSearchMatch() {
        val matches = _searchMatches.value
        if (matches.isEmpty()) return
        val current = _selectedMatchIndex.value
        val prev = if (current <= 0) matches.size - 1 else current - 1
        _selectedMatchIndex.value = prev
        jumpToPage(matches[prev].pageIndex)
    }

    fun jumpToMatch(index: Int) {
        val matches = _searchMatches.value
        if (index in matches.indices) {
            _selectedMatchIndex.value = index
            jumpToPage(matches[index].pageIndex)
        }
    }

    fun closeSearch() {
        searchJob?.cancel()
        _isSearchVisible.value = false
        _pdfSearchQuery.value = ""
        _searchMatches.value = emptyList()
        _selectedMatchIndex.value = -1
        _searchProgress.value = SearchProgress.Complete(emptyList())
    }

    // User Bookmarks
    fun addBookmarkForCurrentPage(note: String = "") {
        val uri = _activeUriString.value ?: return
        val page = _currentPage.value
        viewModelScope.launch {
            repository.addBookmark(uri, page, note.ifBlank { "Page ${page + 1}" })
        }
    }

    fun togglePageBookmark(pageIndex: Int, note: String = "") {
        val uri = _activeUriString.value ?: return
        val existing = currentBookmarks.value.firstOrNull { it.pageNumber == pageIndex }
        viewModelScope.launch {
            if (existing != null) {
                repository.deleteBookmark(existing)
            } else {
                repository.addBookmark(uri, pageIndex, note.ifBlank { "Page ${pageIndex + 1}" })
            }
        }
    }

    fun deleteBookmark(bookmark: BookmarkEntity) {
        viewModelScope.launch {
            repository.deleteBookmark(bookmark)
        }
    }

    // Text-To-Speech (TTS)
    fun toggleTtsBar() {
        _isTtsBarVisible.value = !_isTtsBarVisible.value
        if (_isTtsBarVisible.value) {
            readCurrentPageTts()
        } else {
            closeTts()
        }
    }

    fun readCurrentPageTts() {
        viewModelScope.launch {
            _isTtsLoading.value = true
            val page = _currentPage.value
            var text = pdfBoxHelper.extractPageText(
                documentManager.cachedFile,
                documentManager.currentUri,
                page
            )
            // Fallback to ML Kit OCR if page is a scanned image with no embedded text
            if (text.isBlank()) {
                val ocrRes = _pageOcrResults.value[page] ?: run {
                    val bmp = getPageBitmap(page, targetWidth = 1440)
                    val res = if (bmp != null) {
                        ocrEngine.recognizeTextFromBitmap(bmp)
                    } else {
                        ocrEngine.recognizeTextFromPdfPage(
                            documentManager.cachedFile,
                            documentManager.currentUri,
                            page
                        )
                    }
                    if (res.fullText.isNotBlank()) {
                        _pageOcrResults.value = _pageOcrResults.value + (page to res)
                    }
                    res
                }
                text = ocrRes.fullText
            }
            _ttsCurrentText.value = text
            _isTtsLoading.value = false

            if (text.isNotBlank()) {
                ttsManager.speak(text) {
                    // Automatically proceed to next page if available
                    if (_currentPage.value + 1 < documentManager.totalPages) {
                        jumpToPage(_currentPage.value + 1)
                        readCurrentPageTts()
                    }
                }
            }
        }
    }

    fun toggleTtsPlayPause() {
        if (ttsManager.isPlaying.value) {
            ttsManager.pause()
        } else {
            if (_ttsCurrentText.value.isNotBlank()) {
                ttsManager.resume()
            } else {
                readCurrentPageTts()
            }
        }
    }

    fun setTtsSpeed(speed: Float) {
        ttsManager.setSpeed(speed)
        settingsManager.setTtsSpeed(speed)
    }

    fun nextTtsPage() {
        if (_currentPage.value + 1 < documentManager.totalPages) {
            jumpToPage(_currentPage.value + 1)
            readCurrentPageTts()
        }
    }

    fun prevTtsPage() {
        if (_currentPage.value > 0) {
            jumpToPage(_currentPage.value - 1)
            readCurrentPageTts()
        }
    }

    fun closeTts() {
        ttsManager.stop()
        _isTtsBarVisible.value = false
        _ttsCurrentText.value = ""
        _isTtsLoading.value = false
    }

    // Settings
    fun setThemeMode(mode: AppThemeMode) {
        settingsManager.setThemeMode(mode)
    }

    fun setDefaultReadingMode(mode: ReadingMode) {
        settingsManager.setReadingMode(mode)
        _readingMode.value = mode
    }

    fun setKeepScreenOn(keepOn: Boolean) {
        settingsManager.setKeepScreenOn(keepOn)
    }

    fun closeViewer() {
        metadataLoadJob?.cancel()
        savePageJob?.cancel()
        searchJob?.cancel()
        stopAutoScroll()
        closeSearch()
        closeTts()
        documentManager.close()
        _activeUriString.value = null
        _viewerState.value = ViewerState.Idle
        _isThumbnailSheetOpen.value = false
        _isThumbnailBottomDrawerOpen.value = false
        _isThumbnailSidebarOpen.value = false
        _thumbnailFilterMode.value = ThumbnailFilterMode.ALL_PAGES
        _selectedSectionId.value = null
        _isJumpDialogOpen.value = false
        _isOutlineSheetOpen.value = false
        _isFullScreen.value = false
        _outlineItems.value = emptyList()
        _isAnnotationToolbarVisible.value = false
        _activeAnnotationTool.value = null
        _allAnnotations.value = emptyList()
        _formFields.value = emptyList()
        _placedStamps.value = emptyList()
        _isFillAndSignActive.value = false
        _activeQuickFillType.value = null
        _pendingSnippetText.value = null
        _focusedFieldId.value = null
        _selectedStampId.value = null
        undoStack.clear()
        redoStack.clear()
        _canUndo.value = false
        _canRedo.value = false
        cancelActiveAiRequest()
        _isAiSheetOpen.value = false
        resetOcrStateForNewDocument()
        resetAiSessionForNewDocument()
    }

    fun trimDeviceMemory() {
        documentManager.trimMemory()
        if (_viewerState.value is ViewerState.Idle) {
            cachedDocumentChunks = null
            _pageExistingTextLines.value = emptyMap()
        }
    }

    fun toggleLightDarkTheme() {
        val current = settingsManager.settings.value.themeMode
        val next = if (current == AppThemeMode.DARK) AppThemeMode.LIGHT else AppThemeMode.DARK
        setThemeMode(next)
    }

    suspend fun getPageBitmap(pageIndex: Int, targetWidth: Int = 1080): Bitmap? {
        return documentManager.getPageBitmap(pageIndex, targetWidth)
    }

    suspend fun getThumbnailBitmap(pageIndex: Int): Bitmap? {
        return documentManager.getThumbnailBitmap(pageIndex)
    }

    fun toggleFavorite(uri: String, current: Boolean) {
        viewModelScope.launch {
            repository.toggleFavorite(uri, current)
        }
    }

    fun deleteRecent(uri: String) {
        viewModelScope.launch {
            repository.deleteRecent(uri)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    // ==========================================
    // PDF Tools & Background Operations
    // ==========================================

    fun cancelActiveOperation() {
        activeOperationJob?.cancel()
        activeOperationJob = null
        PdfOperationService.stop(getApplication())
        _operationProgress.value = OperationProgress.Idle
    }

    fun dismissOperationDialog() {
        _operationProgress.value = OperationProgress.Idle
    }

    fun executeMerge(items: List<MergeItem>, outputName: String) {
        activeOperationJob?.cancel()
        PdfOperationService.start(getApplication(), "Merging Documents", "Starting merge...")
        PdfOperationService.onCancelRequested = { cancelActiveOperation() }

        activeOperationJob = viewModelScope.launch {
            _operationProgress.value = OperationProgress.Running("Merging Documents", "Preparing files...", 0f)
            try {
                val resultFile = operationEngine.mergeDocuments(items, outputName) { frac, step ->
                    _operationProgress.value = OperationProgress.Running("Merging Documents", step, frac)
                    PdfOperationService.updateProgress(getApplication(), "Merging Documents", step, (frac * 100).toInt())
                }
                PdfOperationService.stop(getApplication())
                _operationProgress.value = OperationProgress.Success(
                    title = "Merge Complete",
                    message = "Successfully combined ${items.size} files into $outputName",
                    outputFiles = listOf(resultFile),
                    primaryFile = resultFile
                )
            } catch (e: Exception) {
                PdfOperationService.stop(getApplication())
                if (e !is kotlinx.coroutines.CancellationException) {
                    _operationProgress.value = OperationProgress.Failure("Merge Failed", e.localizedMessage ?: "Unknown error")
                }
            }
        }
    }

    fun executeSplit(
        sourceUri: Uri,
        sourceName: String,
        mode: SplitMode,
        destination: SplitDestination,
        folderUri: Uri?
    ) {
        activeOperationJob?.cancel()
        PdfOperationService.start(getApplication(), "Splitting PDF", "Preparing split...")
        PdfOperationService.onCancelRequested = { cancelActiveOperation() }

        activeOperationJob = viewModelScope.launch {
            _operationProgress.value = OperationProgress.Running("Splitting PDF", "Analyzing pages...", 0f)
            try {
                val (parts, zipFile) = operationEngine.splitPdf(sourceUri, sourceName, mode, destination, folderUri) { frac, step ->
                    _operationProgress.value = OperationProgress.Running("Splitting PDF", step, frac)
                    PdfOperationService.updateProgress(getApplication(), "Splitting PDF", step, (frac * 100).toInt())
                }
                PdfOperationService.stop(getApplication())
                val isZip = destination == SplitDestination.ZIP_ARCHIVE && zipFile != null
                _operationProgress.value = OperationProgress.Success(
                    title = "Split Complete",
                    message = "Created ${parts.size} split PDF document(s)",
                    outputFiles = parts,
                    primaryFile = if (isZip) zipFile else parts.firstOrNull(),
                    isZip = isZip
                )
            } catch (e: Exception) {
                PdfOperationService.stop(getApplication())
                if (e !is kotlinx.coroutines.CancellationException) {
                    _operationProgress.value = OperationProgress.Failure("Split Failed", e.localizedMessage ?: "Unknown error")
                }
            }
        }
    }

    fun executeExtract(sourceUri: Uri, pageIndices: List<Int>, outputName: String) {
        activeOperationJob?.cancel()
        PdfOperationService.start(getApplication(), "Extracting Pages", "Extracting selected pages...")
        PdfOperationService.onCancelRequested = { cancelActiveOperation() }

        activeOperationJob = viewModelScope.launch {
            _operationProgress.value = OperationProgress.Running("Extracting Pages", "Extracting...", 0f)
            try {
                val file = operationEngine.extractPages(sourceUri, pageIndices, outputName) { frac, step ->
                    _operationProgress.value = OperationProgress.Running("Extracting Pages", step, frac)
                    PdfOperationService.updateProgress(getApplication(), "Extracting Pages", step, (frac * 100).toInt())
                }
                PdfOperationService.stop(getApplication())
                _operationProgress.value = OperationProgress.Success(
                    title = "Pages Extracted",
                    message = "Extracted ${pageIndices.size} page(s) into $outputName",
                    outputFiles = listOf(file),
                    primaryFile = file
                )
            } catch (e: Exception) {
                PdfOperationService.stop(getApplication())
                if (e !is kotlinx.coroutines.CancellationException) {
                    _operationProgress.value = OperationProgress.Failure("Extraction Failed", e.localizedMessage ?: "Unknown error")
                }
            }
        }
    }

    fun executeRotateSelectedPages(
        sourceFile: File?,
        sourceUri: Uri,
        sourceName: String,
        pageRotationDeltas: Map<Int, Int>,
        overwriteOriginal: Boolean = true,
        outputName: String = "Rotated_Document.pdf"
    ) {
        activeOperationJob?.cancel()
        PdfOperationService.start(getApplication(), "Rotating PDF Pages", "Applying 90° page rotations...")
        PdfOperationService.onCancelRequested = { cancelActiveOperation() }

        activeOperationJob = viewModelScope.launch {
            _operationProgress.value = OperationProgress.Running(
                "Rotating PDF Pages",
                "Rotating selected pages in 90° increments...",
                0f
            )
            try {
                val safeOutName = outputName.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "Rotated_Document.pdf" }
                val targetOutFile = if (!overwriteOriginal) {
                    File(getApplication<Application>().cacheDir, safeOutName)
                } else {
                    null
                }

                val resultFile = operationEngine.rotateSelectedPages(
                    sourceFile = sourceFile,
                    sourceUri = sourceUri,
                    pageRotationDeltas = pageRotationDeltas,
                    outputFile = targetOutFile,
                    overwriteSourceFile = overwriteOriginal
                ) { frac, step ->
                    _operationProgress.value = OperationProgress.Running("Rotating PDF Pages", step, frac)
                    PdfOperationService.updateProgress(getApplication(), "Rotating PDF Pages", step, (frac * 100).toInt())
                }

                if (overwriteOriginal && documentManager.currentUri == sourceUri) {
                    overwriteOriginalWithSavedFile(resultFile, forceReloadViewer = true)
                }

                PdfOperationService.stop(getApplication())
                val rotatedCount = pageRotationDeltas.count { (_, deg) ->
                    com.example.pdf.RotationIncrement.normalizeTo90Step(deg) != 0
                }
                val destDescription = if (overwriteOriginal) sourceName else resultFile.name
                _operationProgress.value = OperationProgress.Success(
                    title = "Pages Rotated & Saved",
                    message = "Rotated $rotatedCount page(s) by 90° increments and saved changes to $destDescription",
                    outputFiles = listOf(resultFile),
                    primaryFile = resultFile
                )
            } catch (e: Exception) {
                PdfOperationService.stop(getApplication())
                if (e !is kotlinx.coroutines.CancellationException) {
                    _operationProgress.value = OperationProgress.Failure(
                        "Rotate Pages Failed",
                        e.localizedMessage ?: "Could not rotate PDF pages"
                    )
                }
            }
        }
    }

    fun executeImagesToPdf(
        images: List<Uri>,
        pageSize: PageSizePreset = PageSizePreset.entries.first(),
        margin: PageMarginPreset = PageMarginPreset.entries.first(),
        orientation: PageOrientationPreset = PageOrientationPreset.entries.first(),
        quality: ImageQualityPreset = ImageQualityPreset.entries.first()
    ) {
        activeOperationJob?.cancel()
        PdfOperationService.start(getApplication(), "Converting Images", "Converting images to PDF...")
        PdfOperationService.onCancelRequested = { cancelActiveOperation() }

        activeOperationJob = viewModelScope.launch {
            _operationProgress.value = OperationProgress.Running("Images to PDF", "Preparing images...", 0f)
            try {
                val file = operationEngine.imagesToPdf(images, pageSize, margin, orientation, quality) { frac, step ->
                    _operationProgress.value = OperationProgress.Running("Images to PDF", step, frac)
                    PdfOperationService.updateProgress(getApplication(), "Images to PDF", step, (frac * 100).toInt())
                }
                PdfOperationService.stop(getApplication())
                _operationProgress.value = OperationProgress.Success(
                    title = "PDF Created",
                    message = "Converted ${images.size} image(s) into a multi-page PDF",
                    outputFiles = listOf(file),
                    primaryFile = file
                )
            } catch (e: Exception) {
                PdfOperationService.stop(getApplication())
                if (e !is kotlinx.coroutines.CancellationException) {
                    _operationProgress.value = OperationProgress.Failure("Conversion Failed", e.localizedMessage ?: "Unknown error")
                }
            }
        }
    }

    fun executePdfToImages(
        sourceUri: Uri,
        sourceName: String,
        pageIndices: List<Int>,
        format: ExportImageFormat,
        dpi: ExportDpi,
        destination: SplitDestination,
        folderUri: Uri?
    ) {
        activeOperationJob?.cancel()
        PdfOperationService.start(getApplication(), "Exporting Images", "Rendering pages...")
        PdfOperationService.onCancelRequested = { cancelActiveOperation() }

        activeOperationJob = viewModelScope.launch {
            _operationProgress.value = OperationProgress.Running("PDF to Images", "Rendering pages...", 0f)
            try {
                val (files, zipFile) = operationEngine.pdfToImages(sourceUri, sourceName, pageIndices, format, dpi, destination, folderUri) { frac, step ->
                    _operationProgress.value = OperationProgress.Running("PDF to Images", step, frac)
                    PdfOperationService.updateProgress(getApplication(), "PDF to Images", step, (frac * 100).toInt())
                }
                PdfOperationService.stop(getApplication())
                val isZip = destination == SplitDestination.ZIP_ARCHIVE && zipFile != null
                _operationProgress.value = OperationProgress.Success(
                    title = "Images Exported",
                    message = "Rendered ${files.size} page(s) at ${dpi.dpi} DPI (${format.extension.uppercase()})",
                    outputFiles = files,
                    primaryFile = if (isZip) zipFile else files.firstOrNull(),
                    isZip = isZip
                )
            } catch (e: Exception) {
                PdfOperationService.stop(getApplication())
                if (e !is kotlinx.coroutines.CancellationException) {
                    _operationProgress.value = OperationProgress.Failure("Export Failed", e.localizedMessage ?: "Unknown error")
                }
            }
        }
    }

    fun executeSaveOrganizedPages(
        sourceUri: Uri,
        pages: List<OrganizedPage>,
        onSaved: (File) -> Unit
    ) {
        activeOperationJob?.cancel()
        PdfOperationService.start(getApplication(), "Saving Pages", "Reorganizing document...")
        PdfOperationService.onCancelRequested = { cancelActiveOperation() }

        activeOperationJob = viewModelScope.launch {
            _operationProgress.value = OperationProgress.Running("Organizing Pages", "Writing pages...", 0f)
            try {
                val cachedSource = if (sourceUri == documentManager.currentUri) documentManager.cachedFile else null
                val file = operationEngine.saveOrganizedPages(
                    originalUri = sourceUri,
                    pages = pages,
                    originalCachedFile = cachedSource
                ) { frac, step ->
                    _operationProgress.value = OperationProgress.Running("Organizing Pages", step, frac)
                    PdfOperationService.updateProgress(getApplication(), "Organizing Pages", step, (frac * 100).toInt())
                }
                PdfOperationService.stop(getApplication())
                _operationProgress.value = OperationProgress.Idle
                onSaved(file)
            } catch (e: Exception) {
                PdfOperationService.stop(getApplication())
                if (e !is kotlinx.coroutines.CancellationException) {
                    _operationProgress.value = OperationProgress.Failure("Save Failed", e.localizedMessage ?: "Unknown error")
                }
            }
        }
    }

    // ==========================================
    // Gemini Cloud AI Assistant Actions
    // ==========================================
    private fun resetAiSessionForNewDocument() {
        cancelActiveAiRequest()
        hasConfirmedPasswordProtectedAiForCurrentDoc = false
        cachedDocumentChunks = null
        _aiErrorMessage.value = null
        _aiSummaryResult.value = null
        _aiChatMessages.value = emptyList()
        _aiTranslationResult.value = null
        _aiExplainResult.value = null
        _selectedTextForAi.value = ""
    }

    fun openPdfAndLaunchAiTab(uri: Uri, tab: AiToolTab) {
        pendingTabAfterPdfOpen = tab
        openPdf(uri)
    }

    fun openAiAssistant(tab: AiToolTab = _activeAiTab.value, preselectedText: String? = null) {
        _activeAiTab.value = tab
        if (!preselectedText.isNullOrBlank()) {
            _selectedTextForAi.value = preselectedText
        }
        _aiErrorMessage.value = null
        settingsManager.refreshDailyCounterIfNeeded()

        val defaultSummaryLang = if (settingsManager.settings.value.appLanguage == com.example.data.AppLanguage.BENGALI) {
            AiOutputLanguage.BENGALI
        } else {
            AiOutputLanguage.ENGLISH
        }

        // If user has never given Cloud AI consent yet, show the consent dialog before first AI use
        if (!settingsManager.settings.value.aiConsentGranted) {
            pendingAiActionAfterConsent = {
                _isAiSheetOpen.value = true
                if (_activeAiTab.value == AiToolTab.SUMMARY && _aiSummaryResult.value == null && !_isAiRequestRunning.value) {
                    val total = documentManager.totalPages.coerceAtLeast(1)
                    requestDocumentSummary(
                        wholeDocument = true,
                        pageRangeInput = "1-$total",
                        outputLanguage = defaultSummaryLang
                    )
                }
            }
            _isAiConsentDialogOpen.value = true
        } else {
            _isAiSheetOpen.value = true
            if (tab == AiToolTab.SUMMARY &&
                _aiSummaryResult.value == null &&
                !_isAiRequestRunning.value &&
                settingsManager.settings.value.aiEnabled &&
                !settingsManager.settings.value.isAiLimitReached &&
                !documentManager.isCurrentPdfPasswordProtected &&
                aiEngine.isNetworkAvailable()
            ) {
                val total = documentManager.totalPages.coerceAtLeast(1)
                requestDocumentSummary(
                    wholeDocument = true,
                    pageRangeInput = "1-$total",
                    outputLanguage = defaultSummaryLang
                )
            }
        }
    }

    fun closeAiAssistantSheet() {
        _isAiSheetOpen.value = false
    }

    fun setActiveAiTab(tab: AiToolTab) {
        _activeAiTab.value = tab
        _aiErrorMessage.value = null
    }

    fun setSelectedTextForAi(text: String) {
        _selectedTextForAi.value = text
    }

    fun clearAiErrorMessage() {
        _aiErrorMessage.value = null
    }

    fun acceptAiConsent() {
        settingsManager.setAiConsentGranted(true)
        settingsManager.setAiEnabled(true)
        _isAiConsentDialogOpen.value = false
        val action = pendingAiActionAfterConsent
        pendingAiActionAfterConsent = null
        action?.invoke()
    }

    fun declineAiConsentAndTurnOff() {
        settingsManager.setAiConsentGranted(false)
        settingsManager.setAiEnabled(false)
        _isAiConsentDialogOpen.value = false
        pendingAiActionAfterConsent = null
    }

    fun dismissAiConsentDialog() {
        _isAiConsentDialogOpen.value = false
        pendingAiActionAfterConsent = null
    }

    fun confirmPasswordProtectedAiUse() {
        hasConfirmedPasswordProtectedAiForCurrentDoc = true
        _isAiPasswordConfirmDialogOpen.value = false
        val action = pendingAiActionAfterConsent
        pendingAiActionAfterConsent = null
        action?.invoke()
    }

    fun dismissPasswordProtectedAiDialog() {
        _isAiPasswordConfirmDialogOpen.value = false
        pendingAiActionAfterConsent = null
    }

    fun cancelActiveAiRequest() {
        activeAiJob?.cancel()
        activeAiJob = null
        _isAiRequestRunning.value = false
        _aiStatusMessage.value = ""
    }

    private fun runGuardedAiRequest(
        statusLabel: String,
        action: suspend () -> Unit
    ) {
        _aiErrorMessage.value = null
        settingsManager.refreshDailyCounterIfNeeded()
        val currentSettings = settingsManager.settings.value

        // 1. Check if AI is turned off in Settings
        if (!currentSettings.aiEnabled) {
            _aiErrorMessage.value = "Cloud AI features are turned off. Enable AI in Settings or the top switch to continue."
            return
        }

        // 2. Check Consent before sending any content to Cloud AI
        if (!currentSettings.aiConsentGranted) {
            pendingAiActionAfterConsent = {
                runGuardedAiRequest(statusLabel, action)
            }
            _isAiConsentDialogOpen.value = true
            return
        }

        // 3. Check extra confirmation for password-protected PDFs
        if (documentManager.isCurrentPdfPasswordProtected && !hasConfirmedPasswordProtectedAiForCurrentDoc) {
            pendingAiActionAfterConsent = {
                runGuardedAiRequest(statusLabel, action)
            }
            _isAiPasswordConfirmDialogOpen.value = true
            return
        }

        // 4. Check Daily Usage Limit
        if (currentSettings.isAiLimitReached) {
            _aiErrorMessage.value =
                "Daily AI limit reached (${currentSettings.aiRequestsUsedToday}/${currentSettings.aiDailyLimit} requests used today). You can increase the daily limit in Settings or try again tomorrow."
            return
        }

        // 5. Check Offline Status
        if (!aiEngine.isNetworkAvailable()) {
            _aiErrorMessage.value =
                "You are offline. Cloud AI features require an active internet connection."
            return
        }

        // Execute cancelable coroutine job
        activeAiJob?.cancel()
        activeAiJob = viewModelScope.launch {
            _isAiRequestRunning.value = true
            _aiStatusMessage.value = statusLabel
            try {
                action()
                settingsManager.recordAiRequestUsed()
            } catch (te: kotlinx.coroutines.TimeoutCancellationException) {
                _aiErrorMessage.value = "AI generation timed out after 60 seconds. Please try again or summarize a smaller page range."
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                _aiErrorMessage.value = e.localizedMessage ?: "Cloud AI request failed. Please check your connection and try again."
            } finally {
                _isAiRequestRunning.value = false
                _aiStatusMessage.value = ""
            }
        }
    }

    fun requestDocumentSummary(
        wholeDocument: Boolean,
        pageRangeInput: String,
        outputLanguage: AiOutputLanguage
    ) {
        val totalPages = documentManager.totalPages.coerceAtLeast(1)
        val pageIndices = if (wholeDocument) {
            (0 until totalPages).toList()
        } else {
            PdfAiAssistantEngine.parsePageRangeInput(pageRangeInput, totalPages)
        }

        if (pageIndices.isEmpty()) {
            _aiErrorMessage.value = "Please enter a valid page range between 1 and $totalPages."
            return
        }

        val rangeLabel = if (wholeDocument) {
            "All Pages (1-$totalPages)"
        } else {
            "Pages ${pageRangeInput.trim()}"
        }

        runGuardedAiRequest("Summarizing $rangeLabel in ${outputLanguage.displayName}...") {
            val existingOcrMap = _pageOcrResults.value.mapValues { it.value.fullText }
            val pagesText = aiEngine.extractPagesText(
                cachedFile = documentManager.cachedFile,
                uri = documentManager.currentUri,
                pageIndices = pageIndices,
                existingOcrText = existingOcrMap,
                autoOcrScannedPages = true,
                getPageBitmap = { idx, w -> documentManager.getPageBitmap(idx, w) },
                onStatusUpdate = { msg -> _aiStatusMessage.value = msg }
            )
            _aiStatusMessage.value = "Generating executive summary in ${outputLanguage.displayName}..."
            val result = aiEngine.summarizePages(
                pagesText = pagesText,
                pageRangeLabel = rangeLabel,
                outputLanguage = outputLanguage,
                getPageBitmap = { idx, w -> documentManager.getPageBitmap(idx, w) },
                requestedPageIndices = pageIndices
            )
            _aiSummaryResult.value = result
        }
    }

    fun sendAiChatQuestion(
        question: String,
        outputLanguage: AiOutputLanguage
    ) {
        val trimmed = question.trim()
        if (trimmed.isEmpty()) return

        runGuardedAiRequest("Searching document chunks & asking Gemini...") {
            val userMsg = AiChatMessage(
                isUser = true,
                text = trimmed
            )
            val previousMessages = _aiChatMessages.value
            _aiChatMessages.value = previousMessages + userMsg

            val existingOcrMap = _pageOcrResults.value.mapValues { it.value.fullText }
            // Lazily extract and split document into chunks once per opened PDF
            val chunks = cachedDocumentChunks ?: run {
                val totalPages = documentManager.totalPages.coerceAtLeast(1)
                val allPagesText = aiEngine.extractPagesText(
                    cachedFile = documentManager.cachedFile,
                    uri = documentManager.currentUri,
                    pageIndices = (0 until totalPages).toList(),
                    existingOcrText = existingOcrMap,
                    autoOcrScannedPages = true,
                    getPageBitmap = { idx, w -> documentManager.getPageBitmap(idx, w) },
                    onStatusUpdate = { msg -> _aiStatusMessage.value = msg }
                )
                PdfAiAssistantEngine.splitDocumentTextIntoChunks(allPagesText).also {
                    cachedDocumentChunks = it
                }
            }

            _aiStatusMessage.value = "Asking Gemini..."
            val aiReply = aiEngine.chatWithDocument(
                allChunks = chunks,
                question = trimmed,
                recentHistory = previousMessages,
                outputLanguage = outputLanguage
            )
            _aiChatMessages.value = _aiChatMessages.value + aiReply
        }
    }

    fun requestAiTranslation(
        useSelectedText: Boolean,
        selectedText: String,
        pageRangeInput: String,
        sourceLanguage: String,
        targetLanguage: AiOutputLanguage
    ) {
        val totalPages = documentManager.totalPages.coerceAtLeast(1)
        val pageIndices = if (useSelectedText) {
            emptyList()
        } else {
            PdfAiAssistantEngine.parsePageRangeInput(pageRangeInput, totalPages)
        }

        if (!useSelectedText && pageIndices.isEmpty()) {
            _aiErrorMessage.value = "Please enter a valid page range between 1 and $totalPages."
            return
        }

        runGuardedAiRequest("Translating to ${targetLanguage.displayName}...") {
            val existingOcrMap = _pageOcrResults.value.mapValues { it.value.fullText }
            val pagesText = if (useSelectedText) {
                emptyMap()
            } else {
                aiEngine.extractPagesText(
                    cachedFile = documentManager.cachedFile,
                    uri = documentManager.currentUri,
                    pageIndices = pageIndices,
                    existingOcrText = existingOcrMap,
                    autoOcrScannedPages = true,
                    getPageBitmap = { idx, w -> documentManager.getPageBitmap(idx, w) },
                    onStatusUpdate = { msg -> _aiStatusMessage.value = msg }
                )
            }
            _aiStatusMessage.value = "Translating to ${targetLanguage.displayName}..."
            val result = aiEngine.translateContent(
                sourceLanguage = sourceLanguage,
                targetLanguage = targetLanguage,
                selectedText = if (useSelectedText) selectedText else null,
                pagesText = pagesText
            )
            _aiTranslationResult.value = result
        }
    }

    fun requestExplainOrRewrite(
        selectedText: String,
        mode: AiExplainMode,
        outputLanguage: AiOutputLanguage,
        pageNumber: Int?
    ) {
        runGuardedAiRequest("${mode.label} (${outputLanguage.displayName})...") {
            val result = aiEngine.explainOrRewriteSelection(
                selectedText = selectedText,
                mode = mode,
                outputLanguage = outputLanguage,
                pageNumber = pageNumber
            )
            _aiExplainResult.value = result
        }
    }

    fun exportAiSummaryToPdf(
        result: AiSummaryResult,
        selectedBengaliFont: com.example.pdf.BengaliFontOption = com.example.pdf.BengaliFontOption.KALPURUSH,
        onReady: (File) -> Unit
    ) {
        viewModelScope.launch {
            val out = File(getApplication<Application>().cacheDir, "ai_summary_${System.currentTimeMillis()}.pdf")
            val ok = aiEngine.exportSummaryAsPdf(result, out, selectedBengaliFont)
            if (ok && out.exists()) {
                onReady(out)
            } else {
                _aiErrorMessage.value = "Failed to export summary PDF."
            }
        }
    }

    fun exportAiSummaryToTxt(result: AiSummaryResult, onReady: (File) -> Unit) {
        viewModelScope.launch {
            val out = File(getApplication<Application>().cacheDir, "ai_summary_${System.currentTimeMillis()}.txt")
            val ok = aiEngine.exportSummaryAsTxt(result, out)
            if (ok && out.exists()) {
                onReady(out)
            } else {
                _aiErrorMessage.value = "Failed to export summary text file."
            }
        }
    }

    fun exportAiTranslationToPdf(result: AiTranslationResult, onReady: (File) -> Unit) {
        viewModelScope.launch {
            val out = File(getApplication<Application>().cacheDir, "ai_translated_${System.currentTimeMillis()}.pdf")
            val ok = aiEngine.exportTranslationAsPdf(result, out)
            if (ok && out.exists()) {
                onReady(out)
            } else {
                _aiErrorMessage.value = "Failed to export translated PDF."
            }
        }
    }

    fun exportAiTranslationToTxt(result: AiTranslationResult, onReady: (File) -> Unit) {
        viewModelScope.launch {
            val out = File(getApplication<Application>().cacheDir, "ai_translated_${System.currentTimeMillis()}.txt")
            val ok = aiEngine.exportTranslationAsTxt(result, out)
            if (ok && out.exists()) {
                onReady(out)
            } else {
                _aiErrorMessage.value = "Failed to export translated text file."
            }
        }
    }

    // ==========================================
    // ML Kit OCR Selectable Text Actions (for Scanned / Non-Searchable PDFs)
    // ==========================================

    private fun resetOcrStateForNewDocument() {
        ocrScanCheckJob?.cancel()
        _isOcrSelectionModeActive.value = false
        _isOcrSheetOpen.value = false
        _pageOcrResults.value = emptyMap()
        _isOcrRunningForPage.value = false
        _isCurrentPageScannedWithoutText.value = false
        _selectedOcrLineTexts.value = emptyList()
    }

    private fun checkIfPageIsScannedWithoutText(pageIndex: Int) {
        ocrScanCheckJob?.cancel()
        if (_pageOcrResults.value.containsKey(pageIndex)) {
            _isCurrentPageScannedWithoutText.value = false
            return
        }
        ocrScanCheckJob = viewModelScope.launch {
            delay(350)
            val embeddedText = pdfBoxHelper.extractPageText(
                documentManager.cachedFile,
                documentManager.currentUri,
                pageIndex
            )
            _isCurrentPageScannedWithoutText.value = embeddedText.length < 10
        }
    }

    fun dismissScannedPageOcrBanner() {
        _isCurrentPageScannedWithoutText.value = false
    }

    fun toggleOcrSelectionMode() {
        val next = !_isOcrSelectionModeActive.value
        setOcrSelectionModeActive(next)
    }

    fun setOcrSelectionModeActive(active: Boolean) {
        _isOcrSelectionModeActive.value = active
        if (active) {
            _isAnnotationToolbarVisible.value = false
            _activeAnnotationTool.value = null
            _isFillAndSignActive.value = false
            _isContentEditActive.value = false
            _isCurrentPageScannedWithoutText.value = false
            runOcrOnPage(_currentPage.value)
        } else {
            _selectedOcrLineTexts.value = emptyList()
        }
    }

    fun openOcrTextSheet(pageIndex: Int = _currentPage.value) {
        _isOcrSheetOpen.value = true
        _isCurrentPageScannedWithoutText.value = false
        runOcrOnPage(pageIndex)
    }

    fun closeOcrTextSheet() {
        _isOcrSheetOpen.value = false
    }

    fun toggleSelectOcrLine(lineText: String) {
        val trimmed = lineText.trim()
        if (trimmed.isBlank()) return
        val current = _selectedOcrLineTexts.value.toMutableList()
        if (current.contains(trimmed)) {
            current.remove(trimmed)
        } else {
            current.add(trimmed)
        }
        _selectedOcrLineTexts.value = current
    }

    fun selectAllOcrLinesOnPage(pageIndex: Int = _currentPage.value) {
        val res = _pageOcrResults.value[pageIndex] ?: return
        val allLines = res.lines.map { it.text.trim() }.filter { it.isNotBlank() }
        _selectedOcrLineTexts.value = allLines
    }

    fun clearSelectedOcrLines() {
        _selectedOcrLineTexts.value = emptyList()
    }

    fun runOcrOnPage(pageIndex: Int, forceRefresh: Boolean = false) {
        val activePack = ocrEngine.languagePackManager.selectedLanguagePack.value
        val cached = _pageOcrResults.value[pageIndex]
        if (!forceRefresh && cached != null && cached.languagePackUsed == activePack) {
            return
        }
        if (forceRefresh) {
            _pageOcrResults.value = _pageOcrResults.value - pageIndex
        }
        viewModelScope.launch {
            _isOcrRunningForPage.value = true
            try {
                val result = ocrEngine.recognizeTextFromPdfPage(
                    pdfFile = documentManager.cachedFile,
                    uri = documentManager.currentUri,
                    pageIndex = pageIndex,
                    targetWidth = 1440,
                    languagePack = activePack
                )
                _pageOcrResults.value = _pageOcrResults.value + (pageIndex to result)
                _isCurrentPageScannedWithoutText.value = false
            } finally {
                _isOcrRunningForPage.value = false
            }
        }
    }

    fun runOcrOnAllDocumentPages(onComplete: (String) -> Unit = {}) {
        val total = documentManager.totalPages
        if (total <= 0) return
        val activePack = ocrEngine.languagePackManager.selectedLanguagePack.value
        viewModelScope.launch {
            _isOcrRunningForPage.value = true
            val sb = StringBuilder()
            try {
                for (p in 0 until total) {
                    val existing = _pageOcrResults.value[p]?.takeIf { it.languagePackUsed == activePack }
                    val res = existing ?: run {
                        val recognized = ocrEngine.recognizeTextFromPdfPage(
                            pdfFile = documentManager.cachedFile,
                            uri = documentManager.currentUri,
                            pageIndex = p,
                            targetWidth = 1440,
                            languagePack = activePack
                        )
                        _pageOcrResults.value = _pageOcrResults.value + (p to recognized)
                        recognized
                    }
                    if (res.fullText.isNotBlank()) {
                        sb.append("--- Page ${p + 1} ---\n")
                        sb.append(res.fullText.trim())
                        sb.append("\n\n")
                    }
                }
                onComplete(sb.toString().trim())
            } finally {
                _isOcrRunningForPage.value = false
            }
        }
    }

    fun executeEmbedSearchableOcrInCurrentPdf(outputName: String = "") {
        val uri = documentManager.currentUri ?: return
        val file = documentManager.cachedFile
        val baseName = outputName.ifBlank {
            (documentManager.currentFileName.removeSuffix(".pdf") + "_searchable.pdf")
        }
        activeOperationJob?.cancel()
        PdfOperationService.start(getApplication(), "Making PDF Searchable (OCR)", "Running ML Kit Text Recognition...")
        PdfOperationService.onCancelRequested = { cancelActiveOperation() }

        activeOperationJob = viewModelScope.launch {
            _operationProgress.value = OperationProgress.Running(
                "Making PDF Searchable (OCR)",
                "Initializing ML Kit OCR...",
                0f
            )
            try {
                val resultFile = ocrEngine.embedSearchableOcrIntoPdf(
                    sourceFile = file,
                    sourceUri = uri,
                    outputFileName = baseName,
                    cachedPageResults = _pageOcrResults.value
                ) { frac, step ->
                    _operationProgress.value = OperationProgress.Running("Making PDF Searchable (OCR)", step, frac)
                    PdfOperationService.updateProgress(
                        getApplication(),
                        "Making PDF Searchable (OCR)",
                        step,
                        (frac * 100).toInt()
                    )
                }
                PdfOperationService.stop(getApplication())
                _operationProgress.value = OperationProgress.Success(
                    title = "Searchable PDF Created",
                    message = "Embedded selectable ML Kit OCR text layer into ${resultFile.name}",
                    outputFiles = listOf(resultFile),
                    primaryFile = resultFile
                )
            } catch (e: Exception) {
                PdfOperationService.stop(getApplication())
                if (e !is CancellationException) {
                    _operationProgress.value = OperationProgress.Failure(
                        "OCR Conversion Failed",
                        e.localizedMessage ?: "Could not embed OCR text layer."
                    )
                }
            }
        }
    }

    // ==========================================
    // PDF to MS Word (.docx), Excel (.xlsx), PowerPoint (.pptx), RTF, CSV, HTML, MD & TXT
    // ==========================================
    fun executePdfToWordOrOffice(
        sourceFile: File?,
        sourceUri: Uri,
        sourceName: String,
        config: PdfConversionConfig
    ) {
        activeOperationJob?.cancel()
        val opTitle = "Converting to ${config.format.displayName}"
        PdfOperationService.start(getApplication(), opTitle, "Initializing ${config.format.displayName} converter...")
        PdfOperationService.onCancelRequested = { cancelActiveOperation() }
        activeOperationJob = viewModelScope.launch {
            _operationProgress.value = OperationProgress.Running(
                opTitle,
                "Initializing ${config.format.displayName} converter...",
                0f
            )
            try {
                val outFile = converterEngine.convertPdfDocument(
                    sourceFile = sourceFile,
                    sourceUri = sourceUri,
                    sourceFileName = sourceName,
                    config = config
                ) { frac, step ->
                    _operationProgress.value = OperationProgress.Running(opTitle, step, frac)
                    PdfOperationService.updateProgress(
                        getApplication(),
                        opTitle,
                        step,
                        (frac * 100).toInt()
                    )
                }
                PdfOperationService.stop(getApplication())
                _operationProgress.value = OperationProgress.Success(
                    title = "Converted to ${config.format.displayName}",
                    message = "Successfully exported ${outFile.name}. Tap Open, Save, or Share below.",
                    outputFiles = listOf(outFile),
                    primaryFile = outFile
                )
            } catch (e: Exception) {
                PdfOperationService.stop(getApplication())
                if (e !is CancellationException) {
                    _operationProgress.value = OperationProgress.Failure(
                        "Conversion Failed",
                        e.localizedMessage ?: "Unable to convert PDF to ${config.format.displayName}."
                    )
                }
            }
        }
    }

    /**
     * Generates a rich 6-page sample PDF document on-device using PDFBox and opens it in the viewer,
     * allowing immediate testing of 90° page rotation, the scrollable page thumbnail bottom drawer,
     * and Gemini AI summarization even when no external PDFs are stored on the device.
     */
    fun openOrGenerateSamplePdf() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(getApplication())
                val samplesDir = File(getApplication<Application>().filesDir, "samples").apply { mkdirs() }
                val sampleFile = File(samplesDir, "PDF_All_In_One_Sample_Guide.pdf")
                if (!sampleFile.exists() || sampleFile.length() < 1024L) {
                    val sections = listOf(
                        Triple(
                            "Section 1: Executive Overview & Document Architecture",
                            "PDF ALL IN ONE Pro+Ai delivers an offline-first document workspace with hardware-accelerated rendering.",
                            listOf(
                                "Key Highlight 1: High-precision PDF rendering with continuous vertical & horizontal modes.",
                                "Key Highlight 2: Built-in 90-degree page rotation tool that saves rotated pages directly to file.",
                                "Key Highlight 3: Interactive bottom thumbnail drawer for instant navigation across long documents.",
                                "Key Highlight 4: Gemini AI Executive Summary, Q&A Chat with [Page N] citations, and translation."
                            )
                        ),
                        Triple(
                            "Section 2: Q4 Financial & Operational Performance",
                            "Quarterly revenue reached $14.8M (+28% YoY) with operating margin expanding to 34.2% across all regions.",
                            listOf(
                                "Enterprise Subscriptions: $9.4M (+31% YoY) driven by automated document workflows.",
                                "Cloud & Mobile Efficiency: Memory footprint reduced by 45% via 2 MB streaming scratch buffers.",
                                "Customer Retention: Net Dollar Retention (NDR) improved to 118% in Q4.",
                                "Action Item: Finalize annual audit report and sign compliance forms on Page 6."
                            )
                        ),
                        Triple(
                            "Section 3: 90-Degree Page Rotation & Page Organizer",
                            "Rotate individual pages, odd/even pages, or custom ranges by +90°, -90°, or 180° increments.",
                            listOf(
                                "Step 1: Tap the Rotate Pages (90°) icon in the top viewer bar or Home/Tools screen.",
                                "Step 2: Select specific pages, All Pages, Odd/Even, or enter a custom range like 1-3, 5.",
                                "Step 3: Apply +90° Clockwise, -90° Counter-Clockwise, or 180° Upside-Down rotation.",
                                "Step 4: Save directly to the original PDF file or export as a new rotated PDF document."
                            )
                        ),
                        Triple(
                            "Section 4: Scrollable Page Thumbnails Bottom Drawer",
                            "Quickly browse and jump to any section of long PDF documents using the interactive bottom drawer.",
                            listOf(
                                "Open the Thumbnail Strip button in the floating bottom bar to launch the bottom drawer.",
                                "Filter pages by All Pages, Bookmarked Pages, or Rotated Pages.",
                                "Use the quick-jump slider or section chips (Start, 25%, 50%, 75%, End) for rapid navigation.",
                                "Tap the +90° badge on any thumbnail card to rotate that page in-place and persist to disk."
                            )
                        ),
                        Triple(
                            "Section 5: Gemini AI Executive Summary & Document Q&A",
                            "Extract structured summaries, key takeaways, and action items in English or Bengali (বাংলা).",
                            listOf(
                                "Automatic multi-page text extraction with on-device ML Kit OCR fallback for scanned pages.",
                                "One-tap Executive Summary, Key Takeaways, and Action Items generation.",
                                "Interactive Q&A Chat with clickable [Page N] badges that jump directly to the cited page.",
                                "Side-by-side English and Bengali translation with export to PDF or TXT."
                            )
                        ),
                        Triple(
                            "Section 6: Digital Signatures, AcroForms & Security",
                            "Complete contracts, apply handwritten signatures or stamps, and protect sensitive PDFs with AES-256.",
                            listOf(
                                "Draw smooth vector signatures or import transparent signature stamps.",
                                "Lock confidential PDF documents with 4-digit PIN and Biometric fingerprint verification.",
                                "Export PDFs to editable Microsoft Word (.docx), Excel (.xlsx), PowerPoint (.pptx), or Images.",
                                "End of Sample Guide — Use the bottom thumbnail drawer or Rotate Pages tool to explore!"
                            )
                        )
                    )

                    com.tom_roush.pdfbox.pdmodel.PDDocument().use { doc ->
                        sections.forEachIndexed { idx, (heading, subtitle, bullets) ->
                            val page = com.tom_roush.pdfbox.pdmodel.PDPage(
                                com.tom_roush.pdfbox.pdmodel.common.PDRectangle.A4
                            )
                            doc.addPage(page)
                            com.tom_roush.pdfbox.pdmodel.PDPageContentStream(doc, page).use { cs ->
                                // Header banner
                                cs.setNonStrokingColor(198, 40, 40)
                                cs.addRect(40f, 760f, 515f, 44f)
                                cs.fill()

                                cs.beginText()
                                cs.setFont(com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA_BOLD, 13f)
                                cs.setNonStrokingColor(255, 255, 255)
                                cs.newLineAtOffset(54f, 777f)
                                cs.showText("PDF ALL IN ONE Pro+Ai  •  Interactive Sample Document (Page ${idx + 1} of ${sections.size})")
                                cs.endText()

                                // Section Heading
                                cs.beginText()
                                cs.setFont(com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA_BOLD, 18f)
                                cs.setNonStrokingColor(26, 28, 30)
                                cs.newLineAtOffset(44f, 715f)
                                cs.showText(heading)
                                cs.endText()

                                // Subtitle
                                cs.beginText()
                                cs.setFont(com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA_OBLIQUE, 11.5f)
                                cs.setNonStrokingColor(80, 84, 92)
                                cs.newLineAtOffset(44f, 688f)
                                cs.showText(subtitle)
                                cs.endText()

                                // Divider line
                                cs.setStrokingColor(210, 214, 222)
                                cs.setLineWidth(1f)
                                cs.moveTo(44f, 670f)
                                cs.lineTo(550f, 670f)
                                cs.stroke()

                                // Bullet points
                                var yPos = 635f
                                bullets.forEach { bullet ->
                                    cs.beginText()
                                    cs.setFont(com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA, 12f)
                                    cs.setNonStrokingColor(35, 38, 42)
                                    cs.newLineAtOffset(52f, yPos)
                                    cs.showText("•  $bullet")
                                    cs.endText()
                                    yPos -= 36f
                                }

                                // Footer box
                                cs.setNonStrokingColor(240, 243, 248)
                                cs.addRect(44f, 60f, 506f, 48f)
                                cs.fill()

                                cs.beginText()
                                cs.setFont(com.tom_roush.pdfbox.pdmodel.font.PDType1Font.HELVETICA_BOLD, 10.5f)
                                cs.setNonStrokingColor(198, 40, 40)
                                cs.newLineAtOffset(58f, 80f)
                                cs.showText("Tip: Try rotating this page by 90°, browsing thumbnails in the bottom drawer, or running AI Summary!")
                                cs.endText()
                            }
                        }
                        doc.save(sampleFile)
                    }
                }
                openPdf(Uri.fromFile(sampleFile))
            } catch (_: Exception) {
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        cancelActiveAiRequest()
        ttsManager.shutdown()
        documentManager.close()
    }

    companion object {
        fun provideFactory(application: Application): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val db = AppDatabase.getInstance(application)
                    val repo = PdfRepository(db.recentPdfDao(), db.bookmarkDao())
                    val settings = SettingsManager(application)
                    return PdfViewModel(application, repo, settings) as T
                }
            }
        }
    }
}
