# Contributing to MYLO

MYLO is an Android-first music player derived from BitChord. Keep changes focused, preserve upstream notices, and verify behavior instead of inferring it from a successful compile.

## Development setup

- Android: JDK 17, Android SDK platforms/build tools required by the Gradle configuration, NDK and CMake 3.22.1 for native audio code.
- Backend: use the Go version declared by `backend/go.mod` (currently Go 1.27.0).
- Web: Node.js 22 and pnpm 12.11.2, as pinned by `.github/workflows/web-ci.yml`.

## Build and test

Run commands from the repository root.

Android phone, TV and Wear OS debug builds:
```bash
./gradlew --no-daemon :app:assembleDevDebug :tv:assembleDevDebug :wearos:assembleDevDebug
./gradlew --no-daemon :app:testDevDebugUnitTest
```

A focused Android test:
```bash
./gradlew --no-daemon :app:testDevDebugUnitTest --tests "com.music.bitchord.playback.audio.DirectAudioStreamingRegressionTest"
```

Listen Together backend:
```bash
cd backend
go test -count=1 -race ./...
go vet ./...
```

Web:
```bash
cd web
pnpm install --frozen-lockfile
pnpm typecheck
pnpm test
pnpm build
```

## Architecture and review

- Keep UI, repositories, domain logic and playback-service responsibilities separate.
- `MainActivity.kt` is large; prefer focused extractions or targeted edits over broad rewrites.
- Media3 playback is owned by `PlaybackService`; verify queue and player-state behavior as well as UI state.
- Add regression tests for behavioral fixes. Run the narrow test first, then relevant module checks.
- Audio telemetry is not proof of physical endpoint behavior. Distinguish source metadata, decoder/DSP formats, AudioTrack, AudioFlinger/HAL and actual hardware measurements.
- Do not add endpoints, credentials, dependencies or working-service claims without evidence.
- Do not claim device verification unless the app was exercised on a real device.

## CI and release

The repository has separate Android/Desktop, backend and web workflows under `.github/workflows/`. Inspect the run for the exact commit being evaluated. Android CI builds debug variants and unit tests; it does not prove playback, background execution, Bluetooth routing or UI quality on hardware.

Release signing uses local `keystore.properties` and is not committed. Follow GPL-3.0 and retain applicable upstream license and attribution notices.
