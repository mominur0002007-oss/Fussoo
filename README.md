# Fussoo VR

A phone-only stereoscopic theatre for YouTube. Built for a **TECNO Spark 10C** in a
passive VR box, but it runs on any Android 7+ phone with a gyroscope.

The idea: you put the phone in the box, and instead of watching a flat video you are
sitting in a dark IMAX-style room looking at a huge curved screen. Look around, the room
stays put. Stare at a control, it activates.

---

## Install

**On the phone (no computer needed)**

1. Download `FussooVR-1.0.1.apk`.
2. Open it. Android asks to allow installs from this source — allow it.
3. Install, open **Fussoo VR**.

The APK is signed with a committed development key, so future builds install *over* this
one without uninstalling.

**From the repo**

| File | Use |
| --- | --- |
| `release/FussooVR-1.0.1-release.apk` | Recommended. 5.7 MB, release build. |
| `release/FussooVR-1.0.1-debug.apk` | Debug build, same key. Logs in `logcat`. |

Checksums are in `release/SHA256SUMS.txt`.

---

## How it works

```
MainActivity ──search / trending──► Yt (NewPipeExtractor + OkHttp)
     │
     └─ pick a video ──► TheatreActivity
                              │
              ┌───────────────┼────────────────┐
              ▼               ▼                ▼
         ExoPlayer      VrRenderer        Tracker
       (OES texture)   (stereo GL)     (3DoF head)
              │               │                │
              └──── VideoTex ─┴─ Atlas/Layer ──┘
                              │
                     one eye per half-screen
                        + lens pre-warp
```

### The renderer

Each frame is drawn twice, once into the left half of an off-screen buffer and once into
the right, then a single full-screen pass applies the lens pre-distortion.

* **Projection** is derived from the physical screen, not a magic number:
  `tanY = halfHeight / distance`. With the default 11 m screen at 4.2 m that is a 73°
  vertical view — the screen overflows your field of view the way an IMAX screen does.
* **Eye separation** translates the view per eye by ±31.5 mm along the panel, which gives
  real parallax: a point 30 cm in front of the screen shifts by ~1% between eyes.
* **Lens pre-distortion** is a radial barrel warp in the fragment shader with a touch of
  chromatic correction and a vignette that hides the box's bezel.
* **Render scale** is 68% of the panel by default. Through a plastic lens that is
  indistinguishable, and it is the single biggest frame-rate win on a Unisoc T606.

Every HUD element — buttons, labels, posters, the gaze dot — is packed into one 2 KB
texture atlas built with Canvas, so the whole interface is **one draw call**. Bitmaps are
cached by key, so a steady frame allocates nothing.

### Tracking, and why it is stable

`Tracker` uses the platform rotation vector when it exists and falls back to gyro
integration with a gravity levelling term that only corrects pitch and roll — never yaw,
so the horizon cannot drift sideways while you watch. Smoothing is filtered in *frame
time*, not frame count, so a slow frame never adds lag. Sensors register on their own
handler thread, and every session resets its state before the first sample.

### Controls

* **Gaze + dwell** — look at a button, a ring fills, it activates. Default 1.7 s.
* **Tap anywhere** — pause / resume.
* **Back** — return to the library shelf inside the headset.
* Buttons: play/pause, −10 s, +10 s, volume, quality, recentre, exit.

---

## Settings worth touching

| Setting | Why |
| --- | --- |
| Lens separation | Boxes vary from 60 to 65 mm. Wrong here = double vision. |
| Field of view | Leave on automatic unless the image looks zoomed. |
| Barrel correction | Increase until straight lines look straight through the lenses. |
| Curvature | 0 is flat, 1 is a deep IMAX wrap. |
| Performance | Battery / Balanced / Cinematic, changes render scale. |
| Max quality | 480p / 720p / 1080p. 720p is the sweet spot on this phone. |

`Calibrate` shows a stereo crosshair pair side by side — use it to line up the box.

---

## Honest limits

* **YouTube playback is best-effort.** It uses [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor)
  (GPLv3), which Google can break at any time. If extraction fails the app falls back to
  the official YouTube embed on a WebView, which always plays but is lower quality and
  shows ads.
* **3DoF only.** Look around, not walk around. The phone has no positional tracking.
* **No thermal magic.** Expect ~2.5–4 h of playback; the phone gets warm. Battery mode
  helps.
* **Vertical video** is letterboxed in a 16:9 theatre. Vertical Shorts look small.

---

## Build

Everything is built by GitHub Actions — no local Android SDK required:

```
.github/workflows/fussoo-vr-apk.yml
```

Push to `android/**` and the workflow assembles debug + release, signs both with
`android/keystore/fussoo-dev.p12`, and commits the APKs into `release/`.

Locally:

```bash
cd android && gradle :app:assembleRelease
```

---

## Licence and content

The app's own code is yours to do as you like with. NewPipeExtractor is GPLv3; Media3 is
Apache 2.0. YouTube content belongs to its owners — this is a personal player, not a
download tool, and nothing is redistributed.
