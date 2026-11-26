package com.tuvr.app.vr;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/** Small OpenGL ES 2.0 helper: shader programs, textures and asset loading. */
public final class GlUtil {

    private static final String TAG = "TuVrGl";

    private GlUtil() {
    }

    /* ---------------- shaders ---------------- */

    public static int compileShader(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        int[] status = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0);
        if (status[0] == 0) {
            String log = GLES20.glGetShaderInfoLog(shader);
            GLES20.glDeleteShader(shader);
            Log.e(TAG, "Shader compile failed: " + log + "\n" + source);
            return 0;
        }
        return shader;
    }

    public static int linkProgram(int vertexShader, int fragmentShader) {
        int program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vertexShader);
        GLES20.glAttachShader(program, fragmentShader);
        // Fixed attribute slots so Mesh can bind buffers without name lookups.
        GLES20.glBindAttribLocation(program, Mesh.ATTR_POSITION, "aPos");
        GLES20.glBindAttribLocation(program, Mesh.ATTR_UV, "aUv");
        GLES20.glBindAttribLocation(program, Mesh.ATTR_COLOR, "aColor");
        GLES20.glBindAttribLocation(program, Mesh.ATTR_SIZE, "aSize");
        GLES20.glLinkProgram(program);
        int[] status = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0);
        if (status[0] == 0) {
            String log = GLES20.glGetProgramInfoLog(program);
            GLES20.glDeleteProgram(program);
            Log.e(TAG, "Program link failed: " + log);
            return 0;
        }
        GLES20.glDeleteShader(vertexShader);
        GLES20.glDeleteShader(fragmentShader);
        return program;
    }

    public static String asset(AssetManager assets, String path) {
        StringBuilder sb = new StringBuilder();
        try (InputStream in = assets.open(path);
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } catch (IOException e) {
            Log.e(TAG, "Cannot read asset " + path, e);
        }
        return sb.toString();
    }

    /** Compiles a program from assets/shaders/name.vert + name.frag. */
    public static Program program(AssetManager assets, String name) {
        String vertexSrc = asset(assets, "shaders/" + name + ".vert");
        String fragmentSrc = asset(assets, "shaders/" + name + ".frag");
        int vs = compileShader(GLES20.GL_VERTEX_SHADER, vertexSrc);
        int fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSrc);
        if (vs == 0 || fs == 0) {
            return null;
        }
        int id = linkProgram(vs, fs);
        if (id == 0) {
            return null;
        }
        return new Program(id);
    }

    /** A linked program with cached uniform locations. */
    public static final class Program {
        public final int id;
        private final Map<String, Integer> uniforms = new HashMap<>();

        Program(int id) {
            this.id = id;
        }

        public void use() {
            GLES20.glUseProgram(id);
        }

        public int handle(String name) {
            Integer cached = uniforms.get(name);
            if (cached == null) {
                cached = GLES20.glGetUniformLocation(id, name);
                uniforms.put(name, cached);
            }
            return cached;
        }

        public void setMatrix(String name, float[] matrix) {
            GLES20.glUniformMatrix4fv(handle(name), 1, false, matrix, 0);
        }

        public void set1f(String name, float value) {
            GLES20.glUniform1f(handle(name), value);
        }

        public void set2f(String name, float x, float y) {
            GLES20.glUniform2f(handle(name), x, y);
        }

        public void set3f(String name, float x, float y, float z) {
            GLES20.glUniform3f(handle(name), x, y, z);
        }

        public void set4f(String name, float x, float y, float z, float w) {
            GLES20.glUniform4f(handle(name), x, y, z, w);
        }

        public void set1i(String name, int value) {
            GLES20.glUniform1i(handle(name), value);
        }

        public void delete() {
            GLES20.glDeleteProgram(id);
        }
    }

    /* ---------------- textures ---------------- */

    /** Creates a 2D texture and uploads the bitmap. Returns the texture id. */
    public static int textureFromBitmap(Bitmap bitmap) {
        int[] ids = new int[1];
        GLES20.glGenTextures(1, ids, 0);
        int id = ids[0];
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        android.opengl.GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        return id;
    }

    public static void updateTexture(int textureId, Bitmap bitmap) {
        if (textureId == 0 || bitmap == null || bitmap.isRecycled()) {
            return;
        }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
        android.opengl.GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, bitmap);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
    }

    /** Creates an empty 2D texture of the given size (used for eye buffers). */
    public static int emptyTexture(int width, int height) {
        int[] ids = new int[1];
        GLES20.glGenTextures(1, ids, 0);
        int id = ids[0];
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        return id;
    }

    /** Creates the external (video) texture used with SurfaceTexture. */
    public static int externalTexture() {
        int[] ids = new int[1];
        GLES20.glGenTextures(1, ids, 0);
        int id = ids[0];
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, id);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);
        return id;
    }

    /* ---------------- framebuffers ---------------- */

    /** Simple offscreen render target with a depth buffer. */
    public static final class Framebuffer {
        public int framebufferId;
        public int textureId;
        public int depthBufferId;
        public int width;
        public int height;

        public boolean ensure(int newWidth, int newHeight) {
            int w = Math.max(2, newWidth);
            int h = Math.max(2, newHeight);
            if (framebufferId != 0 && w == width && h == height) {
                return false;
            }
            release();
            width = w;
            height = h;

            textureId = emptyTexture(w, h);

            int[] fbo = new int[1];
            GLES20.glGenFramebuffers(1, fbo, 0);
            framebufferId = fbo[0];
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebufferId);
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER,
                    GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, textureId, 0);

            int[] depth = new int[1];
            GLES20.glGenRenderbuffers(1, depth, 0);
            depthBufferId = depth[0];
            GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, depthBufferId);
            GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER, GLES20.GL_DEPTH_COMPONENT16, w, h);
            GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER,
                    GLES20.GL_DEPTH_ATTACHMENT, GLES20.GL_RENDERBUFFER, depthBufferId);

            int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
            return status == GLES20.GL_FRAMEBUFFER_COMPLETE;
        }

        public void release() {
            if (framebufferId != 0) {
                GLES20.glDeleteFramebuffers(1, new int[]{framebufferId}, 0);
                framebufferId = 0;
            }
            if (textureId != 0) {
                GLES20.glDeleteTextures(1, new int[]{textureId}, 0);
                textureId = 0;
            }
            if (depthBufferId != 0) {
                GLES20.glDeleteRenderbuffers(1, new int[]{depthBufferId}, 0);
                depthBufferId = 0;
            }
            width = 0;
            height = 0;
        }
    }

    public static void checkGlError(String tag) {
        int error = GLES20.glGetError();
        if (error != GLES20.GL_NO_ERROR) {
            Log.e(TAG, tag + " glError 0x" + Integer.toHexString(error));
        }
    }
}
