package com.example.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

sealed interface PdfLoadResult {
    data class Success(val pageCount: Int, val fileName: String, val fileSize: Long) : PdfLoadResult
    data object PasswordRequired : PdfLoadResult
    data class Error(val message: String) : PdfLoadResult
}

class PdfDocumentManager(private val context: Context) {
    private var fileDescriptor: ParcelFileDescriptor? = null
    private var pdfRenderer: PdfRenderer? = null
    private var tempCachedFile: File? = null
    private val renderMutex = Mutex()

    private val runtimeMemoryKb = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val normalCacheSizeKb = (runtimeMemoryKb / 10).coerceIn(12 * 1024, 36 * 1024)
    private val lowMemCacheSizeKb = (runtimeMemoryKb / 20).coerceIn(4 * 1024, 12 * 1024)

    var isLowMemoryMode: Boolean = com.example.data.SettingsManager.detectDeviceMemoryInfo(context).shouldAutoEnableLowMemoryMode
        private set

    val activePageCacheMaxKb: Int
        get() = if (isLowMemoryMode) lowMemCacheSizeKb else normalCacheSizeKb

    val activeRenderScaleMultiplier: Float
        get() = if (isLowMemoryMode) 0.65f else 1.0f

    private val pageBitmapCache = object : LruCache<Int, Bitmap>(
        if (isLowMemoryMode) lowMemCacheSizeKb else normalCacheSizeKb
    ) {
        override fun sizeOf(key: Int, value: Bitmap): Int {
            return (value.byteCount / 1024).coerceAtLeast(1)
        }
    }

    // Separate thumbnail cache for grid/drawer view (6MB normal, 2MB in low-memory mode)
    private val thumbnailCache = object : LruCache<Int, Bitmap>(
        if (isLowMemoryMode) 2 * 1024 else 6 * 1024
    ) {
        override fun sizeOf(key: Int, value: Bitmap): Int {
            return (value.byteCount / 1024).coerceAtLeast(1)
        }
    }

    fun setLowMemoryMode(enabled: Boolean) {
        if (isLowMemoryMode == enabled) return
        isLowMemoryMode = enabled
        val newPageMax = if (enabled) lowMemCacheSizeKb else normalCacheSizeKb
        val newThumbMax = if (enabled) 2 * 1024 else 6 * 1024
        pageBitmapCache.resize(newPageMax)
        thumbnailCache.resize(newThumbMax)
        if (enabled) {
            pageBitmapCache.evictAll()
            thumbnailCache.evictAll()
        }
    }

    fun trimMemory() {
        pageBitmapCache.trimToSize(pageBitmapCache.maxSize() / 2)
        thumbnailCache.trimToSize(thumbnailCache.maxSize() / 2)
        PdfBoxMemoryUtil.cleanupScratchFiles(context, tempCachedFile)
    }

    var totalPages: Int = 0
        private set
    var currentFileName: String = ""
        private set
    var currentFileSize: Long = 0L
        private set
    var currentUri: Uri? = null
        private set
    var isCurrentPdfPasswordProtected: Boolean = false
        private set
    val cachedFile: File?
        get() = tempCachedFile

    fun isUriPasswordProtected(uri: Uri): Boolean {
        if (currentUri == uri && isCurrentPdfPasswordProtected) return true
        return try {
            val pfd = if (uri.scheme == "file" && uri.path != null) {
                ParcelFileDescriptor.open(File(uri.path!!), ParcelFileDescriptor.MODE_READ_ONLY)
            } else {
                context.contentResolver.openFileDescriptor(uri, "r")
            }
            pfd?.use { descriptor ->
                try {
                    val r = PdfRenderer(descriptor)
                    r.close()
                    false
                } catch (sec: SecurityException) {
                    true
                } catch (_: Throwable) {
                    false
                }
            } ?: false
        } catch (sec: SecurityException) {
            true
        } catch (_: Throwable) {
            false
        }
    }

    suspend fun openPdf(uri: Uri): PdfLoadResult = withContext(Dispatchers.IO) {
        close()
        currentUri = uri
        isCurrentPdfPasswordProtected = false

        val (name, size) = queryFileInfo(uri)
        currentFileName = name
        currentFileSize = size

        try {
            var pfd: ParcelFileDescriptor? = null
            var isSeekable = false

            val cacheDir = File(context.cacheDir, "pdf_cache").apply { mkdirs() }

            try {
                pfd = if (uri.scheme == "file" && uri.path != null) {
                    ParcelFileDescriptor.open(File(uri.path!!), ParcelFileDescriptor.MODE_READ_ONLY)
                } else {
                    context.contentResolver.openFileDescriptor(uri, "r")
                }
                if (pfd != null) {
                    // Try seeking or check if seekable
                    try {
                        val renderer = PdfRenderer(pfd)
                        pdfRenderer = renderer
                        fileDescriptor = pfd
                        totalPages = renderer.pageCount
                        if (uri.scheme == "file" && uri.path != null) {
                            val srcFile = File(uri.path!!)
                            if (srcFile.parentFile == cacheDir) {
                                tempCachedFile = srcFile
                            } else {
                                val cachedCopy = File(cacheDir, "view_${System.currentTimeMillis()}.pdf")
                                srcFile.copyTo(cachedCopy, overwrite = true)
                                tempCachedFile = cachedCopy
                            }
                        } else {
                            // Also cache content:// streams locally so PDFBox extraction/summarization/OCR
                            // never contend on or lose temporary ContentResolver stream permissions.
                            try {
                                val cachedCopy = File(cacheDir, "view_${System.currentTimeMillis()}.pdf")
                                context.contentResolver.openInputStream(uri)?.use { input ->
                                    FileOutputStream(cachedCopy).use { output ->
                                        val buffer = ByteArray(32 * 1024)
                                        var bytesRead: Int
                                        while (input.read(buffer).also { bytesRead = it } != -1) {
                                            output.write(buffer, 0, bytesRead)
                                        }
                                        output.flush()
                                    }
                                }
                                if (cachedCopy.exists() && cachedCopy.length() > 0L) {
                                    tempCachedFile = cachedCopy
                                }
                            } catch (_: Throwable) {
                            }
                        }
                        isSeekable = true
                    } catch (secEx: SecurityException) {
                        pfd.close()
                        isCurrentPdfPasswordProtected = true
                        return@withContext PdfLoadResult.PasswordRequired
                    } catch (e: Throwable) {
                        // Might not be seekable directly from content provider or stubbed in JVM tests
                        try { pfd.close() } catch (_: Throwable) {}
                        pfd = null
                    }
                }
            } catch (sec: SecurityException) {
                isCurrentPdfPasswordProtected = true
                return@withContext PdfLoadResult.PasswordRequired
            } catch (e: Throwable) {
                pfd = null
            }

            if (!isSeekable) {
                // Safely stream copy to cache without holding entire file in RAM
                val tempFile = File(cacheDir, "view_${System.currentTimeMillis()}.pdf")
                tempCachedFile = tempFile

                val inputStream = if (uri.scheme == "file" && uri.path != null) {
                    File(uri.path!!).inputStream()
                } else {
                    context.contentResolver.openInputStream(uri)
                }
                inputStream?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(32 * 1024) // 32 KB chunk
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                        }
                        output.flush()
                    }
                } ?: return@withContext PdfLoadResult.Error("Unable to open PDF stream.")

                var newPfd: ParcelFileDescriptor? = null
                try {
                    newPfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
                    val renderer = PdfRenderer(newPfd)
                    pdfRenderer = renderer
                    fileDescriptor = newPfd
                    totalPages = renderer.pageCount
                } catch (sec: SecurityException) {
                    try { newPfd?.close() } catch (_: Throwable) {}
                    tempFile.delete()
                    isCurrentPdfPasswordProtected = true
                    return@withContext PdfLoadResult.PasswordRequired
                } catch (_: Throwable) {
                    fileDescriptor = newPfd
                }
            }

            if (totalPages <= 0) {
                val fallbackFile = tempCachedFile ?: (if (uri.scheme == "file" && uri.path != null) File(uri.path!!) else null)
                if (fallbackFile != null && fallbackFile.exists()) {
                    try {
                        if (!com.tom_roush.pdfbox.android.PDFBoxResourceLoader.isReady()) {
                            com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context.applicationContext)
                        }
                        PdfBoxMemoryUtil.loadFromFile(context, fallbackFile).use { doc ->
                            totalPages = doc.numberOfPages
                        }
                    } catch (_: Throwable) {
                    }
                }
            }

            PdfLoadResult.Success(
                pageCount = totalPages,
                fileName = currentFileName,
                fileSize = currentFileSize
            )
        } catch (e: Exception) {
            close()
            PdfLoadResult.Error(e.localizedMessage ?: "Failed to open PDF document.")
        }
    }

    suspend fun openPdfWithPassword(
        uri: Uri,
        password: String,
        removePasswordPermanently: Boolean = false
    ): PdfLoadResult = withContext(Dispatchers.IO) {
        close()
        currentUri = uri
        isCurrentPdfPasswordProtected = !removePasswordPermanently
        val (name, size) = queryFileInfo(uri)
        currentFileName = name
        currentFileSize = size

        val cacheDir = File(context.cacheDir, "pdf_cache").apply { mkdirs() }
        val tempFile = File(cacheDir, "unlocked_${System.currentTimeMillis()}.pdf")
        tempCachedFile = tempFile

        var pdDoc: com.tom_roush.pdfbox.pdmodel.PDDocument? = null
        try {
            if (!com.tom_roush.pdfbox.android.PDFBoxResourceLoader.isReady()) {
                com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context.applicationContext)
            }
            val stream = if (uri.scheme == "file" && uri.path != null) {
                File(uri.path!!).inputStream()
            } else {
                context.contentResolver.openInputStream(uri)
            } ?: return@withContext PdfLoadResult.Error("Unable to open PDF stream.")
            pdDoc = stream.use {
                PdfBoxMemoryUtil.loadFromStream(context, it, password)
            }
            val pdfBoxPageCount = pdDoc.numberOfPages

            // Always remove encryption on the private temporary cache file so Android's
            // PdfRenderer and downstream engines can render and inspect pages.
            pdDoc.setAllSecurityToBeRemoved(true)
            pdDoc.save(tempFile)
            pdDoc.close()
            pdDoc = null

            // If the user explicitly requested removing the password permanently and the URI is writable,
            // write the unlocked PDF back to the document URI.
            if (removePasswordPermanently) {
                try {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                        tempFile.inputStream().use { input ->
                            input.copyTo(out)
                        }
                    }
                } catch (_: Exception) {
                }
            }

            totalPages = try {
                val newPfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
                fileDescriptor = newPfd
                val renderer = PdfRenderer(newPfd)
                pdfRenderer = renderer
                renderer.pageCount.takeIf { it > 0 } ?: pdfBoxPageCount
            } catch (_: Throwable) {
                pdfBoxPageCount
            }

            PdfLoadResult.Success(
                pageCount = totalPages,
                fileName = currentFileName,
                fileSize = currentFileSize
            )
        } catch (e: com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException) {
            tempFile.delete()
            PdfLoadResult.Error("Incorrect password. Please verify and try again.")
        } catch (e: Exception) {
            tempFile.delete()
            PdfLoadResult.Error("Unlock failed: ${e.localizedMessage}")
        } finally {
            try { pdDoc?.close() } catch (ignored: Exception) {}
        }
    }

    suspend fun getPageBitmap(pageIndex: Int, targetWidth: Int = 1080): Bitmap? = withContext(Dispatchers.IO) {
        if (pageIndex !in 0 until totalPages) return@withContext null

        // Check in LRU cache
        pageBitmapCache.get(pageIndex)?.let { return@withContext it }

        // Render under Mutex because PdfRenderer only allows one page open at a time
        renderMutex.withLock {
            // Check cache again in case another coroutine just rendered it
            pageBitmapCache.get(pageIndex)?.let { return@withLock it }

            val renderer = pdfRenderer ?: return@withLock null
            var page: PdfRenderer.Page? = null
            try {
                page = renderer.openPage(pageIndex)
                val originalWidth = page.width.coerceAtLeast(1)
                val originalHeight = page.height.coerceAtLeast(1)

                // Scale to fit targetWidth while keeping aspect ratio; use lower resolution & RGB_565 in Low-Memory Mode
                val effectiveTargetWidth = if (isLowMemoryMode) (targetWidth * 0.65f).toInt().coerceAtLeast(480) else targetWidth
                val maxScale = if (isLowMemoryMode) 1.35f else 2.5f
                val maxW = if (isLowMemoryMode) 960 else 1800
                val maxH = if (isLowMemoryMode) 1400 else 2600

                val scale = (effectiveTargetWidth.toFloat() / originalWidth).coerceIn(0.75f, maxScale)
                val width = (originalWidth * scale).toInt().coerceIn(320, maxW)
                val height = (originalHeight * scale).toInt().coerceIn(450, maxH)

                val bmpConfig = if (isLowMemoryMode) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
                val bitmap = Bitmap.createBitmap(width, height, bmpConfig)
                // Draw white background
                val canvas = android.graphics.Canvas(bitmap)
                canvas.drawColor(Color.WHITE)

                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                pageBitmapCache.put(pageIndex, bitmap)
                bitmap
            } catch (e: Exception) {
                null
            } finally {
                try {
                    page?.close()
                } catch (ignored: Exception) {
                }
            }
        }
    }

    suspend fun getThumbnailBitmap(pageIndex: Int): Bitmap? = withContext(Dispatchers.IO) {
        if (pageIndex !in 0 until totalPages) return@withContext null

        thumbnailCache.get(pageIndex)?.let { return@withContext it }

        renderMutex.withLock {
            thumbnailCache.get(pageIndex)?.let { return@withLock it }

            val renderer = pdfRenderer ?: return@withLock null
            var page: PdfRenderer.Page? = null
            try {
                page = renderer.openPage(pageIndex)
                val originalWidth = page.width.coerceAtLeast(1)
                val originalHeight = page.height.coerceAtLeast(1)

                val targetWidth = 200
                val scale = targetWidth.toFloat() / originalWidth
                val width = targetWidth
                val height = ((originalHeight * scale).toInt()).coerceIn(140, 340)

                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565) // lightweight 16-bit
                val canvas = android.graphics.Canvas(bitmap)
                canvas.drawColor(Color.WHITE)

                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                thumbnailCache.put(pageIndex, bitmap)
                bitmap
            } catch (e: Exception) {
                null
            } finally {
                try {
                    page?.close()
                } catch (ignored: Exception) {
                }
            }
        }
    }

    private fun queryFileInfo(uri: Uri): Pair<String, Long> {
        var name = uri.lastPathSegment ?: "Document.pdf"
        var size = 0L

        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) {
                        cursor.getString(nameIndex)?.let { name = it }
                    }
                    if (sizeIndex != -1) {
                        size = cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (e: Exception) {
            // fallback to path segment
        }

        if (!name.endsWith(".pdf", ignoreCase = true)) {
            name = "$name.pdf"
        }
        return Pair(name, size)
    }

    suspend fun rotatePageInDocument(pageIndex: Int, clockwise: Boolean): File? = withContext(Dispatchers.IO) {
        renderMutex.withLock {
            val uri = currentUri ?: return@withLock null
            if (pageIndex !in 0 until totalPages) return@withLock null

            if (!com.tom_roush.pdfbox.android.PDFBoxResourceLoader.isReady()) {
                com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context.applicationContext)
            }

            val cacheDir = File(context.cacheDir, "pdf_cache").apply { mkdirs() }
            val targetTempFile = File(cacheDir, "rotated_${System.currentTimeMillis()}.pdf")

            val pdDoc = PdfBoxMemoryUtil.loadDocument(context, tempCachedFile, uri)
                ?: return@withLock null

            val docPages = pdDoc.numberOfPages
            try {
                val page = pdDoc.getPage(pageIndex)
                val curRot = page.rotation
                val delta = if (clockwise) 90 else -90
                page.rotation = (curRot + delta + 360) % 360
                pdDoc.save(targetTempFile)
            } finally {
                pdDoc.close()
            }

            try {
                pdfRenderer?.close()
            } catch (_: Throwable) {}
            try {
                fileDescriptor?.close()
            } catch (_: Throwable) {}

            tempCachedFile?.delete()
            tempCachedFile = targetTempFile

            try {
                val newPfd = ParcelFileDescriptor.open(targetTempFile, ParcelFileDescriptor.MODE_READ_ONLY)
                fileDescriptor = newPfd
                pdfRenderer = PdfRenderer(newPfd)
            } catch (_: Throwable) {}
            totalPages = (pdfRenderer?.pageCount ?: 0).takeIf { it > 0 } ?: docPages

            pageBitmapCache.evictAll()
            thumbnailCache.evictAll()

            targetTempFile
        }
    }

    suspend fun cropPageInDocument(
        pageIndex: Int,
        normalizedBox: NormalizedRect,
        applyToAllPages: Boolean
    ): File? = withContext(Dispatchers.IO) {
        renderMutex.withLock {
            val uri = currentUri ?: return@withLock null
            if (pageIndex !in 0 until totalPages) return@withLock null

            if (!com.tom_roush.pdfbox.android.PDFBoxResourceLoader.isReady()) {
                com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context.applicationContext)
            }

            val cacheDir = File(context.cacheDir, "pdf_cache").apply { mkdirs() }
            val targetTempFile = File(cacheDir, "crop_${System.currentTimeMillis()}.pdf")

            val pdDoc = PdfBoxMemoryUtil.loadDocument(context, tempCachedFile, uri)
                ?: return@withLock null

            val docPages = pdDoc.numberOfPages
            try {
                val total = pdDoc.numberOfPages
                val pagesToCrop = if (applyToAllPages) (0 until total).toList() else listOf(pageIndex)

                for (pIdx in pagesToCrop) {
                    val page = pdDoc.getPage(pIdx)
                    val media = page.mediaBox ?: com.tom_roush.pdfbox.pdmodel.common.PDRectangle.A4
                    val rotation = (page.rotation % 360 + 360) % 360

                    var pdfLeft = media.lowerLeftX + normalizedBox.left * media.width
                    var pdfRight = media.lowerLeftX + normalizedBox.right * media.width
                    var pdfBottom = media.lowerLeftY + (1f - normalizedBox.bottom) * media.height
                    var pdfTop = media.lowerLeftY + (1f - normalizedBox.top) * media.height

                    when (rotation) {
                        90 -> {
                            pdfLeft = media.lowerLeftX + normalizedBox.top * media.width
                            pdfRight = media.lowerLeftX + normalizedBox.bottom * media.width
                            pdfBottom = media.lowerLeftY + (1f - normalizedBox.right) * media.height
                            pdfTop = media.lowerLeftY + (1f - normalizedBox.left) * media.height
                        }
                        180 -> {
                            pdfLeft = media.lowerLeftX + (1f - normalizedBox.right) * media.width
                            pdfRight = media.lowerLeftX + (1f - normalizedBox.left) * media.width
                            pdfBottom = media.lowerLeftY + normalizedBox.top * media.height
                            pdfTop = media.lowerLeftY + normalizedBox.bottom * media.height
                        }
                        270 -> {
                            pdfBottom = media.lowerLeftY + normalizedBox.left * media.height
                            pdfTop = media.lowerLeftY + normalizedBox.right * media.height
                            pdfLeft = media.lowerLeftX + (1f - normalizedBox.bottom) * media.width
                            pdfRight = media.lowerLeftX + (1f - normalizedBox.top) * media.width
                        }
                    }

                    val finalLeft = minOf(pdfLeft, pdfRight)
                    val finalRight = maxOf(pdfLeft, pdfRight)
                    val finalBottom = minOf(pdfBottom, pdfTop)
                    val finalTop = maxOf(pdfBottom, pdfTop)

                    val cropW = (finalRight - finalLeft).coerceAtLeast(10f)
                    val cropH = (finalTop - finalBottom).coerceAtLeast(10f)

                    val newCrop = com.tom_roush.pdfbox.pdmodel.common.PDRectangle(finalLeft, finalBottom, cropW, cropH)
                    page.cropBox = newCrop
                    page.mediaBox = newCrop
                }

                pdDoc.save(targetTempFile)
            } finally {
                pdDoc.close()
            }

            try {
                pdfRenderer?.close()
            } catch (_: Throwable) {}
            try {
                fileDescriptor?.close()
            } catch (_: Throwable) {}

            tempCachedFile?.delete()
            tempCachedFile = targetTempFile

            try {
                val newPfd = ParcelFileDescriptor.open(targetTempFile, ParcelFileDescriptor.MODE_READ_ONLY)
                fileDescriptor = newPfd
                pdfRenderer = PdfRenderer(newPfd)
            } catch (_: Throwable) {}
            totalPages = (pdfRenderer?.pageCount ?: 0).takeIf { it > 0 } ?: docPages

            pageBitmapCache.evictAll()
            thumbnailCache.evictAll()

            targetTempFile
        }
    }

    suspend fun deletePageInDocument(pageIndex: Int): File? = withContext(Dispatchers.IO) {
        renderMutex.withLock {
            val uri = currentUri ?: return@withLock null
            if (totalPages <= 1 || pageIndex !in 0 until totalPages) return@withLock null

            if (!com.tom_roush.pdfbox.android.PDFBoxResourceLoader.isReady()) {
                com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context.applicationContext)
            }

            val cacheDir = File(context.cacheDir, "pdf_cache").apply { mkdirs() }
            val targetTempFile = File(cacheDir, "deleted_page_${System.currentTimeMillis()}.pdf")

            val pdDoc = PdfBoxMemoryUtil.loadDocument(context, tempCachedFile, uri)
                ?: return@withLock null

            var remainingPages = totalPages
            try {
                if (pdDoc.numberOfPages <= 1 || pageIndex !in 0 until pdDoc.numberOfPages) {
                    return@withLock null
                }
                pdDoc.removePage(pageIndex)
                remainingPages = pdDoc.numberOfPages
                pdDoc.save(targetTempFile)
            } finally {
                pdDoc.close()
            }

            try {
                pdfRenderer?.close()
            } catch (_: Throwable) {}
            try {
                fileDescriptor?.close()
            } catch (_: Throwable) {}

            tempCachedFile?.delete()
            tempCachedFile = targetTempFile

            try {
                val newPfd = ParcelFileDescriptor.open(targetTempFile, ParcelFileDescriptor.MODE_READ_ONLY)
                fileDescriptor = newPfd
                pdfRenderer = PdfRenderer(newPfd)
            } catch (_: Throwable) {}
            totalPages = (pdfRenderer?.pageCount ?: 0).takeIf { it > 0 } ?: remainingPages

            pageBitmapCache.evictAll()
            thumbnailCache.evictAll()

            targetTempFile
        }
    }

    suspend fun replaceOpenDocumentWithFile(modifiedFile: File): Int = withContext(Dispatchers.IO) {
        renderMutex.withLock {
            if (!modifiedFile.exists() || modifiedFile.length() == 0L) return@withLock totalPages

            if (!com.tom_roush.pdfbox.android.PDFBoxResourceLoader.isReady()) {
                com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context.applicationContext)
            }

            val cacheDir = File(context.cacheDir, "pdf_cache").apply { mkdirs() }
            val targetTempFile = if (modifiedFile.parentFile == cacheDir) {
                modifiedFile
            } else {
                val copy = File(cacheDir, "organized_${System.currentTimeMillis()}.pdf")
                modifiedFile.copyTo(copy, overwrite = true)
                copy
            }

            var fallbackCount = totalPages
            try {
                PdfBoxMemoryUtil.loadFromFile(context, targetTempFile).use { doc ->
                    fallbackCount = doc.numberOfPages
                }
            } catch (_: Throwable) {
            }

            try {
                pdfRenderer?.close()
            } catch (_: Throwable) {}
            try {
                fileDescriptor?.close()
            } catch (_: Throwable) {}

            if (tempCachedFile != null && tempCachedFile != targetTempFile) {
                tempCachedFile?.delete()
            }
            tempCachedFile = targetTempFile
            currentFileSize = targetTempFile.length()

            try {
                val newPfd = ParcelFileDescriptor.open(targetTempFile, ParcelFileDescriptor.MODE_READ_ONLY)
                fileDescriptor = newPfd
                pdfRenderer = PdfRenderer(newPfd)
            } catch (_: Throwable) {}
            totalPages = (pdfRenderer?.pageCount ?: 0).takeIf { it > 0 } ?: fallbackCount

            pageBitmapCache.evictAll()
            thumbnailCache.evictAll()

            totalPages
        }
    }

    fun close() {
        try {
            pdfRenderer?.close()
        } catch (ignored: Throwable) {
        }
        pdfRenderer = null

        try {
            fileDescriptor?.close()
        } catch (ignored: Throwable) {
        }
        fileDescriptor = null

        pageBitmapCache.evictAll()
        thumbnailCache.evictAll()

        tempCachedFile?.delete()
        tempCachedFile = null
        totalPages = 0
        PdfBoxMemoryUtil.cleanupScratchFiles(context, null)
    }
}

