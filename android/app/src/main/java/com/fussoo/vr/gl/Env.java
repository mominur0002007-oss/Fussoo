package com.fussoo.vr.gl;

/** Venue palettes. Deliberately dim: the screen must be the brightest thing in view. */
public final class Env {

    private Env() {
    }

    public static final String[] NAMES = {
            "IMAX Theatre", "Noir Lounge", "Deep Space", "Cozy Room"
    };

    public static float[] wall(int i) {
        switch (i) {
            case 1:
                return new float[]{0.018f, 0.018f, 0.022f};
            case 2:
                return new float[]{0.014f, 0.022f, 0.055f};
            case 3:
                return new float[]{0.036f, 0.025f, 0.017f};
            default:
                return new float[]{0.030f, 0.034f, 0.046f};
        }
    }

    public static float[] floor(int i) {
        switch (i) {
            case 1:
                return new float[]{0.010f, 0.011f, 0.014f};
            case 2:
                return new float[]{0.008f, 0.013f, 0.034f};
            case 3:
                return new float[]{0.028f, 0.019f, 0.013f};
            default:
                return new float[]{0.016f, 0.018f, 0.026f};
        }
    }

    public static float[] accent(int i) {
        switch (i) {
            case 1:
                return new float[]{0.55f, 0.62f, 0.80f};
            case 2:
                return new float[]{0.34f, 0.52f, 1.00f};
            case 3:
                return new float[]{1.00f, 0.68f, 0.34f};
            default:
                return new float[]{1.00f, 0.40f, 0.16f};
        }
    }
}
