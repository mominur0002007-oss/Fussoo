# Known limitations

Read this before judging the app. Every item is a deliberate boundary, not a hidden bug.

## Tracking

* **3DoF only.** Yaw, pitch and roll from the phone's sensors. There is no positional
  (translational) tracking and none is claimed. Leaning forward does not move you closer
  to anything.
* **No 6DoF fallback tricks.** No camera tracking, no SLAM, no "walk around" claims.
* With no gyroscope the app degrades to accelerometer-only (pitch/roll, no yaw) and warns
  you; with no sensors at all it degrades to drag-to-look and says so.
* Magnetic yaw (rotation vector) can drift or jump near metal, magnets, speakers or
  chargers. Use *Recenter*; the diagnostics screen shows the tracking source and an
  estimated drift.
* Sensors are sampled at the rate HiOS permits while the screen is on. Battery savers can
  lower that rate; nothing in the app can override it.

## Optics and calibration

* Calibration is by eye. The distortion model is a two-term radial polynomial
  (`1 + k₁r² + k₂r⁴`) plus an optional per-channel offset; it cannot be more accurate than
  the viewer's own estimate for an unknown lens.
* The Shinecon defaults are a **starting point**, not a measurement of your unit. Every
  headset is slightly different; the values are stored per app install.
* Only one calibration profile is active at a time (stored values + a label). There is no
  profile list with names, although the preset ids exist.
* Chromatic correction is a simple radial channel offset, not a spectral model.

## Video

* Playback is whatever Media3/ExoPlayer can hardware-decode on the Unisoc T606. Very high
  bitrate 4K HEVC may drop frames or fall back to software decoding (and then stutter).
* DRM content (Netflix, Prime Video, etc.) cannot be played — no DRM component is
  included, by design (no accounts, no cloud).
* Projection modes cover 2D, 180°, 360°, SBS and TAB. Fisheye/barrel source layouts and
  "VR180 fisheye" files are not supported.
* 360°/180° orientation correction is a single rotation parameter; it is not an automatic
  3D "horizon lock".
* In VR cinema you can change speed, pause and seek, but **audio-track and subtitle-track
  selection is only available in the phone player** (the cinema uses the file's default
  tracks and renders the selected subtitle track's cues if one is enabled).
* Subtitles are drawn onto a panel as plain text from the player's cue callbacks; SSA/ASS
  styling and positioning are not honoured.

## Gallery and files

* MediaStore lists videos the system indexes. Files in app-private folders or not yet
  indexed need the Storage Access Framework picker; folders added that way are re-scanned
  on demand, not watched live.
* Thumbnails are memory-only and rebuilt after the process is killed.
* In VR the grid supports paging, folder filtering and favourites; **search and sorting are
  only in the phone gallery** (typing inside a headset with gaze selection is not usable).
  The VR list is always ordered newest first, so it doubles as "recents".
* No editing, no transcoding, no downloading of remote video.

## Web screen

* The page is rendered by a WebView into a bitmap a few times per second
  (~260 ms). That is fine for reading and navigation and **useless for video**.
* It cannot render at display refresh rate; scrolling will feel choppy.
* Some devices/WebView builds do not expose pixels to a software canvas. When that happens
  the app detects a flat/blank capture and shows an explanatory message instead of a black
  panel; use the phone browser in that case.
* No tabs and no downloads. Bookmarks are created in the phone browser; the VR browser
  shows pages but has no bookmark panel (Back/Forward/Reload/URL/Exit only).
* HTTPS only (`usesCleartextTraffic=false`).

## VR apps / launcher

* Other apps are **not** turned into VR. The app launcher shows *VR ready* only for
  activities that genuinely handle VR intent or landscape full screen; everything else is
  labelled *flat only* and opens as a normal flat app on the phone screen.
* There is no real split-screen VR compositor — that would require system privileges.

## Performance

* Quality presets change the eye buffer scale; *Balanced* is the default. Adaptive scaling
  reacts to frame time and thermal status, so resolution can change while you watch.
* No frame-rate promises are made for this device. Use Diagnostics → Show FPS and the stats
  overlay to see what your unit actually achieves.
* The Mali-G57 handles this workload well, but a closed headset heats a phone faster than
  hand-held use; expect the adaptive scaler to kick in on long sessions.

## Known after the first phone install (1.0.0 → 1.0.1)

* **YouTube in VR**: the VR browser shows YouTube's mobile site as a texture that is
  captured a few times per second. That is fine for browsing titles, but it is *not* a video
  player; video playback inside the VR browser will be choppy. The **YouTube app** panel
  hands a video id to the installed YouTube app instead, which leaves TU VR. There is no
  YouTube API integration and no support for downloading videos.
* **In-VR search** is typed on the phone screen: search mode shows a normal text field over
  the stereo view and uses the phone keyboard. The headset has no text input of its own.
* **Environment switching is visual only**: the cinema/theater/world are geometry without
  textures or reflections; there is no ambient occlusion or lighting model beyond vertex
  colour. This is a deliberate choice for the T606/Mali-G57, not a promised feature.
* **A hard crash still closes the process.** Recoverable failures (scene callbacks, decoder
  updates, background work) no longer do, and the crash text is written to
  `Download/TUVR-crash.txt` and shown in Diagnostics, but Android cannot resume a process
  whose main thread has died.
* **Video orientation** depends on what the file and the decoder report. If a file still
  arrives mirrored or rotated on your device, the cinema has Flip and Rotate controls and
  *Swap eyes* for stereo files; those choices last for the session, not per file, on 1.0.1.

## Platform

* targetSdk 34. Android 15/16 behaviour changes are untested.
* Landscape-only VR screens by design; the phone screens rotate freely.
* No Android TV, no Daydream/Cardboard SDK integration, no controller support beyond gaze,
  tap and (optionally) media buttons already handled by the system.
