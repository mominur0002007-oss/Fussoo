package com.fussoo.vr.net;

import android.text.TextUtils;

import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.ListExtractor;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.ServiceList;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.kiosk.KioskExtractor;
import org.schabi.newpipe.extractor.localization.ContentCountry;
import org.schabi.newpipe.extractor.localization.Localization;
import org.schabi.newpipe.extractor.search.SearchExtractor;
import org.schabi.newpipe.extractor.stream.AudioStream;
import org.schabi.newpipe.extractor.stream.DeliveryMethod;
import org.schabi.newpipe.extractor.MediaFormat;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.extractor.stream.VideoStream;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The whole YouTube surface of the app. Everything here blocks, so call it from a worker
 * thread. Nothing in this class touches the UI.
 */
public final class Yt {

    private static volatile boolean init;
    private static YtDownloader downloader;

    private Yt() {
    }

    public static synchronized void init() {
        if (init) {
            return;
        }
        downloader = new YtDownloader();
        NewPipe.init(downloader, Localization.fromLocale(Locale.US), new ContentCountry("US"));
        init = true;
    }

    public static YtDownloader downloader() {
        return downloader;
    }

    private static StreamingService service() throws Exception {
        init();
        return ServiceList.YouTube;
    }

    // -------------------------------------------------------------- model

    public static final class Video {
        public final String id;
        public final String title;
        public final String uploader;
        public final long durationMs;
        public final long views;
        public final String thumbnail;

        public Video(String id, String title, String uploader, long durationMs, long views,
                     String thumbnail) {
            this.id = id;
            this.title = title == null ? "" : title;
            this.uploader = uploader == null ? "" : uploader;
            this.durationMs = durationMs;
            this.views = views;
            this.thumbnail = thumbnail;
        }
    }

    public static final class Play {
        public final String videoUrl;
        public final String audioUrl;   // null when the stream is already muxed
        public final int height;
        public final String quality;

        Play(String videoUrl, String audioUrl, int height, String quality) {
            this.videoUrl = videoUrl;
            this.audioUrl = audioUrl;
            this.height = height;
            this.quality = quality;
        }
    }

    // ---------------------------------------------------------------- ids

    /** Pulls the 11-character id out of any YouTube URL shape, or the raw id. */
    public static String id(String input) {
        if (TextUtils.isEmpty(input)) {
            return null;
        }
        String s = input.trim();
        if (s.matches("[A-Za-z0-9_-]{11}")) {
            return s;
        }
        String[] marks = {"watch?v=", "youtu.be/", "/shorts/", "/embed/", "/live/", "/v/"};
        for (String m : marks) {
            int i = s.indexOf(m);
            if (i >= 0) {
                String rest = s.substring(i + m.length());
                int cut = rest.length();
                int amp = rest.indexOf('&');
                int q = rest.indexOf('?');
                int slash = rest.indexOf('/');
                if (amp > 0) {
                    cut = Math.min(cut, amp);
                }
                if (q > 0) {
                    cut = Math.min(cut, q);
                }
                if (slash > 0) {
                    cut = Math.min(cut, slash);
                }
                String candidate = rest.substring(0, cut);
                if (candidate.length() >= 11) {
                    return candidate.substring(0, 11);
                }
            }
        }
        return null;
    }

    public static String url(String id) {
        return "https://www.youtube.com/watch?v=" + id;
    }

    // --------------------------------------------------------- discovery

    public static List<Video> trending(int limit) throws Exception {
        StreamingService svc = service();
        KioskExtractor kiosk = svc.getKioskList().getDefaultKioskExtractor();
        kiosk.fetchPage();
        List<Video> out = new ArrayList<>();
        collect(out, kiosk.getInitialPage().getItems(), limit);
        return out;
    }

    public static List<Video> search(String query, int limit) throws Exception {
        List<Video> out = new ArrayList<>();
        if (TextUtils.isEmpty(query)) {
            return out;
        }
        SearchExtractor search = service().getSearchExtractor(query.trim());
        search.fetchPage();
        ListExtractor.InfoItemsPage<InfoItem> page = search.getInitialPage();
        collect(out, page.getItems(), limit);
        if (out.size() < limit && page.hasNextPage()) {
            try {
                collect(out, search.getPage(page.getNextPage()).getItems(), limit);
            } catch (Exception ignored) {
                // A missing second page is not worth a user-facing error.
            }
        }
        return out;
    }

    private static void collect(List<Video> out, List<InfoItem> items, int limit) {
        if (items == null) {
            return;
        }
        for (InfoItem item : items) {
            if (out.size() >= limit) {
                return;
            }
            if (!(item instanceof StreamInfoItem)) {
                continue;
            }
            StreamInfoItem s = (StreamInfoItem) item;
            if (s.getStreamType() != StreamType.VIDEO_STREAM
                    && s.getStreamType() != StreamType.LIVE_STREAM) {
                continue;
            }
            String vid = id(s.getUrl());
            if (vid == null) {
                continue;
            }
            out.add(new Video(vid, s.getName(), s.getUploaderName(), s.getDuration(),
                    s.getViewCount(), largest(s.getThumbnails())));
        }
    }

    // ------------------------------------------------------------- stream

    public static StreamInfo info(String videoId) throws Exception {
        StreamInfo info = StreamInfo.getInfo(service(), url(videoId));
        if (info == null) {
            throw new IllegalStateException("YouTube returned no data for this video.");
        }
        return info;
    }

    public static Video describe(StreamInfo info, String id) {
        return new Video(id, info.getName(), info.getUploaderName(), info.getDuration(),
                info.getViewCount(), largest(info.getThumbnails()));
    }

    /**
     * Chooses what to hand to the player. A muxed (progressive) stream is preferred when
     * it is good enough, because it needs no merging and starts fastest; otherwise a
     * video-only stream plus the best m4a audio track is used.
     */
    public static Play choose(StreamInfo info, int maxHeight) {
        VideoStream bestMuxed = null;
        if (info.getVideoStreams() != null) {
            for (VideoStream v : info.getVideoStreams()) {
                if (!playable(v) || !isMp4(v)) {
                    continue;
                }
                if (v.getHeight() > maxHeight) {
                    continue;
                }
                if (bestMuxed == null || v.getHeight() > bestMuxed.getHeight()) {
                    bestMuxed = v;
                }
            }
        }

        VideoStream bestVideo = null;
        if (info.getVideoOnlyStreams() != null) {
            for (VideoStream v : info.getVideoOnlyStreams()) {
                if (!playable(v) || !isMp4(v) || v.getHeight() > maxHeight) {
                    continue;
                }
                if (bestVideo == null || v.getHeight() > bestVideo.getHeight()) {
                    bestVideo = v;
                }
            }
        }

        AudioStream bestAudio = null;
        if (info.getAudioStreams() != null) {
            for (AudioStream a : info.getAudioStreams()) {
                if (a.getDeliveryMethod() != DeliveryMethod.PROGRESSIVE_HTTP
                        || TextUtils.isEmpty(a.getUrl()) || a.getUrl().startsWith("rtmp")) {
                    continue;
                }
                boolean m4a = a.getFormat() == MediaFormat.M4A;
                if (bestAudio == null) {
                    bestAudio = a;
                    continue;
                }
                boolean haveM4a = bestAudio.getFormat() == MediaFormat.M4A;
                if (m4a && !haveM4a) {
                    bestAudio = a;
                } else if (m4a == haveM4a && a.getAverageBitrate() > bestAudio.getAverageBitrate()) {
                    bestAudio = a;
                }
            }
        }

        boolean muxedIsFine = bestMuxed != null
                && (bestVideo == null || bestVideo.getHeight() <= bestMuxed.getHeight());

        if (muxedIsFine) {
            return new Play(bestMuxed.getUrl(), null, bestMuxed.getHeight(),
                    quality(bestMuxed));
        }
        if (bestVideo != null && bestAudio != null) {
            return new Play(bestVideo.getUrl(), bestAudio.getUrl(), bestVideo.getHeight(),
                    quality(bestVideo));
        }
        if (bestMuxed != null) {
            return new Play(bestMuxed.getUrl(), null, bestMuxed.getHeight(), quality(bestMuxed));
        }
        if (bestVideo != null) {
            return new Play(bestVideo.getUrl(), null, bestVideo.getHeight(), quality(bestVideo));
        }
        if (!TextUtils.isEmpty(info.getHlsUrl())) {
            return new Play(info.getHlsUrl(), null, 0, "HLS");
        }
        if (!TextUtils.isEmpty(info.getDashMpdUrl())) {
            return new Play(info.getDashMpdUrl(), null, 0, "DASH");
        }
        throw new IllegalStateException(
                "No playable stream for this video. It may be live, private or region locked.");
    }

    private static boolean playable(VideoStream v) {
        return v.getDeliveryMethod() == DeliveryMethod.PROGRESSIVE_HTTP
                && !TextUtils.isEmpty(v.getUrl())
                && !v.getUrl().startsWith("rtmp");
    }

    private static boolean isMp4(VideoStream v) {
        return v.getFormat() == MediaFormat.MPEG_4;
    }

    private static String quality(VideoStream v) {
        String res = v.getResolution();
        String codec = v.getCodec() == null ? "" : v.getCodec();
        String c = codec.toLowerCase(Locale.US);
        if (c.startsWith("avc") || c.contains("264")) {
            codec = "H.264";
        } else if (c.contains("vp9") || c.startsWith("vp09")) {
            codec = "VP9";
        } else if (c.contains("av01") || c.contains("av1")) {
            codec = "AV1";
        }
        return (res == null ? "" : res) + (codec.isEmpty() ? "" : " · " + codec);
    }

    public static boolean isVr(StreamInfo info) {
        String name = info.getName();
        if (name != null) {
            String n = name.toLowerCase(Locale.US);
            if (n.contains("360") || n.contains("vr ") || n.contains("180")) {
                return true;
            }
        }
        if (info.getTags() != null) {
            for (String t : info.getTags()) {
                if (t == null) {
                    continue;
                }
                String s = t.toLowerCase(Locale.US);
                if (s.contains("360") || s.contains("vr")) {
                    return true;
                }
            }
        }
        return false;
    }

    public static String largest(List<Image> images) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        Image best = null;
        int bestArea = -1;
        for (Image i : images) {
            int area = (i.getWidth() > 0 && i.getHeight() > 0)
                    ? i.getWidth() * i.getHeight() : 0;
            if (area > bestArea) {
                bestArea = area;
                best = i;
            }
        }
        return best == null ? null : best.getUrl();
    }
}
