package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentPdfDao {
    @Query("SELECT * FROM recent_pdfs ORDER BY lastOpenedTimestamp DESC")
    fun getAllRecents(): Flow<List<RecentPdfEntity>>

    @Query("SELECT * FROM recent_pdfs WHERE isFavorite = 1 ORDER BY lastOpenedTimestamp DESC")
    fun getFavorites(): Flow<List<RecentPdfEntity>>

    @Query("SELECT * FROM recent_pdfs WHERE folderName = :folderName ORDER BY lastOpenedTimestamp DESC")
    fun getByFolder(folderName: String): Flow<List<RecentPdfEntity>>

    @Query("SELECT * FROM recent_pdfs WHERE uri = :uri LIMIT 1")
    suspend fun getPdfByUri(uri: String): RecentPdfEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(pdf: RecentPdfEntity)

    @Query("UPDATE recent_pdfs SET lastPage = :lastPage, lastOpenedTimestamp = :timestamp WHERE uri = :uri")
    suspend fun updateLastPage(uri: String, lastPage: Int, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE recent_pdfs SET isFavorite = :isFavorite WHERE uri = :uri")
    suspend fun setFavorite(uri: String, isFavorite: Boolean)

    @Query("UPDATE recent_pdfs SET folderName = :folderName WHERE uri = :uri")
    suspend fun setFolder(uri: String, folderName: String)

    @Query("UPDATE recent_pdfs SET folderName = '' WHERE folderName = :folderName")
    suspend fun clearFolderFromPdfs(folderName: String)

    @Query("DELETE FROM recent_pdfs WHERE uri = :uri")
    suspend fun deleteByUri(uri: String)

    @Query("DELETE FROM recent_pdfs")
    suspend fun clearAll()
}
