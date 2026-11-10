package com.yogganstick.data

import android.content.Context
import com.yogganstick.media.TrayIconBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class Sticker(
    val fileName: String,
    val animated: Boolean,
    val emoji: String = "\uD83D\uDE42",
)

data class StickerPack(
    val id: String,
    val name: String,
    val version: Int,
    val createdAt: Long,
    val stickers: List<Sticker>,
    val owner: String = "",
) {
    val isAnimated: Boolean get() = stickers.any { it.animated }
    val trayFileName: String get() = "tray.png"
    val displayOwner: String get() = owner.ifBlank { FALLBACK_OWNER }

    companion object {
        const val FALLBACK_OWNER = "Yogganstick"
    }
}

object StickerPackJson {
    private const val PREFS = "yogganstick"
    private const val KEY = "packs"
    private const val OWNER_KEY = "owner_name"

    fun read(context: Context): List<StickerPack> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyList()
        return runCatching { decode(raw) }.getOrElse { emptyList() }
    }

    fun write(context: Context, packs: List<StickerPack>): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, encode(packs))
            .commit()
    }

    fun readDefaultOwner(context: Context): String? {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(OWNER_KEY, null)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    fun writeDefaultOwner(context: Context, owner: String): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(OWNER_KEY, owner.trim())
            .commit()
    }

    fun encode(packs: List<StickerPack>): String {
        val root = JSONObject()
        val array = JSONArray()
        packs.forEach { pack ->
            array.put(
                JSONObject().apply {
                    put("id", pack.id)
                    put("name", pack.name)
                    put("owner", pack.owner)
                    put("version", pack.version)
                    put("createdAt", pack.createdAt)
                    put(
                        "stickers",
                        JSONArray().apply {
                            pack.stickers.forEach { sticker ->
                                put(
                                    JSONObject().apply {
                                        put("file", sticker.fileName)
                                        put("animated", sticker.animated)
                                        put("emoji", sticker.emoji)
                                    },
                                )
                            }
                        },
                    )
                },
            )
        }
        root.put("packs", array)
        return root.toString()
    }

    fun decode(raw: String): List<StickerPack> {
        val root = JSONObject(raw)
        val array = root.optJSONArray("packs") ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val id = obj.optString("id").takeIf { it.isNotBlank() } ?: continue
                val stickers = mutableListOf<Sticker>()
                val stickerArray = obj.optJSONArray("stickers") ?: JSONArray()
                for (j in 0 until stickerArray.length()) {
                    val s = stickerArray.optJSONObject(j) ?: continue
                    val file = s.optString("file").takeIf { it.isNotBlank() } ?: continue
                    stickers.add(
                        Sticker(
                            fileName = file,
                            animated = s.optBoolean("animated", false),
                            emoji = s.optString("emoji", "\uD83D\uDE42"),
                        ),
                    )
                }
                add(
                    StickerPack(
                        id = id,
                        name = obj.optString("name", "Stickers"),
                        version = obj.optInt("version", 1),
                        createdAt = obj.optLong("createdAt", 0L),
                        stickers = stickers,
                        owner = obj.optString("owner", ""),
                    ),
                )
            }
        }
    }
}

class StickerStore(private val context: Context) {
    companion object {
        const val MAX_STICKERS = 30
        private const val PACKS_DIR = "packs"
    }

    private val _packs = MutableStateFlow<List<StickerPack>>(emptyList())
    val packs: StateFlow<List<StickerPack>> = _packs.asStateFlow()

    private val _defaultOwner = MutableStateFlow<String?>(null)
    val defaultOwner: StateFlow<String?> = _defaultOwner.asStateFlow()

    private val mutex = Mutex()

    init {
        _packs.value = StickerPackJson.read(context)
        _defaultOwner.value = StickerPackJson.readDefaultOwner(context)
    }

    fun packDir(packId: String): File = File(context.filesDir, "$PACKS_DIR/$packId")

    fun stickerFile(packId: String, fileName: String): File = File(packDir(packId), fileName)

    fun trayFile(packId: String): File = File(packDir(packId), "tray.png")

    suspend fun setDefaultOwner(owner: String) = withContext(Dispatchers.IO) {
        mutex.withLock { persistDefaultOwner(owner) }
    }

    suspend fun createPack(name: String, owner: String): StickerPack = withContext(Dispatchers.IO) {
        mutex.withLock {
            val cleanName = name.trim().take(60)
            require(cleanName.isNotEmpty()) { "Give your pack a name." }
            val cleanOwner = persistDefaultOwner(owner)
            val pack = StickerPack(
                id = UUID.randomUUID().toString().replace("-", ""),
                name = cleanName,
                version = 1,
                createdAt = System.currentTimeMillis(),
                stickers = emptyList(),
                owner = cleanOwner,
            )
            packDir(pack.id).mkdirs()
            persist(listOf(pack) + _packs.value)
            pack
        }
    }

    private fun persistDefaultOwner(owner: String): String {
        val cleanOwner = owner.trim().take(60)
        require(cleanOwner.isNotEmpty()) { "Add your name." }
        check(StickerPackJson.writeDefaultOwner(context, cleanOwner)) {
            "Couldn't save your name."
        }
        _defaultOwner.value = cleanOwner
        return cleanOwner
    }

    suspend fun renamePack(packId: String, name: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val cleanName = name.trim().take(60)
            require(cleanName.isNotEmpty()) { "Give your pack a name." }
            val updated = _packs.value.map {
                if (it.id == packId) it.copy(name = cleanName) else it
            }
            persist(updated)
        }
    }

    suspend fun deletePack(packId: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            persist(_packs.value.filterNot { it.id == packId })
            packDir(packId).deleteRecursively()
        }
    }

    suspend fun addSticker(
        packId: String,
        webp: ByteArray,
        animated: Boolean,
        trayPng: ByteArray,
    ): StickerPack = withContext(Dispatchers.IO) {
        mutex.withLock {
            val pack = _packs.value.firstOrNull { it.id == packId }
                ?: error("That sticker pack no longer exists.")
            check(pack.stickers.size < MAX_STICKERS) { "This pack is full (30 stickers max)." }

            val dir = packDir(packId)
            if (!dir.exists()) dir.mkdirs()
            val fileName = "sticker_${System.currentTimeMillis()}_" +
                UUID.randomUUID().toString().take(6) + ".webp"
            val file = File(dir, fileName)
            file.writeBytes(webp)

            val tray = File(dir, "tray.png")
            if (pack.stickers.isEmpty() || !tray.exists()) {
                tray.writeBytes(trayPng)
            }

            val sticker = Sticker(fileName = fileName, animated = animated)
            val updatedPack = pack.copy(
                stickers = pack.stickers + sticker,
                version = pack.version + 1,
            )
            val updated = _packs.value.map { if (it.id == packId) updatedPack else it }
            try {
                persist(updated)
            } catch (t: Throwable) {
                file.delete()
                throw t
            }
            updatedPack
        }
    }

    suspend fun deleteSticker(packId: String, fileName: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val pack = _packs.value.firstOrNull { it.id == packId } ?: return@withLock
            val index = pack.stickers.indexOfFirst { it.fileName == fileName }
            if (index < 0) return@withLock
            stickerFile(packId, fileName).delete()
            val remaining = pack.stickers.filterNot { it.fileName == fileName }
            val updatedPack = pack.copy(stickers = remaining, version = pack.version + 1)
            persist(_packs.value.map { if (it.id == packId) updatedPack else it })

            val dir = packDir(packId)
            if (remaining.isEmpty()) {
                trayFile(packId).delete()
            } else if (index == 0) {
                val first = File(dir, remaining.first().fileName)
                TrayIconBuilder.buildFromStickerFile(first)?.let { trayFile(packId).writeBytes(it) }
            }
        }
    }

    private fun persist(updated: List<StickerPack>) {
        check(StickerPackJson.write(context, updated)) { "Couldn't save your sticker pack." }
        _packs.value = updated
    }
}
