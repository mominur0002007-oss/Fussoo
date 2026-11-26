package com.tuvr.app.core;

import java.util.Locale;

/** Small formatting helpers used across the phone and VR UI. */
public final class Formats {

    private Formats() {
    }

    public static String duration(long ms) {
        if (ms <= 0) {
            return "--:--";
        }
        long totalSeconds = ms / 1000L;
        long seconds = totalSeconds % 60L;
        long minutes = (totalSeconds / 60L) % 60L;
        long hours = totalSeconds / 3600L;
        if (hours > 0) {
            return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.US, "%d:%02d", minutes, seconds);
    }

    public static String size(long bytes) {
        if (bytes <= 0) {
            return "";
        }
        double mb = bytes / (1024.0 * 1024.0);
        if (mb >= 1024.0) {
            return String.format(Locale.US, "%.1f GB", mb / 1024.0);
        }
        if (mb >= 1.0) {
            return String.format(Locale.US, "%.1f MB", mb);
        }
        return String.format(Locale.US, "%d KB", Math.max(1, (int) (bytes / 1024L)));
    }

    public static String resolution(int width, int height) {
        if (width <= 0 || height <= 0) {
            return "?";
        }
        return String.format(Locale.US, "%d × %d", width, height);
    }

    public static String mb(long bytes) {
        return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
    }

    public static String percent(float fraction) {
        return String.format(Locale.US, "%d%%", Math.round(fraction * 100f));
    }

    public static String oneDecimal(float value) {
        return String.format(Locale.US, "%.1f", value);
    }

    /** Best-effort display name from a content:// or file:// uri. */
    public static String displayNameFromUri(String uriString) {
        if (uriString == null) {
            return "";
        }
        String value = uriString;
        int query = value.indexOf('?');
        if (query > 0) {
            value = value.substring(0, query);
        }
        int slash = value.lastIndexOf('/');
        if (slash >= 0 && slash < value.length() - 1) {
            value = value.substring(slash + 1);
        }
        try {
            return java.net.URLDecoder.decode(value, "UTF-8");
        } catch (Exception e) {
            return value;
        }
    }
}
