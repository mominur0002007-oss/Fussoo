package com.tuvr.app;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import com.tuvr.app.core.AppSettings;
import com.tuvr.app.core.CrashLog;
import com.tuvr.app.core.TuVrAppCtx;

/**
 * TU VR application entry point.
 *
 * Kept intentionally minimal: no background services, no analytics, no network
 * initialisation. The only work done here is loading preferences and installing
 * a small crash logger that writes to app-private storage.
 */
public class TuVrApp extends Application {

    private static Context appContext;

    @Override
    public void onCreate() {
        super.onCreate();
        appContext = getApplicationContext();
        TuVrAppCtx.set(this);
        CrashLog.install(this);
        AppSettings.init(this);
    }

    /**
     * Usable from code that has no Activity reference (renderer, loaders).
     * This is the application context, never an Activity context.
     */
    public static Context ctx() {
        return appContext;
    }

    public static SharedPreferences prefs() {
        return AppSettings.prefs();
    }
}
