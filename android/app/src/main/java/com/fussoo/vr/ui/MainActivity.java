package com.fussoo.vr.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.fussoo.vr.core.Boot;
import com.fussoo.vr.core.Formats;
import com.fussoo.vr.core.VrPrefs;
import com.fussoo.vr.net.Thumbs;
import com.fussoo.vr.net.Yt;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The phone-side app: search YouTube, browse trending, and hand a video to the theatre.
 *
 * <p>Built in code rather than XML so the whole layout is one readable file, and so the
 * dark "glass" look stays identical across the Tecno's older Android build.</p>
 */
public final class MainActivity extends Activity {

    private VrPrefs prefs;
    private Thumbs thumbs;
    private final ExecutorService pool = Executors.newFixedThreadPool(2);
    private final Handler main = new Handler(Looper.getMainLooper());

    private LinearLayout results;
    private TextView status;
    private ProgressBar spinner;
    private EditText search;
    private LinearLayout chips;
    private final List<Yt.Video> current = new ArrayList<>();
    private int requestSeq;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Boot.once(this);
        prefs = new VrPrefs(this);
        thumbs = new Thumbs();
        setContentView(build());
        handleIntent(getIntent());
        loadTrending();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) {
            return;
        }
        String data = intent.getDataString();
        String id = Yt.id(data);
        if (id != null) {
            openTheatre(id, null);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        thumbs.shutdown();
        pool.shutdownNow();
    }

    // ------------------------------------------------------------------ UI

    private View build() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundResource(com.fussoo.vr.R.drawable.bg_phone);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int p = Palette.dp(this, 20);
        col.setPadding(p, Palette.dp(this, 18), p, Palette.dp(this, 40));
        scroll.addView(col, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Header: wordmark + settings.
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView logo = new TextView(this);
        logo.setText("FUSSOO");
        logo.setTextSize(26);
        logo.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
        logo.setTextColor(Palette.TEXT);
        logo.setLetterSpacing(0.24f);
        header.addView(logo, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView gear = pill("Settings");
        gear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });
        header.addView(gear);
        col.addView(header);

        TextView tag = new TextView(this);
        tag.setText("IMAX-style YouTube theatre for your VR box");
        tag.setTextSize(14);
        tag.setTextColor(Palette.MUTED);
        tag.setPadding(0, Palette.dp(this, 2), 0, Palette.dp(this, 20));
        col.addView(tag);

        // Continue watching card.
        String last = prefs.lastVideo();
        if (last != null) {
            col.addView(continueCard(last));
        }

        // Search field.
        FrameLayout searchWrap = new FrameLayout(this);
        searchWrap.setBackground(Palette.round(Palette.CARD, 0x22FFFFFF,
                Palette.dp(this, 20)));
        search = new EditText(this);
        search.setSingleLine(true);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setHint("Search YouTube");
        search.setHintTextColor(0xFF6C7688);
        search.setTextColor(Palette.TEXT);
        search.setTextSize(16);
        search.setBackgroundColor(Color.TRANSPARENT);
        int sp = Palette.dp(this, 18);
        search.setPadding(sp, Palette.dp(this, 14), sp, Palette.dp(this, 14));
        searchWrap.addView(search, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        search.setOnEditorActionListener((v, actionId, event) -> {
            runSearch(search.getText().toString());
            return true;
        });
        col.addView(searchWrap, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        ((LinearLayout.LayoutParams) searchWrap.getLayoutParams()).topMargin =
                Palette.dp(this, 4);

        // Quick chips.
        chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        String[] quick = {"Trending", "Music", "Lo-fi", "Nature 4K", "Movies", "Tech"};
        HorizontalScrollView chipScroll = new HorizontalScrollView(this);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chipScroll.setClipToPadding(false);
        chipScroll.setPadding(0, Palette.dp(this, 12), 0, Palette.dp(this, 6));
        for (final String q : quick) {
            TextView c = pill(q);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = Palette.dp(this, 8);
            c.setLayoutParams(lp);
            c.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if ("Trending".equals(q)) {
                        loadTrending();
                    } else {
                        search.setText(q);
                        runSearch(q);
                    }
                }
            });
            chips.addView(c);
        }
        chipScroll.addView(chips);
        col.addView(chipScroll);

        // Progress line + status text.
        spinner = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        spinner.setIndeterminate(true);
        spinner.setProgressTintList(ColorStateList.valueOf(Palette.ACCENT));
        spinner.setVisibility(View.GONE);
        LinearLayout.LayoutParams spinLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Palette.dp(this, 3));
        spinLp.topMargin = Palette.dp(this, 10);
        col.addView(spinner, spinLp);

        status = new TextView(this);
        status.setTextColor(Palette.MUTED);
        status.setTextSize(13);
        status.setPadding(0, Palette.dp(this, 10), 0, Palette.dp(this, 10));
        col.addView(status);

        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        col.addView(results);

        root.addView(scroll);
        return root;
    }

    private View continueCard(final String videoId) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(Palette.round(Palette.CARD_HI, 0x33FF3B30,
                Palette.dp(this, 20)));
        int p = Palette.dp(this, 16);
        card.setPadding(p, p, p, p);
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView label = new TextView(this);
        label.setText("CONTINUE IN VR");
        label.setTextSize(11);
        label.setLetterSpacing(0.18f);
        label.setTextColor(Palette.ACCENT);
        texts.addView(label);
        TextView t = new TextView(this);
        t.setText("Resume where you stopped");
        t.setTextSize(16);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(Palette.TEXT);
        texts.addView(t);
        card.addView(texts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView go = pill("Enter");
        card.addView(go);
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openTheatre(videoId, null);
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Palette.dp(this, 16);
        card.setLayoutParams(lp);
        return card;
    }

    private TextView pill(String text) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(13);
        v.setTextColor(Palette.TEXT);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setGravity(Gravity.CENTER);
        v.setBackground(Palette.round(0x1FFFFFFF, 0x2EFFFFFF, Palette.dp(this, 18)));
        int px = Palette.dp(this, 14);
        int py = Palette.dp(this, 8);
        v.setPadding(px, py, px, py);
        return v;
    }

    // -------------------------------------------------------------- data

    private void loadTrending() {
        status.setText("Loading trending videos…");
        spinner.setVisibility(View.VISIBLE);
        results.removeAllViews();
        final int seq = ++requestSeq;
        pool.execute(new Runnable() {
            @Override
            public void run() {
                String error = null;
                List<Yt.Video> list = new ArrayList<>();
                try {
                    list = Yt.trending(24);
                } catch (Throwable t) {
                    error = describe(t);
                }
                final List<Yt.Video> out = list;
                final String err = error;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (seq != requestSeq) {
                            return;
                        }
                        finishLoad(out, err, "Trending on YouTube");
                    }
                });
            }
        });
    }

    private void runSearch(final String query) {
        if (query == null || query.trim().isEmpty()) {
            return;
        }
        status.setText("Searching \"" + query.trim() + "\"…");
        spinner.setVisibility(View.VISIBLE);
        results.removeAllViews();
        final int seq = ++requestSeq;
        pool.execute(new Runnable() {
            @Override
            public void run() {
                String error = null;
                List<Yt.Video> list = new ArrayList<>();
                try {
                    list = Yt.search(query, 24);
                } catch (Throwable t) {
                    error = describe(t);
                }
                final List<Yt.Video> out = list;
                final String err = error;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (seq != requestSeq) {
                            return;
                        }
                        finishLoad(out, err, "Results for \"" + query.trim() + "\"");
                    }
                });
            }
        });
    }

    private String describe(Throwable t) {
        String m = t.getMessage();
        if (m == null) {
            m = t.getClass().getSimpleName();
        }
        return m;
    }

    private void finishLoad(List<Yt.Video> list, String error, String heading) {
        spinner.setVisibility(View.GONE);
        current.clear();
        current.addAll(list);
        results.removeAllViews();
        if (error != null) {
            status.setText("Could not load YouTube: " + error);
            TextView retry = pill("Try again");
            retry.setOnClickListener(v -> loadTrending());
            results.addView(retry);
            return;
        }
        if (list.isEmpty()) {
            status.setText("Nothing found.");
            return;
        }
        status.setText(heading + " · " + list.size() + " videos");

        for (final Yt.Video v : list) {
            results.addView(row(v));
        }
    }

    private View row(final Yt.Video v) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setBackground(Palette.round(Palette.CARD, 0x14FFFFFF,
                Palette.dp(this, 18)));
        int p = Palette.dp(this, 8);
        card.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Palette.dp(this, 8);
        card.setLayoutParams(lp);

        FrameLayout thumbBox = new FrameLayout(this);
        int tw = Palette.dp(this, 132);
        int th = (int) (tw * 0.5625f);
        ImageView img = new ImageView(this);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        img.setBackground(Palette.round(0xFF161B26, 0, Palette.dp(this, 12)));
        thumbBox.addView(img, new FrameLayout.LayoutParams(tw, th));

        TextView dur = new TextView(this);
        dur.setTextColor(Color.WHITE);
        dur.setTextSize(11);
        dur.setTypeface(Typeface.DEFAULT_BOLD);
        dur.setBackground(Palette.round(0xCC000000, 0, Palette.dp(this, 6)));
        dur.setPadding(Palette.dp(this, 5), 0, Palette.dp(this, 5), Palette.dp(this, 1));
        dur.setText(v.durationMs > 0 ? Formats.duration(v.durationMs) : "LIVE");
        FrameLayout.LayoutParams durLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.END);
        durLp.rightMargin = Palette.dp(this, 6);
        durLp.bottomMargin = Palette.dp(this, 6);
        thumbBox.addView(dur, durLp);
        card.addView(thumbBox);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(Palette.dp(this, 12), Palette.dp(this, 2), 0, 0);
        TextView title = new TextView(this);
        title.setText(v.title);
        title.setTextColor(Palette.TEXT);
        title.setTextSize(15);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setMaxLines(2);
        texts.addView(title);
        TextView meta = new TextView(this);
        meta.setText(v.uploader + (v.views > 0 ? " · " + Formats.views(v.views) : ""));
        meta.setTextColor(Palette.MUTED);
        meta.setTextSize(12);
        meta.setPadding(0, Palette.dp(this, 4), 0, 0);
        texts.addView(meta);
        card.addView(texts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (v.thumbnail != null) {
            thumbs.load(v.id, v.thumbnail, 400, new Thumbs.Callback() {
                @Override
                public void onBitmap(String key, final Bitmap bmp) {
                    img.setImageBitmap(bmp);
                }

                @Override
                public void onFailed(String key) {
                }
            });
        }

        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openTheatre(v.id, v.title);
            }
        });
        return card;
    }

    private void openTheatre(String videoId, String title) {
        Intent i = new Intent(this, TheatreActivity.class);
        i.putExtra(TheatreActivity.EXTRA_VIDEO_ID, videoId);
        if (title != null) {
            i.putExtra(TheatreActivity.EXTRA_TITLE, title);
        }
        startActivity(i);
    }

    @SuppressWarnings("unused")
    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    @SuppressWarnings("unused")
    private TextWatcher noop() {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        };
    }
}
