package com.yogganstick

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yogganstick.data.StickerStore
import com.yogganstick.data.StickerPackJson
import com.yogganstick.media.TrayIconBuilder
import com.yogganstick.provider.StickerContentProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException

@RunWith(AndroidJUnit4::class)
class ProviderContractTest {
    @Test
    fun providerServesWhatsAppContract() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val store = StickerStore(context)
            val pack = store.createPack("Provider contract test", "Alice")
            assertEquals("Alice", store.defaultOwner.value)
            assertEquals("Alice", StickerPackJson.readDefaultOwner(context))

            val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.TRANSPARENT)
            canvas.drawCircle(256f, 256f, 220f, android.graphics.Paint().apply {
                color = Color.rgb(219, 39, 119)
                isAntiAlias = true
            })
            val webp = ByteArrayOutputStream().use {
                @Suppress("DEPRECATION")
                bitmap.compress(Bitmap.CompressFormat.WEBP, 90, it)
                it.toByteArray()
            }
            val tray = TrayIconBuilder.buildFromSticker(bitmap)
            store.addSticker(pack.id, webp, animated = false, trayPng = tray)
            bitmap.recycle()

            val authority = context.packageName + StickerContentProvider.AUTHORITY_SUFFIX
            val metadataUri = Uri.parse("content://$authority/metadata")
            context.contentResolver.query(metadataUri, null, null, null, null)!!.use { cursor ->
                assertTrue(cursor.count >= 1)
                var found = false
                while (cursor.moveToNext()) {
                    if (cursor.getString(
                            cursor.getColumnIndexOrThrow(
                                StickerContentProvider.STICKER_PACK_IDENTIFIER_IN_QUERY,
                            ),
                        ) != pack.id
                    ) {
                        continue
                    }
                    found = true
                    assertEquals(
                        pack.name,
                        cursor.getString(
                            cursor.getColumnIndexOrThrow(
                                StickerContentProvider.STICKER_PACK_NAME_IN_QUERY,
                            ),
                        ),
                    )
                    assertEquals(
                        "Alice",
                        cursor.getString(
                            cursor.getColumnIndexOrThrow(
                                StickerContentProvider.STICKER_PACK_PUBLISHER_IN_QUERY,
                            ),
                        ),
                    )
                    assertEquals(
                        "tray.png",
                        cursor.getString(
                            cursor.getColumnIndexOrThrow(
                                StickerContentProvider.STICKER_PACK_ICON_IN_QUERY,
                            ),
                        ),
                    )
                    assertEquals(
                        0,
                        cursor.getInt(
                            cursor.getColumnIndexOrThrow(
                                StickerContentProvider.ANIMATED_STICKER_PACK,
                            ),
                        ),
                    )
                    assertEquals(
                        0,
                        cursor.getInt(
                            cursor.getColumnIndexOrThrow(StickerContentProvider.AVOID_CACHE),
                        ),
                    )
                    assertTrue(
                        cursor.getString(
                            cursor.getColumnIndexOrThrow(
                                StickerContentProvider.IMAGE_DATA_VERSION,
                            ),
                        )!!.toInt() >= 1,
                    )
                }
                assertTrue("Pack missing from metadata", found)
            }

            var stickerFile: String? = null
            val stickersUri = Uri.parse("content://$authority/stickers/${pack.id}")
            context.contentResolver.query(stickersUri, null, null, null, null)!!.use { cursor ->
                assertEquals(1, cursor.count)
                assertTrue(cursor.moveToFirst())
                stickerFile = cursor.getString(
                    cursor.getColumnIndexOrThrow(
                        StickerContentProvider.STICKER_FILE_NAME_IN_QUERY,
                    ),
                )
                assertEquals(
                    "\uD83D\uDE42",
                    cursor.getString(
                        cursor.getColumnIndexOrThrow(
                            StickerContentProvider.STICKER_FILE_EMOJI_IN_QUERY,
                        ),
                    ),
                )
            }

            val trayUri = Uri.parse("content://$authority/stickers_asset/${pack.id}/tray.png")
            context.contentResolver.openInputStream(trayUri)!!.use { input ->
                val header = ByteArray(8)
                assertEquals(8, input.read(header))
                assertEquals("PNG", header.copyOfRange(1, 4).decodeToString())
            }

            val assetUri = Uri.parse("content://$authority/stickers_asset/${pack.id}/$stickerFile")
            context.contentResolver.openInputStream(assetUri)!!.use { input ->
                val bytes = input.readBytes()
                assertEquals(webp.size, bytes.size)
                assertEquals("RIFF", bytes.copyOfRange(0, 4).decodeToString())
                assertEquals("WEBP", bytes.copyOfRange(8, 12).decodeToString())
            }

            val missing = runCatching {
                context.contentResolver.openInputStream(
                    Uri.parse("content://$authority/stickers_asset/${pack.id}/nope.webp"),
                )
            }.exceptionOrNull()
            assertNotNull(missing)
            assertTrue(missing is FileNotFoundException || missing is SecurityException)

            store.deletePack(pack.id)
        }
    }
}
