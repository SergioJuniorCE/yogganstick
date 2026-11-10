package com.yogganstick.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.RectF
import java.io.ByteArrayOutputStream
import java.io.File

object TrayIconBuilder {
    private const val TRAY_SIZE = 96
    private const val ART_SIZE = 80f

    fun buildFromStickerFile(file: File): ByteArray? = runCatching {
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        buildFromSticker(bitmap)
    }.getOrNull()

    fun buildFromSticker(sticker: Bitmap): ByteArray {
        val out = Bitmap.createBitmap(TRAY_SIZE, TRAY_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        val scale = ART_SIZE / maxOf(sticker.width, sticker.height).toFloat()
        val drawWidth = sticker.width * scale
        val drawHeight = sticker.height * scale
        val left = (TRAY_SIZE - drawWidth) / 2f
        val top = (TRAY_SIZE - drawHeight) / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(
            sticker,
            null,
            RectF(left, top, left + drawWidth, top + drawHeight),
            paint,
        )
        return ByteArrayOutputStream().use { stream ->
            out.compress(Bitmap.CompressFormat.PNG, 100, stream)
            out.recycle()
            stream.toByteArray()
        }
    }
}
