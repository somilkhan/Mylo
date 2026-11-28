# Scrobble playlists completion audit

## Summary and verdict

This unit completes the existing Last.fm Scrobble playlist list/detail experience, keeps Mylo's native release header and shared controls, adds real production-composable detail screenshots, and fixes confirmed edit/navigation/export/cover defects. The source unit is frozen and safe for a scoped commit. **Full Android compilation, Robolectric screenshot execution, and device visual validation remain pending**; this report does not claim they passed.

The audit began from the assigned baseline `49f98c8950082df622e15c3dd678ccc1e665f98a` on `feat/scrobble-screen`. Parent-owned generation and locale commits continued independently. No generation implementation, locale file, `strings.xml`, SDK/build configuration, or signing configuration was modified by this unit. No new theme, gradient page background, or Replay link was introduced.

## Files safe to commit

- `app/src/main/java/com/music/bitchord/ui/scrobble/ScrobblePlaylistsScreen.kt`
- `app/src/main/java/com/music/bitchord/ui/scrobble/PlaylistExportDialog.kt`
- `app/src/main/java/com/music/bitchord/data/spotify/LocalPlaylistStore.kt`
- `app/src/test/java/com/music/bitchord/ui/scrobble/ScrobbleGeneratePlaylistsScreenshotTest.kt`
- `app/src/test/java/com/music/bitchord/ui/scrobble/ScrobblePlaylistDetailRulesTest.kt` (new)
- `app/src/test/java/com/music/bitchord/data/spotify/LocalPlaylistCodecTest.kt`
- `app/src/test/java/com/music/bitchord/data/spotify/LocalPlaylistMutationTest.kt` (new)
- `docs/audits/SCROBBLE_PLAYLISTS.md` (copy of this audit)

## Confirmed defects and implemented fixes

### Playlist detail, editing, and navigation

`ScrobblePlaylistsScreen.kt` contains the detail page: `PlaylistDetail`, approximately line 769. It still renders `LocalPlaylistReleaseHeader` from `ui/screens/DetailScreen.kt`, followed by the real shared `SongRow` components. The native header file did not need modification.

**Major — duplicate tracks could crash lazy-list rendering.** Imported playlists legitimately retain repeated YouTube IDs, but detail keys were only playlist ID plus video ID. `playlistTrackRowKeys` now adds a deterministic occurrence index, preserving distinct rows and stable keys when unrelated tracks move or a title changes. Existing remove/move operations still target one requested slot, not every duplicate. Dedicated tests cover these rules.

**Major — concurrent legacy removal could overwrite add/reorder results.** The non-suspending library removal overload bypassed the store mutex. It now takes the same mutex immediately when idle, or queues the operation on a bounded-operation coroutine scope when another transaction owns the lock. Existing public signatures and immediate idle behavior remain intact. The new transaction test holds a write in progress and verifies a queued removal retains the concurrently added track.

**Major — custom shelf ordering could use stale absolute destinations.** An in-flight generated playlist insert can shift store indexes between a menu click and its write. The shelf now expresses up/down as a direction, and the new `movePlaylistBy` resolves the source and destination under the store mutex. The existing absolute `movePlaylist` API remains intact. Custom sort remains unpinned and unfiltered persisted order; reordering is still unavailable under automatic sort or search.

**Moderate — locate used the wrong item offset with add results.** The add-results card inserts an additional lazy-list item before tracks. `playlistTrackListIndex` includes that conditional item; locate also uses immediate scrolling under reduce-motion. Tests cover offsets with and without results.

**Moderate — stale add results and misleading loading state.** Results now clear immediately when the query changes, loading starts during debounce, and cancelled searches cannot clear their successor's loading state. Existing cancellation rethrow and `ensureActive` checks remain. An in-flight add ID disables repeated taps and shows the shared compact spinner; the store remains the final duplicate guard.

**Moderate — IDs were validated trimmed but stored untrimmed.** `appendUniquePlaylistSong` now normalizes the stored candidate ID and compares trimmed existing identities before adding. Regression tests cover both directions of whitespace duplication.

**Moderate — rename/delete could submit repeatedly or dismiss while saving.** Shared alert actions disable during a transaction, and dismiss/cancel are guarded. Blank/duplicate rename validation, failure feedback, and delete confirmation remain. Storage failure does not close the alert. Transaction tests verify failed rename/delete/add/reorder/remove/cover writes leave the published playlist unchanged.

**Moderate — Back and list continuity.** System and toolbar Back close the nested add mode first. Returning from detail retains the parent-owned list scroll state. A deleted/missing selected ID is cleared after successful storage state reconciliation. Anchored shelf menus resolve the latest playlist metadata/index using a remembered snapshot rather than stale row data.

### Covers and exports

**Major — shared cover URI grants could be revoked incorrectly.** Delete, cover replacement, reset, and failed-new-cover cleanup now release a grant only if no remaining stored playlist references it. Delete/reset read the current stored cover rather than relying solely on the opening menu snapshot. Save/reset serialize their UI operations.

**Moderate — unreadable or invalid covers could be persisted as successful.** Chosen image bounds are validated from a readable input stream on `Dispatchers.IO`, without allocating a full bitmap, before storing the new URI. Permission/image/picker failures use the existing localized cover error. Existing covers remain unchanged after failure. Final self-review specifically checked that bounds-only `decodeStream` returns null and that the validation block therefore returns `Unit`, not the decoded bitmap.

**Moderate — export blocked the main thread and reported errors as successes.** CSV/M3U serialization and output-file writes now run on `Dispatchers.IO`. Save/share actions guard repeated submission; save uses the selected playlist snapshot. The existing callback remains compatible, with optional `onError` separating error presentation from success notices. Picker/chooser/file failures are localized and do not crash the page. Document writes explicitly truncate with mode `wt`.

**Moderate — imported IDs could create unsafe share paths.** Export file prefixes use a deterministic UUID derived from playlist identity, never the raw imported ID. Existing title sanitization now also replaces control characters. FileProvider access remains limited to `cache/shared`, with MIME type, ClipData, and read-grant flags intact. Filename tests cover newlines, tabs, and NUL.

**Minor — raw storage diagnostics were exposed in the UI.** The retry card retains its localized error title without echoing raw storage exception text. The store's diagnostic flow remains available for diagnosis.

## UI consistency review

| Before | After | Why |
| --- | --- | --- |
| Menus had anchors but no usable page haze source | Scroll content supplies Haze; shared anchored menus receive the same state | Real native frost with existing reduced-blur/opaque fallback, not a custom theme |
| Add pill used only its existing flat fill | Existing pill plus shared `liquidGlassRim(CircleShape)` | Lightweight glass treatment over a stable page; no new backdrop/lens cost per row |
| Locate always animated | Immediate locate under reduce-motion | Respects the page's motion setting |
| Detail screenshot coverage missing | Actual `PlaylistDetail` and native header rendered in the real test activity | No recreated UI or fabricated screenshot pixels |

The shared `IosMenuPopover` continues to own window-coordinate anchoring, origin-aware animation, scrim, and menu Back handling. No bespoke menu-position algorithm was introduced. Glass support and reduced-dynamic-blur behavior remain delegated to shared components.

## Tests and evidence

Executed **without Gradle** using the cached Kotlin 2.0.20 compiler and JUnit 4.13.2:

- **21 isolated tests passed**, combining existing pure playlist order/edit/export tests, new normalization/control-character/duplicate/stale-index tests, two detail-rule tests, three transaction tests, and three existing legacy removal tests.
- The isolated harness compiles the actual `LocalPlaylistStore.kt` and `LocalPlaylistSort.kt` plus extracted actual detail-rule functions. Android interfaces, Song, and the JSON codec are stubs. Consequently this validates rule/transaction logic, not Android integration or JSON codec compatibility.
- Kotlin PSI syntax parsing passed for both modified composable implementation files and the screenshot test file. This is syntax validation, not Compose compiler/type validation.
- All **85** string references in those three scoped files resolve in the default resources.
- Scoped `git diff --check` passed before the final commit.

Evidence: `/home/ubuntu/handoff/check-playlists.py`, `/home/ubuntu/handoff/playlists-isolated-check.log`, and `/home/ubuntu/handoff/playlists-final.diff`.

The expanded `ScrobbleGeneratePlaylistsScreenshotTest` includes eight real-composable detail tests: populated/current playback, light theme, empty, duplicate tracks, anchored menu, export alert, add-mode Back, and play/back callbacks. Captures draw `compose.activity.window.decorView` to PNG only after composition and assertions, validate dimensions/output, and recycle the bitmap. The existing Coil preview handler supplies deterministic sample artwork only; page/control/text rendering comes from production composables. **These screenshot tests were added but were not executed here, and no PNG deliverable was fabricated.** The parent should run this class, `ScrobblePlaylistDetailRulesTest`, `LocalPlaylistMutationTest`, `LocalPlaylistCodecTest`, and `LocalPlaylistRemoveSongTest` in its single Android build/test session.

## Positive patterns retained

The playlist detail reuses the exact native release header instead of forking its layout. Storage publishes new immutable playlist state only after successful suspending persistence. Edit operations use stable track identity to recover stale indexes without replacing whole stale snapshots. Search is debounced, bounded, duplicate-filtered, and cancellation-aware. Menus, alerts, text fields, segmented choices, artwork placeholders, and row controls reuse existing app components. Export intents use the restricted FileProvider and explicit URI grants. No credentials or logging of playlist contents were added.

## Residual risks and limits

1. **Android checks pending:** Compose API/type resolution, Robolectric execution, screenshot appearance, actual blur rendering, and physical-device touch/back behavior still require the parent's build and visual inspection. No claim of Android test success is made.
2. **Generation title atomicity remains parent/generation scope:** unique generation-title selection happens before the store mutex. Rename's UI duplicate check likewise is not a storage-level uniqueness guarantee against a simultaneous external save. This unit does not silently impose a new naming policy on imported/local playlists.
3. **Legacy removal durability remains its existing fire-and-forget contract:** the mutex race is fixed, but the non-suspending overload still uses SharedPreferences `apply` and cannot return durable-write failure to its existing caller. Changing that public flow needs broader integration.
4. **External export/cover providers:** permission revocation after a successful cover save, cloud-provider delays, process death while a picker is open, and cancellation while writing an external document need device tests. Export jobs are composition-scoped, not durable workers. Shared URI cleanup checks cover references known to `LocalPlaylistStore`, not arbitrary external consumers.
5. **Duplicate row identity:** imported duplicate occurrences have no per-row persisted ID. Occurrence keys prevent duplicate-key crashes; ambiguous concurrent edits of indistinguishable duplicate copies retain the established first-match fallback.
6. **CSV spreadsheet interpretation:** RFC-style quoting remains unchanged; CSV is data interchange, not a promise that arbitrary metadata cannot be interpreted as a spreadsheet formula by downstream applications. Formula-neutralization policy is a separate compatibility decision.

Final review found no remaining blocking defect in the scoped implementation, subject to the explicit Android-validation and external-provider limits above. Parent-owned stable debug signing fixes remain untouched.
