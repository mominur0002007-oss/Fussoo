# Fussoo VR 2.0

An in-world YouTube cinema for Android. Not a video player with a VR viewer bolted on:
the browsing, the search, the player and the settings all live **inside** the virtual
room, the way iQIYI VR works.

Built for a **TECNO Spark 10C** (Android 13, Unisoc T606) in a passive VR box, and it runs
on any Android 7+ phone with OpenGL ES 2.

---

## Install

**Download:** [FussooVR-2.0.0.apk](https://drive.google.com/file/d/1493v2qyAcJfSKT0xy02irsMt__vttb1Z/view)

1. Open the link on the phone and download.
2. Open the file. Android asks to allow installs from this source — allow it.
3. Install, then open **Fussoo VR**.

Already have 1.0.1? Install straight over it: v2.0.0 is signed with the same key
(cert SHA-256 `0f84ad70…99ecac`), so nothing is lost and you do not have to uninstall.

| File | Size | SHA-256 |
| --- | --- | --- |
| `release/FussooVR-2.0.0-release.apk` | 5,669,899 B | `413cf9ff4f9f38f318fb90add1daf6effd1798e4dc254838f6179d4896003ee7` |
| `release/FussooVR-2.0.0-debug.apk` | 7,134,603 B | `3ac17736cacc9c30b31a26cc4612b5a8afea4a5b75f4bc15ee7b029583568a84` |

---

## What you see

One room, four walls of content, nothing on the phone itself.

```
        ┌──────────────────────────────┐
        │  ██  ██  ██  ██  ██          │   HOME: a hero film, a row of
        │  ████████████████            │   posters, search chips, a gear
        └──────────────────────────────┘
                 ▁▁▁▁▁▁▁▁▁                rail: Home Browse Search Settings ⟳
     ═══════════════════════════════════   console (only while a film plays)
                    ▲ your seat
```

* **HOME** — a featured film filling the wall, five posters beneath it, quick-search chips
  along the top, settings behind the gear.
* **BROWSE** — a 5 × 2 poster wall with paging.
* **PLAYER** — the film on the curved screen, a control console tilted toward you, and a
  title card that fades out once playback settles.
* **SETTINGS** — nine rows of real controls, all steppable with your eyes.

### Controls

| Gesture | Does |
| --- | --- |
| Look at something | A ring fills; at 1.7 s it activates |
| Tap | Activate whatever the reticle is on. On the film: pause |
| Long press | Recentre the room in front of you |
| Swipe sideways | Seek ±10 s |
| Swipe up / down | Volume |

There is deliberately **no keyboard**. Typing with your eyes is miserable, so search is a
cycle of curated queries (cinematic 4K, lofi, nature docs, space, music, trailers).

---

## How it works

```
MainActivity ──► WorldSurface (GLSurfaceView) ──► VrRenderer ──► ExoPlayer
      │                  │            │
      └── WorldController ┘            └── Tracker (3DoF head)
              │
      WorldModel (one snapshot, pushed every 33 ms)
```

### The renderer

Each frame is drawn twice into one off-screen buffer — left half, right half — and then a
single full-screen pass applies the lens pre-distortion.

* **The film is centred on your eye** at the distance you chose. It used to hang in the
  lower half of the view, which is what made v1 feel like a monitor on a desk rather than
  a cinema.
* **Projection comes from the room, not a magic number:** `tanY = halfHeight / distance`,
  with an 11 m screen at 4.2 m giving a 73° vertical field — the film overflows your view
  the way an IMAX screen does.
* **The screen is curved** — the film quad bends into a cylinder, so a 2.39:1 film wraps
  around you instead of sitting flat.
* **Eye separation** offsets each eye by ±31.5 mm in eye space, which gives real parallax.
* **Lens pre-distortion** is a radial barrel warp plus chromatic correction and a vignette
  that hides the box's bezel.
* **Render scale is 62%** of the panel by default. Through a plastic lens that is
  indistinguishable, and it is the biggest frame-rate win on a T606.

Every panel, label and badge is packed into one 2048² Canvas atlas built at runtime, so
the whole interface is a **single draw call**; third-party thumbnails get their own
textures so they can never evict the UI.

### Gaze that actually points where you look

The tracker's matrix is world-from-body. The forward vector is the **negated third
column** (`-m[8], -m[9], -m[10]`). Using the third *row* instead — the mistake in v1 —
flips the cursor left/right and makes it run away from you.

The gaze ray is intersected with each panel **in that panel's own rotated frame**, using
the same numbers the renderer draws with, so a hit box can never drift away from the
picture. The console is tilted 26°; the intersection is exact at any tilt.

Two threads ask for a head matrix every frame (renderer and controller), so `Tracker`
hands out ping-ponged scratch matrices — they can no longer write the same matrix
mid-frame.

### Why v1 showed a black screen

`GLSurfaceView.setRenderer()` was called from a `SurfaceHolder.Callback` added *after* the
surface existed, by which point GLSurfaceView had already started its GL thread. The
renderer was never attached: no crash, no log, just black. v2 attaches it from
`onAttachedToWindow`, the only point where the view has a window and the GL thread does
not yet exist.

v2 also fixes the second black screen waiting to happen: **ExoPlayer was never given a
Surface**, so even a working renderer would have played audio over an empty theatre.

---

## Settings worth touching

| Setting | Why |
| --- | --- |
| Lens separation | Boxes vary from 60 to 65 mm. Wrong here means double vision. |
| Distance | How far the screen feels. Closer is more immersive, less comfortable. |
| Barrel correction | Raise until straight lines look straight through the lenses. |
| Curvature | 0 is flat, 1 is a deep IMAX wrap. |
| Performance | Battery / Balanced / Cinematic — changes render scale. |
| Venue | IMAX Theatre, Noir Lounge, Deep Space, Cozy Room. |
| Gaze | Off means touch-only. |

---

## Honest limits

* **YouTube extraction is best-effort.** It uses
  [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) (GPLv3), which
  Google can break whenever it changes the player. When it fails the app falls back to
  the official web player, which always plays but shows ads.
* **3DoF only** — look around, you cannot walk around.
* **No thermal magic.** Expect 2.5–4 h of playback; the phone gets warm. Battery mode
  helps.
* **Vertical Shorts** are letterboxed in a 16:9 theatre and look small.
* **No gyroscope?** The app still works: it falls back to the accelerometer for a
  level, stable image. Head tracking is then limited to whatever that sensor can give, so
  look direction is coarse — touch and the rail become the main controls.

---

## Build

CI does the real build; no local Android SDK needed:

```
.github/workflows/fussoo-vr-apk.yml
```

Push to `android/**` and it assembles debug + release, signs both with
`android/keystore/fussoo-dev.p12`, and commits the APKs into `release/`.

Locally: `cd android && gradle :app:assembleRelease`

`tools/check_java.py` is a static gate for the sandbox (which has no JDK): it parses every
source file and checks that every referenced member, method and loop-variable capture
actually exists. It catches the class of error that shipped in v1.

---

## Licence and content

The app's own code is yours to do as you like with. NewPipeExtractor is GPLv3, Media3 is
Apache 2.0, Space Grotesk is OFL. YouTube content belongs to its owners — this is a
personal player, not a download tool, and nothing is redistributed.
