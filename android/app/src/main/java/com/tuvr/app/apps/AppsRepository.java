package com.tuvr.app.apps;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Reads the list of launchable apps from PackageManager.
 *
 * The app declares a narrow <queries> intent filter in the manifest instead of
 * requesting QUERY_ALL_PACKAGES, so only launcher activities are visible.
 */
public final class AppsRepository {

    private static final String PREF = "tuvr_settings";
    private static final String KEY_FAVORITES = "favorite_apps";
    private static final String KEY_RECENT = "recent_apps";

    private AppsRepository() {
    }

    public static List<AppEntry> load(Context context) {
        PackageManager pm = context.getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN);
        main.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolved = pm.queryIntentActivities(main, 0);
        List<AppEntry> apps = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String self = context.getPackageName();
        for (ResolveInfo info : resolved) {
            if (info.activityInfo == null) {
                continue;
            }
            String pkg = info.activityInfo.packageName;
            if (self.equals(pkg) || !seen.add(pkg)) {
                continue;
            }
            CharSequence label = info.loadLabel(pm);
            Drawable icon = info.loadIcon(pm);
            boolean system = (info.activityInfo.applicationInfo.flags
                    & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0;
            apps.add(new AppEntry(pkg, label == null ? pkg : label.toString(),
                    icon, system, classify(pkg, label)));
        }
        Collections.sort(apps, (a, b) -> a.label.compareToIgnoreCase(b.label));
        return apps;
    }

    /**
     * Very rough classification used only for a label in the launcher list.
     * The app never claims that a normal Android app becomes a VR app.
     */
    private static int classify(String pkg, CharSequence label) {
        String haystack = (pkg + " " + (label == null ? "" : label)).toLowerCase(Locale.US);
        String[] markers = {"video", "player", "vlc", "mxtech", "browser", "chrome", "youtube",
                "netflix", "prime", "movie"};
        for (String marker : markers) {
            if (haystack.contains(marker)) {
                return 1;
            }
        }
        return 0;
    }

    public static Set<String> favorites(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        Set<String> set = prefs.getStringSet(KEY_FAVORITES, null);
        return set == null ? new HashSet<>() : new HashSet<>(set);
    }

    public static void toggleFavorite(Context context, String pkg) {
        SharedPreferences prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        Set<String> favorites = new HashSet<>(prefs.getStringSet(KEY_FAVORITES,
                Collections.<String>emptySet()));
        if (!favorites.remove(pkg)) {
            favorites.add(pkg);
        }
        prefs.edit().putStringSet(KEY_FAVORITES, favorites).apply();
    }

    public static boolean isFavorite(Context context, String pkg) {
        return favorites(context).contains(pkg);
    }

    public static List<String> recents(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String raw = prefs.getString(KEY_RECENT, "");
        List<String> out = new ArrayList<>();
        if (raw != null && !raw.isEmpty()) {
            for (String part : raw.split("\\|")) {
                if (!part.isEmpty()) {
                    out.add(part);
                }
            }
        }
        return out;
    }

    public static void recordLaunch(Context context, String pkg) {
        List<String> recents = recents(context);
        recents.remove(pkg);
        recents.add(0, pkg);
        while (recents.size() > 12) {
            recents.remove(recents.size() - 1);
        }
        StringBuilder sb = new StringBuilder();
        for (String p : recents) {
            if (sb.length() > 0) {
                sb.append('|');
            }
            sb.append(p);
        }
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit().putString(KEY_RECENT, sb.toString()).apply();
    }

    /** Launches an app; returns false when nothing could be started. */
    public static boolean launch(Context context, String pkg) {
        try {
            Intent intent = context.getPackageManager().getLaunchIntentForPackage(pkg);
            if (intent == null) {
                return false;
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            recordLaunch(context, pkg);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
