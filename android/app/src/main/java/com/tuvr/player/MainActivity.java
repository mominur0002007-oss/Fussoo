package com.tuvr.player;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.view.Display;
import android.view.Surface;
import android.content.Context;
import android.webkit.JavascriptInterface;

/** Native Android host: SAF-backed media selection, immersive landscape cinema and low-latency fused motion sensors. */
public final class MainActivity extends Activity implements SensorEventListener {
    private static final int OPEN_MEDIA = 41;
    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;
    private SensorManager sensorManager;
    private Sensor rotationSensor;
    private final float[] rotationMatrix = new float[9];
    private final float[] remappedMatrix = new float[9];
    private final float[] orientation = new float[3];
    private boolean listening = false;
    private boolean trackingRequested = false;
    private boolean cinemaActive = false;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED, WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
        getWindow().setStatusBarColor(android.graphics.Color.rgb(9, 10, 16));
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        webView = new WebView(this);
        webView.setBackgroundColor(android.graphics.Color.rgb(9, 10, 16));
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        webView.addJavascriptInterface(new NativeBridge(), "NativeVR");
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"video/*", "image/*", "application/octet-stream"});
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, params != null && params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE);
                try { startActivityForResult(intent, OPEN_MEDIA); }
                catch (Exception ex) { fileCallback = null; callback.onReceiveValue(null); return false; }
                return true;
            }
        });
        setContentView(new FrameLayout(this) {{ addView(webView, new FrameLayout.LayoutParams(-1, -1)); }});
        if (rotationSensor == null) webView.evaluateJavascript("window.NativeVRSensorAvailable=false", null);
        webView.loadUrl("file:///android_asset/web/index.html");
    }

    private final class NativeBridge {
        @JavascriptInterface public void startSensors() {
            runOnUiThread(() -> { trackingRequested = true; startMotion(); });
        }
        @JavascriptInterface public void stopSensors() { runOnUiThread(() -> { trackingRequested = false; stopMotion(); }); }
        @JavascriptInterface public void enterCinema() { runOnUiThread(() -> {
            cinemaActive = true; setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            immersive(true);
        }); }
        @JavascriptInterface public void exitCinema() { runOnUiThread(() -> {
            trackingRequested = false; stopMotion(); cinemaActive = false; setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED); immersive(false);
        }); }
    }

    private void immersive(boolean enabled) {
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        if (enabled) flags |= View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }
    private void startMotion() { if (trackingRequested && rotationSensor != null && !listening && sensorManager != null) { sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_GAME); listening = true; } }
    private void stopMotion() { if (listening && sensorManager != null) sensorManager.unregisterListener(this); listening = false; }
    @Override public void onSensorChanged(SensorEvent event) {
        if (!listening || event.sensor.getType() != Sensor.TYPE_ROTATION_VECTOR || webView == null) return;
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
        Display display = getWindowManager().getDefaultDisplay();
        int rotation = display.getRotation();
        int x = SensorManager.AXIS_X, y = SensorManager.AXIS_Y;
        if (rotation == Surface.ROTATION_90) { x = SensorManager.AXIS_Y; y = SensorManager.AXIS_MINUS_X; }
        else if (rotation == Surface.ROTATION_180) { x = SensorManager.AXIS_MINUS_X; y = SensorManager.AXIS_MINUS_Y; }
        else if (rotation == Surface.ROTATION_270) { x = SensorManager.AXIS_MINUS_Y; y = SensorManager.AXIS_X; }
        SensorManager.remapCoordinateSystem(rotationMatrix, x, y, remappedMatrix);
        SensorManager.getOrientation(remappedMatrix, orientation);
        final double alpha = ((Math.toDegrees(orientation[0]) + 360) % 360);
        final double beta = Math.toDegrees(orientation[1]) + 90;
        final double gamma = Math.toDegrees(orientation[2]);
        runOnUiThread(() -> webView.evaluateJavascript("if(window.onNativeOrientation)window.onNativeOrientation(" + alpha + "," + beta + "," + gamma + ")", null));
    }
    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != OPEN_MEDIA) return;
        if (fileCallback == null) return;
        Uri[] results = null;
        if (resultCode == RESULT_OK && data != null) {
            if (data.getClipData() != null) {
                int count = data.getClipData().getItemCount(); results = new Uri[count];
                for (int i = 0; i < count; i++) results[i] = data.getClipData().getItemAt(i).getUri();
            } else if (data.getData() != null) results = new Uri[]{data.getData()};
        }
        fileCallback.onReceiveValue(results); fileCallback = null;
    }
    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (webView != null && event.getRepeatCount() == 0) {
            String js = null;
            if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || keyCode == KeyEvent.KEYCODE_HEADSETHOOK || keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_BUTTON_A) js = "document.getElementById('play-toggle').click()";
            else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) js = "document.getElementById('back-10').click()";
            else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) js = "document.getElementById('forward-10').click()";
            else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) js = "document.dispatchEvent(new KeyboardEvent('keydown',{key:'ArrowUp'}))";
            else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) js = "document.dispatchEvent(new KeyboardEvent('keydown',{key:'ArrowDown'}))";
            else if (keyCode == KeyEvent.KEYCODE_BUTTON_X) js = "document.getElementById('recenter-cinema').click()";
            else if (keyCode == KeyEvent.KEYCODE_BUTTON_Y) js = "document.getElementById('recenter-cinema').click()";
            if (js != null) { webView.evaluateJavascript("if(document.getElementById('cinema').classList.contains('open')){" + js + "}", null); return true; }
        }
        return super.onKeyDown(keyCode, event);
    }
    @Override public void onBackPressed() {
        if (cinemaActive && webView != null) webView.evaluateJavascript("document.getElementById('exit-cinema').click()", null);
        else super.onBackPressed();
    }
    @Override protected void onPause() { stopMotion(); if (webView != null) webView.onPause(); super.onPause(); }
    @Override protected void onResume() { super.onResume(); if (webView != null) webView.onResume(); startMotion(); }
    @Override protected void onDestroy() { stopMotion(); if (webView != null) { webView.removeJavascriptInterface("NativeVR"); webView.destroy(); } super.onDestroy(); }
}
