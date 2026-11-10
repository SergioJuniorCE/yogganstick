package com.yogganstick.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.yogganstick.LinkDialogState
import com.yogganstick.data.Sticker
import com.yogganstick.data.StickerPack
import com.yogganstick.data.StickerStore
import com.yogganstick.provider.StickerContentProvider
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PackScreen(
    pack: StickerPack,
    stickerFileFor: (String) -> File,
    linkDialog: LinkDialogState?,
    onBack: () -> Unit,
    onPickedMedia: (Uri) -> Unit,
    onShowLinkDialog: () -> Unit,
    onDismissLinkDialog: () -> Unit,
    onUpdateLinkUrl: (String) -> Unit,
    onImportLink: () -> Unit,
    onDeleteSticker: (String) -> Unit,
    onRenamePack: (String) -> Unit,
    onDeletePack: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val context = LocalContext.current
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    var showRenameDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var renameText by rememberSaveable { mutableStateOf(pack.name) }
    var selectedFileName by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedSticker = pack.stickers.firstOrNull { it.fileName == selectedFileName }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) onPickedMedia(uri)
    }

    val addPackLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            onMessage("Pack added to WhatsApp")
        } else {
            val validationError = result.data?.getStringExtra("validation_error")
            if (validationError != null) {
                Log.e("Yogganstick", "WhatsApp: $validationError")
                onMessage("WhatsApp rejected this pack. Make sure WhatsApp is updated.")
            } else {
                onMessage("Pack was not added.")
            }
        }
    }

    fun openGallery() {
        if (pack.stickers.size >= StickerStore.MAX_STICKERS) {
            onMessage("This pack is full (30 stickers max).")
            return
        }
        galleryLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo),
        )
    }

    fun addToWhatsApp() {
        val intent = Intent("com.whatsapp.intent.action.ENABLE_STICKER_PACK").apply {
            putExtra("sticker_pack_id", pack.id)
            putExtra("sticker_pack_authority", StickerContentProvider.authority(context))
            putExtra("sticker_pack_name", pack.name)
        }
        if (context.packageManager.queryIntentActivities(intent, 0).isEmpty()) {
            onMessage("WhatsApp isn't installed on this phone.")
            return
        }
        runCatching {
            addPackLauncher.launch(Intent.createChooser(intent, "Add to WhatsApp"))
        }.onFailure {
            onMessage("Couldn't open WhatsApp.")
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = pack.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${pack.stickers.size} of 30 stickers \u00B7 by ${pack.displayOwner}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Pack options")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Rename pack") },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    renameText = pack.name
                                    showRenameDialog = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete pack") },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    showDeleteDialog = true
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Column(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (pack.stickers.isNotEmpty()) {
                        Button(
                            onClick = { addToWhatsApp() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                        ) {
                            Text("Add to WhatsApp")
                        }
                    }
                    if (pack.stickers.size < StickerStore.MAX_STICKERS) {
                        OutlinedButton(
                            onClick = {
                                showAddDialog = true
                                onDismissLinkDialog()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(19.dp))
                            Spacer(Modifier.width(7.dp))
                            Text(if (pack.stickers.isEmpty()) "Add your first sticker" else "Add sticker")
                        }
                    } else {
                        Text(
                            text = "This pack is full (30 stickers max).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    }
                    if (pack.stickers.isEmpty()) {
                        Text(
                            text = "Photos, GIFs, videos, or a link from X.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        },
    ) { padding ->
        if (pack.stickers.isEmpty()) {
            EmptyPack(
                onAdd = { showAddDialog = true },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(104.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(pack.stickers, key = { it.fileName }) { sticker ->
                    StickerTile(
                        file = stickerFileFor(sticker.fileName),
                        animated = sticker.animated,
                        onClick = { selectedFileName = sticker.fileName },
                        modifier = Modifier.aspectRatio(1f),
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddSourceDialog(
            onDismiss = { showAddDialog = false },
            onGallery = {
                showAddDialog = false
                openGallery()
            },
            onLink = {
                showAddDialog = false
                onShowLinkDialog()
            },
        )
    }

    if (linkDialog != null && linkDialog.packId == pack.id) {
        LinkStickerDialog(
            state = linkDialog,
            onDismiss = onDismissLinkDialog,
            onUrlChange = onUpdateLinkUrl,
            onImport = onImportLink,
        )
    }

    if (showRenameDialog) {
        RenamePackDialog(
            currentName = pack.name,
            name = renameText,
            onNameChange = { renameText = it },
            onDismiss = { showRenameDialog = false },
            onRename = {
                if (renameText.isNotBlank()) {
                    onRenamePack(renameText)
                    showRenameDialog = false
                }
            },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            shape = RoundedCornerShape(28.dp),
            title = { Text("Delete this pack?") },
            text = {
                Text(
                    "\u201C${pack.name}\u201D and its ${pack.stickers.size} " +
                        "${if (pack.stickers.size == 1) "sticker" else "stickers"} will be removed from this phone.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        onDeletePack()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (selectedSticker != null) {
        StickerDetailDialog(
            sticker = selectedSticker,
            file = stickerFileFor(selectedSticker.fileName),
            onDismiss = { selectedFileName = null },
            onDelete = {
                onDeleteSticker(selectedSticker.fileName)
                selectedFileName = null
            },
        )
    }
}

@Composable
private fun EmptyPack(
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            StickerMark(Modifier.size(112.dp))
            Spacer(Modifier.height(20.dp))
            Text(
                text = "This pack is empty",
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Add a photo, GIF or video from your gallery \u2014 or paste a link from X.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            Button(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(7.dp))
                Text("Add your first sticker")
            }
        }
    }
}

@Composable
private fun AddSourceDialog(
    onDismiss: () -> Unit,
    onGallery: () -> Unit,
    onLink: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Add a sticker") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SourceOption(
                    emoji = "\uD83D\uDDBC\uFE0F",
                    title = "Photo, GIF or video",
                    subtitle = "Choose from your gallery",
                    onClick = onGallery,
                )
                SourceOption(
                    emoji = "\uD83D\uDD17",
                    title = "Paste a link",
                    subtitle = "An X/Twitter post or direct GIF link",
                    onClick = onLink,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun SourceOption(
    emoji: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(emoji, style = MaterialTheme.typography.titleLarge)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LinkStickerDialog(
    state: LinkDialogState,
    onDismiss: () -> Unit,
    onUrlChange: (String) -> Unit,
    onImport: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val focusRequester = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Sticker from a link") },
        text = {
            Column {
                Text(
                    "Paste a post from X. GIFs and videos become animated stickers; photos become still ones.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.url,
                    onValueChange = onUrlChange,
                    label = { Text("Link") },
                    placeholder = { Text("https://x.com/\u2026/status/\u2026") },
                    singleLine = true,
                    enabled = !state.loading,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { onImport() }),
                    trailingIcon = {
                        TextButton(
                            onClick = {
                                clipboard.getText()?.text?.let(onUrlChange)
                            },
                            enabled = !state.loading,
                        ) {
                            Text("Paste")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Also works with direct .gif, .mp4, .jpg or .png links.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.loading) {
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("Fetching the media\u2026", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                val error = state.error
                if (error != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onImport,
                enabled = !state.loading && state.url.isNotBlank(),
            ) {
                Text("Get sticker")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
    LaunchedEffect(state.loading) {
        if (!state.loading && state.url.isEmpty()) {
            runCatching { focusRequester.requestFocus() }
        }
    }
}

@Composable
private fun RenamePackDialog(
    currentName: String,
    name: String,
    onNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Rename pack") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 60) onNameChange(it) },
                label = { Text("Pack name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        keyboard?.hide()
                        onRename()
                    },
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(onClick = onRename, enabled = name.isNotBlank() && name != currentName) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun StickerDetailDialog(
    sticker: Sticker,
    file: File,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Sticker",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .padding(4.dp),
                ) {
                    Checkerboard(
                        modifier = Modifier.fillMaxSize(),
                        light = true,
                        cellSize = 22.dp,
                    )
                    StickerImage(
                        file = file,
                        modifier = Modifier.fillMaxSize(),
                        contentDescription = "Sticker preview",
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (sticker.animated) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Text(
                                "ANIMATED",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Text(
                                "STILL",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        formatBytes(file.length()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onDelete,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Delete")
                    }
                    Button(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Done")
                    }
                }
            }
        }
    }
}
