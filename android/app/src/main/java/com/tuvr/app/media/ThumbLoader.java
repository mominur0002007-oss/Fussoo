package com.tuvr.app.media;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.util.Size;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Asynchronous video thumbnails and metadata with a small memory cache.
 *
 * Deliberately tiny: two worker threads, ~10 MB bitmap cache, downscaled
 * thumbnails. Nothing is written to disk, so there is no cache to clean up on
 * a 4 GB phone and no storage surprise for the user.
 */
public final class ThumbLoader {

    public interface Callback {
        void onThumb(String uri, Bitmap bitmap);
    }

    public interface MetaCallback {
        void onMeta(VideoItem item);
    }

    private static volatile ThumbLoader instance;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> cache;

    private ThumbLoader() {
        int maxKb = (int) (Runtime.getRuntime().maxMemory() / 1024L / 24L); // ~4% of heap
        cache = new LruCache<String, Bitmap>(Math.max(4 * 1024, maxKb)) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount() / 1024;
            }
        };
    }

    public static ThumbLoader get() {
        if (instance == null) {
            synchronized (ThumbLoader.class) {
                if (instance == null) {
                    instance = new ThumbLoader();
                }
            }
        }
        return instance;
    }

    private static String key(String uri, int width) {
        return width + "|" + uri;
    }

    public Bitmap cached(String uri, int width) {
        return cache.get(key(uri, width));
    }

    /** Loads (or returns cached) thumbnail asynchronously, callback on the main thread. */
    public void load(final Context context, final VideoItem item, final int width,
                     final Callback callback) {
        final Bitmap hit = cache.get(key(item.uri, width));
        if (hit != null && !hit.isRecycled()) {
            callback.onThumb(item.uri, hit);
            return;
        }
        final Context app = context.getApplicationContext();
        executor.execute(() -> {
            Bitmap bitmap = decodeThumb(app, item.uri, width);
            if (bitmap != null) {
                cache.put(key(item.uri, width), bitmap);
            }
            final Bitmap result = bitmap;
            main.post(() -> callback.onThumb(item.uri, result));
        });
    }

    /** Reads duration/resolution/size for items whose metadata is still unknown. */
    public void loadMetadata(final Context context, final VideoItem item,
                             final MetaCallback callback) {
        if (item.durationMs > 0 && item.width > 0) {
            callback.onMeta(item);
            return;
        }
        final Context app = context.getApplicationContext();
        executor.execute(() -> {
            fillMetadata(app, item);
            main.post(() -> callback.onMeta(item));
        });
    }

    public static void fillMetadata(Context context, VideoItem item) {
        if (item.durationMs > 0 && item.width > 0) {
            return;
        }
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, Uri.parse(item.uri));
            String duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            String width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
            String height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
            String rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
            if (duration != null) {
                item.durationMs = Long.parseLong(duration);
            }
            if (width != null) {
                item.width = Integer.parseInt(width);
            }
            if (height != null) {
                item.height = Integer.parseInt(height);
            }
            if (rotation != null && ("90".equals(rotation) || "270".equals(rotation))) {
                int t = item.width;
                item.width = item.height;
                item.height = t;
            }
        } catch (Exception ignored) {
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {
            }
        }
    }

    private static Bitmap decodeThumb(Context context, String uriString, int targetWidth) {
        Uri uri = Uri.parse(uriString);
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                return context.getContentResolver().loadThumbnail(uri,
                        new Size(targetWidth, Math.max(1, targetWidth * 9 / 16)), null);
            }
        } catch (Exception ignored) {
        }
        // Fallback (also used for SAF files on older releases)
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, uri);
            if (Build.VERSION.SDK_INT >= 27) {
                Bitmap frame = retriever.getScaledFrameAtTime(1_000_000L,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC, targetWidth,
                        Math.max(1, targetWidth * 9 / 16));
                if (frame != null) {
                    return frame;
                }
            }
            Bitmap frame = retriever.getFrameAtTime(1_000_000L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            if (frame != null) {
                return ThumbnailUtils.extractThumbnail(frame, targetWidth,
                        Math.max(1, targetWidth * 9 / 16));
            }
        } catch (Exception ignored) {
        } finally {
            try {
                retriever.release();
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    public void clear() {
        cache.evictAll();
    }

    public int cacheSizeKb() {
        return cache.size();
    }
}
