# Installing TU VR

## Requirements

* Android 7.0 (API 24) or newer. Development target: **TECNO Spark 10C
  (KI5k), Android 12 / HiOS 8.6, 1612×720, Unisoc Tiger T606, Mali-G57, 4 GB RAM**.
* A passive phone VR box (a Shinecon VR Box is used as the reference viewer).
* OpenGL ES 2.0 (every Android phone since ~2012 has it).
* A gyroscope is strongly recommended; accelerometer-only and no-sensor devices fall
  back to orientation from the accelerometer or to drag-to-look.

## Install the APK

1. Get `TUVR-1.0.1-release.apk`:
   * GitHub → **Actions** → newest *TU VR APK* run → **Artifacts** → `TUVR-APK`
     (a `.zip` on mobile) → unzip.
   * Or take it from a local/CI build (see [BUILD.md](BUILD.md)).
2. Tap the APK. Android 12 asks to allow "install unknown apps" for the app you are
   installing from — allow it and confirm.
3. Press **Install**, then **Open**.

Updates install over the previous version without losing settings, because every build
is signed with the same committed key. If you build with your own key, uninstall first.

## First run

1. The home screen lists the entry points and a notice about 3DoF limits.
2. Open **Videos** and grant the video permission (Android 13+: *Videos*; Android 12:
   *Storage*). Denying it still lets you open single videos through **Open with SAF**,
   which needs no permission at all.
3. Play a video, then press **VR** (or open **VR Cinema** from the home screen) to see
   it stereoscopically.
4. Put the phone into the box, then use gaze + dwell to select panels: point the
   reticle at a panel and hold your gaze; the ring fills and the panel activates.

## Recommended first session in the headset

1. **Calibrate** → align the test pattern for your eyes:
   * *Lens sep*: move the two images until each eye sees a centred, un-cropped frame.
   * *FOV*: the frame should touch the edges of what you can see without clipping.
   * *IPD* / *Eye X, Eye Y*: make the centre crosses merge into a single cross.
   * *Distort*: make the vertical bars straight (not bowed).
   * *Screen*: only needed if your box’s lens-to-screen distance differs from 45 mm.
   * Press **Save** when the pattern looks right; **Reset** restores Shinecon defaults.
2. **Recenter** (in the corner menu or the panels below the screen) while looking
   straight ahead — this sets the forward direction.
3. Set **Quality** to *Balanced* (default) and enable *Show FPS* in Diagnostics if you
   want to watch frame timing on your own device.

## Permissions in detail

| Permission | Why | What happens if you refuse |
| --- | --- | --- |
| `READ_MEDIA_VIDEO` (API 33+) / `READ_EXTERNAL_STORAGE` (≤ 32) | list videos in the gallery | gallery is empty; you can still open single files via SAF |
| `INTERNET` | the web screen | only the web screen is affected |

No camera, microphone, location, contacts or notification permissions are requested.
