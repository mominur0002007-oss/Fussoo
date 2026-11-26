package com.tuvr.app.core;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Deque;
import java.util.Locale;

/**
 * A small in-memory log of recoverable failures.
 *
 * Nothing here is uploaded anywhere: it exists so that a screen which fails on a
 * phone the developer cannot hold still leaves a readable trace, which the
 * Diagnostics screen shows and can share. Fatal crashes still go to
 * {@link CrashLog}.
 */
public final class Problems {

    private static final int MAX_ENTRIES = 30;
    private static final Deque<String> ENTRIES = new ArrayDeque<>();
    private static int total;

    private Problems() {
    }

    public static synchronized void record(String tag, Throwable throwable) {
        total++;
        StringBuilder sb = new StringBuilder();
        sb.append(new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date()));
        sb.append(' ').append(tag).append(": ");
        if (throwable == null) {
            sb.append("unknown failure");
        } else {
            sb.append(throwable.getClass().getSimpleName());
            if (throwable.getMessage() != null) {
                sb.append(": ").append(throwable.getMessage());
            }
            StackTraceElement[] trace = throwable.getStackTrace();
            for (int i = 0; i < Math.min(4, trace.length); i++) {
                sb.append("\n    at ").append(trace[i]);
            }
        }
        ENTRIES.addLast(sb.toString());
        while (ENTRIES.size() > MAX_ENTRIES) {
            ENTRIES.removeFirst();
        }
    }

    public static synchronized void record(String tag, String message) {
        total++;
        ENTRIES.addLast(new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date())
                + ' ' + tag + ": " + message);
        while (ENTRIES.size() > MAX_ENTRIES) {
            ENTRIES.removeFirst();
        }
    }

    public static synchronized int count() {
        return total;
    }

    public static synchronized String report() {
        if (ENTRIES.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(total).append(" recoverable problem(s) recorded\n\n");
        for (String entry : ENTRIES) {
            sb.append(entry).append("\n\n");
        }
        return sb.toString();
    }

    public static synchronized void clear() {
        ENTRIES.clear();
        total = 0;
    }
}
