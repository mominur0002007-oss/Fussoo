package com.fussoo.vr.gl;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;

import com.fussoo.vr.core.Tracker;
import com.fussoo.vr.core.WorldModel;

/**
 * The theatre's rendering surface.
 *
 * <p>This class exists because {@link GLSurfaceView} has two ordering rules that are easy
 * to break and produce a black screen when broken:</p>
 *
 * <ol>
 *   <li>{@code setEGLContextClientVersion(2)} must run before the surface is created.</li>
 *   <li>{@code setRenderer()} must be called <em>after</em> the view is attached and the
 *       surface exists. Calling it earlier, or from a SurfaceHolder callback that fires
 *       after GLSurfaceView has already started its own thread, leaves you with a black
 *       view and no error anywhere.</li>
 * </ol>
 *
 * <p>So the renderer is attached from {@code onAttachedToWindow}, which is the one point
 * where the view is guaranteed to have a window but GLSurfaceView has not yet created its
 * GL thread.</p>
 */
public final class VrSurface extends GLSurfaceView {

    private final VideoTex video = new VideoTex();
    private final Tracker tracker;
    private VrRenderer renderer;
    private boolean attached;
    private VrRenderer.Callback callback;

    public VrSurface(Context context, Tracker tracker) {
        super(context);
        this.tracker = tracker;
        init();
    }

    public VrSurface(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.tracker = null;
        init();
    }

    private void init() {
        setEGLContextClientVersion(2);
        setEGLConfigChooser(8, 8, 8, 0, 16, 0);
        setPreserveEGLContextOnPause(true);
        setFocusable(true);
        setFocusableInTouchMode(true);
        setKeepScreenOn(true);
    }

    /**
     * Attaches the renderer exactly once, at the only moment GLSurfaceView allows it
     * reliably. Safe to call from {@code onCreate} before the view is attached.
     */
    public void prepare() {
        if (tracker == null || renderer != null) {
            return;
        }
        renderer = new VrRenderer(video, tracker);
        if (callback != null) {
            renderer.setCallback(callback);
        }
        setRenderer(renderer);
        setRenderMode(RENDERMODE_CONTINUOUSLY);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!attached) {
            attached = true;
            prepare();
        }
    }

    /** Set before the view is attached; the renderer picks it up on creation. */
    public void setRendererCallback(VrRenderer.Callback cb) {
        callback = cb;
        if (renderer != null) {
            renderer.setCallback(cb);
        }
    }

    public VrRenderer renderer() {
        return renderer;
    }

    public VideoTex video() {
        return video;
    }

    /** Publishing model updates is safe before the renderer exists; they are dropped. */
    public void push(WorldModel model) {
        VrRenderer r = renderer;
        if (r != null) {
            r.push(model);
        }
    }

    public void onResume() {
        super.onResume();
        if (renderer != null) {
            setRenderMode(RENDERMODE_CONTINUOUSLY);
        }
    }

    public void onPause() {
        super.onPause();
    }

    public Scene scene() {
        return renderer == null ? new Scene() : renderer.scene();
    }
}
