package com.tuplayer.app.playback

import android.content.Context
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.os.Handler
import android.os.Looper
import android.view.SurfaceView
import android.view.View
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/** Primary engine: AndroidX Media3 / ExoPlayer. Must be created and used on the main thread. */
@UnstableApi
class Media3Engine(appContext: Context) : PlaybackEngine {

    companion object {
        private const val TICK_MS = 250L
        private const val SEEK_STEP_MS = 10_000L
        /** Track id of the "Off" entry in the subtitle list. */
        const val SUBTITLE_OFF_ID = "SUBTITLE:off"
        private const val MIN_BUFFER_MS = 15_000
        private const val MAX_BUFFER_MS = 30_000
        private const val PLAYBACK_BUFFER_MS = 1_500
        private const val REBUFFER_MS = 3_000
        private const val TARGET_BUFFER_BYTES = 24 * 1024 * 1024
    }

    private val context = appContext.applicationContext
    override val type = EngineType.MEDIA3

    // Buffer limits for low-end phones. The default buffers can exhaust memory on long HD or 4K network
    // streams, and Android then kills the whole app.
    private val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(MIN_BUFFER_MS, MAX_BUFFER_MS, PLAYBACK_BUFFER_MS, REBUFFER_MS)
        .setTargetBufferBytes(TARGET_BUFFER_BYTES)
        .build()

    private val exo: ExoPlayer = ExoPlayer.Builder(context)
        .setLoadControl(loadControl)
        .setSeekBackIncrementMs(SEEK_STEP_MS)
        .setSeekForwardIncrementMs(SEEK_STEP_MS)
        .build()

    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _failure = MutableStateFlow<PlaybackFailure?>(null)
    override val failure: StateFlow<PlaybackFailure?> = _failure.asStateFlow()

    private val _tracks = MutableStateFlow<List<TrackOption>>(emptyList())
    override val tracks: StateFlow<List<TrackOption>> = _tracks.asStateFlow()

    /** Maps a track id from [publishTracks] back to its group and index, for overrides. */
    private val trackRefs = HashMap<String, Pair<Tracks.Group, Int>>()

    private var playerView: PlayerView? = null
    private var subtitleStyle = SubtitleStyle()
    private var scale = ScaleMode.FIT
    private var boost = BoostLevel.OFF
    private var loudness: LoudnessEnhancer? = null
    private var equalizer: Equalizer? = null

    private val handler = Handler(Looper.getMainLooper())
    private var released = false

    // Position is polled because ExoPlayer does not push position updates.
    private val ticker = object : Runnable {
        override fun run() {
            if (released) return
            publishPosition()
            handler.postDelayed(this, TICK_MS)
        }
    }

    init {
        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                update { it.copy(isPlaying = isPlaying) }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                update {
                    it.copy(
                        isBuffering = playbackState == Player.STATE_BUFFERING,
                        isEnded = playbackState == Player.STATE_ENDED
                    )
                }
                publishPosition()
            }

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                update { it.copy(speed = playbackParameters.speed) }
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                update { it.copy(videoWidth = videoSize.width, videoHeight = videoSize.height) }
            }

            override fun onTracksChanged(tracks: Tracks) {
                publishTracks(tracks)
            }

            override fun onPlayerError(error: PlaybackException) {
                update { it.copy(isPlaying = false, isBuffering = false) }
                _failure.value = PlaybackFailure(
                    kind = kindOf(error),
                    message = "${error.errorCodeName}: ${error.message ?: "unknown error"}"
                )
            }
        })
        handler.post(ticker)
    }

    override fun createView(viewContext: Context): View = PlayerView(viewContext).apply {
        player = exo
        // TU Player draws its own transport controls so every engine looks and behaves the same.
        useController = false
        resizeMode = scale.toResizeMode()
        setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
        // No black shutter: the last frame stays visible while a track switch or a reset happens.
        setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
        setKeepContentOnPlayerReset(true)
        playerView = this
        applySubtitleStyle()
    }

    override fun load(source: PlaybackSource, startPositionMs: Long) {
        if (released) return
        _failure.value = null
        _tracks.value = emptyList()
        try {
            val httpFactory = DefaultHttpDataSource.Factory().setDefaultRequestProperties(source.headers)
            val mediaSource = DefaultMediaSourceFactory(DefaultDataSource.Factory(context, httpFactory))
                .createMediaSource(
                    MediaItem.Builder()
                        .setUri(source.uri)
                        .setMediaMetadata(MediaMetadata.Builder().setTitle(source.title).build())
                        .build()
                )
            exo.setMediaSource(mediaSource, startPositionMs)
            exo.prepare()
            exo.playWhenReady = true
        } catch (e: Exception) {
            // Report the error on the failure flow so the UI shows it, instead of crashing the app.
            _failure.value = PlaybackFailure(FailureKind.OTHER, "Media3 could not open this source: ${e.message}")
        }
    }

    override fun play() {
        if (released) return
        if (_state.value.isEnded) exo.seekTo(0L)
        exo.play()
    }

    override fun pause() {
        if (!released) exo.pause()
    }

    override fun seekTo(positionMs: Long) {
        if (released) return
        exo.seekTo(positionMs.coerceAtLeast(0L))
        publishPosition()
    }

    override fun setSpeed(speed: Float) {
        if (released) return
        exo.playbackParameters = PlaybackParameters(speed)
        update { it.copy(speed = speed) }
    }

    override fun selectTrack(option: TrackOption) {
        if (released) return
        val builder = exo.trackSelectionParameters.buildUpon()
        if (option.id == SUBTITLE_OFF_ID) {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        } else {
            val (group, index) = trackRefs[option.id] ?: return
            val trackType = if (option.kind == TrackKind.AUDIO) C.TRACK_TYPE_AUDIO else C.TRACK_TYPE_TEXT
            builder.setTrackTypeDisabled(trackType, false)
            builder.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, index))
        }
        exo.trackSelectionParameters = builder.build()
        // Rebuild the list so the checked row matches the new selection.
        publishTracks(exo.currentTracks)
    }

    override fun setScaleMode(mode: ScaleMode) {
        scale = mode
        playerView?.resizeMode = mode.toResizeMode()
    }

    override fun setBoost(level: BoostLevel): Boolean {
        boost = level
        if (released) return true
        if (level == BoostLevel.OFF) {
            loudness?.enabled = false
            return true
        }
        val enhancer = loudness
            ?: runCatching { LoudnessEnhancer(exo.audioSessionId) }.getOrNull()?.also { loudness = it }
            ?: return false
        return runCatching {
            // LoudnessEnhancer takes millibels: 1 dB = 100 mB.
            enhancer.setTargetGain((level.decibels * 100).toInt())
            enhancer.enabled = true
            true
        }.getOrDefault(false)
    }

    override fun setRepeatOne(enabled: Boolean) {
        if (released) return
        exo.repeatMode = if (enabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    override fun setSubtitleStyle(style: SubtitleStyle) {
        subtitleStyle = style
        applySubtitleStyle()
    }

    private fun applySubtitleStyle() {
        val view: SubtitleView = playerView?.subtitleView ?: return
        val style = subtitleStyle
        view.setStyle(
            CaptionStyleCompat(
                style.color.argb,
                if (style.background) 0xB3000000.toInt() else android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
                CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                android.graphics.Color.BLACK,
                null
            )
        )
        view.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * style.size.mediaScale)
        view.setBottomPaddingFraction(style.position.bottomFraction)
    }

    override fun setMuted(muted: Boolean) {
        if (!released) exo.volume = if (muted) 0f else 1f
    }

    override fun videoSurfaceView(): SurfaceView? = (playerView?.videoSurfaceView as? SurfaceView)

    /** Presets of the device's audio equalizer, attached to this player's audio session. */
    override fun equalizerPresets(): List<String> {
        if (released) return emptyList()
        val eq = equalizerOrNull() ?: return emptyList()
        return (0 until eq.numberOfPresets).map { eq.getPresetName(it.toShort()) }
    }

    override fun setEqualizerPreset(index: Int): Boolean {
        val eq = equalizerOrNull() ?: return false
        return runCatching {
            eq.usePreset(index.toShort())
            eq.enabled = true
            true
        }.getOrDefault(false)
    }

    private fun equalizerOrNull(): Equalizer? {
        equalizer?.let { return it }
        return runCatching { Equalizer(0, exo.audioSessionId).also { equalizer = it } }.getOrNull()
    }

    override fun release() {
        if (released) return
        released = true
        handler.removeCallbacksAndMessages(null)
        loudness?.release()
        loudness = null
        equalizer?.release()
        equalizer = null
        playerView = null
        exo.release()
    }

    private fun publishTracks(tracks: Tracks) {
        trackRefs.clear()
        val audio = mutableListOf<TrackOption>()
        val subtitles = mutableListOf<TrackOption>()
        tracks.groups.forEachIndexed { groupIndex, group ->
            val kind = when (group.type) {
                C.TRACK_TYPE_AUDIO -> TrackKind.AUDIO
                C.TRACK_TYPE_TEXT -> TrackKind.SUBTITLE
                else -> return@forEachIndexed
            }
            for (trackIndex in 0 until group.length) {
                val id = "${kind.name}:$groupIndex:$trackIndex"
                trackRefs[id] = group to trackIndex
                val option = TrackOption(
                    kind = kind,
                    id = id,
                    label = describe(group.getTrackFormat(trackIndex), trackIndex),
                    selected = group.isTrackSelected(trackIndex)
                )
                if (kind == TrackKind.AUDIO) audio += option else subtitles += option
            }
        }
        if (subtitles.isNotEmpty()) {
            subtitles.add(0, TrackOption(TrackKind.SUBTITLE, SUBTITLE_OFF_ID, "Off", subtitles.none { it.selected }))
        }
        _tracks.value = audio + subtitles
    }

    private fun describe(format: Format, index: Int): String {
        val language = format.language
            ?.let { Locale.forLanguageTag(it).displayLanguage }
            ?.takeIf { it.isNotBlank() }
        val codec = format.sampleMimeType
            ?.substringAfter('/', "")
            ?.takeIf { it.isNotBlank() }
            ?.uppercase(Locale.US)
        return listOfNotNull(format.label, language, codec).joinToString(" · ").ifBlank { "Track ${index + 1}" }
    }

    private fun ScaleMode.toResizeMode(): Int = when (this) {
        ScaleMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        ScaleMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        ScaleMode.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
    }

    private fun publishPosition() {
        val duration = exo.duration
        update {
            it.copy(
                positionMs = exo.currentPosition,
                durationMs = if (duration > 0) duration else 0L
            )
        }
    }

    private fun update(block: (PlaybackState) -> PlaybackState) {
        _state.value = block(_state.value)
    }

    private fun kindOf(error: PlaybackException): FailureKind = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> FailureKind.SOURCE_UNAVAILABLE

        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED -> FailureKind.UNSUPPORTED_FORMAT_OR_CODEC

        else -> FailureKind.OTHER
    }
}
