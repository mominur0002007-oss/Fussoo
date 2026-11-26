package com.tuvr.app.ui;

import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.tuvr.app.R;
import com.tuvr.app.core.AppSettings;
import com.tuvr.app.vr.VrActivity;
import com.tuvr.app.vr.VrScene;
import com.tuvr.app.vr.scene.CalibrationScene;

import java.util.Locale;

/**
 * Live viewer calibration. The phone shows sliders while the headset shows a
 * stereo test pattern, so lens separation, FOV, eye alignment and distortion can
 * be dialled in by eye.
 */
public class CalibrationActivity extends VrActivity {

    private CalibrationScene scene;
    private AppSettings settings;
    private View panel;

    @Override
    protected VrScene createScene() {
        if (settings == null) {
            settings = AppSettings.get(this);
        }
        scene = new CalibrationScene(this, this);
        return scene;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        settings = AppSettings.get(this);

        panel = LayoutInflater.from(this).inflate(R.layout.view_calibration_panel, null, false);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(panel);
        scroll.setBackgroundColor(0xCC070A10);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (250 * getResources()
                .getDisplayMetrics().density), Gravity.BOTTOM);
        params.setMargins(0, 0, 0, 0);
        scroll.setLayoutParams(params);
        contentRoot().addView(scroll);

        bindSlider(R.id.row_lens, R.string.pref_lens_separation, 50f, 82f,
                settings.lensSeparationMm(), "%",
                value -> {
                    settings.setLensSeparationMm(value);
                    settings.setViewerProfileId("custom");
                });

        bindSlider(R.id.row_fov, R.string.pref_fov, 55f, 115f, settings.fovDeg(), "°",
                value -> {
                    settings.setFovDeg(value);
                    settings.setViewerProfileId("custom");
                });

        bindSlider(R.id.row_offset_y, R.string.pref_eye_offset_y, -20f, 20f,
                settings.eyeOffsetYMm(), " mm", settings::setEyeOffsetYMm);

        bindSlider(R.id.row_offset_x, R.string.pref_eye_offset_x, -20f, 20f,
                settings.eyeOffsetXMm(), " mm", settings::setEyeOffsetXMm);

        bindSlider(R.id.row_distortion, R.string.pref_distortion, 0f, 60f,
                settings.distortionK1() * 100f, "",
                value -> settings.setDistortionK1(value / 100f));

        bindSlider(R.id.row_distance, R.string.pref_screen_distance, 25f, 70f,
                settings.screenLensDistanceMm(), " mm", settings::setScreenLensDistanceMm);

        Button reset = panel.findViewById(R.id.btn_reset);
        Button save = panel.findViewById(R.id.btn_save);
        reset.setOnClickListener(v -> {
            settings.setViewerProfileId("shinecon");
            com.tuvr.app.core.ViewerProfile preset =
                    com.tuvr.app.core.ViewerProfile.preset("shinecon");
            settings.setLensSeparationMm(preset.lensSeparationMm);
            settings.setFovDeg(preset.fovDeg);
            settings.setScreenLensDistanceMm(preset.screenLensDistanceMm);
            settings.setEyeOffsetXMm(0f);
            settings.setEyeOffsetYMm(0f);
            settings.setDistortionK1(preset.distortionK1);
            settings.setDistortionK2(preset.distortionK2);
            reloadValues();
            renderer.reloadSettings();
            scene.markDirty();
            showWhisper("Viewer reset to Shinecon defaults");
        });
        save.setOnClickListener(v -> {
            settings.setViewerProfileId("custom");
            Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show();
            finish();
        });

        findViewById(android.R.id.content).setKeepScreenOn(true);
    }

    private interface ValueSink {
        void onValue(float value);
    }

    private void bindSlider(int rowId, int labelRes, final float min, final float max,
                            float current, String unit, final ValueSink sink) {
        View row = panel.findViewById(rowId);
        if (row == null) {
            return;
        }
        TextView label = row.findViewById(R.id.slider_label);
        final TextView valueView = row.findViewById(R.id.slider_value);
        SeekBar seekBar = row.findViewById(R.id.slider);
        label.setText(labelRes);
        seekBar.setMax(1000);
        final String suffix = unit;
        int progress = (int) ((current - min) / (max - min) * 1000f);
        seekBar.setProgress(Math.max(0, Math.min(1000, progress)));
        valueView.setText(String.format(Locale.US, "%.1f%s", current, suffix));

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                float actual = min + (max - min) * value / 1000f;
                valueView.setText(String.format(Locale.US, "%.1f%s", actual, suffix));
                if (fromUser) {
                    sink.onValue(actual);
                    renderer.reloadSettings();
                    scene.markDirty();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
            }
        });
    }

    private void reloadValues() {
        contentRoot().removeAllViews();
        recreate();
    }
}
