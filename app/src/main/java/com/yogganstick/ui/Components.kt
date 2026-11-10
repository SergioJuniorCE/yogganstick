package com.yogganstick.ui

import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.graphics.ImageDecoder
import android.view.ViewGroup
import android.widget.ImageView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.yogganstick.data.StickerPack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun StickerMark(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Canvas(modifier) {
        val side = minOf(size.width, size.height)
        val left = (size.width - side) / 2f
        val top = (size.height - side) / 2f
        rotate(-7f, pivot = Offset(size.width / 2f, size.height / 2f)) {
            drawRoundRect(
                color = color,
                topLeft = Offset(left + side * 0.06f, top + side * 0.06f),
                size = Size(side * 0.88f, side * 0.88f),
                cornerRadius = CornerRadius(side * 0.22f, side * 0.22f),
            )
            val cx = left + side * 0.5f
            val cy = top + side * 0.47f
            drawCircle(
                color = Color.White,
                radius = side * 0.045f,
                center = Offset(cx - side * 0.15f, cy - side * 0.08f),
            )
            drawCircle(
                color = Color.White,
                radius = side * 0.045f,
                center = Offset(cx + side * 0.15f, cy - side * 0.08f),
            )
            drawArc(
                color = Color.White,
                startAngle = 20f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(cx - side * 0.18f, cy + side * 0.02f),
                size = Size(side * 0.36f, side * 0.26f),
                style = Stroke(width = side * 0.055f, cap = StrokeCap.Round),
            )
        }
        drawCircle(
            color = Color.White.copy(alpha = 0.45f),
            radius = side * 0.035f,
            center = Offset(left + side * 0.25f, top + side * 0.2f),
        )
    }
}

@Composable
fun Checkerboard(
    modifier: Modifier = Modifier,
    light: Boolean = false,
    cellSize: Dp = 24.dp,
) {
    val cell = with(LocalDensity.current) { cellSize.toPx() }
    val colorA = if (light) Color(0xFFF7EAF1) else Color(0xFF241C29)
    val colorB = if (light) Color(0xFFEFDDE8) else Color(0xFF1C1621)
    Canvas(modifier) {
        drawRect(colorA)
        var row = 0
        var y = 0f
        while (y < size.height) {
            var col = 0
            var x = 0f
            while (x < size.width) {
                if ((row + col) % 2 == 1) {
                    drawRect(colorB, topLeft = Offset(x, y), size = Size(cell, cell))
                }
                x += cell
                col++
            }
            y += cell
            row++
        }
    }
}

@Composable
fun rememberStickerDrawable(file: File): Drawable? {
    val lastModified = file.lastModified()
    val drawable by produceState<Drawable?>(initialValue = null, file.path, lastModified) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                ImageDecoder.decodeDrawable(ImageDecoder.createSource(file))
            }.getOrNull()
        }
    }
    return drawable
}

@Composable
fun StickerImage(
    file: File,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val drawable = rememberStickerDrawable(file)
    AndroidView(
        factory = { context ->
            ImageView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                scaleType = ImageView.ScaleType.FIT_CENTER
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        },
        update = { view ->
            view.contentDescription = contentDescription
            if (view.drawable !== drawable) {
                (view.drawable as? AnimatedImageDrawable)?.stop()
                view.setImageDrawable(drawable)
                (drawable as? AnimatedImageDrawable)?.let { animated ->
                    animated.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                    if (!animated.isRunning) animated.start()
                }
            }
        },
        onRelease = { view ->
            (view.drawable as? AnimatedImageDrawable)?.stop()
            view.setImageDrawable(null)
        },
        modifier = modifier,
    )
}

@Composable
fun PackCard(
    pack: StickerPack,
    trayFile: File,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(64.dp),
            ) {
                Box(Modifier.fillMaxSize().padding(4.dp), contentAlignment = Alignment.Center) {
                    if (pack.stickers.isNotEmpty() && trayFile.exists()) {
                        StickerImage(
                            file = trayFile,
                            modifier = Modifier.fillMaxSize(),
                            contentDescription = null,
                        )
                    } else {
                        StickerMark(Modifier.fillMaxSize(0.72f))
                    }
                }
            }
            Spacer(Modifier.width(14.dp))
            Box(Modifier.weight(1f)) {
                androidx.compose.foundation.layout.Column {
                    Text(
                        text = pack.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = buildString {
                            if (pack.stickers.isEmpty()) {
                                append("No stickers yet")
                            } else {
                                append(pack.stickers.size)
                                append(if (pack.stickers.size == 1) " sticker" else " stickers")
                            }
                            append(" \u00B7 by ")
                            append(pack.displayOwner)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
            if (pack.isAnimated) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        text = "ANIM",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = "\u203A",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
fun InfoChip(text: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@Composable
fun BusyOverlay(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.38f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {},
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.5.dp,
                )
                Text(message, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
fun StickerTile(
    file: File,
    animated: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Box(Modifier.fillMaxSize().padding(8.dp)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp)),
            ) {
                Checkerboard(Modifier.fillMaxSize(), light = true, cellSize = 16.dp)
                StickerImage(
                    file = file,
                    modifier = Modifier.fillMaxSize(),
                    contentDescription = if (animated) "Animated sticker" else "Sticker",
                )
            }
            if (animated) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.align(Alignment.BottomEnd),
                ) {
                    Text(
                        text = "ANIM",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

fun formatDuration(milliseconds: Long): String {
    val totalTenths = (milliseconds / 100).coerceAtLeast(0)
    val seconds = totalTenths / 10.0
    return String.format(java.util.Locale.US, "%.1f s", seconds)
}

fun formatBytes(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
    bytes >= 1024 -> String.format(java.util.Locale.US, "%d KB", bytes / 1024)
    else -> "$bytes B"
}
