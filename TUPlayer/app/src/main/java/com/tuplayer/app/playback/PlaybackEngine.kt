package com.tuplayer.app.playback

import android.content.Context
import android.net.Uri
import android.view.SurfaceView
import android.view.View
import kotlinx.coroutines.flow.StateFlow

enum class EngineType(val label: String) {
    MEDIA3("Media3"),
    MPV("libmpv")
}

/** What the user chose in Settings. AUTO picks an engine per file and may fall back. */
enum class EnginePreference { AUTO, MEDIA3, MPV }

/** A thing to play. Headers are sent with HTTP(S) requests (e.g. Referer, Authorization). */
data class PlaybackSource(
    val uri: Uri,
    val title: String,
    val headers: Map<String, String> = emptyMap()
)

/** Engine-independent playback state. Both engines publish this same shape. */
data class PlaybackState(
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val isEnded: Boolean = false,
    val speed: Float = 1f,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0
)

/** Coarse classification used by the engine policy to decide whether a fallback can help. */
enum class FailureKind {
    /** The engine could not parse, decode or open the format. Another engine may succeed. */
    UNSUPPORTED_FORMAT_OR_CODEC,
    /** The network or file could not be reached. Switching engines will not help. */
    SOURCE_UNAVAILABLE,
    OTHER
}

data class PlaybackFailure(val kind: FailureKind, val message: String)

enum class TrackKind { AUDIO, SUBTITLE }

/**
 * One selectable audio or subtitle track. [id] is opaque and only meaningful to the engine that
 * produced it. The UI passes it back unchanged to [PlaybackEngine.selectTrack].
 */
data class TrackOption(
    val kind: TrackKind,
    val id: String,
    val label: String,
    val selected: Boolean
)

/** How the picture fills the frame. */
enum class ScaleMode(val label: String) {
    FIT("Fit"),
    FILL("Fill (crop)"),
    STRETCH("Stretch")
}

/**
 * Extra loudness on top of the normal level. Media3 uses Android's LoudnessEnhancer and libmpv
 * uses its volume gain. High levels can distort loud audio.
 */
enum class BoostLevel(val label: String, val decibels: Float) {
    OFF("Off", 0f),
    LOW("+3 dB", 3f),
    MEDIUM("+6 dB", 6f),
    HIGH("+10 dB", 10f)
}

/** Subtitle text size. [mediaScale] scales Media3 captions; [mpvSize] is libmpv's sub-font-size. */
enum class SubtitleSize(val label: String, val mediaScale: Float, val mpvSize: Int) {
    SMALL("S", 0.75f, 40),
    MEDIUM("M", 1f, 55),
    LARGE("L", 1.35f, 72),
    XLARGE("XL", 1.7f, 90)
}

/** Subtitle text colour, as ARGB for Media3 and a hex string for libmpv. */
enum class SubtitleColor(val label: String, val argb: Int, val mpvHex: String) {
    WHITE("White", 0xFFFFFFFF.toInt(), "#FFFFFFFF"),
    YELLOW("Yellow", 0xFFFFEB3B.toInt(), "#FFFFEB3B"),
    GREEN("Green", 0xFF8BC34A.toInt(), "#FF8BC34A"),
    CYAN("Cyan", 0xFF4DD0E1.toInt(), "#FF4DD0E1")
}

/** Subtitle height. [bottomFraction] is the Media3 bottom padding; [mpvPos] is libmpv's sub-pos (0 top, 100 bottom). */
enum class SubtitlePosition(val label: String, val bottomFraction: Float, val mpvPos: Int) {
    LOW("Low", 0.06f, 96),
    MIDDLE("Mid", 0.2f, 80),
    HIGH("High", 0.36f, 64)
}

/** Subtitle look. Applied the same way by both engines. */
data class SubtitleStyle(
    val size: SubtitleSize = SubtitleSize.MEDIUM,
    val color: SubtitleColor = SubtitleColor.WHITE,
    val background: Boolean = false,
    val position: SubtitlePosition = SubtitlePosition.LOW
)

/**
 * The contract every playback backend implements. Calls must be made on the main thread.
 * Engines publish state, tracks and failures as StateFlows; they never block the UI thread.
 */
interface PlaybackEngine {
    val type: EngineType
    val state: StateFlow<PlaybackState>

    /** Non-null once the engine has hit an error. Reset to null on the next [load]. */
    val failure: StateFlow<PlaybackFailure?>

    /** Audio and subtitle tracks of the loaded file. Empty until the file is prepared. */
    val tracks: StateFlow<List<TrackOption>>

    /** Creates the view that renders video for this engine. Called once per engine. */
    fun createView(viewContext: Context): View

    fun load(source: PlaybackSource, startPositionMs: Long = 0L)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun setSpeed(speed: Float)
    fun release()

    /** Switches to an audio track, or to subtitles Off (see the engine's OFF id). */
    fun selectTrack(option: TrackOption)

    fun setScaleMode(mode: ScaleMode)

    /** Returns false when the device cannot apply the boost. */
    fun setBoost(level: BoostLevel): Boolean

    fun setRepeatOne(enabled: Boolean)
    /** Applies the subtitle look. Takes effect on the current file and on the next one. */
    fun setSubtitleStyle(style: SubtitleStyle)

    fun setMuted(muted: Boolean)

    /** The SurfaceView that draws video frames, for screenshots. Null when the engine has none. */
    fun videoSurfaceView(): SurfaceView? = null

    /** Names of the equalizer presets this engine offers. Empty when the engine has no equalizer. */
    fun equalizerPresets(): List<String> = emptyList()

    /** Applies an equalizer preset by index. Returns false when unsupported on this device. */
    fun setEqualizerPreset(index: Int): Boolean = false
}

/** Playback speeds offered in the UI. 0.25x to 4x, as required. */
val SPEED_PRESETS: List<Float> = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 3f, 4f)
