package com.tuvr.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.tuvr.app.R;
import com.tuvr.app.core.AppSettings;
import com.tuvr.app.core.Background;
import com.tuvr.app.core.ViewerProfile;
import com.tuvr.app.media.MediaStateDb;
import com.tuvr.app.media.VideoItem;
import com.tuvr.app.media.VideoLibrary;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Phone-mode home screen. Fully usable without a headset. */
public class MainActivity extends AppCompatActivity {

    private MediaAdapter recentAdapter;
    private TextView recentEmpty;
    private TextView viewerLabel;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recentEmpty = findViewById(R.id.txt_recent_empty);
        viewerLabel = findViewById(R.id.txt_viewer_profile);

        recentAdapter = new MediaAdapter(new MediaAdapter.Listener() {
            @Override
            public void onOpen(VideoItem item) {
                startActivity(Intents.player(MainActivity.this, item));
            }

            @Override
            public void onLongPress(VideoItem item) {
                MediaActions.showMenu(MainActivity.this, item, MainActivity.this::loadRecents);
            }
        }, 320);
        RecyclerView list = findViewById(R.id.recent_list);
        list.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        list.setAdapter(recentAdapter);
        list.setNestedScrollingEnabled(false);

        findViewById(R.id.btn_enter_vr).setOnClickListener(v ->
                startActivity(new Intent(this, VrHomeActivity.class)));

        bindTile(R.id.card_cinema, R.drawable.ic_cinema,
                R.string.home_card_cinema, R.string.home_card_cinema_desc,
                () -> startActivity(new Intent(this, CinemaActivity.class)));

        bindTile(R.id.card_gallery, R.drawable.ic_gallery,
                R.string.home_card_gallery, R.string.home_card_gallery_desc,
                () -> startActivity(Intents.gallery(this)));

        bindTile(R.id.card_browser, R.drawable.ic_browser,
                R.string.home_card_browser, R.string.home_card_browser_desc,
                () -> startActivity(Intents.browser(this, null)));

        bindTile(R.id.card_apps, R.drawable.ic_apps,
                R.string.home_card_apps, R.string.home_card_apps_desc,
                () -> startActivity(Intents.apps(this)));

        bindTile(R.id.card_settings, R.drawable.ic_settings,
                R.string.home_card_settings, R.string.home_card_settings_desc,
                () -> startActivity(Intents.settings(this)));

        bindTile(R.id.card_diagnostics, R.drawable.ic_info,
                R.string.home_card_diagnostics, R.string.home_card_diagnostics_desc,
                () -> startActivity(Intents.diagnostics(this)));
    }

    private void bindTile(int includeId, int iconRes, int titleRes, int descRes, Runnable action) {
        View root = findViewById(includeId);
        if (root == null) {
            return;
        }
        ImageView icon = root.findViewById(R.id.tile_icon);
        TextView title = root.findViewById(R.id.tile_title);
        TextView desc = root.findViewById(R.id.tile_desc);
        View card = root.findViewById(R.id.tile_card);
        if (icon != null) {
            icon.setImageResource(iconRes);
        }
        if (title != null) {
            title.setText(titleRes);
        }
        if (desc != null) {
            desc.setText(descRes);
        }
        if (card != null) {
            card.setOnClickListener(v -> action.run());
        } else {
            root.setOnClickListener(v -> action.run());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        AppSettings settings = AppSettings.get(this);
        ViewerProfile profile = settings.viewerProfile();
        viewerLabel.setText(ViewerProfile.displayName(profile.id)
                + " · " + Math.round(profile.fovDeg) + "°");
        loadRecents();
    }

    private void loadRecents() {
        Background.run(() -> {
            List<VideoItem> all = VideoLibrary.all(this);
            Map<String, VideoItem> byUri = new LinkedHashMap<>();
            for (VideoItem item : all) {
                byUri.put(item.uri, item);
            }
            List<VideoItem> recents = new ArrayList<>();
            for (String uri : MediaStateDb.get(this).recentUris(12)) {
                VideoItem item = byUri.get(uri);
                if (item != null) {
                    recents.add(item);
                }
            }
            if (recents.isEmpty()) {
                // Nothing played yet: show the newest few videos instead of an empty row.
                for (int i = 0; i < Math.min(6, all.size()); i++) {
                    recents.add(all.get(i));
                }
            }
            Background.main(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                recentAdapter.setItems(recents);
                recentEmpty.setVisibility(recents.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }
}
