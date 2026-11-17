package com.tuplayer.app.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tuplayer.app.library.LibraryViewModel
import com.tuplayer.app.library.VideoEntry
import com.tuplayer.app.music.MusicController
import com.tuplayer.app.settings.AppearanceViewModel
import com.tuplayer.app.playback.LaunchResult
import com.tuplayer.app.playback.PlaybackController
import com.tuplayer.app.playback.PlaybackSource
import com.tuplayer.app.vault.VaultViewModel

enum class AppTab(val label: String) {
    VIDEOS("Videos"),
    MUSIC("Music"),
    FOLDERS("Folders"),
    LOCKER("Locker"),
    SETTINGS("Settings");

    fun glyph(): Glyph = when (this) {
        VIDEOS -> Glyph.FILM
        MUSIC -> Glyph.MUSIC
        FOLDERS -> Glyph.FOLDER
        LOCKER -> Glyph.LOCK
        SETTINGS -> Glyph.SLIDERS
    }

    /** Only the library tabs have a search box. The locker and settings do not. */
    val searchable: Boolean get() = this == VIDEOS || this == MUSIC || this == FOLDERS
}

/**
 * App shell: header with search, a bottom tab bar, and the full-screen player.
 * The player is a layer above the tabs. Closing it saves the position and returns to the list.
 */
@Composable
fun TUPlayerApp(
    library: LibraryViewModel = viewModel(),
    playback: PlaybackController = viewModel(),
    music: MusicController = viewModel(),
    vault: VaultViewModel = viewModel(),
    appearance: AppearanceViewModel = viewModel()
) {
    val themeMode by appearance.mode.collectAsState()
    val accent by appearance.accent.collectAsState()
    TUTheme(mode = themeMode, accent = accent) {
        val context = LocalContext.current
        var selectedTab by rememberSaveable { mutableStateOf(AppTab.VIDEOS) }
        var searching by rememberSaveable { mutableStateOf(false) }
        var query by rememberSaveable { mutableStateOf("") }
        var playerOpen by rememberSaveable { mutableStateOf(false) }

        val videos by library.videos.collectAsState()
        val scanning by library.scanning.collectAsState()
        val libraryStatus by library.status.collectAsState()
        val nowPlaying by playback.current.collectAsState()

        fun requiredVideoPermission(): String =
            if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO
            else Manifest.permission.READ_EXTERNAL_STORAGE

        val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                library.scanDevice()
            } else {
                library.setStatus("Media access denied. Open files or pick a folder.")
            }
        }
        val openFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                playback.playLocal(uri)
                playerOpen = true
            }
        }
        val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) library.setFolder(uri)
        }

        fun requestDeviceScan() {
            val permission = requiredVideoPermission()
            if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                library.scanDevice()
            } else {
                permissionLauncher.launch(permission)
            }
        }

        LaunchedEffect(Unit) { requestDeviceScan() }

        // Leaving the app pauses video, unless the player has just moved into picture-in-picture.
        // Music keeps playing in its own service, so it is not paused here.
        DisposableEffect(context) {
            val activity = context as? Activity
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP && activity?.isInPictureInPictureMode != true) {
                    playback.pause()
                }
            }
            (context as? androidx.lifecycle.LifecycleOwner)?.lifecycle?.addObserver(observer)
            onDispose { (context as? androidx.lifecycle.LifecycleOwner)?.lifecycle?.removeObserver(observer) }
        }

        // The queue is the list the user tapped from, so Previous and Next follow what is on screen.
        val playEntry: (VideoEntry, List<VideoEntry>) -> Unit = { entry, list ->
            val items = list.map { PlaybackSource(it.uri, it.name) }
            val index = list.indexOf(entry).coerceAtLeast(0)
            if (playback.playQueue(items, index) is LaunchResult.Started) {
                playerOpen = true
            }
        }
        val playLocker: (List<PlaybackSource>, Int) -> Unit = { items, index ->
            if (playback.playQueue(items, index) is LaunchResult.Started) {
                playerOpen = true
            }
        }
        val openFile: () -> Unit = { openFileLauncher.launch(arrayOf("video/*", "audio/*")) }
        val pickFolder: () -> Unit = { folderLauncher.launch(null) }
        val scan: () -> Unit = { requestDeviceScan() }

        Box(Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = Ink,
                bottomBar = {
                    NavigationBar(containerColor = SurfaceA, tonalElevation = 0.dp) {
                        AppTab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = selectedTab == tab,
                                onClick = {
                                    selectedTab = tab
                                    if (!tab.searchable) {
                                        searching = false
                                        query = ""
                                    }
                                },
                                icon = {
                                    GlyphIcon(tab.glyph(), tint = if (selectedTab == tab) TextPrimary else Muted, size = 22.dp)
                                },
                                label = { Text(tab.label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = Accent.copy(alpha = 0.22f),
                                    selectedTextColor = TextPrimary,
                                    unselectedTextColor = Muted
                                )
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Column(Modifier.fillMaxSize().padding(innerPadding)) {
                    TopBar(
                        title = selectedTab.label,
                        searching = searching && selectedTab.searchable,
                        query = query,
                        onQuery = { query = it },
                        showsSearch = selectedTab.searchable,
                        onOpenSearch = { searching = true },
                        onCloseSearch = {
                            searching = false
                            query = ""
                        }
                    )
                    Box(Modifier.fillMaxSize()) {
                        when (selectedTab) {
                            AppTab.VIDEOS -> VideosTab(
                                videos = videos,
                                query = query,
                                scanning = scanning,
                                status = libraryStatus,
                                onPlay = playEntry,
                                onScan = scan,
                                onOpenFile = openFile,
                                onPickFolder = pickFolder
                            )
                            AppTab.MUSIC -> MusicTab(music = music, query = query)
                            AppTab.FOLDERS -> FoldersTab(videos = videos, query = query, onPlay = playEntry)
                            AppTab.LOCKER -> LockerTab(vault = vault, onPlay = playLocker)
                            AppTab.SETTINGS -> SettingsTab(playback = playback, appearance = appearance)
                        }
                    }
                }
            }

            if (playerOpen && nowPlaying != null) {
                // The player is always drawn with the dark palette, whatever the app theme is.
                DarkRegion {
                    PlayerScreen(
                        playback = playback,
                        onClose = {
                            playback.pause()
                            playerOpen = false
                        }
                    )
                }
            }
        }
    }
}

/** Header: app mark and the current section name, with a search control on library tabs. */
@Composable
private fun TopBar(
    title: String,
    searching: Boolean,
    query: String,
    onQuery: (String) -> Unit,
    showsSearch: Boolean,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit
) {
    if (searching) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            HeaderCircle(onClick = onCloseSearch) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Close search",
                    tint = TextPrimary
                )
            }
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Search $title", color = Muted) },
                singleLine = true,
                shape = RoundedCornerShape(50),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = Muted.copy(alpha = 0.4f),
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = Accent
                )
            )
            if (query.isNotEmpty()) {
                HeaderCircle(onClick = { onQuery("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear search", tint = TextPrimary)
                }
            }
        }
    } else {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(start = 18.dp, end = 12.dp, top = 12.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(Brush.linearGradient(listOf(Accent, Color(0xFF7C5CFF)))),
                contentAlignment = Alignment.Center
            ) {
                GlyphIcon(Glyph.PLAY, tint = TextPrimary, size = 16.dp)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("TU Player", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(title, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (showsSearch) {
                HeaderCircle(onClick = onOpenSearch) {
                    GlyphIcon(Glyph.SEARCH, tint = TextPrimary, size = 20.dp)
                }
            }
        }
    }
}

@Composable
private fun HeaderCircle(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(SurfaceB)
            .border(1.dp, SurfaceB, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
