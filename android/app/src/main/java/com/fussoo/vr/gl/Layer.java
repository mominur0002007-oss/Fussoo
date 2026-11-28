package com.fussoo.vr.gl;

import android.opengl.GLES20;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * Batched textured quads, all sampling {@link Atlas}.
 *
 * <p>Positions are given in metres in the plane they belong to. The caller passes a model
 * matrix per surface, so the same layer class draws a floating menu panel, a curved video
 * wall and a poster shelf without knowing the difference.</p>
 */
public final class Layer {

    private static final int F = 5;              // x y u v alpha
    private static final int MAX_VERTS = 12000;

    private final FloatBuffer buf;
    private int verts;

    private final int program;
    private final int uMvp;
    private final int uTex;
    private final Atlas atlas;

    public Layer(Atlas atlas) {
        this.atlas = atlas;
        ByteBuffer bb = ByteBuffer.allocateDirect(MAX_VERTS * F * 4);
        bb.order(ByteOrder.nativeOrder());
        buf = bb.asFloatBuffer();
        program = Gl.program(Sh.HUD_VS, Sh.HUD_FS);
        uMvp = GLES20.glGetUniformLocation(program, "uMvp");
        uTex = GLES20.glGetUniformLocation(program, "uTex");
    }

    public void begin() {
        buf.clear();
        verts = 0;
    }

    public void clear() {
        begin();
    }

    /** Adds a quad by explicit corners, in the current surface's local metres. */
    public void quad(Atlas.Region r, float x0, float y0, float x1, float y1, float alpha) {
        if (r == null || alpha <= 0.004f || verts + 6 > MAX_VERTS) {
            return;
        }
        put(x0, y0, r.u0, r.v0, alpha);
        put(x1, y0, r.u1, r.v0, alpha);
        put(x1, y1, r.u1, r.v1, alpha);
        put(x0, y0, r.u0, r.v0, alpha);
        put(x1, y1, r.u1, r.v1, alpha);
        put(x0, y1, r.u0, r.v1, alpha);
        verts += 6;
    }

    /** Centres a region in a box, preserving its aspect ratio. */
    public void fit(Atlas.Region r, float cx, float cy, float boxW, float boxH, float alpha) {
        if (r == null) {
            return;
        }
        float ar = r.aspect();
        float w = boxW;
        float h = w / ar;
        if (h > boxH) {
            h = boxH;
            w = h * ar;
        }
        quad(r, cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f, alpha);
    }

    /** Draws text left-aligned with its vertical centre at {@code cy}. */
    public void text(Atlas.Region r, float x, float cy, float height, float alpha) {
        if (r == null) {
            return;
        }
        float w = height * r.aspect();
        quad(r, x, cy - height / 2f, x + w, cy + height / 2f, alpha);
    }

    /** Draws a region rotated about its centre, in radians. Used for the spinner. */
    public void quadRotated(Atlas.Region r, float cx, float cy, float half, float deg) {
        if (r == null) {
            return;
        }
        double a = Math.toRadians(deg);
        float c = (float) Math.cos(a);
        float s = (float) Math.sin(a);
        float[][] corners = {{-half, -half}, {half, -half}, {half, half}, {-half, half}};
        float[] px = new float[4];
        float[] py = new float[4];
        for (int i = 0; i < 4; i++) {
            px[i] = cx + corners[i][0] * c - corners[i][1] * s;
            py[i] = cy + corners[i][0] * s + corners[i][1] * c;
        }
        put(px[0], py[0], r.u0, r.v0, 1f);
        put(px[1], py[1], r.u1, r.v0, 1f);
        put(px[2], py[2], r.u1, r.v1, 1f);
        put(px[0], py[0], r.u0, r.v0, 1f);
        put(px[2], py[2], r.u1, r.v1, 1f);
        put(px[3], py[3], r.u0, r.v1, 1f);
        verts += 6;
    }

    public float textWidth(Atlas.Region r, float height) {
        return r == null ? 0f : height * r.aspect();
    }

    public void textCentred(Atlas.Region r, float cx, float cy, float height, float alpha) {
        if (r == null) {
            return;
        }
        float w = height * r.aspect();
        quad(r, cx - w / 2f, cy - height / 2f, cx + w / 2f, cy + height / 2f, alpha);
    }

    private void put(float x, float y, float u, float v, float a) {
        buf.put(x).put(y).put(u).put(v).put(a);
    }

    public void draw(float[] mvp) {
        draw(mvp, atlas.texture());
    }

    /** Draws with an explicit texture (the atlas, or a poster bitmap uploaded to one). */
    public void draw(float[] mvp, int tex) {
        if (verts == 0) {
            return;
        }
        GLES20.glUseProgram(program);
        GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0);
        GLES20.glUniform1i(uTex, 0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex == 0 ? atlas.texture() : tex);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);

        buf.position(0);
        int p = GLES20.glGetAttribLocation(program, "aPos");
        int uv = GLES20.glGetAttribLocation(program, "aUv");
        int al = GLES20.glGetAttribLocation(program, "aAlpha");
        int stride = F * 4;
        GLES20.glEnableVertexAttribArray(p);
        GLES20.glEnableVertexAttribArray(uv);
        GLES20.glEnableVertexAttribArray(al);
        GLES20.glVertexAttribPointer(p, 2, GLES20.GL_FLOAT, false, stride, buf);
        buf.position(2);
        GLES20.glVertexAttribPointer(uv, 2, GLES20.GL_FLOAT, false, stride, buf);
        buf.position(4);
        GLES20.glVertexAttribPointer(al, 1, GLES20.GL_FLOAT, false, stride, buf);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, verts);
        GLES20.glDisableVertexAttribArray(p);
        GLES20.glDisableVertexAttribArray(uv);
        GLES20.glDisableVertexAttribArray(al);

        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDisable(GLES20.GL_BLEND);
    }

    public void release() {
        GLES20.glDeleteProgram(program);
    }
}
