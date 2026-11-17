package com.tuplayer.app.playback

import android.app.Application
import android.content.Context
import android.net.Uri
import android.view.View
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.tuplayer.app.library.persistReadPermission
import com.tuplayer.app.library.queryDisplayName
import com.tuplayer.app.settings.AppPrefs
import com.tuplayer.app.util.formatDuration
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Picture frame ratio. ORIGINAL uses the whole screen. */
enum class AspectOption(val label: String, val ratio: Float?) {
    ORIGINAL("Original", null),
    RATIO_16_9("16:9", 16f / 9f),
    RATIO_4_3("4:3", 4f / 3f),
    RATIO_21_9("21:9", 21f / 9f)
}

/** Screen orientation while the player is open. LOCKED keeps the orientation the screen is in now. */
enum class RotationMode(val label: String) {
    AUTO("Auto"),
    LANDSCAPE("Landscape"),
    PORTRAIT("Portrait"),
    LOCKED("Locked")
}

/**
 * Content presets. Each one sets existing controls (fit and sound boost) when a file starts.
 * They do not analyze the picture or the sound. AUTO picks MOVIE for files of 40 minutes or
 * longer and WEB_SERIES for shorter ones, once the duration is known.
 */
enum class ContentMode(val label: String) {
    AUTO("Auto"),
    MOVIE("Movie"),
    WEB_SERIES("Web series"),
    SONG("Song")
}

/** Whether the Prev and Next buttons have something to go to. */
data class QueueState(val hasPrevious: Boolean = false, val hasNext: Boolean = false)

sealed interface LaunchResult {
    data object Started : LaunchResult

    /** The URL is plain HTTP and its host has not been approved yet. Ask the user first. */
    data class NeedsCleartextApproval(val host: String, val source: PlaybackSource) : LaunchResult
}

/**
 * Owns the active playback engine for the whole app. It applies the engine policy, runs automatic
 * fallback, keeps a play queue, remembers per-file engine choices, and holds the picture, sound
 * and rotation settings so they carry over when the engine changes.
 */
class PlaybackController(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val RESTART_THRESHOLD_MS = 3_000L
        private const val MIN_ZOOM = 1f
        private const val MAX_ZOOM = 3f
        private const val MINUTE_MS = 60_000L
        private const val SAVE_EVERY_MS = 5_000L
    }

    private val prefs = AppPrefs(application)

    private val _enginePreference = MutableStateFlow(prefs.enginePreference)
    val enginePreference: StateFlow<EnginePreference> = _enginePreference.asStateFlow()

    private val _current = MutableStateFlow<PlaybackSource?>(null)
    val current: StateFlow<PlaybackSource?> = _current.asStateFlow()

    private val _engineType = MutableStateFlow<EngineType?>(null)
    val engineType: StateFlow<EngineType?> = _engineType.asStateFlow()

    /** Increments every time a new engine instance is created, so views can be rebuilt. */
    private val _generation = MutableStateFlow(0)
    val generation: StateFlow<Int> = _generation.asStateFlow()

    private val _playback = MutableStateFlow(PlaybackState())
    val playback: StateFlow<PlaybackState> = _playback.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    private val _tracks = MutableStateFlow<List<TrackOption>>(emptyList())
    val tracks: StateFlow<List<TrackOption>> = _tracks.asStateFlow()

    private val _scaleMode = MutableStateFlow(ScaleMode.FIT)
    val scaleMode: StateFlow<ScaleMode> = _scaleMode.asStateFlow()

    private val _boost = MutableStateFlow(BoostLevel.OFF)
    val boost: StateFlow<BoostLevel> = _boost.asStateFlow()

    private val _repeatOne = MutableStateFlow(false)
    val repeatOne: StateFlow<Boolean> = _repeatOne.asStateFlow()

    private val _hardwareDecoding = MutableStateFlow(true)
    val hardwareDecoding: StateFlow<Boolean> = _hardwareDecoding.asStateFlow()

    private val _aspect = MutableStateFlow(AspectOption.ORIGINAL)
    val aspect: StateFlow<AspectOption> = _aspect.asStateFlow()

    private val _zoom = MutableStateFlow(MIN_ZOOM)
    val zoom: StateFlow<Float> = _zoom.asStateFlow()

    private val _rotation = MutableStateFlow(RotationMode.AUTO)
    val rotation: StateFlow<RotationMode> = _rotation.asStateFlow()

    /** Minutes until playback pauses, or null when no sleep timer is set. */
    private val _sleepMinutesLeft = MutableStateFlow<Int?>(null)
    val sleepMinutesLeft: StateFlow<Int?> = _sleepMinutesLeft.asStateFlow()

    private val _contentMode = MutableStateFlow(ContentMode.AUTO)
    val contentMode: StateFlow<ContentMode> = _contentMode.asStateFlow()

    private val _subtitleStyle = MutableStateFlow(prefs.subtitleStyle)
    /** Subtitle size, colour, background and position. Saved, and applied to every engine. */
    val subtitleStyle: StateFlow<SubtitleStyle> = _subtitleStyle.asStateFlow()

    private val _muted = MutableStateFlow(false)
    val muted: StateFlow<Boolean> = _muted.asStateFlow()

    /** A-B repeat: loop start and end in milliseconds. Both null when off. */
    private val _abA = MutableStateFlow<Long?>(null)
    val abA: StateFlow<Long?> = _abA.asStateFlow()
    private val _abB = MutableStateFlow<Long?>(null)
    val abB: StateFlow<Long?> = _abB.asStateFlow()

    /** Persistent playback error with the engine's message. The player shows it until the user acts. */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _eqPresets = MutableStateFlow<List<String>>(emptyList())
    val equalizerPresets: StateFlow<List<String>> = _eqPresets.asStateFlow()
    private val _eqIndex = MutableStateFlow(-1)
    val equalizerIndex: StateFlow<Int> = _eqIndex.asStateFlow()

    private val _queueState = MutableStateFlow(QueueState())
    val queueState: StateFlow<QueueState> = _queueState.asStateFlow()

    private var queue: List<PlaybackSource> = emptyList()
    private var queueIndex = 0
    private var engine: PlaybackEngine? = null
    private var engineJob: Job? = null
    private var sleepJob: Job? = null

    /** AUTO waits for the duration before it picks a preset. True until then. */
    private var presetPending = false

    /** Position of the last saved resume point during playback. Saves run every SAVE_EVERY_MS. */
    private var lastSavedPositionMs = 0L

    private val _inPip = MutableStateFlow(false)
    /** True while the activity shows the picture-in-picture window. The UI then shows only the video. */
    val inPip: StateFlow<Boolean> = _inPip.asStateFlow()

    private val _playerVisible = MutableStateFlow(false)
    /** True while the full-screen player is open. Picture-in-picture only starts from the player. */
    val playerVisible: StateFlow<Boolean> = _playerVisible.asStateFlow()

    fun setInPip(value: Boolean) {
        _inPip.value = value
    }

    fun setPlayerVisible(value: Boolean) {
        _playerVisible.value = value
    }

    /** Whether leaving the app should switch to picture-in-picture: the player is open and video is playing. */
    fun canEnterPicture(): Boolean =
        _playerVisible.value && _current.value != null && _playback.value.isPlaying

    fun isMpvSupported(): Boolean = MpvEngine.isSupported()

    fun setEnginePreference(preference: EnginePreference) {
        prefs.enginePreference = preference
        _enginePreference.value = preference
    }

    /** Plays a file the user picked with the system file picker. */
    fun playLocal(uri: Uri) {
        val app = getApplication<Application>()
        persistReadPermission(app, uri)
        playQueue(listOf(PlaybackSource(uri, queryDisplayName(app, uri) ?: "Selected video")), 0)
    }

    /** Plays one source with no queue around it. */
    fun play(source: PlaybackSource): LaunchResult = playQueue(listOf(source), 0)

    /** Plays [items] starting at [index]. Previous and Next move through this list. */
    fun playQueue(items: List<PlaybackSource>, index: Int): LaunchResult {
        require(items.isNotEmpty()) { "playQueue needs at least one item" }
        queue = items
        queueIndex = index.coerceIn(0, items.lastIndex)
        return startCurrent()
    }

    /** The user approved plain HTTP for this host in the warning dialog. */
    fun approveHostAndPlay(host: String, source: PlaybackSource): LaunchResult {
        prefs.approveCleartextHost(host)
        return play(source)
    }

    fun next() {
        if (queueIndex < queue.lastIndex) {
            queueIndex++
            startCurrent()
        }
    }

    /** Like most players: a press after a few seconds restarts the file, a second press goes back. */
    fun previous() {
        if (_playback.value.positionMs > RESTART_THRESHOLD_MS || queueIndex == 0) {
            seekTo(0L)
        } else {
            queueIndex--
            startCurrent()
        }
    }

    fun togglePlay() {
        val e = engine ?: return
        if (_playback.value.isPlaying) e.pause() else e.play()
    }

    fun pause() {
        engine?.pause()
        rememberPosition()
    }

    /**
     * Stores where the loaded file stopped, so opening it again resumes there. Positions near the start
     * or the end are forgotten, so the next open starts from the top. Does nothing before a file has loaded.
     */
    fun rememberPosition() {
        val source = _current.value ?: return
        val s = _playback.value
        if (s.durationMs <= 0L && s.positionMs <= 0L) return
        val key = source.uri.toString()
        if (s.isEnded || !ResumeRules.shouldRemember(s.positionMs, s.durationMs)) {
            prefs.clearSavedPosition(key)
        } else {
            prefs.savePosition(key, s.positionMs, s.durationMs)
        }
    }

    fun seekTo(positionMs: Long) {
        engine?.seekTo(positionMs.coerceAtLeast(0L))
    }

    fun seekBy(deltaMs: Long) {
        val s = _playback.value
        val target = s.positionMs + deltaMs
        val max = if (s.durationMs > 0) s.durationMs else Long.MAX_VALUE
        engine?.seekTo(target.coerceIn(0L, max))
    }

    fun setSpeed(speed: Float) {
        _playback.value = _playback.value.copy(speed = speed)
        engine?.setSpeed(speed)
    }

    /** Restarts the current file on another engine at the same position (manual switch from the player). */
    fun switchEngine(type: EngineType) {
        val source = _current.value ?: return
        if (type == _engineType.value) return
        if (type == EngineType.MPV && !isMpvSupported()) {
            _notice.value = "Needs Android 8.0+."
            return
        }
        prefs.setLearnedEngine(source.uri.toString(), type)
        startEngine(type, source, _playback.value.positionMs, allowFallback = false)
        _notice.value = "Switched to ${type.label}."
    }

    fun selectTrack(option: TrackOption) {
        engine?.selectTrack(option)
    }

    fun setScaleMode(mode: ScaleMode) {
        _scaleMode.value = mode
        engine?.setScaleMode(mode)
    }

    fun setSubtitleStyle(style: SubtitleStyle) {
        _subtitleStyle.value = style
        prefs.subtitleStyle = style
        engine?.setSubtitleStyle(style)
    }

    fun setBoost(level: BoostLevel) {
        _boost.value = level
        val applied = engine?.setBoost(level) ?: true
        if (!applied && level != BoostLevel.OFF) {
            _notice.value = "Boost not supported here."
        }
    }

    fun setRepeatOne(enabled: Boolean) {
        _repeatOne.value = enabled
        engine?.setRepeatOne(enabled)
    }

    /** Software decoding only exists in libmpv. Media3 always decodes in hardware. */
    fun setHardwareDecoding(enabled: Boolean) {
        _hardwareDecoding.value = enabled
        val current = engine ?: return
        if (current is MpvEngine) {
            current.setHardwareDecoding(enabled)
        } else {
            _notice.value = "Software decoding needs libmpv."
        }
    }

    fun setAspect(option: AspectOption) {
        _aspect.value = option
    }

    fun setZoom(value: Float) {
        _zoom.value = value.coerceIn(MIN_ZOOM, MAX_ZOOM)
    }

    fun setRotation(mode: RotationMode) {
        _rotation.value = mode
    }

    /** Pauses playback after [minutes]. Pass null to cancel the timer. */
    fun setSleepTimer(minutes: Int?) {
        sleepJob?.cancel()
        sleepJob = null
        if (minutes == null || minutes <= 0) {
            _sleepMinutesLeft.value = null
            return
        }
        sleepJob = viewModelScope.launch {
            var left = minutes
            _sleepMinutesLeft.value = left
            while (left > 0) {
                delay(MINUTE_MS)
                left -= 1
                _sleepMinutesLeft.value = left
            }
            pause()
            _sleepMinutesLeft.value = null
        }
    }

    /** Sets the content preset. It applies now and again to each file that starts. */
    fun setContentMode(mode: ContentMode) {
        _contentMode.value = mode
        if (mode == ContentMode.AUTO) {
            val duration = _playback.value.durationMs
            presetPending = duration <= 0L
            if (!presetPending) applyPreset(ContentMode.AUTO, duration)
        } else {
            presetPending = false
            applyPreset(mode, 0L)
        }
    }

    private fun applyPreset(mode: ContentMode, durationMs: Long) {
        val resolved = when (mode) {
            ContentMode.AUTO -> if (durationMs >= 40 * MINUTE_MS) ContentMode.MOVIE else ContentMode.WEB_SERIES
            else -> mode
        }
        setScaleMode(ScaleMode.FIT)
        setBoost(
            when (resolved) {
                ContentMode.SONG -> BoostLevel.MEDIUM
                ContentMode.WEB_SERIES -> BoostLevel.LOW
                else -> BoostLevel.OFF
            }
        )
    }

    fun toggleMute() {
        val muted = !_muted.value
        _muted.value = muted
        engine?.setMuted(muted)
    }

    /** Shuffles the files after the current one. The current file keeps playing. */
    fun shuffleUpcoming() {
        if (queueIndex >= queue.lastIndex) {
            _notice.value = "Nothing to shuffle."
            return
        }
        queue = queue.take(queueIndex + 1) + queue.drop(queueIndex + 1).shuffled()
        updateQueueState()
        _notice.value = "Upcoming files shuffled."
    }

    /** First press sets A, second sets B, third clears. Playback then loops from A to B. */
    fun cycleAbRepeat() {
        val position = _playback.value.positionMs
        val a = _abA.value
        when {
            a == null -> _abA.value = position
            _abB.value == null -> if (position > a + 1_000L) {
                _abB.value = position
            } else {
                _notice.value = "B must be later than A."
            }
            else -> {
                _abA.value = null
                _abB.value = null
            }
        }
    }

    fun setEqualizerPreset(index: Int) {
        if (engine?.setEqualizerPreset(index) == true) {
            _eqIndex.value = index
        } else {
            _notice.value = "Equalizer unavailable."
        }
    }

    fun takeScreenshot() {
        Screenshots.capture(getApplication(), engine?.videoSurfaceView()) { result ->
            _notice.value = when (result) {
                is Screenshots.Result.Saved -> "Saved ${result.name} to Pictures/TU Player"
                Screenshots.Result.NoFrame -> "No frame yet."
                Screenshots.Result.UnsupportedOs -> "Needs Android 10+."
                is Screenshots.Result.Failed -> "Screenshot failed: ${result.reason}"
            }
        }
    }

    /** Starts the current file again on the same engine, from where it stopped. */
    fun retry() {
        val source = _current.value ?: return
        val type = _engineType.value ?: return
        startEngine(type, source, _playback.value.positionMs, allowFallback = true)
    }

    fun dismissError() {
        _error.value = null
    }

    fun dismissNotice() {
        _notice.value = null
    }

    /** The video view for the active engine. The UI keys this on [generation]. */
    fun createSurface(context: Context): View = engine?.createView(context) ?: View(context)

    private fun startCurrent(): LaunchResult {
        val source = queue[queueIndex]
        val uri = source.uri
        if (uri.scheme.equals("http", ignoreCase = true)) {
            val host = uri.host.orEmpty()
            if (!prefs.isCleartextApproved(host)) {
                return LaunchResult.NeedsCleartextApproval(host, source)
            }
        }

        _notice.value = null
        _error.value = null
        val mpvOk = isMpvSupported()
        val preference = _enginePreference.value
        if (preference == EnginePreference.MPV && !mpvOk) {
            _notice.value = "libmpv needs Android 8.0+. Using Media3."
        }
        val learned = prefs.learnedEngine(uri.toString())
        val resumeMs = ResumeRules.startFor(prefs.savedPosition(uri.toString()), durationMs = 0L)
        startEngine(
            type = EnginePolicy.initialEngine(preference, learned, mpvOk),
            source = source,
            startMs = resumeMs,
            allowFallback = true
        )
        if (resumeMs > 0) _notice.value = "Resumed at ${formatDuration(resumeMs)}"
        updateQueueState()
        return LaunchResult.Started
    }

    private fun updateQueueState() {
        _queueState.value = QueueState(
            hasPrevious = queueIndex > 0,
            hasNext = queueIndex < queue.lastIndex
        )
    }

    @androidx.annotation.OptIn(markerClass = [UnstableApi::class])
    private fun startEngine(type: EngineType, source: PlaybackSource, startMs: Long, allowFallback: Boolean) {
        // Keep the position of the file being replaced, before its engine is released.
        rememberPosition()
        lastSavedPositionMs = 0L
        engineJob?.cancel()
        engine?.release()
        _error.value = null
        _abA.value = null
        _abB.value = null

        val app = getApplication<Application>()
        val created: PlaybackEngine = try {
            when (type) {
                EngineType.MEDIA3 -> Media3Engine(app)
                EngineType.MPV -> if (isMpvSupported()) MpvEngine(app) else Media3Engine(app)
            }
        } catch (e: Throwable) {
            engine = null
            _current.value = source
            _notice.value = "The ${type.label} engine could not start: ${e.message}"
            return
        }
        engine = created
        _current.value = source
        _engineType.value = created.type
        _generation.value = _generation.value + 1
        _playback.value = PlaybackState(speed = _playback.value.speed)
        _tracks.value = emptyList()

        // Re-apply the session's picture and sound settings to the new engine.
        created.setScaleMode(_scaleMode.value)
        if (!created.setBoost(_boost.value) && _boost.value != BoostLevel.OFF) {
            _notice.value = "Boost not supported here."
        }
        created.setRepeatOne(_repeatOne.value)
        created.setSubtitleStyle(_subtitleStyle.value)
        (created as? MpvEngine)?.setHardwareDecoding(_hardwareDecoding.value)

        // A fixed preset applies to every new file now. AUTO waits for the duration (see the state collector).
        presetPending = _contentMode.value == ContentMode.AUTO
        if (!presetPending) applyPreset(_contentMode.value, 0L)

        engineJob = viewModelScope.launch {
            launch {
                created.state.collect { s ->
                    _playback.value = s
                    if (s.isPlaying && abs(s.positionMs - lastSavedPositionMs) >= SAVE_EVERY_MS) {
                        lastSavedPositionMs = s.positionMs
                        rememberPosition()
                    }
                    // A-B repeat: jump back to A once playback reaches B.
                    val a = _abA.value
                    val b = _abB.value
                    if (a != null && b != null && s.positionMs >= b) created.seekTo(a)
                    if (presetPending && s.durationMs > 0) {
                        presetPending = false
                        applyPreset(ContentMode.AUTO, s.durationMs)
                    }
                    maybeAdvance(created, s)
                }
            }
            launch {
                created.failure.collect { f ->
                    if (f != null) {
                        // Post, so the failed engine is never released from inside its own callback.
                        viewModelScope.launch(Dispatchers.Main) {
                            handleFailure(created, f, source, allowFallback, startMs)
                        }
                    }
                }
            }
            launch { created.tracks.collect { _tracks.value = it } }
        }
        created.setSpeed(_playback.value.speed)
        created.load(source, startMs)
        created.setMuted(_muted.value)
        _eqPresets.value = created.equalizerPresets()
        _eqIndex.value = -1
    }

    /** When a file ends, play the next item in the queue, unless "repeat this video" is on. */
    private fun maybeAdvance(owner: PlaybackEngine, s: PlaybackState) {
        if (!s.isEnded || _repeatOne.value || !_queueState.value.hasNext) return
        viewModelScope.launch(Dispatchers.Main) {
            if (engine === owner) next()
        }
    }

    @androidx.annotation.OptIn(markerClass = [UnstableApi::class])
    private fun handleFailure(
        failed: PlaybackEngine,
        failure: PlaybackFailure,
        source: PlaybackSource,
        allowFallback: Boolean,
        startMs: Long
    ) {
        if (engine !== failed) return
        val mpvOk = isMpvSupported()
        if (allowFallback && EnginePolicy.canFallback(_enginePreference.value, failed.type, failure.kind, mpvOk)) {
            _notice.value = "Switching to libmpv…"
            prefs.setLearnedEngine(source.uri.toString(), EngineType.MPV)
            startEngine(
                type = EngineType.MPV,
                source = source,
                startMs = _playback.value.positionMs.takeIf { it > 0 } ?: startMs,
                allowFallback = false
            )
        } else {
            _error.value = failure.message
        }
    }

    override fun onCleared() {
        rememberPosition()
        engineJob?.cancel()
        sleepJob?.cancel()
        engine?.release()
        engine = null
        super.onCleared()
    }
}
