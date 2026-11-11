package com.yogganstick

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.yogganstick.ui.BusyOverlay
import com.yogganstick.ui.EditorScreen
import com.yogganstick.ui.EditorText
import com.yogganstick.ui.PackScreen
import com.yogganstick.ui.PacksScreen
import com.yogganstick.ui.PinkMist
import com.yogganstick.ui.YogganstickTheme

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            YogganstickTheme {
                AppRoot(viewModel)
            }
        }
    }
}

@Composable
private fun AppRoot(viewModel: AppViewModel) {
    val packs by viewModel.packs.collectAsState()
    val defaultOwner by viewModel.defaultOwner.collectAsState()
    val screen = viewModel.screen
    val context = LocalContext.current
    val activity = context as? Activity
    val darkBars = screen is Screen.Editor

    SideEffect {
        val window = activity?.window ?: return@SideEffect
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = !darkBars
        controller.isAppearanceLightNavigationBars = !darkBars
    }

    LaunchedEffect(viewModel.message) {
        val text = viewModel.message ?: return@LaunchedEffect
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
        viewModel.consumeMessage()
    }

    BackHandler(
        enabled = screen !is Screen.Packs || viewModel.linkDialog != null,
    ) {
        viewModel.back()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when (val current = screen) {
            Screen.Packs -> {
                PacksScreen(
                    packs = packs,
                    defaultOwner = defaultOwner,
                    trayFileFor = { packId -> viewModel.trayFile(packId) },
                    onOpenPack = viewModel::openPack,
                    onSaveOwner = viewModel::setDefaultOwner,
                    onCreatePack = viewModel::createPack,
                )
            }

            is Screen.PackDetail -> {
                val pack = packs.firstOrNull { it.id == current.packId }
                if (pack != null) {
                    PackScreen(
                        pack = pack,
                        stickerFileFor = { fileName ->
                            viewModel.stickerFile(pack.id, fileName)
                        },
                        linkDialog = viewModel.linkDialog,
                        onBack = viewModel::back,
                        onPickedMedia = { uri -> viewModel.importPickedMedia(pack.id, uri) },
                        onShowLinkDialog = { viewModel.showLinkDialog(pack.id) },
                        onDismissLinkDialog = viewModel::dismissLinkDialog,
                        onUpdateLinkUrl = viewModel::updateLinkUrl,
                        onImportLink = viewModel::importLink,
                        onDeleteSticker = { fileName ->
                            viewModel.deleteSticker(pack.id, fileName)
                        },
                        onRenamePack = { name -> viewModel.renamePack(pack.id, name) },
                        onDeletePack = { viewModel.deletePack(pack.id) },
                        onMessage = viewModel::notify,
                        whatsAppSync = viewModel.whatsAppSync,
                        onWhatsAppSyncHandled = viewModel::consumeWhatsAppSync,
                    )
                } else {
                    LaunchedEffect(current.packId) { viewModel.openPacks() }
                    LoadingScreen(dark = false, message = "Loading pack\u2026")
                }
            }

            is Screen.Editor -> {
                val editor = viewModel.editor
                if (editor != null) {
                    EditorScreen(
                        editor = editor,
                        onClose = viewModel::closeEditor,
                        onPreviewAt = viewModel::previewAt,
                        onSave = viewModel::saveSticker,
                        onDismissError = viewModel::dismissSaveError,
                    )
                } else {
                    LoadingScreen(dark = true, message = "Opening your media\u2026")
                }
            }
        }

        viewModel.busyMessage?.let { BusyOverlay(it) }
    }
}

@Composable
private fun LoadingScreen(dark: Boolean, message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (dark) com.yogganstick.ui.EditorBackground else MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                modifier = Modifier.size(34.dp),
                color = if (dark) PinkMist else MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = if (dark) EditorText.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
