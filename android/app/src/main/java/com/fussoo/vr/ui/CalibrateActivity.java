package com.fussoo.vr.ui;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.fussoo.vr.core.Boot;
import com.fussoo.vr.core.VrPrefs;

/**
 * A stereo test pattern for lining up the box: a crosshair per eye with tick marks, plus
 * the current numbers. Users compare what each eye sees and adjust in Settings until the
 * two crosses merge into one.
 */
public final class CalibrateActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Boot.once(this);
        VrPrefs prefs = new VrPrefs(this);
        setContentView(build(prefs));
    }

    private LinearLayout build(VrPrefs prefs) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackgroundColor(0xFF000000);
        row.addView(eye(prefs, "L", -1), new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        row.addView(eye(prefs, "R", 1), new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        return row;
    }

    private LinearLayout eye(VrPrefs prefs, String side, int dir) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        col.setBackgroundColor(dir < 0 ? 0xFF08090C : 0xFF0B0C10);

        PatternView pattern = new PatternView(this, dir);
        col.addView(pattern, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView read = new TextView(this);
        read.setText(String.format(java.util.Locale.US,
                "EYE %s\nlens %.1f mm · fov %.0f° · barrel %.2f\n"
                        + "screen %.1f m wide at %.1f m",
                side, prefs.lensSeparation() * 1000f, prefs.fov(), prefs.barrel(),
                prefs.screenWidth(), prefs.screenDistance()));
        read.setTextColor(0xFF8FA6C6);
        read.setTextSize(11);
        read.setGravity(Gravity.CENTER);
        read.setPadding(0, Palette.dp(this, 10), 0, Palette.dp(this, 14));
        col.addView(read);
        return col;
    }

    /** Crosshair, corner squares and a ring, drawn with plain canvas calls. */
    private static final class PatternView extends android.view.View {
        private final int dir;
        private final android.graphics.Paint stroke =
                new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);

        PatternView(android.content.Context c, int dir) {
            super(c);
            this.dir = dir;
            stroke.setStyle(android.graphics.Paint.Style.STROKE);
            stroke.setStrokeWidth(2.5f);
            stroke.setColor(0xFFB9D4FF);
        }

        @Override
        protected void onDraw(android.graphics.Canvas g) {
            float w = getWidth();
            float h = getHeight();
            float cx = w / 2f;
            float cy = h / 2f;
            g.drawColor(0xFF05060A);

            // Grid.
            stroke.setColor(0xFF16202E);
            stroke.setStrokeWidth(1f);
            for (int i = 1; i < 8; i++) {
                float x = w * i / 8f;
                float y = h * i / 8f;
                g.drawLine(x, 0, x, h, stroke);
                g.drawLine(0, y, w, y, stroke);
            }

            // Centre cross.
            stroke.setColor(0xFFB9D4FF);
            stroke.setStrokeWidth(2.5f);
            float arm = Math.min(w, h) * 0.16f;
            g.drawLine(cx - arm, cy, cx + arm, cy, stroke);
            g.drawLine(cx, cy - arm, cx, cy + arm, stroke);
            g.drawCircle(cx, cy, arm * 0.42f, stroke);

            // Direction marker: a filled arrow pointing towards the other eye, so the
            // pair visibly points at each other when the separation is right.
            android.graphics.Paint fill = new android.graphics.Paint(
                    android.graphics.Paint.ANTI_ALIAS_FLAG);
            fill.setColor(0xFFFF3B30);
            android.graphics.Path p = new android.graphics.Path();
            float ax = cx + dir * arm * 1.6f;
            float s = arm * 0.22f;
            p.moveTo(ax, cy);
            p.lineTo(ax - dir * s, cy - s);
            p.lineTo(ax - dir * s, cy + s);
            p.close();
            g.drawPath(p, fill);

            // Corner brackets for bezel alignment.
            stroke.setColor(0xFF4E6B8F);
            float m = Math.min(w, h) * 0.08f;
            float len = Math.min(w, h) * 0.10f;
            g.drawLine(m, m, m + len, m, stroke);
            g.drawLine(m, m, m, m + len, stroke);
            g.drawLine(w - m, m, w - m - len, m, stroke);
            g.drawLine(w - m, m, w - m, m + len, stroke);
            g.drawLine(m, h - m, m + len, h - m, stroke);
            g.drawLine(m, h - m, m, h - m - len, stroke);
            g.drawLine(w - m, h - m, w - m - len, h - m, stroke);
            g.drawLine(w - m, h - m, w - m, h - m - len, stroke);
        }
    }
}
