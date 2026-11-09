package com.example.ai

import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Log
import com.example.pdf.BengaliFontOption
import com.example.pdf.BengaliTextNormalizer
import com.example.scan.ScanOcrEngine
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.File
import kotlin.coroutines.coroutineContext

private const val TAG = "PdfAiAssistantEngine"

const val AI_CONSENT_DIALOG_MESSAGE =
    "This sends the text of the pages you choose to a cloud AI service."

const val AI_DISCLAIMER_TEXT =
    "AI generated, may contain mistakes"

enum class AiOutputLanguage(val displayName: String, val promptName: String) {
    ENGLISH("English", "English"),
    BENGALI("Bengali (বাংলা)", "Bengali (বাংলা)"),
    HINDI("Hindi (हिन्दी)", "Hindi"),
    SPANISH("Spanish (Español)", "Spanish"),
    FRENCH("French (Français)", "French"),
    ARABIC("Arabic (العربية)", "Arabic")
}

enum class AiExplainMode(val label: String, val instruction: String) {
    EXPLAIN_SIMPLY(
        "Explain Simply",
        "Explain the selected text in clear, simple everyday language so anyone can understand it."
    ),
    EXPLAIN_DETAILED(
        "Detailed Breakdown",
        "Provide a thorough, structured explanation of the selected text, including key terms, context, and implications."
    ),
    REWRITE_CLEARLY(
        "Rewrite Clearly",
        "Rewrite the selected text to improve clarity, readability, and flow while preserving its exact meaning."
    ),
    REWRITE_FORMAL(
        "Make Formal",
        "Rewrite the selected text in a polished, professional, formal tone suitable for official documents."
    ),
    REWRITE_BULLETS(
        "Key Bullet Points",
        "Rewrite and condense the selected text into concise, actionable bullet points."
    )
}

enum class AiToolTab(val label: String) {
    SUMMARY("Summary"),
    CHAT("Chat with PDF"),
    TRANSLATE("Translate"),
    EXPLAIN("Explain / Rewrite")
}

data class PdfTextChunk(
    val chunkId: String,
    val pageIndex: Int, // 0-based
    val pageNumber: Int, // 1-based
    val text: String
)

data class AiSummaryResult(
    val shortSummary: String,
    val keyPoints: List<String>,
    val pageRangeDescription: String,
    val outputLanguage: AiOutputLanguage,
    val referencedPages: List<Int>,
    val disclaimer: String = AI_DISCLAIMER_TEXT
)

data class AiChatMessage(
    val id: String = "msg_${System.currentTimeMillis()}_${(100..999).random()}",
    val isUser: Boolean,
    val text: String,
    val referencedPages: List<Int> = emptyList(), // 1-based page numbers
    val disclaimer: String = AI_DISCLAIMER_TEXT
)

data class TranslationPair(
    val label: String,
    val pageNumber: Int?,
    val originalText: String,
    val translatedText: String
)

data class AiTranslationResult(
    val sourceLanguage: String,
    val targetLanguage: AiOutputLanguage,
    val pairs: List<TranslationPair>,
    val disclaimer: String = AI_DISCLAIMER_TEXT
)

data class AiExplainResult(
    val selectedText: String,
    val mode: AiExplainMode,
    val outputLanguage: AiOutputLanguage,
    val responseText: String,
    val pageNumber: Int?,
    val disclaimer: String = AI_DISCLAIMER_TEXT
)

class PdfAiAssistantEngine(private val context: Context) {

    init {
        try {
            if (!PDFBoxResourceLoader.isReady()) {
                PDFBoxResourceLoader.init(context.applicationContext)
            }
        } catch (_: Exception) {
        }
        ensureFirebaseReady()
    }

    private fun ensureFirebaseReady() {
        try {
            if (com.google.firebase.FirebaseApp.getApps(context).isEmpty()) {
                configureAppCheck(context.applicationContext, null)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "ensureFirebaseReady encountered an issue", t)
        }
    }

    private val systemPromptText =
        "You are an accurate, helpful document assistant inside PDF ALL IN ONE Pro+Ai. " +
            "Always base your answers strictly on the provided document excerpts, page images, or selected text. " +
            "When referencing specific pages from the document, cite them clearly as [Page N] where N is the 1-based page number."

    // Structured JSON schema for reliable document summarization via Firebase AI Logic
    private val summaryJsonSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "shortSummary" to Schema.string(
                    description = "A clear, well-written 3-5 sentence executive summary of the document pages in the requested output language, citing [Page N] where appropriate."
                ),
                "keyPoints" to Schema.array(
                    items = Schema.string(
                        description = "A concise, informative takeaway point in the requested output language with [Page N] reference."
                    ),
                    description = "List of 4 to 7 key points summarizing the most important facts, findings, or sections."
                ),
                "referencedPages" to Schema.array(
                    items = Schema.integer(
                        description = "1-based page number referenced in the summary."
                    ),
                    description = "Sorted list of 1-based page numbers cited."
                )
            ),
            optionalProperties = listOf("referencedPages")
        )
    }

    // Lazily initialized structured-output Gemini model for Summarization
    private val summaryGenerativeModel by lazy {
        ensureFirebaseReady()
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                temperature = 0.25f
                responseMimeType = "application/json"
                responseSchema = summaryJsonSchema
            },
            systemInstruction = content {
                text(systemPromptText)
            }
        )
    }

    private val liteSummaryGenerativeModel by lazy {
        ensureFirebaseReady()
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                temperature = 0.25f
                responseMimeType = "application/json"
                responseSchema = summaryJsonSchema
            },
            systemInstruction = content {
                text(systemPromptText)
            }
        )
    }

    // Lazily initialized general Gemini model via Firebase AI Logic (no client API key)
    private val generativeModel by lazy {
        ensureFirebaseReady()
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                temperature = 0.3f
            },
            systemInstruction = content {
                text(systemPromptText)
            }
        )
    }

    private val liteGenerativeModel by lazy {
        ensureFirebaseReady()
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                temperature = 0.3f
            },
            systemInstruction = content {
                text(systemPromptText)
            }
        )
    }

    private suspend fun generateTextWithFallback(prompt: String): String {
        ensureFirebaseReady()
        var primaryError: Exception? = null
        val primaryText = try {
            withTimeout(40_000L) {
                generativeModel.generateContent(prompt).text?.trim().orEmpty()
            }
        } catch (e: TimeoutCancellationException) {
            coroutineContext.ensureActive()
            Log.w(TAG, "Primary model timed out after 40s, falling back to lite model", e)
            primaryError = e
            ""
        } catch (e: CancellationException) {
            throw e
        } catch (primaryEx: Exception) {
            Log.w(TAG, "Primary model gemini-3.8-flash failed, retrying with gemini-3.5-flash-lite", primaryEx)
            primaryError = primaryEx
            ""
        }

        if (primaryText.isNotBlank()) {
            return primaryText
        }

        return try {
            withTimeout(35_000L) {
                liteGenerativeModel.generateContent(prompt).text?.trim().orEmpty()
            }
        } catch (e: TimeoutCancellationException) {
            coroutineContext.ensureActive()
            Log.e(TAG, "Fallback model gemini-3.5-flash-lite timed out", e)
            throw (primaryError ?: e)
        } catch (e: CancellationException) {
            throw e
        } catch (fallbackEx: Exception) {
            Log.e(TAG, "Fallback model gemini-3.5-flash-lite also failed", fallbackEx)
            throw (primaryError ?: fallbackEx)
        }
    }

    fun isNetworkAvailable(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val network = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Extracts text for the requested 0-based page indices from the PDF file or URI.
     * - Automatically normalizes Bengali Unicode order (broken matras ি, ে, ৈ & nuktas) and legacy Bijoy/ANSI fonts.
     * - Automatically samples up to 24 evenly distributed pages when summarizing very large documents so extraction stays fast.
     * - Automatically uses existing OCR results or runs ML Kit OCR fallback when extracted text is sparse.
     */
    suspend fun extractPagesText(
        cachedFile: File?,
        uri: Uri?,
        pageIndices: List<Int>,
        existingOcrText: Map<Int, String> = emptyMap(),
        autoOcrScannedPages: Boolean = true,
        getPageBitmap: (suspend (Int, Int) -> Bitmap?)? = null,
        onStatusUpdate: ((String) -> Unit)? = null
    ): Map<Int, String> = withContext(Dispatchers.IO) {
        val result = linkedMapOf<Int, String>()
        val pagesNeedingOcr = mutableListOf<Int>()
        val sampledPageIndices = samplePageIndicesForExtraction(pageIndices, maxPages = 24)
        var pdDoc: PDDocument? = null
        try {
            pdDoc = com.example.pdf.PdfBoxMemoryUtil.loadDocument(context, cachedFile, uri)
            if (pdDoc != null) {
                val total = pdDoc.numberOfPages
                val stripper = PDFTextStripper().apply {
                    sortByPosition = true
                }
                for (pageIndex in sampledPageIndices) {
                    coroutineContext.ensureActive()
                    if (pageIndex in 0 until total) {
                        val pdPage = runCatching { pdDoc.getPage(pageIndex) }.getOrNull()
                        val hasBijoyFont = pdPage?.let {
                            BengaliTextNormalizer.hasLegacyBijoyFontsOnPage(it)
                        } ?: false

                        stripper.startPage = pageIndex + 1
                        stripper.endPage = pageIndex + 1
                        val rawPageText = runCatching { stripper.getText(pdDoc).trim() }.getOrDefault("")
                        val normalizedText = if (rawPageText.isNotBlank()) {
                            cleanReadablePdfText(
                                BengaliTextNormalizer.normalizeExtractedPdfText(
                                    rawText = rawPageText,
                                    pageHasBijoyFont = hasBijoyFont,
                                    fixBrokenMatrasAndNuktas = true,
                                    convertBijoyAnsiToUnicode = true
                                )
                            )
                        } else {
                            ""
                        }

                        if (countReadableCharacters(normalizedText) >= 15) {
                            result[pageIndex] = normalizedText
                        } else {
                            val cachedOcr = existingOcrText[pageIndex]?.trim().orEmpty()
                            if (cachedOcr.isNotBlank()) {
                                result[pageIndex] = BengaliTextNormalizer.normalizeExtractedPdfText(
                                    rawText = cachedOcr,
                                    fixBrokenMatrasAndNuktas = true,
                                    convertBijoyAnsiToUnicode = true
                                )
                            } else if (countReadableCharacters(normalizedText) >= 4) {
                                // Keep short embedded text as fallback if OCR is disabled or produces nothing
                                result[pageIndex] = normalizedText
                                if (autoOcrScannedPages) {
                                    pagesNeedingOcr.add(pageIndex)
                                }
                            } else if (autoOcrScannedPages) {
                                pagesNeedingOcr.add(pageIndex)
                            }
                        }
                    }
                }
            } else {
                // Document could not be opened by PDFBox; check existingOcrText or try OCR
                for (pageIndex in sampledPageIndices) {
                    val cachedOcr = existingOcrText[pageIndex]?.trim().orEmpty()
                    if (cachedOcr.isNotBlank()) {
                        result[pageIndex] = cachedOcr
                    } else if (autoOcrScannedPages) {
                        pagesNeedingOcr.add(pageIndex)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (oom: OutOfMemoryError) {
            System.gc()
        } catch (t: Throwable) {
            Log.w(TAG, "PDFBox text extraction encountered an issue", t)
        } finally {
            try {
                pdDoc?.close()
            } catch (_: Throwable) {
            }
        }

        // Only run ML Kit OCR fallback if the extracted text across the document is sparse (< 80 chars)
        // or the user explicitly selected a small range (<= 3 pages), capping OCR to 4 pages with an 8s timeout per page.
        val totalExtractedReadableChars = result.values.sumOf { countReadableCharacters(it) }
        val shouldRunOcr = autoOcrScannedPages &&
            pagesNeedingOcr.isNotEmpty() &&
            (totalExtractedReadableChars < 80 || sampledPageIndices.size <= 3)

        if (shouldRunOcr) {
            val ocrEngine = ScanOcrEngine(context)
            val ocrTargets = pagesNeedingOcr.take(4)
            for ((idx, pageIndex) in ocrTargets.withIndex()) {
                coroutineContext.ensureActive()
                onStatusUpdate?.invoke("Extracting text via OCR (page ${pageIndex + 1}, ${idx + 1}/${ocrTargets.size})...")
                val ocrResult = kotlinx.coroutines.withTimeoutOrNull(8_000L) {
                    runCatching {
                        val fromCallerBitmap = getPageBitmap?.invoke(pageIndex, 1280)?.let { bmp ->
                            ocrEngine.recognizeTextFromBitmap(bmp)
                        }
                        if (fromCallerBitmap != null && fromCallerBitmap.fullText.isNotBlank()) {
                            fromCallerBitmap
                        } else {
                            ocrEngine.recognizeTextFromPdfPage(
                                pdfFile = cachedFile,
                                uri = uri,
                                pageIndex = pageIndex,
                                targetWidth = 1280
                            )
                        }
                    }.getOrNull()
                }
                val recognized = ocrResult?.fullText?.trim().orEmpty()
                if (recognized.isNotBlank()) {
                    result[pageIndex] = BengaliTextNormalizer.normalizeExtractedPdfText(
                        rawText = recognized,
                        fixBrokenMatrasAndNuktas = true,
                        convertBijoyAnsiToUnicode = true
                    )
                }
            }
        }

        result.toSortedMap()
    }

    /**
     * Generates a summary for the selected pages in the chosen output language.
     * Uses Firebase AI Logic structured JSON output (Schema.obj) with multi-model fallback,
     * multimodal page-image fallback for scanned/custom-font PDFs, and robust Markdown/multi-language parsing.
     */
    suspend fun summarizePages(
        pagesText: Map<Int, String>,
        pageRangeLabel: String,
        outputLanguage: AiOutputLanguage,
        getPageBitmap: (suspend (Int, Int) -> Bitmap?)? = null,
        requestedPageIndices: List<Int> = emptyList()
    ): AiSummaryResult = withContext(Dispatchers.IO) {
        ensureFirebaseReady()
        val chunks = splitDocumentTextIntoChunks(pagesText, maxChunkChars = 1400, overlapChars = 140)
        val totalReadableChars = chunks.sumOf { countReadableCharacters(it.text) }

        // If PDFBox + ML Kit OCR yielded little or no readable text (e.g. scanned Bengali/Arabic/complex PDF
        // or custom font glyph encoding), use Gemini multimodal vision on rendered page bitmaps!
        if ((chunks.isEmpty() || totalReadableChars < 30) && getPageBitmap != null) {
            val targetIndices = requestedPageIndices.ifEmpty { pagesText.keys.toList() }.ifEmpty { listOf(0) }
            val visualPages = samplePageIndicesForExtraction(targetIndices, maxPages = 4)
            val pageBitmaps = mutableListOf<Pair<Int, Bitmap>>()
            for (pageIdx in visualPages) {
                coroutineContext.ensureActive()
                val bmp = runCatching { getPageBitmap.invoke(pageIdx, 1024) }.getOrNull()
                if (bmp != null) {
                    pageBitmaps.add((pageIdx + 1) to bmp)
                }
            }
            if (pageBitmaps.isNotEmpty()) {
                val visionResult = runCatching {
                    summarizeFromPageBitmaps(
                        pageBitmaps = pageBitmaps,
                        pageRangeLabel = pageRangeLabel,
                        outputLanguage = outputLanguage
                    )
                }.getOrNull()
                if (visionResult != null && visionResult.shortSummary.isNotBlank()) {
                    return@withContext visionResult
                }
            }
        }

        if (chunks.isEmpty()) {
            throw IllegalStateException("No readable text was found on the selected pages. Please ensure the pages contain visible content.")
        }

        // Fit within safe prompt budget (~14000 chars) by proportionally sampling across all pages
        val sampledChunks = sampleChunksWithinBudget(chunks, maxTotalChars = 14_000)
        val contextBlock = sampledChunks.joinToString("\n\n") { chunk ->
            "[Page ${chunk.pageNumber}]\n${chunk.text}"
        }

        val structuredPrompt = buildString {
            appendLine("Summarize the following PDF document pages ($pageRangeLabel).")
            appendLine("Write the entire summary and all key points in ${outputLanguage.promptName}.")
            appendLine("Requirements:")
            appendLine("1. 'shortSummary': A clear, cohesive 3 to 5 sentence executive overview of the document's core subject, purpose, and conclusions, citing [Page N] where relevant.")
            appendLine("2. 'keyPoints': 4 to 7 specific, high-value bullet points covering key facts, figures, or sections, each citing [Page N] (do not include leading bullet symbols like '-' or '•').")
            appendLine("3. 'referencedPages': Array of 1-based page numbers cited in the summary.")
            appendLine()
            appendLine("DOCUMENT CONTENT:")
            appendLine(contextBlock)
        }

        val fallbackTextPrompt = buildString {
            appendLine("Summarize the following PDF document pages ($pageRangeLabel).")
            appendLine("Respond entirely in ${outputLanguage.promptName}, but keep the exact ASCII section labels SHORT_SUMMARY: and KEY_POINTS: on their own lines.")
            appendLine("SHORT_SUMMARY:")
            appendLine("(Write a clear 3-5 sentence executive overview here, citing [Page N] where appropriate.)")
            appendLine("KEY_POINTS:")
            appendLine("- (Bullet point 1 with [Page N] reference)")
            appendLine("- (Bullet point 2 with [Page N] reference)")
            appendLine("- (Bullet point 3 with [Page N] reference)")
            appendLine("- (Bullet point 4 with [Page N] reference)")
            appendLine()
            appendLine("DOCUMENT CONTENT:")
            appendLine(contextBlock)
        }

        val fallbackPageNumbers = pagesText.keys.map { it + 1 }.sorted()
            .ifEmpty { requestedPageIndices.map { it + 1 }.sorted() }
            .ifEmpty { listOf(1) }

        var lastError: Exception? = null

        // 1. Try primary structured model (gemini-3.8-flash)
        val primaryRaw = try {
            withTimeout(35_000L) {
                summaryGenerativeModel.generateContent(structuredPrompt).text?.trim().orEmpty()
            }
        } catch (e: TimeoutCancellationException) {
            coroutineContext.ensureActive()
            Log.w(TAG, "Primary structured summary model timed out after 35s, trying lite model", e)
            lastError = e
            ""
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Primary structured summary model failed, trying lite model", e)
            lastError = e
            ""
        }

        if (primaryRaw.isNotBlank()) {
            val parsed = parseSummaryResponse(
                rawResponse = primaryRaw,
                pageRangeDescription = pageRangeLabel,
                outputLanguage = outputLanguage,
                fallbackPages = fallbackPageNumbers
            )
            if (parsed.shortSummary.isNotBlank()) {
                return@withContext parsed
            }
        }

        // 2. Try lite structured model (gemini-3.5-flash-lite)
        val liteRaw = try {
            withTimeout(30_000L) {
                liteSummaryGenerativeModel.generateContent(structuredPrompt).text?.trim().orEmpty()
            }
        } catch (e: TimeoutCancellationException) {
            coroutineContext.ensureActive()
            Log.w(TAG, "Lite structured summary model timed out after 30s, trying plain-text fallback", e)
            lastError = e
            ""
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Lite structured summary model failed, trying plain-text fallback", e)
            lastError = e
            ""
        }

        if (liteRaw.isNotBlank()) {
            val parsed = parseSummaryResponse(
                rawResponse = liteRaw,
                pageRangeDescription = pageRangeLabel,
                outputLanguage = outputLanguage,
                fallbackPages = fallbackPageNumbers
            )
            if (parsed.shortSummary.isNotBlank()) {
                return@withContext parsed
            }
        }

        // 3. Final fallback: unstructured plain-text prompt with model fallback
        val fallbackRaw = try {
            generateTextWithFallback(fallbackTextPrompt)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw (lastError ?: e)
        }

        if (fallbackRaw.isBlank()) {
            throw IllegalStateException("AI service returned an empty summary. Please try again.")
        }

        parseSummaryResponse(
            rawResponse = fallbackRaw,
            pageRangeDescription = pageRangeLabel,
            outputLanguage = outputLanguage,
            fallbackPages = fallbackPageNumbers
        )
    }

    private suspend fun summarizeFromPageBitmaps(
        pageBitmaps: List<Pair<Int, Bitmap>>,
        pageRangeLabel: String,
        outputLanguage: AiOutputLanguage
    ): AiSummaryResult {
        val pageNums = pageBitmaps.map { it.first }
        val promptText = buildString {
            appendLine("Read and summarize the attached PDF document page images ($pageRangeLabel; Pages: ${pageNums.joinToString(", ")}).")
            appendLine("Write the entire summary and all key points in ${outputLanguage.promptName}.")
            appendLine("Requirements:")
            appendLine("1. 'shortSummary': A clear 3 to 5 sentence executive overview of the document's core content, citing [Page N].")
            appendLine("2. 'keyPoints': 4 to 7 specific bullet points covering key facts, figures, or sections, each citing [Page N].")
            appendLine("3. 'referencedPages': Array of 1-based page numbers cited in the summary.")
        }

        val multimodalContent = content {
            for ((pageNum, bitmap) in pageBitmaps) {
                text("Page $pageNum:")
                image(bitmap)
            }
            text(promptText)
        }

        val rawResponse = try {
            withTimeout(45_000L) {
                summaryGenerativeModel.generateContent(multimodalContent).text?.trim().orEmpty()
            }
        } catch (e: TimeoutCancellationException) {
            coroutineContext.ensureActive()
            withTimeout(35_000L) {
                liteSummaryGenerativeModel.generateContent(multimodalContent).text?.trim().orEmpty()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            withTimeout(35_000L) {
                liteSummaryGenerativeModel.generateContent(multimodalContent).text?.trim().orEmpty()
            }
        }

        if (rawResponse.isBlank()) {
            throw IllegalStateException("AI service returned an empty summary for the page images.")
        }

        return parseSummaryResponse(
            rawResponse = rawResponse,
            pageRangeDescription = pageRangeLabel,
            outputLanguage = outputLanguage,
            fallbackPages = pageNums
        )
    }

    /**
     * Answers a user question about the PDF by selecting only the most relevant chunks
     * and citing page references [Page N] that jump to the page when tapped.
     */
    suspend fun chatWithDocument(
        allChunks: List<PdfTextChunk>,
        question: String,
        recentHistory: List<AiChatMessage>,
        outputLanguage: AiOutputLanguage
    ): AiChatMessage = withContext(Dispatchers.IO) {
        if (allChunks.isEmpty()) {
            throw IllegalStateException("This PDF has no extractable text to chat with. Run OCR first for scanned documents.")
        }

        val relevantChunks = selectRelevantChunks(
            chunks = allChunks,
            query = question,
            maxChunks = 7,
            maxTotalChars = 9000
        )

        val chunksContext = relevantChunks.joinToString("\n\n") { chunk ->
            "[Page ${chunk.pageNumber}]\n${chunk.text}"
        }

        val historyText = recentHistory.takeLast(6).joinToString("\n") { msg ->
            val role = if (msg.isUser) "User" else "Assistant"
            "$role: ${msg.text.take(400)}"
        }

        val prompt = buildString {
            appendLine("Answer the user's question based on the relevant PDF excerpts below.")
            appendLine("Respond in ${outputLanguage.promptName} unless the user explicitly asks for another language.")
            appendLine("IMPORTANT: Always cite the page numbers you used in the exact format [Page N] (for example [Page 1], [Page 3]).")
            if (historyText.isNotBlank()) {
                appendLine()
                appendLine("RECENT CONVERSATION:")
                appendLine(historyText)
            }
            appendLine()
            appendLine("RELEVANT DOCUMENT CHUNKS:")
            appendLine(chunksContext)
            appendLine()
            appendLine("USER QUESTION: $question")
        }

        val answerText = generateTextWithFallback(prompt)

        if (answerText.isBlank()) {
            throw IllegalStateException("AI service returned an empty answer. Please try again.")
        }

        val citedPages = parsePageCitations(answerText).ifEmpty {
            relevantChunks.map { it.pageNumber }.distinct().sorted().take(3)
        }

        AiChatMessage(
            isUser = false,
            text = answerText,
            referencedPages = citedPages,
            disclaimer = AI_DISCLAIMER_TEXT
        )
    }

    /**
     * Translates selected text or a page range into the target language, returning side-by-side pairs.
     */
    suspend fun translateContent(
        sourceLanguage: String,
        targetLanguage: AiOutputLanguage,
        selectedText: String?,
        pagesText: Map<Int, String>
    ): AiTranslationResult = withContext(Dispatchers.IO) {
        val pairs = mutableListOf<TranslationPair>()

        if (!selectedText.isNullOrBlank()) {
            val clipped = selectedText.trim().take(5000)
            val prompt = buildString {
                appendLine("Translate the following text from $sourceLanguage into ${targetLanguage.promptName}.")
                appendLine("Preserve paragraph breaks and formatting. Output ONLY the translated text without commentary.")
                appendLine()
                appendLine("TEXT TO TRANSLATE:")
                appendLine(clipped)
            }
            val translated = generateTextWithFallback(prompt)
            pairs.add(
                TranslationPair(
                    label = "Selected Text",
                    pageNumber = null,
                    originalText = clipped,
                    translatedText = translated.ifBlank { "(Translation unavailable)" }
                )
            )
        } else {
            if (pagesText.isEmpty()) {
                throw IllegalStateException("No selectable text found on the chosen pages to translate.")
            }
            // Translate page by page (up to 8 pages per request with page markers)
            val entries = pagesText.entries.sortedBy { it.key }.take(8)
            val combinedSource = entries.joinToString("\n\n===PAGE_BREAK===\n\n") { (pageIdx, txt) ->
                "[[PAGE_${pageIdx + 1}]]\n${txt.take(1800)}"
            }

            val prompt = buildString {
                appendLine("Translate each page section below from $sourceLanguage into ${targetLanguage.promptName}.")
                appendLine("Keep the exact marker [[PAGE_N]] at the start of each page's translation, and separate pages with ===PAGE_BREAK===.")
                appendLine("Output ONLY the markers and translated text.")
                appendLine()
                appendLine(combinedSource)
            }

            val rawTranslated = generateTextWithFallback(prompt)

            val parsedByPage = parseMultiPageTranslation(rawTranslated)
            for ((pageIdx, originalPageText) in entries) {
                val pageNum = pageIdx + 1
                val translatedForPage = parsedByPage[pageNum]
                    ?: if (entries.size == 1) rawTranslated.replace(Regex("\\[\\[PAGE_\\d+]]"), "").trim()
                    else rawTranslated
                pairs.add(
                    TranslationPair(
                        label = "Page $pageNum",
                        pageNumber = pageNum,
                        originalText = originalPageText.take(1800),
                        translatedText = translatedForPage.ifBlank { "(Translation unavailable)" }
                    )
                )
            }
        }

        AiTranslationResult(
            sourceLanguage = sourceLanguage,
            targetLanguage = targetLanguage,
            pairs = pairs,
            disclaimer = AI_DISCLAIMER_TEXT
        )
    }

    /**
     * Explains or rewrites user-selected text from the viewer.
     */
    suspend fun explainOrRewriteSelection(
        selectedText: String,
        mode: AiExplainMode,
        outputLanguage: AiOutputLanguage,
        pageNumber: Int?
    ): AiExplainResult = withContext(Dispatchers.IO) {
        val cleanSelection = selectedText.trim().take(4500)
        if (cleanSelection.isBlank()) {
            throw IllegalArgumentException("Please select or enter text to explain or rewrite.")
        }

        val prompt = buildString {
            appendLine(mode.instruction)
            appendLine("Respond in ${outputLanguage.promptName}.")
            if (pageNumber != null) {
                appendLine("Context: Selected from Page $pageNumber of the PDF document.")
            }
            appendLine()
            appendLine("SELECTED TEXT:")
            appendLine(cleanSelection)
        }

        val response = generateTextWithFallback(prompt)

        if (response.isBlank()) {
            throw IllegalStateException("AI service returned an empty response. Please try again.")
        }

        AiExplainResult(
            selectedText = cleanSelection,
            mode = mode,
            outputLanguage = outputLanguage,
            responseText = response,
            pageNumber = pageNumber,
            disclaimer = AI_DISCLAIMER_TEXT
        )
    }

    /**
     * Exports a summary result as a formatted .txt file.
     */
    suspend fun exportSummaryAsTxt(
        result: AiSummaryResult,
        outputFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val content = buildString {
                appendLine("====================================================")
                appendLine("PDF ALL IN ONE Pro+Ai — Executive Summary")
                appendLine("Scope: ${result.pageRangeDescription} | Language: ${result.outputLanguage.displayName}")
                appendLine("Disclaimer: ${result.disclaimer}")
                appendLine("====================================================")
                appendLine()
                appendLine("SHORT SUMMARY:")
                appendLine(result.shortSummary)
                appendLine()
                if (result.keyPoints.isNotEmpty()) {
                    appendLine("KEY POINTS:")
                    result.keyPoints.forEach { bullet ->
                        appendLine("• $bullet")
                    }
                    appendLine()
                }
                if (result.referencedPages.isNotEmpty()) {
                    appendLine("Referenced Pages: ${result.referencedPages.joinToString(", ") { "Page $it" }}")
                }
            }
            outputFile.writeText(content, Charsets.UTF_8)
            outputFile.exists() && outputFile.length() > 0L
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Exports an AI summary result as a clean PDF document supporting English and all 26 popular Bengali fonts.
     */
    @android.annotation.SuppressLint("ResourceType")
    suspend fun exportSummaryAsPdf(
        result: AiSummaryResult,
        outputFile: File,
        selectedBengaliFont: BengaliFontOption = BengaliFontOption.KALPURUSH
    ): Boolean = withContext(Dispatchers.IO) {
        var doc: PDDocument? = null
        try {
            doc = PDDocument()
            val latinFont = runCatching {
                context.resources.openRawResource(com.example.R.font.noto_sans).use {
                    PDType0Font.load(doc, it, true)
                }
            }.getOrNull()

            val bengaliFont = selectedBengaliFont.loadPdfBoxFont(context, doc)

            val allBodyLines = mutableListOf<String>()
            allBodyLines.add("Executive Summary (${result.pageRangeDescription})")
            allBodyLines.add("")
            allBodyLines.addAll(wrapTextToLines(result.shortSummary, maxCharsPerLine = 74))
            allBodyLines.add("")
            if (result.keyPoints.isNotEmpty()) {
                allBodyLines.add("Key Takeaways:")
                for (pt in result.keyPoints) {
                    val wrappedBullet = wrapTextToLines("• $pt", maxCharsPerLine = 72)
                    allBodyLines.addAll(wrappedBullet)
                }
                allBodyLines.add("")
            }
            if (result.referencedPages.isNotEmpty()) {
                allBodyLines.add("Referenced Pages: ${result.referencedPages.joinToString(", ") { "Page $it" }}")
            }

            val linesPerPage = 42
            val chunks = allBodyLines.chunked(linesPerPage).ifEmpty { listOf(emptyList()) }

            for ((pageIdx, pageLines) in chunks.withIndex()) {
                val page = PDPage(PDRectangle.A4)
                doc.addPage(page)
                val pageW = page.mediaBox.width
                val pageH = page.mediaBox.height
                val margin = 40f

                PDPageContentStream(doc, page).use { cs ->
                    cs.setNonStrokingColor(240, 244, 250)
                    cs.addRect(margin, pageH - 72f, pageW - margin * 2, 42f)
                    cs.fill()

                    cs.setNonStrokingColor(25, 45, 85)
                    drawSafePdfText(
                        cs = cs,
                        text = "AI Document Summary | ${result.pageRangeDescription} (Page ${pageIdx + 1}/${chunks.size})",
                        x = margin + 10f,
                        y = pageH - 48f,
                        fontSize = 11f,
                        latinFont = latinFont,
                        bengaliFont = bengaliFont
                    )
                    drawSafePdfText(
                        cs = cs,
                        text = result.disclaimer,
                        x = margin + 10f,
                        y = pageH - 63f,
                        fontSize = 8.5f,
                        latinFont = latinFont,
                        bengaliFont = bengaliFont
                    )

                    cs.setNonStrokingColor(20, 20, 20)
                    var curY = pageH - 98f
                    for (line in pageLines) {
                        if (curY < margin + 24f) break
                        if (line.isNotBlank()) {
                            drawSafePdfText(
                                cs = cs,
                                text = line,
                                x = margin,
                                y = curY,
                                fontSize = 10.5f,
                                latinFont = latinFont,
                                bengaliFont = bengaliFont
                            )
                        }
                        curY -= 15.5f
                    }
                }
            }

            doc.save(outputFile)
            outputFile.exists() && outputFile.length() > 0L
        } catch (_: Exception) {
            false
        } finally {
            try {
                doc?.close()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Exports a side-by-side translation result as a formatted .txt file.
     */
    suspend fun exportTranslationAsTxt(
        result: AiTranslationResult,
        outputFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val content = buildString {
                appendLine("====================================================")
                appendLine("PDF ALL IN ONE Pro+Ai — Side-by-Side Translation")
                appendLine("Source: ${result.sourceLanguage} -> Target: ${result.targetLanguage.displayName}")
                appendLine("Disclaimer: ${result.disclaimer}")
                appendLine("====================================================")
                appendLine()
                for (pair in result.pairs) {
                    appendLine("--- ${pair.label} ---")
                    appendLine("[Original (${result.sourceLanguage})]:")
                    appendLine(pair.originalText)
                    appendLine()
                    appendLine("[Translated (${result.targetLanguage.displayName})]:")
                    appendLine(pair.translatedText)
                    appendLine()
                    appendLine("----------------------------------------------------")
                    appendLine()
                }
            }
            outputFile.writeText(content, Charsets.UTF_8)
            outputFile.exists() && outputFile.length() > 0L
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Exports a side-by-side translation result as a new PDF with two columns:
     * Left column = Original text, Right column = Translated text (supports English and Bengali fonts).
     */
    @android.annotation.SuppressLint("ResourceType")
    suspend fun exportTranslationAsPdf(
        result: AiTranslationResult,
        outputFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        var doc: PDDocument? = null
        try {
            doc = PDDocument()
            val latinFont = runCatching {
                context.resources.openRawResource(com.example.R.font.noto_sans).use {
                    PDType0Font.load(doc, it, true)
                }
            }.getOrNull()

            val bengaliFont = runCatching {
                context.resources.openRawResource(com.example.R.font.noto_sans_bengali).use {
                    PDType0Font.load(doc, it, true)
                }
            }.getOrNull()

            for (pair in result.pairs) {
                val page = PDPage(PDRectangle.A4)
                doc.addPage(page)
                val pageW = page.mediaBox.width
                val pageH = page.mediaBox.height
                val margin = 36f
                val gutter = 20f
                val colWidth = (pageW - margin * 2 - gutter) / 2f
                val leftColX = margin
                val rightColX = margin + colWidth + gutter

                PDPageContentStream(doc, page).use { cs ->
                    // Header bar
                    cs.setNonStrokingColor(240, 244, 250)
                    cs.addRect(margin, pageH - 72f, pageW - margin * 2, 42f)
                    cs.fill()

                    cs.setNonStrokingColor(25, 45, 85)
                    drawSafePdfText(
                        cs = cs,
                        text = "${pair.label} | ${result.sourceLanguage} -> ${result.targetLanguage.name}",
                        x = margin + 10f,
                        y = pageH - 48f,
                        fontSize = 11f,
                        latinFont = latinFont,
                        bengaliFont = bengaliFont
                    )

                    drawSafePdfText(
                        cs = cs,
                        text = result.disclaimer,
                        x = margin + 10f,
                        y = pageH - 63f,
                        fontSize = 8.5f,
                        latinFont = latinFont,
                        bengaliFont = bengaliFont
                    )

                    // Column Divider Line
                    cs.setStrokingColor(200, 205, 215)
                    cs.setLineWidth(1f)
                    cs.moveTo(margin + colWidth + gutter / 2f, margin + 20f)
                    cs.lineTo(margin + colWidth + gutter / 2f, pageH - 84f)
                    cs.stroke()

                    // Column Titles
                    cs.setNonStrokingColor(80, 90, 110)
                    drawSafePdfText(
                        cs = cs,
                        text = "Original (${result.sourceLanguage})",
                        x = leftColX,
                        y = pageH - 96f,
                        fontSize = 10f,
                        latinFont = latinFont,
                        bengaliFont = bengaliFont
                    )
                    drawSafePdfText(
                        cs = cs,
                        text = "Translated (${result.targetLanguage.name})",
                        x = rightColX,
                        y = pageH - 96f,
                        fontSize = 10f,
                        latinFont = latinFont,
                        bengaliFont = bengaliFont
                    )

                    // Left Column Lines
                    cs.setNonStrokingColor(20, 20, 20)
                    val leftLines = wrapTextToLines(pair.originalText, maxCharsPerLine = 42)
                    var leftY = pageH - 116f
                    for (line in leftLines) {
                        if (leftY < margin + 24f) break
                        drawSafePdfText(
                            cs = cs,
                            text = line,
                            x = leftColX,
                            y = leftY,
                            fontSize = 9.5f,
                            latinFont = latinFont,
                            bengaliFont = bengaliFont
                        )
                        leftY -= 13.5f
                    }

                    // Right Column Lines
                    val rightLines = wrapTextToLines(pair.translatedText, maxCharsPerLine = 42)
                    var rightY = pageH - 116f
                    for (line in rightLines) {
                        if (rightY < margin + 24f) break
                        drawSafePdfText(
                            cs = cs,
                            text = line,
                            x = rightColX,
                            y = rightY,
                            fontSize = 9.5f,
                            latinFont = latinFont,
                            bengaliFont = bengaliFont
                        )
                        rightY -= 13.5f
                    }
                }
            }

            doc.save(outputFile)
            outputFile.exists() && outputFile.length() > 0L
        } catch (_: Exception) {
            false
        } finally {
            try {
                doc?.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun drawSafePdfText(
        cs: PDPageContentStream,
        text: String,
        x: Float,
        y: Float,
        fontSize: Float,
        latinFont: PDFont?,
        bengaliFont: PDFont?
    ) {
        val clean = text.replace("\r", " ").replace("\n", " ").trim()
        if (clean.isEmpty()) return
        val hasBengali = clean.any { it.code in 0x0980..0x09FF }
        val chosenFont = if (hasBengali && bengaliFont != null) bengaliFont else latinFont
        if (chosenFont != null) {
            try {
                cs.beginText()
                cs.setFont(chosenFont, fontSize)
                cs.newLineAtOffset(x, y)
                cs.showText(clean)
                cs.endText()
                return
            } catch (_: Exception) {
                try {
                    cs.endText()
                } catch (_: Exception) {
                }
            }
        }

        try {
            val asciiSafe = clean.map { if (it.code in 32..126) it else '?' }.joinToString("")
            cs.beginText()
            cs.setFont(PDType1Font.HELVETICA, fontSize)
            cs.newLineAtOffset(x, y)
            cs.showText(asciiSafe)
            cs.endText()
        } catch (_: Exception) {
            try {
                cs.endText()
            } catch (_: Exception) {
            }
        }
    }

    private fun wrapTextToLines(text: String, maxCharsPerLine: Int): List<String> {
        val lines = mutableListOf<String>()
        for (paragraph in text.split("\n")) {
            val trimmed = paragraph.trim()
            if (trimmed.isEmpty()) {
                lines.add("")
                continue
            }
            val words = trimmed.split(Regex("\\s+"))
            var currentLine = StringBuilder()
            for (word in words) {
                if (currentLine.isEmpty()) {
                    currentLine.append(word)
                } else if (currentLine.length + 1 + word.length <= maxCharsPerLine) {
                    currentLine.append(' ').append(word)
                } else {
                    lines.add(currentLine.toString())
                    currentLine = StringBuilder(word)
                }
            }
            if (currentLine.isNotEmpty()) {
                lines.add(currentLine.toString())
            }
        }
        return lines
    }

    companion object {
        /**
         * Splits extracted page text into overlapping chunks tagged with 0-based pageIndex and 1-based pageNumber.
         */
        fun splitDocumentTextIntoChunks(
            pagesText: Map<Int, String>,
            maxChunkChars: Int = 1100,
            overlapChars: Int = 150
        ): List<PdfTextChunk> {
            val chunks = mutableListOf<PdfTextChunk>()
            val safeMax = maxChunkChars.coerceAtLeast(200)
            val safeOverlap = overlapChars.coerceIn(0, safeMax / 2)

            for ((pageIndex, rawPageText) in pagesText.entries.sortedBy { it.key }) {
                val cleaned = rawPageText.replace(Regex("\\s+"), " ").trim()
                if (cleaned.isEmpty()) continue
                val pageNumber = pageIndex + 1

                if (cleaned.length <= safeMax) {
                    chunks.add(
                        PdfTextChunk(
                            chunkId = "p${pageNumber}_c0",
                            pageIndex = pageIndex,
                            pageNumber = pageNumber,
                            text = cleaned
                        )
                    )
                } else {
                    var start = 0
                    var subIndex = 0
                    while (start < cleaned.length) {
                        var end = (start + safeMax).coerceAtMost(cleaned.length)
                        if (end < cleaned.length) {
                            val lastSpace = cleaned.lastIndexOf(' ', end)
                            if (lastSpace > start + safeMax / 2) {
                                end = lastSpace
                            }
                        }
                        val slice = cleaned.substring(start, end).trim()
                        if (slice.isNotEmpty()) {
                            chunks.add(
                                PdfTextChunk(
                                    chunkId = "p${pageNumber}_c$subIndex",
                                    pageIndex = pageIndex,
                                    pageNumber = pageNumber,
                                    text = slice
                                )
                            )
                            subIndex++
                        }
                        if (end >= cleaned.length) break
                        start = (end - safeOverlap).coerceAtLeast(start + 1)
                    }
                }
            }
            return chunks
        }

        /**
         * Selects only the most relevant chunks for a user query while strictly respecting maxTotalChars
         * so long documents never exceed model context limits.
         */
        fun selectRelevantChunks(
            chunks: List<PdfTextChunk>,
            query: String,
            maxChunks: Int = 6,
            maxTotalChars: Int = 7200
        ): List<PdfTextChunk> {
            if (chunks.isEmpty()) return emptyList()
            val queryTokens = tokenizeForRetrieval(query)

            // Also check if user explicitly mentioned a page number in the question (e.g. "page 4")
            val explicitPages = parsePageCitations(query).toSet()

            val scored = chunks.mapIndexed { idx, chunk ->
                val lowerText = chunk.text.lowercase()
                var score = 0.0

                if (chunk.pageNumber in explicitPages) {
                    score += 50.0
                }

                for (token in queryTokens) {
                    if (lowerText.contains(token)) {
                        val occurrences = lowerText.windowed(token.length, step = token.length.coerceAtLeast(1))
                            .count { it == token }
                            .coerceAtLeast(1)
                        score += (1.5 + token.length * 0.25) * occurrences.coerceAtMost(5)
                    }
                }

                // Slight tie-breaker preference for earlier pages (abstract/intro)
                val positionBonus = 0.05 / (idx + 1)
                Pair(chunk, score + positionBonus)
            }

            val sortedByScore = scored.sortedByDescending { it.second }
            val chosen = mutableListOf<PdfTextChunk>()
            var currentChars = 0

            for ((chunk, _) in sortedByScore) {
                if (chosen.size >= maxChunks) break
                if (currentChars + chunk.text.length > maxTotalChars && chosen.isNotEmpty()) {
                    continue
                }
                chosen.add(chunk)
                currentChars += chunk.text.length
            }

            // Return in natural reading order by page and chunk ID
            return chosen.sortedWith(compareBy<PdfTextChunk> { it.pageIndex }.thenBy { it.chunkId })
        }

        /**
         * Extracts 1-based page numbers cited in text, such as [Page 3], (Page 5), Page 12, or পৃষ্ঠা ৩.
         */
        fun parsePageCitations(text: String): List<Int> {
            val pages = linkedSetOf<Int>()
            val regex = Regex(
                """(?:\[\s*Page\s*([0-9০-৯]+)\s*]|\(\s*Page\s*([0-9০-৯]+)\s*\)|\bPage\s+([0-9০-৯]+)\b|পৃষ্ঠা\s*([0-9০-৯]+))""",
                RegexOption.IGNORE_CASE
            )
            for (match in regex.findAll(text)) {
                for (groupIdx in 1..4) {
                    val raw = match.groupValues.getOrNull(groupIdx)
                    if (!raw.isNullOrBlank()) {
                        val normalizedDigits = raw.map { ch ->
                            if (ch in '০'..'৯') ('0' + (ch - '০')) else ch
                        }.joinToString("")
                        normalizedDigits.toIntOrNull()?.let { p ->
                            if (p > 0) pages.add(p)
                        }
                    }
                }
            }
            return pages.toList().sorted()
        }

        /**
         * Parses a page range expression like "1-3, 5" or Bengali "১-৩, ৫" into 0-based page indices within 0 until totalPages.
         */
        fun parsePageRangeInput(rangeInput: String, totalPages: Int): List<Int> {
            if (totalPages <= 0) return emptyList()
            val normalizedInput = rangeInput
                .map { ch ->
                    when {
                        ch in '০'..'৯' -> '0' + (ch - '০')
                        ch == '–' || ch == '—' -> '-'
                        else -> ch
                    }
                }
                .joinToString("")
                .trim()
            if (normalizedInput.isEmpty()) return (0 until totalPages).toList()

            val indices = sortedSetOf<Int>()
            val parts = normalizedInput.split(",", ";")
            for (part in parts) {
                val token = part.trim()
                if (token.isEmpty()) continue
                if (token.contains("-")) {
                    val bounds = token.split("-").map { it.trim() }
                    val start = bounds.getOrNull(0)?.toIntOrNull()
                    val end = bounds.getOrNull(1)?.toIntOrNull()
                    if (start != null && end != null) {
                        val low = minOf(start, end).coerceIn(1, totalPages)
                        val high = maxOf(start, end).coerceIn(1, totalPages)
                        for (p in low..high) {
                            indices.add(p - 1)
                        }
                    }
                } else {
                    token.toIntOrNull()?.let { p ->
                        if (p in 1..totalPages) {
                            indices.add(p - 1)
                        }
                    }
                }
            }
            return indices.toList()
        }

        private fun tokenizeForRetrieval(query: String): List<String> {
            val stopWords = setOf(
                "the", "and", "for", "with", "what", "where", "when", "how", "why", "who",
                "this", "that", "from", "are", "was", "were", "have", "has", "had", "does",
                "can", "you", "about", "into", "page", "pdf", "document", "tell"
            )
            return query.lowercase()
                .split(Regex("""[^\p{L}\p{N}]+"""))
                .map { it.trim() }
                .filter { it.length >= 2 && it !in stopWords }
                .distinct()
        }

        internal fun samplePageIndicesForExtraction(
            pageIndices: List<Int>,
            maxPages: Int = 24
        ): List<Int> {
            val distinctSorted = pageIndices.distinct().sorted()
            if (distinctSorted.size <= maxPages || maxPages <= 1) return distinctSorted
            val selected = linkedSetOf<Int>()
            // Always include the first 3 pages (title, abstract, introduction)
            distinctSorted.take(3).forEach { selected.add(it) }
            // Always include the last 2 pages (conclusions / summary)
            val tailPages = distinctSorted.takeLast(2)
            val targetMiddleCount = (maxPages - selected.size - tailPages.size).coerceAtLeast(1)
            val middlePool = distinctSorted.drop(3).dropLast(2)
            if (middlePool.isNotEmpty()) {
                val step = (middlePool.size.toDouble() / targetMiddleCount).coerceAtLeast(1.0)
                var cursor = 0.0
                while (cursor.toInt() < middlePool.size && selected.size < maxPages - tailPages.size) {
                    selected.add(middlePool[cursor.toInt()])
                    cursor += step
                }
            }
            tailPages.forEach { selected.add(it) }
            return selected.sorted()
        }

        internal fun cleanReadablePdfText(raw: String): String {
            if (raw.isBlank()) return ""
            // Strip Private Use Area glyphs (\uE000..\uF8FF), replacement chars (\uFFFD), and NULL/control bytes
            val filtered = StringBuilder(raw.length)
            for (ch in raw) {
                val code = ch.code
                val isPrivateUse = code in 0xE000..0xF8FF
                val isReplacement = code == 0xFFFD || code == 0xFFFF || code == 0xFFFE
                val isControl = code < 0x20 && ch != '\n' && ch != '\r' && ch != '\t'
                if (!isPrivateUse && !isReplacement && !isControl) {
                    filtered.append(ch)
                }
            }
            return filtered.toString().trim()
        }

        internal fun countReadableCharacters(text: String): Int {
            var count = 0
            for (ch in text) {
                if (ch.isLetterOrDigit() || ch.code in 0x0980..0x09FF || ch.code in 0x0900..0x097F || ch.code in 0x0600..0x06FF) {
                    count++
                }
            }
            return count
        }

        internal fun sampleChunksWithinBudget(
            chunks: List<PdfTextChunk>,
            maxTotalChars: Int
        ): List<PdfTextChunk> {
            if (chunks.isEmpty()) return emptyList()
            val totalChars = chunks.sumOf { it.text.length }
            if (totalChars <= maxTotalChars) return chunks

            val avgChunkChars = (totalChars.toDouble() / chunks.size).coerceAtLeast(150.0)
            val targetChunkCount = (maxTotalChars.toDouble() / avgChunkChars).coerceIn(6.0, chunks.size.toDouble())
            val step = (chunks.size.toDouble() / targetChunkCount).coerceAtLeast(1.0)

            val selected = mutableListOf<PdfTextChunk>()
            var accumulated = 0
            var idx = 0.0
            while (idx.toInt() < chunks.size && accumulated < maxTotalChars) {
                val c = chunks[idx.toInt()]
                if (c !in selected) {
                    val remaining = maxTotalChars - accumulated
                    if (c.text.length <= remaining) {
                        selected.add(c)
                        accumulated += c.text.length
                    } else if (remaining >= 250) {
                        selected.add(c.copy(text = c.text.take(remaining)))
                        accumulated += remaining
                        break
                    }
                }
                idx += step
            }
            // Always include the final chunk if budget permits so conclusion/ending pages are represented
            val lastChunk = chunks.last()
            if (lastChunk !in selected && accumulated + 200 <= maxTotalChars) {
                val sliceLen = (maxTotalChars - accumulated).coerceAtMost(lastChunk.text.length)
                selected.add(lastChunk.copy(text = lastChunk.text.take(sliceLen)))
            }
            return selected.ifEmpty {
                listOf(chunks.first().copy(text = chunks.first().text.take(maxTotalChars)))
            }
        }

        private val SUMMARY_HEADING_REGEX = Regex(
            """^\s*(?:#{1,6}\s*)?(?:\*\*|__)?\s*(?:SHORT_SUMMARY|SHORT\s+SUMMARY|EXECUTIVE\s+SUMMARY|SUMMARY|OVERVIEW|সংক্ষিপ্ত\s*সারসংক্ষেপ|সারসংক্ষেপ|সারাংশ|संक्षिप्त\s*सारांश|सारांश|RESUMEN(?:\s+CORTO)?|RÉSUMÉ(?:\s+COURT)?|ملخص(?:\s+قصير)?)\s*(?:\*\*|__)?\s*:?\s*(?:\*\*|__)?\s*""",
            RegexOption.IGNORE_CASE
        )

        private val KEY_POINTS_HEADING_REGEX = Regex(
            """(?m)^\s*(?:#{1,6}\s*)?(?:\*\*|__)?\s*(?:KEY_POINTS|KEY\s+POINTS|KEY\s+TAKEAWAYS|MAIN\s+POINTS|HIGHLIGHTS|BULLET\s+POINTS|মূল\s*পয়েন্ট(?:সমূহ)?|প্রধান\s*পয়েন্ট(?:সমূহ)?|মূল\s*বিষয়(?:সমূহ)?|मुख्य\s*बिंदु|PUNTOS\s+CLAVE|POINTS\s+CLÉS|النقاط\s+الرئيسية)\s*(?:\*\*|__)?\s*:?\s*(?:\*\*|__)?\s*""",
            RegexOption.IGNORE_CASE
        )

        private fun cleanMarkdownFormatting(input: String): String {
            return input
                .replace(Regex("""^\s*(?:#{1,6}\s*)"""), "")
                .replace(Regex("""\*\*(.*?)\*\*"""), "$1")
                .replace(Regex("""__(.*?)__"""), "$1")
                .replace(Regex("""^[\s*:*-]+|[\s*]+$"""), "")
                .trim()
        }

        internal fun parseSummaryResponse(
            rawResponse: String,
            pageRangeDescription: String,
            outputLanguage: AiOutputLanguage,
            fallbackPages: List<Int>
        ): AiSummaryResult {
            val trimmedRaw = rawResponse.trim()
            val strippedCodeFence = trimmedRaw
                .removePrefix("```json")
                .removePrefix("```JSON")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            // 1. First attempt structured JSON parsing (used by summaryGenerativeModel with Schema.obj)
            val jsonStart = strippedCodeFence.indexOf('{')
            val jsonEnd = strippedCodeFence.lastIndexOf('}')
            if (jsonStart != -1 && jsonEnd > jsonStart) {
                val jsonCandidate = strippedCodeFence.substring(jsonStart, jsonEnd + 1)
                val parsedJsonResult = runCatching {
                    val obj = JSONObject(jsonCandidate)
                    val summaryStr = (
                        obj.optString("shortSummary").takeIf { it.isNotBlank() }
                            ?: obj.optString("short_summary").takeIf { it.isNotBlank() }
                            ?: obj.optString("summary").takeIf { it.isNotBlank() }
                            ?: obj.optString("executiveSummary").takeIf { it.isNotBlank() }
                            ?: obj.optString("overview").takeIf { it.isNotBlank() }
                        )?.let { cleanMarkdownFormatting(it) }.orEmpty()

                    val pointsArray = obj.optJSONArray("keyPoints")
                        ?: obj.optJSONArray("key_points")
                        ?: obj.optJSONArray("keyTakeaways")
                        ?: obj.optJSONArray("bullets")
                    val pointsList = mutableListOf<String>()
                    if (pointsArray != null) {
                        for (i in 0 until pointsArray.length()) {
                            val rawElem = pointsArray.opt(i)
                            val rawItemText = when (rawElem) {
                                is JSONObject -> {
                                    rawElem.optString("point").takeIf { it.isNotBlank() }
                                        ?: rawElem.optString("text").takeIf { it.isNotBlank() }
                                        ?: rawElem.optString("summary").takeIf { it.isNotBlank() }
                                        ?: rawElem.optString("description").takeIf { it.isNotBlank() }
                                        ?: ""
                                }
                                else -> pointsArray.optString(i)
                            }
                            val item = rawItemText.trim()
                                .removePrefix("-").removePrefix("•").removePrefix("*")
                                .replaceFirst(Regex("^\\d+\\.\\s*"), "")
                                .let { cleanMarkdownFormatting(it) }
                            if (item.isNotBlank()) {
                                pointsList.add(item)
                            }
                        }
                    }

                    val pagesArray = obj.optJSONArray("referencedPages")
                        ?: obj.optJSONArray("referenced_pages")
                        ?: obj.optJSONArray("pages")
                    val jsonPages = mutableListOf<Int>()
                    if (pagesArray != null) {
                        for (i in 0 until pagesArray.length()) {
                            val p = pagesArray.optInt(i, -1)
                            if (p > 0) jsonPages.add(p)
                        }
                    }

                    if (summaryStr.isNotBlank() || pointsList.isNotEmpty()) {
                        val finalSummary = summaryStr.ifBlank {
                            pointsList.firstOrNull().orEmpty()
                        }
                        if (pointsList.isEmpty() && finalSummary.isNotBlank()) {
                            val derivedSentences = finalSummary
                                .split(Regex("""(?<=[.!?।])\s+"""))
                                .map { it.trim() }
                                .filter { it.length > 20 }
                            if (derivedSentences.size > 1) {
                                pointsList.addAll(derivedSentences.take(5))
                            } else {
                                pointsList.add(finalSummary)
                            }
                        }
                        val cited = (jsonPages + parsePageCitations(finalSummary) + pointsList.flatMap { parsePageCitations(it) })
                            .distinct()
                            .sorted()
                            .ifEmpty { fallbackPages.take(5) }

                        AiSummaryResult(
                            shortSummary = finalSummary,
                            keyPoints = pointsList,
                            pageRangeDescription = pageRangeDescription,
                            outputLanguage = outputLanguage,
                            referencedPages = cited,
                            disclaimer = AI_DISCLAIMER_TEXT
                        )
                    } else {
                        null
                    }
                }.getOrNull()

                if (parsedJsonResult != null) {
                    return parsedJsonResult
                }
            }

            // 2. Fallback: Markdown / Plain-Text parser supporting English, Bengali, Hindi, Spanish, French, Arabic
            val keyPointsMatch = KEY_POINTS_HEADING_REGEX.find(strippedCodeFence)
            val beforeKeyPoints: String
            val afterKeyPoints: String

            if (keyPointsMatch != null) {
                beforeKeyPoints = strippedCodeFence.substring(0, keyPointsMatch.range.first).trim()
                afterKeyPoints = strippedCodeFence.substring(keyPointsMatch.range.last + 1).trim()
            } else {
                beforeKeyPoints = strippedCodeFence
                afterKeyPoints = ""
            }

            // Strip any leading SHORT_SUMMARY / Summary heading line(s) from beforeKeyPoints
            val summaryLines = beforeKeyPoints
                .lines()
                .map { line ->
                    SUMMARY_HEADING_REGEX.replaceFirst(line, "").trim()
                }
                .filter { line ->
                    val cleaned = cleanMarkdownFormatting(line)
                    cleaned.isNotEmpty() && !SUMMARY_HEADING_REGEX.matches(line)
                }

            val bulletLineRegex = Regex("""^\s*(?:[-•*▪▸]|\d+[.)]|[ক-হ][.)])\s+(.+)$""")

            val extractedSummarySentences = mutableListOf<String>()
            val inlineBulletsInFirstSection = mutableListOf<String>()

            for (line in summaryLines) {
                val bulletMatch = bulletLineRegex.matchEntire(line)
                if (bulletMatch != null && keyPointsMatch == null && extractedSummarySentences.isNotEmpty()) {
                    inlineBulletsInFirstSection.add(cleanMarkdownFormatting(bulletMatch.groupValues[1]))
                } else if (bulletMatch != null && keyPointsMatch != null) {
                    extractedSummarySentences.add(cleanMarkdownFormatting(bulletMatch.groupValues[1]))
                } else {
                    val cleanedLine = cleanMarkdownFormatting(line)
                    if (cleanedLine.isNotEmpty()) {
                        extractedSummarySentences.add(cleanedLine)
                    }
                }
            }

            val bullets = mutableListOf<String>()
            if (afterKeyPoints.isNotEmpty()) {
                for (line in afterKeyPoints.lines()) {
                    val trimmed = line.trim()
                    if (trimmed.isEmpty()) continue
                    val match = bulletLineRegex.matchEntire(trimmed)
                    val bulletText = if (match != null) {
                        cleanMarkdownFormatting(match.groupValues[1])
                    } else {
                        cleanMarkdownFormatting(trimmed)
                    }
                    if (bulletText.length > 3 && !KEY_POINTS_HEADING_REGEX.matches(trimmed)) {
                        bullets.add(bulletText)
                    }
                }
            } else if (inlineBulletsInFirstSection.isNotEmpty()) {
                bullets.addAll(inlineBulletsInFirstSection)
            }

            val finalShortSummary = extractedSummarySentences.joinToString(" ")
                .trim()
                .ifBlank {
                    bullets.firstOrNull() ?: cleanMarkdownFormatting(strippedCodeFence).take(400)
                }

            if (bullets.isEmpty() && finalShortSummary.isNotBlank()) {
                // Split multi-sentence summary into distinct key points if the model didn't emit bullets
                val sentences = finalShortSummary
                    .split(Regex("""(?<=[.!?।])\s+"""))
                    .map { it.trim() }
                    .filter { it.length > 20 }
                if (sentences.size > 1) {
                    bullets.addAll(sentences.take(5))
                } else {
                    bullets.add(finalShortSummary)
                }
            }

            val cited = parsePageCitations(strippedCodeFence).ifEmpty { fallbackPages.take(5) }

            return AiSummaryResult(
                shortSummary = finalShortSummary,
                keyPoints = bullets,
                pageRangeDescription = pageRangeDescription,
                outputLanguage = outputLanguage,
                referencedPages = cited,
                disclaimer = AI_DISCLAIMER_TEXT
            )
        }

        private fun parseMultiPageTranslation(raw: String): Map<Int, String> {
            val map = mutableMapOf<Int, String>()
            val regex = Regex("""\[\[PAGE_(\d+)]](.*?)(?=\[\[PAGE_\d+]]|===PAGE_BREAK===|\z)""", RegexOption.DOT_MATCHES_ALL)
            for (match in regex.findAll(raw)) {
                val pageNum = match.groupValues[1].toIntOrNull() ?: continue
                val body = match.groupValues[2].replace("===PAGE_BREAK===", "").trim()
                if (body.isNotEmpty()) {
                    map[pageNum] = body
                }
            }
            return map
        }
    }
}
