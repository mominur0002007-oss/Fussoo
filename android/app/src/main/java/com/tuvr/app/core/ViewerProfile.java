package com.tuvr.app.core;

/**
 * Immutable optics description of a Cardboard-style viewer.
 *
 * All distances are millimetres, all angles degrees. Derived values expose the
 * numbers the renderer needs (per-eye field of view, eye offset, half-angle
 * tangents) so the GL code never invents optical constants of its own.
 *
 * Nothing here is hard-coded for a specific phone or headset: presets are only
 * starting points and every value can be overridden by manual calibration.
 */
public final class ViewerProfile {

    public static final String ID_SHINECON = "shinecon";
    public static final String ID_CARDBOARD_V1 = "cardboard_v1";
    public static final String ID_CARDBOARD_V2 = "cardboard_v2";
    public static final String ID_GENERIC = "generic";
    public static final String ID_CUSTOM = "custom";

    public final String id;
    public final float lensSeparationMm;
    public final float screenLensDistanceMm;
    /** Total diagonal-ish horizontal field of view of one lens, in degrees. */
    public final float fovDeg;
    public final float eyeOffsetXMm;
    public final float eyeOffsetYMm;
    public final float distortionK1;
    public final float distortionK2;
    public final float chromaticShift;
    public final float ipdMm;
    public final boolean distortionEnabled;

    public ViewerProfile(String id, float lensSeparationMm, float screenLensDistanceMm,
                         float fovDeg, float eyeOffsetXMm, float eyeOffsetYMm,
                         float distortionK1, float distortionK2, float chromaticShift,
                         float ipdMm, boolean distortionEnabled) {
        this.id = id;
        this.lensSeparationMm = lensSeparationMm;
        this.screenLensDistanceMm = screenLensDistanceMm;
        this.fovDeg = fovDeg;
        this.eyeOffsetXMm = eyeOffsetXMm;
        this.eyeOffsetYMm = eyeOffsetYMm;
        this.distortionK1 = distortionK1;
        this.distortionK2 = distortionK2;
        this.chromaticShift = chromaticShift;
        this.ipdMm = ipdMm;
        this.distortionEnabled = distortionEnabled;
    }

    /**
     * Physical eye separation used for stereo rendering.
     *
     * A VR box with fixed lenses shows each eye the half of the screen behind
     * its lens; the effective stereo baseline is therefore limited by the lens
     * separation, not by the user's IPD. We use the smaller of the two values,
     * clamped so that very wide or very narrow settings stay comfortable.
     */
    public float stereoBaselineMm() {
        float baseline = Math.min(ipdMm, lensSeparationMm * 0.98f);
        return AppSettings.clamp(baseline, 40f, 80f);
    }

    /** Horizontal field of view per eye. The two eyes overlap, so it is > fov/2. */
    public float eyeFovXDeg() {
        // Real lenses have a large usable area; two thirds of the total FOV per
        // eye is a good practical compromise that also gives ~33% overlap.
        return AppSettings.clamp(fovDeg * 0.66f, 30f, 110f);
    }

    /**
     * Vertical field of view per eye. The screen is wider than it is tall, and a
     * phone VR box crops the top/bottom less than the sides, so the vertical
     * angle is derived from the horizontal one with the physical eye aspect.
     */
    public float eyeFovYDeg(float eyeAspect) {
        float aspect = (eyeAspect <= 0.05f || Float.isNaN(eyeAspect)) ? 1.0f : eyeAspect;
        double tanHalfX = Math.tan(Math.toRadians(eyeFovXDeg() * 0.5f));
        double tanHalfY = tanHalfX / aspect;
        return (float) Math.toDegrees(2.0 * Math.atan(tanHalfY));
    }

    public float tanHalfFovX() {
        return (float) Math.tan(Math.toRadians(eyeFovXDeg() * 0.5f));
    }

    public float tanHalfFovY(float eyeAspect) {
        return tanHalfFovX() / Math.max(0.05f, eyeAspect);
    }

    /** Estimated usable half-width of the lens-to-screen cone, metres. */
    public float halfLensToScreenCone() {
        return (float) (Math.tan(Math.toRadians(fovDeg * 0.5f)) * (screenLensDistanceMm / 1000f));
    }

    public ViewerProfile withDistortionK1(float k1) {
        return new ViewerProfile(id, lensSeparationMm, screenLensDistanceMm, fovDeg,
                eyeOffsetXMm, eyeOffsetYMm, k1, distortionK2, chromaticShift, ipdMm, distortionEnabled);
    }

    public ViewerProfile withId(String newId) {
        return new ViewerProfile(newId, lensSeparationMm, screenLensDistanceMm, fovDeg,
                eyeOffsetXMm, eyeOffsetYMm, distortionK1, distortionK2, chromaticShift, ipdMm,
                distortionEnabled);
    }

    public static String displayName(String id) {
        if (id == null) {
            return "Custom";
        }
        switch (id) {
            case ID_SHINECON:
                return "Shinecon VR Box";
            case ID_CARDBOARD_V1:
                return "Cardboard v1";
            case ID_CARDBOARD_V2:
                return "Cardboard v2";
            case ID_GENERIC:
                return "Generic 3D glasses box";
            default:
                return "Custom calibration";
        }
    }

    /** Starting point for a viewer. Values come from common published figures. */
    public static ViewerProfile preset(String id) {
        switch (id == null ? "" : id) {
            case ID_CARDBOARD_V1:
                return new ViewerProfile(ID_CARDBOARD_V1, 60f, 42f, 90f, 0f, 0f, 0.34f, 0.05f, 0f, 63f, true);
            case ID_CARDBOARD_V2:
                return new ViewerProfile(ID_CARDBOARD_V2, 64f, 39f, 96f, 0f, 0f, 0.28f, 0.08f, 0f, 63f, true);
            case ID_GENERIC:
                return new ViewerProfile(ID_GENERIC, 62f, 48f, 88f, 0f, 0f, 0.16f, 0.02f, 0f, 63f, true);
            case ID_SHINECON:
            default:
                // Shinecon-style adjustable box: ~62 mm lenses, 45 mm lens-to-screen.
                return new ViewerProfile(ID_SHINECON, 62f, 45f, 92f, 0f, 0f, 0.20f, 0.03f, 0f, 63f, true);
        }
    }
}
