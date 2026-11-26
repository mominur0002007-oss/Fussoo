package com.tuvr.app.vr.scene;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;

import com.tuvr.app.vr.MathUtil;
import com.tuvr.app.vr.Mesh;
import com.tuvr.app.vr.MeshBuilder;
import com.tuvr.app.vr.Panel;
import com.tuvr.app.vr.VrRenderer;
import com.tuvr.app.vr.VrScene;

import java.util.ArrayList;
import java.util.List;

/**
 * VR browser scene.
 *
 * The page is drawn by a WebView in software mode and copied into a texture a
 * few times per second, then rendered on a large virtual screen. This keeps the
 * implementation honest and cheap: it is meant for reading and navigation, not
 * for smooth video playback inside VR.
 */
public class BrowserScene implements VrScene {

    public interface Bridge {
        void onEditUrl();

        void onExit();

        /** In-scene Back panel: go back in history, do not exit. */
        void onBack();

        void onForward();

        void onReload();

        String currentUrl();

        /** Hands the page to another app (used for YouTube videos). */
        void onOpenInApp();
    }

    private static final int CAPTURE_WIDTH = 960;
    private static final int CAPTURE_HEIGHT = 540;
    private static final long CAPTURE_INTERVAL_MS = 260L;

    private final Context context;
    private final WebView webView;
    private final Bridge bridge;
    private final List<Panel> panels = new ArrayList<>();

    private Mesh screenMesh;
    private Panel pagePanel;
    private Panel urlPanel;
    private final float[] model = new float[16];

    private Bitmap bufferA;
    private Bitmap bufferB;
    private Canvas canvas;
    private int bufferIndex;
    private boolean captureEnabled = true;

    /** Consecutive captures that came back as one flat colour (blank page). */
    private int blankCaptures;
    private boolean blankReported;
    private VrRenderer rendererRef;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable captureTask = new Runnable() {
        @Override
        public void run() {
            capture();
            if (captureEnabled) {
                handler.postDelayed(this, CAPTURE_INTERVAL_MS);
            }
        }
    };

    public BrowserScene(Context context, WebView webView, Bridge bridge) {
        this.context = context;
        this.webView = webView;
        this.bridge = bridge;
    }

    @Override
    public String title() {
        return "VR Browser";
    }

    @Override
    public void onCreated(VrRenderer renderer) {
        screenMesh = MeshBuilder.quad(2.6f, 1.4625f);
        bufferA = Bitmap.createBitmap(CAPTURE_WIDTH, CAPTURE_HEIGHT, Bitmap.Config.ARGB_8888);
        bufferB = Bitmap.createBitmap(CAPTURE_WIDTH, CAPTURE_HEIGHT, Bitmap.Config.ARGB_8888);
        canvas = new Canvas(bufferA);
        panels.clear();

        pagePanel = new Panel("page");
        pagePanel.interactive = false;
        pagePanel.recycleOnReplace = false;
        pagePanel.widthM = 2.6f;
        pagePanel.heightM = 1.4625f;
        pagePanel.distanceM = 3.2f;
        pagePanel.setContent(bufferA);
        panels.add(pagePanel);

        urlPanel = new Panel("url");
        urlPanel.widthM = 1.6f;
        urlPanel.heightM = 0.22f;
        urlPanel.pitchDeg = 22f;
        urlPanel.distanceM = 2.6f;
        urlPanel.tag = (Runnable) bridge::onEditUrl;
        panels.add(urlPanel);

        addButton(renderer, "back", "\u25C0", "Back", -30f, () -> bridge.onBack());
        addButton(renderer, "forward", "\u25B6", "Forward", -14f, () -> bridge.onForward());
        addButton(renderer, "reload", "\u21BB", "Reload", 2f, () -> bridge.onReload());
        addButton(renderer, "url", "\u270E", "URL", 18f, () -> bridge.onEditUrl());
        addButton(renderer, "exit", "\u23FB", "Exit", 34f, () -> bridge.onExit());
        addButton(renderer, "app", "\u2197", "YouTube app", 0f,
                () -> bridge.onOpenInApp(), -38f);

        rendererRef = renderer;
        startCapture();
    }

    private void addButton(VrRenderer renderer, String id, String glyph, String label,
                           float yaw, Runnable action) {
        addButton(renderer, id, glyph, label, yaw, action, -25f);
    }

    private void addButton(VrRenderer renderer, String id, String glyph, String label,
                           float yaw, Runnable action, float pitch) {
        Panel panel = new Panel(id);
        panel.widthM = 0.4f;
        panel.heightM = 0.22f;
        panel.yawDeg = yaw;
        panel.pitchDeg = pitch;
        panel.distanceM = 2.3f;
        panel.tag = action;
        panel.setContent(renderer.panelFactory().button(label, glyph, false));
        panels.add(panel);
    }

    private void startCapture() {
        captureEnabled = true;
        handler.removeCallbacks(captureTask);
        handler.post(captureTask);
    }

    private void stopCapture() {
        captureEnabled = false;
        handler.removeCallbacks(captureTask);
    }

    private void capture() {
        if (webView == null || pagePanel == null) {
            return;
        }
        Bitmap target = bufferIndex == 0 ? bufferA : bufferB;
        bufferIndex = 1 - bufferIndex;
        if (target.isRecycled()) {
            return;
        }
        try {
            target.eraseColor(Color.BLACK);
            canvas.setBitmap(target);
            webView.draw(canvas);
            pagePanel.setContent(target);
            checkForBlankCapture(target);
        } catch (Exception ignored) {
        }
        updateUrlPanel();
    }

    /**
     * Some devices refuse to give a software WebView real pixels. Rather than
     * silently showing a black rectangle, detect it and tell the user once.
     */
    private void checkForBlankCapture(Bitmap bitmap) {
        if (blankReported) {
            return;
        }
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        int[] row = new int[width];
        bitmap.getPixels(row, 0, width, 0, height / 2, width, 1);
        int first = row[0];
        boolean uniform = true;
        for (int i = 1; i < width; i++) {
            if (row[i] != first) {
                uniform = false;
                break;
            }
        }
        if (!uniform) {
            blankCaptures = 0;
            return;
        }
        blankCaptures++;
        if (blankCaptures >= 8 && rendererRef != null) {
            blankReported = true;
            rendererRef.showWhisper(context.getString(com.tuvr.app.R.string.browser_blank_capture),
                    7000);
        }
    }

    private String lastUrl = "";

    private void updateUrlPanel() {
        String url = bridge.currentUrl();
        if (url == null) {
            url = "";
        }
        if (url.equals(lastUrl)) {
            return;
        }
        lastUrl = url;
        if (pagePanel != null && context != null) {
            // Reuse the URL panel: keep the visible string short.
            String display = url.replace("https://", "").replace("http://", "");
            if (display.length() > 64) {
                display = display.substring(0, 63) + "\u2026";
            }
            urlPanel.setContent(new com.tuvr.app.vr.PanelFactory().textPanel(display,
                    768, 96, 30f, true, 0xFF9AA7BD));
        }
    }

    @Override
    public void onSettingsChanged(VrRenderer renderer) {
    }

    @Override
    public void onPaused(VrRenderer renderer) {
        stopCapture();
    }

    @Override
    public void onResumed(VrRenderer renderer) {
        startCapture();
    }

    @Override
    public void update(VrRenderer renderer, float dt) {
    }

    @Override
    public void drawEye(VrRenderer renderer, int eye, float[] viewMatrix, float[] projMatrix) {
        if (screenMesh == null || pagePanel == null) {
            return;
        }
        renderer.ensureTexture(pagePanel);
        if (pagePanel.textureId == 0) {
            return;
        }
        MathUtil.identity(model);
        model[12] = 0f;
        model[13] = 0f;
        model[14] = -3.2f;
        renderer.drawTextured(screenMesh, pagePanel.textureId, model, 1f);
    }

    @Override
    public void collectPanels(VrRenderer renderer, List<Panel> out) {
        for (Panel panel : panels) {
            if (panel != pagePanel) {
                out.add(panel);
            }
        }
    }

    @Override
    public void onPanelSelected(Panel panel, VrRenderer renderer) {
        if (panel.tag instanceof Runnable) {
            ((Runnable) panel.tag).run();
        }
    }

    public void setCaptureEnabled(boolean enabled) {
        if (enabled) {
            startCapture();
        } else {
            stopCapture();
        }
    }

    @Override
    public void release() {
        stopCapture();
        if (screenMesh != null) {
            screenMesh.release();
            screenMesh = null;
        }
        for (Panel panel : panels) {
            panel.release();
        }
        panels.clear();
        if (bufferA != null && !bufferA.isRecycled()) {
            bufferA.recycle();
        }
        if (bufferB != null && !bufferB.isRecycled()) {
            bufferB.recycle();
        }
        bufferA = null;
        bufferB = null;
    }
}
