package com.tuplayer.app.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import com.tuplayer.app.music.AudioEntry
import com.tuplayer.app.music.MusicController
import com.tuplayer.app.music.queryDeviceMusic
import com.tuplayer.app.util.formatDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The permission that lets the app list device audio. Android 13+ uses READ_MEDIA_AUDIO. */
private fun audioPermission(): String =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

/**
 * Music tab: every audio file on the device, with search. Tapping a track starts the list from there.
 * Playback runs in [com.tuplayer.app.music.MusicService], so it continues with the screen off and shows
 * a media notification. The mini bar at the bottom opens the full now-playing view.
 */
@Composable
fun MusicTab(music: MusicController, query: String) {
    val context = LocalContext.current
    var tracks by remember { mutableStateOf<List<AudioEntry>?>(null) }
    var permissionDenied by remember { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val loadTracks: (Context) -> Unit = { ctx ->
        // Reading MediaStore touches the disk, so it runs off the main thread.
        scope.launch {
            tracks = withContext(Dispatchers.IO) { runCatching { queryDeviceMusic(ctx) }.getOrDefault(emptyList()) }
        }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            permissionDenied = false
            loadTracks(context)
        } else {
            permissionDenied = true
        }
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, audioPermission()) == PackageManager.PERMISSION_GRANTED) {
            loadTracks(context)
        } else {
            launcher.launch(audioPermission())
        }
    }

    val nowPlaying by music.nowPlaying.collectAsState()
    val isPlaying by music.isPlaying.collectAsState()
    val musicError by music.error.collectAsState()

    BackHandler(enabled = expanded) { expanded = false }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            val all = tracks
            val shown = remember(all, query) {
                all.orEmpty().filter {
                    query.isBlank() ||
                        it.title.contains(query, ignoreCase = true) ||
                        it.artist.contains(query, ignoreCase = true) ||
                        it.album.contains(query, ignoreCase = true)
                }
            }
            when {
                all == null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Accent)
                }
                permissionDenied && all.isEmpty() -> EmptyState(
                    title = "Music access is off",
                    body = "Allow audio access to list music.",
                    primaryLabel = "Allow access",
                    onPrimary = { launcher.launch(audioPermission()) },
                    secondaryLabel = null,
                    onSecondary = null
                )
                all.isEmpty() -> EmptyState(
                    title = "No music found",
                    body = "No music found on this device.",
                    primaryLabel = "Scan again",
                    onPrimary = { loadTracks(context) },
                    secondaryLabel = null,
                    onSecondary = null
                )
                else -> LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = if (nowPlaying != null) 88.dp else 16.dp)
                ) {
                    item {
                        Text(
                            "${shown.size} tracks",
                            color = Muted,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                        )
                    }
                    items(shown, key = { it.uri.toString() }) { track ->
                        TrackRow(
                            track = track,
                            active = nowPlaying?.mediaUri == track.uri.toString(),
                            onClick = { music.playList(shown, shown.indexOf(track).coerceAtLeast(0)) }
                        )
                    }
                }
            }
            musicError?.let { message ->
                Text(message, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            }
        }

        // Mini bar: visible while a track is loaded. Tapping the body opens the full view.
        val np = nowPlaying
        if (np != null && !expanded) {
            DarkRegion { Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xE61C1C24))
                    .border(1.dp, Color(0x26FFFFFF), RoundedCornerShape(18.dp))
                    .clickable { expanded = true }
                    .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(np.title, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(np.artist.ifBlank { "Unknown artist" }, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconCircle(if (isPlaying) Glyph.PAUSE else Glyph.PLAY, size = 44.dp) { music.toggle() }
                IconCircle(Glyph.NEXT, size = 40.dp) { music.next() }
            } }
        }
    }

    if (expanded && nowPlaying != null) {
        DarkRegion { NowPlayingView(music = music, onCollapse = { expanded = false }) }
    }
}

@Composable
private fun TrackRow(track: AudioEntry, active: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (active) Accent else SurfaceB),
            contentAlignment = Alignment.Center
        ) {
            GlyphIcon(Glyph.MUSIC, tint = TextPrimary, size = 22.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                color = if (active) Accent else TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                listOf(track.artist.ifBlank { "Unknown artist" }, track.album).filter { it.isNotBlank() }.joinToString(" · "),
                color = Muted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(formatDuration(track.durationMs), color = Muted, fontSize = 12.sp)
    }
}

/** Full now-playing view: art placeholder, title, a seek bar, and transport with shuffle and repeat. */
@Composable
private fun NowPlayingView(music: MusicController, onCollapse: () -> Unit) {
    val np by music.nowPlaying.collectAsState()
    val isPlaying by music.isPlaying.collectAsState()
    val position by music.positionMs.collectAsState()
    val duration by music.durationMs.collectAsState()
    val shuffle by music.shuffle.collectAsState()
    val repeat by music.repeatMode.collectAsState()
    val track = np ?: return

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF1B2A44), Color(0xFF0B0B0F))))
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconCircle(Glyph.CLOSE, size = 40.dp) { onCollapse() }
            Spacer(Modifier.weight(1f))
            Text("Now playing", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(40.dp))
        }

        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0x33FFFFFF)),
            contentAlignment = Alignment.Center
        ) {
            GlyphIcon(Glyph.MUSIC, tint = TextPrimary, size = 96.dp)
        }

        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(track.title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 22.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOf(track.artist, track.album).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Unknown artist" },
                color = Muted,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Column(Modifier.fillMaxWidth()) {
            val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
            Slider(
                value = fraction,
                onValueChange = { if (duration > 0) music.seekTo((it * duration).toLong()) },
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Accent, inactiveTrackColor = Color(0x44FFFFFF)),
                enabled = duration > 0
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatDuration(position), color = Muted, fontSize = 12.sp)
                Text(formatDuration(duration), color = Muted, fontSize = 12.sp)
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconCircle(Glyph.SHUFFLE, size = 46.dp, active = shuffle) { music.toggleShuffle() }
            IconCircle(Glyph.PREVIOUS, size = 52.dp) { music.previous() }
            IconCircle(if (isPlaying) Glyph.PAUSE else Glyph.PLAY, size = 74.dp, active = true) { music.toggle() }
            IconCircle(Glyph.NEXT, size = 52.dp) { music.next() }
            IconCircle(
                Glyph.REPEAT,
                size = 46.dp,
                active = repeat != Player.REPEAT_MODE_OFF
            ) { music.cycleRepeat() }
        }
    }
}

/** Round icon button with an accent fill when [active]. Used for both mini bar and full view. */
@Composable
private fun IconCircle(glyph: Glyph, size: androidx.compose.ui.unit.Dp, active: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (active) Accent else Color(0x33FFFFFF))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        GlyphIcon(glyph, tint = TextPrimary, size = size * 0.45f)
    }
}
