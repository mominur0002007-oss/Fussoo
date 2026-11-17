package com.tuplayer.app.playback

/**
 * Decides when a file's position is worth remembering. Pure functions so they can be unit tested.
 *
 * - Positions under [MIN_RESUME_MS] are not saved: a file opened for a few seconds starts from the top.
 * - Within [END_MARGIN_MS] of the end the file counts as finished, so the next open starts from the top.
 */
object ResumeRules {
    const val MIN_RESUME_MS = 5_000L
    const val END_MARGIN_MS = 15_000L

    /** True when this position should be stored for the file. */
    fun shouldRemember(positionMs: Long, durationMs: Long): Boolean {
        if (positionMs < MIN_RESUME_MS) return false
        if (durationMs > 0 && positionMs >= durationMs - END_MARGIN_MS) return false
        return true
    }

    /** Where to start a file that has a saved position, or 0 when it should start from the top. */
    fun startFor(savedMs: Long?, durationMs: Long): Long {
        if (savedMs == null || savedMs < MIN_RESUME_MS) return 0L
        if (durationMs > 0 && savedMs >= durationMs - END_MARGIN_MS) return 0L
        return savedMs
    }
}
