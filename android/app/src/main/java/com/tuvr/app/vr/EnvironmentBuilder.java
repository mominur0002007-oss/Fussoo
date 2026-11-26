package com.tuvr.app.vr;

import java.util.Arrays;

/**
 * Builds the virtual environments: simple, cheap geometry (a handful of quads and
 * a dome) that gives the cinema a sense of place without any textures.
 *
 * All meshes are created on the GL thread and released with the environment.
 */
public final class EnvironmentBuilder {

    public static final int VOID = 0;
    public static final int CINEMA = 1;
    public static final int THEATER = 2;
    public static final int SPACE = 3;
    public static final int AMBIENT = 4;
    public static final int WORLD = 5;

    public static final String[] NAMES = {
            "Void", "Cinema", "Dark theater", "Space", "Ambient grid", "3D world"};

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
        /** Extra unlit triangles (sun disc and similar) drawn like the backdrop. */
        public Mesh glow;
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
            if (glow != null) {
                glow.release();
                glow = null;
            }
        }
    }

    public static Env build(int type) {
        Env env = new Env();
        env.type = type;
        switch (type) {
            case CINEMA:
                // A dim room: wall box plus a warm floor and cool trim lines.
                env.backdrop = room(11f, 5f, -1.7f, 3.2f, 0xFF1B2836, 0xFF0A1018, 0xFF131C26);
                env.floor = floor(12f, 0xFF1A2430, 0xFF070B10);
                env.accents = accentStrips(0xFF3AC0E0, 0.5f);
                break;
            case THEATER:
                // Warm screening room: walls read even on a dim phone panel.
                env.backdrop = room(7.5f, 4f, -1.7f, 2.6f, 0xFF3A2418, 0xFF1A0F09, 0xFF241207);
                env.floor = floor(8f, 0xFF2A160C, 0xFF080402);
                env.accents = accentStrips(0xFFE0A160, 0.3f);
                env.glow = wallGlow(0xFFB4783C);
                break;
            case SPACE:
                env.backdrop = gradientDome(60f, 0xFF000000, 0xFF070C18, 24, 14);
                env.stars = starfield(1600, 44f);
                env.floor = nebula(0xFF0A1430, 0xFF01040A);
                break;
            case AMBIENT:
                env.backdrop = gradientDome(45f, 0xFF04161C, 0xFF07242E, 22, 12);
                env.floor = gridFloor(16f, 0xFF2A8FA6);
                env.accents = accentStrips(0xFF2A8FA6, 0.2f);
                break;
            case WORLD:
                // Outdoor: sky gradient, ground, mountain silhouettes and a sun.
                env.backdrop = worldSky();
                env.floor = floor(70f, 0xFF2C4227, 0xFF12200F);
                env.glow = sunDisc();
                break;
            case VOID:
            default:
                env.backdrop = gradientDome(45f, 0xFF04060B, 0xFF0E1622, 24, 12);
                break;
        }
        return env;
    }

    /* ------------------------------------------------------------------ */
    /* geometry helpers                                                    */
    /* ------------------------------------------------------------------ */

    /** Small float buffer that appends interleaved position+colour vertices. */
    private static final class Buf {
        private float[] data;
        private int used;

        Buf(int floats) {
            data = new float[floats];
        }

        void vertex(float x, float y, float z, int color) {
            if (used + 6 > data.length) {
                data = Arrays.copyOf(data, Math.max(data.length * 2, used + 6));
            }
            data[used++] = x;
            data[used++] = y;
            data[used++] = z;
            data[used++] = ((color >> 16) & 0xFF) / 255f;
            data[used++] = ((color >> 8) & 0xFF) / 255f;
            data[used++] = (color & 0xFF) / 255f;
        }

        /** Quad a-b at the bottom edge, c-d at the top edge (counter-clockwise). */
        void quad(float ax, float ay, float az, float bx, float by, float bz,
                  float cx, float cy, float cz, float dx, float dy, float dz,
                  int bottomColor, int topColor) {
            vertex(ax, ay, az, bottomColor);
            vertex(bx, by, bz, bottomColor);
            vertex(cx, cy, cz, topColor);
            vertex(bx, by, bz, bottomColor);
            vertex(dx, dy, dz, topColor);
            vertex(cx, cy, cz, topColor);
        }

        Mesh toMesh() {
            if (used == 0) {
                return null;
            }
            return Mesh.create(Arrays.copyOf(data, used), 6, null);
        }
    }

    /** A three-colour wall box around the viewer (no top face, no side flicker). */
    private static Mesh room(float halfWidth, float halfDepth, float floorY,
                             float ceilingY, int topColor, int bottomColor, int ceiling) {
        Buf b = new Buf(4 * 6 * 6 + 6 * 6);
        // North (in front), south (behind), west, east walls.
        b.quad(-halfWidth, floorY, -halfDepth, halfWidth, floorY, -halfDepth,
                -halfWidth, ceilingY, -halfDepth, halfWidth, ceilingY, -halfDepth,
                bottomColor, topColor);
        b.quad(halfWidth, floorY, halfDepth, -halfWidth, floorY, halfDepth,
                halfWidth, ceilingY, halfDepth, -halfWidth, ceilingY, halfDepth,
                bottomColor, topColor);
        b.quad(-halfWidth, floorY, halfDepth, -halfWidth, floorY, -halfDepth,
                -halfWidth, ceilingY, halfDepth, -halfWidth, ceilingY, -halfDepth,
                bottomColor, topColor);
        b.quad(halfWidth, floorY, -halfDepth, halfWidth, floorY, halfDepth,
                halfWidth, ceilingY, -halfDepth, halfWidth, ceilingY, halfDepth,
                bottomColor, topColor);
        // Ceiling
        b.quad(-halfWidth, ceilingY, -halfDepth, halfWidth, ceilingY, -halfDepth,
                -halfWidth, ceilingY, halfDepth, halfWidth, ceilingY, halfDepth,
                ceiling, ceiling);
        return b.toMesh();
    }

    /** Blue sky with a horizon haze, plus mountain silhouettes. */
    private static Mesh worldSky() {
        Buf b = new Buf(64 * 6);
        float radius = 80f;
        int skyTop = 0xFF0B2A5A;
        int skyHorizon = 0xFF8FB6D8;
        int ground = 0xFF243521;

        // Sky dome as a ring of quads (cheap, only 32 segments).
        int segments = 32;
        for (int i = 0; i < segments; i++) {
            double a0 = 2 * Math.PI * i / segments;
            double a1 = 2 * Math.PI * (i + 1) / segments;
            float x0 = (float) Math.cos(a0) * radius;
            float z0 = (float) Math.sin(a0) * radius;
            float x1 = (float) Math.cos(a1) * radius;
            float z1 = (float) Math.sin(a1) * radius;
            b.quad(x0, -4f, z0, x1, -4f, z1, x0, radius, z0, x1, radius, z1,
                    skyHorizon, skyTop);
        }
        // Mountain silhouettes just above the horizon.
        int peaks = 16;
        for (int i = 0; i < peaks; i++) {
            double a0 = 2 * Math.PI * i / peaks;
            double a1 = 2 * Math.PI * (i + 1) / peaks;
            float r = 62f;
            float h = 6f + (float) ((i * 37 % 11) * 0.9);
            b.vertex((float) Math.cos(a0) * r, -2.5f, (float) Math.sin(a0) * r, ground);
            b.vertex((float) Math.cos(a1) * r, -2.5f, (float) Math.sin(a1) * r, ground);
            b.vertex((float) Math.cos((a0 + a1) * 0.5) * r, h, (float) Math.sin((a0 + a1) * 0.5) * r,
                    0xFF1A2A33);
        }
        return b.toMesh();
    }

    /** Two soft vertical panels either side of the screen: the room's key light. */
    private static Mesh wallGlow(int color) {
        Buf b = new Buf(12 * 6);
        b.quad(-6f, -1.5f, -3.9f, -3.2f, -1.5f, -3.9f, -6f, 2.4f, -3.9f, -3.2f, 2.4f, -3.9f,
                color, 0xFF120A05);
        b.quad(3.2f, -1.5f, -3.9f, 6f, -1.5f, -3.9f, 3.2f, 2.4f, -3.9f, 6f, 2.4f, -3.9f,
                color, 0xFF120A05);
        return b.toMesh();
    }

    /** A bright disc standing in for the sun (drawn with the star point program). */
    private static Mesh sunDisc() {
        Buf b = new Buf(6 * 6);
        float x = -18f;
        float y = 22f;
        float z = -52f;
        int core = 0xFFFFF3C4;
        int edge = 0xFF9FBFE0;
        b.vertex(x - 4f, y - 4f, z, edge);
        b.vertex(x + 4f, y - 4f, z, edge);
        b.vertex(x, y + 4.5f, z, core);
        b.vertex(x + 4f, y - 4f, z, edge);
        b.vertex(x, y + 4.5f, z, core);
        b.vertex(x - 4f, y + 4.5f, z, edge);
        return b.toMesh();
    }

    /** A faint horizontal band used as a nebula in the space environment. */
    private static Mesh nebula(int innerColor, int outerColor) {
        Buf b = new Buf(6 * 24);
        float radius = 55f;
        int segments = 24;
        for (int i = 0; i < segments; i++) {
            double a0 = 2 * Math.PI * i / segments;
            double a1 = 2 * Math.PI * (i + 1) / segments;
            b.quad((float) Math.cos(a0) * radius, -8f, (float) Math.sin(a0) * radius,
                    (float) Math.cos(a1) * radius, -8f, (float) Math.sin(a1) * radius,
                    (float) Math.cos(a0) * radius, 8f, (float) Math.sin(a0) * radius,
                    (float) Math.cos(a1) * radius, 8f, (float) Math.sin(a1) * radius,
                    outerColor, innerColor);
        }
        return b.toMesh();
    }

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
        int rings = 5;
        int segments = 32;
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
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float y = -1.65f;
        for (int i = 0; i < lines; i++) {
            float t = -radius + (2f * radius) * i / (lines - 1);
            addLineVertex(vertices, v, -radius, y, t, r, g, b);
            v += 6;
            addLineVertex(vertices, v, radius, y, t, r, g, b);
            v += 6;
            addLineVertex(vertices, v, t, y, -radius, r, g, b);
            v += 6;
            addLineVertex(vertices, v, t, y, radius, r, g, b);
            v += 6;
        }
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
        float[] vertices = new float[8 * 2 * 6];
        int v = 0;
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float distance = 9f;
        float width = 11f;
        float y = height - 1.0f;
        addLineVertex(vertices, v, -width, y, -distance, r, g, b);
        v += 6;
        addLineVertex(vertices, v, width, y, -distance, r, g, b);
        v += 6;
        addLineVertex(vertices, v, -width, y, distance, r, g, b);
        v += 6;
        addLineVertex(vertices, v, width, y, distance, r, g, b);
        v += 6;
        addLineVertex(vertices, v, -width, y, -distance, r, g, b);
        v += 6;
        addLineVertex(vertices, v, -width, y, distance, r, g, b);
        v += 6;
        addLineVertex(vertices, v, width, y, -distance, r, g, b);
        v += 6;
        addLineVertex(vertices, v, width, y, distance, r, g, b);
        return Mesh.create(vertices, 6, null);
    }

    /** Points scattered on a sphere, drawn with the point program. */
    private static Mesh starfield(int count, float radius) {
        float[] vertices = new float[count * 6];
        java.util.Random random = new java.util.Random(7);
        int v = 0;
        for (int i = 0; i < count; i++) {
            double theta = random.nextDouble() * Math.PI * 2;
            double cosPhi = random.nextDouble() * 2 - 1;
            double sinPhi = Math.sqrt(Math.max(0, 1 - cosPhi * cosPhi));
            float x = (float) (Math.cos(theta) * sinPhi * radius);
            float y = (float) (cosPhi * radius);
            float z = (float) (Math.sin(theta) * sinPhi * radius);
            float brightness = 0.45f + random.nextFloat() * 0.55f;
            vertices[v++] = x;
            vertices[v++] = y;
            vertices[v++] = z;
            vertices[v++] = brightness;
            vertices[v++] = brightness;
            vertices[v++] = Math.min(1f, brightness + 0.08f);
        }
        return Mesh.create(vertices, 6, null);
    }

    private static int lerpColor(int from, int to, float t) {
        t = MathUtil.clamp(t, 0f, 1f);
        int r = (int) (((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int g = (int) (((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int b = (int) ((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
