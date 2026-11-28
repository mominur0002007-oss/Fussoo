package com.fussoo.vr.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.MergingMediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;

import com.fussoo.vr.core.Boot;
import com.fussoo.vr.core.Formats;
import com.fussoo.vr.core.Tracker;
import com.fussoo.vr.core.VrPrefs;
import com.fussoo.vr.gl.Hud;
import com.fussoo.vr.gl.Stage;
import com.fussoo.vr.gl.VrRenderer;
import com.fussoo.vr.gl.VrSurface;
import com.fussoo.vr.net.Thumbs;
import com.fussoo.vr.net.Yt;

import org.schabi.newpipe.extractor.stream.StreamInfo;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The theatre: one stereo renderer, one ExoPlayer, gaze-driven controls.
 *
 * <p>Threading: the UI thread owns the player and the gaze state; the GL thread only reads
 * an immutable {@link Stage} snapshot. Resolution of the YouTube stream happens on a
 * worker pool, since the extractor does network I/O.</p>
 */
public final class TheatreActivity extends Activity implements VrRenderer.Callback {

    public static final String EXTRA_VIDEO_ID = "video_id";
    public static final String EXTRA_TITLE = "title";

    private static final String TAG = "FussooVR";

    private VrPrefs prefs;
    private Tracker tracker;
    private VrSurface surface;
    private VrRenderer renderer;
    private ExoPlayer player;
    private Thumbs thumbs;
    private final ExecutorService pool = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Stage stage = new Stage();

    private PowerManager.WakeLock wakeLock;
    private AudioManager audio;

    private String videoId;
    private String pendingTitle;
    private boolean browsing;
    private boolean gazeEnabled;
    private long dwellStartMs;
    private int dwellTarget = -1;
    private float dwellProgress;
    private float gazeX, gazeY;
    private int hover = -1;
    private long lastTouchMs;
    private float volume = 0.85f;
    private boolean scrubbing;
    private long scrubToMs;
    private long toastUntil;
    private String toast = "";
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            tick();
            main.postDelayed(this, 50);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Boot.once(this);
        prefs = new VrPrefs(this);
        volume = prefs.volume();
        audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        Boot.immersive(this);

        tracker = new Tracker(this);
        surface = new VrSurface(this);
        setContentView(surface);
        // The GLSurfaceView needs a live Surface before setRenderer, so the renderer is
        // created from onAttachedToWindow via this listener (fires once).
        surface.onSurfaceReady(new VrSurface.Ready() {
            @Override
            public void go() {
                surface.boot(tracker);
                renderer = surface.renderer();
                renderer.setCallback(TheatreActivity.this);
            }
        });

        loadStageFromPrefs();
        videoId = getIntent().getStringExtra(EXTRA_VIDEO_ID);
        pendingTitle = getIntent().getStringExtra(EXTRA_TITLE);

        player = buildPlayer();
        gazeEnabled = prefs.gaze();

        if (videoId == null || videoId.trim().isEmpty()) {
            browsing = true;
            loadShelf();
        } else {
            browsing = false;
            resolveAndPlay(videoId);
        }
        main.post(ticker);
    }

    private void loadStageFromPrefs() {
        stage.screenWidth = prefs.screenWidth();
        stage.screenDistance = prefs.screenDistance();
        stage.screenCenterY = prefs.screenHeight();
        stage.curve = prefs.curve();
        stage.lensSeparation = prefs.lensSeparation();
        stage.fov = prefs.fov();
        stage.barrel = prefs.barrel();
        stage.fisheye = prefs.fisheye();
        stage.env = prefs.env();
        stage.quality = prefs.quality();
        stage.brightness = prefs.brighten();
        stage.hasVideo = !browsing;

        VrPrefs fresh = prefs;
        if (!fresh.showVrButton()) {
            // Nothing to do yet; kept for a future "hand controls to the phone" mode.
        }
    }

    private DefaultHttpDataSource.Factory httpFactory;

    private ExoPlayer buildPlayer() {
        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setUserAgent(com.fussoo.vr.net.YtDownloader.USER_AGENT)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(20000)
                .setDefaultRequestProperties(java.util.Collections.singletonMap(
                        "Referer", "https://www.youtube.com/"));
        httpFactory = http;
        DefaultMediaSourceFactory factory = new DefaultMediaSourceFactory(http);
        ExoPlayer p = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(factory)
                .setHandleAudioBecomingNoisy(true)
                .build();
        p.setVolume(volume);
        p.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_BUFFERING) {
                    setStatus("Buffering…");
                } else if (state == Player.STATE_READY) {
                    setStatus("");
                    stage.buffered = buffered();
                } else if (state == Player.STATE_ENDED) {
                    setStatus("Finished");
                }
            }

            @Override
            public void onVideoSizeChanged(VideoSize size) {
                if (size.width > 0 && size.height > 0) {
                    stage.videoW = size.width * Math.max(1, size.pixelWidthHeightRatio > 0
                            ? 1 : 1);
                    stage.videoH = size.height;
                    if (size.pixelWidthHeightRatio != 1f && size.pixelWidthHeightRatio > 0) {
                        stage.videoW = (int) (stage.videoW * size.pixelWidthHeightRatio);
                    }
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                Log.w(TAG, "playback error", error);
                setToast("Playback error: " + shortError(error));
                stage.hasVideo = false;
                browsing = true;
                loadShelf();
            }
        });
        return p;
    }

    private String shortError(PlaybackException e) {
        String m = e.getMessage();
        if (m == null) {
            return String.valueOf(e.errorCode);
        }
        int cut = m.indexOf('\n');
        return cut > 0 ? m.substring(0, cut) : m;
    }

    // --------------------------------------------------------------- video

    private void resolveAndPlay(final String id) {
        setStatus("Connecting to YouTube…");
        stage.hasVideo = false;
        pool.execute(new Runnable() {
            @Override
            public void run() {
                String error = null;
                Yt.Play play = null;
                Yt.Video meta = null;
                try {
                    StreamInfo info = Yt.info(id);
                    meta = Yt.describe(info, id);
                    play = Yt.choose(info, prefs.maxHeight());
                } catch (Throwable t) {
                    error = t.getMessage() == null ? t.getClass().getSimpleName()
                            : t.getMessage();
                }
                final Yt.Play chosen = play;
                final Yt.Video described = meta;
                final String err = error;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (err != null || chosen == null) {
                            setToast("Extraction failed — using the official player");
                            startWebFallback(id);
                            return;
                        }
                        startPlayback(chosen, described);
                    }
                });
            }
        });
    }

    private void startPlayback(Yt.Play play, Yt.Video meta) {
        stage.hasVideo = true;
        stage.title = meta != null ? meta.title
                : (pendingTitle == null ? "" : pendingTitle);
        stage.uploader = meta != null ? meta.uploader : "";
        stage.qualityText = play.quality;
        if (meta != null) {
            stage.durationMs = meta.durationMs;
            prefs.setLastVideo(meta.id);
        }
        surface.video().size(1280, 720);

        MediaItem videoItem = new MediaItem.Builder().setUri(play.videoUrl).build();
        ProgressiveMediaSource.Factory factory =
                new ProgressiveMediaSource.Factory(httpFactory != null ? httpFactory
                        : new DefaultHttpDataSource.Factory()
                        .setUserAgent(com.fussoo.vr.net.YtDownloader.USER_AGENT));
        if (play.audioUrl == null) {
            player.setMediaSource(factory.createMediaSource(videoItem));
        } else {
            MediaItem audioItem = new MediaItem.Builder().setUri(play.audioUrl).build();
            MergingMediaSource merged = new MergingMediaSource(
                    factory.createMediaSource(videoItem),
                    factory.createMediaSource(audioItem));
            player.setMediaSource(merged);
        }
        player.prepare();
        player.setPlayWhenReady(true);
        setToast("Playing " + play.quality);
    }

    private void setStatus(String s) {
        stage.status = s;
    }

    private void setToast(String s) {
        toast = s;
        toastUntil = System.currentTimeMillis() + 3200;
        stage.toast = s;
    }

    private float buffered() {
        if (player == null || player.getDuration() <= 0) {
            return 0f;
        }
        return player.getBufferedPosition() / (float) player.getDuration();
    }

    /**
     * Fallback: put the official YouTube embed on a WebView and pull its frames into the
     * same texture the theatre renders. Extraction breaks when Google changes their player;
     * this path cannot break, it just has fewer pixels.
     */
    private void startWebFallback(String id) {
        if (fallbackTried) {
            setStatus("This video cannot be played. Back = library.");
            browsing = true;
            stage.hasVideo = false;
            loadShelf();
            return;
        }
        fallbackTried = true;
        stage.hasVideo = true;
        if (stage.title == null || stage.title.isEmpty()) {
            stage.title = pendingTitle == null ? "YouTube" : pendingTitle;
        }
        stage.qualityText = "embed";
        android.webkit.WebView wv = com.fussoo.vr.net.WebFallback.create(this, id, null);
        // The WebView needs a real window to render video; keep it 1x1 and transparent.
        android.widget.FrameLayout holder = new android.widget.FrameLayout(this);
        holder.addView(wv, new android.widget.FrameLayout.LayoutParams(
                Palette.dp(this, 2), Palette.dp(this, 2)));
        addContentView(holder, new android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT));
        holder.setAlpha(0f);
        wv.setAlpha(0f);
        fallbackWebView = wv;
    }

    private android.webkit.WebView fallbackWebView;
    private boolean fallbackTried;

    // ---------------------------------------------------------------- shelf

    private void loadShelf() {
        setStatus("Loading library…");
        pool.execute(new Runnable() {
            @Override
            public void run() {
                List<Yt.Video> list = null;
                try {
                    list = Yt.trending(8);
                } catch (Throwable ignored) {
                }
                if (list == null || list.isEmpty()) {
                    try {
                        list = Yt.search("4k cinematic", 8);
                    } catch (Throwable ignored) {
                    }
                }
                final List<Yt.Video> videos = list;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (videos == null || videos.isEmpty()) {
                            setToast("Could not load the library. Press back and retry.");
                            return;
                        }
                        setStatus("");
                        final Bitmap[] posters = new Bitmap[5];
                        final String[] names = new String[5];
                        if (thumbs == null) {
                            thumbs = new Thumbs();
                        }
                        for (int i = 0; i < 5 && i < videos.size(); i++) {
                            Yt.Video v = videos.get(i);
                            names[i] = v.title;
                            final int slot = i;
                            thumbs.load("shelf" + v.id, v.thumbnail, 512,
                                    new Thumbs.Callback() {
                                        @Override
                                        public void onBitmap(String key, Bitmap bmp) {
                                            posters[slot] = bmp;
                                            renderer.setShelf(posters, names);
                                        }

                                        @Override
                                        public void onFailed(String key) {
                                        }
                                    });
                        }
                        renderer.setShelf(posters, names);
                        shelfVideos = videos;
                    }
                });
            }
        });
    }

    private volatile List<Yt.Video> shelfVideos;

    // ------------------------------------------------------------- per frame

    private void tick() {
        if (player != null && player.isPlaying()) {
            stage.playing = true;
            stage.positionMs = player.getCurrentPosition();
            if (player.getDuration() > 0) {
                stage.durationMs = player.getDuration();
            }
            if (player.getPlaybackState() == Player.STATE_READY) {
                stage.buffered = buffered();
            }
        } else {
            stage.playing = player != null && player.getPlayWhenReady();
        }
        if (toast != null && toast.length() > 0
                && System.currentTimeMillis() > toastUntil) {
            toast = "";
            stage.toast = "";
        }
        stage.toast = toast;
        stage.toastUntil = toastUntil;

        // Gaze and dwell.
        float halfW = stage.screenWidth * 0.5f;
        if (renderer != null && renderer.hud() != null) {
            float halfH = halfW / Math.max(0.55f, stage.aspect());
            float[] g = gazePoint();
            gazeX = g[0];
            gazeY = g[1];
            stage.gazeX = gazeX;
            stage.gazeY = gazeY;
            stage.gazeVisible = gazeEnabled;
            int hit;
            if (browsing) {
                hit = renderer.hud().hitCard(gazeX, gazeY);
                hit = hit >= 0 ? 100 + hit : -1;
            } else {
                hit = renderer.hud().hitButton(gazeX, gazeY);
            }
            hover = hit;
            stage.hover = hit;
            updateDwell(hit);
        }
        stage.hudAlpha = 1f;
        if (renderer != null) {
            renderer.push(stage);
        }
    }

    private void updateDwell(int target) {
        long now = System.currentTimeMillis();
        if (!gazeEnabled || target < 0) {
            dwellTarget = -1;
            dwellProgress = 0f;
            stage.dwell = 0f;
            return;
        }
        if (target != dwellTarget) {
            dwellTarget = target;
            dwellStartMs = now;
            dwellProgress = 0f;
        } else {
            float p = (now - dwellStartMs) / (float) Math.max(400, prefs.dwellMs());
            dwellProgress = Math.min(1f, p);
            if (p >= 1f) {
                dwellProgress = 0f;
                dwellStartMs = now;
                activate(target);
            }
        }
        stage.dwell = dwellProgress;
    }

    private float[] gazePoint() {
        // Rotate the camera's forward vector (0,0,-1) by the head rotation, then intersect
        // that ray with the virtual screen plane. This mirrors exactly what the renderer
        // does, so the dot under the crosshair is the dot the user sees.
        float[] m = tracker.view(0f);
        float fx = -m[8];
        float fy = -m[9];
        float fz = -m[10];
        if (fz > -0.08f) {
            return new float[]{9999f, 9999f};   // looking away from the screen
        }
        float dist = stage.screenDistance;
        float t = dist / -fz;
        float x = fx * t;
        // Screen centre sits (screenCenterY - eyeHeight) above the eye. Eye height is
        // assumed at 1.6 m, matching the renderer's model translation.
        float y = fy * t + (stage.screenCenterY - 1.6f);
        return new float[]{x, y};
    }

    private void activate(int target) {
        haptic();
        if (browsing) {
            int idx = target - 100;
            List<Yt.Video> list = shelfVideos;
            if (list != null && idx >= 0 && idx < list.size()) {
                Yt.Video v = list.get(idx);
                browsing = false;
                stage.hasVideo = false;
                videoId = v.id;
                pendingTitle = v.title;
                resolveAndPlay(v.id);
            }
            return;
        }
        switch (target) {
            case Hud.PLAY:
                if (player != null) {
                    if (player.isPlaying()) {
                        player.pause();
                        setStatus("Paused");
                    } else {
                        player.play();
                        setStatus("");
                    }
                }
                break;
            case Hud.BACK:
                seek(-prefs.seekStepMs());
                break;
            case Hud.FWD:
                seek(prefs.seekStepMs());
                break;
            case Hud.VOL_DOWN:
                volume = Math.max(0f, volume - 0.08f);
                applyVolume();
                break;
            case Hud.VOL_UP:
                volume = Math.min(1f, volume + 0.08f);
                applyVolume();
                break;
            case Hud.QUALITY:
                if (player != null) {
                    setToast("Quality: " + stage.qualityText + " (change max in Settings)");
                }
                break;
            case Hud.RECENTER:
                recenter();
                break;
            case Hud.EXIT:
                finish();
                break;
            default:
                break;
        }
    }

    private void applyVolume() {
        if (player != null) {
            player.setVolume(volume);
        }
        prefs.setVolume(volume);
        setToast("Volume " + Math.round(volume * 100) + "%");
    }

    private void seek(long deltaMs) {
        if (player == null) {
            return;
        }
        long target = Math.max(0, Math.min(player.getDuration() - 500,
                player.getCurrentPosition() + deltaMs));
        player.seekTo(target);
        setToast((deltaMs > 0 ? "+" : "-") + Math.abs(deltaMs / 1000) + "s  ·  "
                + Formats.duration(target));
    }

    private void recenter() {
        tracker.recenter();
        lookAtScreen(true);
        setToast("Recentred");
        haptic();
    }

    private void lookAtScreen(boolean alsoToast) {
        // Recentring is a UI affordance only; the tracker keeps its own origin so the
        // world never jumps. Kept as a hook for a future "snap to screen" gesture.
        if (alsoToast) {
            setStatus("Look straight ahead, then hold RECENTRE");
        }
    }

    private void haptic() {
        if (!prefs.haptics()) {
            return;
        }
        try {
            View v = surface;
            if (v != null) {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            }
        } catch (Exception ignored) {
        }
    }

    // --------------------------------------------------------------- input

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        lastTouchMs = System.currentTimeMillis();
        if (event.getAction() == MotionEvent.ACTION_UP) {
            // A tap anywhere pauses/resumes: the most requested VR control.
            if (!browsing && prefs.tapToPause() && player != null) {
                if (player.isPlaying()) {
                    player.pause();
                    setToast("Paused · tap to resume");
                } else {
                    player.play();
                    setToast("");
                }
                haptic();
            }
        }
        return true;
    }

    // ------------------------------------------------------------ callbacks

    @Override
    public void onGlReady() {
        tracker.start(this);
        if (prefs.keepAwake()) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "FussooVR:theatre");
            wakeLock.acquire(90 * 60 * 1000L);
        }
    }

    @Override
    public void onFps(float fps) {
        // Diagnostics only. Logged at debug level so logcat stays clean on release.
        if (fps < 28 && stage.hasVideo && player != null && player.isPlaying()) {
            Log.d(TAG, "low fps: " + fps);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        surface.onPause();
        tracker.stop();
        if (player != null) {
            player.pause();
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Boot.immersive(this);
        surface.onResume();
        loadStageFromPrefs();
        stage.quality = prefs.quality();
        gazeEnabled = prefs.gaze();
        if (wakeLock != null && prefs.keepAwake() && !wakeLock.isHeld()) {
            wakeLock.acquire(90 * 60 * 1000L);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        main.removeCallbacks(ticker);
        pool.shutdownNow();
        if (thumbs != null) {
            thumbs.shutdown();
        }
        if (player != null) {
            player.release();
            player = null;
        }
        if (fallbackWebView != null) {
            fallbackWebView.destroy();
            fallbackWebView = null;
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
    }

    @Override
    public void onBackPressed() {
        if (!browsing) {
            browsing = true;
            if (player != null) {
                player.stop();
            }
            stage.hasVideo = false;
            loadShelf();
            return;
        }
        super.onBackPressed();
    }
}
