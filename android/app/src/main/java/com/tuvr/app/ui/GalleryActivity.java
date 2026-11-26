package com.tuvr.app.ui;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.tuvr.app.R;
import com.tuvr.app.core.Background;
import com.tuvr.app.core.Formats;
import com.tuvr.app.media.MediaStateDb;
import com.tuvr.app.media.VideoItem;
import com.tuvr.app.media.VideoLibrary;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Phone-mode media gallery: thumbnails, search, sorting, folders, favourites. */
public class GalleryActivity extends AppCompatActivity implements MediaAdapter.Listener {

    private static final int FILTER_ALL = 0;
    private static final int FILTER_FAVORITES = 1;
    private static final int FILTER_RECENT = 2;
    private static final int FILTER_FOLDERS = 3;

    private MediaAdapter adapter;
    private FolderAdapter folderAdapter;
    private RecyclerView list;
    private View emptyView;
    private TextView emptyText;
    private TextView subtitle;
    private TextView title;
    private EditText search;
    private Button emptyAction;
    private TextView chipAll;
    private TextView chipFavorites;
    private TextView chipRecent;
    private TextView chipFolders;

    private final List<VideoItem> allItems = new ArrayList<>();
    private final Map<String, Integer> folderCounts = new LinkedHashMap<>();
    private int filter = FILTER_ALL;
    private String folder = null;
    private int sort = VideoLibrary.SORT_DATE;
    private boolean ascending = false;
    private String query = "";
    private boolean showingFolders;

    private ActivityResultLauncher<String> permissionLauncher;
    private ActivityResultLauncher<String[]> openDocumentLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gallery);

        list = findViewById(R.id.list);
        emptyView = findViewById(R.id.empty_view);
        emptyText = findViewById(R.id.txt_empty);
        emptyAction = findViewById(R.id.btn_empty_action);
        subtitle = findViewById(R.id.txt_subtitle);
        title = findViewById(R.id.txt_title);
        search = findViewById(R.id.edit_search);
        chipAll = findViewById(R.id.chip_all);
        chipFavorites = findViewById(R.id.chip_favorites);
        chipRecent = findViewById(R.id.chip_recent);
        chipFolders = findViewById(R.id.chip_folders);

        adapter = new MediaAdapter(this, 320);
        folderAdapter = new FolderAdapter();
        list.setLayoutManager(new GridLayoutManager(this, 2));
        list.setAdapter(adapter);

        findViewById(R.id.btn_back).setOnClickListener(v -> {
            if (showingFolders && folder != null) {
                folder = null;
                reload();
            } else {
                finish();
            }
        });
        findViewById(R.id.btn_sort).setOnClickListener(v -> showSortDialog());
        findViewById(R.id.btn_open_file).setOnClickListener(v -> pickFile());
        findViewById(R.id.chip_enter_vr).setOnClickListener(v -> openVrGrid());

        chipAll.setOnClickListener(v -> setFilter(FILTER_ALL));
        chipFavorites.setOnClickListener(v -> setFilter(FILTER_FAVORITES));
        chipRecent.setOnClickListener(v -> setFilter(FILTER_RECENT));
        chipFolders.setOnClickListener(v -> setFilter(FILTER_FOLDERS));

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

        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> {
                    if (granted) {
                        reload();
                    } else {
                        showEmpty(getString(R.string.gallery_permission_needed));
                        emptyAction.setVisibility(View.GONE);
                    }
                });

        openDocumentLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(), uri -> {
                    if (uri != null) {
                        handlePickedFile(uri);
                    }
                });

        emptyAction.setOnClickListener(v -> {
            if (!VideoLibrary.hasPermission(this)) {
                permissionLauncher.launch(VideoLibrary.requiredPermission());
            } else {
                pickFile();
            }
        });
    }

    private void handlePickedFile(Uri uri) {
        int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
        VideoLibrary.addSafUri(this, uri, flags);
        VideoItem item = VideoLibrary.querySingle(this, uri);
        Toast.makeText(this, item.name, Toast.LENGTH_SHORT).show();
        reload();
        if (item.durationMs > 0) {
            startActivity(Intents.player(this, item));
        }
    }

    private void pickFile() {
        try {
            openDocumentLauncher.launch(new String[]{"video/*"});
        } catch (Exception e) {
            Toast.makeText(this, R.string.gallery_open_file, Toast.LENGTH_SHORT).show();
        }
    }

    private void openVrGrid() {
        Intent intent = new Intent(this, VrGridActivity.class);
        intent.putExtra(Intents.EXTRA_MODE, Intents.GRID_MODE_MEDIA);
        if (folder != null) {
            intent.putExtra(Intents.EXTRA_FOLDER, folder);
        }
        intent.putExtra(Intents.EXTRA_FAVORITES, filter == FILTER_FAVORITES);
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!VideoLibrary.hasPermission(this)
                && ContextCompat.checkSelfPermission(this, VideoLibrary.requiredPermission())
                != PackageManager.PERMISSION_GRANTED) {
            // Ask once; if the user declines we still show SAF-picked files.
            if (allItems.isEmpty()) {
                permissionLauncher.launch(VideoLibrary.requiredPermission());
            }
        }
        reload();
    }

    private void setFilter(int newFilter) {
        filter = newFilter;
        if (filter != FILTER_FOLDERS) {
            folder = null;
        }
        applyFilter();
    }

    private void reload() {
        emptyView.setVisibility(View.VISIBLE);
        emptyText.setText(R.string.gallery_empty);
        Background.run(() -> {
            List<VideoItem> items = VideoLibrary.all(this);
            Map<String, Integer> folders = VideoLibrary.folders(this);
            Background.main(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                allItems.clear();
                allItems.addAll(items);
                folderCounts.clear();
                folderCounts.putAll(folders);
                applyFilter();
            });
        });
    }

    private void applyFilter() {
        updateChips();
        List<VideoItem> base = new ArrayList<>();
        if (filter == FILTER_FOLDERS) {
            if (folder == null) {
                showFolders();
                return;
            }
            base.addAll(VideoLibrary.filterAndSort(allItems, "", sort, ascending, false));
            List<VideoItem> inFolder = new ArrayList<>();
            for (VideoItem item : base) {
                if (folder.equals(item.folderName)) {
                    inFolder.add(item);
                }
            }
            setVideoList(inFolder);
            return;
        }

        if (filter == FILTER_RECENT) {
            Map<String, VideoItem> byUri = new LinkedHashMap<>();
            for (VideoItem item : allItems) {
                byUri.put(item.uri, item);
            }
            for (String uri : MediaStateDb.get(this).recentUris(40)) {
                VideoItem item = byUri.get(uri);
                if (item != null) {
                    base.add(item);
                }
            }
            setVideoList(VideoLibrary.filterAndSort(base, query, sort, ascending, false));
            return;
        }

        boolean favoritesOnly = filter == FILTER_FAVORITES;
        setVideoList(VideoLibrary.filterAndSort(allItems, query, sort, ascending, favoritesOnly));
    }

    private void showFolders() {
        showingFolders = true;
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(folderAdapter);
        folderAdapter.setFolders(folderCounts);
        title.setText(R.string.gallery_folders);
        subtitle.setText(getString(R.string.gallery_video_count, allItems.size()));
        boolean isEmpty = folderCounts.isEmpty();
        emptyView.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        emptyText.setText(R.string.gallery_empty);
        emptyAction.setVisibility(VideoLibrary.hasPermission(this) ? View.GONE : View.VISIBLE);
    }

    private void setVideoList(List<VideoItem> items) {
        showingFolders = false;
        list.setLayoutManager(new GridLayoutManager(this, 2));
        list.setAdapter(adapter);
        adapter.setItems(items);
        title.setText(folder == null ? getString(R.string.gallery_title) : folder);
        subtitle.setText(getString(R.string.gallery_video_count, items.size()));
        boolean isEmpty = items.isEmpty();
        emptyView.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        if (isEmpty) {
            if (!VideoLibrary.hasPermission(this)) {
                emptyText.setText(R.string.gallery_permission_needed);
                emptyAction.setVisibility(View.VISIBLE);
                emptyAction.setText(R.string.gallery_grant);
            } else if (!query.isEmpty()) {
                emptyText.setText(R.string.gallery_empty);
                emptyAction.setVisibility(View.GONE);
            } else if (folder != null) {
                emptyText.setText(R.string.gallery_empty_folder);
                emptyAction.setVisibility(View.GONE);
            } else {
                emptyText.setText(R.string.gallery_empty);
                emptyAction.setVisibility(View.VISIBLE);
                emptyAction.setText(R.string.gallery_open_file);
            }
        }
    }

    private void updateChips() {
        setChipState(chipAll, filter == FILTER_ALL);
        setChipState(chipFavorites, filter == FILTER_FAVORITES);
        setChipState(chipRecent, filter == FILTER_RECENT);
        setChipState(chipFolders, filter == FILTER_FOLDERS);
    }

    private void setChipState(TextView chip, boolean selected) {
        chip.setBackgroundResource(selected ? R.drawable.bg_chip_selected : R.drawable.bg_chip);
        chip.setTextColor(ContextCompat.getColor(this,
                selected ? R.color.text_primary : R.color.text_secondary));
    }

    private void showSortDialog() {
        String[] labels = new String[]{
                "Name A→Z", "Name Z→A",
                "Duration short→long", "Duration long→short",
                "Size small→large", "Size large→small",
                "Newest first", "Oldest first"
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.gallery_sort)
                .setItems(labels, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            sort = VideoLibrary.SORT_NAME;
                            ascending = true;
                            break;
                        case 1:
                            sort = VideoLibrary.SORT_NAME;
                            ascending = false;
                            break;
                        case 2:
                            sort = VideoLibrary.SORT_DURATION;
                            ascending = true;
                            break;
                        case 3:
                            sort = VideoLibrary.SORT_DURATION;
                            ascending = false;
                            break;
                        case 4:
                            sort = VideoLibrary.SORT_SIZE;
                            ascending = true;
                            break;
                        case 5:
                            sort = VideoLibrary.SORT_SIZE;
                            ascending = false;
                            break;
                        case 6:
                            sort = VideoLibrary.SORT_DATE;
                            ascending = false;
                            break;
                        default:
                            sort = VideoLibrary.SORT_DATE;
                            ascending = true;
                            break;
                    }
                    applyFilter();
                })
                .show();
    }

    @Override
    public void onOpen(VideoItem item) {
        startActivity(Intents.player(this, item));
    }

    @Override
    public void onLongPress(VideoItem item) {
        MediaActions.showMenu(this, item, this::reload);
    }

    private void showEmpty(String message) {
        emptyView.setVisibility(View.VISIBLE);
        emptyText.setText(message);
    }

    /* ------------------------------------------------------------------ */

    private class FolderAdapter extends RecyclerView.Adapter<FolderVH> {
        private final List<Map.Entry<String, Integer>> entries = new ArrayList<>();

        void setFolders(Map<String, Integer> counts) {
            entries.clear();
            entries.addAll(counts.entrySet());
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public FolderVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_folder, parent, false);
            return new FolderVH(view);
        }

        @Override
        public void onBindViewHolder(@NonNull FolderVH holder, int position) {
            Map.Entry<String, Integer> entry = entries.get(position);
            holder.name.setText(entry.getKey());
            holder.count.setText(getString(R.string.gallery_folder_count, entry.getValue()));
            holder.itemView.setOnClickListener(v -> {
                folder = entry.getKey();
                applyFilter();
            });
        }

        @Override
        public int getItemCount() {
            return entries.size();
        }
    }

    private static class FolderVH extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView count;

        FolderVH(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.txt_folder);
            count = itemView.findViewById(R.id.txt_count);
        }
    }

    @SuppressWarnings("unused")
    private String describe(VideoItem item) {
        return Formats.duration(item.durationMs);
    }
}
