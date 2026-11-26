package com.tuvr.app.browser;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Local bookmark store (no sync, no account). */
public final class Bookmarks {

    public static final class Entry {
        public final String title;
        public final String url;

        public Entry(String title, String url) {
            this.title = title;
            this.url = url;
        }
    }

    private static final String KEY = "bookmarks_json";

    private Bookmarks() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("tuvr_settings", Context.MODE_PRIVATE);
    }

    public static List<Entry> list(Context context) {
        List<Entry> out = new ArrayList<>();
        String raw = prefs(context).getString(KEY, null);
        if (raw == null) {
            return out;
        }
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                out.add(new Entry(obj.optString("title"), obj.optString("url")));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public static void add(Context context, String title, String url) {
        List<Entry> entries = list(context);
        for (Entry entry : entries) {
            if (entry.url.equals(url)) {
                return;
            }
        }
        entries.add(0, new Entry(title, url));
        persist(context, entries);
    }

    public static void remove(Context context, String url) {
        List<Entry> entries = list(context);
        List<Entry> kept = new ArrayList<>();
        for (Entry entry : entries) {
            if (!entry.url.equals(url)) {
                kept.add(entry);
            }
        }
        persist(context, kept);
    }

    private static void persist(Context context, List<Entry> entries) {
        JSONArray array = new JSONArray();
        for (Entry entry : entries) {
            JSONObject obj = new JSONObject();
            try {
                obj.put("title", entry.title);
                obj.put("url", entry.url);
            } catch (Exception ignored) {
            }
            array.put(obj);
        }
        prefs(context).edit().putString(KEY, array.toString()).apply();
    }
}
