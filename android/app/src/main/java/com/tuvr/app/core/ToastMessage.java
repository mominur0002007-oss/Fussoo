package com.tuvr.app.core;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

/** Tiny wrapper so short confirmations never pile up. */
public final class ToastMessage {

    private ToastMessage() {
    }

    public static void show(Context context, String message) {
        if (context == null) {
            return;
        }
        Context app = context.getApplicationContext();
        new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(app, message, Toast.LENGTH_SHORT).show());
    }
}
