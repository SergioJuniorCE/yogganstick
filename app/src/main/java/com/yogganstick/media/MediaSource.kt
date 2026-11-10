package com.yogganstick.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import androidx.media3.common.util.UnstableApi
import com.bumptech.glide.gifdecoder.GifDecoder
import com.bumptech.glide.gifdecoder.GifHeader
import com.bumptech.glide.gifdecoder.GifHeaderParser
import com.bumptech.glide.gifdecoder.StandardGifDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class SourceFrame(val bitmap: Bitmap, val durationMs: Int)

sealed class MediaSource {
    abstract val kind: MediaKind
    abstract val width: Int
    abstract val height: Int
    abstract val durationMs: Long
    abstract val isAnimated: Boolean

    abstract suspend fun previewAt(timeMs: Long): Bitmap

    abstract suspend fun frames(startMs: Long, fps: Int, maxFrames: Int): List<SourceFrame>

    /** Small frame sequence for the editor's live preview. Empty for stills. */
    open suspend fun previewFrames(startMs: Long): List<SourceFrame> = emptyList()

    open fun close() {}

            companion object {
        suspend fun open(context: Context, file: File, mime: String?): MediaSource =
            withContext(Dispatchers.IO) {
                val kind = MediaFiles.kind(file, mime)
                android.util.Log.d(
                    "Yogganstick",
                    "Opening ${file.name} ($mime) as $kind, ${file.length()} bytes",
                )
                when (kind) {
                    MediaKind.GIF -> GifSource.open(file)
                    MediaKind.VIDEO -> openVideo(context, file)
                    MediaKind.PHOTO -> PhotoSource(decodePhoto(file))
                }
            }

        @androidx.annotation.OptIn(UnstableApi::class)
        private suspend fun openVideo(context: Context, file: File): MediaSource {
            return try {
                VideoSource.open(file)
            } catch (first: Throwable) {
                android.util.Log.i(
                    "Yogganstick",
                    "Normalizing unsupported video container: ${file.name}",
                )
                val normalized = VideoTranscoder.normalize(context, file)
                try {
                    VideoSource.open(normalized, normalized)
                } catch (second: Throwable) {
                    android.util.Log.e("Yogganstick", "Video normalization failed", second)
                    normalized.delete()
                    throw IllegalArgumentException("Couldn't read that video.", second)
                }
            }
        }
    }
}

private class PhotoSource(private val bitmap: Bitmap) : MediaSource() {
    override val kind = MediaKind.PHOTO
    override val width = bitmap.width
    override val height = bitmap.height
    override val durationMs = 0L
    override val isAnimated = false

    override suspend fun previewAt(timeMs: Long): Bitmap = bitmap

    override suspend fun frames(startMs: Long, fps: Int, maxFrames: Int): List<SourceFrame> =
        listOf(SourceFrame(bitmap, 3000))
}

private class GifSource private constructor(
    private val bytes: ByteArray,
    private val header: GifHeader,
    private val frameCount: Int,
    override val width: Int,
    override val height: Int,
    private val delays: IntArray,
    private val sampleSize: Int,
) : MediaSource() {
    private val lock = Any()

    override val kind = MediaKind.GIF
    override val durationMs = delays.sumOf { it.toLong() }
    override val isAnimated = frameCount > 1

    override suspend fun previewAt(timeMs: Long): Bitmap = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val index = frameIndexAt(timeMs)
            val decoded = decodeSelected(setOf(index))
            decoded[index] ?: error("Couldn't decode that GIF.")
        }
    }

    override suspend fun frames(startMs: Long, fps: Int, maxFrames: Int): List<SourceFrame> =
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                if (frameCount <= 0) return@withContext emptyList()
                val starts = IntArray(frameCount)
                var cursor = 0
                for (i in delays.indices) {
                    starts[i] = cursor
                    cursor += delays[i]
                }
                val windowStart = startMs.coerceIn(0, max(0, durationMs - 1)).toInt()
                val windowEnd = min(durationMs, windowStart + 3000L).toInt()
                var window = buildList {
                    for (i in 0 until frameCount) {
                        val clippedStart = max(starts[i], windowStart)
                        val clippedEnd = min(starts[i] + delays[i], windowEnd)
                        if (clippedEnd - clippedStart > 10) {
                            add(GifWindowFrame(i, clippedEnd - clippedStart))
                        }
                    }
                }
                if (window.isEmpty()) {
                    window = listOf(
                        GifWindowFrame(
                            frameIndexAt(windowStart.toLong()),
                            max(20, windowEnd - windowStart),
                        ),
                    )
                }
                while (window.size > maxFrames) {
                    window = window.chunked(2).map { group ->
                        group.first().copy(duration = group.sumOf { it.duration })
                    }
                }
                val decoded = decodeSelected(window.map { it.index }.toSet())
                window.mapNotNull { frame ->
                    decoded[frame.index]?.let { SourceFrame(it, frame.duration) }
                }
            }
        }

    override suspend fun previewFrames(startMs: Long): List<SourceFrame> =
        withContext(Dispatchers.IO) {
            frames(startMs, fps = 8, maxFrames = 18).map { frame ->
                val bitmap = frame.bitmap
                val largest = max(bitmap.width, bitmap.height)
                if (largest <= 512) {
                    frame
                } else {
                    val scale = 512f / largest
                    val scaled = Bitmap.createScaledBitmap(
                        bitmap,
                        max(1, (bitmap.width * scale).roundToInt()),
                        max(1, (bitmap.height * scale).roundToInt()),
                        true,
                    )
                    bitmap.recycle()
                    SourceFrame(scaled, frame.durationMs)
                }
            }
        }

    private fun frameIndexAt(timeMs: Long): Int {
        val target = timeMs.coerceIn(0, max(0, durationMs - 1))
        var cumulative = 0
        for (i in delays.indices) {
            cumulative += delays[i]
            if (target < cumulative) return i
        }
        return frameCount - 1
    }

    private fun decodeSelected(indices: Set<Int>): Map<Int, Bitmap> {
        if (indices.isEmpty() || frameCount <= 0) return emptyMap()
        val last = indices.max()
        val decoder = newDecoder()
        val result = HashMap<Int, Bitmap>(indices.size)
        try {
            for (i in 0..last) {
                // StandardGifDecoder starts at frame -1; advance before each decode.
                decoder.advance()
                val frame = decoder.getNextFrame() ?: error("Couldn't decode that GIF.")
                if (i in indices) {
                    val copy = frame.copy(Bitmap.Config.ARGB_8888, true)
                    if (copy != null) result[i] = copy
                }
                frame.recycle()
            }
        } finally {
            decoder.clear()
        }
        check(result.size == indices.size) { "Couldn't decode that GIF." }
        return result
    }

    private fun newDecoder(): StandardGifDecoder =
        StandardGifDecoder(GifBitmapProvider, header, ByteBuffer.wrap(bytes), sampleSize)
            .apply { setDefaultBitmapConfig(Bitmap.Config.ARGB_8888) }

    companion object {
        fun open(file: File): MediaSource {
            if (file.length() > 64L * 1024 * 1024) error("That GIF is too large.")
            val bytes = file.readBytes()
            val header = runCatching {
                GifHeaderParser().setData(bytes).parseHeader()
            }.getOrNull() ?: return PhotoSource(decodePhoto(file))
            val count = header.numFrames
            if (count <= 1) return PhotoSource(decodePhoto(file))
            val width = header.width
            val height = header.height
            if (width <= 0 || height <= 0) return PhotoSource(decodePhoto(file))

            var sampleSize = 1
            while (max(width, height) / sampleSize > 1024) sampleSize *= 2
            val downsampledWidth = width / sampleSize
            val downsampledHeight = height / sampleSize
            if (downsampledWidth <= 0 || downsampledHeight <= 0) {
                return PhotoSource(decodePhoto(file))
            }
            val probe = StandardGifDecoder(
                GifBitmapProvider,
                header,
                ByteBuffer.wrap(bytes),
                sampleSize,
            )
            val delays = IntArray(count) { probe.getDelay(it).coerceIn(20, 1000) }
            probe.clear()
            return GifSource(
                bytes,
                header,
                count,
                downsampledWidth,
                downsampledHeight,
                delays,
                sampleSize,
            )
        }
    }
}

private data class GifWindowFrame(val index: Int, val duration: Int)

private object GifBitmapProvider : GifDecoder.BitmapProvider {
    override fun obtain(width: Int, height: Int, config: Bitmap.Config): Bitmap =
        Bitmap.createBitmap(width, height, config)

    override fun release(bitmap: Bitmap) {
        bitmap.recycle()
    }

    override fun obtainByteArray(size: Int): ByteArray = ByteArray(size)

    override fun release(bytes: ByteArray) = Unit

    override fun obtainIntArray(size: Int): IntArray = IntArray(size)

    override fun release(array: IntArray) = Unit
}

private class VideoSource private constructor(
    private val file: File,
    private val retriever: MediaMetadataRetriever,
    override val width: Int,
    override val height: Int,
    override val durationMs: Long,
    private val rotation: Int,
    private val encodedWidth: Int,
    private val encodedHeight: Int,
    private val ownedFile: File? = null,
) : MediaSource() {
    private val lock = Any()

    override val kind = MediaKind.VIDEO
    override val isAnimated = true

    override suspend fun previewAt(timeMs: Long): Bitmap = withContext(Dispatchers.IO) {
        synchronized(lock) { frameAt(timeMs) }
    }

    override suspend fun frames(startMs: Long, fps: Int, maxFrames: Int): List<SourceFrame> =
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                sampleFrames(
                    startMs = startMs,
                    targetFrames = 15,
                    maxFrames = maxFrames,
                    maxStepMs = 100L,
                    maxDimension = 1024,
                )
            }
        }

    override suspend fun previewFrames(startMs: Long): List<SourceFrame> =
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                sampleFrames(
                    startMs = startMs,
                    targetFrames = 18,
                    maxFrames = 18,
                    maxStepMs = 200L,
                    maxDimension = 512,
                )
            }
        }

    private fun sampleFrames(
        startMs: Long,
        targetFrames: Int,
        maxFrames: Int,
        maxStepMs: Long,
        maxDimension: Int,
    ): List<SourceFrame> {
        val available = if (durationMs <= 0) 0L else min(3000L, durationMs - startMs)
        if (available < 20) {
            val bitmap = frameAt(if (durationMs <= 0) 0 else startMs, maxDimension)
            return listOf(SourceFrame(bitmap, 3000))
        }
        // Short clips get a denser sampling so a 0.2 s loop still animates.
        val step = (available / targetFrames).coerceIn(20L, maxStepMs)
        val count = min(maxFrames, max(2, (available / step).toInt()))
        return buildList {
            for (i in 0 until count) {
                val offset = i.toLong() * step
                val end = min(available, offset + step)
                if (end - offset < 10) continue
                val bitmap = frameAt(startMs + offset + (end - offset) / 2, maxDimension)
                add(SourceFrame(bitmap, (end - offset).toInt()))
            }
        }
    }

    private fun frameAt(timeMs: Long, maxDimension: Int = 1024): Bitmap {
        val timeUs = timeMs.coerceAtLeast(0) * 1000
        val raw = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
            ?: retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST)
            ?: error("Couldn't read a frame from that video.")

        val needsRotate = rotation % 180 != 0 &&
            raw.width == encodedWidth && raw.height == encodedHeight &&
            encodedWidth != encodedHeight
        val oriented = if (needsRotate) {
            val matrix = android.graphics.Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true)
                .also { if (it !== raw) raw.recycle() }
        } else {
            raw
        }

        val largest = max(oriented.width, oriented.height)
        if (largest <= maxDimension) return oriented
        val scale = maxDimension / largest.toFloat()
        val scaled = Bitmap.createScaledBitmap(
            oriented,
            max(1, (oriented.width * scale).roundToInt()),
            max(1, (oriented.height * scale).roundToInt()),
            true,
        )
        if (scaled !== oriented) oriented.recycle()
        return scaled
    }

    override fun close() {
        runCatching { retriever.release() }
        ownedFile?.delete()
    }

    companion object {
        fun open(file: File, ownedFile: File? = null): MediaSource {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.absolutePath)
            } catch (first: Throwable) {
                android.util.Log.w("Yogganstick", "setDataSource(path) failed for ${file.name}", first)
                try {
                    java.io.FileInputStream(file).use { input ->
                        retriever.setDataSource(input.fd)
                    }
                } catch (second: Throwable) {
                    android.util.Log.e("Yogganstick", "setDataSource(fd) failed for ${file.name}", second)
                    retriever.release()
                    throw IllegalArgumentException("Couldn't read that video.", second)
                }
            }
            val encodedWidth = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            val encodedHeight = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0
            val rotation = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull() ?: 0
            val duration = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L

            if (encodedWidth <= 0 || encodedHeight <= 0) {
                val frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST)
                retriever.release()
                ownedFile?.delete()
                if (frame == null) throw IllegalArgumentException("Couldn't read that video.")
                return PhotoSource(frame)
            }

            val displayWidth = if (rotation == 90 || rotation == 270) encodedHeight else encodedWidth
            val displayHeight = if (rotation == 90 || rotation == 270) encodedWidth else encodedHeight
            return VideoSource(
                file = file,
                retriever = retriever,
                width = displayWidth,
                height = displayHeight,
                durationMs = duration,
                rotation = rotation,
                encodedWidth = encodedWidth,
                encodedHeight = encodedHeight,
                ownedFile = ownedFile,
            )
        }
    }
}

private fun decodePhoto(file: File): Bitmap {
    val bitmap = runCatching {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val maxDimension = 2048
            val size = info.size
            val largest = max(size.width, size.height)
            if (largest > maxDimension) {
                val scale = maxDimension / largest.toFloat()
                decoder.setTargetSize(
                    max(1, (size.width * scale).roundToInt()),
                    max(1, (size.height * scale).roundToInt()),
                )
            }
        }
    }.getOrNull() ?: BitmapFactory.decodeFile(file.absolutePath)
    return bitmap ?: error("Couldn't open that image.")
}
