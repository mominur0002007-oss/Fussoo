package com.tuvr.app.player;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.Surface;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.Tracks;
import androidx.media3.common.VideoSize;
import androidx.media3.common.text.Cue;
import androidx.media3.common.text.CueGroup;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;

import java.util.ArrayList;
import java.util.List;

/**
 * Thin wrapper around ExoPlayer tuned for a low-end phone:
 * hardware decoders first, decoder fallback enabled, no extension renderers.
 *
 * The same session is used by the flat phone player and by the VR renderer
 * (which simply attaches a SurfaceTexture instead of a SurfaceView).
 */
public final class VideoSession {

    public interface Listener {
        void onReady(long durationMs);

        void onVideoSize(int width, int height, float pixelRatio);

        void onError(String message);

        void onCues(String text);

        void onPlayingChanged(boolean playing);

        void onBufferingChanged(boolean buffering);
    }

    public static final class TrackChoice {
        public final String label;
        public final TrackGroup group;
        public final int trackIndex;

        TrackChoice(String label, TrackGroup group, int trackIndex) {
            this.label = label;
            this.group = group;
            this.trackIndex = trackIndex;
        }
    }

    private final Context context;
    private ExoPlayer player;
    private final Handler handler = new Handler(Looper.getMainLooper());
    @Nullable
    private Listener listener;
    private String lastCueText = "";
    private boolean prepared;
    private String mediaUri;

    public VideoSession(Context context) {
        this.context = context.getApplicationContext();
    }

    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    public void prepare(String uri, boolean autoPlay) {
        release();
        mediaUri = uri;
        DefaultRenderersFactory renderers = new DefaultRenderersFactory(context)
                .setEnableDecoderFallback(true)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF);
        DefaultTrackSelector trackSelector = new DefaultTrackSelector(context);
        trackSelector.setParameters(trackSelector.buildUponParameters()
                .setPreferredTextLanguage("en")
                .setSelectUndeterminedTextLanguage(true));
        player = new ExoPlayer.Builder(context, renderers)
                .setTrackSelector(trackSelector)
                .setHandleAudioBecomingNoisy(true)
                .build();
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) {
                    prepared = true;
                    if (listener != null) {
                        listener.onReady(player.getDuration() == C.TIME_UNSET ? 0 : player.getDuration());
                    }
                } else if (state == Player.STATE_BUFFERING && listener != null) {
                    listener.onBufferingChanged(true);
                } else if (state == Player.STATE_ENDED && listener != null) {
                    listener.onPlayingChanged(false);
                }
                if (state != Player.STATE_BUFFERING && listener != null) {
                    listener.onBufferingChanged(false);
                }
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                if (listener != null) {
                    listener.onPlayingChanged(isPlaying);
                }
            }

            @Override
            public void onVideoSizeChanged(VideoSize videoSize) {
                if (listener != null) {
                    listener.onVideoSize(videoSize.width, videoSize.height,
                            videoSize.pixelWidthHeightRatio == 0 ? 1f : videoSize.pixelWidthHeightRatio);
                }
            }

            @Override
            public void onCues(CueGroup cueGroup) {
                StringBuilder sb = new StringBuilder();
                for (Cue cue : cueGroup.cues) {
                    if (cue.text != null) {
                        if (sb.length() > 0) {
                            sb.append('\n');
                        }
                        sb.append(cue.text);
                    }
                }
                lastCueText = sb.toString();
                if (listener != null) {
                    listener.onCues(lastCueText);
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                if (listener != null) {
                    listener.onError(describe(error));
                }
            }
        });
        MediaItem item = MediaItem.fromUri(Uri.parse(uri));
        player.setMediaItem(item);
        player.setVolume(1f);
        player.prepare();
        if (autoPlay) {
            player.setPlayWhenReady(true);
        }
    }

    private static String describe(PlaybackException error) {
        int code = error.errorCode;
        switch (code) {
            case PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND:
                return "file not found";
            case PlaybackException.ERROR_CODE_IO_NO_PERMISSION:
                return "no read permission";
            case PlaybackException.ERROR_CODE_DECODING_FAILED:
                return "decoder failed";
            case PlaybackException.ERROR_CODE_DECODER_INIT_FAILED:
                return "decoder init failed";
            case PlaybackException.ERROR_CODE_UNSPECIFIED:
                return "unspecified error";
            default:
                return "code " + code;
        }
    }

    public void setSurface(@Nullable android.view.Surface surface) {
        if (player != null) {
            player.setVideoSurface(surface);
        }
    }

    /** Used by the VR renderer: the player renders into the surface of an external texture. */
    public void setVideoSurface(Surface surface) {
        if (player == null || surface == null) {
            return;
        }
        player.setVideoSurface(surface);
    }

    public void play() {
        if (player != null) {
            player.setPlayWhenReady(true);
        }
    }

    public void pause() {
        if (player != null) {
            player.setPlayWhenReady(false);
        }
    }

    public void togglePlayPause() {
        if (player == null) {
            return;
        }
        if (player.isPlaying()) {
            pause();
        } else {
            play();
        }
    }

    public boolean isPlaying() {
        return player != null && player.isPlaying();
    }

    public long position() {
        return player == null ? 0L : player.getCurrentPosition();
    }

    public long duration() {
        if (player == null) {
            return 0L;
        }
        long d = player.getDuration();
        return d == C.TIME_UNSET ? 0L : d;
    }

    public void seekTo(long positionMs) {
        if (player != null) {
            player.seekTo(Math.max(0L, positionMs));
        }
    }

    public void setSpeed(float speed) {
        if (player != null) {
            player.setPlaybackSpeed(speed);
        }
    }

    public void setVolume(float volume) {
        if (player != null) {
            player.setVolume(Math.max(0f, Math.min(1f, volume)));
        }
    }

    public int videoWidth() {
        VideoSize size = player == null ? VideoSize.UNKNOWN : player.getVideoSize();
        return size.width;
    }

    public int videoHeight() {
        VideoSize size = player == null ? VideoSize.UNKNOWN : player.getVideoSize();
        return size.height;
    }

    public String lastCueText() {
        return lastCueText;
    }

    public String mediaUri() {
        return mediaUri;
    }

    public boolean isPrepared() {
        return prepared;
    }

    public ExoPlayer raw() {
        return player;
    }

    /* ---------------- tracks ---------------- */

    public List<TrackChoice> audioTracks() {
        List<TrackChoice> out = new ArrayList<>();
        if (player == null) {
            return out;
        }
        Tracks tracks = player.getCurrentTracks();
        for (Tracks.Group group : tracks.getGroups()) {
            if (group.getType() != C.TRACK_TYPE_AUDIO) {
                continue;
            }
            for (int i = 0; i < group.length; i++) {
                if (!group.isTrackSupported(i)) {
                    continue;
                }
                String label = group.getTrackFormat(i).label;
                String lang = group.getTrackFormat(i).language;
                String name = label != null ? label : (lang != null ? lang : "Track " + (i + 1));
                out.add(new TrackChoice(name, group.getMediaTrackGroup(), i));
            }
        }
        return out;
    }

    public List<TrackChoice> subtitleTracks() {
        List<TrackChoice> out = new ArrayList<>();
        if (player == null) {
            return out;
        }
        Tracks tracks = player.getCurrentTracks();
        for (Tracks.Group group : tracks.getGroups()) {
            if (group.getType() != C.TRACK_TYPE_TEXT) {
                continue;
            }
            for (int i = 0; i < group.length; i++) {
                String label = group.getTrackFormat(i).label;
                String lang = group.getTrackFormat(i).language;
                String mime = group.getTrackFormat(i).sampleMimeType;
                String name = label != null ? label : (lang != null ? lang : (mime != null ? mime : "Subtitle"));
                out.add(new TrackChoice(name, group.getMediaTrackGroup(), i));
            }
        }
        return out;
    }

    public void selectAudio(TrackChoice choice) {
        if (player == null) {
            return;
        }
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                .setOverrideForType(new TrackSelectionOverride(choice.group, choice.trackIndex))
                .build());
    }

    public void selectSubtitle(@Nullable TrackChoice choice) {
        if (player == null) {
            return;
        }
        if (choice == null) {
            player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build());
            lastCueText = "";
        } else {
            player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .setOverrideForType(new TrackSelectionOverride(choice.group, choice.trackIndex))
                    .build());
        }
    }

    public void release() {
        prepared = false;
        if (player != null) {
            try {
                player.setVideoSurface(null);
                player.release();
            } catch (Exception ignored) {
            }
            player = null;
        }
        handler.removeCallbacksAndMessages(null);
    }
}
