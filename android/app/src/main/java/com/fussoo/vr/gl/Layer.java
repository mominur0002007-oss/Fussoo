package com.fussoo.vr.gl;

import android.opengl.GLES20;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * One batched 2D layer sampling {@link Atlas}. Coordinates are screen-local metres:
 * (0,0) at the centre of the screen, +x right, +y up.
 */
public final class Layer {

    private static final int F = 5;   // x y u v a
    private static final int MAXV = 9000;

    private final FloatBuffer buf;
    private int verts;
    private final int program;
    private final int uMvp;
    private final int uTex;
    private final Atlas atlas;

    public Layer(Atlas atlas) {
        this.atlas = atlas;
        ByteBuffer bb = ByteBuffer.allocateDirect(MAXV * F * 4);
        bb.order(ByteOrder.nativeOrder());
        buf = bb.asFloatBuffer();
        program = Gl.program(Sh.HUD_VS, Sh.HUD_FS);
        uMvp = GLES20.glGetUniformLocation(program, "uMvp");
        uTex = GLES20.glGetUniformLocation(program, "uTex");
    }

    public void clear() {
        buf.clear();
        verts = 0;
    }

    public void quad(Atlas.Region r, float x0, float y0, float x1, float y1, float a) {
        if (r == null || verts + 6 > MAXV || a <= 0.004f) {
            return;
        }
        put(x0, y0, r.u0, r.v0, a);
        put(x1, y0, r.u1, r.v0, a);
        put(x1, y1, r.u1, r.v1, a);
        put(x0, y0, r.u0, r.v0, a);
        put(x1, y1, r.u1, r.v1, a);
        put(x0, y1, r.u0, r.v1, a);
        verts += 6;
    }

    /** Centres a region in a box, letterboxed to keep its aspect ratio. */
    public void fit(Atlas.Region r, float cx, float cy, float boxW, float boxH, float a) {
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
        quad(r, cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f, a);
    }

    /** Draws a region with its left edge at x and its baseline vertically centred at cy. */
    public void text(Atlas.Region r, float x, float cy, float height, float a) {
        if (r == null) {
            return;
        }
        float w = height * r.aspect();
        quad(r, x, cy - height / 2f, x + w, cy + height / 2f, a);
    }

    public float textWidth(Atlas.Region r, float height) {
        return r == null ? 0f : height * r.aspect();
    }

    private void put(float x, float y, float u, float v, float a) {
        buf.put(x).put(y).put(u).put(v).put(a);
    }

    public void draw(float[] mvp) {
        if (verts == 0) {
            return;
        }
        GLES20.glUseProgram(program);
        GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0);
        GLES20.glUniform1i(uTex, 0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, atlas.texture());
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);

        buf.position(0);
        int p = GLES20.glGetAttribLocation(program, "aPos");
        int uv = GLES20.glGetAttribLocation(program, "aUv");
        int al = GLES20.glGetAttribLocation(program, "aA");
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
