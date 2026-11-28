package com.fussoo.vr.core;

import android.graphics.Bitmap;

import java.util.ArrayList;
import java.util.List;

/**
 * The single source of truth for what the virtual world shows.
 *
 * <p>Written by {@code WorldController} on the UI thread, read by the renderer on the GL
 * thread. Deliberately made of plain fields and immutable lists: no locks are held while
 * drawing, and a torn read is impossible because the renderer snapshots under a short
 * synchronized block.</p>
 */
public final class WorldModel {

    /** Which screen the user is looking at. */
    public enum Screen {
        HOME,
        BROWSE,
        PLAYER,
        SETTINGS
    }

    /** One selectable thing in the world: a poster, a button, a settings row. */
    public static final class Tile {
        public final String key;
        public final String title;
        public final String subtitle;
        public final String badge;
        public Bitmap poster;
        public final int icon;        // 0 = none, else a glyph code from WorldController
        public final boolean primary;

        public Tile(String key, String title, String subtitle, String badge, Bitmap poster,
                    int icon, boolean primary) {
            this.key = key;
            this.title = title == null ? "" : title;
            this.subtitle = subtitle == null ? "" : subtitle;
            this.badge = badge == null ? "" : badge;
            this.poster = poster;
            this.icon = icon;
            this.primary = primary;
        }
    }

    /** A settings line rendered as a panel row with a value you can step. */
    public static final class Setting {
        public final String key;
        public final String label;
        public final String value;
        public final boolean toggle;
        public final boolean on;

        public Setting(String key, String label, String value, boolean toggle, boolean on) {
            this.key = key;
            this.label = label == null ? "" : label;
            this.value = value == null ? "" : value;
            this.toggle = toggle;
            this.on = on;
        }
    }

    // ---------------------------------------------------------- navigation

    public Screen screen = Screen.HOME;
    public String shelfTitle = "Trending now";
    public String query = "";
    public final List<Tile> tiles = new ArrayList<>();
    public final List<Setting> settings = new ArrayList<>();
    public Tile hero;                        // large featured card on HOME
    public boolean loading;
    public String statusLine = "";
    public String errorLine = "";

    // ------------------------------------------------------------ playback

    public boolean hasVideo;
    public boolean playing;
    public boolean buffering;
    public String videoTitle = "";
    public String videoUploader = "";
    public String qualityText = "";
    public long positionMs;
    public long durationMs;
    public float bufferedFraction;
    public float hudAlpha = 1f;
    public String toast = "";
    public long toastUntil;

    // -------------------------------------------------------------- gaze

    public float gazeX, gazeY;
    public boolean gazeVisible;
    public float dwell;
    /** Stable identity of the hovered thing ("tile:abc", "btn:play"), or null. */
    public String hoverKey;
    public boolean hoverConsumed;

    // --------------------------------------------------------- environment

    public int env = 0;
    public int quality = 1;
    public float screenWidth = 11f;
    public float screenDistance = 4.2f;
    public float screenCenterY = 2.35f;
    public float curve = 0.72f;
    public float lensSeparation = 0.063f;
    public float fovAuto = 0f;
    public float barrel = 0.12f;
    public boolean fisheye = true;
    public float brightness = 1f;
    public boolean showClock = true;

    // ------------------------------------------------------------- timing

    public float dt = 0.016f;
    public float clock;

    public int videoW = 1920;
    public int videoH = 1080;

    public float aspect() {
        return videoH <= 0 ? 16f / 9f : videoW / (float) videoH;
    }

    /** Deep copy for the renderer. Bitmaps are shared by reference on purpose. */
    public void copyFrom(WorldModel o) {
        screen = o.screen;
        shelfTitle = o.shelfTitle;
        query = o.query;
        tiles.clear();
        tiles.addAll(o.tiles);
        settings.clear();
        settings.addAll(o.settings);
        hero = o.hero;
        loading = o.loading;
        statusLine = o.statusLine;
        errorLine = o.errorLine;

        hasVideo = o.hasVideo;
        playing = o.playing;
        buffering = o.buffering;
        videoTitle = o.videoTitle;
        videoUploader = o.videoUploader;
        qualityText = o.qualityText;
        positionMs = o.positionMs;
        durationMs = o.durationMs;
        bufferedFraction = o.bufferedFraction;
        hudAlpha = o.hudAlpha;
        toast = o.toast;
        toastUntil = o.toastUntil;

        gazeX = o.gazeX;
        gazeY = o.gazeY;
        gazeVisible = o.gazeVisible;
        dwell = o.dwell;
        hoverKey = o.hoverKey;
        hoverConsumed = o.hoverConsumed;

        env = o.env;
        quality = o.quality;
        screenWidth = o.screenWidth;
        screenDistance = o.screenDistance;
        screenCenterY = o.screenCenterY;
        curve = o.curve;
        lensSeparation = o.lensSeparation;
        fovAuto = o.fovAuto;
        barrel = o.barrel;
        fisheye = o.fisheye;
        brightness = o.brightness;
        showClock = o.showClock;

        dt = o.dt;
        clock = o.clock;
        videoW = o.videoW;
        videoH = o.videoH;
    }
}
