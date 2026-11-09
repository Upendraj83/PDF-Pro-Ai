package com.example.ui.common

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.R
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class GlobalErrorReport(
    val title: String,
    val summaryMessage: String,
    val fullTechnicalDetails: String,
    val timestampMillis: Long = System.currentTimeMillis()
)

object GlobalErrorManager {
    private const val PREFS_NAME = "pdf_global_error_prefs"
    private const val KEY_LAST_CRASH_TITLE = "last_crash_title"
    private const val KEY_LAST_CRASH_SUMMARY = "last_crash_summary"
    private const val KEY_LAST_CRASH_DETAILS = "last_crash_details"

    fun sanitizeFileNamesAndPaths(input: String): String {
        return input
            .replace(Regex("""content://[^\s"')\]]+"""), "content://[redacted-uri]")
            .replace(Regex("""file://[^\s"')\]]+"""), "file://[redacted-path]")
            .replace(Regex("""/(?:storage|sdcard|data|mnt)/[^\s"')\]]+"""), "/[redacted-path]")
            .replace(Regex("""\b[^\s/\\:"']+\.(?:pdf|PDF)\b"""), "[redacted.pdf]")
    }

    fun formatThrowableReport(
        throwable: Throwable,
        contextInfo: String = "Uncaught Runtime Exception"
    ): GlobalErrorReport {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val rawStackTrace = sw.toString().take(4000)
        val stackTrace = sanitizeFileNamesAndPaths(rawStackTrace)
        val rawMessage = throwable.localizedMessage ?: "No message"
        val safeMessage = sanitizeFileNamesAndPaths(rawMessage)
        val safeContext = sanitizeFileNamesAndPaths(contextInfo)
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

        val details = buildString {
            appendLine("=== PDF ALL IN ONE Pro+Ai Diagnostic Report ===")
            appendLine("Time: $timestamp")
            appendLine("App Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Android SDK: ${Build.VERSION.SDK_INT} (${Build.VERSION.RELEASE})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Context: $safeContext")
            appendLine("Error Type: ${throwable.javaClass.name}")
            appendLine("Message: $safeMessage")
            appendLine("\n--- Stack Trace ---")
            append(stackTrace)
        }

        return GlobalErrorReport(
            title = safeContext,
            summaryMessage = safeMessage.ifBlank { throwable.javaClass.simpleName },
            fullTechnicalDetails = details
        )
    }

    @Volatile
    private var isHandlerInstalled = false

    @Volatile
    private var errorCallback: ((GlobalErrorReport) -> Unit)? = null

    @Synchronized
    fun installGlobalExceptionHandler(context: Context, onErrorCaptured: (GlobalErrorReport) -> Unit) {
        val appContext = context.applicationContext
        errorCallback = onErrorCaptured
        if (isHandlerInstalled) return
        isHandlerInstalled = true
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val report = formatThrowableReport(throwable, "Thread: ${thread.name}")
                saveCrashReport(appContext, report)
                errorCallback?.invoke(report)
            } catch (_: Exception) {
            }
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    fun saveCrashReport(context: Context, report: GlobalErrorReport) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_LAST_CRASH_TITLE, report.title)
            .putString(KEY_LAST_CRASH_SUMMARY, report.summaryMessage)
            .putString(KEY_LAST_CRASH_DETAILS, report.fullTechnicalDetails)
            .apply()
    }

    fun consumePendingCrashReport(context: Context): GlobalErrorReport? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val details = prefs.getString(KEY_LAST_CRASH_DETAILS, null) ?: return null
        val title = prefs.getString(KEY_LAST_CRASH_TITLE, "Application Error") ?: "Application Error"
        val summary = prefs.getString(KEY_LAST_CRASH_SUMMARY, "") ?: ""
        prefs.edit().clear().apply()
        return GlobalErrorReport(
            title = title,
            summaryMessage = summary,
            fullTechnicalDetails = details
        )
    }

    fun copyErrorDetailsToClipboard(context: Context, details: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText("PDF Error Details", details)
        clipboard?.setPrimaryClip(clip)
    }
}

/**
 * Global Error Screen with a "Copy details" button and a "Dismiss & Continue" button.
 */
@Composable
fun GlobalErrorScreen(
    errorReport: GlobalErrorReport,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.activity.compose.BackHandler {
        onDismiss()
    }
    val context = LocalContext.current
    val copiedToastText = stringResource(R.string.error_copied_toast)

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("global_error_screen")
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.errorContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.BugReport,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(38.dp)
                    )
                }

                Text(
                    text = stringResource(R.string.error_screen_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Text(
                    text = stringResource(R.string.error_screen_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.error_technical_details_label),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = errorReport.fullTechnicalDetails,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.testTag("global_error_details_text")
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            GlobalErrorManager.copyErrorDetailsToClipboard(
                                context,
                                errorReport.fullTechnicalDetails
                            )
                            Toast.makeText(context, copiedToastText, Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("btn_copy_error_details")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.error_copy_details),
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("btn_dismiss_global_error")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.error_dismiss_continue),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
