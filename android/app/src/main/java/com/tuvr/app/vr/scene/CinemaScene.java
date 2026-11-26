package com.tuvr.app.vr.scene;

import android.content.Context;

import com.tuvr.app.core.AppSettings;
import com.tuvr.app.core.Formats;
import com.tuvr.app.media.VideoFormat;
import com.tuvr.app.player.VideoSession;
import com.tuvr.app.vr.MathUtil;
import com.tuvr.app.vr.Mesh;
import com.tuvr.app.vr.MeshBuilder;
import com.tuvr.app.vr.Panel;
import com.tuvr.app.vr.VrHost;
import com.tuvr.app.vr.VrRenderer;
import com.tuvr.app.vr.VrScene;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * VR Cinema: a large virtual screen in a selectable environment with world-locked
 * control panels and full 2D / VR180 / VR360 / SBS / TAB rendering.
 *
 * The scene never touches ExoPlayer directly. The activity pushes a small
 * snapshot (position, duration, playing flag, video size, subtitle) that this
 * scene renders, which keeps all player calls on the main thread where Media3
 * requires them.
 */
public class CinemaScene implements VrScene {

    private static final float BASE_SCREEN_WIDTH_M = 2.8f;

    private final Context context;
    private final VrHost host;
    private final AppSettings settings;
    private final VideoSession session;
    private final VideoFormat format;

    private final List<Panel> panels = new ArrayList<>();

    private Mesh flatMesh;
    private Mesh sphereMesh;
    private Mesh hemisphereMesh;
    private float meshAspect = -1f;
    private float meshCurvature = -1f;
    private float meshWidth = -1f;
    private float meshHeight = -1f;

    private Panel playPanel;
    private Panel progressPanel;
    private Panel subtitlePanel;
    private Panel speedPanel;

    private volatile boolean playing;
    private volatile long positionMs;
    private volatile long durationMs;
    private volatile float videoAspect = 16f / 9f;
    private volatile String subtitleText = "";
    private volatile float speed = 1f;

    private String lastProgressText = "";
    private String lastSubtitleText = "";
    private boolean lastPlayingState;

    private float volume = 1f;
    private float brightness = 0.6f;
    private VrRenderer rendererRef;
    private final float[] modelMatrix = new float[16];

    public CinemaScene(Context context, VrHost host, VideoSession session, VideoFormat format) {
        this.context = context;
        this.host = host;
        this.settings = AppSettings.get(context);
        this.session = session;
        this.format = format;
        this.brightness = MathUtil.clamp(host.screenBrightness(), 0.05f, 1f);
        this.volume = MathUtil.clamp(settings.volumeFraction(), 0.05f, 1f);
    }

    @Override
    public String title() {
        return "VR Cinema";
    }

    /* ---------------- data pushed from the activity ---------------- */

    public void setPlaybackState(boolean playing, long positionMs, long durationMs) {
        this.playing = playing;
        this.positionMs = positionMs;
        this.durationMs = durationMs;
    }

    public void setVideoAspect(float aspect) {
        if (aspect > 0.05f) {
            this.videoAspect = aspect;
        }
    }

    public void setSubtitle(String text) {
        this.subtitleText = text == null ? "" : text;
    }

    public void setSpeed(float speed) {
        this.speed = speed;
    }

    public VideoFormat format() {
        return format;
    }

    public void cycleProjection() {
        format.projection = (format.projection + 1) % 3;
        host.showWhisper(VideoFormat.projectionName(format.projection));
    }

    /* ---------------- scene lifecycle ---------------- */

    @Override
    public void onCreated(final VrRenderer renderer) {
        rendererRef = renderer;
        flatMesh = null;
        sphereMesh = MeshBuilder.sphere(10f, 48, 24);
        hemisphereMesh = MeshBuilder.hemisphere(10f, 40, 20);
        buildPanels(renderer);

        // The player can only render into the texture once the GL surface exists.
        final VideoSession currentSession = session;
        if (currentSession != null) {
            com.tuvr.app.core.Background.main(() ->
                    currentSession.setVideoSurface(renderer.videoTexture().surface()));
        }
    }

    private void buildPanels(VrRenderer renderer) {
        panels.clear();
        float iconRowPitch = -23f;
        float iconRowDistance = 2.15f;
        float[] yaws = {-40f, -30f, -20f, -10f, 0f, 10f, 20f, 30f, 40f};

        addControl(renderer, "exit", "\u23FB", "Exit", yaws[0], iconRowPitch, iconRowDistance,
                host::leaveScene);
        addControl(renderer, "rew", "\u21BA", "-10s", yaws[1], iconRowPitch, iconRowDistance,
                () -> seekBy(-10000L));
        addControl(renderer, "prevproj", "\u25A6", "Mode", yaws[2], iconRowPitch, iconRowDistance,
                this::cycleProjection);
        addControl(renderer, "speed", "\u00D7", "Speed", yaws[3], iconRowPitch, iconRowDistance,
                this::cycleSpeed);
        playPanel = addControl(renderer, "play", "\u23F8", "Pause", yaws[4], iconRowPitch,
                iconRowDistance, this::togglePlay);
        addControl(renderer, "reset", "\u21BB", "Reset", yaws[5], iconRowPitch, iconRowDistance,
                this::resetScreen);
        addControl(renderer, "fwd", "\u21BB", "+10s", yaws[6], iconRowPitch, iconRowDistance,
                () -> seekBy(10000L));
        addControl(renderer, "smaller", "\u2212", "Smaller", yaws[7], iconRowPitch,
                iconRowDistance, () -> changeSize(-1));
        addControl(renderer, "bigger", "\u002B", "Bigger", yaws[8], iconRowPitch,
                iconRowDistance, () -> changeSize(1));

        float row2 = -32f;
        float[] yaws2 = {-40f, -24f, -8f, 8f, 24f, 40f};
        addControl(renderer, "closer", "\u2191", "Nearer", yaws2[0], row2, iconRowDistance,
                () -> changeDistance(-1));
        addControl(renderer, "farther", "\u2193", "Farther", yaws2[1], row2, iconRowDistance,
                () -> changeDistance(1));
        addControl(renderer, "recenter", "\u25C9", "Recenter", yaws2[2], row2, iconRowDistance,
                host::recenter);
        addControl(renderer, "dim", "\u25D1", "Dim", yaws2[3], row2, iconRowDistance,
                () -> changeBrightness(-1));
        addControl(renderer, "bright", "\u25D0", "Bright", yaws2[4], row2, iconRowDistance,
                () -> changeBrightness(1));
        addControl(renderer, "volume", "\u25B6\u25B6", "Volume", yaws2[5], row2, iconRowDistance,
                this::toggleVolumeStep);
        addControl(renderer, "environment", "\u25A3", "Env", 52f, row2, iconRowDistance,
                this::cycleEnvironment);

        progressPanel = new Panel("progress");
        progressPanel.interactive = false;
        progressPanel.widthM = 1.15f;
        progressPanel.heightM = 0.2f;
        progressPanel.pitchDeg = -13f;
        progressPanel.distanceM = 2.3f;
        panels.add(progressPanel);

        subtitlePanel = new Panel("subtitle");
        subtitlePanel.interactive = false;
        subtitlePanel.widthM = 1.9f;
        subtitlePanel.heightM = 0.3f;
        subtitlePanel.pitchDeg = -7f;
        subtitlePanel.distanceM = 3.2f;
        panels.add(subtitlePanel);

        speedPanel = new Panel("speedlabel");
        speedPanel.interactive = false;
        speedPanel.widthM = 0.42f;
        speedPanel.heightM = 0.16f;
        speedPanel.yawDeg = 52f;
        speedPanel.pitchDeg = -23f;
        speedPanel.distanceM = 2.15f;
        panels.add(speedPanel);

        updatePlayPanel(renderer);
        updateProgressPanel(renderer, true);
        updateSpeedPanel(renderer);
    }

    private Panel addControl(VrRenderer renderer, String id, String glyph, String label,
                             float yaw, float pitch, float distance, Runnable action) {
        Panel panel = new Panel(id);
        panel.widthM = 0.36f;
        panel.heightM = 0.24f;
        panel.yawDeg = yaw;
        panel.pitchDeg = pitch;
        panel.distanceM = distance;
        panel.tag = action;
        panel.setContent(renderer.panelFactory().button(label, glyph, false));
        panels.add(panel);
        return panel;
    }

    /* ---------------- controls ---------------- */

    private void togglePlay() {
        if (session == null) {
            return;
        }
        session.togglePlayPause();
    }

    private void seekBy(long deltaMs) {
        if (session == null) {
            return;
        }
        long target = Math.max(0L, session.position() + deltaMs);
        session.seekTo(target);
    }

    private void cycleSpeed() {
        if (session == null) {
            return;
        }
        float[] speeds = {0.75f, 1f, 1.25f, 1.5f};
        float current = speed;
        int index = 0;
        for (int i = 0; i < speeds.length; i++) {
            if (Math.abs(speeds[i] - current) < 0.01f) {
                index = i;
                break;
            }
        }
        index = (index + 1) % speeds.length;
        speed = speeds[index];
        session.setSpeed(speed);
        host.showWhisper(String.format(Locale.US, "Speed %.2fx", speed));
    }

    private void changeSize(int direction) {
        settings.setScreenSize(settings.screenSize() + direction * 0.08f);
        if (rendererRef != null) {
            rendererRef.reloadSettings();
        }
        host.showWhisper("Screen " + Formats.percent(settings.screenSize() / 2.2f));
    }

    private void changeDistance(int direction) {
        settings.setScreenDistance(settings.screenDistance() + direction * 0.5f);
        if (rendererRef != null) {
            rendererRef.reloadSettings();
        }
        host.showWhisper("Distance " + Formats.oneDecimal(settings.screenDistance()) + " m");
    }

    private void changeBrightness(int direction) {
        brightness = MathUtil.clamp(brightness + direction * 0.1f, 0.1f, 1f);
        host.setScreenBrightness(brightness);
        host.showWhisper("Brightness " + Formats.percent(brightness));
    }

    private void toggleVolumeStep() {
        volume += 0.2f;
        if (volume > 1.001f) {
            volume = 0.2f;
        }
        if (session != null) {
            session.setVolume(volume);
        }
        host.showWhisper("Volume " + Formats.percent(volume));
    }

    /** Cycles the virtual environment without leaving the cinema. */
    private void cycleEnvironment() {
        int count = com.tuvr.app.vr.EnvironmentBuilder.NAMES.length;
        int next = (settings.environment() + 1) % count;
        settings.setEnvironment(next);
        if (rendererRef != null) {
            rendererRef.reloadSettings();
        }
        host.showWhisper(com.tuvr.app.vr.EnvironmentBuilder.name(next));
    }

    private void resetScreen() {
        settings.setScreenSize(1f);
        settings.setScreenDistance(6f);
        settings.setCurvature(0.25f);
        settings.setScreenY(0f);
        if (rendererRef != null) {
            rendererRef.reloadSettings();
        }
        host.showWhisper("Screen reset");
    }

    /* ---------------- scene API ---------------- */

    @Override
    public void onSettingsChanged(VrRenderer renderer) {
        meshAspect = -1f;
        meshCurvature = -1f;
        host.setScreenBrightness(brightness);
    }

    @Override
    public void onResumed(VrRenderer renderer) {
        final VideoSession currentSession = session;
        if (currentSession != null) {
            com.tuvr.app.core.Background.main(() ->
                    currentSession.setVideoSurface(renderer.videoTexture().surface()));
        }
    }

    @Override
    public void update(VrRenderer renderer, float dt) {
        if (playing != lastPlayingState) {
            lastPlayingState = playing;
            updatePlayPanel(renderer);
        }
        String progress = Formats.duration(positionMs) + "  /  " + Formats.duration(durationMs)
                + (durationMs > 0
                ? "   " + Formats.percent(durationMs == 0 ? 0f : (float) positionMs / durationMs)
                : "");
        if (!progress.equals(lastProgressText)) {
            lastProgressText = progress;
            updateProgressPanel(renderer, false);
        }
        String subtitle = subtitleText;
        if (!subtitle.equals(lastSubtitleText)) {
            lastSubtitleText = subtitle;
            updateSubtitlePanel(renderer);
        }
    }

    private void updatePlayPanel(VrRenderer renderer) {
        if (playPanel == null) {
            return;
        }
        playPanel.setContent(renderer.panelFactory().button(
                playing ? "Pause" : "Play", playing ? "\u23F8" : "\u25B6", playing));
    }

    private void updateProgressPanel(VrRenderer renderer, boolean force) {
        if (progressPanel == null) {
            return;
        }
        String text = lastProgressText.isEmpty()
                ? Formats.duration(positionMs) + "  /  " + Formats.duration(durationMs)
                : lastProgressText;
        progressPanel.setContent(renderer.panelFactory().textPanel(text, 512, 96, 34f,
                true, 0xFFE9F2FF));
    }

    private void updateSubtitlePanel(VrRenderer renderer) {
        if (subtitlePanel == null) {
            return;
        }
        if (lastSubtitleText.isEmpty()) {
            subtitlePanel.visible = false;
            return;
        }
        subtitlePanel.visible = true;
        subtitlePanel.setContent(renderer.panelFactory().textPanel(lastSubtitleText,
                768, 128, 40f, true, 0xFFFFFFFF));
    }

    private void updateSpeedPanel(VrRenderer renderer) {
        if (speedPanel == null) {
            return;
        }
        speedPanel.setContent(renderer.panelFactory().textPanel(
                String.format(Locale.US, "%.2fx", speed).replace("0x", "x"),
                256, 96, 40f, true, 0xFF9AA7BD));
    }

    @Override
    public void drawEye(VrRenderer renderer, int eye, float[] viewMatrix, float[] projMatrix) {
        if (session == null) {
            return;
        }
        float[] model = modelMatrix;
        // Left/right eye swapping is handled once for the whole frame by the
        // renderer's composite pass, so both eyes must sample their natural half.
        int uvEye = eye;

        float scaleX = 1f;
        float scaleY = 1f;
        float offsetX = 0.5f;
        float offsetY = 0.5f;
        if (format.stereo == VideoFormat.STEREO_SBS) {
            scaleX = 0.5f;
            offsetX = uvEye == 0 ? 0.25f : 0.75f;
        } else if (format.stereo == VideoFormat.STEREO_TAB) {
            scaleY = 0.5f;
            offsetY = uvEye == 0 ? 0.25f : 0.75f;
        }

        if (format.projection == VideoFormat.PROJ_FLAT) {
            float width = BASE_SCREEN_WIDTH_M * settings.screenSize();
            float aspect = videoAspect > 0.05f ? videoAspect : 16f / 9f;
            float height = width / aspect;
            boolean curved = settings.curvature() > 0.02f;
            if (flatMesh == null
                    || Math.abs(meshAspect - aspect) > 0.01f
                    || Math.abs(meshCurvature - settings.curvature()) > 0.01f
                    || Math.abs(meshWidth - width) > 0.005f
                    || Math.abs(meshHeight - height) > 0.005f) {
                if (flatMesh != null) {
                    flatMesh.release();
                }
                flatMesh = curved
                        ? MeshBuilder.curvedQuad(width, height, settings.curvature())
                        : MeshBuilder.quad(width, height);
                meshAspect = aspect;
                meshCurvature = settings.curvature();
                meshWidth = width;
                meshHeight = height;
            }
            MathUtil.identity(model);
            model[12] = 0f;
            model[13] = settings.screenY();
            model[14] = -settings.screenDistance();
            renderer.drawVideo(flatMesh, model, scaleX, scaleY, offsetX, offsetY,
                    format.rotationDeg * MathUtil.DEG_TO_RAD, 1f);
        } else {
            MathUtil.identity(model);
            if (format.projection == VideoFormat.PROJ_VR360) {
                if (sphereMesh != null) {
                    renderer.drawVideo(sphereMesh, model, scaleX, scaleY, offsetX, offsetY,
                            format.rotationDeg * MathUtil.DEG_TO_RAD, 1f);
                }
            } else if (hemisphereMesh != null) {
                renderer.drawVideo(hemisphereMesh, model, scaleX, scaleY, offsetX, offsetY,
                        format.rotationDeg * MathUtil.DEG_TO_RAD, 1f);
            }
        }
    }

    @Override
    public void collectPanels(VrRenderer renderer, List<Panel> out) {
        updateSpeedPanelIfNeeded(renderer);
        out.addAll(panels);
    }

    private void updateSpeedPanelIfNeeded(VrRenderer renderer) {
        if (speedPanel == null) {
            return;
        }
        String text = String.format(Locale.US, "%.2fx", speed);
        if (speedPanel.content == null || !text.equals(lastSpeedLabel)) {
            lastSpeedLabel = text;
            speedPanel.setContent(renderer.panelFactory().textPanel(text, 256, 96, 40f,
                    true, 0xFF9AA7BD));
        }
    }

    private String lastSpeedLabel = "";

    @Override
    public void onPanelSelected(Panel panel, VrRenderer renderer) {
        if (panel.tag instanceof Runnable) {
            ((Runnable) panel.tag).run();
        }
    }

    @Override
    public void release() {
        if (flatMesh != null) {
            flatMesh.release();
            flatMesh = null;
        }
        if (sphereMesh != null) {
            sphereMesh.release();
            sphereMesh = null;
        }
        if (hemisphereMesh != null) {
            hemisphereMesh.release();
            hemisphereMesh = null;
        }
        for (Panel panel : panels) {
            if (panel.content != null && !panel.content.isRecycled()) {
                panel.content.recycle();
                panel.content = null;
            }
        }
        panels.clear();
    }
}
