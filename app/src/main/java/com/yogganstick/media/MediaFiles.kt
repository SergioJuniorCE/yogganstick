package com.yogganstick.media

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import kotlin.coroutines.coroutineContext

enum class MediaKind { PHOTO, GIF, VIDEO }

data class MediaFile(val file: File, val mimeType: String?)

object MediaFiles {
    private const val DIR = "media"
    private const val MAX_IMPORT_BYTES = 250L * 1024 * 1024

    fun dir(context: Context): File = File(context.cacheDir, DIR).apply { mkdirs() }

    suspend fun copyFromUri(context: Context, uri: Uri): MediaFile = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri)
        var displayName: String? = null
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIndex >= 0) displayName = cursor.getString(nameIndex)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    require(cursor.getLong(sizeIndex) <= MAX_IMPORT_BYTES) {
                        "That file is too large (250 MB max)."
                    }
                }
            }
        }
        val extension = extensionFor(mime, displayName)
        val target = File(
            dir(context),
            "import_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.$extension",
        )
        var copied = 0L
        try {
            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read <= 0) break
                        copied += read
                        require(copied <= MAX_IMPORT_BYTES) { "That file is too large (250 MB max)." }
                        output.write(buffer, 0, read)
                    }
                }
            } ?: error("Couldn't open that file.")
        } catch (t: Throwable) {
            target.delete()
            throw t
        }
        MediaFile(target, mime ?: mimeFromExtension(extension))
    }

    fun delete(file: File?) {
        runCatching { file?.delete() }
    }

    fun cleanOld(context: Context) {
        runCatching {
            val cutoff = System.currentTimeMillis() - 24L * 60 * 60 * 1000
            dir(context).listFiles()?.forEach { file ->
                if (file.lastModified() < cutoff) file.delete()
            }
        }
    }

    fun kind(file: File, mime: String?): MediaKind {
        val magic = readMagic(file)
        if (magic == "GIF89a" || magic == "GIF87a" || mime == "image/gif") return MediaKind.GIF
        val extension = file.extension.lowercase()
        if (mime?.startsWith("video/") == true || extension in setOf("mp4", "m4v", "mov", "webm", "mkv", "3gp")) {
            return MediaKind.VIDEO
        }
        if (magic == "ftyp" && extension !in setOf("heic", "heif", "avif")) return MediaKind.VIDEO
        return MediaKind.PHOTO
    }

    fun extensionFor(mime: String?, name: String?): String {
        mime?.let {
            MimeTypeMap.getSingleton().getExtensionFromMimeType(it)?.let { ext -> return ext }
        }
        val fromName = name?.substringAfterLast('.', "")?.lowercase()
        if (fromName != null && fromName.length in 1..5 && fromName.all { it.isLetterOrDigit() }) {
            return fromName
        }
        return when {
            mime == null -> "bin"
            mime.startsWith("image/") -> "img"
            mime.startsWith("video/") -> "mp4"
            else -> "bin"
        }
    }

    fun extensionForResponse(mime: String?, url: String): String {
        val known = when (mime?.substringBefore(';')?.trim()?.lowercase()) {
            "image/gif" -> "gif"
            "image/jpeg", "image/jpg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/heic", "image/heif" -> "heic"
            "image/avif" -> "avif"
            "video/mp4" -> "mp4"
            "video/webm" -> "webm"
            "video/quicktime" -> "mov"
            "video/x-matroska" -> "mkv"
            "video/3gpp" -> "3gp"
            else -> null
        }
        if (known != null) return known
        val path = Uri.parse(url).path ?: ""
        val ext = path.substringAfterLast('.', "").lowercase()
        return if (ext in setOf("gif", "jpg", "jpeg", "png", "webp", "mp4", "webm", "mov", "heic", "avif", "mkv", "3gp")) {
            ext
        } else {
            "bin"
        }
    }

    fun mimeFromExtension(extension: String): String? = when (extension.lowercase()) {
        "gif" -> "image/gif"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "heic", "heif" -> "image/heic"
        "avif" -> "image/avif"
        "mp4", "m4v" -> "video/mp4"
        "webm" -> "video/webm"
        "mov" -> "video/quicktime"
        "mkv" -> "video/x-matroska"
        "3gp" -> "video/3gpp"
        else -> null
    }

    private fun readMagic(file: File): String? = runCatching {
        val bytes = ByteArray(12)
        var read = 0
        FileInputStream(file).use { stream ->
            while (read < bytes.size) {
                val n = stream.read(bytes, read, bytes.size - read)
                if (n <= 0) break
                read += n
            }
        }
        if (read >= 12 && bytes.copyOfRange(4, 8).decodeToString() == "ftyp") "ftyp"
        else if (read >= 6 && bytes.copyOfRange(0, 6).decodeToString() in setOf("GIF89a", "GIF87a")) {
            bytes.copyOfRange(0, 6).decodeToString()
        } else if (read >= 12 && bytes.copyOfRange(0, 4).decodeToString() == "RIFF" &&
            bytes.copyOfRange(8, 12).decodeToString() == "WEBP"
        ) {
            "WEBP"
        } else {
            null
        }
    }.getOrNull()
}
