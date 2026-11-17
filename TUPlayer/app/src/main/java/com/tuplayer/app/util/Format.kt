package com.tuplayer.app.util

import java.util.Locale

/** Formats milliseconds as m:ss, or h:mm:ss for runs of an hour or more. Always uses ASCII digits. */
fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val total = ms / 1000
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

/** 0.25 -> "0.25×", 2.0 -> "2×" */
fun speedLabel(speed: Float): String =
    if (speed % 1f == 0f) "${speed.toInt()}×" else "${speed}×"
