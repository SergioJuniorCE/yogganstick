package com.yogganstick.ui

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.yogganstick.EditorUiState
import com.yogganstick.media.MediaKind
import com.yogganstick.media.SourceFrame
import com.yogganstick.media.StickerMath
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun EditorScreen(
    editor: EditorUiState,
    onClose: () -> Unit,
    onPreviewAt: (Long) -> Unit,
    onSave: (Float, Float, Float, Boolean, Long) -> Unit,
    onDismissError: () -> Unit,
) {
    val source = editor.source
    var zoom by rememberSaveable(editor.file.path) { mutableFloatStateOf(1f) }
    var panX by rememberSaveable(editor.file.path) { mutableFloatStateOf(0f) }
    var panY by rememberSaveable(editor.file.path) { mutableFloatStateOf(0f) }
    var outline by rememberSaveable(editor.file.path) { mutableStateOf(true) }
    var trimStart by rememberSaveable(editor.file.path) { mutableLongStateOf(0L) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    val maxStart = (source.durationMs - MAX_CLIP_MS).coerceAtLeast(0L)
    val clipLength = if (source.isAnimated) {
        min(MAX_CLIP_MS, source.durationMs - trimStart).coerceAtLeast(0L)
    } else {
        0L
    }
    val kindLabel = when (source.kind) {
        MediaKind.PHOTO -> "PHOTO"
        MediaKind.GIF -> "GIF"
        MediaKind.VIDEO -> "VIDEO"
    }
    val detail = when {
        !source.isAnimated -> "Still image \u00B7 ${source.width} \u00D7 ${source.height}"
        source.kind == MediaKind.GIF -> "Animated GIF \u00B7 ${formatDuration(clipLength)} clip"
        else -> "Video \u00B7 ${formatDuration(clipLength)} clip"
    }
    val hasTransform = zoom > 1.001f || panX != 0f || panY != 0f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(EditorBackground)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose, enabled = !editor.isSaving) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel", tint = EditorText)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "New sticker",
                        style = MaterialTheme.typography.titleLarge,
                        color = EditorText,
                    )
                    Text(
                        text = editor.packName,
                        style = MaterialTheme.typography.bodySmall,
                        color = EditorText.copy(alpha = 0.62f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (hasTransform) {
                    TextButton(
                        onClick = {
                            zoom = 1f
                            panX = 0f
                            panY = 0f
                        },
                    ) {
                        Text("Reset", color = PinkMist)
                    }
                }
            }

            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                val side = minOf(maxWidth - 32.dp, maxHeight - 42.dp, 430.dp)
                    .coerceAtLeast(140.dp)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(side)
                            .clip(RoundedCornerShape(30.dp))
                            .background(EditorPanel)
                            .onSizeChanged { canvasSize = it }
                            .pointerInput(source.width, source.height) {
                                detectTransformGestures { _, pan, gestureZoom, _ ->
                                    if (canvasSize.width == 0 || canvasSize.height == 0) {
                                        return@detectTransformGestures
                                    }
                                    val nextZoom = (zoom * gestureZoom).coerceIn(1f, 8f)
                                    val nextPanX = panX + pan.x / canvasSize.width
                                    val nextPanY = panY + pan.y / canvasSize.height
                                    val (clampedX, clampedY) = StickerMath.clampPan(
                                        source.width,
                                        source.height,
                                        nextZoom,
                                        nextPanX,
                                        nextPanY,
                                    )
                                    zoom = nextZoom
                                    panX = clampedX
                                    panY = clampedY
                                }
                            },
                    ) {
                        Checkerboard(
                            modifier = Modifier.fillMaxSize(),
                            light = false,
                            cellSize = 30.dp,
                        )
                        AnimatedStickerCanvas(
                            frames = editor.previewFrames,
                            fallback = editor.preview,
                            playing = !editor.isSaving,
                            mediaWidth = source.width,
                            mediaHeight = source.height,
                            zoom = zoom,
                            panX = panX,
                            panY = panY,
                            outline = outline,
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (editor.previewLoading) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(44.dp)
                                    .background(EditorBackground.copy(alpha = 0.75f), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = PinkMist,
                                    strokeWidth = 2.5.dp,
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = EditorBackground.copy(alpha = 0.72f),
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(12.dp),
                        ) {
                            Text(
                                text = kindLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = EditorText,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Pinch to zoom \u00B7 drag to crop",
                        style = MaterialTheme.typography.labelSmall,
                        color = EditorText.copy(alpha = 0.45f),
                    )
                }
            }

            Surface(
                color = EditorPanel,
                shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = EditorPanelHigh,
                        ) {
                            Text(
                                text = if (source.isAnimated) "ANIMATED" else "STICKER",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (source.isAnimated) PinkMist else EditorText,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = EditorText.copy(alpha = 0.7f),
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "White outline",
                                style = MaterialTheme.typography.titleSmall,
                                color = EditorText,
                            )
                            Text(
                                "Adds a sticker-style border around the edges.",
                                style = MaterialTheme.typography.bodySmall,
                                color = EditorText.copy(alpha = 0.55f),
                            )
                        }
                        Switch(
                            checked = outline,
                            onCheckedChange = { outline = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = EditorBackground,
                                checkedTrackColor = PinkMist,
                                uncheckedThumbColor = EditorText.copy(alpha = 0.7f),
                                uncheckedTrackColor = EditorPanelHigh,
                                uncheckedBorderColor = EditorText.copy(alpha = 0.25f),
                            ),
                        )
                    }

                    if (source.isAnimated && maxStart > 0) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Start at ${formatDuration(trimStart)}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = EditorText,
                                )
                                Spacer(Modifier.weight(1f))
                                Text(
                                    "First ${formatDuration(MAX_CLIP_MS)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = EditorText.copy(alpha = 0.55f),
                                )
                            }
                            Slider(
                                value = trimStart.toFloat(),
                                onValueChange = { trimStart = it.toLong() },
                                valueRange = 0f..maxStart.toFloat(),
                                onValueChangeFinished = { onPreviewAt(trimStart) },
                                colors = SliderDefaults.colors(
                                    thumbColor = PinkMist,
                                    activeTrackColor = PinkMist,
                                    inactiveTrackColor = EditorPanelHigh,
                                ),
                            )
                        }
                    }

                    Button(
                        onClick = {
                            onSave(zoom, panX, panY, outline, trimStart)
                        },
                        enabled = !editor.isSaving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Pink,
                            contentColor = androidx.compose.ui.graphics.Color.White,
                        ),
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Save sticker")
                        Spacer(Modifier.weight(1f))
                        Text(
                            if (source.isAnimated) "max 500 KB" else "max 100 KB",
                            style = MaterialTheme.typography.labelSmall,
                            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f),
                        )
                    }
                    Text(
                        text = "Saved as a 512 \u00D7 512 WebP sticker for WhatsApp.",
                        style = MaterialTheme.typography.bodySmall,
                        color = EditorText.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        if (editor.isSaving) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(EditorBackground.copy(alpha = 0.82f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = PinkMist)
                    Spacer(Modifier.height(18.dp))
                    Text(
                        "Making your sticker\u2026",
                        style = MaterialTheme.typography.titleMedium,
                        color = EditorText,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (source.isAnimated) "Encoding frames into an animated WebP." else "Encoding a WebP sticker.",
                        style = MaterialTheme.typography.bodySmall,
                        color = EditorText.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }

    if (editor.saveError != null) {
        AlertDialog(
            onDismissRequest = onDismissError,
            shape = RoundedCornerShape(28.dp),
            title = { Text("Couldn't save the sticker") },
            text = { Text(editor.saveError) },
            confirmButton = {
                TextButton(onClick = onDismissError) { Text("OK") }
            },
        )
    }
}

private const val MAX_CLIP_MS = 3000L

@Composable
private fun AnimatedStickerCanvas(
    frames: List<SourceFrame>,
    fallback: Bitmap,
    playing: Boolean,
    mediaWidth: Int,
    mediaHeight: Int,
    zoom: Float,
    panX: Float,
    panY: Float,
    outline: Boolean,
    modifier: Modifier = Modifier,
) {
    var frameIndex by remember(frames) { mutableIntStateOf(0) }
    LaunchedEffect(frames, playing) {
        if (frames.size < 2 || !playing) return@LaunchedEffect
        frameIndex = 0
        var current = 0
        while (true) {
            delay(frames[current].durationMs.coerceAtLeast(20).toLong())
            current = (current + 1) % frames.size
            frameIndex = current
        }
    }

    val bitmap = frames.getOrNull(frameIndex)?.bitmap ?: fallback
    Canvas(modifier) {
        drawIntoCanvas { composeCanvas ->
            val canvas = composeCanvas.nativeCanvas
            val bitmapWidth = bitmap.width.toFloat()
            val bitmapHeight = bitmap.height.toFloat()
            val fit = min(size.width / bitmapWidth, size.height / bitmapHeight)
            val scale = fit * zoom
            val (safePanX, safePanY) = StickerMath.clampPan(
                mediaWidth,
                mediaHeight,
                zoom,
                panX,
                panY,
            )
            val safeCenterX = size.width / 2f + safePanX * size.width
            val safeCenterY = size.height / 2f + safePanY * size.height
            val paint = Paint(
                Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG,
            )
            canvas.save()
            canvas.translate(safeCenterX, safeCenterY)
            canvas.scale(scale, scale)
            if (outline) {
                val outlinePaint = Paint(
                    Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG,
                ).apply {
                    colorFilter = PorterDuffColorFilter(
                        AndroidColor.WHITE,
                        PorterDuff.Mode.SRC_IN,
                    )
                }
                val radius = (10f * (size.width / 512f)) / scale
                val left = -bitmapWidth / 2f
                val top = -bitmapHeight / 2f
                for (i in 0 until 16) {
                    val angle = 2.0 * Math.PI * i / 16.0
                    canvas.drawBitmap(
                        bitmap,
                        left + (cos(angle) * radius).toFloat(),
                        top + (sin(angle) * radius).toFloat(),
                        outlinePaint,
                    )
                }
            }
            canvas.drawBitmap(
                bitmap,
                -bitmapWidth / 2f,
                -bitmapHeight / 2f,
                paint,
            )
            canvas.restore()
        }
    }
}
