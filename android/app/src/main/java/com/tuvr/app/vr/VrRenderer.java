package com.tuvr.app.vr;

import android.content.Context;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;

import com.tuvr.app.core.AppSettings;
import com.tuvr.app.core.ViewerProfile;

import java.util.ArrayList;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Stereoscopic renderer for TU VR.
 *
 * Pipeline per frame:
 *   1. read the tracked head orientation,
 *   2. render the environment, scene content and panels into one offscreen
 *      framebuffer per eye (overscanned so lens correction never samples
 *      outside the image),
 *   3. composite both eye textures onto the screen halves through a barrel
 *      distortion shader (this is what makes the image look correct through the
 *      headset lenses).
 *
 * Everything is intentionally cheap: no shadows, no post effects, no 3D models.
 */
public class VrRenderer implements GLSurfaceView.Renderer {

    public interface Listener {
        void onPanelSelected(Panel panel);

        void onStats(float fps, float frameMs, float renderScale);

        void onTrackingWarning(int state, String source);
    }

    private static final float[] ATTR_POS = {3};
    private static final float[] ATTR_POS_UV = {3, 2};
    private static final float[] ATTR_POS_COLOR = {3, 0, 3};

    private static final float OVERSHOOT = 1.22f;
    private static final float NEAR_PLANE = 0.08f;
    private static final float FAR_PLANE = 90f;

    private final Context context;
    private final HeadTracker tracker;
    private final PanelFactory panelFactory = new PanelFactory();

    private AppSettings settings;
    private ViewerProfile profile;
    private Listener listener;

    /* GL programs */
    private GlUtil.Program colorProgram;
    private GlUtil.Program pointsProgram;
    private GlUtil.Program texturedProgram;
    private GlUtil.Program videoProgram;
    private GlUtil.Program distortProgram;
    private GlUtil.Program reticleProgram;

    /* shared meshes */
    private Mesh fullScreenQuad;
    private EnvironmentBuilder.Env environment;
    private int environmentType = -1;

    /* offscreen targets */
    private final GlUtil.Framebuffer leftEye = new GlUtil.Framebuffer();
    private final GlUtil.Framebuffer rightEye = new GlUtil.Framebuffer();

    private final VideoTexture videoTexture = new VideoTexture();

    /* scene plumbing (GL thread only) */
    private VrScene scene;
    private VrScene pendingScene;
    private final List<Panel> panels = new ArrayList<>();
    private final List<Panel> internalPanels = new ArrayList<>();
    private Panel fpsPanel;
    private Panel whisperPanel;
    private long whisperUntilMs;

    /* matrices */
    private final float[] quat = {0f, 0f, 0f, 1f};
    private final float[] viewMatrix = new float[16];
    private final float[] projMatrix = new float[16];
    private final float[] vpMatrix = new float[16];
    private final float[] eyeView = new float[16];
    private final float[] rotationMatrix = new float[16];
    private final float[] translationMatrix = new float[16];
    private final float[] modelMatrix = new float[16];
    private final float[] tempMatrix = new float[16];
    private final float[] tempMvp = new float[16];
    private final float[] gazeDir = {0f, 0f, -1f};
    private final float[] eyeOffsetWorld = new float[3];

    /* gaze / dwell state */
    private Panel focusedPanel;
    private long focusStartMs;
    private long selectionCooldownUntilMs;
    private float dwellProgress;

    /* quality / stats */
    private int surfaceWidth = 1;
    private int surfaceHeight = 1;
    private float baseScale = 0.85f;
    private float adaptiveScale = 1f;
    private float appliedScale = -1f;
    private float emaFrameMs = 16f;
    private int framesSinceQualityCheck;
    private int thermalStatus;
    private long frameCount;
    private long lastFpsTimeMs;
    private float currentFps;
    private float currentFrameMs;
    private long lastFrameNanos;
    private long lastRenderedNanos;
    private long lastWarningMs;

    private boolean glReady;
    private boolean settingsDirty = true;
    private boolean monoPreview;
    private boolean paused;

    public VrRenderer(Context context, HeadTracker tracker) {
        this.context = context.getApplicationContext();
        this.tracker = tracker;
        this.settings = AppSettings.get(context);
        this.profile = settings.viewerProfile();
        refreshQualityBase();
    }

    /* ------------------------------------------------------------------ */
    /* public API                                                          */
    /* ------------------------------------------------------------------ */

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setScene(VrScene newScene) {
        this.pendingScene = newScene;
    }

    public VrScene scene() {
        return scene;
    }

    public VideoTexture videoTexture() {
        return videoTexture;
    }

    public PanelFactory panelFactory() {
        return panelFactory;
    }

    public AppSettings settings() {
        return settings;
    }

    public ViewerProfile profile() {
        return profile;
    }

    public HeadTracker tracker() {
        return tracker;
    }

    public void reloadSettings() {
        settingsDirty = true;
    }

    public void setPaused(boolean value) {
        paused = value;
    }

    public void setMonoPreview(boolean value) {
        monoPreview = value;
    }

    public boolean isMonoPreview() {
        return monoPreview;
    }

    public void setThermalStatus(int status) {
        thermalStatus = status;
        if (status >= 4) {
            adaptiveScale = 0.55f;
        } else if (status >= 3) {
            adaptiveScale = Math.min(adaptiveScale, 0.65f);
        }
    }

    /** Shows a short message panel in front of the user. */
    public void showWhisper(String text, long durationMs) {
        if (whisperPanel == null) {
            whisperPanel = new Panel("whisper");
            whisperPanel.interactive = false;
            whisperPanel.widthM = 0.9f;
            whisperPanel.heightM = 0.225f;
            whisperPanel.pitchDeg = -22f;
            whisperPanel.distanceM = 1.2f;
            internalPanels.add(whisperPanel);
        }
        whisperPanel.setContent(panelFactory.textPanel(text, 512, 128, 40f, false, 0xFFE9F2FF));
        whisperUntilMs = System.currentTimeMillis() + durationMs;
    }

    /** Touch selection: coordinates in normalised device space (-1..1, y up). */
    public void onTouch(float ndcX, float ndcY) {
        if (!glReady) {
            return;
        }
        float tanX = (float) Math.tan(Math.toRadians(profile.eyeFovXDeg() * 0.5f));
        float tanY = (float) Math.tan(Math.toRadians(profile.eyeFovYDeg(1f) * 0.5f));
        float[] localDir = {ndcX * tanX, ndcY * tanY, -1f};
        MathUtil.normalize(localDir);
        float[] worldDir = new float[3];
        MathUtil.rotateVector(quat, localDir[0], localDir[1], localDir[2], worldDir);
        Panel hit = pickPanel(worldDir[0], worldDir[1], worldDir[2]);
        if (hit != null && System.currentTimeMillis() > selectionCooldownUntilMs) {
            selectionCooldownUntilMs = System.currentTimeMillis() + 350;
            dispatchSelection(hit);
        }
    }

    /* ------------------------------------------------------------------ */
    /* GLSurfaceView.Renderer                                              */
    /* ------------------------------------------------------------------ */

    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        GLES20.glDisable(GLES20.GL_CULL_FACE);
        GLES20.glDisable(GLES20.GL_DITHER);
        GLES20.glClearColor(0f, 0f, 0f, 1f);

        colorProgram = GlUtil.program(context.getAssets(), "color");
        pointsProgram = GlUtil.program(context.getAssets(), "points");
        texturedProgram = GlUtil.program(context.getAssets(), "textured");
        videoProgram = GlUtil.program(context.getAssets(), "video");
        distortProgram = GlUtil.program(context.getAssets(), "distort");
        reticleProgram = GlUtil.program(context.getAssets(), "reticle");

        fullScreenQuad = MeshBuilder.unitQuad();
        videoTexture.create();
        environmentType = -1;
        glReady = true;

        if (scene != null) {
            scene.release();
            scene.onCreated(this);
        }
    }

    @Override
    public void onSurfaceChanged(GL10 unused, int width, int height) {
        surfaceWidth = Math.max(2, width);
        surfaceHeight = Math.max(2, height);
        appliedScale = -1f;
        updateEyeBuffers();
    }

    @Override
    public void onDrawFrame(GL10 unused) {
        if (paused || !glReady) {
            return;
        }
        long nowNanos = System.nanoTime();
        long nowMs = System.currentTimeMillis();

        // Frame limiter: on a 1612x720 panel capping to the setting saves battery.
        int limit = settings.frameLimit();
        if (limit > 0) {
            long intervalNanos = 1_000_000_000L / limit;
            if (nowNanos - lastRenderedNanos < intervalNanos - 1_500_000L) {
                return;
            }
        }
        lastRenderedNanos = nowNanos;

        float dt = lastFrameNanos == 0 ? 0.016f : (nowNanos - lastFrameNanos) / 1e9f;
        lastFrameNanos = nowNanos;
        if (dt > 0.25f) {
            dt = 0.25f;
        }

        applyPendingWork();

        tracker.updateOutput(quat);
        updateGaze(nowMs);

        if (scene != null) {
            scene.update(this, dt);
        }
        videoTexture.update();
        collectPanels();

        drawEyes();
        composite();

        updateStats(nowMs, dt);
    }

    /* ------------------------------------------------------------------ */
    /* frame stages                                                        */
    /* ------------------------------------------------------------------ */

    private void applyPendingWork() {
        if (pendingScene != null) {
            if (scene != null) {
                scene.release();
            }
            scene = pendingScene;
            pendingScene = null;
            if (scene != null) {
                scene.onCreated(this);
            }
            settingsDirty = true;
        }
        if (settingsDirty) {
            settingsDirty = false;
            profile = settings.viewerProfile();
            refreshQualityBase();
            int desiredEnv = settings.environment();
            if (desiredEnv != environmentType) {
                if (environment != null) {
                    environment.release();
                }
                environment = EnvironmentBuilder.build(desiredEnv);
                environmentType = desiredEnv;
            }
            if (scene != null) {
                scene.onSettingsChanged(this);
            }
            applyEyeSettings();
        }
        if (whisperPanel != null && System.currentTimeMillis() > whisperUntilMs) {
            internalPanels.remove(whisperPanel);
            whisperPanel.release();
            whisperPanel = null;
        }
    }

    private void refreshQualityBase() {
        switch (settings.quality()) {
            case AppSettings.QUALITY_LOW:
                baseScale = 0.70f;
                break;
            case AppSettings.QUALITY_HIGH:
                baseScale = 1.0f;
                break;
            default:
                baseScale = 0.85f;
                break;
        }
        if (adaptiveScale > baseScale) {
            adaptiveScale = baseScale;
        }
    }

    private void applyEyeSettings() {
        tracker.setSmoothing(settings.trackingSmoothing());
        tracker.setSensitivity(settings.trackingSensitivity());
    }

    private void collectPanels() {
        panels.clear();
        if (scene != null) {
            scene.collectPanels(this, panels);
        }
        panels.addAll(internalPanels);
        for (int i = 0; i < panels.size(); i++) {
            panels.get(i).updateBasis();
        }
        if (settings.showFps()) {
            ensureFpsPanel();
        } else if (fpsPanel != null) {
            internalPanels.remove(fpsPanel);
            fpsPanel.release();
            fpsPanel = null;
            lastFpsText = "";
        }
    }

    private void ensureFpsPanel() {
        if (fpsPanel == null) {
            fpsPanel = new Panel("fps");
            fpsPanel.interactive = false;
            fpsPanel.widthM = 0.7f;
            fpsPanel.heightM = 0.35f;
            fpsPanel.yawDeg = -38f;
            fpsPanel.pitchDeg = 26f;
            fpsPanel.distanceM = 1.6f;
            internalPanels.add(fpsPanel);
        }
        String text = String.format(java.util.Locale.US, "%d fps\n%.1f ms\nscale %.2f",
                Math.round(currentFps), currentFrameMs, appliedScale);
        if (!text.equals(lastFpsText)) {
            lastFpsText = text;
            fpsPanel.setContent(panelFactory.textPanel(text, 256, 128, 34f, false, 0xFF4DE0FF));
        }
    }

    private String lastFpsText = "";

    private void drawEyes() {
        updateEyeBuffers();
        float baseline = profile.stereoBaselineMm() / 1000f;
        float eyeHalf = baseline * 0.5f;

        for (int eye = 0; eye < 2; eye++) {
            GlUtil.Framebuffer fbo = eye == 0 ? leftEye : rightEye;
            if (fbo.framebufferId == 0) {
                continue;
            }
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo.framebufferId);
            GLES20.glViewport(0, 0, fbo.width, fbo.height);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
            GLES20.glEnable(GLES20.GL_DEPTH_TEST);
            GLES20.glDepthMask(true);

            float eyeAspect = fbo.width / (float) Math.max(1, fbo.height);
            buildViewMatrix(eye, eyeHalf);
            buildProjectionMatrix(eyeAspect);

            drawEnvironment();
            if (scene != null) {
                scene.drawEye(this, eye, eyeView, projMatrix);
            }
            drawPanels();
            drawReticle(eyeAspect);

            if (monoPreview) {
                break;
            }
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
    }

    private void composite() {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glClearColor(0f, 0f, 0f, 1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        if (distortProgram == null) {
            return;
        }
        boolean distortion = profile.distortionEnabled;
        float tanHalfX = (float) Math.tan(Math.toRadians(profile.eyeFovXDeg() * 0.5f));
        float eyeAspect = eyeWidthPx() / (float) Math.max(1, eyeHeightPx());
        float tanHalfY = tanHalfX / Math.max(0.05f, eyeAspect);
        float offsetXTan = (profile.eyeOffsetXMm / 1000f)
                / Math.max(0.01f, profile.screenLensDistanceMm / 1000f) * 0.5f;
        float offsetYTan = (profile.eyeOffsetYMm / 1000f)
                / Math.max(0.01f, profile.screenLensDistanceMm / 1000f) * 0.5f;

        if (monoPreview) {
            GlUtil.Framebuffer fbo = leftEye;
            drawDistortion(fbo.textureId, 0, 0, surfaceWidth, surfaceHeight,
                    tanHalfX, tanHalfY, offsetXTan, offsetYTan, distortion);
        } else {
            boolean swap = settings.swapEyes();
            int halfWidth = surfaceWidth / 2;
            int rightWidth = surfaceWidth - halfWidth;
            GlUtil.Framebuffer leftSource = swap ? rightEye : leftEye;
            GlUtil.Framebuffer rightSource = swap ? leftEye : rightEye;
            drawDistortion(leftSource.textureId, 0, 0, halfWidth, surfaceHeight,
                    tanHalfX, tanHalfY, offsetXTan, offsetYTan, distortion);
            drawDistortion(rightSource.textureId, halfWidth, 0, rightWidth, surfaceHeight,
                    tanHalfX, tanHalfY, -offsetXTan, offsetYTan, distortion);
        }
    }

    private void drawDistortion(int textureId, int x, int y, int width, int height,
                                float tanHalfX, float tanHalfY,
                                float offsetXTan, float offsetYTan, boolean distortion) {
        GLES20.glViewport(x, y, Math.max(1, width), Math.max(1, height));
        distortProgram.use();
        distortProgram.set2f("uTanHalfEye", tanHalfX, tanHalfY);
        distortProgram.set2f("uCenterOffset", offsetXTan, offsetYTan);
        distortProgram.set1f("uOverscan", OVERSHOOT);
        if (distortion) {
            distortProgram.set1f("uK1", profile.distortionK1);
            distortProgram.set1f("uK2", profile.distortionK2);
            distortProgram.set1f("uChromatic", profile.chromaticShift);
        } else {
            distortProgram.set1f("uK1", 0f);
            distortProgram.set1f("uK2", 0f);
            distortProgram.set1f("uChromatic", 0f);
        }
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
        distortProgram.set1i("uTex", 0);
        fullScreenQuad.draw(ATTR_POS);
    }

    private void buildViewMatrix(int eye, float eyeHalfMetres) {
        // Rotation from the head tracker, then a small per-eye translation.
        MathUtil.quatToViewMatrix(quat[0], quat[1], quat[2], quat[3], rotationMatrix);
        if (eye == 0) {
            MathUtil.rotateVector(quat, -eyeHalfMetres, 0f, 0f, eyeOffsetWorld);
        } else {
            MathUtil.rotateVector(quat, eyeHalfMetres, 0f, 0f, eyeOffsetWorld);
        }
        MathUtil.translation(-eyeOffsetWorld[0], -eyeOffsetWorld[1], -eyeOffsetWorld[2],
                translationMatrix);
        MathUtil.multiply(rotationMatrix, translationMatrix, eyeView);
    }

    private void buildProjectionMatrix(float eyeAspect) {
        float tanHalfX = (float) Math.tan(Math.toRadians(profile.eyeFovXDeg() * 0.5f));
        float tanHalfY = tanHalfX / Math.max(0.05f, eyeAspect);
        float fovY = (float) Math.toDegrees(2.0 * Math.atan(tanHalfY * OVERSHOOT));
        MathUtil.perspective(fovY, eyeAspect, NEAR_PLANE, FAR_PLANE, projMatrix);
        MathUtil.multiply(projMatrix, eyeView, vpMatrix);
    }

    private void drawEnvironment() {
        if (environment == null) {
            return;
        }
        GLES20.glDisable(GLES20.GL_BLEND);
        colorProgram.use();
        colorProgram.setMatrix("uMvp", vpMatrix);
        colorProgram.set1f("uAlpha", 1f);
        if (environment.backdrop != null) {
            environment.backdrop.draw(ATTR_POS_COLOR);
        }
        if (environment.floor != null) {
            environment.floor.draw(ATTR_POS_COLOR);
        }
        if (environment.accents != null) {
            environment.accents.drawLines(ATTR_POS_COLOR);
        }
        if (environment.stars != null && pointsProgram != null) {
            pointsProgram.use();
            pointsProgram.setMatrix("uMvp", vpMatrix);
            pointsProgram.set1f("uPointScale", 2.4f);
            environment.stars.drawPoints(ATTR_POS_COLOR);
        }
    }

    private void drawPanels() {
        if (panels.isEmpty() || texturedProgram == null) {
            return;
        }
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glDepthMask(false);
        for (int i = 0; i < panels.size(); i++) {
            Panel panel = panels.get(i);
            if (!panel.visible) {
                continue;
            }
            uploadPanelTexture(panel);
            if (panel.textureId == 0) {
                continue;
            }
            float tint = panel == focusedPanel ? 1.28f : 1f;
            MathUtil.multiply(vpMatrix, panel.model, tempMvp);
            texturedProgram.use();
            texturedProgram.setMatrix("uMvp", tempMvp);
            texturedProgram.set1f("uAlpha", panel.alpha);
            texturedProgram.set3f("uTint", tint, tint, tint);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, panel.textureId);
            texturedProgram.set1i("uTex", 0);
            fullScreenQuad.draw(ATTR_POS_UV);
        }
        GLES20.glDepthMask(true);
        GLES20.glDisable(GLES20.GL_BLEND);
    }

    /** Uploads a panel bitmap if needed. Used by scenes that draw a panel themselves. */
    public void ensureTexture(Panel panel) {
        uploadPanelTexture(panel);
    }

    private void uploadPanelTexture(Panel panel) {
        if (panel.content == null) {
            return;
        }
        if (!panel.contentDirty && panel.textureId != 0) {
            return;
        }
        if (panel.textureId == 0) {
            panel.textureId = GlUtil.textureFromBitmap(panel.content);
        } else {
            GlUtil.updateTexture(panel.textureId, panel.content);
        }
        panel.contentDirty = false;
        if (panel.pendingRecycle != null) {
            if (!panel.pendingRecycle.isRecycled()) {
                panel.pendingRecycle.recycle();
            }
            panel.pendingRecycle = null;
        }
    }

    private void drawReticle(float eyeAspect) {
        if (reticleProgram == null || settings.selectionMode() != AppSettings.SELECT_GAZE) {
            return;
        }
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

        float size = 0.055f * settings.uiScale();
        MathUtil.identity(modelMatrix);
        modelMatrix[0] = size;
        modelMatrix[5] = size;
        modelMatrix[12] = 0f;
        modelMatrix[13] = 0f;
        modelMatrix[14] = -1.2f;
        MathUtil.multiply(projMatrix, modelMatrix, tempMvp);

        reticleProgram.use();
        reticleProgram.setMatrix("uMvp", tempMvp);
        reticleProgram.set1f("uProgress", dwellProgress);
        reticleProgram.set1f("uRingRadius", 0.72f);
        reticleProgram.set3f("uColor", 0.62f, 0.94f, 1.0f);
        fullScreenQuad.draw(ATTR_POS_UV);

        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
    }

    /* ------------------------------------------------------------------ */
    /* gaze / dwell                                                        */
    /* ------------------------------------------------------------------ */

    private void updateGaze(long nowMs) {
        MathUtil.rotateVector(quat, 0f, 0f, -1f, gazeDir);
        if (settings.selectionMode() != AppSettings.SELECT_GAZE) {
            focusedPanel = null;
            dwellProgress = 0f;
            return;
        }
        Panel best = pickPanel(gazeDir[0], gazeDir[1], gazeDir[2]);
        if (best != focusedPanel) {
            focusedPanel = best;
            focusStartMs = nowMs;
            dwellProgress = 0f;
            return;
        }
        if (focusedPanel == null || !focusedPanel.interactive) {
            dwellProgress = 0f;
            return;
        }
        int dwellMs = Math.max(500, settings.dwellMs());
        long elapsedMs = nowMs - focusStartMs;
        dwellProgress = MathUtil.clamp(elapsedMs / (float) dwellMs, 0f, 1f);
        if (elapsedMs >= dwellMs && nowMs > selectionCooldownUntilMs) {
            selectionCooldownUntilMs = nowMs + 500;
            dwellProgress = 0f;
            focusStartMs = nowMs;
            dispatchSelection(focusedPanel);
        }
    }

    private Panel pickPanel(float dirX, float dirY, float dirZ) {
        Panel best = null;
        float bestT = Float.MAX_VALUE;
        for (int i = 0; i < panels.size(); i++) {
            Panel panel = panels.get(i);
            float t = panel.hitTest(dirX, dirY, dirZ);
            if (t > 0f && t < bestT) {
                bestT = t;
                best = panel;
            }
        }
        return best;
    }

    private void dispatchSelection(final Panel panel) {
        if (listener == null || scene == null) {
            return;
        }
        final VrScene targetScene = scene;
        android.os.Handler handler = new android.os.Handler(
                android.os.Looper.getMainLooper());
        handler.post(() -> {
            listener.onPanelSelected(panel);
            targetScene.onPanelSelected(panel, VrRenderer.this);
        });
    }

    /* ------------------------------------------------------------------ */
    /* stats & adaptive quality                                            */
    /* ------------------------------------------------------------------ */

    private void updateStats(long nowMs, float dt) {
        frameCount++;
        currentFrameMs = currentFrameMs == 0f ? dt * 1000f
                : currentFrameMs * 0.9f + dt * 1000f * 0.1f;
        if (nowMs - lastFpsTimeMs >= 1000L) {
            currentFps = frameCount * 1000f / Math.max(1, nowMs - lastFpsTimeMs);
            frameCount = 0;
            lastFpsTimeMs = nowMs;
            if (listener != null) {
                final float fps = currentFps;
                final float ms = currentFrameMs;
                final float scale = appliedScale;
                android.os.Handler handler = new android.os.Handler(
                        android.os.Looper.getMainLooper());
                handler.post(() -> listener.onStats(fps, ms, scale));
            }
        }
        maybeWarnAboutTracking(nowMs);

        if (!settings.adaptiveQuality()) {
            adaptiveScale = 1f;
            return;
        }
        emaFrameMs = emaFrameMs * 0.9f + currentFrameMs * 0.1f;
        framesSinceQualityCheck++;
        if (framesSinceQualityCheck < 45) {
            return;
        }
        framesSinceQualityCheck = 0;
        float ceiling = baseScale;
        if (thermalStatus >= 4) {
            ceiling = Math.min(ceiling, 0.55f);
        } else if (thermalStatus >= 3) {
            ceiling = Math.min(ceiling, 0.68f);
        } else if (thermalStatus >= 2) {
            ceiling = Math.min(ceiling, 0.8f);
        }
        if (emaFrameMs > 21f && adaptiveScale > 0.55f) {
            adaptiveScale = Math.max(0.55f, adaptiveScale - 0.06f);
        } else if (emaFrameMs < 13.5f && adaptiveScale < ceiling) {
            adaptiveScale = Math.min(ceiling, adaptiveScale + 0.04f);
        }
    }

    private void maybeWarnAboutTracking(long nowMs) {
        if (listener == null || nowMs - lastWarningMs < 12000L) {
            return;
        }
        if (tracker.state() == HeadTracker.STATE_UNAVAILABLE) {
            lastWarningMs = nowMs;
            final String source = tracker.sensorName();
            new android.os.Handler(android.os.Looper.getMainLooper())
                    .post(() -> listener.onTrackingWarning(HeadTracker.STATE_UNAVAILABLE, source));
            showWhisper("No head tracking sensor - drag to look around", 4000);
        } else if (tracker.state() == HeadTracker.STATE_DEGRADED && tracker.isRunning()) {
            lastWarningMs = nowMs;
            final String source = tracker.sensorName();
            new android.os.Handler(android.os.Looper.getMainLooper())
                    .post(() -> listener.onTrackingWarning(HeadTracker.STATE_DEGRADED, source));
        }
    }

    /* ------------------------------------------------------------------ */
    /* eye buffers                                                         */
    /* ------------------------------------------------------------------ */

    private int eyeWidthPx() {
        return monoPreview ? surfaceWidth : Math.max(2, surfaceWidth / 2);
    }

    private int eyeHeightPx() {
        return Math.max(2, surfaceHeight);
    }

    private void updateEyeBuffers() {
        float scale = MathUtil.clamp(baseScale * adaptiveScale, 0.45f, 1.0f);
        if (Math.abs(scale - appliedScale) < 0.035f && appliedScale > 0) {
            return;
        }
        appliedScale = scale;
        int width = Math.max(320, (int) (eyeWidthPx() * scale));
        int height = Math.max(240, (int) (eyeHeightPx() * scale));
        int[] maxTexture = new int[1];
        GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, maxTexture, 0);
        int max = maxTexture[0] > 0 ? maxTexture[0] : 2048;
        width = Math.min(width, max);
        height = Math.min(height, max);
        leftEye.ensure(width, height);
        if (!monoPreview) {
            rightEye.ensure(width, height);
        }
    }

    /* ------------------------------------------------------------------ */
    /* drawing helpers used by scenes                                      */
    /* ------------------------------------------------------------------ */

    public float eyeTanHalfX() {
        return (float) Math.tan(Math.toRadians(profile.eyeFovXDeg() * 0.5f));
    }

    public float eyeTanHalfY(float aspect) {
        return eyeTanHalfX() / Math.max(0.05f, aspect);
    }

    public void drawTextured(Mesh mesh, int textureId, float[] model, float alpha) {
        if (texturedProgram == null || textureId == 0) {
            return;
        }
        MathUtil.multiply(vpMatrix, model, tempMvp);
        texturedProgram.use();
        texturedProgram.setMatrix("uMvp", tempMvp);
        texturedProgram.set1f("uAlpha", alpha);
        texturedProgram.set3f("uTint", 1f, 1f, 1f);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
        texturedProgram.set1i("uTex", 0);
        mesh.draw(ATTR_POS_UV);
    }

    public void drawColored(Mesh mesh, float[] model, float alpha) {
        if (colorProgram == null) {
            return;
        }
        MathUtil.multiply(vpMatrix, model, tempMvp);
        colorProgram.use();
        colorProgram.setMatrix("uMvp", tempMvp);
        colorProgram.set1f("uAlpha", alpha);
        mesh.draw(ATTR_POS_COLOR);
    }

    /**
     * Draws video content. Texture coordinates are scaled/offset so that one
     * half of a stereoscopic frame can be mapped to one eye.
     */
    public void drawVideo(Mesh mesh, float[] model, float uvScaleX, float uvScaleY,
                          float uvOffsetX, float uvOffsetY, float uvRotationRad, float alpha) {
        if (videoProgram == null || !videoTexture.isReady()) {
            return;
        }
        MathUtil.multiply(vpMatrix, model, tempMvp);
        videoProgram.use();
        videoProgram.setMatrix("uMvp", tempMvp);
        videoProgram.setMatrix("uTexMatrix", videoTexture.transform);
        videoProgram.set2f("uUvScale", uvScaleX, uvScaleY);
        videoProgram.set2f("uUvOffset", uvOffsetX, uvOffsetY);
        videoProgram.set1f("uUvRotation", uvRotationRad);
        videoProgram.set1f("uAlpha", alpha);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, videoTexture.textureId());
        videoProgram.set1i("uTex", 0);
        mesh.draw(ATTR_POS_UV);
    }

    public void drawColorMesh(Mesh mesh) {
        if (colorProgram == null) {
            return;
        }
        colorProgram.use();
        colorProgram.setMatrix("uMvp", vpMatrix);
        colorProgram.set1f("uAlpha", 1f);
        mesh.draw(ATTR_POS_COLOR);
    }

    public void drawLines(Mesh mesh, float alpha) {
        if (colorProgram == null) {
            return;
        }
        colorProgram.use();
        colorProgram.setMatrix("uMvp", vpMatrix);
        colorProgram.set1f("uAlpha", alpha);
        mesh.drawLines(ATTR_POS_COLOR);
    }

    public void multiplyVp(float[] model, float[] out) {
        MathUtil.multiply(vpMatrix, model, out);
    }

    public float[] currentVp() {
        return vpMatrix;
    }

    public void drawUiQuad(Mesh mesh, float[] model, int textureId, float alpha, float tint) {
        if (texturedProgram == null || textureId == 0) {
            return;
        }
        MathUtil.multiply(vpMatrix, model, tempMvp);
        texturedProgram.use();
        texturedProgram.setMatrix("uMvp", tempMvp);
        texturedProgram.set1f("uAlpha", alpha);
        texturedProgram.set3f("uTint", tint, tint, tint);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
        texturedProgram.set1i("uTex", 0);
        mesh.draw(ATTR_POS_UV);
    }

    public Mesh fullScreenQuad() {
        return fullScreenQuad;
    }

    /* ------------------------------------------------------------------ */

    /** Must be called on the GL thread (for example via GLSurfaceView.queueEvent). */
    public void releaseGl() {
        if (scene != null) {
            scene.release();
            scene = null;
        }
        for (Panel panel : panels) {
            if (panel.textureId != 0) {
                GLES20.glDeleteTextures(1, new int[]{panel.textureId}, 0);
                panel.textureId = 0;
            }
        }
        for (Panel panel : internalPanels) {
            if (panel.textureId != 0) {
                GLES20.glDeleteTextures(1, new int[]{panel.textureId}, 0);
                panel.textureId = 0;
            }
        }
        panels.clear();
        internalPanels.clear();
        fpsPanel = null;
        whisperPanel = null;
        if (fullScreenQuad != null) {
            fullScreenQuad.release();
            fullScreenQuad = null;
        }
        leftEye.release();
        rightEye.release();
        if (environment != null) {
            environment.release();
            environment = null;
        }
        videoTexture.release();
        deleteProgram(colorProgram);
        deleteProgram(pointsProgram);
        deleteProgram(texturedProgram);
        deleteProgram(videoProgram);
        deleteProgram(distortProgram);
        deleteProgram(reticleProgram);
        glReady = false;
    }

    private static void deleteProgram(GlUtil.Program program) {
        if (program != null) {
            program.delete();
        }
    }
}
