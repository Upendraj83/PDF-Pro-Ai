package com.example.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDAcroForm
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDCheckBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDChoice
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDComboBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDListBox
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDNonTerminalField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDRadioButton
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTerminalField
import com.tom_roush.pdfbox.pdmodel.interactive.form.PDTextField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

class PdfFormEngine(private val context: Context) {

    init {
        try {
            PDFBoxResourceLoader.init(context.applicationContext)
        } catch (ignored: Exception) {
        }
    }

    /**
     * Extracts all AcroForm interactive fields from the PDF document.
     */
    suspend fun extractFormFields(
        pdfFile: File?,
        uri: Uri?
    ): List<FormFieldItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<FormFieldItem>()
        var pdDocument: PDDocument? = null

        try {
            pdDocument = openPdDocument(pdfFile, uri) ?: return@withContext emptyList()
            val acroForm = pdDocument.documentCatalog.acroForm ?: return@withContext emptyList()
            val pageCount = pdDocument.numberOfPages

            fun processField(field: PDField) {
                if (field is PDNonTerminalField) {
                    for (child in field.children) {
                        processField(child)
                    }
                    return
                }

                val fqName = field.fullyQualifiedName ?: "field_${results.size}"
                val partialName = field.partialName ?: fqName
                val isRequired = field.isRequired
                val isReadOnly = field.isReadOnly

                val fieldType: FormFieldType = when (field) {
                    is PDCheckBox -> FormFieldType.CHECKBOX
                    is PDRadioButton -> FormFieldType.RADIO
                    is PDComboBox -> FormFieldType.DROPDOWN
                    is PDListBox -> FormFieldType.LIST
                    is PDTextField -> {
                        val lower = fqName.lowercase()
                        if (lower.contains("date") || lower.contains("dob") || lower.contains("deadline")) {
                            FormFieldType.DATE
                        } else {
                            FormFieldType.TEXT
                        }
                    }
                    else -> FormFieldType.TEXT
                }

                val options = when (field) {
                    is PDChoice -> {
                        try {
                            field.optionsDisplayValues ?: field.options ?: emptyList()
                        } catch (e: Exception) {
                            emptyList()
                        }
                    }
                    is PDRadioButton -> {
                        try {
                            field.onValues.toList()
                        } catch (e: Exception) {
                            emptyList()
                        }
                    }
                    else -> emptyList()
                }

                val value = try {
                    field.valueAsString ?: ""
                } catch (e: Exception) {
                    ""
                }

                val widgets = field.widgets
                for (widgetIndex in widgets.indices) {
                    val widget = widgets[widgetIndex]
                    val widgetPage = widget.page
                    var pageIndex = -1

                    if (widgetPage != null) {
                        pageIndex = pdDocument.pages.indexOf(widgetPage)
                    }
                    if (pageIndex < 0) {
                        // Search which page contains this widget
                        for (p in 0 until pageCount) {
                            val pObj = pdDocument.getPage(p)
                            if (pObj.annotations.contains(widget)) {
                                pageIndex = p
                                break
                            }
                        }
                    }
                    if (pageIndex < 0) {
                        pageIndex = 0
                    }

                    val pageObj = pdDocument.getPage(pageIndex)
                    val cropBox = pageObj.cropBox ?: pageObj.mediaBox ?: PDRectangle(0f, 0f, 612f, 792f)
                    val rect = widget.rectangle ?: PDRectangle(0f, 0f, 100f, 20f)

                    val normLeft = ((rect.lowerLeftX - cropBox.lowerLeftX) / cropBox.width).coerceIn(0f, 1f)
                    val normTop = ((cropBox.upperRightY - (rect.lowerLeftY + rect.height)) / cropBox.height).coerceIn(0f, 1f)
                    val normRight = ((rect.lowerLeftX + rect.width - cropBox.lowerLeftX) / cropBox.width).coerceIn(0f, 1f)
                    val normBottom = ((cropBox.upperRightY - rect.lowerLeftY) / cropBox.height).coerceIn(0f, 1f)

                    results.add(
                        FormFieldItem(
                            id = "${fqName}_w$widgetIndex",
                            name = partialName,
                            fullyQualifiedName = fqName,
                            pageIndex = pageIndex,
                            bounds = NormalizedRect(
                                left = normLeft,
                                top = normTop,
                                right = normRight,
                                bottom = normBottom
                            ),
                            type = fieldType,
                            currentValue = value,
                            options = options,
                            isRequired = isRequired,
                            isReadOnly = isReadOnly
                        )
                    )
                }
            }

            for (field in acroForm.fields) {
                processField(field)
            }
        } catch (oom: OutOfMemoryError) {
            System.gc()
        } catch (e: Throwable) {
            // Ignore corrupted form parsing
        } finally {
            try {
                pdDocument?.close()
            } catch (ignored: Throwable) {
            }
        }

        results
    }

    /**
     * Saves updated form field values and permanently burns placed stamps (signatures, text, marks) into the PDF.
     * Optionally flattens the form fields into non-editable static appearances.
     */
    suspend fun saveFormAndStamps(
        sourceFile: File?,
        sourceUri: Uri?,
        fieldValues: Map<String, String>,
        placedStamps: List<PlacedStampItem>,
        flatten: Boolean,
        outputFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        var pdDocument: PDDocument? = null
        try {
            pdDocument = openPdDocument(sourceFile, sourceUri) ?: return@withContext false
            val acroForm = pdDocument.documentCatalog.acroForm

            // 1. Update AcroForm field values if form exists
            if (acroForm != null && fieldValues.isNotEmpty()) {
                for ((fieldName, newValue) in fieldValues) {
                    try {
                        val field = acroForm.getField(fieldName)
                        if (field != null) {
                            when (field) {
                                 is PDTextField -> {
                                    field.value = newValue
                                }
                                is PDCheckBox -> {
                                    val isChecked = newValue.equals("yes", true) ||
                                            newValue.equals("true", true) ||
                                            newValue.equals("1", true) ||
                                            newValue.equals("on", true)
                                    if (isChecked) {
                                        field.check()
                                    } else {
                                        field.unCheck()
                                    }
                                }
                                is PDRadioButton -> {
                                    field.value = newValue
                                }
                                is PDChoice -> {
                                    field.value = listOf(newValue)
                                }
                                else -> {
                                    field.setValue(newValue)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // Skip unmodifiable fields
                    }
                }
            }

            // 2. Burn user-placed stamps/signatures permanently onto pages
            val stampsByPage = placedStamps.groupBy { it.pageIndex }
            for ((pageIdx, stamps) in stampsByPage) {
                if (pageIdx < 0 || pageIdx >= pdDocument.numberOfPages) continue
                val page = pdDocument.getPage(pageIdx)
                val cropBox = page.cropBox ?: page.mediaBox ?: PDRectangle(0f, 0f, 612f, 792f)

                PDPageContentStream(
                    pdDocument,
                    page,
                    PDPageContentStream.AppendMode.APPEND,
                    true,
                    true
                ).use { contentStream ->
                    for (stamp in stamps) {
                        val pdfX = cropBox.lowerLeftX + stamp.normX * cropBox.width
                        val pdfY = cropBox.upperRightY - (stamp.normY + stamp.normHeight) * cropBox.height
                        val pdfW = stamp.normWidth * cropBox.width
                        val pdfH = stamp.normHeight * cropBox.height

                        val renderBitmap: Bitmap = when {
                            stamp.imageBitmap != null -> stamp.imageBitmap
                            stamp.type == PlacedStampType.CHECKMARK -> createVectorTextBitmap("✓", stamp.colorArgb, isBold = true)
                            stamp.type == PlacedStampType.CROSS -> createVectorTextBitmap("✕", stamp.colorArgb, isBold = true)
                            stamp.type == PlacedStampType.DATE -> createVectorTextBitmap(stamp.textValue ?: "", stamp.colorArgb, isBold = false)
                            !stamp.textValue.isNullOrBlank() -> createVectorTextBitmap(stamp.textValue, stamp.colorArgb, isBold = false)
                            else -> continue
                        }

                        try {
                            val pdImage = LosslessFactory.createFromImage(pdDocument, renderBitmap)
                            contentStream.drawImage(pdImage, pdfX, pdfY, pdfW, pdfH)
                        } catch (e: Exception) {
                            // Skip stamp if drawing failed
                        }
                    }
                }
            }

            // 3. Flatten if requested
            if (flatten && acroForm != null) {
                try {
                    acroForm.flatten()
                } catch (e: Exception) {
                    // Ignore flatten errors on complex non-standard fonts
                }
            }

            // 4. Save to target file
            outputFile.parentFile?.mkdirs()
            FileOutputStream(outputFile).use { out ->
                pdDocument.save(out)
                out.flush()
            }

            true
        } catch (e: Exception) {
            false
        } finally {
            try {
                pdDocument?.close()
            } catch (ignored: Exception) {
            }
        }
    }

    /**
     * Adds a new AcroForm field to the specified document page.
     */
    suspend fun addNewFormField(
        sourceFile: File?,
        sourceUri: Uri?,
        pageIndex: Int,
        bounds: NormalizedRect,
        fieldType: FormFieldType,
        fieldName: String,
        defaultValue: String,
        isRequired: Boolean,
        outputFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        var pdDocument: PDDocument? = null
        try {
            pdDocument = openPdDocument(sourceFile, sourceUri) ?: return@withContext false
            val pageCount = pdDocument.numberOfPages
            if (pageIndex < 0 || pageIndex >= pageCount) return@withContext false

            val page = pdDocument.getPage(pageIndex)
            val cropBox = page.cropBox ?: page.mediaBox ?: PDRectangle(0f, 0f, 612f, 792f)

            var acroForm = pdDocument.documentCatalog.acroForm
            if (acroForm == null) {
                acroForm = PDAcroForm(pdDocument)
                pdDocument.documentCatalog.acroForm = acroForm
            }

            val pdfX = cropBox.lowerLeftX + bounds.left * cropBox.width
            val pdfY = cropBox.upperRightY - (bounds.top + bounds.height) * cropBox.height
            val pdfW = bounds.width * cropBox.width
            val pdfH = bounds.height * cropBox.height
            val fieldRect = PDRectangle(pdfX, pdfY, pdfW, pdfH)

            val safeName = fieldName.ifBlank { "field_${UUID.randomUUID().toString().take(6)}" }

            val field: PDField = when (fieldType) {
                FormFieldType.CHECKBOX -> {
                    val cb = PDCheckBox(acroForm)
                    cb.partialName = safeName
                    if (defaultValue.equals("yes", true) || defaultValue.equals("true", true)) {
                        cb.check()
                    }
                    cb
                }
                FormFieldType.RADIO -> {
                    val rb = PDRadioButton(acroForm)
                    rb.partialName = safeName
                    rb
                }
                FormFieldType.DROPDOWN -> {
                    val combo = PDComboBox(acroForm)
                    combo.partialName = safeName
                    combo.setOptions(listOf("Option 1", "Option 2", "Option 3"))
                    if (defaultValue.isNotBlank()) {
                        combo.value = listOf(defaultValue)
                    }
                    combo
                }
                else -> {
                    val tf = PDTextField(acroForm)
                    tf.partialName = safeName
                    if (defaultValue.isNotBlank()) {
                        tf.value = defaultValue
                    }
                    tf
                }
            }

            field.isRequired = isRequired

            val widget = field.widgets.firstOrNull() ?: PDAnnotationWidget()
            widget.rectangle = fieldRect
            widget.page = page

            if (!page.annotations.contains(widget)) {
                page.annotations.add(widget)
            }
            if (!acroForm.fields.contains(field)) {
                acroForm.fields.add(field)
            }

            outputFile.parentFile?.mkdirs()
            FileOutputStream(outputFile).use { out ->
                pdDocument.save(out)
                out.flush()
            }

            true
        } catch (e: Exception) {
            false
        } finally {
            try {
                pdDocument?.close()
            } catch (ignored: Exception) {
            }
        }
    }

    private fun createVectorTextBitmap(text: String, colorArgb: Int, isBold: Boolean): Bitmap {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorArgb
            textSize = 64f
            isFakeBoldText = isBold
            typeface = if (isBold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }

        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)

        val padding = 16
        val width = (bounds.width() + padding * 2).coerceAtLeast(80)
        val height = (bounds.height() + padding * 2).coerceAtLeast(80)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val x = padding.toFloat() - bounds.left
        val y = padding.toFloat() - bounds.top
        canvas.drawText(text, x, y, paint)

        return bitmap
    }

    private fun openPdDocument(pdfFile: File?, uri: Uri?): PDDocument? {
        return PdfBoxMemoryUtil.loadDocument(context, pdfFile, uri)
    }
}
