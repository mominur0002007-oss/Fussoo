# TU Player (v0.7.1)

Created by: Mominur Islam  
Contact: mominur0002007@gmail.com  
Development: developed by AI (Arena.ai Agent Mode)

A native Android media player foundation built with Kotlin, Jetpack Compose and AndroidX Media3.

## Implemented in this milestone
- Premium cinematic dark interface with Home, Library, Online and Settings tabs.
- Media3 playback engine for local files and compatible HTTP/HTTPS streams.
- Automatic device video-library scanning through MediaStore (after runtime permission is granted).
- Manual folder selection through Android's Storage Access Framework; selected folder access is persisted and scanned recursively.
- Manual file opening remains available without granting broad media-library access.
- Playback speed presets from 0.5x to 4x.
- Engine preference screen: automatic mode and Media3 selection are available; libmpv is visibly marked as not yet integrated.
- GitHub Actions workflow to build a debug APK in the cloud.

## Important engine status
This is **not yet a functioning hybrid Media3 + libmpv build**. Media3 is integrated and is the only available playback engine. The app exposes the requested automatic/manual engine preference UI, but the libmpv option is intentionally disabled until the upstream native library and Android bindings are ported into a dedicated module. The automatic mode therefore uses Media3 for all playback in this milestone. No fake libmpv support is claimed.

## Library permissions and privacy
- Device scanning requests `READ_MEDIA_VIDEO` on Android 13+ or `READ_EXTERNAL_STORAGE` on older Android versions.
- Manual folder selection uses Android's Storage Access Framework and persists access only to the folder the user selects.
- No background upload or media sharing is implemented.

## Build using GitHub Actions (phone-only)
1. Upload the complete contents of this folder to the root of your `TUPlayer` GitHub repository.
2. Open **Actions** and run **Build TU Player APK**, or push to `main`.
3. Download the `TU-Player-debug` artifact from a successful run.
4. Extract and install `app-debug.apk` on your Android phone.

## Build configuration
- Android Gradle Plugin: 8.13.2
- Gradle: 8.13
- Kotlin: 2.2.20
- Android compile SDK: 36
- Android min SDK: 24
- AndroidX Media3: 1.11.1
- AndroidX DocumentFile: 1.0.1

## Known limitations
- The current player view uses Media3. libmpv integration is a later milestone.
- HDR/4K capability depends on the device decoder, display and source format.
- Direct URL playback does not bypass DRM or replace protected streaming services' official apps.
- Device-wide scanning may show only media the user granted access to, particularly with Android's selected-photo/video access modes.
