package com.fussoo.vr.gl;

/** Venue palettes. Deliberately dim so the film is the brightest thing in view. */
public enum Env {
    IMAX("IMAX Theatre", 0.016f, 0.018f, 0.026f, 0.032f, 0.036f, 0.048f,
            1.00f, 0.40f, 0.16f),
    NOIR("Noir Lounge", 0.010f, 0.011f, 0.014f, 0.017f, 0.018f, 0.022f,
            0.55f, 0.62f, 0.80f),
    DEEP_SPACE("Deep Space", 0.008f, 0.012f, 0.032f, 0.018f, 0.026f, 0.060f,
            0.34f, 0.52f, 1.00f),
    COZY("Cozy Room", 0.026f, 0.018f, 0.012f, 0.038f, 0.026f, 0.018f,
            1.00f, 0.68f, 0.34f);

    public final String title;
    public final float floorR, floorG, floorB;
    public final float wallR, wallG, wallB;
    public final float accentR, accentG, accentB;

    Env(String title, float floorR, float floorG, float floorB,
        float wallR, float wallG, float wallB,
        float accentR, float accentG, float accentB) {
        this.title = title;
        this.floorR = floorR;
        this.floorG = floorG;
        this.floorB = floorB;
        this.wallR = wallR;
        this.wallG = wallG;
        this.wallB = wallB;
        this.accentR = accentR;
        this.accentG = accentG;
        this.accentB = accentB;
    }

    public static Env of(int i) {
        Env[] all = values();
        return (i < 0 || i >= all.length) ? IMAX : all[i];
    }
}
