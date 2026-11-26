# Building TU VR with nothing but a phone

The whole point of this project is that it can be developed and shipped from an
Android phone. Two realistic routes exist; the recommended one needs no local
toolchain at all.

## Route A — GitHub Actions (recommended)

This is exactly how the committed APKs in this repository are produced.

1. Install the **GitHub** app (or use the browser) and sign in.
2. Change anything in `android/**` and commit — from the GitHub web editor, the GitHub
   app, or an editor such as Acode / Termux.
3. Open **Actions → TU VR APK**. A run starts automatically for pushes that touch the
   build files; otherwise open the run and press **Run workflow**.
4. Wait ~2–4 minutes. The run’s **Artifacts** section has `TUVR-APK`.
5. Download it. On Android you get a `.zip`; extract it with any file manager
   (Files by Google, Material Files, …) and tap `TUVR-1.0.0-release.apk`.
6. Android asks for permission to install unknown apps for the app that is opening the
   APK — allow it once, then install/update.

Notes

* The release APK is signed with the committed key, so later builds install as updates.
* If the build fails, the errors are committed to `ci/last-build.log` in the branch,
  which you can read directly in the GitHub app. The lint summary of a successful run
  is in `ci/lint.txt`.
* No PC, no USB, no ADB, no Android Studio is needed anywhere in this route.

## Route B — Termux (local build on the phone)

Possible, but heavier and slower; use it only if you want fully offline builds.

```bash
pkg update
pkg install openjdk-17 gradle git unzip
# Android SDK command line tools (aarch64 host build is not official; most people
# instead extract the SDK from Termux packages or copy it from another device)
```

Realistically the Android SDK has to be fetched with `sdkmanager` from the
`commandlinetools` zip, which requires `--sdk_root=$HOME/android-sdk`, accepting the
licences (`yes | sdkmanager --licenses`), and installing
`"platforms;android-35" "build-tools;35.0.0" "platform-tools"`. Then:

```bash
cd android
gradle :app:assembleDebug --no-daemon
```

Expect 10–30 minutes on a Tiger T606 and ~3 GB of free storage. If Gradle runs out of
memory, lower `org.gradle.jvmargs` in `gradle.properties` to `-Xmx1024m` and add
`org.gradle.workers.max=1`.

## Installing an APK you built on the phone

1. Files app → Downloads → the APK.
2. Allow "install unknown apps" for that file manager when prompted.
3. Install. If a previous build with a **different signature** is installed, Android
   refuses the update: uninstall TU VR first (this erases its settings).

## Editing the source on a phone

* **Acode** or **Squircle CE** for Java/XML editing, GitHub app for commits.
* Handy checks without a compiler: search for `R.string.` / `R.id.` usages and make
  sure the resource exists — a typo is the most common build break in this project.
* Keep `ci/lint.txt` open in another tab while working; it lists unused resources and
  API-level mistakes that would otherwise only show up at runtime.
