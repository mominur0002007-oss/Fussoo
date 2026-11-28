package com.fussoo.vr.ui;

import android.content.Context;
import android.view.MotionEvent;

import com.fussoo.vr.core.Tracker;
import com.fussoo.vr.core.WorldModel;
import com.fussoo.vr.gl.Scene;
import com.fussoo.vr.gl.VrSurface;

/**
 * The GL surface plus a small gesture vocabulary.
 *
 * <p>In a headset you cannot see your finger, so gestures are deliberately coarse and
 * forgiving: tap, double tap, long press, and a swipe for seek/volume. Everything else is
 * done with the gaze.</p>
 */
public final class WorldSurface extends VrSurface {

    /** Gesture sink. Implemented by the controller. */
    public interface Listener {
        void onTap(float x, float y);

        void onDoubleTap();

        void onLongPress();

        void onSwipe(float dx, float dy);

        void onScrubStart();

        void onScrub(float fraction);

        void onScrubEnd(float fraction);
    }

    private Listener listener;
    private float downX, downY;
    private long downAt;
    private boolean dragging;

    public WorldSurface(Context context, Tracker tracker) {
        super(context, tracker);
    }

    public void setGestureListener(Listener l) {
        this.listener = l;
    }

    @Override
    protected void onDetachedFromWindow() {
        // Deletes textures, programs and the FBO on the GL thread before the context
        // dies; skipping this leaks a full atlas plus the render target per rotation.
        VrRenderer r = renderer();
        if (r != null) {
            queueEvent(r::release);
        }
        super.onDetachedFromWindow();
    }

    public void qualityChanged() {
        // Drop and rebuild the off-screen buffers at the new scale.
        requestRender();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (listener == null) {
            return super.onTouchEvent(e);
        }
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = e.getY();
                downAt = System.currentTimeMillis();
                dragging = false;
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dx = e.getX() - downX;
                float dy = e.getY() - downY;
                if (!dragging && Math.hypot(dx, dy) > dp(24)) {
                    dragging = true;
                }
                return true;
            }
            case MotionEvent.ACTION_UP: {
                long held = System.currentTimeMillis() - downAt;
                float dx = e.getX() - downX;
                float dy = e.getY() - downY;
                if (!dragging && held < 250) {
                    listener.onTap(e.getX(), e.getY());
                } else if (!dragging && held >= 600) {
                    listener.onLongPress();
                } else if (dragging) {
                    listener.onSwipe(dx, dy);
                }
                return true;
            }
            default:
                return super.onTouchEvent(e);
        }
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    /** Called by the renderer when the model changes; keeps the caller honest. */
    public void publish(WorldModel model) {
        push(model);
        requestRender();
    }

    public Scene sceneOf() {
        return scene();
    }
}
