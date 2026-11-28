package com.fussoo.vr.gl;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.view.SurfaceHolder;

import com.fussoo.vr.core.Tracker;

/**
 * Wired GLSurfaceView for the theatre.
 *
 * <p>Two ordering rules matter and are handled here rather than in the activity:</p>
 * <ol>
 *   <li>{@link #setEGLContextClientVersion(int)} must be called before the surface is
 *       created, so ES 2.0 is requested in the very first configuration.</li>
 *   <li>{@link #setRenderer} must be called once the holder exists, so the callback is
 *       created on the GL thread and never races the UI thread.</li>
 * </ol>
 */
public final class VrSurface extends GLSurfaceView implements SurfaceHolder.Callback {

    /** Called once, when it is safe to attach the renderer. */
    public interface Ready {
        void go();
    }

    private final VideoTex video = new VideoTex();
    private VrRenderer renderer;
    private Ready ready;
    private boolean booted;

    public VrSurface(Context context) {
        super(context);
        init();
    }

    public VrSurface(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setEGLContextClientVersion(2);
        setPreserveEGLContextOnPause(true);
        setFocusable(true);
        setFocusableInTouchMode(true);
        getHolder().addCallback(this);
    }

    public void onSurfaceReady(Ready r) {
        ready = r;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (!booted) {
            booted = true;
            if (ready != null) {
                ready.go();
            }
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
    }

    public void boot(Tracker tracker) {
        renderer = new VrRenderer(video, tracker);
        setRenderer(renderer);
        setRenderMode(RENDERMODE_CONTINUOUSLY);
    }

    public VrRenderer renderer() {
        return renderer;
    }

    public VideoTex video() {
        return video;
    }
}
