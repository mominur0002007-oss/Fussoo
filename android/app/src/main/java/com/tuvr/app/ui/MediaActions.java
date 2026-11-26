package com.tuvr.app.ui;

import android.app.Activity;
import android.content.Intent;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.tuvr.app.R;
import com.tuvr.app.core.Formats;
import com.tuvr.app.media.MediaStateDb;
import com.tuvr.app.media.VideoItem;

/** Shared long-press actions for a video item. */
public final class MediaActions {

    private MediaActions() {
    }

    public static void showMenu(Activity activity, VideoItem item, Runnable onChanged) {
        boolean favorite = MediaStateDb.get(activity).isFavorite(item.uri);
        String[] actions = new String[]{
                activity.getString(R.string.action_play),
                activity.getString(R.string.player_enter_vr),
                favorite ? activity.getString(R.string.action_unfavorite)
                        : activity.getString(R.string.action_favorite),
                activity.getString(R.string.diag_device),
                activity.getString(R.string.action_cancel)
        };
        new MaterialAlertDialogBuilder(activity)
                .setTitle(item.name == null ? "Video" : item.name)
                .setItems(actions, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            activity.startActivity(Intents.player(activity, item));
                            break;
                        case 1: {
                            Intent intent = new Intent(activity, CinemaActivity.class);
                            intent.putExtra(Intents.EXTRA_URI, item.uri);
                            intent.putExtra(Intents.EXTRA_NAME, item.name);
                            intent.putExtra(Intents.EXTRA_AUTOPLAY, true);
                            activity.startActivity(intent);
                            break;
                        }
                        case 2:
                            MediaStateDb.get(activity).setFavorite(item.uri, !favorite);
                            item.favorite = !favorite;
                            if (onChanged != null) {
                                onChanged.run();
                            }
                            break;
                        case 3:
                            showInfo(activity, item);
                            break;
                        default:
                            break;
                    }
                })
                .show();
    }

    public static void showInfo(Activity activity, VideoItem item) {
        StringBuilder sb = new StringBuilder();
        sb.append("Name: ").append(item.name).append('\n');
        sb.append("Folder: ").append(item.folderName == null ? "-" : item.folderName).append('\n');
        sb.append("Duration: ").append(item.durationMs > 0
                ? Formats.duration(item.durationMs) : "unknown").append('\n');
        sb.append("Resolution: ").append(item.width > 0
                ? Formats.resolution(item.width, item.height) : "unknown").append('\n');
        sb.append("Size: ").append(item.sizeBytes > 0
                ? Formats.size(item.sizeBytes) : "unknown").append('\n');
        if (item.positionMs > 0) {
            sb.append("Resume at: ").append(Formats.duration(item.positionMs)).append('\n');
        }
        if (item.favorite) {
            sb.append("Favorite: yes\n");
        }
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.diag_device)
                .setMessage(sb.toString())
                .setPositiveButton(R.string.action_close, null)
                .show();
    }
}
