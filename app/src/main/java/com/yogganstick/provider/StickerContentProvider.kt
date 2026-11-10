package com.yogganstick.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.yogganstick.data.StickerPack
import com.yogganstick.data.StickerPackJson
import java.io.File
import java.io.FileNotFoundException

/**
 * Contract consumed by WhatsApp. URI shapes and column names are fixed by
 * WhatsApp's sticker API and must not be renamed.
 */
class StickerContentProvider : ContentProvider() {
    companion object {
        const val AUTHORITY_SUFFIX = ".stickercontentprovider"

        const val STICKER_PACK_IDENTIFIER_IN_QUERY = "sticker_pack_identifier"
        const val STICKER_PACK_NAME_IN_QUERY = "sticker_pack_name"
        const val STICKER_PACK_PUBLISHER_IN_QUERY = "sticker_pack_publisher"
        const val STICKER_PACK_ICON_IN_QUERY = "sticker_pack_icon"
        const val ANDROID_APP_DOWNLOAD_LINK_IN_QUERY = "android_play_store_link"
        const val IOS_APP_DOWNLOAD_LINK_IN_QUERY = "ios_app_download_link"
        const val PUBLISHER_EMAIL = "sticker_pack_publisher_email"
        const val PUBLISHER_WEBSITE = "sticker_pack_publisher_website"
        const val PRIVACY_POLICY_WEBSITE = "sticker_pack_privacy_policy_website"
        const val LICENSE_AGREEMENT_WEBSITE = "sticker_pack_license_agreement_website"
        const val IMAGE_DATA_VERSION = "image_data_version"
        const val AVOID_CACHE = "whatsapp_will_not_cache_stickers"
        const val ANIMATED_STICKER_PACK = "animated_sticker_pack"
        const val STICKER_FILE_NAME_IN_QUERY = "sticker_file_name"
        const val STICKER_FILE_EMOJI_IN_QUERY = "sticker_emoji"
        const val STICKER_FILE_ACCESSIBILITY_TEXT_IN_QUERY = "sticker_accessibility_text"

        private const val METADATA = "metadata"
        private const val STICKERS = "stickers"
        private const val STICKERS_ASSET = "stickers_asset"
        private const val TRAY_ICON = "tray.png"

        fun authority(context: Context): String = context.packageName + AUTHORITY_SUFFIX
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        val context = context ?: return null
        val packs = StickerPackJson.read(context)
            .filter { it.stickers.isNotEmpty() }
            .take(30)
        val segments = uri.pathSegments
        return when {
            segments.size == 1 && segments[0] == METADATA -> metadataCursor(packs)
            segments.size == 2 && segments[0] == METADATA ->
                metadataCursor(packs.filter { it.id == segments[1] })
            segments.size == 2 && segments[0] == STICKERS ->
                stickersCursor(packs.firstOrNull { it.id == segments[1] })
            else -> throw IllegalArgumentException("Unknown URI: $uri")
        }
    }

    override fun getType(uri: Uri): String? {
        return when {
            uri.pathSegments.firstOrNull() == METADATA -> "vnd.android.cursor.dir/vnd." +
                "${authority(context ?: return null)}.$METADATA"
            uri.pathSegments.firstOrNull() == STICKERS -> "vnd.android.cursor.dir/vnd." +
                "${authority(context ?: return null)}.$STICKERS"
            uri.pathSegments.firstOrNull() == STICKERS_ASSET -> {
                if (uri.pathSegments.lastOrNull() == TRAY_ICON) "image/png" else "image/webp"
            }
            else -> null
        }
    }

    override fun openAssetFile(uri: Uri, mode: String): AssetFileDescriptor? {
        val file = resolveAssetFile(uri, mode) ?: return null
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        return AssetFileDescriptor(descriptor, 0, file.length())
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val file = resolveAssetFile(uri, mode)
            ?: throw FileNotFoundException("Unknown sticker asset: $uri")
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    private fun resolveAssetFile(uri: Uri, mode: String): File? {
        if (!mode.startsWith("r")) throw FileNotFoundException("Sticker files are read-only.")
        val context = context ?: return null
        val segments = uri.pathSegments
        if (segments.size != 3 || segments[0] != STICKERS_ASSET) return null
        val packId = segments[1]
        val fileName = segments[2]
        val pack = StickerPackJson.read(context)
            .firstOrNull { it.id == packId && it.stickers.isNotEmpty() }
            ?: throw FileNotFoundException("Unknown sticker pack: $packId")
        val allowed = fileName == TRAY_ICON || pack.stickers.any { it.fileName == fileName }
        if (!allowed) throw FileNotFoundException("Unknown sticker file: $fileName")

        val file = File(File(context.filesDir, "packs/$packId"), fileName)
        if (!file.isFile) throw FileNotFoundException("Missing sticker file: $fileName")
        return file
    }

    private fun metadataCursor(packs: List<StickerPack>): Cursor {
        val cursor = MatrixCursor(
            arrayOf(
                STICKER_PACK_IDENTIFIER_IN_QUERY,
                STICKER_PACK_NAME_IN_QUERY,
                STICKER_PACK_PUBLISHER_IN_QUERY,
                STICKER_PACK_ICON_IN_QUERY,
                ANDROID_APP_DOWNLOAD_LINK_IN_QUERY,
                IOS_APP_DOWNLOAD_LINK_IN_QUERY,
                PUBLISHER_EMAIL,
                PUBLISHER_WEBSITE,
                PRIVACY_POLICY_WEBSITE,
                LICENSE_AGREEMENT_WEBSITE,
                IMAGE_DATA_VERSION,
                AVOID_CACHE,
                ANIMATED_STICKER_PACK,
            ),
        )
        packs.forEach { pack ->
            cursor.addRow(
                arrayOf<Any?>(
                    pack.id,
                    pack.name,
                    pack.displayOwner,
                    TRAY_ICON,
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    pack.version.toString(),
                    0,
                    if (pack.isAnimated) 1 else 0,
                ),
            )
        }
        return cursor
    }

    private fun stickersCursor(pack: StickerPack?): Cursor {
        val cursor = MatrixCursor(
            arrayOf(
                STICKER_FILE_NAME_IN_QUERY,
                STICKER_FILE_EMOJI_IN_QUERY,
                STICKER_FILE_ACCESSIBILITY_TEXT_IN_QUERY,
            ),
        )
        pack?.stickers?.take(30)?.forEach { sticker ->
            cursor.addRow(
                arrayOf(
                    sticker.fileName,
                    sticker.emoji,
                    "A sticker from ${pack.name}".take(120),
                ),
            )
        }
        return cursor
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        throw UnsupportedOperationException("Not supported")
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        throw UnsupportedOperationException("Not supported")
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int {
        throw UnsupportedOperationException("Not supported")
    }
}
