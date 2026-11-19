# Mylo Scrobble Integration — Context & Audit Log

> Living engineering record. Update this file after every audit pass and before any implementation decision. This is not the public README.

## 0. CURRENT AGENT RULES — READ FIRST; LIBRARY SOURCES ARE CANONICAL

### Definitive source documents (read the actual files before every implementation task)
The user's ChatGPT Sources Library is the authoritative product/UX instruction source. The repository context is a synchronized engineering record and handover, not a replacement for those documents.

1. `/Mylo/AGENT-BRIEF.md` — scope, hard constraints, generation/statistics behavior, durable scrobble queue, accessibility, mandatory verification and delivery format.
2. `/Mylo/mylo-ux-spec.md` — approved Scrobble Hub screen behavior, labels, modes, states, playlist capabilities, decisions and out-of-scope items.
3. `/Mylo/CHANGE-THEME-TOGGLE.md` — the one approved Settings change and exact gradient toggle behavior/storage.
4. `/Agent skills/Universal Agent Engineering/references/ui-ux-review.md` — whole-journey UI/UX, interaction, accessibility and visual-quality review checklist.

The supplied `mylo-replay-prototype-v3.html` is the visual and interaction reference named by AGENT-BRIEF. Open and inspect it in a browser before UI edits. Its data is explicitly sample/demo data: never copy sample tracks, counts, simulated network behavior or fake success into the app. The prototype is not the source of truth for business rules, persistence, real data, or Settings toggle implementation.

### Source precedence and conflict handling
- Library Markdown above is definitive for approved product intent. This file records implementation history, verified repository state, discrepancies and next steps.
- Use AGENT-BRIEF for scope/constraints/acceptance; mylo-ux-spec for screen behavior; CHANGE-THEME-TOGGLE for the sole approved Settings addition; ui-ux-review for the quality audit.
- Where the UX spec and prototype differ, the spec wins. Where spec and repository code differ, inspect the actual code/callers/tests first and document the discrepancy; do not silently rewrite either. The spec says the product owner corrects the spec when code inspection establishes that the approved behavior needs revision.
- If sources conflict or a required decision is absent, record the exact conflict and ask only if it blocks safe implementation. Do not invent a resolution.
- Sections 1–66 below are the chronological audit/implementation record. They are useful history, not current instructions where they conflict with this section or the current Library documents. In particular, older notes that describe LastWave as the implementation source are historical; the current AGENT-BRIEF directs agents to the Mylo repository's existing Compose/data layers and the supplied v3 HTML as the visual/interaction reference. Do not wholesale port LastWave or another branch.

### Scope — implement only what the Library brief authorizes
- Implement/maintain the three Mylo Scrobble Hub screens: Statistics, Generate and Playlists, using existing Compose/Material 3 components and existing Mylo data layers.
- Implement the durable scrobble queue described in AGENT-BRIEF section 6.
- Do not redesign or casually change the FAB, navigation swap, mini-player, full player, Home, Explore, Library, Search, Settings layout, app-wide theme, approved gradient palette, icons, dialog look, or unrelated screens.
- Narrow exception: add exactly one Settings > Appearance switch labelled **Scrobble gradient theme**, following the existing Settings row pattern. Do not restructure Settings or add a toggle anywhere else.
- Toggle ON (default true): the three hub screens use the approved v3 prototype gradient theme. Toggle OFF: only those three screens use the existing Mylo theme. No changes to FAB/nav/player or other app screens. Preserve the approved gradient palette; do not introduce dynamic artwork/wallpaper colors.
- Do not add a mini-player close button. FAB Close remains the hub exit.
- Preserve the existing four normal tabs and all current Mylo shell behavior unless the product owner explicitly changes the spec.

### Hard implementation constraints
- Use existing storage. No Room dependency, new database or new competing repository/store without written product-owner approval.
- Playlist persistence remains `LocalPlaylistStore` (SharedPreferences JSON); local stats remain the existing `ListeningStats` month JSON.
- Real data only. No sample numbers, placeholder tracks, invented counts or fake history in app UI.
- Statistics: normalized artist + title identity; one row per key; when Last.fm is connected use max(local, Last.fm), never sum; when disconnected use local only. Apply period to both sources before merge and apply the same merge semantics to time listened and concentration.
- Last.fm cache is 5 minutes; fetch on hub open only when stale; pull-to-refresh forces fetch; no polling. Keep stale content visible during fetch and after refresh failure. Only show the refresh-failure notice while Last.fm is connected.
- Generate all nine existing `PlaylistGenerationMode` modes. Last.fm is optional in every mode. Library is disabled only when YouTube Music is signed out, with the exact reason in the UX spec. Count is 5–35 in steps of 5, default 25. Respect mode-specific inputs, opt-in Never Heard exclusions, success/failure behavior and cancellation.
- Playlist list search is title-only per the current UX spec; track-level search is explicitly out of scope there. Required row actions and detail actions are listed in the spec. Drag reorder and track-level list search are out of scope.
- Scrobble queue: retryable failures must persist across process death; preserve order; cap 200 and drop/log oldest on overflow; flush on app start and after successful scrobbles when network is available and Last.fm is connected; permanent invalid-session/track-rejected failures are dropped/logged. No Room.
- Queue storage decision: product owner has not supplied a separate choice in the available handoff. AGENT-BRIEF explicitly says to default to a single JSON file written through temp-file-and-rename if unanswered. Use that fallback and report it; do not block or invent another database.
- Coroutine safety: always rethrow `CancellationException`; do not swallow cancellation in `runCatching`; avoid nested `runCatching` that hides failures; do not use lossy `tryEmit` for must-deliver events; do not start network work from composition; closing the hub cancels generation.

### Repository and branch safety
- Repository: `somilkhan/Mylo`.
- Integration/working branch currently authorized by the existing project workflow: `Main-dev` (case-sensitive). Never edit or push `main`.
- AGENT-BRIEF contains internally conflicting wording: “Work only on branch Main-dev” and “Create your own feature branch from it.” The existing context workflow and recent user direction have been Main-dev-only. Until the product owner explicitly resolves this contradiction, do not create a new branch; keep this documentation update and subsequent work on `Main-dev`. Record this ambiguity rather than silently changing branch policy.
- `last-wave` is read-only if a comparison is explicitly useful. No wholesale merge, cherry-pick or file replacement.
- Before every task, refresh live refs, inspect current working tree/branch, preserve uncommitted/user changes, read the full current context and the four Library docs, inspect callers/tests, and search for existing/renamed equivalents before adding code.
- Check line endings before editing; preserve CRLF/LF. Avoid unrelated formatting/import churn. Never commit secrets, keys or `local.properties`.

### Required engineering and UX workflow
1. Read all four Library Markdown files and open the v3 HTML prototype in a browser.
2. Read this full context file and refresh live branch/commit refs. Inspect actual code, call sites, data owners, existing storage, settings preference store and tests before planning edits.
3. Trace complete user journeys, not just widgets: navigation → action → ViewModel/state → repository/API/storage/playback → result, including loading/empty/error/offline/permission/cancel/retry/relaunch paths.
4. Search for duplicate or renamed equivalents before adding components, preferences, queue managers, stores, models or navigation owners.
5. Make the smallest scoped change. Preserve Mylo's existing UI language and the approved prototype's hierarchy/behavior; do not invent a second design system or redesign excluded surfaces.
6. Review with the shared ui-ux-review playbook: hierarchy, navigation/back/deep links, progressive disclosure, interaction states, recovery, semantic accessibility, contrast, scaling, touch targets, RTL/localization, motion/reduced motion, clipping and real device screenshots where possible.
7. Verify exact acceptance items in AGENT-BRIEF. Run `./gradlew assembleDebug` (or exact CI equivalent), unit tests and the requested device/emulator scenarios. Add focused tests for the specified merge/cache/count/title/queue behaviors.
8. Update this file after each meaningful pass with exact refs, code/doc commits, changed files, confirmed behavior, test/build output, device evidence, open gaps and next action. Continue through safe implementation when asked; do not stop after an audit or one small fix.
9. Never claim build, tests, APK, screenshots or device verification without exact-commit evidence. Clearly list anything not verified.

### Mandatory delivery
Report branch and commit hashes; one-line file/change rationale; exact build/test/CI results; device verification and anything not verified; queue storage choice; out-of-scope findings; and deliberately omitted work with reasons. Keep the user-facing summary concise unless a detailed handover is requested.

---

## 1. Agreed scope and branch responsibilities

- **Target:** `Main-dev` (the repository branch is case-sensitive; the earlier lowercase `main-dev` lookup returned 404). Current ref snapshot at this documentation update: `c4230c3226c84f07774a6efd676a93c3fb8e8bc8` (refresh before work).
- **FAB/navigation source:** `scrobble-ui`, audited head `5bf065da305e6bb663960f9e09d30572dcdc0f32`. Reuse only the Scrobble FAB, its visibility/placement/open-close behavior, and the static Statistics / Generate / Playlists navigation. Do not port its Scrobble destination screens or feature logic as the implementation source.
- **Feature source:** `last-wave`, current ref snapshot `d0dd73755bfe640c515502751f440a32e57506d5` (refresh before work). Use as the source for Scrobble-related architecture, Statistics, Generate, Playlists and their supporting domain/data flows, then adapt to Mylo's existing architecture and UI.
- **Final integration target:** `Main-dev`. No work on `main`; no merge to `Main-dev` until audit, implementation, review and user approval.
- Preserve Mylo's existing Home/main navigation except the specifically agreed FAB entry/exit integration. In Scrobble mode, replace the full existing normal navigation (Home / Explore / Library / Search) with persistent Statistics / Generate / Playlists tabs; restore all four normal tabs on exit. Tapping the FAB again or dismissing Scrobble restores the normal navigation.
- The original planning-only restriction is superseded by the user's current explicit instruction to continue implementation from `last-wave` on `Main-dev`, adapting to Mylo UI. Build/test only when appropriate and report evidence; never touch `main`.

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

## 22. GitHub-only execution path and beta CI enablement (2026-11-18)

### Fresh branch and instruction recheck
- Latest Main-dev HEAD after CI configuration change: 988a833368274377b48cf681043dae6d660ea4b7.
- Commit parent: 5cd59b308b96e1fef524d74a43fbfa91a8b7acc4. The branch now has three commits ahead of main (two context commits plus this CI commit); main was not modified.
- Repository metadata confirms default branch main; Main-dev exists and is the explicit beta target.
- Re-fetched the complete SCROBBLE_CONTEXT.md from Main-dev (blob d2f49f419874eed296bdcf1848561013a9b4a615).
- Rechecked root CLAUDE.md and AGENTS.md: both absent (404). .claude/ contains only launch.json; no additional project instructions were found there. CONTRIBUTING.md remains the available Main-dev engineering guide.
- Correct source path: app/src/main/java/com/music/bitchord/MainActivity.kt (blob f48697f73f299161856eb392d5600f1767e3c093, 304,613 bytes). The earlier attempted path without app/src was incorrect; no file was changed by that failed lookup.
- MainActivity baseline still has four normal tabs: Home, Explore, Library, Search. Existing host uses GlassNavBar and FloatingBottomBar; no Scrobble FAB or Scrobble host state exists on Main-dev.
- PR #8 source has ScrobbleFab.kt (blob c70fd7c0840da6a13bac59497f24053a7c17084d) and a MainActivity patch that adds FAB/tab host integration. The source PR's current exact-head Android CI run #1053 / ID 43747641686 is completed-success. This does not validate Main-dev.
- Re-read Main-dev ScrobbleManager.kt (blob 153b488ed0eea8893ffb0e7983de9fd26a569847): elapsed pause accounting still uses wall-clock time; resume derives duration only from Song.durationText; unknown initial duration still marks the song started. No changes made to this file in this pass.

### CI enablement change
- Main-dev .github/workflows/android-ci.yml was updated from push.branches: [main] to push.branches: [main, Main-dev]; PR trigger remains pull_request.branches: [main].
- Implementation commit: 988a833368274377b48cf681043dae6d660ea4b7, message ci(android): validate Main-dev beta pushes.
- The first Actions API query for branch=Main-dev returned zero workflow runs immediately after the push. Exact commit CI is therefore not yet confirmed; recheck before claiming it ran or passed.
- This allows future implementation commits on Main-dev to invoke Android CI without opening a PR against stable main. Do not change workflow permissions or release-signing behavior.

### Execution method and guardrails
- GitHub's Git Trees/Blobs/Commits APIs are available through the connected repository integration. They can create a new tree based on the exact Main-dev tree, commit it with the exact current parent, and advance Main-dev using a non-force ref update with expected_sha. This is the safe remote patch path; no whole-file Contents API replacement without fetching and validating the exact current blob first.
- MainActivity can be fetched as a full Git blob and inspected/manipulated as text in the GitHub execution runtime; its exact source is available despite the container clone/DNS failure. GitHub Actions provides remote Gradle verification after a commit. Do not call local build/test as performed.
- Before every tree commit: recheck branch HEAD; build only against that parent; use force=false and expected_sha; if lease fails, re-fetch and reconcile.
- No feature implementation code has been changed yet. The only new commit in this pass changes Android CI branch filtering.

### Next
1. Recheck CI run/status for 988a833368274377b48cf681043dae6d660ea4b7.
2. Build the Phase 1 patch by applying only approved FAB + nav host behavior, preserving the four normal tabs, existing overlays, player/keyboard suppression, Back priority, and both Glass/Standard modes.
3. Add host-level tests; do not treat PR #8's isolated FAB test as sufficient.
4. Then implement Statistics, Generate cancellation/semantics, and playlist persistence as separate coherent slices. Keep PR #8 destination business logic under audit against LastWave and Mylo's real stores; do not bulk-copy it.


## 23. Phase 1 implementation — reusable FAB component (2026-11-18)

- Implementation commit: `7198ad29b5e43fd877084cab59f120d745926851` (`feat(scrobble): add accessible floating entry component`).
- Added `app/src/main/java/com/music/bitchord/ui/components/ScrobbleFab.kt`, adapted from the audited PR #8 component blob `c70fd7c0840da6a13bac59497f24053a7c17084d`.
- Component provides a 62dp accessible touch target, GraphicEq/Close state glyph, reduced-motion snap behavior, spring scale/fade/lift, stable layout slot, and hidden-state removal from click/accessibility semantics. Host supplies its surface treatment.
- The source component was copied as a self-contained Compose unit; it is not yet wired into MainActivity. No behavior change is visible in the app until host integration lands.
- Verification: source reviewed against current Main-dev package/component conventions; no Gradle build or tests have run yet. GitHub Actions is expected to trigger for this Main-dev push; exact run must be checked.
- Review note: do not claim the FAB feature complete from component addition alone. Host integration still must calculate overlay/player/IME visibility, measure the player+nav dock, preserve Glass/Standard layout, swap only the three Scrobble nav tabs, and give Back priority without breaking existing handlers.
- Next: integrate the FAB and three-tab host into MainActivity using exact current blob, then add host-level tests and inspect the complete diff.


## 24. FAB visibility regression test (2026-11-18)

- Test commit: `5ad1a4519bfb3a79ba9001466d8f664993cebdb8`.
- Added `app/src/test/java/com/music/bitchord/ui/components/ScrobbleFabTest.kt`.
- Covers Home-only visibility and suppression for another tab, an overlay, the expanded player, and the IME/keyboard.
- This is a focused JVM unit test for the pure visibility helper. It does **not** validate Compose layout, host integration, measured docking, navigation replacement, Back priority, or actual accessibility semantics.
- Android CI must be checked against the exact test commit. No local Gradle execution is claimed.
- Current next action: integrate the FAB and Scrobble host into MainActivity only after reconciling the PR #8 host patch with current overlay state and the complete ScrobbleScreen dependency graph. The PR patch requires new account-synced-history state and the full ScrobbleScreen implementation; do not paste the host diff alone because it will not compile.


## 25. Prototype review and Glass navigation prerequisite (2026-11-18)

- Reviewed the uploaded `Mylo Final Prototype.md` directly. Its explicit interaction requirements include: Glass/Standard switching, reduced-motion behavior, FAB above the player/navigation stack in both modes, Home-only FAB, Scrobble's three tabs inside the pill, Scrobble-specific scroll folding in Glass mode, normal tabs restored on close, and tapping a normal tab exits Scrobble. Its screen content is illustrative prototype copy, not production data.
- Root `CLAUDE.md` and `AGENTS.md` remain absent on current `Main-dev`; do not claim they were read. Existing project instructions and this context log remain the available engineering guidance.
- Current branch before this change: `Main-dev`, head `749270c32c49fe05b4bc425b471c410c2b304b04`; stable `main` remains `11478fde56f1817cb777b296e60907359086f383`.
- Changed `app/src/main/java/com/music/bitchord/ui/components/GlassNavBar.kt` (blob `ba27410d2563008d741a0772ebf6a7f06e8426c4`) to accept optional `standaloneIndexOverride`. Default behavior remains the last tab as standalone; Scrobble host can pass `-1` so all three tabs remain inside the glass pill, matching the prototype.
- Commit: `b189d169a755d2092faadd9b4621673c62c9a7be`.
- This is a navigation-component prerequisite only; it is not yet wired or visually verified. No Gradle build/test was run in this GitHub-only step.
- Next: inspect the actual Scrobble destination dependencies from the approved feature source, port/adapt only required pieces, then integrate MainActivity state and both nav modes together. Do not copy the old `ScrobbleScreen.kt` wholesale: the feature source of truth is LastWave, while Mylo's current recorder, settings, local playlist store and playback/queue APIs remain authoritative.


## 26. Implementation pass — local playlist foundation (2026-11-18)

### Verified repository state
- Active implementation branch: `Main-dev`, current code commit `c5b338ee57ea7d0625d801d79adbeaf5780d20cf`.
- Stable `main` remains `11478fde56f1817cb777b296e60907359086f383`; no changes were made there.
- The prior Glass-tab code commit `b189d169a755d2092faadd9b4621673c62c9a7be` has a successful Android CI run (#1084 / `44334566782`). The current playlist commit's Android CI run is pending (#1086 / `44347313833`); do not treat it as passed until it completes.
- Root `CLAUDE.md` and `AGENTS.md` were rechecked on this branch and both return 404. The LastWave branch still contains the previously read engineering instructions, but those are not a substitute for missing Main-dev instructions.

### Code changed
- Updated `app/src/main/java/com/music/bitchord/data/spotify/LocalPlaylistStore.kt` (current blob `f18f9ebcfce4979f63f6f628db1e3ec0dab9841d`).
- Added backward-compatible optional playlist metadata: `createdAtEpochMillis`, `generationMode`, and `isPinned`. Missing fields in existing JSON decode to legacy-safe defaults.
- New playlist IDs use UUIDs rather than millisecond timestamps, avoiding same-millisecond ID collisions.
- Added `updatePlaylistSongs`, `setPinned`, and `movePlaylist` operations; all preserve playlist identity and other metadata.
- Serialized load/mutation/persist entry points with `@Synchronized` to prevent in-process read-modify-write races. Existing SharedPreferences JSON key and `apply()` persistence remain unchanged; this is not a cross-process transaction or durable-write acknowledgement.
- A first edit contained a stale identifier in the new save expression; it was corrected immediately in the following commit before verification. Current source was fetched again and reviewed after the fix.

### Verification / limitations
- Current exact-head Android CI run is pending; no local Gradle build or test result is claimed.
- No new unit test was added in this pass. Existing JUnit and org.json test dependencies are available; add focused persistence/metadata tests before calling this store work fully verified.
- No UI actions are wired to the new playlist methods yet. This is a persistence foundation, not a completed Playlists screen.

### Next
- Continue the Mylo-native generator audit: identify the actual InnerTube search/radio APIs, listening-history identity, queue mutation entry points, and playlist-save caller.
- Port generator orchestration with structured cancellation. Dismissing Scrobble must cancel generation before any save or queue mutation; never catch and convert `CancellationException` into fallback output.
- Then integrate Statistics by reusing Mylo's actual local Replay data and keeping Last.fm remote profile metrics separate.

## 27. Playlist persistence verification and Mylo-native generation engine (2026-11-18)

### Verified branch / CI
- Work is on Main-dev, parent HEAD for this implementation batch: 274d861136dc2a0565ac25e9ea765cba070f74a2.
- Stable main remains 11478fde56f1817cb777b296e60907359086f383; it was not modified.
- Android CI run #1089 / 44348090931 passed on exact HEAD 274d861136dc2a0565ac25e9ea765cba070f74a2: Android unit tests, desktop compile/tests, and web tests/typecheck all succeeded.
- Root CLAUDE.md and AGENTS.md were checked again and remain absent (404). Do not claim they were read.

### Persistence codec and tests
- Extracted JSON encode/decode into app/src/main/java/com/music/bitchord/data/spotify/LocalPlaylistCodec.kt, with no Android framework dependency.
- Added LocalPlaylistCodecTest: legacy JSON defaults, full generated metadata/song round-trip, and empty collection handling.
- LocalPlaylistStore now delegates to the codec; existing JSON keys and song fields remain compatible.
- Critical startup audit finding: LocalPlaylistStore.init(context) had no call site in BitChordApplication or MainActivity. That left its SharedPreferences field uninitialized, so persist() returned early and local playlists were only held in memory. Added initialization in BitChordApplication.onCreate before UI consumers run. This restores actual local playlist load/save persistence.

### Mylo-native Generate domain
- Added PlaylistGenerationRepository with all nine modes, using Mylo's existing sources rather than LastWave's Room/Last.fm-specific repositories:
  - Top: local ListeningStats.summary(period) / Replay rankings.
  - Recent: device-local ListeningHistory.
  - Similar Tracks: YouTube Music radio from the selected seed video ID.
  - Similar Artists: exact artist-page lookup, then that artist's tracks.
  - Tag: YouTube Music mood/genre shelves, falling back to Songs search.
  - Mix: seed radio + Quick Picks + Home shelves.
  - Recommendations: YouTube Music Quick Picks.
  - Never Heard: Quick Picks/Home candidates filtered by local event history (including two-second skips), local Replay totals, and signed-in YouTube Music history.
  - Library: YouTube Music liked + added library songs.
- Never Heard does not silently exclude saved playlists, liked tracks, or library tracks. Those are explicit request flags. Existing local playlist tracks are only soft-deprioritized for freshness unless an explicit exclusion is selected.
- Candidate rules deduplicate by video ID, remove blank IDs/video uploads, use normalized title+artist identity for heard matching, and retain saved tracks at the end if the fresh pool is thin.
- Added ScrobbleGenerateViewModel with a 60-second timeout, progress/error state, unique playlist naming, and a dismiss cancellation method. ViewModel teardown cancels too.
- Added generateAndCommit: candidate work is suspending, then ensureActive() is checked immediately before a synchronous save. Cancellation during generation or immediately after the provider returns cannot reach the save callback. Generation itself does not mutate the playback queue; queue changes remain explicit playback actions.
- Added unit tests for candidate identity/deduplication/freshness, explicit exclusion semantics, unique titles, and cancellation-before-commit.

### Remaining / verification
- This implementation batch is not yet on the branch; it is prepared against the exact parent above and will be committed atomically after the branch-head lease check.
- The new engine/ViewModel are not yet wired into the Scrobble Generate screen. The Scrobble FAB and three-tab host are still not integrated into MainActivity.
- Latest successful CI (#1089) predates this batch; a new exact-head Android CI run is required after commit.
- No physical-device validation is claimed.

### Next
1. Wire the Generate ViewModel into the Scrobble Generate tab and ensure every dismissal path calls cancelGeneration().
2. Build the Playlists tab on the now-initialized local store, including generated playlist open/play actions without changing queue semantics until the user taps Play.
3. Integrate Statistics with local Replay as the primary source and keep Last.fm remote profile data explicitly separate.

## 28. Generation review corrections and implementation commit (2026-11-18)

- The engine/ViewModel/persistence batch was committed to Main-dev at 983cea23bf5f61167f960b2896d76946fe64722f; follow-up review commit 037777645d0c49b064c0e3bbd64a548f37944352 tightened cancellation and Never Heard behavior.
- Never Heard no longer applies even a soft saved-playlist freshness bias by default. Saved-playlist, liked-song, and library-song exclusions are applied only when their explicit request flags are selected.
- The title helper now uses Locale.ROOT for deterministic case-insensitive duplicate detection.
- Corrected the active-generation commit test so the helper's return value is asserted independently from the side effect.
- The exact-head CI run for 983cea23bf5f61167f960b2896d76946fe64722f was superseded by the follow-up commit. Check CI only against 037777645d0c49b064c0e3bbd64a548f37944352 before reporting validation.
- Current feature code is committed, but the Scrobble Generate UI and FAB/nav host are still not integrated. Dismissal must call ScrobbleGenerateViewModel.cancelGeneration() when the screen host is wired.
- No physical-device validation is claimed.

## 29. Scrobble host and first functional tabs (2026-11-18)

### Branch / source checks
- Implementation branch before this commit: `Main-dev`, HEAD `0b436c15625b646e2125ed2368eca215e0d4eb79`; stable `main` remains `11478fde56f1817cb777b296e60907359086f383`.
- Rechecked root `CLAUDE.md` and `AGENTS.md`; both are still absent on Main-dev (404). The complete project contract cannot be verified from those missing files.
- PR #8 remains draft/open, head `5bf065da305e6bb663960f9e09d30572dcdc0f32`; both exact-head CI runs #35 / `43748195854` and #1053 / `43747641686` passed. This validates the PR8 branch only, not Main-dev.
- Main-dev's generator code commit `037777645d0c49b064c0e3bbd64a548f37944352` passed exact-head Android CI run #1091 / `44350542384`.

### Code added / wired
- Added `ui/scrobble/ScrobbleContent.kt`:
  - Generate UI selects all nine implemented generation modes, seed track search for Similar Tracks, optional current-track seed for Mix, Replay period for Top, playlist size, and explicit Never Heard exclusions.
  - Shows progress, cancellation, errors, and the saved-playlist result.
  - Playlists UI reads the existing `LocalPlaylistStore`; supports search, pinned-first display, detail, play, shuffle, pin/unpin, rename, delete, track reorder and track removal.
  - Playback actions call MainActivity's existing `playFrom`/QueueCoordinator route. Generation itself still performs no queue mutation.
- Integrated the Scrobble surface in MainActivity:
  - Home-only FAB, suppressed by competing overlays, full player and IME; it uses the current Glass/Standard material, folds with the existing nav scroll state, and becomes the close glyph while open.
  - Three Scrobble tabs replace the normal four-tab bar only while Scrobble is open; normal Home/Explore/Library/Search tabs remain unchanged and the prior selected normal tab is retained.
  - Glass mode keeps all three Scrobble tabs inside the pill via `standaloneIndexOverride = -1`; mini-player remains in the existing nav stack.
  - Back, FAB close and normal-tab exit cancel active generation before dismissing the Scrobble host.
- Reused the existing local Replay data as the Statistics tab, with an optional heading override so the page reads “Statistics” while the Library Replay keeps its original title.
- Starting a new generation clears the prior success result so a stale saved-playlist card cannot be mistaken for the new request.

### Remaining / validation
- This commit introduces the first working host and functional Generate/Playlists pages; it is not a complete port of every PR8 playlist affordance. Playlist-level manual reorder, export/cover actions, Last.fm remote statistics (separately labelled from local Replay), and richer Stats source switching remain.
- Exact-head CI must run after this commit. No local Gradle build or device/emulator run is claimed.
- Physical-device checks remain: FAB measured docking with/without mini-player in Glass and Standard modes, scroll folding, IME/overlay suppression, Back priority inside playlist detail, search/seed selection, generation cancellation under network delay, and playlist persistence across process restart.
- Do not merge PR #8 or change `main` without explicit approval.


## 30. Scrobble motion and scroll isolation (2026-11-18)

- Added a dedicated `scrobbleNavBarScroll` connection. Scrobble content dispatches scroll into it and GlassNavBar consumes it only while Scrobble is open; the normal Home navigation retains its previous fold state.
- FAB folding follows the active Glass connection and is disabled in Standard mode. Opening/closing Scrobble expands only the Scrobble connection.
- Scrobble page entry/exit now uses a short drop/return transition. The existing Reduce Animation setting snaps this transition and the FAB glyph/scale; the Glass bar's inline/expanded fade is also disabled when Reduce Animation is enabled.
- Standard bottom-bar glyph tint now snaps with Reduce Animation instead of continuing a 200ms tint tween.
- No Home content or normal tab order was changed. Exact-head CI for the previous host commit `442bb3f28834c19c3fc7f62d7e2cfe705ab18f25` passed run #1094 / `44359311548`.


## 31. Separate Last.fm remote statistics (2026-11-18)

- Added public read-only Last.fm methods for `user.getinfo`, `user.gettoptracks`, `user.gettopartists`, and `user.gettopalbums`. These methods return `Result`, validate supported chart periods, clamp limits, and rethrow coroutine cancellation rather than swallowing it.
- Added tolerant JSON parsers for user totals and ranked charts, including Last.fm's single-object/array response shapes and image arrays. Added JVM parser tests for profile totals, chart rows, and malformed/empty payloads.
- Statistics now defaults to **Mylo Replay** (local device listening). A separate **Last.fm** source displays remote profile totals and remote top charts for 7 days, 1 month, 3 months, 6 months, 12 months, or all time. The two sources are never combined.
- Last.fm remote stats require a configured API client and a saved username. If unavailable, the page explains that the account must be connected in Settings; API failures have retry actions.
- No changes to the existing Last.fm scrobble submission pipeline. The new methods are read-only.
- Exact-head CI for the prior scroll/motion commit `712e0928ea09b316307db2ef5341a300f22059e9` is still being checked; this new statistics commit will need its own exact-head CI result. No physical-device validation is claimed.

## 32. Rollback and context-source correction (2026-11-19)

- User clarified that all continuation must be driven by this file, `SCROBBLE_CONTEXT.md`, not by the uploaded `Mylo Final Prototype.md`.
- To remove the unapproved post-checkpoint continuation, Main-dev was restored to the exact source tree of checkpoint `23d7afd235c075f083500ad0420ee38378c94545` using a normal, non-force rollback commit: `1dcd8a8621663ee55af39fa15f34f5925b2a5121`.
- Reverted post-checkpoint changes in: `MainActivity.kt`, `PlaylistGenerationRepository.kt`, `LocalPlaylistCodec.kt`, `LocalPlaylistStore.kt`, `ScrobbleContent.kt`, `ScrobbleGenerateViewModel.kt`, `PlaylistGenerationRulesTest.kt`, `LocalPlaylistCodecTest.kt`, and removed the post-checkpoint `LocalPlaylistExport.kt` and `LocalPlaylistExportTest.kt`. The previous context-only additions were also rolled back; this entry records the correction.
- Verified the rollback commit has no file-content diff against checkpoint `23d7afd...`; it only adds a new commit to preserve history. `main` remains at `11478fde56f1817cb777b296e60907359086f383`; no PR was merged or modified.
- Current target remains `Main-dev`. No implementation work is authorized by this rollback itself. Resume only from the audit/approved sequence recorded above, and inspect exact current refs before each action.
- Exact-head CI for rollback commit `1dcd8a8621663ee55af39fa15f34f5925b2a5121`: run #1104 / `44373228203` was observed in progress; Web tests/typecheck passed, Android SDK setup and Desktop compile were still running. Do not report the overall run as passed until all jobs finish.
- No local build, device test, or emulator test was run. Physical-device validation remains outstanding.
- Next: finish exact-head CI inspection; then resume the outstanding Phase 5 regression/review checklist from section 20 and the remaining Playlists acceptance items from sections 10, 13, 19 and 29. Before any additional app-code change, confirm the exact remaining gap against the current Main-dev source and the approved scope.

## 33. Post-rollback playlist gap re-audit (2026-11-19)

### Exact refs
- `Main-dev`: `b2a5900ebab2805e5c490a4b055d3ee8c18c898d` (documentation-only commit after rollback); its app source tree is identical to checkpoint `23d7afd235c075f083500ad0420ee38378c94545`.
- `main`: `11478fde56f1817cb777b296e60907359086f383`, unchanged.
- `last-wave`: `d0dd73755bfe640c515502751f440a32e57506d5`.
- `scrobble-ui`: `5bf065da305e6bb663960f9e09d30572dcdc0f32`.
- No PR was merged or retargeted. The current implementation target remains `Main-dev`.

### Current Mylo playlist implementation rechecked
- `app/src/main/java/com/music/bitchord/data/spotify/LocalPlaylistStore.kt` currently provides UUID-backed IDs, optional `createdAtEpochMillis` / `generationMode` / `isPinned`, save/delete/rename/get, update songs, pin, and a `movePlaylist` operation. Existing SharedPreferences JSON persistence and codec remain the source of truth; no Room migration is justified.
- `app/src/main/java/com/music/bitchord/ui/scrobble/ScrobbleContent.kt` currently provides playlist search, pinned/newest ordering, open/detail, play/shuffle, pin, rename, delete, track move-up/down and remove.
- Confirmed gaps in the current Scrobble playlist UI: manual custom-playlist creation, list sort choices (oldest/name/track count), playlist-level reorder UI, custom cover selection/reset, CSV/M3U export/share, Generate Similar/regenerate, and track search/add.
- The current Scrobble list reads the complete `LocalPlaylistStore.playlists` flow without a Scrobble ownership filter. This needs an explicit ownership decision/test so Library/import fallback playlists are not accidentally presented as generated Scrobble playlists. Preserve those existing playlists in Library.
- `movePlaylist` exists in the store but is not exposed by this screen. Track reordering/removal are already present in detail, but the UI does not surface persistence failures. `persist()` currently catches serialization/write exceptions and uses SharedPreferences `apply()`; do not promise durable-write acknowledgement or display success without checking the actual store contract.

### LastWave feature reference rechecked
- Current `last-wave` `PlaylistViewModel.kt` covers custom creation, rename, cover, pin, track removal/reorder, delete, CSV/M3U save/share and regenerate.
- `PlaylistScreen.kt` exposes create, sort (newest/oldest/name/track count), export, cover, pin and delete actions.
- `PlaylistDetailScreen.kt` exposes track search/sort/reorder, cover selection/reset, regenerate, rename, export and delete.
- `FileExportHelper.kt` contains pure CSV/M3U format builders plus Android save/share helpers. Only adapt the format and user-facing behavior; do not import LastWave's Room/DI/storage layer or its remote YouTube account playlist projections.
- Mylo's export adapter must use `Song.videoId` as the playable identity; do not blindly copy LastWave `GeneratedTrack.url`. Local/non-YouTube tracks need an explicit skip/report policy. CSV escaping and filename sanitization need focused tests.
- Cover selection must use Android document-picker URI grants safely. Replacing, resetting or deleting a cover requires lifecycle-aware permission cleanup; avoid releasing a URI grant still used by another playlist.

### Verification / limits
- This was a read-only source audit. No app code, build configuration, test or PR was changed in this pass.
- Rollback exact-head CI run #1104 / `44373228203` was still in progress at the last poll: Web tests/typecheck passed; Android unit tests and Desktop compile were running. No overall pass is claimed.
- No local Gradle build, emulator or physical-device check was performed.

### Next audit slice
1. Trace Mylo `Song` identity and YouTube Music search/radio APIs for track-add and Generate Similar; verify cancellation, timeout and stale-request behavior.
2. Inspect all current `LocalPlaylistStore` callers (especially Library/import fallback and generated-save paths) before choosing a stable Scrobble ownership marker.
3. Define the smallest playlist feature batch and regression tests against the acceptance matrix in section 19; then proceed under the already approved Phase 4 scope. Keep `main` untouched.


## 34. Mylo visual-system alignment and post-rollback continuation audit (2026-11-19)

### User direction reaffirmed
- Continue only from the approved Scrobble plan and this context file. The uploaded `Mylo Final Prototype.md` is not the implementation authority.
- Scrobble must feel native to Mylo: use its existing monochrome theme, typography, component language, spacing, dialogs, Glass/Standard navigation and player integration. Do not apply a separate LastWave visual identity or redesign approved flows.
- Preserve the approved host behavior: the Scrobble FAB opens persistent Statistics / Generate / Playlists tabs; the regular Mylo navigation returns unchanged on exit; Glass and Standard modes both remain supported. Keep the normal Home / Explore / Library / Search tabs and playback behavior intact.
- Library ownership / “Add to Library” remains explicitly deferred. Do not implement or infer that feature in this pass.

### Current Mylo visual source of truth inspected
- `ui/theme/Theme.kt`, blob `5ddab4736c637c03a81ebb05850ade46c89adfdb`:
  - Dark palette is near-black with white primary text and restrained charcoal surfaces; light palette is white/off-white with black text and muted gray secondary text.
  - Mylo typography uses the platform sans-serif family, heavy/tight display and headline styles (headlineLarge 30sp W800; headlineMedium 22sp W700; titleLarge 20sp W700; titleMedium 16sp W600), and restrained body/label styles.
  - UI should use `MaterialTheme.colorScheme` and `MaterialTheme.typography`, not hard-coded theme colors or a second font scale.
- `ui/components/FloatingBottomBar.kt`, blob `b6b83eb8fec7c8d1df472287afe16aaa62b57939`, and `GlassNavBar.kt` were inspected:
  - The two bottom bars deliberately share sizing/spacing primitives, including `PILL_INSET` and `TAB_VERTICAL_PADDING`; Glass mode also has its own supported material/fallback and Reduce Animation behavior.
  - Keep Scrobble tabs inside the existing host/bar implementation. Do not create a separate Scrobble-only tab bar or bypass the existing Glass/Standard selection.
- Current `ui/scrobble/ScrobbleContent.kt`, blob `72d2692b572e9a9c83e87b791525c1d36b7f358e`, already references Mylo MaterialTheme typography and color scheme, and uses the existing Material 3 controls. It also contains screen-local geometry and surface styling (for example 20dp content gutters, 16dp playlist surfaces and custom alpha overlays). These values are not yet proven to match all current Mylo screen patterns. Treat them as audit targets, not permission to restyle arbitrarily.
- No screenshot/device comparison was performed in this audit. Therefore **pixel-perfect alignment is not yet verified**. Before changing styling, compare the Scrobble pages against current Mylo Replay, Library, settings dialogs and shared components in both Glass and Standard modes; only change confirmed inconsistencies while preserving approved layout and behavior.

### Current data ownership facts rechecked
- `data/spotify/LocalPlaylistStore.kt`, blob `23c85bb33f7a9094f2b8f785ed186e4a0f947e89`, still stores local playlists in the existing SharedPreferences JSON store. `generationMode` distinguishes generated saves from imports/manual saves at the model level; `isPinned` and playlist identity are preserved. Do not introduce Room or a parallel store.
- `BitChordApplication.kt`, blob `b55849c00b3965e25c74c005df4872220e8f7d4e`, initializes LocalPlaylistStore before UI consumers.
- `MainActivity.kt`, blob `2b137bc5aa44ae85d006c7ae5035789814fe45f1`, currently maps every local playlist into the Library's On Device shelf. Spotify import flows call `createPlaylistWithVideoIds`; local fallback ownership is therefore a separate concern from YT Music account playlists.
- The current Scrobble playlist page also observes the complete LocalPlaylistStore list. This overlap is known and remains deferred with the user's Library decision; do not silently filter, duplicate, move, or sync playlists as part of visual alignment.
- `PlaylistGenerationRepository.kt`, blob `ef84b1ad96de57fadf46caf68b62bb134346955f`, uses Mylo `Song` rows and stable `videoId` identity. `ScrobbleGenerateViewModel.kt`, blob `60d99ddb35c0c17ea0a7b2f200369f3cc5f0ce69`, owns timeout/cancellation and saves only after generation returns. Keep generation separate from playback queue mutation; queue changes remain explicit user playback actions.

### Confirmed next work (no speculative UI additions)
1. Complete the outstanding source trace for Mylo's YouTube Music search/radio/artist/mood/library APIs, including cancellation/error propagation and track identity mapping. The repository source file was not yet located through the current GitHub file lookup; do not infer its implementation from call sites.
2. Finish the LocalPlaylistStore caller map, especially the exact Spotify import local-fallback save path and Library detail/play path. Keep YT Music account playlists distinct from local imported/generated playlists.
3. Compare current Scrobble Generate and Playlists components against Mylo's established screen/dialog patterns, then record only verified visual mismatches and the smallest safe alignment changes.
4. Complete the approved playlist acceptance gaps from sections 10, 13, 19 and 33 only after the above traces; do not add Library integration, YT Music export/sync, or unapproved playlist features.
5. Run focused tests and exact-head CI after implementation. No local build, emulator or physical-device validation is claimed by this audit.

### Change boundary
- This entry is documentation-only. No app source, tests, dependencies, branch structure, PR, or `main` was changed.
- Continue on `Main-dev` only. Do not merge PR #8/#4 or modify `main` without explicit approval.


## 35. Replay visual comparison and LastWave playlist behavior trace (2026-11-19)

### Mylo screen comparison
- `ui/scrobble/ScrobbleStatisticsContent.kt`, blob `2955a2e2f31ad2271f44ee6bf7adf9b996591623`, routes the Mylo Replay source directly through the existing `ReplayScreen`; it does not fork or restyle Replay. Last.fm is a separate source and is not mixed with local Replay metrics.
- `ui/replay/ReplayScreen.kt`, blob `dfb8b5ec20bc3601cf38defbc9cddd7ba3d278cf`, uses Mylo's artwork-derived mesh background, theme-aware ink/contrast, `PAGE_GUTTER + 4.dp`, `displayLarge` page heading and the existing Replay period selector. Preserve this implementation for Statistics; do not create a second local-stats screen.
- Generate and Playlists currently use `headlineLarge`, 20dp side gutters, Material 3 inputs/chips/buttons/dialogs and theme surface colors. Their broad material language is Mylo-native, but their individual hierarchy and spacing still need comparison with other Mylo pages before calling them pixel-aligned. Avoid replacing Mylo's monochrome/artwork-led visual language with LastWave's expressive surfaces, colors, icons or custom components.
- Current source-level design check only; no screenshot comparison or device test. Pixel alignment remains unverified.

### LastWave behavior source (not its UI)
- The correct LastWave paths use package `com.lastwave.app`, not Mylo's `com.music.bitchord`:
  - `ui/playlist/PlaylistScreen.kt`, blob `2bea1f6d4f9ccc9f08164b85a7a95a485d69fa56`.
  - `ui/playlist/PlaylistDetailScreen.kt`, blob `54712d5106f9a244677c3679373609948ddf110b`.
  - `ui/playlist/PlaylistViewModel.kt`, blob `0fe9109f3a0cce5cd3e23d90f6822e13e0678162`.
  - `data/playlist/PlaylistRepository.kt`, blob `37bd36c5d269b494aafd8c536fc54eac50f3f5a5`.
- LastWave's playlist screen confirms behavior for list sorting (newest, oldest, name, track count), custom playlist creation, search, pinning, custom cover selection/reset, export/share (CSV and M3U), regeneration, delete confirmation and a just-generated result banner.
- LastWave's detail screen confirms track search/filter, custom sort vs drag reorder, lock/unlock reorder mode, track removal, and URI-backed cover editing. Its implementation has substantial lifecycle, drag/autoscroll, stable-key and concurrent-write protections; do not copy the large screen wholesale.
- LastWave's repository supports track addition with duplicate protection and resolves incomplete tracks before saving. Mylo must adapt this around `Song.videoId`, current search/radio APIs and `LocalPlaylistStore`; do not port `GeneratedTrack`, Room, Hilt or LastWave playback infrastructure.
- LastWave `DESIGN.md` is not Mylo's design authority. It was read only to confirm that the feature reference uses its own visual system; Mylo UI work must continue to use Mylo's own theme and shared components.

### Clarified Mylo playlist import ownership
- `ui/MainViewModel.kt`, blob `38998fc5925ee8c42886dc7ad23bf73f5b66b378`, method `createPlaylistWithVideoIds` (lines 1114–1168) confirms:
  - When signed in, a Spotify import attempts to create a YouTube Music account playlist; on success it is a remote YT Music playlist.
  - When signed out, or when remote playlist creation fails, the imported songs are saved to `LocalPlaylistStore` as a local fallback.
  - This does not create a Spotify write-back/sync path. A local fallback is not linked to the Spotify original.
- `MainActivity.kt` also maps every LocalPlaylistStore item into the Library On Device shelf. The previously recorded overlap between Library and Scrobble remains real and deliberately deferred.

### Tests and implementation boundary
- Current Mylo tests inspected: `LocalPlaylistCodecTest.kt` blob `97d20d039349953dd6712579b5c1f9af65a38a78` covers legacy metadata defaults, Song identity/metadata round-trip and empty lists; `PlaylistGenerationRulesTest.kt` blob `1730c6237530aeec9b8ce2143922b5112de129df` covers videoId dedupe, normalized heard identity, explicit exclusion semantics and freshness ordering.
- No code or tests changed during this source trace. Do not infer the implementation of `YtMusicRepository` from its call sites; its source location/API cancellation contract still needs verification before adding search/add or multi-seed generation.
- Next implementation slice after that verification: add the smallest Mylo-native playlist management batch (create/sort/reorder and safe edit operations first), with codec/store tests before UI wiring. Then add export and generation-similar flows as separate tested slices. Keep Library integration postponed and `main` untouched.


## 36. Scrobble playlist sorting and manual order (2026-11-19)

### Implemented on Main-dev
- Added `data/spotify/LocalPlaylistSort.kt`, blob `4d14968025a430e67a2cddd76bb68dba59a80a99`:
  - Automatic sort modes: Newest, Oldest, Name and Track count.
  - Pinned playlists stay first in every automatic mode.
  - Comparisons use `Locale.ROOT` for deterministic case-insensitive names and stable tie-breakers.
  - Custom order returns the store's persisted order unchanged.
- Updated `ui/scrobble/ScrobbleContent.kt`, blob `f07f22d1d19a4f22a6a872b40efb263ebe199ea4`:
  - Added a compact sort menu using existing Material icons and MaterialTheme.
  - Added Move up / Move down to each playlist's existing overflow menu only while Custom order is selected and the search field is blank. This ensures the visible index is the same as the LocalPlaylistStore index.
  - Boundary actions are disabled at the first and last positions. Moves call the existing `LocalPlaylistStore.movePlaylist`; no new storage path or playlist identity was introduced.
  - Existing default behavior remains Newest with pinned playlists first.
- Added three sorting tests to `LocalPlaylistCodecTest.kt`, blob `424554b1646ca9ab800028efc6b44bb3e778ea85`: pinned-first newest/oldest, deterministic name/track-count ordering, and custom-order preservation.

### Commits
- Helper: `e0316cb6626c8afd48d1f7f8e2d7dc961d39f093`.
- UI: `4e32a25fd653202ec49bb8b2cc80bd93828c7139`.
- Boundary correction: `b1ba378a2f867aa09817fa5936aaaa59d68c8672`.
- Tests: `cef2c240cdee0471280b82903c3a0faded10a9d0`.

### Verification and limits
- Source review confirms the Custom mode is the only mode that exposes manual moves, and it hides those controls while searching so a filtered index cannot corrupt the persisted full-list order.
- No local Gradle build or unit-test execution was possible in this session. The GitHub connector returned no workflow runs or combined status entries for the test commit; therefore tests are **added but not yet verified as passing**.
- No emulator or physical-device visual check was performed. The sort menu uses Mylo's existing Material 3 controls and monochrome theme; device-level visual alignment remains to be checked.
- Library visibility/ownership, manual playlist creation, cover editing, export/share, Generate Similar, track search/add and other remaining playlist gaps are not part of this batch. The user's Library integration decision remains deferred.
- `main` was not changed. Continue on `Main-dev` only.


## 37. Playlist track search/add and repository cancellation trace (2026-11-19)

### Repository contract verified
- Located the actual shared implementation: `shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/YtMusicRepository.kt`, blob `efbd57934cd5f50449e2ceb579a9ce7cafe90ada`.
- `search(query, SearchFilter.SONGS)` returns the first page of YouTube Music track results; rows are `SearchResult.TopTrack` / `SearchResult.Track` with `Song` payloads. The repository deduplicates search rows by videoId.
- `radio(videoId)` calls the watch queue endpoint and parses the returned `Song` rows. Keep this API as the source for existing generation modes; do not invent a second search client.
- The shared `call()` wrapper rethrows `CancellationException` in its `onFailure` path. New Compose search effects must still explicitly rethrow cancellation and avoid publishing stale results after a cancelled query.

### Implemented on Main-dev
- `data/spotify/LocalPlaylistStore.kt`, blob `3482fb5a3af05240f696a5c9f54d4a0b9468edd1`:
  - Added synchronized `addSongIfAbsent`, preserving the same playlist ID, generation metadata, pin state and existing track order.
  - Added pure `appendUniquePlaylistSong`: rejects blank IDs, video uploads and an existing matching videoId; otherwise appends one track.
- `ui/scrobble/ScrobbleContent.kt`, blob `10354ae87110f68c426b0e77eecd1d0808794c1d`:
  - Playlist detail now has an Add tracks / Done toggle.
  - Search is debounced by 300ms, uses `YtMusicRepository.search(..., SearchFilter.SONGS)`, maps only track rows, removes blank/video/duplicate results and caps the display at eight.
  - Existing tracks are disabled as Added. The synchronized store method performs the final duplicate check at insertion time.
  - Loading, no-results and error states use Mylo's existing MaterialTheme controls; no LastWave component or visual token was copied.
- `LocalPlaylistCodecTest.kt`, blob `e62ea4d81b2d067d6671251bc54e482517a7b16a`, now tests append order, duplicate rejection, blank-ID rejection and video-upload rejection.

### Commits
- Store insertion: `95defc7d1ccf4aaadff1ccdd8f04cc075f7ead62`.
- Search UI: `8b579806f8aee3cb3583fd9065fe7d22a61cf45a`.
- Tests: `4469317f85c83aa1e5b3fca9eaf590733e26994b`.

### Verification and remaining work
- The store insertion is synchronized and rechecks the latest stored list, so two quick taps cannot append the same videoId twice.
- No local Gradle build or unit-test execution was possible in this session. The GitHub connector returned no workflow runs or combined status entries for the current test commit; tests are added but not yet verified as passing.
- No emulator or physical-device check has been performed.
- Still pending from the approved playlist scope: custom cover selection/reset, CSV/M3U export/share, Generate Similar/regenerate and the remaining detail/list acceptance checks. Manual playlist creation and Library visibility ownership remain deferred until the Library decision is resumed.
- `main` remains untouched; continue on `Main-dev` only.


## 38. CSV/M3U export and share (2026-11-19)

### Implemented on Main-dev
- Added pure `LocalPlaylistExport` serializers to `data/spotify/LocalPlaylistStore.kt`, blob `2071c6554fbd584ef44829f3f44d826abf1c6147`:
  - CSV contains title, artist, video ID, duration and YouTube Music URL. Every field is quoted and embedded quotes are doubled.
  - M3U writes `#EXTM3U`, duration-bearing `#EXTINF` rows and YouTube Music URLs; blank IDs and video uploads are omitted.
  - Export filenames strip path/reserved characters and constrain the title length.
- Added tests to `LocalPlaylistCodecTest.kt`, blob `021973a65691fcf116ef8946045088d748ada96d`, covering CSV quoting, M3U duration/video filtering and safe filenames.
- Added `ScrobblePlaylistExportDialog` in `ui/scrobble/ScrobbleStatisticsContent.kt`, blob `ffe4304f28ec2264354a8828dcdeb5658c1ba456`:
  - Mylo Material 3 AlertDialog with Save CSV, Share CSV, Save M3U and Share M3U actions.
  - Save uses Android's CreateDocument picker.
  - Share writes a temporary export under `cache/shared/playlist_exports` and sends it through the already-declared `${applicationId}.fileprovider`; the existing `file_paths.xml` exposes the shared cache path, so no manifest/provider expansion was needed.
- Connected the export action and status feedback to the playlist detail page in `ui/scrobble/ScrobbleContent.kt`, blob `ec42faf189808d170b7d0f22eeb6f05cc754c96c`.
- Corrected the save-picker lifecycle: the dialog remains composed while CreateDocument is open, and dismisses only when the picker returns. This keeps the activity-result launcher registered. Fix commit: `37127376a1b4de0f68b823f3a9fff4ba59f6d0fc`.

### Commits
- Serializers: `e3c9dcaadaf467e50561b2b14544326845ff0757`.
- Export tests: `2919c7b2df07d95d53b71293fc080077c51c6423`.
- Export dialog: `09301e10f30b2046b301fe960cdbc0a11a035719`.
- Detail-page connection: `beb77744bbf8a2e63098eb8ff55b745c496ffbe1`.
- Picker lifecycle fix: `37127376a1b4de0f68b823f3a9fff4ba59f6d0fc`.

### Verification and limits
- Source review confirms the share authority matches the existing manifest provider (`${applicationId}.fileprovider`) and the shared cache path already permitted by `res/xml/file_paths.xml`.
- No local Gradle build or unit-test execution was possible. The GitHub connector has not surfaced CI/workflow results for the current Main-dev commits; the export tests are added but not verified as passing.
- No emulator/physical-device visual or share-target validation has been performed. The export dialog uses Mylo's existing Material 3 theme; visual alignment still needs device confirmation.
- Custom cover editing, Generate Similar/regenerate and remaining list/detail acceptance checks are still pending. Manual playlist creation and Library ownership integration remain deferred.
- `main` remains untouched.


## 39. Custom playlist cover selection and persistence (2026-11-19)

### Implemented on Main-dev
- Extended `LocalPlaylist` with nullable `customCoverUri` in `data/spotify/LocalPlaylistStore.kt`, blob `8695d20ab1b54955a63496dd135dc282a4483c2b`.
- Added synchronized `LocalPlaylistStore.setCustomCover`; it updates only cover metadata and preserves playlist identity, tracks, pin and generation metadata.
- Updated `LocalPlaylistCodec.kt`, blob `da3cc5402d8d5a6f3e1679b2919fca17b93dd9c4`, to encode/decode `customCoverUri`. Legacy JSON without this field safely defaults to null.
- Updated `LocalPlaylistCodecTest.kt`, blob `46cd3657c81e5c3d56e6cf33c01a3167228eb231`, to assert the legacy null default and URI round-trip.
- Updated `ui/scrobble/ScrobbleContent.kt`, blob `9498f3dbf3bd7cb3edccf5cd22e3929a616d1a11`:
  - Playlist rows and detail artwork prefer the custom URI, falling back to the first track's thumbnail.
  - Detail overflow offers Change cover and, when set, Reset cover.
  - Uses Android OpenDocument for image selection and takes persistable read permission before saving the URI.
  - On replacement/reset/delete, releases the previous persisted permission; on failed save, releases the newly acquired permission.
- No broad storage permission or new provider path was added. The selected URI is stored as metadata in the existing playlist JSON.

### Commits
- Model/store: `e29b064eb8b6c044c59414ed1357dc760fbade44`.
- Codec migration: `6ff2754d7dde2241ae1215c035d88e68d8aa3694`.
- Tests: `26a96c5a01aa1d093918497a32e7322dedb374a8`.
- UI: `76318377cec8ab051af58ec443031ef34d3c6ced`.
- Context binding correction: `d8f70a995bc400bb2275463ade1b74dd2b4c29ff`.

### Verification and limits
- Reviewed all current direct `deletePlaylist` callers: the other MainActivity delete action is for remote YouTube Music account playlists, not LocalPlaylistStore. The Scrobble local delete confirmation releases the custom URI permission before deleting the local row.
- No local Gradle build, unit-test execution, emulator test or physical-device test was possible. Tests are added but not yet verified as passing.
- URI permission behavior and Coil loading of a persisted content URI still require device verification with an actual image provider.
- Remaining approved work: Generate Similar/regenerate and final playlist/detail acceptance checks. Library ownership and manual playlist creation remain deferred.
- `main` remains untouched; continue on `Main-dev` only.


## 40. Generate Similar from an existing playlist (2026-11-19)

### Implemented on Main-dev
- Added `PlaylistGenerationRepository.generateSimilar`, file blob `56cfcde9332601b9b2b661b29860a7ac7d68b000`:
  - Uses up to three source tracks, preferring distinct artists, as independent YouTube Music radio seeds.
  - Interleaves the radio queues so one seed cannot dominate.
  - Excludes every source-playlist videoId and normalized title/artist identity.
  - If the radio pool is short, widens with Quick Picks and Home recommendations.
  - Deduplicates candidates and prioritizes tracks not already saved locally; saved tracks are only retained when the pool is thin.
  - Requires at least five results and caps the new mix at the existing generator maximum.
- Added `PlaylistGenerationRules.interleave`; test coverage is in `PlaylistGenerationRulesTest.kt`, blob `69e10f995721ede41ec35eec9237a343e6dbb75e`.
- Added `ScrobbleGenerateViewModel.generateSimilar`, file blob `e7b11bcc94d206e76423132b1542fa0fd0656c32`:
  - Saves a new playlist named from the source title with a unique suffix when needed.
  - Stores generation metadata as `similar-playlist`.
  - Never edits or replaces the source playlist.
  - Uses the existing generation timeout, cancellation token, progress/error state and saved-playlist event flow.
- Added the action to playlist overflow and detail actions, with progress, cancel, error and saved-playlist feedback in `ScrobbleContent.kt`, blob `06d5cabe01b90fcb4d699bcda3e431f731e7767b`.
- Wired the existing generation ViewModel through `MainActivity.kt`, blob `8a0d8b28d529ea4032f27fef162e0e5c1ffb3e3b`.
- Added source-title uniqueness tests to `GenerateCommitTest.kt`, blob `3eeb52d51df58e4ca21180be1bee94439ed1650d`.

### Important behavior boundary
- This is a new playlist-inspired mix, not an exact replay of the original generation request. `LocalPlaylist` stores the generation mode but not the original count, period, seed and exclusion settings, so exact request replay is not currently possible.
- The current Mylo Last.fm client does not expose the LastWave similar-track/artist graph endpoints. This implementation intentionally uses Mylo's existing YouTube Music radio and recommendation APIs instead; Last.fm graph expansion is not claimed as ported.
- The source playlist remains intact. The new playlist is saved locally and appears in the current Scrobble/Library surfaces according to the existing behavior; the user's Library ownership redesign remains deferred.

### Verification and limits
- Reviewed the repository and ViewModel call chain, list/detail action wiring, cancellation checks, source-track exclusion and unique naming.
- Tests were added for radio interleaving and title uniqueness. No local Gradle build or unit-test execution was possible; the GitHub connector returned no workflow runs or combined status entries for the current Main-dev commit.
- No device test or visual screenshot check has been performed. Main-dev head at this audit: `e1cb98039f218795e15a188d3ebe666bd47321dd`.
- `main` remains untouched. Continue only on `Main-dev`.


## 41. Playlist/Generate acceptance audit after implementation (2026-11-19)

### Confirmed working in source
- Playlist list: Newest / Oldest / Name / Track count / Custom order; pinned-first automatic sorting; Move up/Move down only in Custom order and hidden while filtering.
- Playlist row actions: Pin/Unpin, Generate similar, Move up/Move down, Rename and Delete.
- Playlist detail: Play, Shuffle, Add tracks, cover Change/Reset, Generate similar, CSV/M3U export/share, Rename/Delete, and track Move up/Move down/Remove.
- Add-track search: debounced, cancellation-safe, filters blank IDs/video uploads, deduplicates results and rechecks duplicates atomically in LocalPlaylistStore.
- Seed-track search on Generate: now clears stale results while typing, rethrows cancellation, filters invalid/video results and surfaces loading, no-results and error states. Latest UI blob: `f34212b4722f6308071b903888972f94a79f68fa`.
- Playlist detail action row was consolidated to Back, Add tracks and one overflow menu; this avoids crowding the small-screen header and keeps the actions in Mylo's existing Material 3 style.
- Statistics still delegates to the existing ReplayScreen for Mylo Replay data; Last.fm remains a separate statistics source.

### Explicitly not complete / not silently substituted
- Track ordering currently uses explicit Move up/Move down actions. LastWave's drag-and-drop reorder/lock mode has not been ported.
- Exact regeneration of the original request is unavailable because original count/period/seed/exclusion settings are not stored on LocalPlaylist. Generate similar creates a new source-inspired mix instead.
- Last.fm similar-track/artist graph expansion is not implemented; Generate similar uses multi-seed YouTube Music radio and Quick Picks/Home fallback.
- Manual playlist creation and Library ownership/visibility redesign remain deferred per the user's decision.
- No pixel-perfect device screenshot, actual image-provider permission test, SAF export test or share-target test has been performed.

### Next verification gate
1. Run the Android unit tests and app compile on Main-dev.
2. Fix any compile/test failures before adding more features.
3. Device-check image URI persistence/reset/delete, CSV/M3U save/share, playlist search/add, sorting/reorder, Generate Similar cancellation and result visibility.
4. Revisit drag-and-drop only after the current move controls and device behavior are verified.
- The latest Main-dev UI source is `ScrobbleContent.kt` blob `f34212b4722f6308071b903888972f94a79f68fa`.
- Android CI run #1133 / ID `44383699697` targets commit `6e3b6fb85f711a3491164e6860cb9096ea0f04d3`; it is still pending with zero jobs started at the latest poll. Earlier run #1132 / ID `44383676132` for `4e35ff13adfaaa5b357fc37dd2389eaddcd6c814` was cancelled by a later push. Do not treat either as a passing result; recheck run #1133 before proceeding.
- `main` remains untouched.


## 42. Local playlist persistence and mutation safety (2026-11-19)

### Implemented
- `LocalPlaylistStore.kt` blob `f797f9563919df54517eb283c790478dab1bfff6` now serializes mutations with a `Mutex`, writes JSON with `SharedPreferences.commit()` on `Dispatchers.IO`, and publishes the new StateFlow snapshot only after the write succeeds.
- Mutations are non-cancellable after acquiring the store lock so a screen leaving mid-write cannot leave disk and in-memory state divergent. Cancellation while waiting for the lock still prevents the operation from starting.
- Save failures throw to the caller; rename/delete/pin/cover/reorder/add/remove return failure without optimistic state changes. Scrobble surfaces those failures instead of claiming success.
- Local playlist load/JSON decode failures are exposed through `loadError`; the Scrobble list shows the error and a Retry action instead of a misleading empty state.
- Added atomic `moveSong` / `removeSong` operations keyed by the expected `videoId` and current index, avoiding stale whole-list snapshots when users move/remove tracks quickly. Pure helper tests were added to `LocalPlaylistCodecTest.kt`, blob `1be05233c24356fb8eb0bc4857052b44bf5c73be`.
- `GenerateCommit.kt` blob `e005cb014aa5c7645e772364acfb79d15e2bb9c6` now accepts a suspending commit callback so generated playlist persistence can run on IO.
- Spotify local fallback now catches persistence failures and reports them through the existing queue notice; see `MainViewModel.kt` blob `27d6619b0608f756c2697544ead9273a82478aae` and `MainActivity.kt` blob `bbb0039708eb4a4c06f54dd1756ce033c5c9cca3`.
- Scrobble list/detail mutations now launch through Compose coroutine scopes; cover URI grant cleanup and deletion cleanup are protected so cancellation cannot release a URI after a successful persisted cover update. UI blob `92480551fac2ceaab4aee428ecd9e77822189cd0`.
- Corrected CSV field quoting to use explicit quote concatenation; the export test remains the regression guard.

### Verification
- Android CI run #1156 / ID `44386064413` targets Main-dev commit `b2ec6ffa24be4d6f3decde439c81c1044f8ad74b`. At the latest poll, Web tests/typecheck, Desktop compile/tests and Android unit tests were all in progress. No passing result is claimed yet.
- The workflow builds/tests but does not produce an installable APK by default. Device checks remain outstanding, especially large-playlist write latency, custom-cover URI persistence/release, and CSV/M3U save/share.
- Current SharedPreferences JSON storage remains the existing persistence backend; this change makes its writes serialized and failure-aware, but it is not a Room/DataStore migration.
- `main` remains untouched. Continue only on `Main-dev`.

## 43. Approved final prototype constraints + CI failure follow-up (2026-11-19)

### Source of truth
- Read the user-uploaded `Mylo Final Prototype.md` in this session. It is the approved visual/interaction reference for the Scrobble shell. Do not replace it with LastWave's UI or infer a new design.
- All Scrobble screens must use Mylo's own existing visual language: existing theme colors, typography, Material 3 surfaces, spacing, shapes, dialogs and navigation components. Adapt feature content to Mylo; do not introduce a second design system.

### Prototype acceptance checklist
- FAB is right-aligned directly above the mini-player, at the same measured position in Glass and Standard modes; visible only on Home.
- Glass mode supports the approved scroll-fold behavior: the nav pill folds to the active tab and the mini-player/column compact as specified by the existing prototype.
- Opening Scrobble uses the approved drop transition; the FAB morphs to Close. Closing restores the normal circular FAB state and normal navigation.
- Scrobble tabs are Statistics, Generate and Playlists inside the existing navigation pill; the mini-player remains above it.
- Statistics count-up and split-bar growth animations match the prototype. Generate's generated row animates into Playlists.
- Selecting a normal app destination exits Scrobble mode and restores the normal tab set. Switching among the three Scrobble destinations must remain within Scrobble mode.
- Reduce motion disables the animations while preserving the exact same end states and navigation behavior.
- Verify both Glass/Standard and Reduce motion on/off combinations. No device-visual pass is claimed until checked on a device/emulator.

### CI follow-up — run 1156 / ID 44386064413
- Web tests/typecheck: **passed**.
- Desktop compile/tests: **cancelled** during compile; desktop tests skipped. This is not a pass.
- Android unit tests: **failed** — 1,136 tests completed, 1 failed, 1 skipped. Failure reported as `trackMoveUsesExpectedIdentityAndPreservesTheOtherOrder` at `LocalPlaylistCodecTest.kt:141`.
- Inspected the exact tested commit `b2ec6ffa24be4d6f3decde439c81c1044f8ad74b`: its test expected a missing track identity to return the unchanged list, while `movePlaylistSong` correctly returns null when the expected videoId is absent. This is a stale test expectation, not evidence that a track was moved incorrectly.
- The current `Main-dev` test file has since been updated to assert null for a missing identity (current fetched blob `1b4a9492bb5bac466e20af390ac7d39df0643f04`). However, that newer source has **not** yet been verified by a new CI run. Do not mark Android tests green until a run for the newer commit passes.
- Run 1156 did not verify the Android APK build: the APK build step was skipped. No device test was performed.

### Next actions
1. Establish the exact current `Main-dev` head and the commit containing the corrected test expectation; trigger/obtain CI for that exact commit.
2. Confirm Android unit tests pass; inspect desktop compile/tests separately because the previous job was cancelled.
3. Continue the final prototype acceptance audit, especially FAB positioning in both nav modes, scroll-fold, normal-tab exit/reset, reduced-motion behavior, stats animations and Generate-to-Playlists animation.
4. Do not add new playlist features while this verification gate is unresolved. Library integration remains deferred.
5. Keep `main` untouched; work only on `Main-dev`.

## 44. Prototype implementation audit — shell and motion gaps (2026-11-19)

### Confirmed implemented in current Main-dev source
- `MainActivity.kt` blob `bbb0039708eb4a4c06f54dd1756ce033c5c9cca3`:
  - Scrobble mode has saved state and a close handler that cancels generation, expands the Scrobble nav and resets the selected Scrobble tab to Statistics.
  - The normal tab handler closes Scrobble before selecting a normal destination; selecting a Scrobble tab only changes the Scrobble section and keeps Scrobble open.
  - The normal app tab list remains intact (Home, Explore, Library, Search).
  - FAB visibility is restricted to Home and suppressed by competing overlays, the full player and keyboard.
  - FAB is positioned at the right edge above the measured floating-bar stack using the same host layout in Glass and Standard modes.
  - Glass mode receives the dedicated Scrobble scroll connection while Scrobble is open.
  - Scrobble's three tabs use `standaloneIndexOverride = -1`, keeping all three tabs inside the pill.
- `GlassNavBar.kt` blob `ba27410d2563008d741a0772ebf6a7f06e8426c4` supports the standalone-index override and uses the passed scroll connection.
- `ScrobbleFab.kt` blob `c70fd7c0840da6a13bac59497f24053a7c17084d` implements the GraphicEq/Close glyph, fold scale/lift, hidden non-interactive semantics and reduced-motion snap behavior.
- `ScrobbleContent.kt` blob `445ab4ffa746c5624c199dac038b00ff426451af` contains the current playlist/generate UI and Mylo Material 3 styling.

### Confirmed prototype gaps
- `ScrobbleStatisticsContent.kt` blob `4b255694098cd5c2c7c33a5f9d88a4139bcc1245` contains no count-up/split-bar animation implementation (no Compose animation APIs in this file). The approved Statistics motion is therefore not yet implemented here.
- `ScrobbleContent.kt` contains no AnimatedVisibility/AnimatedContent/animate* usage for the generated-playlist row handoff. The approved Generate → Playlists fly animation is not yet implemented in this screen.
- The source confirms the nav shell and FAB behavior, but source inspection cannot establish pixel-perfect alignment, actual Glass/Standard placement, drop transition feel, or reduced-motion behavior on device.
- Do not claim the approved prototype is fully implemented until these two motion gaps are resolved and all mode combinations are device-checked.

### Next implementation slice
1. Trace the exact Statistics data source and current composable ownership before adding count-up/split-bar animation; do not fabricate or combine metrics.
2. Trace the generation success event and playlist list state to implement a single handoff animation without duplicate rows, lost saves, or stale list state.
3. Reuse Mylo's existing Material 3/theme tokens and animation/reduced-motion settings; keep the same final states when motion is disabled.
4. Add focused tests for state/event correctness where possible, then rerun CI on the corrected current Main-dev source.
5. Do not touch `main`; Library integration remains postponed.

## 45. Correction — Statistics animation audit (2026-11-19)

- Section 44's statement was too narrow: `ScrobbleStatisticsContent` delegates its Mylo Replay source to `ReplayScreen`; auditing only the wrapper was not enough to decide whether Statistics motion existed.
- Inspected `ReplayScreen.kt` blob `dfb8b5ec20bc3601cf38defbc9cddd7ba3d278cf`. It imports/uses `animateColorAsState`, but has no count-up numeric animation (`animateIntAsState` / equivalent) and no animated bar-width/progress implementation for the approved split-bar growth. These two approved Statistics details remain unimplemented.
- The shell's Scrobble entry/exit transition is present in `MainActivity.kt`: entering uses vertical slide + fade, leaving uses vertical slide-out + fade, and Reduce motion removes those transitions. This is source-confirmed; device feel still needs verification.
- Generate success currently changes the selected Scrobble tab to Playlists through `onOpenPlaylists = { selectedScrobbleTab = 2 }`. The generated-row fly/handoff itself is not implemented in the current Scrobble content.
- Next implementation must preserve the current Replay data and event path; add only presentation motion around real values/events. Do not fabricate a split percentage or use fake counts to match the static prototype.

## 46. Re-opened approved prototype source (2026-11-19)

- Re-opened the actual uploaded `/mnt/data/Mylo Final Prototype.md` (not a prior summary). It confirms the intended app surfaces: Home, Explore, Library, Search, then the Scrobble sub-pages Statistics, Generate and Playlists.
- Exact checklist wording confirms: FAB at right above mini-player in both Glass and Standard, Home only; scroll-fold only in Glass; Scrobble entry drop motion; close restores FAB circle; three Scrobble tabs remain inside the pill; mini-player stays above and FAB becomes X; Statistics numbers count up and split bar grows; generated row flies into Playlists; tapping a normal app tab exits Scrobble and resets normal tab list; Reduce motion disables motion but preserves final state.
- Prototype text does **not** define the split-bar categories, data source, percentage denominator, or precise visual dimensions. Do not invent these from the sample `Plays 0 / 0% Mylo / Next up` copy. Before implementing the split bar, map it to an existing authoritative metric/model or obtain the missing visual/data definition.
- Current `ReplayScreen.kt` renders the existing Replay data (cards, ranked charts and listening-shape tiles). It is not itself a spec for the prototype's compact Statistics split-bar component; do not change Replay's information architecture just to approximate the mock.
- The prototype's "Tab tap" means a normal app destination tap closes Scrobble; tapping among Statistics/Generate/Playlists stays inside Scrobble. The current MainActivity handler does this distinction.
- Current next work must stay source-led: inspect the actual Scrobble Statistics state/data and generation-success event; implement only details defined by the prototype and backed by real data. Where the prototype is silent, preserve current Mylo behavior and record the open point rather than guessing.


## 47. Generate-to-Playlists handoff implementation (2026-11-19)

### Implemented on Main-dev
- MainActivity collects the existing saved-playlist event only while Scrobble is open. A successful, persisted save switches directly to the Playlists tab and passes the new playlist id to the list surface.
- The handoff id is cleared after the arrival-animation window and on Scrobble close, preventing an old playlist from replaying the animation on later visits.
- The newly saved row uses a short fade, vertical-rise and scale entrance in the existing Material 3 row. The entrance is skipped when the app's Reduce animation setting is enabled; the saved playlist and destination state remain identical.
- The existing manual View playlists action remains available. Generation failure or cancellation does not emit the save event and therefore does not navigate.

### Scope boundary
- This implements successful-save navigation and destination-row arrival motion. It is not yet a measured shared-element flight from the Generate row to the exact Playlists row; that remains an explicit visual acceptance item rather than being claimed complete.
- Statistics count-up and split-bar work remains gated on verified data semantics. No categories, percentages or denominators are invented.
- The uploaded prototype attachment is no longer an active source. Continue from the user's conversation-approved decisions, current Mylo implementation and this context record; do not reopen the removed attachment.

### Verification
- Source-level review only at this commit. Android, desktop and web CI plus device animation checks are still required; no pass is claimed.
- main remains untouched; work only on Main-dev.


## 48. Statistics count-up using Mylo Replay data (2026-11-19)

- Added count-up motion to the real Mylo Replay totals shown in the Statistics surface: total plays and listening minutes. The displayed values are derived from ReplaySummary; no placeholder counts or external totals are introduced.
- Animation runs only when ReplayScreen is hosted with the Statistics heading. Standalone Replay surfaces retain their existing values and behavior.
- Reduce animation snaps both values directly to their real totals without changing the data or layout.
- The split-bar remains unimplemented: current ReplaySummary has totals and ranked songs/artists/albums/genres, but no approved source/category definition or denominator for the bar. Do not infer one.
- CI/device verification remains pending; no build or test pass is claimed. Continue only on Main-dev; main remains untouched.


## 49. Reduce-motion coverage and counter performance (2026-11-19)

- Moved the Statistics count-up state into ReplayCardRow so only the metric-card row recomposes during the 900 ms count-up; Replay charts and the rest of the page no longer recompose on every animation frame.
- Scrobble-to-Scrobble tab changes now use no transition when Reduce animation is enabled. The existing entry/exit transitions, FAB, playlist-row arrival and Statistics counters already snap/skip under the same setting.
- Glass FloatingTabBar now skips its container size animation when Reduce animation is enabled. Its pill motion and inline/expanded AnimatedContent already use the setting; Standard FloatingBottomBar already snaps its indicator.
- No change to data, tab state, or playlist persistence when animations are disabled.
- Full shared-bounds Generate-to-Playlists transition remains open; current row entrance is the safe fallback until the shared transition is implemented and device-checked. Split-bar categories/denominator remain unspecified and are not invented.
- The preceding CI run #1163 caught the missing ReplayScreen remember import; this commit adds it and isolates animation recomposition. Fresh CI is required for this head.


## 50. Generate-to-Playlists shared-bounds transition (2026-11-19)

- Wrapped the existing screen AnimatedContent in Compose SharedTransitionLayout and matched the saved-playlist card on Generate to the new playlist row on Playlists with a stable playlist-id key and sharedBounds. This is a container transform because the source card and destination row have different content/layout.
- The shared transition is enabled only for a save originating on Generate. Saves initiated from Playlists keep the existing row-arrival animation; no phantom source element is created.
- The source id and origin are one-shot and cleared after the transition window or when Scrobble closes. The current selected tab is read through rememberUpdatedState so the origin is not captured stale by the long-lived event collector.
- Reduce animation skips the shared-bounds transition and the fallback row entrance. Replay period-chip color transitions also snap when Reduce animation is enabled.
- The transition uses the existing Mylo playlist card/row styling; no new design system or Library behavior was introduced.
- Android/desktop/web CI must pass on the resulting head, and the shared transition still requires device visual verification. The Statistics split-bar definition remains unresolved and is not fabricated.


## 51. Verification follow-up — shared-bounds handoff (2026-11-19)

- Re-fetched live Main-dev. The branch was at `29faf8a5e6e6a4d56d1c5f0e8814ac00ec32e6ec` (commit `feat(scrobble): share saved playlist transition`); the handoff's earlier SHA `0fd7e0f` is stale. The live `main` head remains `11478fde56f1817cb777b296e60907359086f383`; no main ref or file was changed.
- Reconciled current sources: `MainActivity.kt` blob `6686758823ff0821feb6c0afb8c87dc797a3b91c`, `ScrobbleContent.kt` blob `9ab870bd3329c0bbe15682b52e4e5a237f1dcb43`, `ReplayScreen.kt` blob `7bb957e4e73b9fd7ef872fad5c985c3ba5d9ac40`, and `ScrobbleGenerateViewModel.kt` blob `e7b11bcc94d206e76423132b1542fa0fd0656c32`.
- Confirmed stable playlist row identity: `LazyColumn.itemsIndexed` keys each row by `playlist.id`. The shared-bounds source and destination use the same `scrobble-playlist-<id>` key, and the event collector records whether the save originated from Generate before selecting Playlists. Handoff origin/id are cleared on close and after 1.6 seconds.
- Existing focused tests include `GenerateCommitTest` (cancellation before/after generation and exactly-once commit) and `ScrobbleFabTest`; there is not yet a direct test for the MainActivity saved-playlist event → tab-navigation handoff. Do not overstate that coverage.
- CI run #1167 / ID `44395593096`, exact head `29faf8a5e6e6a4d56d1c5f0e8814ac00ec32e6ec`: Android unit tests **passed**; desktop compile **passed**; desktop unit tests **passed**; web tests **passed**; web typecheck **passed**. The optional Android/TV/Wear APK build was **skipped** because `build_apks` was not requested. This run verifies the shared-bounds implementation at that exact commit.
- No physical-device/emulator verification was performed. Shared transition visual quality, Glass/Standard nav combinations, and Reduce Motion behavior remain device acceptance checks.
- Statistics source remains Mylo's actual on-device ListeningStats aggregation (track/artist/album totals and day/time buckets). No split-bar categories or denominator are defined by the existing summary/prototype decisions, so no split-bar was fabricated.
- Historical next-step note superseded by Agent Rules (section 0): do not ask the user to define split-bar categories/denominator before tracing LastWave and Mylo source. Inspect the real component and data model; implement only source-backed, truthful behavior. Navigation regression coverage, optional APK build and physical-device checks remain verification tasks.


## 52. Correction — re-read current LastWave branch before Statistics decisions (2026-11-19)

### Fresh branch state
- Re-read this entire 1,507-line context file from the current `Main-dev` head before continuing. This section corrects the stale assumption in section 51 that the split-bar question should be sent back to the user immediately.
- Current `Main-dev` head at this update: `0348f5a7745fb0ae617c90ba70ce4e1bf3b0e642`.
- Current `last-wave` head: `d0dd73755bfe640c515502751f440a32e57506d5` (not the older `e68d6f9` / `5da5446` refs recorded by earlier audit passes). Its latest commit is documentation-only: `docs: complete Scrobble audit interaction and failure matrix`.
- The current `last-wave` branch still contains the feature reference implementation; inspect its live source at the current branch head rather than relying on old branch SHAs or asking the user to repeat decisions already available in code/context.

### Current LastWave Statistics behavior inspected
- `ui/home/HomeScreen.kt` blob `8460bf1a5fe545f25ce5ed4011804ed1e6f91386` chooses the honest headline label: local Room aggregates are **Plays**, while remote Last.fm data is **Scrobbles**. It renders the profile/overview statistics card and opens the detailed Listening Stats destination.
- `ui/home/HomeScreenStats.kt` blob `f5ba02d075aa4b53d205b5d5514629a1d0206784` implements the overview card: animated headline count, then separate Tracks / Artists / Albums stat pills; compact scroll state retains the same values and navigation.
- `ui/stats/ListeningStatsScreen.kt` blob `ca369899fd476b53ae3960d28c8d2ab9ef4ca587` implements detailed local listening analytics: range selection (month-to-date / year-to-date / all-time), Listening and Plays summary cards, timeline metric chips, and grouped day/month/year bar-chart progression.
- These are two distinct Statistics surfaces: Last.fm/local profile overview and detailed local listening timeline. The current LastWave source does **not** contain a component explicitly named or implemented as the prototype's compact split bar. Do not conflate the timeline chart or three stat pills with that split bar.
- The Mylo prototype/context decisions remain authoritative for Mylo's visual result. The split bar must be mapped against the actual available Mylo/LastWave data and the intended prototype meaning; do not invent a percentage denominator or combine local Plays with remote Scrobbles. First inspect all relevant source/state and existing UI data, then implement the closest faithful, truthful behavior without asking the user to re-state information already in the repo.

### Process correction
- The earlier follow-up question was premature because it relied on section 51's open-question line without rechecking the current `last-wave` branch and the full context file. Do not repeat that question.
- Continue using `last-wave` as the feature/behavior reference and `Main-dev` as the only implementation target. Keep `main` untouched. No branch creation and no wholesale merge of `last-wave`.
- This update is documentation only; no application code or branch other than `Main-dev` was changed.

## 53. LastWave track-concentration behavior ported into Mylo Statistics (2026-11-19)

### Source traced
- LastWave reference head: `d0dd73755bfe640c515502751f440a32e57506d5`.
- `ListeningStatsScreen.kt` blob `ca369899fd476b53ae3960d28c8d2ab9ef4ca587` contains the Track concentration section and its three-way breakdown.
- `ListeningStatsModels.kt` blob `d7f5bf16c54566f1bbd380e96971d2cc43e32e08` defines the concentration values: top track, next two tracks, and remaining tracks; duration labels; unique-track count; average plays per track.
- `ListeningStatsViewModel.kt` blob `1425e7e14199feea2773f6450df3d5850a65d44c` computes the share against total listening duration. The original screen also shows the donut summary, breakdown rows and average/unique metrics.
- Important source-quality finding: other LastWave Listening Stats calculations include synthetic/fallback values and approximations (e.g. fabricated 210-second play durations and inferred sessions). Those are **not** being copied into Mylo. Only the concentration behavior that can be computed exactly from Mylo's existing per-track duration aggregates is ported.

### Mylo implementation on Main-dev
- Added `app/src/main/java/com/music/bitchord/ui/scrobble/TrackConcentration.kt`: pure concentration calculation plus Mylo-native Material 3 card, animated three-segment bar, percentages, duration breakdown, average plays/track and unique track count.
- Updated `app/src/main/java/com/music/bitchord/ui/scrobble/ScrobbleStatisticsContent.kt`: shows the concentration card above the existing Mylo Replay content when the Mylo Replay source is selected and real summary data exists. Last.fm remains separate and untouched.
- Data source is only the current `ReplaySummary`: ranked per-track milliseconds and actual play counts. Segments are top 1, tracks 2–3, and all remaining tracks; percentages use `summary.totalMs` and are rounded to a total of exactly 100%. No synthetic values or guessed denominator.
- Reduce Motion uses snap animations; otherwise the three segments animate in. Existing Replay information architecture and Library behavior are unchanged.
- Added `app/src/test/java/com/music/bitchord/ui/scrobble/TrackConcentrationTest.kt` covering the 3-way split, single-track case, empty/zero input and negative-duration safety.

### Commits and verification
- Component: `9e2845bce13d07f2cae65d6a30100ae2a90eff6e`.
- Statistics screen integration: `4f5e76263b59a14251480d2510e52040f8ee0209`.
- Pure calculation/testability: `c04e902fb200f29cabf031cc277508aac9d1bac5`.
- Focused tests: `48b731a10216acefb94eb2695f31dd739752174d`.
- Android CI run `44398750532` targets exact HEAD `48b731a10216acefb94eb2695f31dd739752174d`; it was pending when this note was written. No test/build pass is claimed yet.
- Branch integrity check: Main-dev HEAD `48b731a10216acefb94eb2695f31dd739752174d`; main remains `11478fde56f1817cb777b296e60907359086f383`. Only Main-dev was modified.
- Remaining: verify CI, inspect any failures, run APK build if workflow supports it, and complete physical-device visual checks. Continue porting the rest of LastWave's source-backed Statistics behavior only where Mylo's stored aggregates can support it exactly; do not port fabricated session/time fallbacks.


## 54. Source-backed hourly listening timeline port (2026-11-19)

### LastWave source audit
- Re-read LastWave Statistics source at `last-wave` head `d0dd73755bfe640c515502751f440a32e57506d5`: `ListeningStatsScreen.kt`, `ListeningStatsViewModel.kt`, `ListeningStatsModels.kt` and `ListeningStatsComponents.kt`.
- The screen's timeline offers time-range and metric controls, but its ViewModel attributes each track's full accumulated duration to its last-played timestamp, infers session counts from play counts, and falls back to synthetic 210-second durations when exact durations are absent. Its session/habit calculations also infer durations and active-day totals. These values are not equivalent to a sampled listening timeline and are not copied.
- Mylo's `ListeningStats` records actual audible milliseconds into 24 hourly buckets; `ReplaySummary.hourOfDay` is already merged for the selected Replay period. This is a more accurate source for a Mylo-native hourly timeline than reusing LastWave's approximations.

### Mylo implementation
- Added `app/src/main/java/com/music/bitchord/ui/replay/ListeningRhythm.kt`: a Material 3 24-hour bar chart, actual peak hour and duration, plus accessible hour/value description. It normalizes old/malformed bucket lists to 24 entries and clamps negative values.
- Integrated the card into `ReplayScreen.kt` only when `heading == "Statistics"`; ordinary Replay/Library presentation remains unchanged. It follows the selected Replay period because it consumes that period's existing `ReplaySummary.hourOfDay`.
- Added `app/src/test/java/com/music/bitchord/ui/replay/ListeningRhythmTest.kt` for 24-bucket peak calculation, short/negative input normalization and empty data.
- No inferred plays, sessions, fabricated durations, remote Last.fm counts or new persistence were introduced.

### Commit and verification
- Implementation commit: `8405db11952bc747cd1f7343155bc1cebe150aa8` (`feat(stats): add real hourly listening rhythm`).
- Android CI run #1172 / ID `44400935268` targets this exact implementation commit and was **in progress** at documentation time. No pass is claimed; inspect its jobs and logs before further release/device claims.
- Earlier concentration test run #1171 / ID `44398750532` completed successfully on exact head `48b731a10216acefb94eb2695f31dd739752174d`.
- Physical-device verification has not been performed. Keep the hourly chart's visual spacing/readability and TalkBack output on the device acceptance list.
- Branch boundary: continue only on `Main-dev`; `main` remains `11478fde56f1817cb777b296e60907359086f383`. No branch creation or merge.


## 55. Hourly timeline period-integrity correction (2026-11-19)

- Rechecked the actual `MergedBucket.add(bucket, range)` path in `data/stats/ListeningStats.kt`. Weekly Replay correctly filters track/day aggregates, but the persisted hourly buckets are monthly aggregates and are intentionally not merged for `THIS_WEEK`; therefore `ReplaySummary.hourOfDay` is empty for a non-empty weekly summary.
- Corrected `ListeningRhythmCard` to compare the 24-hour bucket total with the selected period's actual `summary.totalMs`. It renders the chart only when the hourly buckets account for the full period total. When they do not (weekly or legacy data without hourly buckets), it explicitly says the hourly breakdown is unavailable rather than falsely claiming no listening occurred.
- Added a regression test for non-zero period listening with all-zero hourly buckets. Existing exact-24-hour, malformed/negative and true-empty cases remain covered.
- Correction commit: `faa9d05daeba10604df27a2b55cbb1c8c416c719`.
- CI run #1172 / ID `44400935268` still targeted the earlier implementation SHA `8405db11952bc747cd1f7343155bc1cebe150aa8` and was in progress at this update. It does **not** verify this correction commit; obtain fresh CI evidence for `faa9d05`. No pass is claimed.
- Continue only on `Main-dev`; `main` remains untouched at `11478fde56f1817cb777b296e60907359086f383`.


## 56. Similar-artist generation source port (2026-11-19)

### LastWave behavior traced
- `GenerateRepository.fetchSimilarArtistTracks` uses Last.fm `artist.getsimilar` to discover similar artist names, then combines those with YouTube Music discovery and filters to playable tracks.
- Mylo already has a Last.fm signed API client and a YouTube Music artist-page resolver. Its old `SIMILAR_ARTISTS` mode only opened the exact artist's page, so it did not reproduce the source's similar-artist discovery behavior.

### Mylo adaptation
- Added `LastFM.getSimilarArtists` and a parser that handles array or single-object responses, deduplicates names case-insensitively, and returns an empty result for malformed payloads.
- Updated `PlaylistGenerationRepository` similar-artist mode to request up to eight similar artists when Last.fm API credentials are configured, resolve each name through Mylo's YouTube Music artist pages, and interleave their playable `Song` pools.
- If Last.fm is not configured, the graph lookup fails, or similar artist pages produce no playable tracks, generation falls back to the previous exact-artist page behavior. Cancellation is rethrown; individual artist-page failures do not cancel the other pools.
- Added parser tests for multiple/single artist payloads, case-insensitive duplicates and malformed responses.
- Mylo's existing candidate dedupe, optional exclusions, saved-playlist freshness ordering and single save/navigation commit remain unchanged.

### Commit and verification
- Implementation commit: `441aa6720ecaaca78ea599449d24f36d3d9d613f`.
- Android CI run #1174 / ID `44401745427` targets this exact commit and was pending when this note was written. No pass is claimed.
- Hourly timeline period-integrity correction commit: `faa9d05daeba10604df27a2b55cbb1c8c416c719`; its CI run #1173 / ID `44401577805` was still in progress. The later #1174 run should be checked for full verification of all code at its head.
- Continue only on `Main-dev`; `main` remains `11478fde56f1817cb777b296e60907359086f383`.


## 57. Personalized weighted Mix port (2026-11-19)

- Traced LastWave `GenerateRepository.fetchMix`: primary YouTube discovery/radio and home-feed sources, local listening/library seeds, source weights (4/3/2/1), highest-weight duplicate retention, and a three-per-artist diversity pass before fallback.
- Mylo's old Mix only concatenated an optional seed radio, Quick Picks and Home shelves. It did not seed radio from local Replay/history or rank sources by trust/priority.
- Updated Mylo Mix to seed up to six YouTube Music radio pools from the selected seed, actual Replay top tracks, local recent history, and (when signed in) account history/liked songs. It blends radio + Quick Picks at weight 4, local/account taste at weight 3, and Home shelves at weight 1.
- Added pure `PlaylistGenerationRules.weightedBlend`: duplicate title/artist identities keep the highest source weight, candidates are ordered by weight, artist output is capped at three where possible, and the cap is relaxed only to fill a thin pool. Existing downstream explicit exclusions, saved-playlist freshness ordering and single playlist save remain in control.
- Added unit coverage for weight precedence, cross-video duplicate identity, artist diversity and thin-pool fallback.
- Implementation commit: `067e3f66d36e08d02fa77fd595d5ea0b937dbe97`; follow-up test/label cleanup and this documentation update are in the current commit.
- Earlier CI #1174 / ID `44401745427` was pending for similar-artist commit `441aa6720ecaaca78ea599449d24f36d3d9d613f`; the latest combined code must be checked on its own CI head. No pass is claimed.
- Continue only on `Main-dev`; `main` remains `11478fde56f1817cb777b296e60907359086f383`.


## 58. Taste-profile recommendation port (2026-11-19)

- Traced LastWave `TasteProfileProvider` and `GenerateRepository.fetchRecommendations`: recommendations are seeded from real listening, account history/likes, and connected Last.fm taste; YouTube radio/discovery and Home/Quick Picks expand those seeds; heard tracks are filtered before the result is curated.
- Mylo's previous Recommendations mode returned only YouTube Music Quick Picks. It now reuses a shared `loadTasteSignals` path for Mix and Recommendations: local Replay top tracks, local recent history, signed-in YouTube Music history/likes, and (when configured) the user's Last.fm overall top tracks.
- Last.fm tracks are resolved through Mylo's YouTube Music song search using normalized title+artist identity. Unresolved matches are skipped rather than saving a guessed/wrong rendition.
- Recommendations fans out up to six distinct taste seeds into YouTube Music radio, blends radio/Quick Picks with Home fallback, applies a stricter two-per-artist cap, and prefers candidates absent from local Replay, local recent history, YT Music history/likes and resolved Last.fm top tracks. If fewer than five fresh candidates exist, it falls back to the full curated candidate pool; Never Heard remains the strict mode.
- Mix and Recommendations now share the same bounded taste-signal and radio-seed helpers. Cancellation is propagated; unavailable optional sources degrade to empty pools instead of failing the whole generation.
- Implementation commit: `ab4d133e2fa287963f7f3d6f4ea75cfaa8757446`.
- CI run #1177 / ID `44402436004` was pending on the earlier refactor commit `bc66572ae31cda7975c36835409e122e27538ad8`; it does not verify the Last.fm seed addition. Await CI on the latest combined head; no pass is claimed.
- Continue only on `Main-dev`; `main` remains `11478fde56f1817cb777b296e60907359086f383`.


## 59. Saved-playlist Similar regeneration port (2026-11-19)

- Traced LastWave `GenerateRepository.fetchTasteMixForPlaylist`: three source-artist seeds, four source-track seeds, Last.fm similar artists/tracks and deep cuts, YouTube radio, optional tag discovery, source-track exclusion, progressive artist-cap relaxation, and a 20-track minimum (bounded by requested count).
- Mylo's old `generateSimilar` used only three YouTube radio seeds, then Quick Picks/Home, and accepted as few as five tracks. It did not explore the source playlist's artist graph or widen discovery progressively.
- Reworked Mylo regeneration to use up to three source artists and four source tracks; it combines YouTube radio, Last.fm similar-artist discovery resolved to playable YouTube Music artist pages, direct source-artist pages (with original tracks removed), Quick Picks/Home fallback, and Mylo mood/genre shelves as the final widening source.
- Candidate pools are weighted and source playlist tracks are excluded by both video ID and normalized title/artist. Artist diversity starts at three per artist and relaxes only when required to fill the target. It now requires `min(20, requestedCount)` tracks, matching LastWave's minimum-size behavior without changing Mylo's playlist persistence/navigation flow.
- Extracted `artistPagePools` so Similar Artists and Similar Playlist share the same safe artist-page resolution and per-artist failure handling.
- Corrected Mix/Recommendations weighted curation to cap against the actual requested playlist size, not twice that size; this prevents the later save truncation from accidentally bypassing artist diversity.
- Added a unit test proving the artist cap holds when enough distinct artists are available. Existing tests cover progressive thin-pool fallback.
- Implementation commit: `f269fe18eacbfb93f5f1992423a40a7257a7df05`.
- Latest CI must be checked on the current branch head; no pass is claimed. Previous run #1178 / ID `44402595986` targeted earlier Last.fm taste-seed commit `ab4d133e2fa287963f7f3d6f4ea75cfaa8757446` and was pending when this implementation landed.
- Continue only on `Main-dev`; `main` remains `11478fde56f1817cb777b296e60907359086f383`.


## 60. Global Start Mix with this Song — LastWave flow port (2026-11-19)

### LastWave flow traced
- The actual global song-menu entry is `ui/common/TrackContextMenuSheet.kt`: the shared menu shows Start Mix for playable song targets. Its `StartMixMenuViewModel.startMix` calls `GenerateRepository.fetchSimilarTracks(track, artist, limit = 30, seedVideoId)`, converts the results to playable tracks and calls `MusicPlayer.replaceUpcoming`. It does not save a playlist itself.
- `GenerateRepository.fetchSimilarTracks` expands the seed through YouTube Music radio, branches from a related result when the first pool is thin, falls back to Last.fm `track.getsimilar`, excludes the seed and edits/remixes/covers, deduplicates by normalized title and progressively relaxes artist caps.
- A separate generator-launch path also exists: `MixLauncher` publishes a pending `MixSeed`; `GenerateViewModel.init` consumes it, selects Similar Tracks, fills the seed fields, uses a 28-track target and starts generation. That route saves through the Generator's `PlaylistRepository`, then replaces upcoming playback. It is distinct from the shared context-menu action.
- Mylo intentionally unifies the global action with its existing Generate and local-playlist architecture: generate the actual similar-track pool, persist it through `LocalPlaylistStore`, then queue it through Mylo's queue coordinator. This preserves Mylo's required saved-playlist lifecycle while retaining LastWave's seed discovery and current-track continuity.

### Mylo implementation on Main-dev
- Added **Start Mix with this Song** to the shared `SongActionsSheet`. It is available for online, playable audio songs with a nonblank video ID, title and artist; local files, videos and incomplete rows do not show it.
- The action closes the song menu, opens Mylo's existing Scrobble Generate tab, and calls `ScrobbleGenerateViewModel.startMixWithSong`. No new navigation host, player owner or playlist store was introduced.
- `PlaylistGenerationRepository` now expands the selected seed through Mylo YouTube Music radio, branches from a related result if the first pool is thin, then uses Last.fm similar-track discovery as a secondary fallback when configured. Last.fm names are only accepted after YouTube Music search returns an exact normalized title/artist identity; no guessed recording is saved.
- Similar-track curation removes the seed and title variants, deduplicates normalized titles, applies LastWave's initial one-per-seed-artist / two-per-other-artist limits, then relaxes to two/three and finally the full unique pool only when needed to fill the requested count.
- Radio fan-out now isolates individual failures while preserving coroutine cancellation. The seed validator is shared by UI and repository and rejects missing IDs, video items, local files and incomplete metadata.
- A successful generation is saved through the existing `LocalPlaylistStore` first. Only then does the ViewModel publish a one-shot queue handoff. Mylo appends the generated songs after the existing user queue (before AutoPlay), preserving the current track and existing queued items. If no track is playing, the queue helper starts the generated mix rather than leaving a saved-but-unplayed queue.
- The saved-playlist event uses replay to survive a collector starting after a fast generation; the activity deduplicates the playlist ID so recomposition/event replay cannot navigate twice. Queue handoff has its own pending state and is consumed once.
- Generation already uses the cancellation token and `generateAndCommit` active-context guard. Cancellation before the durable save does not emit a playlist event or queue handoff. Save failures remain surfaced through the existing Generate error state.

### Files changed
- `app/src/main/java/com/music/bitchord/ui/components/SongActionsSheet.kt`
- `app/src/main/java/com/music/bitchord/MainActivity.kt`
- `app/src/main/java/com/music/bitchord/ui/scrobble/ScrobbleGenerateViewModel.kt`
- `app/src/main/java/com/music/bitchord/data/generate/PlaylistGenerationRepository.kt`
- `app/src/main/java/com/music/bitchord/data/scrobbling/LastFM.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/test/java/com/music/bitchord/data/generate/PlaylistGenerationRulesTest.kt`
- `app/src/test/java/com/music/bitchord/data/scrobbling/LastFMSimilarTracksParsingTest.kt`

### Tests and verification
- Added pure-rule tests for valid/invalid Start Mix seeds, source-seed exclusion, title/edit deduplication and progressive artist-cap relaxation.
- Added Last.fm parser tests for array and single-object responses, incomplete rows and malformed payloads.
- Corrected the pre-existing Kotlin compiler error in `weightedBlend`: the candidate loop now returns to its actual `candidateLoop` label rather than the unresolved `forEach` label.
- Implementation commit: `4dee0d8ae65f2438ac0b9be603db5c86934c8dbf`.
- Follow-up queue-idle behavior and this documentation update are recorded in the next commit.
- CI #1179 / ID `44402919408` targeted `f269fe18eacbfb93f5f1992423a40a7257a7df05`, not the later code. Its Android job failed at the unresolved label above; web and desktop jobs passed. Subsequent push-triggered runs were created: #1180 / ID `44420602893` targets `4dee0d8ae65f2438ac0b9be603db5c86934c8dbf` and is still in progress (Android unit tests running; desktop cancelled by concurrency); #1182 / ID `44420807573` targets `e27d4cb2c2c329c36710a5c877bf4975bfea4914`, the latest feature-code commit, and is pending behind the earlier run. Neither is a pass yet, so this feature remains **CI-unverified**.
- Local Gradle execution is unavailable in this session; the container cannot resolve GitHub to obtain the repository. No local build/test pass is claimed.
- Device verification: not performed.
- Branch integrity at this update: `Main-dev` is the only modified branch; `main` remains `11478fde56f1817cb777b296e60907359086f383` and `last-wave` remains `d0dd73755bfe640c515502751f440a32e57506d5`.

### Remaining follow-up
- Obtain a fresh Android CI run for the exact latest `Main-dev` head and fix any new compiler/test failures.
- Verify on device: global action availability across list/player song surfaces, generation progress/error/cancel, playlist persistence after relaunch, current-track continuity, queue ordering, idle-player behavior and Listen Together queue limit.
- Continue auditing the next unfinished LastWave behavior only after this implementation is compile/test verified.


### Follow-up hardening — bounded matching and controller readiness (2026-11-19)
- Limited Last.fm-to-YouTube exact track resolution to five concurrent searches, matching the bounded-concurrency approach used by LastWave's repository and avoiding a burst of dozens of simultaneous catalogue searches.
- The Start Mix queue handoff now remains pending until Mylo's playback controller exists. Since it is a StateFlow, the collector receives the pending saved playlist when the controller attaches; it is consumed only when the queue helper can act.
- This is a safety refinement to section 60, not a new generation or playlist architecture.


## 61. Start Mix locale CI failure — diagnosis and correction (2026-11-19)

- Inspected Android CI run #1182 / ID `44420807573`, which tested feature-code commit `e27d4cb2c2c329c36710a5c877bf4975bfea4914`.
- Web tests/typecheck and desktop compile/tests passed. Android compilation reached unit tests; the failure was not a Kotlin compile error in Start Mix.
- Root cause: the new base resource key `start_mix_with_song` existed only in `values/strings.xml`. `LocaleStringsTest` requires every translatable base key in every app-supported locale and failed for all 16 supported non-English locales.
- Added translated `start_mix_with_song` resources to `values-de`, `values-es`, `values-fr`, `values-he`, `values-hi`, `values-id`, `values-it`, `values-ja`, `values-pl`, `values-pt`, `values-ru`, `values-th`, `values-tr`, `values-vi`, `values-zh` and `values-zh-rTW`. These cover the locale tags used by `SUPPORTED_LANGUAGES`; Simplified/Traditional Chinese follow the existing `LocaleStringsTest.tagToQualifier` mapping.
- Verified by fetching all 16 supported locale resource files from the updated `Main-dev` branch and confirming each contains `start_mix_with_song`. This is source-level verification only; the Android test suite must still rerun.
- CI run #1182 / ID `44420807573` remains failed for the earlier feature SHA. Pushes for the locale correction created run #1202 / ID `44423376514`, targeting `d6e9b9a2f08db06c409e4ed7bad1940050336211` (the complete translation source tree immediately before the docs-only commit `6daf82d`). At the time of this note, #1202 was pending and had no jobs listed yet. The current branch head is `6daf82d`; only the context Markdown differs from #1202's tested tree. Inspect #1202's Android, desktop and web jobs before claiming CI green.
- Branch rule unchanged: only `Main-dev` is modified. Do not create branches, merge, or touch `main` or read-only `last-wave`.


## 62. Start Mix CI #1202 duplicate-resource correction and integration review (2026-11-19)

### Live repository / branch verification
- Rechecked live refs before editing: `Main-dev` was `d5d3907776b25e5912de94ef52cf92c321d77f18`; `last-wave` remained `d0dd73755bfe640c515502751f440a32e57506d5`; `main` remained `11478fde56f1817cb777b296e60907359086f383`.
- Work remains scoped to `Main-dev`. LastWave and main were read-only.
- Read the current context record through its Start Mix sections and refreshed the source files listed below; older snapshots elsewhere in this living log are historical, not live branch truth.

### CI #1202 exact failure
- Run #1202 / ID `44423376514` tested `d6e9b9a2f08db06c409e4ed7bad1940050336211` and completed with overall `failure`.
- Android unit-test job failed during Android resource merge, before the unit-test suite could execute. The exact error was duplicate `String/start_mix_with_song` declarations in `values-de`, `values-es`, `values-fr` and `values-he`.
- Desktop compile/tests passed; web tests/typecheck passed.
- Root cause: these four locale files already contained the key, and the locale correction added an identical second declaration. The previous section 61's "each contains the key" check did not check uniqueness. This is a correction to that earlier verification claim.
- Live Main-dev inspection found exactly two identical declarations in each of those four files and exactly one declaration in each of the other 12 supported locale files.

### Corrective change
- Removed only the second identical `start_mix_with_song` declaration from `values-de/strings.xml`, `values-es/strings.xml`, `values-fr/strings.xml` and `values-he/strings.xml`.
- All 16 supported locale directories must contain exactly one declaration after this change. No translation text, base string, UI code or feature behavior is changed.
- This is a resource-merge fix, not evidence that Android unit tests pass. A new CI run is required.

### Start Mix source review — confirmed so far
- `SongActionsSheet.kt` exposes the action only when a callback exists, the row is online, the video ID/title/artist are nonblank and the item is not a video.
- `ScrobbleGenerateViewModel.startMixWithSong` validates the seed again and rejects a second start while a generation job is active. It creates a Similar Tracks request with the selected `Song` as the seed.
- The repository validates the seed again before discovery. Its existing generation pipeline remains separate from persistence/player writes; the ViewModel saves with `LocalPlaylistStore` and publishes the queue handoff only after save returns.
- `MainActivity` keeps the pending handoff in StateFlow until a playback controller is available, then consumes the matching playlist ID and passes the songs to the existing queue helper.
- The queue helper is called with `startIfIdle = true`; it starts the generated songs if the controller has no media items, otherwise it inserts into the user queue. Listen Together limits are applied by the shared queue helper.
- Found stale comments in the queue-handoff block saying it "never starts playback when idle" and in the helper saying Start Mix cannot start from idle. Those comments contradict the actual `startIfIdle = true` call and helper behavior; correct them in a later grouped code/doc change after the surrounding queue behavior has been fully traced. No runtime behavior was changed in this pass.

### Verification still required
- Re-run Android CI after this resource fix; inspect all jobs and logs. Do not call #1202 green.
- Continue the queue-ordering and AutoPlay insertion review, controller lifecycle/recreation, handoff consumption race, save failure/cancellation boundaries, duplicate saved-playlist navigation, Listen Together limit and playback continuity review.
- Existing pure-rule tests cover seed eligibility and similar-track curation; they do not yet prove ViewModel lifecycle, durable save/queue handoff or physical playback.
- Device verification remains outstanding for every item in section 60. No device result is claimed.
- Next: inspect `queueSongs`, `QueueCoordinator.findUserQueueInsertionIndex`, `LocalPlaylistStore.savePlaylist`, the saved-playlist collector/deduplication and the ViewModel cancellation/reset path; add targeted regression coverage only for verified gaps.

### Branch integrity
- Expected resulting branch: `Main-dev` only. `last-wave` remains read-only; `main` must remain untouched.


## 63. Start Mix regression review — queue semantics and CI verification (2026-11-19)

### CI #1203 — exact correction commit verified
- Run #1203 / ID `44425520017` tested exact commit `f658abb0caf58bef88ea6d2a75dca1ded1533b4d`.
- Overall conclusion: **success**.
- Android unit tests: passed.
- Desktop compile and unit tests: passed.
- Web unit tests and typecheck: passed.
- This resolves the duplicate-resource failure from #1202. CI now verifies the locale cleanup and all code present at the tested SHA. It does not provide device verification.

### Queue behavior traced
- `QueueTimeline.findUserQueueInsertionIndex` inserts Add to Queue after all existing `USER_QUEUE` entries and before the first non-user entry (normally `CONTEXT` or `AUTOPLAY`). Existing `QueueCoordinatorTest` cases cover both a populated user queue and the no-user-queue insertion point.
- The Start Mix handoff uses the existing `queueSongs(playlist.songs, false, true)` helper. With a non-empty local player timeline, it inserts the generated songs without replacing the current item; in Listen Together, `PartySync.onLocalIntent` publishes local queue edits to the server and clamps the published queue to the 25-upcoming-track limit.
- With an empty local player timeline, the helper calls the normal `play(songs, 0)` path. Outside a party, this starts the generated list through the existing context-queue path.
- **Confirmed Listen Together edge requiring follow-up:** the normal `playFrom` party branch builds its party timeline from only the selected song plus the already-existing party upcoming queue (`QueueCoordinator.buildPartyPlaybackQueue`). It does not pass the remaining songs from the generated list. Therefore Start Mix from an empty local timeline while in an unlocked party can start the first generated recommendation but does not include the rest of that generated mix in the party queue. The non-empty local-timeline path does publish appended songs and applies the 25-item limit. This discrepancy must be resolved or explicitly accepted before Start Mix is called fully integrated. Do not bypass PartySync or write the party queue directly without preserving its sequence/host-control rules.
- Corrected two stale MainActivity comments that claimed the idle path never starts playback. The comments now describe the normal empty-player path and distinguish Listen Together's server-owned queue behavior. No runtime behavior changed.

### Persistence and cancellation boundary traced
- `LocalPlaylistStore.savePlaylist` creates a stable local playlist ID, persists the complete encoded list through SharedPreferences `commit()` on IO, and only then updates its StateFlow. `init()` reloads that JSON on application startup. Save failure throws before publishing the new in-memory list.
- Store mutations run under a Mutex and `NonCancellable` block. Once the store mutation begins, a cancellation can arrive while the durable write is in progress; the store still completes its atomic write. The caller's cancelled context may then stop the ViewModel from publishing the saved-playlist event and queue handoff, leaving a durable playlist without automatic playback/navigation. The current `generateAndCommit` test suite covers cancellation before commit, but not cancellation during/after a durable store commit. Add a focused regression test or explicitly define this post-commit cancellation behavior before changing it.
- The ViewModel's generation token prevents a stale/cancelled generation from publishing UI events after the token changes. `cancelGeneration` clears the pending queue intent, increments the token and cancels the job. Save errors are caught and exposed through the existing Generate error state.
- The StateFlow queue handoff is consumed once a controller is observed, before the queue helper's own launched coroutine confirms that it applied the queue. If the host guard rejects the action or the controller disappears in that gap, the handoff may be consumed without a queue mutation. Host rejection is expected to show the host-only notice; controller-disconnect timing remains an untested lifecycle edge.

### Remaining blockers before moving on
- Resolve/test the Listen Together empty-timeline Start Mix behavior above, including party queue ordering and the 25-upcoming limit.
- Add targeted tests for cancellation during durable save and for queue-handoff/controller lifecycle if the implementation can be extracted into testable helpers.
- Verify duplicate taps, screen dismissal during generation, activity recreation, playlist persistence after process relaunch, current-track continuity, AutoPlay ordering, host-only refusal and the party path on a real device.
- Keep all these separate from automated CI results. CI #1203 is green; physical-device verification remains outstanding.
- Do not start the next LastWave feature until the Start Mix party-path gap and the required device checks are addressed or explicitly accepted.

### Branch integrity
- Current base for this review: `Main-dev` `f658abb0caf58bef88ea6d2a75dca1ded1533b4d`. `last-wave` remains read-only at `d0dd73755bfe640c515502751f440a32e57506d5`; `main` remains untouched at `11478fde56f1817cb777b296e60907359086f383`.


## 64. Deep UX/UI audit — Generator and Playlists vs `last-wave` (2026-11-19)

### Exact source snapshot and branch boundary
- Target branch remains `Main-dev`; current reported target head before this audit: `786599b99cb3da6cdbec40c38adc9c674a1c1d19`. Refresh before any code write.
- Reference branch remains read-only: `last-wave` = `d0dd73755bfe640c515502751f440a32e57506d5`.
- GitHub compare (`last-wave` base, `Main-dev` head): diverged; Main-dev is 160 commits ahead and 27 behind; merge base is `11478fde56f1817cb777b296e60907359086f383`. This confirms a selective port is required; do not merge either branch wholesale.
- LastWave UI sources inspected:
  - `app/src/main/java/com/lastwave/app/ui/generate/GenerateScreen.kt` blob `06dbba0c77d1e06d02d60abfd98455f13d22f278` (420 lines).
  - `.../ui/generate/GenerateViewModel.kt` blob `74b283322be32a9a17be644a3679f7ee76fd8398` (357 lines).
  - `.../data/generate/GenerateRepository.kt` blob `e81bac3a8c938402931225e5c8f5a7cbda1645df` (1,715 lines).
  - `.../ui/playlist/PlaylistScreen.kt` blob `2bea1f6d4f9ccc9f08164b85a7a95a485d69fa56` (694 lines).
  - `.../ui/playlist/PlaylistViewModel.kt` blob `0fe9109f3a0cce5cd3e23d90f6822e13e0678162` (903 lines).
  - `.../ui/playlist/PlaylistDetailScreen.kt` blob `54712d5106f9a244677c3679373609948ddf110b`.
  - `.../ui/playlist/PlaylistComponents.kt` blob `80805ea8cef2911ae28593e5b824e9a575dc7f19`.
  - `.../data/playlist/PlaylistRepository.kt` blob `37bd36c5d269b494aafd8c536fc54eac50f3f5a5` (704 lines).
  - LastWave design rules: `DESIGN.md` blob `e6a058245f67fb0a04eb180ab467b2b8369c2e2d`; polished/minimal hierarchy, content-first, 4dp spacing rhythm, use theme tokens, all loading/empty/error/long-text/RTL/large-font/landscape states designed, touch targets >=48dp.
- Current Main-dev UI/data sources inspected:
  - `app/src/main/java/com/music/bitchord/ui/scrobble/ScrobbleContent.kt` blob `9ab870bd3329c0bbe15682b52e4e5a237f1dcb43` (1,211 lines).
  - `.../ui/scrobble/ScrobbleGenerateViewModel.kt` blob `714589ff7a2d4f1977f96c8b4dc93dddbccc8a52`.
  - `.../data/generate/PlaylistGenerationRepository.kt` blob `7fa7ac842c13b33b2d101f8b082b20e57b39f8c3`.
  - `.../data/spotify/LocalPlaylistStore.kt` blob `15eda239dfe96f4c94553cb4f779dae1faf5acae`.
- The UI comparison below is source inspection, not screenshot/device verification.

### Generator — what LastWave actually presents
- Nine modes are presented as a vertically composed expressive selectable group. Each mode has a distinct icon badge, title, explanatory subtitle, selected treatment and press feedback. The selected mode reveals its own options section; the user is not required to infer mode behavior from a row of compact chips.
- The options surface adapts to the mode:
  - Top and Library: six period choices (overall, 12 months, 6 months, 3 months, 1 month, 7 days).
  - Recent, Mix, Recommendations and Never Heard: explicit explanatory helper text.
  - Similar Tracks: track name + artist inputs, search action, and a top-tracks shortcut; search results are selectable.
  - Similar Artists: artist query/search and selectable artist suggestions.
  - Tag: free-text tag plus 12 genre quick chips.
  - Recommendations: fixed count is explained; other modes expose a 5–35 track slider.
- Generation state appears as a dedicated expressive progress card/message. Errors are a distinct error-container card with icon and dismiss action. On success, the ViewModel emits navigation to the newly saved playlist rather than leaving the result as an ambiguous inline status.
- LastWave's UI mode labels/descriptions and mode-specific controls are part of the UX contract, not decoration. Its repository/VM also own source blending, request deduplication, candidate precheck, artist caps, playable-ID pinning, exclusions and mode-specific fallback behavior. These algorithms must be compared independently; do not replace Mylo's existing repository just to reproduce the appearance.

### Generator — confirmed Mylo gaps and existing strengths
- Mylo already exposes the same nine broad modes and has meaningful per-mode behavior in `PlaylistGenerationRepository`; preserve it rather than duplicating LastWave's generator engine.
- Mylo's mode chooser is a horizontally scrolling row of `FilterChip` controls with one shared mode description. It lacks LastWave's expressive icon/title/subtitle selection rows and clear selected-mode hierarchy. This is a confirmed presentation gap.
- Mylo's TOP period selector exposes four Replay periods (All time / This year / This month / This week), not LastWave's six YT Music/Last.fm windows. Do not add six labels unless Mylo's underlying data sources can truthfully calculate those periods; map the actual supported ranges first.
- Similar Tracks currently has a useful Mylo-specific path: the current playing song can be selected, and a debounced YouTube Music song search produces up to six selectable seed results. This differs from LastWave's text + artist search and top-track shortcut. Keep the working YT Music identity/search path; evaluate adding artist disambiguation and top-track suggestions without creating a second search pipeline.
- Similar Artists and Tag are currently plain text fields. The Tag flow has no visible quick-select genre chips; Similar Artists has no visible result/suggestion selection flow. LastWave provides selectable artist suggestions and 12 tag chips. These are confirmed discoverability/interaction gaps.
- Mylo's MIX exposes an optional now-playing/search seed; LastWave's regular Mix options are only explanatory text and its separate Start Mix launch path uses `MixLauncher`. Keep Mylo's intentional seed behavior unless the actual generation contract proves it wrong.
- Mylo's Never Heard mode already exposes three opt-in exclusion toggles (saved playlists, liked tracks, library) and explanatory text; this is richer than the inspected LastWave screen. Preserve and visually integrate it.
- Mylo already exposes track count 5–35, progress text + Cancel, inline errors, and a saved-playlist result card with a route to Playlists. The presentation is comparatively plain: no dedicated expressive progress/error treatment, and success is a small inline card rather than an obvious next-step transition. Review the actual handoff before changing navigation; do not lose the saved-playlist ID or Start Mix queue intent.
- The current form is one long vertically scrolling page. Options and mode chooser lack the LastWave hierarchy and visual grouping. This is the central confirmed Generator UX incompleteness, even though the generator algorithms are not wholly absent.

### Playlists — what LastWave actually presents and supports
- Playlist list is a full feature surface, not just a saved-items list: expressive header with playlist/track totals, create action, sort selector (newest/oldest/name/track count), search that matches playlist title and track title/artist, generator shortcut, saved/regenerated banner, loading/empty/error states, playlist cards with expandable track previews, selection and multi-delete, and per-playlist actions.
- ViewModel coordinates Room-backed local playlists, remote YouTube Music library playlists, optional Spotify playlist projection, sync state, refresh-on-resume, download status, create/rename/delete, pin/cover, reorder, exports, regeneration, and error/toast feedback. These remote-provider surfaces are LastWave-specific and must not be faked in Mylo.
- Playlist detail is a dedicated high-hierarchy screen: large full-bleed cover/hero with scroll/parallax behavior, playlist metadata, prominent Play action and current-playing state, locate-current-track action, persistent per-playlist sort preference, search/filter, reorder lock, drag reorder with edge auto-scroll, stable displayed-order row keys, and rich track rows/context actions. It handles remote/liked/local playlists differently and routes the Local Songs playlist to a local-music hub.
- Detail overflow actions include playlist management and export; track actions include context menu, track management and ordering. Loading and missing-detail states are explicitly represented. The inspected detail also preserves a cached playlist while detail refreshes.

### Playlists — confirmed Mylo gaps and existing strengths
- Mylo's list currently has a simple header, sort menu, storage/action errors, generation progress/error, title-only search, empty/no-match state and playlist rows. Rows show cover/title/count/pinned state and expose Play plus an overflow menu for Pin, Generate Similar, Move Up/Down, Rename and Delete.
- Mylo's list search only filters playlist titles; it does not search track names/artists. There is no visible create-playlist action, playlist/track total summary, generator shortcut in the list header, multi-select/multi-delete, or expanded track preview. These are confirmed differences from LastWave's playlist-list UX; decide which make sense for Mylo rather than blindly importing provider-specific features.
- Mylo's detail already supports Play, Shuffle, custom cover change/reset, Generate Similar, export, rename/delete, add-track search, duplicate-track prevention, per-track play, Move Up/Down and Remove. Its local store supports pinning and custom playlist ordering. Preserve these existing capabilities.
- Mylo detail is materially simpler than LastWave's: compact 68dp cover/title header and basic action buttons; no large cover-led hero, current-playback indicator/locate action, in-detail search, sort controls, reorder lock/drag gesture, or LastWave-style grouped track rows/context menu. Track ordering is menu-based Move Up/Down rather than drag. This is a confirmed UX gap, not proof that the underlying playlist storage is broken.
- Mylo's list/detail use the existing `LocalPlaylistStore` and `Song` model; LastWave uses Room `PlaylistRepository` and `GeneratedTrack` with remote playlist types. Do not replace Mylo storage/model or introduce a second playlist database as a design shortcut. Reuse Mylo's durable store and playback callbacks.

### Audit conclusion and implementation direction
- The earlier port delivered functional generator and playlist foundations, but the screen-level experience is not a faithful UX port and is visibly under-composed compared with LastWave. The user's correction is valid.
- Priority 1: redesign Generator presentation in Mylo's native Compose/theme language around the LastWave information hierarchy: mode selection rows with icon/title/subtitle; clearly scoped options; mode-specific seed/tag/artist affordances; improved progress/error/success feedback. Preserve Mylo's current generation state, validation, current-song seed, exclusions, cancellation, saved-playlist event and Start Mix behavior.
- Priority 2: redesign playlist list/detail hierarchy. Start with title+track-aware search, totals, a clear Generator entry point, richer playlist cards/expanded previews and empty/loading/error states; then detail hero, current-track/locate affordance, sort/search and safer drag reorder only after mapping Mylo's existing player identity and LocalPlaylistStore mutation semantics.
- Reuse LastWave's design ideas and real interaction patterns, but adapt to Mylo's theme, Compose components, `Song`/local playlist identity, local playback APIs and navigation. Provider-specific Spotify/remote-YT sync, Room entities and Local Songs hub are not assumed in-scope.
- Before implementation, inspect every relevant Mylo caller and existing shared components/tests; search for existing artwork/track-row/menu/dialog patterns to avoid duplicates. Keep the existing Scrobble tab host and player UI unchanged.
- No code was changed in this audit pass. No build, automated test or device visual verification is claimed. The next pass is a focused UI implementation plan and source audit of Mylo playback identity, sort/reorder and playlist store APIs before editing.


## 65. UX implementation — Generator and playlist shelf first slice (2026-11-19)

### Generator presentation changes
- Updated `app/src/main/java/com/music/bitchord/ui/scrobble/ScrobbleContent.kt`:
  - Replaced the horizontal mode-chip picker with vertically composed selectable mode rows. Each of the nine modes now has an icon, title, explanatory subtitle and selected treatment.
  - Added a distinct Options hierarchy with a mode-specific explanation and controls.
  - Kept Mylo's real four Replay periods; no unsupported LastWave-only date windows were invented.
  - Retained the existing debounced YouTube Music seed search and now-playing seed; search results now render with artwork, title/artist and selected feedback.
  - Added twelve tag quick-select chips, clearer artist/tag labels, and track-count range labels.
  - Kept Never Heard's existing opt-in exclusions and Mix's optional now-playing seed.
  - Reworked progress, error and saved-playlist feedback into clearer themed surfaces; added error dismissal to `ScrobbleGenerateViewModel`.
  - Hid the Generate action while generation is active and preserved the existing shared-bounds playlist handoff animation.
- Added `dismissError()` to `ScrobbleGenerateViewModel`; no generator repository, mode algorithm, persistence, cancellation token or Start Mix queue behavior was changed.
- Related code commits:
  - `60ebeaee354ba159626290365d8f69c042dbfb1d` — generator hierarchy and mode-specific controls.
  - `6bcf18c5033f9ed496b29cb0dfcfe9d21f0f3347` — generator error dismissal.
  - `0cce7f27ebf89abe7ac5b4674cf1856361f46b1e` — hide action during generation and preserve shared transition.

### Playlist shelf changes
- Updated `ScrobblePlaylistsContent` in the same `ScrobbleContent.kt`:
  - Search now matches playlist title, track title and artist.
  - Header now reports playlist and track totals and exposes a direct Generate action.
  - Empty state has a Generator entry point; no new storage model or empty-playlist creation path was introduced.
  - Generation progress/error presentation is clearer.
  - Each playlist can reveal a compact three-track preview; tapping a preview track starts playback at that exact playlist index.
  - Existing pin, sort, custom-order, move up/down, rename, delete, generate-similar, storage retry, export and cover behaviors were retained.
- Updated `MainActivity.kt` to route the shelf's Generate action to the existing Scrobble Generate tab.
- Related commits:
  - `13e517856a9b85266a017333f17db6d4f375f868` — playlist shelf search, summary, Generator entry and expandable track previews.
  - `525bf8c4a1ece5664937931e3fa14dfa28685b6b` — wire shelf Generator action to the existing tab host.

### Review and verification status
- Re-read the updated generator/shelf source and the MainActivity call site after the edits. Existing LocalPlaylistStore operations remain the only persistence path.
- Combined commit status query for `0cce7f27` returned no status records at the time checked; no green CI result is claimed for the new UI.
- The user-triggered clean APK build began before these UI commits, so that APK cannot verify this new UI unless the user rebuilt from the later `Main-dev` head.
- Not yet redesigned: playlist detail hierarchy and controls. It still uses a compact cover/title row, basic Play/Shuffle, menu-based track move/remove, and has no in-detail search/sort, current-track indicator/locate action, or drag reorder. These remain the next design slice.
- No physical-device visual verification is claimed. Check compact phone layout, long titles, empty playlist, large font, landscape, light/dark theme, add-track/search states and playlist handoff after the next APK build.
- Latest reported Main-dev commit after this slice: `525bf8c4a1ece5664937931e3fa14dfa28685b6b`. Refresh branch refs before further edits. `last-wave` remains read-only; `main` remains untouched.


## 66. Theme/contrast correction and playlist-detail UX continuation (2026-11-19)

### Context and live refs rechecked
- Re-read the current `SCROBBLE_CONTEXT.md` end-to-end for its governing rules and prior implementation history, including sections 0, 34–41 and 60–65. Older SHA snapshots remain historical; this section records the live refs for this pass.
- Refreshed branch heads before editing:
  - `Main-dev` parent before this pass: `a3628752306e5fe36fdc1ca633822201a315ad4e`.
  - `last-wave` read-only reference: `d0dd73755bfe640c515502751f440a32e57506d5`.
  - `main` untouched: `11478fde56f1817cb777b296e60907359086f383`.
- LastWave detail source rechecked at `app/src/main/java/com/lastwave/app/ui/playlist/PlaylistDetailScreen.kt`; the live Mylo visual source remains `ui/theme/Theme.kt` and `ui/scrobble/ScrobbleContent.kt`.

### Confirmed contrast/theme defects
- Mylo's original `DarkColors` / `LightColors` set only a subset of Material 3 tokens. Generator and playlist surfaces consume `primaryContainer`, `secondaryContainer`, `surfaceContainerHighest`, `errorContainer` and matching content colors, so unspecified tokens fell back to Material 3 defaults rather than Mylo's deliberately monochrome palette. This was a real source-level color drift risk.
- Playlist shelf cards used `Row.background(surfaceVariant)` without a Surface/content-color provider. Playlist and track titles without explicit colors therefore inherited the surrounding content color rather than the intended `onSurface` for the grey card. In dark mode that can render dark/default text against a dark charcoal card. This matches the user's reported black-on-grey contrast symptom.
- The page roots also lacked an explicit `Surface` to establish the background and content color for all plain text on Generator, playlist shelf and detail.
- The latest pre-fix commit `a3628752306e5fe36fdc1ca633822201a315ad4e` had added required `currentSong` and `isPlaying` parameters to `ScrobblePlaylistsContent` but had not passed them from its MainActivity call site. The call site was incomplete and would not compile. This pass wired the actual `player.song` and `player.isPlaying` values.

### Implemented on Main-dev — commit `6f81c08c3c92b7a7d6a51af11c15257f55c41f9a`
- `ui/theme/Theme.kt`:
  - Completed the dark and light Material 3 palettes for primary/on-primary, primary container, secondary/container, tertiary/container, surface/container tiers, inverse surfaces, outline, scrim and error/container pairs.
  - All generator/playlist accent and container tokens are now intentionally monochrome and consistent in both themes. The existing `AccentRed` remains a separate Replay-only accent; no artwork-derived or wallpaper/dynamic color is introduced.
  - Contrast spot-checks for the actual chosen token pairs: dark title/card 17.01:1; light title/card 14.97:1; dark secondary surface 13.31:1; light secondary surface 14.18:1; muted text on dark/light surfaces 8.62:1 / 6.51:1. These are calculated token-pair ratios, not screenshot measurements.
- `ui/scrobble/ScrobbleContent.kt`:
  - Generator and Playlists/detail now each have a page-level themed `Surface` with explicit background and foreground content colors.
  - Playlist shelf cards now use Material 3 `Surface` with explicit `surfaceVariant` + `onSurface`, preventing text color from being inherited incorrectly.
  - Playlist detail has a larger cover-led Mylo-native hero card, explicit title/metadata colors, a current-playing label and a Locate action that scrolls to the current track by exact `videoId`.
  - Added in-playlist title/artist search and view-only sort options (Custom order / Title / Artist). Sorting preserves original track indices; move/remove/play actions still target the original playlist entries. Move Up/Down remains available only in Custom order with no active search, so a sorted/filtered visual index can never be mistaken for a storage index.
  - Current track rows receive a theme-aware selected surface and explicit foreground colors. Search, sort, empty results, menus, progress/error and existing add/remove/export/cover states inherit the explicit page theme.
  - Source scan of this file found no hard-coded black/white/hex colors; remaining background styling uses Material 3 theme tokens.
- `MainActivity.kt`: passed `player.song` and `player.isPlaying` to the playlist shelf/detail host, completing the playback-state connection introduced by the preceding commit.
- Existing LocalPlaylistStore remains the only persistence path. No LastWave Room database, remote playlist type, new queue owner or second theme system was introduced.

### Remaining parity and verification
- LastWave's detail supports drag reordering and remembers per-playlist sort settings. Mylo still uses its already-tested Move Up/Down store operation; the new sort selection is screen-saveable but not yet persisted per playlist. Do not implement drag reorder until the gesture, stable identity and durable move transaction are traced and covered by tests.
- The playlist hero is deliberately Mylo-native and neutral, not a LastWave full-bleed artwork/dynamic-color surface. No dynamic accent/color sampling is used on Generator or playlist detail.
- Android CI run #1204 / ID `44432162937` targets this exact commit. At documentation time Android APK/unit tests, desktop compile/tests and web tests/typecheck were still in progress; no pass is claimed yet. Inspect all jobs and logs before calling it green.
- No emulator or physical-device screenshot/visual verification has been performed. Device review must cover forced Light, forced Dark and System themes; generator mode selection/options/progress/error/success; playlist shelf row title/metadata/action menus; detail hero, long playlist names, current-track locate, search/sort, add-track, empty playlist, dialogs and playback state.
- Do not treat successful compilation as visual acceptance. The user's reported contrast issue remains open for device confirmation even though the source-level inheritance and palette defects are corrected.

### Branch integrity
- Code commit for this pass: `6f81c08c3c92b7a7d6a51af11c15257f55c41f9a`. This context update is a separate Markdown-only follow-up; refresh the live ref before further work.
- `last-wave` remains read-only at `d0dd73755bfe640c515502751f440a32e57506d5`; `main` remains untouched at `11478fde56f1817cb777b296e60907359086f383`.
- Continue on `Main-dev` only. Refresh refs and read this context before the next edit.



## 67. Library UX source-of-truth alignment and handoff refresh (2026-11-19)

### Library sources read and adopted
Read the complete contents of:
- `/Mylo/AGENT-BRIEF.md` — canonical implementation scope, hard rules, statistics/generation behavior, queue option A, accessibility, mandatory acceptance and delivery.
- `/Mylo/mylo-ux-spec.md` — approved v2 UX specification dated 2026-11-19.
- `/Mylo/CHANGE-THEME-TOGGLE.md` — exact Settings-only gradient switch requirements.
- `/Agent skills/Universal Agent Engineering/references/ui-ux-review.md` — whole-journey UI/UX review checklist.
Also inspected `mylo-replay-prototype-v3.html` (758 lines). It contains the approved dark gradient visual language, Statistics concentration/period/list layout, nine mode rows, Playlist shelf, count/period dialog, 62dp FAB, mini player and normal/hub navigation. The prototype's controls and sample track data are explicitly demo-only; its simulated stats/generation/network behaviors must not be ported as real logic. It does not implement the Settings toggle, durable queue or full playlist detail.

### Canonical product requirements now reflected in section 0
- The Library docs are definitive; this context file is the repo-side engineering mirror/history. New agents must open the Library docs themselves rather than relying only on this file.
- The feature scope is Statistics, Generate, Playlists and the durable scrobble queue, using existing Mylo Compose/data/storage. The one permitted Settings change is the Appearance switch “Scrobble gradient theme”; no other shell/player/settings redesign.
- ON is default true and uses the exact approved prototype gradient on the three hub screens only. OFF uses the existing Mylo theme on those screens only. No dynamic artwork colors. The toggle is Settings-only, persisted under `scrobble_gradient_theme` in the existing preferences store, read/write guarded by try/catch, read failure defaults to true, and it must apply on return without Activity/app restart.
- Statistics merge is normalized artist+title, one row per identity, max(local, Last.fm) when connected, local-only when disconnected; apply periods to both sources before merging and use the same rule for time listened/concentration. Five-minute cache, no polling, keep stale content during refresh.
- All nine existing generation modes remain available; Last.fm is optional; Library requires signed-in YouTube Music; count is 5–35/step 5/default 25; cancel stops network work; closing hub cancels generation; success saves then navigates/highlights Playlists; failure saves nothing and keeps retry available.
- Playlist list search is title-only; track-level list search and drag reorder are out of scope. Required shelf and detail actions are defined in the UX spec.
- Durable queue is required. Since no product-owner storage selection is present, use the brief's explicit fallback: a single JSON file written through temp-file-and-rename. Cap 200, preserve order, flush on app start/network availability and after successful scrobble, drop/log permanent failures, and drop/log oldest on overflow.
- Shared UI/UX review must cover the entire journey and all interaction/accessibility/visual states, not just changed composables.

### Important source/code discrepancies to preserve for deliberate review
- Historical section 65 says playlist shelf search was expanded to track title and artist. That conflicts with the current approved spec's title-only playlist search and “track-level search is out of scope.” Do not assume the historical implementation note is still approved. Inspect the current code and report the exact behavior; do not silently remove or extend it. If the product owner wants to retain it, update the Library spec through the owner rather than treating this context file as authority.
- Historical section 66 added in-detail search and sort. The current spec explicitly lists detail actions and says drag reorder is out of scope, but does not clearly enumerate those extra detail controls. Inspect the live code and document whether these are harmless extras or a scope mismatch before changing them.
- Historical section 66 completed Material 3 monochrome theme tokens and page surfaces. The current Library spec now requires the approved gradient as the default ON state and the existing Mylo theme as OFF. These are not interchangeable: preserve the approved gradient palette and implement the Settings preference without recoloring the rest of the app.
- The prototype is not a substitute for the real Mylo data pipeline or the exact merge/cache/cancellation/persistence rules in the Markdown.

### Live repository refs and CI checked
- Repository: `somilkhan/Mylo`.
- `Main-dev` current HEAD before this documentation update: `a3f85753e299d95c60cc478dca876eba461097c0`.
- `main` remains untouched at `11478fde56f1817cb777b296e60907359086f383`.
- `last-wave` remains read-only at `d0dd73755bfe640c515502751f440a32e57506d5`.
- Current context file blob before this update: `8b9ce63b333b4332c03465afada3749c4dda3fa7`.
- Latest UI/theme code commit remains `6f81c08c3c92b7a7d6a51af11c15257f55c41f9a`; the current branch head after that contains documentation-only commits.
- CI run #1211 / run ID `44432162937` completed with conclusion **success** for code commit `6f81c08c3c92b7a7d6a51af11c15257f55c41f9a`:
  - Web unit tests and web workspace typecheck: passed.
  - Android unit tests: passed.
  - Desktop compile and unit tests: passed.
  - Android APK build and artifact upload: skipped (optional build was not requested by this run). No APK was produced by this CI run.
- This CI result verifies that commit only; it does not prove physical-device appearance or the full device acceptance matrix.

### Current implementation checks and remaining work
- `SettingsSheet.kt` at blob `7916a220667ad468401c8442640426462feebe7e` was inspected; the required `scrobble_gradient_theme` key/label was not found. The Settings-only toggle is not yet implemented in that screen.
- `Theme.kt` at blob `0c693ffde840d05737c6b4708d97b05453bf69b5` and `ScrobbleContent.kt` at blob `52235257d3f7ad84546ebf4f0206e9858e4d412b` were checked; the previous implementation is the explicit Material 3/monochrome theme correction from section 66, not yet verified as the approved gradient-on default.
- `ScrobbleManager.kt` at blob `153b488ed0eea8893ffb0e7983de9fd26a569847` was inspected and contains no durable queue implementation. Before adding one, perform a repo-wide search for any existing pending-scrobble persistence/worker/network observer so a duplicate queue is not introduced.
- `MainActivity.kt` at blob `a209acb20614994e255564513146512d14e1c452` passes `player.song` and `player.isPlaying` into the playlist host; the earlier missing-call-site issue is fixed.
- No new APK, emulator screenshot, physical-device visual acceptance, force-close preference persistence test, corrupted-preference test, offline scrobble replay test, or complete nine-mode device test is evidenced by this pass.
- The required queue fallback is clear: use one JSON file with atomic temp-file-and-rename unless the owner explicitly chooses otherwise. No Room.
- The brief's branch wording is internally inconsistent (“Work only on branch Main-dev” and “Create your own feature branch from it”). Existing project direction has been Main-dev-only; this documentation pass remains on Main-dev. Do not create a new branch until the owner clarifies.

### Next agent's exact priority order
1. Refresh refs and read section 0 plus all four Library Markdown docs; open the supplied v3 HTML in a browser. Do not trust old SHAs.
2. Audit existing Settings preference ownership and row patterns, then implement the one Appearance switch exactly as specified; verify default true, ON/OFF hub-only behavior, persistence after process restart and read-failure fallback.
3. Repo-wide audit the scrobble submission path, existing retry/network observers, persistence conventions and tests. Add exactly one durable queue only if no equivalent exists; use JSON temp-file-and-rename, cap/order/flush/permanent-error rules and focused tests.
4. Reconcile the playlist search/detail extras in sections 65–66 against the current spec and live code. Do not silently remove existing behavior; record any product-owner spec correction needed.
5. Run required build and unit tests; inspect CI against the exact resulting SHA. Request/produce a fresh APK and perform the device acceptance matrix (Statistics sources/periods/cache/refresh; all nine Generate modes and cancellation; playlist actions/empty/search; FAB/nav/back/overlay/player/keyboard; toggle in both states and persistence; offline queue restart/flush/no duplicate).
6. Update this context file again with exact commit, file/blob SHAs, test commands/results, APK provenance and device evidence. Do not call the task complete while required verification remains open.
