package com.tuvr.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.tuvr.app.R;
import com.tuvr.app.core.AppSettings;
import com.tuvr.app.core.Formats;
import com.tuvr.app.media.MediaStateDb;
import com.tuvr.app.media.VideoFormat;
import com.tuvr.app.media.VideoLibrary;
import com.tuvr.app.player.VideoSession;

import java.util.ArrayList;
import java.util.List;

/**
 * Phone-mode video player: 2D playback with full transport controls, track and
 * subtitle selection, playback speed, and a one-tap hand-off into the VR cinema.
 */
public class PlayerActivity extends AppCompatActivity implements VideoSession.Listener {

    private static final float[] SPEEDS = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f};

    private SurfaceView surfaceView;
    private VideoSession session;
    private VideoFormat format = new VideoFormat();
    private String uri;
    private String displayName;
    private long pendingSeekOnReady = -1L;

    private View controls;
    private View topBar;
    private ImageView playButton;
    private ImageView favoriteButton;
    private SeekBar seekBar;
    private TextView positionText;
    private TextView durationText;
    private TextView titleText;
    private TextView subtitleText;
    private TextView speedText;
    private ProgressBar buffering;
    private TextView status;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean userSeeking;
    private boolean controlsVisible = true;

    private final Runnable hideControls = () -> {
        if (session != null && session.isPlaying()) {
            setControlsVisible(false);
        }
    };

    private final Runnable progressTick = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            handler.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        uri = getIntent().getStringExtra(Intents.EXTRA_URI);
        displayName = getIntent().getStringExtra(Intents.EXTRA_NAME);
        if (uri == null) {
            finish();
            return;
        }
        if (displayName == null) {
            displayName = Formats.displayNameFromUri(uri);
        }

        AppSettings settings = AppSettings.get(this);
        format = VideoFormat.defaults(this);
        int[] stored = MediaStateDb.get(this).storedFormat(uri);
        if (stored != null) {
            format.projection = stored[0];
            format.stereo = stored[1];
        }

        surfaceView = findViewById(R.id.surface);
        controls = findViewById(R.id.controls);
        topBar = findViewById(R.id.top_bar);
        playButton = findViewById(R.id.btn_play);
        favoriteButton = findViewById(R.id.btn_favorite);
        seekBar = findViewById(R.id.seek);
        positionText = findViewById(R.id.txt_position);
        durationText = findViewById(R.id.txt_duration);
        titleText = findViewById(R.id.txt_title);
        subtitleText = findViewById(R.id.txt_subtitle);
        speedText = findViewById(R.id.btn_speed);
        buffering = findViewById(R.id.progress_buffering);
        status = findViewById(R.id.txt_status);

        titleText.setText(displayName);
        speedText.setText(String.format(java.util.Locale.US, "%.2f×", settings.playbackSpeed())
                .replace(".00×", ".0×"));
        subtitleText.setTextSize(settings.subtitleSizeSp());

        session = new VideoSession(this);
        session.setListener(this);

        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(@NonNull SurfaceHolder holder) {
                session.setSurface(holder.getSurface());
            }

            @Override
            public void surfaceChanged(@NonNull SurfaceHolder holder, int f, int w, int h) {
                session.setSurface(holder.getSurface());
            }

            @Override
            public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
                session.setSurface(null);
            }
        });

        bindControls();

        String savedUri = uri;
        long resume = settings.resumePlayback()
                ? MediaStateDb.get(this).resumePosition(savedUri) : 0L;
        if (resume > 15000L) {
            askResume(resume);
        } else {
            startPlayback(0, true);
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                savePosition();
                finish();
            }
        });

        applyFavoriteIcon();
        handler.post(progressTick);
    }

    private void askResume(long resumeMs) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.player_resume_title)
                .setMessage(getString(R.string.player_resume_message, Formats.duration(resumeMs)))
                .setPositiveButton(R.string.player_resume_continue, (d, w) -> startPlayback(resumeMs, true))
                .setNegativeButton(R.string.player_resume_restart, (d, w) -> startPlayback(0, true))
                .setCancelable(false)
                .show();
    }

    private void startPlayback(long positionMs, boolean autoPlay) {
        pendingSeekOnReady = positionMs;
        session.prepare(uri, autoPlay);
        session.setSurface(surfaceView.getHolder().getSurface());
        session.setSpeed(AppSettings.get(this).playbackSpeed());
        MediaStateDb.get(this).recordPlay(uri);
    }

    private void bindControls() {
        findViewById(R.id.btn_close).setOnClickListener(v -> {
            savePosition();
            finish();
        });
        playButton.setOnClickListener(v -> {
            session.togglePlayPause();
            updatePlayIcon();
            scheduleHide();
        });
        findViewById(R.id.btn_rew).setOnClickListener(v -> {
            session.seekTo(session.position() - 10000L);
            updateProgress();
        });
        findViewById(R.id.btn_fwd).setOnClickListener(v -> {
            session.seekTo(session.position() + 10000L);
            updateProgress();
        });
        speedText.setOnClickListener(v -> showSpeedDialog());
        findViewById(R.id.btn_audio).setOnClickListener(v -> showAudioDialog());
        findViewById(R.id.btn_subtitles).setOnClickListener(v -> showSubtitleDialog());
        findViewById(R.id.btn_projection).setOnClickListener(v -> showProjectionDialog());
        findViewById(R.id.btn_vr).setOnClickListener(v -> openVrCinema());
        favoriteButton.setOnClickListener(v -> {
            boolean favorite = MediaStateDb.get(this).isFavorite(uri);
            MediaStateDb.get(this).setFavorite(uri, !favorite);
            applyFavoriteIcon();
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) {
                    positionText.setText(Formats.duration(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
                userSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                userSeeking = false;
                session.seekTo(bar.getProgress());
            }
        });

        surfaceView.setOnClickListener(v -> setControlsVisible(!controlsVisible));
        controls.setOnClickListener(v -> scheduleHide());
        scheduleHide();
    }

    private void applyFavoriteIcon() {
        boolean favorite = MediaStateDb.get(this).isFavorite(uri);
        favoriteButton.setImageResource(favorite ? R.drawable.ic_star_filled : R.drawable.ic_star);
    }

    private void setControlsVisible(boolean visible) {
        controlsVisible = visible;
        controls.setVisibility(visible ? View.VISIBLE : View.GONE);
        topBar.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible) {
            scheduleHide();
        }
    }

    private void scheduleHide() {
        handler.removeCallbacks(hideControls);
        handler.postDelayed(hideControls, 4000);
    }

    private void updatePlayIcon() {
        boolean playing = session.isPlaying();
        playButton.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
    }

    private void updateProgress() {
        if (session == null) {
            return;
        }
        long duration = session.duration();
        long position = session.position();
        if (duration > 0) {
            seekBar.setMax((int) Math.min(Integer.MAX_VALUE, duration));
            if (!userSeeking) {
                seekBar.setProgress((int) Math.min(Integer.MAX_VALUE, position));
            }
            durationText.setText(Formats.duration(duration));
        }
        positionText.setText(Formats.duration(position));
        updatePlayIcon();
    }

    private void showSpeedDialog() {
        String[] labels = new String[SPEEDS.length];
        for (int i = 0; i < SPEEDS.length; i++) {
            labels[i] = String.format(java.util.Locale.US, "%.2f×", SPEEDS[i]);
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.player_speed)
                .setItems(labels, (d, which) -> {
                    session.setSpeed(SPEEDS[which]);
                    speedText.setText(labels[which]);
                })
                .show();
    }

    private void showAudioDialog() {
        List<VideoSession.TrackChoice> tracks = session.audioTracks();
        if (tracks.isEmpty()) {
            Toast.makeText(this, R.string.player_track_none, Toast.LENGTH_SHORT).show();
            return;
        }
        List<String> labels = new ArrayList<>();
        for (VideoSession.TrackChoice track : tracks) {
            labels.add(track.label);
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.player_audio_track)
                .setItems(labels.toArray(new String[0]), (d, which) ->
                        session.selectAudio(tracks.get(which)))
                .show();
    }

    private void showSubtitleDialog() {
        List<VideoSession.TrackChoice> tracks = session.subtitleTracks();
        List<String> labels = new ArrayList<>();
        labels.add(getString(R.string.player_off_subtitles));
        for (VideoSession.TrackChoice track : tracks) {
            labels.add(track.label);
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.player_subtitles)
                .setItems(labels.toArray(new String[0]), (d, which) -> {
                    if (which == 0) {
                        session.selectSubtitle(null);
                        subtitleText.setVisibility(View.GONE);
                    } else {
                        session.selectSubtitle(tracks.get(which - 1));
                    }
                })
                .show();
    }

    private void showProjectionDialog() {
        String[] labels = new String[]{
                getString(R.string.proj_flat) + " · " + getString(R.string.player_stereo_mono),
                getString(R.string.proj_flat) + " · " + getString(R.string.player_stereo_sbs),
                getString(R.string.proj_flat) + " · " + getString(R.string.player_stereo_tab),
                getString(R.string.proj_180) + " · " + getString(R.string.player_stereo_mono),
                getString(R.string.proj_180) + " · " + getString(R.string.player_stereo_sbs),
                getString(R.string.proj_360) + " · " + getString(R.string.player_stereo_mono),
                getString(R.string.proj_360) + " · " + getString(R.string.player_stereo_tab),
        };
        int[] projections = {
                VideoFormat.PROJ_FLAT, VideoFormat.PROJ_FLAT, VideoFormat.PROJ_FLAT,
                VideoFormat.PROJ_VR180, VideoFormat.PROJ_VR180,
                VideoFormat.PROJ_VR360, VideoFormat.PROJ_VR360
        };
        int[] stereos = {
                VideoFormat.STEREO_MONO, VideoFormat.STEREO_SBS, VideoFormat.STEREO_TAB,
                VideoFormat.STEREO_MONO, VideoFormat.STEREO_SBS,
                VideoFormat.STEREO_MONO, VideoFormat.STEREO_TAB
        };
        int checked = 0;
        for (int i = 0; i < projections.length; i++) {
            if (projections[i] == format.projection && stereos[i] == format.stereo) {
                checked = i;
                break;
            }
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.player_projection)
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    format.projection = projections[which];
                    format.stereo = stereos[which];
                    MediaStateDb.get(this).savePosition(uri, session.position(),
                            session.duration(), format.projection, format.stereo);
                    Toast.makeText(this, format.describe(), Toast.LENGTH_SHORT).show();
                    d.dismiss();
                })
                .show();
    }

    private void openVrCinema() {
        savePosition();
        session.pause();
        Intent intent = new Intent(this, CinemaActivity.class);
        intent.putExtra(Intents.EXTRA_URI, uri);
        intent.putExtra(Intents.EXTRA_NAME, displayName);
        intent.putExtra(Intents.EXTRA_POSITION, session.position());
        intent.putExtra(Intents.EXTRA_PROJECTION, format.projection);
        intent.putExtra(Intents.EXTRA_STEREO, format.stereo);
        intent.putExtra(Intents.EXTRA_AUTOPLAY, true);
        startActivity(intent);
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
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (session != null) {
            session.pause();
        }
        savePosition();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (session != null) {
            session.setListener(null);
            session.release();
        }
    }

    /* VideoSession.Listener ------------------------------------------------ */

    @Override
    public void onReady(long durationMs) {
        buffering.setVisibility(View.GONE);
        durationText.setText(Formats.duration(durationMs));
        seekBar.setMax((int) Math.min(Integer.MAX_VALUE, durationMs));
        if (pendingSeekOnReady >= 0) {
            session.seekTo(pendingSeekOnReady);
            pendingSeekOnReady = -1L;
        }
        updatePlayIcon();
    }

    @Override
    public void onVideoSize(int width, int height, float pixelRatio) {
        // The SurfaceView keeps the video centred; aspect handling is automatic.
    }

    @Override
    public void onError(String message) {
        buffering.setVisibility(View.GONE);
        status.setVisibility(View.VISIBLE);
        status.setText(getString(R.string.player_error, message));
    }

    @Override
    public void onCues(String text) {
        if (text == null || text.isEmpty()) {
            subtitleText.setVisibility(View.GONE);
            return;
        }
        subtitleText.setText(text);
        subtitleText.setVisibility(View.VISIBLE);
    }

    @Override
    public void onPlayingChanged(boolean playing) {
        updatePlayIcon();
    }

    @Override
    public void onBufferingChanged(boolean isBuffering) {
        buffering.setVisibility(isBuffering ? View.VISIBLE : View.GONE);
    }
}
