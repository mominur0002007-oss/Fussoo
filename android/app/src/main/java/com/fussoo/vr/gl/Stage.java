package com.fussoo.vr.gl;

/**
 * Everything the renderer knows about the world, written by the UI thread and read by the
 * GL thread. Plain floats and strings only: no Android UI objects, no locks held while
 * drawing.
 */
public final class Stage {

    // Geometry, metres.
    public float screenWidth = 11.0f;
    public float screenDistance = 4.2f;
    public float screenCenterY = 2.35f;
    public float curve = 0.72f;

    // Optics.
    public float lensSeparation = 0.063f;
    public float fov = 96f;
    public float barrel = 0.12f;
    public boolean fisheye = true;

    // Look.
    public int env = 0;
    public float brightness = 1f;
    public int quality = 1;

    // Player.
    public boolean hasVideo;
    public boolean playing;
    public String title = "";
    public String uploader = "";
    public String status = "";
    public String toast = "";
    public long toastUntil;
    public String qualityText = "";
    public long positionMs;
    public long durationMs;
    public float buffered;

    // Gaze.
    public float gazeX, gazeY;
    public boolean gazeVisible;
    public float dwell;
    public int hover = -1;
    public float hudAlpha = 1f;

    // Timing.
    public float dt = 0.016f;
    public float clock;

    // Video size for aspect fitting.
    public int videoW = 1920;
    public int videoH = 1080;

    public void copyFrom(Stage o) {
        screenWidth = o.screenWidth;
        screenDistance = o.screenDistance;
        screenCenterY = o.screenCenterY;
        curve = o.curve;
        lensSeparation = o.lensSeparation;
        fov = o.fov;
        barrel = o.barrel;
        fisheye = o.fisheye;
        env = o.env;
        brightness = o.brightness;
        quality = o.quality;
        hasVideo = o.hasVideo;
        playing = o.playing;
        title = o.title;
        uploader = o.uploader;
        status = o.status;
        toast = o.toast;
        toastUntil = o.toastUntil;
        qualityText = o.qualityText;
        positionMs = o.positionMs;
        durationMs = o.durationMs;
        buffered = o.buffered;
        gazeX = o.gazeX;
        gazeY = o.gazeY;
        gazeVisible = o.gazeVisible;
        dwell = o.dwell;
        hover = o.hover;
        hudAlpha = o.hudAlpha;
        clock = o.clock;
        videoW = o.videoW;
        videoH = o.videoH;
    }

    public float aspect() {
        if (videoH <= 0) {
            return 16f / 9f;
        }
        return videoW / (float) videoH;
    }
}
