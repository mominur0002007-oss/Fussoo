package com.tuvr.app.vr;

import java.util.Random;

/**
 * Procedural VR environments built from vertex-coloured geometry only:
 * no textures, no lighting, no post-processing. Each preset is a handful of
 * small meshes so it costs almost nothing on a Mali-G57.
 */
public final class EnvironmentBuilder {

    public static final int VOID = 0;
    public static final int CINEMA = 1;
    public static final int THEATER = 2;
    public static final int SPACE = 3;
    public static final int AMBIENT = 4;

    public static final String[] NAMES = {"Void", "Cinema", "Dark theater", "Space", "Ambient grid"};

    private EnvironmentBuilder() {
    }

    public static String name(int type) {
        if (type < 0 || type >= NAMES.length) {
            return NAMES[VOID];
        }
        return NAMES[type];
    }

    /** Holds the meshes of one environment. */
    public static final class Env {
        public Mesh backdrop;
        public Mesh floor;
        public Mesh stars;
        public Mesh accents;
        public int type = VOID;

        public void release() {
            if (backdrop != null) {
                backdrop.release();
                backdrop = null;
            }
            if (floor != null) {
                floor.release();
                floor = null;
            }
            if (stars != null) {
                stars.release();
                stars = null;
            }
            if (accents != null) {
                accents.release();
                accents = null;
            }
        }
    }

    public static Env build(int type) {
        Env env = new Env();
        env.type = type;
        switch (type) {
            case CINEMA:
                env.backdrop = gradientDome(40f, 0xFF05080D, 0xFF161C27, 24, 12);
                env.floor = floor(18f, 0xFF0A0D12, 0xFF14181F);
                env.accents = accentStrips(0xFF2A8FA6, 0.18f);
                break;
            case THEATER:
                env.backdrop = gradientDome(40f, 0xFF020304, 0xFF0C0F13, 24, 12);
                env.floor = floor(18f, 0xFF040506, 0xFF0B0D11);
                env.accents = accentStrips(0xFF3A3F46, 0.12f);
                break;
            case SPACE:
                env.backdrop = gradientDome(40f, 0xFF000000, 0xFF060A14, 20, 10);
                env.stars = starfield(1400, 30f);
                break;
            case AMBIENT:
                env.backdrop = gradientDome(40f, 0xFF04161C, 0xFF071A22, 20, 10);
                env.floor = gridFloor(14f, 0xFF2A8FA6);
                break;
            case VOID:
            default:
                env.backdrop = gradientDome(40f, 0xFF04060B, 0xFF0E1622, 24, 12);
                break;
        }
        return env;
    }

    /* ---------------------------------------------------------------- */

    /**
     * A dome around the camera with a smooth vertical gradient. Rendered inside
     * out, which is why the winding order of the indices is flipped.
     */
    private static Mesh gradientDome(float radius, int bottomColor, int topColor,
                                     int segments, int rings) {
        int[] colors = new int[rings + 1];
        for (int r = 0; r <= rings; r++) {
            float t = r / (float) rings;
            colors[r] = lerpColor(bottomColor, topColor, t);
        }
        float[] vertices = new float[(segments + 1) * (rings + 1) * 6];
        int v = 0;
        for (int r = 0; r <= rings; r++) {
            float vertical = r / (float) rings;         // 0 bottom .. 1 top
            float phi = (float) Math.PI * (0.5f - vertical); // -90..+90 degrees
            float y = (float) Math.sin(phi) * radius;
            float ringRadius = (float) Math.cos(phi) * radius;
            int color = colors[r];
            for (int s = 0; s <= segments; s++) {
                float theta = (float) (2.0 * Math.PI * s / segments);
                vertices[v++] = (float) (Math.cos(theta) * ringRadius);
                vertices[v++] = y;
                vertices[v++] = (float) (Math.sin(theta) * ringRadius);
                vertices[v++] = ((color >> 16) & 0xFF) / 255f;
                vertices[v++] = ((color >> 8) & 0xFF) / 255f;
                vertices[v++] = (color & 0xFF) / 255f;
            }
        }
        int quads = segments * rings;
        short[] indices = new short[quads * 6];
        int idx = 0;
        for (int r = 0; r < rings; r++) {
            for (int s = 0; s < segments; s++) {
                short a = (short) (r * (segments + 1) + s);
                short b = (short) (a + 1);
                short c = (short) (a + segments + 1);
                short d = (short) (c + 1);
                // inside-out winding
                indices[idx++] = a;
                indices[idx++] = b;
                indices[idx++] = c;
                indices[idx++] = b;
                indices[idx++] = d;
                indices[idx++] = c;
            }
        }
        return Mesh.create(vertices, 6, indices);
    }

    /** A flat disc below the camera that grounds the scene without a texture. */
    private static Mesh floor(float radius, int centerColor, int edgeColor) {
        int rings = 4;
        int segments = 24;
        float[] vertices = new float[(rings + 1) * (segments + 1) * 6];
        int v = 0;
        float y = -1.65f;
        for (int r = 0; r <= rings; r++) {
            float t = r / (float) rings;
            float ringRadius = radius * t;
            int color = lerpColor(centerColor, edgeColor, t);
            for (int s = 0; s <= segments; s++) {
                float theta = (float) (2.0 * Math.PI * s / segments);
                vertices[v++] = (float) (Math.cos(theta) * ringRadius);
                vertices[v++] = y;
                vertices[v++] = (float) (Math.sin(theta) * ringRadius);
                vertices[v++] = ((color >> 16) & 0xFF) / 255f;
                vertices[v++] = ((color >> 8) & 0xFF) / 255f;
                vertices[v++] = (color & 0xFF) / 255f;
            }
        }
        short[] indices = new short[rings * segments * 6];
        int idx = 0;
        for (int r = 0; r < rings; r++) {
            for (int s = 0; s < segments; s++) {
                short a = (short) (r * (segments + 1) + s);
                short b = (short) (a + 1);
                short c = (short) (a + segments + 1);
                short d = (short) (c + 1);
                indices[idx++] = a;
                indices[idx++] = c;
                indices[idx++] = b;
                indices[idx++] = b;
                indices[idx++] = c;
                indices[idx++] = d;
            }
        }
        return Mesh.create(vertices, 6, indices);
    }

    /** Teal grid floor drawn with GL_LINES. */
    private static Mesh gridFloor(float radius, int color) {
        int lines = 21;
        float[] vertices = new float[lines * 2 * 2 * 6];
        int v = 0;
        float r = 0.72f;
        float g = 0.85f;
        float b = 0.95f;
        float y = -1.65f;
        for (int i = 0; i < lines; i++) {
            float t = -radius + (2f * radius) * i / (lines - 1);
            // line along X
            addLineVertex(vertices, v, -radius, y, t, r, g, b);
            v += 6;
            addLineVertex(vertices, v, radius, y, t, r, g, b);
            v += 6;
            // line along Z
            addLineVertex(vertices, v, t, y, -radius, r, g, b);
            v += 6;
            addLineVertex(vertices, v, t, y, radius, r, g, b);
            v += 6;
        }
        // fade the colour toward the edge by using an unused alpha channel slot
        return Mesh.create(vertices, 6, null);
    }

    private static void addLineVertex(float[] target, int offset, float x, float y, float z,
                                      float r, float g, float b) {
        target[offset] = x;
        target[offset + 1] = y;
        target[offset + 2] = z;
        target[offset + 3] = r;
        target[offset + 4] = g;
        target[offset + 5] = b;
    }

    /** A few soft emissive lines on the walls to suggest a room. */
    private static Mesh accentStrips(int color, float height) {
        float[] vertices = new float[4 * 2 * 6];
        int v = 0;
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float distance = 9f;
        float width = 11f;
        float y = height - 1.0f;
        // front and back walls
        addLineVertex(vertices, v, -width, y, -distance, r, g, b);
        v += 6;
        addLineVertex(vertices, v, width, y, -distance, r, g, b);
        v += 6;
        addLineVertex(vertices, v, -width, y, distance, r, g, b);
        v += 6;
        addLineVertex(vertices, v, width, y, distance, r, g, b);
        v += 6;
        // side walls
        addLineVertex(vertices, v, -width, y, -distance, r, g, b);
        v += 6;
        addLineVertex(vertices, v, -width, y, distance, r, g, b);
        v += 6;
        addLineVertex(vertices, v, width, y, -distance, r, g, b);
        v += 6;
        addLineVertex(vertices, v, width, y, distance, r, g, b);
        return Mesh.create(vertices, 6, null);
    }

    /** Star points on the inside of a large sphere. */
    private static Mesh starfield(int count, float radius) {
        float[] vertices = new float[count * 6];
        Random random = new Random(20260401L);
        int v = 0;
        for (int i = 0; i < count; i++) {
            // uniform direction on a sphere
            float u = random.nextFloat() * 2f - 1f;
            float theta = random.nextFloat() * (float) (Math.PI * 2.0);
            float r = (float) Math.sqrt(1f - u * u);
            float x = r * (float) Math.cos(theta) * radius;
            float y = u * radius;
            float z = r * (float) Math.sin(theta) * radius;
            float brightness = 0.45f + random.nextFloat() * 0.55f;
            vertices[v++] = x;
            vertices[v++] = y;
            vertices[v++] = z;
            vertices[v++] = brightness;
            vertices[v++] = brightness;
            vertices[v++] = Math.min(1f, brightness * 1.1f);
        }
        return Mesh.create(vertices, 6, null);
    }

    private static int lerpColor(int from, int to, float t) {
        int a = (int) (((from >> 24) & 0xFF) + (((to >> 24) & 0xFF) - ((from >> 24) & 0xFF)) * t);
        int r = (int) (((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int g = (int) (((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
