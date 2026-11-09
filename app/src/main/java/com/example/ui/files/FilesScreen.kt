package com.example.ui.files

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.widget.Toast
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.runtime.rememberCoroutineScope
import com.example.R
import com.example.data.HomeSortOrder
import com.example.data.RecentPdfEntity
import com.example.ui.BottomNavTab
import com.example.ui.PdfViewModel
import com.example.ui.common.AssignFolderDialog
import com.example.ui.common.PermissionsAndCloudDrivesCard
import com.example.ui.common.RecentPdfItemCard
import com.example.ui.common.ShareToCloudDriveDialog
import com.example.ui.common.VoiceInputMicButton
import com.example.ui.common.scanPdfUrisInTree
import com.example.ui.tools.PrintPdfDialog
import com.example.ui.tools.WatermarkDialog
import com.example.viewer.UniversalFileParserEngine
import androidx.compose.material.icons.filled.TableChart
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilesScreen(
    viewModel: PdfViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = LocalContext.current as? FragmentActivity
    val scope = rememberCoroutineScope()
    val recentPdfs by viewModel.recentPdfs.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedFolder by viewModel.selectedFolderFilter.collectAsStateWithLifecycle()
    val settings by viewModel.appSettings.collectAsStateWithLifecycle()
    val protectedDocs by viewModel.biometricProtectedUris.collectAsStateWithLifecycle()

    var selectedFilter by remember { mutableStateOf("ALL") } // "ALL", "FAV", "LARGE", "SCANS"
    var showSortMenu by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var pdfForFolderAssign by remember { mutableStateOf<RecentPdfEntity?>(null) }
    var pdfForWatermark by remember { mutableStateOf<RecentPdfEntity?>(null) }
    var pdfForPrint by remember { mutableStateOf<RecentPdfEntity?>(null) }
    var pdfForCloudShare by remember { mutableStateOf<RecentPdfEntity?>(null) }

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

    val grantFolderTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri: Uri? ->
        if (treeUri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {
            }
            scope.launch {
                val uris = scanPdfUrisInTree(context, treeUri)
                if (uris.isNotEmpty()) {
                    viewModel.importPdfUrisToLibrary(uris)
                    Toast.makeText(
                        context,
                        "Imported ${uris.size} PDF(s) from internal folder",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(context, "Internal folder access granted", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val filteredList = remember(recentPdfs, selectedFilter) {
        when (selectedFilter) {
            "FAV" -> recentPdfs.filter { it.isFavorite }
            "LARGE" -> recentPdfs.filter { it.fileSizeBytes > 10 * 1024 * 1024 }
            "SCANS" -> recentPdfs.filter {
                it.fileName.contains("scan", ignoreCase = true) ||
                    it.folderName.equals("Scans", ignoreCase = true)
            }
            "OFFICE_IMG" -> recentPdfs.filter {
                val lower = it.fileName.lowercase(java.util.Locale.US)
                !lower.endsWith(".pdf")
            }
            else -> recentPdfs
        }
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.files_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Row {
                        Box {
                            IconButton(
                                onClick = { showSortMenu = true },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    Icons.Default.Sort,
                                    contentDescription = stringResource(R.string.cd_sort_files)
                                )
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.sort_by_date)) },
                                    onClick = {
                                        viewModel.setHomeSortOrder(HomeSortOrder.DATE)
                                        showSortMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.sort_by_name)) },
                                    onClick = {
                                        viewModel.setHomeSortOrder(HomeSortOrder.NAME)
                                        showSortMenu = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.sort_by_size)) },
                                    onClick = {
                                        viewModel.setHomeSortOrder(HomeSortOrder.SIZE)
                                        showSortMenu = false
                                    }
                                )
                            }
                        }

                        if (recentPdfs.isNotEmpty()) {
                            IconButton(
                                onClick = { showClearConfirm = true },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    Icons.Default.DeleteSweep,
                                    contentDescription = stringResource(R.string.cd_clear_history),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            // Video-Style File Locations Directory List (00:30-00:46 in video)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        FileLocationRow(
                            icon = Icons.Default.PhoneAndroid,
                            title = stringResource(R.string.files_loc_on_device),
                            count = recentPdfs.size,
                            isSelected = selectedFilter == "ALL",
                            onClick = { selectedFilter = "ALL" }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        FileLocationRow(
                            icon = Icons.Default.Star,
                            title = stringResource(R.string.files_loc_starred),
                            count = recentPdfs.count { it.isFavorite },
                            isSelected = selectedFilter == "FAV",
                            onClick = { selectedFilter = "FAV" }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        FileLocationRow(
                            icon = Icons.Default.CameraAlt,
                            title = stringResource(R.string.files_loc_scans),
                            count = recentPdfs.count {
                                it.fileName.contains("scan", ignoreCase = true) ||
                                    it.folderName.equals("Scans", ignoreCase = true)
                            },
                            isSelected = selectedFilter == "SCANS",
                            onClick = {
                                val scanCount = recentPdfs.count {
                                    it.fileName.contains("scan", ignoreCase = true) ||
                                        it.folderName.equals("Scans", ignoreCase = true)
                                }
                                if (scanCount > 0) {
                                    selectedFilter = "SCANS"
                                } else {
                                    viewModel.selectTab(BottomNavTab.SCAN)
                                }
                            }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        FileLocationRow(
                            icon = Icons.Default.Storage,
                            title = stringResource(R.string.files_loc_large),
                            count = recentPdfs.count { it.fileSizeBytes > 10 * 1024 * 1024 },
                            isSelected = selectedFilter == "LARGE",
                            onClick = { selectedFilter = "LARGE" }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        FileLocationRow(
                            icon = Icons.Default.TableChart,
                            title = "Word, Excel & JPEG Files",
                            count = recentPdfs.count { !it.fileName.lowercase(java.util.Locale.US).endsWith(".pdf") },
                            isSelected = selectedFilter == "OFFICE_IMG",
                            onClick = {
                                val nonPdfCount = recentPdfs.count { !it.fileName.lowercase(java.util.Locale.US).endsWith(".pdf") }
                                if (nonPdfCount > 0) {
                                    selectedFilter = "OFFICE_IMG"
                                } else {
                                    openPdfLauncher.launch(UniversalFileParserEngine.ALL_SUPPORTED_OPEN_MIME_TYPES)
                                }
                            }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        FileLocationRow(
                            icon = Icons.Default.FolderOpen,
                            title = stringResource(R.string.files_loc_browse_system),
                            count = null,
                            isSelected = false,
                            showPlusIcon = true,
                            onClick = { openPdfLauncher.launch(UniversalFileParserEngine.ALL_SUPPORTED_OPEN_MIME_TYPES) }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        FileLocationRow(
                            icon = Icons.Default.Folder,
                            title = stringResource(R.string.files_loc_grant_folder),
                            count = null,
                            isSelected = false,
                            showPlusIcon = true,
                            onClick = { grantFolderTreeLauncher.launch(null) }
                        )
                    }
                }
            }

            // Search Bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text(stringResource(R.string.files_search_placeholder)) },
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
                                testTag = "files_voice_search_btn"
                            )
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("files_search_input")
                )
            }

            // Custom Folder Filter Chips (only shown when folders exist, avoiding repeating All/Starred/Large)
            if (allAvailableFolders.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedFolder == null,
                            onClick = { viewModel.setSelectedFolderFilter(null) },
                            label = { Text(stringResource(R.string.folder_all)) },
                            modifier = Modifier.minimumInteractiveComponentSize()
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
                                modifier = Modifier.minimumInteractiveComponentSize()
                            )
                        }
                    }
                }
            }

            // Document Items
            if (filteredList.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank()) {
                                stringResource(R.string.files_no_match, searchQuery)
                            } else {
                                stringResource(R.string.files_empty_category)
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(
                    items = filteredList,
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

        // Video-style Circular Blue '+' FAB at bottom right
        FloatingActionButton(
            onClick = { openPdfLauncher.launch(UniversalFileParserEngine.ALL_SUPPORTED_OPEN_MIME_TYPES) },
            shape = CircleShape,
            containerColor = Color(0xFF1976D2),
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 24.dp)
                .minimumInteractiveComponentSize()
                .testTag("files_fab_open")
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = stringResource(R.string.cd_open_pdf_fab),
                modifier = Modifier.size(28.dp)
            )
        }
    }

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

    pdfForWatermark?.let { targetPdf ->
        WatermarkDialog(
            initialUri = Uri.parse(targetPdf.uri),
            initialFileName = targetPdf.fileName,
            viewModel = viewModel,
            onDismiss = { pdfForWatermark = null }
        )
    }

    pdfForPrint?.let { targetPdf ->
        PrintPdfDialog(
            initialUri = Uri.parse(targetPdf.uri),
            initialFileName = targetPdf.fileName,
            onDismiss = { pdfForPrint = null }
        )
    }

    pdfForCloudShare?.let { targetPdf ->
        ShareToCloudDriveDialog(
            pdfUri = Uri.parse(targetPdf.uri),
            fileName = targetPdf.fileName,
            onDismiss = { pdfForCloudShare = null }
        )
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(stringResource(R.string.files_clear_history_title)) },
            text = { Text(stringResource(R.string.files_clear_history_msg)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearHistory()
                        showClearConfirm = false
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(
                        stringResource(R.string.files_clear_all),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearConfirm = false },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun FileLocationRow(
    icon: ImageVector,
    title: String,
    count: Int?,
    isSelected: Boolean,
    showPlusIcon: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                else Color.Transparent
            )
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )

        if (count != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Icon(
            imageVector = if (showPlusIcon) Icons.Default.Add else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}
