package com.laudreader.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.laudreader.auth.GoogleAuthManager
import com.laudreader.data.Article
import com.laudreader.data.ArticleDao
import com.laudreader.data.ArticleStatus
import com.laudreader.extractor.ArticleExtractor
import com.laudreader.player.PlaybackService
import com.laudreader.tts.TtsService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlayerState(
    val articleId: Long = -1,
    val title: String = "",
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0
)

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val articleDao: ArticleDao,
    private val articleExtractor: ArticleExtractor,
    val authManager: GoogleAuthManager
) : ViewModel() {

    val articles: StateFlow<List<Article>> = articleDao.getAllArticles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private var mediaController: MediaController? = null

    fun connectMediaController() {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture.addListener({
            mediaController = controllerFuture.get()
            mediaController?.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _playerState.value = _playerState.value.copy(isPlaying = isPlaying)
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        _playerState.value = PlayerState()
                    }
                }
            })
            startPositionPolling()
        }, MoreExecutors.directExecutor())
    }

    private fun startPositionPolling() {
        viewModelScope.launch {
            while (true) {
                delay(500)
                val controller = mediaController ?: continue
                if (controller.isPlaying) {
                    _playerState.value = _playerState.value.copy(
                        positionMs = controller.currentPosition,
                        durationMs = controller.duration.coerceAtLeast(0)
                    )
                }
            }
        }
    }

    fun handleSharedUrl(url: String) {
        if (!authManager.isSignedIn()) {
            _snackbarMessage.value = "Please sign in with Google first"
            return
        }

        viewModelScope.launch {
            try {
                _snackbarMessage.value = "Article added"

                val extracted = articleExtractor.extract(url)
                val articleId = articleDao.insert(
                    Article(
                        title = extracted.title,
                        sourceUrl = url,
                        domain = extracted.domain,
                        extractedText = extracted.textContent,
                        status = ArticleStatus.GENERATING
                    )
                )

                TtsService.start(context, articleId)
            } catch (e: Exception) {
                _snackbarMessage.value = "Failed to add article: ${e.message}"
            }
        }
    }

    fun onArticleTap(article: Article) {
        when {
            // Tap the currently playing article -> pause
            _playerState.value.articleId == article.id && _playerState.value.isPlaying -> {
                mediaController?.pause()
            }
            // Tap a paused article that was playing -> resume
            _playerState.value.articleId == article.id && !_playerState.value.isPlaying -> {
                mediaController?.play()
            }
            // Tap a different article that is ready
            article.status == ArticleStatus.READY || article.status == ArticleStatus.PLAYED || article.status == ArticleStatus.PLAYING -> {
                playArticle(article)
            }
            // Article is still generating
            article.status == ArticleStatus.GENERATING -> {
                _snackbarMessage.value = "Still generating audio..."
            }
        }
    }

    private fun playArticle(article: Article) {
        val path = article.audioFilePath ?: return

        _playerState.value = PlayerState(
            articleId = article.id,
            title = article.title,
            isPlaying = true,
            positionMs = article.playbackPositionMs
        )

        viewModelScope.launch {
            articleDao.updateStatus(article.id, ArticleStatus.PLAYING.name)
        }

        val intent = Intent(context, PlaybackService::class.java).apply {
            putExtra("article_id", article.id)
            putExtra("audio_path", path)
            putExtra("seek_to_ms", article.playbackPositionMs)
        }
        context.startForegroundService(intent)
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            controller.play()
        }
    }

    fun seekBack15s() {
        val controller = mediaController ?: return
        controller.seekTo((controller.currentPosition - 15_000).coerceAtLeast(0))
    }

    fun seekForward15s() {
        val controller = mediaController ?: return
        controller.seekTo(controller.currentPosition + 15_000)
    }

    fun deleteArticle(article: Article) {
        viewModelScope.launch {
            // Stop playback if this article is playing
            if (_playerState.value.articleId == article.id) {
                mediaController?.stop()
                _playerState.value = PlayerState()
            }

            // Delete audio file
            article.audioFilePath?.let { java.io.File(it).delete() }

            articleDao.delete(article)
            _snackbarMessage.value = "Article deleted"
        }
    }

    fun undoDelete(article: Article) {
        viewModelScope.launch {
            articleDao.insert(article)
            _snackbarMessage.value = null
        }
    }

    fun markAsPlayed(article: Article) {
        viewModelScope.launch {
            articleDao.updateStatus(article.id, ArticleStatus.PLAYED.name)
        }
    }

    fun markAsUnplayed(article: Article) {
        viewModelScope.launch {
            articleDao.updateStatus(article.id, ArticleStatus.READY.name)
            articleDao.updatePlaybackPosition(article.id, 0, System.currentTimeMillis())
        }
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    override fun onCleared() {
        mediaController?.release()
        super.onCleared()
    }
}
