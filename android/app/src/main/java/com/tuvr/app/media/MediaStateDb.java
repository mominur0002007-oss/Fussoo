package com.tuvr.app.media;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Tiny SQLite store for favourites, play history and resume positions.
 * Kept separate from MediaStore so the app never needs write access to the
 * user's media library.
 */
public final class MediaStateDb extends SQLiteOpenHelper {

    private static final String DB_NAME = "tuvr_media.db";
    private static final int DB_VERSION = 1;
    private static final String TABLE = "media_state";

    private static volatile MediaStateDb instance;

    private MediaStateDb(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
    }

    public static MediaStateDb get(Context context) {
        if (instance == null) {
            synchronized (MediaStateDb.class) {
                if (instance == null) {
                    instance = new MediaStateDb(context);
                }
            }
        }
        return instance;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " ("
                + "uri TEXT PRIMARY KEY,"
                + "favorite INTEGER NOT NULL DEFAULT 0,"
                + "plays INTEGER NOT NULL DEFAULT 0,"
                + "last_played INTEGER NOT NULL DEFAULT 0,"
                + "position_ms INTEGER NOT NULL DEFAULT 0,"
                + "duration_ms INTEGER NOT NULL DEFAULT 0,"
                + "projection INTEGER NOT NULL DEFAULT -1,"
                + "stereo INTEGER NOT NULL DEFAULT -1"
                + ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // No migrations yet - the table is a cache, so recreate it.
        db.execSQL("DROP TABLE IF EXISTS " + TABLE);
        onCreate(db);
    }

    private ContentValues valuesFor(String uri) {
        ContentValues cv = new ContentValues();
        cv.put("uri", uri);
        return cv;
    }

    public void setFavorite(String uri, boolean favorite) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = valuesFor(uri);
        cv.put("favorite", favorite ? 1 : 0);
        int updated = db.update(TABLE, cv, "uri=?", new String[]{uri});
        if (updated == 0) {
            db.insertWithOnConflict(TABLE, null, cv, SQLiteDatabase.CONFLICT_IGNORE);
            db.update(TABLE, cv, "uri=?", new String[]{uri});
        }
    }

    public boolean isFavorite(String uri) {
        return queryFlag(uri, "favorite");
    }

    public int plays(String uri) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query(TABLE, new String[]{"plays"}, "uri=?", new String[]{uri},
                null, null, null)) {
            if (c.moveToFirst()) {
                return c.getInt(0);
            }
        }
        return 0;
    }

    public void recordPlay(String uri) {
        SQLiteDatabase db = getWritableDatabase();
        long now = System.currentTimeMillis();
        ContentValues insert = valuesFor(uri);
        insert.put("plays", 1);
        insert.put("last_played", now);
        long rowId = db.insertWithOnConflict(TABLE, null, insert, SQLiteDatabase.CONFLICT_IGNORE);
        if (rowId == -1L) {
            // Existing row: bump the play counter and timestamp without losing other columns.
            db.execSQL("UPDATE " + TABLE + " SET plays = plays + 1, last_played = ? WHERE uri = ?",
                    new Object[]{now, uri});
        }
    }

    public void savePosition(String uri, long positionMs, long durationMs,
                             int projection, int stereo) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = valuesFor(uri);
        cv.put("position_ms", positionMs);
        cv.put("duration_ms", durationMs);
        cv.put("projection", projection);
        cv.put("stereo", stereo);
        cv.put("last_played", System.currentTimeMillis());
        long rowId = db.insertWithOnConflict(TABLE, null, cv, SQLiteDatabase.CONFLICT_IGNORE);
        if (rowId == -1L) {
            cv.remove("uri");
            db.update(TABLE, cv, "uri=?", new String[]{uri});
        }
    }

    public long resumePosition(String uri) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query(TABLE, new String[]{"position_ms", "duration_ms"}, "uri=?",
                new String[]{uri}, null, null, null)) {
            if (c.moveToFirst()) {
                long position = c.getLong(0);
                long duration = c.getLong(1);
                // Forget almost-finished videos, they should start over.
                if (duration > 0 && position > duration - 15000L) {
                    return 0L;
                }
                return position;
            }
        }
        return 0L;
    }

    /** Returns {projection, stereo} for a video or null when nothing was stored. */
    public int[] storedFormat(String uri) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query(TABLE, new String[]{"projection", "stereo"}, "uri=?",
                new String[]{uri}, null, null, null)) {
            if (c.moveToFirst() && c.getInt(0) >= 0 && c.getInt(1) >= 0) {
                return new int[]{c.getInt(0), c.getInt(1)};
            }
        }
        return null;
    }

    /** Most recently played URIs. */
    public List<String> recentUris(int limit) {
        List<String> out = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query(TABLE, new String[]{"uri"}, "last_played>0",
                null, null, null, "last_played DESC", String.valueOf(limit))) {
            while (c.moveToNext()) {
                out.add(c.getString(0));
            }
        }
        return out;
    }

    public List<String> favoriteUris() {
        List<String> out = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query(TABLE, new String[]{"uri"}, "favorite=1",
                null, null, null, "last_played DESC")) {
            while (c.moveToNext()) {
                out.add(c.getString(0));
            }
        }
        return out;
    }

    public long resumeFor(String uri) {
        return resumePosition(uri);
    }

    public void clearResume() {
        getWritableDatabase().execSQL("UPDATE " + TABLE + " SET position_ms=0");
    }

    public void clearAll() {
        getWritableDatabase().execSQL("DELETE FROM " + TABLE);
    }

    private boolean queryFlag(String uri, String column) {
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query(TABLE, new String[]{column}, "uri=?", new String[]{uri},
                null, null, null)) {
            return c.moveToFirst() && c.getInt(0) == 1;
        }
    }
}
