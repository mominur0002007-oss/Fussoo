package com.fussoo.vr.gl;

import com.fussoo.vr.core.Formats;
import com.fussoo.vr.core.WorldModel;

import android.opengl.GLES20;

import java.util.ArrayList;
import java.util.List;

/**
 * Spatial layout for every screen in the world.
 *
 * <p>This is the class that makes the app feel like a VR product instead of a video player
 * with menus glued on. Nothing is a 2D overlay glued to your face:</p>
 *
 * <ul>
 *   <li><b>HOME</b> — a curved wall of posters standing in the room, a big featured card
 *       in the middle, and a search panel you aim at.</li>
 *   <li><b>PLAYER</b> — the curved screen, with a console of controls floating just below
 *       it, angled up towards your face.</li>
 *   <li><b>SETTINGS</b> — physical sliders and switches, laid out as a board to the side.</li>
 * </ul>
 *
 * <p>Layout is in metres and anchored to the same distance as the theatre, so resizing the
 * cinema moves the whole interface coherently and nothing lands behind your head.</p>
 */
public final class Scene {

    public static final int SURFACE_NONE = -1;
    public static final int SURFACE_MAIN = 0;
    public static final int SURFACE_CONSOLE = 1;
    public static final int SURFACE_RAIL = 2;

    /** Result of a gaze intersection. */
    public static final class Hit {
        public boolean valid;
        public int surface = SURFACE_NONE;
        public String key = "";
        public float x, y;           // metres in the surface's own space
        public float u, v;           // 0..1 across the surface
    }

    // --------------------------------------------------------- geometry

    public float mainW() {
        return 5.6f;
    }

    /** Main board keeps a 16:9 shape whatever the video's aspect is, so the UI never jumps. */
    public float mainH() {
        return mainW() / 1.78f;
    }

    public float mainY() {
        return 1.62f;
    }

    public float mainZ() {
        return -4.15f;
    }

    /** Sag of the main board at its edges, metres. Gives the wall its curve. */
    public float mainSag() {
        return 0.55f;
    }

    public float consoleW() {
        return 4.4f;
    }

    public float consoleH() {
        return 0.60f;
    }

    public float consoleY() {
        return 0.80f;
    }

    public float consoleZ() {
        return -3.25f;
    }

    /** Console is tilted up towards the viewer. */
    public float consoleTilt() {
        return 26f;
    }

    public float railW() {
        return 3.6f;
    }

    public float railH() {
        return 0.28f;
    }

    public float railY() {
        return 0.30f;
    }

    public float railZ() {
        return -2.60f;
    }

    /**
     * The neutral 0..1 region used when drawing a texture that is not in the atlas.
     * Layer pushes uv + alpha per vertex, so no atlas bookkeeping is needed.
     */
    public static Atlas.Region region() {
        return Atlas.wholeRegion();
    }

    // ============================================================ layout

    /** One clickable rectangle on a surface, in that surface's metres. */
    private static final class Box {
        final String key;
        final int surface;
        final float x0, y0, x1, y1;

        Box(String key, int surface, float x0, float y0, float x1, float y1) {
            this.key = key;
            this.surface = surface;
            this.x0 = x0;
            this.y0 = y0;
            this.x1 = x1;
            this.y1 = y1;
        }

        boolean inside(float x, float y) {
            return x >= x0 && x <= x1 && y >= y0 && y <= y1;
        }
    }

    private final List<Box> boxes = new ArrayList<>();

    /**
     * Recomputes hit boxes for the current screen and returns the one under the gaze.
     *
     * @param gx head-ray intersection in the MAIN surface's local metres
     * @param gy same, vertical
     */
    public Hit hitTest(WorldModel w, float gx, float gy) {
        buildBoxes(w);
        Hit hit = new Hit();
        for (Box b : boxes) {
            if (b.surface != SURFACE_MAIN) {
                continue;
            }
            if (b.inside(gx, gy)) {
                hit.valid = true;
                hit.surface = b.surface;
                hit.key = b.key;
                hit.x = gx;
                hit.y = gy;
                hit.u = (gx + mainW() * 0.5f) / mainW();
                hit.v = (gy + mainH() * 0.5f) / mainH();
                return hit;
            }
        }
        return hit;
    }

    public List<String> keys() {
        List<String> out = new ArrayList<>();
        for (Box b : boxes) {
            out.add(b.key);
        }
        return out;
    }

    private void buildBoxes(WorldModel w) {
        boxes.clear();
        float hw = mainW() * 0.5f;
        float hh = mainH() * 0.5f;

        switch (w.screen) {
            case HOME: {
                // Featured hero card, centred.
                boxes.add(new Box("hero", SURFACE_MAIN, -1.55f, -0.30f, 1.55f, 1.55f));
                // Poster row beneath.
                int n = Math.min(5, w.tiles.size());
                float cw = 0.92f;
                float gap = 0.10f;
                float total = n * cw + (n - 1) * gap;
                float x = -total * 0.5f;
                for (int i = 0; i < n; i++) {
                    boxes.add(new Box("tile:" + i, SURFACE_MAIN,
                            x, -1.62f, x + cw, -0.52f));
                    x += cw + gap;
                }
                // Search bar top-left, plus a row of quick filters.
                boxes.add(new Box("search", SURFACE_MAIN, -hw + 0.12f, hh - 0.44f,
                        -hw + 2.05f, hh - 0.14f));
                boxes.add(new Box("filter:trending", SURFACE_MAIN, -hw + 2.20f, hh - 0.44f,
                        -hw + 3.05f, hh - 0.14f));
                boxes.add(new Box("filter:music", SURFACE_MAIN, -hw + 3.15f, hh - 0.44f,
                        -hw + 3.85f, hh - 0.14f));
                boxes.add(new Box("filter:4k", SURFACE_MAIN, -hw + 3.95f, hh - 0.44f,
                        -hw + 4.55f, hh - 0.14f));
                boxes.add(new Box("settings", SURFACE_MAIN, hw - 0.60f, hh - 0.44f,
                        hw - 0.12f, hh - 0.14f));
                break;
            }
            case BROWSE: {
                boxes.add(new Box("back", SURFACE_MAIN, -hw + 0.12f, hh - 0.44f,
                        -hw + 0.92f, hh - 0.14f));
                boxes.add(new Box("search", SURFACE_MAIN, -hw + 1.05f, hh - 0.44f,
                        hw - 1.10f, hh - 0.14f));
                boxes.add(new Box("nextpage", SURFACE_MAIN, hw - 1.00f, hh - 0.44f,
                        hw - 0.12f, hh - 0.14f));
                int cols = 5;
                int rows = 2;
                float cw = 0.92f;
                float ch = 0.60f;
                float gx2 = 0.20f;
                float gy2 = 0.16f;
                float totalW = cols * cw + (cols - 1) * gx2;
                float totalH = rows * ch + (rows - 1) * gy2;
                float x0 = -totalW * 0.5f;
                float y0 = totalH * 0.5f - 0.28f;
                for (int r = 0; r < rows; r++) {
                    for (int c = 0; c < cols; c++) {
                        int idx = r * cols + c;
                        float bx = x0 + c * (cw + gx2);
                        float by = y0 - r * (ch + gy2) - ch;
                        boxes.add(new Box("tile:" + idx, SURFACE_MAIN, bx, by,
                                bx + cw, by + ch));
                    }
                }
                break;
            }
            case PLAYER: {
                // The video itself is on the console-less wall; controls live on the
                // console surface and are handled separately by consoleHitTest().
                boxes.add(new Box("video", SURFACE_MAIN, -hw, -hh, hw, hh));
                break;
            }
            case SETTINGS: {
                boxes.add(new Box("back", SURFACE_MAIN, -hw + 0.12f, hh - 0.44f,
                        -hw + 0.92f, hh - 0.14f));
                int rows = Math.min(9, w.settings.size());
                float rh = 0.30f;
                float y = 1.05f;
                for (int i = 0; i < rows; i++) {
                    boxes.add(new Box("set:" + i, SURFACE_MAIN, -2.15f, y - rh,
                            2.15f, y));
                    y -= rh + 0.055f;
                }
                boxes.add(new Box("reset", SURFACE_MAIN, -0.9f, y - 0.30f, 0.9f, y));
                break;
            }
            default:
                break;
        }
    }

    // ------------------------------------------------------------- console

    /** Hit test for the player console, in console-local metres. */
    public Hit consoleHitTest(WorldModel w, float x, float y) {
        Hit hit = new Hit();
        if (w.screen != WorldModel.Screen.PLAYER) {
            return hit;
        }
        float hw = consoleW() * 0.5f;
        float hh = consoleH() * 0.5f;

        // Seek bar across the top of the console.
        if (y > hh - 0.10f && y < hh + 0.02f && x > -hw + 0.10f && x < hw - 0.10f) {
            hit.valid = true;
            hit.surface = SURFACE_CONSOLE;
            hit.key = "seek";
            hit.x = x;
            hit.y = y;
            hit.u = (x + hw) / consoleW();
            return hit;
        }
        // Eight buttons in a row.
        String[] keys = {"play", "back10", "fwd10", "vol-", "vol+", "quality",
                "recenter", "exit"};
        float bw = 0.42f;
        float gap = 0.09f;
        float total = keys.length * bw + (keys.length - 1) * gap;
        float bx = -total * 0.5f;
        for (String k : keys) {
            if (x >= bx && x <= bx + bw && y >= -0.26f && y <= 0.16f) {
                hit.valid = true;
                hit.surface = SURFACE_CONSOLE;
                hit.key = k;
                hit.x = x;
                hit.y = y;
                hit.u = (bx + bw * 0.5f + hw) / consoleW();
                return hit;
            }
            bx += bw + gap;
        }
        return hit;
    }

    /** Hit test for the persistent bottom rail. */
    public Hit railHitTest(WorldModel w, float x, float y) {
        Hit hit = new Hit();
        float hw = railW() * 0.5f;
        String[] keys = {"nav:home", "nav:browse", "nav:search", "nav:settings",
                "nav:recenter"};
        float bw = 0.62f;
        float gap = 0.08f;
        float total = keys.length * bw + (keys.length - 1) * gap;
        float bx = -total * 0.5f;
        for (String k : keys) {
            if (x >= bx && x <= bx + bw && y >= -railH() * 0.5f && y <= railH() * 0.5f) {
                hit.valid = true;
                hit.surface = SURFACE_RAIL;
                hit.key = k;
                hit.x = x;
                hit.y = y;
                hit.u = (bx + bw * 0.5f + hw) / railW();
                return hit;
            }
            bx += bw + gap;
        }
        return hit;
    }

    // ============================================================= drawing

    /**
     * Paints the main board.
     *
     * @param at    batched layer to add quads to
     * @param atlas art cache
     * @param w     world snapshot being drawn
     */
    public void drawMain(Layer at, Atlas atlas, WorldModel w, float[] mvp) {
        buildBoxes(w);
        float hw = mainW() * 0.5f;
        float hh = mainH() * 0.5f;

        switch (w.screen) {
            case HOME:
                drawHome(at, atlas, w, hw, hh);
                break;
            case BROWSE:
                drawBrowse(at, atlas, w, hw, hh);
                break;
            case PLAYER:
                // The video is drawn by the renderer; only the chrome goes here.
                drawPlayerChrome(at, atlas, w, hw, hh);
                break;
            case SETTINGS:
                drawSettings(at, atlas, w, hw, hh);
                break;
            default:
                break;
        }
    }

    // ---------------------------------------------------------------- home

    private void drawHome(Layer at, Atlas atlas, WorldModel w, float hw, float hh) {
        // Header: wordmark left, search field, filters, settings gear.
        Atlas.Region wordmark = atlas.use("word", () ->
                Atlas.label("FUSSOO", 128, 0xFFF5F7FA, 0, true));
        at.text(wordmark, -hw + 0.12f, hh - 0.29f, 0.20f, 1f);

        Atlas.Region searchBg = atlas.use("searchbg", () ->
                Atlas.glass(560, 96, 44f));
        at.quad(searchBg, -hw + 0.12f, hh - 0.44f, -hw + 2.05f, hh - 0.14f, 0.95f);
        String q = w.query == null || w.query.isEmpty() ? "Search YouTube…" : w.query;
        Atlas.Region qr = atlas.use("q:" + q, () ->
                Atlas.label(q, 46, 0xFFB9C3D2, 0, false));
        at.text(qr, -hw + 0.24f, hh - 0.29f, 0.115f, 1f);

        String[] filters = {"Trending", "Music", "4K"};
        float fx = -hw + 2.20f;
        for (String f : filters) {
            float fw = f.equals("Trending") ? 0.85f : (f.equals("Music") ? 0.70f : 0.60f);
            Atlas.Region chip = atlas.use("chip:" + f, () ->
                    Atlas.panel(240, 76, 0x1AFFFFFF, 0x33FFFFFF, 34f));
            at.quad(chip, fx, hh - 0.44f, fx + fw, hh - 0.14f, 0.95f);
            Atlas.Region fl = atlas.use("chipl:" + f, () ->
                    Atlas.label(f, 42, 0xFFE9EEF6, 0, true));
            at.textCentred(fl, fx + fw * 0.5f, hh - 0.29f, 0.090f, 1f);
            fx += fw + 0.10f;
        }

        Atlas.Region gear = atlas.use("gear", () ->
                Atlas.button("\u2699", 128, 0x33FFFFFF, 0xFFF5F7FA));
        at.quad(gear, hw - 0.60f, hh - 0.44f, hw - 0.12f, hh - 0.14f, 1f);

        // Featured hero.
        if (w.hero != null) {
            drawHero(at, atlas, w, -1.55f, -0.30f, 1.55f, 1.55f, mvp);
        }

        // Poster row.
        int n = Math.min(5, w.tiles.size());
        float cw = 0.92f;
        float gap = 0.10f;
        float total = n * cw + (n - 1) * gap;
        float x = -total * 0.5f;
        for (int i = 0; i < n; i++) {
            WorldModel.Tile t = w.tiles.get(i);
            drawPoster(at, atlas, t, x, -1.62f, x + cw, -0.52f, "tile:" + i, w, mvp);
            x += cw + gap;
        }

        if (w.loading) {
            drawSpinner(at, atlas, w, 0f, 0.55f);
        }
        if (w.errorLine != null && !w.errorLine.isEmpty()) {
            Atlas.Region err = atlas.use("err:" + w.errorLine, () ->
                    Atlas.paragraph(w.errorLine, 40, 0xFFFF8A80, 1500, 2, false));
            at.textCentred(err, 0f, -0.35f, 0.085f, 1f);
        }
    }

    private void drawHero(Layer at, Atlas atlas, WorldModel w, float x0, float y0,
                          float x1, float y1, float[] mvp) {
        float wdt = x1 - x0;
        float hgt = y1 - y0;
        float cx = (x0 + x1) * 0.5f;
        float cy = (y0 + y1) * 0.5f;

        Atlas.Region frame = atlas.use("heroframe", () ->
                Atlas.panel(900, 700, 0x0EFFFFFF, 0x33FFFFFF, 26f));
        at.quad(frame, x0, y0, x1, y1, 1f);

        boolean hot = "hero".equals(w.hoverKey);
        if (w.hero.poster != null) {
            int tex = Atlas.upload(w.hero.poster);
            if (tex != 0) {
                at.begin();
                at.fit(Atlas.wholeRegion(), cx, cy + 0.10f, wdt - 0.06f, hgt - 0.30f, 1f);
                at.draw(mvp, tex);
                GLES20.glDeleteTextures(1, new int[]{tex}, 0);
                at.begin();
            }
        } else {
            Atlas.Region p = atlas.use("heroblank", () ->
                    Atlas.panel(900, 500, 0x14FFFFFF, 0, 20f));
            at.quad(p, x0 + 0.03f, y0 + 0.14f, x1 - 0.03f, y1 - 0.20f, 1f);
        }

        Atlas.Region title = atlas.use("herot:" + w.hero.title, () ->
                Atlas.paragraph(w.hero.title, 54, 0xFFFFFFFF, 1200, 2, true));
        at.textCentred(title, cx, y0 + 0.34f, 0.105f, 1f);

        Atlas.Region sub = atlas.use("heros:" + w.hero.subtitle, () ->
                Atlas.label(w.hero.subtitle, 40, 0xFFB9C3D2, 0, false));
        at.textCentred(sub, cx, y0 + 0.16f, 0.072f, 1f);

        // Primary play chip, only lit when this is the gaze target.
        Atlas.Region play = atlas.use(hot ? "heroph" : "heropn", () ->
                Atlas.button("\u25B6", 160, hot ? 0xFFE8322B : 0xCC20262F, 0xFFFFFFFF));
        float pr = 0.16f;
        at.quad(play, cx - pr, y1 - 0.44f - pr, cx + pr, y1 - 0.44f + pr, 1f);
    }

    // -------------------------------------------------------------- browse

    private void drawBrowse(Layer at, Atlas atlas, WorldModel w, float hw, float hh) {
        Atlas.Region back = atlas.use("backbtn", () ->
                Atlas.button("\u2039", 128, 0x33FFFFFF, 0xFFF5F7FA));
        at.quad(back, -hw + 0.12f, hh - 0.44f, -hw + 0.92f, hh - 0.14f, 1f);

        Atlas.Region searchBg = atlas.use("searchbg2", () ->
                Atlas.glass(1000, 96, 44f));
        at.quad(searchBg, -hw + 1.05f, hh - 0.44f, hw - 1.10f, hh - 0.14f, 0.95f);
        String q = w.query == null || w.query.isEmpty() ? "Search YouTube…" : w.query;
        Atlas.Region qr = atlas.use("q2:" + q, () ->
                Atlas.label(q, 46, 0xFFB9C3D2, 0, false));
        at.text(qr, -hw + 1.20f, hh - 0.29f, 0.115f, 1f);

        Atlas.Region title = atlas.use("shelf:" + w.shelfTitle, () ->
                Atlas.label(w.shelfTitle, 44, 0xFF9AA3B2, 0, true));
        at.text(title, -hw + 0.14f, hh - 0.60f, 0.085f, 1f);

        Atlas.Region next = atlas.use("next", () ->
                Atlas.button("\u203A", 128, 0x33FFFFFF, 0xFFF5F7FA));
        at.quad(next, hw - 1.00f, hh - 0.44f, hw - 0.12f, hh - 0.14f, 1f);

        int cols = 5;
        int rows = 2;
        float cw = 0.92f;
        float ch = 0.60f;
        float gx2 = 0.20f;
        float gy2 = 0.16f;
        float totalW = cols * cw + (cols - 1) * gx2;
        float totalH = rows * ch + (rows - 1) * gy2;
        float x0 = -totalW * 0.5f;
        float y0 = totalH * 0.5f - 0.28f;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int idx = r * cols + c;
                if (idx >= w.tiles.size()) {
                    continue;
                }
                float bx = x0 + c * (cw + gx2);
                float by = y0 - r * (ch + gy2) - ch;
                drawPoster(at, atlas, w.tiles.get(idx), bx, by, bx + cw, by + ch,
                        "tile:" + idx, w, mvp);
            }
        }

        if (w.loading) {
            drawSpinner(at, atlas, w, 0f, 0f);
        }
    }

    /** Poster card: image, gradient, title, meta, duration badge. */
    private void drawPoster(Layer at, Atlas atlas, WorldModel.Tile t, float x0, float y0,
                            float x1, float y1, String key, WorldModel w, float[] mvp) {
        boolean hot = key.equals(w.hoverKey);
        float pad = hot ? 0.018f : 0f;
        float ax0 = x0 - pad;
        float ay0 = y0 - pad;
        float ax1 = x1 + pad;
        float ay1 = y1 + pad;
        float cx = (ax0 + ax1) * 0.5f;
        float cy = (ay0 + ay1) * 0.5f;

        Atlas.Region card = atlas.use(hot ? "cardhot" : "card", () ->
                Atlas.panel(420, 280, hot ? 0x26FFFFFF : 0x12FFFFFF,
                        hot ? 0x66A8C8FF : 0x26FFFFFF, 18f));
        at.quad(card, ax0, ay0, ax1, ay1, 1f);

        if (t.poster != null) {
            // Posters are unique full-size bitmaps, so they are sampled from their own
            // texture: the atlas stays reserved for UI art and never gets evicted.
            int tex = Atlas.upload(t.poster);
            if (tex != 0) {
                at.begin();
                at.fit(Atlas.wholeRegion(), cx, cy + 0.05f, (ax1 - ax0) - 0.03f,
                        (ay1 - ay0) - 0.10f, 1f);
                at.draw(mvp, tex);
                GLES20.glDeleteTextures(1, new int[]{tex}, 0);
                at.begin();
            }
        } else {
            Atlas.Region img = atlas.use("pblank", () ->
                    Atlas.panel(420, 236, 0x18FFFFFF, 0, 14f));
            at.quad(img, ax0 + 0.02f, ay0 + 0.08f, ax1 - 0.02f, ay1 - 0.04f, 1f);
        }

        Atlas.Region title = atlas.use("pt:" + t.key + t.title, () ->
                Atlas.label(shorten(t.title, 26), 38, 0xFFFFFFFF, 0xCC000000, true));
        float th = 0.046f;
        float tw = at.textWidth(title, th);
        if (tw > (ax1 - ax0) * 0.94f) {
            th *= ((ax1 - ax0) * 0.94f) / tw;
        }
        at.textCentred(title, cx, ay0 + 0.055f, th, 1f);

        if (t.badge != null && !t.badge.isEmpty()) {
            Atlas.Region b = atlas.use("b:" + t.badge, () ->
                    Atlas.badge(t.badge, 30));
            float bw = 0.115f * b.aspect();
            at.quad(b, ax1 - bw - 0.025f, ay1 - 0.072f, ax1 - 0.025f, ay1 - 0.028f, 1f);
        }

        if (hot && w.dwell > 0.01f) {
            float r = Math.max(ax1 - ax0, ay1 - ay0) * 0.25f;
            Atlas.Region ring = atlas.use("dw" + (int) (w.dwell * 24f), () ->
                    Atlas.arc(96, w.dwell, 0xFF63B8FF, 0.09f));
            at.quad(ring, cx - r, cy - r + 0.05f, cx + r, cy + r + 0.05f, 1f);
        }
    }

    // -------------------------------------------------------------- player

    private void drawPlayerChrome(Layer at, Atlas atlas, WorldModel w, float hw, float hh) {
        if (w.buffering) {
            drawSpinner(at, atlas, w, 0f, 0f);
        }
        if (w.hudAlpha <= 0.01f) {
            return;
        }
        // Title card, only while the HUD is up, so films are never permanently obscured.
        if (w.videoTitle != null && !w.videoTitle.isEmpty()) {
            Atlas.Region t = atlas.use("vt:" + w.videoTitle, () ->
                    Atlas.label(shorten(w.videoTitle, 46), 54, 0xFFFFFFFF, 0xDD000000, true));
            at.text(t, -hw + 0.10f, hh - 0.14f, 0.085f, w.hudAlpha);
        }
    }

    // ------------------------------------------------------------ settings

    private void drawSettings(Layer at, Atlas atlas, WorldModel w, float hw, float hh) {
        Atlas.Region back = atlas.use("backbtn", () ->
                Atlas.button("\u2039", 128, 0x33FFFFFF, 0xFFF5F7FA));
        at.quad(back, -hw + 0.12f, hh - 0.44f, -hw + 0.92f, hh - 0.14f, 1f);

        Atlas.Region title = atlas.use("settitle", () ->
                Atlas.label("Theatre settings", 64, 0xFFF5F7FA, 0, true));
        at.text(title, -hw + 1.10f, hh - 0.29f, 0.115f, 1f);

        int rows = Math.min(9, w.settings.size());
        float rh = 0.30f;
        float y = 1.05f;
        for (int i = 0; i < rows; i++) {
            WorldModel.Setting s = w.settings.get(i);
            boolean hot = ("set:" + i).equals(w.hoverKey);
            Atlas.Region row = atlas.use(hot ? "srowhot" : "srow", () ->
                    Atlas.panel(860, 100, hot ? 0x22FFFFFF : 0x0FFFFFFF,
                            hot ? 0x55A8C8FF : 0x1FFFFFFF, 16f));
            at.quad(row, -2.15f, y - rh, 2.15f, y, 1f);

            Atlas.Region lbl = atlas.use("sl:" + s.key, () ->
                    Atlas.label(s.label, 44, 0xFFE8EDF5, 0, false));
            at.text(lbl, -2.03f, y - rh * 0.5f, 0.082f, 1f);

            if (s.toggle) {
                Atlas.Region tog = atlas.use("tg:" + s.key + s.on, () -> {
                    android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(
                            220, 110, android.graphics.Bitmap.Config.ARGB_8888);
                    android.graphics.Canvas c = new android.graphics.Canvas(b);
                    android.graphics.Paint p =
                            new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                    p.setColor(s.on ? 0xFF3BA55D : 0x33FFFFFF);
                    c.drawRoundRect(new android.graphics.RectF(4, 4, 216, 106),
                            52, 52, p);
                    p.setColor(0xFFFFFFFF);
                    c.drawCircle(s.on ? 162 : 58, 55, 42, p);
                    return b;
                });
                at.quad(tog, 1.55f, y - rh * 0.5f - 0.055f, 2.05f,
                        y - rh * 0.5f + 0.055f, 1f);
            } else {
                Atlas.Region val = atlas.use("sv:" + s.key + s.value, () ->
                        Atlas.label(s.value, 42, 0xFF8FB7FF, 0, true));
                float vw = at.textWidth(val, 0.078f);
                at.text(val, 2.03f - vw, y - rh * 0.5f, 0.078f, 1f);
                // Stepper chevrons.
                Atlas.Region minus = atlas.use("sminus", () ->
                        Atlas.label("\u2212", 44, 0xFF9AA3B2, 0, true));
                at.text(minus, 1.30f, y - rh * 0.5f, 0.075f, 0.9f);
                Atlas.Region plus = atlas.use("splus", () ->
                        Atlas.label("+", 44, 0xFF9AA3B2, 0, true));
                at.text(plus, 1.78f, y - rh * 0.5f, 0.075f, 0.9f);
            }
            y -= rh + 0.055f;
        }

        Atlas.Region reset = atlas.use("resetbtn", () ->
                Atlas.panel(600, 110, 0x22FF3B30, 0x66FF6B60, 22f));
        at.quad(reset, -0.9f, y - 0.30f, 0.9f, y, 1f);
        Atlas.Region rl = atlas.use("resetlbl", () ->
                Atlas.label("Reset optics", 46, 0xFFFFDAD6, 0, true));
        at.textCentred(rl, 0f, y - 0.15f, 0.085f, 1f);
    }

    // -------------------------------------------------------------- shared

    private void drawSpinner(Layer at, Atlas atlas, WorldModel w, float cx, float cy) {
        int step = (int) ((w.clock * 1.6f) % 1f * 24f);
        Atlas.Region r = atlas.use("spin" + step, () ->
                Atlas.arc(96, 0.30f, 0xFF63B8FF, 0.10f));
        float size = 0.16f;
        // Rotate the arc by re-picking the cached frame; cheap and stops the spinner
        // looking frozen on a device that has no vsync-driven animation here.
        float ang = (w.clock * 220f) % 360f;
        at.quadRotated(r, cx, cy, size, ang);
    }

    private void drawGaze(Layer at, Atlas atlas, WorldModel w) {
        if (!w.gazeVisible) {
            return;
        }
        boolean hot = w.hoverKey != null && !w.hoverKey.isEmpty();
        Atlas.Region dot = atlas.use(hot ? "gazehot" : "gaze", () ->
                hot ? Atlas.circle(64, 0xFF63B8FF, 0xFFFFFFFF)
                        : Atlas.circle(64, 0xA8FFFFFF, 0x88FFFFFF));
        at.quad(dot, w.gazeX - 0.04f, w.gazeY - 0.04f,
                w.gazeX + 0.04f, w.gazeY + 0.04f, 0.95f);
    }

    // ------------------------------------------------------------- console

    public void drawConsole(Layer at, Atlas atlas, WorldModel w) {
        if (w.screen != WorldModel.Screen.PLAYER) {
            return;
        }
        float hw = consoleW() * 0.5f;
        float hh = consoleH() * 0.5f;
        float a = w.hudAlpha;
        if (a <= 0.01f) {
            return;
        }

        Atlas.Region bg = atlas.use("consolebg", () ->
                Atlas.glass(1100, 300, 40f));
        at.quad(bg, -hw, -hh, hw, hh, 0.92f * a);

        // Seek bar.
        float by = hh - 0.055f;
        Atlas.Region track = atlas.use("trk", () ->
                Atlas.panel(1100, 26, 0x3DFFFFFF, 0, 13f));
        at.quad(track, -hw + 0.10f, by - 0.022f, hw - 0.10f, by + 0.022f, 0.8f * a);
        if (w.durationMs > 0) {
            float p = Math.min(1f, w.positionMs / (float) w.durationMs);
            float bx = -hw + 0.10f + (consoleW() - 0.20f) * p;
            Atlas.Region fill = atlas.use("fill", () ->
                    Atlas.panel(1100, 26, 0xFFE8322B, 0, 13f));
            at.quad(fill, -hw + 0.10f, by - 0.022f, bx, by + 0.022f, a);
            Atlas.Region knob = atlas.use("knob", () -> Atlas.circle(48, 0xFFFFFFFF, 0));
            at.quad(knob, bx - 0.030f, by - 0.030f, bx + 0.030f, by + 0.030f, a);

            Atlas.Region time = atlas.use("tc:" + w.positionMs / 1000, () ->
                    Atlas.label(Formats.duration(w.positionMs) + "  /  "
                            + Formats.duration(w.durationMs), 34, 0xFFB9C3D2, 0, false));
            at.text(time, -hw + 0.10f, by + 0.075f, 0.055f, 0.95f * a);
        }

        // Buttons.
        String[] keys = {"play", "back10", "fwd10", "vol-", "vol+", "quality",
                "recenter", "exit"};
        String[] glyphs = {w.playing ? "\u2759\u2759" : "\u25B6", "\u00AB", "\u00BB",
                "\u2212", "+", "HD", "\u27F3", "\u2715"};
        float bw = 0.42f;
        float gap = 0.09f;
        float total = keys.length * bw + (keys.length - 1) * gap;
        float bx = -total * 0.5f;
        for (int i = 0; i < keys.length; i++) {
            boolean hot = keys[i].equals(w.hoverKey);
            boolean primary = i == 0;
            int fill = primary ? (hot ? 0xFFFF4A3E : 0xFFE8322B)
                    : (hot ? 0xF23A4357 : 0xD9121720);
            Atlas.Region btn = atlas.use("cb:" + i + (hot ? "h" : ""), () ->
                    Atlas.button(glyphs[i], 160, fill, 0xFFFFFFFF));
            at.quad(btn, bx, -0.235f, bx + bw, 0.125f, a);
            if (hot && w.dwell > 0.01f) {
                float r = bw * 0.72f;
                float cx = bx + bw * 0.5f;
                float cy = -0.055f;
                Atlas.Region ring = atlas.use("cd" + (int) (w.dwell * 24f), () ->
                        Atlas.arc(96, w.dwell, 0xFF63B8FF, 0.09f));
                at.quad(ring, cx - r, cy - r, cx + r, cy + r, a);
            }
            bx += bw + gap;
        }
    }

    // ----------------------------------------------------------------- rail

    public void drawRail(Layer at, Atlas atlas, WorldModel w) {
        float hw = railW() * 0.5f;
        float hh = railH() * 0.5f;
        Atlas.Region bg = atlas.use("railbg", () ->
                Atlas.glass(760, 120, 30f));
        at.quad(bg, -hw, -hh, hw, hh, 0.80f);

        String[] keys = {"nav:home", "nav:browse", "nav:search", "nav:settings",
                "nav:recenter"};
        String[] labels = {"Home", "Browse", "Search", "Settings", "\u27F3"};
        float bw = 0.62f;
        float gap = 0.08f;
        float total = keys.length * bw + (keys.length - 1) * gap;
        float bx = -total * 0.5f;
        for (int i = 0; i < keys.length; i++) {
            boolean hot = keys[i].equals(w.hoverKey);
            Atlas.Region lbl = atlas.use("rl:" + i + (hot ? "h" : ""), () ->
                    Atlas.label(labels[i], 40, hot ? 0xFF63B8FF : 0xFFC8D2E0, 0, hot));
            at.textCentred(lbl, bx + bw * 0.5f, 0f, 0.055f, 0.95f);
            bx += bw + gap;
        }
    }

    private static String shorten(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max - 1) + "\u2026" : s;
    }
}
