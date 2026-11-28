package com.fussoo.vr.ui;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

/** Shared colours and small styling helpers so the phone UI stays consistent. */
public final class Palette {

    public static final int BG = 0xFF05060A;
    public static final int CARD = 0xFF0E1119;
    public static final int CARD_HI = 0xFF161B26;
    public static final int TEXT = 0xFFF5F7FA;
    public static final int MUTED = 0xFF9AA3B2;
    public static final int ACCENT = 0xFFFF3B30;
    public static final int BLUE = 0xFF8FB7FF;

    private Palette() {
    }

    public static int dp(Context c, float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics());
    }

    public static GradientDrawable round(int fill, int stroke, int radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(radiusPx);
        if (stroke != 0) {
            d.setStroke(Math.max(1, radiusPx / 40), stroke);
        }
        return d;
    }

    public static void pad(View v, int l, int t, int r, int b) {
        v.setPadding(l, t, r, b);
    }

    public static void title(TextView v) {
        v.setTextColor(TEXT);
        v.setLetterSpacing(-0.01f);
    }

    public static void muted(TextView v) {
        v.setTextColor(MUTED);
    }
}
