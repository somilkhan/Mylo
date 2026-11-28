# Scrobble localization regression — 28 November 2026

## Confirmed major defect

Starting branch `feat/scrobble-screen`, commit `1f494445faadd7f6335e96d4752761062e343b2e`, is clean and matches origin. The real baseline Check workflow `46097428907` ran 1,455 Android tests: 17 failed, one skipped. All failures were `LocaleStringsTest.strings_containsAllBaseKeys`.

The missing keys are **plurals**, not strings: `scrobble_stats_scrobble_count`, `scrobble_stats_collapsed_caption`, `scrobble_stats_plays_unit`, `scrobble_stats_artist_count`, `scrobble_stats_album_count`, and `scrobble_stats_track_count`. They live in default `strings.xml` and were omitted from all 19 locale completion files. The previous lead-side validation inspected only strings, whereas the Android JUnit coverage assertion also includes plurals. Resource splitting itself was already supported; no exemption or weaker assertion is justified.

## Implemented correction

Add exactly six plural resources to each existing `scrobble_completion.xml` (114 total), preserving every existing translation. Use appropriate locale quantity forms, retain `%1$s` placeholders and existing terminology. Include both region-specific Traditional Chinese folders and the `values-b+zh+Hant` script fallback. Strengthen JUnit duplicate-name and printf checks to cover plural items as well as strings, and cover all 19 completion folders rather than only the 17 non-English language-menu entries.

## Evidence and release limits

- PASS: full XML parsing, required base/Scrobble key coverage, unique string/plural/array names and printf parity for all 19 locale folders, including every added plural item.
- PASS: `git diff --check` for the locale additions.
- PASS: actual Android SDK 35 AAPT2 compiled the complete production resource directory, including all locale XML, with exit 0 (ARM64 x86-tool emulation). This verifies Android resource syntax, not Gradle integration.
- PASS, isolated: exact locale test source compiled with Kotlin/JUnit and a language-list stub; 57 synthetic cases passed and mutation checks rejected missing/type-substituted/duplicate/blank/bad-placeholder resources. This is not the Android Gradle test.
- Gradle `LocaleStringsTest` is running; its result will be recorded after completion. Dependency resolution for `devDebugUnitTestRuntimeClasspath` completed successfully.
- Translation policy: engineering-complete. Native-speaker review remains a release follow-up, including plural terminology and compact caption grammar. Existing translations have not been indiscriminately replaced.

The fresh sandbox is ARM64, with JDK 17 at `/usr/lib/jvm/java-17-openjdk-arm64` and SDK 37.0 plus the AGP compatibility symlink at `/home/ubuntu/Android/Sdk`. Android x86 tools require emulation here; local Robolectric native graphics and native APK compilation may require existing x86 GitHub CI. No local signing key, SDK configuration or generated artifact is committed.
