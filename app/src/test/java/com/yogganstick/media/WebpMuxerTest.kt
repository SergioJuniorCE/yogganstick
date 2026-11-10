package com.yogganstick.media

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class WebpMuxerTest {
    private val alphaPayload = byteArrayOf(0x01, 0x02, 0x03)
    private val imagePayload = byteArrayOf(0x10, 0x20, 0x30, 0x40, 0x50)

    @Test
    fun extractsAlphaAndImageFromSimpleWebp() {
        val alphaChunk = chunk("ALPH", alphaPayload)
        val imageChunk = chunk("VP8 ", imagePayload)
        val simple = riff(
            chunk("VP8X", ByteArray(10)),
            alphaChunk,
            imageChunk,
        )
        val (alpha, image) = WebpMuxer.extractChunks(simple)
        assertNotNull(alpha)
        assertArrayEquals(alphaChunk, alpha)
        assertArrayEquals(imageChunk, image)
    }

    @Test
    fun assemblesAnimatedContainer() {
        val alphaChunk = chunk("ALPH", alphaPayload)
        val imageChunk = chunk("VP8 ", imagePayload)
        val bytes = WebpMuxer.assemble(
            listOf(
                WebpMuxer.EncodedFrame(alphaChunk, imageChunk, 100),
                WebpMuxer.EncodedFrame(alphaChunk, imageChunk, 200),
            ),
            width = 512,
            height = 512,
        )

        assertEquals("RIFF", tag(bytes, 0))
        assertEquals(bytes.size - 8, le32(bytes, 4))
        assertEquals("WEBP", tag(bytes, 8))

        val chunks = chunks(bytes)
        assertEquals(listOf("VP8X", "ANIM", "ANMF", "ANMF"), chunks.map { it.first })

        val vp8x = chunks[0].second
        assertEquals(10, vp8x.size)
        assertEquals(0x12, vp8x[0].toInt() and 0xFF) // animation + alpha
        assertEquals(511, le24(vp8x, 4))
        assertEquals(511, le24(vp8x, 7))

        val anim = chunks[1].second
        assertEquals(6, anim.size)
        assertEquals(0, le32(anim, 0)) // transparent background
        assertEquals(0, anim[4].toInt() or (anim[5].toInt() shl 8)) // loop forever

        val firstFrame = chunks[2].second
        assertEquals(0, le24(firstFrame, 0))
        assertEquals(0, le24(firstFrame, 3))
        assertEquals(511, le24(firstFrame, 6))
        assertEquals(511, le24(firstFrame, 9))
        assertEquals(100, le24(firstFrame, 12))
        assertEquals(0x03, firstFrame[15].toInt() and 0xFF)
        val nested = chunksIn(firstFrame.copyOfRange(16, firstFrame.size))
        assertEquals(listOf("ALPH", "VP8 "), nested.map { it.first })
        assertArrayEquals(alphaPayload, nested[0].second)
        assertArrayEquals(imagePayload, nested[1].second)

        assertEquals(200, le24(chunks[3].second, 12))
    }

    @Test
    fun omitsAlphaFlagWithoutAlphaChunks() {
        val imageChunk = chunk("VP8 ", imagePayload)
        val bytes = WebpMuxer.assemble(
            listOf(
                WebpMuxer.EncodedFrame(null, imageChunk, 50),
                WebpMuxer.EncodedFrame(null, imageChunk, 50),
            ),
            width = 64,
            height = 64,
        )
        val chunks = chunks(bytes)
        assertEquals(0x02, chunks[0].second[0].toInt() and 0xFF)
        assertEquals(63, le24(chunks[0].second, 4))
        assertTrue(chunks.drop(2).all { frame -> chunksIn(frame.second.copyOfRange(16, frame.second.size)).size == 1 })
    }

    @Test
    fun rejectsMissingImageChunk() {
        val simple = riff(chunk("VP8X", ByteArray(10)))
        val failure = runCatching { WebpMuxer.extractChunks(simple) }
        assertTrue(failure.isFailure)
    }

    private fun chunks(bytes: ByteArray): List<Pair<String, ByteArray>> = chunksIn(bytes.copyOfRange(12, bytes.size))

    private fun chunksIn(payload: ByteArray): List<Pair<String, ByteArray>> {
        val result = mutableListOf<Pair<String, ByteArray>>()
        var offset = 0
        while (offset + 8 <= payload.size) {
            val tag = tag(payload, offset)
            val size = le32(payload, offset + 4)
            val total = 8 + size + (size and 1)
            require(offset + total <= payload.size)
            result.add(tag to payload.copyOfRange(offset + 8, offset + 8 + size))
            offset += total
        }
        return result
    }

    private fun riff(vararg chunks: ByteArray): ByteArray {
        val body = ByteArrayOutputStream()
        body.write("WEBP".toByteArray(Charsets.US_ASCII))
        chunks.forEach(body::write)
        val bytes = body.toByteArray()
        val out = ByteArrayOutputStream()
        out.write("RIFF".toByteArray(Charsets.US_ASCII))
        writeLe32(out, bytes.size)
        out.write(bytes)
        return out.toByteArray()
    }

    private fun chunk(tag: String, payload: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(tag.toByteArray(Charsets.US_ASCII))
        writeLe32(out, payload.size)
        out.write(payload)
        if (payload.size and 1 == 1) out.write(0)
        return out.toByteArray()
    }

    private fun tag(bytes: ByteArray, offset: Int): String =
        String(bytes, offset, 4, Charsets.US_ASCII)

    private fun le24(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16)

    private fun le32(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)

    private fun writeLe32(out: ByteArrayOutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value shr 8) and 0xFF)
        out.write((value shr 16) and 0xFF)
        out.write((value shr 24) and 0xFF)
    }
}
