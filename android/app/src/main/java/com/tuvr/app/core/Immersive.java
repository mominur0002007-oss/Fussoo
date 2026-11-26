package com.tuvr.app.core;

import android.app.Activity;
import android.provider.Settings;
import android.view.WindowManager;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/** Helpers for full-screen immersive VR activities and screen brightness. */
public final class Immersive {

    private Immersive() {
    }

    public static void enter(Activity activity) {
        WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(
                activity.getWindow(), activity.getWindow().getDecorView());
        if (controller != null) {
            controller.hide(WindowInsetsCompat.Type.systemBars());
            controller.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
    }

    public static void setBrightness(Activity activity, float fraction) {
        WindowManager.LayoutParams params = activity.getWindow().getAttributes();
        params.screenBrightness = Math.max(0.01f, Math.min(1f, fraction));
        activity.getWindow().setAttributes(params);
    }

    /** System brightness as 0..1 (read only, no permission needed). */
    public static float systemBrightness(Activity activity) {
        try {
            int value = Settings.System.getInt(activity.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS);
            return value / 255f;
        } catch (Exception e) {
            return 0.5f;
        }
    }
}
