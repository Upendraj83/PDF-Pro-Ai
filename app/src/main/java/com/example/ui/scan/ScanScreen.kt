package com.example.ui.scan

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.scan.SavedScanRecord
import com.example.scan.ScanCaptureMode
import com.example.scan.ScanOcrEngine
import com.example.ui.PdfViewModel
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ScanScreen(
    viewModel: PdfViewModel? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ocrEngine = remember(context) { ScanOcrEngine(context) }

    // Flow states
    var showCameraXScanner by remember { mutableStateOf(false) }
    var activeCaptureMode by remember { mutableStateOf(ScanCaptureMode.SINGLE_OR_BATCH) }
    val pendingRawFiles = remember { mutableStateListOf<File>() }
    var showCropFilterScreen by remember { mutableStateOf(false) }

    // OCR Extract Text dialog state
    var showExtractTextDialog by remember { mutableStateOf(false) }
    var showOcrLanguagePacksDialog by remember { mutableStateOf(false) }
    var ocrInitialUri by remember { mutableStateOf<Uri?>(null) }

    // Saved Scans in app storage (filesDir/Scans)
    var savedScans by remember { mutableStateOf<List<SavedScanRecord>>(emptyList()) }
    var scanToRename by remember { mutableStateOf<SavedScanRecord?>(null) }

    fun refreshSavedScans() {
        savedScans = ocrEngine.listSavedScans()
    }

    LaunchedEffect(Unit) {
        refreshSavedScans()
    }

    // Gallery multi-image picker for scanning
    val galleryImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val copied = withContext(Dispatchers.IO) {
                    val tempDir = File(context.cacheDir, "raw_scans").apply { mkdirs() }
                    uris.mapNotNull { uri ->
                        try {
                            val dest = File(tempDir, "import_${System.currentTimeMillis()}_${uri.hashCode()}.jpg")
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                FileOutputStream(dest).use { output ->
                                    input.copyTo(output)
                                }
                            }
                            if (dest.exists() && dest.length() > 0L) dest else null
                        } catch (_: Exception) {
                            null
                        }
                    }
                }
                if (copied.isNotEmpty()) {
                    pendingRawFiles.addAll(copied)
                    showCameraXScanner = false
                    showCropFilterScreen = true
                }
            }
        }
    }

    // Single image picker for Extract Text (OCR)
    val ocrImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            ocrInitialUri = uri
            showExtractTextDialog = true
        }
    }

    // Google Play services ML Kit Document Scanner launcher
    val gmsScannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            val pages = scanResult?.pages
            if (!pages.isNullOrEmpty()) {
                scope.launch {
                    val imported = withContext(Dispatchers.IO) {
                        val tempDir = File(context.cacheDir, "raw_scans").apply { mkdirs() }
                        pages.mapNotNull { page ->
                            try {
                                val out = File(tempDir, "gms_${System.currentTimeMillis()}_${page.imageUri.hashCode()}.jpg")
                                context.contentResolver.openInputStream(page.imageUri)?.use { input ->
                                    FileOutputStream(out).use { output -> input.copyTo(output) }
                                }
                                if (out.exists() && out.length() > 0L) out else null
                            } catch (_: Exception) {
                                null
                            }
                        }
                    }
                    if (imported.isNotEmpty()) {
                        pendingRawFiles.clear()
                        pendingRawFiles.addAll(imported)
                        showCropFilterScreen = true
                    }
                }
            }
        }
    }

    fun launchMlKitScannerOrFallback(mode: ScanCaptureMode) {
        activeCaptureMode = mode
        val activity = context as? Activity
        if (activity == null || mode != ScanCaptureMode.SINGLE_OR_BATCH) {
            showCameraXScanner = true
            return
        }
        try {
            val options = GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(true)
                .setResultFormats(
                    GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                    GmsDocumentScannerOptions.RESULT_FORMAT_PDF
                )
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .build()
            val scanner = GmsDocumentScanning.getClient(options)
            scanner.getStartScanIntent(activity)
                .addOnSuccessListener { intentSender ->
                    gmsScannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
                }
                .addOnFailureListener {
                    showCameraXScanner = true
                }
        } catch (_: Throwable) {
            showCameraXScanner = true
        }
    }

    // Active sub-screens
    if (showCameraXScanner) {
        CameraXScannerScreen(
            captureMode = activeCaptureMode,
            onModeChanged = { activeCaptureMode = it },
            capturedCount = pendingRawFiles.size,
            onPageCaptured = { rawFile, _ ->
                pendingRawFiles.add(rawFile)
                if (activeCaptureMode == ScanCaptureMode.ID_CARD && pendingRawFiles.size >= 2) {
                    showCameraXScanner = false
                    showCropFilterScreen = true
                }
            },
            onImportGalleryClick = {
                galleryImportLauncher.launch("image/*")
            },
            onReviewPagesClick = {
                showCameraXScanner = false
                if (pendingRawFiles.isNotEmpty()) {
                    showCropFilterScreen = true
                }
            },
            onClose = {
                showCameraXScanner = false
            }
        )
        return
    }

    if (showCropFilterScreen && pendingRawFiles.isNotEmpty()) {
        ScanCropFilterScreen(
            initialRawFiles = pendingRawFiles.toList(),
            captureMode = activeCaptureMode,
            onAddMoreFromCamera = {
                showCropFilterScreen = false
                showCameraXScanner = true
            },
            onExportFinished = { savedFile, isPdf ->
                pendingRawFiles.clear()
                showCropFilterScreen = false
                refreshSavedScans()
                if (isPdf && viewModel != null) {
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        savedFile
                    )
                    viewModel.openPdf(uri)
                }
            },
            onClose = {
                pendingRawFiles.clear()
                showCropFilterScreen = false
            }
        )
        return
    }

    // Main Scan Dashboard + Saved Scans list
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 840.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = androidx.compose.ui.res.stringResource(com.example.R.string.scan_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = androidx.compose.ui.res.stringResource(com.example.R.string.scan_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Primary Hero Scanner Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DocumentScanner,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .size(28.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "CameraX + ML Kit Document Scanner",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "Live edge overlay, auto-capture, flash, 4-corner perspective warp & 5 filters",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    pendingRawFiles.clear()
                                    activeCaptureMode = ScanCaptureMode.SINGLE_OR_BATCH
                                    showCameraXScanner = true
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("scan_start_camera_btn"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("CameraX Scan")
                            }

                            FilledTonalButton(
                                onClick = {
                                    pendingRawFiles.clear()
                                    galleryImportLauncher.launch("image/*")
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("scan_import_gallery_btn"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("From Gallery")
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                pendingRawFiles.clear()
                                launchMlKitScannerOrFallback(ScanCaptureMode.SINGLE_OR_BATCH)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("scan_gms_scanner_btn"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Google Play Services Scanner (Auto-Fallback)")
                        }
                    }
                }
            }

            // Specialized Capture & OCR Modes (ID Card, Book Mode, Extract Text)
            item {
                Text(
                    text = "Scan Modes & Multi-Language OCR",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ScanModeShortcutCard(
                        title = "ID Card (A4)",
                        subtitle = "Front & back on 1 page",
                        icon = Icons.Default.Badge,
                        testTag = "scan_mode_id_card_btn",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            pendingRawFiles.clear()
                            activeCaptureMode = ScanCaptureMode.ID_CARD
                            showCameraXScanner = true
                        }
                    )
                    ScanModeShortcutCard(
                        title = "Book Mode",
                        subtitle = "Split 2 facing pages",
                        icon = Icons.Default.MenuBook,
                        testTag = "scan_mode_book_btn",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            pendingRawFiles.clear()
                            activeCaptureMode = ScanCaptureMode.BOOK
                            showCameraXScanner = true
                        }
                    )
                    ScanModeShortcutCard(
                        title = "Extract Text",
                        subtitle = "Multi-language OCR",
                        icon = Icons.Default.TextFields,
                        testTag = "scan_mode_extract_text_btn",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            ocrImagePickerLauncher.launch("image/*")
                        }
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                OcrLanguageQuickSelectorBar(
                    languagePackManager = ocrEngine.languagePackManager,
                    onOpenManagePacksDialog = { showOcrLanguagePacksDialog = true }
                )
            }

            // Saved Scans Folder Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderSpecial,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Saved Scans (${savedScans.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "App Storage / Scans",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (savedScans.isEmpty()) {
                item {
                    OutlinedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("scan_empty_state_card"),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DocumentScanner,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = "No saved scans yet",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Scans you export as searchable PDFs will appear here.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(savedScans, key = { it.file.absolutePath }) { record ->
                    val isPdf = record.file.extension.equals("pdf", ignoreCase = true)
                    SavedScanItemCard(
                        record = record,
                        isPdf = isPdf,
                        onOpen = {
                            if (isPdf && viewModel != null) {
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    record.file
                                )
                                viewModel.openPdf(uri)
                            } else {
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    record.file
                                )
                                ocrInitialUri = uri
                                showExtractTextDialog = true
                            }
                        },
                        onRename = { scanToRename = record },
                        onShare = {
                            try {
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    record.file
                                )
                                val mime = when (record.file.extension.lowercase()) {
                                    "pdf" -> "application/pdf"
                                    "zip" -> "application/zip"
                                    else -> "image/jpeg"
                                }
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = mime
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Scan"))
                            } catch (e: Exception) {
                                Toast.makeText(context, "Unable to share scan: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onDelete = {
                            if (ocrEngine.deleteSavedScan(record.file)) {
                                refreshSavedScans()
                                Toast.makeText(context, "Deleted ${record.name}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }
    }

    if (showExtractTextDialog) {
        ExtractTextDialog(
            initialUri = ocrInitialUri,
            onDismiss = {
                showExtractTextDialog = false
                ocrInitialUri = null
            }
        )
    }

    if (showOcrLanguagePacksDialog) {
        OcrLanguagePacksDialog(
            languagePackManager = ocrEngine.languagePackManager,
            onDismiss = { showOcrLanguagePacksDialog = false }
        )
    }

    scanToRename?.let { target ->
        var newNameText by remember(target) {
            mutableStateOf(target.file.nameWithoutExtension)
        }
        AlertDialog(
            onDismissRequest = { scanToRename = null },
            title = { Text("Rename Scan", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newNameText,
                    onValueChange = { newNameText = it },
                    label = { Text("New Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val renamedFile = ocrEngine.renameSavedScan(target.file, newNameText)
                        scanToRename = null
                        if (renamedFile != null) {
                            refreshSavedScans()
                            Toast.makeText(context, "Renamed to ${renamedFile.name}", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { scanToRename = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ScanModeShortcutCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    testTag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    OutlinedCard(
        modifier = modifier
            .clickable(onClick = onClick)
            .testTag(testTag),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        MaterialTheme.colorScheme.secondaryContainer,
                        RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SavedScanItemCard(
    record: SavedScanRecord,
    isPdf: Boolean,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val dateFormatted = remember(record.lastModified) {
        SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.US).format(Date(record.lastModified))
    }
    val sizeFormatted = remember(record.sizeBytes) {
        Formatter.formatFileSize(context, record.sizeBytes)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        if (isPdf) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.tertiaryContainer,
                        RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPdf) Icons.Default.PictureAsPdf else Icons.Default.Image,
                    contentDescription = null,
                    tint = if (isPdf) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onTertiaryContainer
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$sizeFormatted • $dateFormatted",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onOpen) {
                Icon(Icons.Default.OpenInNew, contentDescription = "Open scan")
            }
            IconButton(onClick = onRename) {
                Icon(Icons.Default.Edit, contentDescription = "Rename scan")
            }
            IconButton(onClick = onShare) {
                Icon(Icons.Default.Share, contentDescription = "Share scan")
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete scan",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
