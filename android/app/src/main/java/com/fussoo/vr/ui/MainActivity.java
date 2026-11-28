package com.fussoo.vr.ui;

import android.app.Activity;
import android.os.Bundle;
import android.view.WindowManager;

import com.fussoo.vr.core.Boot;
import com.fussoo.vr.core.Tracker;

/**
 * A thin host for the theatre.
 *
 * <p>There is no phone-side UI any more, by design. Everything — browsing, searching,
 * playing, settings — happens inside the headset, the way iQIYI VR works. This activity
 * only owns the surface, the tracker and the controller.</p>
 *
 * <p>The phone screen still works without a headset: the stereo image is simply two copies,
 * and you can tap to select whatever the reticle is on.</p>
 */
public final class MainActivity extends Activity {

    private Tracker tracker;
    private WorldSurface surface;
    private WorldController controller;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Boot.once(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        Boot.immersive(this);

        tracker = new Tracker(this);
        surface = new WorldSurface(this, tracker);
        setContentView(surface);

        controller = new WorldController(this, surface, tracker);
        surface.setGestureListener(controller);
        // The GL context does not exist yet: the renderer tells us when the OES texture
        // is ready, and only then can the player be handed a Surface.
        surface.setRendererCallback(new com.fussoo.vr.gl.VrRenderer.Callback() {
            @Override
            public void onGlReady() {
                controller.onGlReady();
            }

            @Override
            public void onFps(float fps) {
                // No on-screen counter: this is a cinema, not a benchmark.
            }
        });

        String shared = Boot.videoIdFrom(getIntent());
        controller.start(shared);
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (controller != null) {
            String id = Boot.videoIdFrom(intent);
            if (id != null) {
                controller.playVideoId(id);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Boot.immersive(this);
        if (surface != null) {
            surface.onResume();
        }
        tracker.start(this);
        if (controller != null) {
            controller.reloadPreferences();
            controller.resume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (controller != null) {
            controller.pause();
        }
        tracker.stop();
        if (surface != null) {
            surface.onPause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (controller != null) {
            controller.destroy();
        }
        if (surface != null) {
            surface.onDestroy();
        }
    }

    @Override
    public void onBackPressed() {
        if (controller != null && controller.back()) {
            return;
        }
        super.onBackPressed();
    }
}
