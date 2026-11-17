package com.tuplayer.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuplayer.app.playback.AspectOption
import com.tuplayer.app.playback.BoostLevel
import com.tuplayer.app.playback.ContentMode
import com.tuplayer.app.playback.EngineType
import com.tuplayer.app.playback.PlaybackController
import com.tuplayer.app.playback.RotationMode
import com.tuplayer.app.playback.SPEED_PRESETS
import com.tuplayer.app.playback.ScaleMode
import com.tuplayer.app.playback.SubtitleColor
import com.tuplayer.app.playback.SubtitlePosition
import com.tuplayer.app.playback.SubtitleSize
import com.tuplayer.app.playback.SubtitleStyle
import com.tuplayer.app.playback.TrackKind
import com.tuplayer.app.util.formatDuration
import com.tuplayer.app.util.speedLabel
import java.util.Locale
import kotlin.math.abs

/** Option sheets opened from the player's options bar. */
enum class MenuPage { SPEED, SLEEP, BOOST, EQUALIZER, ROTATION, PICTURE, CONTENT, ENGINE, INFO, AUDIO, SUBTITLE }

/** Translucent glass over the picture, so a track change never shows a black box. */
private val SheetGlass = Color(0xB0121216)
private val ChipFill = Color(0x59FFFFFF)
private val RowFill = Color(0x33FFFFFF)
private val SheetEdge = Color(0x26FFFFFF)

/**
 * A glass sheet that slides up over the video. Tap outside it, or the close button, to dismiss.
 * There is no dark scrim, so the video stays visible while a track or setting changes.
 */
@Composable
fun PlayerMenu(
    playback: PlaybackController,
    page: MenuPage,
    onClose: () -> Unit
) {
    val engine by playback.engineType.collectAsState()
    val state by playback.playback.collectAsState()
    val source by playback.current.collectAsState()
    val muted by playback.muted.collectAsState()

    Box(
        Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClose
            )
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(SheetGlass)
                .border(1.dp, SheetEdge, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                // Taps on the sheet are consumed here, so they never reach the close handler behind it.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .navigationBarsPadding()
                .padding(top = 10.dp, bottom = 18.dp)
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0x66FFFFFF))
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    pageTitle(page),
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                IconTile(Glyph.CLOSE, onClick = onClose)
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (page) {
                    MenuPage.SUBTITLE -> SubtitlePage(playback)

                    MenuPage.AUDIO -> {
                        val tracks by playback.tracks.collectAsState()
                        val audio = tracks.filter { it.kind == TrackKind.AUDIO }
                        Section("Audio track") {
                            if (audio.isEmpty()) {
                                Note("No other tracks.")
                            } else {
                                audio.forEach { option -> OptionRow(option.label, option.selected) { playback.selectTrack(option) } }
                            }
                        }
                    }

                    MenuPage.SPEED -> {
                        val speed = state.speed
                        Section("Speed") {
                            ChipRow {
                                SPEED_PRESETS.forEach { preset ->
                                    Chip(speedLabel(preset), abs(speed - preset) < 0.001f) { playback.setSpeed(preset) }
                                }
                            }
                        }
                    }

                    MenuPage.SLEEP -> {
                        val sleepLeft by playback.sleepMinutesLeft.collectAsState()
                        val left = sleepLeft
                        Section("Sleep timer") {
                            ChipRow {
                                listOf<Int?>(null, 15, 30, 60, 90).forEach { minutes ->
                                    Chip(
                                        if (minutes == null) "Off" else "$minutes min",
                                        selected = if (minutes == null) left == null else left != null && left in 1..minutes
                                    ) { playback.setSleepTimer(minutes) }
                                }
                            }
                            left?.let { Note("Pauses in $it min") }
                        }
                    }

                    MenuPage.BOOST -> {
                        val boost by playback.boost.collectAsState()
                        Section("Boost") {
                            ChipRow {
                                BoostLevel.entries.forEach { level ->
                                    Chip(level.label, boost == level) { playback.setBoost(level) }
                                }
                            }
                            Note("High levels can distort.")
                        }
                    }

                    MenuPage.EQUALIZER -> {
                        val presets by playback.equalizerPresets.collectAsState()
                        val selected by playback.equalizerIndex.collectAsState()
                        Section("Equalizer") {
                            if (engine != EngineType.MEDIA3) {
                                Note("Media3 only.")
                            } else if (presets.isEmpty()) {
                                Note("Not available.")
                            } else {
                                presets.forEachIndexed { index, name ->
                                    OptionRow(name, selected = selected == index) { playback.setEqualizerPreset(index) }
                                }
                            }
                        }
                    }

                    MenuPage.ROTATION -> {
                        val rotation by playback.rotation.collectAsState()
                        Section("Rotation") {
                            ChipRow {
                                RotationMode.entries.forEach { mode ->
                                    Chip(mode.label, rotation == mode) { playback.setRotation(mode) }
                                }
                            }
                        }
                    }

                    MenuPage.PICTURE -> {
                        val aspect by playback.aspect.collectAsState()
                        val scale by playback.scaleMode.collectAsState()
                        val zoom by playback.zoom.collectAsState()
                        Section("Zoom ${zoomText(zoom)}") {
                            Slider(
                                value = zoom,
                                onValueChange = { playback.setZoom(it) },
                                valueRange = 1f..3f,
                                colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent)
                            )
                        }
                        Section("Aspect") {
                            ChipRow {
                                AspectOption.entries.forEach { option ->
                                    Chip(option.label, aspect == option) { playback.setAspect(option) }
                                }
                            }
                        }
                        Section("Fit") {
                            ChipRow {
                                ScaleMode.entries.forEach { option ->
                                    Chip(option.label, scale == option) { playback.setScaleMode(option) }
                                }
                            }
                        }
                    }

                    MenuPage.CONTENT -> {
                        val contentMode by playback.contentMode.collectAsState()
                        Section("Content") {
                            ChipRow {
                                ContentMode.entries.forEach { option ->
                                    Chip(option.label, contentMode == option) { playback.setContentMode(option) }
                                }
                            }
                            Note("Sets fit and boost on start.")
                        }
                    }

                    MenuPage.ENGINE -> {
                        val hardware by playback.hardwareDecoding.collectAsState()
                        val mpvOk = playback.isMpvSupported()
                        Section("Engine") {
                            OptionRow("Media3", selected = engine == EngineType.MEDIA3) {
                                playback.switchEngine(EngineType.MEDIA3)
                            }
                            OptionRow("libmpv", selected = engine == EngineType.MPV, enabled = mpvOk) {
                                playback.switchEngine(EngineType.MPV)
                            }
                            if (!mpvOk) Note("libmpv needs Android 8.0+.")
                        }
                        if (engine == EngineType.MPV) {
                            Section("Decoder") {
                                ChipRow {
                                    Chip("Hardware", hardware) { playback.setHardwareDecoding(true) }
                                    Chip("Software", !hardware) { playback.setHardwareDecoding(false) }
                                }
                            }
                        }
                    }

                    MenuPage.INFO -> {
                        val hw by playback.hardwareDecoding.collectAsState()
                        val abA by playback.abA.collectAsState()
                        val abB by playback.abB.collectAsState()
                        Section("Info") {
                            InfoRow("Title", source?.title?.ifBlank { "—" } ?: "—")
                            InfoRow("Engine", engine?.label ?: "—")
                            InfoRow(
                                "Size",
                                if (state.videoWidth > 0 && state.videoHeight > 0) {
                                    "${state.videoWidth} × ${state.videoHeight}"
                                } else {
                                    "—"
                                }
                            )
                            InfoRow("Length", if (state.durationMs > 0) formatDuration(state.durationMs) else "Live")
                            InfoRow("Speed", speedLabel(state.speed))
                            InfoRow(
                                "Decoder",
                                when (engine) {
                                    EngineType.MEDIA3 -> "Hardware"
                                    EngineType.MPV -> if (hw) "Hardware" else "Software"
                                    null -> "—"
                                }
                            )
                            InfoRow("Sound", if (muted) "Muted" else "On")
                            InfoRow(
                                "A-B",
                                when {
                                    abA == null -> "Off"
                                    abB == null -> "A ${formatDuration(abA ?: 0L)}"
                                    else -> "${formatDuration(abA ?: 0L)} – ${formatDuration(abB ?: 0L)}"
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Subtitle sheet: pick a track, then adjust how subtitles look. Both engines apply the same style. */
@Composable
private fun SubtitlePage(playback: PlaybackController) {
    val tracks by playback.tracks.collectAsState()
    val style by playback.subtitleStyle.collectAsState()
    val subtitles = tracks.filter { it.kind == TrackKind.SUBTITLE }

    Section("Track") {
        if (subtitles.isEmpty()) {
            Note("No subtitles in this file.")
        } else {
            subtitles.forEach { option ->
                OptionRow(option.label, option.selected) { playback.selectTrack(option) }
            }
        }
    }

    Section("Size") {
        ChipRow {
            SubtitleSize.entries.forEach { size ->
                Chip(size.label, style.size == size) { playback.setSubtitleStyle(style.copy(size = size)) }
            }
        }
    }
    Section("Colour") {
        ChipRow {
            SubtitleColor.entries.forEach { color ->
                ColorChip(color, selected = style.color == color) { playback.setSubtitleStyle(style.copy(color = color)) }
            }
        }
    }
    Section("Background") {
        ChipRow {
            Chip("Off", !style.background) { playback.setSubtitleStyle(style.copy(background = false)) }
            Chip("Box", style.background) { playback.setSubtitleStyle(style.copy(background = true)) }
        }
    }
    Section("Position") {
        ChipRow {
            SubtitlePosition.entries.forEach { position ->
                Chip(position.label, style.position == position) { playback.setSubtitleStyle(style.copy(position = position)) }
            }
        }
    }
}

private fun pageTitle(page: MenuPage): String = when (page) {
    MenuPage.SPEED -> "Speed"
    MenuPage.SLEEP -> "Sleep timer"
    MenuPage.BOOST -> "Boost"
    MenuPage.EQUALIZER -> "Equalizer"
    MenuPage.ROTATION -> "Rotation"
    MenuPage.PICTURE -> "Picture"
    MenuPage.CONTENT -> "Content"
    MenuPage.ENGINE -> "Engine"
    MenuPage.INFO -> "Info"
    MenuPage.AUDIO -> "Audio"
    MenuPage.SUBTITLE -> "Subtitles"
}

/** Round glass button used in sheet headers. */
@Composable
private fun IconTile(glyph: Glyph, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(ChipFill)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        GlyphIcon(glyph, tint = Color.White, size = 18.dp)
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = Accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        content()
    }
}

@Composable
private fun Note(text: String) {
    Text(text, color = Muted, fontSize = 12.sp)
}

@Composable
private fun ChipRow(content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 13.sp) },
        shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = ChipFill,
            labelColor = Color.White,
            selectedContainerColor = Accent,
            selectedLabelColor = Color.White
        )
    )
}

/** Chip with a colour dot, used for subtitle colours. */
@Composable
private fun ColorChip(color: SubtitleColor, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(color.label, fontSize = 13.sp) },
        leadingIcon = {
            Box(
                Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Color(color.argb))
                    .border(1.dp, Color(0x66FFFFFF), CircleShape)
            )
        },
        shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = ChipFill,
            labelColor = Color.White,
            selectedContainerColor = Accent,
            selectedLabelColor = Color.White
        )
    )
}

@Composable
private fun OptionRow(title: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) RowFill else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick, enabled = enabled)
        Spacer(Modifier.width(6.dp))
        Text(
            title,
            color = if (enabled) Color.White else Muted,
            fontSize = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Text(label, color = Muted, fontSize = 13.sp, modifier = Modifier.weight(0.4f))
        Text(value, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(0.6f), textAlign = TextAlign.End)
    }
}

private fun zoomText(zoom: Float): String = "${String.format(Locale.US, "%.1f", zoom)}×"
