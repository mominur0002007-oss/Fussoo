# Building TU VR

The project is a standard Gradle/AGP Android app. There is **no Gradle wrapper on
purpose** (it keeps the repository small on a phone-first workflow): the build is
driven either by the GitHub Actions workflow or by a Gradle installation you already
have.

```
android/
├── settings.gradle          project name + module list
├── build.gradle             AGP 8.7.3 plugin declaration
├── gradle.properties        AndroidX, non-transitive R, build-feature defaults
├── keystore/tuvr-release.jks  committed personal release key (see note below)
└── app/
    ├── build.gradle         appId, sdk levels, deps, signing
    └── src/main/            manifest, java/, res/, assets/
```

## 1. GitHub Actions (recommended, no computer needed)

`.github/workflows/android-apk.yml` builds everything in the cloud.

* Runs on every push that touches `android/**` or the workflow file, and can be started
  manually from **Actions → TU VR APK → Run workflow**.
* Installs Temurin JDK 17, the Android SDK, `platforms;android-35`,
  `build-tools;35.0.0`, `platform-tools` and Gradle 8.9.
* Builds `assembleDebug` **and** `assembleRelease`.
* Uploads the artifact **`TUVR-APK`** containing
  `TUVR-1.0.0-debug.apk`, `TUVR-1.0.0-release.apk` and `SHA256SUMS.txt`.
* On a failure it commits the compile errors to `ci/last-build.log`; on success it
  commits the lint summary to `ci/lint.txt`. That way the build can be debugged from
  the repository even when the Actions log is not reachable from a phone.

Download: open the run, scroll to **Artifacts**, tap `TUVR-APK`. On Android the
artifact arrives as a `.zip`; unzip it with any file manager and install the APK
inside.

## 2. Local build (JDK 17 + Android SDK)

```bash
export JAVA_HOME=/path/to/jdk-17
export ANDROID_SDK_ROOT=/path/to/android-sdk     # must contain platforms;android-35
cd android
gradle :app:assembleDebug        # debug APK
gradle :app:assembleRelease      # signed release APK
```

Outputs:

```
android/app/build/outputs/apk/debug/app-debug.apk
android/app/build/outputs/apk/release/app-release.apk
```

Useful extras:

```bash
gradle :app:lintDebug            # static analysis
gradle :app:installDebug         # install on a connected device
```

## 3. Phone-only build

See [MOBILE_BUILD.md](MOBILE_BUILD.md). Short version: use GitHub Actions from the
GitHub app/browser, or run Gradle in Termux (SDK packages for `android-35` must be
installed first; Termux has no `sdkmanager` by default, so the CI route is strongly
preferred).

## Signing

`android/keystore/tuvr-release.jks` is a dedicated private key that is committed so
that consecutive CI builds share one signature and can be installed over each other
without uninstalling (and without losing settings).

* Alias: `tuvr` · store/key password: `tuvr-release-2026` · RSA 2048 · 30 years
* **If you ever distribute this app publicly, generate your own key and keep it
  private** — anyone with this repository can sign an "update" for it.

## Version bumps

`android/app/build.gradle`:

```gradle
versionCode 2          // must increase for every store/install update
versionName '1.0.1'
```
