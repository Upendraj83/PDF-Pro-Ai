package com.example.ui.tools

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import com.example.pdf.PdfOptimizationEngine
import com.example.pdf.PdfSecurityStatus
import com.example.ui.PdfViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream

@Composable
fun UnlockPdfDialog(
    initialUri: Uri?,
    initialFileName: String?,
    viewModel: PdfViewModel,
    onDismiss: () -> Unit,
    initialCachedFile: File? = null
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val coroutineScope = rememberCoroutineScope()
    val optimizationEngine = remember { PdfOptimizationEngine(context) }

    var selectedUri by remember { mutableStateOf(initialUri) }
    var selectedFileName by remember { mutableStateOf(initialFileName ?: "Select protected PDF") }
    var activeCachedFile by remember { mutableStateOf(initialCachedFile) }
    var securityStatus by remember { mutableStateOf<PdfSecurityStatus?>(null) }

    var passwordInput by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var removePasswordPermanently by remember { mutableStateOf(true) }

    var isProcessing by remember { mutableStateOf(false) }
    var progressFraction by remember { mutableFloatStateOf(0f) }
    var progressStatus by remember { mutableStateOf("") }
    var unlockedFile by remember { mutableStateOf<File?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val hasSavedBiometric = remember(selectedUri) {
        selectedUri?.let { viewModel.hasSavedBiometricPassword(it.toString()) } ?: false
    }

    LaunchedEffect(selectedUri, activeCachedFile) {
        if (selectedUri != null || activeCachedFile != null) {
            securityStatus = optimizationEngine.inspectSecurityStatus(
                pdfFile = activeCachedFile,
                uri = selectedUri
            )
        } else {
            securityStatus = null
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedUri = uri
            selectedFileName = queryToolFileName(context, uri) ?: "Protected_Document.pdf"
            activeCachedFile = null
            unlockedFile = null
            errorMessage = null
        }
    }

    val createDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destinationUri ->
        if (destinationUri != null && unlockedFile != null) {
            coroutineScope.launch {
                try {
                    context.contentResolver.openOutputStream(destinationUri)?.use { out ->
                        FileInputStream(unlockedFile!!).use { it.copyTo(out) }
                    }
                    Toast.makeText(context, "Clean unencrypted PDF saved!", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        modifier = Modifier.testTag("unlock_pdf_dialog"),
        icon = {
            Icon(
                imageVector = Icons.Default.LockOpen,
                contentDescription = "Unlock & Decrypt PDF",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = "Decrypt & Unlock PDF",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // File Selector Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { if (!isProcessing) filePicker.launch(arrayOf("application/pdf")) }
                        .testTag("btn_select_unlock_pdf"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = selectedFileName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (selectedUri != null) "Tap to change file" else "Choose locked PDF from device",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        securityStatus?.let { status ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (status.isEncrypted) {
                                    MaterialTheme.colorScheme.tertiaryContainer
                                } else {
                                    MaterialTheme.colorScheme.secondaryContainer
                                }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (status.isEncrypted) Icons.Default.Lock else Icons.Default.Security,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = status.algorithmSummary,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }

                if (unlockedFile == null) {
                    if (hasSavedBiometric && selectedUri != null) {
                        Button(
                            onClick = {
                                val targetUri = selectedUri ?: return@Button
                                val savedPass = viewModel.appLockManager.getSavedDocumentPasswordForBiometrics(targetUri.toString())
                                if (!savedPass.isNullOrBlank()) {
                                    if (activity != null && viewModel.appLockManager.canAuthenticateWithBiometrics()) {
                                        viewModel.appLockManager.promptBiometrics(
                                            activity = activity,
                                            title = "Decrypt PDF with Biometrics",
                                            subtitle = "Verify identity to unlock $selectedFileName",
                                            negativeButtonText = "Enter Password",
                                            onSuccess = {
                                                passwordInput = savedPass
                                            },
                                            onError = { err ->
                                                errorMessage = err
                                            }
                                        )
                                    } else {
                                        passwordInput = savedPass
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("btn_unlock_with_biometrics")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Use Saved Biometric Vault Password")
                        }
                    }

                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text("Document Password") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_unlock_password"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
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
                            .clickable { removePasswordPermanently = !removePasswordPermanently }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = removePasswordPermanently,
                            onCheckedChange = { removePasswordPermanently = it }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Remove password & restrictions permanently",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Create an unencrypted copy that opens without credentials",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (isProcessing) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            LinearProgressIndicator(
                                progress = { progressFraction },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                text = progressStatus,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (errorMessage != null) {
                        Text(
                            text = errorMessage ?: "",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp)
                            )
                            Text(
                                text = "Document Successfully Decrypted!",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (removePasswordPermanently)
                                    "Encryption has been permanently removed. You can now open, save, or share the clean document."
                                else
                                    "Document unlocked successfully for viewing.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )

                            OutlinedButton(
                                onClick = {
                                    val cleanFile = unlockedFile ?: return@OutlinedButton
                                    onDismiss()
                                    viewModel.openPdf(Uri.fromFile(cleanFile))
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("btn_open_unlocked_pdf_in_viewer")
                            ) {
                                Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Open Decrypted PDF in Viewer")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (unlockedFile == null) {
                Button(
                    onClick = {
                        val uri = selectedUri
                        if (uri == null && activeCachedFile == null) {
                            errorMessage = "Please choose a PDF document."
                            return@Button
                        }
                        if (passwordInput.isBlank()) {
                            errorMessage = "Please enter the document password."
                            return@Button
                        }
                        errorMessage = null
                        isProcessing = true

                        coroutineScope.launch {
                            try {
                                val file = optimizationEngine.unlockPdf(
                                    pdfFile = activeCachedFile,
                                    uri = uri,
                                    password = passwordInput,
                                    removePasswordPermanently = removePasswordPermanently,
                                    onProgress = { fraction, step ->
                                        progressFraction = fraction
                                        progressStatus = step
                                    }
                                )
                                if (removePasswordPermanently && uri != null) {
                                    viewModel.removeBiometricPasswordForDocument(uri.toString())
                                }
                                unlockedFile = file
                            } catch (e: Exception) {
                                errorMessage = e.localizedMessage ?: "Failed to unlock document."
                            } finally {
                                isProcessing = false
                            }
                        }
                    },
                    enabled = !isProcessing && (selectedUri != null || activeCachedFile != null),
                    modifier = Modifier.testTag("btn_confirm_unlock")
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Decrypting...")
                    } else {
                        Text("Decrypt & Unlock")
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val shareUri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                unlockedFile!!
                            )
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(Intent.EXTRA_STREAM, shareUri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Unlocked PDF"))
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share")
                    }

                    Button(
                        onClick = {
                            createDocLauncher.launch("Unlocked_${selectedFileName.removeSuffix(".pdf")}.pdf")
                        }
                    ) {
                        Text("Save as Copy")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isProcessing
            ) {
                Text(if (unlockedFile != null) "Done" else "Cancel")
            }
        }
    )
}
