package com.yogganstick.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

data class StickerTransform(
    val zoom: Float = 1f,
    val panX: Float = 0f,
    val panY: Float = 0f,
    val outline: Boolean = true,
)

data class EncodedSticker(
    val webp: ByteArray,
    val animated: Boolean,
    val trayPng: ByteArray,
)

object StickerMath {
    /** Maximum pan, as a fraction of the square, for the image to still cover it. */
    fun maxPan(width: Int, height: Int, zoom: Float): Pair<Float, Float> {
        val fit = min(1f / width, 1f / height)
        val drawWidth = width * fit * zoom
        val drawHeight = height * fit * zoom
        return max(0f, (drawWidth - 1f) / 2f) to max(0f, (drawHeight - 1f) / 2f)
    }

    fun clampPan(width: Int, height: Int, zoom: Float, panX: Float, panY: Float): Pair<Float, Float> {
        val (maxX, maxY) = maxPan(width, height, zoom)
        return panX.coerceIn(-maxX, maxX) to panY.coerceIn(-maxY, maxY)
    }
}

object StickerEncoder {
    const val STICKER_SIZE = 512
    const val STATIC_MAX_BYTES = 100_000
    const val ANIMATED_MAX_BYTES = 500_000
    const val TARGET_FPS = 10
    const val MAX_FRAMES = 30

    suspend fun encode(
        source: MediaSource,
        transform: StickerTransform,
        startMs: Long,
    ): EncodedSticker = withContext(Dispatchers.IO) {
        if (!source.isAnimated) {
            val rendered = render(source.previewAt(0), transform)
            val webp = encodeStatic(rendered)
            val tray = TrayIconBuilder.buildFromSticker(rendered)
            rendered.recycle()
            return@withContext EncodedSticker(webp, animated = false, trayPng = tray)
        }

        val frames = source.frames(startMs, TARGET_FPS, MAX_FRAMES)
        if (frames.size < 2) {
            val rendered = render(frames.firstOrNull()?.bitmap ?: source.previewAt(startMs), transform)
            val webp = encodeStatic(rendered)
            val tray = TrayIconBuilder.buildFromSticker(rendered)
            rendered.recycle()
            frames.forEach { if (it.bitmap !== rendered) it.bitmap.recycle() }
            return@withContext EncodedSticker(webp, animated = false, trayPng = tray)
        }

        val rendered = frames.map { frame ->
            val bitmap = render(frame.bitmap, transform)
            frame.bitmap.recycle()
            RenderedFrame(bitmap, frame.durationMs)
        }
        try {
            val webp = encodeAnimation(rendered)
            val tray = TrayIconBuilder.buildFromSticker(rendered.first().bitmap)
            EncodedSticker(webp, animated = true, trayPng = tray)
        } finally {
            rendered.forEach { it.bitmap.recycle() }
        }
    }

    fun render(source: Bitmap, transform: StickerTransform): Bitmap {
        val output = Bitmap.createBitmap(STICKER_SIZE, STICKER_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val fit = min(STICKER_SIZE / source.width.toFloat(), STICKER_SIZE / source.height.toFloat())
        val scale = fit * transform.zoom
        val (panX, panY) = StickerMath.clampPan(
            source.width,
            source.height,
            transform.zoom,
            transform.panX,
            transform.panY,
        )
        val centerX = STICKER_SIZE / 2f + panX * STICKER_SIZE
        val centerY = STICKER_SIZE / 2f + panY * STICKER_SIZE

        canvas.save()
        canvas.translate(centerX, centerY)
        canvas.scale(scale, scale)

        if (transform.outline) {
            val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
            }
            val radius = OUTLINE_PX / scale
            val left = -source.width / 2f
            val top = -source.height / 2f
            for (i in 0 until OUTLINE_STEPS) {
                val angle = 2.0 * PI * i / OUTLINE_STEPS
                canvas.drawBitmap(
                    source,
                    left + (cos(angle) * radius).toFloat(),
                    top + (sin(angle) * radius).toFloat(),
                    outlinePaint,
                )
            }
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
        canvas.drawBitmap(source, -source.width / 2f, -source.height / 2f, paint)
        canvas.restore()
        stripOpaqueAlpha(output)
        return output
    }

    private fun encodeStatic(bitmap: Bitmap): ByteArray {
        val qualities = intArrayOf(90, 80, 70, 60, 50, 40, 30, 20)
        for (quality in qualities) {
            val bytes = compress(bitmap, quality)
            if (bytes.size <= STATIC_MAX_BYTES) return bytes
        }
        error("This image is too detailed for WhatsApp's 100 KB sticker limit. Try a simpler image.")
    }

    private fun encodeAnimation(frames: List<RenderedFrame>): ByteArray {
        val attempts = buildList {
            add(AnimationAttempt(frames, 72))
            add(AnimationAttempt(frames, 55))
            add(AnimationAttempt(frames, 40))
            add(AnimationAttempt(mergeFrames(frames, 2), 60))
            add(AnimationAttempt(mergeFrames(frames, 2), 42))
            add(AnimationAttempt(mergeFrames(frames, 3), 50))
            add(AnimationAttempt(mergeFrames(frames, 3), 35))
        }.distinctBy { it.frames.size to it.quality }

        for (attempt in attempts) {
            if (attempt.frames.size < 2) continue
            val encoded = attempt.frames.map { frame ->
                val bytes = compress(frame.bitmap, attempt.quality)
                val (alpha, image) = WebpMuxer.extractChunks(bytes)
                WebpMuxer.EncodedFrame(alpha, image, frame.durationMs)
            }
            val webp = WebpMuxer.assemble(encoded, STICKER_SIZE, STICKER_SIZE)
            if (webp.size <= ANIMATED_MAX_BYTES) {
                verifyAnimation(webp)
                return webp
            }
        }
        error("This clip is too heavy for WhatsApp's 500 KB animated sticker limit. Try a shorter clip.")
    }

    private fun verifyAnimation(bytes: ByteArray) {
        try {
            val drawable = ImageDecoder.decodeDrawable(ImageDecoder.createSource(ByteBuffer.wrap(bytes)))
            check(drawable is android.graphics.drawable.AnimatedImageDrawable) {
                "Animated WebP did not decode as an animation."
            }
        } catch (t: Throwable) {
            throw IllegalStateException("Couldn't build the animated sticker. Try a different clip.", t)
        }
    }

    private fun mergeFrames(frames: List<RenderedFrame>, step: Int): List<RenderedFrame> {
        if (step <= 1) return frames
        val merged = mutableListOf<RenderedFrame>()
        var index = 0
        while (index < frames.size) {
            val group = frames.subList(index, min(index + step, frames.size))
            merged.add(RenderedFrame(group.first().bitmap, group.sumOf { it.durationMs }))
            index += step
        }
        return merged
    }

    private fun compress(bitmap: Bitmap, quality: Int): ByteArray {
        stripOpaqueAlpha(bitmap)
        return ByteArrayOutputStream().use { stream ->
            @Suppress("DEPRECATION")
            bitmap.compress(Bitmap.CompressFormat.WEBP, quality.coerceIn(0, 100), stream)
            stream.toByteArray()
        }
    }

    private fun stripOpaqueAlpha(bitmap: Bitmap) {
        if (!bitmap.hasAlpha()) return
        val width = bitmap.width
        val row = IntArray(width)
        for (y in 0 until bitmap.height) {
            bitmap.getPixels(row, 0, width, 0, y, width, 1)
            if (row.any { (it ushr 24) != 0xFF }) return
        }
        bitmap.setHasAlpha(false)
    }

    private data class RenderedFrame(val bitmap: Bitmap, val durationMs: Int)
    private data class AnimationAttempt(val frames: List<RenderedFrame>, val quality: Int)

    private const val OUTLINE_PX = 10f
    private const val OUTLINE_STEPS = 24
}
