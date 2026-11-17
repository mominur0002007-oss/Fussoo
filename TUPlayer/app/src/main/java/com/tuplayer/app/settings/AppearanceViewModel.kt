package com.tuplayer.app.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Holds the theme mode and accent, and saves each change right away. */
class AppearanceViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = AppPrefs(application)

    private val _mode = MutableStateFlow(prefs.themeMode)
    val mode: StateFlow<ThemeMode> = _mode.asStateFlow()

    private val _accent = MutableStateFlow(prefs.accentColor)
    val accent: StateFlow<AccentColor> = _accent.asStateFlow()

    fun setMode(mode: ThemeMode) {
        _mode.value = mode
        prefs.themeMode = mode
    }

    fun setAccent(accent: AccentColor) {
        _accent.value = accent
        prefs.accentColor = accent
    }
}
