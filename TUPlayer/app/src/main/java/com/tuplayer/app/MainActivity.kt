/*
 * TU Player
 * Created by: Mominur Islam
 * Contact: mominur0002007@gmail.com
 * Development: developed by AI (Arena.ai Agent Mode)
 */
package com.tuplayer.app

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import com.tuplayer.app.diagnostics.CrashLog
import com.tuplayer.app.playback.PlaybackController
import com.tuplayer.app.ui.TUPlayerApp

class MainActivity : ComponentActivity() {

    private val playback: PlaybackController by lazy {
        ViewModelProvider(this)[PlaybackController::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLog.install(this)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent { TUPlayerApp() }

        // Keep the PiP parameters current: aspect ratio, and on Android 12+ whether leaving the app
        // should enter picture-in-picture automatically (only while a video is playing).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.STARTED) {
                    playback.playback
                        .map { Triple(it.isPlaying, it.videoWidth, it.videoHeight) }
                        .distinctUntilChanged()
                        .collect { setPictureInPictureParams(pictureParams()) }
                }
            }
        }
    }

    /** Home button or task switch while a video plays: shrink it into a picture-in-picture window. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && playback.canEnterPicture()) {
            enterPictureMode()
        }
    }

    /** Opens the picture-in-picture window now. Called from the player's PiP button. */
    @RequiresApi(Build.VERSION_CODES.O)
    fun enterPictureMode() {
        enterPictureInPictureMode(pictureParams())
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        playback.setInPip(isInPictureInPictureMode)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun pictureParams(): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(playback.canEnterPicture())
        }
        val state = playback.playback.value
        if (state.videoWidth > 0 && state.videoHeight > 0) {
            // The system accepts ratios from 1:2.39 to 2.39:1. Clamp to keep the call valid.
            val ratio = Rational(state.videoWidth, state.videoHeight)
            val value = ratio.toDouble().coerceIn(1 / 2.39, 2.39)
            builder.setAspectRatio(Rational((value * 1000).toInt(), 1000))
        }
        return builder.build()
    }
}
