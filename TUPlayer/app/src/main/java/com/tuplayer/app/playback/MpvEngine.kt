package com.tuplayer.app.playback

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import dev.jdtech.mpv.MPVLib
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import kotlin.math.pow

/**
 * Advanced engine: libmpv through the prebuilt binding `dev.jdtech.mpv:libmpv`.
 *
 * Only constructed when [isSupported] is true (Android 8.0+ with the native libraries loadable).
 * Video renders into a SurfaceView. mpv delivers events on its own thread, so every state change
 * is posted to the main thread first.
 */
class MpvEngine(appContext: Context) : PlaybackEngine {

    companion object {
        private const val TAG = "TUPlayer.MpvEngine"
        /** Track id of the "Off" entry in the subtitle list. */
        const val SUBTITLE_OFF_ID = "SUBTITLE:off"
        /** Highest volume mpv accepts (percent). Needed for sound boost above 100%. */
        private const val VOLUME_MAX = 400

        private val nativeLoaded: Boolean by lazy {
            runCatching {
                System.loadLibrary("mpv")
                System.loadLibrary("player")
            }.onFailure { Log.w(TAG, "libmpv native libraries could not be loaded", it) }
                .isSuccess
        }

        /** True when the device runs Android 8.0+ and the libmpv native libraries load for this ABI. */
        fun isSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nativeLoaded
    }

    private class PendingLoad(val source: PlaybackSource, val startMs: Long)

    private val context = appContext.applicationContext
    override val type = EngineType.MPV

    private val mainHandler = Handler(Looper.getMainLooper())

    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _failure = MutableStateFlow<PlaybackFailure?>(null)
    override val failure: StateFlow<PlaybackFailure?> = _failure.asStateFlow()

    private val _tracks = MutableStateFlow<List<TrackOption>>(emptyList())
    override val tracks: StateFlow<List<TrackOption>> = _tracks.asStateFlow()

    private var mpv: MPVLib? = null
    private var surface: Surface? = null
    private var videoView: SurfaceView? = null
    private var muted = false
    /** Last real size of the surface view. libmpv must be told about every change, or it draws at the old size. */
    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var pending: PendingLoad? = null
    private var openedFile: ParcelFileDescriptor? = null
    private var released = false
    private var speed = 1f

    // Settings kept here so they survive until mpv exists, then are applied in ensureMpv().
    private var scale = ScaleMode.FIT
    private var boost = BoostLevel.OFF
    private var repeatOne = false
    private var subtitleStyle = SubtitleStyle()
    private var hardwareDecoding = true

    private val observer = object : MPVLib.EventObserver {
        override fun event(eventId: Int) = onMain {
            when (eventId) {
                MPVLib.MpvEvent.MPV_EVENT_START_FILE -> {
                    _tracks.value = emptyList()
                    update { it.copy(isBuffering = true, isEnded = false) }
                }
                MPVLib.MpvEvent.MPV_EVENT_FILE_LOADED -> {
                    update { it.copy(isBuffering = false) }
                    refreshTracks()
                }
                MPVLib.MpvEvent.MPV_EVENT_PLAYBACK_RESTART -> update { it.copy(isBuffering = false) }
                else -> Unit
            }
        }

        override fun eventEndFile(reason: Int, error: Int) = onMain {
            when (reason) {
                MPVLib.MpvEndFileReason.MPV_END_FILE_REASON_EOF ->
                    update { it.copy(isEnded = true, isPlaying = false, isBuffering = false) }

                MPVLib.MpvEndFileReason.MPV_END_FILE_REASON_ERROR -> {
                    update { it.copy(isPlaying = false, isBuffering = false) }
                    _failure.value = PlaybackFailure(kindOf(error), "libmpv error code $error")
                }

                else -> Unit
            }
        }

        override fun eventProperty(property: String, value: Double) = onMain {
            when (property) {
                "time-pos" -> update { it.copy(positionMs = (value * 1000).toLong()) }
                "duration" -> update { it.copy(durationMs = (value * 1000).toLong().coerceAtLeast(0L)) }
                else -> Unit
            }
        }

        override fun eventProperty(property: String, value: Boolean) = onMain {
            when (property) {
                "pause" -> update { it.copy(isPlaying = !value) }
                "paused-for-cache" -> update { it.copy(isBuffering = value) }
                else -> Unit
            }
        }

        override fun eventProperty(property: String, value: Long) = onMain {
            when (property) {
                "width" -> update { it.copy(videoWidth = value.toInt()) }
                "height" -> update { it.copy(videoHeight = value.toInt()) }
                else -> Unit
            }
        }
    }

    override fun createView(viewContext: Context): View = SurfaceView(viewContext).apply {
        videoView = this
        holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                onSurfaceCreated(holder.surface)
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                onSurfaceSizeChanged(width, height)
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                onSurfaceDestroyed()
            }
        })
    }

    override fun load(source: PlaybackSource, startPositionMs: Long) {
        if (released) return
        _failure.value = null
        _tracks.value = emptyList()
        _state.value = PlaybackState(speed = speed, isBuffering = true)
        if (surface == null) {
            // mpv can only render once a surface exists. Hold the request until it does.
            pending = PendingLoad(source, startPositionMs)
            return
        }
        loadNow(source, startPositionMs)
    }

    override fun play() {
        val m = mpv ?: return
        if (_state.value.isEnded) m.command(arrayOf("seek", "0", "absolute"))
        m.setPropertyBoolean("pause", false)
    }

    override fun pause() {
        mpv?.setPropertyBoolean("pause", true)
    }

    override fun seekTo(positionMs: Long) {
        val m = mpv ?: return
        val seconds = String.format(Locale.US, "%.3f", positionMs.coerceAtLeast(0L) / 1000.0)
        m.command(arrayOf("seek", seconds, "absolute+exact"))
    }

    override fun setSpeed(speed: Float) {
        this.speed = speed
        mpv?.setPropertyDouble("speed", speed.toDouble())
        update { it.copy(speed = speed) }
    }

    override fun selectTrack(option: TrackOption) {
        val m = mpv ?: return
        if (option.id == SUBTITLE_OFF_ID) {
            m.setPropertyString("sid", "no")
        } else {
            val trackId = option.id.substringAfterLast(':').toIntOrNull() ?: return
            if (option.kind == TrackKind.AUDIO) {
                m.setPropertyInt("aid", trackId)
            } else {
                m.setPropertyInt("sid", trackId)
            }
        }
        refreshTracks()
    }

    override fun setScaleMode(mode: ScaleMode) {
        scale = mode
        mpv?.let { applyScale(it) }
    }

    override fun setBoost(level: BoostLevel): Boolean {
        boost = level
        mpv?.setPropertyDouble("volume", boostVolume())
        return true
    }

    override fun setRepeatOne(enabled: Boolean) {
        repeatOne = enabled
        mpv?.setPropertyString("loop-file", if (enabled) "inf" else "no")
    }

    override fun setSubtitleStyle(style: SubtitleStyle) {
        subtitleStyle = style
        mpv?.let { applySubtitle(it) }
    }

    /** Maps the shared subtitle style onto libmpv's sub-* properties. */
    private fun applySubtitle(m: MPVLib) {
        val style = subtitleStyle
        m.setPropertyInt("sub-font-size", style.size.mpvSize)
        m.setPropertyString("sub-color", style.color.mpvHex)
        m.setPropertyString("sub-back-color", if (style.background) "#B3000000" else "#00000000")
        m.setPropertyString("sub-border-style", if (style.background) "opaque-box" else "outline-and-shadow")
        m.setPropertyInt("sub-pos", style.position.mpvPos)
    }

    override fun setMuted(muted: Boolean) {
        this.muted = muted
        mpv?.setPropertyBoolean("mute", muted)
    }

    override fun videoSurfaceView(): SurfaceView? = videoView

    /** Hardware decoding through MediaCodec, or software decoding in libavcodec. Only libmpv offers software. */
    fun setHardwareDecoding(enabled: Boolean) {
        hardwareDecoding = enabled
        mpv?.setPropertyString("hwdec", if (enabled) "mediacodec-copy" else "no")
    }

    override fun release() {
        if (released) return
        released = true
        mainHandler.removeCallbacksAndMessages(null)
        pending = null
        surface = null
        mpv?.let { m ->
            runCatching {
                m.removeObserver(observer)
                m.detachSurface()
                m.destroy()
            }.onFailure { Log.w(TAG, "libmpv shutdown reported an error", it) }
        }
        mpv = null
        openedFile?.close()
        openedFile = null
    }

    private fun onSurfaceCreated(s: Surface) {
        if (released) return
        surface = s
        val m = ensureMpv() ?: return
        m.attachSurface(s)
        applySurfaceSize(m)
        pending?.let {
            pending = null
            loadNow(it.source, it.startMs)
        }
    }

    private fun onSurfaceDestroyed() {
        surface = null
        mpv?.detachSurface()
    }

    /** Called on every layout or rotation change of the surface view. */
    private fun onSurfaceSizeChanged(width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
        mpv?.let { applySurfaceSize(it) }
    }

    /**
     * Passes the real pixel size to libmpv's android-surface-size property. Without it, the video
     * is drawn for the size the surface had when it was first attached, which shows up as
     * stretched or squashed video after rotation or fullscreen changes.
     */
    private fun applySurfaceSize(m: MPVLib) {
        if (surfaceWidth > 0 && surfaceHeight > 0) {
            m.setPropertyString("android-surface-size", "${surfaceWidth}x$surfaceHeight")
        }
    }

    private fun loadNow(source: PlaybackSource, startMs: Long) {
        val m = ensureMpv() ?: return
        try {
            val target = try {
                resolveTarget(source.uri)
            } catch (e: Exception) {
                Log.w(TAG, "Could not open ${source.uri}", e)
                null
            }
            if (target == null) {
                _failure.value = PlaybackFailure(FailureKind.SOURCE_UNAVAILABLE, "This file can't be opened.")
                return
            }
            m.setPropertyString(
                "http-header-fields",
                source.headers.entries.joinToString(",") { "${it.key}: ${it.value}" }
            )
            val start = String.format(Locale.US, "%.3f", startMs / 1000.0)
            m.command(arrayOf("loadfile", target, "replace", "-1", "start=$start"))
            m.setPropertyBoolean("pause", false)
        } catch (e: Exception) {
            Log.e(TAG, "libmpv could not load ${source.uri}", e)
            _failure.value = PlaybackFailure(FailureKind.OTHER, "libmpv could not load this source: ${e.message}")
        }
    }

    /** mpv cannot read content:// URIs directly, so they are opened here and passed as fd:// */
    private fun resolveTarget(uri: Uri): String? = when (uri.scheme) {
        "content" -> {
            openedFile?.close()
            openedFile = context.contentResolver.openFileDescriptor(uri, "r")
            openedFile?.let { "fd://${it.fd}" }
        }
        "file" -> uri.path
        else -> uri.toString()
    }

    /** Creates and configures the mpv instance once. Returns null if the native library fails. */
    private fun ensureMpv(): MPVLib? {
        mpv?.let { return it }
        return try {
            val m: MPVLib = checkNotNull(MPVLib.create(context)) { "libmpv returned no instance" }
            m.setOptionString("vo", "gpu")
            m.setOptionString("gpu-context", "android")
            m.setOptionString("opengl-es", "yes")
            m.setOptionString("ao", "audiotrack,opensles")
            m.setOptionString("volume-max", VOLUME_MAX.toString())
            m.setOptionString("force-window", "yes")
            m.setOptionString("keep-open", "yes")
            m.setOptionString("idle", "yes")
            m.init()
            m.addObserver(observer)
            m.observeProperty("time-pos", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
            m.observeProperty("duration", MPVLib.MpvFormat.MPV_FORMAT_DOUBLE)
            m.observeProperty("pause", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
            m.observeProperty("paused-for-cache", MPVLib.MpvFormat.MPV_FORMAT_FLAG)
            m.observeProperty("width", MPVLib.MpvFormat.MPV_FORMAT_INT64)
            m.observeProperty("height", MPVLib.MpvFormat.MPV_FORMAT_INT64)
            m.setPropertyDouble("speed", speed.toDouble())
            m.setPropertyString("hwdec", if (hardwareDecoding) "mediacodec-copy" else "no")
            applyScale(m)
            m.setPropertyDouble("volume", boostVolume())
            m.setPropertyString("loop-file", if (repeatOne) "inf" else "no")
            m.setPropertyBoolean("mute", muted)
            applySubtitle(m)
            mpv = m
            m
        } catch (e: Throwable) {
            Log.e(TAG, "libmpv failed to start", e)
            _failure.value = PlaybackFailure(FailureKind.OTHER, "libmpv failed to start: ${e.message}")
            null
        }
    }

    /** Fit keeps the whole picture, Fill crops to the frame, Stretch ignores the aspect ratio. */
    private fun applyScale(m: MPVLib) {
        m.setPropertyString("keepaspect", if (scale == ScaleMode.STRETCH) "no" else "yes")
        m.setPropertyDouble("panscan", if (scale == ScaleMode.FILL) 1.0 else 0.0)
    }

    /** Percent volume for the boost level: 10^(dB/20) times 100. Normal level is 100. */
    private fun boostVolume(): Double = 100.0 * 10.0.pow(boost.decibels / 20.0)

    /** Rebuilds the track list from mpv's track-list properties. */
    private fun refreshTracks() {
        val m = mpv ?: return
        val count = m.getPropertyInt("track-list/count") ?: 0
        val audio = mutableListOf<TrackOption>()
        val subtitles = mutableListOf<TrackOption>()
        for (i in 0 until count) {
            val kind = when (m.getPropertyString("track-list/$i/type")) {
                "audio" -> TrackKind.AUDIO
                "sub" -> TrackKind.SUBTITLE
                else -> continue
            }
            val trackId = m.getPropertyInt("track-list/$i/id") ?: continue
            val title = m.getPropertyString("track-list/$i/title")
            val language = m.getPropertyString("track-list/$i/lang")
                ?.let { Locale.forLanguageTag(it).displayLanguage.ifBlank { it } }
            val codec = m.getPropertyString("track-list/$i/codec")?.uppercase(Locale.US)
            val selected = m.getPropertyBoolean("track-list/$i/selected") ?: false
            val label = listOfNotNull(title, language, codec)
                .joinToString(" · ")
                .ifBlank { "Track $trackId" }
            val option = TrackOption(kind, "${kind.name}:$trackId", label, selected)
            if (kind == TrackKind.AUDIO) audio += option else subtitles += option
        }
        if (subtitles.isNotEmpty()) {
            subtitles.add(0, TrackOption(TrackKind.SUBTITLE, SUBTITLE_OFF_ID, "Off", subtitles.none { it.selected }))
        }
        _tracks.value = audio + subtitles
    }

    private fun onMain(block: () -> Unit) {
        mainHandler.post { if (!released) block() }
    }

    private fun update(block: (PlaybackState) -> PlaybackState) {
        _state.value = block(_state.value)
    }

    private fun kindOf(mpvError: Int): FailureKind = when (mpvError) {
        MPVLib.MpvError.MPV_ERROR_UNKNOWN_FORMAT,
        MPVLib.MpvError.MPV_ERROR_UNSUPPORTED,
        MPVLib.MpvError.MPV_ERROR_VO_INIT_FAILED,
        MPVLib.MpvError.MPV_ERROR_AO_INIT_FAILED -> FailureKind.UNSUPPORTED_FORMAT_OR_CODEC

        MPVLib.MpvError.MPV_ERROR_LOADING_FAILED -> FailureKind.SOURCE_UNAVAILABLE

        else -> FailureKind.OTHER
    }
}
