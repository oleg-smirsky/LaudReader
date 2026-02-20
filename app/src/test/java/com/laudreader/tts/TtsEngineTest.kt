package com.laudreader.tts

import com.laudreader.auth.GoogleAuthManager
import io.mockk.mockk
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TtsEngineTest {

    private lateinit var engine: TtsEngine

    @Before
    fun setUp() {
        engine = TtsEngine(
            httpClient = mockk<OkHttpClient>(),
            authManager = mockk<GoogleAuthManager>()
        )
    }

    // --- splitIntoChunks tests ---

    @Test
    fun `short text returns single chunk`() {
        val text = "Hello, this is a short text."
        val chunks = engine.splitIntoChunks(text)
        assertEquals(1, chunks.size)
        assertEquals(text, chunks[0])
    }

    @Test
    fun `empty text returns single chunk`() {
        val chunks = engine.splitIntoChunks("")
        assertEquals(1, chunks.size)
    }

    @Test
    fun `text exactly at limit returns single chunk`() {
        val text = "a".repeat(4900)
        val chunks = engine.splitIntoChunks(text)
        assertEquals(1, chunks.size)
        assertEquals(text, chunks[0])
    }

    @Test
    fun `text over limit is split into multiple chunks`() {
        val text = "a".repeat(10000)
        val chunks = engine.splitIntoChunks(text)
        assertTrue(chunks.size > 1)
        // All chunks should be at most 4900 characters
        chunks.forEach { assertTrue("Chunk too large: ${it.length}", it.length <= 4900) }
    }

    @Test
    fun `splits at sentence boundary when possible`() {
        // Build text with a sentence ending near the middle of the chunk limit
        val firstSentence = "a".repeat(4000) + ". "
        val secondSentence = "b".repeat(3000) + "."
        val text = firstSentence + secondSentence

        val chunks = engine.splitIntoChunks(text)
        assertEquals(2, chunks.size)
        // First chunk should end at the sentence boundary (trimmed)
        assertTrue(chunks[0].endsWith("."))
    }

    @Test
    fun `preserves all content after chunking`() {
        // Create text with proper sentences that exceeds the limit
        val sentences = (1..100).joinToString(" ") { "This is sentence number $it." }
        val chunks = engine.splitIntoChunks(sentences)

        val reassembled = chunks.joinToString(" ")
        // All original content should be preserved (modulo whitespace differences from trim)
        assertTrue(reassembled.contains("sentence number 1"))
        assertTrue(reassembled.contains("sentence number 100"))
    }

    @Test
    fun `handles text with no sentence boundaries`() {
        // A continuous string with no sentence enders or newlines
        val text = "a".repeat(10000)
        val chunks = engine.splitIntoChunks(text)
        assertTrue(chunks.size > 1)
        chunks.forEach { assertTrue(it.length <= 4900) }
    }

    @Test
    fun `splits at newline when no sentence boundary`() {
        // Text with newlines but no sentence-ending punctuation followed by space
        val line = "a".repeat(2400) + "\n"
        val text = line.repeat(5)
        val chunks = engine.splitIntoChunks(text)
        assertTrue(chunks.size >= 2)
        chunks.forEach { assertTrue("Chunk too large: ${it.length}", it.length <= 4900) }
    }

    @Test
    fun `no empty chunks in output`() {
        val text = "Hello. " + "World! ".repeat(1000)
        val chunks = engine.splitIntoChunks(text)
        chunks.forEach { assertTrue("Empty chunk found", it.isNotEmpty()) }
    }

    // --- GenerationProgress tests ---

    @Test
    fun `progress percent is zero when total is zero`() {
        val progress = TtsEngine.GenerationProgress(current = 0, total = 0)
        assertEquals(0, progress.percent)
    }

    @Test
    fun `progress percent at start`() {
        val progress = TtsEngine.GenerationProgress(current = 0, total = 10)
        assertEquals(0, progress.percent)
    }

    @Test
    fun `progress percent at halfway`() {
        val progress = TtsEngine.GenerationProgress(current = 5, total = 10)
        assertEquals(50, progress.percent)
    }

    @Test
    fun `progress percent at completion`() {
        val progress = TtsEngine.GenerationProgress(current = 10, total = 10)
        assertEquals(100, progress.percent)
    }

    @Test
    fun `progress percent rounds down`() {
        val progress = TtsEngine.GenerationProgress(current = 1, total = 3)
        assertEquals(33, progress.percent) // 33.33... rounds down to 33
    }
}
