package com.tuvr.app.vr.scene;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import com.tuvr.app.core.AppSettings;
import com.tuvr.app.core.ViewerProfile;
import com.tuvr.app.vr.GlUtil;
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
 * Live stereo test pattern for aligning the optics of a phone VR box, with the
 * adjustment controls placed inside the scene so it can be tuned while wearing
 * the headset (looking down brings the control rows into view).
 *
 * The pattern shows a grid, centre crosses per eye, a vertical ladder and the
 * current optical values: the crosses must merge into one, the frame must be
 * visible on all four sides and the vertical bars must stay straight.
 */
public class CalibrationScene implements VrScene {

    private static final float PATTERN_WIDTH_M = 1.6f;
    private static final float PATTERN_HEIGHT_M = 0.9f;
    private static final float PATTERN_DISTANCE_M = 1.5f;

    /** One adjustable optical value. */
    private interface Param {
        float get();

        void set(float value);

        String label();
    }

    private static final class Stepper {
        final Param param;
        final Panel label;
        final float step;
        final float min;
        final float max;

        Stepper(Param param, Panel label, float step, float min, float max) {
            this.param = param;
            this.label = label;
            this.step = step;
            this.min = min;
            this.max = max;
        }
    }

    private final Context context;
    private final VrHost host;
    private final AppSettings settings;
    private final List<Panel> panels = new ArrayList<>();
    private final List<Stepper> steppers = new ArrayList<>();

    private Mesh screenMesh;
    private int patternTexture;
    private Bitmap patternBitmap;
    private boolean dirty = true;
    private boolean labelsDirty = false;
    private final float[] model = new float[16];
    private VrRenderer rendererRef;

    public CalibrationScene(Context context, VrHost host) {
        this.context = context;
        this.host = host;
        this.settings = AppSettings.get(context);
    }

    /** Forces the test pattern to be redrawn on the next frame. */
    public void markDirty() {
        dirty = true;
        labelsDirty = true;
    }

    @Override
    public String title() {
        return "Calibration";
    }

    @Override
    public void onCreated(VrRenderer renderer) {
        rendererRef = renderer;
        screenMesh = MeshBuilder.quad(PATTERN_WIDTH_M, PATTERN_HEIGHT_M);
        panels.clear();
        steppers.clear();

        // Row 1: lens separation, field of view, IPD, distortion.
        addStepper(renderer, -40f, -20f, 1f, 50f, 82f, new Param() {
            @Override
            public float get() {
                return settings.lensSeparationMm();
            }

            @Override
            public void set(float value) {
                settings.setLensSeparationMm(value);
            }

            @Override
            public String label() {
                return String.format(Locale.US, "Lens sep %.0f mm", get());
            }
        });
        addStepper(renderer, -14f, -20f, 2f, 55f, 115f, new Param() {
            @Override
            public float get() {
                return settings.fovDeg();
            }

            @Override
            public void set(float value) {
                settings.setFovDeg(value);
            }

            @Override
            public String label() {
                return String.format(Locale.US, "FOV %.0f deg", get());
            }
        });
        addStepper(renderer, 12f, -20f, 1f, 50f, 78f, new Param() {
            @Override
            public float get() {
                return settings.ipdMm();
            }

            @Override
            public void set(float value) {
                settings.setIpdMm(value);
            }

            @Override
            public String label() {
                return String.format(Locale.US, "IPD %.0f mm", get());
            }
        });
        addStepper(renderer, 38f, -20f, 0.02f, 0f, 0.6f, new Param() {
            @Override
            public float get() {
                return settings.distortionK1();
            }

            @Override
            public void set(float value) {
                settings.setDistortionK1(value);
            }

            @Override
            public String label() {
                return String.format(Locale.US, "Distort %.2f", get());
            }
        });

        // Row 2: eye alignment and screen-to-lens distance.
        addStepper(renderer, -40f, -32f, 0.5f, -12f, 12f, new Param() {
            @Override
            public float get() {
                return settings.eyeOffsetXMm();
            }

            @Override
            public void set(float value) {
                settings.setEyeOffsetXMm(value);
            }

            @Override
            public String label() {
                return String.format(Locale.US, "Eye X %.0f mm", get());
            }
        });
        addStepper(renderer, -14f, -32f, 0.5f, -12f, 12f, new Param() {
            @Override
            public float get() {
                return settings.eyeOffsetYMm();
            }

            @Override
            public void set(float value) {
                settings.setEyeOffsetYMm(value);
            }

            @Override
            public String label() {
                return String.format(Locale.US, "Eye Y %.0f mm", get());
            }
        });
        addStepper(renderer, 12f, -32f, 1f, 25f, 70f, new Param() {
            @Override
            public float get() {
                return settings.screenLensDistanceMm();
            }

            @Override
            public void set(float value) {
                settings.setScreenLensDistanceMm(value);
            }

            @Override
            public String label() {
                return String.format(Locale.US, "Screen %.0f mm", get());
            }
        });

        addAction(renderer, "recenter", "Recenter", "\u25C9", 38f, -32f, host::recenter);

        // Row 3: finish.
        addAction(renderer, "reset", "Reset", "\u21BB", -12f, -43f, this::resetToShinecon);
        addAction(renderer, "save", "Save", "\u2713", 0f, -43f, this::saveAndExit);
        addAction(renderer, "exit", "Exit", "\u23FB", 12f, -43f, this::saveAndExit);

        dirty = true;
        labelsDirty = true;
    }

    /* ---------------- controls ---------------- */

    private void addStepper(VrRenderer renderer, float yawCenter, float pitch,
                            float step, float min, float max, Param param) {
        Panel label = new Panel("label_" + yawCenter + "_" + pitch);
        label.interactive = false;
        label.widthM = 0.62f;
        label.heightM = 0.2f;
        label.yawDeg = yawCenter;
        label.pitchDeg = pitch;
        label.distanceM = 2.0f;
        panels.add(label);
        final Stepper stepper = new Stepper(param, label, step, min, max);
        steppers.add(stepper);

        Panel minus = new Panel("minus_" + yawCenter + "_" + pitch);
        minus.widthM = 0.2f;
        minus.heightM = 0.2f;
        minus.yawDeg = yawCenter - 9f;
        minus.pitchDeg = pitch;
        minus.distanceM = 2.0f;
        minus.tag = (Runnable) () -> applyStep(stepper, -1f);
        minus.setContent(renderer.panelFactory().button("\u2212", "", false));
        panels.add(minus);

        Panel plus = new Panel("plus_" + yawCenter + "_" + pitch);
        plus.widthM = 0.2f;
        plus.heightM = 0.2f;
        plus.yawDeg = yawCenter + 9f;
        plus.pitchDeg = pitch;
        plus.distanceM = 2.0f;
        plus.tag = (Runnable) () -> applyStep(stepper, 1f);
        plus.setContent(renderer.panelFactory().button("\u002B", "", false));
        panels.add(plus);
    }

    private void addAction(VrRenderer renderer, String id, String text, String glyph,
                           float yaw, float pitch, Runnable action) {
        Panel panel = new Panel(id);
        panel.widthM = 0.34f;
        panel.heightM = 0.2f;
        panel.yawDeg = yaw;
        panel.pitchDeg = pitch;
        panel.distanceM = 1.9f;
        panel.tag = action;
        panel.setContent(renderer.panelFactory().button(text, glyph, false));
        panels.add(panel);
    }

    private void applyStep(Stepper stepper, float direction) {
        float value = stepper.param.get() + direction * stepper.step;
        value = MathUtil.clamp(value, stepper.min, stepper.max);
        stepper.param.set(value);
        settings.setViewerProfileId("custom");
        dirty = true;
        labelsDirty = true;
        if (rendererRef != null) {
            rendererRef.reloadSettings();
        }
        host.showWhisper(stepper.param.label());
    }

    /** Restores the Shinecon VR Box defaults and refreshes the pattern. */
    public void resetToShinecon() {
        ViewerProfile preset = ViewerProfile.preset("shinecon");
        settings.setViewerProfileId("shinecon");
        settings.setLensSeparationMm(preset.lensSeparationMm);
        settings.setFovDeg(preset.fovDeg);
        settings.setScreenLensDistanceMm(preset.screenLensDistanceMm);
        settings.setIpdMm(preset.ipdMm);
        settings.setEyeOffsetXMm(0f);
        settings.setEyeOffsetYMm(0f);
        settings.setDistortionK1(preset.distortionK1);
        settings.setDistortionK2(preset.distortionK2);
        dirty = true;
        labelsDirty = true;
        if (rendererRef != null) {
            rendererRef.reloadSettings();
        }
        host.showWhisper("Shinecon defaults restored");
    }

    /** Persists the current values (they are already stored) and leaves. */
    public void saveAndExit() {
        settings.setViewerProfileId("custom");
        host.showWhisper("Calibration saved");
        host.leaveScene();
    }

    /* ---------------- scene lifecycle ---------------- */

    @Override
    public void onSettingsChanged(VrRenderer renderer) {
        dirty = true;
        labelsDirty = true;
    }

    @Override
    public void onResumed(VrRenderer renderer) {
        dirty = true;
        labelsDirty = true;
    }

    @Override
    public void update(VrRenderer renderer, float dt) {
        if (labelsDirty) {
            labelsDirty = false;
            for (Stepper stepper : steppers) {
                stepper.label.setContent(renderer.panelFactory().textPanel(
                        stepper.param.label(), 384, 96, 34f, true, 0xFF9AA7BD));
            }
        }
        if (!dirty) {
            return;
        }
        dirty = false;
        Bitmap bitmap = renderPattern();
        if (patternTexture == 0) {
            patternTexture = GlUtil.textureFromBitmap(bitmap);
            patternBitmap = bitmap;
        } else {
            GlUtil.updateTexture(patternTexture, bitmap);
            if (patternBitmap != null && patternBitmap != bitmap && !patternBitmap.isRecycled()) {
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

        // Vertical alignment ladder: the two bars must line up vertically.
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
        canvas.drawText(String.format(Locale.US, "offset X %.1f mm   offset Y %.1f mm",
                profile.eyeOffsetXMm, profile.eyeOffsetYMm), 24f, 78f, text);
        canvas.drawText(String.format(Locale.US, "distortion %.2f / %.2f   screen %.0f mm",
                profile.distortionK1, profile.distortionK2, profile.screenLensDistanceMm),
                24f, 112f, text);
        text.setColor(0xFF9AA7BD);
        text.setTextSize(22f);
        canvas.drawText("Bars straight, crosses merged, frame fully visible",
                24f, height - 26f, text);
        return bitmap;
    }

    @Override
    public void drawEye(VrRenderer renderer, int eye, float[] viewMatrix, float[] projMatrix) {
        if (screenMesh == null || patternTexture == 0) {
            return;
        }
        MathUtil.identity(model);
        model[14] = -PATTERN_DISTANCE_M;
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
        steppers.clear();
    }
}
