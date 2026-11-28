package com.fussoo.vr.core;

import android.content.Context;
import android.content.SharedPreferences;

/** Every tunable value, in exactly one place. All values are plain SharedPreferences. */
public final class VrPrefs {

    public static final String FILE = "fussoo_vr";

    private final SharedPreferences p;

    public VrPrefs(Context ctx) {
        p = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    // ------------------------------------------------------------- theatre

    public float screenWidth() {
        return p.getFloat("screen_w", 11.0f);
    }

    public void setScreenWidth(float v) {
        p.edit().putFloat("screen_w", v).apply();
    }

    public float screenDistance() {
        return p.getFloat("screen_d", 4.2f);
    }

    public void setScreenDistance(float v) {
        p.edit().putFloat("screen_d", v).apply();
    }

    public float screenHeight() {
        return p.getFloat("screen_y", 2.35f);
    }

    public void setScreenHeight(float v) {
        p.edit().putFloat("screen_y", v).apply();
    }

    public float curve() {
        return p.getFloat("curve", 0.72f);
    }

    public void setCurve(float v) {
        p.edit().putFloat("curve", v).apply();
    }

    public int env() {
        return p.getInt("env", 0);
    }

    public void setEnv(int v) {
        p.edit().putInt("env", v).apply();
    }

    public int quality() {
        return p.getInt("quality", 1);
    }

    public void setQuality(int v) {
        p.edit().putInt("quality", v).apply();
    }

    public float dimmer() {
        return p.getFloat("dimmer", 0.10f);
    }

    public void setDimmer(float v) {
        p.edit().putFloat("dimmer", v).apply();
    }

    public float brighten() {
        return p.getFloat("brighten", 1.0f);
    }

    public void setBrighten(float v) {
        p.edit().putFloat("brighten", v).apply();
    }

    // --------------------------------------------------------------- optics

    public float lensSeparation() {
        return p.getFloat("lens_sep", 0.063f);
    }

    public void setLensSeparation(float v) {
        p.edit().putFloat("lens_sep", v).apply();
    }

    public float fov() {
        return p.getFloat("fov", 96f);
    }

    public void setFov(float v) {
        p.edit().putFloat("fov", v).apply();
    }

    public float barrel() {
        return p.getFloat("barrel", 0.12f);
    }

    public void setBarrel(float v) {
        p.edit().putFloat("barrel", v).apply();
    }

    public boolean fisheye() {
        return p.getBoolean("fisheye", true);
    }

    public void setFisheye(boolean v) {
        p.edit().putBoolean("fisheye", v).apply();
    }

    public float eyeShift() {
        return p.getFloat("eye_shift", 0f);
    }

    public void setEyeShift(float v) {
        p.edit().putFloat("eye_shift", v).apply();
    }

    // ---------------------------------------------------------- behaviour

    public int dwellMs() {
        return p.getInt("dwell", 1700);
    }

    public void setDwellMs(int v) {
        p.edit().putInt("dwell", v).apply();
    }

    public boolean gaze() {
        return p.getBoolean("gaze", true);
    }

    public void setGaze(boolean v) {
        p.edit().putBoolean("gaze", v).apply();
    }

    public boolean haptics() {
        return p.getBoolean("haptics", true);
    }

    public void setHaptics(boolean v) {
        p.edit().putBoolean("haptics", v).apply();
    }

    public boolean tapToPause() {
        return p.getBoolean("tap_pause", true);
    }

    public void setTapToPause(boolean v) {
        p.edit().putBoolean("tap_pause", v).apply();
    }

    public boolean keepAwake() {
        return p.getBoolean("keep_awake", true);
    }

    public void setKeepAwake(boolean v) {
        p.edit().putBoolean("keep_awake", v).apply();
    }

    public int seekStepMs() {
        return p.getInt("seek_step", 10_000);
    }

    public void setSeekStepMs(int v) {
        p.edit().putInt("seek_step", v).apply();
    }

    public boolean showVrButton() {
        return p.getBoolean("vr_button", true);
    }

    public int maxHeight() {
        return p.getInt("max_height", 720);
    }

    public void setMaxHeight(int v) {
        p.edit().putInt("max_height", v).apply();
    }

    public float volume() {
        return p.getFloat("volume", 0.85f);
    }

    public void setVolume(float v) {
        p.edit().putFloat("volume", v).apply();
    }

    public String lastVideo() {
        return p.getString("last_video", null);
    }

    public void setLastVideo(String id) {
        p.edit().putString("last_video", id).apply();
    }

    public void resetOptics() {
        p.edit()
                .remove("lens_sep")
                .remove("fov")
                .remove("barrel")
                .remove("eye_shift")
                .remove("screen_w")
                .remove("screen_d")
                .remove("screen_y")
                .remove("curve")
                .apply();
    }
}
