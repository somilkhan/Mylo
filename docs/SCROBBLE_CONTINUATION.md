# Scrobble continuation — 28 November 2026

Continue `feat/scrobble-screen` from `49f98c8950082df622e15c3dd678ccc1e665f98a`. The exported conversation names `somilkhan/Mylo` and this branch; do not create a replacement project or migrate packages. The user explicitly requests incremental pushes so work does not remain only in the working directory.

## Accepted scope

Scrobble is dedicated to Last.fm. Preserve the previous Stats dashboard, folding behavior, period selector, recent-history paging and Generate/Playlists redesign. Preserve Mylo's native visual identity, use the existing liquid-glass pipeline appropriately, remove the Scrobble gradient theme, and keep Replay unlinked from Scrobble. The unrelated app Replay feature remains intact. Preserve all stable debug-signing fixes.

The previous agent reported three remaining items: the playlist-detail screenshot, resource-backed generation progress/error messages, and translations for the new English strings. This continuation also includes bug hunting and UI/UX consistency auditing of the scrobble feature and its integration.

## Outcomes

- [ ] Generation progress and error messages use localizable resources, including engine callbacks, and recovery/cancellation/account/exclusion paths are audited and fixed where defects are confirmed.
- [ ] Missing new scrobble strings are completed across the 19 existing locale folders without overwriting existing translations; keys, formatting placeholders and Android resource escaping are validated.
- [ ] The playlist detail page has a real Compose/Robolectric screenshot fixture with clearly labeled sample data; playlist CRUD, cover, export and navigation paths are audited and confirmed defects fixed.
- [ ] Statistics account/cache/paging/chart/fold state paths are audited; confirmed defects are fixed without reintroducing local-play merging or Replay.
- [ ] Shared scrobble controls match Mylo's themes and liquid-glass behavior, with readable fallback surfaces, touch targets, semantics and reduced-motion handling.
- [ ] Scrobble integration/settings/theme/signing regressions are reviewed and recorded, with explicit residual risks and checks actually run.
- [ ] Coherent fixes are committed and pushed incrementally to `feat/scrobble-screen`, followed by named compilation/tests and an audit report. Real hardware, live Last.fm behavior and native GPU rendering must not be claimed unless exercised.

## Starting evidence

GitHub Actions for the baseline commit were completed successfully: Android CI run `46089107986`, Check run `46089108030`, TV run `46089107951`. These are evidence for the baseline only, not this continuation.

The current sandbox has JDK 17 and the SDK configuration expected by Android CI (`platforms;android-37.0` with the `android-37` symlink). Dependency resolution `:app:dependencies --configuration devDebugRuntimeClasspath` succeeded. Local builds have no Last.fm secrets and use the machine's debug key; use the existing CI workflow for a stable-signed device APK rather than claiming a local APK updates the installed CI build.

## Ownership during implementation

Generation, playlist-detail, statistics and shared-glass changes have non-overlapping file ownership. A separate read-only reviewer covers scrobble integration and signing. The lead owns localization output, builds, commits/pushes and final evidence. Detailed findings will be consolidated in `docs/SCROBBLE_AUDIT.md` when validation completes.
