package com.pika.app.ui.screens

import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pika.app.rust.ChatMediaAttachment
import com.pika.app.ui.theme.PikaBlue
import kotlinx.coroutines.delay

/**
 * Renders a voice message bubble with play/pause, an animated waveform preview, and an elapsed
 * time counter – mirroring the iOS VoiceMessageView.swift.
 *
 * When the attachment is not yet downloaded (localPath == null) it shows a download button
 * so the caller can dispatch AppAction.DownloadChatMedia.
 */
@Composable
fun VoiceMessageRow(
    attachment: ChatMediaAttachment,
    isMine: Boolean,
    onDownload: () -> Unit,
) {
    val textColor = if (isMine) Color.White else MaterialTheme.colorScheme.onSurfaceVariant

    val localPath = attachment.localPath
    if (localPath == null) {
        NotDownloadedRow(attachment = attachment, isMine = isMine, textColor = textColor, onDownload = onDownload)
        return
    }

    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var currentMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    val player = remember { MediaPlayer() }

    DisposableEffect(localPath) {
        runCatching {
            player.setDataSource(localPath)
            player.prepare()
            durationMs = player.duration.toLong().coerceAtLeast(0L)
        }
        player.setOnCompletionListener {
            isPlaying = false
            progress = 0f
            currentMs = 0L
        }
        onDispose { player.release() }
    }

    // Poll playback position every 50 ms while playing
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentMs = player.currentPosition.toLong()
            progress = if (durationMs > 0L) currentMs.toFloat() / durationMs.toFloat() else 0f
            delay(50)
        }
    }

    val displayMs = if (isPlaying || currentMs > 0L) currentMs else durationMs
    val timeLabel = remember(displayMs) {
        val totalSec = (displayMs / 1000).toInt()
        "%d:%02d".format(totalSec / 60, totalSec % 60)
    }

    Row(
        modifier = Modifier
            .padding(horizontal = 10.dp, vertical = 10.dp)
            .widthIn(min = 180.dp, max = 220.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = {
                if (isPlaying) {
                    player.pause()
                    isPlaying = false
                } else {
                    if (progress == 0f) player.seekTo(0)
                    player.start()
                    isPlaying = true
                }
            },
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = textColor,
            )
        }

        StaticWaveform(
            progress = progress,
            isMine = isMine,
            modifier = Modifier
                .weight(1f)
                .height(28.dp),
        )

        Text(
            text = timeLabel,
            style = MaterialTheme.typography.labelMedium,
            color = textColor.copy(alpha = 0.85f),
            modifier = Modifier.width(38.dp),
        )
    }
}

@Composable
private fun NotDownloadedRow(
    attachment: ChatMediaAttachment,
    isMine: Boolean,
    textColor: Color,
    onDownload: () -> Unit,
) {
    Row(
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .widthIn(min = 180.dp, max = 220.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.GraphicEq,
            contentDescription = null,
            tint = textColor.copy(alpha = 0.7f),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Voice Message",
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
            )
            Text(
                text = attachment.mimeType,
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(alpha = 0.6f),
            )
        }
        IconButton(onClick = onDownload, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Default.DownloadForOffline,
                contentDescription = "Download",
                tint = textColor,
            )
        }
    }
}

/**
 * Decorative static waveform – 20 bars at fixed heights, progressively highlighted.
 * Mirrors the iOS StaticWaveformView.
 */
@Composable
private fun StaticWaveform(
    progress: Float,
    isMine: Boolean,
    modifier: Modifier = Modifier,
) {
    val barHeights = remember {
        listOf(
            0.40f, 0.70f, 0.50f, 0.90f, 0.60f, 0.80f, 0.45f, 1.00f, 0.70f, 0.65f,
            0.80f, 0.55f, 0.90f, 0.65f, 0.75f, 0.45f, 0.85f, 0.60f, 0.55f, 0.70f,
        )
    }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        barHeights.forEachIndexed { index, height ->
            val barFrac = (index + 1).toFloat() / barHeights.size.toFloat()
            val played = barFrac <= progress
            val color = if (isMine) {
                if (played) Color.White else Color.White.copy(alpha = 0.35f)
            } else {
                if (played) PikaBlue else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.30f)
            }
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight(height)
                    .background(color = color, shape = RoundedCornerShape(1.5.dp)),
            )
        }
    }
}
