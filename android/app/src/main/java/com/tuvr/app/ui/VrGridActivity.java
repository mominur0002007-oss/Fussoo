package com.tuvr.app.ui;

import android.os.Bundle;

import androidx.annotation.Nullable;

import com.tuvr.app.vr.VrActivity;
import com.tuvr.app.vr.VrScene;
import com.tuvr.app.vr.scene.GridScene;

/** Immersive grid of videos or apps, depending on the launch extras. */
public class VrGridActivity extends VrActivity {

    private int mode;
    private String folder;
    private boolean favoritesOnly;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        mode = Intents.GRID_MODE_APPS.equals(getIntent().getStringExtra(Intents.EXTRA_MODE))
                ? GridScene.MODE_APPS : GridScene.MODE_MEDIA;
        folder = getIntent().getStringExtra(Intents.EXTRA_FOLDER);
        favoritesOnly = getIntent().getBooleanExtra(Intents.EXTRA_FAVORITES, false);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected VrScene createScene() {
        return new GridScene(this, this, mode, folder, favoritesOnly);
    }
}
