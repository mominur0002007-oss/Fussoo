package com.fussoo.vr.gl;

/** Column-major 4x4 math. Small on purpose. */
public final class M {

    private M() {
    }

    public static float[] id() {
        float[] m = new float[16];
        m[0] = 1f;
        m[5] = 1f;
        m[10] = 1f;
        m[15] = 1f;
        return m;
    }

    public static float[] mul(float[] a, float[] b) {
        float[] t = new float[16];
        for (int c = 0; c < 4; c++) {
            for (int r = 0; r < 4; r++) {
                t[c * 4 + r] = a[r] * b[c * 4]
                        + a[4 + r] * b[c * 4 + 1]
                        + a[8 + r] * b[c * 4 + 2]
                        + a[12 + r] * b[c * 4 + 3];
            }
        }
        return t;
    }

    public static float[] invRot(float[] m) {
        float[] t = id();
        t[0] = m[0];
        t[1] = m[4];
        t[2] = m[8];
        t[4] = m[1];
        t[5] = m[5];
        t[6] = m[9];
        t[8] = m[2];
        t[9] = m[6];
        t[10] = m[10];
        return t;
    }

    /**
     * Symmetric perspective from half-angle tangents.
     *
     * <p>{@code tanX}/{@code tanY} are tan(halfFov) values, which is how the view angles are
     * derived from the physical screen. The classic mistake here is {@code n / tan}; the
     * correct term is {@code 1 / tan}, i.e. the cotangent, or the scene shrinks to a speck.</p>
     */
    public static float[] frustum(float tanX, float tanY, float n, float f) {
        float[] m = new float[16];
        m[0] = 1f / tanX;
        m[5] = 1f / tanY;
        m[10] = -(f + n) / (f - n);
        m[11] = -1f;
        m[14] = -(2f * f * n) / (f - n);
        return m;
    }

    public static float[] t(float x, float y, float z) {
        float[] m = id();
        m[12] = x;
        m[13] = y;
        m[14] = z;
        return m;
    }

    public static float[] s(float x, float y, float z) {
        float[] m = id();
        m[0] = x;
        m[5] = y;
        m[10] = z;
        return m;
    }
}
