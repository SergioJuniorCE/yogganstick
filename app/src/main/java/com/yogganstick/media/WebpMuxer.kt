package com.yogganstick.media

import java.io.ByteArrayOutputStream

/**
 * Minimal animated WebP writer.
 *
 * Android can encode single-frame WebP but has no animation encoder, so each
 * frame is encoded by the platform and the resulting VP8/ALPH chunks are wrapped
 * in the standard animated WebP container here.
 */
object WebpMuxer {
    data class EncodedFrame(
        val alphaChunk: ByteArray?,
        val imageChunk: ByteArray,
        val durationMs: Int,
    )

    /**
     * Splits a single-frame WebP file into its optional ALPH chunk and its
     * VP8/VP8L chunk. Both returned arrays include the 8-byte chunk header.
     */
    fun extractChunks(webp: ByteArray): Pair<ByteArray?, ByteArray> {
        require(webp.size >= 12) { "Not a WebP file." }
        require(fourCc(webp, 0) == "RIFF" && fourCc(webp, 8) == "WEBP") { "Not a WebP file." }

        var offset = 12
        var alpha: ByteArray? = null
        var image: ByteArray? = null
        while (offset + 8 <= webp.size) {
            val tag = fourCc(webp, offset)
            val size = readLe32(webp, offset + 4)
            require(size >= 0) { "Invalid WebP chunk." }
            val total = 8 + size + (size and 1)
            require(offset + total <= webp.size) { "Truncated WebP chunk." }
            when (tag) {
                "ALPH" -> alpha = webp.copyOfRange(offset, offset + total)
                "VP8 ", "VP8L" -> if (image == null) image = webp.copyOfRange(offset, offset + total)
            }
            offset += total
        }
        return alpha to (image ?: error("WebP encoder returned no image chunk."))
    }

    fun assemble(frames: List<EncodedFrame>, width: Int, height: Int): ByteArray {
        require(frames.isNotEmpty()) { "An animation needs at least one frame." }
        require(width in 1..0xFFFFFF && height in 1..0xFFFFFF) { "Invalid canvas size." }

        val body = ByteArrayOutputStream()
        val hasAlpha = frames.any { it.alphaChunk != null }

        val vp8x = ByteArray(10)
        var flags = FLAG_ANIMATION
        if (hasAlpha) flags = flags or FLAG_ALPHA
        writeLe32(vp8x, 0, flags)
        writeLe24(vp8x, 4, width - 1)
        writeLe24(vp8x, 7, height - 1)
        writeChunk(body, "VP8X", vp8x)

        // Transparent background, loop forever.
        writeChunk(body, "ANIM", ByteArray(6))

        frames.forEach { frame ->
            val duration = frame.durationMs.coerceIn(10, 0xFFFFFF)
            val frameHeader = ByteArray(16)
            writeLe24(frameHeader, 0, 0) // x / 2
            writeLe24(frameHeader, 3, 0) // y / 2
            writeLe24(frameHeader, 6, width - 1)
            writeLe24(frameHeader, 9, height - 1)
            writeLe24(frameHeader, 12, duration)
            // dispose-to-background + do-not-blend, so frames fully replace each other
            frameHeader[15] = 0x03

            val payload = ByteArrayOutputStream()
            payload.write(frameHeader)
            frame.alphaChunk?.let(payload::write)
            payload.write(frame.imageChunk)
            writeChunk(body, "ANMF", payload.toByteArray())
        }

        val bodyBytes = body.toByteArray()
        val output = ByteArrayOutputStream(12 + bodyBytes.size)
        output.write("RIFF".toByteArray(Charsets.US_ASCII))
        writeLe32(output, bodyBytes.size + 4)
        output.write("WEBP".toByteArray(Charsets.US_ASCII))
        output.write(bodyBytes)
        return output.toByteArray()
    }

    private fun writeChunk(output: ByteArrayOutputStream, tag: String, payload: ByteArray) {
        output.write(tag.toByteArray(Charsets.US_ASCII))
        writeLe32(output, payload.size)
        output.write(payload)
        if (payload.size and 1 == 1) output.write(0)
    }

    private fun fourCc(bytes: ByteArray, offset: Int): String =
        String(bytes, offset, 4, Charsets.US_ASCII)

    private fun readLe32(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)

    private fun writeLe24(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value and 0xFF).toByte()
        bytes[offset + 1] = ((value shr 8) and 0xFF).toByte()
        bytes[offset + 2] = ((value shr 16) and 0xFF).toByte()
    }

    private fun writeLe32(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value and 0xFF).toByte()
        bytes[offset + 1] = ((value shr 8) and 0xFF).toByte()
        bytes[offset + 2] = ((value shr 16) and 0xFF).toByte()
        bytes[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    private fun writeLe32(output: ByteArrayOutputStream, value: Int) {
        output.write(value and 0xFF)
        output.write((value shr 8) and 0xFF)
        output.write((value shr 16) and 0xFF)
        output.write((value shr 24) and 0xFF)
    }

    private const val FLAG_ANIMATION = 0x02
    private const val FLAG_ALPHA = 0x10
}
