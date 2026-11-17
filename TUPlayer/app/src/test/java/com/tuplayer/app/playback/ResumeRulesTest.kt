package com.tuplayer.app.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumeRulesTest {
    private val minute = 60_000L

    @Test
    fun shortPositionsAreNotRemembered() {
        assertFalse(ResumeRules.shouldRemember(4_999L, 10 * minute))
        assertTrue(ResumeRules.shouldRemember(5_000L, 10 * minute))
    }

    @Test
    fun nearTheEndCountsAsFinished() {
        val duration = 10 * minute
        assertTrue(ResumeRules.shouldRemember(duration - 16_000L, duration))
        assertFalse(ResumeRules.shouldRemember(duration - 15_000L, duration))
        assertFalse(ResumeRules.shouldRemember(duration, duration))
    }

    @Test
    fun unknownDurationStillRemembersPosition() {
        assertTrue(ResumeRules.shouldRemember(30_000L, 0L))
    }

    @Test
    fun startForReturnsSavedPositionOrZero() {
        assertEquals(0L, ResumeRules.startFor(null, minute))
        assertEquals(0L, ResumeRules.startFor(2_000L, minute))
        assertEquals(42_000L, ResumeRules.startFor(42_000L, 10 * minute))
        assertEquals(0L, ResumeRules.startFor(10 * minute - 1_000L, 10 * minute))
    }
}
