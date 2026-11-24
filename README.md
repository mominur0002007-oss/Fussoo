# TU VR Player — The Unlimited VR Cinema

A smartphone-first VR cinema for Cardboard-style viewers, including VR Box and Shinecon. The repository contains a responsive browser/PWA build and a small native Android WebView host. The renderer is a custom WebGL 1.0 pipeline (not a pair of duplicated `<video>` elements): it draws a perspective-correct cinema screen or an equirectangular sphere, renders independent left/right eye viewports, applies IPD/lens/alignment calibration, and reads fused Android rotation-vector / browser orientation data.

## Run the web player

Serve the repository root over HTTP(S); for example:

```sh
python3 -m http.server 4173 --bind 0.0.0.0
```

Open the URL on a phone. For motion sensors in a browser, use HTTPS and grant motion permission. Select **Add videos** to open the device file picker. The player uses browser-native hardware video decoding and keeps playback history in local storage. Local video containers and codecs depend on Android WebView/browser support; unsupported codecs will not play. Subtitle loading accepts WebVTT and SRT files; audio/subtitle track availability depends on the browser decoder.

Installable PWA metadata and an offline shell are included. Browsers require HTTPS (or localhost) to install a PWA and use Service Workers.

## Build the Android host

Open `android/` in Android Studio (JDK 17 and Android SDK 35), sync Gradle, then run the `app` configuration on an Android 6.0+ phone. The host bundles the web UI in `app/src/main/assets/web`, opens videos/images with Android's Storage Access Framework, uses the Android rotation-vector sensor while tracking is enabled, and switches to immersive landscape while the VR cinema is open. No broad storage permission is requested.

Web source is in the repository root. After changing `index.html`, `styles.css`, `app.js`, `manifest.webmanifest` or `icon.svg`, refresh the bundled copy:

```sh
cp index.html styles.css app.js manifest.webmanifest icon.svg android/app/src/main/assets/web/
```

The Android host has no network or account dependency; typography uses platform-safe system sans-serif fallbacks.

## Implemented controls and limitations

- Normal, VR cinema, 180°/360° video, flat SBS/top-bottom 3D, and 360° SBS/top-bottom 3D modes.
- Built-in procedural 360° environments plus imported equirectangular stills; panoramic video can be selected as the active 360° source.
- Play/pause, seek, ±10 second seek, volume, playback speed, supported audio-track selection, built-in/external subtitles and sync adjustment, resume history, and fullscreen/immersive landscape.
- Touch/mouse drag fallback, keyboard shortcuts (Space, arrows, R), Bluetooth media keys supported by the host/browser, and configurable view calibration.
- This is a smartphone/WebGL player, not a Google VR SDK/Cardboard SDK app. It does not provide optical correction profiles for specific headset optics, DRM playback, external streaming services, or a guaranteed codec set. Rendering quality and supported media vary by device/GPU/WebView. Test headset fit, IPD and comfort at low brightness before extended use.

## Structure

- `app.js`, `styles.css`, `index.html` — product UI, local library, settings and WebGL renderer.
- `android/app/src/main/java/com/tuvr/player/MainActivity.java` — Android SAF, sensor, orientation and immersive host.
- `android/app/src/main/assets/web/` — bundled web interface.
