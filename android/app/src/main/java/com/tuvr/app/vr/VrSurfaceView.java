package com.tuvr.app.vr;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.PowerManager;

/**
 * GLSurfaceView configured for VR: ES 2.0 context, preserved EGL context across
 * pauses (fast resume on the T606), continuous rendering, and thermal status
 * forwarding so the renderer can drop resolution when the phone gets warm.
 */
public class VrSurfaceView extends GLSurfaceView {

    private final VrRenderer renderer;
    private PowerManager.OnThermalStatusChangedListener thermalListener;

    public VrSurfaceView(Context context, VrRenderer renderer) {
        super(context);
        this.renderer = renderer;
        setEGLContextClientVersion(2);
        setEGLConfigChooser(8, 8, 8, 0, 16, 0);
        setPreserveEGLContextOnPause(true);
        setRenderer(renderer);
        setRenderMode(RENDERMODE_CONTINUOUSLY);
        setFocusable(true);
        setFocusableInTouchMode(true);
        registerThermalListener();
    }

    private void registerThermalListener() {
        if (Build.VERSION.SDK_INT < 29) {
            return;
        }
        try {
            PowerManager powerManager = (PowerManager) getContext()
                    .getSystemService(Context.POWER_SERVICE);
            if (powerManager == null) {
                return;
            }
            thermalListener = status -> renderer.setThermalStatus(status);
            powerManager.addThermalStatusListener(getContext().getMainExecutor(),
                    thermalListener);
        } catch (Exception ignored) {
        }
    }

    public void unregisterThermal() {
        if (Build.VERSION.SDK_INT < 29 || thermalListener == null) {
            return;
        }
        try {
            PowerManager powerManager = (PowerManager) getContext()
                    .getSystemService(Context.POWER_SERVICE);
            if (powerManager != null) {
                powerManager.removeThermalStatusListener(thermalListener);
            }
        } catch (Exception ignored) {
        }
        thermalListener = null;
    }
}
