package com.fussoo.vr.gl;

import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.view.Surface;

/** The decoder's OES texture and Surface. */
public final class VideoTex implements SurfaceTexture.OnFrameAvailableListener {

    private int tex;
    private SurfaceTexture st;
    private Surface surface;
    private volatile boolean fresh;
    private volatile long lastFrameMs;

    public void init() {
        int[] ids = new int[1];
        GLES20.glGenTextures(1, ids, 0);
        tex = ids[0];
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, tex);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        st = new SurfaceTexture(tex);
        st.setDefaultBufferSize(1280, 720);
        st.setOnFrameAvailableListener(this);
        surface = new Surface(st);
    }

    public Surface surface() {
        return surface;
    }

    public int texture() {
        return tex;
    }

    @Override
    public void onFrameAvailable(SurfaceTexture s) {
        fresh = true;
        lastFrameMs = System.currentTimeMillis();
    }

    public boolean update() {
        if (st == null || !fresh) {
            return false;
        }
        fresh = false;
        try {
            st.updateTexImage();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void size(int w, int h) {
        if (st != null && w > 0 && h > 0) {
            st.setDefaultBufferSize(w, h);
        }
    }

    public boolean hasFrame() {
        return lastFrameMs > 0;
    }

    public void release() {
        if (surface != null) {
            surface.release();
            surface = null;
        }
        if (st != null) {
            st.release();
            st = null;
        }
        if (tex != 0) {
            GLES20.glDeleteTextures(1, new int[]{tex}, 0);
            tex = 0;
        }
    }
}
