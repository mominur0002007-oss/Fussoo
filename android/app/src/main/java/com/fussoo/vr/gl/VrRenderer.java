package com.fussoo.vr.gl;

import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.view.Surface;

import com.fussoo.vr.core.Tracker;
import com.fussoo.vr.core.WorldModel;

import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Stereo renderer for the theatre.
 *
 * <p>Per eye: room shell, the main board (video wall or poster wall), the control console,
 * the bottom navigation rail, all drawn in world space so they sit in the room rather than
 * floating on the screen. Then one full-screen pass applies the lens pre-distortion.</p>
 *
 * <p>Resolution is deliberately traded for frame rate. Both eyes render into a buffer that
 * is 62% of the panel by default; through a moulded lens that is not visible, and on a
 * Mali-G57 it is the difference between a stuttering 30 and a locked 60.</p>
 */
public final class VrRenderer implements GLSurfaceView.Renderer {

    public interface Callback {
        void onGlReady();

        void onFps(float fps);
    }

    private final VideoTex video;
    private final Tracker tracker;
    private final WorldModel model = new WorldModel();
    private final WorldModel snap = new WorldModel();
    private final Scene scene = new Scene();

    private Callback callback;

    private Atlas atlas;
    private Layer layer;
    private FloatBuffer quadVerts;
    private ShortBuffer quadIdx;
    private FloatBuffer sphereVerts;
    private ShortBuffer sphereIdx;
    private FloatBuffer curvedWallVerts;
    private ShortBuffer curvedWallIdx;

    private int pShell, uShellMvp, uShellWall, uShellFloor, uShellAccent, uShellTime;
    private int pVideo, uVidMvp, uVidW, uVidH, uVidCurve, uVidDim, uVidHas, uVidTex;
    private int pWarp, uWarpTex, uWarpBarrel, uWarpZoom, uWarpAspect, uWarpPos;

    private int fbo, fboTex, fboDepth, fboW, fboH;
    private int surfaceW = 1600;
    private int surfaceH = 720;

    private float[] proj;
    private float[] viewRot;
    private float[] mvp;

    private long lastFrameNs;
    private int frames;
    private long fpsStartNs;
    private float fps;
    private boolean ready;
    private boolean released;
    private long lastVideoMs;

    public VrRenderer(VideoTex video, Tracker tracker) {
        this.video = video;
        this.tracker = tracker;
    }

    public void setCallback(Callback cb) {
        callback = cb;
    }

    public Scene scene() {
        return scene;
    }

    /** Pushed from the UI thread; copied under a short lock onto {@link #model}. */
    public void push(WorldModel from) {
        synchronized (model) {
            model.copyFrom(from);
        }
    }

    public float fps() {
        return fps;
    }

    /** The Surface ExoPlayer renders into. Only valid once the GL context is up. */
    public Surface videoSurface() {
        return video.surface();
    }

    public boolean glReady() {
        return ready;
    }

    public long sinceVideoFrameMs() {
        return lastVideoMs == 0 ? -1 : System.currentTimeMillis() - lastVideoMs;
    }

    // --------------------------------------------------------------- setup

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0f, 0f, 0f, 1f);
        GLES20.glDisable(GLES20.GL_CULL_FACE);
        GLES20.glDisable(GLES20.GL_DITHER);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthFunc(GLES20.GL_LEQUAL);

        video.init();

        atlas = new Atlas();
        atlas.init();
        layer = new Layer(atlas);

        quadVerts = Geo.quad2d();
        quadIdx = Geo.quadIdx();
        sphereVerts = Geo.sphere(20, 28);
        sphereIdx = Geo.sphereIdx(20, 28);

        pShell = Gl.program(Sh.SHELL_VS, Sh.SHELL_FS);
        uShellMvp = loc(pShell, "uMvp");
        uShellScale = loc(pShell, "uScale");
        uShellWall = loc(pShell, "uWall");
        uShellFloor = loc(pShell, "uFloor");
        uShellAccent = loc(pShell, "uAccent");
        uShellTime = loc(pShell, "uTime");

        pVideo = Gl.program(Sh.VIDEO_VS, Sh.VIDEO_FS);
        uVidMvp = loc(pVideo, "uMvp");
        uVidW = loc(pVideo, "uWidth");
        uVidH = loc(pVideo, "uHeight");
        uVidCurve = loc(pVideo, "uCurve");
        uVidDim = loc(pVideo, "uDim");
        uVidHas = loc(pVideo, "uHas");
        uVidTex = loc(pVideo, "uTex");

        pWarp = Gl.program(Sh.QUAD_VS, Sh.WARP_FS);
        uWarpTex = loc(pWarp, "uTex");
        uWarpBarrel = loc(pWarp, "uBarrel");
        uWarpZoom = loc(pWarp, "uZoom");
        uWarpAspect = loc(pWarp, "uAspect");
        uWarpPos = GLES20.glGetAttribLocation(pWarp, "aPos");
    }

    private int uShellScale;

    private static int loc(int p, String name) {
        return GLES20.glGetUniformLocation(p, name);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        surfaceW = Math.max(2, width);
        surfaceH = Math.max(2, height);

        float scale;
        switch (model.quality) {
            case 0:
                scale = 0.50f;
                break;
            case 2:
                scale = 0.80f;
                break;
            default:
                scale = 0.62f;
                break;
        }
        int w = ((int) (surfaceW * scale)) & ~1;
        int h = ((int) (surfaceH * scale)) & ~1;
        fboW = Math.max(2, w);
        fboH = Math.max(2, h);
        createFbo();
    }

    private void createFbo() {
        if (fbo != 0) {
            GLES20.glDeleteFramebuffers(1, new int[]{fbo}, 0);
            GLES20.glDeleteTextures(1, new int[]{fboTex}, 0);
            GLES20.glDeleteRenderbuffers(1, new int[]{fboDepth}, 0);
        }
        int[] f = new int[1];
        GLES20.glGenFramebuffers(1, f, 0);
        fbo = f[0];
        int[] t = new int[1];
        GLES20.glGenTextures(1, t, 0);
        fboTex = t[0];
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fboTex);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGB, fboW, fboH, 0,
                GLES20.GL_RGB, GLES20.GL_UNSIGNED_BYTE, null);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE);

        int[] d = new int[1];
        GLES20.glGenRenderbuffers(1, d, 0);
        fboDepth = d[0];
        GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, fboDepth);
        GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER, GLES20.GL_DEPTH_COMPONENT16,
                fboW, fboH);

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, fboTex, 0);
        GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_DEPTH_ATTACHMENT,
                GLES20.GL_RENDERBUFFER, fboDepth);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
    }

    // ---------------------------------------------------------------- frame

    @Override
    public void onDrawFrame(GL10 gl) {
        long now = System.nanoTime();
        float dt = lastFrameNs == 0 ? 0.016f : (now - lastFrameNs) / 1e9f;
        lastFrameNs = now;
        if (dt <= 0f || dt > 0.2f) {
            dt = 0.016f;
        }

        synchronized (model) {
            model.dt = dt;
            model.clock += dt;
            snap.copyFrom(model);
        }

        boolean fresh = video.update();
        if (fresh) {
            lastVideoMs = System.currentTimeMillis();
        }

        viewRot = tracker.view(dt);
        buildProjection(snap);

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo);
        GLES20.glViewport(0, 0, fboW, fboH);
        GLES20.glClear(GL10.GL_COLOR_BUFFER_BIT | GL10.GL_DEPTH_BUFFER_BIT);

        drawEye(snap, -1);
        drawEye(snap, 1);

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, surfaceW, surfaceH);
        drawWarp(snap);

        atlas.commit();

        frames++;
        long span = now - fpsStartNs;
        if (span >= 500_000_000L) {
            fps = frames * 1e9f / span;
            frames = 0;
            fpsStartNs = now;
            if (callback != null) {
                callback.onFps(fps);
            }
        }
        if (!ready) {
            ready = true;
            if (callback != null) {
                callback.onGlReady();
            }
        }
    }

    /**
     * Field of view is derived from the physical theatre: the panel half-height at the
     * viewing distance. Passed through unchanged so the screen keeps the angular size the
     * user chose in metres.
     */
    private void buildProjection(WorldModel w) {
        float near = 0.05f;
        float far = 160f;
        float halfW = w.screenWidth * 0.5f;
        float halfH = halfW / Math.max(0.6f, w.aspect());
        float tanY = halfH / Math.max(0.5f, w.screenDistance);
        float eyeAspect = (fboW * 0.5f) / (float) fboH;
        float tanX = tanY * eyeAspect;
        proj = M.frustum(tanX, tanY, near, far);
    }

    private void drawEye(WorldModel w, int eye) {
        int half = fboW / 2;
        GLES20.glViewport(eye < 0 ? 0 : half, 0, half, fboH);
        GLES20.glClear(GL10.GL_DEPTH_BUFFER_BIT);

        // Half an interpupillary distance to each side. The offset is in eye space, so it
        // is applied after the view rotation: view = invRot(head) * T(+/-ipd/2).
        float eyeX = eye * (w.lensSeparation * 0.5f);
        viewRot = M.invRot(tracker.view(w.dt));
        float[] view = M.mul(viewRot, M.t(eyeX, 0f, 0f));

        drawShell(w, view);
        drawMainSurface(w, view);
        drawConsole(w, view);
        drawRail(w, view);
    }

    // ------------------------------------------------------------- surfaces

    private void drawShell(WorldModel w, float[] view) {
        GLES20.glUseProgram(pShell);
        mvp = M.mul(proj, view);
        GLES20.glUniformMatrix4fv(uShellMvp, 1, false, mvp, 0);
        GLES20.glUniform1f(uShellScale, 30f);
        float[] c = Env.wall(w.env);
        float[] f = Env.floor(w.env);
        float[] a = Env.accent(w.env);
        GLES20.glUniform3f(uShellWall, c[0], c[1], c[2]);
        GLES20.glUniform3f(uShellFloor, f[0], f[1], f[2]);
        GLES20.glUniform3f(uShellAccent, a[0], a[1], a[2]);
        GLES20.glUniform1f(uShellTime, w.clock);
        int p = GLES20.glGetAttribLocation(pShell, "aPos");
        GLES20.glEnableVertexAttribArray(p);
        sphereVerts.position(0);
        GLES20.glVertexAttribPointer(p, 3, GLES20.GL_FLOAT, false, 12, sphereVerts);
        GLES20.glDepthMask(false);
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, sphereIdx.capacity(),
                GLES20.GL_UNSIGNED_SHORT, sphereIdx);
        GLES20.glDepthMask(true);
        GLES20.glDisableVertexAttribArray(p);
    }

    /**
     * The main board. For the player it is the film itself; for every other screen it is
     * the interface, drawn as a curved panel standing in the room.
     */
    private void drawMainSurface(WorldModel w, float[] view) {
        float hw = scene.mainW() * 0.5f;
        float hh = scene.mainH() * 0.5f;

        // The board is centred on the eye at the chosen distance: that is what makes the
        // film fill the field of view instead of sitting in the lower half of it.
        float[] model = M.mul(
                M.t(0f, 0f, -Math.max(0.6f, w.screenDistance)),
                M.id());

        if (w.screen == WorldModel.Screen.PLAYER && w.hasVideo) {
            drawVideo(w, view, model, hw, hh);
        } else {
            layer.begin();
            mvp = M.mul(proj, M.mul(view, model));
            scene.drawMain(layer, atlas, w, mvp);
            layer.draw(mvp);
        }
    }

    private void drawVideo(WorldModel w, float[] view, float[] model,
                          float hw, float hh) {
        mvp = M.mul(proj, M.mul(view, model));
        GLES20.glUseProgram(pVideo);
        GLES20.glUniformMatrix4fv(uVidMvp, 1, false, mvp, 0);
        GLES20.glUniform1f(uVidW, hw * 2f);
        GLES20.glUniform1f(uVidH, hh * 2f);
        GLES20.glUniform1f(uVidCurve, w.fisheye ? w.curve : 0f);
        GLES20.glUniform1f(uVidDim, 1f - Math.max(0.25f, w.brightness));
        GLES20.glUniform1f(uVidHas, video.hasFrame() ? 1f : 0f);
        GLES20.glUniform1i(uVidTex, 0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, video.texture());
        int p = GLES20.glGetAttribLocation(pVideo, "aPos");
        GLES20.glEnableVertexAttribArray(p);
        quadVerts.position(0);
        GLES20.glVertexAttribPointer(p, 2, GLES20.GL_FLOAT, false, 8, quadVerts);
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, 6, GLES20.GL_UNSIGNED_SHORT, quadIdx);
        GLES20.glDisableVertexAttribArray(p);

        // Chrome (title, spinner) rides on the same plane.
        layer.begin();
        scene.drawMain(layer, atlas, w, mvp);
        layer.draw(mvp);
    }

    private void drawConsole(WorldModel w, float[] view) {
        if (w.screen != WorldModel.Screen.PLAYER) {
            return;
        }
        // Tilt is negative exactly like the gaze solver, so what you look at is what you hit.
        float[] model = M.mul(
                M.t(0f, scene.consoleY() - 1.6f, scene.consoleZ()),
                M.rotX(-scene.consoleTilt()));
        layer.begin();
        scene.drawConsole(layer, atlas, w);
        mvp = M.mul(proj, M.mul(view, model));
        layer.draw(mvp);
    }

    private void drawRail(WorldModel w, float[] view) {
        if (w.screen == WorldModel.Screen.PLAYER && w.hudAlpha < 0.05f) {
            return;
        }
        float[] model = M.t(0f, scene.railY() - 1.6f, scene.railZ());
        layer.begin();
        scene.drawRail(layer, atlas, w);
        mvp = M.mul(proj, M.mul(view, model));
        layer.draw(mvp);
    }

    private void drawWarp(WorldModel w) {
        GLES20.glUseProgram(pWarp);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fboTex);
        GLES20.glUniform1i(uWarpTex, 0);
        GLES20.glUniform1f(uWarpBarrel, w.fisheye ? w.barrel : 0f);
        GLES20.glUniform1f(uWarpZoom, w.fisheye ? 1.035f : 1f);
        GLES20.glUniform1f(uWarpAspect, (fboW * 0.5f) / (float) fboH);
        quadVerts.position(0);
        GLES20.glEnableVertexAttribArray(uWarpPos);
        GLES20.glVertexAttribPointer(uWarpPos, 2, GLES20.GL_FLOAT, false, 8, quadVerts);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, 6, GLES20.GL_UNSIGNED_SHORT, quadIdx);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDisableVertexAttribArray(uWarpPos);
    }

    public void release() {
        if (released) {
            return;
        }
        released = true;
        if (atlas != null) {
            atlas.release();
        }
        if (layer != null) {
            layer.release();
        }
        video.release();
        GLES20.glDeleteProgram(pShell);
        GLES20.glDeleteProgram(pVideo);
        GLES20.glDeleteProgram(pWarp);
        if (fbo != 0) {
            GLES20.glDeleteFramebuffers(1, new int[]{fbo}, 0);
            GLES20.glDeleteTextures(1, new int[]{fboTex}, 0);
            GLES20.glDeleteRenderbuffers(1, new int[]{fboDepth}, 0);
            fbo = 0;
        }
    }
}
