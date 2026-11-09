package com.example.ui.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
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
import com.example.pdf.PasswordStrengthLevel
import com.example.pdf.PdfEncryptionAlgorithm
import com.example.pdf.PdfOptimizationEngine
import com.example.pdf.PdfSecurityStatus
import com.example.ui.PdfViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream

enum class PdfSecurityDialogMode {
    ENCRYPT,
    DECRYPT
}

@Composable
fun ProtectPdfDialog(
    initialUri: Uri?,
    initialFileName: String?,
    viewModel: PdfViewModel,
    onDismiss: () -> Unit,
    initialCachedFile: File? = null,
    initialMode: PdfSecurityDialogMode = PdfSecurityDialogMode.ENCRYPT
) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val coroutineScope = rememberCoroutineScope()
    val optimizationEngine = remember { PdfOptimizationEngine(context) }

    var dialogMode by remember { mutableStateOf(initialMode) }

    var selectedUri by remember { mutableStateOf(initialUri) }
    var selectedFileName by remember { mutableStateOf(initialFileName ?: "Select a PDF") }
    var activeCachedFile by remember { mutableStateOf(initialCachedFile) }
    var securityStatus by remember { mutableStateOf<PdfSecurityStatus?>(null) }

    // Encrypt state
    var existingPasswordForReencrypt by remember { mutableStateOf("") }
    var userPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var ownerPassword by remember { mutableStateOf("") }
    var showPasswords by remember { mutableStateOf(false) }
    var selectedAlgorithm by remember { mutableStateOf(PdfEncryptionAlgorithm.AES_256) }

    var canPrint by remember { mutableStateOf(true) }
    var canCopy by remember { mutableStateOf(true) }
    var canEdit by remember { mutableStateOf(false) }
    var canAnnotate by remember { mutableStateOf(false) }
    var canFillForms by remember { mutableStateOf(true) }
    var saveInBiometricVault by remember { mutableStateOf(true) }

    // Decrypt state
    var decryptPasswordInput by remember { mutableStateOf("") }
    var showDecryptPassword by remember { mutableStateOf(false) }
    var removePasswordPermanently by remember { mutableStateOf(true) }

    // Processing & result state
    var isProcessing by remember { mutableStateOf(false) }
    var progressFraction by remember { mutableFloatStateOf(0f) }
    var progressStatus by remember { mutableStateOf("") }
    var processedFile by remember { mutableStateOf<File?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val hasSavedBiometricForSelected = remember(selectedUri) {
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
            selectedFileName = queryToolFileName(context, uri) ?: "Document.pdf"
            activeCachedFile = null
            processedFile = null
            errorMessage = null
        }
    }

    val createDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destinationUri ->
        if (destinationUri != null && processedFile != null) {
            coroutineScope.launch {
                try {
                    context.contentResolver.openOutputStream(destinationUri)?.use { out ->
                        FileInputStream(processedFile!!).use { it.copyTo(out) }
                    }
                    val toastMsg = if (dialogMode == PdfSecurityDialogMode.ENCRYPT) {
                        "Encrypted PDF saved!"
                    } else {
                        "Decrypted PDF saved!"
                    }
                    Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val strengthInfo = remember(userPassword) {
        PdfOptimizationEngine.evaluatePasswordStrength(userPassword)
    }

    AlertDialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        modifier = Modifier.testTag("protect_pdf_dialog"),
        icon = {
            Icon(
                imageVector = if (dialogMode == PdfSecurityDialogMode.ENCRYPT) Icons.Default.Lock else Icons.Default.LockOpen,
                contentDescription = "PDF Password Security",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = if (dialogMode == PdfSecurityDialogMode.ENCRYPT) {
                    "Encrypt & Password Protect PDF"
                } else {
                    "Decrypt & Unlock PDF"
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Mode Switcher: Encrypt vs Decrypt
                if (processedFile == null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = dialogMode == PdfSecurityDialogMode.ENCRYPT,
                            onClick = {
                                dialogMode = PdfSecurityDialogMode.ENCRYPT
                                errorMessage = null
                            },
                            label = { Text("Encrypt (Lock)") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("tab_security_encrypt")
                        )
                        FilterChip(
                            selected = dialogMode == PdfSecurityDialogMode.DECRYPT,
                            onClick = {
                                dialogMode = PdfSecurityDialogMode.DECRYPT
                                errorMessage = null
                            },
                            label = { Text("Decrypt (Unlock)") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.LockOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("tab_security_decrypt")
                        )
                    }
                }

                // File Selector Card + Security Status Badge
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { if (!isProcessing) filePicker.launch(arrayOf("application/pdf")) }
                        .testTag("btn_select_protect_pdf"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
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
                                    text = if (selectedUri != null) "Tap to choose a different PDF" else "Choose PDF from device",
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
                                        modifier = Modifier.size(15.dp),
                                        tint = if (status.isEncrypted) {
                                            MaterialTheme.colorScheme.onTertiaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                        }
                                    )
                                    val pageInfo = if (status.pageCount > 0) " • ${status.pageCount} pages" else ""
                                    Text(
                                        text = "${status.algorithmSummary}$pageInfo",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = if (status.isEncrypted) {
                                            MaterialTheme.colorScheme.onTertiaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSecondaryContainer
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                if (processedFile == null) {
                    if (dialogMode == PdfSecurityDialogMode.ENCRYPT) {
                        if (securityStatus?.requiresOpenPassword == true && activeCachedFile == null) {
                            OutlinedTextField(
                                value = existingPasswordForReencrypt,
                                onValueChange = { existingPasswordForReencrypt = it },
                                label = { Text("Current Password (Required to update)") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_existing_password"),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                visualTransformation = if (showPasswords) VisualTransformation.None else PasswordVisualTransformation()
                            )
                        }

                        // Encryption Algorithm Selector
                        Text(
                            text = "Encryption Standard",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PdfEncryptionAlgorithm.entries.forEach { algo ->
                                FilterChip(
                                    selected = selectedAlgorithm == algo,
                                    onClick = { selectedAlgorithm = algo },
                                    label = { Text(algo.shortBadge) },
                                    modifier = Modifier.testTag("chip_algo_${algo.name}")
                                )
                            }
                        }
                        Text(
                            text = selectedAlgorithm.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Password input + Quick Generator + Copy
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Document Credentials",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(
                                    onClick = {
                                        val generated = PdfOptimizationEngine.generateStrongPassword(14)
                                        userPassword = generated
                                        confirmPassword = generated
                                        showPasswords = true
                                    },
                                    modifier = Modifier.testTag("btn_generate_strong_password")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AutoFixHigh,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Generate Strong", style = MaterialTheme.typography.labelMedium)
                                }
                                if (userPassword.isNotBlank()) {
                                    IconButton(
                                        onClick = {
                                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                            cm?.setPrimaryClip(ClipData.newPlainText("PDF Password", userPassword))
                                            Toast.makeText(context, "Password copied to clipboard", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier
                                            .size(36.dp)
                                            .testTag("btn_copy_generated_password")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy password",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = userPassword,
                            onValueChange = { userPassword = it },
                            label = { Text("Open Password (Required)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_user_password"),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            visualTransformation = if (showPasswords) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showPasswords = !showPasswords }) {
                                    Icon(
                                        imageVector = if (showPasswords) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle password visibility"
                                    )
                                }
                            }
                        )

                        // Password Strength Meter
                        if (userPassword.isNotEmpty()) {
                            val barColor = when (strengthInfo.level) {
                                PasswordStrengthLevel.EMPTY, PasswordStrengthLevel.WEAK -> MaterialTheme.colorScheme.error
                                PasswordStrengthLevel.FAIR -> Color(0xFFE65100)
                                PasswordStrengthLevel.GOOD -> Color(0xFF1976D2)
                                PasswordStrengthLevel.STRONG -> Color(0xFF2E7D32)
                            }
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Password Strength: ${strengthInfo.level.label}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = barColor
                                    )
                                    Text(
                                        text = "${userPassword.length} chars",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { strengthInfo.level.fraction },
                                    color = barColor,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                )
                                Text(
                                    text = strengthInfo.recommendation,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            label = { Text("Confirm Open Password") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_confirm_password"),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            visualTransformation = if (showPasswords) VisualTransformation.None else PasswordVisualTransformation()
                        )

                        OutlinedTextField(
                            value = ownerPassword,
                            onValueChange = { ownerPassword = it },
                            label = { Text("Permissions / Master Password (Optional)") },
                            placeholder = { Text("Defaults to open password") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_owner_password"),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            visualTransformation = if (showPasswords) VisualTransformation.None else PasswordVisualTransformation()
                        )

                        HorizontalDivider()

                        // Permissions Checkboxes
                        Text(
                            text = "Document Access Permissions",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )

                        PermissionCheckRow(
                            label = "Allow Printing",
                            description = "Permit recipients to print this document",
                            checked = canPrint,
                            onCheckedChange = { canPrint = it }
                        )

                        PermissionCheckRow(
                            label = "Allow Copying Text & Graphics",
                            description = "Permit copying text, tables, and images",
                            checked = canCopy,
                            onCheckedChange = { canCopy = it }
                        )

                        PermissionCheckRow(
                            label = "Allow Editing & Page Assembly",
                            description = "Permit modifying pages, rotating, or deleting pages",
                            checked = canEdit,
                            onCheckedChange = {
                                canEdit = it
                                if (it) canAnnotate = true
                            }
                        )

                        PermissionCheckRow(
                            label = "Allow Annotations & Comments",
                            description = "Permit highlighting, drawing, and adding notes",
                            checked = canAnnotate,
                            onCheckedChange = { canAnnotate = it }
                        )

                        PermissionCheckRow(
                            label = "Allow Form Field Filling",
                            description = "Permit filling interactive AcroForm fields and signatures",
                            checked = canFillForms,
                            onCheckedChange = { canFillForms = it }
                        )

                        HorizontalDivider()

                        PermissionCheckRow(
                            label = "Save in AES-256 Biometric Vault",
                            description = "Allow 1-tap Fingerprint / Face unlock for this document on your device",
                            checked = saveInBiometricVault,
                            onCheckedChange = { saveInBiometricVault = it }
                        )

                        // Prominent Warning
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Warning",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Warning: Standard PDF encryption has no backdoor. Keep your password in a safe place or enable the Biometric Vault above.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    } else {
                        // DECRYPT MODE UI
                        if (hasSavedBiometricForSelected && selectedUri != null) {
                            Button(
                                onClick = {
                                    val targetUri = selectedUri ?: return@Button
                                    val savedPass = viewModel.appLockManager.getSavedDocumentPasswordForBiometrics(targetUri.toString())
                                    if (!savedPass.isNullOrBlank()) {
                                        if (activity != null && viewModel.appLockManager.canAuthenticateWithBiometrics()) {
                                            viewModel.appLockManager.promptBiometrics(
                                                activity = activity,
                                                title = "Decrypt PDF with Biometric Vault",
                                                subtitle = "Confirm identity to decrypt $selectedFileName",
                                                negativeButtonText = "Enter Password",
                                                onSuccess = {
                                                    decryptPasswordInput = savedPass
                                                },
                                                onError = { err ->
                                                    errorMessage = err
                                                }
                                            )
                                        } else {
                                            decryptPasswordInput = savedPass
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("btn_decrypt_fill_from_biometric")
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
                            value = decryptPasswordInput,
                            onValueChange = { decryptPasswordInput = it },
                            label = { Text("Enter Document Password to Decrypt") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_decrypt_password"),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            visualTransformation = if (showDecryptPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showDecryptPassword = !showDecryptPassword }) {
                                    Icon(
                                        imageVector = if (showDecryptPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle password visibility"
                                    )
                                }
                            }
                        )

                        PermissionCheckRow(
                            label = "Remove password & restrictions permanently",
                            description = "Strip PDF encryption handlers so the saved document opens freely without a password",
                            checked = removePasswordPermanently,
                            onCheckedChange = { removePasswordPermanently = it }
                        )
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
                    // Success View (Encrypt or Decrypt)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
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
                                modifier = Modifier.size(42.dp)
                            )
                            Text(
                                text = if (dialogMode == PdfSecurityDialogMode.ENCRYPT) {
                                    "Document Encrypted (${selectedAlgorithm.shortBadge})"
                                } else {
                                    "Document Decrypted & Unlocked!"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = if (dialogMode == PdfSecurityDialogMode.ENCRYPT) {
                                    "Your PDF is now protected with ${selectedAlgorithm.displayLabel} and your configured access permissions."
                                } else {
                                    "Encryption and permission restrictions have been removed from your PDF."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )

                            OutlinedButton(
                                onClick = {
                                    val resultFile = processedFile ?: return@OutlinedButton
                                    onDismiss()
                                    viewModel.openPdf(Uri.fromFile(resultFile))
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("btn_open_processed_security_pdf")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Key,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    if (dialogMode == PdfSecurityDialogMode.ENCRYPT) {
                                        "Open Encrypted PDF in Viewer"
                                    } else {
                                        "Open Decrypted PDF in Viewer"
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (processedFile == null) {
                Button(
                    onClick = {
                        val uri = selectedUri
                        if (uri == null && activeCachedFile == null) {
                            errorMessage = "Please choose a PDF document."
                            return@Button
                        }

                        if (dialogMode == PdfSecurityDialogMode.ENCRYPT) {
                            if (userPassword.isBlank()) {
                                errorMessage = "Password cannot be empty."
                                return@Button
                            }
                            if (userPassword != confirmPassword) {
                                errorMessage = "Passwords do not match."
                                return@Button
                            }
                            errorMessage = null
                            isProcessing = true

                            coroutineScope.launch {
                                try {
                                    val file = optimizationEngine.encryptPdf(
                                        pdfFile = activeCachedFile,
                                        uri = uri,
                                        userPassword = userPassword,
                                        ownerPassword = ownerPassword,
                                        canPrint = canPrint,
                                        canCopy = canCopy,
                                        canEdit = canEdit,
                                        canAnnotate = canAnnotate,
                                        canFillForms = canFillForms,
                                        algorithm = selectedAlgorithm,
                                        currentPassword = existingPasswordForReencrypt,
                                        onProgress = { fraction, step ->
                                            progressFraction = fraction
                                            progressStatus = step
                                        }
                                    )
                                    if (saveInBiometricVault) {
                                        uri?.let { viewModel.saveBiometricPasswordForDocument(it.toString(), userPassword) }
                                        viewModel.saveBiometricPasswordForDocument(Uri.fromFile(file).toString(), userPassword)
                                    }
                                    processedFile = file
                                } catch (e: Exception) {
                                    errorMessage = "Encryption failed: ${e.localizedMessage}"
                                } finally {
                                    isProcessing = false
                                }
                            }
                        } else {
                            // DECRYPT MODE
                            if (decryptPasswordInput.isBlank()) {
                                errorMessage = "Please enter the document password to decrypt."
                                return@Button
                            }
                            errorMessage = null
                            isProcessing = true

                            coroutineScope.launch {
                                try {
                                    val file = optimizationEngine.unlockPdf(
                                        pdfFile = activeCachedFile,
                                        uri = uri,
                                        password = decryptPasswordInput,
                                        removePasswordPermanently = removePasswordPermanently,
                                        onProgress = { fraction, step ->
                                            progressFraction = fraction
                                            progressStatus = step
                                        }
                                    )
                                    if (removePasswordPermanently && uri != null) {
                                        viewModel.removeBiometricPasswordForDocument(uri.toString())
                                    }
                                    processedFile = file
                                } catch (e: Exception) {
                                    errorMessage = e.localizedMessage ?: "Failed to decrypt PDF."
                                } finally {
                                    isProcessing = false
                                }
                            }
                        }
                    },
                    enabled = !isProcessing && (selectedUri != null || activeCachedFile != null),
                    modifier = Modifier.testTag("btn_confirm_encrypt")
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (dialogMode == PdfSecurityDialogMode.ENCRYPT) "Encrypting..." else "Decrypting...")
                    } else {
                        Text(if (dialogMode == PdfSecurityDialogMode.ENCRYPT) "Encrypt Document" else "Decrypt PDF")
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val shareUri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                processedFile!!
                            )
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(Intent.EXTRA_STREAM, shareUri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            val title = if (dialogMode == PdfSecurityDialogMode.ENCRYPT) {
                                "Share Encrypted PDF"
                            } else {
                                "Share Decrypted PDF"
                            }
                            context.startActivity(Intent.createChooser(shareIntent, title))
                        }
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share")
                    }

                    Button(
                        onClick = {
                            val prefix = if (dialogMode == PdfSecurityDialogMode.ENCRYPT) "Protected_" else "Decrypted_"
                            createDocLauncher.launch("$prefix${selectedFileName.removeSuffix(".pdf")}.pdf")
                        },
                        modifier = Modifier.testTag("btn_save_security_copy")
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
                Text(if (processedFile != null) "Done" else "Cancel")
            }
        }
    )
}

@Composable
private fun PermissionCheckRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Spacer(modifier = Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
