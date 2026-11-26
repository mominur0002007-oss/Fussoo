package com.tuvr.app.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.tuvr.app.R;
import com.tuvr.app.core.AppSettings;
import com.tuvr.app.media.MediaStateDb;
import com.tuvr.app.media.VideoFormat;
import com.tuvr.app.media.VideoItem;
import com.tuvr.app.media.VideoLibrary;
import com.tuvr.app.player.VideoSession;
import com.tuvr.app.vr.VrActivity;
import com.tuvr.app.vr.VrScene;
import com.tuvr.app.vr.scene.CinemaScene;

/**
 * VR Cinema activity: owns the ExoPlayer session and pushes small snapshots into
 * the scene so all player access stays on the main thread.
 */
@androidx.media3.common.util.UnstableApi
public class CinemaActivity extends VrActivity implements VideoSession.Listener {

    private VideoSession session;
    private CinemaScene cinemaScene;
    private VideoFormat format = new VideoFormat();
    private String uri;
    private String displayName;
    private long startPositionMs;
    private boolean autoPlay;
    private long pendingSeekMs = -1L;
    private boolean prepared;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable progressTick = new Runnable() {
        @Override
        public void run() {
            if (session != null && cinemaScene != null) {
                cinemaScene.setPlaybackState(session.isPlaying(), session.position(),
                        session.duration());
                // Reflect the player's real speed so in-scene speed cycling sticks.
                cinemaScene.setSpeed(session.speed());
            }
            handler.postDelayed(this, 700);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // Read the intent before super.onCreate(), because super creates the scene.
        uri = getIntent().getStringExtra(Intents.EXTRA_URI);
        displayName = getIntent().getStringExtra(Intents.EXTRA_NAME);
        startPositionMs = getIntent().getLongExtra(Intents.EXTRA_POSITION, 0L);
        autoPlay = getIntent().getBooleanExtra(Intents.EXTRA_AUTOPLAY, false);

        format = VideoFormat.defaults(this);
        int projection = getIntent().getIntExtra(Intents.EXTRA_PROJECTION, -1);
        int stereo = getIntent().getIntExtra(Intents.EXTRA_STEREO, -1);
        if (projection >= 0) {
            format.projection = projection;
        }
        if (stereo >= 0) {
            format.stereo = stereo;
        }

        session = new VideoSession(this);
        session.setListener(this);
        super.onCreate(savedInstanceState);

        if (uri != null) {
            session.prepare(uri, autoPlay);
            session.setSpeed(AppSettings.get(this).playbackSpeed());
            session.setVolume(AppSettings.get(this).volumeFraction());
            MediaStateDb.get(this).recordPlay(uri);
            AppSettings.get(this).setLastVideo(uri, startPositionMs);
            if (startPositionMs <= 0L) {
                long resume = AppSettings.get(this).resumePlayback()
                        ? MediaStateDb.get(this).resumePosition(uri) : 0L;
                if (resume > 15000L) {
                    pendingSeekMs = resume;
                }
            } else {
                pendingSeekMs = startPositionMs;
            }
        } else {
            Toast.makeText(this, R.string.cinema_no_video, Toast.LENGTH_LONG).show();
        }
        handler.post(progressTick);
    }

    @Override
    protected VrScene createScene() {
        cinemaScene = new CinemaScene(this, this, uri == null ? null : session, format);
        return cinemaScene;
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (session != null) {
            session.pause();
            savePosition();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (session != null) {
            session.setListener(null);
            session.release();
            session = null;
        }
    }

    private void savePosition() {
        if (session == null || uri == null) {
            return;
        }
        long position = session.position();
        long duration = session.duration();
        if (position > 5000L && duration > 0) {
            MediaStateDb.get(this).savePosition(uri, position, duration,
                    format.projection, format.stereo);
            AppSettings.get(this).setLastVideo(uri, position);
        }
    }

    /* VideoSession.Listener ------------------------------------------------ */

    @Override
    public void onReady(long durationMs) {
        prepared = true;
        if (pendingSeekMs > 0) {
            session.seekTo(pendingSeekMs);
            pendingSeekMs = -1L;
        }
        if (cinemaScene != null) {
            cinemaScene.setPlaybackState(session.isPlaying(), session.position(), durationMs);
        }
    }

    @Override
    public void onVideoSize(int width, int height, float pixelRatio) {
        if (width <= 0 || height <= 0) {
            return;
        }
        if (cinemaScene == null) {
            return;
        }
        float aspect = format.pictureAspect(width, height);
        if (aspect <= 0.05f) {
            aspect = width / (float) Math.max(1, height);
        }
        cinemaScene.setVideoAspect(aspect);
    }

    @Override
    public void onError(String message) {
        showWhisper(getString(R.string.player_error, message));
    }

    @Override
    public void onCues(String text) {
        if (cinemaScene != null) {
            cinemaScene.setSubtitle(text);
        }
    }

    @Override
    public void onPlayingChanged(boolean playing) {
        if (cinemaScene != null) {
            cinemaScene.setPlaybackState(playing, session == null ? 0 : session.position(),
                    session == null ? 0 : session.duration());
        }
    }

    @Override
    public void onBufferingChanged(boolean buffering) {
        if (buffering && !prepared && cinemaScene != null) {
            cinemaScene.setSubtitle("");
        }
    }

    /* helpers -------------------------------------------------------------- */

    /** Resumes the last video when the user opens the cinema without a file. */
    public void loadLastVideo() {
        String last = AppSettings.get(this).lastVideoUri();
        if (last == null) {
            return;
        }
        VideoItem item = VideoLibrary.find(this, last);
        uri = last;
        displayName = item == null ? null : item.name;
        session.prepare(last, true);
    }
}
