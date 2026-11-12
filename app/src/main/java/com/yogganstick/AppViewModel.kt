package com.yogganstick

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yogganstick.data.StickerPack
import com.yogganstick.data.StickerStore
import com.yogganstick.media.MediaFiles
import com.yogganstick.media.MediaSource
import com.yogganstick.media.SourceFrame
import com.yogganstick.media.StickerEncoder
import com.yogganstick.media.StickerTransform
import com.yogganstick.net.LinkImporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface Screen {
    data object Packs : Screen
    data class PackDetail(val packId: String) : Screen
    data class Editor(val packId: String) : Screen
}

data class EditorUiState(
    val packId: String,
    val packName: String,
    val file: File,
    val source: MediaSource,
    val preview: Bitmap,
    val previewFrames: List<SourceFrame> = emptyList(),
    val isSaving: Boolean = false,
    val previewLoading: Boolean = false,
    val saveError: String? = null,
) {
    val isAnimated: Boolean get() = source.isAnimated
    val durationMs: Long get() = source.durationMs
    val kind get() = source.kind
}

data class LinkDialogState(
    val packId: String,
    val url: String = "",
    val loading: Boolean = false,
    val error: String? = null,
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val store = StickerStore(application)
    private val app = application

    val packs: StateFlow<List<StickerPack>> = store.packs
    val defaultOwner: StateFlow<String?> = store.defaultOwner

    var screen by mutableStateOf<Screen>(Screen.Packs)
        private set
    var editor by mutableStateOf<EditorUiState?>(null)
        private set
    var editorLoading by mutableStateOf(false)
        private set
    var busyMessage by mutableStateOf<String?>(null)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var linkDialog by mutableStateOf<LinkDialogState?>(null)
        private set
    var whatsAppSync by mutableStateOf<String?>(null)
        private set

    private var previewJob: Job? = null
    private var linkJob: Job? = null

    init {
        viewModelScope.launch(Dispatchers.IO) { MediaFiles.cleanOld(app) }
    }

    fun openPacks() {
        dismissLinkDialog()
        screen = Screen.Packs
    }

    fun openPack(packId: String) {
        dismissLinkDialog()
        screen = Screen.PackDetail(packId)
    }

    fun back() {
        if (linkDialog != null) {
            dismissLinkDialog()
            return
        }
        when (screen) {
            Screen.Packs -> Unit
            is Screen.PackDetail -> screen = Screen.Packs
            is Screen.Editor -> {
                if (editor?.isSaving != true) closeEditor()
            }
        }
    }

    fun trayFile(packId: String): File = store.trayFile(packId)

    fun stickerFile(packId: String, fileName: String): File = store.stickerFile(packId, fileName)

    fun notify(text: String) {
        showMessage(text)
    }

    fun setDefaultOwner(owner: String) {
        viewModelScope.launch {
            attempt { store.setDefaultOwner(owner) }
                .onFailure { showMessage(it.message ?: "Couldn't save your name.") }
        }
    }

    fun createPack(name: String, owner: String) {
        viewModelScope.launch {
            attempt { store.createPack(name, owner) }
                .onSuccess { pack ->
                    screen = Screen.PackDetail(pack.id)
                }
                .onFailure { showMessage(it.message ?: "Couldn't create that pack.") }
        }
    }

    fun renamePack(packId: String, name: String) {
        viewModelScope.launch {
            attempt { store.renamePack(packId, name) }
                .onFailure { showMessage(it.message ?: "Couldn't rename that pack.") }
        }
    }

    fun deletePack(packId: String) {
        viewModelScope.launch {
            attempt { store.deletePack(packId) }
                .onSuccess {
                    screen = Screen.Packs
                    showMessage("Pack deleted")
                }
                .onFailure { showMessage(it.message ?: "Couldn't delete that pack.") }
        }
    }

    fun deleteSticker(packId: String, fileName: String) {
        viewModelScope.launch {
            attempt { store.deleteSticker(packId, fileName) }
                .onSuccess {
                    if (store.packs.value.firstOrNull { it.id == packId }?.stickers?.isNotEmpty() == true) {
                        whatsAppSync = packId
                    }
                }
                .onFailure { showMessage(it.message ?: "Couldn't remove that sticker.") }
        }
    }

    fun consumeWhatsAppSync() {
        whatsAppSync = null
    }

    fun importPickedMedia(packId: String, uri: Uri) {
        val pack = store.packs.value.firstOrNull { it.id == packId } ?: return
        if (pack.stickers.size >= StickerStore.MAX_STICKERS) {
            showMessage("This pack is full (30 stickers max).")
            return
        }
        if (busyMessage != null) return
        busyMessage = "Preparing your media\u2026"
        viewModelScope.launch {
            val result = attempt { MediaFiles.copyFromUri(app, uri) }
            busyMessage = null
            result.onSuccess { openEditor(packId, it.file, it.mimeType) }
                .onFailure { showMessage(it.message ?: "Couldn't open that file.") }
        }
    }

    fun showLinkDialog(packId: String) {
        val pack = store.packs.value.firstOrNull { it.id == packId } ?: return
        if (pack.stickers.size >= StickerStore.MAX_STICKERS) {
            showMessage("This pack is full (30 stickers max).")
            return
        }
        linkDialog = LinkDialogState(packId = packId)
    }

    fun updateLinkUrl(url: String) {
        linkDialog = linkDialog?.copy(url = url, error = null)
    }

    fun dismissLinkDialog() {
        linkJob?.cancel()
        linkJob = null
        linkDialog = null
    }

    fun importLink() {
        val dialog = linkDialog ?: return
        if (dialog.loading) return
        val url = dialog.url.trim()
        if (url.isBlank()) {
            linkDialog = dialog.copy(error = "Paste a link first.")
            return
        }
        linkDialog = dialog.copy(loading = true, error = null)
        linkJob = viewModelScope.launch {
            val result = attempt { LinkImporter.fetch(app, url) }
            val current = linkDialog
            if (current == null || current.packId != dialog.packId) {
                result.getOrNull()?.let { MediaFiles.delete(it.file) }
                return@launch
            }
            result.onSuccess { media ->
                linkDialog = null
                openEditor(dialog.packId, media.file, media.mimeType)
            }.onFailure { throwable ->
                Log.e("Yogganstick", "Link import failed", throwable)
                linkDialog = linkDialog?.copy(
                    loading = false,
                    error = throwable.message ?: "Couldn't fetch that link.",
                )
            }
        }
    }

    fun openEditor(packId: String, file: File, mime: String?) {
        val pack = store.packs.value.firstOrNull { it.id == packId }
        if (pack == null) {
            MediaFiles.delete(file)
            showMessage("That sticker pack no longer exists.")
            screen = Screen.Packs
            return
        }
        if (pack.stickers.size >= StickerStore.MAX_STICKERS) {
            MediaFiles.delete(file)
            showMessage("This pack is full (30 stickers max).")
            return
        }
        previewJob?.cancel()
        screen = Screen.Editor(packId)
        editor = null
        editorLoading = true
        viewModelScope.launch {
            val result = attempt { MediaSource.open(app, file, mime) }
            if (screen != Screen.Editor(packId)) {
                result.getOrNull()?.close()
                MediaFiles.delete(file)
                return@launch
            }
            result.onSuccess { source ->
                val preview = attempt { source.previewAt(0) }
                if (screen != Screen.Editor(packId)) {
                    source.close()
                    MediaFiles.delete(file)
                    return@launch
                }
                preview.onSuccess { bitmap ->
                    editorLoading = false
                    editor = EditorUiState(
                        packId = packId,
                        packName = store.packs.value.firstOrNull { it.id == packId }?.name
                            ?: pack.name,
                        file = file,
                        source = source,
                        preview = bitmap,
                    )
                    loadPreviewSequence(source, file, 0)
                }.onFailure { throwable ->
                    Log.e("Yogganstick", "Preview failed", throwable)
                    source.close()
                    MediaFiles.delete(file)
                    editorLoading = false
                    screen = Screen.PackDetail(packId)
                    showMessage(throwable.message ?: "Couldn't open that media.")
                }
            }.onFailure { throwable ->
                Log.e("Yogganstick", "Source open failed", throwable)
                MediaFiles.delete(file)
                editorLoading = false
                screen = Screen.PackDetail(packId)
                showMessage(throwable.message ?: "Couldn't open that media.")
            }
        }
    }

    fun previewAt(timeMs: Long) {
        val current = editor ?: return
        if (!current.source.isAnimated) return
        previewJob?.cancel()
        editor = current.copy(previewLoading = true, previewFrames = emptyList())
        previewJob = viewModelScope.launch {
            val single = attempt { current.source.previewAt(timeMs) }
            val latest = editor
            if (latest != null && latest.file == current.file) {
                editor = latest.copy(
                    preview = single.getOrNull() ?: latest.preview,
                    previewLoading = false,
                )
                single.exceptionOrNull()?.let {
                    showMessage(it.message ?: "Couldn't preview that frame.")
                }
            }
            val frames = attempt { current.source.previewFrames(timeMs) }.getOrElse { emptyList() }
            val after = editor
            if (after != null && after.file == current.file) {
                editor = after.copy(previewFrames = frames)
            }
        }
    }

    private fun loadPreviewSequence(source: MediaSource, file: File, startMs: Long) {
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            val frames = attempt { source.previewFrames(startMs) }.getOrElse { emptyList() }
            val latest = editor
            if (latest != null && latest.file == file) {
                editor = latest.copy(previewFrames = frames)
            }
        }
    }

    fun saveSticker(
        zoom: Float,
        panX: Float,
        panY: Float,
        outline: Boolean,
        startMs: Long,
    ) {
        val current = editor ?: return
        if (current.isSaving) return
        editor = current.copy(isSaving = true, saveError = null)
        viewModelScope.launch {
            val encoded = attempt {
                StickerEncoder.encode(
                    source = current.source,
                    transform = StickerTransform(zoom, panX, panY, outline),
                    startMs = startMs,
                )
            }
            encoded.onSuccess { sticker ->
                val added = attempt {
                    store.addSticker(
                        packId = current.packId,
                        webp = sticker.webp,
                        animated = sticker.animated,
                        trayPng = sticker.trayPng,
                    )
                }
                added.onSuccess {
                    editor = editor?.copy(isSaving = false)
                    closeEditor()
                    showMessage("Sticker added")
                    whatsAppSync = current.packId
                }.onFailure { throwable ->
                    editor = editor?.copy(
                        isSaving = false,
                        saveError = throwable.message ?: "Couldn't save that sticker.",
                    )
                }
            }.onFailure { throwable ->
                editor = editor?.copy(
                    isSaving = false,
                    saveError = throwable.message ?: "Couldn't make that sticker.",
                )
            }
        }
    }

    fun dismissSaveError() {
        editor = editor?.copy(saveError = null)
    }

    fun closeEditor() {
        if (editor?.isSaving == true) return
        val packId = editor?.packId ?: (screen as? Screen.Editor)?.packId
        previewJob?.cancel()
        editor?.source?.close()
        MediaFiles.delete(editor?.file)
        editor = null
        editorLoading = false
        if (packId != null) screen = Screen.PackDetail(packId)
    }

    fun consumeMessage() {
        message = null
    }

    private fun showMessage(text: String) {
        message = text
    }

    private suspend fun <T> attempt(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (throwable: Throwable) {
        Result.failure(throwable)
    }

    override fun onCleared() {
        previewJob?.cancel()
        linkJob?.cancel()
        editor?.source?.close()
        MediaFiles.delete(editor?.file)
        super.onCleared()
    }
}
