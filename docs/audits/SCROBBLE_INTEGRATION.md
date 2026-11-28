# Scrobble integration and signing audit — 28 November 2026

## Scope and confirmed fixes

Read-focused review of Scrobble routing, Settings provider controls, Last.fm authentication, Android/TV/Wear integration, shared/desktop separation, and the existing debug-signing workflows. No backend, audio, package, module or theme migration.

| Severity | File/location | Finding and correction |
| --- | --- | --- |
| Major | `MainActivity.kt`, Last.fm sign-in callback; `ui/screens/SettingsSheet.kt`, Last.fm sign-in dialog | Raw provider/network exception messages were shown directly. Use existing localized `login_failed` copy instead. Cancellation is rethrown, and coroutine activity is checked after authentication before publishing credentials/account/success. Guard duplicate sign-in submission. |
| Major | `ui/screens/SettingsSheet.kt`, password field | The secondary Settings dialog displayed the Last.fm password as plain text. Add native password transformation/keyboard options and disable both fields while submitting. The primary existing `LastfmLoginAlert` already masks passwords and disables edits. |
| Minor verification gap | `.github/workflows/android-ci.yml` | Successful real Compose screenshots were not retained for inspection. Add a small, three-day `android-test-evidence` artifact containing existing screenshots and JUnit reports, even on test failure. Upload failure does not mask the test result. No image generation or fabricated UI. |

## Product and integration boundaries retained

- `MainActivity.kt` routes Scrobble Stats, Generate and Playlists through the existing dedicated route and floating tab navigation. The Stats profile action encodes the username in the Last.fm URL. Replay remains a separate app-wide route and has no Scrobble action/entry.
- Stats repository/viewmodel consume Last.fm only; local playlists remain save/playback destinations, not statistical input. ListenBrainz remains a separate token-based ScrobbleManager submission provider and Settings integration; this pass does not imply a new ListenBrainz statistics dashboard or combine provider totals.
- No Scrobble gradient/hub theme is restored. Existing restrained card/page typography and global liquid-glass pipeline remain.
- Source namespace stays `com.music.bitchord`; production/dev application IDs remain `app.mylo.music` / `app.mylo.music.dev`. New source changes stay under Android `app`; no Android-only imports were added to shared/desktop code.

## Signing review — no changes warranted

`.github/scripts/debug-keystore.sh` installs the fixed secret debug key, validates its alias/password, exports `MYLO_DEBUG_KEYSTORE`, and records its certificate fingerprint. Phone/TV/Wear Gradle signing configurations use that key when supplied. CI verifies every APK signer against the recorded SHA-256. Fork PRs use the established restricted-secret fallback. The signing script, keystore configuration, secrets and verification steps are unchanged. Local APKs use the sandbox's key and are **not** asserted to update existing CI-installed APKs.

## Verification and residual risks

- PASS: scoped `git diff --check`, workflow YAML parsing and exactly one evidence-upload step; static source checks find no `lastfmError = ...message` assignments after the fix.
- Android compilation, exact-SHA CI/test/build evidence and screenshot inspection are parent-owned and recorded in the final handoff after completion.
- NOT VERIFIED: real sign-in, invalid-password/offline behavior, cancellation while an alert/activity closes, token revocation, real Last.fm/ListenBrainz submissions, physical phone/TV/Wear interactions, system keyboard behavior and native GPU blur. A compile or fixture is not evidence for these.
- Existing account/session persistence and global runtime initialization remain; no live provider transaction or authentication redesign is introduced. App-store/production signing is outside this dev-debug quality pass.

## Positive patterns retained

Encrypted settings own credentials; Last.fm transport returns typed failures and rethrows coroutine cancellation. Login fields remain composition-local and are not logged. Existing CI certificate checks provide concrete upgrade-signing evidence rather than assuming a keystore path is honored. The Scrobble route and global Replay route are separate, and shared/desktop builds retain their own platform boundaries.
