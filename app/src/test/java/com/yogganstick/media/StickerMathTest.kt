package com.yogganstick.media

import org.junit.Assert.assertEquals
import org.junit.Test

class StickerMathTest {
    @Test
    fun landscapeFitsWidthAndPansOnlyHorizontallyWhenZoomed() {
        val (x1, y1) = StickerMath.maxPan(2000, 1000, 1f)
        assertEquals(0f, x1, 0.0001f)
        assertEquals(0f, y1, 0.0001f)

        val (x2, y2) = StickerMath.maxPan(2000, 1000, 2f)
        assertEquals(0.5f, x2, 0.0001f)
        assertEquals(0f, y2, 0.0001f)
    }

    @Test
    fun portraitPansOnlyVertically() {
        val (x, y) = StickerMath.maxPan(1000, 2000, 3f)
        assertEquals(0.25f, x, 0.0001f)
        assertEquals(1f, y, 0.0001f)
    }

    @Test
    fun squareCanPanOnceZoomedIn() {
        val (x, y) = StickerMath.maxPan(1000, 1000, 5f)
        assertEquals(2f, x, 0.0001f)
        assertEquals(2f, y, 0.0001f)
    }

    @Test
    fun clampsToImageBounds() {
        val (x, y) = StickerMath.clampPan(2000, 1000, 2f, 2f, 2f)
        assertEquals(0.5f, x, 0.0001f)
        assertEquals(0f, y, 0.0001f)
    }
}
