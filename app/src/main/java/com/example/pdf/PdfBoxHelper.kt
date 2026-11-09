package com.example.pdf

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

data class PdfOutlineEntry(
    val title: String,
    val pageIndex: Int, // 0-indexed
    val depth: Int = 0
)

data class SearchMatch(
    val pageIndex: Int,
    val matchIndexInDoc: Int,
    val snippet: String,
    val matchTerm: String
)

sealed interface SearchProgress {
    data class Searching(val currentPage: Int, val totalPages: Int, val matchesFound: Int) : SearchProgress
    data class Complete(val matches: List<SearchMatch>) : SearchProgress
    data class Failed(val error: String) : SearchProgress
}

class PdfBoxHelper(private val context: Context) {

    init {
        try {
            PDFBoxResourceLoader.init(context.applicationContext)
        } catch (ignored: Exception) {
        }
    }

    suspend fun extractOutline(pdfFile: File?, uri: Uri?): List<PdfOutlineEntry> = withContext(Dispatchers.IO) {
        val results = mutableListOf<PdfOutlineEntry>()
        var pdDocument: PDDocument? = null

        try {
            pdDocument = openPdDocument(pdfFile, uri) ?: return@withContext emptyList()
            val outline = pdDocument.documentCatalog.documentOutline ?: return@withContext emptyList()
            collectOutlineItems(pdDocument, outline, results, 0)
        } catch (oom: OutOfMemoryError) {
            System.gc()
        } catch (e: Throwable) {
            // Ignore outline extraction errors on malformed outlines
        } finally {
            try {
                pdDocument?.close()
            } catch (ignored: Throwable) {
            }
        }

        results
    }

    private fun collectOutlineItems(
        doc: PDDocument,
        node: PDOutlineNode,
        list: MutableList<PdfOutlineEntry>,
        depth: Int
    ) {
        var current: PDOutlineItem? = node.firstChild
        while (current != null) {
            try {
                val title = current.title ?: "Untitled Section"
                var targetPage = 0

                val destination = current.destination
                if (destination is PDPageDestination) {
                    val pageNum = destination.pageNumber
                    if (pageNum >= 0) {
                        targetPage = pageNum
                    }
                } else {
                    val destPage = current.findDestinationPage(doc)
                    if (destPage != null) {
                        val index = doc.pages.indexOf(destPage)
                        if (index >= 0) {
                            targetPage = index
                        }
                    }
                }

                list.add(PdfOutlineEntry(title = title, pageIndex = targetPage, depth = depth))

                // Recursively check children
                if (current.hasChildren()) {
                    collectOutlineItems(doc, current, list, depth + 1)
                }
            } catch (ignored: Exception) {
            }
            current = current.nextSibling
        }
    }

    suspend fun extractPageText(pdfFile: File?, uri: Uri?, pageIndex: Int): String = withContext(Dispatchers.IO) {
        var pdDocument: PDDocument? = null
        try {
            pdDocument = openPdDocument(pdfFile, uri) ?: return@withContext ""
            val total = pdDocument.numberOfPages
            if (pageIndex !in 0 until total) return@withContext ""

            extractSinglePageTextRotationAware(pdDocument, pageIndex)
        } catch (oom: OutOfMemoryError) {
            System.gc()
            ""
        } catch (e: Throwable) {
            ""
        } finally {
            try {
                pdDocument?.close()
            } catch (ignored: Throwable) {
            }
        }
    }

    /**
     * Extracts text from [pageIndex] while handling pages whose `/Rotate` entry was changed
     * to 90°, 180°, or 270° so horizontal content-stream text is not split character-by-character.
     */
    fun extractSinglePageTextRotationAware(pdDocument: PDDocument, pageIndex: Int): String {
        val page = pdDocument.getPage(pageIndex)
        val originalRotation = page.rotation
        val stripper = PDFTextStripper().apply {
            startPage = pageIndex + 1
            endPage = pageIndex + 1
        }
        val primaryText = try {
            stripper.getText(pdDocument).trim()
        } catch (_: Exception) {
            ""
        }
        if (originalRotation % 360 == 0) {
            return primaryText
        }
        return try {
            page.rotation = 0
            val unrotatedText = stripper.getText(pdDocument).trim()
            val primaryLines = primaryText.lines().count { it.isNotBlank() }
            val unrotatedLines = unrotatedText.lines().count { it.isNotBlank() }
            // Prefer unrotated extraction when /Rotate caused character-per-line fragmentation
            if (unrotatedText.isNotBlank() && (primaryText.isBlank() || unrotatedLines < primaryLines)) {
                unrotatedText
            } else {
                primaryText
            }
        } catch (_: Exception) {
            primaryText
        } finally {
            page.rotation = originalRotation
        }
    }

    fun searchDocument(
        pdfFile: File?,
        uri: Uri?,
        query: String
    ): Flow<SearchProgress> = flow {
        if (query.isBlank()) {
            emit(SearchProgress.Complete(emptyList()))
            return@flow
        }

        var pdDocument: PDDocument? = null
        val matches = mutableListOf<SearchMatch>()
        var matchCount = 0

        try {
            pdDocument = openPdDocument(pdfFile, uri)
            if (pdDocument == null) {
                emit(SearchProgress.Failed("Could not open document for search."))
                return@flow
            }

            val totalPages = pdDocument.numberOfPages

            for (pageIndex in 0 until totalPages) {
                currentCoroutineContext().ensureActive()

                emit(
                    SearchProgress.Searching(
                        currentPage = pageIndex + 1,
                        totalPages = totalPages,
                        matchesFound = matchCount
                    )
                )

                val text = extractSinglePageTextRotationAware(pdDocument, pageIndex)

                if (text.isNotBlank()) {
                    var startIndex = 0
                    while (startIndex < text.length) {
                        val foundIndex = text.indexOf(query, startIndex, ignoreCase = true)
                        if (foundIndex == -1) break

                        // Extract context snippet
                        val snippetStart = (foundIndex - 32).coerceAtLeast(0)
                        val snippetEnd = (foundIndex + query.length + 32).coerceAtMost(text.length)
                        val rawSnippet = text.substring(snippetStart, snippetEnd)
                            .replace("\n", " ")
                            .replace("\r", " ")
                            .trim()

                        val prefix = if (snippetStart > 0) "…" else ""
                        val suffix = if (snippetEnd < text.length) "…" else ""
                        val snippet = "$prefix$rawSnippet$suffix"

                        matches.add(
                            SearchMatch(
                                pageIndex = pageIndex,
                                matchIndexInDoc = matchCount,
                                snippet = snippet,
                                matchTerm = query
                            )
                        )
                        matchCount++

                        startIndex = foundIndex + query.length.coerceAtLeast(1)
                    }
                }
            }

            emit(SearchProgress.Complete(matches))
        } catch (oom: OutOfMemoryError) {
            System.gc()
            emit(SearchProgress.Failed("Out of memory while searching large document"))
        } catch (e: Throwable) {
            emit(SearchProgress.Failed(e.localizedMessage ?: "Search failed"))
        } finally {
            try {
                pdDocument?.close()
            } catch (ignored: Throwable) {
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun openPdDocument(pdfFile: File?, uri: Uri?): PDDocument? {
        return PdfBoxMemoryUtil.loadDocument(context, pdfFile, uri)
    }
}
