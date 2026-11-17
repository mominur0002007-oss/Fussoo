package com.tuplayer.app.ui

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.tuplayer.app.playback.PlaybackSource
import com.tuplayer.app.util.formatDuration
import com.tuplayer.app.vault.VaultItem
import com.tuplayer.app.vault.VaultViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PIN_MIN = 4
private const val PIN_MAX = 8

/**
 * Private locker: videos copied into app-only storage behind a PIN.
 * The locker locks itself when the app leaves the screen. Copying a video does not delete the original.
 */
@Composable
fun LockerTab(vault: VaultViewModel, onPlay: (List<PlaybackSource>, Int) -> Unit) {
    val context = LocalContext.current
    val hasPin by vault.hasPin.collectAsState()
    val unlocked by vault.unlocked.collectAsState()
    val items by vault.items.collectAsState()
    val busy by vault.busy.collectAsState()
    val message by vault.message.collectAsState()
    val scope = rememberCoroutineScope()

    // Lock as soon as the app goes to the background, so the locker never stays open in the task switcher.
    val owner = context as? LifecycleOwner
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) vault.lock()
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val (name, durationMs) = readVideoInfo(context, uri)
                vault.importVideo(uri, name, durationMs)
            }
        }
    }

    when {
        !hasPin -> SetupPin(onCreate = { vault.setPin(it) })
        !unlocked -> LockedPanel(vault = vault, message = message)
        else -> UnlockedPanel(
            vaultItems = items,
            busy = busy,
            message = message,
            onAdd = { picker.launch(arrayOf("video/*")) },
            onPlay = { list, index -> onPlay(list.map { PlaybackSource(vault.fileUri(it), it.name) }, index) },
            onDelete = { vault.delete(it) },
            onLock = { vault.lock() },
            onErase = { vault.eraseAll() },
            onDismissMessage = { vault.clearMessage() }
        )
    }
}

@Composable
private fun SetupPin(onCreate: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val valid = pin.length in PIN_MIN..PIN_MAX && pin == confirm

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Create a locker PIN", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Text(
            "Copies stay in app-only storage. Your PIN opens it. Not encrypted: a forgotten PIN erases the locker.",
            color = Muted,
            fontSize = 13.sp
        )
        PinField(label = "New PIN ($PIN_MIN to $PIN_MAX digits)", value = pin) { pin = it }
        PinField(label = "Confirm PIN", value = confirm) { confirm = it }
        if (confirm.isNotEmpty() && pin != confirm) {
            Text("The PINs do not match.", color = Warning, fontSize = 12.sp)
        }
        Button(onClick = { onCreate(pin) }, enabled = valid, modifier = Modifier.fillMaxWidth()) {
            Text("Create locker")
        }
    }
}

@Composable
private fun LockedPanel(vault: VaultViewModel, message: String?) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmErase by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Box(
            Modifier.size(72.dp).clip(RoundedCornerShape(20.dp)).background(SurfaceB),
            contentAlignment = Alignment.Center
        ) {
            GlyphIcon(Glyph.LOCK, tint = TextPrimary, size = 36.dp)
        }
        Text("Locker is locked", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        PinField(label = "PIN", value = pin) { pin = it; error = null }
        error?.let { Text(it, color = Warning, fontSize = 12.sp) }
        message?.let { Text(it, color = Muted, fontSize = 12.sp) }
        Button(
            onClick = {
                error = vault.unlock(pin)
                pin = ""
            },
            enabled = pin.length >= PIN_MIN,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Unlock") }
        TextButton(onClick = { confirmErase = true }) { Text("Forgot PIN? Erase locker", color = Warning) }
    }

    if (confirmErase) {
        AlertDialog(
            onDismissRequest = { confirmErase = false },
            title = { Text("Erase the locker?") },
            text = { Text("Delete all locker videos? This cannot be undone. Originals are kept.") },
            confirmButton = {
                TextButton(onClick = { confirmErase = false; vault.eraseAll() }) { Text("Erase", color = Warning) }
            },
            dismissButton = { TextButton(onClick = { confirmErase = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun UnlockedPanel(
    vaultItems: List<VaultItem>,
    busy: Boolean,
    message: String?,
    onAdd: () -> Unit,
    onPlay: (List<VaultItem>, Int) -> Unit,
    onDelete: (VaultItem) -> Unit,
    onLock: () -> Unit,
    onErase: () -> Unit,
    onDismissMessage: () -> Unit
) {
    var pendingDelete by remember { mutableStateOf<VaultItem?>(null) }
    var confirmErase by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Private locker", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(
                    "${vaultItems.size} video${if (vaultItems.size == 1) "" else "s"} · app-only storage",
                    color = Muted,
                    fontSize = 11.sp
                )
            }
            TextButton(onClick = onLock) { Text("Lock") }
            Button(onClick = onAdd, enabled = !busy) { Text(if (busy) "Copying…" else "Add") }
        }

        message?.let {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(it, color = Warning, fontSize = 12.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismissMessage) { Text("OK") }
            }
        }

        if (vaultItems.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("No videos in the locker", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text(
                    "Copies are hidden from the gallery. Originals are not moved.",
                    color = Muted,
                    fontSize = 13.sp
                )
            }
        } else {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(vaultItems, key = { it.id }) { item ->
                    val index = vaultItems.indexOf(item)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPlay(vaultItems, index) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(SurfaceB),
                            contentAlignment = Alignment.Center
                        ) { GlyphIcon(Glyph.FILM, tint = TextPrimary, size = 22.dp) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.name, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${formatSize(item.sizeBytes)} · ${if (item.durationMs > 0) formatDuration(item.durationMs) else "unknown length"}",
                                color = Muted,
                                fontSize = 12.sp
                            )
                        }
                        Box(
                            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).clickable { pendingDelete = item },
                            contentAlignment = Alignment.Center
                        ) { GlyphIcon(Glyph.TRASH, tint = Muted, size = 20.dp) }
                    }
                }
                item {
                    TextButton(onClick = { confirmErase = true }, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Text("Erase locker", color = Warning)
                    }
                }
            }
        }
    }

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete from locker?") },
            text = { Text("\"${item.name}\" will be removed from the locker. Original kept.") },
            confirmButton = {
                TextButton(onClick = { onDelete(item); pendingDelete = null }) { Text("Delete", color = Warning) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } }
        )
    }

    if (confirmErase) {
        AlertDialog(
            onDismissRequest = { confirmErase = false },
            title = { Text("Erase the locker?") },
            text = { Text("Delete all locker videos and the PIN? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { confirmErase = false; onErase() }) { Text("Erase", color = Warning) }
            },
            dismissButton = { TextButton(onClick = { confirmErase = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun PinField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { typed ->
            if (typed.length <= PIN_MAX && typed.all { it.isDigit() }) onChange(typed)
        },
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth()
    )
}

/** Reads the display name and duration. Runs off the main thread and falls back to safe defaults. */
private suspend fun readVideoInfo(context: Context, uri: Uri): Pair<String, Long> = withContext(Dispatchers.IO) {
    var name = "Video"
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val col = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (col >= 0) name = cursor.getString(col) ?: name
            }
        }
    }
    val duration = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } finally {
            retriever.release()
        }
    }.getOrDefault(0L)
    name to duration
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> String.format(java.util.Locale.US, "%.1f GB", bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1L shl 20).toDouble())
    else -> String.format(java.util.Locale.US, "%d KB", (bytes / 1024).coerceAtLeast(1))
}
