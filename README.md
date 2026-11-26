# TU VR — The Unlimited Virtual Reality

A self-contained stereoscopic VR app for Android phones, written from scratch as a
native Android (Java) project. It turns an ordinary phone plus a passive VR box
(development target: **Shinecon VR Box** on a **TECNO Spark 10C**) into a viewer for
local video, a virtual cinema, a media gallery, an app launcher and a simple web
screen.

* Package: `com.tuvr.app`
* Version: 1.0.0 (versionCode 1)
* Min Android: 7.0 (API 24) · Target: Android 14 (API 34) · Compiled against API 35
* Rendering: OpenGL ES 2.0, own stereo renderer — **no Cardboard/Daydream SDK, no Unity**
* Tracking: 3DoF orientation only (rotation vector, or gyroscope + accelerometer fallback)
* No ads, no analytics, no accounts, no cloud. The only component that uses the
  network is the web screen, because that is what a browser does.

---

## What is in the app

**Phone mode**

| Screen | What it does |
| --- | --- |
| Home | Entry points, persistent notice about 3DoF limits, quick access to settings/diagnostics |
| Videos | Gallery with thumbnails, duration, folders, favourites, search and sort; SAF picker for libraries outside MediaStore |
| Player | Full-screen flat playback: play/pause, seek, speed, subtitle and audio-track selection, resume |
| Apps | Installed launchable apps, favourites and recents, honest "VR ready / flat only" labels |
| Browser | Normal WebView with URL bar, back/forward/reload |
| Settings | Every option, stored as normal Android preferences |
| Diagnostics | Device report, live tracker readout, copy/share, crash log |
| Licences | Attribution and licence text |

**VR mode (stereo, inside the headset)**

| Screen | What it does |
| --- | --- |
| Home | Floating glass panels in a curved arc: Cinema, Videos, Continue, Apps, Browser, Settings, Calibrate, Recenter, Diagnostics, Exit |
| Cinema | Large screen (size, distance, curvature, vertical position, reset), 2D / 180° / 360°, SBS / TAB, control panels below the screen, progress, speed, brightness, volume, environment cycling |
| Videos / Apps / Favorites / Folders | Paged grid of panels with thumbnails or app icons |
| Web | WebView captured into a virtual screen ~4 times per second, with back/forward/reload/URL/exit panels |
| Calibration | Stereo test pattern plus in-headset steppers for lens separation, FOV, IPD, distortion, eye offset X/Y and screen-to-lens distance; reset to Shinecon defaults; save |

Interaction is **gaze + dwell** (default) or tap. Leave VR at any time with the
small `⋮` button in the corner of every VR screen.

---

## Documentation

| File | Contents |
| --- | --- |
| [docs/BUILD.md](docs/BUILD.md) | Building on a computer and via GitHub Actions |
| [docs/MOBILE_BUILD.md](docs/MOBILE_BUILD.md) | Building without a computer (phone-only workflow) |
| [docs/INSTALL.md](docs/INSTALL.md) | Installing the APK, first run, permissions |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Code layout, renderer pipeline, tracking maths, threading rules |
| [docs/DEPENDENCIES.md](docs/DEPENDENCIES.md) | Libraries, versions and why they are pinned |
| [docs/TESTING.md](docs/TESTING.md) | What has been verified, what has not, and the on-device test checklist |
| [docs/KNOWN_LIMITATIONS.md](docs/KNOWN_LIMITATIONS.md) | Honest limits — read this before judging the app |
| [docs/ROADMAP.md](docs/ROADMAP.md) | What would come next |

---

## Quick start

1. Get the APK: GitHub → **Actions** → the newest *TU VR APK* run → artifact
   `TUVR-APK` → `TUVR-1.0.0-release.apk` (or `-debug`).
2. Install it on the phone (allow "install unknown apps" for the browser/file app
   you use).
3. Open **TU VR**, grant the video permission, tap **Videos → Open VR Cinema** on a
   video, put the phone into the headset, and look at the control panels.
4. First time in a headset: open **Calibrate** and align the optics for your eyes
   (the values are stored per device, nothing about your headset is hardcoded).

---

## Privacy

* No internet permission is used outside the web screen (single `INTERNET` permission,
  used by `BrowserActivity` / `VrBrowserActivity` only).
* No advertising, analytics, crash reporting or account code — the diagnostics screen
  reads *local* data and only shares it if you press *Share*.
* Video files are read in place through MediaStore or the Storage Access Framework;
  nothing is copied, uploaded or catalogued off the device.

See [docs/KNOWN_LIMITATIONS.md](docs/KNOWN_LIMITATIONS.md) for what the app does **not**
do (no 6DoF, no positional tracking, no true VR for flat games, no DRM video).
