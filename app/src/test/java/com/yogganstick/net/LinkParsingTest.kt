package com.yogganstick.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkParsingTest {
    private val example = "https://x.com/GIFOOC/status/2120214917450002669/photo/1"

    @Test
    fun readsTweetIdAndUser() {
        val ref = requireNotNull(LinkParsing.tweet(example))
        assertEquals("2120214917450002669", ref.id)
        assertEquals("GIFOOC", ref.user)

        val short = requireNotNull(LinkParsing.tweet("https://x.com/i/status/1234567890"))
        assertEquals("1234567890", short.id)
        assertNull(short.user)
    }

    @Test
    fun normalizesBareAndHttpLinks() {
        assertEquals(example, LinkParsing.normalize(example))
        assertEquals(example, LinkParsing.normalize(example.removePrefix("https://")))
        assertEquals(example, LinkParsing.normalize(example.replace("https://", "http://")))
    }

    @Test
    fun picksAnimatedMediaFromFxTwitter() {
        val json = """
            {"tweet":{"media":{"all":[
              {"type":"photo","url":"https://pbs.twimg.com/media/photo.jpg"},
              {"type":"gif","url":"https://video.twimg.com/tweet_video/clip.mp4"}
            ]}}}
        """.trimIndent()
        assertEquals("https://video.twimg.com/tweet_video/clip.mp4", LinkParsing.pickFxMedia(json))
    }

    @Test
    fun picksHighestResPhotoWhenNoAnimation() {
        val json = """
            {"tweet":{"media":{"all":[
              {"type":"photo","url":"https://pbs.twimg.com/media/photo.jpg"}
            ]}}}
        """.trimIndent()
        assertEquals(
            "https://pbs.twimg.com/media/photo.jpg?name=orig",
            LinkParsing.pickFxMedia(json),
        )
    }

    @Test
    fun readsEscapedVideoUrlFromTweetHtml() {
        val html = """
            <html><script>const x = "https:\/\/video.twimg.com\/tweet_video\/abc.mp4";</script>
            <meta property="og:image" content="https://pbs.twimg.com/tweet_video_thumb/abc.jpg"/></html>
        """.trimIndent()
        assertEquals(
            "https://video.twimg.com/tweet_video/abc.mp4",
            LinkParsing.pickHtmlMedia(html),
        )
    }

    @Test
    fun photoHtmlFallsBackToOgImage() {
        val html = """<meta property="og:image" content="https://pbs.twimg.com/media/photo.jpg?name=large" />"""
        assertEquals(
            "https://pbs.twimg.com/media/photo.jpg?name=large",
            LinkParsing.pickHtmlMedia(html),
        )
    }

    @Test
    fun videoThumbnailIsNotMistakenForAPhoto() {
        val html = """<meta property="og:image" content="https://pbs.twimg.com/tweet_video_thumb/abc.jpg" />"""
        assertNull(LinkParsing.pickHtmlMedia(html))
    }

    @Test
    fun classifiesDirectMedia() {
        assertTrue(LinkParsing.isDirectMediaUrl("https://media.giphy.com/media/abc/giphy.gif"))
        assertTrue(LinkParsing.isDirectMediaUrl("https://video.twimg.com/tweet_video/abc.mp4"))
        assertTrue(LinkParsing.isDirectMediaUrl("https://pbs.twimg.com/media/abc?format=jpg&name=large"))
        assertFalse(LinkParsing.isDirectMediaUrl("https://example.com/article"))
        assertFalse(LinkParsing.isDirectMediaUrl("https://x.com/GIFOOC/status/1"))
    }
}
