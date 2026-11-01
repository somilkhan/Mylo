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
./gradlew --no-daemon :tv:compileDevDebugKotlin
./gradlew --no-daemon :app:testDevDebugUnitTest
./gradlew --no-daemon :app:assembleDevDebug :tv:assembleDevDebug :wearos:assembleDevDebug
```

Use JDK 17 and the SDK/NDK/CMake versions required by the project. Check Android CI logs for the exact commit; a compile is not device verification.

## Scrobble navigation contract

- Scrobble is an overlay/page state owned by MainActivity.kt (page key scrobble), not a new bottom-navigation destination. Keep Replay as its own existing Explore experience.
- The Home FAB is visible only when selectedTab == TAB_HOME and no competing full-screen overlay/detail is active. Preserve the scrobbleFabVisible rule; do not duplicate it with a second visibility policy.
- The FAB is SCROBBLE_FAB_SIZE (62 dp). The bottom pill reserves its width plus a 10 dp gap; keep that slot, measured floating-bar inset, mini-player stack and window insets in sync when changing navigation geometry.
- While Scrobble is open, reuse the existing bottom pill for Stats / Generate / Playlists. Do not introduce a second navigation bar or move Replay under Scrobble.
- Pass the same reduced-motion preference to FAB, page transitions, metrics and source-split animation. The FAB snaps instead of springing when reduced motion is enabled; new Scrobble animations must do the same.
- ScrobbleFabTest proves the pure Home/overlay visibility rule. ScrobbleFabComposeTest runs on the API 36 emulator and verifies accessible visibility, click semantics and a minimum 48 dp target; neither proves MainActivity placement, mini-player/scroll interaction, glass/standard fold behavior, real-device contrast or animation quality.

## Known traps

- The approved splash artwork is committed at `app/src/main/branding/mylo-logo.webp.base64`. Both `app` and `tv` register their own `generateMyloSplashArtwork` task and local generated resource directory; TV's task reads the single source from `rootProject/app/src/main/branding`. Do not add the app module's generated resource directory to TV: it creates a duplicate `mylo_logo` during resource merging. `MyloSplash.kt` animates the flattened character/emblem/thread/wave composition from above with a settling swing and respects the system animator-duration scale. Keep this exact artwork; the inherited metallic headphone SVGs are not substitutes. The source is a single flattened image, so do not claim independent character-layer animation.
- Scrobble's page key is `scrobble` and its Home FAB is the motion origin. Keep the page enter/exit scale transform anchored near the bottom-right FAB; section changes slide in the selected pill direction. Every Scrobble transition, count-up and source-split animation must snap when `AppSettings.reduceAnimation` is enabled.
- Internal package names still contain `bitchord`; do not rename them without an explicit compatibility plan.
- Performance/refresh-rate preferences are requests to Android, not guarantees about actual display cadence.
