package com.tuplayer.app.playback

/**
 * Pure decision logic for choosing a playback engine. It has no Android dependencies so it
 * can be unit tested on the JVM.
 */
object EnginePolicy {

    /**
     * Engine to start a file with.
     * - MEDIA3 / MPV: the user's forced choice. MPV falls back to Media3 if libmpv is unavailable.
     * - AUTO: Media3, unless this file previously needed libmpv (remembered per file).
     */
    fun initialEngine(
        preference: EnginePreference,
        learned: EngineType?,
        mpvAvailable: Boolean
    ): EngineType = when (preference) {
        EnginePreference.MEDIA3 -> EngineType.MEDIA3
        EnginePreference.MPV -> if (mpvAvailable) EngineType.MPV else EngineType.MEDIA3
        EnginePreference.AUTO ->
            if (mpvAvailable && learned == EngineType.MPV) EngineType.MPV else EngineType.MEDIA3
    }

    /**
     * Whether a failure should move playback from Media3 to libmpv.
     * Only AUTO does this. Only format and codec failures qualify. Network failures do not,
     * because libmpv would hit the same error. libmpv never falls back, since it is the broader engine.
     */
    fun canFallback(
        preference: EnginePreference,
        failedEngine: EngineType,
        kind: FailureKind,
        mpvAvailable: Boolean
    ): Boolean =
        preference == EnginePreference.AUTO &&
            failedEngine == EngineType.MEDIA3 &&
            mpvAvailable &&
            kind == FailureKind.UNSUPPORTED_FORMAT_OR_CODEC
}
