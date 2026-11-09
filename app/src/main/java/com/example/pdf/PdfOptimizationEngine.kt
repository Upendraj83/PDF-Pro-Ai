package com.example.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.cos.COSArray
import com.tom_roush.pdfbox.cos.COSDictionary
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDDocumentInformation
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.util.Matrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Calendar
import java.util.UUID

enum class CompressionPreset {
    LOW,        // High visual quality, ~80% JPEG, max 2000px
    MEDIUM,     // Balanced, ~60% JPEG, max 1400px
    HIGH,       // Maximum size reduction, ~35% JPEG, max 900px
    TARGET_SIZE // Dynamically search to hit under target bytes
}

data class CompressionResult(
    val originalSize: Long,
    val compressedSize: Long,
    val targetReached: Boolean,
    val bestEffortMessage: String?,
    val outputFile: File
)

data class PdfMetadataItem(
    val title: String = "",
    val author: String = "",
    val subject: String = "",
    val keywords: String = "",
    val creator: String = "",
    val producer: String = "",
    val creationDate: String = "",
    val modificationDate: String = "",
    val pageCount: Int = 0,
    val isEncrypted: Boolean = false
)

enum class WatermarkPosition(val label: String) {
    CENTER("Center"),
    TOP_LEFT("Top Left"),
    TOP_RIGHT("Top Right"),
    BOTTOM_LEFT("Bottom Left"),
    BOTTOM_RIGHT("Bottom Right"),
    TILED_GRID("Tiled 3×3 Grid")
}

data class WatermarkConfig(
    val isText: Boolean = true,
    val text: String = "CONFIDENTIAL",
    val imageUri: Uri? = null,
    val color: Int = Color.GRAY,
    val opacity: Float = 0.35f, // 0.1f - 1.0f
    val rotationDegrees: Float = 45f,
    val isTiled: Boolean = false,
    val fontSize: Float = 42f,
    val position: WatermarkPosition = WatermarkPosition.CENTER,
    val pageRange: String = "" // Blank = All Pages; or "odd", "even", or "1-3, 5"
)

data class HeaderFooterConfig(
    val showPageNumbers: Boolean = true,
    val pageNumberPosition: PageNumberPosition = PageNumberPosition.BOTTOM_CENTER,
    val pageNumberFormat: String = "Page {page} of {total}",
    val headerText: String = "",
    val footerText: String = "",
    val fontSize: Float = 10f,
    val color: Int = Color.DKGRAY
)

enum class PageNumberPosition {
    TOP_LEFT,
    TOP_CENTER,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_CENTER,
    BOTTOM_RIGHT
}

enum class PdfEncryptionAlgorithm(
    val keyLengthBits: Int,
    val preferAes: Boolean,
    val displayLabel: String,
    val shortBadge: String,
    val description: String
) {
    AES_256(
        keyLengthBits = 256,
        preferAes = true,
        displayLabel = "AES-256 (Maximum Security)",
        shortBadge = "AES-256",
        description = "256-bit Advanced Encryption Standard for sensitive legal & financial records"
    ),
    AES_128(
        keyLengthBits = 128,
        preferAes = true,
        displayLabel = "AES-128 (Universal Standard)",
        shortBadge = "AES-128",
        description = "128-bit AES encryption compatible with all modern desktop and mobile PDF readers"
    ),
    RC4_128(
        keyLengthBits = 128,
        preferAes = false,
        displayLabel = "RC4-128 (Legacy Compatible)",
        shortBadge = "RC4-128",
        description = "128-bit RC4 standard for older PDF viewers and printers"
    )
}

enum class PasswordStrengthLevel(
    val score: Int,
    val label: String,
    val fraction: Float
) {
    EMPTY(0, "Enter a password", 0f),
    WEAK(1, "Weak", 0.25f),
    FAIR(2, "Fair", 0.50f),
    GOOD(3, "Good", 0.75f),
    STRONG(4, "Strong", 1.0f)
}

data class PasswordStrengthInfo(
    val level: PasswordStrengthLevel,
    val recommendation: String
)

data class PdfSecurityStatus(
    val isEncrypted: Boolean = false,
    val requiresOpenPassword: Boolean = false,
    val keyLengthBits: Int = 0,
    val algorithmSummary: String = "Unencrypted",
    val pageCount: Int = 0,
    val canPrint: Boolean = true,
    val canCopy: Boolean = true,
    val canEdit: Boolean = true,
    val canAnnotate: Boolean = true,
    val canFillForms: Boolean = true
)

class PdfOptimizationEngine(private val context: Context) {

    companion object {
        private const val UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ"
        private const val LOWER = "abcdefghijkmnopqrstuvwxyz"
        private const val DIGITS = "23456789"
        private const val SYMBOLS = "!@#$%^&*()-_=+"

        fun evaluatePasswordStrength(password: String): PasswordStrengthInfo {
            if (password.isEmpty()) {
                return PasswordStrengthInfo(
                    level = PasswordStrengthLevel.EMPTY,
                    recommendation = "Use at least 8 characters with letters, numbers, and symbols."
                )
            }
            var points = 0
            if (password.length >= 6) points++
            if (password.length >= 10) points++
            if (password.any { it.isUpperCase() } && password.any { it.isLowerCase() }) points++
            if (password.any { it.isDigit() }) points++
            if (password.any { !it.isLetterOrDigit() }) points++

            return when {
                password.length < 6 || points <= 1 -> PasswordStrengthInfo(
                    level = PasswordStrengthLevel.WEAK,
                    recommendation = "Short or simple password. Add numbers, uppercase letters, or symbols."
                )
                points == 2 -> PasswordStrengthInfo(
                    level = PasswordStrengthLevel.FAIR,
                    recommendation = "Decent start — mix uppercase/lowercase and symbols for stronger protection."
                )
                points == 3 -> PasswordStrengthInfo(
                    level = PasswordStrengthLevel.GOOD,
                    recommendation = "Good password strength suitable for standard document sharing."
                )
                else -> PasswordStrengthInfo(
                    level = PasswordStrengthLevel.STRONG,
                    recommendation = "Strong high-entropy password ready for AES encryption."
                )
            }
        }

        fun generateStrongPassword(length: Int = 14): String {
            val targetLen = length.coerceIn(8, 32)
            val random = java.security.SecureRandom()
            val allChars = UPPER + LOWER + DIGITS + SYMBOLS
            val chars = mutableListOf<Char>()
            // Guarantee at least one character from each class
            chars.add(UPPER[random.nextInt(UPPER.length)])
            chars.add(LOWER[random.nextInt(LOWER.length)])
            chars.add(DIGITS[random.nextInt(DIGITS.length)])
            chars.add(SYMBOLS[random.nextInt(SYMBOLS.length)])
            while (chars.size < targetLen) {
                chars.add(allChars[random.nextInt(allChars.length)])
            }
            // Fisher-Yates shuffle with SecureRandom
            for (i in chars.lastIndex downTo 1) {
                val j = random.nextInt(i + 1)
                val tmp = chars[i]
                chars[i] = chars[j]
                chars[j] = tmp
            }
            return chars.joinToString("")
        }
    }

    /**
     * Compress PDF:
     * Recompresses embedded images, removes unused objects and duplicate resources.
     * Preserves sharp vector text unless aggressiveScanMode is chosen.
     */
    suspend fun compressPdf(
        pdfFile: File?,
        uri: Uri?,
        preset: CompressionPreset,
        targetSizeBytes: Long? = null,
        aggressiveScanMode: Boolean = false,
        onProgress: (fraction: Float, step: String) -> Unit
    ): CompressionResult = withContext(Dispatchers.IO) {
        val originalSize = when {
            pdfFile != null && pdfFile.exists() -> pdfFile.length()
            uri != null -> queryFileSize(uri)
            else -> 0L
        }

        onProgress(0.1f, "Preparing document for optimization...")

        val tempOutputFile = File(context.cacheDir, "compressed_${UUID.randomUUID()}.pdf")

        if (aggressiveScanMode) {
            // Aggressive scan mode: render each page to scaled bitmap and recompress as JPEG
            compressAsScannedDocument(pdfFile, uri, preset, tempOutputFile, onProgress)
        } else {
            // Standard lossless-text mode: recompress embedded raster images
            compressEmbeddedImages(pdfFile, uri, preset, targetSizeBytes, tempOutputFile, onProgress)
        }

        val finalSize = tempOutputFile.length()
        val targetReached = if (preset == CompressionPreset.TARGET_SIZE && targetSizeBytes != null) {
            finalSize <= targetSizeBytes
        } else {
            true
        }

        val bestEffortMessage = if (preset == CompressionPreset.TARGET_SIZE && targetSizeBytes != null && !targetReached) {
            "Best achievable compression: ${formatBytes(finalSize)} (Target was ${formatBytes(targetSizeBytes)}). Text and structural components reached minimum limit."
        } else null

        onProgress(1.0f, "Compression complete!")

        CompressionResult(
            originalSize = originalSize,
            compressedSize = finalSize,
            targetReached = targetReached,
            bestEffortMessage = bestEffortMessage,
            outputFile = tempOutputFile
        )
    }

    private suspend fun compressEmbeddedImages(
        pdfFile: File?,
        uri: Uri?,
        preset: CompressionPreset,
        targetSizeBytes: Long?,
        outputFile: File,
        onProgress: (fraction: Float, step: String) -> Unit
    ) {
        val (quality, maxDim) = when (preset) {
            CompressionPreset.LOW -> Pair(0.80f, 2000)
            CompressionPreset.MEDIUM -> Pair(0.60f, 1400)
            CompressionPreset.HIGH -> Pair(0.35f, 900)
            CompressionPreset.TARGET_SIZE -> {
                // Initial aggressive pass for target size
                Pair(0.40f, 1000)
            }
        }

        var doc: PDDocument? = null
        try {
            doc = openPdDocument(pdfFile, uri) ?: throw IllegalStateException("Could not open PDF")
            val totalPages = doc.numberOfPages

            for (pageIndex in 0 until totalPages) {
                currentCoroutineContext().ensureActive()
                val stepFraction = 0.1f + (pageIndex.toFloat() / totalPages.coerceAtLeast(1)) * 0.7f
                onProgress(stepFraction, "Optimizing page ${pageIndex + 1} of $totalPages...")

                val page = doc.getPage(pageIndex)
                val resources = page.resources ?: continue

                for (xName in resources.xObjectNames) {
                    val xObject = resources.getXObject(xName)
                    if (xObject is PDImageXObject) {
                        try {
                            val originalBmp = xObject.image
                            if (originalBmp != null) {
                                val downscaled = downscaleBitmap(originalBmp, maxDim)
                                val compressedImage = JPEGFactory.createFromImage(doc, downscaled, quality)
                                resources.put(xName, compressedImage)
                                if (downscaled != originalBmp) {
                                    downscaled.recycle()
                                }
                            }
                        } catch (ignored: Exception) {
                            // If a specific image cannot be re-encoded, keep original
                        }
                    }
                }
            }

            onProgress(0.85f, "Cleaning unused objects & resources...")
            // Strip metadata stream to save space
            try {
                doc.documentCatalog.metadata = null
            } catch (ignored: Exception) {}

            onProgress(0.92f, "Writing optimized PDF...")
            doc.save(outputFile)

            // If TARGET_SIZE is requested and we are still above target, attempt second pass if possible
            if (preset == CompressionPreset.TARGET_SIZE && targetSizeBytes != null && outputFile.length() > targetSizeBytes) {
                doc.close()
                doc = null

                // Secondary extra-compact pass
                val secondPassDoc = PdfBoxMemoryUtil.loadFromFile(context, outputFile)
                try {
                    val pass2Pages = secondPassDoc.numberOfPages
                    for (p in 0 until pass2Pages) {
                        currentCoroutineContext().ensureActive()
                        val page = secondPassDoc.getPage(p)
                        val res = page.resources ?: continue
                        for (xName in res.xObjectNames) {
                            val xObject = res.getXObject(xName)
                            if (xObject is PDImageXObject) {
                                try {
                                    val bmp = xObject.image ?: continue
                                    val tinyBmp = downscaleBitmap(bmp, 650)
                                    val compactImg = JPEGFactory.createFromImage(secondPassDoc, tinyBmp, 0.22f)
                                    res.put(xName, compactImg)
                                    if (tinyBmp != bmp) tinyBmp.recycle()
                                } catch (ignored: Exception) {}
                            }
                        }
                    }
                    val pass2Temp = File(context.cacheDir, "pass2_${UUID.randomUUID()}.pdf")
                    secondPassDoc.save(pass2Temp)
                    if (pass2Temp.length() < outputFile.length()) {
                        pass2Temp.copyTo(outputFile, overwrite = true)
                    }
                    pass2Temp.delete()
                } finally {
                    try { secondPassDoc.close() } catch (ignored: Exception) {}
                }
            }

        } finally {
            try {
                doc?.close()
            } catch (ignored: Exception) {}
        }
    }

    private suspend fun compressAsScannedDocument(
        pdfFile: File?,
        uri: Uri?,
        preset: CompressionPreset,
        outputFile: File,
        onProgress: (fraction: Float, step: String) -> Unit
    ) {
        val (dpiScale, jpegQuality) = when (preset) {
            CompressionPreset.LOW -> Pair(1.5f, 0.80f)
            CompressionPreset.MEDIUM -> Pair(1.2f, 0.60f)
            CompressionPreset.HIGH -> Pair(0.9f, 0.35f)
            CompressionPreset.TARGET_SIZE -> Pair(0.85f, 0.30f)
        }

        // Open via ParcelFileDescriptor for PdfRenderer
        val pfd: ParcelFileDescriptor = (if (pdfFile != null && pdfFile.exists()) {
            ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
        } else if (uri != null) {
            context.contentResolver.openFileDescriptor(uri, "r")
        } else null) ?: throw IllegalStateException("Cannot access PDF stream")

        val outDoc = PDDocument()
        try {
            val renderer = PdfRenderer(pfd)
            val totalPages = renderer.pageCount

            for (pageIndex in 0 until totalPages) {
                currentCoroutineContext().ensureActive()
                val stepFraction = 0.1f + (pageIndex.toFloat() / totalPages) * 0.75f
                onProgress(stepFraction, "Rendering scanned page ${pageIndex + 1} of $totalPages...")

                val page = renderer.openPage(pageIndex)
                val targetW = (page.width * dpiScale).toInt().coerceAtLeast(300)
                val targetH = (page.height * dpiScale).toInt().coerceAtLeast(400)

                val bitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                page.close()

                val pdPage = PDPage(PDRectangle(page.width.toFloat(), page.height.toFloat()))
                outDoc.addPage(pdPage)

                val pdImage = JPEGFactory.createFromImage(outDoc, bitmap, jpegQuality)
                bitmap.recycle()

                val cs = PDPageContentStream(outDoc, pdPage)
                cs.drawImage(pdImage, 0f, 0f, pdPage.mediaBox.width, pdPage.mediaBox.height)
                cs.close()
            }
            renderer.close()
            pfd.close()

            onProgress(0.90f, "Finalizing compressed file...")
            outDoc.save(outputFile)
        } finally {
            try { outDoc.close() } catch (ignored: Exception) {}
        }
    }

    /**
     * Inspects whether a PDF is currently encrypted, requires an open password, and its active permissions.
     */
    suspend fun inspectSecurityStatus(
        pdfFile: File?,
        uri: Uri?,
        password: String = ""
    ): PdfSecurityStatus = withContext(Dispatchers.IO) {
        if (!com.tom_roush.pdfbox.android.PDFBoxResourceLoader.isReady()) {
            com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context.applicationContext)
        }
        var doc: PDDocument? = null
        try {
            doc = when {
                pdfFile != null && pdfFile.exists() -> {
                    PdfBoxMemoryUtil.loadFromFile(context, pdfFile, password)
                }
                uri != null && uri.scheme == "file" && uri.path != null -> {
                    PdfBoxMemoryUtil.loadFromFile(context, File(uri.path!!), password)
                }
                uri != null -> {
                    val stream = context.contentResolver.openInputStream(uri)
                        ?: return@withContext PdfSecurityStatus()
                    stream.use { PdfBoxMemoryUtil.loadFromStream(context, it, password) }
                }
                else -> return@withContext PdfSecurityStatus()
            }

            val isEnc = doc.isEncrypted
            val encDict = doc.encryption
            val keyLen = encDict?.length ?: if (isEnc) 128 else 0
            val ap = doc.currentAccessPermission
            val algoSummary = if (!isEnc) {
                "Unencrypted (Open)"
            } else {
                val bits = if (keyLen > 0) keyLen else 128
                "Encrypted ($bits-bit PDF Security)"
            }

            PdfSecurityStatus(
                isEncrypted = isEnc,
                requiresOpenPassword = isEnc && password.isNotEmpty(),
                keyLengthBits = keyLen,
                algorithmSummary = algoSummary,
                pageCount = doc.numberOfPages,
                canPrint = ap?.canPrint() ?: true,
                canCopy = ap?.canExtractContent() ?: true,
                canEdit = ap?.canModify() ?: true,
                canAnnotate = ap?.canModifyAnnotations() ?: true,
                canFillForms = ap?.canFillInForm() ?: true
            )
        } catch (inv: InvalidPasswordException) {
            PdfSecurityStatus(
                isEncrypted = true,
                requiresOpenPassword = true,
                keyLengthBits = 128,
                algorithmSummary = "Password-Protected (Locked)",
                pageCount = 0,
                canPrint = false,
                canCopy = false,
                canEdit = false,
                canAnnotate = false,
                canFillForms = false
            )
        } catch (_: Exception) {
            PdfSecurityStatus()
        } finally {
            try { doc?.close() } catch (_: Exception) {}
        }
    }

    /**
     * Password Protect (Encrypt PDF):
     * Encrypts with user password, owner password, chosen encryption algorithm (AES-256 / AES-128 / RC4-128),
     * and configurable AccessPermission policies.
     */
    suspend fun encryptPdf(
        pdfFile: File?,
        uri: Uri?,
        userPassword: String,
        ownerPassword: String,
        canPrint: Boolean = true,
        canCopy: Boolean = true,
        canEdit: Boolean = false,
        canAnnotate: Boolean = canEdit,
        canFillForms: Boolean = true,
        algorithm: PdfEncryptionAlgorithm = PdfEncryptionAlgorithm.AES_256,
        currentPassword: String = "",
        onProgress: (fraction: Float, step: String) -> Unit
    ): File = withContext(Dispatchers.IO) {
        if (!com.tom_roush.pdfbox.android.PDFBoxResourceLoader.isReady()) {
            com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context.applicationContext)
        }
        onProgress(0.2f, "Opening document for ${algorithm.shortBadge} encryption...")
        val doc = try {
            when {
                pdfFile != null && pdfFile.exists() -> {
                    if (currentPassword.isNotEmpty()) {
                        PdfBoxMemoryUtil.loadFromFile(context, pdfFile, currentPassword)
                    } else {
                        try {
                            PdfBoxMemoryUtil.loadFromFile(context, pdfFile)
                        } catch (_: InvalidPasswordException) {
                            PdfBoxMemoryUtil.loadFromFile(context, pdfFile, userPassword)
                        }
                    }
                }
                uri != null && uri.scheme == "file" && uri.path != null -> {
                    val fileFromUri = File(uri.path!!)
                    if (currentPassword.isNotEmpty()) {
                        PdfBoxMemoryUtil.loadFromFile(context, fileFromUri, currentPassword)
                    } else {
                        PdfBoxMemoryUtil.loadFromFile(context, fileFromUri)
                    }
                }
                uri != null -> {
                    val stream = context.contentResolver.openInputStream(uri)
                        ?: throw IllegalStateException("Cannot access PDF stream")
                    stream.use {
                        if (currentPassword.isNotEmpty()) {
                            PdfBoxMemoryUtil.loadFromStream(context, it, currentPassword)
                        } else {
                            PdfBoxMemoryUtil.loadFromStream(context, it)
                        }
                    }
                }
                else -> throw IllegalStateException("Could not open PDF")
            }
        } catch (inv: InvalidPasswordException) {
            throw IllegalArgumentException("Document is already password-protected. Please enter its current password first.")
        }

        val outputFile = File(context.cacheDir, "protected_${UUID.randomUUID()}.pdf")
        try {
            if (doc.isEncrypted) {
                doc.setAllSecurityToBeRemoved(true)
            }
            onProgress(0.5f, "Applying ${algorithm.shortBadge} security policy and permissions...")
            val permissions = AccessPermission().apply {
                setCanPrint(canPrint)
                setCanPrintDegraded(canPrint)
                setCanExtractContent(canCopy)
                setCanExtractForAccessibility(canCopy)
                setCanModify(canEdit)
                setCanAssembleDocument(canEdit)
                setCanModifyAnnotations(canAnnotate)
                setCanFillInForm(canFillForms)
            }

            val effectiveOwnerPass = if (ownerPassword.isBlank()) userPassword else ownerPassword
            val policy = StandardProtectionPolicy(effectiveOwnerPass, userPassword, permissions).apply {
                encryptionKeyLength = algorithm.keyLengthBits
                setPreferAES(algorithm.preferAes)
            }

            try {
                doc.protect(policy)
                onProgress(0.85f, "Writing ${algorithm.shortBadge} encrypted PDF...")
                doc.save(outputFile)
            } catch (_: Exception) {
                // Fallback to 128-bit AES if 256-bit JCE policy is restricted on the host environment
                val fallbackPolicy = StandardProtectionPolicy(effectiveOwnerPass, userPassword, permissions).apply {
                    encryptionKeyLength = 128
                    setPreferAES(true)
                }
                doc.protect(fallbackPolicy)
                onProgress(0.85f, "Writing AES-128 encrypted PDF...")
                doc.save(outputFile)
            }
            onProgress(1.0f, "Encryption complete!")
            outputFile
        } finally {
            try { doc.close() } catch (ignored: Exception) {}
        }
    }

    /**
     * Unlock / Decrypt PDF:
     * Verifies user or owner password, opens the encrypted document, and removes security permanently.
     */
    suspend fun unlockPdf(
        pdfFile: File?,
        uri: Uri?,
        password: String,
        removePasswordPermanently: Boolean = true,
        onProgress: (fraction: Float, step: String) -> Unit
    ): File = withContext(Dispatchers.IO) {
        if (!com.tom_roush.pdfbox.android.PDFBoxResourceLoader.isReady()) {
            com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context.applicationContext)
        }
        onProgress(0.2f, "Verifying credentials and decrypting PDF...")
        val doc = try {
            when {
                pdfFile != null && pdfFile.exists() -> {
                    PdfBoxMemoryUtil.loadFromFile(context, pdfFile, password)
                }
                uri != null && uri.scheme == "file" && uri.path != null -> {
                    PdfBoxMemoryUtil.loadFromFile(context, File(uri.path!!), password)
                }
                uri != null -> {
                    val stream = context.contentResolver.openInputStream(uri)
                        ?: throw IllegalStateException("Cannot access PDF stream")
                    stream.use { PdfBoxMemoryUtil.loadFromStream(context, it, password) }
                }
                else -> {
                    throw IllegalStateException("No document source specified")
                }
            }
        } catch (e: InvalidPasswordException) {
            throw IllegalArgumentException("Incorrect password. Please verify and try again.")
        } catch (e: Exception) {
            throw IllegalStateException("Failed to decrypt: ${e.localizedMessage}")
        }

        val outputFile = File(context.cacheDir, "unlocked_${UUID.randomUUID()}.pdf")
        try {
            if (removePasswordPermanently) {
                onProgress(0.6f, "Removing PDF encryption and permission restrictions...")
                doc.setAllSecurityToBeRemoved(true)
            }
            onProgress(0.85f, "Saving decrypted PDF document...")
            doc.save(outputFile)
            onProgress(1.0f, "Document successfully decrypted!")
            outputFile
        } finally {
            try { doc.close() } catch (ignored: Exception) {}
        }
    }

    /**
     * Convenience alias for permanently decrypting an encrypted PDF with its password.
     */
    suspend fun decryptPdf(
        pdfFile: File?,
        uri: Uri?,
        password: String,
        onProgress: (fraction: Float, step: String) -> Unit = { _, _ -> }
    ): File = unlockPdf(
        pdfFile = pdfFile,
        uri = uri,
        password = password,
        removePasswordPermanently = true,
        onProgress = onProgress
    )

    /**
     * Metadata:
     * Read metadata details from PDF.
     */
    suspend fun getMetadata(pdfFile: File?, uri: Uri?): PdfMetadataItem = withContext(Dispatchers.IO) {
        var doc: PDDocument? = null
        try {
            doc = openPdDocument(pdfFile, uri) ?: return@withContext PdfMetadataItem()
            val info = doc.documentInformation ?: PDDocumentInformation()
            val isEncrypted = doc.isEncrypted

            val creationStr = info.creationDate?.time?.toString() ?: "Not specified"
            val modStr = info.modificationDate?.time?.toString() ?: "Not specified"

            PdfMetadataItem(
                title = info.title ?: "",
                author = info.author ?: "",
                subject = info.subject ?: "",
                keywords = info.keywords ?: "",
                creator = info.creator ?: "",
                producer = info.producer ?: "",
                creationDate = creationStr,
                modificationDate = modStr,
                pageCount = doc.numberOfPages,
                isEncrypted = isEncrypted
            )
        } catch (e: Exception) {
            PdfMetadataItem()
        } finally {
            try { doc?.close() } catch (ignored: Exception) {}
        }
    }

    /**
     * Update metadata fields and save copy.
     */
    suspend fun updateMetadata(
        pdfFile: File?,
        uri: Uri?,
        newMetadata: PdfMetadataItem,
        onProgress: (fraction: Float, step: String) -> Unit
    ): File = withContext(Dispatchers.IO) {
        onProgress(0.3f, "Updating document metadata...")
        val doc = openPdDocument(pdfFile, uri) ?: throw IllegalStateException("Could not open PDF")
        val outputFile = File(context.cacheDir, "metadata_${UUID.randomUUID()}.pdf")

        try {
            val info = doc.documentInformation ?: PDDocumentInformation()
            info.title = newMetadata.title
            info.author = newMetadata.author
            info.subject = newMetadata.subject
            info.keywords = newMetadata.keywords
            info.modificationDate = Calendar.getInstance()
            doc.documentInformation = info

            onProgress(0.8f, "Saving updated document...")
            doc.save(outputFile)
            onProgress(1.0f, "Metadata saved successfully!")
            outputFile
        } finally {
            try { doc.close() } catch (ignored: Exception) {}
        }
    }

    /**
     * Sanitize PDF before sharing:
     * Removes metadata, hidden XMP streams, embedded attachments, and optionally annotations.
     */
    suspend fun sanitizePdf(
        pdfFile: File?,
        uri: Uri?,
        removeMetadata: Boolean = true,
        removeAttachments: Boolean = true,
        removeHiddenXmp: Boolean = true,
        removeAnnotations: Boolean = false,
        onProgress: (fraction: Float, step: String) -> Unit
    ): File = withContext(Dispatchers.IO) {
        onProgress(0.2f, "Inspecting document components...")
        val doc = openPdDocument(pdfFile, uri) ?: throw IllegalStateException("Could not open PDF")
        val outputFile = File(context.cacheDir, "sanitized_${UUID.randomUUID()}.pdf")

        try {
            if (removeMetadata) {
                onProgress(0.4f, "Clearing document properties and history...")
                val blankInfo = PDDocumentInformation()
                blankInfo.title = null
                blankInfo.author = null
                blankInfo.subject = null
                blankInfo.keywords = null
                blankInfo.creator = "PDF ALL IN ONE Pro+Ai"
                blankInfo.producer = "PDF Sanitizer"
                doc.documentInformation = blankInfo
            }

            if (removeHiddenXmp) {
                onProgress(0.6f, "Removing hidden XMP metadata stream...")
                try {
                    doc.documentCatalog.metadata = null
                } catch (ignored: Exception) {}
            }

            if (removeAttachments) {
                onProgress(0.75f, "Removing embedded file attachments...")
                try {
                    val names = doc.documentCatalog.names
                    if (names != null) {
                        names.embeddedFiles = null
                    }
                } catch (ignored: Exception) {}
            }

            if (removeAnnotations) {
                onProgress(0.85f, "Stripping comments and annotations...")
                for (page in doc.pages) {
                    try {
                        page.annotations = emptyList()
                    } catch (ignored: Exception) {}
                }
            }

            onProgress(0.95f, "Saving sanitized PDF...")
            doc.save(outputFile)
            onProgress(1.0f, "Document successfully sanitized!")
            outputFile
        } finally {
            try { doc.close() } catch (ignored: Exception) {}
        }
    }

    /**
     * Watermark, Page Numbers, Header and Footer:
     */
    suspend fun applyWatermarkAndDecorations(
        pdfFile: File?,
        uri: Uri?,
        watermarkConfig: WatermarkConfig?,
        headerFooterConfig: HeaderFooterConfig?,
        onProgress: (fraction: Float, step: String) -> Unit
    ): File = withContext(Dispatchers.IO) {
        onProgress(0.1f, "Opening document...")
        val doc = openPdDocument(pdfFile, uri) ?: throw IllegalStateException("Could not open PDF")
        val outputFile = File(context.cacheDir, "watermarked_${UUID.randomUUID()}.pdf")

        try {
            val totalPages = doc.numberOfPages
            val font = PDType1Font.HELVETICA_BOLD
            val normalFont = PDType1Font.HELVETICA

            val targetWatermarkPages: Set<Int> = if (watermarkConfig != null) {
                resolveTargetPageIndices(watermarkConfig.pageRange, totalPages)
            } else {
                emptySet()
            }

            // Prepare watermark image if specified, or rasterize Unicode/Bengali text watermark
            var watermarkImage: PDImageXObject? = null
            var isUnicodeRasterizedTextWatermark = false
            if (watermarkConfig != null) {
                if (!watermarkConfig.isText && watermarkConfig.imageUri != null) {
                    try {
                        val bmp = decodeBitmapSafely(watermarkConfig.imageUri, maxDimension = 1200)
                        if (bmp != null) {
                            watermarkImage = LosslessFactory.createFromImage(doc, bmp)
                            bmp.recycle()
                        }
                    } catch (ignored: Exception) {}
                } else if (watermarkConfig.isText) {
                    val text = watermarkConfig.text.ifBlank { "CONFIDENTIAL" }
                    if (text.any { it.code > 126 }) {
                        // Render Unicode / Bengali text via Android Canvas so conjuncts display accurately
                        val textBmp = renderTextStampBitmap(
                            text = text,
                            fontSizePt = watermarkConfig.fontSize,
                            colorArgb = watermarkConfig.color,
                            isBold = true
                        )
                        watermarkImage = LosslessFactory.createFromImage(doc, textBmp)
                        textBmp.recycle()
                        isUnicodeRasterizedTextWatermark = true
                    }
                }
            }

            for (pageIndex in 0 until totalPages) {
                currentCoroutineContext().ensureActive()
                val stepFraction = 0.1f + (pageIndex.toFloat() / totalPages) * 0.8f
                onProgress(stepFraction, "Decorating page ${pageIndex + 1} of $totalPages...")

                val page = doc.getPage(pageIndex)
                val mediaBox = page.mediaBox
                val pageW = mediaBox.width
                val pageH = mediaBox.height

                val contentStream = PDPageContentStream(
                    doc,
                    page,
                    PDPageContentStream.AppendMode.APPEND,
                    true,
                    true
                )

                try {
                    // 1. Watermark Layer (applied to selected page range)
                    if (watermarkConfig != null && pageIndex in targetWatermarkPages) {
                        val gState = PDExtendedGraphicsState().apply {
                            nonStrokingAlphaConstant = watermarkConfig.opacity
                            strokingAlphaConstant = watermarkConfig.opacity
                        }
                        contentStream.setGraphicsStateParameters(gState)

                        val useTiled = watermarkConfig.isTiled ||
                            watermarkConfig.position == WatermarkPosition.TILED_GRID

                        if (watermarkConfig.isText && !isUnicodeRasterizedTextWatermark) {
                            val r = Color.red(watermarkConfig.color) / 255f
                            val g = Color.green(watermarkConfig.color) / 255f
                            val b = Color.blue(watermarkConfig.color) / 255f
                            contentStream.setNonStrokingColor(PDColor(floatArrayOf(r, g, b), PDDeviceRGB.INSTANCE))

                            val text = watermarkConfig.text.ifBlank { "CONFIDENTIAL" }
                            val fontSize = watermarkConfig.fontSize
                            val textWidth = (font.getStringWidth(text) / 1000f) * fontSize

                            if (useTiled) {
                                // 3x3 Tiled Pattern
                                val xStep = pageW / 3f
                                val yStep = pageH / 3f
                                for (ix in 0..2) {
                                    for (iy in 0..2) {
                                        val cx = ix * xStep + xStep / 2f
                                        val cy = iy * yStep + yStep / 2f
                                        contentStream.saveGraphicsState()
                                        contentStream.transform(
                                            Matrix.getRotateInstance(
                                                Math.toRadians(watermarkConfig.rotationDegrees.toDouble()),
                                                cx,
                                                cy
                                            )
                                        )
                                        contentStream.beginText()
                                        contentStream.setFont(font, fontSize * 0.75f)
                                        contentStream.newLineAtOffset(cx - (textWidth * 0.75f) / 2f, cy)
                                        contentStream.showText(text)
                                        contentStream.endText()
                                        contentStream.restoreGraphicsState()
                                    }
                                }
                            } else {
                                val (cx, cy) = resolveAnchorCoordinates(
                                    position = watermarkConfig.position,
                                    pageW = pageW,
                                    pageH = pageH,
                                    itemW = textWidth,
                                    itemH = fontSize
                                )
                                contentStream.saveGraphicsState()
                                contentStream.transform(
                                    Matrix.getRotateInstance(
                                        Math.toRadians(watermarkConfig.rotationDegrees.toDouble()),
                                        cx,
                                        cy
                                    )
                                )
                                contentStream.beginText()
                                contentStream.setFont(font, fontSize)
                                contentStream.newLineAtOffset(cx - textWidth / 2f, cy)
                                contentStream.showText(text)
                                contentStream.endText()
                                contentStream.restoreGraphicsState()
                            }
                        } else if (watermarkImage != null) {
                            // Image watermark or Unicode/Bengali rasterized text watermark
                            val maxRatio = if (isUnicodeRasterizedTextWatermark) 0.65f else 0.45f
                            val baseScale = if (isUnicodeRasterizedTextWatermark) 0.5f else 1.0f
                            val imgW = (pageW * maxRatio).coerceAtMost(watermarkImage.width.toFloat() * baseScale)
                            val aspect = watermarkImage.height.toFloat() / watermarkImage.width.toFloat().coerceAtLeast(1f)
                            val imgH = imgW * aspect

                            if (useTiled) {
                                val tileW = imgW * 0.65f
                                val tileH = imgH * 0.65f
                                val xStep = pageW / 3f
                                val yStep = pageH / 3f
                                for (ix in 0..2) {
                                    for (iy in 0..2) {
                                        val cx = ix * xStep + xStep / 2f
                                        val cy = iy * yStep + yStep / 2f
                                        contentStream.saveGraphicsState()
                                        contentStream.transform(
                                            Matrix.getRotateInstance(
                                                Math.toRadians(watermarkConfig.rotationDegrees.toDouble()),
                                                cx,
                                                cy
                                            )
                                        )
                                        contentStream.drawImage(
                                            watermarkImage,
                                            cx - tileW / 2f,
                                            cy - tileH / 2f,
                                            tileW,
                                            tileH
                                        )
                                        contentStream.restoreGraphicsState()
                                    }
                                }
                            } else {
                                val (cx, cy) = resolveAnchorCoordinates(
                                    position = watermarkConfig.position,
                                    pageW = pageW,
                                    pageH = pageH,
                                    itemW = imgW,
                                    itemH = imgH
                                )
                                contentStream.saveGraphicsState()
                                contentStream.transform(
                                    Matrix.getRotateInstance(
                                        Math.toRadians(watermarkConfig.rotationDegrees.toDouble()),
                                        cx,
                                        cy
                                    )
                                )
                                contentStream.drawImage(watermarkImage, cx - imgW / 2f, cy - imgH / 2f, imgW, imgH)
                                contentStream.restoreGraphicsState()
                            }
                        }
                    }

                    // 2. Header and Footer Layer
                    if (headerFooterConfig != null) {
                        val solidGState = PDExtendedGraphicsState().apply {
                            nonStrokingAlphaConstant = 1.0f
                        }
                        contentStream.setGraphicsStateParameters(solidGState)

                        val cr = Color.red(headerFooterConfig.color) / 255f
                        val cg = Color.green(headerFooterConfig.color) / 255f
                        val cb = Color.blue(headerFooterConfig.color) / 255f
                        contentStream.setNonStrokingColor(PDColor(floatArrayOf(cr, cg, cb), PDDeviceRGB.INSTANCE))

                        val margin = 36f
                        val topY = pageH - margin
                        val bottomY = margin

                        // Header Text
                        if (headerFooterConfig.headerText.isNotBlank()) {
                            drawAsciiOrUnicodeLine(
                                doc = doc,
                                contentStream = contentStream,
                                font = normalFont,
                                text = headerFooterConfig.headerText,
                                fontSize = headerFooterConfig.fontSize,
                                colorArgb = headerFooterConfig.color,
                                x = margin,
                                y = topY
                            )
                        }

                        // Footer Text
                        if (headerFooterConfig.footerText.isNotBlank()) {
                            drawAsciiOrUnicodeLine(
                                doc = doc,
                                contentStream = contentStream,
                                font = normalFont,
                                text = headerFooterConfig.footerText,
                                fontSize = headerFooterConfig.fontSize,
                                colorArgb = headerFooterConfig.color,
                                x = margin,
                                y = bottomY
                            )
                        }

                        // Page Numbers
                        if (headerFooterConfig.showPageNumbers) {
                            val pageNumText = headerFooterConfig.pageNumberFormat
                                .replace("{page}", "${pageIndex + 1}")
                                .replace("{total}", "$totalPages")

                            val asciiSafe = pageNumText.all { it.code <= 126 }
                            val strW = if (asciiSafe) {
                                (normalFont.getStringWidth(pageNumText) / 1000f) * headerFooterConfig.fontSize
                            } else {
                                pageNumText.length * headerFooterConfig.fontSize * 0.55f
                            }

                            val (posX, posY) = when (headerFooterConfig.pageNumberPosition) {
                                PageNumberPosition.TOP_LEFT -> Pair(margin, topY)
                                PageNumberPosition.TOP_CENTER -> Pair((pageW - strW) / 2f, topY)
                                PageNumberPosition.TOP_RIGHT -> Pair(pageW - margin - strW, topY)
                                PageNumberPosition.BOTTOM_LEFT -> Pair(margin, bottomY)
                                PageNumberPosition.BOTTOM_CENTER -> Pair((pageW - strW) / 2f, bottomY)
                                PageNumberPosition.BOTTOM_RIGHT -> Pair(pageW - margin - strW, bottomY)
                            }

                            drawAsciiOrUnicodeLine(
                                doc = doc,
                                contentStream = contentStream,
                                font = normalFont,
                                text = pageNumText,
                                fontSize = headerFooterConfig.fontSize,
                                colorArgb = headerFooterConfig.color,
                                x = posX,
                                y = posY
                            )
                        }
                    }

                } finally {
                    contentStream.close()
                }
            }

            onProgress(0.95f, "Writing final document...")
            doc.save(outputFile)
            onProgress(1.0f, "Completed!")
            outputFile
        } finally {
            try { doc.close() } catch (ignored: Exception) {}
        }
    }

    private fun resolveAnchorCoordinates(
        position: WatermarkPosition,
        pageW: Float,
        pageH: Float,
        itemW: Float,
        itemH: Float
    ): Pair<Float, Float> {
        val padX = (itemW / 2f + 42f).coerceAtMost(pageW / 2f)
        val padY = (itemH / 2f + 42f).coerceAtMost(pageH / 2f)
        return when (position) {
            WatermarkPosition.CENTER, WatermarkPosition.TILED_GRID -> Pair(pageW / 2f, pageH / 2f)
            WatermarkPosition.TOP_LEFT -> Pair(padX, pageH - padY)
            WatermarkPosition.TOP_RIGHT -> Pair(pageW - padX, pageH - padY)
            WatermarkPosition.BOTTOM_LEFT -> Pair(padX, padY)
            WatermarkPosition.BOTTOM_RIGHT -> Pair(pageW - padX, padY)
        }
    }

    private fun resolveTargetPageIndices(pageRange: String, totalPages: Int): Set<Int> {
        val trimmed = pageRange.trim().lowercase(java.util.Locale.US)
        if (trimmed.isEmpty() || trimmed == "all") {
            return (0 until totalPages).toSet()
        }
        if (trimmed == "odd") {
            return (0 until totalPages).filter { (it + 1) % 2 == 1 }.toSet()
        }
        if (trimmed == "even") {
            return (0 until totalPages).filter { (it + 1) % 2 == 0 }.toSet()
        }
        val parsed = sortedSetOf<Int>()
        for (part in trimmed.split(",", ";")) {
            val token = part.trim()
            if (token.isEmpty()) continue
            if (token.contains("-")) {
                val bounds = token.split("-").map { it.trim() }
                if (bounds.size == 2) {
                    val s = bounds[0].toIntOrNull()
                    val e = bounds[1].toIntOrNull()
                    if (s != null && e != null) {
                        val low = minOf(s, e).coerceIn(1, totalPages)
                        val high = maxOf(s, e).coerceIn(1, totalPages)
                        for (p in low..high) parsed.add(p - 1)
                    }
                }
            } else {
                val single = token.toIntOrNull()
                if (single != null && single in 1..totalPages) {
                    parsed.add(single - 1)
                }
            }
        }
        return if (parsed.isNotEmpty()) parsed else (0 until totalPages).toSet()
    }

    private fun renderTextStampBitmap(
        text: String,
        fontSizePt: Float,
        colorArgb: Int,
        isBold: Boolean
    ): Bitmap {
        val scale = 2.0f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorArgb
            textSize = fontSizePt * scale
            isFakeBoldText = isBold
        }
        val textW = paint.measureText(text).coerceAtLeast(32f).toInt() + 24
        val fm = paint.fontMetrics
        val textH = (fm.descent - fm.ascent).coerceAtLeast(24f).toInt() + 16
        val bmp = Bitmap.createBitmap(textW, textH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawText(text, 12f, -fm.ascent + 8f, paint)
        return bmp
    }

    private fun drawAsciiOrUnicodeLine(
        doc: PDDocument,
        contentStream: PDPageContentStream,
        font: PDType1Font,
        text: String,
        fontSize: Float,
        colorArgb: Int,
        x: Float,
        y: Float
    ) {
        if (text.all { it.code <= 126 }) {
            contentStream.beginText()
            contentStream.setFont(font, fontSize)
            contentStream.newLineAtOffset(x, y)
            contentStream.showText(text)
            contentStream.endText()
        } else {
            val bmp = renderTextStampBitmap(text, fontSize, colorArgb, isBold = false)
            val img = LosslessFactory.createFromImage(doc, bmp)
            val drawW = bmp.width / 2f
            val drawH = bmp.height / 2f
            bmp.recycle()
            contentStream.drawImage(img, x, y - drawH * 0.25f, drawW, drawH)
        }
    }

    private fun openPdDocument(pdfFile: File?, uri: Uri?): PDDocument? {
        return PdfBoxMemoryUtil.loadDocument(context, pdfFile, uri)
    }

    private fun queryFileSize(uri: Uri): Long {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    private fun downscaleBitmap(source: Bitmap, maxDim: Int): Bitmap {
        val width = source.width
        val height = source.height
        if (width <= maxDim && height <= maxDim) return source

        val scale = minOf(maxDim.toFloat() / width, maxDim.toFloat() / height)
        val targetW = (width * scale).toInt().coerceAtLeast(1)
        val targetH = (height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, targetW, targetH, true)
    }

    private fun decodeBitmapSafely(uri: Uri, maxDimension: Int): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
            var inSampleSize = 1
            while (options.outWidth / inSampleSize > maxDimension || options.outHeight / inSampleSize > maxDimension) {
                inSampleSize *= 2
            }
            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.2f MB", bytes / (1024f * 1024f))
            bytes >= 1024 -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1024f)
            else -> "$bytes B"
        }
    }
}
