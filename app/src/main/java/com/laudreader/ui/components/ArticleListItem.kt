package com.laudreader.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.laudreader.data.Article
import com.laudreader.data.ArticleStatus
import com.laudreader.ui.theme.GeneratingColor
import com.laudreader.ui.theme.PlayedColor
import com.laudreader.ui.theme.PlayingColor
import com.laudreader.ui.theme.ReadyColor

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArticleListItem(
    article: Article,
    isCurrentlyPlaying: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onMarkPlayed: () -> Unit,
    onMarkUnplayed: () -> Unit,
    onOpenUrl: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showMenu = true }
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status icon
        StatusIcon(article.status, isCurrentlyPlaying)

        Spacer(modifier = Modifier.width(12.dp))

        // Article info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = article.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (article.status == ArticleStatus.PLAYED && !isCurrentlyPlaying) {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = article.domain,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (article.audioFileSizeBytes > 0) {
                    Text(
                        text = formatFileSize(article.audioFileSizeBytes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (article.status == ArticleStatus.GENERATING) {
                    Text(
                        text = "${article.generationProgress}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = GeneratingColor
                    )
                }
            }

            // Generation progress bar
            if (article.status == ArticleStatus.GENERATING) {
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { article.generationProgress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = GeneratingColor,
                    trackColor = GeneratingColor.copy(alpha = 0.2f)
                )
            }

            // Playback progress (for played/playing articles)
            if (article.durationMs > 0 && article.status != ArticleStatus.GENERATING) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${formatTime(article.playbackPositionMs)} / ${formatTime(article.durationMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Context menu
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Open original URL") },
                onClick = {
                    showMenu = false
                    onOpenUrl()
                }
            )
            if (article.status == ArticleStatus.PLAYED) {
                DropdownMenuItem(
                    text = { Text("Mark as unplayed") },
                    onClick = {
                        showMenu = false
                        onMarkUnplayed()
                    }
                )
            } else if (article.status == ArticleStatus.READY || article.status == ArticleStatus.PLAYING) {
                DropdownMenuItem(
                    text = { Text("Mark as played") },
                    onClick = {
                        showMenu = false
                        onMarkPlayed()
                    }
                )
            }
            DropdownMenuItem(
                text = { Text("Delete") },
                onClick = {
                    showMenu = false
                    onDelete()
                }
            )
        }
    }
}

@Composable
private fun StatusIcon(status: ArticleStatus, isCurrentlyPlaying: Boolean) {
    val (icon, tint) = when {
        isCurrentlyPlaying -> Icons.Filled.Pause to PlayingColor
        status == ArticleStatus.GENERATING -> Icons.Filled.HourglassTop to GeneratingColor
        status == ArticleStatus.READY -> Icons.Filled.PlayArrow to ReadyColor
        status == ArticleStatus.PLAYING -> Icons.Filled.PlayArrow to PlayingColor
        status == ArticleStatus.PLAYED -> Icons.Filled.CheckCircle to PlayedColor
        else -> Icons.Filled.PlayArrow to ReadyColor
    }

    Icon(
        imageVector = icon,
        contentDescription = status.name,
        modifier = Modifier.size(28.dp),
        tint = tint
    )
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
