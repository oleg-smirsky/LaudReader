package com.laudreader.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ArticleDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: ArticleDao

    private val testArticle = Article(
        title = "Test Article",
        sourceUrl = "https://example.com/article",
        domain = "example.com",
        extractedText = "Some article text content",
        status = ArticleStatus.GENERATING
    )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.articleDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndRetrieveById() = runTest {
        val id = dao.insert(testArticle)
        val retrieved = dao.getById(id)

        assertNotNull(retrieved)
        assertEquals("Test Article", retrieved!!.title)
        assertEquals("example.com", retrieved.domain)
        assertEquals(ArticleStatus.GENERATING, retrieved.status)
    }

    @Test
    fun getAllArticlesReturnsFlowOrderedByCreatedAtDesc() = runTest {
        val article1 = testArticle.copy(title = "First", createdAt = 1000L)
        val article2 = testArticle.copy(title = "Second", createdAt = 2000L)
        val article3 = testArticle.copy(title = "Third", createdAt = 3000L)

        dao.insert(article1)
        dao.insert(article2)
        dao.insert(article3)

        dao.getAllArticles().test {
            val articles = awaitItem()
            assertEquals(3, articles.size)
            // Most recent first
            assertEquals("Third", articles[0].title)
            assertEquals("Second", articles[1].title)
            assertEquals("First", articles[2].title)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun updateArticle() = runTest {
        val id = dao.insert(testArticle)
        val inserted = dao.getById(id)!!

        dao.update(inserted.copy(title = "Updated Title"))

        val updated = dao.getById(id)
        assertEquals("Updated Title", updated!!.title)
    }

    @Test
    fun deleteArticle() = runTest {
        val id = dao.insert(testArticle)
        val inserted = dao.getById(id)!!

        dao.delete(inserted)

        assertNull(dao.getById(id))
    }

    @Test
    fun updateStatus() = runTest {
        val id = dao.insert(testArticle)

        dao.updateStatus(id, ArticleStatus.READY.name)

        val updated = dao.getById(id)
        assertEquals(ArticleStatus.READY, updated!!.status)
    }

    @Test
    fun updateGenerationProgress() = runTest {
        val id = dao.insert(testArticle)

        dao.updateGenerationProgress(id, 75)

        val updated = dao.getById(id)
        assertEquals(75, updated!!.generationProgress)
    }

    @Test
    fun updateAudioReady() = runTest {
        val id = dao.insert(testArticle)

        dao.updateAudioReady(id, "/path/to/audio.mp3", 1024L, 60000L)

        val updated = dao.getById(id)!!
        assertEquals("/path/to/audio.mp3", updated.audioFilePath)
        assertEquals(1024L, updated.audioFileSizeBytes)
        assertEquals(60000L, updated.durationMs)
        assertEquals(ArticleStatus.READY, updated.status)
    }

    @Test
    fun updatePlaybackPosition() = runTest {
        val id = dao.insert(testArticle)
        val playedAt = System.currentTimeMillis()

        dao.updatePlaybackPosition(id, 30000L, playedAt)

        val updated = dao.getById(id)!!
        assertEquals(30000L, updated.playbackPositionMs)
        assertEquals(playedAt, updated.lastPlayedAt)
    }

    @Test
    fun getFirstWithStatus() = runTest {
        dao.insert(testArticle.copy(title = "Generating 1", status = ArticleStatus.GENERATING))
        dao.insert(testArticle.copy(title = "Ready 1", status = ArticleStatus.READY))
        dao.insert(testArticle.copy(title = "Ready 2", status = ArticleStatus.READY))

        val ready = dao.getFirstWithStatus(ArticleStatus.READY.name)
        assertNotNull(ready)
        assertEquals(ArticleStatus.READY, ready!!.status)
    }

    @Test
    fun getFirstWithStatusReturnsNullWhenNoneMatch() = runTest {
        dao.insert(testArticle.copy(status = ArticleStatus.GENERATING))

        val result = dao.getFirstWithStatus(ArticleStatus.PLAYED.name)
        assertNull(result)
    }

    @Test
    fun flowUpdatesWhenDataChanges() = runTest {
        dao.getAllArticles().test {
            // Initial empty state
            assertEquals(0, awaitItem().size)

            // Insert triggers update
            val id = dao.insert(testArticle)
            assertEquals(1, awaitItem().size)

            // Delete triggers update
            dao.delete(dao.getById(id)!!)
            assertEquals(0, awaitItem().size)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
