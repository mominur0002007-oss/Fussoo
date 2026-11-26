# Architecture

TU VR is plain Java (no Kotlin, no Compose, no native C++) on top of the Android
framework, AppCompat and Media3. Everything stereoscopic is written directly against
OpenGL ES 2.0 with a small hand-rolled engine, so the whole rendering path is
inspectable in this repository.

```
android/app/src/main/
├── java/com/tuvr/app/
│   ├── TuVrApp.java            Application: installs the crash handler, applies defaults
│   ├── core/                   AppSettings, ViewerProfile, Background, Immersive, DeviceCapabilities,
│   │                           Hardware, CrashLog, Formats, ToastMessage
│   ├── media/                  VideoLibrary, VideoItem, VideoFormat, MediaStateDb, ThumbLoader
│   ├── player/                 VideoSession (Media3/ExoPlayer wrapper)
│   ├── apps/                   AppEntry, AppsRepository (launcher list, favourites, recents)
│   ├── browser/                Bookmarks
│   ├── ui/                     phone-mode activities + adapters + Intents
│   │   └── VR activities: VrHomeActivity, CinemaActivity, VrGridActivity,
│   │       VrBrowserActivity, CalibrationActivity
│   └── vr/                     the VR engine
│       ├── MathUtil, GlUtil, Mesh, MeshBuilder, EnvironmentBuilder, VideoTexture
│       ├── HeadTracker         sensor fusion, smoothing, recenter, calibration
│       ├── Panel, PanelFactory glass panels: geometry, hit testing, bitmap textures
│       ├── VrSurfaceView       GLSurfaceView + thermal listener
│       ├── VrRenderer          stereo rendering, distortion, gaze/dwell, adaptive quality
│       ├── VrActivity, VrHost  activity base class and the scene↔activity contract
│       ├── VrScene             scene interface
│       └── scene/              HomeScene, CinemaScene, GridScene, BrowserScene, CalibrationScene
├── assets/shaders/             12 GLSL files (loaded by name at runtime)
└── res/                        values, layouts, vector drawables, preference XML
```

## Rendering pipeline

Per frame (`VrRenderer.onDrawFrame`, GL thread):

1. **Frame limiter** — if the user picked 30/45/60 Hz, skip the frame early.
2. **Pending work** — swap in a new scene, re-read settings, expire the whisper panel.
3. **Tracking** — `HeadTracker.updateOutput(quat)` produces the final head orientation.
4. **Gaze** — the forward vector of the head is intersected with every interactive panel;
   the closest hit becomes the focus, a dwell timer fills, and on completion the panel's
   `Runnable` runs on the main thread.
5. **Scene update** — the scene updates animated state and its own panel bitmaps.
6. **Panel collection** — the scene contributes its panels; basis vectors are recomputed.
7. **Two eye passes** — for each eye:
   * bind the eye FBO (RGBA8888 + 16-bit depth), clear,
   * view matrix = head rotation `Rᵀ` followed by a translation of the eye offset
     (`±stereoBaseline/2` rotated into world space),
   * projection from the viewer profile FOV, aspect from the FBO, FOV widened by the
     overshoot factor,
   * environment (dome/floor/stars per environment type), then the scene's geometry
     (video screen / sphere / test pattern), then the panels, then the reticle.
8. **Composite** — the full-screen quad samples each eye texture through the lens model:
   `uv = 0.5 + (t · (1 + k₁r² + k₂r⁴)) / (2·tanHalfEye·overscan)`, with optional
   per-channel offsets for chromatic correction, drawn into the left/right half of the
   screen (or full width in mono preview). Eye-swap happens here, once for the frame.

Resolution: the eye FBOs are allocated at `surface/2 × qualityScale × adaptiveScale`,
clamped, and rebuilt only when the scale changes. `adaptiveScale` shrinks when the
measured frame time exceeds the budget and recovers slowly; the thermal listener drops
it further at Android's `THERMAL_STATUS_SEVERE`/`CRITICAL`.

## Panels

A `Panel` is a billboard: `yawDeg`, `pitchDeg`, `distanceM`, physical `widthM`/`heightM`.
`updateBasis()` builds the model matrix from the placement and a view-projection product;
`hitTest(dir)` is a ray/plane intersection plus a local-space bounds check, so gaze
selection matches what is visible.

Textures come from `Bitmap`s created by `PanelFactory` (tiles, buttons, text, progress
bars). Hand-off from the UI thread to the GL thread is reference-based:
`Panel.setContent()` stores the bitmap and sets `contentDirty`; the renderer uploads it on
the next frame and recycles the previous one afterwards. Both sides synchronise on the
panel, so a bitmap is never recycled while the GL thread reads its pixels. Scenes that
manage their own double buffer set `recycleOnReplace = false` (the browser does).

## Head tracking

`HeadTracker` picks the best available source at runtime:

| Source | Sensor | Notes |
| --- | --- | --- |
| 1 | `TYPE_ROTATION_VECTOR` | fused with magnetometer, absolute yaw |
| 2 | `TYPE_GAME_ROTATION_VECTOR` | fused without magnetometer, no magnetic drift correction |
| 3 | `TYPE_GYROSCOPE` + `TYPE_ACCELEROMETER` | own complementary filter, gyro bias removed |
| 4 | accelerometer only | pitch/roll only; yaw is not tracked (warned in the UI) |
| 5 | none | drag to look, head movement ignored (warned in the UI) |

Orientation maths, in order:

1. **Display rotation** — `q_cam = q_device ⊗ q_display`, where `q_display` is a rotation
   about Z by `-90° × displayRotation`, converting screen axes into device axes.
2. **World frame** — Android reports east-north-up (Z up); the renderer wants Y up, so the
   sample is pre-multiplied by `Rx(-90°)`. Without this the horizon would be rolled 90°.
3. **Sensitivity** — yaw and pitch angles are scaled, roll is left alone.
4. **Smoothing** — spherical interpolation towards the new sample with
   `α = clamp(1 − 0.92·smoothing, 0.08, 1)`, which keeps latency low at the default.
5. **Recenter** — stores the conjugate of the yaw-only part of the current orientation, so
   "forward" becomes the direction the user was looking at; pitch and roll are untouched.
6. **Touch offset** — a quaternion prepended for drag-to-look, used when no sensor can
   provide orientation.

Gyro bias calibration averages the gyroscope while the device is still and stores the
bias; `driftDegPerMinute()` estimates residual yaw drift for the diagnostics screen.
No positional tracking is implemented or implied anywhere in the app.

## Threading rules

| Owner | Responsibilities |
| --- | --- |
| GL thread | all GL calls, scene update/draw, panel texture uploads, gaze evaluation |
| Main thread | ExoPlayer/Media3 calls, settings writes, starting activities, panel bitmap creation from UI callbacks |
| `Background` (single pool) | MediaStore queries, package manager queries, metadata extraction |

Rules that the code follows and that new code must keep:

* Never touch an `ExoPlayer` from the GL thread — scene actions are dispatched to the
  main thread by `VrRenderer.dispatchSelection()`.
* Never call `Bitmap.recycle()` on a panel bitmap from the UI thread; use
  `Panel.setContent()` (which defers recycling) or opt out with `recycleOnReplace = false`.
* Scene callbacks (`onPanelSelected`, bridge callbacks) run on the main thread.
* Fields shared between threads are `volatile` (`Panel.content`, `contentDirty`,
  `VrRenderer.settingsDirty/monoPreview/paused`, scene playback snapshots).

## Settings and profiles

All preferences live in `SharedPreferences("tuvr_settings")` and are typed (`int`, `String`,
`boolean`) so standard `SeekBarPreference`/`ListPreference`/`SwitchPreferenceCompat` work
from `res/xml/pref_root.xml`. `AppSettings.applyDefaults()` writes the defaults once
(`defaults_applied` flag) instead of on every read, and every accessor clamps its value,
so a corrupted file can never put the renderer into an impossible state.

`ViewerProfile` is built from the stored optical values (lens separation, screen-to-lens
distance, FOV, eye offsets, distortion k1/k2, chromatic shift, IPD). Presets exist
(`shinecon`, `generic`, `cardboard`, `custom`), but the renderer always uses the stored
numbers — no optics are hardcoded in the render path.
