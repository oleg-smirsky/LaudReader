package com.laudreader.extractor

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ArticleExtractorTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var extractor: ArticleExtractor

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        extractor = ArticleExtractor(OkHttpClient())
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `extracts title and text from valid HTML`() = runTest {
        val html = """
            <html>
            <head><title>Test Article Title</title></head>
            <body>
                <article>
                    <h1>Test Article Title</h1>
                    <p>This is the main article content. It has enough text to be recognized by
                    the readability parser as actual article content. We need to make sure there
                    is a substantial amount of text here so the parser doesn't skip it. Let's add
                    more sentences to make this realistic. Articles typically have multiple paragraphs
                    with significant content that readers want to consume.</p>
                    <p>This is a second paragraph with additional content. The readability algorithm
                    looks for content-dense areas of the page and tries to extract them. Having
                    multiple paragraphs helps it identify the main content area correctly.</p>
                </article>
            </body>
            </html>
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(html).setResponseCode(200))
        val url = mockWebServer.url("/article").toString()

        val result = extractor.extract(url)

        assertTrue(result.title.isNotBlank())
        assertTrue(result.textContent.isNotBlank())
        assertTrue(result.textContent.contains("main article content"))
    }

    @Test
    fun `extracts domain without www prefix`() = runTest {
        val html = """
            <html>
            <head><title>Title</title></head>
            <body>
                <article>
                    <p>This is the main article content with enough text to be picked up by the
                    readability parser. We need substantial text here for reliable extraction.
                    Adding more sentences to ensure the parser recognizes this as content.</p>
                </article>
            </body>
            </html>
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(html).setResponseCode(200))
        val url = mockWebServer.url("/article").toString()

        val result = extractor.extract(url)
        // MockWebServer uses localhost or 127.0.0.1
        assertTrue(result.domain.isNotBlank())
        assertTrue(!result.domain.startsWith("www."))
    }

    @Test(expected = IllegalStateException::class)
    fun `throws on HTTP error`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(404))
        val url = mockWebServer.url("/not-found").toString()

        extractor.extract(url)
    }

    @Test(expected = IllegalStateException::class)
    fun `throws on empty body`() = runTest {
        mockWebServer.enqueue(MockResponse().setBody("").setResponseCode(200))
        val url = mockWebServer.url("/empty").toString()

        extractor.extract(url)
    }

    @Test
    fun `sends browser user agent`() = runTest {
        val html = """
            <html>
            <head><title>UA Test</title></head>
            <body>
                <article>
                    <p>Article content with sufficient text length for extraction purposes.
                    The readability parser needs enough text to identify this as content.
                    Adding more sentences here to ensure reliable extraction behavior.</p>
                </article>
            </body>
            </html>
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(html).setResponseCode(200))
        val url = mockWebServer.url("/article").toString()

        extractor.extract(url)

        val request = mockWebServer.takeRequest()
        val userAgent = request.getHeader("User-Agent") ?: ""
        assertTrue("Expected browser User-Agent", userAgent.contains("Mozilla"))
    }

    @Test
    fun `falls back to domain as title when title is blank`() = runTest {
        val html = """
            <html>
            <head><title></title></head>
            <body>
                <article>
                    <p>This article has no proper title set. The extractor should fall back
                    to using the domain name as the title when the title element is empty.
                    We need enough text here for the readability parser to work properly
                    with this particular test case and extract the content.</p>
                </article>
            </body>
            </html>
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(html).setResponseCode(200))
        val url = mockWebServer.url("/article").toString()

        val result = extractor.extract(url)
        // When title is blank, it should fall back to domain
        assertTrue(result.title.isNotBlank())
    }
}
