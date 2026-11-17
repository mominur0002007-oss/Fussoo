package com.tuplayer.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.tuplayer.app.settings.AccentColor
import com.tuplayer.app.settings.ThemeMode

/** The colours the screens draw with. One instance is active at a time; see [TUTheme] and [DarkRegion]. */
data class TUColors(
    val isDark: Boolean,
    val accent: Color,
    val background: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val text: Color,
    val muted: Color,
    val warning: Color
)

fun tuColors(dark: Boolean, accent: Color): TUColors =
    if (dark) {
        TUColors(
            isDark = true,
            accent = accent,
            background = Color(0xFF0D0D0F),
            surface = Color(0xFF17171B),
            surfaceAlt = Color(0xFF26262C),
            text = Color.White,
            muted = Color(0xFF9B9BA4),
            warning = Color(0xFFFFB86B)
        )
    } else {
        TUColors(
            isDark = false,
            accent = accent,
            background = Color(0xFFF6F6F8),
            surface = Color.White,
            surfaceAlt = Color(0xFFEDEDF1),
            text = Color(0xFF15151A),
            muted = Color(0xFF6B6B75),
            warning = Color(0xFFB35C00)
        )
    }

val LocalTUColors = staticCompositionLocalOf { tuColors(dark = true, accent = Color(AccentColor.RED.argb)) }

// Theme-aware colours. Each one reads the active palette, so screens follow the theme without changes.
val Ink: Color
    @Composable @ReadOnlyComposable get() = LocalTUColors.current.background
val SurfaceA: Color
    @Composable @ReadOnlyComposable get() = LocalTUColors.current.surface
val SurfaceB: Color
    @Composable @ReadOnlyComposable get() = LocalTUColors.current.surfaceAlt
val Accent: Color
    @Composable @ReadOnlyComposable get() = LocalTUColors.current.accent
val Muted: Color
    @Composable @ReadOnlyComposable get() = LocalTUColors.current.muted
val TextPrimary: Color
    @Composable @ReadOnlyComposable get() = LocalTUColors.current.text
val Warning: Color
    @Composable @ReadOnlyComposable get() = LocalTUColors.current.warning

// Fixed colours. They do not change with the theme.
val OnAccent = Color.White
val FolderColor = Color(0xFFF5C451)
val Scrim = Color(0xB3000000)

/**
 * Applies the chosen mode and accent. SYSTEM follows the phone's dark setting.
 * Status and navigation bar icons switch so they stay readable in both modes.
 */
@Composable
fun TUTheme(mode: ThemeMode, accent: AccentColor, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val colors = tuColors(dark, Color(accent.argb))
    val view = LocalView.current
    SideEffect {
        (view.context.findActivity())?.window?.let { window ->
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }
    MaterialTheme(
        colorScheme = if (dark) {
            darkColorScheme(
                primary = colors.accent,
                secondary = colors.accent,
                background = colors.background,
                surface = colors.surface,
                surfaceVariant = colors.surfaceAlt,
                onPrimary = OnAccent,
                onBackground = colors.text,
                onSurface = colors.text,
                onSurfaceVariant = colors.muted
            )
        } else {
            lightColorScheme(
                primary = colors.accent,
                secondary = colors.accent,
                background = colors.background,
                surface = colors.surface,
                surfaceVariant = colors.surfaceAlt,
                onPrimary = OnAccent,
                onBackground = colors.text,
                onSurface = colors.text,
                onSurfaceVariant = colors.muted
            )
        }
    ) {
        CompositionLocalProvider(LocalTUColors provides colors, content = content)
    }
}

/**
 * Forces the dark palette for content drawn over video or media, such as the player and the
 * now-playing view. The accent is kept, so the look matches the rest of the app.
 */
@Composable
fun DarkRegion(content: @Composable () -> Unit) {
    val accent = LocalTUColors.current.accent
    CompositionLocalProvider(LocalTUColors provides tuColors(dark = true, accent = accent), content = content)
}
