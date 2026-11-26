package com.tuvr.app.ui;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.tuvr.app.R;
import com.tuvr.app.browser.Bookmarks;
import com.tuvr.app.core.YoutubeLinks;

import java.util.List;

/** Phone-mode WebView browser with a dark start page and local bookmarks. */
public class BrowserActivity extends AppCompatActivity {

    private static final String START_PAGE =
            "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'>"
                    + "<style>body{background:#04060b;color:#e9f2ff;font-family:sans-serif;"
                    + "padding:32px 24px}h1{font-size:26px;letter-spacing:.08em;color:#4de0ff;"
                    + "margin:0 0 4px}p{color:#9aa7bd;font-size:14px;line-height:1.5}"
                    + "ul{list-style:none;padding:0;margin-top:28px}"
                    + "li{margin:12px 0;padding:16px;border:1px solid #1f2b3d;border-radius:14px;"
                    + "background:#0a1018}a{color:#e9f2ff;text-decoration:none;font-size:15px}"
                    + "small{color:#66748c;font-size:11px}</style></head><body>"
                    + "<h1>TU VR BROWSER</h1>"
                    + "<p>Type an address above. Pages render on a flat virtual screen in VR.</p>"
                    + "<ul>"
                    + "<li><a href='https://duckduckgo.com'>DuckDuckGo — private search</a></li>"
                    + "<li><a href='https://www.youtube.com'>YouTube</a><br>"
                    + "<small>Playback inside VR varies by codec and page</small></li>"
                    + "<li><a href='https://archive.org/details/movies'>Internet Archive — free movies</a></li>"
                    + "<li><a href='https://www.w3schools.com/html/html5_video.asp'>HTML5 video test page</a></li>"
                    + "</ul></body></html>";

    private WebView webView;
    private EditText urlBar;
    private ProgressBar progress;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_browser);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        webView = findViewById(R.id.webview);
        urlBar = findViewById(R.id.edit_url);
        progress = findViewById(R.id.progress);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        webView.setBackgroundColor(0xFF04060B);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view,
                                                    android.webkit.WebResourceRequest request) {
                String target = request.getUrl().toString();
                // A WebView is a poor YouTube player: hand video links to the app.
                if (request.isForMainFrame() && YoutubeLinks.videoId(target) != null
                        && YoutubeLinks.openInApp(view.getContext(), target)) {
                    return true;
                }
                return false;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                urlBar.setText(url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                urlBar.setText(url);
                progress.setVisibility(View.GONE);
            }

            @Override
            public void onReceivedError(WebView view, android.webkit.WebResourceRequest request,
                                        android.webkit.WebResourceError error) {
                Toast.makeText(BrowserActivity.this, R.string.browser_page_error,
                        Toast.LENGTH_SHORT).show();
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progress.setVisibility(newProgress < 100 ? View.VISIBLE : View.GONE);
                progress.setProgress(newProgress);
            }
        });

        urlBar.setOnEditorActionListener((v, actionId, event) -> {
            boolean enter = actionId == EditorInfo.IME_ACTION_GO
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER);
            if (enter) {
                load(urlBar.getText().toString());
                hideKeyboard();
                return true;
            }
            return false;
        });

        findViewById(R.id.btn_back).setOnClickListener(v -> {
            if (webView.canGoBack()) {
                webView.goBack();
            } else {
                finish();
            }
        });
        findViewById(R.id.btn_forward).setOnClickListener(v -> {
            if (webView.canGoForward()) {
                webView.goForward();
            }
        });
        findViewById(R.id.btn_reload).setOnClickListener(v -> webView.reload());
        findViewById(R.id.btn_bookmark).setOnClickListener(v -> showBookmarks());
        findViewById(R.id.btn_vr).setOnClickListener(v -> openVr());

        String url = getIntent().getStringExtra(Intents.EXTRA_URL);
        if (url == null) {
            webView.loadDataWithBaseURL("about:blank", START_PAGE, "text/html", "utf-8", null);
        } else {
            load(url);
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(urlBar.getWindowToken(), 0);
        }
        urlBar.clearFocus();
    }

    private void load(String input) {
        String target = normalize(input);
        if (target != null) {
            webView.loadUrl(target);
        }
    }

    private static String normalize(String input) {
        if (input == null) {
            return null;
        }
        String value = input.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (!value.contains(" ") && (value.contains(".") || value.startsWith("http"))) {
            if (!value.startsWith("http://") && !value.startsWith("https://")) {
                return "https://" + value;
            }
            return value;
        }
        return "https://duckduckgo.com/?q=" + android.net.Uri.encode(value);
    }

    private void showBookmarks() {
        List<Bookmarks.Entry> entries = Bookmarks.list(this);
        String[] labels = new String[entries.size() + 1];
        labels[0] = getString(R.string.browser_add_bookmark) + " — " + webView.getTitle();
        for (int i = 0; i < entries.size(); i++) {
            labels[i + 1] = entries.get(i).title + "\n" + entries.get(i).url;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.browser_bookmarks)
                .setItems(labels, (d, which) -> {
                    if (which == 0) {
                        String url = webView.getUrl();
                        if (TextUtils.isEmpty(url)) {
                            return;
                        }
                        String title = webView.getTitle();
                        Bookmarks.add(this, title == null ? url : title, url);
                        Toast.makeText(this, R.string.action_save, Toast.LENGTH_SHORT).show();
                    } else {
                        Bookmarks.Entry entry = entries.get(which - 1);
                        webView.loadUrl(entry.url);
                    }
                })
                .setNegativeButton(R.string.action_close, null)
                .show();
    }

    private void openVr() {
        String url = webView.getUrl();
        Intent intent = new Intent(this, VrBrowserActivity.class);
        intent.putExtra(Intents.EXTRA_URL, url == null ? "" : url);
        startActivity(intent);
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (webView != null) {
            webView.loadUrl("about:blank");
            webView.destroy();
        }
    }
}
