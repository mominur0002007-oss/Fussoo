package com.tuvr.app.core;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * YouTube link handling.
 *
 * The VR browser can show YouTube pages, but a WebView capture is not a video
 * player. When the user asks for a video, the honest thing is to hand it to the
 * official YouTube app, which on many phones can then be used in its own
 * cardboard/VR mode for supported videos.
 */
public final class YoutubeLinks {

    private static final Pattern WATCH = Pattern.compile("[?&]v=([A-Za-z0-9_-]{6,})");
    private static final Pattern SHORT = Pattern.compile("youtu\\.be/([A-Za-z0-9_-]{6,})");
    private static final Pattern EMBED = Pattern.compile("/embed/([A-Za-z0-9_-]{6,})");
    private static final Pattern SHORTS = Pattern.compile("/shorts/([A-Za-z0-9_-]{6,})");

    private YoutubeLinks() {
    }

    public static boolean isYoutube(String url) {
        if (url == null) {
            return false;
        }
        String lower = url.toLowerCase(java.util.Locale.US);
        return lower.contains("youtube.com") || lower.contains("youtu.be")
                || lower.startsWith("vnd.youtube:");
    }

    /** Returns the video id, or null when the URL is not a video link. */
    public static String videoId(String url) {
        if (url == null) {
            return null;
        }
        Matcher matcher = WATCH.matcher(url);
        if (matcher.find()) {
            return matcher.group(1);
        }
        matcher = SHORT.matcher(url);
        if (matcher.find()) {
            return matcher.group(1);
        }
        matcher = EMBED.matcher(url);
        if (matcher.find()) {
            return matcher.group(1);
        }
        matcher = SHORTS.matcher(url);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    public static String watchUrl(String videoId) {
        return "https://m.youtube.com/watch?v=" + videoId;
    }

    public static String searchUrl(String query) {
        return "https://m.youtube.com/results?search_query="
                + Uri.encode(query == null ? "" : query);
    }

    /**
     * Opens the video in the YouTube app when it is installed. Returns false when
     * there is nothing to hand over (not a video link, or no app installed).
     */
    public static boolean openInApp(Context context, String url) {
        String id = videoId(url);
        if (id == null) {
            return false;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:" + id));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            if (intent.resolveActivity(context.getPackageManager()) != null) {
                context.startActivity(intent);
                return true;
            }
        } catch (RuntimeException ignored) {
            // fall through
        }
        return false;
    }
}
