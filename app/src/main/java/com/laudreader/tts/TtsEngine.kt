package com.laudreader.tts

import com.laudreader.auth.GoogleAuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

class TtsEngine @Inject constructor(
    private val httpClient: OkHttpClient,
    private val authManager: GoogleAuthManager
) {

    companion object {
        private const val TTS_API_URL = "https://texttospeech.googleapis.com/v1/text:synthesize"
        private const val MAX_CHUNK_SIZE = 4900 // Stay under 5000 char API limit
        private const val VOICE_NAME = "en-US-Wavenet-D"
        private const val LANGUAGE_CODE = "en-US"
    }

    data class GenerationProgress(val current: Int, val total: Int) {
        val percent: Int get() = if (total == 0) 0 else (current * 100) / total
    }

    suspend fun generateAudio(
        text: String,
        outputFile: File,
        onProgress: suspend (GenerationProgress) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val chunks = splitIntoChunks(text)
        val tempFiles = mutableListOf<File>()

        try {
            for ((index, chunk) in chunks.withIndex()) {
                onProgress(GenerationProgress(index, chunks.size))

                val audioBytes = synthesizeChunk(chunk)
                val tempFile = File(outputFile.parent, "chunk_${index}.mp3")
                tempFile.writeBytes(audioBytes)
                tempFiles.add(tempFile)
            }

            // Concatenate all MP3 chunks into the final file
            FileOutputStream(outputFile).use { output ->
                for (tempFile in tempFiles) {
                    tempFile.inputStream().use { input ->
                        input.copyTo(output)
                    }
                }
            }

            onProgress(GenerationProgress(chunks.size, chunks.size))
            outputFile
        } finally {
            tempFiles.forEach { it.delete() }
        }
    }

    private suspend fun synthesizeChunk(text: String): ByteArray {
        val token = authManager.getAccessToken()
            ?: throw IllegalStateException("Not authenticated. Please sign in with Google.")

        val requestBody = JSONObject().apply {
            put("input", JSONObject().put("text", text))
            put("voice", JSONObject().apply {
                put("languageCode", LANGUAGE_CODE)
                put("name", VOICE_NAME)
            })
            put("audioConfig", JSONObject().apply {
                put("audioEncoding", "MP3")
                put("speakingRate", 1.0)
                put("pitch", 0.0)
            })
        }

        val request = Request.Builder()
            .url(TTS_API_URL)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            throw IllegalStateException("TTS API error ${response.code}: $errorBody")
        }

        val json = JSONObject(response.body?.string() ?: throw IllegalStateException("Empty TTS response"))
        val audioContent = json.getString("audioContent")
        return android.util.Base64.decode(audioContent, android.util.Base64.DEFAULT)
    }

    internal fun splitIntoChunks(text: String): List<String> {
        if (text.length <= MAX_CHUNK_SIZE) return listOf(text)

        val chunks = mutableListOf<String>()
        var remaining = text

        while (remaining.isNotEmpty()) {
            if (remaining.length <= MAX_CHUNK_SIZE) {
                chunks.add(remaining)
                break
            }

            // Find the last sentence boundary within the chunk size limit
            val searchArea = remaining.substring(0, MAX_CHUNK_SIZE)
            val sentenceEnd = findLastSentenceBoundary(searchArea)

            val splitAt = if (sentenceEnd > 0) sentenceEnd else MAX_CHUNK_SIZE
            chunks.add(remaining.substring(0, splitAt).trim())
            remaining = remaining.substring(splitAt).trim()
        }

        return chunks.filter { it.isNotEmpty() }
    }

    private fun findLastSentenceBoundary(text: String): Int {
        // Look for sentence-ending punctuation followed by whitespace
        val sentenceEnders = listOf(". ", "! ", "? ", ".\n", "!\n", "?\n")
        var lastBoundary = -1

        for (ender in sentenceEnders) {
            val idx = text.lastIndexOf(ender)
            if (idx > lastBoundary) {
                lastBoundary = idx + ender.length
            }
        }

        // If no sentence boundary found, try to split at a paragraph or line break
        if (lastBoundary <= 0) {
            val newlineIdx = text.lastIndexOf('\n')
            if (newlineIdx > 0) return newlineIdx + 1
        }

        return lastBoundary
    }
}
