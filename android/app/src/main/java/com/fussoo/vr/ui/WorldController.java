package com.fussoo.vr.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MotionEvent;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MergingMediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;

import com.fussoo.vr.core.Boot;
import com.fussoo.vr.core.Formats;
import com.fussoo.vr.core.Tracker;
import com.fussoo.vr.core.VrPrefs;
import com.fussoo.vr.core.WorldModel;
import com.fussoo.vr.gl.Env;
import com.fussoo.vr.gl.Scene;
import com.fussoo.vr.gl.VrRenderer;
import com.fussoo.vr.net.Thumbs;
import com.fussoo.vr.net.Yt;
import com.fussoo.vr.net.YtDownloader;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Runs the whole application inside the virtual world.
 *
 * <p>Responsibilities, in order of importance:</p>
 * <ol>
 *   <li>Turn the head direction into a gaze ray, intersect it with the room's surfaces,
 *       and drive dwell selection from that.</li>
 *   <li>Fill {@link WorldModel} with whatever the current screen should show.</li>
 *   <li>Fetch YouTube data and drive ExoPlayer.</li>
 * </ol>
 *
 * <p>Threading: everything here runs on the UI thread except network work, which is handed
 * to a small pool. The renderer only ever sees snapshots.</p>
 */
public final class WorldController implements WorldSurface.Listener {

    private static final String TAG = "FussooVR";
    private static final long HUD_IDLE_MS = 3800L;

    private final Activity activity;
    private final WorldSurface surface;
    private final Tracker tracker;
    private final VrPrefs prefs;
    private final WorldModel model = new WorldModel();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService pool = Executors.newFixedThreadPool(3);
    private final ExecutorService netPool = Executors.newSingleThreadExecutor();

    private Thumbs thumbs;
    private ExoPlayer player;
    private DefaultHttpDataSource.Factory httpFactory;

    /** Current list backing the shelf; index in the model maps into this. */
    private final List<Yt.Video> videos = new ArrayList<>();
    private final List<Bitmap> posters = new ArrayList<>();
    private final List<Yt.Video> shelf = new ArrayList<>();
    private String shelfQuery = "";
    private int page;

    private int settingIndex;
    private String settingKey = "";

    private long dwellStart;
    private String dwellKey = "";
    private float dwellProgress;
    private long lastInteractionMs;
    private long hudShownUntilMs;
    private String toast = "";
    private long toastUntil;
    private boolean scrubbing;
    private boolean embedFallbackTried;
    private float volume;
    private AudioManager audio;

    public WorldController(Activity activity, WorldSurface surface, Tracker tracker) {
        this.activity = activity;
        this.surface = surface;
        this.tracker = tracker;
        this.prefs = new VrPrefs(activity);
        this.thumbs = new Thumbs();
        this.audio = (AudioManager) activity.getSystemService(Context.AUDIO_SERVICE);
        this.volume = prefs.volume();
        loadPrefsIntoModel();
        buildPlayer();
    }

    // --------------------------------------------------------------- start

    /** @param sharedVideoId a video shared into the app, or null to open the home screen */
    /** Called from the renderer once the GL context exists and the texture is live. */
    public void onGlReady() {
        main.post(new Runnable() {
            @Override
            public void run() {
                VrRenderer r = surface.renderer();
                if (player != null && r != null && r.videoSurface() != null) {
                    player.setVideoSurface(r.videoSurface());
                }
            }
        });
    }

    public void start(String sharedVideoId) {
        model.query = "trending";
        model.shelfTitle = "Trending on YouTube";
        model.screen = WorldModel.Screen.HOME;
        if (sharedVideoId != null) {
            playVideoId(sharedVideoId);
        } else {
            loadShelf("Trending on YouTube", true);
        }
        main.post(tick);
    }

    public void reloadPreferences() {
        loadPrefsIntoModel();
        if (model.screen == WorldModel.Screen.SETTINGS) {
            model.settings.clear();
            model.settings.addAll(buildSettings());
        }
        push();
    }

    public void resume() {
        main.removeCallbacks(tick);
        main.post(tick);
    }

    public void pause() {
        main.removeCallbacks(tick);
        if (player != null && player.isPlaying()) {
            player.pause();
        }
    }

    public void destroy() {
        main.removeCallbacks(tick);
        pool.shutdownNow();
        netPool.shutdownNow();
        thumbs.shutdown();
        if (player != null) {
            player.release();
            player = null;
        }
    }

    private void loadPrefsIntoModel() {
        model.env = prefs.env();
        model.quality = prefs.quality();
        model.screenWidth = prefs.screenWidth();
        model.screenDistance = prefs.screenDistance();
        model.screenCenterY = prefs.screenHeight();
        model.curve = prefs.curve();
        model.lensSeparation = prefs.lensSeparation();
        model.barrel = prefs.barrel();
        model.fisheye = prefs.fisheye();
        model.brightness = prefs.brighten();
        model.gazeVisible = prefs.gaze();
        model.videoW = 1920;
        model.videoH = 1080;
    }

    // -------------------------------------------------------------- player

    private void buildPlayer() {
        httpFactory = new DefaultHttpDataSource.Factory()
                .setUserAgent(YtDownloader.USER_AGENT)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(20000)
                .setDefaultRequestProperties(java.util.Collections.singletonMap(
                        "Referer", "https://www.youtube.com/"));
        player = new ExoPlayer.Builder(activity)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(httpFactory))
                .setHandleAudioBecomingNoisy(true)
                .build();
        player.setVolume(volume);
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                model.buffering = state == Player.STATE_BUFFERING;
                if (state == Player.STATE_READY) {
                    model.buffering = false;
                } else if (state == Player.STATE_ENDED) {
                    model.playing = false;
                }
                push();
            }

            @Override
            public void onIsPlayingChanged(boolean playing) {
                model.playing = playing;
                if (playing) {
                    model.hudAlpha = 1f;
                    hudShownUntilMs = System.currentTimeMillis() + HUD_IDLE_MS;
                }
                push();
            }

            @Override
            public void onVideoSizeChanged(VideoSize size) {
                if (size.width > 0 && size.height > 0) {
                    model.videoW = size.width;
                    model.videoH = size.height;
                    if (size.pixelWidthHeightRatio > 0 && size.pixelWidthHeightRatio != 1f) {
                        model.videoW = (int) (model.videoW * size.pixelWidthHeightRatio);
                    }
                    push();
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                Log.w(TAG, "player error", error);
                String msg = error.getMessage() == null ? "unknown"
                        : error.getMessage().split("\n")[0];
                say("Playback problem: " + msg);
                if (!embedFallbackTried) {
                    embedFallbackTried = true;
                    startEmbedFallback(model.screen == WorldModel.Screen.PLAYER
                            ? null : lastVideoId);
                } else {
                    goHome();
                }
            }
        });
    }

    private String lastVideoId;

    /** Resolves a YouTube id and starts playback. */
    public void playVideoId(final String id) {
        lastVideoId = id;
        embedFallbackTried = false;
        model.screen = WorldModel.Screen.PLAYER;
        model.hasVideo = false;
        model.buffering = true;
        model.videoTitle = "Loading…";
        model.videoUploader = "";
        model.positionMs = 0;
        model.durationMs = 0;
        model.hudAlpha = 1f;
        hudShownUntilMs = System.currentTimeMillis() + HUD_IDLE_MS;
        push();

        netPool.execute(new Runnable() {
            @Override
            public void run() {
                String error = null;
                Yt.Play play = null;
                Yt.Video meta = null;
                try {
                    org.schabi.newpipe.extractor.stream.StreamInfo info = Yt.info(id);
                    meta = Yt.describe(info, id);
                    play = Yt.choose(info, prefs.maxHeight());
                } catch (Throwable t) {
                    error = t.getMessage() == null ? t.getClass().getSimpleName()
                            : t.getMessage();
                    Log.w(TAG, "extraction failed: " + error);
                }
                final Yt.Play resolved = play;
                final Yt.Video described = meta;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (resolved != null) {
                            startPlayback(resolved, described);
                        } else {
                            startEmbedFallback(id);
                        }
                    }
                });
            }
        });
    }

    private void startPlayback(Yt.Play play, Yt.Video meta) {
        model.hasVideo = true;
        model.buffering = false;
        model.videoTitle = meta != null ? meta.title : "YouTube";
        model.videoUploader = meta != null ? meta.uploader : "";
        model.qualityText = play.quality;
        model.durationMs = meta != null ? meta.durationMs : 0;
        prefs.setLastVideo(play != null && meta != null ? meta.id : lastVideoId);

        MediaItem v = new MediaItem.Builder().setUri(play.videoUrl).build();
        ProgressiveMediaSource.Factory f =
                new ProgressiveMediaSource.Factory(httpFactory);
        if (play.audioUrl == null) {
            player.setMediaSource(f.createMediaSource(v));
        } else {
            MediaItem a = new MediaItem.Builder().setUri(play.audioUrl).build();
            player.setMediaSource(new MergingMediaSource(
                    f.createMediaSource(v), f.createMediaSource(a)));
        }
        // The film is decoded straight into an OES texture that the stereo renderer
        // samples. Without this the player would draw to its own view and the theatre
        // would stay black even though audio plays.
        VrRenderer r = surface.renderer();
        if (r != null && r.videoSurface() != null) {
            player.setVideoSurface(r.videoSurface());
        }
        player.prepare();
        player.setPlayWhenReady(true);
        // 720p is the right default: the panel is small, and the T606 decoder is happiest
        // with 8-bit H.264 at this size.
        surface.video().size(1280, 720);
        say("Playing · " + play.quality);
        push();
    }

    /**
     * Embed fallback. Kept deliberately silent: if the extractor fails the user still gets
     * to watch, they just lose quality.
     */
    private void startEmbedFallback(String id) {
        if (id == null) {
            goHome();
            return;
        }
        model.hasVideo = true;
        model.buffering = false;
        model.videoTitle = model.videoTitle == null || model.videoTitle.isEmpty()
                ? "YouTube" : model.videoTitle;
        model.qualityText = "embed";
        say("Extraction unavailable · using the official player");
        push();
        // The WebView path is a last resort; it is wired in WorldEmbed.
        WorldEmbed.show(activity, surface, id);
    }

    // ---------------------------------------------------------------- shelf

    private void loadShelf(final String title, final boolean trending) {
        model.shelfTitle = title;
        model.loading = true;
        model.errorLine = "";
        push();
        netPool.execute(new Runnable() {
            @Override
            public void run() {
                String error = null;
                List<Yt.Video> list = new ArrayList<>();
                try {
                    list = trending ? Yt.trending(30) : Yt.search(title, 30);
                } catch (Throwable t) {
                    error = t.getMessage() == null ? t.getClass().getSimpleName()
                            : t.getMessage();
                }
                final List<Yt.Video> out = list;
                final String err = error;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        model.loading = false;
                        if (err != null) {
                            model.errorLine = "YouTube: " + err;
                            push();
                            return;
                        }
                        videos.clear();
                        videos.addAll(out);
                        page = 0;
                        rebuildTiles(0);
                        push();
                        loadThumbs();
                    }
                });
            }
        });
    }

    private void search(final String query) {
        model.query = query;
        model.screen = WorldModel.Screen.BROWSE;
        model.shelfTitle = "\"" + query + "\"";
        model.loading = true;
        push();
        netPool.execute(new Runnable() {
            @Override
            public void run() {
                String error = null;
                List<Yt.Video> list = new ArrayList<>();
                try {
                    list = Yt.search(query, 30);
                } catch (Throwable t) {
                    error = t.getMessage() == null ? t.getClass().getSimpleName()
                            : t.getMessage();
                }
                final List<Yt.Video> out = list;
                final String err = error;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        model.loading = false;
                        if (err != null) {
                            model.errorLine = "Search failed: " + err;
                            push();
                            return;
                        }
                        if (out.isEmpty()) {
                            model.errorLine = "Nothing found for \"" + query + "\"";
                            push();
                            return;
                        }
                        videos.clear();
                        videos.addAll(out);
                        page = 0;
                        rebuildTiles(0);
                        push();
                        loadThumbs();
                    }
                });
            }
        });
    }

    private int tileCount() {
        return model.screen == WorldModel.Screen.HOME ? 5 : 10;
    }

    /** Rebuilds the poster wall from the current page of results. */
    private void rebuildTiles(int pageIndex) {
        int offset = pageIndex * tileCount();
        model.tiles.clear();
        model.hero = null;
        if (videos.isEmpty()) {
            return;
        }
        Yt.Video heroVideo = videos.get(Math.min(offset, videos.size() - 1));
        model.hero = new WorldModel.Tile("hero:" + heroVideo.id, heroVideo.title,
                heroVideo.uploader + " · " + Formats.duration(heroVideo.durationMs),
                "", thumbs.peek(heroVideo.id), 0, true);

        for (int i = 0; i < tileCount(); i++) {
            int idx = offset + i;
            if (idx >= videos.size()) {
                break;
            }
            Yt.Video v = videos.get(idx);
            model.tiles.add(new WorldModel.Tile("tile:" + idx, v.title,
                    v.uploader, v.durationMs > 0 ? Formats.duration(v.durationMs) : "LIVE",
                    thumbs.peek(v.id), 0, false));
        }
    }

    private void loadThumbs() {
        List<Yt.Video> snapshot = new ArrayList<>(videos);
        for (final Yt.Video v : snapshot) {
            if (v.thumbnail == null) {
                continue;
            }
            if (thumbs.peek(v.id) != null) {
                continue;
            }
            thumbs.load(v.id, v.thumbnail, 512, new Thumbs.Callback() {
                @Override
                public void onBitmap(String key, Bitmap bmp) {
                    // `key` is the video id. Tiles are stable objects for the lifetime of
                    // a page, but rebuildTiles() may have replaced them, so match on the
                    // title-independent index kept in the video list.
                    for (int i = 0; i < model.tiles.size(); i++) {
                        int idx = i + page * tileCount();
                        if (idx < videos.size() && videos.get(idx).id.equals(key)) {
                            model.tiles.get(i).poster = bmp;
                        }
                    }
                    if (model.hero != null) {
                        String hk = model.hero.key.substring("hero:".length());
                        if (hk.equals(key)) {
                            model.hero.poster = bmp;
                        }
                    }
                    push();
                }

                @Override
                public void onFailed(String key) {
                }
            });
        }
    }

    // ------------------------------------------------------------ settings

    private List<WorldModel.Setting> buildSettings() {
        List<WorldModel.Setting> s = new ArrayList<>();
        s.add(new WorldModel.Setting("screen_w", "Screen width",
                String.format(java.util.Locale.US, "%.1f m", prefs.screenWidth()), false, true));
        s.add(new WorldModel.Setting("screen_d", "Distance",
                String.format(java.util.Locale.US, "%.1f m", prefs.screenDistance()), false, true));
        s.add(new WorldModel.Setting("lens", "Lens separation",
                String.format(java.util.Locale.US, "%.1f mm", prefs.lensSeparation() * 1000f),
                false, true));
        s.add(new WorldModel.Setting("barrel", "Barrel correction",
                String.format(java.util.Locale.US, "%.2f", prefs.barrel()), false, true));
        s.add(new WorldModel.Setting("curve", "Curvature",
                String.format(java.util.Locale.US, "%.2f", prefs.curve()), false, true));
        s.add(new WorldModel.Setting("quality", "Performance",
                prefs.quality() == 0 ? "Battery" : prefs.quality() == 2 ? "Cinematic"
                        : "Balanced", false, true));
        s.add(new WorldModel.Setting("env", "Venue",
                Env.NAMES[Math.max(0, Math.min(Env.NAMES.length - 1, prefs.env()))],
                false, true));
        s.add(new WorldModel.Setting("gaze", "Gaze selection", "", true, prefs.gaze()));
        s.add(new WorldModel.Setting("fisheye", "Lens correction", "", true,
                prefs.fisheye()));
        return s;
    }

    private void openSettings() {
        model.screen = WorldModel.Screen.SETTINGS;
        model.settings.clear();
        model.settings.addAll(buildSettings());
        model.errorLine = "";
        push();
    }

    private void stepSetting(int index, int direction) {
        if (index < 0 || index >= model.settings.size()) {
            return;
        }
        WorldModel.Setting s = model.settings.get(index);
        float step = direction > 0 ? 1 : -1;

        if (s.toggle) {
            if (s.key.equals("gaze")) {
                prefs.setGaze(!prefs.gaze());
                model.gazeVisible = prefs.gaze();
            } else if (s.key.equals("fisheye")) {
                prefs.setFisheye(!prefs.fisheye());
                model.fisheye = prefs.fisheye();
            }
        } else if (s.key.equals("screen_w")) {
            prefs.setScreenWidth(clamp(prefs.screenWidth() + step * 0.5f, 4f, 18f));
            model.screenWidth = prefs.screenWidth();
        } else if (s.key.equals("screen_d")) {
            prefs.setScreenDistance(clamp(prefs.screenDistance() + step * 0.2f, 2.2f, 9f));
            model.screenDistance = prefs.screenDistance();
        } else if (s.key.equals("lens")) {
            prefs.setLensSeparation(clamp(prefs.lensSeparation() + step * 0.0005f,
                    0.050f, 0.078f));
            model.lensSeparation = prefs.lensSeparation();
        } else if (s.key.equals("barrel")) {
            prefs.setBarrel(clamp(prefs.barrel() + step * 0.005f, 0f, 0.4f));
            model.barrel = prefs.barrel();
        } else if (s.key.equals("curve")) {
            prefs.setCurve(clamp(prefs.curve() + step * 0.05f, 0f, 1f));
            model.curve = prefs.curve();
        } else if (s.key.equals("quality")) {
            prefs.setQuality((prefs.quality() + (direction > 0 ? 1 : 2)) % 3);
            model.quality = prefs.quality();
            surface.qualityChanged();
        } else if (s.key.equals("env")) {
            prefs.setEnv((prefs.env() + (direction > 0 ? 1 : Env.NAMES.length - 1))
                    % Env.NAMES.length);
            model.env = prefs.env();
        }
        model.settings.clear();
        model.settings.addAll(buildSettings());
        push();
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    // ------------------------------------------------------------ per frame

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            long now = System.currentTimeMillis();
            pollPlayer();
            updateGaze(now);
            updateHud(now);
            push();
            main.postDelayed(this, 33);
        }
    };

    private void pollPlayer() {
        if (player == null) {
            return;
        }
        if (player.getPlaybackState() == Player.STATE_READY
                || player.getPlaybackState() == Player.STATE_BUFFERING) {
            model.positionMs = player.getCurrentPosition();
            if (player.getDuration() > 0) {
                model.durationMs = player.getDuration();
            }
            long dur = player.getDuration();
            model.bufferedFraction = dur > 0
                    ? Math.min(1f, player.getBufferedPosition() / (float) dur) : 0f;
        }
        model.playing = player.isPlaying();
    }

    /**
     * Turns the head ray into a surface hit and feeds the dwell timer.
     *
     * <p>Note the maths: the tracker's matrix is world-from-body, and its third row (the
     * r8/r9/r10 entries) is the world-space direction of the device's -Z axis, which is
     * where the screen faces. Getting this backwards is what makes gaze cursors run away
     * from you, so it is spelled out here rather than hidden in a helper.</p>
     */
    private void updateGaze(long now) {
        float[] m = tracker.view(0f);
        // forward = R * (0,0,-1). In a column-major matrix that is the negated third
        // column, m[8..10]. Using the row instead flips the cursor left/right and is the
        // classic way to get a gaze that runs away from you.
        float fx = -m[8];
        float fy = -m[9];
        float fz = -m[10];

        Scene scene = surface.scene();
        Scene.Hit hit = null;

        // 1. The main board, tested in its own metres (centre at the eye).
        if (fz < -0.05f) {
            float t = Math.max(0.6f, model.screenDistance) / -fz;
            hit = scene.hitTest(model, fx * t, fy * t);
        }
        // 2. The player console, in front and below the film.
        if ((hit == null || !hit.valid) && model.screen == WorldModel.Screen.PLAYER) {
            hit = rayToSurface(scene, fx, fy, fz, scene.consoleZ(), scene.consoleY(),
                    scene.consoleW() * 0.5f, scene.consoleH() * 0.5f, scene.consoleTilt(),
                    Scene.SURFACE_CONSOLE);
        }
        // 3. The bottom rail, always available for navigation.
        if (hit == null || !hit.valid) {
            hit = rayToSurface(scene, fx, fy, fz, scene.railZ(), scene.railY(),
                    scene.railW() * 0.5f, scene.railH() * 0.5f, 0f, Scene.SURFACE_RAIL);
        }

        model.gazeVisible = prefs.gaze();
        if (hit != null && hit.valid) {
            model.hoverKey = hit.key;
            if (hit.surface == Scene.SURFACE_MAIN) {
                // The visual cursor only exists on the main board; panels are small.
                model.gazeX = hit.x;
                model.gazeY = hit.y;
            } else {
                model.gazeX = 9999f;
                model.gazeY = 9999f;
            }
            updateDwell(hit.key, now);
        } else {
            model.hoverKey = "";
            dwellKey = "";
            dwellProgress = 0f;
            model.dwell = 0f;
            model.gazeX = 9999f;
            model.gazeY = 9999f;
        }
    }

    /**
     * Intersects the gaze ray with a tilted panel and hands the hit to the scene's own box
     * test, so hit boxes and drawing can never drift apart.
     *
     * <p>The panel is rotated about X by {@code tiltDeg}, so its normal is
     * {@code (0, sin t, cos t)} and its surface points are
     * {@code P = C + (lx, ly*cos t, -ly*sin t)}. Intersecting the ray with that plane and
     * inverting the rotation is exact for any tilt, including zero.</p>
     */
    private Scene.Hit rayToSurface(Scene scene, float fx, float fy, float fz, float cz,
                                   float cy, float halfW, float halfH, float tiltDeg,
                                   int surfaceId) {
        Scene.Hit miss = new Scene.Hit();
        double t = Math.toRadians(tiltDeg);
        float ct = (float) Math.cos(t);
        float st = (float) Math.sin(t);
        // Panel normal after rotating about X.
        float nx = 0f;
        float ny = -st;
        float nz = ct;

        float ox = 0f;
        float oy = 1.6f - cy;
        float oz = cz;

        float denom = fx * nx + fy * ny + fz * nz;
        if (Math.abs(denom) < 1e-4f) {
            return miss;
        }
        float sDist = -(ox * nx + oy * ny + oz * nz) / denom;
        if (sDist <= 0f) {
            return miss;
        }
        float hx = ox + fx * sDist;
        float hy = oy + fy * sDist;
        float hz = oz + fz * sDist;

        // Local panel axes: X is unchanged, the in-plane up axis is (0, cos t, sin t).
        float lx = hx;
        float ly = hy * ct + hz * st;
        if (Math.abs(lx) > halfW || Math.abs(ly) > halfH) {
            return miss;
        }
        return surfaceId == Scene.SURFACE_CONSOLE
                ? scene.consoleHitTest(model, lx, ly)
                : scene.railHitTest(model, lx, ly);
    }

    private void updateDwell(String key, long now) {
        if (key == null || key.isEmpty() || !prefs.gaze()) {
            dwellKey = "";
            dwellProgress = 0f;
            model.dwell = 0f;
            return;
        }
        if (!key.equals(dwellKey)) {
            dwellKey = key;
            dwellStart = now;
            dwellProgress = 0f;
        } else {
            dwellProgress = Math.min(1f,
                    (now - dwellStart) / (float) Math.max(500, prefs.dwellMs()));
            if (dwellProgress >= 1f) {
                dwellProgress = 0f;
                dwellStart = now;
                activate(key);
                dwellKey = "";
                model.dwell = 0f;
                return;
            }
        }
        model.dwell = dwellProgress;
    }

    private void updateHud(long now) {
        if (model.screen == WorldModel.Screen.PLAYER) {
            if (now > hudShownUntilMs && model.playing) {
                float a = model.hudAlpha - 0.06f;
                model.hudAlpha = Math.max(0f, a);
            }
            // Any hover resets the fade so the controls never vanish mid-reach.
            if (!model.hoverKey.isEmpty()) {
                model.hudAlpha = 1f;
                hudShownUntilMs = now + HUD_IDLE_MS;
                lastInteractionMs = now;
            }
        } else {
            model.hudAlpha = 1f;
        }
        if (toast != null && !toast.isEmpty() && now > toastUntil) {
            toast = "";
        }
        model.toast = toast;
        model.toastUntil = toastUntil;
    }

    private void push() {
        model.dt = 0.033f;
        surface.push(model);
    }

    // ------------------------------------------------------------- activate

    private void activate(String key) {
        haptic();
        lastInteractionMs = System.currentTimeMillis();
        model.hudAlpha = 1f;
        hudShownUntilMs = System.currentTimeMillis() + HUD_IDLE_MS;

        if (key.startsWith("tile:")) {
            // The tile key carries the real index into `videos`, so HOME (5 posters) and
            // BROWSE (10 posters) can never disagree about what sits under the cursor.
            int idx;
            try {
                idx = Integer.parseInt(key.substring(5));
            } catch (NumberFormatException e) {
                return;
            }
            if (idx >= 0 && idx < videos.size()) {
                playVideoId(videos.get(idx).id);
            }
            return;
        }
        if ("hero".equals(key)) {
            if (model.hero != null) {
                String id = model.hero.key.substring("hero:".length());
                playVideoId(id);
            }
            return;
        }
        if (key.startsWith("filter:")) {
            String f = key.substring(7);
            if ("trending".equals(f)) {
                model.screen = WorldModel.Screen.HOME;
                loadShelf("Trending on YouTube", true);
            } else if ("music".equals(f)) {
                search("lofi music");
            } else {
                search("4k nature");
            }
            return;
        }
        if (key.startsWith("nav:")) {
            nav(key.substring(4));
            return;
        }
        if (key.startsWith("set:")) {
            int idx = Integer.parseInt(key.substring(4));
            // Left third of the row decreases, right third increases.
            stepSetting(idx, 1);
            return;
        }
        if (key.startsWith("btn:")) {
            button(key.substring(4));
            return;
        }

        switch (key) {
            case "search":
                // No keyboard in VR by design: cycle through curated searches instead,
                // which is faster than typing with your eyes.
                cycleSearch();
                break;
            case "settings":
                openSettings();
                break;
            case "back":
                back();
                break;
            case "reset":
                prefs.resetOptics();
                loadPrefsIntoModel();
                openSettings();
                break;
            case "nextpage":
                if (videos.size() > tileCount()) {
                    page = (page + 1) % Math.max(1,
                            (videos.size() + tileCount() - 1) / tileCount());
                    rebuildTiles(page);
                    model.shelfTitle = model.query;
                    push();
                }
                break;
            // ---- player console ----
            case "play":
                togglePlay();
                break;
            case "back10":
                seek(-prefs.seekStepMs());
                break;
            case "fwd10":
                seek(prefs.seekStepMs());
                break;
            case "vol-":
                nudgeVolume(-0.08f);
                break;
            case "vol+":
                nudgeVolume(0.08f);
                break;
            case "quality":
                prefs.setMaxHeight(prefs.maxHeight() == 720 ? 1080
                        : prefs.maxHeight() == 1080 ? 480 : 720);
                say("Max quality " + prefs.maxHeight() + "p · reloads next video");
                break;
            case "recenter":
                tracker.recenter();
                say("Recentred");
                break;
            case "exit":
                goHome();
                break;
            default:
                break;
        }
    }

    private void nav(String which) {
        switch (which) {
            case "home":
                goHome();
                break;
            case "browse":
                model.screen = WorldModel.Screen.BROWSE;
                if (videos.isEmpty()) {
                    loadShelf("Trending on YouTube", true);
                } else {
                    rebuildTiles(page);
                    push();
                }
                break;
            case "search":
                cycleSearch();
                break;
            case "settings":
                openSettings();
                break;
            case "recenter":
                tracker.recenter();
                say("Recentred");
                break;
            default:
                break;
        }
    }

    private final String[] quickSearches = {
            "cinematic 4k", "lofi hip hop", "nature documentary", "space 4k",
            "music video", "movie trailer"
    };
    private int quickSearchIndex;

    private void cycleSearch() {
        String q = quickSearches[quickSearchIndex % quickSearches.length];
        quickSearchIndex++;
        search(q);
    }

    private void button(String which) {
        if ("play".equals(which)) {
            togglePlay();
            return;
        }
        activate(which);
    }

    private void togglePlay() {
        if (player == null) {
            return;
        }
        if (player.isPlaying()) {
            player.pause();
            say("Paused");
        } else {
            player.play();
            say("");
        }
        model.hudAlpha = 1f;
        hudShownUntilMs = System.currentTimeMillis() + HUD_IDLE_MS;
    }

    private void seek(long deltaMs) {
        if (player == null) {
            return;
        }
        long dur = player.getDuration();
        long target = Math.max(0L, player.getCurrentPosition() + deltaMs);
        if (dur > 1000L) {
            target = Math.min(target, dur - 500L);
        }
        player.seekTo(target);
        say((deltaMs > 0 ? "+" : "-") + Math.abs(deltaMs / 1000) + "s");
    }

    private void seekToFraction(float f) {
        if (player == null || player.getDuration() <= 0) {
            return;
        }
        player.seekTo((long) (player.getDuration() * Math.max(0f, Math.min(1f, f))));
    }

    private void nudgeVolume(float delta) {
        volume = clamp(volume + delta, 0f, 1f);
        if (player != null) {
            player.setVolume(volume);
        }
        prefs.setVolume(volume);
        say("Volume " + Math.round(volume * 100) + "%");
    }

    private void goHome() {
        if (player != null) {
            player.stop();
        }
        model.screen = WorldModel.Screen.HOME;
        model.hasVideo = false;
        model.playing = false;
        model.hudAlpha = 1f;
        if (videos.isEmpty()) {
            loadShelf("Trending on YouTube", true);
        } else {
            rebuildTiles(0);
            push();
        }
    }

    /** @return true when the back press was consumed */
    public boolean back() {
        haptic();
        switch (model.screen) {
            case PLAYER:
                goHome();
                return true;
            case SETTINGS:
                model.screen = WorldModel.Screen.HOME;
                rebuildTiles(0);
                push();
                return true;
            case BROWSE:
                model.screen = WorldModel.Screen.HOME;
                push();
                return true;
            default:
                return false;
        }
    }

    private void say(String message) {
        toast = message == null ? "" : message;
        toastUntil = System.currentTimeMillis() + 2600;
        model.toast = toast;
        model.toastUntil = toastUntil;
    }

    private void haptic() {
        if (prefs.haptics()) {
            surface.performHapticFeedback(
                    android.view.HapticFeedbackConstants.VIRTUAL_KEY);
        }
    }

    // --------------------------------------------------------------- input

    @Override
    public void onTap(float x, float y) {
        if (!model.hoverKey.isEmpty()) {
            activate(model.hoverKey);
        } else if (model.screen == WorldModel.Screen.PLAYER && prefs.tapToPause()) {
            togglePlay();
        } else if (model.screen == WorldModel.Screen.PLAYER) {
            model.hudAlpha = 1f;
            hudShownUntilMs = System.currentTimeMillis() + HUD_IDLE_MS;
            push();
        }
    }

    @Override
    public void onDoubleTap() {
        if (model.screen == WorldModel.Screen.PLAYER) {
            togglePlay();
        }
    }

    @Override
    public void onLongPress() {
        tracker.recenter();
        say("Recentred");
    }

    @Override
    public void onSwipe(float dx, float dy) {
        if (model.screen == WorldModel.Screen.PLAYER) {
            if (Math.abs(dx) > Math.abs(dy)) {
                seek(dx > 0 ? prefs.seekStepMs() : -prefs.seekStepMs());
            } else {
                nudgeVolume(dy > 0 ? -0.10f : 0.10f);
            }
        }
    }

    @Override
    public void onScrubStart() {
        scrubbing = true;
        model.hudAlpha = 1f;
        hudShownUntilMs = System.currentTimeMillis() + HUD_IDLE_MS;
    }

    @Override
    public void onScrub(float fraction) {
        if (scrubbing) {
            // Preview without seeking every pixel: only commit on release.
            model.positionMs = (long) (model.durationMs * fraction);
            push();
        }
    }

    @Override
    public void onScrubEnd(float fraction) {
        scrubbing = false;
        seekToFraction(fraction);
    }
}
