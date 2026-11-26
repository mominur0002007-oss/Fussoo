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
            case CINEMA: {
                // A small screening room: wall box, screen surround, ceiling light
                // strips, side sconces and a carpet in cool blue-grey.
                Buf b = new Buf(4096);
                addRoom(b, 11f, 5f, -1.7f, 3.2f, 0xFF1B2836, 0xFF0A1018, 0xFF131C26);
                addScreenSurround(b, 3.4f, -1.6f, 2.2f, 0xFF05080C, 0xFF10161E);
                addCeilingStrips(b, 9f, 3.15f, 0xFF2E6E86);
                addSconces(b, 9.4f, -0.4f, 2.0f, 0xFF2FA8C8);
                addCarpet(b, -6.5f, -1.68f, 0xFF16202B);
                env.backdrop = b.toMesh();
                env.floor = floor(12f, 0xFF1A2430, 0xFF070B10);
                env.accents = accentStrips(0xFF3AC0E0, 0.5f);
                break;
            }
            case THEATER: {
                // Warm screening room with curtains either side of the screen,
                // sconces on the walls and two rows of seat backs behind you.
                Buf b = new Buf(4096);
                addRoom(b, 7.5f, 4f, -1.7f, 2.6f, 0xFF3A2418, 0xFF1A0F09, 0xFF241207);
                addScreenSurround(b, 3.4f, -1.6f, 2.1f, 0xFF120A05, 0xFF2A1509);
                addCurtains(b, 3.9f, -1.6f, 2.5f, 0xFF4A1309, 0xFF2A0A05);
                addSconces(b, 6.2f, -0.2f, 1.8f, 0xFFE0A160);
                addSeats(b, 5, 3, 1.2f, 0xFF3A1B12, 0xFF1E0C07);
                addCarpet(b, -4.5f, -1.68f, 0xFF2A1409);
                env.backdrop = b.toMesh();
                env.floor = floor(8f, 0xFF2A160C, 0xFF080402);
                env.accents = accentStrips(0xFFE0A160, 0.3f);
                env.glow = wallGlow(0xFFB4783C);
                break;
            }
            case SPACE:
                env.backdrop = gradientDome(60f, 0xFF02040A, 0xFF0A1630, 28, 16);
                env.stars = starfield(1800, 44f);
                env.floor = nebula(0xFF102146, 0xFF01040A);
                env.glow = planet(0xFF2A4E7A, 0xFF6E9BC8);
                break;
            case AMBIENT:
                env.backdrop = gradientDome(45f, 0xFF04161C, 0xFF07242E, 22, 12);
                env.floor = gridFloor(16f, 0xFF2A8FA6);
                env.accents = accentStrips(0xFF2A8FA6, 0.2f);
                break;
            case WORLD: {
                // Outdoor: sky gradient, two mountain ranges, clouds and a sun.
                Buf b = new Buf(8192);
                addWorldSky(b);
                addMountainRing(b, 62f, -2.5f, 6f, 16, 0xFF1A2A33);
                addMountainRing(b, 44f, -2.4f, 4f, 22, 0xFF20362E);
                addClouds(b, 0xFFDCE8F2, 0xFF9FBFD8);
                env.backdrop = b.toMesh();
                env.floor = floor(70f, 0xFF2C4227, 0xFF12200F);
                env.glow = sunDisc();
                break;
            }
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

    /** A three-colour wall box around the viewer, appended to {@code b}. */
    private static void addRoom(Buf b, float halfWidth, float halfDepth, float floorY,
                                float ceilingY, int topColor, int bottomColor, int ceiling) {
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
        b.quad(-halfWidth, ceilingY, -halfDepth, halfWidth, ceilingY, -halfDepth,
                -halfWidth, ceilingY, halfDepth, halfWidth, ceilingY, halfDepth,
                ceiling, ceiling);
    }

    /**
     * A dark frame on the front wall so the video has something to sit in; without
     * it the screen looks like it is floating in a box.
     */
    private static void addScreenSurround(Buf b, float halfWidth, float bottom, float top,
                                          int inner, int outer) {
        float z = -4.2f;
        float bar = 0.4f;
        // bottom bar
        b.quad(-halfWidth - bar, bottom - bar, z, halfWidth + bar, bottom - bar, z,
                -halfWidth - bar, bottom, z, halfWidth + bar, bottom, z, inner, outer);
        // top bar
        b.quad(-halfWidth - bar, top, z, halfWidth + bar, top, z,
                -halfWidth - bar, top + bar, z, halfWidth + bar, top + bar, z, outer, inner);
        // side bars
        b.quad(-halfWidth - bar, bottom, z, -halfWidth, bottom, z,
                -halfWidth - bar, top, z, -halfWidth, top, z, outer, outer);
        b.quad(halfWidth, bottom, z, halfWidth + bar, bottom, z,
                halfWidth, top, z, halfWidth + bar, top, z, outer, outer);
    }

    /** Two long light strips just under the ceiling. */
    private static void addCeilingStrips(Buf b, float halfWidth, float y, int color) {
        float z0 = -3.6f;
        float z1 = 3.6f;
        float w = 0.5f;
        for (int side = -1; side <= 1; side += 2) {
            float x = side * halfWidth * 0.55f;
            int edge = blend(color, 0xFF05080C, 0.75f);
            b.quad(x - w, y, z0, x + w, y, z0, x - w, y, z1, x + w, y, z1, color, edge);
        }
    }

    /** Small wall lights on both side walls. */
    private static void addSconces(Buf b, float x, float y, float spacing, int color) {
        float w = 0.35f;
        float h = 0.9f;
        int edge = blend(color, 0xFF08060A, 0.6f);
        for (int side = -1; side <= 1; side += 2) {
            float wallX = side * x;
            for (int i = -1; i <= 1; i++) {
                float z = i * spacing;
                b.quad(wallX, y, z - w, wallX, y, z + w,
                        wallX, y + h, z - w, wallX, y + h, z + w, color, edge);
            }
        }
    }

    /** Curtains flanking the screen: alternating vertical strips. */
    private static void addCurtains(Buf b, float xEdge, float bottom, float top,
                                    int light, int dark) {
        float z = -4.0f;
        int strips = 7;
        float width = 0.34f;
        for (int side = -1; side <= 1; side += 2) {
            for (int i = 0; i < strips; i++) {
                float x0 = side * (xEdge + i * width);
                float x1 = x0 + side * width * 0.92f;
                int color = (i % 2 == 0) ? light : dark;
                b.quad(x0, bottom, z, x1, bottom, z, x0, top, z, x1, top, z,
                        blend(color, 0xFF000000, 0.45f), color);
            }
        }
    }

    /** Rows of seat backs behind the viewer, so turning around is not a void. */
    private static void addSeats(Buf b, int columns, int rows, float zStart,
                                 int back, int dark) {
        float seatWidth = 0.52f;
        float seatHeight = 0.62f;
        float rowGap = 1.05f;
        float floorY = -1.6f;
        for (int r = 0; r < rows; r++) {
            float z = zStart + r * rowGap;
            float damp = 1f - r * 0.12f;
            int color = blend(back, dark, 1f - damp);
            for (int c = 0; c < columns; c++) {
                float x = (c - (columns - 1) * 0.5f) * seatWidth * 1.25f;
                float halfW = seatWidth * 0.5f;
                b.quad(x - halfW, floorY, z, x + halfW, floorY, z,
                        x - halfW, floorY + seatHeight, z, x + halfW, floorY + seatHeight, z,
                        color, blend(color, 0xFFFFFFFF, 0.12f));
            }
        }
    }

    /** A lighter patch of floor in front of the seats. */
    private static void addCarpet(Buf b, float zFront, float y, int color) {
        float w = 3.2f;
        float zBack = -1.2f;
        b.quad(-w, y, zFront, w, y, zFront, -w, y, zBack, w, y, zBack,
                blend(color, 0xFF000000, 0.5f), color);
    }

    /** A planet disc low on the horizon of the space environment. */
    private static Mesh planet(int body, int rim) {
        Buf b = new Buf(64 * 6);
        float cx = 26f;
        float cy = 12f;
        float cz = -40f;
        float radius = 9f;
        int segments = 28;
        for (int i = 0; i < segments; i++) {
            double a0 = 2 * Math.PI * i / segments;
            double a1 = 2 * Math.PI * (i + 1) / segments;
            b.vertex(cx, cy, cz, body);
            b.vertex(cx + (float) Math.cos(a0) * radius, cy + (float) Math.sin(a0) * radius, cz,
                    blend(body, rim, 0.5f));
            b.vertex(cx + (float) Math.cos(a1) * radius, cy + (float) Math.sin(a1) * radius, cz,
                    rim);
        }
        return b.toMesh();
    }

    /** A ring of mountain triangles. */
    private static void addMountainRing(Buf b, float radius, float baseY, float maxHeight,
                                        int peaks, int color) {
        int edge = blend(color, 0xFF7FA0B8, 0.35f);
        for (int i = 0; i < peaks; i++) {
            double a0 = 2 * Math.PI * i / peaks;
            double a1 = 2 * Math.PI * (i + 1) / peaks;
            double am = (a0 + a1) * 0.5;
            float h = baseY + 3f + (float) ((i * 37 % 11) * (maxHeight / 11f));
            b.vertex((float) Math.cos(a0) * radius, baseY, (float) Math.sin(a0) * radius, color);
            b.vertex((float) Math.cos(a1) * radius, baseY, (float) Math.sin(a1) * radius, color);
            b.vertex((float) Math.cos(am) * radius, h, (float) Math.sin(am) * radius, edge);
        }
    }

    /** Flat cloud slabs high above the ground. */
    private static void addClouds(Buf b, int top, int bottom) {
        float[][] clouds = {
                {-34f, 34f, -52f, 16f, 5f},
                {-6f, 40f, -60f, 20f, 6f},
                {28f, 30f, -48f, 14f, 4.5f},
                {46f, 38f, -18f, 18f, 5.5f},
                {-44f, 30f, 4f, 15f, 5f},
                {8f, 44f, 22f, 22f, 6f},
        };
        for (float[] c : clouds) {
            float x = c[0];
            float y = c[1];
            float z = c[2];
            float w = c[3] * 0.5f;
            float h = c[4] * 0.5f;
            b.quad(x - w, y - h, z, x + w, y - h, z, x - w, y + h, z, x + w, y + h, z,
                    bottom, top);
            b.quad(x - w * 0.7f, y + h, z, x + w * 0.7f, y + h, z,
                    x - w * 0.5f, y + h * 1.5f, z, x + w * 0.5f, y + h * 1.5f, z, top, top);
        }
    }

    private static int blend(int from, int to, float t) {
        return lerpColor(from, to, t);
    }

    /** Blue sky with a horizon haze. */
    private static void addWorldSky(Buf b) {
        float radius = 80f;
        int skyTop = 0xFF0B2A5A;
        int skyMid = 0xFF3E7AB4;
        int skyHorizon = 0xFFBFD8EA;
        int ground = 0xFF243521;

        int segments = 32;
        for (int i = 0; i < segments; i++) {
            double a0 = 2 * Math.PI * i / segments;
            double a1 = 2 * Math.PI * (i + 1) / segments;
            float x0 = (float) Math.cos(a0) * radius;
            float z0 = (float) Math.sin(a0) * radius;
            float x1 = (float) Math.cos(a1) * radius;
            float z1 = (float) Math.sin(a1) * radius;
            // horizon band
            b.quad(x0, -4f, z0, x1, -4f, z1, x0, 6f, z0, x1, 6f, z1, skyHorizon, skyMid);
            // upper sky
            b.quad(x0, 6f, z0, x1, 6f, z1, x0, radius, z0, x1, radius, z1, skyMid, skyTop);
            // haze strip just above the horizon so mountains do not float
            b.quad(x0, -2.4f, z0, x1, -2.4f, z1, x0, -4f, z0, x1, -4f, z1, ground, skyHorizon);
        }
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
