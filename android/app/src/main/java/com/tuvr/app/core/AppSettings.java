package com.tuvr.app.core;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Locale;

/**
 * Single source of truth for user preferences.
 *
 * Types are chosen so that every value can be edited by a standard Android
 * preference widget (int, string, boolean). All public accessors expose the
 * convenient types the renderer and players need.
 *
 * Defaults are tuned for a TECNO Spark 10C in a Shinecon box: balanced quality,
 * comfortable field of view, conservative smoothing.
 */
public final class AppSettings {

    public static final String FILE = "tuvr_settings";

    /* ---------------- viewer / optics ---------------- */
    public static final String K_VIEWER_PROFILE = "viewer_profile";
    public static final String K_LENS_SEPARATION = "lens_separation_mm";
    public static final String K_SCREEN_LENS = "screen_lens_mm";
    public static final String K_FOV = "fov_deg";
    public static final String K_EYE_OFFSET_X = "eye_offset_x_mm";
    public static final String K_EYE_OFFSET_Y = "eye_offset_y_mm";
    public static final String K_DISTORTION_K1 = "distortion_k1_pct";
    public static final String K_DISTORTION_K2 = "distortion_k2_pct";
    public static final String K_CHROMATIC = "chromatic_milli";
    public static final String K_IPD = "ipd_mm";
    public static final String K_DISTORTION_ENABLED = "distortion_enabled";
    public static final String K_SWAP_EYES = "swap_eyes";

    /* ---------------- tracking ---------------- */
    public static final String K_TRACKING_SMOOTHING = "tracking_smoothing_pct";
    public static final String K_TRACKING_SENSITIVITY = "tracking_sensitivity_pct";
    public static final String K_RECENTER_ON_START = "recenter_on_start";
    public static final String K_COMFORT_FOV = "comfort_fov";
    public static final String K_GYRO_BIAS_X = "gyro_bias_x";
    public static final String K_GYRO_BIAS_Y = "gyro_bias_y";
    public static final String K_GYRO_BIAS_Z = "gyro_bias_z";

    /* ---------------- graphics ---------------- */
    public static final String K_QUALITY = "graphics_quality";
    public static final String K_ADAPTIVE_QUALITY = "adaptive_quality";
    public static final String K_FRAME_LIMIT = "frame_limit";
    public static final String K_SHOW_FPS = "show_fps";

    /* ---------------- video / audio ---------------- */
    public static final String K_DEFAULT_PROJECTION = "default_projection";
    public static final String K_DEFAULT_STEREO = "default_stereo";
    public static final String K_RESUME_PLAYBACK = "resume_playback";
    public static final String K_PLAYBACK_SPEED = "playback_speed_pct";
    public static final String K_SUBTITLE_SIZE = "subtitle_size";
    public static final String K_VOLUME = "volume_pct";

    /* ---------------- interface ---------------- */
    public static final String K_ENVIRONMENT = "environment";
    public static final String K_SCREEN_SIZE = "screen_size_pct";
    public static final String K_SCREEN_DISTANCE = "screen_distance_dm";
    public static final String K_CURVATURE = "curvature_pct";
    public static final String K_SCREEN_Y = "screen_y_cm";
    public static final String K_DWELL_MS = "dwell_ms";
    public static final String K_SELECTION_MODE = "selection_mode";
    public static final String K_UI_SCALE = "ui_scale_pct";
    public static final String K_KEEP_AWAKE = "keep_awake";

    /* ---------------- misc ---------------- */
    public static final String K_LAST_VIDEO = "last_video_uri";
    public static final String K_LAST_VIDEO_POS = "last_video_pos";
    public static final String K_SAF_URIS = "saf_video_uris";
    public static final String K_BOOKMARKS = "bookmarks_json";
    public static final String K_FAVORITE_APPS = "favorite_apps";
    public static final String K_RECENT_APPS = "recent_apps";

    /* quality levels */
    public static final int QUALITY_LOW = 0;
    public static final int QUALITY_BALANCED = 1;
    public static final int QUALITY_HIGH = 2;

    /* selection modes */
    public static final int SELECT_GAZE = 0;
    public static final int SELECT_TOUCH = 1;

    private static volatile AppSettings instance;
    private final SharedPreferences prefs;

    private AppSettings(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
        applyDefaults();
    }

    public static void init(Context context) {
        if (instance == null) {
            synchronized (AppSettings.class) {
                if (instance == null) {
                    instance = new AppSettings(context);
                }
            }
        }
    }

    public static AppSettings get(Context context) {
        init(context);
        return instance;
    }

    public static AppSettings get() {
        if (instance == null) {
            throw new IllegalStateException("AppSettings.init() not called");
        }
        return instance;
    }

    public static SharedPreferences prefs() {
        return get().prefs;
    }

    private void applyDefaults() {
        if (prefs.getBoolean("defaults_applied", false)) {
            return;
        }
        SharedPreferences.Editor e = prefs.edit();
        e.putString(K_VIEWER_PROFILE, "shinecon");
        e.putInt(K_LENS_SEPARATION, 62);
        e.putInt(K_SCREEN_LENS, 45);
        e.putInt(K_FOV, 92);
        e.putInt(K_EYE_OFFSET_X, 0);
        e.putInt(K_EYE_OFFSET_Y, 0);
        e.putInt(K_DISTORTION_K1, 20);
        e.putInt(K_DISTORTION_K2, 3);
        e.putInt(K_CHROMATIC, 0);
        e.putInt(K_IPD, 63);
        e.putBoolean(K_DISTORTION_ENABLED, true);
        e.putBoolean(K_SWAP_EYES, false);
        e.putInt(K_TRACKING_SMOOTHING, 35);
        e.putInt(K_TRACKING_SENSITIVITY, 100);
        e.putBoolean(K_RECENTER_ON_START, true);
        e.putBoolean(K_COMFORT_FOV, false);
        e.putString(K_QUALITY, "balanced");
        e.putBoolean(K_ADAPTIVE_QUALITY, true);
        e.putString(K_FRAME_LIMIT, "60");
        e.putBoolean(K_SHOW_FPS, false);
        e.putString(K_DEFAULT_PROJECTION, "flat");
        e.putString(K_DEFAULT_STEREO, "mono");
        e.putBoolean(K_RESUME_PLAYBACK, true);
        e.putInt(K_PLAYBACK_SPEED, 100);
        e.putInt(K_SUBTITLE_SIZE, 16);
        e.putInt(K_VOLUME, 100);
        e.putString(K_ENVIRONMENT, "cinema");
        e.putInt(K_SCREEN_SIZE, 100);
        e.putInt(K_SCREEN_DISTANCE, 60);
        e.putInt(K_CURVATURE, 25);
        e.putInt(K_SCREEN_Y, 0);
        e.putInt(K_DWELL_MS, 1300);
        e.putString(K_SELECTION_MODE, "gaze");
        e.putInt(K_UI_SCALE, 100);
        e.putBoolean(K_KEEP_AWAKE, true);
        e.putBoolean("defaults_applied", true);
        e.apply();
    }

    private int intOf(String key, int fallback) {
        try {
            return prefs.getInt(key, fallback);
        } catch (ClassCastException e) {
            return fallback;
        }
    }

    private float floatOf(String key) {
        try {
            return prefs.getFloat(key, 0f);
        } catch (ClassCastException e) {
            return 0f;
        }
    }

    private String stringOf(String key, String fallback) {
        try {
            String value = prefs.getString(key, fallback);
            return value == null ? fallback : value;
        } catch (ClassCastException e) {
            return fallback;
        }
    }

    /* ------------------------------------------------------------------ */
    /* viewer                                                             */
    /* ------------------------------------------------------------------ */

    public String viewerProfileId() {
        return stringOf(K_VIEWER_PROFILE, "shinecon");
    }

    public void setViewerProfileId(String id) {
        prefs.edit().putString(K_VIEWER_PROFILE, id).apply();
    }

    public float lensSeparationMm() {
        return intOf(K_LENS_SEPARATION, 62);
    }

    public void setLensSeparationMm(float value) {
        prefs.edit().putInt(K_LENS_SEPARATION,
                Math.round(clamp(value, 45f, 85f))).apply();
    }

    public float screenLensDistanceMm() {
        return intOf(K_SCREEN_LENS, 45);
    }

    public void setScreenLensDistanceMm(float value) {
        prefs.edit().putInt(K_SCREEN_LENS, Math.round(clamp(value, 22f, 75f))).apply();
    }

    public float fovDeg() {
        return intOf(K_FOV, 92);
    }

    public void setFovDeg(float value) {
        prefs.edit().putInt(K_FOV, Math.round(clamp(value, 55f, 115f))).apply();
    }

    public float eyeOffsetXMm() {
        return intOf(K_EYE_OFFSET_X, 0);
    }

    public void setEyeOffsetXMm(float value) {
        prefs.edit().putInt(K_EYE_OFFSET_X, Math.round(clamp(value, -20f, 20f))).apply();
    }

    public float eyeOffsetYMm() {
        return intOf(K_EYE_OFFSET_Y, 0);
    }

    public void setEyeOffsetYMm(float value) {
        prefs.edit().putInt(K_EYE_OFFSET_Y, Math.round(clamp(value, -20f, 20f))).apply();
    }

    public float distortionK1() {
        return intOf(K_DISTORTION_K1, 20) / 100f;
    }

    public void setDistortionK1(float value) {
        prefs.edit().putInt(K_DISTORTION_K1, Math.round(clamp(value, 0f, 0.8f) * 100f)).apply();
    }

    public float distortionK2() {
        return intOf(K_DISTORTION_K2, 3) / 100f;
    }

    public void setDistortionK2(float value) {
        prefs.edit().putInt(K_DISTORTION_K2, Math.round(clamp(value, 0f, 0.6f) * 100f)).apply();
    }

    public float chromaticShift() {
        return intOf(K_CHROMATIC, 0) / 1000f;
    }

    public void setChromaticShift(float value) {
        prefs.edit().putInt(K_CHROMATIC, Math.round(clamp(value, 0f, 0.04f) * 1000f)).apply();
    }

    public float ipdMm() {
        return intOf(K_IPD, 63);
    }

    public void setIpdMm(float value) {
        prefs.edit().putInt(K_IPD, Math.round(clamp(value, 45f, 80f))).apply();
    }

    public boolean distortionEnabled() {
        return prefs.getBoolean(K_DISTORTION_ENABLED, true);
    }

    public void setDistortionEnabled(boolean value) {
        prefs.edit().putBoolean(K_DISTORTION_ENABLED, value).apply();
    }

    public boolean swapEyes() {
        return prefs.getBoolean(K_SWAP_EYES, false);
    }

    public void setSwapEyes(boolean value) {
        prefs.edit().putBoolean(K_SWAP_EYES, value).apply();
    }

    public ViewerProfile viewerProfile() {
        return new ViewerProfile(viewerProfileId(), lensSeparationMm(), screenLensDistanceMm(),
                fovDeg(), eyeOffsetXMm(), eyeOffsetYMm(), distortionK1(), distortionK2(),
                chromaticShift(), ipdMm(), distortionEnabled());
    }

    /* ------------------------------------------------------------------ */
    /* tracking                                                           */
    /* ------------------------------------------------------------------ */

    public float trackingSmoothing() {
        return intOf(K_TRACKING_SMOOTHING, 35) / 100f;
    }

    public void setTrackingSmoothing(float value) {
        prefs.edit().putInt(K_TRACKING_SMOOTHING, Math.round(clamp(value, 0f, 0.9f) * 100f)).apply();
    }

    public float trackingSensitivity() {
        return intOf(K_TRACKING_SENSITIVITY, 100) / 100f;
    }

    public void setTrackingSensitivity(float value) {
        prefs.edit().putInt(K_TRACKING_SENSITIVITY,
                Math.round(clamp(value, 0.4f, 2f) * 100f)).apply();
    }

    public boolean recenterOnStart() {
        return prefs.getBoolean(K_RECENTER_ON_START, true);
    }

    public boolean comfortFov() {
        return prefs.getBoolean(K_COMFORT_FOV, false);
    }

    public float gyroBias(int axis) {
        String key = axis == 1 ? K_GYRO_BIAS_Y : (axis == 2 ? K_GYRO_BIAS_Z : K_GYRO_BIAS_X);
        return floatOf(key);
    }

    public void setGyroBias(float x, float y, float z) {
        prefs.edit()
                .putFloat(K_GYRO_BIAS_X, x)
                .putFloat(K_GYRO_BIAS_Y, y)
                .putFloat(K_GYRO_BIAS_Z, z)
                .apply();
    }

    /* ------------------------------------------------------------------ */
    /* graphics                                                           */
    /* ------------------------------------------------------------------ */

    public int quality() {
        String value = stringOf(K_QUALITY, "balanced");
        switch (value) {
            case "low":
                return QUALITY_LOW;
            case "high":
                return QUALITY_HIGH;
            default:
                return QUALITY_BALANCED;
        }
    }

    public void setQuality(int quality) {
        String value = quality == QUALITY_LOW ? "low" : (quality == QUALITY_HIGH ? "high" : "balanced");
        prefs.edit().putString(K_QUALITY, value).apply();
    }

    public boolean adaptiveQuality() {
        return prefs.getBoolean(K_ADAPTIVE_QUALITY, true);
    }

    public int frameLimit() {
        try {
            return Integer.parseInt(stringOf(K_FRAME_LIMIT, "60"));
        } catch (NumberFormatException e) {
            return 60;
        }
    }

    public boolean showFps() {
        return prefs.getBoolean(K_SHOW_FPS, false);
    }

    /* ------------------------------------------------------------------ */
    /* video / audio                                                      */
    /* ------------------------------------------------------------------ */

    public int defaultProjection() {
        switch (stringOf(K_DEFAULT_PROJECTION, "flat")) {
            case "vr180":
                return 1;
            case "vr360":
                return 2;
            default:
                return 0;
        }
    }

    public void setDefaultProjection(int projection) {
        String value = projection == 1 ? "vr180" : (projection == 2 ? "vr360" : "flat");
        prefs.edit().putString(K_DEFAULT_PROJECTION, value).apply();
    }

    public int defaultStereo() {
        switch (stringOf(K_DEFAULT_STEREO, "mono")) {
            case "sbs":
                return 1;
            case "tab":
                return 2;
            default:
                return 0;
        }
    }

    public void setDefaultStereo(int stereo) {
        String value = stereo == 1 ? "sbs" : (stereo == 2 ? "tab" : "mono");
        prefs.edit().putString(K_DEFAULT_STEREO, value).apply();
    }

    public boolean resumePlayback() {
        return prefs.getBoolean(K_RESUME_PLAYBACK, true);
    }

    public float playbackSpeed() {
        return intOf(K_PLAYBACK_SPEED, 100) / 100f;
    }

    public void setPlaybackSpeed(float value) {
        prefs.edit().putInt(K_PLAYBACK_SPEED, Math.round(clamp(value, 0.5f, 2f) * 100f)).apply();
    }

    public int subtitleSizeSp() {
        return intOf(K_SUBTITLE_SIZE, 16);
    }

    public float volumeFraction() {
        return intOf(K_VOLUME, 100) / 100f;
    }

    /* ------------------------------------------------------------------ */
    /* interface                                                          */
    /* ------------------------------------------------------------------ */

    public int environment() {
        switch (stringOf(K_ENVIRONMENT, "cinema")) {
            case "void":
                return 0;
            case "theater":
                return 2;
            case "space":
                return 3;
            case "ambient":
                return 4;
            case "world":
                return 5;
            default:
                return 1;
        }
    }

    public void setEnvironment(int environment) {
        String value;
        switch (environment) {
            case 0:
                value = "void";
                break;
            case 2:
                value = "theater";
                break;
            case 3:
                value = "space";
                break;
            case 4:
                value = "ambient";
                break;
            case 5:
                value = "world";
                break;
            default:
                value = "cinema";
                break;
        }
        prefs.edit().putString(K_ENVIRONMENT, value).apply();
    }

    public float screenSize() {
        return intOf(K_SCREEN_SIZE, 100) / 100f;
    }

    public void setScreenSize(float value) {
        prefs.edit().putInt(K_SCREEN_SIZE, Math.round(clamp(value, 0.4f, 2.2f) * 100f)).apply();
    }

    public float screenDistance() {
        return intOf(K_SCREEN_DISTANCE, 60) / 10f;
    }

    public void setScreenDistance(float metres) {
        prefs.edit().putInt(K_SCREEN_DISTANCE, Math.round(clamp(metres, 2.5f, 16f) * 10f)).apply();
    }

    public float curvature() {
        return intOf(K_CURVATURE, 25) / 100f;
    }

    public void setCurvature(float value) {
        prefs.edit().putInt(K_CURVATURE, Math.round(clamp(value, 0f, 1f) * 100f)).apply();
    }

    public float screenY() {
        return intOf(K_SCREEN_Y, 0) / 100f;
    }

    public void setScreenY(float metres) {
        prefs.edit().putInt(K_SCREEN_Y, Math.round(clamp(metres, -2.5f, 2.5f) * 100f)).apply();
    }

    public int dwellMs() {
        return intOf(K_DWELL_MS, 1300);
    }

    public int selectionMode() {
        return "touch".equals(stringOf(K_SELECTION_MODE, "gaze")) ? SELECT_TOUCH : SELECT_GAZE;
    }

    public void setSelectionMode(int mode) {
        prefs.edit().putString(K_SELECTION_MODE, mode == SELECT_TOUCH ? "touch" : "gaze").apply();
    }

    public float uiScale() {
        return intOf(K_UI_SCALE, 100) / 100f;
    }

    public boolean keepAwake() {
        return prefs.getBoolean(K_KEEP_AWAKE, true);
    }

    /* ------------------------------------------------------------------ */
    /* misc                                                               */
    /* ------------------------------------------------------------------ */

    public String lastVideoUri() {
        return stringOf(K_LAST_VIDEO, null);
    }

    public long lastVideoPosition() {
        return prefs.getLong(K_LAST_VIDEO_POS, 0L);
    }

    public void setLastVideo(String uri, long positionMs) {
        prefs.edit().putString(K_LAST_VIDEO, uri).putLong(K_LAST_VIDEO_POS, positionMs).apply();
    }

    public void clearResumePositions() {
        prefs.edit().remove(K_LAST_VIDEO).remove(K_LAST_VIDEO_POS).apply();
    }

    public static float clamp(float value, float min, float max) {
        return value < min ? min : (value > max ? max : value);
    }

    public static int clamp(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }

    public String summary() {
        return String.format(Locale.US, "%s | fov %.0f | lens %.0f | quality %d",
                viewerProfileId(), fovDeg(), lensSeparationMm(), quality());
    }
}
