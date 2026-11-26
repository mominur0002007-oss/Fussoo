package com.tuvr.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.tuvr.app.R;
import com.tuvr.app.apps.AppEntry;
import com.tuvr.app.apps.AppsRepository;
import com.tuvr.app.core.Background;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Phone-mode launcher for installed applications. */
public class AppsActivity extends AppCompatActivity {

    private static final int FILTER_ALL = 0;
    private static final int FILTER_FAVORITES = 1;
    private static final int FILTER_RECENT = 2;

    private final List<AppEntry> all = new ArrayList<>();
    private final List<AppEntry> shown = new ArrayList<>();
    private AppAdapter adapter;
    private int filter = FILTER_ALL;
    private String query = "";

    private TextView chipAll;
    private TextView chipFavorites;
    private TextView chipRecent;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_apps);

        adapter = new AppAdapter();
        RecyclerView list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_enter_vr).setOnClickListener(v -> {
            Intent intent = new Intent(this, VrGridActivity.class);
            intent.putExtra(Intents.EXTRA_MODE, Intents.GRID_MODE_APPS);
            startActivity(intent);
        });

        chipAll = findViewById(R.id.chip_all);
        chipFavorites = findViewById(R.id.chip_favorites);
        chipRecent = findViewById(R.id.chip_recent);
        chipAll.setOnClickListener(v -> setFilter(FILTER_ALL));
        chipFavorites.setOnClickListener(v -> setFilter(FILTER_FAVORITES));
        chipRecent.setOnClickListener(v -> setFilter(FILTER_RECENT));

        EditText search = findViewById(R.id.edit_search);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                query = s.toString();
                applyFilter();
            }
        });

        load();
    }

    private void load() {
        Background.run(() -> {
            List<AppEntry> apps = AppsRepository.load(this);
            Background.main(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                all.clear();
                all.addAll(apps);
                applyFilter();
            });
        });
    }

    private void setFilter(int newFilter) {
        filter = newFilter;
        applyFilter();
    }

    private void applyFilter() {
        updateChips();
        shown.clear();
        String needle = query.trim().toLowerCase(Locale.US);
        if (filter == FILTER_RECENT) {
            for (String pkg : AppsRepository.recents(this)) {
                for (AppEntry entry : all) {
                    if (entry.packageName.equals(pkg)) {
                        shown.add(entry);
                        break;
                    }
                }
            }
        } else {
            for (AppEntry entry : all) {
                if (filter == FILTER_FAVORITES && !AppsRepository.isFavorite(this, entry.packageName)) {
                    continue;
                }
                if (!needle.isEmpty() && !entry.label.toLowerCase(Locale.US).contains(needle)) {
                    continue;
                }
                shown.add(entry);
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void updateChips() {
        setChipState(chipAll, filter == FILTER_ALL);
        setChipState(chipFavorites, filter == FILTER_FAVORITES);
        setChipState(chipRecent, filter == FILTER_RECENT);
    }

    private void setChipState(TextView chip, boolean selected) {
        chip.setBackgroundResource(selected ? R.drawable.bg_chip_selected : R.drawable.bg_chip);
        chip.setTextColor(ContextCompat.getColor(this,
                selected ? R.color.text_primary : R.color.text_secondary));
    }

    private void launch(AppEntry entry) {
        if (!AppsRepository.launch(this, entry.packageName)) {
            Toast.makeText(this, R.string.apps_launch_failed, Toast.LENGTH_SHORT).show();
        }
        applyFilter();
    }

    private void showAppMenu(AppEntry entry) {
        boolean favorite = AppsRepository.isFavorite(this, entry.packageName);
        String[] actions = new String[]{
                getString(R.string.action_open),
                favorite ? getString(R.string.action_unfavorite) : getString(R.string.action_favorite),
                getString(R.string.action_cancel)
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(entry.label)
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        launch(entry);
                    } else if (which == 1) {
                        AppsRepository.toggleFavorite(this, entry.packageName);
                        applyFilter();
                    }
                })
                .show();
    }

    private class AppAdapter extends RecyclerView.Adapter<AppVH> {
        @NonNull
        @Override
        public AppVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_app, parent, false);
            return new AppVH(view);
        }

        @Override
        public void onBindViewHolder(@NonNull AppVH holder, int position) {
            AppEntry entry = shown.get(position);
            holder.name.setText(entry.label);
            String subtitle = entry.packageName + "  ·  " + entry.vrLabel();
            holder.pkg.setText(subtitle);
            if (entry.icon != null) {
                holder.icon.setImageDrawable(entry.icon);
            }
            boolean favorite = AppsRepository.isFavorite(AppsActivity.this, entry.packageName);
            holder.favorite.setImageResource(favorite ? R.drawable.ic_star_filled : R.drawable.ic_star);
            holder.itemView.setOnClickListener(v -> launch(entry));
            holder.itemView.setOnLongClickListener(v -> {
                showAppMenu(entry);
                return true;
            });
            holder.favorite.setOnClickListener(v -> {
                AppsRepository.toggleFavorite(AppsActivity.this, entry.packageName);
                applyFilter();
            });
        }

        @Override
        public int getItemCount() {
            return shown.size();
        }
    }

    private static class AppVH extends RecyclerView.ViewHolder {
        final ImageView icon;
        final ImageView favorite;
        final TextView name;
        final TextView pkg;

        AppVH(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.img_icon);
            favorite = itemView.findViewById(R.id.img_favorite);
            name = itemView.findViewById(R.id.txt_name);
            pkg = itemView.findViewById(R.id.txt_package);
        }
    }
}
