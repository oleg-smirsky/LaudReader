package com.laudreader.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.laudreader.data.Article
import com.laudreader.ui.components.ArticleListItem
import com.laudreader.ui.components.BottomPlayerBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onSignInClick: () -> Unit
) {
    val articles by viewModel.articles.collectAsState()
    val playerState by viewModel.playerState.collectAsState()
    val snackbarMessage by viewModel.snackbarMessage.collectAsState()
    val context = LocalContext.current

    val snackbarHostState = remember { SnackbarHostState() }

    // Show snackbar messages
    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("LaudReader") },
                actions = {
                    IconButton(onClick = onSignInClick) {
                        Icon(
                            imageVector = Icons.Filled.AccountCircle,
                            contentDescription = "Sign in"
                        )
                    }
                }
            )
        },
        bottomBar = {
            if (playerState.articleId > 0) {
                BottomPlayerBar(
                    playerState = playerState,
                    onPlayPause = viewModel::togglePlayPause,
                    onSeekBack = viewModel::seekBack15s,
                    onSeekForward = viewModel::seekForward15s
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (articles.isEmpty()) {
                EmptyState(
                    isSignedIn = viewModel.authManager.isSignedIn(),
                    onSignInClick = onSignInClick
                )
            } else {
                ArticleList(
                    articles = articles,
                    currentPlayingId = playerState.articleId,
                    isPlaying = playerState.isPlaying,
                    onArticleTap = viewModel::onArticleTap,
                    onDelete = { article ->
                        val deletedArticle = article.copy()
                        viewModel.deleteArticle(article)
                    },
                    onMarkPlayed = viewModel::markAsPlayed,
                    onMarkUnplayed = viewModel::markAsUnplayed,
                    onOpenUrl = { url ->
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                )
            }
        }
    }
}

@Composable
private fun EmptyState(
    isSignedIn: Boolean,
    onSignInClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (isSignedIn) {
                    "No articles yet"
                } else {
                    "Sign in to get started"
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (isSignedIn) {
                    "Share a URL from your browser to add an article"
                } else {
                    "Sign in with your Google account to use Cloud TTS"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArticleList(
    articles: List<Article>,
    currentPlayingId: Long,
    isPlaying: Boolean,
    onArticleTap: (Article) -> Unit,
    onDelete: (Article) -> Unit,
    onMarkPlayed: (Article) -> Unit,
    onMarkUnplayed: (Article) -> Unit,
    onOpenUrl: (String) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(articles, key = { it.id }) { article ->
            val dismissState = rememberSwipeToDismissBoxState(
                confirmValueChange = { dismissValue ->
                    if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                        onDelete(article)
                        true
                    } else {
                        false
                    }
                }
            )

            SwipeToDismissBox(
                state = dismissState,
                backgroundContent = {
                    SwipeDeleteBackground()
                },
                enableDismissFromStartToEnd = false,
                modifier = Modifier.animateContentSize()
            ) {
                ArticleListItem(
                    article = article,
                    isCurrentlyPlaying = article.id == currentPlayingId && isPlaying,
                    onClick = { onArticleTap(article) },
                    onDelete = { onDelete(article) },
                    onMarkPlayed = { onMarkPlayed(article) },
                    onMarkUnplayed = { onMarkUnplayed(article) },
                    onOpenUrl = { onOpenUrl(article.sourceUrl) }
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(start = 56.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )
        }
    }
}

@Composable
private fun SwipeDeleteBackground() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        Text(
            text = "Delete",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
