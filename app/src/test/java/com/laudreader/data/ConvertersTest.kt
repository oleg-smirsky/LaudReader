package com.laudreader.data

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ConvertersTest {

    private lateinit var converters: Converters

    @Before
    fun setUp() {
        converters = Converters()
    }

    @Test
    fun `fromArticleStatus converts GENERATING to string`() {
        assertEquals("GENERATING", converters.fromArticleStatus(ArticleStatus.GENERATING))
    }

    @Test
    fun `fromArticleStatus converts READY to string`() {
        assertEquals("READY", converters.fromArticleStatus(ArticleStatus.READY))
    }

    @Test
    fun `fromArticleStatus converts PLAYING to string`() {
        assertEquals("PLAYING", converters.fromArticleStatus(ArticleStatus.PLAYING))
    }

    @Test
    fun `fromArticleStatus converts PLAYED to string`() {
        assertEquals("PLAYED", converters.fromArticleStatus(ArticleStatus.PLAYED))
    }

    @Test
    fun `toArticleStatus converts string to GENERATING`() {
        assertEquals(ArticleStatus.GENERATING, converters.toArticleStatus("GENERATING"))
    }

    @Test
    fun `toArticleStatus converts string to READY`() {
        assertEquals(ArticleStatus.READY, converters.toArticleStatus("READY"))
    }

    @Test
    fun `toArticleStatus converts string to PLAYING`() {
        assertEquals(ArticleStatus.PLAYING, converters.toArticleStatus("PLAYING"))
    }

    @Test
    fun `toArticleStatus converts string to PLAYED`() {
        assertEquals(ArticleStatus.PLAYED, converters.toArticleStatus("PLAYED"))
    }

    @Test
    fun `round trip preserves all statuses`() {
        for (status in ArticleStatus.entries) {
            val asString = converters.fromArticleStatus(status)
            val backToStatus = converters.toArticleStatus(asString)
            assertEquals(status, backToStatus)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `toArticleStatus throws for invalid string`() {
        converters.toArticleStatus("INVALID")
    }
}
