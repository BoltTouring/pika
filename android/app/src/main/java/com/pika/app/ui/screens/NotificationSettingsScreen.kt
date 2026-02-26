package com.pika.app.ui.screens

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pika.app.AppManager
import com.pika.app.rust.AppAction
import org.json.JSONObject
import java.io.File

/**
 * Notification settings screen – permission status, server URL, and re-register action.
 * Mirrors iOS NotificationSettingsView.swift.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun NotificationSettingsScreen(
    manager: AppManager,
    padding: PaddingValues,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val notificationsEnabled = remember { areNotificationsEnabled(ctx) }
    val notificationUrl = remember { readNotificationUrl(ctx) }
    var didReregister by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.padding(padding),
        topBar = {
            TopAppBar(
                title = { Text("Notifications") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            // — Push Notifications section ——————————————————————————————————
            item {
                SectionHeader("Push Notifications")

                ListItem(
                    headlineContent = { Text("Permission") },
                    trailingContent = {
                        Text(
                            text = if (notificationsEnabled) "Enabled" else "Disabled",
                            color = if (notificationsEnabled) Color(0xFF34C759)
                            else MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                )

                if (!notificationsEnabled) {
                    ListItem(
                        headlineContent = {
                            TextButton(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    ctx.startActivity(intent)
                                },
                            ) { Text("Open Settings") }
                        },
                        supportingContent = {
                            Text(
                                text = "Notifications are disabled. Tap to enable them.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }

            // — Notification Server section —————————————————————————————————
            item {
                SectionHeader("Notification Server")

                ListItem(
                    headlineContent = { Text("Server URL") },
                    trailingContent = {
                        Text(
                            text = notificationUrl,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 160.dp),
                        )
                    },
                )

                ListItem(
                    headlineContent = { Text("Re-register") },
                    supportingContent = {
                        Text(
                            text = "Re-register the device and re-subscribe to all your chats.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingContent = {
                        if (didReregister) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color(0xFF34C759),
                            )
                        } else {
                            TextButton(
                                onClick = {
                                    manager.dispatch(AppAction.ReregisterPush)
                                    didReregister = true
                                },
                            ) { Text("Re-register") }
                        }
                    },
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

private fun areNotificationsEnabled(ctx: Context): Boolean {
    val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    return nm.areNotificationsEnabled()
}

private fun readNotificationUrl(ctx: Context): String {
    runCatching {
        val config = File(ctx.filesDir, "pika_config.json")
        if (config.exists()) {
            val url = JSONObject(config.readText()).optString("notification_url", "").trim()
            if (url.isNotEmpty()) return url
        }
    }
    return "https://test.notifs.benthecarman.com"
}
