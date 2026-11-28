package com.fussoo.vr.gl;

import android.graphics.Bitmap;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;

import com.fussoo.vr.core.Tracker;

import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Stereo theatre renderer.
 *
 * <p>Per frame: room shell, then the curved screen, then the HUD, drawn once per eye into
 * a half-resolution-per-eye off-screen buffer; then a single full-screen pass applies the
 * lens pre-distortion. That resolution choice is the main reason this runs smoothly on a
 * Unisoc T606 — through a plastic lens, 66% scale is indistinguishable.</p>
 */
public final class VrRenderer implements GLSurfaceView.Renderer {

    public interface Callback {
        void onGlReady();

        void onFps(float fps);
    }

    private final VideoTex video;
    private final Tracker tracker;
    private final Stage stage = new Stage();
    private Hud hud;

    private Callback callback;

    private Atlas atlas;
    private Layer layer;
    private FloatBuffer quadVerts;
    private ShortBuffer quadIdx;
    private FloatBuffer sphereVerts;
    private ShortBuffer sphereIdx;

    private int pShell, uShellMvp, uShellWall, uShellFloor, uShellAccent, uShellTime;
    private int pScreen, uScrMvp, uScrW, uScrH, uScrCurve, uScrDim, uScrHas;
    private int pWarp, uWarpBarrel, uWarpZoom, uWarpAspect;

    private int fbo, fboTex, fboDepth, fboW, fboH;
    private int surfaceW = 1280;
    private int surfaceH = 720;

    private float[] view = new float[16];
    private float[] proj = new float[16];
    private float[] mvp = new float[16];
    private final Stage snap = new Stage();

    private long lastFrameNs;
    private int frames;
    private long fpsStartNs;
    private float fps;
    private boolean ready;
    private long lastVideoMs;

    private volatile Bitmap[] shelf;
    private volatile String[] shelfNames;

    public VrRenderer(VideoTex video, Tracker tracker) {
        this.video = video;
        this.tracker = tracker;
    }

    public void setCallback(Callback cb) {
        callback = cb;
    }

    public void setShelf(Bitmap[] posters, String[] names) {
        shelf = posters;
        shelfNames = names;
    }

    public Hud hud() {
        return hud;
    }

    /** Called from the UI thread whenever playback state changes. */
    public void push(Stage from) {
        synchronized (stage) {
            stage.copyFrom(from);
        }
    }

    public float fps() {
        return fps;
    }

    public long sinceVideoFrame() {
        return lastVideoMs == 0 ? -1 : System.currentTimeMillis() - lastVideoMs;
    }

    // ------------------------------------------------------------- lifecycle

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
        hud = new Hud(atlas);

        quadVerts = Geo.quad2d();
        quadIdx = Geo.quadIdx();
        sphereVerts = Geo.sphere(20, 28);
        sphereIdx = Geo.sphereIdx(20, 28);

        pShell = Gl.program(Sh.SHELL_VS, Sh.SHELL_FS);
        uShellMvp = GLES20.glGetUniformLocation(pShell, "uMvp");
        uShellScale = GLES20.glGetUniformLocation(pShell, "uScale");
        uShellWall = GLES20.glGetUniformLocation(pShell, "uWall");
        uShellFloor = GLES20.glGetUniformLocation(pShell, "uFloor");
        uShellAccent = GLES20.glGetUniformLocation(pShell, "uAccent");
        uShellTime = GLES20.glGetUniformLocation(pShell, "uTime");

        pScreen = Gl.program(Sh.SCREEN_VS, Sh.SCREEN_FS);
        uScrMvp = GLES20.glGetUniformLocation(pScreen, "uMvp");
        uScrW = GLES20.glGetUniformLocation(pScreen, "uWidth");
        uScrH = GLES20.glGetUniformLocation(pScreen, "uHeight");
        uScrCurve = GLES20.glGetUniformLocation(pScreen, "uCurve");
        uScrDim = GLES20.glGetUniformLocation(pScreen, "uDim");
        uScrHas = GLES20.glGetUniformLocation(pScreen, "uHas");
        uScrTexId = GLES20.glGetUniformLocation(pScreen, "uTex");

        pWarp = Gl.program(Sh.QUAD_VS, Sh.WARP_FS);
        uWarpTex = GLES20.glGetUniformLocation(pWarp, "uTex");
        uWarpBarrel = GLES20.glGetUniformLocation(pWarp, "uBarrel");
        uWarpZoom = GLES20.glGetUniformLocation(pWarp, "uZoom");
        uWarpAspect = GLES20.glGetUniformLocation(pWarp, "uAspect");
        uQuadPos = GLES20.glGetAttribLocation(pWarp, "aPos");
    }

    private int uShellScale, uScrTexId, uWarpTex, uQuadPos;

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        surfaceW = width;
        surfaceH = height;
        float scale;
        switch (stage.quality) {
            case 0:
                scale = 0.55f;
                break;
            case 2:
                scale = 0.85f;
                break;
            default:
                scale = 0.68f;
                break;
        }
        fboW = Math.max(2, ((int) (width * scale)) & ~1);
        fboH = Math.max(2, ((int) (height * scale)) & ~1);
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

    // ----------------------------------------------------------------- frame

    @Override
    public void onDrawFrame(GL10 gl) {
        long now = System.nanoTime();
        float dt = lastFrameNs == 0 ? 0.016f : (now - lastFrameNs) / 1e9f;
        lastFrameNs = now;
        if (dt <= 0f || dt > 0.2f) {
            dt = 0.016f;
        }

        synchronized (stage) {
            stage.dt = dt;
            stage.clock += dt;
        }
        Stage s = snap;

        boolean newVideo = video.update();
        if (newVideo) {
            lastVideoMs = System.currentTimeMillis();
        }

        synchronized (stage) {
            snap.copyFrom(stage);
        }
        buildProjection(snap);

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo);
        GLES20.glViewport(0, 0, fboW, fboH);
        GLES20.glClear(GL10.GL_COLOR_BUFFER_BIT | GL10.GL_DEPTH_BUFFER_BIT);

        drawEye(s, -1);
        drawEye(s, 1);

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, surfaceW, surfaceH);
        drawWarp(s);

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
     * One shared symmetric frustum, defined the way cardboard optics actually work:
     *
     * <p>Each half of the screen sits in front of one lens at {@code screen_distance}, so
     * the vertical view angle is {@code 2·atan(halfHeight / distance)}. At the default
     * 4.4 m distance and a 1.86 m tall half-panel that is about 46° per eye, which is
     * exactly what the lens shows you. Horizontal uses the eye panel's real aspect.</p>
     */
    private void buildProjection(Stage s) {
        float near = 0.05f;
        float far = 150f;
        float halfW = s.screenWidth * 0.5f;
        float halfH = halfW / Math.max(0.55f, s.aspect());
        // The virtual screen is placed at s.screenDistance; its angular size follows.
        float vt = halfH / Math.max(0.5f, s.screenDistance);
        float aspect = (fboW * 0.5f) / (float) fboH;
        float ht = vt * aspect;
        proj = M.frustum(ht, vt, near, far);
    }

    private void drawEye(Stage s, int eye) {
        int half = fboW / 2;
        GLES20.glViewport(eye < 0 ? 0 : half, 0, half, fboH);
        GLES20.glClear(GL10.GL_DEPTH_BUFFER_BIT);
        // Left eye sits half an interpupillary distance to the left of the head origin.
        float eyeX = eye * (s.lensSeparation * 0.5f) * -1f;
        view = M.mul(M.invRot(tracker.view(s.dt)), M.t(eyeX, 0f, 0f));
        drawShell(s);
        drawScreen(s);
        drawHud(s);
    }

    private void drawShell(Stage s) {
        Env e = Env.of(s.env);
        mvp = M.mul(proj, view);
        GLES20.glUseProgram(pShell);
        GLES20.glUniformMatrix4fv(uShellMvp, 1, false, mvp, 0);
        GLES20.glUniform1f(uShellScale, 26f);
        GLES20.glUniform3f(uShellWall, e.wallR, e.wallG, e.wallB);
        GLES20.glUniform3f(uShellFloor, e.floorR, e.floorG, e.floorB);
        GLES20.glUniform3f(uShellAccent, e.accentR, e.accentG, e.accentB);
        GLES20.glUniform1f(uShellTime, s.clock);
        int a = GLES20.glGetAttribLocation(pShell, "aPos");
        GLES20.glEnableVertexAttribArray(a);
        sphereVerts.position(0);
        GLES20.glVertexAttribPointer(a, 3, GLES20.GL_FLOAT, false, 12, sphereVerts);
        // The shell is the backdrop; depth writes off so it never occludes anything.
        GLES20.glDepthMask(false);
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, sphereIdx.capacity(),
                GLES20.GL_UNSIGNED_SHORT, sphereIdx);
        GLES20.glDepthMask(true);
        GLES20.glDisableVertexAttribArray(a);
    }

    private void drawScreen(Stage s) {
        // Screen sits at the configured distance and eye height.
        float[] model = M.t(s.screenCenterY - 1.6f, -(0f), 0f);
        model[13] = s.screenCenterY - 1.6f;
        model[14] = -s.screenDistance;
        mvp = M.mul(proj, M.mul(view, model));

        float halfW = s.screenWidth * 0.5f;
        float halfH = halfW / Math.max(0.55f, s.aspect());

        GLES20.glUseProgram(pScreen);
        GLES20.glUniformMatrix4fv(uScrMvp, 1, false, mvp, 0);
        GLES20.glUniform1f(uScrW, halfW * 2f);
        GLES20.glUniform1f(uScrH, halfH * 2f);
        GLES20.glUniform1f(uScrCurve, s.fisheye ? s.curve : 0f);
        GLES20.glUniform1f(uScrDim, 1f - Math.max(0.25f, s.brightness));
        GLES20.glUniform1f(uScrHas, s.hasVideo && video.hasFrame() ? 1f : 0f);
        GLES20.glUniform1i(uScrTexId, 0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, video.texture());
        int a = GLES20.glGetAttribLocation(pScreen, "aPos");
        GLES20.glEnableVertexAttribArray(a);
        quadVerts.position(0);
        GLES20.glVertexAttribPointer(a, 2, GLES20.GL_FLOAT, false, 8, quadVerts);
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, 6, GLES20.GL_UNSIGNED_SHORT, quadIdx);
        GLES20.glDisableVertexAttribArray(a);
    }

    private void drawHud(Stage s) {
        float[] model = M.id();
        model[13] = s.screenCenterY - 1.6f;
        model[14] = -(s.screenDistance - 0.04f);
        mvp = M.mul(proj, M.mul(view, model));
        layer.clear();
        hud.draw(layer, s, shelf, shelfNames);
        layer.draw(mvp);
    }

    private void drawWarp(Stage s) {
        GLES20.glUseProgram(pWarp);
        GLES20.glUniform1i(uWarpTex, 0);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fboTex);
        GLES20.glUniform1f(uWarpBarrel, s.fisheye ? s.barrel : 0f);
        GLES20.glUniform1f(uWarpZoom, s.fisheye ? 1.035f : 1f);
        GLES20.glUniform1f(uWarpAspect, (fboW * 0.5f) / (float) fboH);
        GLES20.glEnableVertexAttribArray(uQuadPos);
        quadVerts.position(0);
        GLES20.glVertexAttribPointer(uQuadPos, 2, GLES20.GL_FLOAT, false, 8, quadVerts);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, 6, GLES20.GL_UNSIGNED_SHORT, quadIdx);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDisableVertexAttribArray(uQuadPos);
    }

    public void release() {
        if (atlas != null) {
            atlas.release();
        }
        if (layer != null) {
            layer.release();
        }
        video.release();
        GLES20.glDeleteProgram(pShell);
        GLES20.glDeleteProgram(pScreen);
        GLES20.glDeleteProgram(pWarp);
        if (fbo != 0) {
            GLES20.glDeleteFramebuffers(1, new int[]{fbo}, 0);
            GLES20.glDeleteTextures(1, new int[]{fboTex}, 0);
            GLES20.glDeleteRenderbuffers(1, new int[]{fboDepth}, 0);
            fbo = 0;
        }
    }
}
