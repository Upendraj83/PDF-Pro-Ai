package com.example.data

import kotlinx.coroutines.flow.Flow

class PdfRepository(
    private val recentPdfDao: RecentPdfDao,
    private val bookmarkDao: BookmarkDao
) {
    val allRecents: Flow<List<RecentPdfEntity>> = recentPdfDao.getAllRecents()
    val favorites: Flow<List<RecentPdfEntity>> = recentPdfDao.getFavorites()

    suspend fun getPdf(uri: String): RecentPdfEntity? = recentPdfDao.getPdfByUri(uri)

    suspend fun recordPdfOpened(
        uri: String,
        fileName: String,
        fileSizeBytes: Long,
        pageCount: Int
    ) {
        val existing = recentPdfDao.getPdfByUri(uri)
        val entity = RecentPdfEntity(
            uri = uri,
            fileName = fileName,
            fileSizeBytes = fileSizeBytes,
            lastOpenedTimestamp = System.currentTimeMillis(),
            pageCount = if (pageCount > 0) pageCount else (existing?.pageCount ?: 0),
            lastPage = existing?.lastPage ?: 0,
            isFavorite = existing?.isFavorite ?: false,
            folderName = existing?.folderName ?: ""
        )
        recentPdfDao.insertOrUpdate(entity)
    }

    suspend fun updateLastPage(uri: String, lastPage: Int) {
        recentPdfDao.updateLastPage(uri, lastPage)
    }

    suspend fun toggleFavorite(uri: String, isCurrentlyFavorite: Boolean) {
        recentPdfDao.setFavorite(uri, !isCurrentlyFavorite)
    }

    suspend fun assignToFolder(uri: String, folderName: String) {
        recentPdfDao.setFolder(uri, folderName.trim())
    }

    suspend fun removeFolderAndUnassign(folderName: String) {
        recentPdfDao.clearFolderFromPdfs(folderName.trim())
    }

    suspend fun deleteRecent(uri: String) {
        recentPdfDao.deleteByUri(uri)
    }

    suspend fun clearHistory() {
        recentPdfDao.clearAll()
    }

    // Bookmark operations
    fun getBookmarks(pdfUri: String): Flow<List<BookmarkEntity>> =
        bookmarkDao.getBookmarksForPdf(pdfUri)

    suspend fun addBookmark(pdfUri: String, pageNumber: Int, note: String): Long {
        return bookmarkDao.insertBookmark(
            BookmarkEntity(
                pdfUri = pdfUri,
                pageNumber = pageNumber,
                note = note.trim()
            )
        )
    }

    suspend fun deleteBookmark(bookmark: BookmarkEntity) {
        bookmarkDao.deleteBookmark(bookmark)
    }

    suspend fun deleteBookmarkById(id: Long) {
        bookmarkDao.deleteBookmarkById(id)
    }
}
