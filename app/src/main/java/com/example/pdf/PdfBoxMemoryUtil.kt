package com.example.pdf

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.File
import java.io.InputStream

/**
 * Centralized memory-safe loader for PDFBox [PDDocument] instances.
 *
 * By default, `PDDocument.load(file)` or `PDDocument.load(stream)` uses
 * `MemoryUsageSetting.setupMainMemoryOnly()`, which allocates all PDF stream buffers
 * (`ScratchFileBuffer` 4KB pages) on the Android JVM heap and causes `OutOfMemoryError`
 * on large or image-heavy PDFs.
 *
 * This utility configures PDFBox to spill scratch buffers to temporary files in `cacheDir`
 * (capping in-memory scratch usage to 4 MB) and provides `OutOfMemoryError` fallback to
 * pure temp-file mode.
 */
object PdfBoxMemoryUtil {

    private const val MAX_MAIN_MEMORY_BYTES = 2L * 1024L * 1024L // 2 MB max in RAM, rest on disk

    fun ensureInitialized(context: Context) {
        try {
            if (!PDFBoxResourceLoader.isReady()) {
                PDFBoxResourceLoader.init(context.applicationContext)
            }
        } catch (_: Throwable) {
        }
    }

    fun getScratchDir(context: Context): File {
        return File(context.cacheDir, "pdfbox_scratch").apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    /**
     * Cleans up PDFBox temporary scratch files and stale cached PDFs to keep device storage light.
     */
    fun cleanupScratchFiles(context: Context, activeFile: File? = null) {
        try {
            val scratchDir = File(context.cacheDir, "pdfbox_scratch")
            scratchDir.listFiles()?.forEach { file ->
                runCatching { file.delete() }
            }
            val pdfCacheDir = File(context.cacheDir, "pdf_cache")
            val cutoffMs = System.currentTimeMillis() - 30L * 60L * 1000L
            pdfCacheDir.listFiles()?.forEach { file ->
                if (file != activeFile && file.lastModified() < cutoffMs) {
                    runCatching { file.delete() }
                }
            }
        } catch (_: Throwable) {
        }
    }

    fun createMemorySetting(context: Context, tempFileOnly: Boolean = false): MemoryUsageSetting {
        val scratchDir = getScratchDir(context)
        return if (tempFileOnly) {
            MemoryUsageSetting.setupTempFileOnly().setTempDir(scratchDir)
        } else {
            MemoryUsageSetting.setupMixed(MAX_MAIN_MEMORY_BYTES).setTempDir(scratchDir)
        }
    }

    fun loadDocument(
        context: Context,
        pdfFile: File?,
        uri: Uri?,
        password: String? = null
    ): PDDocument? {
        ensureInitialized(context)
        return try {
            loadInternal(context, pdfFile, uri, password, tempFileOnly = false)
        } catch (oom: OutOfMemoryError) {
            System.gc()
            try {
                loadInternal(context, pdfFile, uri, password, tempFileOnly = true)
            } catch (_: Throwable) {
                System.gc()
                null
            }
        }
    }

    fun loadFromFile(
        context: Context,
        file: File,
        password: String? = null
    ): PDDocument {
        ensureInitialized(context)
        return try {
            if (password != null) {
                PDDocument.load(file, password, createMemorySetting(context, tempFileOnly = false))
            } else {
                PDDocument.load(file, createMemorySetting(context, tempFileOnly = false))
            }
        } catch (oom: OutOfMemoryError) {
            System.gc()
            if (password != null) {
                PDDocument.load(file, password, createMemorySetting(context, tempFileOnly = true))
            } else {
                PDDocument.load(file, createMemorySetting(context, tempFileOnly = true))
            }
        }
    }

    fun loadFromStream(
        context: Context,
        stream: InputStream,
        password: String? = null
    ): PDDocument {
        ensureInitialized(context)
        // Always prefer temp-file scratch when loading from an unbuffered InputStream
        // because PDFBox must copy the entire stream into ScratchFile before parsing.
        return if (password != null) {
            PDDocument.load(stream, password, createMemorySetting(context, tempFileOnly = true))
        } else {
            PDDocument.load(stream, createMemorySetting(context, tempFileOnly = true))
        }
    }

    private fun loadInternal(
        context: Context,
        pdfFile: File?,
        uri: Uri?,
        password: String?,
        tempFileOnly: Boolean
    ): PDDocument? {
        if (pdfFile != null && pdfFile.exists()) {
            val memSetting = createMemorySetting(context, tempFileOnly = tempFileOnly)
            return if (password != null) {
                PDDocument.load(pdfFile, password, memSetting)
            } else {
                PDDocument.load(pdfFile, memSetting)
            }
        }
        if (uri != null) {
            if (uri.scheme == "file" && !uri.path.isNullOrBlank()) {
                val directFile = File(uri.path!!)
                if (directFile.exists()) {
                    val memSetting = createMemorySetting(context, tempFileOnly = tempFileOnly)
                    return if (password != null) {
                        PDDocument.load(directFile, password, memSetting)
                    } else {
                        PDDocument.load(directFile, memSetting)
                    }
                }
            }
            val stream: InputStream? = runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
            if (stream != null) {
                // InputStream loads always use tempFileOnly = true so the raw PDF stream is buffered on disk, not heap
                val memSetting = createMemorySetting(context, tempFileOnly = true)
                return stream.use {
                    if (password != null) {
                        PDDocument.load(it, password, memSetting)
                    } else {
                        PDDocument.load(it, memSetting)
                    }
                }
            }
        }
        return null
    }
}
