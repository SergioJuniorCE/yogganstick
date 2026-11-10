package com.yogganstick.net

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI

object LinkParsing {
    private val tweetRegex = Regex(
        """(?:x|twitter)\.com/(?:i/)?(?:#!?/)?([A-Za-z0-9_]{1,20})/status(?:es)?/(\d+)""",
        RegexOption.IGNORE_CASE,
    )
    private val shortStatusRegex = Regex(
        """(?:x|twitter)\.com/(?:i/)?status(?:es)?/(\d+)""",
        RegexOption.IGNORE_CASE,
    )
    private val mediaExtensions = setOf("gif", "mp4", "webm", "mov", "jpg", "jpeg", "png", "webp", "heic", "avif")

    data class TweetRef(val id: String, val user: String?)

    fun normalize(input: String): String {
        var value = input.trim()
        if (value.isEmpty()) throw LinkException("Paste a link first.")
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            value = "https://$value"
        }
        if (value.startsWith("http://")) value = "https://" + value.removePrefix("http://")
        return value
    }

    fun tweet(input: String): TweetRef? {
        val match = tweetRegex.find(input) ?: shortStatusRegex.find(input) ?: return null
        val groups = match.groupValues
        return if (groups.size >= 3 && groups[2].isNotEmpty()) {
            TweetRef(groups[2], groups[1].takeIf { it.isNotEmpty() && it != "i" })
        } else {
            TweetRef(groups[1], null)
        }
    }

    fun extensionOf(url: String): String? {
        val path = runCatching { URI(url).path }.getOrNull() ?: return null
        val ext = path.substringAfterLast('.', "").lowercase()
        return ext.takeIf { it in mediaExtensions }
    }

    fun isDirectMediaUrl(url: String): Boolean {
        val host = runCatching { URI(url).host?.lowercase() }.getOrNull() ?: return false
        if (host == "pbs.twimg.com" || host == "video.twimg.com") return true
        return extensionOf(url) != null
    }

    fun pickFxMedia(json: String): String? {
        val media = JSONObject(json).optJSONObject("tweet")
            ?.optJSONObject("media")
            ?: return null
        val all = media.optJSONArray("all") ?: JSONArray()
        var photo: String? = null
        for (i in 0 until all.length()) {
            val item = all.optJSONObject(i) ?: continue
            val type = item.optString("type")
            val url = mediaUrl(item) ?: continue
            if (type == "gif" || type == "animated_gif" || type == "video" || url.contains(".mp4")) {
                return url
            }
            if (photo == null && type == "photo") photo = originalPhoto(url)
        }
        if (photo != null) return photo

        val videos = media.optJSONArray("videos") ?: JSONArray()
        for (i in 0 until videos.length()) {
            val item = videos.optJSONObject(i) ?: continue
            mediaUrl(item)?.let { return it }
        }
        return null
    }

    fun pickHtmlMedia(html: String): String? {
        val unescaped = html.replace("\\/", "/").replace("&amp;", "&")
        val video = Regex("""(?:https?:)?//video\.twimg\.com/[^"'\s\\]+?\.mp4""")
            .find(unescaped)?.value
        if (video != null) {
            return if (video.startsWith("//")) "https:$video" else video
        }
        val ogImage = Regex("""property=["']og:image["'][^>]*content=["']([^"']+)["']""")
            .find(unescaped)?.groupValues?.getOrNull(1)
            ?: Regex("""content=["']([^"']+)["'][^>]*property=["']og:image["']""")
                .find(unescaped)?.groupValues?.getOrNull(1)
        return ogImage
            ?.takeIf { "tweet_video_thumb" !in it }
            ?.replace("&amp;", "&")
    }

    private fun mediaUrl(item: JSONObject): String? {
        item.optString("url").takeIf { it.isNotBlank() }?.let { return it }
        val formats = item.optJSONArray("formats") ?: JSONArray()
        for (i in 0 until formats.length()) {
            val url = formats.optJSONObject(i)?.optString("url").orEmpty()
            if (url.isNotBlank()) return url
        }
        return null
    }

    private fun originalPhoto(url: String): String {
        if (!url.contains("pbs.twimg.com")) return url
        return if (url.contains('?')) "$url&name=orig" else "$url?name=orig"
    }
}

class LinkException(message: String) : Exception(message)
