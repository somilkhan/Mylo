# MYLO Android / Kotlin / Compose Engineering

## Source map

- Android entry point and top-level Compose UI: `app/src/main/java/com/music/bitchord/MainActivity.kt`. It is large; inspect the smallest relevant region before editing.
- Theme/window resources: `app/src/main/res/values/themes.xml`; Compose owns the application theme.
- Playback and media-session lifecycle: `app/src/main/java/com/music/bitchord/playback/PlaybackService.kt`.
- Android manifest: `app/src/main/AndroidManifest.xml`; review exported components, permissions, deep links and configuration handling together.

## Rules

1. Preserve the current module boundaries and working integrations. Avoid namespace/package migrations as incidental cleanup: the production application ID is `app.mylo.music`, dev is `app.mylo.music.dev`, while source namespace remains `com.music.bitchord`.
2. Keep side effects in lifecycle-aware scopes and service/repository layers, not recomposition paths.
3. For configuration changes, verify Compose state retention and responsive layout; do not casually remove the manifest's declared configuration handling.
4. Respect reduced-motion/accessibility preferences and avoid global animation disabling as a performance workaround.
5. Add a focused regression test for pure logic; use Compose/UI or device tests for behavior that unit tests cannot establish.

## Verification

```bash
./gradlew --no-daemon :app:testDevDebugUnitTest
./gradlew --no-daemon :app:assembleDevDebug :tv:assembleDevDebug :wearos:assembleDevDebug
```

Use JDK 17 and the SDK/NDK/CMake versions required by the project. Check Android CI logs for the exact commit; a compile is not device verification.

## Known traps

- The startup window is black, but the requested anime-character splash animation is not implemented. Do not substitute the inherited metallic headphone logo.
- Internal package names still contain `bitchord`; do not rename them without an explicit compatibility plan.
- Performance/refresh-rate preferences are requests to Android, not guarantees about actual display cadence.
