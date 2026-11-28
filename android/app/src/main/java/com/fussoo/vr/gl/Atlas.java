package com.fussoo.vr.gl;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.opengl.GLES20;
import android.opengl.GLUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Texture atlas drawn with Canvas.
 *
 * <p>Every 2D surface in the theatre — panels, buttons, text, posters, the gaze cursor —
 * is painted into one 2048 px texture, so the whole in-world interface costs a single
 * draw call. That matters on a Unisoc T606 where each extra draw call is real millivolts.</p>
 *
 * <p>Bitmaps are built once per key and cached. Frames where nothing changed allocate
 * nothing.</p>
 */
public final class Atlas {

    public static final int SIZE = 2048;
    private static final int PAD = 3;

    /** A placed image: normalised UVs (v measured from the bottom, GL convention). */
    public static final class Region {
        public final float u0, v0, u1, v1;
        public final int w, h;

        Region(float u0, float v0, float u1, float v1, int w, int h) {
            this.u0 = u0;
            this.v0 = v0;
            this.u1 = u1;
            this.v1 = v1;
            this.w = w;
            this.h = h;
        }

        public float aspect() {
            if (w == 1 && h == 1) {
                return 16f / 9f;
            }
            return (float) w / (float) Math.max(1, h);
        }
    }

    /** Lazy bitmap factory; called at most once per key for the life of the process. */
    public interface Art {
        Bitmap make();
    }

    private Bitmap bitmap;
    private Canvas canvas;
    private int tex;
    private boolean dirty;
    private int penX, penY, rowH;

    /** Placement in the current atlas. */
    private final Map<String, Region> placed = new HashMap<>();
    /** Every bitmap ever generated, so a rebuild never re-renders text. */
    private final Map<String, Bitmap> art = new HashMap<>();

    public void init() {
        bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        canvas = new Canvas(bitmap);
        canvas.drawColor(Color.TRANSPARENT);
        int[] ids = new int[1];
        GLES20.glGenTextures(1, ids, 0);
        tex = ids[0];
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
        dirty = false;
        placed.clear();
        art.clear();
        penX = 0;
        penY = 0;
        rowH = 0;
    }

    public int texture() {
        return tex;
    }

    /**
     * Returns the region for {@code key}, invoking {@code factory} only if that key has
     * never produced art. This is the only call site anything drawn per frame should use.
     */
    public Region use(String key, Art factory) {
        Region r = placed.get(key);
        if (r != null) {
            return r;
        }
        Bitmap bmp = art.get(key);
        if (bmp == null) {
            bmp = factory.make();
            if (bmp == null) {
                return null;
            }
            art.put(key, bmp);
        }
        r = place(bmp);
        placed.put(key, r);
        return r;
    }

    private Region place(Bitmap src) {
        Bitmap b = src;
        int limit = SIZE - PAD * 2;
        if (b.getWidth() > limit || b.getHeight() > limit) {
            float k = Math.min(limit / (float) b.getWidth(), limit / (float) b.getHeight());
            b = Bitmap.createScaledBitmap(b, Math.max(1, (int) (b.getWidth() * k)),
                    Math.max(1, (int) (b.getHeight() * k)), true);
        }
        int w = b.getWidth();
        int h = b.getHeight();
        if (penX + w + PAD > SIZE) {
            penX = 0;
            penY += rowH + PAD;
            rowH = 0;
        }
        if (penY + h + PAD > SIZE) {
            repack();
            if (penY + h + PAD > SIZE) {
                return new Region(0f, 0f, 0.01f, 0.01f, w, h);
            }
        }
        canvas.drawBitmap(b, penX + PAD, penY + PAD, null);
        dirty = true;
        float u0 = (penX + PAD) / (float) SIZE;
        float u1 = (penX + PAD + w) / (float) SIZE;
        float v1 = 1f - (penY + PAD) / (float) SIZE;
        float v0 = 1f - (penY + PAD + h) / (float) SIZE;
        penX += w + PAD;
        rowH = Math.max(rowH, h);
        return new Region(u0, v0, u1, v1, w, h);
    }

    /** Repacks everything still referenced. Only happens when the atlas fills up. */
    private void repack() {
        Map<String, Region> live = new HashMap<>(placed);
        canvas.drawColor(Color.TRANSPARENT);
        placed.clear();
        penX = 0;
        penY = 0;
        rowH = 0;
        for (Map.Entry<String, Region> e : live.entrySet()) {
            Bitmap b = art.get(e.getKey());
            if (b != null) {
                placed.put(e.getKey(), place(b));
            }
        }
    }

    /** Uploads pending pixels. GL thread only. */
    /**
     * Uploads a standalone bitmap into a new GL texture. Poster art is 16:9 and rarely
     * reused, so it gets its own texture instead of evicting the UI atlas.
     */
    public static int upload(Bitmap bmp) {
        if (bmp == null || bmp.isRecycled()) {
            return 0;
        }
        int[] t = new int[1];
        GLES20.glGenTextures(1, t, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, t[0]);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE);
        android.opengl.GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0);
        return t[0];
    }

    /** A 0..1 region covering a whole texture, for bitmaps outside the atlas. */
    public static Region wholeRegion() {
        return new Region(0f, 0f, 1f, 1f, 1, 1);
    }

    public void commit() {
        if (!dirty) {
            return;
        }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex);
        GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, bitmap);
        dirty = false;
    }

    public void release() {
        if (tex != 0) {
            GLES20.glDeleteTextures(1, new int[]{tex}, 0);
            tex = 0;
        }
        placed.clear();
        art.clear();
    }

    // =====================================================================
    // Art factories. Everything the scene needs is built from these.
    // =====================================================================

    /** Frosted panel: translucent fill, hairline edge, generous radius. */
    public static Bitmap panel(int w, int h, int fill, int stroke, float radius) {
        Bitmap b = Bitmap.createBitmap(Math.max(2, w), Math.max(2, h),
                Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        RectF r = new RectF(1f, 1f, w - 1f, h - 1f);
        p.setColor(fill);
        c.drawRoundRect(r, radius, radius, p);
        if (stroke != 0) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2f);
            p.setColor(stroke);
            c.drawRoundRect(r, radius, radius, p);
        }
        return b;
    }

    /**
     * A panel with a top highlight so it reads as glass under a light, not a flat box.
     */
    public static Bitmap glass(int w, int h, float radius) {
        Bitmap b = Bitmap.createBitmap(Math.max(2, w), Math.max(2, h),
                Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        RectF r = new RectF(1f, 1f, w - 1f, h - 1f);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new android.graphics.LinearGradient(0f, 0f, 0f, h,
                new int[]{0x33FFFFFF, 0x14FFFFFF, 0x0A000000},
                new float[]{0f, 0.45f, 1f},
                android.graphics.Shader.TileMode.CLAMP));
        c.drawRoundRect(r, radius, radius, p);
        p.setShader(null);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2f);
        p.setColor(0x2EFFFFFF);
        c.drawRoundRect(r, radius, radius, p);
        return b;
    }

    public static Bitmap circle(int d, int fill, int stroke) {
        Bitmap b = Bitmap.createBitmap(Math.max(2, d), Math.max(2, d),
                Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(fill);
        c.drawCircle(d / 2f, d / 2f, d / 2f - 1f, p);
        if (stroke != 0) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2f);
            p.setColor(stroke);
            c.drawCircle(d / 2f, d / 2f, d / 2f - 3f, p);
        }
        return b;
    }

    /** Round button with a glyph, the standard control in this UI. */
    public static Bitmap button(String glyph, int d, int fill, int fg) {
        Bitmap b = circle(d, fill, 0x40FFFFFF);
        Canvas c = new Canvas(b);
        Paint t = textPaint(d * (glyph.length() > 2 ? 0.30f : 0.42f), fg, true);
        t.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fm = t.getFontMetrics();
        c.drawText(glyph, d / 2f, d / 2f - (fm.ascent + fm.descent) / 2f, t);
        return b;
    }

    /**
     * Text with an optional dark outline. The outline is what keeps labels readable when
     * they are drawn over a bright poster, which happens constantly in a browse screen.
     */
    public static Bitmap label(String text, int px, int fg, int outline, boolean bold) {
        Paint t = textPaint(px, fg, bold);
        float w = t.measureText(text);
        Paint.FontMetrics fm = t.getFontMetrics();
        int iw = Math.max(2, (int) Math.ceil(w) + 12);
        int ih = Math.max(2, (int) Math.ceil(fm.descent - fm.ascent) + 12);
        Bitmap b = Bitmap.createBitmap(iw, ih, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        float x = 6f;
        float y = 6f - fm.ascent;
        if (outline != 0) {
            t.setStyle(Paint.Style.STROKE);
            t.setStrokeWidth(4.5f);
            t.setColor(outline);
            c.drawText(text, x, y, t);
        }
        t.setStyle(Paint.Style.FILL);
        t.setColor(fg);
        c.drawText(text, x, y, t);
        return b;
    }

    /** Greedy word wrap for descriptions and long titles. */
    public static Bitmap paragraph(String text, int px, int fg, int maxWidthPx,
                                   int maxLines, boolean bold) {
        Paint t = textPaint(px, fg, bold);
        String[] words = text == null ? new String[0] : text.split("\\s+");
        StringBuilder line = new StringBuilder();
        java.util.List<String> lines = new java.util.ArrayList<>();
        for (String w : words) {
            String probe = line.length() == 0 ? w : line + " " + w;
            if (t.measureText(probe) > maxWidthPx && line.length() > 0) {
                lines.add(line.toString());
                line = new StringBuilder(w);
                if (lines.size() == maxLines) {
                    break;
                }
            } else {
                line = new StringBuilder(probe);
            }
        }
        if (lines.size() < maxLines && line.length() > 0) {
            lines.add(line.toString());
        }
        Paint.FontMetrics fm = t.getFontMetrics();
        int lineH = (int) Math.ceil(fm.descent - fm.ascent);
        int iw = Math.max(2, maxWidthPx + 12);
        int ih = Math.max(2, lineH * Math.max(1, lines.size()) + 12);
        Bitmap b = Bitmap.createBitmap(iw, ih, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        for (int i = 0; i < lines.size(); i++) {
            c.drawText(lines.get(i), 6f, 6f - fm.ascent + i * lineH, t);
        }
        return b;
    }

    /** Circular progress ring, used for both dwell and loaders. */
    public static Bitmap arc(int d, float fraction, int color, float thickness) {
        Bitmap b = Bitmap.createBitmap(Math.max(2, d), Math.max(2, d),
                Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(d * thickness);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(color);
        RectF r = new RectF(d * 0.11f, d * 0.11f, d * 0.89f, d * 0.89f);
        c.drawArc(r, -90f, 360f * Math.max(0.001f, Math.min(1f, fraction)), false, p);
        return b;
    }

    /**
     * Poster with a dark gradient at the bottom. Without this, white captions on a bright
     * thumbnail are unreadable — the single most common legibility bug in VR UIs.
     */
    public static Bitmap poster(Bitmap src) {
        Bitmap b = Bitmap.createBitmap(src.getWidth(), src.getHeight(),
                Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawBitmap(src, 0f, 0f, null);
        Paint p = new Paint();
        p.setShader(new android.graphics.LinearGradient(0f, 0f, 0f, src.getHeight(),
                new int[]{0x00000000, 0x22000000, 0xE6000000},
                new float[]{0f, 0.45f, 1f},
                android.graphics.Shader.TileMode.CLAMP));
        c.drawRect(0f, 0f, src.getWidth(), src.getHeight(), p);
        return b;
    }

    /** Duration pill for a poster corner. */
    public static Bitmap badge(String text, int px) {
        Paint t = textPaint(px, 0xFFFFFFFF, true);
        int tw = (int) Math.ceil(t.measureText(text));
        Paint.FontMetrics fm = t.getFontMetrics();
        int th = (int) Math.ceil(fm.descent - fm.ascent);
        int pad = px / 2;
        Bitmap b = Bitmap.createBitmap(tw + pad * 2, th + pad,
                Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(0xCC000000);
        c.drawRoundRect(new RectF(0f, 0f, b.getWidth(), b.getHeight()),
                b.getHeight() / 2f, b.getHeight() / 2f, bg);
        c.drawText(text, pad, pad / 2f - fm.ascent, t);
        return b;
    }

    private static Paint textPaint(float px, int color, boolean bold) {
        Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
        t.setTextSize(px);
        t.setColor(color);
        t.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        return t;
    }
}
