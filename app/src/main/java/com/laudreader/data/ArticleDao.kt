package com.laudreader.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ArticleDao {

    @Query("SELECT * FROM articles ORDER BY createdAt DESC")
    fun getAllArticles(): Flow<List<Article>>

    @Query("SELECT * FROM articles WHERE id = :id")
    suspend fun getById(id: Long): Article?

    @Insert
    suspend fun insert(article: Article): Long

    @Update
    suspend fun update(article: Article)

    @Delete
    suspend fun delete(article: Article)

    @Query("UPDATE articles SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query("UPDATE articles SET generationProgress = :progress WHERE id = :id")
    suspend fun updateGenerationProgress(id: Long, progress: Int)

    @Query("UPDATE articles SET audioFilePath = :path, audioFileSizeBytes = :size, status = 'READY', durationMs = :durationMs WHERE id = :id")
    suspend fun updateAudioReady(id: Long, path: String, size: Long, durationMs: Long)

    @Query("UPDATE articles SET playbackPositionMs = :positionMs, lastPlayedAt = :lastPlayedAt WHERE id = :id")
    suspend fun updatePlaybackPosition(id: Long, positionMs: Long, lastPlayedAt: Long)

    @Query("SELECT * FROM articles WHERE status = :status LIMIT 1")
    suspend fun getFirstWithStatus(status: String): Article?
}
