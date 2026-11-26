package com.tuvr.app.core;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Two small worker threads shared by the whole app plus a main-thread poster.
 * Using a bounded pool keeps memory and thread count predictable on a 4 GB
 * device and avoids creating an executor per screen.
 */
public final class Background {

    private static final ExecutorService IO = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Background() {
    }

    public static void run(Runnable task) {
        IO.execute(task);
    }

    public static void main(Runnable task) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            task.run();
        } else {
            MAIN.post(task);
        }
    }

    public static void mainDelayed(Runnable task, long delayMs) {
        MAIN.postDelayed(task, delayMs);
    }
}
