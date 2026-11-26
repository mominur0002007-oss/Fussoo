# Dependencies

Everything is AndroidX/Google-hosted and free to use. Versions are pinned and were
chosen for stability on Android 7–14; **do not blindly upgrade** — Media3 and Material
minor versions have changed defaults and API surfaces between releases, and this app is
built to be verified on a real phone rather than re-tested on every bump.

## Build toolchain

| Component | Version | Note |
| --- | --- | --- |
| Android Gradle Plugin | 8.7.3 | `android/build.gradle` |
| Gradle | 8.9 | supplied by CI (`gradle/actions/setup-gradle`) |
| Java | 17 | source/target compatibility 17 |
| compileSdk | 35 | |
| targetSdk | 34 | deliberately one behind: Android 14 behaviour is well understood, Android 15/16 changes are not verified on the target phone |
| minSdk | 24 | Android 7.0+, so the app also runs on older spare phones |
| Build tools | 35.0.0 | installed by CI |

## Runtime libraries

| Artifact | Version | Used for |
| --- | --- | --- |
| `androidx.appcompat:appcompat` | 1.7.0 | `AppCompatActivity`, themes, vector support |
| `androidx.core:core` | 1.13.1 | `ContextCompat`, permission helpers |
| `androidx.recyclerview:recyclerview` | 1.3.2 | gallery / app lists |
| `androidx.preference:preference` | 1.2.1 | the whole settings screen from XML |
| `androidx.constraintlayout:constraintlayout` | 2.1.4 | phone-mode layouts |
| `androidx.documentfile:documentfile` | 1.0.1 | folder support for SAF picks |
| `com.google.android.material:material` | 1.12.0 | `MaterialCardView`-based glass panels in phone mode |
| `androidx.media3:media3-exoplayer` | 1.4.1 | playback, speed, track selection, subtitles |
| `androidx.media3:media3-common` | 1.4.1 | `MediaItem`, `TrackGroup`, `CueGroup` |

Nothing else is included: no Cardboard SDK, no GVR, no Unity, no analytics, no ad SDK.
The stereo renderer, the sensor fusion and the panels are all in this repository
(`com.tuvr.app.vr`), which is why the APK is small (~6.5 MB per variant).

## Assets

* `assets/shaders/*.vert|*.frag` — 12 GLSL ES 2.0 files written for this project.
* `assets/licenses.txt` — licence/attribution text shown by the Licences screen.
* Vector drawables and shape XML in `res/drawable`, generated for this app (Apache-2.0
  compatible iconography drawn by hand; no third-party icon packs).

## Upgrading safely

1. Bump one dependency at a time in `android/app/build.gradle`.
2. Push; the CI builds debug **and** release.
3. Read `ci/lint.txt` for `UnsafeOptInUsageError`/`NewApi` regressions.
4. Install on the phone and run the checklist in [TESTING.md](TESTING.md) — especially
   *Cinema playback*, *360 SBS*, *subtitle rendering* and *recentre*, which are the paths
   most affected by Media3 changes.
