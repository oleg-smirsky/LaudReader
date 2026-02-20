package com.laudreader.extractor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.dankito.readability4j.Readability4J
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import javax.inject.Inject

data class ExtractedArticle(
    val title: String,
    val domain: String,
    val textContent: String
)

class ArticleExtractor @Inject constructor(
    private val httpClient: OkHttpClient
) {

    suspend fun extract(url: String): ExtractedArticle = withContext(Dispatchers.IO) {
        val html = fetchHtml(url)
        val domain = URI(url).host?.removePrefix("www.") ?: url

        val readability = Readability4J(url, html)
        val article = readability.parse()

        val title = article.title?.takeIf { it.isNotBlank() } ?: domain
        val text = article.textContent?.trim()
            ?: throw IllegalStateException("Failed to extract article text from $url")

        ExtractedArticle(
            title = title,
            domain = domain,
            textContent = text
        )
    }

    private fun fetchHtml(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IllegalStateException("HTTP ${response.code} fetching $url")
        }
        return response.body?.string() ?: throw IllegalStateException("Empty response body from $url")
    }
}
