package com.laudreader.ui

import android.content.Context
import com.laudreader.auth.GoogleAuthManager
import com.laudreader.data.Article
import com.laudreader.data.ArticleDao
import com.laudreader.data.ArticleStatus
import com.laudreader.extractor.ArticleExtractor
import com.laudreader.extractor.ExtractedArticle
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var context: Context
    private lateinit var articleDao: ArticleDao
    private lateinit var articleExtractor: ArticleExtractor
    private lateinit var authManager: GoogleAuthManager
    private lateinit var viewModel: MainViewModel

    private val testArticle = Article(
        id = 1,
        title = "Test Article",
        sourceUrl = "https://example.com/article",
        domain = "example.com",
        extractedText = "Some article text",
        status = ArticleStatus.READY,
        audioFilePath = "/path/to/audio.mp3"
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        context = mockk(relaxed = true)
        articleDao = mockk(relaxed = true)
        articleExtractor = mockk(relaxed = true)
        authManager = mockk(relaxed = true)

        every { articleDao.getAllArticles() } returns flowOf(listOf(testArticle))

        viewModel = MainViewModel(context, articleDao, articleExtractor, authManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- handleSharedUrl ---

    @Test
    fun `handleSharedUrl shows sign in message when not authenticated`() = runTest {
        every { authManager.isSignedIn() } returns false

        viewModel.handleSharedUrl("https://example.com")

        assertEquals("Please sign in with Google first", viewModel.snackbarMessage.value)
    }

    @Test
    fun `handleSharedUrl extracts article and inserts into database`() = runTest {
        every { authManager.isSignedIn() } returns true
        coEvery { articleExtractor.extract(any()) } returns ExtractedArticle(
            title = "New Article",
            domain = "example.com",
            textContent = "Article body text"
        )
        coEvery { articleDao.insert(any()) } returns 42L

        viewModel.handleSharedUrl("https://example.com/new")
        advanceUntilIdle()

        coVerify {
            articleExtractor.extract("https://example.com/new")
            articleDao.insert(match {
                it.title == "New Article" &&
                    it.domain == "example.com" &&
                    it.extractedText == "Article body text" &&
                    it.status == ArticleStatus.GENERATING
            })
        }
    }

    @Test
    fun `handleSharedUrl shows error on extraction failure`() = runTest {
        every { authManager.isSignedIn() } returns true
        coEvery { articleExtractor.extract(any()) } throws RuntimeException("Network error")

        viewModel.handleSharedUrl("https://example.com/fail")
        advanceUntilIdle()

        assertEquals("Failed to add article: Network error", viewModel.snackbarMessage.value)
    }

    // --- onArticleTap ---

    @Test
    fun `onArticleTap shows message for generating article`() {
        val generating = testArticle.copy(status = ArticleStatus.GENERATING)

        viewModel.onArticleTap(generating)

        assertEquals("Still generating audio...", viewModel.snackbarMessage.value)
    }

    // --- deleteArticle ---

    @Test
    fun `deleteArticle removes article from database`() = runTest {
        viewModel.deleteArticle(testArticle)
        advanceUntilIdle()

        coVerify { articleDao.delete(testArticle) }
        assertEquals("Article deleted", viewModel.snackbarMessage.value)
    }

    // --- undoDelete ---

    @Test
    fun `undoDelete re-inserts article`() = runTest {
        viewModel.undoDelete(testArticle)
        advanceUntilIdle()

        coVerify { articleDao.insert(testArticle) }
        assertNull(viewModel.snackbarMessage.value)
    }

    // --- markAsPlayed / markAsUnplayed ---

    @Test
    fun `markAsPlayed updates status to PLAYED`() = runTest {
        viewModel.markAsPlayed(testArticle)
        advanceUntilIdle()

        coVerify { articleDao.updateStatus(1L, "PLAYED") }
    }

    @Test
    fun `markAsUnplayed updates status to READY and resets position`() = runTest {
        viewModel.markAsUnplayed(testArticle)
        advanceUntilIdle()

        coVerify {
            articleDao.updateStatus(1L, "READY")
            articleDao.updatePlaybackPosition(1L, 0, any())
        }
    }

    // --- clearSnackbar ---

    @Test
    fun `clearSnackbar resets message to null`() {
        every { authManager.isSignedIn() } returns false
        viewModel.handleSharedUrl("https://example.com")
        assertEquals("Please sign in with Google first", viewModel.snackbarMessage.value)

        viewModel.clearSnackbar()

        assertNull(viewModel.snackbarMessage.value)
    }

    // --- PlayerState ---

    @Test
    fun `initial player state is empty`() {
        val state = viewModel.playerState.value
        assertEquals(-1L, state.articleId)
        assertEquals("", state.title)
        assertEquals(false, state.isPlaying)
        assertEquals(0L, state.positionMs)
        assertEquals(0L, state.durationMs)
    }
}
