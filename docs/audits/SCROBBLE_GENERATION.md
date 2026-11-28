# Generation audit and completion

## Summary and integration status

**Intent:** complete localized generation progress/errors and fix confirmed source, account, exclusion, cancellation, retry and seed-search defects without redesigning Scrobble or touching shared ownership.

Work was performed in `/home/ubuntu/Mylo` on `feat/scrobble-screen`, starting at `49f98c8950082df622e15c3dd678ccc1e665f98a`. The generation-owned changes form a coherent integration unit and are ready for the parent to commit/push and include in its single Android build. **No Gradle commands, commits, pushes, signing/build edits, strings.xml edits, locale edits or playlist screenshot-harness edits were performed.** Android compilation, actual project test execution and device acceptance remain unverified.

Read the repository continuation context, contributor guidance, native `android-kotlin-compose` and `testing-release` skills, relevant callers/storage/auth implementations, existing generation tests, and screenshot fixture. Current delegated constraints take precedence over the historical context's Main-dev/gradient/Replay instructions. The canonical external Library documents referenced by that historical context were not present in the checkout; no outside product changes were invented.

## File-level findings and implemented fixes

| File | Confirmed finding | Implemented correction |
| --- | --- | --- |
| `data/generate/PlaylistGenerationRepository.kt` | Engine progress and validation failures were hardcoded English, including interpolated messages. | Every progress callback now resolves an Android string resource; deliberate failures carry typed resource IDs and arguments. Existing translated keys are reused, with 30 missing messages added in the dedicated resource file. |
| `ui/scrobble/ScrobbleGenerateViewModel.kt` | Existing localized keys were unused; raw provider/storage exception text reached the user. | Localized preparation, timeout, invalid-song and busy states. Only deliberate `GenerationException` failures expose specific copy; unknown provider errors use localized generic fallback and save failures use a localized storage action. |
| `data/generate/GenerationMessages.kt` (new) | Mandatory exclusion failure could be confused with an empty exclusion set. | Typed localized failures and a mandatory-read wrapper that rethrows cancellation, otherwise fails closed. No provider diagnostic is substituted for localized UI copy. |
| `data/generate/PlaylistGenerationRepository.kt` | Tag Charts could silently fall back from Last.fm to a YouTube genre/search source, contradicting its promise. Public Last.fm graphs were controlled by the scrobbling-enable preference. | Tag Charts now requires the real Last.fm chart and never swaps sources. Public similar-artist/similar-track discovery uses configured Last.fm credentials without requiring personal scrobbling to be enabled; personal charts/history still require the connected account. Existing other modes and YouTube playable-track resolution remain intact. |
| Same repository | Saved-row freshness partitioning changed the order of Top, Recent, Tag Charts and Top Artists. Now-playing rows could appear as recent scrobbles. | Last.fm-backed ordered modes preserve source ordering; Recent excludes unscrobbled now-playing/timestamp-less rows. |
| Same repository | Never Heard read just five chart pages and ignored history failures; Forgotten Favorites silently stopped after its recent-page cap. | Never Heard reads the complete reported Last.fm chart up to 50 pages, with three concurrent page requests; failures, excessive bounds, changed page metadata and incomplete reported row totals fail closed. Its required recent/account-history reads do not silently become empty. Forgotten Favorites fails rather than claiming an unscanned recent tail was unheard. |
| `data/generate/GenerationPaging.kt` (new), repository | `YtMusicRepository.library()` is a best-effort UI feed: liked rows are only the first page and some failures become empty sections. Applying it as an exclusion set was unsound. | Generation reads first/continuation pages through existing repository APIs, rejects repeated tokens and more than 50 pages, and propagates failures. Selected liked/library exclusions and Library mode require sign-in. The existing separation of liked versus library-only rows is preserved; saved exclusions fail when `LocalPlaylistStore.loadError` is present. |
| `data/generate/GenerationAccountSnapshot.kt` (new), repository, ViewModel | Long discovery could finish and save against a changed account. | Compare initial Last.fm identity/config and YouTube sign-in/cookie after discovery and immediately before save. Changed account state produces an actionable localized error. Auth material is private, and the guard intentionally is not a data class, avoiding generated credential-bearing `toString`. |
| Repository candidate rules | Local-only tracks with nonempty IDs could survive online generation; altered metadata could reintroduce the seed video. | Drop local-URI rows and exclude the seed by stable video ID as well as normalized title/artist identity. |
| Same repository | The Last.fm similar-track fallback launched unbounded searches, duplicating exact-match code. Later similar-playlist fallback could discard an earlier extra-artist pool. | Reuse the existing five-permit resolver for taste/similar names. Accumulate widening pools rather than replace previously found candidates. |
| `ui/scrobble/GenerateCommit.kt`, ViewModel | Durable storage may suspend/finish non-cancellably; completion could subsequently publish navigation/playback after cancellation. | Confirm coroutine activity after commit as well as before commit; the similar-playlist path has the same post-save guard. Cancellation clears pending Start mix playback and retry intent. |
| ViewModel | Retrying a failed Start mix lost its playback intent; unrelated future runs must not inherit it. | Retain intent only for retry of the exact failed request, clear on success/cancel/clear/unrelated generation, and prevent `clearResult()` from erasing active run state. |
| `ui/scrobble/ScrobbleGenerateScreen.kt` | Old seed results remained selectable during debounce; failed seed lookup lacked an explicit recovery action; sheet availability could become stale. | Clear prior results synchronously on query edit and before debounce, propagate cancelled error paths, stop search while generating, add seed-search Retry, and evaluate current account/exclusion availability in the open sheet. |
| Generation tests | Existing coverage did not protect complete exclusion paging, cancellation during commit or changed seed metadata. | Add `GenerationPagingTest.kt`; extend `PlaylistGenerationRulesTest.kt` and `GenerateCommitTest.kt` with focused regressions. |
| `res/values/scrobble_generation_messages.xml` (new) | Missing resource-backed engine messages. | Add 30 unique formatted/default-language messages; no other resource file was edited by this subtask. Translation completion belongs to parent/locale agent. |

`LastFmGenerationRules.kt` and its existing tests were reviewed but not changed. The existing standalone title uniqueness functions and their tests remain compatible.

## UI consistency review

| Before | After | Why |
| --- | --- | --- |
| Stale search results remained visible during a new query's debounce. | Immediate clearing with a real searching state. | Avoid selecting a result that belongs to an abandoned query. |
| Search failure only said to try again. | Existing Mylo secondary-pill Retry action. | Recovery is actionable without deleting/retyping the query. |
| Sheet button could remain available after account availability changed. | Localized availability reason and disabled Generate action. | The sheet and repository enforce the same prerequisite. |
| Genre chips used 36dp height and clickable checkbox roles without checked semantics. | 48dp chips with toggleable checked semantics. | Native accessibility/touch target behavior without a new style. |
| Seed/switch rows declared roles but not proper selection/toggle semantics. | Use Compose `selectable`/`toggleable`. | Expose state to assistive technology. |
| Progress/error expand/shrink animations ignored the supplied reduced-motion flag. | No status enter/exit motion when reduced motion is requested. | Preserve user preference without changing global animation behavior. |

The existing native Mylo color scheme, shared cards/pills/icons, plain page background and opaque Material sheet remain. No gradient, Replay link, new glass subsystem, artwork-driven color or app-wide UI change was introduced. Floating/shared liquid-glass chrome is outside this subtask's ownership.

## Verification evidence

- **PASS:** scoped `git diff --check` after the final changes.
- **PASS:** XML parses; all 30 new names are unique; all scoped `R.string` references resolve against default resources.
- **PASS:** static scans find no hardcoded engine progress callback literals, assigned generation error literals, or explicit raw engine validation exceptions. Public `progress: String`, `error: String?` and `(String) -> Unit` callback contracts remain unchanged.
- **PASS, isolated only:** `/home/ubuntu/handoff/check-generation.py` compiled the production paging/messages/commit helpers and the extracted production candidate rules using the cached Kotlin compiler, then ran **17 JUnit tests** (`GenerationPagingTest`, `PlaylistGenerationRulesTest`) and **3 commit cancellation/active-success checks**. Evidence: `/home/ubuntu/handoff/generation-isolated-check.log`.
- This harness uses minimal Android Context/R and Song/SongPage stubs. It is **not** an Android compile, actual Gradle unit-test run, Compose test, or device/network validation. The newly extended actual `GenerateCommitTest` is still pending the parent's real test task.
- Reviewed the final scoped diff and all new helper implementations, including cancellation catch order, source identity, full-page bounds, resource arguments, API call sites and storage handoff. Final review caught and corrected saved-exclusion load errors, stable seed identity, independent liked/library semantics and incomplete Last.fm metadata.

## Remaining risks and parent coordination

1. **Verification gate:** parent must compile and run actual generation/Scrobble unit tests and screenshot tests. Exercise rapid query changes, retry, cancellation before/during save, signed-out exclusions, account changes, Last.fm outage, large liked libraries and reduced motion on device. No visual/device evidence was produced here.
2. **Translation coordination:** include the 30 keys in `scrobble_generation_messages.xml` in translation completion. No fixture type migration is needed. Optional parameters have defaults, so the playlist-owned harness remains source-compatible; its sheet screenshots may change for larger chips and added failure-retry UI.
3. **Durable-save boundary:** `LocalPlaylistStore` intentionally completes a write under `NonCancellable` once its mutation lock is acquired. A cancellation arriving during that committed write cannot roll it back; these fixes suppress subsequent success/playback/navigation. Absolute “nothing saved after any cancellation instant” requires a storage-owner policy change, not a generation-only patch.
4. **Title atomicity:** unique-title selection still occurs before the store's mutation mutex. Concurrent manual/store writes could race and create matching titles. Fix belongs in the existing persistence owner; this task did not change store signatures.
5. **Conservative account guard:** cookie/session configuration rotation can produce a safe retry even when the human account stayed the same. The check is boundary-based, not an auth-revision event stream; extremely rapid change-and-change-back is not guaranteed to be detected. Prefer an existing stable account revision if the auth owner later supplies one.
6. **Bounded completeness:** Last.fm charts above 50 pages, recent-history windows above the existing 10-page bound, or library feeds above 50 pages now fail explicitly rather than generate against a knowingly partial exclusion set. The existing 60-second deadline may be reached by large accounts. Last.fm cannot provide a transactionally frozen history snapshot, so concurrently changing remote history remains an inherent limitation.
7. **Existing source behavior retained:** YouTube Music still resolves playable songs and supplies the existing Library/radio/recommendation paths; no local Mylo listening log/statistics was added. Never Heard's complete Last.fm exclusion is authoritative; the additional YouTube history API returns its available feed, not a certified all-time history snapshot.
8. **Localization boundary:** persisted playlist title defaults and genre-query tokens remain as existing English naming/search values, outside the requested progress/error completion. Displayed runtime progress is resolved at callback time; keeping compatible String state means an already-visible message is not retroactively reformatted solely by a locale change.

## Positive patterns and verdict

Generation still owns no playlist/player writes in the repository. Cancellation is rethrown, exact normalized title/artist matching is retained, request inputs remain explicit, exclusion policy is not silently relaxed, bounded concurrency limits network work, and the existing durable playlist store/event handoff remain the sole save/navigation mechanisms. These are sound foundations worth preserving.

**Verdict:** scoped implementation and self-audit complete; integration-ready, with Android build/test/device verification gates explicitly pending. Parent owns all commits/pushes. Other agents' shared-worktree changes were observed but not edited or reverted.
