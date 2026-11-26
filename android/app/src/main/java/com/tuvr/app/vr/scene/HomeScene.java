package com.tuvr.app.vr.scene;

import android.content.Context;
import android.graphics.Bitmap;

import com.tuvr.app.core.AppSettings;
import com.tuvr.app.media.MediaStateDb;
import com.tuvr.app.media.VideoItem;
import com.tuvr.app.media.VideoLibrary;
import com.tuvr.app.vr.Panel;
import com.tuvr.app.vr.VrHost;
import com.tuvr.app.vr.VrRenderer;
import com.tuvr.app.vr.VrScene;

import java.util.ArrayList;
import java.util.List;

/**
 * The VR home environment: a gentle arc of floating glass panels with head-gaze
 * selection, plus a "continue watching" shortcut for the last video.
 */
public class HomeScene implements VrScene {

    private final Context context;
    private final VrHost host;
    private final List<Panel> panels = new ArrayList<>();

    private Panel titlePanel;
    private Panel resumePanel;
    private long lastRefreshMs;
    private String resumeLabel = "";

    public HomeScene(Context context, VrHost host) {
        this.context = context;
        this.host = host;
    }

    @Override
    public String title() {
        return "TU VR";
    }

    @Override
    public void onCreated(VrRenderer renderer) {
        panels.clear();
        float[][] positions = {
                // yaw, pitch
                {-42f, 6f}, {-21f, 8f}, {0f, 9f}, {21f, 8f}, {42f, 6f},
                {0f, -24f}
        };

        titlePanel = new Panel("title");
        titlePanel.interactive = false;
        titlePanel.widthM = 1.5f;
        titlePanel.heightM = 0.34f;
        titlePanel.pitchDeg = 24f;
        titlePanel.distanceM = 3.4f;
        setPanelContent(titlePanel, renderer.panelFactory().textPanel(
                "TU VR\nlook and hold to select", 512, 128, 34f, true, 0xFFE9F2FF));

        addTile(renderer, "cinema", "\u25B6", "VR Cinema", "big screen", positions[0][0],
                positions[0][1], () -> host.openVrCinema(null, false));
        addTile(renderer, "gallery", "\u25A6", "Videos", "this phone", positions[1][0],
                positions[1][1], host::openVrGallery);
        addTile(renderer, "resume", "\u21BB", "Continue", "last video", positions[2][0],
                positions[2][1], () -> {
                    AppSettings settings = AppSettings.get(context);
                    String uri = settings.lastVideoUri();
                    if (uri != null) {
                        host.openVrCinema(uri, true);
                    } else {
                        host.showWhisper("No recent video");
                    }
                });
        addTile(renderer, "apps", "\u25A4", "Apps", "launcher", positions[3][0],
                positions[3][1], host::openVrApps);
        addTile(renderer, "browser", "\u25CE", "Browser", "web screen", positions[4][0],
                positions[4][1], () -> host.openVrBrowser(null));

        // Second row: system actions
        addTile(renderer, "settings", "\u2699", "Settings", "viewer + quality", -31f, -24f,
                host::openSettings);
        addTile(renderer, "calibrate", "\u2316", "Calibrate", "lens alignment", -10f, -24f,
                host::openCalibration);
        addTile(renderer, "recenter", "\u25C9", "Recenter", "level the view", 11f, -24f,
                host::recenter);
        addTile(renderer, "diagnostics", "\u2139", "Diagnostics", "sensors + GPU", 32f, -24f,
                host::openDiagnostics);
        addTile(renderer, "exit", "\u23FB", "Exit VR", "back to phone", 0f, -38f,
                host::exitVr);

        panels.add(titlePanel);
        refreshResume(renderer);
    }

    private void addTile(VrRenderer renderer, String id, String glyph, String title,
                         String subtitle, float yaw, float pitch, Runnable action) {
        Panel panel = new Panel(id);
        panel.widthM = 1.05f;
        panel.heightM = 0.52f;
        panel.yawDeg = yaw;
        panel.pitchDeg = pitch;
        panel.distanceM = 3.1f;
        panel.tag = action;
        setPanelContent(panel, renderer.panelFactory().tile(glyph, title, subtitle, false));
        panels.add(panel);
    }

    private void setPanelContent(Panel panel, Bitmap bitmap) {
        if (panel.content != null && !panel.content.isRecycled()) {
            panel.content.recycle();
        }
        panel.setContent(bitmap);
    }

    private void refreshResume(VrRenderer renderer) {
        AppSettings settings = AppSettings.get(context);
        String uri = settings.lastVideoUri();
        String label = "";
        if (uri != null) {
            VideoItem item = VideoLibrary.find(context, uri);
            if (item != null && item.name != null) {
                label = item.name;
            }
            long position = MediaStateDb.get(context).resumePosition(uri);
            if (position > 0) {
                label = label + "  @" + com.tuvr.app.core.Formats.duration(position);
            }
        }
        if (!label.equals(resumeLabel)) {
            resumeLabel = label;
            for (Panel panel : panels) {
                if ("resume".equals(panel.id)) {
                    setPanelContent(panel, renderer.panelFactory().tile("\u21BB", "Continue",
                            label.isEmpty() ? "no recent video" : label, false));
                }
            }
        }
    }

    @Override
    public void onSettingsChanged(VrRenderer renderer) {
        lastRefreshMs = 0;
    }

    @Override
    public void onResumed(VrRenderer renderer) {
        lastRefreshMs = 0;
    }

    @Override
    public void update(VrRenderer renderer, float dt) {
        long now = System.currentTimeMillis();
        if (now - lastRefreshMs > 4000L) {
            lastRefreshMs = now;
            refreshResume(renderer);
        }
    }

    @Override
    public void drawEye(VrRenderer renderer, int eye, float[] viewMatrix, float[] projMatrix) {
        // Panels are drawn by the renderer; the home scene has no other geometry.
    }

    @Override
    public void collectPanels(VrRenderer renderer, List<Panel> out) {
        out.addAll(panels);
    }

    @Override
    public void onPanelSelected(Panel panel, VrRenderer renderer) {
        if (panel.tag instanceof Runnable) {
            ((Runnable) panel.tag).run();
        }
    }

    @Override
    public void release() {
        for (Panel panel : panels) {
            if (panel.content != null && !panel.content.isRecycled()) {
                panel.content.recycle();
                panel.content = null;
            }
        }
        panels.clear();
        resumePanel = null;
    }
}
