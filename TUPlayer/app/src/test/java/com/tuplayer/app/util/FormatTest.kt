package com.tuplayer.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {

    @Test
    fun durationUnderAnHourUsesMinutesAndSeconds() {
        assertEquals("0:00", formatDuration(0L))
        assertEquals("0:00", formatDuration(-5L))
        assertEquals("2:05", formatDuration(125_000L))
    }

    @Test
    fun durationOfAnHourOrMoreShowsHours() {
        assertEquals("1:02:05", formatDuration(3_725_000L))
        assertEquals("10:00:00", formatDuration(36_000_000L))
    }

    @Test
    fun speedLabelsMatchPresets() {
        assertEquals("0.25×", speedLabel(0.25f))
        assertEquals("0.75×", speedLabel(0.75f))
        assertEquals("1×", speedLabel(1f))
        assertEquals("1.5×", speedLabel(1.5f))
        assertEquals("4×", speedLabel(4f))
    }
}
