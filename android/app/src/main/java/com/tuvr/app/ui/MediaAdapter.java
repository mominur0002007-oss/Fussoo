package com.tuvr.app.ui;

import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.tuvr.app.R;
import com.tuvr.app.core.Formats;
import com.tuvr.app.media.ThumbLoader;
import com.tuvr.app.media.VideoItem;

import java.util.ArrayList;
import java.util.List;

/** RecyclerView adapter for video cards (gallery grid and horizontal rows). */
public class MediaAdapter extends RecyclerView.Adapter<MediaAdapter.VH> {

    public interface Listener {
        void onOpen(VideoItem item);

        void onLongPress(VideoItem item);
    }

    private final List<VideoItem> items = new ArrayList<>();
    private final Listener listener;
    private final int thumbWidthPx;

    public MediaAdapter(Listener listener, int thumbWidthPx) {
        this.listener = listener;
        this.thumbWidthPx = thumbWidthPx;
    }

    public void setItems(List<VideoItem> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    public List<VideoItem> items() {
        return items;
    }

    public void replace(int index, VideoItem item) {
        if (index >= 0 && index < items.size()) {
            items.set(index, item);
            notifyItemChanged(index);
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_media, parent, false);
        return new VH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        VideoItem item = items.get(position);
        holder.name.setText(item.name == null ? "Video" : item.name);
        String meta = item.metaLine();
        holder.meta.setText(meta.isEmpty() ? (item.folderName == null ? "" : item.folderName) : meta);
        holder.duration.setText(item.durationMs > 0 ? Formats.duration(item.durationMs) : "");
        holder.favorite.setVisibility(item.favorite ? View.VISIBLE : View.GONE);
        holder.thumb.setImageResource(R.drawable.ic_gallery);
        holder.thumb.setScaleType(ImageView.ScaleType.CENTER_INSIDE);

        ThumbLoader.get().load(holder.itemView.getContext(), item, thumbWidthPx,
                (uri, bitmap) -> {
                    if (bitmap == null || bitmap.isRecycled()) {
                        return;
                    }
                    if (!uri.equals(item.uri)) {
                        return;
                    }
                    holder.thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    holder.thumb.setImageBitmap(bitmap);
                });

        if (!item.hasMetadata()) {
            ThumbLoader.get().loadMetadata(holder.itemView.getContext(), item, meta -> {
                if (meta.durationMs > 0) {
                    holder.duration.setText(Formats.duration(meta.durationMs));
                }
                String line = meta.metaLine();
                if (!line.isEmpty()) {
                    holder.meta.setText(line);
                }
            });
        }

        holder.itemView.setOnClickListener(v -> listener.onOpen(item));
        holder.itemView.setOnLongClickListener(v -> {
            listener.onLongPress(item);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final ImageView thumb;
        final TextView name;
        final TextView meta;
        final TextView duration;
        final ImageView favorite;

        VH(@NonNull View itemView) {
            super(itemView);
            thumb = itemView.findViewById(R.id.img_thumb);
            name = itemView.findViewById(R.id.txt_name);
            meta = itemView.findViewById(R.id.txt_meta);
            duration = itemView.findViewById(R.id.txt_duration);
            favorite = itemView.findViewById(R.id.img_favorite);
        }
    }

    /** Tiny holder used by callers that need a bitmap without a view. */
    public interface BitmapReceiver {
        void onBitmap(String uri, Bitmap bitmap);
    }
}
