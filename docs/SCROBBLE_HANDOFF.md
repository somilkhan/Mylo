# Mylo Scrobble continuation handoff

Updated 2026-11-28. This is the authoritative resume point for the next contributor.

## Repository state

- Repository: `https://github.com/somilkhan/Mylo.git`
- Branch: `feat/scrobble-screen`
- Working tree at handoff: clean
- Verified continuation baseline: `1f494445faadd7f6335e96d4752761062e343b2e` (HEAD matched origin; clean before edits).
- Latest pushed HEAD: `d3c3ec4c72b7e5bd23e1e677ed770a9060f910fe` (handoff update; code checkpoint immediately before it is `8f17239fc381d5dcfb0119873ef68c0ed4b5cc2c`).
- Pull request already open: `https://github.com/somilkhan/Mylo/pull/15`
- Do not create a replacement project, rename packages, or switch away from this branch.
- The branch contains the pre-existing stable CI debug-keystore signing fixes. Do not commit keystores, secrets, or local.properties.

## Commits delivered during this continuation

- `5fc31d19` — records continuation scope and baseline evidence.
- `c3ba1642` — localizes generation progress/errors, hardens Last.fm/YouTube source rules, bounds exclusion paging, protects against account changes and cancellation races, and adds 4,085 generated translations across 19 existing locale folders with format-token checks.
- `25638515` — saves the generation audit and its explicit verification gates.
- `38029159` — completes playlist detail coverage/edit safety, cover URI cleanup and validation, IO-bound exports, stale-index/search fixes, transaction regressions, and the real Compose/Robolectric detail screenshot fixtures. It also saves `docs/audits/SCROBBLE_PLAYLISTS.md`.
- `8f17239f` — compact Stats dashboard and shared Scrobble control accessibility/reduced-motion hardening; pushed to this branch.
- `d3c3ec4c` — documents exact pushed checkpoint and validation limits; documentation-only.

The earlier redesign commits remain the product baseline: Stats is Last.fm-only with dashboard folding/history paging; Scrobble has no gradient hub theme and no Replay entry; Generate and Playlists use the redesigned Mylo style.

## What is implemented

Generation now resolves provider progress/errors through Android resources, fails closed for incomplete mandatory exclusion sources, preserves Last.fm source semantics, checks connected-account/session identity before saving, clears stale seed searches, handles retry/cancellation intent safely, and keeps playable-track resolution in the existing YouTube Music layer.

Playlist detail now has real production-composable screenshots for populated/current playback/light theme/empty/duplicate tracks/anchored menu/export/add-mode Back/play-back callbacks. Playlist mutations are serialized and failure-safe; rename/delete/add/reorder/remove/cover operations do not publish false success, duplicate IDs are normalized, searches are cancellation-aware, exports run off the main thread and use safe deterministic cache filenames, and cover URI grants are released only when no stored playlist still references them.

Every current non-English locale has the completed scrobble catalogue in a dedicated `scrobble_completion.xml`. `LocaleStringsTest` now reads split resource files and checks missing keys and printf placeholder parity. Existing translations are preserved rather than replaced.

## Current localization checkpoint

The fresh continuation confirmed the Android failure on the exact baseline: Check run `46097428907` completed 1,455 tests with 17 failures and one skip. Six Stats **plural** keys were missing from all 19 locale folders; the old lead XML validator checked only strings. The additive correction supplies 114 plural resources, without exemptions or replacement of existing copy. All 19 folders pass XML/key/duplicate-name/string-and-plural printf checks. Read `docs/audits/SCROBBLE_LOCALIZATION.md` for the root cause and current evidence. Android Gradle verification is pending at this checkpoint; do not call static resource checks an Android unit-test pass.

Locale checkpoint `bd5a4fb6397944903ab6fca36b59db62b591d4ca` is pushed. Actual AAPT2 compilation of all production resources passed, as did dependency resolution. The focused Gradle test is running. The next focused integration unit masks the secondary Settings password field, removes raw Last.fm sign-in diagnostics, preserves cancellation before account success, and retains real screenshot/JUnit artifacts in existing CI. Signing configuration/scripts are untouched. Its static diff and workflow checks pass; read `docs/audits/SCROBBLE_INTEGRATION.md`. No device/provider behavior is asserted verified.

Checkpoint `8f17239fc381d5dcfb0119873ef68c0ed4b5cc2c` is pushed. The Stats overview is compact by default (roughly 150dp target; exact rendered height is pending screenshot inspection), shows summary metrics, and expands/collapses the full chart on demand. Recent remains the default list. Stats cache/account/cancellation/paging/date/reduced-motion fixes and focused regression/screenshot fixtures are added. Shared controls preserve 48dp touch targets, subtle `.96–.97` pointer feedback, reduced-motion/system-scale handling, selected/busy semantics, and minimal existing glass rim. Focused audits: `docs/audits/SCROBBLE_STATISTICS.md`, `docs/audits/SCROBBLE_SHARED_UI.md`, and `docs/audits/SCROBBLE_INTEGRATION.md`.

Validation limits as of checkpoint: the exact-a820 CI run verified the **LocaleStringsTest passes** (57 tests, zero failures) and uploaded actual production Stats/Generate screenshots, but its full suite had one unrelated `SourcesTest` failure. A local Gradle run first caught and fixed icon/API-test import errors, then a misplaced Stats list-mode brace. The serialized fresh-source quality gate has restarted; until its completion is recorded, do not claim Stats/shared Compose tests or full unit tests pass. No CI, APK build, physical-device, real Last.fm, ListenBrainz, TalkBack, keyboard, export-provider, or GPU-blur verification is claimed for `8f17239`.

This continuation's sandbox is ARM64 and newly provisioned: JDK 17 is `/usr/lib/jvm/java-17-openjdk-arm64`, SDK is `/home/ubuntu/Android/Sdk`. The prior handoff's amd64 JDK path and installed-tool claims describe its old sandbox, not this one. Stable CI signing remains unchanged.

## Validation evidence and exact limits

Passed before the latest handoff:

- XML parsing, duplicate-resource checks, scrobble-key coverage and printf-placeholder parity for all 19 locale folders in the lead-side validation script.

The first real Android test run was also completed against clean snapshot `c3ba1642` (65 tests, 17 failures). `LocaleStringsTest` failed at its missing-key assertion for `ja`, `ru`, `zh-CN`, `zh-TW`, `he`, `it`, `tr`, `th`, `vi`, and `ko` (and seven other parameterized locale cases); the run ended `BUILD FAILED`. This is a real failure, not a warning. It predates playlist commit `38029159` and must be rerun against current remote HEAD after reconciling resource-file coverage. No Android test result is currently green for the final branch.
- `git diff --check` for committed changes.
- Generation owner’s isolated Kotlin/JUnit harness: 17 paging/rules tests plus 3 commit cancellation/active-success checks. This is not an Android or Compose build.
- Playlist owner’s isolated harness: 21 playlist order/edit/export/detail/transaction tests plus Kotlin syntax parsing and resource-reference checks. This is not an Android or Compose build.
- Gradle dependency resolution for `:app:devDebugRuntimeClasspath`.

Not yet a claim:

- The real Android Gradle unit-test run and Compose screenshot class have not been confirmed successful for remote HEAD.
- Full `:app:testDevDebugUnitTest`, `:app:assembleDevDebug`, `:tv:assembleDevDebug`, and `:wearos:assembleDevDebug` have not been confirmed successful for remote HEAD.
- No physical phone/TV/Wear device run, real Last.fm account, live artwork loading, native blur rendering, or touch/back/export-provider behavior has been verified.
- A clean-snapshot Gradle test was run against `c3ba1642`: compilation completed, but `LocaleStringsTest` failed in 17 parameterized locale cases and the Gradle task ended `BUILD FAILED`. It must not be treated as evidence for remote HEAD or the later playlist commit.

The local sandbox has JDK 17, Android SDK `platforms;android-37.0` plus the `android-37` compatibility symlink, build tools, platform tools and CMake 3.22.1. `local.properties` is ignored and points at that SDK. CI uses the same SDK shape and JDK 17. Local APKs do not carry the stable CI debug key unless the CI secret keystore is installed; do not claim local APK upgrade compatibility.

## Next contributor runbook

1. Start by fetching and verifying the branch:

   ```bash
   git fetch origin feat/scrobble-screen
   git checkout feat/scrobble-screen
   git reset --hard origin/feat/scrobble-screen   # only if the working tree is intentionally disposable
   git status -sb
   ```

2. Read this file, `docs/SCROBBLE_CONTINUATION.md`, `docs/audits/SCROBBLE_GENERATION.md`, and `docs/audits/SCROBBLE_PLAYLISTS.md`. Read repository guidance in `skills/android-kotlin-compose/SKILL.md` and `skills/testing-release/SKILL.md`.

3. Run the narrow Android checks first, then the full test/build in the repository’s documented order:

   ```bash
   export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
   export ANDROID_HOME=/home/ubuntu/Android/Sdk
   ./gradlew --no-daemon :app:testDevDebugUnitTest --tests 'com.music.bitchord.LocaleStringsTest' --tests 'com.music.bitchord.data.generate.*' --tests 'com.music.bitchord.data.spotify.*' --tests 'com.music.bitchord.ui.scrobble.*'
   ./gradlew --no-daemon :app:testDevDebugUnitTest
   ./gradlew --no-daemon :app:assembleDevDebug :tv:assembleDevDebug :wearos:assembleDevDebug
   ```

4. If a test fails, fix only confirmed regressions, rerun the affected test, then the full suite. Inspect screenshot PNGs generated under `app/build/screenshots/` after the screenshot class runs. Do not fabricate screenshots or mark device behavior verified from compilation.

5. Before changing UI, inspect `LiquidGlass.kt`, existing Haze usage, reduced-motion behavior, `ScrobbleUi.kt`, Stats, Generate and Playlist screens together. Keep the existing black/Mylo visual language; no gradient theme, no generic glass on every row, no new dependency unless essential. The remaining shared-glass and Stats/integration audit agents may still have work in the shared sandbox, so first check `git status`, `git log`, and any newer remote commit before editing.

6. Push every coherent checkpoint to `origin/feat/scrobble-screen`. Use explicit file lists, never `git add -A`; fetch before pushing and never force-push. Include the exact commit SHA and test status in the next handoff update.

## Known residual risks

- Generation title uniqueness is selected before the playlist-store mutex; concurrent external saves can still race. A storage-owner atomic uniqueness policy would be a separate change.
- Legacy non-suspending playlist removal still uses its existing fire-and-forget SharedPreferences contract.
- External document providers, picker process death, URI permission revocation, and device touch/back behavior need hardware/device validation.
- Last.fm data is remote and can change during a generation; no transactional remote snapshot exists.
- The 50-page/timeout bounds intentionally fail rather than silently generate from incomplete exclusion feeds.
- The generated translations are resource-complete and placeholder-safe, but native-speaker product review is still desirable before release.
- Existing unrelated compiler deprecation/warning output remains; do not expand this scrobble handoff into an unrelated warning cleanup.

## Required final reporting vocabulary

Use these statuses separately: `IMPLEMENTED`, `COMMITTED`, `BUILT`, `TESTED`, `CI PASSED`, `DEVICE VERIFIED`, `NOT VERIFIED`, and `BLOCKED`. Compilation is not device verification, and an earlier green CI run is not evidence for a newer commit.
