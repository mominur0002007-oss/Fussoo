package com.fussoo.vr.ui;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;

import com.fussoo.vr.net.WebFallback;

/**
 * Last-resort playback: the official YouTube embed, hidden behind the stereo scene but
 * still composited by the system, so its frames land in the theatre texture.
 *
 * <p>This path exists because YouTube extraction can break at any time. It is not pretty
 * and it shows ads, but the alternative is a black screen.</p>
 */
final class WorldEmbed {

    private WorldEmbed() {
    }

    static void show(final Activity activity, final WorldSurface parent, String videoId) {
        final WebView web = WebFallback.create(activity, videoId, null);
        web.setAlpha(0f);
        web.setLayoutParams(new ViewGroup.LayoutParams(2, 2));
        // A stereo surface cannot host extra children, so the WebView hangs off the
        // activity's content frame instead. It is 2x2 px and fully transparent: the
        // official player still decodes and plays audio, which is the point of the
        // fallback, while the theatre keeps drawing whatever frame it has.
        if (parent.getParent() instanceof ViewGroup) {
            ((ViewGroup) parent.getParent()).addView(web);
        } else {
            return;
        }
        // Kept in the hierarchy on purpose: a detached WebView stops rendering video.
        web.postDelayed(new Runnable() {
            @Override
            public void run() {
                web.setVisibility(View.VISIBLE);
            }
        }, 100);
    }
}
