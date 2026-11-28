package com.fussoo.vr.gl;

import android.opengl.GLES20;
import android.util.Log;

/** Shader compile/link helpers with readable failures. */
public final class Gl {

    private static final String TAG = "FussooVR";

    private Gl() {
    }

    public static int program(String vs, String fs) {
        int v = shader(GLES20.GL_VERTEX_SHADER, vs);
        int f = shader(GLES20.GL_FRAGMENT_SHADER, fs);
        int p = GLES20.glCreateProgram();
        GLES20.glAttachShader(p, v);
        GLES20.glAttachShader(p, f);
        GLES20.glLinkProgram(p);
        int[] ok = new int[1];
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) {
            String log = GLES20.glGetProgramInfoLog(p);
            Log.e(TAG, "link: " + log);
            GLES20.glDeleteProgram(p);
            GLES20.glDeleteShader(v);
            GLES20.glDeleteShader(f);
            throw new IllegalStateException("Program link failed: " + log);
        }
        GLES20.glDeleteShader(v);
        GLES20.glDeleteShader(f);
        return p;
    }

    private static int shader(int type, String src) {
        int id = GLES20.glCreateShader(type);
        GLES20.glShaderSource(id, src);
        GLES20.glCompileShader(id);
        int[] ok = new int[1];
        GLES20.glGetShaderiv(id, GLES20.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) {
            String log = GLES20.glGetShaderInfoLog(id);
            Log.e(TAG, "compile: " + log);
            GLES20.glDeleteShader(id);
            throw new IllegalStateException("Shader failed: " + log);
        }
        return id;
    }
}
