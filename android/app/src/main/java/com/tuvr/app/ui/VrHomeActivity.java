package com.tuvr.app.ui;

import com.tuvr.app.vr.VrActivity;
import com.tuvr.app.vr.VrScene;
import com.tuvr.app.vr.scene.HomeScene;

/** Immersive VR home environment with floating panels. */
public class VrHomeActivity extends VrActivity {

    @Override
    protected VrScene createScene() {
        return new HomeScene(this, this);
    }
}
