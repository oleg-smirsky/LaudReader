package com.laudreader.player

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.laudreader.data.ArticleDao
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject lateinit var articleDao: ArticleDao

    private var mediaSession: MediaSession? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var positionTracker: Job? = null
    private var currentArticleId: Long = -1

    override fun onCreate() {
        super.onCreate()

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    startPositionTracking()
                } else {
                    stopPositionTracking()
                    saveCurrentPosition()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    scope.launch {
                        saveCurrentPosition()
                        if (currentArticleId > 0) {
                            articleDao.updateStatus(currentArticleId, com.laudreader.data.ArticleStatus.PLAYED)
                        }
                    }
                }
            }
        })

        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val articleId = intent?.getLongExtra("article_id", -1) ?: -1
        val seekToMs = intent?.getLongExtra("seek_to_ms", 0) ?: 0
        val audioPath = intent?.getStringExtra("audio_path")

        if (articleId > 0 && audioPath != null) {
            currentArticleId = articleId
            val player = mediaSession?.player ?: return super.onStartCommand(intent, flags, startId)

            val mediaItem = MediaItem.fromUri("file://$audioPath")
            player.setMediaItem(mediaItem)
            player.prepare()
            if (seekToMs > 0) {
                player.seekTo(seekToMs)
            }
            player.play()
        }

        return super.onStartCommand(intent, flags, startId)
    }

    private fun startPositionTracking() {
        positionTracker?.cancel()
        positionTracker = scope.launch {
            while (true) {
                delay(1000)
                saveCurrentPosition()
            }
        }
    }

    private fun stopPositionTracking() {
        positionTracker?.cancel()
        positionTracker = null
    }

    private fun saveCurrentPosition() {
        val player = mediaSession?.player ?: return
        if (currentArticleId <= 0) return

        scope.launch {
            articleDao.updatePlaybackPosition(
                id = currentArticleId,
                positionMs = player.currentPosition
            )
        }
    }

    override fun onDestroy() {
        stopPositionTracking()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        scope.cancel()
        super.onDestroy()
    }
}
