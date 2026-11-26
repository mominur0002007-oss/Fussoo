package com.tuvr.app.ui;

import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.tuvr.app.vr.VrActivity;
import com.tuvr.app.vr.VrScene;
import com.tuvr.app.vr.scene.GridScene;

/** Immersive grid of videos or apps, depending on the launch extras. */
public class VrGridActivity extends VrActivity {

    private int mode;
    private String folder;
    private boolean favoritesOnly;
    private boolean searchMode;
    private GridScene gridScene;
    private EditText searchField;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        String requested = getIntent().getStringExtra(Intents.EXTRA_MODE);
        searchMode = Intents.GRID_MODE_SEARCH.equals(requested);
        mode = Intents.GRID_MODE_APPS.equals(requested)
                ? GridScene.MODE_APPS : GridScene.MODE_MEDIA;
        folder = getIntent().getStringExtra(Intents.EXTRA_FOLDER);
        favoritesOnly = getIntent().getBooleanExtra(Intents.EXTRA_FAVORITES, false);
        super.onCreate(savedInstanceState);
        if (searchMode) {
            buildSearchBar(savedInstanceState);
        }
    }

    @Override
    protected VrScene createScene() {
        gridScene = new GridScene(this, this, mode, folder, favoritesOnly);
        return gridScene;
    }

    /**
     * The headset has no keyboard, so search in VR is typed on the phone screen
     * with the app still in stereo: a plain text field floats over the top of
     * the view while the filtered grid updates behind it.
     */
    private void buildSearchBar(@Nullable Bundle savedInstanceState) {
        FrameLayout root = findViewById(android.R.id.content);
        if (root == null) {
            return;
        }
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(0xE6101620);

        searchField = new EditText(this);
        searchField.setHint("Search videos and apps");
        searchField.setHintTextColor(0xFF6E7C93);
        searchField.setTextColor(Color.WHITE);
        searchField.setSingleLine(true);
        searchField.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchField.setTextSize(16f);
        searchField.setPadding(28, 18, 28, 18);
        LinearLayout.LayoutParams fieldParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        bar.addView(searchField, fieldParams);

        TextView close = new TextView(this);
        close.setText("OK");
        close.setTextColor(0xFF5AD2F0);
        close.setTextSize(16f);
        close.setPadding(28, 18, 28, 18);
        // Only the keyboard is dismissed: the bar stays so search stays one tap away.
        close.setOnClickListener(v -> hideKeyboard());
        bar.addView(close);

        FrameLayout.LayoutParams barParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        barParams.gravity = Gravity.TOP;
        barParams.setMargins(24, 24, 24, 0);
        root.addView(bar, barParams);

        if (savedInstanceState != null) {
            searchField.setText(savedInstanceState.getString("vr_query", ""));
        }
        searchField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (gridScene != null) {
                    gridScene.setQuery(s.toString());
                }
            }
        });
        searchField.setOnEditorActionListener((v, actionId, event) -> {
            hideKeyboard();
            return true;
        });
        searchField.postDelayed(() -> {
            searchField.requestFocus();
            InputMethodManager imm = getSystemService(InputMethodManager.class);
            if (imm != null) {
                imm.showSoftInput(searchField, InputMethodManager.SHOW_IMPLICIT);
            }
        }, 350);
    }

    private void hideKeyboard() {
        InputMethodManager imm = getSystemService(InputMethodManager.class);
        if (imm != null && searchField != null) {
            imm.hideSoftInputFromWindow(searchField.getWindowToken(), 0);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (searchField != null) {
            outState.putString("vr_query", searchField.getText().toString());
        }
    }
}
