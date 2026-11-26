package com.tuvr.app.vr.scene;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import com.tuvr.app.core.AppSettings;
import com.tuvr.app.core.ViewerProfile;
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
 * Live stereo test pattern for aligning the lenses of a phone VR box.
 *
 * The pattern shows a grid, centre crosses per eye and the current optical
 * values, so the user can line the two images up in the headset: the crosses
 * must merge into one, the outer frames must be visible on all four sides and
 * the vertical bars must be straight (distortion correction).
 */
public class CalibrationScene implements VrScene {

    private final Context context;
    private final VrHost host;
    private final AppSettings settings;
    private final List<Panel> panels = new ArrayList<>();

    private Mesh screenMesh;
    private int patternTexture;
    private Bitmap patternBitmap;
    private boolean dirty = true;
    private final float[] model = new float[16];

    private Panel recenterPanel;

    public CalibrationScene(Context context, VrHost host) {
        this.context = context;
        this.host = host;
        this.settings = AppSettings.get(context);
    }

    public void markDirty() {
        dirty = true;
    }

    @Override
    public String title() {
        return "Calibration";
    }

    @Override
    public void onCreated(VrRenderer renderer) {
        screenMesh = MeshBuilder.quad(1.6f, 0.9f);
        panels.clear();

        recenterPanel = new Panel("recenter");
        recenterPanel.widthM = 0.42f;
        recenterPanel.heightM = 0.2f;
        recenterPanel.pitchDeg = -26f;
        recenterPanel.distanceM = 1.6f;
        recenterPanel.tag = (Runnable) host::recenter;
        recenterPanel.setContent(renderer.panelFactory().button("Recenter", "\u25C9", false));
        panels.add(recenterPanel);
        dirty = true;
    }

    @Override
    public void onSettingsChanged(VrRenderer renderer) {
        dirty = true;
    }

    @Override
    public void onResumed(VrRenderer renderer) {
        dirty = true;
    }

    @Override
    public void update(VrRenderer renderer, float dt) {
        if (!dirty) {
            return;
        }
        dirty = false;
        Bitmap bitmap = renderPattern();
        if (patternTexture == 0) {
            patternTexture = renderer != null
                    ? com.tuvr.app.vr.GlUtil.textureFromBitmap(bitmap) : 0;
            patternBitmap = bitmap;
        } else {
            com.tuvr.app.vr.GlUtil.updateTexture(patternTexture, bitmap);
            if (patternBitmap != null && patternBitmap != bitmap
                    && !patternBitmap.isRecycled()) {
                patternBitmap.recycle();
            }
            patternBitmap = bitmap;
        }
    }

    private Bitmap renderPattern() {
        int width = 768;
        int height = 432;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(0xFF060A12);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(0x33FFFFFF);
        paint.setStrokeWidth(1f);
        for (int x = 0; x <= width; x += width / 16) {
            canvas.drawLine(x, 0, x, height, paint);
        }
        for (int y = 0; y <= height; y += height / 9) {
            canvas.drawLine(0, y, width, y, paint);
        }

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4f);
        paint.setColor(0xFF4DE0FF);
        RectF border = new RectF(6, 6, width - 6, height - 6);
        canvas.drawRect(border, paint);

        paint.setStrokeWidth(3f);
        canvas.drawLine(width / 2f, 8, width / 2f, 44, paint);
        canvas.drawLine(width / 2f, height - 44, width / 2f, height - 8, paint);
        canvas.drawLine(8, height / 2f, 54, height / 2f, paint);
        canvas.drawLine(width - 54, height / 2f, width - 8, height / 2f, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFFFFC46B);
        canvas.drawCircle(width / 2f, height / 2f, 6f, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        canvas.drawCircle(width / 2f, height / 2f, 26f, paint);
        canvas.drawCircle(width / 2f, height / 2f, 52f, paint);

        // vertical alignment ladder
        paint.setColor(0x99FFFFFF);
        for (int i = -4; i <= 4; i++) {
            float x = width / 2f + i * 40f;
            canvas.drawLine(x, height / 2f - 60, x, height / 2f + 60, paint);
        }

        ViewerProfile profile = settings.viewerProfile();
        Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setColor(0xFFE9F2FF);
        text.setTextSize(26f);
        canvas.drawText(String.format(Locale.US, "FOV %.0f deg   lens %.0f mm   IPD %.0f mm",
                profile.fovDeg, profile.lensSeparationMm, profile.ipdMm), 24f, 44f, text);
        canvas.drawText(String.format(Locale.US, "offset X %.0f mm   offset Y %.0f mm",
                profile.eyeOffsetXMm, profile.eyeOffsetYMm), 24f, 78f, text);
        canvas.drawText(String.format(Locale.US, "distortion %.2f / %.2f   chroma %.3f",
                profile.distortionK1, profile.distortionK2, profile.chromaticShift),
                24f, 112f, text);
        text.setColor(0xFF9AA7BD);
        text.setTextSize(22f);
        canvas.drawText("Bars must be straight, crosses must merge, frame fully visible",
                24f, height - 26f, text);
        return bitmap;
    }

    @Override
    public void drawEye(VrRenderer renderer, int eye, float[] viewMatrix, float[] projMatrix) {
        if (screenMesh == null || patternTexture == 0) {
            return;
        }
        MathUtil.identity(model);
        model[12] = 0f;
        model[13] = 0f;
        model[14] = -1.5f;
        renderer.drawTextured(screenMesh, patternTexture, model, 1f);
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
        if (screenMesh != null) {
            screenMesh.release();
            screenMesh = null;
        }
        if (patternTexture != 0) {
            android.opengl.GLES20.glDeleteTextures(1, new int[]{patternTexture}, 0);
            patternTexture = 0;
        }
        if (patternBitmap != null && !patternBitmap.isRecycled()) {
            patternBitmap.recycle();
            patternBitmap = null;
        }
        for (Panel panel : panels) {
            panel.release();
        }
        panels.clear();
    }
}
