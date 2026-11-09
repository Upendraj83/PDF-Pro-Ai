package com.example.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Manages private storage for up to 3 signatures and 3 initials,
 * background transparency removal for imported images, and handwriting typography generation.
 */
class SignatureManager(private val context: Context) {

    private val signaturesDir: File = File(context.filesDir, "saved_signatures").apply {
        if (!exists()) mkdirs()
    }

    private val initialsDir: File = File(context.filesDir, "saved_initials").apply {
        if (!exists()) mkdirs()
    }

    /**
     * Loads up to 3 saved signatures from private storage.
     */
    suspend fun getSavedSignatures(): List<SavedSignature> = withContext(Dispatchers.IO) {
        loadFromDir(signaturesDir, isInitials = false)
    }

    /**
     * Loads up to 3 saved initials from private storage.
     */
    suspend fun getSavedInitials(): List<SavedSignature> = withContext(Dispatchers.IO) {
        loadFromDir(initialsDir, isInitials = true)
    }

    private fun loadFromDir(dir: File, isInitials: Boolean): List<SavedSignature> {
        val list = mutableListOf<SavedSignature>()
        for (slot in 0..2) {
            val file = File(dir, "slot_$slot.png")
            if (file.exists() && file.length() > 0) {
                try {
                    val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    if (bitmap != null) {
                        list.add(
                            SavedSignature(
                                id = "${if (isInitials) "ini" else "sig"}_$slot",
                                slotIndex = slot,
                                isInitials = isInitials,
                                bitmap = bitmap,
                                createdAt = file.lastModified()
                            )
                        )
                    }
                } catch (ignored: Exception) {
                }
            }
        }
        return list
    }

    /**
     * Saves a signature or initials bitmap to private storage in slot 0, 1, or 2.
     */
    suspend fun saveSignature(
        slotIndex: Int,
        bitmap: Bitmap,
        isInitials: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val targetDir = if (isInitials) initialsDir else signaturesDir
            val slot = slotIndex.coerceIn(0, 2)
            val file = File(targetDir, "slot_$slot.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.flush()
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Deletes a saved signature or initials from private storage.
     */
    suspend fun deleteSignature(slotIndex: Int, isInitials: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val targetDir = if (isInitials) initialsDir else signaturesDir
            val slot = slotIndex.coerceIn(0, 2)
            val file = File(targetDir, "slot_$slot.png")
            if (file.exists()) {
                file.delete()
            } else {
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Removes near-white or white background from an imported signature photo.
     * Converts pixels with high luminance (above [threshold]) into transparent alpha (0).
     */
    fun removeWhiteBackground(
        source: Bitmap,
        threshold: Int = 220
    ): Bitmap {
        val width = source.width
        val height = source.height
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        val th = threshold.coerceIn(120, 255)
        for (i in pixels.indices) {
            val color = pixels[i]
            val a = Color.alpha(color)
            val r = Color.red(color)
            val g = Color.green(color)
            val b = Color.blue(color)

            // Calculate perceptual brightness / luminance
            val luminance = (0.299 * r + 0.587 * g + 0.114 * b).toInt()

            if (luminance >= th || (r >= th && g >= th && b >= th)) {
                // Near white/light paper background -> make completely transparent
                pixels[i] = Color.TRANSPARENT
            } else {
                // Keep signature strokes, ensuring solid or smooth alpha
                pixels[i] = Color.argb(a, r, g, b)
            }
        }

        output.setPixels(pixels, 0, width, 0, 0, width, height)
        return output
    }

    /**
     * Generates a cursive/handwritten style signature bitmap for a typed name.
     */
    fun createTypeSignatureBitmap(
        name: String,
        styleIndex: Int = 0,
        colorArgb: Int = Color.BLACK
    ): Bitmap {
        val text = name.ifBlank { "Signature" }

        val typefaces = listOf(
            Typeface.create(Typeface.SERIF, Typeface.ITALIC),
            Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD_ITALIC),
            Typeface.create("cursive", Typeface.ITALIC),
            Typeface.create(Typeface.MONOSPACE, Typeface.ITALIC)
        )
        val selectedTypeface = typefaces.getOrElse(styleIndex.coerceIn(0, typefaces.size - 1)) {
            Typeface.create(Typeface.SERIF, Typeface.ITALIC)
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorArgb
            textSize = 72f
            typeface = selectedTypeface
            style = Paint.Style.FILL
            strokeWidth = 2f
        }

        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)

        val padding = 32
        val width = (bounds.width() + padding * 2).coerceAtLeast(200)
        val height = (bounds.height() + padding * 2).coerceAtLeast(100)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val x = padding.toFloat() - bounds.left
        val y = padding.toFloat() - bounds.top

        canvas.drawText(text, x, y, paint)

        // Draw an elegant baseline underline flourish
        paint.strokeWidth = 3f
        paint.style = Paint.Style.STROKE
        val underlineY = y + 10f
        canvas.drawLine(x - 10f, underlineY, x + bounds.width() + 10f, underlineY, paint)

        return bitmap
    }
}
