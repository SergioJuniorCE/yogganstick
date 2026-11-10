package com.yogganstick

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
class VideoSourceTest {
    @Test
    fun honoursRotationMetadata() {
        runBlocking {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val file = File(instrumentation.targetContext.cacheDir, "rotated_test.mp4")
            instrumentation.context.assets.open("rotated.mp4").use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            val source = MediaSource.open(instrumentation.targetContext, file, "video/mp4")
            try {
                assertTrue(source.isAnimated)
                assertEquals(360, source.width)
                assertEquals(640, source.height)
                val previewBitmap = source.previewAt(0)
                assertTrue("expected portrait preview", previewBitmap.height > previewBitmap.width)
                val frames = source.frames(0, 10, 30)
                assertTrue(frames.size >= 2)
                assertTrue(frames.all { it.bitmap.height > it.bitmap.width })
                frames.forEach { it.bitmap.recycle() }

                val previewFrames = source.previewFrames(0)
                assertTrue(previewFrames.size >= 2)
                assertTrue(previewFrames.all { it.bitmap.height > it.bitmap.width })
                assertTrue(previewFrames.all { it.durationMs > 0 })
                previewFrames.forEach { it.bitmap.recycle() }
                previewBitmap.recycle()
            } finally {
                source.close()
            }
        }
    }
}
