package com.laudreader.tts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.laudreader.R
import com.laudreader.data.ArticleDao
import com.laudreader.data.ArticleStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class TtsService : Service() {

    companion object {
        const val EXTRA_ARTICLE_ID = "article_id"
        private const val CHANNEL_ID = "tts_generation"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context, articleId: Long) {
            val intent = Intent(context, TtsService::class.java).apply {
                putExtra(EXTRA_ARTICLE_ID, articleId)
            }
            context.startForegroundService(intent)
        }
    }

    @Inject lateinit var ttsEngine: TtsEngine
    @Inject lateinit var articleDao: ArticleDao

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val activeJobs = mutableMapOf<Long, Job>()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val articleId = intent?.getLongExtra(EXTRA_ARTICLE_ID, -1) ?: -1
        if (articleId == -1L) {
            stopSelfIfIdle()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification("Generating audio..."))

        val job = scope.launch {
            generateAudioForArticle(articleId)
            activeJobs.remove(articleId)
            stopSelfIfIdle()
        }
        activeJobs[articleId] = job

        return START_NOT_STICKY
    }

    private suspend fun generateAudioForArticle(articleId: Long) {
        val article = articleDao.getById(articleId) ?: return

        val audioDir = File(filesDir, "audio")
        audioDir.mkdirs()
        val outputFile = File(audioDir, "article_${articleId}.mp3")

        try {
            ttsEngine.generateAudio(
                text = article.extractedText,
                outputFile = outputFile,
                onProgress = { progress ->
                    articleDao.updateGenerationProgress(articleId, progress.percent)
                    updateNotification("Generating: ${article.title} (${progress.percent}%)")
                }
            )

            articleDao.updateAudioReady(
                id = articleId,
                path = outputFile.absolutePath,
                size = outputFile.length(),
                durationMs = 0 // Duration will be determined by ExoPlayer on first load
            )
        } catch (e: Exception) {
            // On failure, delete partial file and reset article status
            outputFile.delete()
            articleDao.updateStatus(articleId, ArticleStatus.GENERATING)
        }
    }

    private fun stopSelfIfIdle() {
        if (activeJobs.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "TTS Generation",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows progress while generating audio"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LaudReader")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
