# Mylo Scrobble Integration — Context & Audit Log

> Living engineering record. Update this file after every audit pass and before any implementation decision. This is not the public README.

## 1. Agreed scope and branch responsibilities

- **Target:** `Main-dev` (the repository branch is case-sensitive; the earlier lowercase `main-dev` lookup returned 404). Latest audited head: `11478fde56f1817cb777b296e60907359086f383`.
- **FAB/navigation source:** `scrobble-ui`, audited head `5bf065da305e6bb663960f9e09d30572dcdc0f32`. Reuse only the Scrobble FAB, its visibility/placement/open-close behavior, and the static Statistics / Generate / Playlists navigation. Do not port its Scrobble destination screens or feature logic as the implementation source.
- **Feature source:** `last-wave`, audited head `e68d6f9e488e9a0136998be6d23c731dbcfe8f9b`. Use as the source for Scrobble-related architecture, Statistics, Generate, Playlists and their supporting domain/data flows, then adapt to Mylo's existing architecture and UI.
- **Final integration target:** `Main-dev`. No work on `main`; no merge to `Main-dev` until audit, implementation, review and user approval.
- Preserve Mylo's existing Home/main navigation except the specifically agreed FAB entry/exit integration. In Scrobble mode, replace the normal Home / Library / Search navigation with persistent Statistics / Generate / Playlists tabs. Tapping the FAB again or dismissing Scrobble restores the normal navigation.
- User requires a complete, evidence-based audit and precise plan before implementation. No Gradle builds during planning. No code changes to app behavior, commits, PRs or merges without explicit authorization.

## 2. Repository instructions inspected

### Main-dev
- No root `CLAUDE.md` or root `AGENTS.md` was found at the audited head.
- Read `CONTRIBUTING.md` (SHA `3f45cdfb014fcbf54ea57bfd18dadf7b20556b4c`), `README.md` (SHA `0f40cfb80212c7e2b7039a5c4b35bb8466e62023`) and `ADDITIONAL.md` (SHA `d6fe84dceb9ed412e75407a00a140c3c25fa8b87`).
- Relevant project constraints: keep UI/domain/repository/playback responsibilities separate; PlaybackService owns Media3 playback; inspect exact CI commit; no device-verification claims without device evidence; Android app build/test tasks are documented in CONTRIBUTING.md. User has explicitly deferred builds for this planning phase.

### last-wave
- Read `CLAUDE.md` (SHA `9aa85866f53c9cd13b10b2e83d6bda5094f3c3f9`), which includes `AGENTS.md` and `DESIGN.md`.
- Read `AGENTS.md` (SHA `cf3399026d45e24aee16006200ce65fc4b08d866`) and `DESIGN.md` (SHA `e6a058245f67fb0a04eb180ab467b2b8369c2e2d`).
- Key constraints: trace caller → callee → storage; fewest correct lines; preserve behavior; cancellation safety; CAS for shared playlist writes; ViewModel scope/lifecycle correctness; Liquid Glass only for floating chrome with fallback; theme tokens over hard-coded colors; touch targets >=48dp; design loading/empty/error/long-text/RTL/large-font/landscape states.

### scrobble-ui
- Read `CLAUDE.md` (SHA `3b0ef90d07b921bd2e8084d7d38598d7e59f8d5e`). No root `AGENTS.md) was found.
- Its broader scope includes Scrobble screens, but for this task only the FAB and navigation behavior are in scope from this branch.

## 3. Initial verified inventory

### Main-dev existing foundations
- Existing Last.fm/ListenBrainz integration: `data/scrobbling/LastFM.kt`, `ListenBrainzManager.kt`, `PrimaryArtist.kt`, `ScrobbleManager.kt`.
- Existing local listening/statistics foundations: `data/ListeningHistory.kt`, `data/stats/ListeningRecorder.kt`, `ListeningStats.kt`, `ArtistFacts.kt`, `Backup.kt`.
- Existing settings UI: `ui/screens/AccountAndScrobblingScreen.kt`.
- Existing playback owner: `playback/PlaybackService.kt`.
- Existing main shell/navigation components include `ui/components/FloatingBottomBar.kt` and `GlassNavBar.kt`; `MainActivity.kt` and `MainViewModel.kt` are significant integration points and must be inspected by exact relevant line ranges, not wholesale replaced.
- This means the destination feature must not introduce a second scrobbling pipeline, duplicate listening recorder, competing stats store or separate playback owner without a proven gap.

### scrobble-ui FAB/navigation source
- `ui/components/ScrobbleFab.kt` defines a 62dp FAB, home-only visibility helper, overlay/player/keyboard suppression, folded-on-scroll state, reduced-motion support, icon morph (GraphicEq ↔ Close), and removes hidden control semantics/interactivity.
- `ui/screens/ScrobbleScreen.kt` contains the old destination implementation and is **not** the source for destination screens/logic in this integration. Its tab constants are Stats=0, Generate=1, Playlists=2; verify the actual host/nav wiring and back/dismiss behavior separately.
- `ScrobbleFabComposeTest.kt` exists and must be reviewed as behavioral evidence for the FAB source.
- Exact static-tab host behavior, state restoration, BackHandler precedence, and all dismissal paths remain to be traced from `MainActivity.kt` and related host code.

### last-wave feature source
- LastWave's tree contains distinct feature/data layers:
  - Scrobble: `data/repository/ScrobbleRepository.kt`, Last.fm API/signing/rate guard, auth callback coordinator, scrobbler/session preferences, playback service integration.
  - Stats: `data/repository/LocalStatsRepository.kt`, `SongPlayStatsRepository.kt`, `data/local/db/SongPlayStatsEntity.kt`, `ui/stats/ListeningStatsScreen.kt`, `ListeningStatsViewModel.kt`, components/models.
  - Generate: `data/generate/*` (repository, taste profile/provider, recommendation and local taste engines, generated track/status, genres), `ui/generate/GenerateScreen.kt`, `GenerateViewModel.kt`, `MixLauncher.kt`.
  - Playlists: `data/playlist/PlaylistRepository.kt`, `data/local/db/SavedPlaylistEntity.kt`, `data/naming/PlaylistNamer.kt`, plus `ui/playlist/*`.
  - Shell/navigation: `ui/shell/MainShell.kt`, `ui/navigation/NavGraph.kt`, `Screen.kt`, `AppRouteNavigator.kt`.
- LastWave's `README.md) is its public project README; no separate context/audit document existed at the inspected head. This file is the separate engineering context log.

## 4. Audit status

- [x] Confirmed repository and branch heads.
- [x] Read branch instruction documents available at repository roots.
- [x] Mapped first-pass feature file inventory across the three branches.
- [ ] Trace Main-dev playback → scrobble → listening recorder → stats storage end to end.
- [ ] Trace LastWave playback → scrobble → auth/session → signed network writes → retry/offline queue end to end.
- [ ] Audit Statistics screen: every period/source/filter/chart/list/detail/action, aggregation, caching, refresh, empty/error/loading behavior.
- [ ] Audit Generate: all modes, filters, seeds, exclusions, identity/deduplication, ranking, queue/play, generation progress/cancellation and failure handling.
- [ ] Audit Playlists: creation, naming, persistence, detail, reorder, edit, delete, export/import, play/queue, recovery and concurrent updates.
- [ ] Trace Mylo FAB host placement, visibility, open/close, tab replacement, BackHandler, overlay/keyboard/player conflicts and state restoration.
- [ ] Compare data models, IDs, persistence formats, dependencies, DI, API contracts and module/package boundaries; produce reuse/adapt/rebuild/avoid ledger.
- [ ] Inspect relevant tests and identify missing regression coverage.
- [ ] Produce precise phased integration plan and acceptance checklist before app implementation.

## 5. Rules for future updates

1. After each audit pass, append date/time, inspected refs/paths, verified findings, confirmed defects, open questions and next audit slice.
2. Distinguish **confirmed** behavior from inference or unverified risk.
3. Record exact branch SHA and file blob SHA when a finding depends on source.
4. Never silently overwrite an earlier conclusion; append a correction with evidence.
5. Keep this file separate from the public `README.md`.
6. Do not mark implementation complete from source inspection alone; builds, tests and physical-device checks are separate verification stages.


## 6. Audit pass — source comparison, navigation and data ownership

### Branch relationship / port boundary
- GitHub compare `Main-dev...scrobble-ui`: status `diverged`, 359 commits ahead / 1 behind, merge base `d809e42cb3e7c9b81b603dec6f087898b2e93abe`. The compare reports feature files beyond the FAB, including the old 3,298-line `ScrobbleScreen.kt`, mix engines, playlist store and other logic. **Do not merge/cherry-pick the branch wholesale.** Extract only the approved FAB + nav host behavior.
- GitHub compare `Main-dev...last-wave`: LastWave branch is 13 commits ahead of Main-dev from the common Main-dev head. It is a complete app migration/rebrand, not a narrow Scrobble patch. Do not merge the whole branch. Port only the feature/data slices proven relevant to the agreed Scrobble experience.
- The LastWave `MainShell` tabs are FEED / STATS / PLAYLISTS; Generate is launched as a pushed screen, not a bottom tab. The Mylo Scrobble UX explicitly requires three static tabs Statistics / Generate / Playlists, so Generator must be hosted as a Scrobble tab in Mylo rather than copying LastWave's shell navigation verbatim.

### Mylo navigation baseline vs user shorthand
- Main-dev `MainActivity.kt` builds **four** normal bottom tabs: Home, Explore, Library, Search (lines 1160–1190); constants are TAB_HOME=0, TAB_EXPLORE=1, TAB_LIBRARY=2, TAB_SEARCH=3.
- User's shorthand previously named Home / Library / Search. Preserve all existing Main-dev tabs when Scrobble mode closes; do not silently remove Explore. Confirm whether the user's intended normal nav is all four current tabs or whether Explore is intentionally excluded before implementation.
- Main-dev uses a single large `MainActivity.kt` host and existing GlassNavBar/FloatingBottomBar. Scrobble integration should be a tightly scoped state/content/nav substitution within this host, not a new root navigation architecture.

### scrobble-ui exact approved behavior (source refs)
- `ScrobbleFab.kt` blob `c70fd7c0840da6a13bac59497f24053a7c17084d`: 62dp touch surface, icon changes GraphicEq ↔ Close, animated alpha/scale/lift, spring or snap for reduce-motion, no semantics/click action while hidden. Visibility helper requires Home selected and no competing overlay/player/keyboard; folded-on-scroll visual is supported.
- `MainActivity.kt` blob `4c587758a397b831ccb103df29e5c683ad7e72c2`: remembers `showScrobble` and `scrobbleSection`; FAB toggles open/closed and resets section to Stats on open; static tab list is Stats / Generate / Playlists; while open, displayed nav list/index/click callback are swapped to Scrobble tabs; clicking a Scrobble tab only changes selected section; normal tab handler clears Scrobble mode and restores normal tabs. BackHandler and top-bar back both close Scrobble. Scrobble screen itself is rendered as a top-level content state with dedicated enter/exit transition.
- Existing behavior uses a separate Scrobble nav scroll connection while Scrobble mode is open. FAB bottom position is measured from the complete player/nav stack, and FAB is only shown on Home, not on other normal tabs or competing overlays.
- Exact all dismissal paths and the source test assertions still need a complete pass; do not assume FAB click is the only close route.

### Confirmed architecture overlaps and data-quality concerns
- Main-dev already owns real device listening accumulation via `ListeningRecorder` → `ListeningStats` and listening history. PlaybackService invokes `ListeningRecorder.onTrackStarted/onSample/onPaused/onStopped`; its sampler counts actual wall-clock audible intervals, records a play after a duration threshold, and flushes to disk. This is an existing source of truth for local listening statistics.
- Main-dev `ScrobbleManager` separately handles Last.fm now-playing/scrobble timing. Avoid adding a second Last.fm submission loop from LastWave; compare protocol/retry/auth capabilities and adapt only missing reliability behavior.
- LastWave `ScrobbleRepository` has a serialized signed-write path, shared rate guard, adaptive cooldown, retryable offline queue and event flow. However its pending queue is a process-memory `ConcurrentLinkedQueue`, not durable storage; the queue is capped and oldest entries are polled on overflow. Process death before a successful flush can therefore lose pending scrobbles. Treat durable queueing as an open reliability issue, not as a guaranteed persistence feature.
- LastWave `HomeScreen` (MainShell STATS tab) is the Last.fm/local profile overview: username/profile, scrobble totals, top tracks/artists/albums and recent track list. Its detailed `ListeningStatsScreen` is a separate pushed destination, not the same screen.
- LastWave `LocalStatsRepository` derives fallback counts from saved playlist occurrences and downloads, and estimates duration at 210 seconds per track. This is library-derived proxy data, not genuine listen telemetry. `ListeningStatsViewModel` additionally synthesizes fallback play counts and random last-played timestamps when the Room stats table is empty. Do not present either proxy/synthetic result as real listening history. Mylo's existing actual ListeningStats recorder is a safer basis for local device stats.
- LastWave `SongPlayStatsRepository` records per-track listened milliseconds and completion counts in Room; this is a second local tracker relative to Mylo's monthly JSON ListeningStats. Need compare semantics, identity keys, lifecycle hooks and test coverage before choosing whether to port any parts or map LastWave stats UI onto Mylo's recorder.
- LastWave `PlaylistRepository` is Room-backed and contains significant integrity protections (CAS update for video-id backfill, safe read failures, startup JSON mirror restore, mutexes and change flow). Do not port it as an independent playlist database until compared against Mylo's existing playlist/persistence implementation and schema.

### Open decisions / next slices
- Confirm whether Explore remains among the normal tabs (current Main-dev source says yes).
- Decide precise Statistics scope: LastWave profile/Last.fm overview vs Mylo device-listening analytics, or a clearly separated combined experience with truthful source labels. No silent mixing of incompatible metrics.
- Trace the LastWave playback hook that feeds `SongPlayStatsRepository` and `ScrobbleRepository`; injection alone in `MusicPlaybackService` is not proof of call-site behavior.
- Continue audit of GenerateRepository modes/algorithms and full PlaylistViewModel actions/storage, then compare against Mylo's current playlist model.


## 7. Audit pass — generation algorithms and persistence compatibility

### LastWave generator inventory
- `GenerateViewModel.kt` blob `74b283322be32a9a17be644a3679f7ee76fd8398` exposes nine modes: Top, Recent, Similar Tracks, Similar Artists, Tag, Mix, Recommendations, Never Heard, Library. UI state includes count (5–35), period, track/artist seeds, tag, seed search results, loading/progress and error.
- Track/artist seed lookup, validation, progress reporting, 60-second timeout, availability precheck, dedupe/freshness preference, generated playlist naming/save, optional queue replacement (Start Mix), background artwork enrichment and navigation event are all handled by ViewModel/repository rather than the screen alone.
- `GenerateRepository.kt` blob `e81bac3a8c938402931225e5c8f5a7cbda1645df` contains distinct data paths for charts, top/recent, similar tracks, similar artists, tags, mix, taste mix, recommendations and never-heard. YouTube Music is generally primary; Last.fm is supplementary; local Room seeds and charts provide guest/offline fallback. It also manages recommendation exclusions, saved-playlist freshness, video ID pinning and heard-track identity.
- `RecommendationEngine.kt` blob `58e5d3394443762673640e5061636f52eaa8a7ec` uses multiple weighted sources, scoring, diversity and refill stages. `LocalTasteSuggestionEngine.kt` blob `3f8df078e9001d9d464492214fb6c5ba31ef2629` is the guest/local counterpart using local play/skip stats.
- Do not copy Generator UI alone: its modes depend on TasteProfileProvider, InnerTubeMusicApi, RecommendationRepository, Last.fm API/session, exclusion DAO, local play stats, playlist repository, artwork enrichment and playback queue APIs. Each dependency needs a Mylo equivalent or an explicit adaptation.

### Confirmed generator cancellation defects to fix/avoid during port
- In `GenerateRepository.fetchTopTracks()` (lines 584–601) and `fetchRecentTracks()` (lines 602–620), broad `catch (e: Exception)` falls back to chart tracks without rethrowing `CancellationException`. This can turn a cancelled request into additional network work and return stale results.
- `fetchNeverHeardTracks()` wraps multiple suspend reads in `runCatching` (taste profile, exclusions, playlists, stats, downloads); Kotlin's `runCatching` catches cancellation unless explicitly rethrown/checked. Cancellation handling must be corrected in any port.
- Other generator branches explicitly rethrow cancellation in some source calls but not all nested broad catches. Complete per-method cancellation audit remains required before classifying the rest.
- `GenerateViewModel.loadSeedResults()` checks `coroutineContext.ensureActive()` after provider return/catch, and `generate()` has explicit cancellation handling; retain these protections.

### Main-dev playlist and dependency baseline
- Main-dev has no app-level Room database, DAO/entity tree or Room dependency found in `app/build.gradle.kts`. It is a single Android app module using SharedPreferences and Ktor (Ktor client 3.5.2 is already present).
- Existing local playlists are `data/spotify/LocalPlaylistStore.kt` blob `0fc00efafbfc42263791d3c1254d39d93d8430f0`: one SharedPreferences JSON document, exposed as StateFlow; model is `LocalPlaylist(id, title, songs: List<Song>)`; current operations are init/load, save, delete, rename, get. Persistence uses `apply()` and `org.json`. MainActivity collects this store and uses it to resolve local playlist detail routes.
- The main playlist picker is for YouTube Music `UserPlaylist` entities and is separate from this local store. Existing local playlists are not equivalent to LastWave's generated-playlist model.
- LastWave uses Room v15 with SavedPlaylistEntity (single row per playlist; tracks stored as JSON), SavedPlaylistDao, CAS update for stale track backfills, transactional trimming, and a substantial PlaylistRepository. It also uses `GeneratedTrack` identity (name/artist/url/artwork/album), unlike Mylo's `Song` (videoId/title/artist/thumbnail/duration/artistId/albumId/albumName/isVideo).
- Porting LastWave Room files directly would introduce a new persistence stack and dependencies in Main-dev. This is not yet approved as the smallest/safer route. Compare whether the existing local store can be evolved to support generated playlists, metadata, reorder, export and robust atomic updates before choosing a Room migration.
- Existing Main-dev queue/playback interfaces use `Song`; LastWave Generator uses `GeneratedTrack` then converts to `PlayableTrack`. Track identity/video IDs/artwork/duration must be mapped without losing Mylo's stable `videoId` or creating duplicate songs.

### Next audit slices
- Inspect Mylo local-playlist read/write/detail/play/reorder/delete flows and tests; map exactly which actions already exist and where generated playlist metadata would fit.
- Complete LastWave PlaylistViewModel action trace (create/rename/make-local/cover/pin/add/remove/move/delete/multi-delete/export/download/regenerate/delete-scrobble), then map each to Mylo capabilities and decide reuse/adapt/build.
- Complete Stats data-source and aggregation audit; keep Last.fm remote scrobbles, actual Mylo device listening, YouTube Music history and library inventory as distinct sources.
- Inspect all LastWave generator modes for cancellation, deduplication, seed identity, source fallback, rate limits, missing IDs and queue semantics; inspect matching tests.


## 8. Audit pass — Statistics source and correctness

### LastWave Statistics entry points
- In LastWave `MainShell`, the STATS tab renders `HomeScreen` (`ui/home/HomeScreen.kt`), which is a Last.fm profile overview when connected: account identity/friend switcher, scrobble total, track/artist/album counts, now-playing/recent/top tracks and sort filters. The separate `ListeningStatsScreen` is a pushed detail route reached from the overview; it is not the MainShell STATS page itself.
- `HomeViewModel` + `HomeRepository` perform Last.fm user.getinfo, user.getrecenttracks, user.gettoptracks, user.gettopartists and user.gettopalbums calls, page loading, 7-day/30-day preloads, now-playing polling, artwork enrichment, friend-profile switching and refresh. The UI's recent list has Recent / Most Played / Last 7 Days / Last 30 Days / Top Albums / Top Artists modes.
- LastWave local/offline overview is not real listening telemetry: `LocalStatsRepository` counts saved playlist occurrences/downloads and estimates listening time as 210 seconds per occurrence. This cannot be labelled scrobbles or actual listening time.

### Detailed LastWave Listening Stats screen — confirmed data-integrity problems
- `ListeningStatsViewModel.kt` blob `1425e7e14199feea2773f6450df3d5850a65d44c` reads Room `SongPlayStatsEntity`, which stores only per-track accumulated duration/count/skip count and one `lastPlayedAtMillis`, not per-play timestamps or daily/monthly buckets.
- When the selected range has no matching rows, the ViewModel fabricates records from saved playlists/downloads, estimates each play as 210 seconds, and assigns a random last-played date within 1–30 days. This is not a valid empty-state fallback.
- Even with real rows, MTD/YTD/All-time timeline buckets assign each track's entire accumulated lifetime duration/count to its single last-played date. Historical plays are therefore attributed to the most recent period; the table does not preserve per-period totals.
- Session analytics are inferred from track last-played timestamps, 25-minute gaps, a forced 15-minute minimum, and estimated session counts. Longest session is further estimated from total duration. These are not measured listening sessions.
- Most-active weekday is assigned 35% of total listening time; peak timeline slot is the current year plus total listening duration. Neither is derived from hour/day play history.
- Genre chart allocates total time/plays by a deterministic descending weight over tag names, not measured genre listening. Album fallback groups by artist if album data is missing. These values must not be presented as measured listening stats.
- Therefore: LastWave detailed Stats *visual hierarchy/components* may be studied, but its current stats calculations and fake fallbacks must not be ported as-is.

### Mylo's stronger local stats source
- Main-dev `ListeningStats.kt` blob `93cb88d2f95aceca12066a433e6c446f41ef3aad` is a real on-device aggregate tracker: it samples listened milliseconds during playback and stores month-partitioned JSON; it aggregates actual track/artist/album totals, per-day sub-buckets, hourly listening, day totals, plays, busiest day and ranked songs/artists/albums/genres where available.
- `ListeningRecorder.kt` blob `39a2e3896d24ed95384aa6f721470ae39ce8b1c1` drives the real listening intervals from PlaybackService and records samples only while the track is actually playing. `ListeningHistory.kt` is a separate chronological history store and should not be conflated with aggregate stats.
- Main-dev `ReplaySummary` already supports THIS_WEEK / THIS_MONTH / THIS_YEAR / ALL_TIME and includes total listened time/plays, ranked songs/artists/albums/genres, hour-of-day, busiest day, distinct counts and coverage start. This is a much more truthful basis for local Statistics than LastWave's Room-derived approximation.
- Mylo's local stats are device-only by design and do not leave the device. Last.fm remote scrobbles are a separate account dataset; any combined UI must label source and avoid summing overlapping device and remote events.

### Statistics integration direction (provisional; requires final cross-check)
- Reuse Mylo's `ListeningStats.summary(ReplayPeriod)` as the local-device data source; preserve its existing aggregation and backup/import behavior.
- Reuse/adapt LastWave's Last.fm profile overview only where it can be backed by Mylo's existing LastFM API/session implementation. Do not port `LocalStatsRepository` as a fake substitute for disconnected users.
- Keep remote Last.fm stats and local device listening analytics clearly separated (or source-switched); do not merge counts without a deduplication identity and provenance model.
- Use truthful empty states when no actual listening data exists. No random dates, synthetic play counts, average-duration substitutes or invented habit/genre shares.

### Remaining Statistics audit
- Trace Main-dev `ListeningStats.init/record/summary/export/import` and `ListeningRecorder` service hooks to verify exact thresholds, day/hour accounting, month rollover, concurrency, backup restore and failure behavior.
- Inspect Main-dev existing Replay UI and its interaction map; determine which UI components/summary calculations can be reused inside the new Statistics tab without redesigning Home or changing global navigation.
- Review LastWave Stats/Home tests and Mylo stats tests for existing correctness contracts; record missing coverage.


## 9. Audit pass — Mylo's existing local stats, Replay and playlist behavior

### Mylo local Statistics/Replay behavior
- `MainActivity.kt` already owns `rememberReplayState(replayOpen)`; it loads the current `ListeningStats.summary(period)` only while Replay is visible or Library needs its summary cards. Replay is currently a pushed/overlay page, with share/story layers and BackHandlers. The Library also contains Replay summary cards that route into the existing Replay page.
- Existing `ReplayScreen` uses Mylo's current Material/mesh-artwork design and supports period selection (This week / This month / This year / All time), headline cards, ranked songs/artists/albums/genres, story playback, track playback/radio, artist/album navigation and share. Reuse this data and existing UI primitives where appropriate; do not modify the Home or global navigation as part of this audit.
- `ListeningStats.record()` keys tracks by stable YouTube videoId, updates real played milliseconds and play counts, and accumulates per-day track/artist/album values, hour-of-day and day-of-month totals. `summary()` merges monthly files and filters exact day buckets for weekly views. This is materially more accurate than LastWave's single-lastPlayedAt aggregation.
- `ListeningRecorder` counts wall-clock time between active playback samples (not media position), pauses/ends intervals explicitly, counts a play only after a duration-derived threshold, and writes listening history separately from Replay aggregate stats. PlaybackService calls it on playback state changes, media-item transitions, a one-second active playback sampler and service teardown.
- Mylo's Replay UI currently lives behind `showReplay` and also under Library. A Scrobble Statistics tab can host or adapt the existing Replay screen/state inside the Scrobble mode, but must not create a second recorder or change the existing Library Replay behavior.

### Confirmed Mylo stats persistence risks to include in implementation plan
- `ListeningStats.pending()` clears `dirty` before the asynchronous file write completes. `write()` logs failure but does not restore dirty state or schedule a retry. If the process dies before another sample/flush, the latest snapshot can be lost; on month rollover the previous open bucket is dropped after scheduling the write, so a failed write can permanently lose that month's unflushed tail.
- The `.tmp` + `renameTo` write path falls back to deleting the old target before a second rename attempt. If that second rename fails, the previous valid bucket has already been deleted. This needs a safer atomic replace/retry strategy before treating the stats store as fully durable.
- `importAll()` deletes the current directory and writes imported buckets directly, not via per-file temp/atomic replacement. Backup restore interruption can leave a partial set. Review Backup's caller/validation and define safe replacement semantics before changing it.
- These are existing Mylo risks discovered during audit, not reasons to replace its entire aggregate architecture with LastWave's Room table.

### Mylo playlist baseline
- `MainViewModel.createPlaylistWithVideoIds()` first attempts YouTube Music playlist creation when signed in, adding tracks in batches beyond 50; if that fails or the user is signed out, it saves a local playlist through `LocalPlaylistStore.savePlaylist(name, songs)`.
- `MainActivity` displays local playlists on the Library's On Device shelf and reloads an open local playlist detail via `LocalPlaylistStore.getPlaylist(browseId)`. `MainViewModel.localPageState()` resolves local playlist contents.
- Existing local playlist model is minimal (id/title/songs) and the UI primarily surfaces imported local playlists; it is not a complete generated-playlist manager with mode/subtitle/created timestamp/cover/pin/reorder/export/regenerate.
- Existing remote YouTube Music playlist creation/edit/delete is a separate account-backed capability. Do not conflate remote YT playlists, imported local playlists and newly generated Scrobble playlists; each needs a clear storage/playback identity and ownership boundary.

### Mylo ↔ LastWave compatibility direction
- Use Mylo `Song.videoId` as the primary stable playback identity wherever available. LastWave `GeneratedTrack` uses name/artist/url/artwork/album and its URL-derived YouTube ID; mapping must preserve exact videoId, title, artist, album metadata, thumbnail and duration. Avoid title+artist-only keys when a stable videoId exists.
- The LastWave Generator's `GeneratedTrack → PlayableTrack` conversion and queue replacement cannot be copied directly; Mylo uses `Song`, `PlaybackService`, `QueueCoordinator` and its own queue-source semantics.
- LastWave playlist manager includes external YouTube library sync, Spotify playlist projections, downloads, and local-song hub. Those are not automatically part of the Scrobble generated-playlist feature. Port only the generated-playlist lifecycle and actions needed for the agreed scope; preserve existing Mylo remote/local playlist behavior.

### Open items
- Inspect Mylo Replay chart/story tests and ListeningStats/Backup tests; establish exact regression tests for real data, empty periods, week boundaries, month rollover, file write failures and import interruption.
- Decide whether the Statistics tab is the Mylo Replay experience, Last.fm profile overview, or a source-switched combination. Recommended safe model: Mylo device Replay is the default local view; a separate Last.fm account section/view uses remote API data and labels counts as Last.fm scrobbles.
- Determine whether generated playlists should extend `LocalPlaylistStore` or introduce a Room-backed repository. A new Room stack is a larger architectural migration and requires an explicit justification, database ownership, migrations, backup compatibility and test plan.


## 10. Audit pass — playlist lifecycle, persistence and failure paths

### LastWave playlist capability map
- List screen: search/filter; sort newest/oldest/name/track-count; expand/collapse; local songs hub; downloads entry; generated playlist cards; open detail; play/shuffle; regenerate latest; create custom playlist; multi-select/delete; toast and export sheet.
- Card/detail actions: rename, pin/unpin, custom cover, export/share CSV or M3U, download all/cancel downloads, make a YouTube playlist local, regenerate an inspired mix, delete, toggle YouTube sync, and open details.
- Detail actions: track playback/context menu, remove track, drag-reorder local tracks, search tracks, cover selection/reset, pin, download/cancel, make-local, regenerate, rename, export/share, toggle YT sync, delete. Track context menu also exposes delete-scrobble and artwork refresh.
- Repository: Room row per playlist with JSON track payload; generated/custom/liked/local modes; stable timestamp IDs; save mutex; duplicate-content detection; same-title suffixing; 20 unpinned generated-playlist retention cap; CRUD; reorder; cover/pin; add/remove track; liked/local special lists; public mirror restore/export; track video-ID backfill; CAS updates to avoid stale snapshot overwrites.
- External-account playlist projections and YT sync are separate from the generated local-playlist core. They should not be pulled into the Scrobble feature automatically.

### Confirmed LastWave playlist defects/risks
- `PlaylistRepository.save()` catches broad `Exception` around DAO upsert/trim and still returns `entity.toDomain()` as if saved. A DB write failure can therefore produce a success-looking playlist/navigation event that is not persisted; cancellation is also swallowed by this catch.
- `PlaylistViewModel.load()` turns a local playlist read exception into `emptyList()` and sets loading false, with no explicit load error/retry state. A transient Room read failure can look like the user has no playlists.
- Several suspend paths use `runCatching` or broad `Exception` without rethrowing cancellation (e.g. ensureLocalSongs in load, syncNow, getById in regenerate/download paths, some seed/local fallback reads). A full cancellation audit is still needed before port.
- Several user actions (custom create/rename/cover/pin/remove) call repository operations without consistent error state; DB failures may escape a ViewModel coroutine or leave optimistic UI stale. The exact behavior depends on the caller/CoroutineExceptionHandler and needs tests.
- `PlaylistRepository.getAll()` can skip individual malformed entities via `mapNotNull` + `runCatching`; resilient track JSON decoding preserves valid rows, but a wholly malformed entity can disappear from the UI without a surfaced recovery action.
- The repository has a bounded generated-playlist retention policy (20 unpinned generated rows). This is product behavior, not a neutral implementation detail; decide whether Mylo should keep it before porting.
- Track identity mixes `sameSongAs`, URL/video ID and normalized title/artist keys. Map to Mylo's videoId-first identity to avoid duplicates from alternate uploads or remasters.

### Mylo playlist gaps confirmed
- Mylo's `LocalPlaylistStore` only persists `id/title/songs`; it has no generated mode, subtitle, created timestamp, cover, pinned state, reorder, remove-track, export, regenerate, or playlist detail management API in the local store itself.
- Mylo `MainViewModel.createPlaylistWithVideoIds()` creates a YouTube Music playlist when possible; on failure or sign-out it saves to `LocalPlaylistStore`. This fallback is not the same as a generated Scrobble playlist and should remain intact.
- Mylo's remote YT Music playlists are account-owned and have different edit/delete/queue semantics. Keep them distinct from Scrobble-generated local playlists.
- LastWave's complete generated-playlist lifecycle is a genuine feature gap in Main-dev. Whether to extend the existing local JSON store or introduce Room remains an explicit architecture decision; a Room migration would require dependency, DI, schema, backup, migration, and test planning.

### Port boundary recommendation
- Port the generated-playlist domain/use cases and the user-visible lifecycle (save, detail, play/queue, sort, search, rename, pin, cover, reorder, remove, export/share, regenerate, delete) as the core.
- Exclude or defer LastWave-specific Spotify account playlist projection, YT library sync, make-local for remote account playlists, download-manager routing and local-audio hub unless separately required by Mylo's Scrobble product.
- Do not port the LastWave failure behavior above. Preserve its good integrity patterns (serialized read-modify-write, CAS where stale writes are possible, non-cancellable completion for destructive work) and correct its confirmed defects.


## 11. Audit pass — Main-dev existing scrobbling and playlist ownership (2026-11-18 19:27:51 IST)

### Exact repository state at this pass
- Main-dev branch head: `11478fde56f1817cb777b296e60907359086f383` (case-sensitive branch name: `Main-dev`; lowercase `main-dev` does not exist).
- scrobble-ui branch head: `5bf065da305e6bb663960f9e09d30572dcdc0f32`.
- last-wave branch head before this context update: `c57dacb2a1ae79a006ffb249fb48e68dc61e4bfa`.
- Compare Main-dev...scrobble-ui: `diverged`, 359 ahead / 1 behind, merge base `d809e42cb3e7c9b81b603dec6f087898b2e93abe`, 27 changed files in compare response. This confirms the FAB/navigation branch must not be merged wholesale.
- Compare Main-dev...last-wave: `ahead`, 18 commits ahead / 0 behind, merge base equals Main-dev head, 300 changed files. This is a broad feature/app snapshot, not a safe whole-branch merge. Cherry-pick only audited feature slices after approval.

### Instruction documents rechecked
- Main-dev has no root `CLAUDE.md`, `AGENTS.md`, or `DESIGN.md` at this head (recursive tree checked; no nested equivalents found). Read full `CONTRIBUTING.md` (blob SHA `3f45cdfb014fcbf54ea57bfd18dadf7b20556b4c`), `ADDITIONAL.md` (`d6fe84dceb9ed412e75407a00a140c3c25fa8b87`) and `README.md` (`0f40cfb80212c7e2b7039a5c4b35bb8466e62023`).
- Main-dev contract: keep UI/repository/domain/playback responsibilities separated; PlaybackService owns Media3; use focused edits to large MainActivity; add regression tests; inspect CI for exact commit; no device verification claims without hardware. User has deferred builds during planning.
- Last-wave: `CLAUDE.md` (`9aa85866f53c9cd13b10b2e83d6bda5094f3c3f9`) points to `AGENTS.md` and `DESIGN.md`; both complete files were read at this pass. Existing rules include cancellation safety, CAS for shared playlist writes, lifecycle ownership, design tokens, Glass fallback, 48dp touch targets and explicit empty/error/responsive states.
- scrobble-ui: full `CLAUDE.md` (`3b0ef90d07b921bd2e8084d7d38598d7e59f8d5e`) was read; no root AGENTS.md exists there. Its broad feature scope does not override the user's narrower instruction: take only FAB + Scrobble navigation from that branch.

### Existing Main-dev scrobbling is already real functionality — do not duplicate it
- `data/scrobbling/LastFM.kt` blob `5e3a7099255b9b54e8da7fb3a3c496e11fc81879`: existing Ktor Last.fm client, API signing, auth.getToken/getSession/getMobileSession, updateNowPlaying, scrobble, endpoint normalization and runtime config. It uses existing app settings for API key/secret/session key.
- `data/scrobbling/ScrobbleManager.kt` blob `153b488ed0eea8893ffb0e7983de9fd26a569847`: existing per-track timer, minimum duration, percent/max delay, pause/resume accounting, now-playing, primary-artist selection and Last.fm submission.
- `playback/PlaybackService.kt` blob `ddd8ff03c57a8e4ab16e7a23b03863ca1b517f59` owns this integration. `observeScrobbling()` collects existing settings, configures LastFM, creates/destroys ScrobbleManager and restores current playing track. Player state changes and media transitions invoke manager start/pause/stop. Do not add LastWave's own second playback scrobble listener or duplicate submission loop.
- `data/scrobbling/ListenBrainzManager.kt` blob `d8384413ab9fb8bf596ed26d8fc7491965f7a6d2` already submits ListenBrainz playing_now and finished listens; PlaybackService owns its transition and shutdown hooks. It must remain intact.
- Existing settings in `data/settings/AppSettings.kt` blob `8a3f1a35a0269803f0d4b73c92f7d46a3ceab089` already cover Last.fm enabled/session/username/API key/secret/endpoint, scrobble enabled, now-playing, primary artist, minimum duration, delay percentage/seconds, and ListenBrainz token/settings. Settings screen already exposes these controls and login flow.
- Existing scrobble path still needs a focused correctness audit: manager timer lifecycle when track changes while paused, missing duration, repeated same-track transitions, in-flight request cancellation/duplicate prevention, offline persistence/retry, API errors, and behavior when credentials/settings change mid-track. Current LastFM uses `runCatching`; cancellation propagation must be checked in full call chain. These are audit items, not yet all confirmed defects.

### Playlist backend/store reality — reuse, do not invent another one
- Main-dev has **no dedicated playlist HTTP backend/service** in the Go backend tree (no playlist endpoints or playlist persistence package found). Existing playlist capabilities are app-owned:
  1. YouTube Music remote playlists via the existing InnerTube/YouTube Music client and account APIs.
  2. Local on-device playlists via `data/spotify/LocalPlaylistStore.kt` blob `0fc00efafbfc42263791d3c1254d39d93d8430f0`.
- The existing local store is SharedPreferences JSON + StateFlow and stores `LocalPlaylist(id,title,songs: List<Song>)`. It is already the local persistence backend; it must be checked for reuse/extension before any new repository/database is proposed.
- Current local store has CRUD only for create/save, delete, rename, get and load. It does not yet model generated playlist metadata, cover, pin, sort, reorder, remove-track, export, regeneration, or explicit persistence failure status. `apply()` is asynchronous; current code updates StateFlow before persistence and swallows JSON/persist exceptions. A careful extension could support the missing generated-playlist features while preserving the existing format/backward compatibility. Do not create a new backend service or duplicate store.
- Existing YT Music playlist API is not the same persistence domain as generated local playlists. Keep remote account playlists and local generated playlists distinct, and reuse existing YT Music APIs only for user-requested remote playlist actions.
- There is an existing `PlaylistOwnershipTest.kt`; inspect its exact assertions and call sites before changing playlist ownership/fallback behavior. No app-side Room dependency or app-level DAO/entity tree exists on Main-dev; do not add Room solely to mirror LastWave.

### LastWave scrobble architecture is a reference, not a second scrobbler to install
- LastWave `ScrobbleRepository.kt` blob `716f22aa35db1dd332ba4c1445910a83bd2fe4a9` serializes signed Last.fm writes, waits/paces rate-limited writes, tracks now-playing dedupe, emits scrobble events and retries a bounded in-memory queue (max 200) on later writes.
- Its queue is process-memory only; it is not durable across process death. Do not describe it as durable offline persistence or copy this limitation as a final reliability solution.
- LastWave `LastFmApiService.kt` (`92520875e49f75642a474c01508fb6b00ea4c9c0`), `LastFmSigner.kt` (`30ab7842b68f8814e046117e4ada0f7ea32ecbf6`), `LastFmRateGuard.kt` (`b4a6a0605f29a622673bb593ab4021b2e906aa86`) and `SessionPreferences.kt` (`f16e782a85811d86d5a00056ff080e1439e93700`) are LastWave-specific DI/Retrofit/DataStore infrastructure. Main-dev already has Ktor client, signer, settings and a live playback integration. Port behavior selectively, not duplicate service/client/settings/auth stack.
- LastWave's session model uses BYO API key/secret and browser callback coordination; Main-dev currently has its own API credential settings and mobile-session login UI. Do not overwrite Mylo's existing auth UX without an explicit need.

### Current reuse/adapt ledger (provisional)
- Reuse unchanged: Main-dev PlaybackService scrobble/ListenBrainz ownership; LastFM and ListenBrainz APIs; AppSettings; ListeningRecorder/ListeningStats; Replay summary and UI; YouTube Music playlist API; LocalPlaylistStore as current local store.
- Adapt after deeper audit: LastWave's Stats information architecture and Last.fm overview queries; Generate domain algorithms/modes/playlist naming; generated-playlist lifecycle and reliability patterns; LastWave's signed-write pacing/dedupe/retry ideas only where Mylo's current manager lacks them.
- Do not port: LastWave's parallel playback scrobbler, Room stack wholesale, synthetic local stats, Spotify account projections, YT sync, download/local-audio hub, or any unrelated shell/navigation.
- Build only verified gaps: missing generator domain capability, missing generated-playlist management actions, or missing Last.fm read/profile APIs — and only after checking equivalent code in Main-dev by behavior, not just names.

### Next audit slice
- Trace Main-dev's complete ScrobbleManager/LastFM lifecycle and LastWave MusicPlayerSession/ScrobbleRepository hook, including track identity and timestamp semantics.
- Inspect Main-dev LocalPlaylistStore callers, PlaylistOwnershipTest and remote playlist CRUD path; decide whether local store extension is sufficient.
- Complete Generate mode-by-mode source/algorithm/error/cancellation audit and map each dependency to Main-dev equivalents.
- Inspect exact FAB/host diff and its Compose test assertions from scrobble-ui; map onto Main-dev's four-tab host without removing Explore.
- Inspect relevant LastWave and Main-dev tests; list missing tests and acceptance criteria.

## 12. Audit pass — Generate modes, screen behavior and Main-dev duplicate check (2026-11-18 19:29:04 IST)

### Main-dev duplicate check
- Recursive Main-dev source tree (2,011 paths) was checked for generate, recommend, taste, similar, mix, radio, smartplaylist and equivalent names.
- No playlist-generation feature/repository/ViewModel exists in Main-dev. Existing Automix code is audio transition/mixing behavior, not a recommendation playlist generator; it is not a substitute.
- Main-dev has only PlaylistOwnershipTest.kt for app-side playlist behavior among the matching app tests; it tests YT playlist ownership parsing, not generated playlists. No app-side Last.fm/ListenBrainz/scrobble tests were found in the test tree.
- Existing MainViewModel and LocalPlaylistStore remain the reuse points for local playlist persistence and app playback/navigation. This is a real missing product capability, not a naming-only duplicate.

### LastWave Generate behavior confirmed
- GenerateViewModel.kt blob 74b283322be32a9a17be644a3679f7ee76fd8398 defines nine modes:
  1. TOP — Last.fm top tracks for selected period.
  2. RECENT — recent Last.fm tracks.
  3. SIMILAR_TRACKS — YouTube Music related/radio + Last.fm similar-track source from a selected track seed.
  4. SIMILAR_ARTISTS — related artists and their top tracks + YouTube artist radio.
  5. TAG — Last.fm tag tracks + YouTube tag search.
  6. MIX — multi-source weighted personal mix.
  7. RECOMMENDATIONS — multi-source scored/diversified recommendation engine; fixed recommendation count.
  8. NEVER_HEARD — excludes tracks from multiple known/heard sources.
  9. LIBRARY — currently shares the TOP repository call with selected period; despite its label, this is not an independent library-only algorithm in the ViewModel.
- TOP/LIBRARY period chips: overall, 12 months, 6 months, 3 months, 1 month, 7 days. Track count is 5–35 except fixed-count Recommendations.
- Similar Tracks supports typed track + artist, track search, loading top tracks as seed suggestions, choosing a result, and preserving a selected YouTube video ID when available. Similar Artists supports typed artist search and top-artist suggestions. Tag mode has text input plus 12 quick chips.
- MIX and Recommendations combine Last.fm taste/history with YouTube Music history/likes/home feed, radio/discovery and local seeds; guest/offline fallbacks use local/YouTube chart sources. The 1-hour TasteProfile cache is keyed by Last.fm username and YouTube Music account identity.
- Generate operation has a 60-second timeout, progress messages, candidate over-fetch, availability precheck (except RecommendationEngine's own filtering), dedupe, saved-playlist freshness preference, unique playlist naming, subtitle/mode metadata, repository save, optional queue replacement for Start Mix, asynchronous artwork enrichment and navigation to the saved playlist.
- Track identity in LastWave is GeneratedTrack (name/artist/artwork/url/album). It extracts YouTube IDs from URL and has identity/dedup helpers. Port must map into Mylo Song and preserve exact videoId; no title/artist-only replacement when videoId exists.

### Generate UI/interaction observations
- GenerateScreen.kt blob 06dbba0c77d1e06d02d60abfd98455f13d22f278 uses nine selectable mode rows, an options panel conditional on selected mode, period chips/tag chips/seed search, count slider, Generate button, progress card and dismissible error card.
- The screen collects StateFlow and handles one-shot navigation events. The GenerateViewModel owns state and repository work; this separation should be retained when adapting the UI to Mylo.
- Confirmed UI gap: GenerateUiState.isSearchingSeed is set by ViewModel, but GenerateScreen does not read/render it; seed search has no visible loading indicator. Empty seed results also have no explicit empty copy. Error is dismiss-only; no explicit retry action.
- During generation, mode option panel is hidden and mode selection is blocked; progress card is shown. No explicit cancel-generation action exists. Leaving the screen cancels ViewModel scope work; this interaction needs deliberate integration with static-tab switching and FAB dismissal.
- The ViewModel's queueNextResult is a one-shot flag consumed before validation, preventing a failed Start Mix launch from unexpectedly queueing a later manual generation. Preserve this behavior if the Start Mix entry point is included in Mylo scope.

### Confirmed generator implementation risks
- GenerateRepository.kt blob e81bac3a8c938402931225e5c8f5a7cbda1645df has explicit cancellation rethrow in several methods, but broad runCatching / catch(Exception) paths in taste profile reads, local/chart fallback, similar-to-recent, similar-to-YouTube expansion, recommendation fallback and never-heard inputs can swallow coroutine cancellation and continue unnecessary work.
- Previously identified TOP/RECENT catch paths also fall back without rethrowing cancellation. Any port needs a systematic cancellation-safe wrapper/pattern, not line-by-line copying.
- Randomized Last.fm pages in Similar Artists/Tag and random/shuffled seeds in MIX make outputs non-deterministic; tests should validate invariants/ranges, not fixed full output ordering.
- precheck() and video-ID pinning can drop unavailable/unresolvable tracks. The final playlist may contain fewer than requested; current code only errors if zero remain. Decide expected partial-result UX and report count accurately.
- filterPlayable() / pinnedWithVideoIds() use bounded fan-out and strict ID resolution; unresolvable rows are dropped rather than replaced with a wrong match. Preserve strict identity safety.
- GenerateViewModel launches background artwork enrichment with a broad empty catch and no cancellation-specific branch. This is non-blocking, but failures are invisible; decide whether diagnostics/quiet failure is appropriate.
- GenerateViewModel catches TimeoutCancellationException before generic cancellation; the timeout surfaces an error, external cancellation is rethrown. Keep that distinction.

### Existing LastWave test coverage relevant to Generate
- Present: GenerateSeedSearchTest, NeverHeardGenerationTest, TrackIdentityTest, PinnedWithVideoIdsTest, DistinctSongsPerformanceTest, GenresPeriodStatsTest and related generation helpers.
- GenerateSeedSearchTest covers stale search results not replacing newer input, mode changes rejecting pending responses, and selecting artists rejecting stale top-artist responses.
- NeverHeardGenerationTest checks heard-source exclusions and playlist subtitle/mode metadata.
- TrackIdentityTest covers normalized key identity, same video ID despite title drift, different-song separation and duplicate video IDs.
- No dedicated test was found for the full GenerateViewModel generation flow across all nine modes, weighted Mix ranking, RecommendationEngine scoring/diversity, timeout/partial availability, playlist save failure, queue replacement or artwork-enrichment lifecycle. These are required port regression tests.

### Provisional integration plan update
- Generate is a genuine missing feature in Main-dev; port the domain algorithm/capabilities and required supporting data sources, but do not bring LastWave's whole repository/DI/Room architecture. Use existing Main-dev Last.fm API/configuration, InnerTube/YouTube Music client, Song model, PlaybackService/queue APIs, AppSettings and LocalPlaylistStore after compatibility mapping.
- Preserve the LastWave product modes only if each can be implemented truthfully with Mylo sources. Clearly distinguish unavailable Last.fm taste from device-local listening data; no fake profile data.
- Extend the existing local playlist store only after a full caller and backup-compatibility audit. The new feature should add generated playlist metadata/actions without duplicating a second persistence backend.
- Add tests around all modes and failure paths before declaring the port complete.

### Next audit slice
- Finish exact GenerateRepository source/dependency mapping and recommendation-engine scoring; trace every dependency to Main-dev equivalent or gap.
- Audit LastWave playlist ViewModel and UI callbacks against Mylo local/remote playlist operations, including playlist details, track menus, persistence and playback.
- Complete Mylo FAB source diff and host/back/dismiss path against Main-dev's current four-tab layout.
- Continue scrobbling lifecycle/reliability audit and inspect tests/CI metadata only when exact commit is known. No builds in planning phase.

## 13. Audit pass — playlist action mapping and FAB/navigation source isolation (2026-11-18 19:33:16 IST)

### Exact branch heads before this documentation update
- Main-dev: 11478fde56f1817cb777b296e60907359086f383.
- scrobble-ui: 5bf065da305e6bb663960f9e09d30572dcdc0f32.
- last-wave: 3739bbf332ea50218c969e9ba760b9c7b4492040.
- Context file prior blob: 0c1106e9886670dee444f0376c4cedc5065c5d41.

### LastWave playlist action trace completed
- PlaylistViewModel.kt blob 0fe9109f3a0cce5cd3e23d90f6822e13e0678162 owns load/list state, sort, expanded rows, create, rename, cover, pin, make-local, remove, reorder, delete, multi-delete, export, download/cancel, regenerate, scrobble delete and artwork refresh.
- PlaylistScreen.kt blob 2bea1f6d4f9ccc9f08164b85a7a95a485d69fa56 exposes search, four sort modes (newest/oldest/name/track count), create dialog, local songs/download entry, generated cards, selection/multi-delete, open detail, play/shuffle, export, rename, cover, pin, download, regenerate, make-local and delete.
- PlaylistDetailScreen.kt blob 54712d5106f9a244677c3679373609948ddf110b exposes track playback/context menu, track sort modes (custom/date/name/artist/play time), search, drag reorder for local playlists, cover picker/reset, pin, download/cancel, make-local, regenerate, rename, export, YT sync toggle and delete. Track context includes remove-from-playlist, delete-scrobble and artwork refresh.
- PlaylistViewModel's local playlist CRUD routes to PlaylistRepository/Room; negative IDs route to YT Music library manager. This ID split is central to keeping local generated playlists separate from remote account playlists.
- PlaylistReorderRequestsTest.kt and PlaylistTrackRowsTest.kt exist; no direct full ViewModel persistence/failure test for all actions was found. PlaylistRepositoryReadFailureTest.kt covers repository read-failure behavior.

### Mylo playlist operation mapping refined
- Main-dev MainViewModel.kt blob 38998fc5925ee8c42886dc7ad23bf73f5b66b378 already owns remote YT playlist create, rename, reorder and delete. It creates remote playlists in batches of 50, with local fallback through LocalPlaylistStore only for the createPlaylistWithVideoIds flow.
- MainActivity.kt blob f48697f73f299161856eb392d5600f1767e3c093 already routes remote playlist menus and reorder sheet through MainViewModel. Do not reimplement remote YT playlist APIs or change existing remote ownership semantics.
- Main-dev LocalPlaylistStore is only the on-device fallback/import store, with create/get/rename/delete and list flow; no generated metadata or track reorder/remove API. Extend this existing store/model only for the generated-local playlist lifecycle after backward-compatible JSON and all caller behavior is fully designed.
- The remote YT playlist APIs are not a separate Mylo-owned server backend. No dedicated playlist endpoints were found in the Go backend. There is no reason to create a new server/backend or a second local persistence system for this port.
- Existing LocalPlaylistStore IDs are string IDs with browseId prefix local:playlist:, while LastWave local playlist IDs are Long timestamps and remote playlist IDs are negative Longs. Do not reuse LastWave numeric ID conventions; preserve Mylo's existing browseId scheme and ensure uniqueness beyond millisecond collisions.
- Main-dev playlist model uses Song/videoId; LastWave SavedPlaylist uses GeneratedTrack/URL. Ported generated tracks must be normalized to Song once at the repository boundary and persisted using existing Song serialization fields.

### FAB and Scrobble navigation — exact source behavior
- scrobble-ui ScrobbleFab.kt blob c70fd7c0840da6a13bac59497f24053a7c17084d:
  - fixed 62dp surface; GraphicEq when closed / Close when open;
  - spring scale/alpha/lift with snap when reduce motion enabled;
  - hidden remains in stable layout slot but has no click action and is removed from accessibility semantics;
  - 48dp+ touch target, Role.Button, action-specific accessibility label;
  - home-only helper hides for overlays, full player and keyboard; scroll folds to 0.88 scale and 4dp lift.
- scrobble-ui MainActivity.kt blob 4c587758a397b831ccb103df29e5c683ad7e72c:
  - saveable showScrobble and scrobbleSection state; separate Scrobble nav scroll connection;
  - normal tabs are exactly Home / Explore / Library / Search; do not remove Explore;
  - Scrobble static tabs are Stats / Generate / Playlists; selected tab only updates scrobbleSection;
  - normal tab handler clears showScrobble and restores normal navigation; Scrobble tab handler does not clear it;
  - FAB open resets selected Scrobble section to Stats (index 0); second tap closes;
  - BackHandler and top-bar back close Scrobble;
  - content key switches to a Scrobble destination; current old ScrobbleScreen is only a host in the source branch and its screens/business logic are explicitly out of the port boundary.
- FAB visibility is suppressed by Home overlays, detail pages, mood detail, library grid, player and IME; it remains visible in Scrobble mode to act as the Close button. Its bottom offset is measured from the complete mini-player/navigation stack and it sits 10dp above that stack.
- In Standard mode the FAB is positioned relative to the existing FloatingBottomBar/mini-player stack. In Glass mode it uses the existing GlassNavBar and a separate Scrobble scroll connection. Preserve existing Glass/Standard appearance and player controls.
- Critical Glass-specific adaptation: Main-dev GlassNavBar.kt blob 88e50b6a8d9d14e5ad1f6dc00d84f2d710bd5026 currently always treats the last tab as the standalone circular tab (Search). If passed the three Scrobble tabs unchanged, Playlists would be detached as a standalone circle. scrobble-ui GlassNavBar adds an optional standaloneIndexOverride (with -1 to keep all Scrobble tabs inside the pill). Only this minimal behavior should be adapted; do not wholesale port unrelated reduced-motion changes in GlassNavBar/FloatingTabBar.
- Main-dev FloatingBottomBar.kt blob 445f6f8cc9e480e19a0ce7846f68f94fcc6f17dc is identical between Main-dev and scrobble-ui at the inspected paths; no need to port it. FloatingTabBar has a scope API for standaloneTab and GlassNavBar chooses which index is standalone.
- scrobble-ui ScrobbleFabComposeTest.kt blob 067af4908126b38c39e4839f82ea65d58eda45dd covers visible button/click, hidden accessibility removal and close label. It does not test home/overlay visibility helper, host placement, Glass standalone behavior, tab replacement, BackHandler, dismiss restoration, or screen recreation. Add targeted host/navigation tests for these when implementing.

### Integration hazards and acceptance criteria
- The source branch's onTabSelected closes Scrobble when a normal tab is selected; the Scrobble nav callback only changes Scrobble section. Keep these callbacks separate to avoid a tap on Generate/Playlists accidentally closing Scrobble.
- Keep the normal selectedTab value unchanged while Scrobble is open; closing restores the exact prior Home state and does not reset Home/Library/Search scroll. If normal tab navigation is intentionally tapped from another entry point, then apply the existing normal onTabSelected semantics.
- Persist Scrobble mode and selected section only as appropriate: source uses rememberSaveable. Audit process/activity recreation behavior and ensure stale open mode does not reappear above an incompatible overlay.
- Ensure FAB remains accessible and operable when open; no click-through when hidden; no duplicate click surface; BackHandler precedence should close nested LastWave screen/modal first, then Scrobble mode.
- In Glass mode, all three Scrobble tabs must stay in the pill; the Playlists tab must not inherit Search's detached-circle role.
- User-approved source boundary remains strict: Scrobble FAB + nav host pattern from scrobble-ui only; all Statistics/Generate/Playlists screens, state and business/data logic come from LastWave and are adapted to Main-dev.

### Next audit slice
- Finish LastWave scrobble service state machine and compare it against Mylo ScrobbleManager/PlaybackService; identify concrete retry/duplicate/threshold defects.
- Complete Stats aggregation integrity audit and compare all visible stats to Mylo Replay.
- Complete LastWave playlist repository failure/CAS/retention review and map generated local playlist actions onto LocalPlaylistStore with backward-compatible serialization.
- Finish dependency-by-dependency mapping for GenerateRepository and RecommendationEngine, including existing Main-dev InnerTube and queue methods.

## 14. Audit pass — playback/scrobble state machine and cross-branch correctness (2026-11-18 19:35:20 IST)

### Exact branch heads before this documentation update
- Main-dev: 11478fde56f1817cb777b296e60907359086f383.
- scrobble-ui: 5bf065da305e6bb663960f9e09d30572dcdc0f32.
- last-wave: 963e86c6151959c9c2ff6e76350e489472e72467.
- Main-dev PlaybackService.kt blob: ddd8ff03c57a8e4ab16e7a23b03863ca1b517f59.
- Main-dev ScrobbleManager.kt blob: 153b488ed0eea8893ffb0e7983de9fd26a569847.
- Main-dev LastFM.kt blob: 5e3a7099255b9b54e8da7fb3a3c496e11fc81879.
- LastWave ScrobbleRepository.kt blob: 716f22aa35db1dd332ba4c1445910a83bd2fe4a9.
- LastWave MusicPlaybackServiceSession.kt blob: 705cbbdf24f8bf32b1ded0ef38db9bad398e1417.
- LastWave MusicPlaybackService.kt blob: dbedd4cf8d555102a1a4f092afae8660ea906169.
- LastWave MusicPlayer.kt blob: 31c0897017d31b58ac13bfb702871075670990e1.
- LastWave SongPlayStatsRepository.kt blob: 42650b8f31c764b2a6fd1bfd955df27b0a5fa5f3.

### Main-dev existing Last.fm playback flow — verified end to end
1. PlaybackService observes AppSettings flows for enabled/session/API key/secret/endpoint/min duration/delay/now-playing/primary-artist.
2. When enabled and credentials exist, it configures the existing LastFM object and creates/reuses ScrobbleManager.
3. Player state callback invokes onPlayerStateChanged; track transitions call onSongStop then onSongStart for the new Song; pause/resume preserves remaining timer.
4. ScrobbleManager calculates the threshold from duration × percent, capped by max delay; it ignores tracks at/below min duration; it stores the start epoch timestamp; optional now-playing and final scrobble use existing LastFM signed POST methods.
5. ListenBrainz is a separate existing path in PlaybackService and remains untouched.
- Main-dev's LastFM client has no read-side Last.fm user/profile/chart methods in the inspected implementation; it currently covers auth, session, now-playing and scrobble writes. LastWave Stats/Generate require read APIs such as user.getinfo, user.getrecenttracks, user.gettoptracks, user.gettopartists, user.gettoptags, artist.getsimilar, track.getsimilar, tag.gettoptracks and chart endpoints. These are missing capabilities, but can be added to the existing Ktor LastFM client if required; do not introduce a second HTTP client solely for Last.fm reads.
- Main-dev ScrobbleManager has no pending offline queue. LastFM.scrobble returns Result failure and the manager logs it; transient network failures can lose that scrobble. This is a confirmed reliability gap relative to LastWave's retry idea, though LastWave's queue itself is not durable.
- Main-dev ScrobbleManager starts the timer only when duration is known. If duration is null at initial onSongStart, the timer returns; a later onSongResume sees no remaining delay and does not initialize a timer. Verify whether PlaybackService later reports a new player-state callback after duration becomes known; if not, short/streaming tracks with initially unknown duration may never scrobble. Keep this as a targeted edge-case to prove with tests, not yet a confirmed runtime defect.
- Main-dev uses timestamp captured at track start, pause/resume does not change it, and stop cancels the timer. It handles repeat transitions via explicit stop/start from media-item transition. Preserve that ownership; do not add a parallel LastWave detector.

### LastWave scrobble defects confirmed from exact code
1. **Duplicate offline queue entries:** ScrobbleRepository.scrobble() enqueues every retryable failure. MusicPlaybackServiceSession.startDetector() then resets submissionAttempted=false and retries up to three times after a retryable Result.Failed. Each retry calls scrobble() again and can enqueue the same track/timestamp again. On recovery, flushPendingScrobbles() may submit duplicates. Queue needs idempotency key (track identity + timestamp) or caller/repository retry ownership must be singular.
2. **Incorrect transition fallback threshold:** detectTransition() submits pendingPreviousTrack when accumulatedMs >= 30,000 and submissionAttempted is false. It does not compare accumulatedMs with the configured percent/max-delay threshold. A manual skip after 30 seconds can therefore be scrobbled even when the actual configured threshold was not met.
3. **Consecutive same-track identity:** detectTransition() uses normalized artist + title as the song key (videoId is used only for podcast episodes). Repeating the same track consecutively can be treated as no transition, so its timing/scrobble state is not reset for the new play. Mylo's transition callback explicitly resets on a media-item transition, including repeat-one.
4. **In-memory only retry queue:** offlineQueue is a ConcurrentLinkedQueue capped at 200; it is lost on process death and drops oldest items on overflow. It is not a durable offline queue despite reliability comments.
5. **No ScrobbleRepository-specific tests found** in LastWave's app test tree. Main-dev also has no scrobble unit tests. Add deterministic tests for threshold, pause/resume, transition/repeat, offline duplicate prevention, process restart/durable queue, rate limit and auth changes.
6. LastWave detector increments accumulatedMs by 1,000 per one-second delay loop rather than deriving actual elapsed monotonic time. Scheduler drift/background throttling can skew listened time; Mylo's ScrobbleManager uses elapsed wall-clock deltas to subtract pause time. A port should use monotonic elapsed time, not copy loop-count timing.
7. LastWave detector uses a fallback duration of 180,000ms when duration is missing, then calculates threshold. Mylo's manager currently refuses to start until duration is known. Product behavior for unknown duration needs an explicit decision and tests.
8. LastWave MusicPlaybackServiceSession imports ScrobblerPreferences and consumes submitNowPlaying/scrobblePercent, but the detector path inspected does not check ScrobblerSettings.enabled or selectedPackages. Confirm intended scope before porting those preference fields; do not add unrelated external-app notification-listener scrobbling.

### Correction / precision note — LastWave local play stats
- Earlier context described SongPlayStatsRepository as actual listened-time tracking. More precisely, MusicPlayer.recordLocalListenSignal() reads the outgoing player's media position at transition and passes that position as listenedMs; it does not sample wall-clock audible intervals. Seeking forward can inflate the value, and keying is normalized title|artist, which can merge different video IDs with the same title/artist.
- Mylo ListeningRecorder instead accumulates wall-clock intervals only while playback is active and is keyed through Mylo's track model; it is the preferred local listening source. LastWave's SongPlayStatsRepository may still be useful as a source of per-track skip counts, but its play-time and identity semantics must not replace Mylo Replay.
- LastWave MusicPlayer.recordLocalListenSignal() is called on media-item transitions before the outgoing state is overwritten. It classifies AUTO or >=85% media-position as completed and other transitions as skips; this is transition-position telemetry, not a per-play timestamp history.

### Reliability adaptation direction (provisional)
- Keep Main-dev's existing PlaybackService → ScrobbleManager → LastFM submission path.
- Adapt only proven reliability improvements: monotonic timer semantics, now-playing dedupe, serialized signed writes/rate pacing, idempotent retry, durable pending scrobbles if product approves, and cancellation-safe API wrappers.
- Do not port LastWave's service detector, track identity key, in-memory queue, or threshold fallback as-is.
- If durable queue is added, persist only the minimum scrobble payload (artist, track, album, timestamp, stable idempotency key), bound size with explicit surfaced retention behavior, retry on network/app resume, and avoid duplicate submissions after uncertain HTTP outcomes. This should be implemented within Mylo's existing settings/storage patterns, not a server backend.

### Next audit slice
- Complete Statistics data source/range calculations and identify which Mylo Replay charts can be reused.
- Review LastWave playlist repository persistence/write failures and Mylo LocalPlaylistStore concurrency/atomicity risks.
- Complete Generator dependency map to Mylo InnerTube/Last.fm/local stats/playback/playlist interfaces.
- Then compile the full reuse/adapt/avoid ledger and ask only unresolved product questions before implementation.

## 15. Audit pass — playlist persistence, concurrency, backup and migration risks (2026-11-18 19:36:35 IST)

### Exact branch heads before this documentation update
- Main-dev: 11478fde56f1817cb777b296e60907359086f383.
- scrobble-ui: 5bf065da305e6bb663960f9e09d30572dcdc0f32.
- last-wave: 27c2ade67d2ed526bbc3e58cba7ed1eee490d639.
- Main-dev LocalPlaylistStore.kt blob: 0fc00efafbfc42263791d3c1254d39d93d8430f0.
- Main-dev Backup.kt blob: d7d3451530a32a8011cd1d4556c220cac844519c.
- Main-dev backup_rules.xml blob: f1706d5ce4c03a2e70be48e9f4189757ca362c42.
- Main-dev data_extraction_rules.xml blob: b0362538939ca23ffbbde0e434aa09b3569d05ee.
- LastWave PlaylistRepository.kt blob: 37bd36c5d269b494aafd8c536fc54eac50f3f5a5.
- LastWave SavedPlaylistEntity.kt blob: daa050e59ccf9aa8f054eea45a22540a882e0207.
- LastWave PlaylistPublicMirror.kt blob: 333edda8fda0358038d322a5684d331bfe24df06.

### Main-dev LocalPlaylistStore risks and extension constraints
- Store uses one SharedPreferences JSON array under file bitchord_local_playlists.xml, key playlists_json. Model currently contains only id/title/songs. Existing Song serialization preserves videoId/title/artist/thumbnail/duration/artistId/albumId/albumName/isVideo.
- savePlaylist creates IDs from currentTimeMillis; two creates in the same millisecond can collide and replace one another because the updated list filters by ID. New generated-playlist IDs need collision-safe uniqueness while preserving existing string IDs and local:playlist:<id> browseId contract.
- Mutations replace the whole in-memory list and call persist(). There is no Mutex/synchronized transaction around read-modify-write; concurrent create/rename/delete/update operations can overwrite a newer snapshot.
- StateFlow is updated before persist succeeds. persist() uses runCatching and SharedPreferences.Editor.apply(), so serialization/write failures are swallowed and callers still receive a success-looking return value. apply() is asynchronous; callers cannot know whether disk persistence has completed.
- load() wraps the entire JSON parse in runCatching and leaves the in-memory list empty/unchanged on malformed JSON; there is no corruption recovery, partial row salvage, error signal or backup restoration path.
- renamePlaylist/getPlaylist strip the local:playlist: prefix and then VL for compatibility. Keep current browseId resolution intact; don't replace it with LastWave's Long/negative-ID scheme.
- deletePlaylist accepts raw id or browseId. Keep all existing callers working; verify imported local playlists remain reachable from Library cards and detail route after schema extension.
- LocalPlaylistStore has no direct tests in the Main-dev test tree. Add store tests for backward-compatible read, unknown optional fields, malformed single row, duplicate IDs, concurrent mutations, failed write, rename/delete by raw ID and browseId, and round-trip Song metadata.
- Existing local playlist data is included in Android Auto Backup/device transfer by default: app manifest allows backup and the current backup exclusion XMLs do not exclude bitchord_local_playlists.xml. But custom user-managed Backup.kt exports only AppSettings preferences and ListeningStats buckets; it does not include LocalPlaylistStore playlists. If generated playlists are important user data, add them to the explicit backup schema and import atomically without breaking older backup versions.

### LastWave Room repository good patterns and confirmed gaps
- SavedPlaylistDao is a single-row-per-playlist table; tracks remain JSON within each row. It uses @Transaction for retention and full mirror replacement, updateTracksIfUnchanged CAS for video-ID backfill and sync, and a saveMutex for several read-modify-write operations.
- The generated retention rule keeps all pinned rows plus the newest 20 unpinned generated rows; custom, liked and local rows are exempt. This is a LastWave product policy, not an implementation default. Do not import this cap without product approval; Mylo should not silently delete generated playlists.
- PlaylistRepository.save() catches broad Exception around DAO upsert/trim, does not rethrow CancellationException, and returns entity.toDomain() even after a failed DB write. Callers can show a saved playlist that never persisted. This must be corrected in any adapted code.
- rename(), setCustomCover() and setPinned() read an entity then call persist() without saveMutex; concurrent edits to different fields can overwrite one another with stale entity snapshots. Keep updates serialized or use field-specific SQL/CAS.
- addTrack() uses runCatching around suspend InnerTube lookup, and several PlaylistViewModel download/regenerate methods use runCatching around suspend reads; cancellation can be swallowed. Every suspend boundary needs explicit CancellationException propagation.
- PlaylistViewModel.load() catches local playlist read failure and sets playlists to emptyList(), making an I/O error look like an empty library; should expose an error/retry state.
- PlaylistRepository.getAll() retries failed reads and uses public mirror only if Room is truly empty, which is a strong integrity pattern. However malformed entity rows are mapNotNull'd after runCatching and can disappear without a visible recovery state.
- Public mirror is a separate user-visible recovery/export mechanism, not a backend. It has its own schema/version and mutex; Mylo should not copy its public Downloads path unless specifically requested.

### Integration implications
- Use Main-dev LocalPlaylistStore as the only local generated-playlist persistence owner initially; extend its JSON model with optional fields/defaults so existing playlists load unchanged. Do not introduce Room or a remote playlist server solely for Scrobble.
- Keep YouTube Music remote playlists in existing MainViewModel/YtMusicRepository and local generated playlists in LocalPlaylistStore; no cross-writing or accidental remote ownership.
- New generated playlist operations must update the existing Library shelf StateFlow and remain compatible with MainViewModel.localPageState/detail route. Do not fork a second list in Scrobble UI.
- Persistence operations should be serialized and report failure before navigation/toast success. Consider a single repository facade around the existing store if needed for suspend/cancellation/testability, but it must delegate to the same store and not own a second copy.
- Define backup schema extension (versioned, backward-compatible) for generated playlist metadata/tracks; export/import should validate the complete payload before replacing any existing data.
- Do not carry over LastWave's arbitrary 20-playlist cap, fake stats, negative remote ID scheme, or its success-on-write-failure behavior.

### Next audit slice
- Finish Stats calculation review and source mapping to Mylo Replay.
- Finish LastWave Generator source dependencies and identify missing Last.fm read APIs / existing InnerTube / local stats equivalents.
- Produce final screen-by-screen integration map and acceptance criteria; no code edits until user approves the plan.

## 16. Audit pass — Statistics correctness and Generator dependency mapping (2026-11-18 19:38:39 IST)

### Exact branch heads before this documentation update
- Main-dev: 11478fde56f1817cb777b296e60907359086f383.
- scrobble-ui: 5bf065da305e6bb663960f9e09d30572dcdc0f32.
- last-wave: 1f09a814de5f48100568d86e031158d233a6b28d.
- LastWave ListeningStatsViewModel.kt blob: 1425e7e14199feea2773f6450df3d5850a65d44c.
- LastWave HomeScreen.kt blob: 8460bf1a5fe545f25ce5ed4011804ed1e6f91386.
- LastWave HomeViewModel.kt blob: fa51a717abf7fa411586ae16ec19b21eb67efeb2.
- LastWave HomeRepository.kt blob: 784de3c151c8ee03f4689dd9fb3ad4884772f7d8.
- LastWave RecommendationEngine.kt blob: 58e5d3394443762673640e5061636f52eaa8a7ec.
- Main-dev YtMusicRepository.kt blob: efbd57934cd5f50449e2ceb579a9ce7cafe90ada.
- Main-dev ReplayScreen.kt and ReplayModel.kt were inspected on Main-dev; current Replay route is already used from Library and Settings, so preserve it.

### LastWave Statistics defects confirmed by full ViewModel review
- The detailed ListeningStatsViewModel uses only one lastPlayedAtMillis per track, not per-play timestamp buckets. MTD/YTD filter tracks by that single timestamp, then add each track's entire lifetime totalPlayTimeMs/playCount to the selected range. A track played once this month and 100 times last year can have all 101 plays attributed to this month.
- ALL_TIME/YTD/MTD timeline buckets assign the whole track lifetime aggregate to the year/month/day of lastPlayedAtMillis. They are not actual historical period totals.
- Session counts are inferred by clustering one timestamp per track with a 25-minute gap, forcing 15-minute minimum session durations, then forcing sessionsCount >= totalPlays/4 and longestSession >= 2×average. Not measured sessions.
- Most-active weekday duration is hardcoded to 35% of total listening time for whichever weekday has most track rows. Peak timeline slot is always current year plus total listening time. Neither metric is derived from listening-hour/day telemetry.
- Genre distribution allocates listening time/plays using descending tag weights rather than actual genre-to-track listening attribution; if tags are missing it invents six default genres. Album fallback groups tracks by artist. These are proxies, not measured stats.
- Empty Room stats are synthesized from LocalStatsRepository with playCount × 210 seconds and random last-played dates 1–30 days ago. This is fabricated history, not a valid empty state.
- totalPlays uses playCount.coerceAtLeast(1), making zero-count rows count as one. Track concentration percentages are clamped to 1–95 and 0–95-top1; they can diverge from the actual ratios.
- loadData wraps the entire DAO/local stats/taste read in runCatching and exposes no error state; DAO failure can appear as empty statistics. Several HomeRepository/HomeViewModel methods also catch Exception or runCatching around suspend calls without consistently rethrowing CancellationException.
- Detailed screen has only MTD/YTD/All-time ranges, while Mylo Replay already supports week/month/year/all-time. Do not copy the narrower range set or fake calculations.
- Main-dev Replay/ListeningStats is the stronger local source: actual active-play sampling, month/day/hour buckets, videoId identity, and existing Story/share/track/artist/album navigation. Use this as the local Statistics experience and keep Last.fm remote totals separate.

### LastWave Stats tab vs detail screen
- LastWave MainShell STATS tab renders HomeScreen, a Last.fm account/profile dashboard: scrobble total, track/artist/album counts, avatar, now-playing, recent tracks, sort modes (Recent/Most Played/Last 7 Days/Last 30 Days/Top Albums/Top Artists), pagination, friend-profile switching and pull-to-refresh.
- The HomeScreen also has local fallback via LocalStatsRepository, but it derives from playlist/download inventory and 210-second estimates, not real listening. Never use this fallback as local Replay data.
- The separate ListeningStatsScreen is pushed from HomeScreen and contains the detailed chart/habits/concentration dashboard above. It is not the same destination as the MainShell STATS tab.
- LastWave HomeRepository uses Last.fm user.getinfo, user.getrecenttracks, user.gettoptracks, user.gettopartists, user.gettopalbums; it coalesces in-flight recent/stats/initial requests and caches initial data for 30 seconds. HomeViewModel polls now-playing/recent while lifecycle is STARTED, loads more recent pages, and can switch between own/friend profiles.
- Mylo's current LastFM client lacks those read methods, but the existing Ktor LastFM client and AppSettings credentials/session can be extended. Avoid a second Last.fm client or replacing Mylo's existing login UX.
- LastWave HomeRepository fetchRecentTracksInternal/fetchStatsInternal and several top chart methods catch broad Exception, which can swallow coroutine cancellation. Preserve its in-flight coalescing/caching ideas only with cancellation-safe handling.

### Main-dev Generator source inventory — existing code to reuse
- Main-dev has no Generate UI/VM/repository, but it already has source primitives:
  - YtMusicRepository.home()/moreHome(): personalized YouTube Music feed and continuation (includes mood mixes/recommendation shelves).
  - YtMusicRepository.quickPicks(excludeSongIds): existing multi-fallback Quick Picks candidate loader; method exists but no current MainActivity/MainViewModel call site was found. It is reusable as a candidate source, not a full generator.
  - YtMusicRepository.radio(videoId): existing YouTube Music radio/related tracks; MainActivity.playRadio uses it and existing queue flow.
  - YtMusicRepository.search(), artistPage(), moodAndGenres(), moodGenreShelves(): existing track/artist search, artist browse, genre/mood discovery.
  - YtMusicRepository.history()/recentlyPlayed(), library(), userPlaylists(), libraryPlaylists(): existing remote history and account library sources.
  - ListeningStats.summary(ReplayPeriod), ListeningHistory, LocalPlaylistStore, Downloads and Song model: local taste/history/library and generated-playlist storage inputs.
  - MainActivity.playFrom() + QueueCoordinator.buildContextQueue()/playSongs(): existing queue replacement semantics. Reuse them; do not implement a second playback queue or directly manipulate Media3 from Generate UI.
- YtMusicRepository.quickPicks is a basic YouTube Music feed fallback chain (Quick Picks/Mix/Recommendations shelf → non-recent Home shelves → all Home track shelves → New Releases). It excludes video IDs supplied by caller and deduplicates by videoId. It is not LastWave's multi-source scoring/diversity engine, and its nested runCatching around Home/New Releases can swallow cancellation despite the outer repository call rethrowing cancellation.
- Main-dev has no RecommendationRepository or TasteProfileProvider equivalent by name/behavior. LastWave's weighted recommender is a genuine new domain capability; build it on Mylo's existing source primitives and Mylo local Replay data, not a duplicate API client or duplicate history store.
- Existing Main-dev Quick Picks/home feed and radio sources should be used for recommendations and Similar Tracks. Add Last.fm GET methods to existing LastFM client only for modes that truly need Last.fm profile/similar/tag data.
- LastWave LIBRARY mode currently calls the same fetchTopTracks(period) branch as TOP; it is not a distinct library algorithm. Do not preserve a misleading mode label. Define a real library mode from Mylo's existing library data or remove/rename it with user approval.

### RecommendationEngine algorithm reviewed
- LastWave RecommendationEngine starts with up to 40 YouTube Music feed tracks and 60 YouTube radio candidates at highest source weight 4, then runs up to five discovery cycles across similar tracks, similar artists, genre matches, tag matches, related artists and discovery pools, followed by bounded fresh-track refill.
- Scoring includes source bucket weight (12/26/38/50), artist affinity ×30, top artist +16, recent artist +8, YouTube feed +8, liked +6, recent +4, unknown artist +3, primary genre +4, multi-source agreement +10/+20, Last.fm match, gem/community scores, fresh +6 vs stale −20, familiar −22, and small random jitter.
- Selection first enforces artist/album/genre caps and a familiar-track cap, then progressively relaxes caps if the fresh pool cannot fill the requested count. Explicit exclusions remain strict. Results are interleaved by genre; highest-score track opens and second-highest closes the playlist.
- This is a real algorithm, but it has no dedicated tests for score weights, diversity relaxation, interleave, stable ordering, or full run behavior. Random jitter and random source pages make full-output snapshots brittle; test deterministic scoring helpers and selection invariants separately.
- Broad catch(Exception) in parallel source fetches can swallow cancellation; all source fan-out must rethrow CancellationException before returning partial results.

### Recommended Statistics/Generate destination composition (still provisional)
- Statistics tab: Mylo Replay local stats as default, reusing current ReplayScreen/ReplaySummary and preserving its existing Library/Settings entry points. Optional separate Last.fm account panel for remote scrobble totals/recent/top charts, explicitly labeled as remote Last.fm data.
- Generate tab: LastWave's nine-mode architecture adapted to Mylo data sources, with TOP/RECENT using Last.fm read API when connected and clear local/YouTube fallback labels when not; SIMILAR_TRACKS using existing Mylo radio plus Last.fm similar; SIMILAR_ARTISTS using Mylo artist page/search plus Last.fm similar artists; TAG using Mylo mood/genre shelves plus Last.fm tag tracks; MIX/RECOMMENDATIONS using Mylo Replay taste + Home/QuickPicks/radio; NEVER_HEARD using Mylo history/stats/library/downloads; LIBRARY implemented as an actual library-derived mode rather than LastWave's duplicate TOP branch.
- Generated playlists should be local by default (same LocalPlaylistStore and Library shelf). Do not route them through MainViewModel.createPlaylistWithVideoIds(), which creates remote YouTube Music playlists when signed in. Reuse existing playFrom/QueueCoordinator for playback.

### Next audit slice
- Verify Main-dev QuickPicks call sites and source primitives; map all nine generator modes to exact existing API calls and local data fields.
- Finish playlist store extension contract, backup compatibility and UI list/detail actions.
- Compile complete end-to-end flow matrix (FAB open/close/back, static nav, Statistics, Generate, Playlists, modals, playback, lifecycle, failure states).
- Ask the user only the remaining product decisions once the technical audit is complete.

## 17. Audit pass — final UI host map, Last.fm API gap and navigation integration risks (2026-11-18 19:40:40 IST)

### Exact branch heads before this documentation update
- Main-dev: 11478fde56f1817cb777b296e60907359086f383.
- scrobble-ui: 5bf065da305e6bb663960f9e09d30572dcdc0f32.
- last-wave: c505372b17cb71e81e66af97609f12d121f67c25.
- Main-dev MainActivity.kt blob: f48697f73f299161856eb392d5600f1767e3c093.
- Main-dev GlassNavBar.kt blob: 88e50b6a8d9d14e5ad1f6dc00d84f2d710bd5026.
- Main-dev FloatingBottomBar.kt blob: 445f6f8cc9e480e19a0ce7846f68f94fcc6f17dc.
- Main-dev ReplayScreen.kt blob: dfd04963c2b0063d1f350d30424663692a325be4.
- Main-dev ReplayModel.kt blob: f92990b7df88f5a71e1d1440818ef8cdae78190e.
- LastWave AuthRepository.kt blob: 8a4995e76f0b5ac4affac266537ab0f4aa7ba338.
- LastWave LastFmApiService.kt blob: 92520875e49f75642a474c01508fb6b00ea4c9c0.

### Mylo host integration map
- MainActivity is the current navigation owner: top-level content is selected by AnimatedContent key; pushed album/artist/playlist detail uses detailStack; Settings/account/source overlays have explicit precedence; BackHandlers are layered in one place; mini-player + navigation bars are measured as one bottom stack.
- Current MainActivity content-key order is: Spotify, Discord, History, Library grid, Settings sub-screen, account scrobbling, sources, Listen Together, Wear OS, Equalizer, Settings, Replay, detail page, then normal selected tab. Scrobble must be inserted as a top-level content key after transient overlays and before normal tabs, with detail route precedence preserved so playlist/artist detail can sit above a Scrobble tab without discarding Scrobble mode.
- The current MainActivity onTabSelected clears details/mood/overlays, closes Replay/History and sets selectedTab. The Scrobble tab callback must be a separate function that only changes scrobbleSection; add showScrobble=false to the normal-tab handler for defensive restoration. Do not route Scrobble tabs through onTabSelected.
- showScrobble and scrobbleSection should be saveable. Opening from Home resets section to Statistics; closing preserves selectedTab=Home and the Home/Library/Search/Explore scroll state. Switching Statistics/Generate/Playlists should preserve each tab's own UI state.
- Existing MainActivity already has Replay state and rememberReplayState(active). If Statistics reuses ReplayScreen, activate the same local summary only while the Statistics tab or existing Replay route is visible; avoid a second recorder, second stats repository or duplicate history load. Keep existing Library/Settings Replay entry points intact.
- LastWave MainShell's HorizontalPager, Hilt ViewModels, nav tabs and Liquid Glass navigation are not to be ported. They are LastWave shell architecture and conflict with the explicit scrobble-ui-only FAB/navigation source boundary.

### Last.fm API/action compatibility
- LastWave AuthRepository supports browser auth callback, BYO API credentials, direct username verification, mobile-session password flow, session-key storage, and track.scrobble.delete.
- Mylo already supports Last.fm token/session/mobile-session auth and optional password-based session key in its existing account/scrobbling settings. Preserve that flow; do not add LastWave's SessionPreferences/DataStore or replace Mylo's Settings UI.
- Mylo LastFM client is write/auth only in inspected code; extend this existing Ktor client with generic unsigned GET/read methods if Last.fm profile/stats/generator modes require them. Do not add Retrofit or a second Last.fm client.
- LastWave track.scrobble.delete requires a real original scrobble timestamp. AuthRepository explicitly documents that Playlist/Search menu actions pass null and therefore always return NoTimestamp; only Recent tracks carry date. Do not copy a delete-scrobble button into generated playlist detail unless the row has the original Last.fm scrobble timestamp and Mylo has a session key.
- LastWave AuthRepository catches broad Exception in completeWebAuth/signIn/obtainSessionKey/deleteScrobble without consistently rethrowing CancellationException. Do not copy this cancellation behavior.
- user.getfriends/friend-profile switching is a LastWave Stats feature; Mylo has no existing friend-profile state. It is optional product scope, not a required dependency for personal Statistics/Generate.

### Mylo UI alignment rules derived from inspected screens
- ReplayScreen is the closest native visual/data reference: MaterialTheme typography and color scheme, artwork-aware mesh background already used by Mylo, restrained dark/light overlay, period chips, compact ranked rows, existing card/story treatment, share and playback callbacks.
- Reuse Mylo's current spacing, text styles, artwork tiles, button/sheet components and top-level content transitions. Do not port LastWave's Liquid Glass effects, blur/backdrop stack, Hilt nav shell or a second design system.
- Statistics can use ReplayScreen/ReplaySummary as its device-local baseline; any Last.fm account data must be clearly labelled as remote scrobbles and not combined with local listen-time totals.
- Generate UI should use Mylo's existing list/card/chip/slider/dialog conventions and work in both Standard and Glass modes; Glass affects chrome/navigation, not a separate screen implementation.
- Playlists should use Mylo's existing Library shelf identity and local Song rows. Reuse existing artwork/track row/context menu, playlist picker, download and queue actions where their semantics match. Keep generated-local playlist actions separate from remote YouTube Music playlist management.
- LastWave Stats/Generate/Playlist screens are behavioral references only; rewrite/adapt the UI to Mylo's design system rather than copying visual components.

### Confirmed open product decisions for final planning review
1. Statistics destination content: LastWave STATS tab is a Last.fm account dashboard, while Mylo Replay is device-local listening analytics. Recommended safe default is Mylo Replay as the Statistics landing content, with a clearly separate Last.fm account overview section/entry. These must not be merged into one total.
2. Friend profiles: LastWave supports switching to Last.fm friends; Mylo currently has no friend-profile state. This is optional and should not be added unless explicitly wanted.
3. Generated playlist retention: Mylo should not inherit LastWave's 20-item unpinned generated-playlist cap without explicit approval. Default recommendation is no automatic deletion.
4. Generate dismissal: LastWave has no explicit cancel control. Mylo should add a clear cancel action and define whether closing Scrobble cancels an in-flight generation; do not allow a hidden task to silently create a playlist after the user closes the feature.

### Final audit checklist before implementation approval
- [x] Verified Main-dev branch head and case-sensitive branch name.
- [x] Read all available Main-dev repository instructions; no CLAUDE.md/AGENTS.md exists on Main-dev at the inspected head.
- [x] Read LastWave CLAUDE.md, AGENTS.md and DESIGN.md.
- [x] Read scrobble-ui CLAUDE.md; no AGENTS.md exists there.
- [x] Compared branch histories; whole-branch merges are prohibited by the agreed scope.
- [x] Verified Main-dev existing Last.fm/ListenBrainz scrobbling, Replay stats, playlist APIs/store, QuickPicks/radio, queue and navigation.
- [x] Audited LastWave Statistics, Generate, Playlists and playback/scrobble data flows and recorded concrete defects.
- [x] Identified source branch FAB/nav-only boundary and Glass detached-tab adaptation.
- [ ] Complete final source-by-source dependency map for every Generate mode and final modal/error/loading/empty/lifecycle matrix.
- [ ] Obtain user's answer on Statistics content and generation dismissal semantics before implementation.
- [ ] After approval, implement only on Main-dev, run tests/builds, review diff and verify current CI commit. No implementation, build, merge or PR has happened in this audit.

### Next action
Finish the final interaction/failure-state matrix and exact per-mode source mapping; then present the complete audit and ask the remaining product decisions. Continue updating this file with timestamp + source commit SHAs after each pass.

## 18. Audit pass — exact generator source mapping, local playlist detail gap and timer corrections (2026-11-18 19:43:15 IST)

### Exact branch heads before this documentation update
- Main-dev: 11478fde56f1817cb777b296e60907359086f383.
- scrobble-ui: 5bf065da305e6bb663960f9e09d30572dcdc0f32.
- last-wave: 533559e133c1b0f3c9b09b212ea81cbabf3c6635.
- Main-dev ScrobbleManager.kt blob: 153b488ed0eea8893ffb0e7983de9fd26a569847.
- Main-dev LocalPlaylistStore.kt blob: 0fc00efafbfc42263791d3c1254d39d93d8430f0.
- Main-dev YtMusicRepository.kt blob: efbd57934cd5f50449e2ceb579a9ce7cafe90ada.
- Main-dev TrackMatcher.kt is present under shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/sources/TrackMatcher.kt.
- LastWave GenerateRepository.kt blob: e81bac3a8c938402931225e5c8f5a7cbda1645df.
- LastWave GenerateViewModel.kt blob: 74b283322be32a9a17be644a3679f7ee76fd8398.
- LastWave TasteProfileProvider.kt blob: 3f8df078e9001d9d464492214fb6c5ba31ef2629.
- LastWave LocalTasteSuggestionEngine.kt blob: 3f8df078e9001d9d464492214fb6c5ba31ef2629.

### Generator dependency-by-dependency map
| LastWave dependency / responsibility | Main-dev equivalent | Port decision |
| --- | --- | --- |
| LastFmApiService GET | Existing LastFM Ktor client | Add generic unsigned GET + typed parsers only for needed Last.fm reads; no Retrofit client |
| SessionPreferences | AppSettings + existing LastFM runtime config | Reuse; no DataStore duplicate |
| InnerTubeMusicApi | Shared YtMusicRepository | Reuse existing home/search/artist/radio/mood/history/library APIs |
| RecommendationRepository.relatedFor | YtMusicRepository.radio(videoId) + existing queue/radio flows | Reuse radio API; no duplicate radio repository |
| TasteProfileProvider | ListeningStats.summary + ListeningHistory + YT Music history/liked/feed | Build profile from existing device/account data; no Room stats table |
| SongPlayStatsDao | ListeningStats (videoId keyed, month/day/hour aggregates) + ListeningHistory | Reuse; do not port LastWave title/artist-keyed table |
| DownloadedTrackDao | Existing Downloads data/store | Reuse existing download inventory |
| PlaylistRepository / Room | LocalPlaylistStore + remote YT playlist APIs | Extend the existing local store for generated playlists; keep remote YT APIs separate |
| RecommendationExclusionDao | No equivalent found | Optional local excluded-video-ID preference only if product needs "not interested"; no DAO/database |
| ViewingProfileState / friend selector | No equivalent | Omit by default; friend profile switching is optional |
| ArtworkRepository | Existing Song thumbnails, YtMusicRepository.trackLinks, RemoteArtwork/RemoteArtworkStore and ArtistFacts | Reuse existing artwork caches/enrichment; do not add LastWave artwork stack |
| MixLauncher | Existing track context-menu and MainActivity host callbacks | Use a typed pending seed event/state if one-tap Start Mix is approved |
| MusicPlayer.replaceUpcoming | MainActivity.playFrom + QueueCoordinator + PlaybackService commands | Reuse existing queue semantics; never call Media3 directly from Generate UI |
| PlaylistNamer | No equivalent found | Add a small deterministic naming helper only if needed; do not add another repository |
| GenerationStatus | No existing global generation status identified | Keep progress in the generator ViewModel; global service only if a real UI consumer needs it |

### Per-mode Mylo implementation source map
1. TOP: Last.fm user.gettoptracks via new method in existing LastFM client when username/API key are available; local fallback from ListeningStats.summary(period).songs. Keep Last.fm period values (overall/12month/6month/3month/1month/7day) only if remote source selected; local Replay supports week/month/year/all-time.
2. RECENT: Last.fm user.getrecenttracks when available; fallback to Mylo ListeningHistory (device log) and YT Music account history, with source labels.
3. SIMILAR_TRACKS: seed via YtMusicRepository.search() or existing track; preserve exact Song.videoId; candidates from YtMusicRepository.radio(videoId), optional Last.fm track.getsimilar; use existing TrackMatcher only when videoId is missing and reject ambiguous matches.
4. SIMILAR_ARTISTS: optional Last.fm artist.getsimilar; Mylo YtMusicRepository.artistPage()/search() for actual artist tracks and radio; use ArtistFacts only for metadata/genres, not as a substitute for discovery.
5. TAG: Last.fm tag.gettoptracks when available; Mylo moodAndGenres()/moodGenreShelves() for known mood/genre categories; free-form tag fallback must be explicit and not pretend every arbitrary tag maps to a YT Music browse ID.
6. MIX: taste profile from Mylo ListeningStats + ListeningHistory + YT Music liked/recent/home feed; expand with existing radio; do not use LastWave Room saved playlists as a fake listening profile.
7. RECOMMENDATIONS: adapt LastWave multi-source score/diversity engine using Mylo local Replay, YT Music feed/QuickPicks/radio, and Last.fm profile when available. Use videoId as primary identity. Mylo currently has no skip-count signal, so do not invent a skip penalty; omit or add measured skip telemetry in a separately approved change.
8. NEVER_HEARD: strict exclusion set from Mylo ListeningHistory (records a short skip after 2s), ListeningStats, YT Music history/liked/library, local playlists, remote playlists and Downloads as product semantics require. LastWave also excludes Last.fm top/recent/loved, YT feed, saved playlists and downloaded tracks; this is broader than literal "never listened", so decide whether saved/liked/library items should be excluded or only actually heard tracks.
9. LIBRARY: implement a true library-derived mix from Mylo library/liked songs/playlists; LastWave currently incorrectly routes LIBRARY to fetchTopTracks(period), so do not copy its current behavior.

### Additional LastWave generator risks confirmed
- GenerateRepository creates requestScope = CoroutineScope(SupervisorJob() + Dispatchers.IO) and its in-flight Last.fm Deferreds are children of that independent scope. Cancelling GenerateViewModel while awaiting a request does not cancel the underlying request; network work can continue until its own 20-second timeout. Adapt request coalescing without allowing abandoned generation work to continue unnecessarily.
- LastWave LocalTasteSuggestionEngine is used by GenerateRepository for guest recommendations, but relies on SongPlayStatsDao.skipCount and Room. Main-dev ListeningHistory records a track after 2 seconds but does not record skip count or listened duration per history row. Do not fabricate skip metrics; use available play counts/recent history and existing Replay stats.
- Mylo YtMusicRepository.call correctly rethrows CancellationException at the outer repository boundary, but quickPicks() contains nested runCatching around Home and New Releases calls which can swallow cancellation and proceed to fallback. Fix this if QuickPicks is reused by Generate.
- LastWave fetchNeverHeardTracks has broad runCatching around TasteProfile, exclusions, playlists, stats, downloads and Last.fm reads. Cancellation must be rethrown in each suspend source path.
- LastWave GenerateViewModel's generation job is not stored as a cancellable property and the UI has no Cancel button. The viewModelScope job is cancelled only when that ViewModel is cleared; static Scrobble tabs will keep the ViewModel alive when switching tabs, so Mylo needs an explicit cancel/close policy and state retention.
- LastWave generated results are converted to PlayableTrack and queued by a separate MusicPlayer. Mylo must map to Song once, then route through existing QueueCoordinator/playFrom; no parallel playback model.

### Mylo local playlist detail is present but not yet a generated-playlist manager
- MainViewModel.localPageState() already resolves local:playlist:<id> to LocalPlaylistStore.getPlaylist() and returns the stored Song list; MainActivity opens it through the existing detailStack and uses current release-page rows, artwork, sort and playback.
- Existing local playlist detail is effectively read-only for playlist ownership: MainViewModel.removeFromPlaylist() requires YouTube setVideoId + sign-in and calls YtMusicRepository.removeFromPlaylist(), so it cannot remove a track from LocalPlaylistStore. No local rename/delete/pin/cover/reorder controls are exposed in the existing detail route.
- Existing MainActivity local playlists use BrowseType.PLAYLIST and are shown in the Library on-device shelf. Reuse this detail route and add local-only actions keyed by local:playlist: ID, or provide a focused Scrobble detail destination that delegates to the same LocalPlaylistStore. Do not fork a second playlist database.
- Local imported Spotify playlists and local fallback playlists already share this store. Generated metadata must be optional/defaulted so imported and existing local playlists continue to render correctly.
- Generated playlist detail should support play/shuffle using existing playFrom, search/sort using existing list behavior, and local-only rename/remove/reorder/cover/pin/export/regenerate/delete operations. Do not send these local operations to YtMusicRepository remote playlist endpoints.

### Correction — Main-dev scrobble timer uses wall-clock elapsed time
- ScrobbleManager stores the Last.fm event timestamp with System.currentTimeMillis()/1000 (correct wall-clock epoch), but also measures pause elapsed time with System.currentTimeMillis() (not monotonic). A device clock adjustment during playback can skew the remaining threshold. Use SystemClock.elapsedRealtime() for elapsed timer measurement while retaining wall-clock epoch for Last.fm timestamp.
- If durationMs is provided at start but Song.durationText is null, the initial threshold uses durationMs; after pause/resume, resumeScrobbleTimer() recomputes duration from Song.durationText only and submits duration 0. Retain the resolved duration for the whole play session.
- If duration is null at initial start, songStarted is set true before startScrobbleTimer returns; subsequent onPlayerStateChanged(true) calls onSongResume(), which returns because remaining delay is 0. Unless a new media-item transition occurs, the timer never starts when duration later becomes known. This is now a confirmed state-machine defect.
- Mylo ListeningRecorder uses monotonic playback samples and already handles same-video resume vs new play; keep this distinction in local statistics. The Last.fm timer should use the same monotonic timing principle.

### Final implementation boundaries
- No new Room database, Hilt setup, Retrofit service, standalone backend server, Last.fm client, scrobble playback detector, local history store, local playlist store or queue engine.
- New code is limited to feature-specific viewmodels/domain orchestration, Last.fm read methods on existing client, generated playlist metadata/actions on existing LocalPlaylistStore, and the new Mylo-styled Scrobble screens/host.
- Do not add or alter Home's main content or unrelated main navigation; only add the explicitly approved FAB and conditional Scrobble navigation/content host.

### Remaining audit completion items
- [ ] Finish complete UI/error/loading/empty/cancellation matrix across all three Scrobble destinations.
- [ ] Decide "Never Heard" exclusion semantics (actual listened history only vs also saved/liked/library/downloads).
- [ ] Decide Statistics source presentation (Mylo Replay local first vs Last.fm profile first).
- [ ] Decide whether Scrobble close cancels generation; recommended default is explicit cancel on close.
- [ ] Then provide final plan for user approval. No feature code/build/merge has happened.

## 19. Audit completion — full interaction and failure-state matrix (2026-11-18 19:43:54 IST)

### Exact source state at audit completion
- Main-dev head: 11478fde56f1817cb777b296e60907359086f383.
- scrobble-ui head: 5bf065da305e6bb663960f9e09d30572dcdc0f32.
- last-wave head before this documentation commit: 5da5446aa005e86efcb8575c42fdab0c1f2e0d3d.
- Main-dev...scrobble-ui: diverged, 359 ahead / 1 behind, merge base d809e42cb3e7c9b81b603dec6f087898b2e93abe, 27 changed files. Only FAB + nav behavior is authorized.
- Main-dev...last-wave: ahead, 26 commits ahead / 0 behind, merge base equals Main-dev HEAD, 300 changed files. Only audited Scrobble feature slices are authorized.
- No Main-dev feature code, tests, build files, or navigation have been modified. Documentation commits only are on last-wave.

### Final user interaction map
1. Normal app state: Main-dev's four tabs Home / Explore / Library / Search remain unchanged; selected tab and all scroll state stay owned by the existing host.
2. FAB visibility: Home only; hidden for active detail, mood page, Library grid, Settings/subscreens, account scrobbling, sources, Listen Together, Wear OS, Equalizer, Replay, History, Spotify/Discord overlays, expanded player and keyboard. Hidden means no click action and no accessibility semantics. Scroll fold uses source behavior.
3. FAB open: showScrobble=true; selected Scrobble section resets to Statistics; normal bottom nav is replaced, not overlaid or shown as a popup.
4. Scrobble tabs: exactly Statistics / Generate / Playlists. Tab taps only update scrobbleSection; they must not call the normal onTabSelected handler or close Scrobble.
5. FAB close: second FAB tap sets showScrobble=false; restores the same normal selected tab (Home) and its previous scroll state. Next open resets to Statistics.
6. Back: close the topmost modal/detail first; once nested destinations are gone, Back closes Scrobble; the next Back follows existing Main-dev behavior. Top-bar back follows the same ordering.
7. Playlist detail: opens using Mylo's existing local browseId/detailStack so the Scrobble nav remains selected; Back returns to the Playlists tab. Local generated playlist operations must route to LocalPlaylistStore, never remote YT playlist endpoints.
8. Process/activity recreation: save open/selected section where appropriate; verify no stale Scrobble mode is restored over an incompatible deep link/overlay. Preserve Generate state and Playlist scroll across tab switches.
9. FAB dismissal while generation runs: recommended behavior is explicit cancellation before leaving Scrobble; no hidden generator may save a playlist after the user closes the feature. Add a visible Cancel action during generation.

### Destination-level state/failure matrix
| Destination | Loading / empty | Error / retry | Lifecycle / edge cases |
| --- | --- | --- | --- |
| Statistics local Replay | First-load spinner; truthful empty period state; week/month/year/all-time | Surface corrupted/unreadable stats as an error/retry instead of silently inventing data | Reuse existing ReplayState/ListeningStats; preserve current Library/Settings Replay routes; artist artwork updates; no duplicate recorder |
| Statistics Last.fm remote | Profile loading, no account, no recent tracks, no top charts, now-playing | API key missing, username invalid, rate limit, offline, 4xx/5xx; retry without clearing local Replay | Remote counts must be visibly labeled Last.fm; never sum remote scrobbles and device listening into one number |
| Generate mode selection | No mode selected, mode-specific options, default count, period and tag chips | Validate required seed/artist/tag before request | Preserve selected mode/inputs when switching Scrobble tabs; do not reset on every recomposition |
| Generate seed search | Searching indicator, result list, no results, selected seed with stable videoId | Empty query disabled; network failure retry; stale response must not replace newer query or another mode | Cancel prior seed search; keep selected seed if user switches tabs; do not resolve a different recording silently |
| Generate execution | Progress stage/message, candidate count, partial-result count | Timeout, no candidates, all candidates unavailable, API quota/rate limit, Last.fm disconnected, YT sign-out, offline, save failure | Explicit cancel; CancellationException propagates; no playlist save after cancel; one active generation only |
| Generate result | Saved playlist detail opens after confirmed persistence | If store write fails, remain on Generate and show error; do not navigate/toast success | Song/videoId dedupe; report actual count if fewer than requested; art enrichment may fail quietly |
| Playlists list | Loading, generated-playlist empty state, search no-results, sort modes | Read failure must not appear as empty list; provide retry | Show only generated Scrobble playlists by default; existing imported local playlists remain in Library; no automatic 20-item cap |
| Playlist detail | Track list, empty/deleted playlist state, custom order and sorted views | Read/write failures surfaced; failed rename/reorder/remove/cover/delete must not leave optimistic success | Reorder is serialized; stable Song.videoId identity; track removal is local-store-only; delete confirmation; playback via QueueCoordinator |
| Playlist export/share | CSV/M3U generation, Android share sheet | File creation/permission/share target failures | Export actual stored order or explicitly selected sort; never silently export a different order |
| Playlist regenerate | Progress, new mix result, previous playlist remains intact | Empty result, timeout, save failure | Regenerate creates a new playlist unless user explicitly selects replace; do not overwrite existing playlist on failure |
| Downloads / playback | Reuse existing Mylo download/queue actions | Existing download errors and queue refusal messages | No second download manager or Media3 queue; preserve current user queue and current song |

### Confirmed test coverage gap / required regression suite
- Main-dev has ListeningHistory tests, QueueCoordinator tests, QueueBuilder tests, QueueShuffle tests and PlaylistOwnershipTest. No app-side tests found for ScrobbleManager/LastFM, ListeningStats/Replay, LocalPlaylistStore, generated playlist persistence or Generate.
- scrobble-ui has ScrobbleFabComposeTest for visible click, hidden semantics and close label; no host tests for tab replacement, Glass pill, overlay suppression, BackHandler, restore or measured placement.
- LastWave has useful seed search, track identity, never-heard, playlist reorder/track rows and repository read-failure tests, but no ScrobbleRepository-specific tests, no complete GenerateViewModel flow tests, no RecommendationEngine scoring/diversity tests, and no detailed Statistics calculation tests.
- Required new tests before completion:
  1. FAB visibility/semantics/placement in Standard + Glass; Scrobble tabs replace normal nav; FAB open/close/back restore Home; nested modal/detail back priority; recreation.
  2. Scrobble threshold and minimum duration; unknown duration becoming known; pause/resume; clock change; same-track repeat; track transition; now-playing dedupe; API failure; offline retry idempotency; process death if durable queue is implemented.
  3. Stats week/month/year/all-time boundaries, month rollover, exact day/hour totals, empty period, file corruption, failed atomic write, backup export/import interruption.
  4. Generator mode source selection, Last.fm disconnected fallback, YT signed-out fallback, seed stale response, strict videoId identity, similar/title false-match rejection, cancellation propagation, timeout, partial availability, no-result, save failure, cancel-before-save and queue semantics.
  5. Playlist backward-compatible JSON migration, existing imported playlist preservation, duplicate/collision-safe IDs, serialized concurrent writes, write failure, rename/delete by browseId, add/remove/reorder, pin/cover, export, backup restore, Library shelf/detail refresh.
- Device validation after implementation: Glass + Standard mode; phone gesture/back; player/mini-player visible; keyboard open; Settings and overlays; rotation/recreation; offline and Last.fm session states; generated playlist survives app process restart; no duplicate scrobbles after reconnect.

### Final reuse / adapt / avoid ledger
- Reuse: Main-dev PlaybackService/ScrobbleManager/LastFM auth + ListenBrainz; ListeningRecorder/ListeningStats/ListeningHistory/ReplayScreen; YtMusicRepository search/home/history/library/radio/moods/quickPicks; LocalPlaylistStore; MainViewModel local detail; QueueCoordinator/playFrom; existing download and artwork stores.
- Adapt: LastWave Last.fm read/profile queries, Generate nine-mode domain, taste scoring/diversity, seed search, playlist manager lifecycle, retry/rate guard ideas, Statistics remote profile overview, FAB/nav host behavior.
- Avoid: LastWave duplicate playback scrobbler, Room/Retrofit/Hilt/DataStore stack, synthetic Stats fallbacks, title/artist-only identity, non-durable duplicate-prone queue, incorrect 30-second transition scrobble, LastWave shell/nav, Spotify account projections, duplicate download service, remote playlist creation for generated playlists.
- New storage: no new backend, no Room. Extend LocalPlaylistStore's current JSON model/backups with optional generated metadata and operations.
- Main navigation: preserve all four current tabs; only conditional Scrobble FAB + three-tab replacement is authorized.

### Audit status
- [x] Repository branches, current SHAs and compare histories verified.
- [x] Mandatory branch instruction files read; Main-dev has no CLAUDE.md/AGENTS.md at its current head, and its available CONTRIBUTING.md/ADDITIONAL.md/README.md were read.
- [x] LastWave CLAUDE.md/AGENTS.md/DESIGN.md and scrobble-ui CLAUDE.md read.
- [x] Existing Main-dev functionality and all three target Scrobble areas audited at source/interaction/data level.
- [x] Reuse-before-build audit completed; duplicate backend/database/client/queue paths ruled out.
- [x] Concrete source defects and missing test coverage recorded.
- [x] Context file updated after each audit pass with timestamp and source branch SHAs.
- [ ] User decision required for Statistics landing source (recommended: Mylo Replay local-first + separate Last.fm remote section).
- [ ] After user confirms the remaining product direction, write final implementation plan and wait for approval before coding.

No feature code, build, test run, PR, merge or Main-dev commit was made during this planning audit.


## 20. Approved execution plan and branch policy (2026-11-18)

### User approval
- User approved the implementation direction:
  - Statistics landing: Mylo Replay/local listening first; Last.fm remote profile/statistics in a separate, clearly labelled section. Never combine unlike local and remote totals.
  - Never Heard: exclude tracks actually listened to by default. Saved/liked/library/download exclusions must be explicit and must not be silently added.
  - Closing/dismissing Scrobble cancels active generation; a cancelled run must never persist a playlist or mutate the playback queue.
- User explicitly rejected creating a separate `scrobble-dev` branch.
- `Main-dev` is the designated beta-testing/integration branch. `main` is the stable branch and is protected from this work. No feature commits, direct changes, or merges to `main`.

### Current repository state recheck
- Repository: `somilkhan/Mylo`; default branch: `main`.
- `Main-dev` exists and its current HEAD is `11478fde56f1817cb777b296e60907359086f383`.
- GitHub compare `main...Main-dev` reports `identical` (0 ahead / 0 behind) at this checkpoint. Although the user designates Main-dev as the beta branch, it currently points to the same commit as stable main. All subsequent changes must target `Main-dev` explicitly; do not infer that its current contents are already divergent.
- Branch inventory includes `Main-dev`, `main`, `last-wave`, and `scrobble-ui`. No `scrobble-dev` branch will be created.
- `SCROBBLE_CONTEXT.md` did not exist on `Main-dev` at this recheck; this document is being copied forward as the living implementation record before feature work.
- Root `CLAUDE.md` and `AGENTS.md` were queried on `Main-dev` and returned 404 (not present at this branch head). `CONTRIBUTING.md` was re-read (blob SHA `3f45cdfb014fcbf54ea57bfd18dadf7b20556b4c`); it requires separation of UI/repository/domain/playback responsibilities, focused changes, regression tests, exact-commit CI inspection, and no device-verification claims without real device evidence.
- GitHub connector exposes repository branch refs but not a local checked-out worktree state; no claim is made about a local active checkout. All writes in this session are explicitly targeted at `Main-dev`.

### Execution policy
1. Work directly on `Main-dev`; never create `scrobble-dev` and never write to `main`.
2. Before each implementation slice, recheck the current `Main-dev` HEAD and relevant source blob SHAs. If the branch moved, rebase the plan against its new state; never overwrite concurrent work.
3. Use `scrobble-ui` only for the approved FAB and navigation behavior. Use `last-wave` only as a reference for audited feature logic. Never merge either branch wholesale.
4. Implement the smallest complete vertical slice, with tests, then review its diff and run focused verification before committing.
5. Keep commits grouped by coherent feature slices rather than tiny cosmetic commits. Do not combine unrelated changes.
6. **After every implementation commit**, immediately update this file on `Main-dev` with the commit SHA, changed paths, behavior delivered, tests/build outcomes, review findings, remaining work, and next action. The context update itself is a separate documentation commit that references the implementation commit. If a slice is not committed, mark it unfinished and do not claim it complete.
7. At the start of every new chat/continuation, fetch this file from the latest `Main-dev` HEAD, verify branch/PR/CI state, and continue from the recorded Next action. This file is the durable handoff between chats.
8. Open a PR only when the integrated feature is ready for beta review; base must be `Main-dev`. Never merge without the user's explicit approval. Do not merge to `main`.

### Approved implementation sequence
- [ ] Phase 0: establish this context file on `Main-dev`; re-verify branch head and instructions; inspect current PR/CI state and exact integration files.
- [ ] Phase 1: extract only Scrobble FAB and conditional three-tab navigation from `scrobble-ui`; preserve Home / Explore / Library / Search and existing host state.
- [ ] Phase 2: implement Statistics using Mylo Replay/local stats as primary and separately labelled Last.fm remote profile data.
- [ ] Phase 3: port/adapt all nine Generate modes using Mylo repositories, stable `Song.videoId` identity and existing QueueCoordinator; fix cancellation and stale-request defects; implement actual-listening-only Never Heard default.
- [ ] Phase 4: implement generated playlist management through backward-compatible `LocalPlaylistStore` extensions; preserve imported/local playlists and use existing playlist detail/playback paths.
- [ ] Phase 5: cross-feature regression tests, full relevant builds, diff review, accessibility/theme/Glass + Standard validation, exact-head CI inspection.
- [ ] Phase 6: beta PR into `Main-dev`; wait for user approval before merge.

### Definition of done
- All three destinations and FAB/nav interactions work end-to-end; no placeholder/mock feature paths.
- Existing Home, Explore, Library, Search, playback, queue, listening history, scrobbling, imported playlists and local playlist persistence regressions are covered.
- Generator cancellation cannot save after cancellation or leave background requests running unexpectedly.
- Existing playlist JSON remains backward compatible and survives process restart.
- Relevant unit/Compose tests and required Android builds pass; exact PR head CI is checked.
- Physical-device checks are explicitly listed and not claimed complete unless performed.
- Context log accurately records all commits, test results, unresolved issues and merge state.

### Current execution status
- [x] User approved the product defaults and confirmed direct work on `Main-dev`.
- [x] Confirmed no `scrobble-dev` branch should be created.
- [x] Rechecked repository and `Main-dev` head; confirmed `main` and `Main-dev` currently identical.
- [x] Re-read available Main-dev contribution guidance; root CLAUDE.md/AGENTS.md are absent at this head.
- [ ] Copy this complete context log onto `Main-dev` before any app behavior change.
- [ ] Begin Phase 0 source/PR/CI recheck, then Phase 1 implementation.
- No app behavior code has been changed by this execution pass yet.


## 21. Phase 0 execution recheck — existing PR and integration baseline (2026-11-18)

### Existing PR discovery and correction
- Open draft PR #8: `Scrobble UI, FAB and navigation redesign`, base `main`, head `scrobble-ui`, head SHA `5bf065da305e6bb663960f9e09d30572dcdc0f32`, 359 commits, 27 files changed. It is **not** based on `Main-dev`; do not assume its contents are present in the beta branch.
- PR #8 changed files include the approved FAB/navigation integration plus a large `ScrobbleScreen.kt`, playlist store, recommendation engines, stats logic, tests and a `CLAUDE.md`. The user-approved boundary still applies: use its FAB/host behavior as reference, but audit/adapt destination business logic against `last-wave` and Main-dev's data contracts. No wholesale merge/cherry-pick.
- PR #8 exact head workflow lookup returned Android CI run #1053 / run ID 43747641686, completed successfully. This is evidence for that exact `scrobble-ui` head only; it does not validate Main-dev or any future integration commit.
- Open draft PR #4 is an older, separate Scrobble/history integration PR based on `main`, head `feat/scrobble-nav-fab` SHA `fa63ed9f35b6f0ef118caf2a8352da7ca5680203`; it is not the selected implementation branch. Its historical CI evidence is not transferable to this work.
- PR #8's `CLAUDE.md` (blob SHA `3b0ef90d07b921bd2e8084d7d38598d7e59f8d5e`) was read. It requires focused scope, preserve Home/main navigation, inspect actual source/PR/tests, lifecycle-safe Compose, track identity, real functionality, regression tests, adversarial review, and exact-head CI. It also requires installed design skills/references before significant UI work; resolve that requirement before a substantial visual implementation.

### Main-dev source recheck
- Main-dev HEAD before this context update: `11478fde56f1817cb777b296e60907359086f383`; context-only commit on Main-dev: `b91dbbf813348cf1af3e021f5cde313d48f37c82`.
- Main-dev `MainActivity.kt` blob SHA: `f48697f73f299161856eb392d5600f1767e3c093`.
- Main-dev `ScrobbleManager.kt` blob SHA: `153b488ed0eea8893ffb0e7983de9fd26a569847`. Reconfirmed timer uses wall-clock for elapsed pause duration, resolves duration again from `Song.durationText` on resume, and marks song started even when duration is initially unknown. These previously documented defects remain candidates for a focused fix with tests.
- Main-dev `ListeningStats.kt` blob SHA: `93cb88d2f95aceca12066a433e6c446f41ef3aad`; local Replay is monthly aggregate storage, not a raw per-play event log. Never invent historical plays/timestamps.
- Main-dev `FloatingBottomBar.kt` blob SHA `445f6f8cc9e480e19a0ce7846f68f94fcc6f17dc`; `GlassNavBar.kt` blob SHA `88e50b6a8d9d14e5ad1f6dc00d84f2d710bd5026`. These are the existing nav components; retain their visual and behavior contracts.
- LastWave/PR8 FAB source `ScrobbleFab.kt` blob SHA `c70fd7c0840da6a13bac59497f24053a7c17084d`; it provides a 62dp surface, Home-only visibility helper, hidden semantics/non-clickability, Reduce Motion snapping, spring scale/fade/lift and GraphicEq/Close glyph transition.
- Main-dev root `CLAUDE.md` and `AGENTS.md` are absent (404); Main-dev `CONTRIBUTING.md` SHA remains `3f45cdfb014fcbf54ea57bfd18dadf7b20556b4c`.
- Repository tree/API inspection is available, but this execution environment has no checked-out Mylo worktree and outbound GitHub clone/DNS is unavailable. GitHub connector can read/write individual files, but cannot run Gradle locally. No code change should be made through blind whole-file replacement; establish a safe patch/build-capable execution path before modifying the large MainActivity integration.

### Next actions / status correction
- [x] Context log copied onto Main-dev before feature work (commit `b91dbbf813348cf1af3e021f5cde313d48f37c82`).
- [x] Confirmed PR #8 and PR #4 are existing drafts on different bases; PR #8 exact-head CI is green.
- [x] Re-read PR #8's CLAUDE.md and Main-dev CONTRIBUTING.md; no root Main-dev CLAUDE.md/AGENTS.md.
- [x] Rechecked Main-dev navigation, scrobble timer and Replay storage source blobs.
- [ ] Obtain a patch-capable repository worktree/build environment (or user-provided current checkout) before implementation; do not risk replacing MainActivity wholesale via Contents API.
- [ ] Resolve required design-skill availability before substantial UI design work.
- [ ] Next code slice after environment is available: FAB + conditional nav host only, with focused Compose/host tests; then Statistics, Generate, Playlists.
- No feature implementation commit exists on Main-dev. The only Main-dev change so far is the living context document.
