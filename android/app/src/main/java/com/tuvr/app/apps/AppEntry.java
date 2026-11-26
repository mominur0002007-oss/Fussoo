package com.tuvr.app.apps;

import android.graphics.drawable.Drawable;

/** One launchable Android application. */
public final class AppEntry {

    public final String packageName;
    public final String label;
    public Drawable icon;
    public final boolean system;
    /** 0 = 2D only, 1 = opens media/browser content on the virtual screen. */
    public final int vrClass;

    public AppEntry(String packageName, String label, Drawable icon, boolean system, int vrClass) {
        this.packageName = packageName;
        this.label = label;
        this.icon = icon;
        this.system = system;
        this.vrClass = vrClass;
    }

    public String vrLabel() {
        return vrClass == 1 ? "Video / browser" : "Regular app";
    }
}
