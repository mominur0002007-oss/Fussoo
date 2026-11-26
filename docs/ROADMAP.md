# Roadmap

Ordered by what actually improves the experience on a TECNO Spark 10C + Shinecon VR Box,
not by what sounds impressive.

## Before anything else: first device run

The next step is not a feature. It is the checklist in [TESTING.md](TESTING.md), executed
on the target phone, with the Diagnostics report attached to whatever fails. Everything
below assumes that pass has happened.

## 1.1 — polish what exists

* Real-device tuning of the default viewer profile for the Shinecon box (lens separation,
  FOV, screen-to-lens distance) and better explanatory text in the calibration scene.
* Per-file projection memory: remember 2D/180/360 and SBS/TAB per URI (the database
  already stores a projection column; the cinema does not read it back yet).
* Gesture/tap play-pause on the cinema screen itself (currently only via the panel).
* Seek bar you can scrub with gaze (drag along the progress panel).
* Save/restore a small set of named viewer profiles.
* Subtitle size/position controls and a subtitle offset in milliseconds.

## 1.2 — comfort and robustness

* Optional "cinema screen follow" toggle (off by default) for people who want the screen
  to stay put when they turn — implemented as a damped yaw follow, never forced camera
  motion.
* Drift compensation using the accelerometer's gravity vector for longer sessions.
* Better thermal policy: reduce panel resolution before reducing the video's texture
  resolution, and expose the current state in the stats overlay.
* Move panel bitmap creation off the GL thread into a small bitmap pool to shorten hitches
  when a page of the grid loads.

## 1.3 — media

* Audio/subtitle track menus inside the VR cinema, not only the phone player.
* Playlists and "next video in folder" after the current file ends.
* Playback of network streams (SMB/DLNA/HTTP) with the honest caveat that DRM services are
  still out of scope.
* 180°/360° fisheye layouts and per-file stereo layout override saved with the file.

## 2.0 — bigger changes, only if the device run supports them

* **Positional drift-free 3DoF+**: not promised. This would need either a depth sensor or
  a SLAM library; on a T606 with 4 GB of RAM the cost/benefit is unproven.
* **Web screen at a usable frame rate**: requires rendering WebView through a hardware
  texture (SurfaceTexture-backed capture or Chromium's own compositor hooks). Only worth
  attempting if the current software capture proves unreliable on this device.
* **Cardboard-compatible QR profile import** so people can reuse existing viewer profiles.
* **Night mode / blue-light reduction** in the cinema environment.

## Explicitly out of scope

* Claiming or faking 6DoF.
* Turning arbitrary flat games into real VR; split-screen apps stay honestly labelled.
* Ads, analytics, accounts, cloud sync, telemetry.
* Streaming DRM video.
