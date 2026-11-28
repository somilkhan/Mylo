# Scrobble Statistics — focused audit

**IMPLEMENTED; not committed or Android-tested by this owner.** Work began at `bd5a4fb6` on `feat/scrobble-screen`. Only the assigned Stats implementation/tests and this audit were changed. The later user instruction overrides the earlier insight-first default: the overview is now compact, with Recent immediately below it and full dashboard detail on demand.

## UI changes

| Before | After | Why |
| --- | --- | --- |
| Full chart/dashboard opened by default and dominated the initial viewport | Compact total, period/comparison, sparkline, four metrics and existing “Show dashboard” action; detail opens on tap and closes with an existing Close-labelled native chevron | Keeps Recent visible without requiring a scroll. Overview targets roughly 150dp at normal typography; actual rendered size is not yet verified |
| First-load skeleton repeated the large chart | Compact overview skeleton | Loading should not restore the original oversized layout |
| Returning to the list top could reopen the full dashboard | Fully compact state stays closed until requested; partial gesture folding remains reversible | Full detail is genuinely on demand |
| Reduced-motion preference recreated the fold and could leave an old settle running; chart growth ignored preference changes | Preserve fold identity, cancel settling on policy changes/disposal, snap explicit reduced-motion expansion, remove decorative translation/scale, use static placeholders/wait glyphs | Reuses shared `scrobbleReduceMotion` policy without editing shared controls |
| Weekly busiest caption named only the interval’s ending day; long ranges hid cross-year context | Caption names the actual listener-zone date range, includes years when crossing a year, and avoids midnight-exclusive display formatting | A rolling 24-hour interval is not necessarily one calendar day |
| Cached chart endpoint always said Today; all-zero bars still named a busiest interval | Use the actual cached endpoint date and omit zero-only chart/sparkline | Avoids misleading labels |

## Confirmed findings, highest impact first

**High — account/config isolation and obsolete work.** Cache keys previously separated username/endpoint/period but not credentials/session or all-time chart timezone. Same-username configuration changes retained old dashboard/history. Keys now include opaque SHA-256 configuration identity and the all-time zone. Stats synchronizes authoritative settings before loading, observes session changes, clears shown state on identity transitions, and guards both result publication and repository cache writes. Cancellation is checked after provider return.

The fresh-cache early return previously preceded cancellation of the obsolete period request. Cancellation/generation invalidation now occurs first. Screen close cancels both snapshot and Recent jobs, clears loading flags, and allows the interrupted history page to be requested again.

**Moderate — time/count correctness.** The current-period count was open-ended, while bins ended at the captured clock, and the current/previous inclusive durations differed by one second. Current counts and rolling bins now use the same bounded, closed-second interval; previous comparison has the same duration. A rolling chart is omitted if independently fetched bucket totals disagree with the period total. All-time still uses the Last.fm profile play count and retains its documented latest-calendar-years chart, not a fabricated sum of all history.

**Moderate — Recent recovery.** Overlap identity previously ignored artist/album. The merge now preserves those identities and repeated occurrences within a Last.fm page, while removing cross-page overlap. Later pages share the paging session’s cutoff and omit repeated now-playing rows. Failed refresh/page requests remember the exact retry page, including page one after a complete feed. Empty loaded feeds retain failure/retry footers. Auto-paging restarts after completed short pages, waits for explicit retry after failure, and shows loading only for an actual request. Backfilled day groups cannot create duplicate lazy-list day keys.

## Positive patterns retained

All statistics remain Last.fm-only. Charts use provider distinct totals; no local-play merge or Replay route was introduced. Existing period pills, Recent as the default list, native monochrome surfaces, artwork placeholders, stale-data retry and bounded activity-call parallelism remain. No dependency, resource/catalogue, signing/build, shared host/control, generation or playlist file was edited.

## Verification and remaining gates

Added regression cases against the **actual ViewModel/repository seams**, covering cached-period cancellation, username/config transitions, Recent close/reopen, fixed cutoff and exact-page retry. Existing repository/day tests now cover bounded totals, equal-duration tiled windows, configuration/hash-collision isolation, overlap multiplicity, duplicate day keys and date ranges. Fold tests exercise real nested-scroll APIs.

The existing production-composable screenshot class now covers compact-default visibility, expanded detail, light theme, failed/stale data, pagination loading/failure and reduced-motion loading/populated states. Compact fixtures assert “Show dashboard” and a Recent row are displayed without scrolling. The fixture default matches the production compact default. These screenshots/tests **were added, not run by this owner**; no image was fabricated.

**TESTED:** scoped `git diff --check` only. **NOT VERIFIED:** Android compilation/tests/screenshots, exact rendered overview height, live Last.fm boundary behavior, real account/artwork, physical-device gestures, system-policy changes or GPU rendering. Parent owns the focused/full Gradle gates and commits. Official Last.fm documentation describes `from`/`to` as after/before timestamps [1]; the repository’s inclusive-second convention is retained, not newly claimed as live-verified. Remote backfills/deletions can still change data between requests; there is no transactional Last.fm snapshot.

## References

[1]: https://www.last.fm/api/show/user.getRecentTracks "Last.fm user.getRecentTracks API parameters"
