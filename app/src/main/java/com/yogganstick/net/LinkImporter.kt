package com.yogganstick.net

import android.content.Context
import com.yogganstick.media.MediaFile
import com.yogganstick.media.MediaFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.UUID
import kotlin.coroutines.coroutineContext

object LinkImporter {
    private const val MAX_MEDIA_BYTES = 80L * 1024 * 1024
    private const val MAX_TEXT_BYTES = 4L * 1024 * 1024
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/126.0 Mobile Safari/537.36 Yogganstick/1.0"

    suspend fun fetch(context: Context, input: String): MediaFile = withContext(Dispatchers.IO) {
        val url = LinkParsing.normalize(input)
        val tweet = LinkParsing.tweet(url)
        val mediaUrl = if (tweet != null) {
            resolveTweet(tweet)
        } else {
            if (!LinkParsing.isDirectMediaUrl(url)) {
                throw LinkException(
                    "Paste an X/Twitter post link, or a direct GIF, video or image link.",
                )
            }
            url
        }
        download(context, mediaUrl)
    }

    private fun resolveTweet(tweet: LinkParsing.TweetRef): String {
        val apiUrl = buildString {
            append("https://api.fxtwitter.com")
            tweet.user?.let { append('/').append(it) }
            append("/status/").append(tweet.id)
        }
        val apiResult = runCatching {
            getText(apiUrl, "application/json")
        }
        apiResult.getOrNull()?.let { body ->
            LinkParsing.pickFxMedia(body)?.let { return it }
        }

        val pageUrl = "https://x.com/" +
            (tweet.user ?: "i") +
            "/status/" + tweet.id
        val html = runCatching { getText(pageUrl, "text/html") }.getOrNull()
        if (html != null) {
            LinkParsing.pickHtmlMedia(html)?.let { return it }
        }

        val apiError = apiResult.exceptionOrNull()
        if (apiError is LinkException) throw apiError
        if (apiError != null) {
            throw LinkException("Couldn't reach X. Check your connection and try again.")
        }
        throw LinkException("That post doesn't include a GIF, video or photo.")
    }

    private suspend fun download(context: Context, url: String): MediaFile {
        val connection = open(url, "*/*")
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw LinkException("The media server returned HTTP $code.")
            }
            val contentType = connection.contentType?.substringBefore(';')?.trim()?.lowercase()
            if (contentType == "text/html" || contentType == "application/json") {
                throw LinkException("That link isn't a direct image or video.")
            }
            val length = connection.contentLengthLong
            if (length > MAX_MEDIA_BYTES) throw LinkException("That file is too large.")

            val extension = MediaFiles.extensionForResponse(contentType, url)
            if (extension == "bin") {
                throw LinkException("That link isn't a supported image or video.")
            }
            val target = File(
                MediaFiles.dir(context),
                "link_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.$extension",
            )
            var total = 0L
            try {
                connection.inputStream.use { input ->
                    FileOutputStream(target).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = input.read(buffer)
                            if (read <= 0) break
                            total += read
                            if (total > MAX_MEDIA_BYTES) {
                                throw LinkException("That file is too large.")
                            }
                            output.write(buffer, 0, read)
                        }
                    }
                }
            } catch (t: Throwable) {
                target.delete()
                throw t
            }
            if (total == 0L) {
                target.delete()
                throw LinkException("That link returned an empty file.")
            }
            val mime = contentType?.takeIf { it.startsWith("image/") || it.startsWith("video/") }
                ?: MediaFiles.mimeFromExtension(extension)
            return MediaFile(target, mime)
        } finally {
            connection.disconnect()
        }
    }

    private fun getText(url: String, accept: String): String {
        val connection = open(url, accept)
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw LinkException("X returned HTTP $code for that post.")
            }
            connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(32 * 1024)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    total += read
                    if (total > MAX_TEXT_BYTES) break
                    output.write(buffer, 0, read)
                }
                return output.toString(Charsets.UTF_8.name())
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String, accept: String): HttpURLConnection {
        val scheme = runCatching { URI(url).scheme }.getOrNull()
        if (scheme != "https") throw LinkException("Only https links are supported.")
        return (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 30_000
            useCaches = false
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", accept)
            setRequestProperty("Accept-Language", "en-US,en;q=0.9")
        }
    }
}
