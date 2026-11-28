package com.fussoo.vr.gl;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;

import com.fussoo.vr.core.Tracker;

/** GLSurfaceView wired for the theatre: ES 2.0, continuous rendering, no depth conflicts. */
public final class VrSurface extends GLSurfaceView {

    private final VideoTex video = new VideoTex();
    private VrRenderer renderer;

    public VrSurface(Context context) {
        super(context);
    }

    public VrSurface(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void boot(Tracker tracker) {
        setEGLContextClientVersion(2);
        setPreserveEGLContextOnPause(true);
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

    /** Frames per second cap implemented with a render-mode style nudge. */
    public void capFps(int cap) {
        // GLSurfaceView has no fps cap API; battery saver drops to on-demand rendering
        // only when nothing is moving, which is handled by the activity instead.
        if (renderer == null) {
            return;
        }
        setRenderMode(cap <= 0 ? RENDERMODE_WHEN_DIRTY : RENDERMODE_CONTINUOUSLY);
    }
}
