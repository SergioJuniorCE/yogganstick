package com.yogganstick.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.yogganstick.data.StickerPack
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PacksScreen(
    packs: List<StickerPack>,
    defaultOwner: String?,
    trayFileFor: (String) -> File,
    onOpenPack: (String) -> Unit,
    onSaveOwner: (String) -> Unit,
    onCreatePack: (String, String) -> Unit,
) {
    var showOwnerDialog by rememberSaveable { mutableStateOf(false) }
    var ownerDraft by rememberSaveable { mutableStateOf("") }
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var newPackName by rememberSaveable { mutableStateOf("") }
    var newPackOwner by rememberSaveable { mutableStateOf("") }

    fun beginCreatePack() {
        val existing = defaultOwner?.trim().orEmpty()
        if (existing.isEmpty()) {
            ownerDraft = ""
            showOwnerDialog = true
        } else {
            newPackOwner = existing
            showCreateDialog = true
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (packs.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { beginCreatePack() },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("New pack") },
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = padding.calculateTopPadding() + 12.dp,
                bottom = padding.calculateBottomPadding() + 100.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { BrandHeader() }
            if (packs.isEmpty()) {
                item { EmptyPacksCard(onCreate = { beginCreatePack() }) }
                item { HowItWorks() }
            } else {
                item {
                    Column(Modifier.padding(top = 10.dp, bottom = 2.dp)) {
                        Text(
                            text = "Your packs",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            text = "Add a pack to WhatsApp to send its stickers.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(packs, key = { it.id }) { pack ->
                    PackCard(
                        pack = pack,
                        trayFile = trayFileFor(pack.id),
                        onClick = { onOpenPack(pack.id) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    Text(
                        text = "Stickers live on this phone only. Nothing is uploaded.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, bottom = 8.dp),
                    )
                }
            }
        }
    }

    if (showOwnerDialog) {
        OwnerNameDialog(
            name = ownerDraft,
            onNameChange = { ownerDraft = it },
            onDismiss = { showOwnerDialog = false },
            onContinue = {
                val clean = ownerDraft.trim()
                if (clean.isNotEmpty()) {
                    onSaveOwner(clean)
                    newPackOwner = clean
                    showOwnerDialog = false
                    showCreateDialog = true
                }
            },
        )
    }

    if (showCreateDialog) {
        CreatePackDialog(
            name = newPackName,
            owner = newPackOwner,
            onNameChange = { newPackName = it },
            onOwnerChange = { newPackOwner = it },
            onDismiss = {
                showCreateDialog = false
                newPackName = ""
            },
            onCreate = {
                val name = newPackName.trim()
                val owner = newPackOwner.trim()
                if (name.isNotEmpty() && owner.isNotEmpty()) {
                    onCreatePack(name, owner)
                    showCreateDialog = false
                    newPackName = ""
                }
            },
        )
    }
}

@Composable
private fun BrandHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StickerMark(Modifier.size(58.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "Yogganstick",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Photos, GIFs and videos \u2192 WhatsApp stickers",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyPacksCard(onCreate: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(34.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.55f),
                    modifier = Modifier.size(148.dp),
                ) {}
                StickerMark(Modifier.size(108.dp))
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = "Make stickers from anything.",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Turn a photo, GIF or video into a sticker. Then add your pack to WhatsApp in one tap.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onCreate,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Create your first pack")
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Photos \u00B7 GIFs \u00B7 Videos \u00B7 X links",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.65f),
            )
        }
    }
}

@Composable
private fun HowItWorks() {
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Three steps", style = MaterialTheme.typography.titleMedium)
            StepRow(1, "Make a pack", "Give it a name \u2014 one pack per vibe.")
            StepRow(2, "Add stickers", "Pick from your gallery or paste a link.")
            StepRow(3, "Send to WhatsApp", "Tap Add to WhatsApp, then start sticking.")
        }
    }
}

@Composable
private fun StepRow(number: Int, title: String, body: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(34.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = number.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OwnerNameDialog(
    name: String,
    onNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onContinue: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text("What's your name?") },
        text = {
            Column {
                Text(
                    "This is the publisher name on your WhatsApp sticker packs. You can change it whenever you create a pack.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 60) onNameChange(it) },
                    label = { Text("Owner name") },
                    placeholder = { Text("e.g. Alex") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            keyboard?.hide()
                            onContinue()
                        },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
            }
        },
        confirmButton = {
            Button(onClick = onContinue, enabled = name.isNotBlank()) {
                Text("Continue")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@Composable
private fun CreatePackDialog(
    name: String,
    owner: String,
    onNameChange: (String) -> Unit,
    onOwnerChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onCreate: () -> Unit,
) {
    val nameFocusRequester = remember { FocusRequester() }
    val ownerFocusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text("New sticker pack") },
        text = {
            Column {
                Text(
                    "Name it after the set \u2014 reactions, pets, cursed images\u2026",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 60) onNameChange(it) },
                    label = { Text("Pack name") },
                    placeholder = { Text("e.g. Reaction faces") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(
                        onNext = { ownerFocusRequester.requestFocus() },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(nameFocusRequester),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = owner,
                    onValueChange = { if (it.length <= 60) onOwnerChange(it) },
                    label = { Text("Owner name") },
                    placeholder = { Text("e.g. Alex") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            keyboard?.hide()
                            onCreate()
                        },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(ownerFocusRequester),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "The owner is shown as the publisher in WhatsApp.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onCreate,
                enabled = name.isNotBlank() && owner.isNotBlank(),
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
    LaunchedEffect(Unit) { nameFocusRequester.requestFocus() }
}
