package com.fussoo.vr.core;

import android.app.Activity;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

/** One-time process setup shared by every activity. */
public final class Boot {

    private static boolean done;

    private Boot() {
    }

    public static void once(Context ctx) {
        if (done) {
            return;
        }
        done = true;
        try {
            com.fussoo.vr.net.Yt.init();
        } catch (Throwable ignored) {
            // The extractor initialises lazily as well; never block app start on it.
        }
    }

    public static boolean online(Context ctx) {
        try {
            ConnectivityManager cm =
                    (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) {
                return true;
            }
            Network n = cm.getActiveNetwork();
            if (n == null) {
                return false;
            }
            NetworkCapabilities c = cm.getNetworkCapabilities(n);
            return c != null && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        } catch (Exception e) {
            return true;
        }
    }

    public static void immersive(Activity a) {
        try {
            a.getWindow().getDecorView().setSystemUiVisibility(
                    android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                            | android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        } catch (Exception ignored) {
        }
    }
}
