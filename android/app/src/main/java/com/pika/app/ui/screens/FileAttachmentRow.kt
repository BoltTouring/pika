package com.pika.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.pika.app.rust.ChatMediaAttachment
import java.io.File

/**
 * Renders a generic file attachment inside a message bubble – filename, MIME type, and a
 * share/download action. Mirrors iOS FileAttachmentRow.swift.
 */
@Composable
fun FileAttachmentRow(
    attachment: ChatMediaAttachment,
    isMine: Boolean,
    onDownload: () -> Unit,
) {
    val ctx = LocalContext.current
    val textColor = if (isMine) Color.White else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .widthIn(max = 240.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.AttachFile,
            contentDescription = null,
            tint = textColor.copy(alpha = 0.80f),
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = attachment.filename,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = attachment.mimeType,
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(alpha = 0.60f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        val localPath = attachment.localPath
        if (localPath != null) {
            IconButton(
                onClick = {
                    val file = File(localPath)
                    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.provider", file)
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = attachment.mimeType
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    ctx.startActivity(Intent.createChooser(intent, "Share ${attachment.filename}"))
                },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share",
                    tint = textColor,
                )
            }
        } else {
            IconButton(onClick = onDownload, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.DownloadForOffline,
                    contentDescription = "Download",
                    tint = textColor,
                )
            }
        }
    }
}
