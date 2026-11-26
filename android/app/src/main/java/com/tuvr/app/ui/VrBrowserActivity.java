package com.tuvr.app.ui;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import com.tuvr.app.R;
import com.tuvr.app.vr.VrActivity;
import com.tuvr.app.vr.VrScene;
import com.tuvr.app.vr.scene.BrowserScene;

/**
 * VR browser: a WebView rendered into the stereo scene through a software
 * surface copy. Reading oriented, low frame rate by design.
 */
public class VrBrowserActivity extends VrActivity implements BrowserScene.Bridge {

    private static final String START_URL = "https://duckduckgo.com";

    private WebView webView;
    private BrowserScene scene;
    private String initialUrl;
    private LinearLayout urlBar;
    private EditText urlInput;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        initialUrl = getIntent().getStringExtra(Intents.EXTRA_URL);
        if (initialUrl == null || initialUrl.isEmpty()) {
            initialUrl = START_URL;
        }
        webView = createWebView();
        super.onCreate(savedInstanceState);

        // The hidden WebView must be attached and laid out for software capture.
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                960, 540);
        webView.setLayoutParams(params);
        webView.setAlpha(0.01f);
        contentRoot().addView(webView);

        buildUrlBar();
        load(initialUrl);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private WebView createWebView() {
        WebView view = new WebView(this);
        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(false);
        settings.setDefaultTextEncodingName("utf-8");
        view.setBackgroundColor(Color.BLACK);
        // Software layer is required so View.draw(Canvas) yields real pixels.
        view.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        view.setWebViewClient(new WebViewClient());
        view.setWebChromeClient(new WebChromeClient());
        return view;
    }

    private void buildUrlBar() {
        urlBar = new LinearLayout(this);
        urlBar.setOrientation(LinearLayout.HORIZONTAL);
        urlBar.setBackgroundColor(0xE6070A10);
        urlBar.setPadding(16, 12, 16, 12);
        urlBar.setVisibility(View.GONE);

        urlInput = new EditText(this);
        urlInput.setSingleLine(true);
        urlInput.setHint(R.string.browser_url_hint);
        urlInput.setTextColor(0xFFFFFFFF);
        urlInput.setHintTextColor(0xFF66748C);
        urlInput.setBackgroundResource(R.drawable.bg_input);
        urlInput.setImeOptions(EditorInfo.IME_ACTION_GO);
        urlInput.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_URI);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        urlInput.setLayoutParams(inputParams);

        Button go = new Button(this);
        go.setText(R.string.action_open);
        go.setOnClickListener(v -> {
            load(urlInput.getText().toString());
            urlBar.setVisibility(View.GONE);
            hideKeyboard();
        });

        urlBar.addView(urlInput);
        urlBar.addView(go);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP);
        params.setMargins(80, 16, 80, 0);
        urlBar.setLayoutParams(params);
        contentRoot().addView(urlBar);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(urlInput.getWindowToken(), 0);
        }
    }

    private void load(String input) {
        if (input == null) {
            return;
        }
        String value = input.trim();
        if (value.isEmpty()) {
            return;
        }
        if (!value.contains(" ") && !value.startsWith("http")) {
            if (value.contains(".")) {
                value = "https://" + value;
            } else {
                value = "https://duckduckgo.com/?q=" + android.net.Uri.encode(value);
            }
        } else if (value.contains(" ")) {
            value = "https://duckduckgo.com/?q=" + android.net.Uri.encode(value);
        }
        webView.loadUrl(value);
    }

    @Override
    protected VrScene createScene() {
        scene = new BrowserScene(this, webView, this);
        return scene;
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (scene != null) {
            scene.setCaptureEnabled(false);
        }
        if (webView != null) {
            webView.onPause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.onResume();
        }
        if (scene != null) {
            scene.setCaptureEnabled(true);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (webView != null) {
            webView.loadUrl("about:blank");
            webView.destroy();
            webView = null;
        }
    }

    /* BrowserScene.Bridge ------------------------------------------------ */

    @Override
    public void onEditUrl() {
        runOnUiThread(() -> {
            urlBar.setVisibility(View.VISIBLE);
            urlInput.setText(webView == null ? "" : webView.getUrl());
            urlInput.requestFocus();
            InputMethodManager imm =
                    (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(urlInput, InputMethodManager.SHOW_IMPLICIT);
            }
        });
    }

    @Override
    public void onExit() {
        finish();
    }

    @Override
    public void onBackPressed() {
        // Hardware/gesture back: navigate the page if possible, otherwise leave.
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return;
        }
        super.onBackPressed();
    }

    /** In-scene Back panel: never exits the VR screen by accident. */
    @Override
    public void onBack() {
        runOnUiThread(() -> {
            if (webView != null && webView.canGoBack()) {
                webView.goBack();
            } else {
                showWhisper("No history");
            }
        });
    }

    @Override
    public void onForward() {
        runOnUiThread(() -> {
            if (webView != null && webView.canGoForward()) {
                webView.goForward();
            }
        });
    }

    @Override
    public void onReload() {
        runOnUiThread(() -> {
            if (webView != null) {
                webView.reload();
            }
        });
    }

    @Override
    public String currentUrl() {
        if (webView == null) {
            return "";
        }
        String url = webView.getUrl();
        return url == null ? "" : url;
    }
}
