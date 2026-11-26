package com.tuvr.app.vr;

import android.graphics.SurfaceTexture;
import android.view.Surface;

/**
 * Owns the external OES texture and SurfaceTexture that the video decoder
 * renders into. All methods except {@link #surface()} must be called on the
 * GL thread.
 */
public final class VideoTexture {

    private int textureId;
    private SurfaceTexture surfaceTexture;
    private Surface surface;
    private final boolean[] frameAvailable = {false};
    public final float[] transform = new float[16];

    public boolean isReady() {
        return textureId != 0 && surfaceTexture != null;
    }

    public void create() {
        if (isReady()) {
            return;
        }
        textureId = GlUtil.externalTexture();
        surfaceTexture = new SurfaceTexture(textureId);
        surfaceTexture.setOnFrameAvailableListener(st -> frameAvailable[0] = true);
        MathUtil.identity(transform);
        surface = new Surface(surfaceTexture);
    }

    /** Returns the surface the player should render into (create() must have run). */
    public Surface surface() {
        return surface;
    }

    public int textureId() {
        return textureId;
    }

    /** Pulls the newest decoded frame, if any. Returns true when a frame was updated. */
    public boolean update() {
        if (!isReady() || !frameAvailable[0]) {
            return false;
        }
        frameAvailable[0] = false;
        try {
            surfaceTexture.updateTexImage();
            surfaceTexture.getTransformMatrix(transform);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public void release() {
        if (surface != null) {
            surface.release();
            surface = null;
        }
        if (surfaceTexture != null) {
            surfaceTexture.release();
            surfaceTexture = null;
        }
        if (textureId != 0) {
            android.opengl.GLES20.glDeleteTextures(1, new int[]{textureId}, 0);
            textureId = 0;
        }
    }
}
