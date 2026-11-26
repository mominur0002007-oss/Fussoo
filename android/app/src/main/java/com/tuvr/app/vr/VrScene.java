package com.tuvr.app.vr;

import java.util.List;

/**
 * A VR screen: home launcher, cinema, gallery grid, browser or calibration.
 *
 * The renderer owns the GL context, the environments, the panel plumbing and the
 * lens distortion pass. A scene only contributes its own content and panels.
 */
public interface VrScene {

    String title();

    /** Called on the GL thread after the GL context is available. */
    void onCreated(VrRenderer renderer);

    /** Preferences changed; rebuild anything that depends on them. */
    void onSettingsChanged(VrRenderer renderer);

    /** Called when the activity resumes (and after a paused GL context). */
    void onResumed(VrRenderer renderer);

    /** Per-frame update, called on the GL thread. */
    void update(VrRenderer renderer, float dtSeconds);

    /** Draws scene content for one eye. The view/projection are already set. */
    void drawEye(VrRenderer renderer, int eye, float[] viewMatrix, float[] projMatrix);

    /** Fills the list of interactive panels for this frame. */
    void collectPanels(VrRenderer renderer, List<Panel> out);

    /** A panel was selected by gaze or touch. Called on the main thread. */
    void onPanelSelected(Panel panel, VrRenderer renderer);

    /** Release GL resources owned by the scene. Called on the GL thread. */
    void release();
}
