package com.tuvr.app.media;

import android.content.Context;

import com.tuvr.app.core.AppSettings;

/** Describes how a video should be mapped into the VR scene. */
public final class VideoFormat {

    public static final int PROJ_FLAT = 0;
    public static final int PROJ_VR180 = 1;
    public static final int PROJ_VR360 = 2;

    public static final int STEREO_MONO = 0;
    public static final int STEREO_SBS = 1;
    public static final int STEREO_TAB = 2;

    public int projection = PROJ_FLAT;
    public int stereo = STEREO_MONO;
    public boolean swapEyes = false;
    /** 0, 90, 180, 270 - rotates the image inside the projection. */
    public int rotationDeg = 0;
    /** Mirrors the picture vertically; some files/decoders need it. */
    public boolean flipVertical = false;

    public static VideoFormat defaults(Context context) {
        AppSettings s = AppSettings.get(context);
        VideoFormat f = new VideoFormat();
        f.projection = s.defaultProjection();
        f.stereo = s.defaultStereo();
        return f;
    }

    public boolean isProjected() {
        return projection != PROJ_FLAT;
    }

    public boolean isStereo() {
        return stereo != STEREO_MONO;
    }

    public VideoFormat copy() {
        VideoFormat f = new VideoFormat();
        f.projection = projection;
        f.stereo = stereo;
        f.swapEyes = swapEyes;
        f.rotationDeg = rotationDeg;
        f.flipVertical = flipVertical;
        return f;
    }

    public static String projectionName(int projection) {
        switch (projection) {
            case PROJ_VR180:
                return "VR180";
            case PROJ_VR360:
                return "VR360";
            default:
                return "Flat 2D";
        }
    }

    public static String stereoName(int stereo) {
        switch (stereo) {
            case STEREO_SBS:
                return "SBS";
            case STEREO_TAB:
                return "Top/Bottom";
            default:
                return "Mono";
        }
    }

    public String describe() {
        String base = projectionName(projection);
        if (isStereo()) {
            base += " · " + stereoName(stereo);
        }
        if (swapEyes) {
            base += " · eyes swapped";
        }
        if (rotationDeg != 0) {
            base += " · " + rotationDeg + "°";
        }
        if (flipVertical) {
            base += " · flipped";
        }
        return base;
    }

    /**
     * Aspect ratio of the visible picture, after removing the part of the frame
     * that belongs to the second eye in stereo layouts. Zero means "unknown".
     */
    public float pictureAspect(int videoWidth, int videoHeight) {
        if (videoWidth <= 0 || videoHeight <= 0) {
            return 0f;
        }
        float w = videoWidth;
        float h = videoHeight;
        if (stereo == STEREO_SBS) {
            w *= 0.5f;
        } else if (stereo == STEREO_TAB) {
            h *= 0.5f;
        }
        if (rotationDeg == 90 || rotationDeg == 270) {
            float t = w;
            w = h;
            h = t;
        }
        return w / h;
    }
}
