package com.tuvr.app.vr;

import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.tuvr.app.R;
import com.tuvr.app.core.AppSettings;
import com.tuvr.app.core.Immersive;
import com.tuvr.app.ui.BrowserActivity;
import com.tuvr.app.ui.CinemaActivity;
import com.tuvr.app.ui.DiagnosticsActivity;
import com.tuvr.app.ui.GalleryActivity;
import com.tuvr.app.ui.Intents;
import com.tuvr.app.ui.SettingsActivity;
import com.tuvr.app.ui.VrBrowserActivity;
import com.tuvr.app.ui.VrHomeActivity;
import com.tuvr.app.ui.VrGridActivity;

/**
 * Base class for every stereoscopic screen.
 *
 * Responsibilities: GL surface + renderer lifecycle, head tracking, immersive
 * mode, a small phone-mode overlay for exiting VR, and the touch fallback for
 * devices without usable rotation sensors.
 */
public abstract class VrActivity extends AppCompatActivity
        implements VrRenderer.Listener, VrHost {

    protected HeadTracker tracker;
    protected VrRenderer renderer;
    protected VrSurfaceView surfaceView;

    private FrameLayout root;
    private View overlayBar;
    private TextView statsView;
    private boolean overlayVisible;

    private float touchDownX;
    private float touchDownY;
    private boolean dragging;
    private long touchDownTime;

    /* ------------------------------------------------------------------ */

    protected abstract VrScene createScene();

    /** Called when the user asks to leave this VR screen. */
    protected void onLeaveScene() {
        finish();
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppSettings settings = AppSettings.get(this);

        tracker = new HeadTracker(this);
        renderer = new VrRenderer(this, tracker);
        renderer.setListener(this);

        surfaceView = new VrSurfaceView(this, renderer);

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.addView(surfaceView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        buildOverlay();
        setContentView(root);

        if (settings.keepAwake()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        Immersive.enter(this);

        tracker.setDisplayRotation(displayRotation());
        try {
            renderer.setScene(createScene());
        } catch (Throwable t) {
            com.tuvr.app.core.Problems.record("createScene:" + getClass().getSimpleName(), t);
            renderer.setScene(null);
            android.widget.Toast.makeText(this,
                    "This VR screen failed to start. See Diagnostics.",
                    android.widget.Toast.LENGTH_LONG).show();
        }

        surfaceView.setOnTouchListener((v, event) -> handleTouch(event));
    }

    private void buildOverlay() {
        final float density = getResources().getDisplayMetrics().density;

        LinearLayout corner = new LinearLayout(this);
        corner.setOrientation(LinearLayout.HORIZONTAL);
        FrameLayout.LayoutParams cornerParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        cornerParams.setMargins(0, (int) (10 * density), (int) (10 * density), 0);
        corner.setLayoutParams(cornerParams);

        TextView menuButton = new TextView(this);
        menuButton.setText("⋮");
        menuButton.setTextSize(20f);
        menuButton.setTextColor(0xFFE9F2FF);
        menuButton.setGravity(Gravity.CENTER);
        menuButton.setAlpha(0.45f);
        int size = (int) (44 * density);
        menuButton.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        menuButton.setOnClickListener(v -> toggleOverlay());
        corner.addView(menuButton);
        root.addView(corner);

        overlayBar = buildBar();
        FrameLayout.LayoutParams barParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        ((View) overlayBar).setLayoutParams(barParams);
        overlayBar.setVisibility(View.GONE);
        root.addView(overlayBar);

        statsView = new TextView(this);
        statsView.setTextColor(0xFF4DE0FF);
        statsView.setTextSize(11f);
        statsView.setPadding((int) (10 * density), (int) (8 * density), 0, 0);
        FrameLayout.LayoutParams statsParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START);
        statsView.setLayoutParams(statsParams);
        statsView.setVisibility(View.GONE);
        root.addView(statsView);
    }

    private View buildBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        bar.setBackgroundColor(0xCC070A10);
        int pad = (int) (6 * getResources().getDisplayMetrics().density);
        bar.setPadding(pad, pad, pad, pad);

        bar.addView(barButton(R.drawable.ic_exit, R.string.vr_exit, v -> exitVr()));
        bar.addView(barButton(R.drawable.ic_recenter, R.string.vr_recenter, v -> recenter()));
        bar.addView(barButton(R.drawable.ic_settings, R.string.vr_settings, v -> openSettings()));
        bar.addView(barButton(R.drawable.ic_info, R.string.diag_title, v -> openDiagnostics()));
        bar.addView(barButton(R.drawable.ic_vr, R.string.vr_mono_preview, v -> toggleMono()));
        return bar;
    }

    private View barButton(int iconRes, int labelRes, View.OnClickListener click) {
        float density = getResources().getDisplayMetrics().density;
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER);
        column.setPadding((int) (12 * density), (int) (4 * density),
                (int) (12 * density), (int) (4 * density));
        column.setOnClickListener(click);

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(0xFFE9F2FF);
        int iconSize = (int) (22 * density);
        icon.setLayoutParams(new LinearLayout.LayoutParams(iconSize, iconSize));

        TextView label = new TextView(this);
        label.setText(labelRes);
        label.setTextSize(9f);
        label.setTextColor(0xFF9AA7BD);
        label.setGravity(Gravity.CENTER);

        column.addView(icon);
        column.addView(label);
        return column;
    }

    private void toggleOverlay() {
        overlayVisible = !overlayVisible;
        overlayBar.setVisibility(overlayVisible ? View.VISIBLE : View.GONE);
    }

    /** Root container so subclasses can add their own overlays. */
    protected FrameLayout contentRoot() {
        return root;
    }

    protected void toggleMono() {
        renderer.setMonoPreview(!renderer.isMonoPreview());
        showWhisper(renderer.isMonoPreview() ? "Mono preview" : "Stereoscopic");
    }

    private int displayRotation() {
        try {
            return getWindowManager().getDefaultDisplay().getRotation();
        } catch (Exception e) {
            return 0;
        }
    }

    /* ------------------------------------------------------------------ */
    /* lifecycle                                                          */
    /* ------------------------------------------------------------------ */

    @Override
    protected void onResume() {
        super.onResume();
        renderer.reloadSettings();
        renderer.setPaused(false);
        surfaceView.onResume();
        tracker.setDisplayRotation(displayRotation());
        tracker.start();
        renderer.setMonoPreview(false);
        renderer.armSelection();
        if (AppSettings.get(this).recenterOnStart()) {
            tracker.recenter();
        }
        statsView.setVisibility(AppSettings.get(this).showFps()
                ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (!isChangingConfigurations()) {
            renderer.setPaused(true);
            surfaceView.onPause();
            tracker.stop();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        surfaceView.unregisterThermal();
        if (isFinishing() && surfaceView != null) {
            surfaceView.queueEvent(() -> renderer.releaseGl());
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            Immersive.enter(this);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        tracker.setDisplayRotation(displayRotation());
    }

    /* ------------------------------------------------------------------ */
    /* touch                                                              */
    /* ------------------------------------------------------------------ */

    private boolean handleTouch(MotionEvent event) {
        float density = getResources().getDisplayMetrics().density;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                touchDownX = event.getX();
                touchDownY = event.getY();
                touchDownTime = System.currentTimeMillis();
                dragging = false;
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dx = event.getX() - touchDownX;
                float dy = event.getY() - touchDownY;
                if (!dragging && Math.hypot(dx, dy) > 18 * density / 3f) {
                    dragging = true;
                }
                if (dragging && !tracker.hasRecentSample()) {
                    // No usable sensor: drag to look around.
                    touchDownX = event.getX();
                    touchDownY = event.getY();
                    tracker.addTouchDelta(dx * 0.14f, dy * 0.14f);
                }
                return true;
            }
            case MotionEvent.ACTION_UP: {
                boolean quick = System.currentTimeMillis() - touchDownTime < 400;
                if (!dragging && quick && surfaceView.getWidth() > 0) {
                    float ndcX = 2f * event.getX() / surfaceView.getWidth() - 1f;
                    float ndcY = 1f - 2f * event.getY() / surfaceView.getHeight();
                    renderer.onTouch(ndcX, ndcY);
                }
                return true;
            }
            default:
                return false;
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    /* ------------------------------------------------------------------ */
    /* VrRenderer.Listener                                                */
    /* ------------------------------------------------------------------ */

    @Override
    public void onPanelSelected(Panel panel) {
    }

    @Override
    public void onStats(float fps, float frameMs, float renderScale) {
        if (AppSettings.get(this).showFps()) {
            statsView.setVisibility(View.VISIBLE);
            statsView.setText(String.format(java.util.Locale.US,
                    "%.0f fps · %.1f ms · %.2fx", fps, frameMs, renderScale));
        } else {
            statsView.setVisibility(View.GONE);
        }
    }

    @Override
    public void onTrackingWarning(int state, String source) {
        if ("none".equals(source)) {
            showWhisper(getString(R.string.vr_no_sensor));
        } else {
            showWhisper(getString(R.string.vr_sensor_warning));
        }
    }

    /* ------------------------------------------------------------------ */
    /* VrHost                                                             */
    /* ------------------------------------------------------------------ */

    @Override
    public void openVrHome() {
        startActivity(new Intent(this, VrHomeActivity.class));
        finish();
    }

    @Override
    public void openVrCinema(String uri, boolean autoPlay) {
        Intent intent = new Intent(this, CinemaActivity.class);
        if (uri != null) {
            intent.putExtra(Intents.EXTRA_URI, uri);
            intent.putExtra(Intents.EXTRA_AUTOPLAY, autoPlay);
        }
        startActivity(intent);
    }

    @Override
    public void openVrGallery() {
        Intent intent = new Intent(this, VrGridActivity.class);
        intent.putExtra(Intents.EXTRA_MODE, Intents.GRID_MODE_MEDIA);
        startActivity(intent);
    }

    @Override
    @Override
    public void openVrSearch() {
        Intent intent = new Intent(this, VrGridActivity.class);
        intent.putExtra(Intents.EXTRA_MODE, Intents.GRID_MODE_SEARCH);
        startActivity(intent);
    }

    @Override
    public void openVrApps() {
        Intent intent = new Intent(this, VrGridActivity.class);
        intent.putExtra(Intents.EXTRA_MODE, Intents.GRID_MODE_APPS);
        startActivity(intent);
    }

    @Override
    public void openVrBrowser(String url) {
        Intent intent = new Intent(this, VrBrowserActivity.class);
        if (url != null) {
            intent.putExtra(Intents.EXTRA_URL, url);
        }
        startActivity(intent);
    }

    @Override
    public void openSettings() {
        startActivity(Intents.settings(this));
    }

    @Override
    public void openDiagnostics() {
        startActivity(Intents.diagnostics(this));
    }

    @Override
    public void openCalibration() {
        startActivity(Intents.calibration(this));
    }

    @Override
    public void recenter() {
        tracker.recenter();
        showWhisper(getString(R.string.vr_recenter_done));
    }

    @Override
    public void showWhisper(String message) {
        renderer.showWhisper(message, 2600);
    }

    @Override
    public void setScreenBrightness(float fraction) {
        Immersive.setBrightness(this, fraction);
    }

    @Override
    public float screenBrightness() {
        WindowManager.LayoutParams params = getWindow().getAttributes();
        if (params.screenBrightness > 0f) {
            return params.screenBrightness;
        }
        return Immersive.systemBrightness(this);
    }

    @Override
    public void exitVr() {
        finish();
    }

    @Override
    public void leaveScene() {
        onLeaveScene();
    }

    /* phone-mode entries kept for the overlay on non-VR screens */
    protected Intent galleryIntent() {
        return new Intent(this, GalleryActivity.class);
    }

    protected Intent browserIntent() {
        return new Intent(this, BrowserActivity.class);
    }

    protected Intent settingsIntent() {
        return new Intent(this, SettingsActivity.class);
    }

    protected Intent diagnosticsIntent() {
        return new Intent(this, DiagnosticsActivity.class);
    }
}
