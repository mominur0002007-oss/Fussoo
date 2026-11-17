package com.tuplayer.app.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EnginePolicyTest {

    @Test
    fun automaticStartsWithMedia3() {
        assertEquals(EngineType.MEDIA3, EnginePolicy.initialEngine(EnginePreference.AUTO, null, mpvAvailable = true))
    }

    @Test
    fun automaticUsesLearnedMpvWhenAvailable() {
        assertEquals(EngineType.MPV, EnginePolicy.initialEngine(EnginePreference.AUTO, EngineType.MPV, mpvAvailable = true))
    }

    @Test
    fun automaticIgnoresLearnedMpvWhenUnavailable() {
        assertEquals(EngineType.MEDIA3, EnginePolicy.initialEngine(EnginePreference.AUTO, EngineType.MPV, mpvAvailable = false))
    }

    @Test
    fun forcedMedia3IgnoresLearnedChoice() {
        assertEquals(EngineType.MEDIA3, EnginePolicy.initialEngine(EnginePreference.MEDIA3, EngineType.MPV, mpvAvailable = true))
    }

    @Test
    fun forcedMpvFallsBackToMedia3WhenUnavailable() {
        assertEquals(EngineType.MEDIA3, EnginePolicy.initialEngine(EnginePreference.MPV, null, mpvAvailable = false))
        assertEquals(EngineType.MPV, EnginePolicy.initialEngine(EnginePreference.MPV, null, mpvAvailable = true))
    }

    @Test
    fun fallsBackOnlyForUnsupportedFormatInAutomaticMode() {
        assertTrue(
            EnginePolicy.canFallback(
                EnginePreference.AUTO, EngineType.MEDIA3, FailureKind.UNSUPPORTED_FORMAT_OR_CODEC, mpvAvailable = true
            )
        )
    }

    @Test
    fun networkFailuresDoNotFallBack() {
        assertFalse(
            EnginePolicy.canFallback(
                EnginePreference.AUTO, EngineType.MEDIA3, FailureKind.SOURCE_UNAVAILABLE, mpvAvailable = true
            )
        )
    }

    @Test
    fun manualChoiceNeverFallsBack() {
        assertFalse(
            EnginePolicy.canFallback(
                EnginePreference.MEDIA3, EngineType.MEDIA3, FailureKind.UNSUPPORTED_FORMAT_OR_CODEC, mpvAvailable = true
            )
        )
    }

    @Test
    fun noFallbackWhenMpvUnavailableOrAlreadyOnMpv() {
        assertFalse(
            EnginePolicy.canFallback(
                EnginePreference.AUTO, EngineType.MEDIA3, FailureKind.UNSUPPORTED_FORMAT_OR_CODEC, mpvAvailable = false
            )
        )
        assertFalse(
            EnginePolicy.canFallback(
                EnginePreference.AUTO, EngineType.MPV, FailureKind.UNSUPPORTED_FORMAT_OR_CODEC, mpvAvailable = true
            )
        )
    }
}
