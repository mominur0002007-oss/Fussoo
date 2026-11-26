package com.tuvr.app.vr.scene;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;

import com.tuvr.app.apps.AppEntry;
import com.tuvr.app.apps.AppsRepository;
import com.tuvr.app.core.Background;
import com.tuvr.app.core.Formats;
import com.tuvr.app.media.MediaStateDb;
import com.tuvr.app.media.ThumbLoader;
import com.tuvr.app.media.VideoItem;
import com.tuvr.app.media.VideoLibrary;
import com.tuvr.app.vr.Panel;
import com.tuvr.app.vr.PanelFactory;
import com.tuvr.app.vr.VrHost;
import com.tuvr.app.vr.VrRenderer;
import com.tuvr.app.vr.VrScene;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Paginated grid used both by the VR media gallery and the VR app launcher.
 * Six panels per page, thumbnail loading is asynchronous and cancellable
 * implicitly by page changes.
 */
public class GridScene implements VrScene {

    public static final int MODE_MEDIA = 0;
    public static final int MODE_APPS = 1;

    private final Context context;
    private final VrHost host;
    private final int mode;
    private final String folderFilter;
    private final boolean favoritesOnly;

    private final List<Panel> slots = new ArrayList<>();
    private final List<Panel> chrome = new ArrayList<>();
    private final List<Object> items = new ArrayList<>();

    private PanelFactory factory;
    private Panel titlePanel;
    private Panel pagePanel;
    private int page;
    private int pageCount = 1;
    private boolean loaded;
    private boolean loading;
    private volatile int contentGeneration;

    private static final int COLUMNS = 3;
    private static final int ROWS = 2;
    private static final int PER_PAGE = COLUMNS * ROWS;

    public GridScene(Context context, VrHost host, int mode, String folderFilter,
                     boolean favoritesOnly) {
        this.context = context;
        this.host = host;
        this.mode = mode;
        this.folderFilter = folderFilter;
        this.favoritesOnly = favoritesOnly;
    }

    @Override
    public String title() {
        return mode == MODE_MEDIA ? "Videos" : "Apps";
    }

    @Override
    public void onCreated(VrRenderer renderer) {
        factory = renderer.panelFactory();
        chrome.clear();
        slots.clear();

        titlePanel = new Panel("title");
        titlePanel.interactive = false;
        titlePanel.widthM = 1.5f;
        titlePanel.heightM = 0.3f;
        titlePanel.pitchDeg = 24f;
        titlePanel.distanceM = 3.2f;
        chrome.add(titlePanel);

        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLUMNS; col++) {
                Panel slot = new Panel("slot_" + row + "_" + col);
                slot.widthM = 1.18f;
                slot.heightM = 0.665f;
                slot.yawDeg = -27f + col * 27f;
                slot.pitchDeg = row == 0 ? 7f : -14f;
                slot.distanceM = 3.1f;
                slots.add(slot);
            }
        }

        chrome.add(makeButton("prev", "\u25C0", "Prev", -30f, -30f, () -> changePage(-1)));
        chrome.add(makeButton("next", "\u25B6", "Next", 30f, -30f, () -> changePage(1)));
        chrome.add(makeButton("back", "\u23FB", "Back", 0f, -30f, host::leaveScene));

        pagePanel = new Panel("page");
        pagePanel.interactive = false;
        pagePanel.widthM = 0.5f;
        pagePanel.heightM = 0.2f;
        pagePanel.pitchDeg = -30f;
        pagePanel.distanceM = 2.4f;
        chrome.add(pagePanel);

        updateTitle();
        load();
    }

    private Panel makeButton(String id, String glyph, String label, float yaw, float pitch,
                             Runnable action) {
        Panel panel = new Panel(id);
        panel.widthM = 0.4f;
        panel.heightM = 0.2f;
        panel.yawDeg = yaw;
        panel.pitchDeg = pitch;
        panel.distanceM = 2.4f;
        panel.tag = action;
        panel.setContent(factory.button(label, glyph, false));
        return panel;
    }

    private void updateTitle() {
        if (factory == null) {
            return;
        }
        String subtitle;
        if (mode == MODE_MEDIA) {
            subtitle = folderFilter == null ? "all folders" : folderFilter;
            if (favoritesOnly) {
                subtitle = "favorites";
            }
        } else {
            subtitle = "installed apps";
        }
        titlePanel.setContent(factory.textPanel(title() + "\n" + subtitle, 512, 128, 40f,
                true, 0xFFE9F2FF));
    }

    private void load() {
        if (loading || loaded) {
            return;
        }
        loading = true;
        final int generation = ++contentGeneration;
        Background.run(() -> {
            List<Object> result = new ArrayList<>();
            if (mode == MODE_MEDIA) {
                List<VideoItem> all = VideoLibrary.all(context);
                for (VideoItem item : all) {
                    if (folderFilter != null && !folderFilter.equals(item.folderName)) {
                        continue;
                    }
                    if (favoritesOnly && !MediaStateDb.get(context).isFavorite(item.uri)) {
                        continue;
                    }
                    result.add(item);
                }
            } else {
                for (AppEntry entry : AppsRepository.load(context)) {
                    result.add(entry);
                }
            }
            Background.main(() -> {
                if (generation != contentGeneration) {
                    return;
                }
                items.clear();
                items.addAll(result);
                loaded = true;
                loading = false;
                page = 0;
                refreshPage();
            });
        });
    }

    private void changePage(int delta) {
        int target = page + delta;
        if (target < 0 || target >= pageCount) {
            return;
        }
        page = target;
        refreshPage();
    }

    private void refreshPage() {
        if (factory == null) {
            return;
        }
        pageCount = Math.max(1, (items.size() + PER_PAGE - 1) / PER_PAGE);
        if (page >= pageCount) {
            page = pageCount - 1;
        }
        pagePanel.setContent(factory.textPanel(String.format(Locale.US, "%d / %d",
                page + 1, pageCount), 256, 96, 34f, true, 0xFF9AA7BD));

        for (int i = 0; i < slots.size(); i++) {
            Panel slot = slots.get(i);
            int index = page * PER_PAGE + i;
            if (index >= items.size()) {
                slot.visible = false;
                slot.interactive = false;
                slot.tag = null;
                continue;
            }
            slot.visible = true;
            slot.interactive = true;
            Object item = items.get(index);
            slot.tag = item;
            if (item instanceof VideoItem) {
                bindVideo(slot, (VideoItem) item);
            } else if (item instanceof AppEntry) {
                bindApp(slot, (AppEntry) item);
            }
        }
    }

    private void bindVideo(final Panel slot, final VideoItem item) {
        String meta = item.durationMs > 0 ? Formats.duration(item.durationMs) : "";
        slot.setContent(factory.thumbnail(null, item.name, meta, false, item.favorite));

        final Bitmap cached = ThumbLoader.get().cached(item.uri, 384);
        if (cached != null) {
            slot.setContent(factory.thumbnail(cached, item.name, meta, false, item.favorite));
            return;
        }
        ThumbLoader.get().load(context, item, 384, (uri, bitmap) -> {
            if (bitmap == null || bitmap.isRecycled()) {
                return;
            }
            if (!(slot.tag instanceof VideoItem)
                    || !((VideoItem) slot.tag).uri.equals(uri)) {
                return;
            }
            slot.setContent(factory.thumbnail(bitmap, item.name, meta, false, item.favorite));
        });
    }

    private void bindApp(Panel slot, AppEntry entry) {
        Bitmap bitmap = factory.blankPublic(PanelFactory.THUMB_W, PanelFactory.THUMB_H);
        Canvas canvas = new Canvas(bitmap);
        int backgroundColor = 0xCC101825;
        canvas.drawColor(backgroundColor);
        if (entry.icon != null) {
            int size = 128;
            int left = (PanelFactory.THUMB_W - size) / 2;
            int top = 40;
            entry.icon.setBounds(left, top, left + size, top + size);
            entry.icon.draw(canvas);
        }
        android.graphics.Paint paint = new android.graphics.Paint(
                android.graphics.Paint.ANTI_ALIAS_FLAG);
        paint.setColor(0xFFE9F2FF);
        paint.setTextSize(28f);
        paint.setTextAlign(android.graphics.Paint.Align.CENTER);
        String label = entry.label;
        if (paint.measureText(label) > PanelFactory.THUMB_W - 40) {
            while (label.length() > 1
                    && paint.measureText(label + "\u2026") > PanelFactory.THUMB_W - 40) {
                label = label.substring(0, label.length() - 1);
            }
            label = label + "\u2026";
        }
        canvas.drawText(label, PanelFactory.THUMB_W / 2f, PanelFactory.THUMB_H - 34f, paint);
        paint.setTextSize(20f);
        paint.setColor(0xFF9AA7BD);
        canvas.drawText(entry.vrLabel(), PanelFactory.THUMB_W / 2f,
                PanelFactory.THUMB_H - 12f, paint);
        slot.setContent(bitmap);
    }

    @Override
    public void onSettingsChanged(VrRenderer renderer) {
        if (renderer != null) {
            factory = renderer.panelFactory();
        }
    }

    @Override
    public void onResumed(VrRenderer renderer) {
        loaded = false;
        items.clear();
        load();
    }

    @Override
    public void update(VrRenderer renderer, float dt) {
        if (!loaded && !loading) {
            load();
        }
    }

    @Override
    public void drawEye(VrRenderer renderer, int eye, float[] viewMatrix, float[] projMatrix) {
        // This scene is panels only; the renderer draws them.
    }

    @Override
    public void collectPanels(VrRenderer renderer, List<Panel> out) {
        out.addAll(chrome);
        out.addAll(slots);
    }

    @Override
    public void onPanelSelected(Panel panel, VrRenderer renderer) {
        if (panel.tag instanceof Runnable) {
            ((Runnable) panel.tag).run();
            return;
        }
        if (panel.tag instanceof VideoItem) {
            VideoItem item = (VideoItem) panel.tag;
            com.tuvr.app.core.AppSettings.get(context).setLastVideo(item.uri, 0L);
            host.openVrCinema(item.uri, true);
            return;
        }
        if (panel.tag instanceof AppEntry) {
            AppEntry entry = (AppEntry) panel.tag;
            if (!AppsRepository.launch(context, entry.packageName)) {
                host.showWhisper("Could not launch " + entry.label);
            } else {
                host.showWhisper("Opened " + entry.label);
            }
        }
    }

    @Override
    public void release() {
        contentGeneration++;
        for (Panel panel : slots) {
            panel.release();
        }
        for (Panel panel : chrome) {
            panel.release();
        }
        slots.clear();
        chrome.clear();
        items.clear();
    }
}
