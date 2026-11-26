package com.tuvr.app.vr;

import android.opengl.GLES20;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

/**
 * A vertex buffer object with interleaved float attributes and optional indices.
 * One instance owns its GL buffers and must be released on the GL thread.
 */
public final class Mesh {

    public static final int ATTR_POSITION = 0;
    public static final int ATTR_UV = 1;
    public static final int ATTR_COLOR = 2;
    public static final int ATTR_SIZE = 3;

    private int vbo;
    private int ibo;
    private int indexCount;
    private int vertexCount;
    private final int strideFloats;
    private boolean released;

    private Mesh(int strideFloats) {
        this.strideFloats = strideFloats;
    }

    /**
     * Builds an interleaved mesh.
     *
     * @param vertices interleaved floats
     * @param stride   floats per vertex
     * @param indices  triangle indices, may be null
     */
    public static Mesh create(float[] vertices, int stride, short[] indices) {
        Mesh mesh = new Mesh(stride);
        mesh.vertexCount = vertices.length / stride;
        FloatBuffer vertexBuffer = ByteBuffer.allocateDirect(vertices.length * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        vertexBuffer.put(vertices).position(0);

        int[] buffers = new int[1];
        GLES20.glGenBuffers(1, buffers, 0);
        mesh.vbo = buffers[0];
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mesh.vbo);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vertices.length * 4,
                vertexBuffer, GLES20.GL_STATIC_DRAW);

        if (indices != null && indices.length > 0) {
            mesh.indexCount = indices.length;
            ShortBuffer indexBuffer = ByteBuffer.allocateDirect(indices.length * 2)
                    .order(ByteOrder.nativeOrder()).asShortBuffer();
            indexBuffer.put(indices).position(0);
            int[] ibos = new int[1];
            GLES20.glGenBuffers(1, ibos, 0);
            mesh.ibo = ibos[0];
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, mesh.ibo);
            GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, indices.length * 2,
                    indexBuffer, GLES20.GL_STATIC_DRAW);
        }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
        return mesh;
    }

    /** Binds the buffer and enables the given attribute layout. */
    public void bind(int[] attributeSizes) {
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo);
        int offsetBytes = 0;
        for (int i = 0; i < attributeSizes.length; i++) {
            if (attributeSizes[i] <= 0) {
                continue;
            }
            GLES20.glEnableVertexAttribArray(i);
            GLES20.glVertexAttribPointer(i, attributeSizes[i], GLES20.GL_FLOAT, false,
                    strideFloats * 4, offsetBytes);
            offsetBytes += attributeSizes[i] * 4;
        }
        if (ibo != 0) {
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ibo);
        }
    }

    public void draw(int[] attributeSizes) {
        if (released) {
            return;
        }
        bind(attributeSizes);
        if (indexCount > 0) {
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, indexCount, GLES20.GL_UNSIGNED_SHORT, 0);
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
        } else {
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount);
        }
        unbind(attributeSizes);
    }

    /** Draws as GL_LINES (used for the ambient grid floor). */
    public void drawLines(int[] attributeSizes) {
        if (released) {
            return;
        }
        bind(attributeSizes);
        if (indexCount > 0) {
            GLES20.glDrawElements(GLES20.GL_LINES, indexCount, GLES20.GL_UNSIGNED_SHORT, 0);
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
        } else {
            GLES20.glDrawArrays(GLES20.GL_LINES, 0, vertexCount);
        }
        unbind(attributeSizes);
    }

    /** Draws as points (starfield). */
    public void drawPoints(int[] attributeSizes) {
        if (released) {
            return;
        }
        bind(attributeSizes);
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, vertexCount);
        unbind(attributeSizes);
    }

    private void unbind(int[] attributeSizes) {
        for (int i = 0; i < attributeSizes.length; i++) {
            if (attributeSizes[i] <= 0) {
                continue;
            }
            GLES20.glDisableVertexAttribArray(i);
        }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
    }

    public void release() {
        if (released) {
            return;
        }
        released = true;
        if (vbo != 0) {
            GLES20.glDeleteBuffers(1, new int[]{vbo}, 0);
            vbo = 0;
        }
        if (ibo != 0) {
            GLES20.glDeleteBuffers(1, new int[]{ibo}, 0);
            ibo = 0;
        }
    }
}
