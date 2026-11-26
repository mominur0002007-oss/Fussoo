package com.tuvr.app.vr;

/** Navigation callbacks a VR scene can use without knowing about activities. */
public interface VrHost {

    void openVrHome();

    void openVrCinema(String uri, boolean autoPlay);

    void openVrGallery();

    void openVrApps();

    void openVrBrowser(String url);

    void openSettings();

    void openDiagnostics();

    void openCalibration();

    void recenter();

    void showWhisper(String message);

    /** 0..1 window brightness used by the cinema controls. */
    void setScreenBrightness(float fraction);

    float screenBrightness();

    void exitVr();

    /** Exits the current VR screen (back navigation inside VR). */
    void leaveScene();
}
