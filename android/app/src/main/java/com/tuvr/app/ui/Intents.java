package com.tuvr.app.ui;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.tuvr.app.media.VideoItem;

/** Central place for the intents used to open media screens. */
public final class Intents {

    public static final String EXTRA_URI = "extra_uri";
    public static final String EXTRA_NAME = "extra_name";
    public static final String EXTRA_MODE = "extra_mode";
    public static final String EXTRA_FOLDER = "extra_folder";
    public static final String EXTRA_FAVORITES = "extra_favorites";
    public static final String EXTRA_AUTOPLAY = "extra_autoplay";
    public static final String EXTRA_URL = "extra_url";
    public static final String EXTRA_POSITION = "extra_position";
    public static final String EXTRA_PROJECTION = "extra_projection";
    public static final String EXTRA_STEREO = "extra_stereo";

    public static final String GRID_MODE_MEDIA = "media";
    public static final String GRID_MODE_APPS = "apps";

    private Intents() {
    }

    public static Intent player(Context context, VideoItem item) {
        Intent intent = new Intent(context, PlayerActivity.class);
        intent.putExtra(EXTRA_URI, item.uri);
        intent.putExtra(EXTRA_NAME, item.name);
        return intent;
    }

    public static Intent gallery(Context context) {
        return new Intent(context, GalleryActivity.class);
    }

    public static Intent browser(Context context, String url) {
        Intent intent = new Intent(context, BrowserActivity.class);
        if (url != null) {
            intent.putExtra(EXTRA_URL, url);
        }
        return intent;
    }

    public static Intent apps(Context context) {
        return new Intent(context, AppsActivity.class);
    }

    public static Intent diagnostics(Context context) {
        return new Intent(context, DiagnosticsActivity.class);
    }

    public static Intent calibration(Context context) {
        return new Intent(context, CalibrationActivity.class);
    }

    public static Intent settings(Context context) {
        return new Intent(context, SettingsActivity.class);
    }

    /** Opens an activity with ACTION_VIEW for a package name. */
    public static Intent launchApp(String packageName) {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setPackage(packageName);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        return intent;
    }

    public static Uri uri(String value) {
        return Uri.parse(value);
    }
}
