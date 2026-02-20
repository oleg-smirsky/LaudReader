package com.laudreader.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ArticleStatus {
    GENERATING,
    READY,
    PLAYING,
    PLAYED
}

@Entity(tableName = "articles")
data class Article(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val sourceUrl: String,
    val domain: String,
    val extractedText: String,
    val audioFilePath: String? = null,
    val audioFileSizeBytes: Long = 0,
    val status: ArticleStatus = ArticleStatus.GENERATING,
    val generationProgress: Int = 0,
    val playbackPositionMs: Long = 0,
    val durationMs: Long = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val lastPlayedAt: Long? = null
)
