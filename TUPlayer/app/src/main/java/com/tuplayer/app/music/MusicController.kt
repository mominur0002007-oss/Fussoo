package com.tuplayer.app.music

import android.app.Application
import android.content.ComponentName
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** What the music screens show about the song that is playing. */
data class NowPlaying(val title: String, val artist: String, val album: String, val mediaUri: String)

private const val TAG = "TUPlayer.Music"

/**
 * UI side of the music player. It connects to [MusicService] through a MediaController, so the same
 * playback continues when the app is closed and reopened.
 */
class MusicController(application: Application) : AndroidViewModel(application) {

    private var controller: MediaController? = null
    private var connecting = false
    private var pending: Pair<List<AudioEntry>, Int>? = null
    private var ticker: Job? = null

    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _shuffle = MutableStateFlow(false)
    val shuffle: StateFlow<Boolean> = _shuffle.asStateFlow()

    /** One of Player.REPEAT_MODE_OFF, REPEAT_MODE_ALL or REPEAT_MODE_ONE. */
    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _queueSize = MutableStateFlow(0)
    val queueSize: StateFlow<Int> = _queueSize.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Starts [items] at [index]. Connects to the service first if it is not running yet. */
    fun playList(items: List<AudioEntry>, index: Int) {
        if (items.isEmpty()) return
        val c = controller
        if (c == null) {
            pending = items to index
            connect()
            return
        }
        applyList(c, items, index)
    }

    fun toggle() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun pause() {
        controller?.pause()
    }

    fun next() {
        controller?.seekToNextMediaItem()
    }

    fun previous() {
        val c = controller ?: return
        // Like the video player: a press after a few seconds restarts the song.
        if (c.currentPosition > 3_000L) c.seekTo(0L) else c.seekToPreviousMediaItem()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs.coerceAtLeast(0L))
        _positionMs.value = positionMs.coerceAtLeast(0L)
    }

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
    }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    @androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
    private fun connect() {
        if (controller != null || connecting) return
        connecting = true
        val app = getApplication<Application>()
        val token = SessionToken(app, ComponentName(app, MusicService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener({
            try {
                val c = future.get()
                controller = c
                attach(c)
                pending?.let { (items, index) ->
                    pending = null
                    applyList(c, items, index)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Could not connect to the music service", e)
                _error.value = "The music service could not start: ${e.message ?: "unknown error"}"
                pending = null
            } finally {
                connecting = false
            }
        }, ContextCompat.getMainExecutor(app))
    }

    private fun applyList(c: MediaController, items: List<AudioEntry>, index: Int) {
        _error.value = null
        _queueSize.value = items.size
        c.setMediaItems(items.map { it.toMediaItem() }, index.coerceIn(0, items.lastIndex), 0L)
        c.prepare()
        c.play()
    }

    private fun attach(c: MediaController) {
        syncState(c)
        c.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                syncState(c)
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _shuffle.value = shuffleModeEnabled
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                _repeatMode.value = repeatMode
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) _durationMs.value = c.duration.takeIf { it > 0 } ?: 0L
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                _error.value = "This song could not play: ${error.message ?: error.errorCodeName}"
            }
        })
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (isActive) {
                _positionMs.value = c.currentPosition.coerceAtLeast(0L)
                val d = c.duration
                _durationMs.value = if (d != C.TIME_UNSET && d > 0) d else 0L
                delay(TICK_MS)
            }
        }
    }

    private fun syncState(c: MediaController) {
        _isPlaying.value = c.isPlaying
        _shuffle.value = c.shuffleModeEnabled
        _repeatMode.value = c.repeatMode
        val item = c.currentMediaItem
        _nowPlaying.value = item?.let {
            val m = it.mediaMetadata
            NowPlaying(
                title = m.title?.toString().orEmpty().ifBlank { "Unknown title" },
                artist = m.artist?.toString().orEmpty().ifBlank { "Unknown artist" },
                album = m.albumTitle?.toString().orEmpty(),
                mediaUri = it.localConfiguration?.uri?.toString().orEmpty()
            )
        }
    }

    override fun onCleared() {
        ticker?.cancel()
        controller?.release()
        controller = null
        super.onCleared()
    }

    private fun AudioEntry.toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(uri.toString())
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .build()
        )
        .build()

    private companion object {
        const val TICK_MS = 500L
    }
}
