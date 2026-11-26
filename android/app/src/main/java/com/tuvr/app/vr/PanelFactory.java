package com.tuvr.app.vr;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

/**
 * Renders the VR panel artwork with Canvas so that text stays crisp inside the
 * headset. Everything is generated at runtime: no large image assets, no heavy
 * 3D models, predictable memory use.
 */
public final class PanelFactory {

    public static final int TILE_W = 512;
    public static final int TILE_H = 256;
    public static final int THUMB_W = 512;
    public static final int THUMB_H = 288;
    public static final int BAR_W = 256;
    public static final int BAR_H = 128;

    private static final int BG_TOP = 0xFF16202F;
    private static final int BG_BOTTOM = 0xE60A0F18;
    private static final int STROKE = 0x40FFFFFF;
    private static final int ACCENT = 0xFF4DE0FF;
    private static final int ACCENT_VIOLET = 0xFF7C5CFF;
    private static final int TEXT_PRIMARY = 0xFFE9F2FF;
    private static final int TEXT_SECONDARY = 0xFF9AA7BD;

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint accentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Typeface medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private final Typeface regular = Typeface.create("sans-serif", Typeface.NORMAL);

    public PanelFactory() {
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(3f);
        strokePaint.setColor(STROKE);
        accentPaint.setColor(ACCENT);
    }

    private Bitmap blank(int w, int h) {
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.TRANSPARENT);
        return bitmap;
    }

    private void glassBackground(Canvas canvas, int w, int h, float radius, boolean highlighted) {
        rect.set(2f, 2f, w - 2f, h - 2f);
        fillPaint.setShader(new LinearGradient(0f, 0f, w * 0.7f, h,
                BG_TOP, BG_BOTTOM, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(rect, radius, radius, fillPaint);
        fillPaint.setShader(null);

        canvas.drawRoundRect(rect, radius, radius, strokePaint);

        // Accent stripe along the top edge.
        accentPaint.setShader(new LinearGradient(0f, 0f, w, 0f,
                highlighted ? 0xFFFFFFFF : ACCENT, ACCENT_VIOLET, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(new RectF(radius * 0.8f, 3f, w - radius * 0.8f, 7f),
                radius * 0.5f, radius * 0.5f, accentPaint);
        accentPaint.setShader(null);
    }

    /** A large tile with an icon glyph, title and subtitle. */
    public Bitmap tile(String glyph, String title, String subtitle, boolean highlighted) {
        Bitmap bitmap = blank(TILE_W, TILE_H);
        Canvas canvas = new Canvas(bitmap);
        glassBackground(canvas, TILE_W, TILE_H, 28f, highlighted);

        textPaint.setTypeface(medium);
        textPaint.setColor(ACCENT);
        textPaint.setTextSize(64f);
        canvas.drawText(glyph, 34f, 92f, textPaint);

        textPaint.setColor(TEXT_PRIMARY);
        textPaint.setTextSize(46f);
        drawEllipsized(canvas, title, 34f, 168f, TILE_W - 68f, textPaint);

        textPaint.setTypeface(regular);
        textPaint.setColor(TEXT_SECONDARY);
        textPaint.setTextSize(28f);
        drawEllipsized(canvas, subtitle == null ? "" : subtitle, 34f, 216f,
                TILE_W - 68f, textPaint);

        if (highlighted) {
            strokePaint.setStrokeWidth(6f);
            strokePaint.setColor(ACCENT);
            rect.set(4f, 4f, TILE_W - 4f, TILE_H - 4f);
            canvas.drawRoundRect(rect, 28f, 28f, strokePaint);
            strokePaint.setStrokeWidth(3f);
            strokePaint.setColor(STROKE);
        }
        return bitmap;
    }

    /** A thumbnail card: video frame, title and metadata. */
    public Bitmap thumbnail(Bitmap frame, String title, String subtitle, boolean highlighted,
                            boolean favorite) {
        Bitmap bitmap = blank(THUMB_W, THUMB_H);
        Canvas canvas = new Canvas(bitmap);
        glassBackground(canvas, THUMB_W, THUMB_H, 24f, highlighted);

        RectF imageRect = new RectF(14f, 14f, THUMB_W - 14f, THUMB_H - 78f);
        Path clip = new Path();
        clip.addRoundRect(imageRect, 16f, 16f, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clip);
        canvas.drawColor(0xFF0A1018);
        if (frame != null && !frame.isRecycled()) {
            canvas.drawBitmap(frame, null, imageRect, fillPaint);
        } else {
            textPaint.setColor(0xFF25313F);
            textPaint.setTextSize(80f);
            textPaint.setTypeface(medium);
            canvas.drawText("▶", imageRect.centerX() - 30f, imageRect.centerY() + 28f, textPaint);
        }
        // bottom fade so the text stays readable
        fillPaint.setShader(new LinearGradient(0f, imageRect.bottom - 70f, 0f, imageRect.bottom,
                0x00000000, 0xCC000000, Shader.TileMode.CLAMP));
        canvas.drawRect(imageRect.left, imageRect.bottom - 70f, imageRect.right,
                imageRect.bottom, fillPaint);
        fillPaint.setShader(null);
        canvas.restore();

        textPaint.setTypeface(medium);
        textPaint.setColor(TEXT_PRIMARY);
        textPaint.setTextSize(30f);
        drawEllipsized(canvas, title == null ? "Video" : title, 22f, THUMB_H - 34f,
                THUMB_W - 44f, textPaint);

        if (subtitle != null && !subtitle.isEmpty()) {
            textPaint.setTypeface(regular);
            textPaint.setColor(TEXT_SECONDARY);
            textPaint.setTextSize(24f);
            textPaint.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(subtitle, THUMB_W - 22f, THUMB_H - 34f, textPaint);
            textPaint.setTextAlign(Paint.Align.LEFT);
        }

        if (favorite) {
            textPaint.setColor(0xFFFFC46B);
            textPaint.setTextSize(30f);
            canvas.drawText("★", 26f, 46f, textPaint);
        }
        return bitmap;
    }

    /** A small control button (play/pause, back, recenter, ...). */
    public Bitmap button(String label, String glyph, boolean active) {
        Bitmap bitmap = blank(BAR_W, BAR_H);
        Canvas canvas = new Canvas(bitmap);
        glassBackground(canvas, BAR_W, BAR_H, 22f, active);

        textPaint.setTypeface(medium);
        textPaint.setTextAlign(Paint.Align.CENTER);
        if (glyph != null && !glyph.isEmpty()) {
            textPaint.setColor(active ? ACCENT : TEXT_PRIMARY);
            textPaint.setTextSize(52f);
            canvas.drawText(glyph, BAR_W / 2f, BAR_H / 2f + (label == null ? 18f : 2f), textPaint);
        }
        if (label != null && !label.isEmpty()) {
            textPaint.setColor(active ? ACCENT : TEXT_SECONDARY);
            textPaint.setTextSize(24f);
            canvas.drawText(label, BAR_W / 2f, BAR_H - 20f, textPaint);
        }
        textPaint.setTextAlign(Paint.Align.LEFT);
        return bitmap;
    }

    /** Text-only panel, used for progress / status / subtitles. */
    public Bitmap textPanel(String text, int width, int height, float textSize,
                            boolean transparentBackground, int color) {
        Bitmap bitmap = blank(width, height);
        Canvas canvas = new Canvas(bitmap);
        if (!transparentBackground) {
            glassBackground(canvas, width, height, 22f, false);
        }
        textPaint.setTypeface(medium);
        textPaint.setColor(color);
        textPaint.setTextSize(textSize);
        textPaint.setTextAlign(Paint.Align.CENTER);
        String[] lines = text == null ? new String[]{""} : text.split("\n");
        float lineHeight = textSize * 1.25f;
        float totalHeight = lineHeight * lines.length;
        float startY = height / 2f - totalHeight / 2f + textSize;
        for (int i = 0; i < lines.length; i++) {
            canvas.drawText(lines[i], width / 2f, startY + i * lineHeight, textPaint);
        }
        textPaint.setTextAlign(Paint.Align.LEFT);
        return bitmap;
    }

    private void drawEllipsized(Canvas canvas, String text, float x, float y, float maxWidth,
                                Paint paint) {
        if (text == null) {
            return;
        }
        String value = text;
        if (paint.measureText(value) > maxWidth) {
            while (value.length() > 1 && paint.measureText(value + "…") > maxWidth) {
                value = value.substring(0, value.length() - 1);
            }
            value = value + "…";
        }
        canvas.drawText(value, x, y, paint);
    }
}
