package com.tuplayer.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import com.tuplayer.app.settings.AccentColor
import com.tuplayer.app.settings.AppearanceViewModel
import com.tuplayer.app.settings.ThemeMode
import com.tuplayer.app.playback.SubtitleColor
import com.tuplayer.app.playback.SubtitlePosition
import com.tuplayer.app.playback.SubtitleSize
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuplayer.app.BuildConfig
import com.tuplayer.app.diagnostics.CrashLog
import com.tuplayer.app.library.VideoEntry
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import com.tuplayer.app.playback.EnginePreference
import com.tuplayer.app.playback.PlaybackController

/** Videos tab: every device and folder video, with search applied. */
@Composable
fun VideosTab(
    videos: List<VideoEntry>,
    query: String,
    scanning: Boolean,
    status: String,
    onPlay: (VideoEntry, List<VideoEntry>) -> Unit,
    onScan: () -> Unit,
    onOpenFile: () -> Unit,
    onPickFolder: () -> Unit
) {
    if (videos.isEmpty()) {
        EmptyState(
            title = if (scanning) "Scanning…" else "No videos yet",
            body = "$status",
            primaryLabel = "Scan device",
            onPrimary = onScan,
            secondaryLabel = "Open file",
            onSecondary = onOpenFile
        )
        return
    }
    val shown = remember(videos, query) {
        videos.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("${shown.size} videos", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(status, color = Muted, fontSize = 11.sp, maxLines = 1)
                }
                TextButton(onClick = onScan, enabled = !scanning) { Text(if (scanning) "Scanning…" else "Rescan") }
                TextButton(onClick = onPickFolder) { Text("Folder") }
            }
        }
        if (shown.isEmpty()) {
            item {
                Text(
                    "No videos match \"$query\".",
                    color = Muted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
        items(shown, key = { it.uri.toString() }) { video ->
            VideoListItem(video, onClick = { onPlay(video, shown) })
        }
    }
}

/** Folders tab: one row per folder, drilling into a folder's videos. Back returns to the folder list. */
@Composable
fun FoldersTab(videos: List<VideoEntry>, query: String, onPlay: (VideoEntry, List<VideoEntry>) -> Unit) {
    var openFolder by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = openFolder != null) { openFolder = null }

    val folders = remember(videos, query) {
        videos
            .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
            .groupBy { it.folder.ifBlank { "Other" } }
            .toSortedMap(String.CASE_INSENSITIVE_ORDER)
    }

    val current = openFolder
    if (current == null) {
        if (folders.isEmpty()) {
            Text("No folders. Scan or pick one in Videos.", color = Muted, fontSize = 13.sp, modifier = Modifier.padding(24.dp))
            return
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(folders.entries.toList(), key = { it.key }) { entry ->
                FolderRow(entry.key, entry.value.size) { openFolder = entry.key }
            }
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { openFolder = null }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("‹  Folders", color = Accent, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Spacer(Modifier.width(12.dp))
                Text(current, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(folders[current].orEmpty(), key = { it.uri.toString() }) { video ->
                    VideoListItem(video, onClick = { onPlay(video, folders[current].orEmpty()) })
                }
            }
        }
    }
}

/** Shows the last crash and the last exit reason, so playback failures on a phone can be diagnosed. */
@Composable
fun DiagnosticsCard() {
    val context = LocalContext.current
    var crash by remember { mutableStateOf(CrashLog.readLastCrash(context)) }
    val exit = remember { CrashLog.lastExitSummary(context) }
    Card(colors = CardDefaults.cardColors(containerColor = SurfaceA), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Diagnostics", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text("Last app exit: ${exit ?: "none recorded"}", color = Muted, fontSize = 12.sp)
            Text("Last crash report:", color = TextPrimary, fontSize = 13.sp)
            Text(
                crash?.take(2000) ?: "None recorded.",
                color = Muted,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
            if (crash != null) {
                TextButton(onClick = {
                    CrashLog.clearLastCrash(context)
                    crash = null
                }) { Text("Clear crash report") }
            }
        }
    }
}

/** Settings: appearance, subtitles and engine. Every change is saved right away. */
@Composable
fun SettingsTab(playback: PlaybackController, appearance: AppearanceViewModel) {
    val preference by playback.enginePreference.collectAsState()
    val mpvSupported = remember { playback.isMpvSupported() }
    val mode by appearance.mode.collectAsState()
    val accent by appearance.accent.collectAsState()
    val subtitle by playback.subtitleStyle.collectAsState()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SettingsHeader("Appearance")
        ChoiceRow {
            ThemeMode.entries.forEach { option ->
                SettingChip(option.label, selected = mode == option) { appearance.setMode(option) }
            }
        }
        ChoiceRow {
            AccentColor.entries.forEach { option ->
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(option.argb))
                        .border(
                            width = if (accent == option) 3.dp else 0.dp,
                            color = TextPrimary,
                            shape = CircleShape
                        )
                        .clickable { appearance.setAccent(option) }
                )
            }
        }

        SettingsHeader("Subtitles")
        Text("Size", color = Muted, fontSize = 12.sp)
        ChoiceRow {
            SubtitleSize.entries.forEach { size ->
                SettingChip(size.label, selected = subtitle.size == size) {
                    playback.setSubtitleStyle(subtitle.copy(size = size))
                }
            }
        }
        Text("Colour", color = Muted, fontSize = 12.sp)
        ChoiceRow {
            SubtitleColor.entries.forEach { color ->
                SettingChip(color.label, selected = subtitle.color == color) {
                    playback.setSubtitleStyle(subtitle.copy(color = color))
                }
            }
        }
        Text("Position", color = Muted, fontSize = 12.sp)
        ChoiceRow {
            SubtitlePosition.entries.forEach { position ->
                SettingChip(position.label, selected = subtitle.position == position) {
                    playback.setSubtitleStyle(subtitle.copy(position = position))
                }
            }
        }
        Text("Background", color = Muted, fontSize = 12.sp)
        ChoiceRow {
            SettingChip("Off", selected = !subtitle.background) {
                playback.setSubtitleStyle(subtitle.copy(background = false))
            }
            SettingChip("Box", selected = subtitle.background) {
                playback.setSubtitleStyle(subtitle.copy(background = true))
            }
        }

        SettingsHeader("Playback engine")
        EngineOption(
            title = "Automatic",
            description = "Media3, with libmpv for other files",
            selected = preference == EnginePreference.AUTO
        ) { playback.setEnginePreference(EnginePreference.AUTO) }
        EngineOption(
            title = "Media3",
            description = "Hardware decoding",
            selected = preference == EnginePreference.MEDIA3
        ) { playback.setEnginePreference(EnginePreference.MEDIA3) }
        EngineOption(
            title = "libmpv",
            description = if (mpvSupported) "Wide codec support" else "Needs Android 8.0+",
            selected = preference == EnginePreference.MPV,
            enabled = mpvSupported
        ) { playback.setEnginePreference(EnginePreference.MPV) }

        SettingsHeader("About")
        Text("TU Player ${BuildConfig.VERSION_NAME} · GPL-3.0-or-later", color = Muted, fontSize = 12.sp)
        Text("Created by: Mominur Islam", color = Muted, fontSize = 12.sp)
        Text("Email: mominur0002007@gmail.com", color = Muted, fontSize = 12.sp)
        DiagnosticsCard()
    }
}

@Composable
private fun SettingsHeader(title: String) {
    Text(title, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun ChoiceRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
private fun SettingChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 13.sp) },
        shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = SurfaceB,
            labelColor = TextPrimary,
            selectedContainerColor = Accent,
            selectedLabelColor = Color.White
        )
    )
}

@Composable
private fun EngineOption(
    title: String,
    description: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) SurfaceB else SurfaceA)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick, enabled = enabled)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = if (enabled) TextPrimary else Muted)
            Text(description, color = Muted, fontSize = 11.sp)
        }
    }
}
