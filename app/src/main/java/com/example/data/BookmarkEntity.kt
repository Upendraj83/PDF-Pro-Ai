package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pdf_bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val pdfUri: String,
    val pageNumber: Int, // 0-indexed internally
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
