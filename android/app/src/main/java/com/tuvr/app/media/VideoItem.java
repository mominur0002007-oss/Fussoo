package com.tuvr.app.media;

import androidx.annotation.NonNull;

import com.tuvr.app.core.Formats;

import java.util.Locale;

/** One playable video entry, either from MediaStore or from a Storage-Access pick. */
public final class VideoItem {

    @NonNull
    public final String uri;
    public String name;
    public String folderName;
    public String folderId;
    public long durationMs;
    public long sizeBytes;
    public int width;
    public int height;
    public long dateAdded;
    public boolean fromSaf;
    public boolean favorite;
    public long lastPlayed;
    public long positionMs;

    public VideoItem(@NonNull String uri) {
        this.uri = uri;
    }

    public VideoItem(@NonNull String uri, String name) {
        this.uri = uri;
        this.name = name;
    }

    public boolean hasMetadata() {
        return durationMs > 0 || width > 0;
    }

    public String metaLine() {
        StringBuilder sb = new StringBuilder();
        if (durationMs > 0) {
            sb.append(Formats.duration(durationMs));
        }
        if (width > 0 && height > 0) {
            if (sb.length() > 0) {
                sb.append("  ·  ");
            }
            sb.append(Formats.resolution(width, height));
        }
        if (sizeBytes > 0) {
            if (sb.length() > 0) {
                sb.append("  ·  ");
            }
            sb.append(Formats.size(sizeBytes));
        }
        if (sb.length() == 0) {
            sb.append("");
        }
        return sb.toString();
    }

    public String debugLine() {
        return String.format(Locale.US, "%s [%s] %s", name, folderName == null ? "-" : folderName, uri);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof VideoItem && ((VideoItem) o).uri.equals(uri);
    }

    @Override
    public int hashCode() {
        return uri.hashCode();
    }
}
