package com.fussoo.vr.net;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Small thumbnail loader with a bounded in-memory cache. PosterView thumbnails are what
 * make the library feel real, so they get their own tiny pipeline instead of a library.
 */
public final class Thumbs {

    public interface Callback {
        void onBitmap(String key, Bitmap bitmap);

        void onFailed(String key);
    }

    private static final int MAX_CACHE = 48;

    private final ExecutorService pool = Executors.newFixedThreadPool(3);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final OkHttpClient client = new OkHttpClient();
    private final java.util.LinkedHashMap<String, Bitmap> cache =
            new java.util.LinkedHashMap<String, Bitmap>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(
                        java.util.Map.Entry<String, Bitmap> eldest) {
                    return size() > MAX_CACHE;
                }
            };

    public Bitmap peek(String key) {
        synchronized (cache) {
            return cache.get(key);
        }
    }

    public void load(final String key, final String url, final int maxDim,
                     final Callback callback) {
        if (key == null || url == null) {
            return;
        }
        Bitmap hit = peek(key);
        if (hit != null) {
            callback.onBitmap(key, hit);
            return;
        }
        pool.execute(new Runnable() {
            @Override
            public void run() {
                Bitmap bmp = null;
                try {
                    bmp = download(url, maxDim);
                } catch (Throwable ignored) {
                }
                final Bitmap result = bmp;
                if (result == null) {
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onFailed(key);
                        }
                    });
                    return;
                }
                synchronized (cache) {
                    cache.put(key, result);
                }
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onBitmap(key, result);
                    }
                });
            }
        });
    }

    private Bitmap download(String url, int maxDim) throws IOException {
        Request req = new Request.Builder()
                .url(url)
                .header("User-Agent", YtDownloader.USER_AGENT)
                .build();
        Response r = client.newCall(req).execute();
        try {
            ResponseBody body = r.body();
            if (body == null) {
                return null;
            }
            InputStream in = body.byteStream();
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            byte[] chunk = new byte[16 * 1024];
            int n;
            while ((n = in.read(chunk)) > 0) {
                buffer.write(chunk, 0, n);
            }
            byte[] data = buffer.toByteArray();
            BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
            int sample = 1;
            if (maxDim > 0 && bounds.outWidth > 0) {
                while (bounds.outWidth / (sample * 2) >= maxDim) {
                    sample *= 2;
                }
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
            return BitmapFactory.decodeByteArray(data, 0, data.length, opts);
        } finally {
            r.close();
        }
    }

    public void shutdown() {
        pool.shutdownNow();
    }

    @SuppressWarnings("unused")
    private static HttpURLConnection unusedProto() throws IOException {
        return (HttpURLConnection) new URL("https://example.com").openConnection();
    }
}
