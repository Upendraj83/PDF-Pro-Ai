package com.example.pdf

import android.content.Context
import android.graphics.Color
import android.net.Uri
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotation
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLine
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationMarkup
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationSquareCircle
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationText
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.UUID

sealed interface SaveAnnotationProgress {
    data class Processing(val currentPage: Int, val totalPages: Int, val message: String) : SaveAnnotationProgress
    data class Success(val savedFile: File, val isFlattened: Boolean) : SaveAnnotationProgress
    data class Failure(val error: String) : SaveAnnotationProgress
}

class PdfAnnotationEngine(private val context: Context) {

    suspend fun loadExistingAnnotations(
        pdfFile: File?,
        uri: Uri?
    ): List<PdfAnnotation> = withContext(Dispatchers.IO) {
        val loaded = mutableListOf<PdfAnnotation>()
        var pdDoc: PDDocument? = null

        try {
            pdDoc = openPdDocument(pdfFile, uri) ?: return@withContext emptyList()
            val totalPages = pdDoc.numberOfPages

            for (pageIndex in 0 until totalPages) {
                val page = pdDoc.getPage(pageIndex)
                val mediaBox = page.mediaBox
                val pageWidth = mediaBox.width
                val pageHeight = mediaBox.height

                val annotations: List<PDAnnotation> = try {
                    page.annotations ?: emptyList()
                } catch (e: Exception) {
                    emptyList()
                }

                for (annot in annotations) {
                    try {
                        val parsed = convertPdAnnotationToModel(annot, pageIndex, pageWidth, pageHeight)
                        if (parsed != null) {
                            loaded.add(parsed)
                        }
                    } catch (ignored: Throwable) {
                    }
                }
            }
        } catch (oom: OutOfMemoryError) {
            System.gc()
        } catch (e: Throwable) {
            // Ignore error loading broken annotations
        } finally {
            try {
                pdDoc?.close()
            } catch (ignored: Throwable) {
            }
        }

        loaded
    }

    private fun convertPdAnnotationToModel(
        annot: PDAnnotation,
        pageIndex: Int,
        pageWidth: Float,
        pageHeight: Float
    ): PdfAnnotation? {
        val rect = annot.rectangle ?: return null
        val normLeft = (rect.lowerLeftX / pageWidth).coerceIn(0f, 1f)
        val normRight = (rect.upperRightX / pageWidth).coerceIn(0f, 1f)
        val normTop = (1f - (rect.upperRightY / pageHeight)).coerceIn(0f, 1f)
        val normBottom = (1f - (rect.lowerLeftY / pageHeight)).coerceIn(0f, 1f)

        val colorArgb = extractColorArgb(annot.color)

        return when (annot) {
            is PDAnnotationTextMarkup -> {
                val type = when (annot.subtype) {
                    PDAnnotationTextMarkup.SUB_TYPE_UNDERLINE -> AnnotationType.UNDERLINE
                    PDAnnotationTextMarkup.SUB_TYPE_STRIKEOUT -> AnnotationType.STRIKEOUT
                    else -> AnnotationType.HIGHLIGHT
                }
                PdfAnnotation.TextMarkup(
                    id = UUID.randomUUID().toString(),
                    pageIndex = pageIndex,
                    type = type,
                    colorArgb = colorArgb ?: if (type == AnnotationType.HIGHLIGHT) 0x88FFEB3B.toInt() else 0xFFE53935.toInt(),
                    rects = listOf(NormalizedRect(normLeft, normTop, normRight, normBottom)),
                    selectedText = annot.contents ?: "",
                    isExistingFromPdf = true
                )
            }
            is PDAnnotationText -> {
                PdfAnnotation.StickyNote(
                    id = UUID.randomUUID().toString(),
                    pageIndex = pageIndex,
                    colorArgb = colorArgb ?: 0xFFFFD54F.toInt(),
                    position = NormalizedPoint(normLeft, normTop),
                    noteText = annot.contents ?: "",
                    author = annot.annotationName ?: "Author",
                    isExistingFromPdf = true
                )
            }
            is PDAnnotationSquareCircle -> {
                val isCircle = annot.subtype == PDAnnotationSquareCircle.SUB_TYPE_CIRCLE
                PdfAnnotation.GeometricShape(
                    id = UUID.randomUUID().toString(),
                    pageIndex = pageIndex,
                    type = if (isCircle) AnnotationType.ELLIPSE else AnnotationType.RECTANGLE,
                    colorArgb = colorArgb ?: 0xFF1E88E5.toInt(),
                    strokeWidth = 2f,
                    startPoint = NormalizedPoint(normLeft, normTop),
                    endPoint = NormalizedPoint(normRight, normBottom),
                    isExistingFromPdf = true
                )
            }
            is PDAnnotationLine -> {
                val line = annot.line
                if (line != null && line.size >= 4) {
                    val x1 = (line[0] / pageWidth).coerceIn(0f, 1f)
                    val y1 = (1f - (line[1] / pageHeight)).coerceIn(0f, 1f)
                    val x2 = (line[2] / pageWidth).coerceIn(0f, 1f)
                    val y2 = (1f - (line[3] / pageHeight)).coerceIn(0f, 1f)
                    PdfAnnotation.GeometricShape(
                        id = UUID.randomUUID().toString(),
                        pageIndex = pageIndex,
                        type = AnnotationType.LINE,
                        colorArgb = colorArgb ?: 0xFF000000.toInt(),
                        strokeWidth = 2f,
                        startPoint = NormalizedPoint(x1, y1),
                        endPoint = NormalizedPoint(x2, y2),
                        isExistingFromPdf = true
                    )
                } else null
            }
            is PDAnnotationMarkup -> {
                if (annot.subtype == PDAnnotationMarkup.SUB_TYPE_FREETEXT) {
                    PdfAnnotation.FreeTextBox(
                        id = UUID.randomUUID().toString(),
                        pageIndex = pageIndex,
                        colorArgb = colorArgb ?: 0xFF000000.toInt(),
                        bounds = NormalizedRect(normLeft, normTop, normRight, normBottom),
                        text = annot.contents ?: "",
                        isExistingFromPdf = true
                    )
                } else if (annot.subtype == PDAnnotationMarkup.SUB_TYPE_INK) {
                    val inkList = annot.inkList
                    if (inkList != null && inkList.isNotEmpty()) {
                        val points = mutableListOf<NormalizedPoint>()
                        for (stroke in inkList) {
                            var i = 0
                            while (i < stroke.size - 1) {
                                val px = (stroke[i] / pageWidth).coerceIn(0f, 1f)
                                val py = (1f - (stroke[i + 1] / pageHeight)).coerceIn(0f, 1f)
                                points.add(NormalizedPoint(px, py))
                                i += 2
                            }
                        }
                        PdfAnnotation.InkStroke(
                            id = UUID.randomUUID().toString(),
                            pageIndex = pageIndex,
                            type = AnnotationType.PEN,
                            colorArgb = colorArgb ?: 0xFF000000.toInt(),
                            strokeWidth = 2f,
                            points = points,
                            isExistingFromPdf = true
                        )
                    } else null
                } else null
            }
            else -> null
        }
    }

    suspend fun saveDocumentWithAnnotations(
        sourceFile: File?,
        sourceUri: Uri?,
        annotations: List<PdfAnnotation>,
        flatten: Boolean,
        onProgress: (SaveAnnotationProgress) -> Unit
    ) = withContext(Dispatchers.IO) {
        var pdDoc: PDDocument? = null
        val tempOutput = File(context.cacheDir, "annot_save_${System.currentTimeMillis()}.pdf")

        try {
            onProgress(SaveAnnotationProgress.Processing(0, 1, "Opening PDF document..."))
            pdDoc = openPdDocument(sourceFile, sourceUri)
            if (pdDoc == null) {
                onProgress(SaveAnnotationProgress.Failure("Could not open source PDF document."))
                return@withContext
            }

            val totalPages = pdDoc.numberOfPages
            val annotationsByPage = annotations.groupBy { it.pageIndex }

            for (pageIndex in 0 until totalPages) {
                onProgress(
                    SaveAnnotationProgress.Processing(
                        currentPage = pageIndex + 1,
                        totalPages = totalPages,
                        message = if (flatten) "Flattening page ${pageIndex + 1} of $totalPages..."
                        else "Writing annotations to page ${pageIndex + 1} of $totalPages..."
                    )
                )

                val page = pdDoc.getPage(pageIndex)
                val mediaBox = page.mediaBox
                val pageWidth = mediaBox.width
                val pageHeight = mediaBox.height

                val pageAnnotations = annotationsByPage[pageIndex] ?: emptyList()

                if (flatten) {
                    if (pageAnnotations.isNotEmpty()) {
                        burnAnnotationsIntoPage(pdDoc, page, pageAnnotations, pageWidth, pageHeight)
                    }
                } else {
                    val newAnnotations = pageAnnotations.filter { !it.isExistingFromPdf }
                    for (annot in newAnnotations) {
                        try {
                            val pdAnnot = createPdAnnotationFromModel(annot, pageWidth, pageHeight)
                            if (pdAnnot != null) {
                                page.annotations.add(pdAnnot)
                            }
                        } catch (ignored: Exception) {
                        }
                    }
                }
            }

            onProgress(SaveAnnotationProgress.Processing(totalPages, totalPages, "Compiling final PDF..."))
            pdDoc.save(tempOutput)
            onProgress(SaveAnnotationProgress.Success(tempOutput, flatten))
        } catch (e: Exception) {
            onProgress(SaveAnnotationProgress.Failure(e.localizedMessage ?: "Failed to save PDF annotations."))
        } finally {
            try {
                pdDoc?.close()
            } catch (ignored: Exception) {
            }
        }
    }

    private fun burnAnnotationsIntoPage(
        doc: PDDocument,
        page: PDPage,
        annotations: List<PdfAnnotation>,
        pageWidth: Float,
        pageHeight: Float
    ) {
        try {
            PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
                for (annot in annotations) {
                    val (r, g, b) = getRgbComponents(annot.colorArgb)
                    when (annot) {
                        is PdfAnnotation.TextMarkup -> {
                            stream.setNonStrokingColor(r, g, b)
                            stream.setStrokingColor(r, g, b)
                            for (rect in annot.rects) {
                                val x = rect.left * pageWidth
                                val y = (1f - rect.bottom) * pageHeight
                                val w = rect.width * pageWidth
                                val h = rect.height * pageHeight
                                when (annot.type) {
                                    AnnotationType.HIGHLIGHT -> {
                                        stream.addRect(x, y, w, h)
                                        stream.fill()
                                    }
                                    AnnotationType.UNDERLINE -> {
                                        stream.setLineWidth(annot.strokeWidth)
                                        stream.moveTo(x, y)
                                        stream.lineTo(x + w, y)
                                        stream.stroke()
                                    }
                                    AnnotationType.STRIKEOUT -> {
                                        stream.setLineWidth(annot.strokeWidth)
                                        stream.moveTo(x, y + (h / 2f))
                                        stream.lineTo(x + w, y + (h / 2f))
                                        stream.stroke()
                                    }
                                    else -> {}
                                }
                            }
                        }
                        is PdfAnnotation.InkStroke -> {
                            if (annot.points.size >= 2) {
                                stream.setStrokingColor(r, g, b)
                                stream.setLineWidth(annot.strokeWidth)
                                val first = annot.points[0]
                                stream.moveTo(first.x * pageWidth, (1f - first.y) * pageHeight)
                                for (i in 1 until annot.points.size) {
                                    val pt = annot.points[i]
                                    stream.lineTo(pt.x * pageWidth, (1f - pt.y) * pageHeight)
                                }
                                stream.stroke()
                            }
                        }
                        is PdfAnnotation.GeometricShape -> {
                            stream.setStrokingColor(r, g, b)
                            stream.setLineWidth(annot.strokeWidth)
                            val x1 = annot.startPoint.x * pageWidth
                            val y1 = (1f - annot.startPoint.y) * pageHeight
                            val x2 = annot.endPoint.x * pageWidth
                            val y2 = (1f - annot.endPoint.y) * pageHeight
                            val left = minOf(x1, x2)
                            val bottom = minOf(y1, y2)
                            val w = kotlin.math.abs(x2 - x1)
                            val h = kotlin.math.abs(y2 - y1)

                            when (annot.type) {
                                AnnotationType.RECTANGLE -> {
                                    stream.addRect(left, bottom, w, h)
                                    stream.stroke()
                                }
                                AnnotationType.LINE, AnnotationType.ARROW -> {
                                    stream.moveTo(x1, y1)
                                    stream.lineTo(x2, y2)
                                    stream.stroke()
                                }
                                AnnotationType.ELLIPSE -> {
                                    stream.addRect(left, bottom, w, h)
                                    stream.stroke()
                                }
                                else -> {}
                            }
                        }
                        is PdfAnnotation.FreeTextBox -> {
                            val x = annot.bounds.left * pageWidth
                            val y = (1f - annot.bounds.bottom) * pageHeight
                            val w = annot.bounds.width * pageWidth
                            val h = annot.bounds.height * pageHeight
                            stream.setStrokingColor(r, g, b)
                            stream.addRect(x, y, w, h)
                            stream.stroke()

                            try {
                                stream.beginText()
                                stream.setFont(PDType1Font.HELVETICA_BOLD, annot.fontSize.coerceIn(10f, 24f))
                                stream.setNonStrokingColor(r, g, b)
                                stream.newLineAtOffset(x + 4f, y + h - annot.fontSize - 4f)
                                stream.showText(annot.text)
                                stream.endText()
                            } catch (ignored: Exception) {
                            }
                        }
                        is PdfAnnotation.StickyNote -> {
                            val x = annot.position.x * pageWidth
                            val y = (1f - annot.position.y) * pageHeight
                            stream.setNonStrokingColor(r, g, b)
                            stream.addRect(x, y, 20f, 20f)
                            stream.fill()
                        }
                    }
                }
            }
            page.annotations.clear()
        } catch (e: Exception) {
        }
    }

    private fun createPdAnnotationFromModel(
        annot: PdfAnnotation,
        pageWidth: Float,
        pageHeight: Float
    ): PDAnnotation? {
        val (r, g, b) = getRgbComponents(annot.colorArgb)
        val pdColor = PDColor(floatArrayOf(r, g, b), PDDeviceRGB.INSTANCE)

        return when (annot) {
            is PdfAnnotation.TextMarkup -> {
                val firstRect = annot.rects.firstOrNull() ?: return null
                val pdfRect = createPdRectangle(firstRect, pageWidth, pageHeight)

                val subtype = when (annot.type) {
                    AnnotationType.HIGHLIGHT -> PDAnnotationTextMarkup.SUB_TYPE_HIGHLIGHT
                    AnnotationType.UNDERLINE -> PDAnnotationTextMarkup.SUB_TYPE_UNDERLINE
                    AnnotationType.STRIKEOUT -> PDAnnotationTextMarkup.SUB_TYPE_STRIKEOUT
                    else -> PDAnnotationTextMarkup.SUB_TYPE_HIGHLIGHT
                }

                val markup = PDAnnotationTextMarkup(subtype)
                markup.rectangle = pdfRect
                markup.color = pdColor
                markup.contents = annot.selectedText

                val quadPoints = FloatArray(annot.rects.size * 8)
                annot.rects.forEachIndexed { i, rNorm ->
                    val rPdf = createPdRectangle(rNorm, pageWidth, pageHeight)
                    val offset = i * 8
                    quadPoints[offset + 0] = rPdf.lowerLeftX
                    quadPoints[offset + 1] = rPdf.upperRightY
                    quadPoints[offset + 2] = rPdf.upperRightX
                    quadPoints[offset + 3] = rPdf.upperRightY
                    quadPoints[offset + 4] = rPdf.lowerLeftX
                    quadPoints[offset + 5] = rPdf.lowerLeftY
                    quadPoints[offset + 6] = rPdf.upperRightX
                    quadPoints[offset + 7] = rPdf.lowerLeftY
                }
                markup.quadPoints = quadPoints
                markup.constructAppearances()
                markup
            }
            is PdfAnnotation.InkStroke -> {
                if (annot.points.size < 2) return null
                val ink = PDAnnotationMarkup()
                ink.cosObject.setName(COSName.SUBTYPE, PDAnnotationMarkup.SUB_TYPE_INK)

                val minX = annot.points.minOf { it.x } * pageWidth
                val maxX = annot.points.maxOf { it.x } * pageWidth
                val minY = (1f - annot.points.maxOf { it.y }) * pageHeight
                val maxY = (1f - annot.points.minOf { it.y }) * pageHeight

                ink.rectangle = PDRectangle(minX, minY, (maxX - minX).coerceAtLeast(10f), (maxY - minY).coerceAtLeast(10f))
                ink.color = pdColor

                val strokeArray = FloatArray(annot.points.size * 2)
                annot.points.forEachIndexed { i, pt ->
                    strokeArray[i * 2] = pt.x * pageWidth
                    strokeArray[i * 2 + 1] = (1f - pt.y) * pageHeight
                }
                ink.inkList = arrayOf(strokeArray)
                ink.constructAppearances()
                ink
            }
            is PdfAnnotation.StickyNote -> {
                val note = PDAnnotationText()
                val x = annot.position.x * pageWidth
                val y = (1f - annot.position.y) * pageHeight
                note.rectangle = PDRectangle(x, y, 24f, 24f)
                note.color = pdColor
                note.contents = annot.noteText
                note.annotationName = annot.author
                note.constructAppearances()
                note
            }
            is PdfAnnotation.FreeTextBox -> {
                val freeText = PDAnnotationMarkup()
                freeText.cosObject.setName(COSName.SUBTYPE, PDAnnotationMarkup.SUB_TYPE_FREETEXT)
                freeText.rectangle = createPdRectangle(annot.bounds, pageWidth, pageHeight)
                freeText.color = pdColor
                freeText.contents = annot.text
                freeText.defaultAppearance = "/Helv ${annot.fontSize.toInt()} Tf $r $g $b rg"
                freeText.constructAppearances()
                freeText
            }
            is PdfAnnotation.GeometricShape -> {
                val x1 = annot.startPoint.x * pageWidth
                val y1 = (1f - annot.startPoint.y) * pageHeight
                val x2 = annot.endPoint.x * pageWidth
                val y2 = (1f - annot.endPoint.y) * pageHeight
                val left = minOf(x1, x2)
                val bottom = minOf(y1, y2)
                val w = kotlin.math.abs(x2 - x1).coerceAtLeast(4f)
                val h = kotlin.math.abs(y2 - y1).coerceAtLeast(4f)

                when (annot.type) {
                    AnnotationType.RECTANGLE -> {
                        val sq = PDAnnotationSquareCircle(PDAnnotationSquareCircle.SUB_TYPE_SQUARE)
                        sq.rectangle = PDRectangle(left, bottom, w, h)
                        sq.color = pdColor
                        sq.constructAppearances()
                        sq
                    }
                    AnnotationType.ELLIPSE -> {
                        val circle = PDAnnotationSquareCircle(PDAnnotationSquareCircle.SUB_TYPE_CIRCLE)
                        circle.rectangle = PDRectangle(left, bottom, w, h)
                        circle.color = pdColor
                        circle.constructAppearances()
                        circle
                    }
                    AnnotationType.LINE, AnnotationType.ARROW -> {
                        val lineAnnot = PDAnnotationLine()
                        lineAnnot.rectangle = PDRectangle(left, bottom, w, h)
                        lineAnnot.color = pdColor
                        lineAnnot.line = floatArrayOf(x1, y1, x2, y2)
                        lineAnnot.constructAppearances()
                        lineAnnot
                    }
                    else -> null
                }
            }
        }
    }

    private fun createPdRectangle(norm: NormalizedRect, pageWidth: Float, pageHeight: Float): PDRectangle {
        val x = norm.left * pageWidth
        val y = (1f - norm.bottom) * pageHeight
        val w = norm.width * pageWidth
        val h = norm.height * pageHeight
        return PDRectangle(x, y, w.coerceAtLeast(1f), h.coerceAtLeast(1f))
    }

    private fun extractColorArgb(color: PDColor?): Int? {
        if (color == null) return null
        return try {
            val components = color.components
            if (components.size >= 3) {
                Color.rgb(
                    (components[0] * 255).toInt().coerceIn(0, 255),
                    (components[1] * 255).toInt().coerceIn(0, 255),
                    (components[2] * 255).toInt().coerceIn(0, 255)
                )
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun getRgbComponents(colorArgb: Int): Triple<Float, Float, Float> {
        val r = ((colorArgb shr 16) and 0xFF) / 255f
        val g = ((colorArgb shr 8) and 0xFF) / 255f
        val b = (colorArgb and 0xFF) / 255f
        return Triple(r, g, b)
    }

    private fun openPdDocument(pdfFile: File?, uri: Uri?): PDDocument? {
        return PdfBoxMemoryUtil.loadDocument(context, pdfFile, uri)
    }
}
