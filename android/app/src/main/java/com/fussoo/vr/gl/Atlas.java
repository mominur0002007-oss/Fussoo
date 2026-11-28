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
 * Canvas-drawn texture atlas.
 *
 * <p>One 2048px texture holds every 2D element — buttons, labels, thumbnails, the gaze
 * cursor — so the HUD is a single draw call. Everything is cached by key, so a steady
 * frame does no allocation at all.</p>
 */
public final class Atlas {

    public static final int SIZE = 2048;
    public static final int PAD = 4;

    /** UV rect. v0 is the bottom edge (GL convention). */
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
            return (float) w / (float) Math.max(1, h);
        }
    }

    private Bitmap bitmap;
    private Canvas canvas;
    private int tex;
    private boolean dirty;
    private int penX, penY, rowH;

    private final Map<String, Region> regions = new HashMap<>();
    private final Map<String, Bitmap> sources = new HashMap<>();
    /** Every art bitmap ever generated, so a repack never re-renders text. */
    private final Map<String, Bitmap> made = new HashMap<>();

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
        reset();
    }

    public int texture() {
        return tex;
    }

    private void reset() {
        regions.clear();
        sources.clear();
        made.clear();
        penX = 0;
        penY = 0;
        rowH = 0;
    }

    /** Builds a bitmap on demand. Called at most once per key per process. */
    public interface Art {
        Bitmap make();
    }

    /**
     * Returns the region for {@code key}, building the bitmap through {@code art} only when
     * that key has never been seen. This is the call site for anything drawn every frame:
     * the bitmap is cached, so a steady frame allocates nothing.
     */
    public Region use(String key, Art art) {
        Region r = regions.get(key);
        if (r != null) {
            return r;
        }
        Bitmap bmp = made.get(key);
        if (bmp == null) {
            bmp = art.make();
            if (bmp == null) {
                return null;
            }
            made.put(key, bmp);
        }
        r = pack(bmp);
        regions.put(key, r);
        sources.put(key, bmp);
        return r;
    }

    /** Convenience for a bitmap that is already in hand (thumbnails). */
    public Region use(String key, Bitmap src) {
        if (src == null) {
            return null;
        }
        made.put(key, src);
        return use(key, () -> made.get(key));
    }

    private Region pack(Bitmap src) {
        Bitmap b = src;
        int maxSide = SIZE - PAD * 2;
        if (b.getWidth() > maxSide || b.getHeight() > maxSide) {
            float k = Math.min(maxSide / (float) b.getWidth(), maxSide / (float) b.getHeight());
            b = Bitmap.createScaledBitmap(b, Math.max(1, (int) (b.getWidth() * k)),
                    Math.max(1, (int) (b.getHeight() * k)), true);
        }
        int w = b.getWidth();
        int h = b.getHeight();
        if (penX + w + PAD * 2 > SIZE) {
            penX = 0;
            penY += rowH + PAD * 2;
            rowH = 0;
        }
        if (penY + h + PAD * 2 > SIZE) {
            // Full. Repack from the live set once, then try again.
            repack();
            if (penY + h + PAD * 2 > SIZE) {
                penX = 0;
                penY = 0;
                rowH = 0;
                canvas.drawColor(Color.TRANSPARENT);
                regions.clear();
                sources.clear();
            }
            rereserve();
        }
        canvas.drawBitmap(b, penX + PAD, penY + PAD, null);
        dirty = true;
        float u0 = (penX + PAD) / (float) SIZE;
        float u1 = (penX + PAD + w) / (float) SIZE;
        float v1 = 1f - (penY + PAD) / (float) SIZE;
        float v0 = 1f - (penY + PAD + h) / (float) SIZE;
        penX += w + PAD * 2;
        rowH = Math.max(rowH, h);
        return new Region(u0, v0, u1, v1, w, h);
    }

    private void rereserve() {
        penX = 0;
        penY += rowH + PAD * 2;
        rowH = 0;
    }

    private void repack() {
        Map<String, Bitmap> keep = new HashMap<>(sources);
        canvas.drawColor(Color.TRANSPARENT);
        regions.clear();
        sources.clear();
        penX = 0;
        penY = 0;
        rowH = 0;
        for (Map.Entry<String, Bitmap> e : keep.entrySet()) {
            Region r = pack(e.getValue());
            regions.put(e.getKey(), r);
            sources.put(e.getKey(), e.getValue());
        }
    }

    /** Uploads pending pixels. GL thread only. */
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
        reset();
    }

    // ------------------------------------------------------------- factories

    public static Bitmap roundRect(int w, int h, int fill, int stroke, float radius) {
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

    public static Bitmap circle(int d, int fill, int stroke) {
        Bitmap b = Bitmap.createBitmap(d, d, Bitmap.Config.ARGB_8888);
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

    /** Round icon button with a text glyph. */
    public static Bitmap icon(String glyph, int d, int fill, int fg) {
        Bitmap b = circle(d, fill, 0x33FFFFFF);
        Canvas c = new Canvas(b);
        Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
        t.setColor(fg);
        t.setTextAlign(Paint.Align.CENTER);
        t.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        float size = d * (glyph.length() > 2 ? 0.30f : 0.44f);
        t.setTextSize(size);
        Paint.FontMetrics fm = t.getFontMetrics();
        c.drawText(glyph, d / 2f, d / 2f - (fm.ascent + fm.descent) / 2f, t);
        return b;
    }

    /** Text with a cheap outline, tight bounds. */
    public static Bitmap label(String text, int px, int fg, int outline, boolean bold) {
        Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
        t.setTextSize(px);
        t.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif",
                bold ? Typeface.BOLD : Typeface.NORMAL));
        float w = t.measureText(text);
        Paint.FontMetrics fm = t.getFontMetrics();
        int iw = Math.max(2, (int) Math.ceil(w) + 10);
        int ih = Math.max(2, (int) Math.ceil(fm.descent - fm.ascent) + 10);
        Bitmap b = Bitmap.createBitmap(iw, ih, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        float x = 5f;
        float y = 5f - fm.ascent;
        if (outline != 0) {
            t.setStyle(Paint.Style.STROKE);
            t.setStrokeWidth(4f);
            t.setColor(outline);
            c.drawText(text, x, y, t);
        }
        t.setStyle(Paint.Style.FILL);
        t.setColor(fg);
        c.drawText(text, x, y, t);
        return b;
    }

    /** Arc used for the dwell timer. */
    public static Bitmap arc(int d, float fraction, int color) {
        Bitmap b = Bitmap.createBitmap(d, d, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(d * 0.08f);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(color);
        RectF r = new RectF(d * 0.10f, d * 0.10f, d * 0.90f, d * 0.90f);
        c.drawArc(r, -90f, 360f * Math.max(0.001f, Math.min(1f, fraction)), false, p);
        return b;
    }

    /** Darkens a thumbnail for legibility of text drawn over it. */
    public static Bitmap shade(Bitmap src) {
        Bitmap b = Bitmap.createBitmap(src.getWidth(), src.getHeight(),
                Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        c.drawBitmap(src, 0f, 0f, null);
        Paint p = new Paint();
        android.graphics.LinearGradient g = new android.graphics.LinearGradient(
                0f, 0f, 0f, src.getHeight(),
                new int[]{0x00000000, 0x00000000, 0xCC000000},
                new float[]{0f, 0.45f, 1f}, android.graphics.Shader.TileMode.CLAMP);
        p.setShader(g);
        c.drawRect(0f, 0f, src.getWidth(), src.getHeight(), p);
        return b;
    }
}
