package com.fussoo.vr.net;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * Last-resort playback path: the real YouTube embed, rendered by the system WebView.
 *
 * <p>Extraction from YouTube is fragile by nature: Google changes their player every few
 * weeks, and a library that worked at build time can stop working at any moment. Rather
 * than showing the user a dead end, the theatre can fall back to the official embed. The
 * picture quality is lower and ads play, but video always plays, and the same stereo
 * renderer draws it.</p>
 */
public final class WebFallback {

    public interface Ready {
        void onEmbedReady(WebView view);
    }

    private WebFallback() {
    }

    @SuppressLint("SetJavaScriptEnabled")
    public static WebView create(Context ctx, String videoId, final Ready ready) {
        WebView wv = new WebView(ctx);
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        if (Build.VERSION.SDK_INT >= 17) {
            s.setUserAgentString(YtDownloader.USER_AGENT);
        }
        wv.setWebChromeClient(new WebChromeClient());
        wv.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }
        });
        String html = "<!doctype html><html><head>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<style>html,body{margin:0;background:#000;height:100%;overflow:hidden}"
                + "iframe{position:fixed;inset:0;width:100%;height:100%;border:0}"
                + "</style></head><body>"
                + "<iframe src='https://www.youtube.com/embed/" + videoId
                + "?autoplay=1&playsinline=1&rel=0&modestbranding=1&controls=1'"
                + " allow='autoplay; encrypted-media; fullscreen'"
                + " allowfullscreen></iframe></body></html>";
        wv.loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "utf-8", null);
        if (ready != null) {
            ready.onEmbedReady(wv);
        }
        return wv;
    }
}
