package com.example.ui.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GTranslate
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai.AI_CONSENT_DIALOG_MESSAGE
import com.example.ai.AI_DISCLAIMER_TEXT
import com.example.ui.common.VoiceInputMicButton
import com.example.ai.AiChatMessage
import com.example.ai.AiExplainMode
import com.example.ai.AiExplainResult
import com.example.ai.AiOutputLanguage
import com.example.ai.AiSummaryResult
import com.example.ai.AiToolTab
import com.example.ai.AiTranslationResult
import com.example.pdf.BengaliFontOption
import com.example.ui.PdfViewModel
import java.io.File

/**
 * First-use Cloud AI Consent Dialog.
 * Displays the exact required text:
 * 'This sends the text of the pages you choose to a cloud AI service.'
 * Saves the choice in Settings and allows the user to turn AI off anytime.
 */
@Composable
fun AiConsentDialog(
    onAcceptConsent: () -> Unit,
    onDeclineAndTurnOff: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = "Cloud AI Consent",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = "Cloud AI Consent",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                ) {
                    Text(
                        text = AI_CONSENT_DIALOG_MESSAGE,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .padding(12.dp)
                            .testTag("ai_consent_exact_message")
                    )
                }
                Text(
                    text = "• Only the text of the pages or selection you choose is sent to Gemini via Firebase AI Logic.\n" +
                        "• Your choice is saved in Settings, and you can turn AI off at any time.\n" +
                        "• Content is never sent without your consent.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onAcceptConsent,
                modifier = Modifier.testTag("btn_ai_consent_accept")
            ) {
                Text("I Agree & Enable AI")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = onDeclineAndTurnOff,
                    modifier = Modifier.testTag("btn_ai_consent_turn_off")
                ) {
                    Text("Turn AI Off")
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("btn_ai_consent_cancel")
                ) {
                    Text("Cancel")
                }
            }
        }
    )
}

/**
 * Extra confirmation dialog required before sending text from a password-protected PDF to Cloud AI.
 */
@Composable
fun AiPasswordProtectedConfirmDialog(
    fileName: String,
    onConfirmSendProtected: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Password Protected PDF Warning",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = "Password-Protected Document",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "\"$fileName\" is password-protected.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Are you sure you want to send the extracted text of the pages you chose from this password-protected PDF to the cloud AI service?",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("ai_password_extra_confirm_text")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmSendProtected,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.testTag("btn_ai_password_confirm")
            ) {
                Text("Confirm & Send Text")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("btn_ai_password_cancel")
            ) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Mandatory AI Disclaimer Banner shown on every AI result.
 */
@Composable
fun AiDisclaimerBanner(
    disclaimer: String = AI_DISCLAIMER_TEXT,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
        modifier = modifier
            .fillMaxWidth()
            .testTag("ai_disclaimer_banner")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = disclaimer,
                style = MaterialTheme.typography.labelSmall,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

/**
 * Interactive full-height Bottom Sheet for Gemini AI Features:
 * 1. Summary (whole document or page range, English / Bengali, short summary + key points)
 * 2. Chat with PDF (chunked retrieval, page citations that jump to page on tap)
 * 3. Translate (selected text or page range, side-by-side original & translated, export as PDF or TXT)
 * 4. Explain / Rewrite Selection (select text from viewer or lines, explain or rewrite)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfAiAssistantSheet(
    viewModel: PdfViewModel,
    onJumpToPage: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val settings by viewModel.appSettings.collectAsStateWithLifecycle()
    val activeTab by viewModel.activeAiTab.collectAsStateWithLifecycle()
    val isAiRunning by viewModel.isAiRequestRunning.collectAsStateWithLifecycle()
    val aiStatusMessage by viewModel.aiStatusMessage.collectAsStateWithLifecycle()
    val aiErrorMessage by viewModel.aiErrorMessage.collectAsStateWithLifecycle()
    val summaryResult by viewModel.aiSummaryResult.collectAsStateWithLifecycle()
    val chatMessages by viewModel.aiChatMessages.collectAsStateWithLifecycle()
    val translationResult by viewModel.aiTranslationResult.collectAsStateWithLifecycle()
    val explainResult by viewModel.aiExplainResult.collectAsStateWithLifecycle()
    val selectedTextForAi by viewModel.selectedTextForAi.collectAsStateWithLifecycle()
    val currentPage by viewModel.currentPage.collectAsStateWithLifecycle()
    val totalPages = viewModel.currentViewingPageCount.coerceAtLeast(1)
    val pageExistingLinesMap by viewModel.pageExistingTextLines.collectAsStateWithLifecycle()

    val isAiConsentDialogOpen by viewModel.isAiConsentDialogOpen.collectAsStateWithLifecycle()
    val isAiPasswordConfirmDialogOpen by viewModel.isAiPasswordConfirmDialogOpen.collectAsStateWithLifecycle()

    // Ensure current page lines are inspected when user is on Explain or Translate tabs
    LaunchedEffect(currentPage, activeTab) {
        if (activeTab == AiToolTab.TRANSLATE || activeTab == AiToolTab.EXPLAIN) {
            viewModel.inspectPageForContentEdit(currentPage)
        }
    }

    // SAF launchers for exporting translated or summarized PDF or TXT
    var pendingTranslatedExportFile by remember { mutableStateOf<File?>(null) }
    val createTranslatedPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { targetUri: Uri? ->
        val file = pendingTranslatedExportFile
        if (targetUri != null && file != null) {
            viewModel.exportSavedFileToUri(file, targetUri)
            Toast.makeText(context, "Saved PDF!", Toast.LENGTH_SHORT).show()
        }
        pendingTranslatedExportFile = null
    }

    val createTranslatedTxtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { targetUri: Uri? ->
        val file = pendingTranslatedExportFile
        if (targetUri != null && file != null) {
            viewModel.exportSavedFileToUri(file, targetUri)
            Toast.makeText(context, "Saved text file!", Toast.LENGTH_SHORT).show()
        }
        pendingTranslatedExportFile = null
    }

    if (isAiConsentDialogOpen) {
        AiConsentDialog(
            onAcceptConsent = { viewModel.acceptAiConsent() },
            onDeclineAndTurnOff = { viewModel.declineAiConsentAndTurnOff() },
            onDismiss = { viewModel.dismissAiConsentDialog() }
        )
    }

    if (isAiPasswordConfirmDialogOpen) {
        AiPasswordProtectedConfirmDialog(
            fileName = viewModel.currentViewingName ?: "Document.pdf",
            onConfirmSendProtected = { viewModel.confirmPasswordProtectedAiUse() },
            onDismiss = { viewModel.dismissPasswordProtectedAiDialog() }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .padding(horizontal = 16.dp)
        ) {
            // Top Header: Title + Daily Usage Counter + Turn AI On/Off + Close
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Gemini PDF Assistant",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (settings.isAiLimitReached)
                                    MaterialTheme.colorScheme.errorContainer
                                else
                                    MaterialTheme.colorScheme.tertiaryContainer
                            ) {
                                Text(
                                    text = "${settings.aiRequestsUsedToday}/${settings.aiDailyLimit} today",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (settings.isAiLimitReached)
                                        MaterialTheme.colorScheme.onErrorContainer
                                    else
                                        MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                        .testTag("ai_sheet_usage_badge")
                                )
                            }
                        }
                        Text(
                            text = viewModel.currentViewingName ?: "Document",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = settings.aiEnabled,
                        onCheckedChange = { viewModel.settingsManager.setAiEnabled(it) },
                        modifier = Modifier.testTag("ai_sheet_enable_switch")
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close AI Sheet")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Warning banner if AI is disabled in Settings
            if (!settings.aiEnabled) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .testTag("ai_disabled_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Cloud AI is currently turned off in Settings. Turn the switch above on to use AI tools.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { viewModel.settingsManager.setAiEnabled(true) }) {
                            Text("Turn On")
                        }
                    }
                }
            }

            // Warning banner if daily usage limit is reached
            if (settings.isAiLimitReached) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .testTag("ai_limit_reached_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
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
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "Daily limit reached (${settings.aiRequestsUsedToday}/${settings.aiDailyLimit} requests today). Increase limit in Profile > Settings.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        TextButton(
                            onClick = { viewModel.settingsManager.setAiDailyLimit(settings.aiDailyLimit + 5) },
                            modifier = Modifier.testTag("btn_increase_limit_inline")
                        ) {
                            Text("+5 Limit")
                        }
                    }
                }
            }

            // Active Request Progress + Cancel Button
            if (isAiRunning) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .testTag("ai_running_progress_bar")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.5.dp
                            )
                            Text(
                                text = aiStatusMessage.ifBlank { "Generating response with Gemini..." },
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        OutlinedButton(
                            onClick = { viewModel.cancelActiveAiRequest() },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("btn_cancel_ai_request")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cancel,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Cancel", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            // Error Message Banner (Offline, Empty text, API failure, etc.)
            if (!aiErrorMessage.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .testTag("ai_error_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (aiErrorMessage!!.contains("offline", ignoreCase = true))
                                    Icons.Default.CloudOff
                                else
                                    Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = aiErrorMessage ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        IconButton(
                            onClick = { viewModel.clearAiErrorMessage() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss error",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Tab Row: Summary | Chat with PDF | Translate | Explain / Rewrite
            val tabs = AiToolTab.entries
            ScrollableTabRow(
                selectedTabIndex = tabs.indexOf(activeTab),
                edgePadding = 0.dp,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                tabs.forEach { tab ->
                    Tab(
                        selected = activeTab == tab,
                        onClick = { viewModel.setActiveAiTab(tab) },
                        text = {
                            Text(
                                text = tab.label,
                                fontWeight = if (activeTab == tab) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        icon = {
                            Icon(
                                imageVector = when (tab) {
                                    AiToolTab.SUMMARY -> Icons.Default.Summarize
                                    AiToolTab.CHAT -> Icons.Default.Chat
                                    AiToolTab.TRANSLATE -> Icons.Default.GTranslate
                                    AiToolTab.EXPLAIN -> Icons.Default.Psychology
                                },
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        modifier = Modifier.testTag("ai_tab_${tab.name}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Tab Content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (activeTab) {
                    AiToolTab.SUMMARY -> {
                        AiSummaryTabContent(
                            totalPages = totalPages,
                            currentPage = currentPage + 1,
                            isRunning = isAiRunning,
                            summaryResult = summaryResult,
                            onRunSummary = { wholeDoc, rangeStr, lang ->
                                viewModel.requestDocumentSummary(
                                    wholeDocument = wholeDoc,
                                    pageRangeInput = rangeStr,
                                    outputLanguage = lang
                                )
                            },
                            onExportSummaryPdf = { result, bengaliFont ->
                                viewModel.exportAiSummaryToPdf(result, bengaliFont) { savedPdf ->
                                    pendingTranslatedExportFile = savedPdf
                                    val base = (viewModel.currentViewingName ?: "Document").removeSuffix(".pdf")
                                    createTranslatedPdfLauncher.launch("${base}_summary.pdf")
                                }
                            },
                            onExportSummaryTxt = { result ->
                                viewModel.exportAiSummaryToTxt(result) { savedTxt ->
                                    pendingTranslatedExportFile = savedTxt
                                    val base = (viewModel.currentViewingName ?: "Document").removeSuffix(".pdf")
                                    createTranslatedTxtLauncher.launch("${base}_summary.txt")
                                }
                            },
                            onJumpToPage = { page1Based ->
                                onJumpToPage((page1Based - 1).coerceIn(0, (totalPages - 1).coerceAtLeast(0)))
                            },
                            context = context
                        )
                    }
                    AiToolTab.CHAT -> {
                        AiChatTabContent(
                            totalPages = totalPages,
                            isRunning = isAiRunning,
                            messages = chatMessages,
                            onSendQuestion = { question, lang ->
                                viewModel.sendAiChatQuestion(question, lang)
                            },
                            onJumpToPage = { page1Based ->
                                onJumpToPage((page1Based - 1).coerceIn(0, (totalPages - 1).coerceAtLeast(0)))
                            }
                        )
                    }
                    AiToolTab.TRANSLATE -> {
                        val pageLines = pageExistingLinesMap[currentPage].orEmpty()
                        AiTranslateTabContent(
                            totalPages = totalPages,
                            currentPage = currentPage + 1,
                            initialSelection = selectedTextForAi,
                            currentPageLines = pageLines.map { it.text },
                            isRunning = isAiRunning,
                            translationResult = translationResult,
                            onRunTranslate = { useSelection, selectionText, pageRangeStr, srcLang, targetLang ->
                                viewModel.requestAiTranslation(
                                    useSelectedText = useSelection,
                                    selectedText = selectionText,
                                    pageRangeInput = pageRangeStr,
                                    sourceLanguage = srcLang,
                                    targetLanguage = targetLang
                                )
                            },
                            onExportPdf = { result ->
                                viewModel.exportAiTranslationToPdf(result) { savedPdf ->
                                    pendingTranslatedExportFile = savedPdf
                                    val base = (viewModel.currentViewingName ?: "Document").removeSuffix(".pdf")
                                    createTranslatedPdfLauncher.launch("${base}_translated.pdf")
                                }
                            },
                            onExportTxt = { result ->
                                viewModel.exportAiTranslationToTxt(result) { savedTxt ->
                                    pendingTranslatedExportFile = savedTxt
                                    val base = (viewModel.currentViewingName ?: "Document").removeSuffix(".pdf")
                                    createTranslatedTxtLauncher.launch("${base}_translated.txt")
                                }
                            },
                            onOpenTranslatedPdfInViewer = { result ->
                                viewModel.exportAiTranslationToPdf(result) { savedPdf ->
                                    onDismiss()
                                    viewModel.openPdf(Uri.fromFile(savedPdf))
                                }
                            }
                        )
                    }
                    AiToolTab.EXPLAIN -> {
                        val pageLines = pageExistingLinesMap[currentPage].orEmpty()
                        AiExplainTabContent(
                            currentPage = currentPage + 1,
                            selectedText = selectedTextForAi,
                            currentPageLines = pageLines.map { it.text },
                            isRunning = isAiRunning,
                            explainResult = explainResult,
                            onUpdateSelectedText = { viewModel.setSelectedTextForAi(it) },
                            onRunExplain = { text, mode, lang ->
                                viewModel.requestExplainOrRewrite(
                                    selectedText = text,
                                    mode = mode,
                                    outputLanguage = lang,
                                    pageNumber = currentPage + 1
                                )
                            },
                            context = context
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AiSummaryTabContent(
    totalPages: Int,
    currentPage: Int,
    isRunning: Boolean,
    summaryResult: AiSummaryResult?,
    onRunSummary: (wholeDocument: Boolean, pageRange: String, language: AiOutputLanguage) -> Unit,
    onExportSummaryPdf: (AiSummaryResult, BengaliFontOption) -> Unit,
    onExportSummaryTxt: (AiSummaryResult) -> Unit,
    onJumpToPage: (Int) -> Unit,
    context: Context
) {
    var wholeDocument by remember { mutableStateOf(true) }
    var pageRangeInput by remember(totalPages) { mutableStateOf("1-${minOf(totalPages, 5)}") }
    var selectedLanguage by remember { mutableStateOf(AiOutputLanguage.ENGLISH) }
    var selectedBengaliFont by remember { mutableStateOf(BengaliFontOption.KALPURUSH) }

    val hasBengaliSummary = selectedLanguage == AiOutputLanguage.BENGALI ||
        summaryResult?.outputLanguage == AiOutputLanguage.BENGALI ||
        summaryResult?.shortSummary?.any { it.code in 0x0980..0x09FF } == true

    val summaryFontFamily = if (hasBengaliSummary) {
        selectedBengaliFont.toComposeFontFamily()
    } else {
        null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Scope & Output Language",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = wholeDocument,
                        onClick = { wholeDocument = true },
                        label = { Text("Whole Document (1-$totalPages)") },
                        modifier = Modifier.testTag("chip_summary_whole_doc")
                    )
                    FilterChip(
                        selected = !wholeDocument,
                        onClick = { wholeDocument = false },
                        label = { Text("Page Range") },
                        modifier = Modifier.testTag("chip_summary_page_range")
                    )
                    if (totalPages > 1) {
                        AssistChip(
                            onClick = {
                                wholeDocument = false
                                pageRangeInput = "$currentPage"
                            },
                            label = { Text("Current Page ($currentPage)") },
                            modifier = Modifier.testTag("chip_summary_current_page")
                        )
                    }
                }

                if (!wholeDocument) {
                    OutlinedTextField(
                        value = pageRangeInput,
                        onValueChange = { pageRangeInput = it },
                        label = { Text("Pages (e.g., 1-3, $currentPage)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_summary_page_range")
                    )
                }

                Text(
                    text = "Output Language",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AiOutputLanguage.entries.forEach { lang ->
                        FilterChip(
                            selected = selectedLanguage == lang,
                            onClick = { selectedLanguage = lang },
                            label = { Text(lang.displayName) },
                            modifier = Modifier.testTag("chip_summary_lang_${lang.name}")
                        )
                    }
                }

                if (hasBengaliSummary) {
                    Text(
                        text = "Bengali Font (${BengaliFontOption.entries.size} Popular Fonts)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        BengaliFontOption.entries.forEach { fontOpt ->
                            FilterChip(
                                selected = selectedBengaliFont == fontOpt,
                                onClick = { selectedBengaliFont = fontOpt },
                                label = {
                                    Text(
                                        text = fontOpt.displayName,
                                        fontFamily = fontOpt.toComposeFontFamily(),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                modifier = Modifier.testTag("chip_summary_bn_font_${fontOpt.name}")
                            )
                        }
                    }
                }

                Button(
                    onClick = { onRunSummary(wholeDocument, pageRangeInput, selectedLanguage) },
                    enabled = !isRunning,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_generate_summary")
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (summaryResult == null)
                            "Summarize (${selectedLanguage.displayName})"
                        else
                            "Regenerate Summary (${selectedLanguage.displayName})"
                    )
                }
            }
        }

        if (summaryResult != null) {
            AiDisclaimerBanner(disclaimer = summaryResult.disclaimer)

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("summary_result_card"),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Short Summary (${summaryResult.pageRangeDescription})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        Row {
                            IconButton(
                                onClick = {
                                    val fullText = buildString {
                                        appendLine("Summary (${summaryResult.pageRangeDescription}):")
                                        appendLine(summaryResult.shortSummary)
                                        appendLine()
                                        appendLine("Key Points:")
                                        summaryResult.keyPoints.forEach { appendLine("• $it") }
                                        appendLine()
                                        appendLine(summaryResult.disclaimer)
                                    }
                                    copyToClipboard(context, "AI Summary", fullText)
                                }
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy Summary", modifier = Modifier.size(18.dp))
                            }
                            IconButton(
                                onClick = {
                                    val fullText = buildString {
                                        appendLine("Summary (${summaryResult.pageRangeDescription}):")
                                        appendLine(summaryResult.shortSummary)
                                        appendLine()
                                        appendLine("Key Points:")
                                        summaryResult.keyPoints.forEach { appendLine("• $it") }
                                        appendLine()
                                        appendLine(summaryResult.disclaimer)
                                    }
                                    sharePlainText(context, "PDF AI Summary", fullText)
                                }
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Share Summary", modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    Text(
                        text = summaryResult.shortSummary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = summaryFontFamily
                    )

                    if (summaryResult.keyPoints.isNotEmpty()) {
                        HorizontalDivider()
                        Text(
                            text = "Key Points",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        summaryResult.keyPoints.forEach { bullet ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = "• ",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = bullet,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = summaryFontFamily
                                )
                            }
                        }
                    }

                    if (summaryResult.referencedPages.isNotEmpty()) {
                        HorizontalDivider()
                        Text(
                            text = "Referenced Pages (Tap to jump):",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            summaryResult.referencedPages.forEach { pageNum ->
                                AssistChip(
                                    onClick = { onJumpToPage(pageNum) },
                                    label = { Text("Page $pageNum") },
                                    modifier = Modifier.testTag("summary_jump_page_$pageNum")
                                )
                            }
                        }
                    }

                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onExportSummaryPdf(summaryResult, selectedBengaliFont) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_export_summary_pdf")
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save PDF", style = MaterialTheme.typography.labelMedium)
                        }
                        OutlinedButton(
                            onClick = { onExportSummaryTxt(summaryResult) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_export_summary_txt")
                        ) {
                            Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save TXT", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun AiChatTabContent(
    totalPages: Int,
    isRunning: Boolean,
    messages: List<AiChatMessage>,
    onSendQuestion: (question: String, language: AiOutputLanguage) -> Unit,
    onJumpToPage: (Int) -> Unit
) {
    var questionInput by remember { mutableStateOf("") }
    var selectedLanguage by remember { mutableStateOf(AiOutputLanguage.ENGLISH) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Language & info bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Smart chunk retrieval active ($totalPages pages)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(AiOutputLanguage.ENGLISH, AiOutputLanguage.BENGALI).forEach { lang ->
                    FilterChip(
                        selected = selectedLanguage == lang,
                        onClick = { selectedLanguage = lang },
                        label = { Text(lang.displayName, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }

        // Messages list
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Chat with this PDF",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Ask any question about the document. Only the most relevant text chunks are sent with each question, and answers include interactive [Page N] buttons that jump directly to the cited page.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    "What is the main topic of this document?",
                                    "List the key dates, numbers, or conclusions.",
                                    "এই নথির মূল বিষয়বস্তু কী?"
                                ).forEach { starter ->
                                    AssistChip(
                                        onClick = { questionInput = starter },
                                        label = { Text(starter, style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            items(messages, key = { it.id }) { msg ->
                val isUser = msg.isUser
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
                ) {
                    Surface(
                        shape = RoundedCornerShape(
                            topStart = 14.dp,
                            topEnd = 14.dp,
                            bottomStart = if (isUser) 14.dp else 4.dp,
                            bottomEnd = if (isUser) 4.dp else 14.dp
                        ),
                        color = if (isUser)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth(0.92f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = msg.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isUser)
                                    MaterialTheme.colorScheme.onPrimary
                                else
                                    MaterialTheme.colorScheme.onSurface
                            )

                            if (!isUser && msg.referencedPages.isNotEmpty()) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Text(
                                    text = "Tap a page reference to jump:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    msg.referencedPages.forEach { pageNum ->
                                        AssistChip(
                                            onClick = { onJumpToPage(pageNum) },
                                            label = {
                                                Text(
                                                    text = "Jump to Page $pageNum",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            },
                                            colors = AssistChipDefaults.assistChipColors(
                                                containerColor = MaterialTheme.colorScheme.primaryContainer
                                            ),
                                            modifier = Modifier.testTag("chat_jump_page_$pageNum")
                                        )
                                    }
                                }
                            }

                            if (!isUser) {
                                Text(
                                    text = msg.disclaimer,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontStyle = FontStyle.Italic,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // Question Input Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = questionInput,
                onValueChange = { questionInput = it },
                placeholder = { Text("Ask a question about this PDF...") },
                trailingIcon = {
                    VoiceInputMicButton(
                        onSpeechResult = { spoken ->
                            questionInput = if (questionInput.isBlank()) spoken else "$questionInput $spoken"
                        },
                        testTag = "ai_chat_voice_mic_btn"
                    )
                },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("input_ai_chat_question")
            )
            Button(
                onClick = {
                    val q = questionInput.trim()
                    if (q.isNotEmpty()) {
                        questionInput = ""
                        onSendQuestion(q, selectedLanguage)
                    }
                },
                enabled = !isRunning && questionInput.isNotBlank(),
                modifier = Modifier.testTag("btn_send_ai_chat")
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Ask AI")
            }
        }
    }
}

@Composable
private fun AiTranslateTabContent(
    totalPages: Int,
    currentPage: Int,
    initialSelection: String,
    currentPageLines: List<String>,
    isRunning: Boolean,
    translationResult: AiTranslationResult?,
    onRunTranslate: (useSelection: Boolean, selectionText: String, pageRange: String, sourceLang: String, targetLang: AiOutputLanguage) -> Unit,
    onExportPdf: (AiTranslationResult) -> Unit,
    onExportTxt: (AiTranslationResult) -> Unit,
    onOpenTranslatedPdfInViewer: (AiTranslationResult) -> Unit
) {
    var useSelectedText by remember { mutableStateOf(initialSelection.isNotBlank()) }
    var selectedTextInput by remember(initialSelection) { mutableStateOf(initialSelection) }
    var pageRangeInput by remember { mutableStateOf("$currentPage") }
    var sourceLanguage by remember { mutableStateOf("Auto-Detect / English / Bengali") }
    var targetLanguage by remember { mutableStateOf(AiOutputLanguage.BENGALI) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Translate Selection or Page Range",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !useSelectedText,
                        onClick = { useSelectedText = false },
                        label = { Text("Page Range") },
                        modifier = Modifier.testTag("chip_translate_page_range")
                    )
                    FilterChip(
                        selected = useSelectedText,
                        onClick = { useSelectedText = true },
                        label = { Text("Selected Text") },
                        modifier = Modifier.testTag("chip_translate_selected_text")
                    )
                }

                if (useSelectedText) {
                    if (currentPageLines.isNotEmpty()) {
                        Text(
                            text = "Tap lines from Page $currentPage to add to selection:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            currentPageLines.take(12).forEach { line ->
                                AssistChip(
                                    onClick = {
                                        selectedTextInput = if (selectedTextInput.isBlank()) line else "$selectedTextInput\n$line"
                                    },
                                    label = {
                                        Text(
                                            text = line.take(32),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = selectedTextInput,
                        onValueChange = { selectedTextInput = it },
                        label = { Text("Selected text to translate") },
                        minLines = 2,
                        maxLines = 5,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_translate_selection")
                    )
                } else {
                    OutlinedTextField(
                        value = pageRangeInput,
                        onValueChange = { pageRangeInput = it },
                        label = { Text("Page range (1-$totalPages, e.g., $currentPage or 1-3)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_translate_page_range")
                    )
                }

                Text(
                    text = "Target Language",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AiOutputLanguage.entries.forEach { lang ->
                        FilterChip(
                            selected = targetLanguage == lang,
                            onClick = { targetLanguage = lang },
                            label = { Text(lang.displayName) },
                            modifier = Modifier.testTag("chip_translate_lang_${lang.name}")
                        )
                    }
                }

                Button(
                    onClick = {
                        onRunTranslate(
                            useSelectedText,
                            selectedTextInput,
                            pageRangeInput,
                            sourceLanguage,
                            targetLanguage
                        )
                    },
                    enabled = !isRunning && (!useSelectedText || selectedTextInput.isNotBlank()),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_run_translate")
                ) {
                    Icon(Icons.Default.GTranslate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Translate to ${targetLanguage.displayName}")
                }
            }
        }

        if (translationResult != null) {
            AiDisclaimerBanner(disclaimer = translationResult.disclaimer)

            // Export Actions Row (Export as New PDF / Export as Text File / Open PDF)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { onExportPdf(translationResult) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_export_translation_pdf")
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export PDF", style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(
                    onClick = { onExportTxt(translationResult) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_export_translation_txt")
                ) {
                    Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Export .TXT", style = MaterialTheme.typography.labelMedium)
                }
                Button(
                    onClick = { onOpenTranslatedPdfInViewer(translationResult) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_open_translation_pdf")
                ) {
                    Text("Open PDF", style = MaterialTheme.typography.labelMedium)
                }
            }

            // Side-by-Side Original vs Translated View
            translationResult.pairs.forEach { pair ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("translation_side_by_side_card_${pair.label}"),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "${pair.label} — Side-by-Side Comparison",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Original Column
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Original",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = pair.originalText,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            // Translated Column
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = translationResult.targetLanguage.displayName,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = pair.translatedText,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun AiExplainTabContent(
    currentPage: Int,
    selectedText: String,
    currentPageLines: List<String>,
    isRunning: Boolean,
    explainResult: AiExplainResult?,
    onUpdateSelectedText: (String) -> Unit,
    onRunExplain: (text: String, mode: AiExplainMode, language: AiOutputLanguage) -> Unit,
    context: Context
) {
    var selectedMode by remember { mutableStateOf(AiExplainMode.EXPLAIN_SIMPLY) }
    var selectedLanguage by remember { mutableStateOf(AiOutputLanguage.ENGLISH) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Select Text from Page $currentPage to Explain or Rewrite",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                if (currentPageLines.isNotEmpty()) {
                    Text(
                        text = "Tap any detected text line on Page $currentPage to select it:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        currentPageLines.take(15).forEachIndexed { idx, line ->
                            AssistChip(
                                onClick = {
                                    val updated = if (selectedText.isBlank()) line else "$selectedText $line"
                                    onUpdateSelectedText(updated)
                                },
                                label = {
                                    Text(
                                        text = line.take(36),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                modifier = Modifier.testTag("chip_select_page_line_$idx")
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = selectedText,
                    onValueChange = onUpdateSelectedText,
                    label = { Text("Selected text from viewer") },
                    placeholder = { Text("Tap a text line above or enter/paste selected text...") },
                    minLines = 3,
                    maxLines = 6,
                    trailingIcon = {
                        if (selectedText.isNotEmpty()) {
                            IconButton(onClick = { onUpdateSelectedText("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear selection")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_explain_selected_text")
                )

                Text(
                    text = "Action",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AiExplainMode.entries.forEach { mode ->
                        FilterChip(
                            selected = selectedMode == mode,
                            onClick = { selectedMode = mode },
                            label = { Text(mode.label) },
                            modifier = Modifier.testTag("chip_explain_mode_${mode.name}")
                        )
                    }
                }

                Text(
                    text = "Output Language",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(AiOutputLanguage.ENGLISH, AiOutputLanguage.BENGALI).forEach { lang ->
                        FilterChip(
                            selected = selectedLanguage == lang,
                            onClick = { selectedLanguage = lang },
                            label = { Text(lang.displayName) },
                            modifier = Modifier.testTag("chip_explain_lang_${lang.name}")
                        )
                    }
                }

                Button(
                    onClick = { onRunExplain(selectedText, selectedMode, selectedLanguage) },
                    enabled = !isRunning && selectedText.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_run_explain")
                ) {
                    Icon(Icons.Default.Psychology, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("${selectedMode.label} (${selectedLanguage.displayName})")
                }
            }
        }

        if (explainResult != null) {
            AiDisclaimerBanner(disclaimer = explainResult.disclaimer)

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("explain_result_card"),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${explainResult.mode.label} • ${explainResult.outputLanguage.displayName}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row {
                            IconButton(
                                onClick = {
                                    copyToClipboard(
                                        context,
                                        explainResult.mode.label,
                                        "${explainResult.responseText}\n\n${explainResult.disclaimer}"
                                    )
                                }
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy result", modifier = Modifier.size(18.dp))
                            }
                            IconButton(
                                onClick = {
                                    sharePlainText(
                                        context,
                                        explainResult.mode.label,
                                        "${explainResult.responseText}\n\n${explainResult.disclaimer}"
                                    )
                                }
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Share result", modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    Text(
                        text = explainResult.responseText,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
    } catch (_: Exception) {
    }
}

private fun sharePlainText(context: Context, title: String, text: String) {
    try {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, title))
    } catch (_: Exception) {
    }
}
