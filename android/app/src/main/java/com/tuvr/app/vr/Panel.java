package com.tuvr.app.vr;

import android.graphics.Bitmap;

/**
 * A flat, textured rectangle floating in the VR scene.
 *
 * Panels are always world-locked (placed on a sphere around the user) so that
 * head gaze can select them. They are the building block of the VR home screen,
 * the VR gallery, the cinema controls and the browser chrome.
 */
public final class Panel {

    public final String id;
    public float widthM = 1.0f;
    public float heightM = 0.5f;
    public float yawDeg = 0f;
    public float pitchDeg = 0f;
    public float distanceM = 3f;
    public boolean interactive = true;
    public boolean visible = true;
    public Object tag;

    /** Texture content; may be replaced at any time (texture is re-uploaded). */
    public Bitmap content;
    /** Previous bitmap, recycled on the GL thread right after its upload. */
    public Bitmap pendingRecycle;
    /** When false the panel never recycles bitmaps, because the owner does. */
    public boolean recycleOnReplace = true;
    public boolean contentDirty = true;
    public int textureId;
    public float alpha = 1f;
    public boolean highlighted;

    /* Derived world-space basis, refreshed by updateBasis(). */
    public final float[] center = new float[3];
    public final float[] normal = {0f, 0f, 1f};
    public final float[] right = {1f, 0f, 0f};
    public final float[] up = {0f, 1f, 0f};
    public final float[] model = new float[16];

    public Panel(String id) {
        this.id = id;
        MathUtil.identity(model);
    }

    /** Recomputes position and orientation from yaw/pitch/distance. */
    public void updateBasis() {
        float yaw = yawDeg * MathUtil.DEG_TO_RAD;
        float pitch = pitchDeg * MathUtil.DEG_TO_RAD;
        float cosPitch = (float) Math.cos(pitch);
        // Direction from the origin (user) towards the panel centre.
        normal[0] = (float) (Math.sin(yaw) * cosPitch);
        normal[1] = (float) Math.sin(pitch);
        normal[2] = (float) (-Math.cos(yaw) * cosPitch);
        MathUtil.normalize(normal);

        center[0] = normal[0] * distanceM;
        center[1] = normal[1] * distanceM;
        center[2] = normal[2] * distanceM;

        // Right vector: horizontal, perpendicular to the view direction.
        right[0] = (float) Math.cos(yaw);
        right[1] = 0f;
        right[2] = (float) Math.sin(yaw);
        // Up vector: cross(right, normal) yields a screen-space "up".
        MathUtil.cross(normal, right, up);
        if (up[1] < 0f) {
            up[0] = -up[0];
            up[1] = -up[1];
            up[2] = -up[2];
        }
        MathUtil.normalize(up);
        // Re-orthogonalise right so it stays perpendicular to both.
        MathUtil.cross(up, normal, right);
        MathUtil.normalize(right);

        // Model matrix: quad (+-1) -> panel rectangle facing the user.
        float hw = widthM * 0.5f;
        float hh = heightM * 0.5f;
        model[0] = right[0] * hw;
        model[1] = right[1] * hw;
        model[2] = right[2] * hw;
        model[3] = 0f;

        model[4] = up[0] * hh;
        model[5] = up[1] * hh;
        model[6] = up[2] * hh;
        model[7] = 0f;

        model[8] = -normal[0];
        model[9] = -normal[1];
        model[10] = -normal[2];
        model[11] = 0f;

        model[12] = center[0];
        model[13] = center[1];
        model[14] = center[2];
        model[15] = 1f;
    }

    /**
     * Tests whether a world-space ray from the origin hits this panel.
     *
     * @param dirX normalised gaze direction
     * @return distance along the ray, or -1 when missed
     */
    public float hitTest(float dirX, float dirY, float dirZ) {
        if (!interactive || !visible) {
            return -1f;
        }
        // Panel plane: dot(p - center, normal) = 0 -> t = dot(center, normal) / dot(dir, normal)
        float denom = dirX * (-normal[0]) + dirY * (-normal[1]) + dirZ * (-normal[2]);
        if (Math.abs(denom) < 1e-5f) {
            return -1f;
        }
        float t = -(center[0] * -normal[0] + center[1] * -normal[1] + center[2] * -normal[2]) / denom;
        if (t <= 0.05f) {
            return -1f;
        }
        float px = dirX * t - center[0];
        float py = dirY * t - center[1];
        float pz = dirZ * t - center[2];
        float localX = px * right[0] + py * right[1] + pz * right[2];
        float localY = px * up[0] + py * up[1] + pz * up[2];
        if (Math.abs(localX) <= widthM * 0.5f && Math.abs(localY) <= heightM * 0.5f) {
            return t;
        }
        return -1f;
    }

    public void setContent(Bitmap bitmap) {
        if (content != null && content != bitmap && recycleOnReplace) {
            if (pendingRecycle != null && !pendingRecycle.isRecycled()) {
                pendingRecycle.recycle();
            }
            pendingRecycle = content;
        }
        this.content = bitmap;
        this.contentDirty = true;
    }

    public void release() {
        // GL resources are released by the renderer on the GL thread.
        if (recycleOnReplace && content != null && !content.isRecycled()) {
            content.recycle();
        }
        if (recycleOnReplace && pendingRecycle != null && !pendingRecycle.isRecycled()) {
            pendingRecycle.recycle();
        }
        content = null;
        pendingRecycle = null;
        textureId = 0;
    }
}
