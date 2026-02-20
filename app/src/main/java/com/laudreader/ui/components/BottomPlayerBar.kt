package com.laudreader.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward15
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay15
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.laudreader.ui.PlayerState

@Composable
fun BottomPlayerBar(
    playerState: PlayerState,
    onPlayPause: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 3.dp,
        shadowElevation = 8.dp
    ) {
        Column {
            // Progress bar across full width
            if (playerState.durationMs > 0) {
                LinearProgressIndicator(
                    progress = { (playerState.positionMs.toFloat() / playerState.durationMs).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Title and time
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Text(
                        text = playerState.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (playerState.durationMs > 0) {
                        Text(
                            text = "${formatTime(playerState.positionMs)} / ${formatTime(playerState.durationMs)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Controls
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onSeekBack) {
                        Icon(
                            imageVector = Icons.Filled.Replay15,
                            contentDescription = "Skip back 15 seconds",
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    IconButton(onClick = onPlayPause) {
                        Icon(
                            imageVector = if (playerState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    IconButton(onClick = onSeekForward) {
                        Icon(
                            imageVector = Icons.Filled.Forward15,
                            contentDescription = "Skip forward 15 seconds",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
