package com.fussoo.vr.gl;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

/** Static geometry: a quad, and a sphere used as the room shell. */
public final class Geo {

    private Geo() {
    }

    public static FloatBuffer quad2d() {
        float[] v = {-1f, -1f, 1f, -1f, 1f, 1f, -1f, 1f};
        return buf(v);
    }

    public static ShortBuffer quadIdx() {
        return idx(new short[]{0, 1, 2, 0, 2, 3});
    }

    public static FloatBuffer sphere(int rings, int sectors) {
        float[] v = new float[(rings + 1) * (sectors + 1) * 3];
        int o = 0;
        for (int r = 0; r <= rings; r++) {
            double phi = Math.PI * r / rings;
            float y = (float) Math.cos(phi);
            float rad = (float) Math.sin(phi);
            for (int s = 0; s <= sectors; s++) {
                double theta = 2.0 * Math.PI * s / sectors;
                v[o++] = (float) (rad * Math.cos(theta));
                v[o++] = y;
                v[o++] = (float) (rad * Math.sin(theta));
            }
        }
        return buf(v);
    }

    public static ShortBuffer sphereIdx(int rings, int sectors) {
        short[] i = new short[rings * sectors * 6];
        int t = 0;
        for (int r = 0; r < rings; r++) {
            for (int s = 0; s < sectors; s++) {
                short a = (short) (r * (sectors + 1) + s);
                short b = (short) (a + sectors + 1);
                i[t++] = a;
                i[t++] = b;
                i[t++] = (short) (a + 1);
                i[t++] = (short) (a + 1);
                i[t++] = b;
                i[t++] = (short) (b + 1);
            }
        }
        return idx(i);
    }

    private static FloatBuffer buf(float[] data) {
        ByteBuffer bb = ByteBuffer.allocateDirect(data.length * 4);
        bb.order(ByteOrder.nativeOrder());
        FloatBuffer f = bb.asFloatBuffer();
        f.put(data).position(0);
        return f;
    }

    private static ShortBuffer idx(short[] data) {
        ByteBuffer bb = ByteBuffer.allocateDirect(data.length * 2);
        bb.order(ByteOrder.nativeOrder());
        ShortBuffer s = bb.asShortBuffer();
        s.put(data).position(0);
        return s;
    }
}
