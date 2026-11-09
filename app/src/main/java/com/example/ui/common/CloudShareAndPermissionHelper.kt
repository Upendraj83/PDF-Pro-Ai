package com.example.ui.common

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

object CloudDrivePackages {
    const val GOOGLE_DRIVE = "com.google.android.apps.docs"
    const val ONEDRIVE = "com.microsoft.skydrive"
    const val DROPBOX = "com.dropbox.android"
}

/**
 * Ensures any file:// or internal File URI is converted to a content:// URI via FileProvider
 * so sharing to Google Drive, OneDrive, Dropbox, or email never throws FileUriExposedException.
 */
fun getShareablePdfUri(context: Context, uri: Uri, fileName: String): Uri {
    if (uri.scheme.equals("content", ignoreCase = true)) {
        return uri
    }
    val authority = "${context.packageName}.fileprovider"
    try {
        val path = uri.path
        if (!path.isNullOrBlank()) {
            val file = File(path)
            if (file.exists()) {
                return try {
                    FileProvider.getUriForFile(context, authority, file)
                } catch (_: Exception) {
                    val safeName = if (fileName.endsWith(".pdf", true)) fileName else "$fileName.pdf"
                    val tempShareDir = File(context.cacheDir, "shared_pdfs").apply { mkdirs() }
                    val copyFile = File(tempShareDir, safeName)
                    file.inputStream().use { input ->
                        FileOutputStream(copyFile).use { output -> input.copyTo(output) }
                    }
                    FileProvider.getUriForFile(context, authority, copyFile)
                }
            }
        }
    } catch (_: Exception) {
    }
    return uri
}

/**
 * Shares a PDF to a specific Cloud Drive package (e.g. Google Drive, OneDrive, Dropbox)
 * or opens the system share chooser if targetPackage is null or not installed on the device.
 */
fun sharePdfToCloudOrApps(
    context: Context,
    uri: Uri,
    fileName: String,
    targetPackage: String? = null
) {
    try {
        val shareUri = getShareablePdfUri(context, uri, fileName)
        val baseIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, shareUri)
            putExtra(Intent.EXTRA_SUBJECT, fileName)
            putExtra(Intent.EXTRA_TITLE, fileName)
            clipData = ClipData.newRawUri(fileName, shareUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        if (targetPackage != null) {
            val targetedIntent = Intent(baseIntent).apply {
                setPackage(targetPackage)
            }
            val pm = context.packageManager
            if (targetedIntent.resolveActivity(pm) != null) {
                context.startActivity(targetedIntent)
                return
            }
        }

        val chooser = Intent.createChooser(baseIntent, fileName).apply {
            if (context !is Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        context.startActivity(chooser)
    } catch (e: Exception) {
        Toast.makeText(context, "Unable to share file: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Copies a PDF URI to a destination URI chosen via Storage Access Framework (Google Drive, OneDrive, or internal storage).
 */
suspend fun copyPdfUriToDestination(
    context: Context,
    sourceUri: Uri,
    destinationUri: Uri
): Boolean = withContext(Dispatchers.IO) {
    try {
        val inputStream = if (sourceUri.scheme.equals("file", ignoreCase = true)) {
            sourceUri.path?.let { File(it).inputStream() }
        } else {
            context.contentResolver.openInputStream(sourceUri)
        }
        if (inputStream == null) return@withContext false
        inputStream.use { input ->
            context.contentResolver.openOutputStream(destinationUri)?.use { output ->
                input.copyTo(output)
            } ?: return@withContext false
        }
        true
    } catch (_: Exception) {
        false
    }
}

/**
 * Scans a SAF Document Tree URI (granted internal folder) for PDF documents and returns their URIs.
 */
suspend fun scanPdfUrisInTree(
    context: Context,
    treeUri: Uri,
    maxFiles: Int = 50
): List<Uri> = withContext(Dispatchers.IO) {
    val found = mutableListOf<Uri>()
    try {
        val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (cursor.moveToNext() && found.size < maxFiles) {
                val docId = if (idIdx != -1) cursor.getString(idIdx) else null
                val name = if (nameIdx != -1) cursor.getString(nameIdx) else ""
                val mime = if (mimeIdx != -1) cursor.getString(mimeIdx) else ""
                if (docId != null && (mime.equals("application/pdf", ignoreCase = true) || name.endsWith(".pdf", ignoreCase = true))) {
                    val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                    found.add(docUri)
                }
            }
        }
    } catch (_: Exception) {
    }
    found
}

/**
 * Reusable Microphone Voice Input button that launches the zero-permission system
 * Speech Recognition intent (English / Bengali) to populate search or AI chat fields.
 */
@Composable
fun VoiceInputMicButton(
    onSpeechResult: (String) -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    testTag: String = "voice_input_mic_btn"
) {
    val context = LocalContext.current
    val promptText = stringResource(R.string.perm_voice_prompt)

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spoken = matches?.firstOrNull()?.trim().orEmpty()
            if (spoken.isNotEmpty()) {
                onSpeechResult(spoken)
            }
        }
    }

    IconButton(
        onClick = {
            try {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                    putExtra(RecognizerIntent.EXTRA_PROMPT, promptText)
                }
                speechLauncher.launch(intent)
            } catch (_: Exception) {
                Toast.makeText(
                    context,
                    "Voice recognition is not available on this device",
                    Toast.LENGTH_SHORT
                ).show()
            }
        },
        modifier = modifier
            .minimumInteractiveComponentSize()
            .testTag(testTag)
    ) {
        Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = stringResource(R.string.perm_voice_search_cd),
            tint = tint
        )
    }
}

/**
 * Dialog for sharing or saving a PDF to Google Drive, Microsoft OneDrive, Dropbox,
 * SAF Cloud/Internal Folder Picker, or standard Android Share Sheet.
 */
@Composable
fun ShareToCloudDriveDialog(
    pdfUri: Uri,
    fileName: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val safeDefaultName = remember(fileName) {
        if (fileName.endsWith(".pdf", ignoreCase = true)) fileName else "$fileName.pdf"
    }

    val saveToCloudOrDeviceSafLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destUri: Uri? ->
        if (destUri != null) {
            scope.launch {
                val ok = copyPdfUriToDestination(context, pdfUri, destUri)
                if (ok) {
                    Toast.makeText(context, "Saved $safeDefaultName to selected location", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Could not save file to selected location", Toast.LENGTH_SHORT).show()
                }
                onDismiss()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CloudUpload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.cloud_share_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.cloud_share_subtitle, fileName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                CloudDriveOptionItem(
                    icon = Icons.Default.CloudDone,
                    iconBg = Color(0xFFE8F5E9),
                    iconTint = Color(0xFF2E7D32),
                    title = stringResource(R.string.cloud_google_drive),
                    subtitle = stringResource(R.string.cloud_google_drive_desc),
                    testTag = "share_option_google_drive",
                    onClick = {
                        sharePdfToCloudOrApps(
                            context = context,
                            uri = pdfUri,
                            fileName = safeDefaultName,
                            targetPackage = CloudDrivePackages.GOOGLE_DRIVE
                        )
                        onDismiss()
                    }
                )

                CloudDriveOptionItem(
                    icon = Icons.Default.SaveAlt,
                    iconBg = Color(0xFFE3F2FD),
                    iconTint = Color(0xFF1565C0),
                    title = stringResource(R.string.cloud_saf_save),
                    subtitle = stringResource(R.string.cloud_saf_save_desc),
                    testTag = "share_option_saf_cloud_save",
                    onClick = {
                        saveToCloudOrDeviceSafLauncher.launch(safeDefaultName)
                    }
                )

                CloudDriveOptionItem(
                    icon = Icons.Default.CloudQueue,
                    iconBg = Color(0xFFE1F5FE),
                    iconTint = Color(0xFF0277BD),
                    title = stringResource(R.string.cloud_onedrive),
                    subtitle = stringResource(R.string.cloud_onedrive_desc),
                    testTag = "share_option_onedrive",
                    onClick = {
                        sharePdfToCloudOrApps(
                            context = context,
                            uri = pdfUri,
                            fileName = safeDefaultName,
                            targetPackage = CloudDrivePackages.ONEDRIVE
                        )
                        onDismiss()
                    }
                )

                CloudDriveOptionItem(
                    icon = Icons.Default.CloudUpload,
                    iconBg = Color(0xFFEDE7F6),
                    iconTint = Color(0xFF4527A0),
                    title = stringResource(R.string.cloud_dropbox),
                    subtitle = stringResource(R.string.cloud_dropbox_desc),
                    testTag = "share_option_dropbox",
                    onClick = {
                        sharePdfToCloudOrApps(
                            context = context,
                            uri = pdfUri,
                            fileName = safeDefaultName,
                            targetPackage = CloudDrivePackages.DROPBOX
                        )
                        onDismiss()
                    }
                )

                CloudDriveOptionItem(
                    icon = Icons.Default.Share,
                    iconBg = Color(0xFFFFF3E0),
                    iconTint = Color(0xFFE65100),
                    title = stringResource(R.string.cloud_other_apps),
                    subtitle = stringResource(R.string.cloud_other_apps_desc),
                    testTag = "share_option_other_apps",
                    onClick = {
                        sharePdfToCloudOrApps(
                            context = context,
                            uri = pdfUri,
                            fileName = safeDefaultName,
                            targetPackage = null
                        )
                        onDismiss()
                    }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.action_close))
            }
        }
    )
}

@Composable
private fun CloudDriveOptionItem(
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Interactive Permissions & Cloud Drives management card for Profile/Settings and Files screens.
 * Handles:
 * 1. Device Internal Documents & Persistent Folder Access (SAF OpenDocumentTree + legacy READ_EXTERNAL_STORAGE on API <= 32)
 * 2. Media & Photos Picker Access (PickMultipleVisualMedia to convert images to PDF)
 * 3. Microphone Permission (RECORD_AUDIO)
 * 4. Opening / Sharing with Google Drive & Cloud Drives
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PermissionsAndCloudDrivesCard(
    onOpenPdfUri: (Uri) -> Unit,
    onImportMultiplePdfUris: (List<Uri>) -> Unit,
    onConvertImagesToPdf: (List<Uri>) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var grantedFoldersCount by remember {
        mutableStateOf(
            try {
                context.contentResolver.persistedUriPermissions.count { it.isReadPermission }
            } catch (_: Exception) {
                0
            }
        )
    }

    val folderTreeLauncher = rememberLauncherForActivityResult(
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
            grantedFoldersCount = try {
                context.contentResolver.persistedUriPermissions.count { it.isReadPermission }
            } catch (_: Exception) {
                grantedFoldersCount + 1
            }
            scope.launch {
                val foundPdfs = scanPdfUrisInTree(context, treeUri)
                if (foundPdfs.isNotEmpty()) {
                    onImportMultiplePdfUris(foundPdfs)
                    Toast.makeText(
                        context,
                        "Imported ${foundPdfs.size} PDF(s) from folder",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        context,
                        "Folder access granted",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    val pickMediaPhotosLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(20)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            onConvertImagesToPdf(uris)
        }
    }

    val openCloudPdfLauncher = rememberLauncherForActivityResult(
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
            onOpenPdfUri(uri)
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("permissions_and_cloud_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Internal Documents & Folder Permission
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
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.perm_internal_docs_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.perm_internal_docs_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        folderTreeLauncher.launch(null)
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("btn_grant_internal_folder")
                ) {
                    Text(stringResource(R.string.perm_grant_folder_btn))
                }

                OutlinedButton(
                    onClick = { openCloudPdfLauncher.launch(arrayOf("application/pdf")) },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("btn_open_cloud_drive_pdf")
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDone,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.files_loc_cloud_import))
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))

            // 2. Media & Photos Access (Zero-permission Photo Picker + Media to PDF)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFFBE9E7)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = Color(0xFFD84315),
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.perm_pick_media_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.perm_pick_media_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = {
                        pickMediaPhotosLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("btn_pick_media_photos")
                ) {
                    Text(stringResource(R.string.perm_pick_media_btn))
                }
            }
        }
    }
}
