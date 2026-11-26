package com.tuvr.app.core;

import android.content.Context;

/** Holder for the application context, set once from TuVrApp. */
public final class TuVrAppCtx {

    private static Context appContext;

    private TuVrAppCtx() {
    }

    public static void set(Context context) {
        appContext = context == null ? null : context.getApplicationContext();
    }

    public static Context get() {
        return appContext;
    }
}
