package com.example.ui.home

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SettingsApplications
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ai.AiToolTab
import com.example.data.HomeSortOrder
import com.example.data.RecentPdfEntity
import com.example.ui.BottomNavTab
import com.example.ui.PdfViewModel
import com.example.ui.common.AssignFolderDialog
import com.example.ui.common.PermissionsAndCloudDrivesCard
import com.example.ui.common.RecentPdfItemCard
import com.example.ui.common.ShareToCloudDriveDialog
import com.example.ui.common.VoiceInputMicButton
import com.example.ui.common.formatFileSize
import com.example.ui.onboarding.DefaultPdfReaderGuideDialog
import androidx.compose.material.icons.filled.Description
import com.example.pdf.OperationProgress
import com.example.ui.tools.CompressPdfDialog
import com.example.ui.tools.ImageToPdfDialog
import com.example.ui.tools.MergeDialog
import com.example.ui.tools.OperationFailureDialog
import com.example.ui.tools.OperationProgressDialog
import com.example.ui.tools.OperationResultDialog
import com.example.ui.tools.PdfCropDialog
import com.example.ui.tools.PdfToWordOfficeDialog
import com.example.ui.tools.PrintPdfDialog
import com.example.ui.tools.ProtectPdfDialog
import com.example.ui.tools.RotatePdfPagesDialog
import com.example.ui.tools.WatermarkDialog
import com.example.viewer.UniversalFileParserEngine
import androidx.compose.material.icons.filled.TableChart

data class QuickActionItem(
    val title: String,
    val subtitle: String,
    val badge: String,
    val icon: ImageVector,
    val description: String,
    val testTag: String,
    val containerTint: Color = Color(0xFFFCE4EC),
    val iconTint: Color = Color(0xFFD81B60),
    val isAiBadge: Boolean = false
)

private data class HomeTipItem(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val iconBg: Color,
    val iconTint: Color,
    val actionTag: String
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: PdfViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = LocalContext.current as? FragmentActivity
    val recentPdfs by viewModel.recentPdfs.collectAsStateWithLifecycle()
    val favoritePdfs by viewModel.favoritePdfs.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedFolder by viewModel.selectedFolderFilter.collectAsStateWithLifecycle()
    val settings by viewModel.appSettings.collectAsStateWithLifecycle()
    val isDefaultPdfGuideOpen by viewModel.isDefaultPdfGuideOpen.collectAsStateWithLifecycle()
    val protectedDocs by viewModel.biometricProtectedUris.collectAsStateWithLifecycle()

    // 0 = Recent, 1 = Starred, 2 = On device
    var selectedHomeTab by remember { mutableIntStateOf(0) }
    var currentTipIndex by remember { mutableIntStateOf(0) }
    var isTipDismissedAll by remember { mutableStateOf(false) }
    var showInlineOrganizerBanner by remember { mutableStateOf(true) }
    var showQuickCreateSheet by remember { mutableStateOf(false) }

    var showCompressDialog by remember { mutableStateOf(false) }
    var showMergeDialog by remember { mutableStateOf(false) }
    var showRotatePagesDialog by remember { mutableStateOf(false) }
    var showImageToPdfDialog by remember { mutableStateOf(false) }
    var showProtectDialog by remember { mutableStateOf(false) }
    var showCropDialog by remember { mutableStateOf(false) }
    var showWatermarkDialog by remember { mutableStateOf(false) }
    var showPrintDialog by remember { mutableStateOf(false) }
    var showPdfToOfficeDialog by remember { mutableStateOf(false) }
    val operationProgress by viewModel.operationProgress.collectAsStateWithLifecycle()
    var pdfForWatermark by remember { mutableStateOf<RecentPdfEntity?>(null) }
    var pdfForPrint by remember { mutableStateOf<RecentPdfEntity?>(null) }
    var pdfForCloudShare by remember { mutableStateOf<RecentPdfEntity?>(null) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var pdfForFolderAssign by remember { mutableStateOf<RecentPdfEntity?>(null) }

    // SAF PDF file picker launcher
    val openPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
            }
            viewModel.openPdf(uri)
        }
    }

    val openPdfForSigningLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
            }
            viewModel.openPdf(uri)
            viewModel.setFillAndSignActive(true)
        }
    }

    val openPdfForEditLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
            }
            viewModel.openPdf(uri)
            viewModel.setContentEditActive(true)
        }
    }

    var pendingAiTab by remember { mutableStateOf(AiToolTab.SUMMARY) }
    val openPdfForAiLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
            }
            viewModel.openPdfAndLaunchAiTab(uri, pendingAiTab)
        }
    }

    val showOnlyFavorites = selectedHomeTab == 1
    val displayList = when (selectedHomeTab) {
        1 -> favoritePdfs
        else -> recentPdfs
    }

    val tipEditTitle = stringResource(R.string.home_tip_edit_title)
    val tipEditDesc = stringResource(R.string.home_tip_edit_desc)
    val tipMergeTitle = stringResource(R.string.home_tip_merge_title)
    val tipMergeDesc = stringResource(R.string.home_tip_merge_desc)
    val tipAiTitle = stringResource(R.string.home_tip_ai_title)
    val tipAiDesc = stringResource(R.string.home_tip_ai_desc)
    val tipCropTitle = stringResource(R.string.home_tip_crop_title)
    val tipCropDesc = stringResource(R.string.home_tip_crop_desc)

    val homeTips = remember(tipEditTitle, tipMergeTitle, tipAiTitle, tipCropTitle) {
        listOf(
            HomeTipItem(
                title = tipEditTitle,
                description = tipEditDesc,
                icon = Icons.Default.EditNote,
                iconBg = Color(0xFFFFEBEE),
                iconTint = Color(0xFFD32F2F),
                actionTag = "edit"
            ),
            HomeTipItem(
                title = tipMergeTitle,
                description = tipMergeDesc,
                icon = Icons.Default.CallMerge,
                iconBg = Color(0xFFEDE7F6),
                iconTint = Color(0xFF5E35B1),
                actionTag = "merge"
            ),
            HomeTipItem(
                title = tipAiTitle,
                description = tipAiDesc,
                icon = Icons.Default.AutoAwesome,
                iconBg = Color(0xFFF3E5F5),
                iconTint = Color(0xFF8E24AA),
                actionTag = "ai_summary"
            ),
            HomeTipItem(
                title = tipCropTitle,
                description = tipCropDesc,
                icon = Icons.Default.Crop,
                iconBg = Color(0xFFE0F2F1),
                iconTint = Color(0xFF00796B),
                actionTag = "crop"
            )
        )
    }

    val mergeTitle = stringResource(R.string.quick_merge_title)
    val mergeSubtitle = stringResource(R.string.quick_merge_subtitle)
    val mergeBadge = stringResource(R.string.quick_merge_badge)
    val compressTitle = stringResource(R.string.quick_compress_title)
    val compressSubtitle = stringResource(R.string.quick_compress_subtitle)
    val compressBadge = stringResource(R.string.quick_compress_badge)
    val signTitle = stringResource(R.string.quick_sign_title)
    val signSubtitle = stringResource(R.string.quick_sign_subtitle)
    val signBadge = stringResource(R.string.quick_sign_badge)
    val scanTitle = stringResource(R.string.quick_scan_title)
    val scanSubtitle = stringResource(R.string.quick_scan_subtitle)
    val scanBadge = stringResource(R.string.quick_scan_badge)
    val watermarkTitle = stringResource(R.string.quick_watermark_title)
    val watermarkSubtitle = stringResource(R.string.quick_watermark_subtitle)
    val watermarkBadge = stringResource(R.string.quick_watermark_badge)
    val printTitle = stringResource(R.string.quick_print_title)
    val printSubtitle = stringResource(R.string.quick_print_subtitle)
    val printBadge = stringResource(R.string.quick_print_badge)
    val editPdfTitle = stringResource(R.string.fab_edit_pdf)
    val aiSummaryTitle = stringResource(R.string.fab_ai_summary)
    val aiAssistantTitle = stringResource(R.string.fab_ai_assistant)
    val createPdfTitle = stringResource(R.string.fab_create_pdf)
    val cropPagesTitle = stringResource(R.string.fab_crop_pages)

    // Horizontal quick actions matching the video's "What would you like to do?" row
    val quickActions = remember(
        mergeTitle, compressTitle, signTitle, scanTitle, watermarkTitle, printTitle,
        editPdfTitle, aiSummaryTitle, aiAssistantTitle, createPdfTitle, cropPagesTitle
    ) {
        listOf(
            QuickActionItem(
                title = "Rotate Pages",
                subtitle = "90° increments",
                badge = "90°",
                icon = Icons.Default.RotateRight,
                description = "Rotate selected PDF pages by 90-degree increments and save changes to file.",
                testTag = "action_rotate_pdf",
                containerTint = Color(0xFFFFF3E0),
                iconTint = Color(0xFFE65100)
            ),
            QuickActionItem(
                title = editPdfTitle,
                subtitle = "Add text & images",
                badge = "Pro",
                icon = Icons.Default.EditNote,
                description = "Add or replace text lines, Bengali fonts, images, links, and redactions.",
                testTag = "action_edit_pdf",
                containerTint = Color(0xFFFFEBEE),
                iconTint = Color(0xFFD32F2F)
            ),
            QuickActionItem(
                title = "PDF to Word",
                subtitle = "DOCX, XLSX & PPTX",
                badge = "Office",
                icon = Icons.Default.Description,
                description = "Convert PDF documents to editable MS Word (.docx), Excel (.xlsx), PowerPoint (.pptx), RTF, HTML, Markdown & TXT.",
                testTag = "action_pdf_to_word",
                containerTint = Color(0xFFE3F2FD),
                iconTint = Color(0xFF1565C0)
            ),
            QuickActionItem(
                title = "Word, Excel & JPEG",
                subtitle = "DOCX, XLSX, JPG",
                badge = "Viewer",
                icon = Icons.Default.TableChart,
                description = "Open and view MS Word (.docx/.rtf), MS Excel (.xlsx/.csv) spreadsheets, PowerPoint (.pptx), and JPEG/PNG images.",
                testTag = "action_open_office_image",
                containerTint = Color(0xFFE8F5E9),
                iconTint = Color(0xFF2E7D32)
            ),
            QuickActionItem(
                title = mergeTitle,
                subtitle = mergeSubtitle,
                badge = mergeBadge,
                icon = Icons.Default.CallMerge,
                description = "Merge multiple PDF documents or select specific pages into a single high-quality PDF.",
                testTag = "action_merge_pdf",
                containerTint = Color(0xFFEDE7F6),
                iconTint = Color(0xFF5E35B1)
            ),
            QuickActionItem(
                title = aiSummaryTitle,
                subtitle = "Key takeaways",
                badge = "AI",
                icon = Icons.Default.AutoAwesome,
                description = "Generate executive summaries and key points for any PDF in English or Bengali.",
                testTag = "action_ai_summary",
                containerTint = Color(0xFFF3E5F5),
                iconTint = Color(0xFF8E24AA),
                isAiBadge = true
            ),
            QuickActionItem(
                title = signTitle,
                subtitle = signSubtitle,
                badge = signBadge,
                icon = Icons.Default.Draw,
                description = "Draw your signature or import saved stamps to sign contracts and forms.",
                testTag = "action_sign_pdf",
                containerTint = Color(0xFFFFF3E0),
                iconTint = Color(0xFFE65100)
            ),
            QuickActionItem(
                title = compressTitle,
                subtitle = compressSubtitle,
                badge = compressBadge,
                icon = Icons.Default.Compress,
                description = "Smart PDF compression that reduces file sizes significantly while keeping text sharp.",
                testTag = "action_compress_pdf",
                containerTint = Color(0xFFE0F2F1),
                iconTint = Color(0xFF00796B)
            ),
            QuickActionItem(
                title = cropPagesTitle,
                subtitle = "Custom bounding box",
                badge = "Trim",
                icon = Icons.Default.Crop,
                description = "Define a custom bounding box to remove margins or focus on content.",
                testTag = "action_crop_pdf",
                containerTint = Color(0xFFE0F7FA),
                iconTint = Color(0xFF00838F)
            )
        )
    }

    val allAvailableFolders = remember(settings.customFolders, recentPdfs) {
        (settings.customFolders + recentPdfs.map { it.folderName }.filter { it.isNotBlank() })
            .distinctBy { it.lowercase() }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. "Welcome back" Header + Dismissible/Cyclable Feature Highlight Tip Banner (as in video 00:00-00:06)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.home_welcome_back),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    if (!isTipDismissedAll && homeTips.isNotEmpty()) {
                        val activeTip = homeTips[currentTipIndex % homeTips.size]
                        HomeFeatureTipCard(
                            tip = activeTip,
                            onCardClick = {
                                when (activeTip.actionTag) {
                                    "edit" -> openPdfForEditLauncher.launch(arrayOf("application/pdf"))
                                    "merge" -> showMergeDialog = true
                                    "ai_summary" -> {
                                        pendingAiTab = AiToolTab.SUMMARY
                                        openPdfForAiLauncher.launch(arrayOf("application/pdf"))
                                    }
                                    "crop" -> showCropDialog = true
                                }
                            },
                            onDismissOrNext = {
                                if (currentTipIndex + 1 < homeTips.size) {
                                    currentTipIndex += 1
                                } else {
                                    isTipDismissedAll = true
                                }
                            }
                        )
                    }
                }
            }

            // 2. "What would you like to do?" Horizontal Scrollable Pastel Quick Action Cards (video 00:01-00:12)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.home_what_would_you_like),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("home_quick_actions_row")
                    ) {
                        items(
                            items = quickActions,
                            key = { it.testTag }
                        ) { action ->
                            VideoStyleQuickActionTile(
                                item = action,
                                onClick = {
                                    when (action.testTag) {
                                        "action_rotate_pdf" -> showRotatePagesDialog = true
                                        "action_edit_pdf" -> openPdfForEditLauncher.launch(arrayOf("application/pdf"))
                                        "action_pdf_to_word" -> showPdfToOfficeDialog = true
                                        "action_open_office_image" -> openPdfLauncher.launch(UniversalFileParserEngine.ALL_SUPPORTED_OPEN_MIME_TYPES)
                                        "action_merge_pdf" -> showMergeDialog = true
                                        "action_ai_summary" -> {
                                            pendingAiTab = AiToolTab.SUMMARY
                                            openPdfForAiLauncher.launch(arrayOf("application/pdf"))
                                        }
                                        "action_sign_pdf" -> openPdfForSigningLauncher.launch(arrayOf("application/pdf"))
                                        "action_compress_pdf" -> showCompressDialog = true
                                        "action_scan_pdf" -> viewModel.selectTab(BottomNavTab.SCAN)
                                        "action_ai_chat" -> {
                                            pendingAiTab = AiToolTab.CHAT
                                            openPdfForAiLauncher.launch(arrayOf("application/pdf"))
                                        }
                                        "action_crop_pdf" -> showCropDialog = true
                                        "action_watermark_pdf" -> showWatermarkDialog = true
                                        "action_print_pdf" -> showPrintDialog = true
                                        "action_images_to_pdf" -> showImageToPdfDialog = true
                                        "action_protect_pdf" -> showProtectDialog = true
                                        else -> {}
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // 3. 3-Tab Underline TabRow: Recent | Starred | On device (video 00:13-00:19)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TabRow(
                        selectedTabIndex = selectedHomeTab,
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.primary
                    ) {
                        Tab(
                            selected = selectedHomeTab == 0,
                            onClick = { selectedHomeTab = 0 },
                            text = {
                                Text(
                                    text = stringResource(R.string.home_tab_recent),
                                    fontWeight = if (selectedHomeTab == 0) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("filter_all_recents")
                        )
                        Tab(
                            selected = selectedHomeTab == 1,
                            onClick = { selectedHomeTab = 1 },
                            text = {
                                Text(
                                    text = stringResource(R.string.home_tab_starred),
                                    fontWeight = if (selectedHomeTab == 1) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("filter_favorites")
                        )
                        Tab(
                            selected = selectedHomeTab == 2,
                            onClick = { selectedHomeTab = 2 },
                            text = {
                                Text(
                                    text = stringResource(R.string.home_tab_on_device),
                                    fontWeight = if (selectedHomeTab == 2) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("filter_on_device")
                        )
                    }

                    // Search Bar + Folders & Sort Controls
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = { Text(stringResource(R.string.home_search_placeholder)) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = stringResource(R.string.cd_search_icon)
                            )
                        },
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { viewModel.setSearchQuery("") },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(
                                            Icons.Default.Clear,
                                            contentDescription = stringResource(R.string.cd_clear_search)
                                        )
                                    }
                                }
                                VoiceInputMicButton(
                                    onSpeechResult = { viewModel.setSearchQuery(it) },
                                    testTag = "home_voice_search_btn"
                                )
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("home_search_field")
                    )

                    // Compact Folders & Sort Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = selectedFolder == null,
                            onClick = { viewModel.setSelectedFolderFilter(null) },
                            label = { Text(stringResource(R.string.folder_all)) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("folder_chip_all")
                        )

                        allAvailableFolders.forEach { folder ->
                            FilterChip(
                                selected = selectedFolder.equals(folder, ignoreCase = true),
                                onClick = {
                                    if (selectedFolder.equals(folder, ignoreCase = true)) {
                                        viewModel.setSelectedFolderFilter(null)
                                    } else {
                                        viewModel.setSelectedFolderFilter(folder)
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Folder,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = { Text(folder) },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("folder_chip_$folder")
                            )
                        }

                        AssistChip(
                            onClick = { showCreateFolderDialog = true },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.CreateNewFolder,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = { Text(stringResource(R.string.folder_create_new)) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("home_new_folder_btn")
                        )
                    }

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 4.dp, top = 8.dp, bottom = 8.dp)
                        ) {
                            Icon(
                                Icons.Default.Sort,
                                contentDescription = stringResource(R.string.cd_sort_files),
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.sort_label),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        FilterChip(
                            selected = settings.homeSortOrder == HomeSortOrder.DATE,
                            onClick = { viewModel.setHomeSortOrder(HomeSortOrder.DATE) },
                            label = { Text(stringResource(R.string.sort_by_date)) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("home_sort_date")
                        )
                        FilterChip(
                            selected = settings.homeSortOrder == HomeSortOrder.NAME,
                            onClick = { viewModel.setHomeSortOrder(HomeSortOrder.NAME) },
                            label = { Text(stringResource(R.string.sort_by_name)) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("home_sort_name")
                        )
                        FilterChip(
                            selected = settings.homeSortOrder == HomeSortOrder.SIZE,
                            onClick = { viewModel.setHomeSortOrder(HomeSortOrder.SIZE) },
                            label = { Text(stringResource(R.string.sort_by_size)) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("home_sort_size")
                        )
                    }
                }
            }

            // Tab 2 ("On device") prominent browser CTA card (as shown in video 00:16)
            if (selectedHomeTab == 2) {
                item {
                    OnDeviceBrowserCard(
                        onBrowseDevice = { openPdfLauncher.launch(UniversalFileParserEngine.ALL_SUPPORTED_OPEN_MIME_TYPES) },
                        onDefaultReaderGuide = { viewModel.setDefaultPdfGuideOpen(true) }
                    )
                }
            }

            // File List or Video-Style Empty State
            if (displayList.isEmpty()) {
                item {
                    EmptyRecentsPlaceholder(
                        isFilteredByFavorites = showOnlyFavorites,
                        onOpenPdf = { openPdfLauncher.launch(UniversalFileParserEngine.ALL_SUPPORTED_OPEN_MIME_TYPES) },
                        onOpenSamplePdf = { viewModel.openOrGenerateSamplePdf() }
                    )
                }
            } else {
                items(
                    items = displayList,
                    key = { it.uri }
                ) { pdf ->
                    RecentPdfItemCard(
                        pdf = pdf,
                        isBiometricProtected = protectedDocs.contains(pdf.uri),
                        onToggleBiometricProtection = {
                            viewModel.toggleDocumentBiometricProtection(pdf.uri)
                        },
                        onClick = {
                            viewModel.openPdfWithBiometricCheck(activity, Uri.parse(pdf.uri), pdf.fileName)
                        },
                        onToggleFavorite = { viewModel.toggleFavorite(pdf.uri, pdf.isFavorite) },
                        onDelete = { viewModel.deleteRecent(pdf.uri) },
                        onShare = { pdfForCloudShare = pdf },
                        onAssignFolder = { pdfForFolderAssign = pdf },
                        onWatermark = { pdfForWatermark = pdf },
                        onPrint = { pdfForPrint = pdf }
                    )
                }
            }
        }

        // 4. Blue Circular '+' FAB in bottom-right (video 01:03-01:06)
        FloatingActionButton(
            onClick = { showQuickCreateSheet = true },
            shape = CircleShape,
            containerColor = Color(0xFF1976D2),
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 24.dp)
                .minimumInteractiveComponentSize()
                .testTag("home_fab_plus")
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = stringResource(R.string.fab_quick_actions_title),
                modifier = Modifier.size(28.dp)
            )
        }
    }

    // Video-Style '+' Quick Actions ModalBottomSheet (shown at 01:04 in video)
    if (showQuickCreateSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showQuickCreateSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            ) {
                Text(
                    text = stringResource(R.string.fab_quick_actions_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
                HorizontalDivider(modifier = Modifier.padding(bottom = 4.dp))

                QuickSheetOptionRow(
                    icon = Icons.Default.FolderOpen,
                    title = stringResource(R.string.fab_open_file),
                    badge = "PDF • DOCX • XLSX • JPG",
                    onClick = {
                        showQuickCreateSheet = false
                        openPdfLauncher.launch(UniversalFileParserEngine.ALL_SUPPORTED_OPEN_MIME_TYPES)
                    }
                )
                QuickSheetOptionRow(
                    icon = Icons.Default.CameraAlt,
                    title = stringResource(R.string.fab_scan_doc),
                    onClick = {
                        showQuickCreateSheet = false
                        viewModel.selectTab(BottomNavTab.SCAN)
                    }
                )
                QuickSheetOptionRow(
                    icon = Icons.Default.Image,
                    title = stringResource(R.string.fab_create_pdf),
                    onClick = {
                        showQuickCreateSheet = false
                        showImageToPdfDialog = true
                    }
                )
                QuickSheetOptionRow(
                    icon = Icons.Default.CallMerge,
                    title = stringResource(R.string.fab_combine_files),
                    onClick = {
                        showQuickCreateSheet = false
                        showMergeDialog = true
                    }
                )
                QuickSheetOptionRow(
                    icon = Icons.Default.RotateRight,
                    title = "Rotate PDF Pages (90° Increments)",
                    badge = "90° Step",
                    onClick = {
                        showQuickCreateSheet = false
                        showRotatePagesDialog = true
                    }
                )
                QuickSheetOptionRow(
                    icon = Icons.Default.Chat,
                    title = stringResource(R.string.fab_ai_assistant),
                    badge = "AI",
                    onClick = {
                        showQuickCreateSheet = false
                        pendingAiTab = AiToolTab.CHAT
                        openPdfForAiLauncher.launch(arrayOf("application/pdf"))
                    }
                )
            }
        }
    }

    // Create New Folder Dialog
    if (showCreateFolderDialog) {
        var folderNameInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.folder_create_dialog_title),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                OutlinedTextField(
                    value = folderNameInput,
                    onValueChange = { folderNameInput = it },
                    label = { Text(stringResource(R.string.folder_name_hint)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_create_folder_name")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = folderNameInput.trim()
                        if (trimmed.isNotEmpty()) {
                            viewModel.createFolder(trimmed)
                            viewModel.setSelectedFolderFilter(trimmed)
                        }
                        showCreateFolderDialog = false
                    },
                    enabled = folderNameInput.trim().isNotEmpty(),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("btn_confirm_create_folder")
                ) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCreateFolderDialog = false },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    // Assign PDF to Folder Dialog
    pdfForFolderAssign?.let { targetPdf ->
        AssignFolderDialog(
            pdfFileName = targetPdf.fileName,
            currentFolder = targetPdf.folderName,
            availableFolders = allAvailableFolders,
            onAssignFolder = { folder ->
                viewModel.assignPdfToFolder(targetPdf.uri, folder)
            },
            onCreateAndAssignFolder = { newFolder ->
                viewModel.assignPdfToFolder(targetPdf.uri, newFolder)
            },
            onDismiss = { pdfForFolderAssign = null }
        )
    }

    // Default PDF Reader Guide Dialog
    if (isDefaultPdfGuideOpen) {
        DefaultPdfReaderGuideDialog(
            onDismiss = { viewModel.setDefaultPdfGuideOpen(false) },
            onPickPdfNow = {
                viewModel.setDefaultPdfGuideOpen(false)
                openPdfLauncher.launch(UniversalFileParserEngine.ALL_SUPPORTED_OPEN_MIME_TYPES)
            }
        )
    }

    if (showCompressDialog) {
        CompressPdfDialog(
            initialUri = null,
            initialFileName = null,
            viewModel = viewModel,
            onDismiss = { showCompressDialog = false }
        )
    }

    if (showMergeDialog) {
        MergeDialog(
            initialPdfUri = viewModel.currentViewingUri,
            initialPdfName = viewModel.currentViewingName,
            onMergeConfirmed = { items, name ->
                showMergeDialog = false
                viewModel.executeMerge(items, name)
            },
            onDismiss = { showMergeDialog = false }
        )
    }

    if (showRotatePagesDialog) {
        RotatePdfPagesDialog(
            initialUri = viewModel.currentViewingUri,
            initialName = viewModel.currentViewingName,
            initialCachedFile = viewModel.currentViewingCachedFile,
            initialCurrentPage = viewModel.currentPage.value,
            onRotateConfirmed = { srcFile, srcUri, srcName, deltas, overwrite, outName ->
                showRotatePagesDialog = false
                viewModel.executeRotateSelectedPages(
                    sourceFile = srcFile,
                    sourceUri = srcUri,
                    sourceName = srcName,
                    pageRotationDeltas = deltas,
                    overwriteOriginal = overwrite,
                    outputName = outName
                )
            },
            onDismiss = { showRotatePagesDialog = false }
        )
    }

    if (showImageToPdfDialog) {
        ImageToPdfDialog(
            onConvertConfirmed = { imgs, size, margin, orient, qual ->
                showImageToPdfDialog = false
                viewModel.executeImagesToPdf(imgs, size, margin, orient, qual)
            },
            onDismiss = { showImageToPdfDialog = false }
        )
    }

    if (showProtectDialog) {
        ProtectPdfDialog(
            initialUri = viewModel.currentViewingUri,
            initialFileName = viewModel.currentViewingName,
            viewModel = viewModel,
            onDismiss = { showProtectDialog = false }
        )
    }

    if (showCropDialog) {
        PdfCropDialog(
            initialUri = viewModel.currentViewingUri,
            initialName = viewModel.currentViewingName,
            initialPageIndex = viewModel.currentPage.value,
            totalPageCount = viewModel.currentViewingPageCount.coerceAtLeast(1),
            cachedFile = viewModel.currentViewingCachedFile,
            getPageBitmap = { pIdx, w -> viewModel.getPageBitmap(pIdx, w) },
            onApplyCrop = { pageIdx, normalizedBox, applyToAll, onDone ->
                val srcUri = viewModel.currentViewingUri
                if (srcUri != null) {
                    viewModel.cropDocumentExternal(srcUri, pageIdx, normalizedBox, applyToAll) { croppedFile ->
                        onDone(croppedFile)
                    }
                } else {
                    showCropDialog = false
                }
            },
            onOpenInViewer = { croppedFile ->
                showCropDialog = false
                viewModel.reloadEditedPdfInViewer(croppedFile)
            },
            onDismiss = { showCropDialog = false }
        )
    }

    if (showWatermarkDialog || pdfForWatermark != null) {
        val target = pdfForWatermark
        WatermarkDialog(
            initialUri = target?.let { Uri.parse(it.uri) },
            initialFileName = target?.fileName,
            viewModel = viewModel,
            onDismiss = {
                showWatermarkDialog = false
                pdfForWatermark = null
            }
        )
    }

    if (showPrintDialog || pdfForPrint != null) {
        val target = pdfForPrint
        PrintPdfDialog(
            initialUri = target?.let { Uri.parse(it.uri) },
            initialFileName = target?.fileName,
            onDismiss = {
                showPrintDialog = false
                pdfForPrint = null
            }
        )
    }

    pdfForCloudShare?.let { targetPdf ->
        ShareToCloudDriveDialog(
            pdfUri = Uri.parse(targetPdf.uri),
            fileName = targetPdf.fileName,
            onDismiss = { pdfForCloudShare = null }
        )
    }

    if (showPdfToOfficeDialog) {
        PdfToWordOfficeDialog(
            initialUri = viewModel.currentViewingUri,
            initialFileName = viewModel.currentViewingName,
            initialCachedFile = viewModel.currentViewingCachedFile,
            onConvertConfirmed = { srcFile, srcUri, srcName, config ->
                showPdfToOfficeDialog = false
                viewModel.executePdfToWordOrOffice(srcFile, srcUri, srcName, config)
            },
            onDismiss = { showPdfToOfficeDialog = false }
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
}

@Composable
private fun HomeFeatureTipCard(
    tip: HomeTipItem,
    onCardClick: () -> Unit,
    onDismissOrNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCardClick() }
            .testTag("home_feature_tip_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(tip.iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = tip.icon,
                    contentDescription = null,
                    tint = tip.iconTint,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tip.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = tip.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onDismissOrNext,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("home_tip_dismiss_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.action_cancel),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun VideoStyleQuickActionTile(
    item: QuickActionItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(88.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(vertical = 4.dp)
            .testTag(item.testTag),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(item.containerTint),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.title,
                tint = item.iconTint,
                modifier = Modifier.size(30.dp)
            )

            if (item.isAiBadge) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = item.iconTint,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                ) {
                    Text(
                        text = "AI",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = item.title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InlineLocalStorageCard(
    onOpenPdfClick: () -> Unit,
    onDefaultReaderClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.home_inline_storage_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.home_inline_storage_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.action_cancel),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onOpenPdfClick,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("home_open_pdf_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.home_open_pdf_button))
                }

                OutlinedButton(
                    onClick = onDefaultReaderClick,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("home_default_reader_guide_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.SettingsApplications,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.onboarding_set_default_reader))
                }
            }
        }
    }
}

@Composable
private fun OnDeviceBrowserCard(
    onBrowseDevice: () -> Unit,
    onDefaultReaderGuide: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.home_on_device_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = stringResource(R.string.home_on_device_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onBrowseDevice,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("home_open_pdf_button")
                ) {
                    Text(stringResource(R.string.home_browse_device_btn))
                }
                TextButton(
                    onClick = onDefaultReaderGuide,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("home_default_reader_guide_btn")
                ) {
                    Text(stringResource(R.string.onboarding_set_default_reader))
                }
            }
        }
    }
}

@Composable
private fun QuickSheetOptionRow(
    icon: ImageVector,
    title: String,
    badge: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (badge != null) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFFE8EAF6)
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF3949AB),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyRecentsPlaceholder(
    isFilteredByFavorites: Boolean,
    onOpenPdf: () -> Unit,
    onOpenSamplePdf: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isFilteredByFavorites) Icons.Outlined.StarBorder else Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }

            Text(
                text = if (isFilteredByFavorites) {
                    stringResource(R.string.home_no_starred_title)
                } else {
                    stringResource(R.string.empty_recents_title)
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = if (isFilteredByFavorites) {
                    stringResource(R.string.home_no_starred_desc)
                } else {
                    stringResource(R.string.empty_recents_desc)
                },
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!isFilteredByFavorites) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onOpenPdf,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("empty_open_pdf_btn")
                    ) {
                        Text(stringResource(R.string.empty_select_pdf_btn))
                    }
                    OutlinedButton(
                        onClick = onOpenSamplePdf,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("home_open_sample_pdf_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open 6-Page Sample PDF")
                    }
                }
            }
        }
    }
}
