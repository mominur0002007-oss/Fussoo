package com.tuplayer.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.tuplayer.app.MainActivity
import com.tuplayer.app.playback.AspectOption
import com.tuplayer.app.playback.EngineType
import com.tuplayer.app.playback.PlaybackController
import com.tuplayer.app.playback.PlaybackState
import com.tuplayer.app.playback.QueueState
import com.tuplayer.app.playback.RotationMode
import com.tuplayer.app.util.formatDuration
import com.tuplayer.app.util.speedLabel
import kotlinx.coroutines.delay
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import com.tuplayer.app.playback.TrackKind
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private const val CONTROLS_HIDE_MS = 5000L
/** Playback speed while the screen is held down. Released, the previous speed returns. */
private const val HOLD_SPEED = 2f
private const val FEEDBACK_HIDE_MS = 900L
private const val NOTICE_HIDE_MS = 4000L
private const val DOUBLE_TAP_SEEK_MS = 10_000L
/** A full-width horizontal swipe moves this far through the video. */
private const val SWIPE_SEEK_SPAN_MS = 120_000L
/** Movement needed before a drag is treated as seek, brightness or volume. */
private const val DRAG_SLOP_PX = 24f
private const val MIN_ZOOM = 1f

/** Glass surfaces: translucent black with a faint edge, readable over any picture. */
private val GlassFill = Color(0x66000000)
private val GlassEdge = Color(0x33FFFFFF)
private val ScrubTrack = Color(0x55FFFFFF)
private val BottomShade = Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000)))
private val TopShade = Brush.verticalGradient(listOf(Color(0xB3000000), Color.Transparent))

private enum class DragMode { NONE, SEEK, BRIGHTNESS, VOLUME }

fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/**
 * Full-screen player with a cinematic overlay.
 * - Double tap left or right to skip 10 s. Swipe horizontally to seek with a time preview.
 * - Swipe up or down on the left half for brightness, on the right half for volume.
 * - Tap to show or hide controls. Controls auto-hide while playing. Lock freezes the screen.
 * - The options bar sits under the seek bar: sleep, A-B repeat, boost, speed, screenshot, picture,
 *   rotation, audio and subtitle tracks, engine and more. Each one is visible, not behind a menu.
 * - Picture-in-picture: the top-bar button, or leaving the app while a video plays. In PiP only the
 *   video is shown.
 * - A failed stream shows a persistent error card with Retry and Try libmpv.
 */
@Composable
fun PlayerScreen(playback: PlaybackController, onClose: () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val audio = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember(audio) { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }

    val playbackState by playback.playback.collectAsState()
    val latest by rememberUpdatedState(playbackState)
    val source by playback.current.collectAsState()
    val engine by playback.engineType.collectAsState()
    val generation by playback.generation.collectAsState()
    val notice by playback.notice.collectAsState()
    val queue by playback.queueState.collectAsState()
    val rotation by playback.rotation.collectAsState()
    val aspect by playback.aspect.collectAsState()
    val zoom by playback.zoom.collectAsState()
    val error by playback.error.collectAsState()
    val hardware by playback.hardwareDecoding.collectAsState()
    val inPip by playback.inPip.collectAsState()

    var controlsVisible by remember { mutableStateOf(true) }
    // Changes on every touch, so the auto-hide timer starts over and the controls stay up while in use.
    var touchTick by remember { mutableIntStateOf(0) }
    var holdActive by remember { mutableStateOf(false) }
    var speedBeforeHold by remember { mutableFloatStateOf(1f) }
    var locked by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var dragging by remember { mutableStateOf(false) }
    var seekPreview by remember { mutableStateOf<Long?>(null) }
    var menuPage by remember { mutableStateOf<MenuPage?>(null) }
    var nightMode by rememberSaveable { mutableStateOf(false) }
    var surfaceWidth by remember { mutableIntStateOf(0) }
    var surfaceHeight by remember { mutableIntStateOf(0) }

    val lockedNow by rememberUpdatedState(locked)
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Tells the activity that the player is open, so leaving the app can switch to picture-in-picture.
    DisposableEffect(playback) {
        playback.setPlayerVisible(true)
        onDispose { playback.setPlayerVisible(false) }
    }

    // Immersive and screen-on while the player is open. Everything is restored on exit.
    DisposableEffect(activity) {
        val window = activity?.window
        val previousOrientation = activity?.requestedOrientation
        if (activity != null && window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val insets = WindowCompat.getInsetsController(window, window.decorView)
            insets.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insets.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            if (activity != null && window != null) {
                previousOrientation?.let { activity.requestedOrientation = it }
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                WindowCompat.getInsetsController(window, window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Rotation setting applied on every change, including the first time the player opens.
    LaunchedEffect(rotation, activity) {
        activity?.requestedOrientation = when (rotation) {
            RotationMode.AUTO -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
            RotationMode.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            RotationMode.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            RotationMode.LOCKED -> ActivityInfo.SCREEN_ORIENTATION_LOCKED
        }
    }

    // Controls hide after a few seconds of playback. They stay up while paused or while a sheet is open.
    LaunchedEffect(controlsVisible, touchTick, playbackState.isPlaying, menuPage, locked) {
        if (controlsVisible && playbackState.isPlaying && menuPage == null) {
            delay(CONTROLS_HIDE_MS)
            controlsVisible = false
        }
    }

    // Gesture feedback fades out shortly after the last change.
    LaunchedEffect(feedback, dragging) {
        if (feedback != null && !dragging) {
            delay(FEEDBACK_HIDE_MS)
            feedback = null
        }
    }

    // Engine notices (a resume, a switch, a screenshot) are shown briefly, then cleared.
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(NOTICE_HIDE_MS)
            playback.dismissNotice()
        }
    }

    // Back closes a sheet first, then the player.
    BackHandler(enabled = menuPage != null) { menuPage = null }
    BackHandler(enabled = menuPage == null, onBack = onClose)

    val shownMs = seekPreview ?: playbackState.positionMs

    Box(
        Modifier
            .fillMaxSize()
            // Opaque dark backdrop. It must cover the library behind the player; a transparent
            // backdrop let the library header, tabs and rows show around the video.
            .background(Ink)
            .onSizeChanged {
                surfaceWidth = it.width
                surfaceHeight = it.height
            }
            .pointerInput(Unit) {
                // Sees every touch without consuming it, so any touch wakes the controls.
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Press) touchTick++
                    }
                }
            }
    ) {
        // Picture frame: fits the chosen aspect ratio, and the zoom scales the video inside it.
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val ratio = aspect.ratio
            val frame = if (ratio != null) {
                Modifier.aspectRatio(ratio, matchHeightConstraintsFirst = landscape)
            } else {
                Modifier.fillMaxSize()
            }
            Box(frame.clipToBounds()) {
                if (source != null) {
                    key(generation) {
                        AndroidView(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = zoom
                                    scaleY = zoom
                                },
                            factory = { ctx -> playback.createSurface(ctx) }
                        )
                    }
                }
            }
        }

        // Night mode dims the picture only, not the controls.
        if (nightMode) Box(Modifier.fillMaxSize().background(Color(0x80000000)))

        // In picture-in-picture only the video is shown. Overlays come back when the window is full size.
        if (!inPip) {
            // Gesture layer. It sits above the video and below the controls, so control taps never reach it.
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { controlsVisible = !controlsVisible },
                            onPress = {
                                tryAwaitRelease()
                                if (holdActive) {
                                    holdActive = false
                                    playback.setSpeed(speedBeforeHold)
                                }
                            },
                            onLongPress = {
                                if (!lockedNow && latest.isPlaying) {
                                    speedBeforeHold = latest.speed
                                    holdActive = true
                                    playback.setSpeed(HOLD_SPEED)
                                }
                            },
                            onDoubleTap = { offset ->
                                if (!lockedNow) {
                                    val half = surfaceWidth.coerceAtLeast(1) / 2f
                                    val delta = if (offset.x < half) -DOUBLE_TAP_SEEK_MS else DOUBLE_TAP_SEEK_MS
                                    playback.seekBy(delta)
                                    feedback = if (delta < 0) "−10s" else "+10s"
                                }
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        var mode = DragMode.NONE
                        var startX = 0f
                        var accX = 0f
                        var accY = 0f
                        var startBrightness = 0.5f
                        var startVolume = 0
                        var seekBase = 0L

                        fun finish() {
                            if (mode == DragMode.SEEK) seekPreview?.let { playback.seekTo(it) }
                            seekPreview = null
                            mode = DragMode.NONE
                            dragging = false
                            feedback = null
                        }

                        detectDragGestures(
                            onDragStart = { offset ->
                                mode = DragMode.NONE
                                startX = offset.x
                                accX = 0f
                                accY = 0f
                                seekBase = latest.positionMs
                            },
                            onDragEnd = { finish() },
                            onDragCancel = { finish() },
                            onDrag = { change, drag ->
                                change.consume()
                                accX += drag.x
                                accY += drag.y
                                if (mode == DragMode.NONE && !lockedNow &&
                                    (abs(accX) > DRAG_SLOP_PX || abs(accY) > DRAG_SLOP_PX)
                                ) {
                                    val w = surfaceWidth.coerceAtLeast(1)
                                    mode = when {
                                        abs(accX) > abs(accY) -> DragMode.SEEK
                                        startX < w / 2f -> DragMode.BRIGHTNESS
                                        else -> DragMode.VOLUME
                                    }
                                    dragging = mode != DragMode.NONE
                                    if (mode == DragMode.BRIGHTNESS) startBrightness = currentBrightness(activity)
                                    if (mode == DragMode.VOLUME) {
                                        startVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                                    }
                                }
                                val h = surfaceHeight.coerceAtLeast(1)
                                when (mode) {
                                    DragMode.SEEK -> {
                                        val duration = latest.durationMs
                                        val delta = (accX / surfaceWidth.coerceAtLeast(1) * SWIPE_SEEK_SPAN_MS).toLong()
                                        val max = if (duration > 0) duration else Long.MAX_VALUE
                                        val target = (seekBase + delta).coerceIn(0L, max)
                                        seekPreview = target
                                        feedback = formatDuration(target)
                                    }
                                    DragMode.BRIGHTNESS -> {
                                        val value = (startBrightness - accY / h).coerceIn(0.01f, 1f)
                                        setBrightness(activity, value)
                                        feedback = "Brightness ${(value * 100).roundToInt()}%"
                                    }
                                    DragMode.VOLUME -> {
                                        val target = (startVolume + (-accY / h) * maxVolume)
                                            .roundToInt()
                                            .coerceIn(0, maxVolume)
                                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
                                        feedback = "Volume $target/$maxVolume"
                                    }
                                    DragMode.NONE -> Unit
                                }
                            }
                        )
                    }
            )

            if (playbackState.isBuffering) {
                CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White, strokeWidth = 3.dp)
            }
            if (holdActive) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 120.dp)
                        .background(GlassFill, RoundedCornerShape(50))
                        .border(1.dp, GlassEdge, RoundedCornerShape(50))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text("2× speed", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
            feedback?.let { text ->
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .background(GlassFill, RoundedCornerShape(16.dp))
                        .border(1.dp, GlassEdge, RoundedCornerShape(16.dp))
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Text(text, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            // Playback errors stay on screen until the user acts, so the message can be read and reported.
            error?.let { message ->
                Box(Modifier.align(Alignment.Center)) {
                    ErrorCard(
                        message = message,
                        canUseMpv = engine == EngineType.MEDIA3 && playback.isMpvSupported(),
                        onRetry = { playback.retry() },
                        onUseMpv = { playback.switchEngine(EngineType.MPV) },
                        onClose = { playback.dismissError() }
                    )
                }
            }

            // Engine notices appear just under the top bar. They are not interactive.
            notice?.let { text ->
                if (!locked) {
                    Box(
                        Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 150.dp, start = 24.dp, end = 24.dp)
                            .background(GlassFill, RoundedCornerShape(14.dp))
                            .border(1.dp, GlassEdge, RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(text, color = Color.White, fontSize = 12.sp, textAlign = TextAlign.Center)
                    }
                }
            }

            if (locked) {
                AnimatedVisibility(
                    visible = controlsVisible,
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 20.dp),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    GlassCircle(size = 52.dp, onClick = { locked = false; controlsVisible = true }) {
                        GlyphIcon(Glyph.LOCK, tint = Color.White, size = 22.dp)
                    }
                }
            } else {
                AnimatedVisibility(
                    visible = controlsVisible,
                    modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().displayCutoutPadding(),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Column(Modifier.fillMaxWidth().background(TopShade).padding(bottom = 6.dp)) {
                    TopBar(
                        title = source?.title ?: "",
                        subtitle = listOfNotNull(
                            engine?.label,
                            if (zoom > MIN_ZOOM + 0.01f) "Zoom ${zoomLabel(zoom)}" else null
                        ).joinToString("  ·  "),
                        hwLabel = if (engine == EngineType.MPV) (if (hardware) "HW" else "SW") else null,
                        onBack = onClose,
                        onHardware = { playback.setHardwareDecoding(!hardware) },
                        onPip = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                (activity as? MainActivity)?.enterPictureMode()
                            }
                        }
                    )
                    OptionsBar(
                        playback = playback,
                        speed = playbackState.speed,
                        engine = engine,
                        aspect = aspect,
                        nightMode = nightMode,
                        onNightMode = { nightMode = it },
                        onLock = { locked = true; controlsVisible = false },
                        onPage = { menuPage = it },
                        onScreenshot = { playback.takeScreenshot() }
                    )
                    }
                }
                AnimatedVisibility(
                    visible = controlsVisible && error == null,
                    modifier = Modifier.align(Alignment.Center),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    CenterTransport(
                        isPlaying = playbackState.isPlaying,
                        queue = queue,
                        onTogglePlay = { playback.togglePlay() },
                        onPrevious = { playback.previous() },
                        onNext = { playback.next() },
                        onSkip = { playback.seekBy(it) }
                    )
                }
                AnimatedVisibility(
                    visible = controlsVisible,
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().displayCutoutPadding(),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    BottomPanel(
                        state = playbackState,
                        shownMs = shownMs,
                        onScrub = { fraction ->
                            val duration = playbackState.durationMs
                            if (duration > 0) seekPreview = (fraction * duration).toLong()
                        },
                        onScrubCommit = {
                            seekPreview?.let { playback.seekTo(it) }
                            seekPreview = null
                        }
                    )
                }
            }
        }
    }

    menuPage?.let { page ->
        PlayerMenu(
            playback = playback,
            page = page,
            onClose = { menuPage = null }
        )
    }
}

private fun currentBrightness(activity: Activity?): Float {
    val value = activity?.window?.attributes?.screenBrightness ?: -1f
    // -1 means "use the system setting". Start the gesture from the middle in that case.
    return if (value < 0f) 0.5f else value
}

private fun setBrightness(activity: Activity?, value: Float) {
    val window = activity?.window ?: return
    val params = window.attributes
    params.screenBrightness = value
    window.attributes = params
}

private fun zoomLabel(zoom: Float): String = "${String.format(Locale.US, "%.1f", zoom)}×"

/** Round translucent button used for every icon control on the player. */
@Composable
private fun GlassCircle(size: Dp = 44.dp, onClick: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(GlassFill)
            .border(1.dp, GlassEdge, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content
    )
}

@Composable
private fun TopBar(
    title: String,
    subtitle: String,
    hwLabel: String?,
    onBack: () -> Unit,
    onHardware: () -> Unit,
    onPip: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        GlassCircle(onClick = onBack) { GlyphIcon(Glyph.BACK, tint = Color.White, size = 22.dp) }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
                Text(subtitle, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (hwLabel != null) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(GlassFill)
                    .border(1.dp, GlassEdge, RoundedCornerShape(50))
                    .clickable(onClick = onHardware)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(hwLabel, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        // Picture-in-picture needs Android 8.0 or newer. The button is hidden below that.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            GlassCircle(onClick = onPip) { GlyphIcon(Glyph.PIP, tint = Color.White, size = 20.dp) }
        }
    }
}

/** Shown when playback fails. It stays until the user acts, so the error can be read and reported. */
@Composable
private fun ErrorCard(message: String, canUseMpv: Boolean, onRetry: () -> Unit, onUseMpv: () -> Unit, onClose: () -> Unit) {
    Column(
        Modifier
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(GlassFill)
            .border(1.dp, GlassEdge, RoundedCornerShape(20.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("This video could not play", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Text(message, color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionPill("Retry", filled = true, onClick = onRetry)
            if (canUseMpv) ActionPill("Try libmpv", filled = false, onClick = onUseMpv)
            ActionPill("Close", filled = false, onClick = onClose)
        }
    }
}

@Composable
private fun ActionPill(label: String, filled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (filled) Accent else Color(0x33FFFFFF))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Centre transport: previous, back 10 s, play or pause, forward 10 s, next. */
@Composable
private fun CenterTransport(
    isPlaying: Boolean,
    queue: QueueState,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSkip: (Long) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
        SideControl(enabled = queue.hasPrevious, onClick = onPrevious) {
            GlyphIcon(Glyph.PREVIOUS, tint = Color.White, size = 22.dp)
        }
        SkipControl(glyph = Glyph.REPLAY, onClick = { onSkip(-DOUBLE_TAP_SEEK_MS) })
        Box(
            Modifier
                .size(78.dp)
                .clip(CircleShape)
                .background(Accent)
                .clickable(onClick = onTogglePlay),
            contentAlignment = Alignment.Center
        ) {
            GlyphIcon(if (isPlaying) Glyph.PAUSE else Glyph.PLAY, tint = Color.White, size = 34.dp)
        }
        SkipControl(glyph = Glyph.FORWARD, onClick = { onSkip(DOUBLE_TAP_SEEK_MS) })
        SideControl(enabled = queue.hasNext, onClick = onNext) {
            GlyphIcon(Glyph.NEXT, tint = Color.White, size = 22.dp)
        }
    }
}

/** Previous and Next. Dimmed and inert when the queue has nothing in that direction. */
@Composable
private fun SideControl(enabled: Boolean, onClick: () -> Unit, content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(if (enabled) GlassFill else Color(0x33000000))
            .border(1.dp, GlassEdge.copy(alpha = if (enabled) 0.2f else 0.05f), CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
        content = content
    )
}

/** Circular skip button: the arrow glyph shows the direction and the 10 s label sits inside it. */
@Composable
private fun SkipControl(glyph: Glyph, onClick: () -> Unit) {
    Box(
        Modifier
            .size(60.dp)
            .clip(CircleShape)
            .background(GlassFill)
            .border(1.dp, GlassEdge, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        GlyphIcon(glyph, tint = Color.White, size = 36.dp)
        Text("10", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BottomPanel(
    state: PlaybackState,
    shownMs: Long,
    onScrub: (Float) -> Unit,
    onScrubCommit: () -> Unit
) {
    val duration = state.durationMs
    val fraction = if (duration > 0) (shownMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val remaining = (duration - shownMs).coerceAtLeast(0L)

    Column(
        Modifier
            .fillMaxWidth()
            .background(BottomShade)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration(shownMs), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(
                if (duration > 0) "-${formatDuration(remaining)}" else "LIVE",
                color = Muted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
        ScrubBar(
            fraction = fraction,
            enabled = duration > 0,
            onScrub = onScrub,
            onCommit = onScrubCommit
        )
    }
}

/**
 * Options along the top of the player, most used first. Toggles act at once and show their state;
 * the others open a sheet. Equalizer is greyed out on libmpv.
 */
@Composable
private fun OptionsBar(
    playback: PlaybackController,
    speed: Float,
    engine: EngineType?,
    aspect: AspectOption,
    nightMode: Boolean,
    onNightMode: (Boolean) -> Unit,
    onLock: () -> Unit,
    onPage: (MenuPage) -> Unit,
    onScreenshot: () -> Unit
) {
    val repeatOne by playback.repeatOne.collectAsState()
    val muted by playback.muted.collectAsState()
    val boost by playback.boost.collectAsState()
    val sleepLeft by playback.sleepMinutesLeft.collectAsState()
    val abA by playback.abA.collectAsState()
    val contentMode by playback.contentMode.collectAsState()
    val tracks by playback.tracks.collectAsState()
    val subtitleOn = tracks.any { it.kind == TrackKind.SUBTITLE && it.selected && it.label != "Off" }

    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        OptionTile(Glyph.CAPTIONS, "Subtitles", active = subtitleOn) { onPage(MenuPage.SUBTITLE) }
        OptionTile(Glyph.MUSIC, "Audio") { onPage(MenuPage.AUDIO) }
        OptionTile(Glyph.SPEED, speedLabel(speed), active = abs(speed - 1f) > 0.001f) { onPage(MenuPage.SPEED) }
        OptionTile(Glyph.ASPECT, aspect.label) { onPage(MenuPage.PICTURE) }
        OptionTile(Glyph.ROTATE, "Rotate") { onPage(MenuPage.ROTATION) }
        OptionTile(Glyph.WAVE, "Boost", active = boost != com.tuplayer.app.playback.BoostLevel.OFF) { onPage(MenuPage.BOOST) }
        OptionTile(Glyph.TIMER, "Sleep", active = sleepLeft != null) { onPage(MenuPage.SLEEP) }
        OptionTile(Glyph.AB, "A-B", active = abA != null) { playback.cycleAbRepeat() }
        OptionTile(Glyph.REPEAT, "Loop", active = repeatOne) { playback.setRepeatOne(!repeatOne) }
        OptionTile(Glyph.CAMERA, "Shot", onClick = onScreenshot)
        OptionTile(Glyph.LOCK, "Lock", onClick = onLock)
        OptionTile(Glyph.SHUFFLE, "Shuffle") { playback.shuffleUpcoming() }
        OptionTile(Glyph.MUTE, if (muted) "Unmute" else "Mute", active = muted) { playback.toggleMute() }
        OptionTile(Glyph.NIGHT, "Night", active = nightMode) { onNightMode(!nightMode) }
        OptionTile(Glyph.FILM, contentMode.label, active = contentMode != com.tuplayer.app.playback.ContentMode.AUTO) {
            onPage(MenuPage.CONTENT)
        }
        OptionTile(Glyph.SLIDERS, "EQ", enabled = engine == EngineType.MEDIA3) { onPage(MenuPage.EQUALIZER) }
        OptionTile(Glyph.GLOBE, engine?.label ?: "Engine") { onPage(MenuPage.ENGINE) }
        OptionTile(Glyph.INFO, "Info") { onPage(MenuPage.INFO) }
    }
}

@Composable
private fun OptionTile(
    glyph: Glyph,
    label: String,
    active: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Column(
        Modifier
            .width(68.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(if (active) Accent else GlassFill)
                .border(1.dp, if (active) Accent else GlassEdge, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            GlyphIcon(glyph, tint = if (enabled) Color.White else Muted, size = 22.dp)
        }
        Text(
            label,
            color = if (enabled) Color.White else Muted,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Thin seek bar drawn on a canvas. Tap or drag to seek.
 * [onScrub] receives a fraction from 0 to 1, and [onCommit] runs when the user lifts the finger.
 */
@Composable
private fun ScrubBar(fraction: Float, enabled: Boolean, onScrub: (Float) -> Unit, onCommit: () -> Unit) {
    val latestScrub by rememberUpdatedState(onScrub)
    val latestCommit by rememberUpdatedState(onCommit)
    val accentColor = Accent
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onTap = { offset ->
                        latestScrub((offset.x / size.width).coerceIn(0f, 1f))
                        latestCommit()
                    }
                )
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { offset -> latestScrub((offset.x / size.width).coerceIn(0f, 1f)) },
                    onDragEnd = { latestCommit() },
                    onDragCancel = { latestCommit() },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        latestScrub((change.position.x / size.width).coerceIn(0f, 1f))
                    }
                )
            }
    ) {
        val centerY = size.height / 2f
        val trackHeight = 4.dp.toPx()
        val x = size.width * fraction
        drawRoundRect(
            color = ScrubTrack,
            topLeft = Offset(0f, centerY - trackHeight / 2f),
            size = Size(size.width, trackHeight),
            cornerRadius = CornerRadius(trackHeight / 2f)
        )
        drawRoundRect(
            color = accentColor,
            topLeft = Offset(0f, centerY - trackHeight / 2f),
            size = Size(x.coerceAtLeast(0f), trackHeight),
            cornerRadius = CornerRadius(trackHeight / 2f)
        )
        drawCircle(color = Color.White, radius = 6.dp.toPx(), center = Offset(x, centerY))
    }
}
