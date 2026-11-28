package com.fussoo.vr.gl;

import android.graphics.Bitmap;

import com.fussoo.vr.core.Formats;

/**
 * In-headset interface. Two states:
 *
 * <ul>
 *   <li><b>Playing</b>: a slim progress bar, the title, and a row of round buttons under
 *       the screen. The row is fixed, so gaze travel is short and muscle memory works.</li>
 *   <li><b>Browse</b>: a horizontal shelf of posters with captions and a "play" affordance.</li>
 * </ul>
 *
 * <p>Everything is laid out in screen metres derived from the screen width, so the HUD
 * scales with the theatre size the user picked.</p>
 */
public final class Hud {

    public static final int PLAY = 0;
    public static final int BACK = 1;
    public static final int FWD = 2;
    public static final int VOL_DOWN = 3;
    public static final int VOL_UP = 4;
    public static final int QUALITY = 5;
    public static final int RECENTER = 6;
    public static final int EXIT = 7;
    public static final int COUNT = 8;

    public static final class Slot {
        public float x0, y0, x1, y1;
        public int id;

        public boolean hit(float x, float y) {
            return x >= x0 && x <= x1 && y >= y0 && y <= y1;
        }

        public float cx() {
            return (x0 + x1) * 0.5f;
        }

        public float cy() {
            return (y0 + y1) * 0.5f;
        }

        public float size() {
            return x1 - x0;
        }
    }

    public final Slot[] slots = new Slot[COUNT];
    public float barX0, barX1, barY;
    public float screenHalfW, screenHalfH;
    public float cardY0, cardY1;
    public float cardW, cardH;
    public int cards;
    public float cardsX0;

    private final Atlas atlas;

    public Hud(Atlas atlas) {
        this.atlas = atlas;
        for (int i = 0; i < COUNT; i++) {
            slots[i] = new Slot();
            slots[i].id = i;
        }
    }

    public void layout(Stage s) {
        float halfW = s.screenWidth * 0.5f;
        screenHalfW = halfW;
        screenHalfH = halfW / Math.max(0.6f, s.aspect());

        float btn = Math.min(halfW * 0.145f, 0.40f);
        float gap = btn * 0.20f;
        float total = COUNT * btn + (COUNT - 1) * gap;
        if (total > s.screenWidth * 0.94f) {
            float k = (s.screenWidth * 0.94f) / total;
            btn *= k;
            gap *= k;
            total = COUNT * btn + (COUNT - 1) * gap;
        }
        float x = -total * 0.5f;
        float y0 = -screenHalfH - btn * 1.35f;
        for (int i = 0; i < COUNT; i++) {
            Slot sl = slots[i];
            sl.x0 = x;
            sl.x1 = x + btn;
            sl.y0 = y0;
            sl.y1 = y0 + btn;
            x += btn + gap;
        }
        barX0 = -total * 0.5f;
        barX1 = total * 0.5f;
        barY = y0 + btn + btn * 0.42f;

        cardW = s.screenWidth * 0.165f;
        cardH = cardW / 1.78f;
        cards = 5;
        float cg = s.screenWidth * 0.014f;
        float cw = cards * cardW + (cards - 1) * cg;
        cardsX0 = -cw * 0.5f;
        cardY0 = screenHalfH - cardH * 0.10f;
        cardY1 = cardY0 + cardH;
    }

    public int hitButton(float x, float y) {
        for (Slot s : slots) {
            if (s.hit(x, y)) {
                return s.id;
            }
        }
        return -1;
    }

    public boolean hitBar(float x, float y, float halfW) {
        return y > -screenHalfH - 0.02f && y < barY + 0.10f
                && x > barX0 - 0.15f && x < barX1 + 0.15f;
    }

    public int hitCard(float x, float y) {
        if (y < cardY0 || y > cardY1) {
            return -1;
        }
        float w = cardW;
        float gap = w * 0.085f;
        for (int i = 0; i < cards; i++) {
            float x0 = cardsX0 + i * (w + gap);
            if (x >= x0 && x <= x0 + w) {
                return i;
            }
        }
        return -1;
    }

    public float barFraction(float x) {
        return Math.max(0f, Math.min(1f, (x - barX0) / (barX1 - barX0)));
    }

    /**
     * Draws the HUD.
     *
     * @param shelf  poster bitmaps for browse mode, may be null
     * @param names  captions matching {@code shelf}
     */
    public void draw(Layer layer, Stage s, Bitmap[] shelf, String[] names) {
        layout(s);
        float a = s.hudAlpha;
        boolean browsing = !s.hasVideo;

        if (browsing) {
            drawBrowse(layer, s, shelf, names, a);
        } else {
            drawPlayback(layer, s, a);
        }
        drawGaze(layer, s);
    }

    // ------------------------------------------------------------ playback

    private void drawPlayback(Layer layer, Stage s, float a) {
        // Progress: track, buffer, playhead.
        float h = Math.max(0.020f, s.screenWidth * 0.0062f);
        Atlas.Region track = atlas.use("bar.track",
                () -> Atlas.roundRect(64, 10, 0x38FFFFFF, 0, 5f));
        Atlas.Region buffered = atlas.use("bar.buf",
                () -> Atlas.roundRect(64, 10, 0x66FFFFFF, 0, 5f));
        Atlas.Region head = atlas.use("bar.head",
                () -> Atlas.roundRect(64, 10, 0xFFF2F5FA, 0, 5f));
        layer.quad(track, barX0, barY - h / 2, barX1, barY + h / 2, 0.75f * a);
        if (s.durationMs > 0 && s.buffered > 0.01f) {
            float bx = barX0 + (barX1 - barX0) * Math.min(1f, s.buffered);
            layer.quad(buffered, barX0, barY - h / 2, bx, barY + h / 2, 0.8f * a);
            float px = barX0 + (barX1 - barX0)
                    * Math.min(1f, s.positionMs / (float) s.durationMs);
            layer.quad(head, barX0, barY - h / 2, px, barY + h / 2, a);
            Atlas.Region dot = atlas.use("bar.dot",
                    () -> Atlas.circle(28, 0xFFF2F5FA, 0));
            float dr = h * 1.15f;
            layer.quad(dot, px - dr, barY - dr, px + dr, barY + dr, a);
        }

        // Title above the bar.
        if (s.title != null && s.title.length() > 0) {
            String t = truncate(s.title, 54);
            Atlas.Region r = atlas.use("t." + t,
                    () -> Atlas.label(t, 44, 0xFFF5F8FC, 0xCC000000, true));
            float th = s.screenWidth * 0.020f;
            float tw = layer.textWidth(r, th);
            float maxW = s.screenWidth * 0.70f;
            if (tw > maxW) {
                th *= maxW / tw;
            }
            layer.quad(r, -tw / 2, barY + 0.055f, tw / 2, barY + 0.055f + th, 0.95f * a);
        }

        // Time on the left, quality on the right.
        String time = Formats.duration(s.positionMs) + " / " + Formats.duration(s.durationMs);
        Atlas.Region tr = atlas.use("time." + time,
                () -> Atlas.label(time, 34, 0xFFB9C3D2, 0xCC000000, false));
        float th = s.screenWidth * 0.0128f;
        layer.text(tr, barX0, barY + 0.012f, th, 0.9f * a);
        if (s.qualityText != null && s.qualityText.length() > 0) {
            Atlas.Region q = atlas.use("q." + s.qualityText,
                    () -> Atlas.label(s.qualityText, 34, 0xFFB9C3D2, 0xCC000000, false));
            float qw = layer.textWidth(q, th);
            layer.text(q, barX1 - qw, barY + 0.012f, th, 0.9f * a);
        }

        // Buttons.
        int d = Math.max(64, Math.min(220, (int) (slots[PLAY].size() * 900f)));
        for (Slot sl : slots) {
            String glyph = glyph(sl.id, s.playing);
            boolean primary = sl.id == PLAY;
            boolean hot = sl.id == s.hover;
            int fill = primary
                    ? (hot ? 0xFFFF4A3E : 0xFFE8322B)
                    : (hot ? 0xF23A4357 : 0xD9121720);
            int fg = 0xFFF7FAFF;
            Atlas.Region r = atlas.use("b" + sl.id + glyph + (hot ? "h" : ""),
                    () -> Atlas.icon(glyph, d, fill, fg));
            layer.quad(r, sl.x0, sl.y0, sl.x1, sl.y1, a);
        }

        // Dwell ring on the focused button.
        if (s.hover >= 0 && s.dwell > 0.01f) {
            Slot sl = slots[Math.max(0, Math.min(COUNT - 1, s.hover))];
            float rr = sl.size() * 0.78f;
            Atlas.Region ring = atlas.use("dwell" + (int) (s.dwell * 30f),
                    () -> Atlas.arc(96, s.dwell, 0xFF63B8FF));
            layer.quad(ring, sl.cx() - rr, sl.cy() - rr, sl.cx() + rr, sl.cy() + rr, a);
        }

        drawStatus(layer, s, a);
    }

    private void drawStatus(Layer layer, Stage s, float a) {
        String note = s.toast != null && s.toast.length() > 0 ? s.toast : s.status;
        if (note == null || note.length() == 0) {
            return;
        }
        Atlas.Region r = atlas.use("s." + note,
                () -> Atlas.label(note, 36, 0xFFF2F6FC, 0xCC000000, true));
        float th = s.screenWidth * 0.0155f;
        float tw = layer.textWidth(r, th);
        float cy = -screenHalfH * 0.72f;
        layer.quad(r, -tw / 2, cy - th / 2, tw / 2, cy + th / 2, 0.95f * a);
    }

    // -------------------------------------------------------------- browse

    private void drawBrowse(Layer layer, Stage s, Bitmap[] shelf, String[] names, float a) {
        float gap = cardW * 0.085f;
        for (int i = 0; i < cards; i++) {
            float x0 = cardsX0 + i * (cardW + gap);
            float x1 = x0 + cardW;
            Bitmap bmp = shelf != null && i < shelf.length ? shelf[i] : null;
            Atlas.Region r;
            if (bmp != null) {
                String key = "poster" + i + "_" + bmp.getGenerationId();
                r = atlas.use(key, Atlas.shade(bmp));
            } else {
                r = atlas.use("poster.blank",
                        () -> Atlas.roundRect(96, 54, 0x12FFFFFF, 0x33FFFFFF, 12f));
            }
            float lift = (s.hover == 100 + i) ? 0.02f : 0f;
            layer.quad(r, x0, cardY0 + lift, x1, cardY1 + lift, 0.97f * a);
            if (s.hover == 100 + i) {
                float rr = cardW * 0.14f;
                float cx = (x0 + x1) / 2f;
                float cy = (cardY0 + cardY1) / 2f;
                Atlas.Region play = atlas.use("browse.play",
                        () -> Atlas.icon("\u25B6", 128, 0xF2E8322B, 0xFFFFFFFF));
                layer.quad(play, cx - rr, cy - rr, cx + rr, cy + rr, a);
                if (s.dwell > 0.01f) {
                    Atlas.Region ring = atlas.use("dwell" + (int) (s.dwell * 30f),
                            () -> Atlas.arc(96, s.dwell, 0xFF63B8FF));
                    layer.quad(ring, cx - rr * 1.25f, cy - rr * 1.25f,
                            cx + rr * 1.25f, cy + rr * 1.25f, a);
                }
            }
            if (names != null && i < names.length && names[i] != null) {
                String t = truncate(names[i], 30);
                Atlas.Region tr = atlas.use("cap" + i + t,
                        () -> Atlas.label(t, 30, 0xFFE6ECF5, 0xCC000000, true));
                float th = s.screenWidth * 0.0102f;
                float tw = layer.textWidth(tr, th);
                if (tw > cardW) {
                    th *= cardW / tw;
                    tw = cardW;
                }
                layer.quad(tr, x0, cardY1 - th - 0.02f, x0 + tw, cardY1 - 0.02f, 0.95f * a);
            }
        }

        Atlas.Region hint = atlas.use("browse.hint",
                () -> Atlas.label("Gaze at a poster and hold", 34, 0xFFB9C3D2, 0xCC000000, false));
        float hh = s.screenWidth * 0.0125f;
        float hw = layer.textWidth(hint, hh);
        float cy = cardY0 - 0.10f;
        layer.quad(hint, -hw / 2, cy - hh / 2, hw / 2, cy + hh / 2, 0.9f * a);

        drawStatus(layer, s, a);
    }

    // ---------------------------------------------------------------- gaze

    private void drawGaze(Layer layer, Stage s) {
        if (!s.gazeVisible) {
            return;
        }
        boolean hot = s.hover >= 0;
        Atlas.Region r = atlas.use(hot ? "gaze.hot" : "gaze", () -> hot
                ? Atlas.circle(48, 0xFF63B8FF, 0xFFFFFFFF)
                : Atlas.circle(48, 0xB8FFFFFF, 0x88FFFFFF));
        float rr = Math.max(0.014f, s.screenWidth * 0.0048f);
        layer.quad(r, s.gazeX - rr, s.gazeY - rr, s.gazeX + rr, s.gazeY + rr, 0.95f);
    }

    // -------------------------------------------------------------- helpers

    public static String glyph(int id, boolean playing) {
        switch (id) {
            case PLAY:
                return playing ? "II" : "▶";
            case BACK:
                return "<<";
            case FWD:
                return ">>";
            case VOL_DOWN:
                return "-";
            case VOL_UP:
                return "+";
            case QUALITY:
                return "HD";
            case RECENTER:
                return "O";
            default:
                return "X";
        }
    }

    public static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }
}
