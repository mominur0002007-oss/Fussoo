package com.fussoo.vr.core;

import java.util.Locale;

/** Human formatting used by both the phone UI and the in-headset HUD. */
public final class Formats {

    private Formats() {
    }

    /** 754000 -> "12:34". Negative or zero -> "--:--". */
    public static String duration(long ms) {
        if (ms <= 0) {
            return "--:--";
        }
        long s = ms / 1000L;
        long h = s / 3600L;
        long m = (s % 3600L) / 60L;
        long sec = s % 60L;
        if (h > 0) {
            return String.format(Locale.US, "%d:%02d:%02d", h, m, sec);
        }
        return String.format(Locale.US, "%d:%02d", m, sec);
    }

    /** 1523432 -> "1.5M views". */
    public static String views(long v) {
        if (v < 0) {
            return "";
        }
        if (v >= 1_000_000_000L) {
            return String.format(Locale.US, "%.1fB views", v / 1_000_000_000.0);
        }
        if (v >= 1_000_000L) {
            return String.format(Locale.US, "%.1fM views", v / 1_000_000.0);
        }
        if (v >= 1_000L) {
            return String.format(Locale.US, "%.0fK views", v / 1_000.0);
        }
        return v + " views";
    }

    public static String ms(long ms) {
        return duration(ms);
    }
}
