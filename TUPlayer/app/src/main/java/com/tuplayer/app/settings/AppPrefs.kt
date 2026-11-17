package com.tuplayer.app.settings

import android.content.Context
import androidx.core.content.edit
import com.tuplayer.app.playback.EnginePreference
import com.tuplayer.app.playback.EngineType
import com.tuplayer.app.playback.SubtitleColor
import com.tuplayer.app.playback.SubtitlePosition
import com.tuplayer.app.playback.SubtitleSize
import com.tuplayer.app.playback.SubtitleStyle

/** Small, typed wrapper around SharedPreferences. Keeps the existing file name for continuity. */
class AppPrefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("tu_player_preferences", Context.MODE_PRIVATE)

    var enginePreference: EnginePreference
        get() = runCatching { EnginePreference.valueOf(sp.getString(KEY_ENGINE, "") ?: "") }
            .getOrDefault(EnginePreference.AUTO)
        set(value) = sp.edit { putString(KEY_ENGINE, value.name) }

    var libraryTreeUri: String?
        get() = sp.getString(KEY_LIBRARY_TREE, null)
        set(value) = sp.edit { putString(KEY_LIBRARY_TREE, value) }

    fun isCleartextApproved(host: String): Boolean =
        approvedHosts().contains(host.lowercase())

    fun approveCleartextHost(host: String) {
        val updated = approvedHosts().toMutableSet().apply { add(host.lowercase()) }
        sp.edit { putStringSet(KEY_CLEARTEXT_HOSTS, updated) }
    }

    /** The engine that last needed a fallback for this exact source, if any. */
    fun learnedEngine(uri: String): EngineType? =
        sp.getString(learnedKey(uri), null)?.let { name ->
            runCatching { EngineType.valueOf(name) }.getOrNull()
        }

    fun setLearnedEngine(uri: String, type: EngineType) {
        sp.edit { putString(learnedKey(uri), type.name) }
    }

    /** Last saved position of a file, or null. Stored as "position|duration" in milliseconds. */
    fun savedPosition(uri: String): Long? =
        sp.getString(resumeKey(uri), null)?.substringBefore('|')?.toLongOrNull()

    fun savePosition(uri: String, positionMs: Long, durationMs: Long) {
        sp.edit { putString(resumeKey(uri), "$positionMs|$durationMs") }
    }

    fun clearSavedPosition(uri: String) {
        sp.edit { remove(resumeKey(uri)) }
    }

    private fun resumeKey(uri: String) = "resume::$uri"

    private fun approvedHosts(): Set<String> =
        sp.getStringSet(KEY_CLEARTEXT_HOSTS, emptySet()) ?: emptySet()

    var themeMode: ThemeMode
        get() = enumOrDefault(sp.getString(KEY_THEME_MODE, null), ThemeMode.SYSTEM)
        set(value) = sp.edit { putString(KEY_THEME_MODE, value.name) }

    var accentColor: AccentColor
        get() = enumOrDefault(sp.getString(KEY_ACCENT, null), AccentColor.RED)
        set(value) = sp.edit { putString(KEY_ACCENT, value.name) }

    var subtitleStyle: SubtitleStyle
        get() = SubtitleStyle(
            size = enumOrDefault(sp.getString(KEY_SUB_SIZE, null), SubtitleSize.MEDIUM),
            color = enumOrDefault(sp.getString(KEY_SUB_COLOR, null), SubtitleColor.WHITE),
            background = sp.getBoolean(KEY_SUB_BACKGROUND, false),
            position = enumOrDefault(sp.getString(KEY_SUB_POSITION, null), SubtitlePosition.LOW)
        )
        set(value) = sp.edit {
            putString(KEY_SUB_SIZE, value.size.name)
            putString(KEY_SUB_COLOR, value.color.name)
            putBoolean(KEY_SUB_BACKGROUND, value.background)
            putString(KEY_SUB_POSITION, value.position.name)
        }

    private inline fun <reified T : Enum<T>> enumOrDefault(raw: String?, default: T): T =
        runCatching { enumValueOf<T>(raw ?: "") }.getOrDefault(default)

    private fun learnedKey(uri: String) = "learned_engine::$uri"

    private companion object {
        const val KEY_ENGINE = "engine_preference"
        const val KEY_LIBRARY_TREE = "library_tree_uri"
        const val KEY_CLEARTEXT_HOSTS = "approved_cleartext_hosts"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_ACCENT = "accent_color"
        const val KEY_SUB_SIZE = "subtitle_size"
        const val KEY_SUB_COLOR = "subtitle_color"
        const val KEY_SUB_BACKGROUND = "subtitle_background"
        const val KEY_SUB_POSITION = "subtitle_position"
    }
}
