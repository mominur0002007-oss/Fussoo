# Testing report

Honest summary first: **the app has been built, but it has not been run on a phone yet.**
The development environment for this milestone had no Android device, no emulator and no
local Android SDK, so every conclusion below is either a *build result*, a *static analysis
result* or a *code-level verification*. Nothing here claims that a screen looked right in a
headset. The checklist at the end is what needs to be executed on the TECNO Spark 10C.

## What was verified, and how

| Verification | Method | Result |
| --- | --- | --- |
| Project builds | GitHub Actions, `ubuntu-latest`, Temurin JDK 17, `platforms;android-35`, `build-tools;35.0.0`, Gradle 8.9 | **`assembleDebug` and `assembleRelease` succeed** — release APK 6,508,313 bytes, debug 8,169,955 bytes |
| Manifest as packaged | `aapt2 dump badging` on the release APK (report in `ci/lint.txt`) | `com.tuvr.app` v1.0.0, minSdk 24, targetSdk 34, compileSdk 35, permissions exactly as intended |
| Release signing | `apksigner verify --print-certs` | verified; signer `CN=TU VR, OU=Personal, O=TU VR Project, L=Dhaka, C=BD`, cert SHA-256 `c1e2b519…ff11d41` |
| Resource wiring | script that extracts every `R.string/R.id/R.drawable/R.array/R.layout/R.color/R.dimen/R.style` reference from Java and XML and compares it with what is defined | 0 unresolved references (55 Java files) |
| Resource validity | AAPT resource linking during the build | passes (duplicate string, malformed style item and `app:max` misuse were found and fixed this way) |
| Shader/Java contract | script comparing every `setXf("name")` uniform against the GLSL sources | all 16 uniforms exist in the shaders; no silently ignored uniforms |
| Static analysis | `:app:lintDebug` (AGP 8.7.3), report committed to `ci/lint.txt` | after fixes: **0 errors**; remaining warnings are dependency-update notices, `OldTargetApi`, and `DiscouragedApi` for the intentionally landscape-locked VR screens |
| Opt-in APIs | lint `UnsafeOptInUsageError` | Media3 usage annotated, 0 remaining |

### Code-level reviews that changed the code

These were found by reading the maths and the threading model, not by a device; each is
fixed in the current tree:

1. **Gaze hit test had an inverted sign** — the ray/plane solution returned a negative
   distance, so no panel would ever have been selectable. Now
   `t = dot(center, normal) / dot(dir, normal)` with the panel normal pointing at the user.
2. **World frame was wrong by 90°** — Android reports orientation in an east-north-up
   frame (Z up) while the renderer works Y-up; without the `Rx(−90°)` correction the
   horizon would have been rolled by 90 degrees. Derived and verified with a worked
   example (landscape phone facing north must produce an identity camera rotation).
3. **Recentre double-counted yaw** — yaw extraction now matches the renderer's forward
   axis convention (`atan2(2(xz+wy), 1−2(xx+yy))`), so recentring zeroes the heading
   instead of doubling it.
4. **Eye swap applied twice** — it was applied both to the video UVs and to the eye
   buffers, cancelling out; it is now a single composite-stage swap.
5. **Cinema settings changes were not applied** — size, distance, curvature and reset
   updated the preference file but the renderer kept cached values; the scene now marks
   the renderer dirty, and the curved/flat screen mesh is rebuilt when its physical size
   changes.
6. **MediaStore query on the GL thread** — the "Continue watching" label query ran during
   rendering; it now runs on a worker and is applied on the next frame.
7. **Bitmap recycle race** — a bitmap could be recycled by the UI thread while the GL
   thread was uploading it; `Panel.setContent`/upload are now synchronised on the panel,
   and the browser's own buffers are marked `recycleOnReplace = false`.
8. **API 26 file APIs on a minSdk 24 app** — `java.nio.file.Files` replaced with streams
   in the crash log reader (would have crashed on Android 7/8).
9. **22 vector drawables had `android:tint` outside the `<vector>` tag** — the icons
   compiled but rendered untinted; caught by lint (`ExtraText`) and fixed.
10. **Back button in the VR browser** — overriding `onBackPressed` without `super` left
   the user unable to leave; navigation history and exit are now separate paths.
11. **Permission request type** — switching to a multi-permission request (needed for
    Android 14 partial media access) also required changing the launcher type.

## What has NOT been verified

* Anything visual: stereo alignment, distortion tuning, text legibility at panel distance,
  environment appearance, reticle size.
* Runtime behaviour of Media3 on the Mali-G57 path: hardware decoders, 4K downscaling,
  subtitle timings, audio-track switching on real files.
* Sensor behaviour on the T606: rotation-vector quality, drift, HiOS sensor throttling
  with the screen in a headset, `sensorPrivacy`/battery-saver effects.
* Thermal and battery behaviour in a closed headset (this is the single most likely source
  of surprise on a 4 GB T606 device).
* The WebView-into-texture capture path (software `View.draw` into a bitmap) — it is
  guarded by a blank-capture detector that shows a message instead of a black panel, but
  whether it produces real pixels on this device is unknown.
* Installation on the actual phone, first-run permission flow, SAF picks from SD card.

## On-device test checklist (to be executed on the Spark 10C)

Mark each item; anything that fails becomes a bug entry in the roadmap.

### A. Install and phone mode
- [ ] APK installs (allow unknown apps), app opens without a crash
- [ ] Videos screen asks for the video permission; grant → thumbnails appear
- [ ] Deny path: SAF open still plays a file
- [ ] Player: play/pause, seek, speed, subtitle track, audio track, resume after leaving
- [ ] Apps screen lists launchable apps; launch one; it appears in recents
- [ ] Settings opens, every section scrolls, values persist after force-stop
- [ ] Diagnostics shows device, GLES, sensors, decoders; Copy and Share work

### B. Stereo basics
- [ ] VR Cinema opens landscape, immersive, no system bars
- [ ] Each eye sees one sharp image; no mirroring of text
- [ ] Head rotation moves the view in the same direction as the head, no axis swap
- [ ] Horizon stays level when looking up/down (no 90° roll) — this is the ENU→Y-up fix
- [ ] Recenter makes the current direction forward; pitch is preserved
- [ ] Mono preview button shows one full-width image

### C. Selection
- [ ] Reticle follows gaze with low latency
- [ ] Dwell ring fills over ~1.2 s and activates the panel once (no double fire)
- [ ] Looking at a non-interactive panel (progress bar) does nothing
- [ ] Tap-to-select works in mono preview (phone held in hand)

### D. Cinema
- [ ] Local 1080p H.264 file plays hardware-decoded, A/V sync
- [ ] Bigger/smaller and nearer/farther change the screen; Reset restores defaults
- [ ] Curvature setting changes the screen shape, no flicker or mesh leak after a few changes
- [ ] Pause/play from the panel; progress and time update
- [ ] Speed button cycles 0.75/1/1.25/1.5 and the label follows
- [ ] Brightness and volume buttons change the panel value and the actual effect
- [ ] Environment button cycles Void → Cinema → Dark theater → Space → Ambient grid
- [ ] Rotation correction visibly rotates 2D content

### E. Projection modes
- [ ] 2D flat (default)
- [ ] 180° equirect (half sphere in front)
- [ ] 360° equirect (inside a full sphere) — check the seam is behind you and the
      horizon is level
- [ ] SBS stereo: left eye sees the left half, no cross-talk; L/R swap fixes a wrong file
- [ ] TAB stereo: top/bottom split, no vertical bleed
- [ ] Leaving and re-entering cinema keeps the chosen projection/stereo for that file

### F. Gallery / apps in VR
- [ ] Grid pages show thumbnails, page indicator correct, Back/Next work
- [ ] Selecting a video opens VR Cinema with it playing
- [ ] Folder filter and favourites-only modes show the right subset
- [ ] App grid shows icons and names; selecting launches a flat app in split screen or
      full screen (honest: it is not VR)

### G. Web
- [ ] Page appears in the virtual screen at all (blank-capture message tells you if not)
- [ ] Back/Forward/Reload/URL panels work; URL bar shows and hides the keyboard
- [ ] Exiting the VR browser returns to the VR home

### H. Stability and thermals
- [ ] 10 minutes of cinema playback: no ANR, no visible leak, frame time stable
- [ ] 10 minutes in VR home: device warm but not throttled to a crawl
- [ ] Background/foreground (home button, headset removal) resumes without losing position
- [ ] Airplane mode: everything except the web screen still works
- [ ] Diagnostics crash log stays empty

### Reporting
Copy Diagnostics → Share, and note the exact step plus whether the failure is visual,
audio, sensor or a crash. The diagnostics report contains device, GLES, refresh rate,
sensor list, decoder list, thermal status and the last crash text.
