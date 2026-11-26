package com.tuvr.app.vr;

/**
 * Generates the small set of procedural meshes used by the VR renderer.
 * No model loading, no large vertex buffers, nothing that hurts a 4 GB phone.
 */
public final class MeshBuilder {

    private MeshBuilder() {
    }

    /** Unit quad in the XY plane, +-1, with UVs 0..1 (v up). */
    public static Mesh unitQuad() {
        float[] vertices = {
                // x, y, z, u, v
                -1f, -1f, 0f, 0f, 0f,
                1f, -1f, 0f, 1f, 0f,
                1f, 1f, 0f, 1f, 1f,
                -1f, 1f, 0f, 0f, 1f
        };
        short[] indices = {0, 1, 2, 0, 2, 3};
        return Mesh.create(vertices, 5, indices);
    }

    /** Flat quad of the given size centred at the origin (UVs 0..1). */
    public static Mesh quad(float width, float height) {
        float hw = width * 0.5f;
        float hh = height * 0.5f;
        float[] vertices = {
                -hw, -hh, 0f, 0f, 0f,
                hw, -hh, 0f, 1f, 0f,
                hw, hh, 0f, 1f, 1f,
                -hw, hh, 0f, 0f, 1f
        };
        short[] indices = {0, 1, 2, 0, 2, 3};
        return Mesh.create(vertices, 5, indices);
    }

    /**
     * Curved screen: a horizontal arc where 0 = flat and 1 = strong curve.
     * Kept low-poly (up to 24 segments) so it costs almost nothing.
     */
    public static Mesh curvedQuad(float width, float height, float curvature) {
        int segments = curvature < 0.02f ? 1 : 16;
        float hw = width * 0.5f;
        float hh = height * 0.5f;

        float depth = curvature * width * 0.35f;
        float[] vertices = new float[(segments + 1) * 2 * 5];
        int v = 0;
        for (int i = 0; i <= segments; i++) {
            float t = i / (float) segments;
            float x = -hw + width * t;
            float z = (float) (depth * (Math.cos((t - 0.5) * Math.PI) * -1.0 + 1.0) * 0.5);
            // vertical pair (bottom, top)
            vertices[v++] = x;
            vertices[v++] = -hh;
            vertices[v++] = z;
            vertices[v++] = t;
            vertices[v++] = 0f;

            vertices[v++] = x;
            vertices[v++] = hh;
            vertices[v++] = z;
            vertices[v++] = t;
            vertices[v++] = 1f;
        }

        short[] indices = new short[segments * 6];
        int idx = 0;
        for (int i = 0; i < segments; i++) {
            short base = (short) (i * 2);
            indices[idx++] = base;
            indices[idx++] = (short) (base + 1);
            indices[idx++] = (short) (base + 2);
            indices[idx++] = (short) (base + 1);
            indices[idx++] = (short) (base + 3);
            indices[idx++] = (short) (base + 2);
        }
        return Mesh.create(vertices, 5, indices);
    }

    /**
     * Sphere for 360 degree video, viewed from the inside.
     * UVs are equirectangular: u = longitude, v = latitude.
     */
    public static Mesh sphere(float radius, int longitudeSegments, int latitudeSegments) {
        int lon = Math.max(8, longitudeSegments);
        int lat = Math.max(4, latitudeSegments);
        float[] vertices = new float[(lon + 1) * (lat + 1) * 5];
        int v = 0;
        for (int y = 0; y <= lat; y++) {
            float vCoord = y / (float) lat;
            float phi = (float) Math.PI * (1f - vCoord); // 0..PI
            float sinPhi = (float) Math.sin(phi);
            float cosPhi = (float) Math.cos(phi);
            for (int x = 0; x <= lon; x++) {
                float uCoord = x / (float) lon;
                float theta = (float) (2.0 * Math.PI * uCoord);
                float sinTheta = (float) Math.sin(theta);
                float cosTheta = (float) Math.cos(theta);
                vertices[v++] = -radius * sinPhi * cosTheta;
                vertices[v++] = radius * cosPhi;
                vertices[v++] = radius * sinPhi * sinTheta;
                vertices[v++] = 1f - uCoord;
                vertices[v++] = vCoord;
            }
        }
        short[] indices = new short[lon * lat * 6];
        int idx = 0;
        for (int y = 0; y < lat; y++) {
            for (int x = 0; x < lon; x++) {
                short first = (short) (y * (lon + 1) + x);
                short second = (short) (first + 1);
                short third = (short) (first + lon + 1);
                short fourth = (short) (third + 1);
                indices[idx++] = first;
                indices[idx++] = third;
                indices[idx++] = second;
                indices[idx++] = second;
                indices[idx++] = third;
                indices[idx++] = fourth;
            }
        }
        return Mesh.create(vertices, 5, indices);
    }

    /** Half sphere (front 180 degrees) for VR180 content. */
    public static Mesh hemisphere(float radius, int longitudeSegments, int latitudeSegments) {
        int lon = Math.max(8, longitudeSegments);
        int lat = Math.max(4, latitudeSegments);
        float[] vertices = new float[(lon + 1) * (lat + 1) * 5];
        int v = 0;
        for (int y = 0; y <= lat; y++) {
            float vCoord = y / (float) lat;
            float phi = (float) Math.PI * (1f - vCoord);
            float sinPhi = (float) Math.sin(phi);
            float cosPhi = (float) Math.cos(phi);
            for (int x = 0; x <= lon; x++) {
                float uCoord = x / (float) lon;
                // Only the front hemisphere: theta in [-PI/2, PI/2]
                float theta = (float) (-Math.PI * 0.5 + Math.PI * uCoord);
                float sinTheta = (float) Math.sin(theta);
                float cosTheta = (float) Math.cos(theta);
                vertices[v++] = -radius * sinPhi * cosTheta;
                vertices[v++] = radius * cosPhi;
                vertices[v++] = radius * sinPhi * sinTheta;
                vertices[v++] = 1f - uCoord;
                vertices[v++] = vCoord;
            }
        }
        short[] indices = new short[lon * lat * 6];
        int idx = 0;
        for (int y = 0; y < lat; y++) {
            for (int x = 0; x < lon; x++) {
                short first = (short) (y * (lon + 1) + x);
                short second = (short) (first + 1);
                short third = (short) (first + lon + 1);
                short fourth = (short) (third + 1);
                indices[idx++] = first;
                indices[idx++] = third;
                indices[idx++] = second;
                indices[idx++] = second;
                indices[idx++] = third;
                indices[idx++] = fourth;
            }
        }
        return Mesh.create(vertices, 5, indices);
    }
}
