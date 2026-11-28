package com.fussoo.vr.ui;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import com.fussoo.vr.core.Boot;
import com.fussoo.vr.core.VrPrefs;
import com.fussoo.vr.gl.Env;

/**
 * Every knob that matters, with a live numeric read-out. Deliberately not a PreferenceScreen:
 * sliders that show real metres/degrees are far easier to tune in a headset.
 */
public final class SettingsActivity extends Activity {

    private VrPrefs prefs;
    private LinearLayout column;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Boot.once(this);
        prefs = new VrPrefs(this);
        setContentView(build());
    }

    private View build() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Palette.BG);
        column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        int p = Palette.dp(this, 20);
        column.setPadding(p, p, p, Palette.dp(this, 48));
        scroll.addView(column, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("Theatre settings");
        title.setTextSize(24);
        title.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        title.setTextColor(Palette.TEXT);
        column.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Tuned for a phone VR box on a TECNO Spark 10C.");
        sub.setTextColor(Palette.MUTED);
        sub.setTextSize(13);
        sub.setPadding(0, Palette.dp(this, 4), 0, Palette.dp(this, 22));
        column.addView(sub);

        section("Screen");
        slider("Screen width", " m", 2.5f, 12f, prefs.screenWidth(), 0.1f,
                v -> prefs.setScreenWidth(v));
        slider("Distance", " m", 2.0f, 10f, prefs.screenDistance(), 0.1f,
                v -> prefs.setScreenDistance(v));
        slider("Eye height", " m", 0.6f, 3.2f, prefs.screenHeight(), 0.02f,
                v -> prefs.setScreenHeight(v));
        slider("Curvature", "", 0f, 1f, prefs.curve(), 0.01f,
                v -> prefs.setCurve(v));

        section("Lens and optics");
        slider("Lens separation", " mm", 50f, 80f, prefs.lensSeparation() * 1000f, 0.5f,
                v -> prefs.setLensSeparation(v / 1000f));
        slider("Field of view", "°", 70f, 120f, prefs.fov(), 1f,
                v -> prefs.setFov(v));
        slider("Barrel correction", "", 0f, 0.45f, prefs.barrel(), 0.005f,
                v -> prefs.setBarrel(v));
        toggle("Fisheye correction", prefs.fisheye(),
                on -> prefs.setFisheye(on));
        TextView calibrate = button("Open calibration pattern");
        calibrate.setOnClickListener(v ->
                startActivity(new android.content.Intent(this, CalibrateActivity.class)));
        column.addView(calibrate);

        section("Look and performance");
        slider("Brightness", "", 0.45f, 1.6f, prefs.brighten(), 0.02f,
                v -> prefs.setBrighten(v));
        segmented("Performance", new String[]{"Battery", "Balanced", "Cinematic"},
                prefs.quality(), i -> prefs.setQuality(i));
        segmented("Venue", new String[]{Env.IMAX.title, Env.NOIR.title,
                Env.DEEP_SPACE.title, Env.COZY.title}, prefs.env(), i -> prefs.setEnv(i));

        section("Comfort and controls");
        toggle("Gaze + dwell selection", prefs.gaze(), on -> prefs.setGaze(on));
        slider("Dwell time", " ms", 600f, 3500f, prefs.dwellMs(), 50f,
                v -> prefs.setDwellMs((int) v));
        toggle("Tap to pause", prefs.tapToPause(), on -> prefs.setTapToPause(on));
        toggle("Haptics", prefs.haptics(), on -> prefs.setHaptics(on));
        toggle("Keep screen awake", prefs.keepAwake(), on -> prefs.setKeepAwake(on));
        slider("Seek step", " s", 5f, 60f, prefs.seekStepMs() / 1000f, 5f,
                v -> prefs.setSeekStepMs((int) (v * 1000f)));

        section("Video");
        segmented("Max quality", new String[]{"480p", "720p", "1080p"},
                prefs.maxHeight() == 480 ? 0 : prefs.maxHeight() == 1080 ? 2 : 1,
                i -> prefs.setMaxHeight(i == 0 ? 480 : i == 2 ? 1080 : 720));

        TextView reset = button("Reset optics to defaults");
        reset.setOnClickListener(v -> {
            prefs.resetOptics();
            recreate();
        });
        column.addView(reset);

        TextView about = new TextView(this);
        about.setText("\nFussoo VR 1.0 · Stereo OpenGL renderer + Media3 + NewPipeExtractor.\n"
                + "YouTube extraction is for personal use; content stays with its owners.");
        about.setTextColor(0xFF6C7688);
        about.setTextSize(11);
        column.addView(about);

        return scroll;
    }

    // ------------------------------------------------------------- widgets

    private void section(String text) {
        TextView t = new TextView(this);
        t.setText(text.toUpperCase());
        t.setTextSize(11);
        t.setLetterSpacing(0.2f);
        t.setTextColor(Palette.ACCENT);
        t.setPadding(0, Palette.dp(this, 22), 0, Palette.dp(this, 8));
        column.addView(t);
    }

    private interface FloatSink {
        void put(float v);
    }

    private void slider(String name, String unit, float min, float max, float value,
                        float step, final FloatSink sink) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        wrap.setPadding(0, Palette.dp(this, 6), 0, Palette.dp(this, 6));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        TextView label = new TextView(this);
        label.setText(name);
        label.setTextColor(Palette.TEXT);
        label.setTextSize(15);
        head.addView(label, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final TextView read = new TextView(this);
        read.setTextColor(Palette.BLUE);
        read.setTextSize(14);
        read.setTypeface(Typeface.MONOSPACE);
        head.addView(read);
        wrap.addView(head);

        final int steps = Math.max(1, Math.round((max - min) / step));
        SeekBar bar = new SeekBar(this);
        bar.setMax(steps);
        bar.setProgress(Math.round((value - min) / step));
        final String u = unit;
        final float mn = min;
        final float st = step;
        final Runnable update = new Runnable() {
            @Override
            public void run() {
                float v = mn + bar.getProgress() * st;
                read.setText(String.format(java.util.Locale.US,
                        unitFmt(u), v));
            }
        };
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                update.run();
                if (fromUser) {
                    sink.put(mn + progress * st);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        update.run();
        wrap.addView(bar);
        column.addView(wrap);
    }

    private String unitFmt(String unit) {
        if (" mm".equals(unit) || " ms".equals(unit) || " s".equals(unit)
                || "°".equals(unit)) {
            return "%.0f" + unit;
        }
        return "%.2f" + unit;
    }

    private interface BoolSink {
        void put(boolean v);
    }

    private void toggle(String name, boolean value, final BoolSink sink) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Palette.dp(this, 8), 0, Palette.dp(this, 8));
        TextView label = new TextView(this);
        label.setText(name);
        label.setTextColor(Palette.TEXT);
        label.setTextSize(15);
        row.addView(label, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Switch sw = new Switch(this);
        sw.setChecked(value);
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                sink.put(checked);
            }
        });
        row.addView(sw);
        column.addView(row);
    }

    private interface IntSink {
        void put(int i);
    }

    private void segmented(String name, String[] options, int selected,
                          final IntSink sink) {
        TextView label = new TextView(this);
        label.setText(name);
        label.setTextColor(Palette.TEXT);
        label.setTextSize(15);
        label.setPadding(0, Palette.dp(this, 10), 0, Palette.dp(this, 6));
        column.addView(label);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < options.length; i++) {
            final int index = i;
            TextView chip = new TextView(this);
            chip.setText(options[i]);
            chip.setTextSize(13);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(Palette.dp(this, 12), Palette.dp(this, 9),
                    Palette.dp(this, 12), Palette.dp(this, 9));
            boolean on = i == selected;
            chip.setTextColor(on ? 0xFF05060A : Palette.TEXT);
            chip.setBackground(Palette.round(on ? Palette.BLUE : 0x14FFFFFF,
                    on ? 0 : 0x26FFFFFF, Palette.dp(this, 16)));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.rightMargin = Palette.dp(this, 6);
            chip.setLayoutParams(lp);
            chip.setOnClickListener(v -> {
                sink.put(index);
                recreate();
            });
            row.addView(chip);
        }
        column.addView(row);
    }

    private TextView button(String text) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(Palette.TEXT);
        b.setGravity(Gravity.CENTER);
        b.setBackground(Palette.round(0x18FFFFFF, 0x2EFFFFFF, Palette.dp(this, 18)));
        b.setPadding(Palette.dp(this, 16), Palette.dp(this, 14),
                Palette.dp(this, 16), Palette.dp(this, 14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Palette.dp(this, 12);
        b.setLayoutParams(lp);
        return b;
    }
}
