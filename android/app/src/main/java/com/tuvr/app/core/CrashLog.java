package com.tuvr.app.core;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Minimal crash logger. Writes the last crash to app-private storage so the user
 * can copy it from the Diagnostics screen. No data ever leaves the device.
 */
public final class CrashLog {

    private static final String FILE_NAME = "last_crash.txt";
    private static volatile boolean installed;
    private static File crashFile;

    private CrashLog() {
    }

    public static void install(Context context) {
        if (installed) {
            return;
        }
        installed = true;
        crashFile = new File(context.getFilesDir(), FILE_NAME);
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                write(throwable);
            } catch (Throwable ignored) {
                // never mask the original crash
            }
            if (previous != null) {
                previous.uncaughtException(thread, throwable);
            }
        });
    }

    private static void write(Throwable throwable) {
        if (crashFile == null) {
            return;
        }
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        String stamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
        pw.println("TU VR crash log");
        pw.println("Time: " + stamp);
        pw.println("Thread: " + Thread.currentThread().getName());
        pw.println();
        throwable.printStackTrace(pw);
        pw.flush();
        try (FileWriter fw = new FileWriter(crashFile, false)) {
            fw.write(sw.toString());
        } catch (IOException ignored) {
        }
    }

    /** Returns the last crash text, or null when there is none. */
    public static String read() {
        if (crashFile == null || !crashFile.exists()) {
            return null;
        }
        // java.nio.file is API 26+, and the app supports Android 7, so read the
        // file with plain streams.
        java.io.FileInputStream in = null;
        try {
            in = new java.io.FileInputStream(crashFile);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
            return new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    public static void clear() {
        if (crashFile != null && crashFile.exists()) {
            //noinspection ResultOfMethodCallIgnored
            crashFile.delete();
        }
    }
}
