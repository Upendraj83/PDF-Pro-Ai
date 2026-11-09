package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a recently opened, favorited, or folder-organized PDF document.
 */
@Entity(tableName = "recent_pdfs")
data class RecentPdfEntity(
    @PrimaryKey
    val uri: String,
    val fileName: String,
    val fileSizeBytes: Long,
    val lastOpenedTimestamp: Long = System.currentTimeMillis(),
    val pageCount: Int = 0,
    val lastPage: Int = 0,
    val isFavorite: Boolean = false,
    val folderName: String = ""
)
