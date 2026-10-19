# MYLO

MYLO is an Android-first music player project being developed from an existing Kotlin/Compose music-player foundation. The repository currently contains Android, TV/Wear OS, desktop, and shared modules; the Android experience is the primary target for MYLO.

> **Development status:** early fork/rebrand. Automated build and unit-test checks are being established for the supported targets. Device-level playback, background-service, and installation testing is still required before calling this a release.

## Current codebase capabilities

The inherited implementation includes work for:
- YouTube Music search and playback
- On-device audio library and offline downloads
- Playlists, queue management, and lyrics
- Account integrations and listening-together functionality
- Shared Kotlin modules and companion TV/Wear OS/desktop targets

Availability and stability of these features in MYLO remain subject to build and regression testing.

## Build Android

Requirements:
- JDK 17
- Android SDK with licenses accepted
- Android SDK/NDK/CMake components required by the Gradle configuration

Build the development debug variant:

```bash
./gradlew --no-daemon :app:assembleDevDebug
```

Run unit tests:

```bash
./gradlew --no-daemon :app:testDevDebugUnitTest
```

The Android module is included when Gradle can locate an Android SDK through `local.properties`, `ANDROID_HOME`, or `ANDROID_SDK_ROOT`.

## CI

The CI workflow builds the Android phone, TV, and Wear OS development debug variants, runs Android unit tests, compiles the desktop app, and runs desktop unit tests on pushes to `main` and pull requests. Successful Android builds upload debug APKs as a 14-day workflow artifact. Passing CI is necessary but does not replace playback, background-service, download, Bluetooth, installation, or real-device testing.

## License and attribution

MYLO is derived from **BitChord**, originally published by kushagrasinghx and contributors:

- Upstream: https://github.com/kushagrasinghx/BitChord
- License: GNU General Public License v3.0 (GPL-3.0)

This fork retains the upstream license and notices. Modifications and redistributed builds must comply with GPL-3.0, including applicable source-code and license obligations. MYLO is an independent project and is not affiliated with the upstream maintainers.
