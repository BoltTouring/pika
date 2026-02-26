package com.pika.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs

/**
 * Full-screen image viewer with pinch-to-zoom and drag-to-dismiss.
 * Mirrors the behaviour of iOS FullscreenImageViewer.swift.
 */
@Composable
fun FullscreenImageViewer(
    localPath: String,
    filename: String,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val dragY = remember { Animatable(0f) }
    val backgroundAlpha = (1f - (abs(dragY.value) / 400f).coerceIn(0f, 1f))

    var scale by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = backgroundAlpha))
            .pointerInput(Unit) {
                detectTransformGestures { _, panDelta, zoomDelta, _ ->
                    val newScale = (scale * zoomDelta).coerceIn(0.5f, 6f)
                    scale = newScale
                    if (scale > 1.05f) {
                        // Panning while zoomed in
                        pan += panDelta
                    } else {
                        // Not zoomed in – drag-to-dismiss on vertical swipe
                        scope.launch { dragY.snapTo(dragY.value + panDelta.y) }
                        if (abs(dragY.value) > 130f) {
                            onDismiss()
                        }
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    // Double-tap to reset zoom
                    scale = 1f
                    pan = Offset.Zero
                    scope.launch { dragY.snapTo(0f) }
                })
            },
    ) {
        AsyncImage(
            model = File(localPath),
            contentDescription = filename,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = pan.x
                    translationY = pan.y + dragY.value
                },
        )

        // Close button (top-left)
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp),
        ) {
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White,
                )
            }
        }
    }

    // Snap back if drag is released without crossing dismiss threshold
    LaunchedEffect(dragY.value) {
        if (!dragY.isRunning && abs(dragY.value) < 130f && dragY.value != 0f) {
            dragY.animateTo(0f)
        }
    }
}
