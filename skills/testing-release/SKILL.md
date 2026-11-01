# MYLO Testing / CI / Release

## CI workflows

- `.github/workflows/android-ci.yml`: Android phone, TV and Wear OS debug builds, Android unit tests, desktop compile/tests, debug APK artifacts.
- `.github/workflows/backend-ci.yml`: Go backend unit/protocol tests with race detector and `go vet`.
- `.github/workflows/web-ci.yml`: web workspace typecheck, tests and build.

Always verify the workflow run for the commit under review; a prior green run is not evidence for a newer commit. The Android workflow treats artifact-upload quota failure as a warning after build/test success, so inspect the build/test steps separately from artifact availability.

## Local verification order

1. Run the narrow regression test.
2. Run `./gradlew --no-daemon :tv:compileDevDebugKotlin` to verify the shared phone-source TV variant and generated splash drawable without assembling an APK.
3. Run `./gradlew --no-daemon :app:testDevDebugUnitTest`.
4. When device/release artifacts are needed, run `./gradlew --no-daemon :app:assembleDevDebug :tv:assembleDevDebug :wearos:assembleDevDebug`.
5. If relevant, run desktop, backend and web checks from `CONTRIBUTING.md`.
6. Review the diff, generated artifacts, logs and final commit; fix failures and rerun affected checks.

## Private history and backup rules

Scrobble history is stored in `filesDir/scrobble_history.tsv` with an atomic sibling `scrobble_history.tsv.tmp`. Exclude both files from Android cloud backup and device transfer in `backup_rules.xml` and `data_extraction_rules.xml`. Revisit exclusions whenever the history store adds a journal, temporary, or sidecar file. Saved Scrobble mixes are a separate versioned SharedPreferences store; preserve `Song.localUri` and `Song.localPath` in its codec and test round-tripping local-file identity. Do not clear the stored JSON when decoding an unknown future schema.

## Report statuses independently

- IMPLEMENTED: code exists.
- COMMITTED: exact commit is on the intended branch.
- BUILT: relevant build completed successfully.
- TESTED: named automated tests completed successfully.
- CI PASSED: required workflow run completed successfully for that commit.
- DEVICE VERIFIED: behavior was exercised on real hardware.
- BLOCKED: a concrete external dependency prevents progress.
- NOT VERIFIED: evidence is unavailable or incomplete.

Do not infer playback correctness, UI quality, installability, physical audio behavior or device performance from compilation alone. Release signing uses local `keystore.properties`; never commit signing material. Preserve GPL-3.0 notices and release source obligations.

## Kotlin Multiplatform JVM test coverage

- The desktop app test task does not execute test tasks from its project dependencies. CI and local verification must run :shared:jvmTest :sharedUi:jvmTest :desktopUi:jvmTest explicitly before :desktopApp:test.
- The Android app suite is separate: run :app:testDevDebugUnitTest. A successful desktop test task does not cover Android-only code, and an Android unit-test pass does not execute the shared JVM-only test source sets.
