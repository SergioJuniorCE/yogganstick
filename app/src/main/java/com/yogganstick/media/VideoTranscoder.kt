package com.yogganstick.media

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Some X/Twitter MP4s (and other containers) are valid but rejected by
 * MediaMetadataRetriever. Media3's extractor is more tolerant, so as a fallback
 * we normalize the first 3 seconds into a plain H.264 MP4 that the rest of the
 * pipeline can read.
 */
@androidx.annotation.OptIn(UnstableApi::class)
object VideoTranscoder {
    private const val TAG = "Yogganstick"
    private const val CLIP_MS = 3000L

    suspend fun normalize(context: Context, input: File): File = withContext(Dispatchers.IO) {
        val output = File(
            input.parentFile ?: context.cacheDir,
            "normalized_${System.currentTimeMillis()}.mp4",
        )
        val latch = CountDownLatch(1)
        var failure: Throwable? = null
        Handler(Looper.getMainLooper()).post {
            try {
                val mediaItem = MediaItem.Builder()
                    .setUri(Uri.fromFile(input))
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setEndPositionMs(CLIP_MS)
                            .build(),
                    )
                    .build()
                val item = EditedMediaItem.Builder(mediaItem)
                    .setRemoveAudio(true)
                    .build()
                val listener = object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        latch.countDown()
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException,
                    ) {
                        failure = exportException
                        latch.countDown()
                    }
                }
                val transformer = Transformer.Builder(context)
                    .setVideoMimeType(MimeTypes.VIDEO_H264)
                    .addListener(listener)
                    .build()
                transformer.start(item, output.absolutePath)
            } catch (throwable: Throwable) {
                failure = throwable
                latch.countDown()
            }
        }
        val completed = latch.await(60, TimeUnit.SECONDS)
        if (!completed) {
            output.delete()
            throw IllegalStateException("Converting that video took too long.")
        }
        failure?.let { throwable ->
            output.delete()
            Log.e(TAG, "Video normalization failed", throwable)
            throw IllegalStateException("Couldn't convert that video.", throwable)
        }
        if (!output.isFile || output.length() == 0L) {
            output.delete()
            throw IllegalStateException("Couldn't convert that video.")
        }
        Log.d(TAG, "Normalized ${input.name} -> ${output.name} (${output.length()} bytes)")
        output
    }
}
