package com.example.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

enum class ScanCaptureMode(val label: String) {
    SINGLE_OR_BATCH("Document"),
    ID_CARD("ID Card (2-Sided)"),
    BOOK("Book Spread")
}

enum class ScanFilter(val label: String) {
    ORIGINAL("Original"),
    AUTO_ENHANCE("Auto-Enhance"),
    BLACK_AND_WHITE("Black & White"),
    GRAYSCALE("Grayscale"),
    MAGIC_COLOR("Magic Color")
}

enum class ScanExportPageSize(val label: String) {
    A4("A4 (210 x 297 mm)"),
    LETTER("US Letter (8.5 x 11 in)"),
    FIT_IMAGE("Fit Scanned Image")
}

data class ScannedPageItem(
    val id: String = UUID.randomUUID().toString(),
    val rawFile: File,
    val processedFile: File,
    val corners: List<PointF> = defaultCorners(), // Normalized [0..1] TL, TR, BR, BL
    val rotationDegrees: Int = 0, // 0, 90, 180, 270
    val filter: ScanFilter = ScanFilter.AUTO_ENHANCE,
    val recognizedText: String = "",
    val ocrWords: List<OcrWordBox> = emptyList()
) {
    companion object {
        fun defaultCorners(): List<PointF> = listOf(
            PointF(0.05f, 0.05f),
            PointF(0.95f, 0.05f),
            PointF(0.95f, 0.95f),
            PointF(0.05f, 0.95f)
        )
    }
}

data class OcrWordBox(
    val text: String,
    val leftNorm: Float,
    val topNorm: Float,
    val rightNorm: Float,
    val bottomNorm: Float
)

data class SavedScanRecord(
    val file: File,
    val name: String,
    val sizeBytes: Long,
    val lastModified: Long
)

object ScanImageProcessor {

    /**
     * Safely decodes a bitmap from a Uri or File without exceeding memory limits (maxDim default 2048px).
     */
    fun decodeSafeBitmap(context: Context, uri: Uri?, file: File?, maxDim: Int = 2048): Bitmap? {
        return try {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            if (file != null && file.exists()) {
                BitmapFactory.decodeFile(file.absolutePath, boundsOpts)
            } else if (uri != null) {
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, boundsOpts)
                }
            } else return null

            var sampleSize = 1
            while (boundsOpts.outWidth / sampleSize > maxDim || boundsOpts.outHeight / sampleSize > maxDim) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            if (file != null && file.exists()) {
                BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
            } else if (uri != null) {
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, decodeOpts)
                }
            } else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Safely decodes a bitmap from a File using inSampleSize downscaling so 2 GB RAM devices never OOM.
     */
    fun decodeSafeFile(file: File, maxDim: Int = 2048): Bitmap? {
        if (!file.exists()) return null
        return try {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, boundsOpts)
            var sampleSize = 1
            while (boundsOpts.outWidth / sampleSize > maxDim || boundsOpts.outHeight / sampleSize > maxDim) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Automatic document edge heuristic detector on a downsampled luminance buffer.
     * Finds high-contrast document quadrilateral or returns clean default margins.
     */
    fun detectDocumentCorners(bitmap: Bitmap): List<PointF> {
        try {
            val smallW = 120
            val smallH = (bitmap.height.toFloat() / bitmap.width.coerceAtLeast(1) * smallW).toInt().coerceAtLeast(120)
            val small = Bitmap.createScaledBitmap(bitmap, smallW, smallH, true)

            var minX = smallW
            var maxX = 0
            var minY = smallH
            var maxY = 0

            // Compute average brightness
            var sumLum = 0L
            val pixels = IntArray(smallW * smallH)
            small.getPixels(pixels, 0, smallW, 0, 0, smallW, smallH)
            if (small != bitmap) small.recycle()

            for (px in pixels) {
                val r = Color.red(px)
                val g = Color.green(px)
                val b = Color.blue(px)
                sumLum += (r * 299 + g * 587 + b * 114) / 1000
            }
            val avgLum = (sumLum / pixels.size.coerceAtLeast(1)).toInt()
            val threshold = (avgLum + 22).coerceIn(130, 235)

            var brightCount = 0
            for (y in 4 until smallH - 4) {
                for (x in 4 until smallW - 4) {
                    val px = pixels[y * smallW + x]
                    val lum = (Color.red(px) * 299 + Color.green(px) * 587 + Color.blue(px) * 114) / 1000
                    if (lum >= threshold) {
                        brightCount++
                        if (x < minX) minX = x
                        if (x > maxX) maxX = x
                        if (y < minY) minY = y
                        if (y > maxY) maxY = y
                    }
                }
            }

            // Validate detected box covers at least 20% of frame
            if (brightCount > (smallW * smallH) / 6 && maxX > minX + 20 && maxY > minY + 20) {
                val left = (minX.toFloat() / smallW).coerceIn(0.03f, 0.35f)
                val top = (minY.toFloat() / smallH).coerceIn(0.03f, 0.35f)
                val right = (maxX.toFloat() / smallW).coerceIn(0.65f, 0.97f)
                val bottom = (maxY.toFloat() / smallH).coerceIn(0.65f, 0.97f)
                return listOf(
                    PointF(left, top),
                    PointF(right, top),
                    PointF(right, bottom),
                    PointF(left, bottom)
                )
            }
        } catch (ignored: Exception) {}

        return ScannedPageItem.defaultCorners()
    }

    /**
     * Applies 4-corner perspective correction, rotation, and image enhancement filter.
     * Saves result to outputFile and returns it.
     */
    fun processPageImage(
        rawFile: File,
        corners: List<PointF>,
        rotationDegrees: Int,
        filter: ScanFilter,
        outputFile: File
    ): Boolean {
        val rawBitmap = decodeSafeFile(rawFile, maxDim = 2048) ?: return false
        try {
            // 1. Perspective Warp
            val warped = warpPerspective(rawBitmap, corners)
            if (warped != rawBitmap) {
                rawBitmap.recycle()
            }

            // 2. Rotation
            val rotated = if (rotationDegrees % 360 != 0) {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                val rot = Bitmap.createBitmap(warped, 0, 0, warped.width, warped.height, matrix, true)
                if (rot != warped) warped.recycle()
                rot
            } else {
                warped
            }

            // 3. Enhancement Filter
            val filtered = applyFilter(rotated, filter)
            if (filtered != rotated) {
                rotated.recycle()
            }

            // 4. Write to outputFile
            FileOutputStream(outputFile).use { out ->
                filtered.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            filtered.recycle()
            return true
        } catch (e: Exception) {
            try { rawBitmap.recycle() } catch (ignored: Exception) {}
            return false
        }
    }

    /**
     * Performs 4-point perspective transformation using Android Matrix.setPolyToPoly.
     */
    fun warpPerspective(source: Bitmap, corners: List<PointF>): Bitmap {
        if (corners.size != 4) return source

        val w = source.width.toFloat()
        val h = source.height.toFloat()

        val tl = PointF(corners[0].x * w, corners[0].y * h)
        val tr = PointF(corners[1].x * w, corners[1].y * h)
        val br = PointF(corners[2].x * w, corners[2].y * h)
        val bl = PointF(corners[3].x * w, corners[3].y * h)

        val widthTop = hypot((tr.x - tl.x).toDouble(), (tr.y - tl.y).toDouble()).toFloat()
        val widthBottom = hypot((br.x - bl.x).toDouble(), (br.y - bl.y).toDouble()).toFloat()
        val targetW = max(widthTop, widthBottom).toInt().coerceIn(200, 2400)

        val heightLeft = hypot((bl.x - tl.x).toDouble(), (bl.y - tl.y).toDouble()).toFloat()
        val heightRight = hypot((br.x - tr.x).toDouble(), (br.y - tr.y).toDouble()).toFloat()
        val targetH = max(heightLeft, heightRight).toInt().coerceIn(200, 2400)

        val srcPts = floatArrayOf(
            tl.x, tl.y,
            tr.x, tr.y,
            br.x, br.y,
            bl.x, bl.y
        )
        val dstPts = floatArrayOf(
            0f, 0f,
            targetW.toFloat(), 0f,
            targetW.toFloat(), targetH.toFloat(),
            0f, targetH.toFloat()
        )

        val matrix = Matrix()
        if (!matrix.setPolyToPoly(srcPts, 0, dstPts, 0, 4)) {
            return source
        }

        val output = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(source, matrix, paint)
        return output
    }

    /**
     * Applies document filter: Original, Auto-Enhance, Black & White, Grayscale, Magic Color.
     */
    fun applyFilter(source: Bitmap, filter: ScanFilter): Bitmap {
        return when (filter) {
            ScanFilter.ORIGINAL -> source
            ScanFilter.AUTO_ENHANCE -> {
                // Boost contrast (1.30x) and slight brightness lift for crisp paper
                val cm = ColorMatrix(
                    floatArrayOf(
                        1.30f, 0f, 0f, 0f, -20f,
                        0f, 1.30f, 0f, 0f, -20f,
                        0f, 0f, 1.30f, 0f, -20f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                renderWithColorMatrix(source, cm)
            }
            ScanFilter.GRAYSCALE -> {
                val cm = ColorMatrix().apply { setSaturation(0f) }
                val contrast = ColorMatrix(
                    floatArrayOf(
                        1.25f, 0f, 0f, 0f, -15f,
                        0f, 1.25f, 0f, 0f, -15f,
                        0f, 0f, 1.25f, 0f, -15f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                cm.postConcat(contrast)
                renderWithColorMatrix(source, cm)
            }
            ScanFilter.BLACK_AND_WHITE -> {
                // High-contrast adaptive document thresholding
                applyBlackAndWhiteThreshold(source)
            }
            ScanFilter.MAGIC_COLOR -> {
                // Vivid saturation (1.45x) + high paper whitening contrast (1.45x)
                val cm = ColorMatrix().apply { setSaturation(1.45f) }
                val magicContrast = ColorMatrix(
                    floatArrayOf(
                        1.48f, 0f, 0f, 0f, -35f,
                        0f, 1.48f, 0f, 0f, -35f,
                        0f, 0f, 1.48f, 0f, -35f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                cm.postConcat(magicContrast)
                renderWithColorMatrix(source, cm)
            }
        }
    }

    private fun renderWithColorMatrix(source: Bitmap, colorMatrix: ColorMatrix): Bitmap {
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(colorMatrix)
        }
        canvas.drawBitmap(source, 0f, 0f, paint)
        return out
    }

    private fun applyBlackAndWhiteThreshold(source: Bitmap): Bitmap {
        val w = source.width
        val h = source.height
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        var totalLum = 0L
        for (px in pixels) {
            val r = Color.red(px)
            val g = Color.green(px)
            val b = Color.blue(px)
            totalLum += (r * 299 + g * 587 + b * 114) / 1000
        }
        val avgLum = (totalLum / pixels.size.coerceAtLeast(1)).toInt()
        val threshold = (avgLum - 12).coerceIn(95, 195)

        for (i in pixels.indices) {
            val px = pixels[i]
            val lum = (Color.red(px) * 299 + Color.green(px) * 587 + Color.blue(px) * 114) / 1000
            pixels[i] = if (lum >= threshold) Color.WHITE else Color.BLACK
        }

        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    /**
     * Book Mode helper: Splits a facing-page spread bitmap down the center into Left and Right pages.
     */
    fun splitBookSpread(source: Bitmap, cacheDir: File): Pair<File, File> {
        val w = source.width
        val h = source.height
        val halfW = (w / 2).coerceAtLeast(1)

        val leftBmp = if (w >= h) {
            Bitmap.createBitmap(source, 0, 0, halfW, h)
        } else {
            val halfH = (h / 2).coerceAtLeast(1)
            Bitmap.createBitmap(source, 0, 0, w, halfH)
        }

        val rightBmp = if (w >= h) {
            Bitmap.createBitmap(source, halfW, 0, w - halfW, h)
        } else {
            val halfH = (h / 2).coerceAtLeast(1)
            Bitmap.createBitmap(source, 0, halfH, w, h - halfH)
        }

        val leftFile = File(cacheDir, "book_left_${UUID.randomUUID()}.jpg")
        val rightFile = File(cacheDir, "book_right_${UUID.randomUUID()}.jpg")

        FileOutputStream(leftFile).use { leftBmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        FileOutputStream(rightFile).use { rightBmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }

        leftBmp.recycle()
        rightBmp.recycle()

        return Pair(leftFile, rightFile)
    }

    /**
     * ID Card Mode helper: Combines Front and Back card images onto a single A4 portrait canvas.
     */
    fun combineIdCardFrontAndBack(frontFile: File, backFile: File, cacheDir: File): File {
        val a4W = 1240
        val a4H = 1754
        val combined = Bitmap.createBitmap(a4W, a4H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(combined)
        canvas.drawColor(Color.WHITE)

        val frontBmp = decodeSafeFile(frontFile, maxDim = 1600)
        val backBmp = decodeSafeFile(backFile, maxDim = 1600)

        val cardMaxW = (a4W * 0.72f).toInt()
        val cardMaxH = (a4H * 0.36f).toInt()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        if (frontBmp != null) {
            val scale = min(cardMaxW.toFloat() / frontBmp.width, cardMaxH.toFloat() / frontBmp.height)
            val drawW = (frontBmp.width * scale).toInt()
            val drawH = (frontBmp.height * scale).toInt()
            val left = (a4W - drawW) / 2f
            val top = a4H * 0.12f
            val scaled = Bitmap.createScaledBitmap(frontBmp, drawW, drawH, true)
            canvas.drawBitmap(scaled, left, top, paint)
            if (scaled != frontBmp) scaled.recycle()
            frontBmp.recycle()
        }

        if (backBmp != null) {
            val scale = min(cardMaxW.toFloat() / backBmp.width, cardMaxH.toFloat() / backBmp.height)
            val drawW = (backBmp.width * scale).toInt()
            val drawH = (backBmp.height * scale).toInt()
            val left = (a4W - drawW) / 2f
            val top = a4H * 0.54f
            val scaled = Bitmap.createScaledBitmap(backBmp, drawW, drawH, true)
            canvas.drawBitmap(scaled, left, top, paint)
            if (scaled != backBmp) scaled.recycle()
            backBmp.recycle()
        }

        val outFile = File(cacheDir, "id_card_combined_${UUID.randomUUID()}.jpg")
        FileOutputStream(outFile).use {
            combined.compress(Bitmap.CompressFormat.JPEG, 92, it)
        }
        combined.recycle()
        return outFile
    }
}
