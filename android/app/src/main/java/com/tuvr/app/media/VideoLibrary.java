package com.tuvr.app.media;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.provider.OpenableColumns;

import androidx.core.content.ContextCompat;

import com.tuvr.app.core.Formats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Read-only access to videos on the device.
 *
 * Two sources are supported:
 *  1. MediaStore (needs READ_MEDIA_VIDEO on Android 13+, READ_EXTERNAL_STORAGE before),
 *  2. Storage Access Framework picks, whose read grants are persisted and stored as
 *     a small string set - files are never copied into the app.
 */
public final class VideoLibrary {

    private static final String PREF_SAF = "saf_video_uris";
    private static final int MAX_ITEMS = 4000;

    private VideoLibrary() {
    }

    public static String requiredPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            return Manifest.permission.READ_MEDIA_VIDEO;
        }
        return Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    /**
     * Permissions to request. On Android 14+ the "select photos and videos" dialog
     * also needs {@code READ_MEDIA_VISUAL_USER_SELECTED}; the app then simply shows
     * whatever the user allowed and can re-ask on the next launch.
     */
    public static String[] requiredPermissions() {
        if (Build.VERSION.SDK_INT >= 34) {
            return new String[]{
                    Manifest.permission.READ_MEDIA_VIDEO,
                    "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"};
        }
        return new String[]{requiredPermission()};
    }

    public static boolean hasPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, requiredPermission())
                == PackageManager.PERMISSION_GRANTED;
    }

    /* ------------------------------------------------------------------ */
    /* MediaStore                                                          */
    /* ------------------------------------------------------------------ */

    private static String[] projection() {
        List<String> cols = new ArrayList<>();
        cols.add(MediaStore.Video.Media._ID);
        cols.add(MediaStore.Video.Media.DISPLAY_NAME);
        cols.add(MediaStore.Video.Media.DURATION);
        cols.add(MediaStore.Video.Media.SIZE);
        cols.add(MediaStore.Video.Media.WIDTH);
        cols.add(MediaStore.Video.Media.HEIGHT);
        cols.add(MediaStore.Video.Media.DATE_ADDED);
        if (Build.VERSION.SDK_INT >= 29) {
            cols.add(MediaStore.Video.Media.RELATIVE_PATH);
            cols.add(MediaStore.Video.Media.BUCKET_DISPLAY_NAME);
        } else {
            cols.add(MediaStore.Video.Media.DATA);
        }
        return cols.toArray(new String[0]);
    }

    public static List<VideoItem> all(Context context) {
        List<VideoItem> items = new ArrayList<>();
        if (!hasPermission(context)) {
            items.addAll(safItems(context));
            applyState(context, items);
            return items;
        }
        ContentResolver cr = context.getContentResolver();
        Uri collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        try (Cursor c = cr.query(collection, projection(), null, null,
                MediaStore.Video.Media.DATE_ADDED + " DESC")) {
            if (c != null) {
                int iId = c.getColumnIndex(MediaStore.Video.Media._ID);
                int iName = c.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME);
                int iDur = c.getColumnIndex(MediaStore.Video.Media.DURATION);
                int iSize = c.getColumnIndex(MediaStore.Video.Media.SIZE);
                int iW = c.getColumnIndex(MediaStore.Video.Media.WIDTH);
                int iH = c.getColumnIndex(MediaStore.Video.Media.HEIGHT);
                int iDate = c.getColumnIndex(MediaStore.Video.Media.DATE_ADDED);
                int iPath = Build.VERSION.SDK_INT >= 29
                        ? c.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH)
                        : c.getColumnIndex(MediaStore.Video.Media.DATA);
                int iBucket = Build.VERSION.SDK_INT >= 29
                        ? c.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME) : -1;
                while (c.moveToNext() && items.size() < MAX_ITEMS) {
                    long id = c.getLong(iId);
                    Uri uri = ContentUris.withAppendedId(collection, id);
                    VideoItem item = new VideoItem(uri.toString(), c.getString(iName));
                    item.durationMs = iDur >= 0 ? c.getLong(iDur) : 0L;
                    item.sizeBytes = iSize >= 0 ? c.getLong(iSize) : 0L;
                    item.width = iW >= 0 ? c.getInt(iW) : 0;
                    item.height = iH >= 0 ? c.getInt(iH) : 0;
                    item.dateAdded = iDate >= 0 ? c.getLong(iDate) : 0L;
                    String folder = null;
                    if (iBucket >= 0) {
                        folder = c.getString(iBucket);
                    }
                    if (folder == null && iPath >= 0) {
                        String path = c.getString(iPath);
                        folder = folderFromPath(path);
                    }
                    item.folderName = folder == null ? "Device" : folder;
                    item.folderId = item.folderName;
                    items.add(item);
                }
            }
        } catch (Exception ignored) {
            // Some vendor ROMs expose a broken media database - the SAF source still works.
        }
        items.addAll(safItems(context));
        applyState(context, items);
        return items;
    }

    private static String folderFromPath(String path) {
        if (path == null || path.isEmpty()) {
            return "Device";
        }
        String p = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        int slash = p.lastIndexOf('/');
        if (slash <= 0) {
            return "Device";
        }
        String trimmed = p.substring(0, slash);
        int prev = trimmed.lastIndexOf('/');
        return prev >= 0 ? trimmed.substring(prev + 1) : trimmed;
    }

    public static List<VideoItem> inFolder(Context context, String folderName) {
        List<VideoItem> result = new ArrayList<>();
        for (VideoItem item : all(context)) {
            if (folderName.equals(item.folderName)) {
                result.add(item);
            }
        }
        return result;
    }

    /** Folder name -> number of videos, ordered by most videos first. */
    public static Map<String, Integer> folders(Context context) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (VideoItem item : all(context)) {
            String key = item.folderName == null ? "Device" : item.folderName;
            Integer count = counts.get(key);
            counts.put(key, count == null ? 1 : count + 1);
        }
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(counts.entrySet());
        Collections.sort(entries, (a, b) -> b.getValue().compareTo(a.getValue()));
        Map<String, Integer> sorted = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : entries) {
            sorted.put(e.getKey(), e.getValue());
        }
        return sorted;
    }

    public static void applyState(Context context, List<VideoItem> items) {
        MediaStateDb db = MediaStateDb.get(context);
        for (VideoItem item : items) {
            item.favorite = db.isFavorite(item.uri);
            item.positionMs = db.resumePosition(item.uri);
        }
    }

    public static VideoItem find(Context context, String uriString) {
        if (uriString == null) {
            return null;
        }
        for (VideoItem item : all(context)) {
            if (item.uri.equals(uriString)) {
                return item;
            }
        }
        // Fall back to a query for that single uri (for example after a share intent).
        VideoItem item = querySingle(context, Uri.parse(uriString));
        if (item != null) {
            MediaStateDb db = MediaStateDb.get(context);
            item.favorite = db.isFavorite(item.uri);
            item.positionMs = db.resumePosition(item.uri);
        }
        return item;
    }

    public static VideoItem querySingle(Context context, Uri uri) {
        ContentResolver cr = context.getContentResolver();
        VideoItem item = new VideoItem(uri.toString(), Formats.displayNameFromUri(uri.toString()));
        item.fromSaf = "content".equals(uri.getScheme()) && !isMediaStore(uri);
        String[] cols = {
                OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE,
                MediaStore.Video.Media.DURATION, MediaStore.Video.Media.WIDTH,
                MediaStore.Video.Media.HEIGHT
        };
        try (Cursor c = cr.query(uri, cols, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int iName = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int iSize = c.getColumnIndex(OpenableColumns.SIZE);
                int iDur = c.getColumnIndex(MediaStore.Video.Media.DURATION);
                int iW = c.getColumnIndex(MediaStore.Video.Media.WIDTH);
                int iH = c.getColumnIndex(MediaStore.Video.Media.HEIGHT);
                if (iName >= 0) {
                    item.name = c.getString(iName);
                }
                if (iSize >= 0) {
                    item.sizeBytes = c.getLong(iSize);
                }
                if (iDur >= 0) {
                    item.durationMs = c.getLong(iDur);
                }
                if (iW >= 0) {
                    item.width = c.getInt(iW);
                }
                if (iH >= 0) {
                    item.height = c.getInt(iH);
                }
            }
        } catch (Exception ignored) {
        }
        if (item.name == null || item.name.isEmpty()) {
            item.name = "Video";
        }
        item.folderName = "Opened file";
        item.folderId = item.folderName;
        return item;
    }

    private static boolean isMediaStore(Uri uri) {
        try {
            return "media".equals(uri.getAuthority());
        } catch (Exception e) {
            return false;
        }
    }

    /* ------------------------------------------------------------------ */
    /* Storage Access Framework picks                                      */
    /* ------------------------------------------------------------------ */

    public static void addSafUri(Context context, Uri uri, int takeFlags) {
        try {
            context.getContentResolver().takePersistableUriPermission(uri, takeFlags);
        } catch (Exception ignored) {
            // Some providers do not offer persistable grants; the file still works
            // for the current session.
        }
        Set<String> uris = new HashSet<>(safUriSet(context));
        uris.add(uri.toString());
        context.getSharedPreferences("tuvr_settings", Context.MODE_PRIVATE)
                .edit().putStringSet(PREF_SAF, uris).apply();
    }

    public static void removeSafUri(Context context, String uriString) {
        Set<String> uris = new HashSet<>(safUriSet(context));
        uris.remove(uriString);
        context.getSharedPreferences("tuvr_settings", Context.MODE_PRIVATE)
                .edit().putStringSet(PREF_SAF, uris).apply();
    }

    private static Set<String> safUriSet(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("tuvr_settings", Context.MODE_PRIVATE);
        Set<String> set = prefs.getStringSet(PREF_SAF, null);
        return set == null ? Collections.<String>emptySet() : set;
    }

    public static List<VideoItem> safItems(Context context) {
        List<VideoItem> items = new ArrayList<>();
        for (String uriString : safUriSet(context)) {
            VideoItem item = new VideoItem(uriString, Formats.displayNameFromUri(uriString));
            item.fromSaf = true;
            item.folderName = "Picked files";
            item.folderId = item.folderName;
            items.add(item);
        }
        return items;
    }

    /* ------------------------------------------------------------------ */
    /* Filtering / sorting                                                 */
    /* ------------------------------------------------------------------ */

    public static final int SORT_DATE = 0;
    public static final int SORT_NAME = 1;
    public static final int SORT_DURATION = 2;
    public static final int SORT_SIZE = 3;

    public static List<VideoItem> filterAndSort(List<VideoItem> source, String query,
                                                int sort, boolean ascending,
                                                boolean favoritesOnly) {
        List<VideoItem> out = new ArrayList<>();
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.US);
        for (VideoItem item : source) {
            if (favoritesOnly && !item.favorite) {
                continue;
            }
            if (!needle.isEmpty()) {
                String name = item.name == null ? "" : item.name.toLowerCase(Locale.US);
                String folder = item.folderName == null ? "" : item.folderName.toLowerCase(Locale.US);
                if (!name.contains(needle) && !folder.contains(needle)) {
                    continue;
                }
            }
            out.add(item);
        }
        Collections.sort(out, (a, b) -> {
            int cmp;
            switch (sort) {
                case SORT_NAME:
                    cmp = safe(a.name).compareToIgnoreCase(safe(b.name));
                    break;
                case SORT_DURATION:
                    cmp = Long.compare(a.durationMs, b.durationMs);
                    break;
                case SORT_SIZE:
                    cmp = Long.compare(a.sizeBytes, b.sizeBytes);
                    break;
                default:
                    cmp = Long.compare(a.dateAdded, b.dateAdded);
                    break;
            }
            return ascending ? cmp : -cmp;
        });
        return out;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
