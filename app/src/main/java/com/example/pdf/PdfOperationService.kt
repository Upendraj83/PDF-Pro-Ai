package com.example.pdf

import android.content.Context

/**
 * Lightweight in-app operation progress coordinator for PDF tasks.
 * Operations run in foreground ViewModel coroutines with an in-app progress dialog,
 * avoiding background ForegroundService overhead and notification permissions.
 */
object PdfOperationService {

    var onCancelRequested: (() -> Unit)? = null

    @Suppress("UNUSED_PARAMETER")
    fun start(context: Context, title: String, message: String) {
        // Tracked via PdfViewModel.operationProgress
    }

    @Suppress("UNUSED_PARAMETER")
    fun updateProgress(context: Context, title: String, message: String, progressPercent: Int) {
        // Tracked via PdfViewModel.operationProgress
    }

    @Suppress("UNUSED_PARAMETER")
    fun stop(context: Context) {
        // Tracked via PdfViewModel.operationProgress
    }
}
