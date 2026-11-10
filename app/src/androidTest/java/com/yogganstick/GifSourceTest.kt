package com.yogganstick

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yogganstick.media.MediaSource
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class GifSourceTest {
    @Test
    fun decodesTransparentAnimatedGif() {
        runBlocking {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val file = File(instrumentation.targetContext.cacheDir, "transparent_test.gif")
            instrumentation.context.assets.open("transparent.gif").use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            val source = MediaSource.open(instrumentation.targetContext, file, "image/gif")
            try {
                assertTrue(source.isAnimated)
                assertEquals(1500L, source.durationMs)
                val first = source.previewAt(0)
                assertEquals(300, first.width)
                assertEquals(300, first.height)
                assertEquals(0, first.getPixel(5, 5) ushr 24)
                assertEquals(255, first.getPixel(110, 170) ushr 24)

                val frames = source.frames(0, 10, 30)
                assertEquals(15, frames.size)
                assertTrue(frames.all { it.durationMs == 100 })
                val firstFrame = frames.first().bitmap
                assertEquals(0, firstFrame.getPixel(5, 5) ushr 24)
                assertEquals(255, firstFrame.getPixel(110, 170) ushr 24)

                val preview = source.previewFrames(0)
                assertEquals(15, preview.size)
                assertTrue(preview.all { it.durationMs == 100 })
                assertEquals(0, preview.first().bitmap.getPixel(5, 5) ushr 24)
                assertEquals(255, preview.first().bitmap.getPixel(110, 170) ushr 24)
                preview.forEach { it.bitmap.recycle() }
                frames.forEach { it.bitmap.recycle() }
                first.recycle()
            } finally {
                source.close()
            }
        }
    }

    @Test
    fun decodesOpaqueGifAndBoundsFrames() {
        runBlocking {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val file = File(instrumentation.targetContext.cacheDir, "opaque_test.gif")
            instrumentation.context.assets.open("animated.gif").use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            val source = MediaSource.open(instrumentation.targetContext, file, "image/gif")
            try {
                assertTrue(source.isAnimated)
                assertEquals(1500L, source.durationMs)
                val frames = source.frames(0, 10, 30)
                assertEquals(15, frames.size)
                assertTrue(frames.all { it.bitmap.config == Bitmap.Config.ARGB_8888 })
                val limited = source.frames(0, 10, 5)
                assertTrue(limited.size <= 5)
                assertEquals(1500, limited.sumOf { it.durationMs })
                (frames + limited).forEach { it.bitmap.recycle() }
            } finally {
                source.close()
            }
        }
    }
}
