package com.yogganstick.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StickerPackJsonTest {
    @Test
    fun roundTripsOwnerAndStickers() {
        val pack = StickerPack(
            id = "pack-1",
            name = "Reaction faces",
            version = 3,
            createdAt = 1234L,
            stickers = listOf(Sticker(fileName = "one.webp", animated = true)),
            owner = "Alex",
        )
        val decoded = StickerPackJson.decode(StickerPackJson.encode(listOf(pack)))
        assertEquals(1, decoded.size)
        assertEquals("Alex", decoded.single().owner)
        assertEquals("Reaction faces", decoded.single().name)
        assertEquals("one.webp", decoded.single().stickers.single().fileName)
        assertTrue(decoded.single().stickers.single().animated)
    }

    @Test
    fun oldPacksWithoutOwnerFallBackToTheAppName() {
        val raw = """
            {"packs":[{"id":"old","name":"Old pack","version":1,"createdAt":1,
            "stickers":[{"file":"a.webp","animated":false,"emoji":"\uD83D\uDE42"}]}]}
        """.trimIndent()
        val pack = StickerPackJson.decode(raw).single()
        assertEquals("", pack.owner)
        assertEquals("Yogganstick", pack.displayOwner)
    }
}
