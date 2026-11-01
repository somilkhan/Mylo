# MYLO Backend / Integration Engineering

## Listen Together configuration

- Build configuration reads `LISTEN_TOGETHER_SERVER` from `local.properties` or the environment.
- Default fallback is the existing BitChord Render service, `https://bitchord-listen-together.onrender.com`. It is a public service URL, not a credential.
- Client health probe: `GET /healthz`; server clock probe: `GET /api/time`; REST party operations live under `/api/parties`; socket path is `/ws/parties/{code}`.
- The Go implementation and deployment blueprint are under `backend/`; its module path remains the upstream BitChord module path for compatibility.

## Rules

1. Trace failures through client configuration, URL validation, network/DNS/TLS, server health, REST response, WebSocket protocol and playback synchronization. Do not guess which layer failed.
2. A locally unreachable host is not proof the public service is down. Capture status code, timestamp and network context before diagnosing an outage.
3. Do not add credentials to the repository. Treat user/device IDs and party tokens as sensitive runtime data; never log party tokens.
4. If self-hosting, verify `backend/render.yaml` and `backend/README.md`, including single-instance/in-memory party assumptions, health check path, public origin and proxy configuration.
5. Never claim the public service is reachable unless a live request succeeds.

## Verification

```bash
cd backend
go test -count=1 -race ./...
go vet ./...
```

Backend CI runs these checks using the Go version declared in `backend/go.mod`. App-level tests alone do not prove the deployed service is compatible or online.


## YouTube Music session consumers (Android)

- There is one Google/YouTube Music authentication flow: `auth/YtMusicLoginScreen.kt`. Do not add a second login, cookie store, or Scrobble-specific credentials.
- `AuthStore` is the durable encrypted account/profile registry. `Innertube.cookie` and its selected channel scope are the active request context restored by `BitChordApplication`; a non-empty cookie is not proof of a live session.
- Background consumers such as `ScrobbleHistorySyncWorker` must validate the signing-cookie shape with `AuthStore.hasApiSid`, then let the authenticated Innertube request establish whether Google still accepts it. Treat 401/403 and `LOGIN_REQUIRED` as a reconnect state, not an endless retry loop.
- Snapshot the active account, profile/channel identity and a non-reversible cookie fingerprint before a fetch. Revalidate the snapshot and an incrementing session generation atomically before persisting results; account/profile switches, sign-out and sync opt-out must fence in-flight responses. Keep the worker suspended throughout identity mutation, then resume only after the new session is committed.
- On sign-out, cancel immediate and periodic work but preserve the user's explicit sync choice. A later successful sign-in may resume only when the explicit-consent marker and enabled preference both permit it. Legacy builds could set the old enabled flag automatically; migrate an unmarked preference to paused before any worker or scheduler can run, and gate the worker itself on recorded consent. Account/profile changes should invalidate old work and schedule a fresh silent import when enabled.
- Never log cookies, SAPISID values, account tokens or imported listening rows. Imported history remains local and must not be exported through backup/device-transfer rules.

Useful regression coverage: `AuthCookieTest`, `AccountSessionsTest`, and `ScrobbleSessionIdentityTest`. Verify Android unit tests and the exact PR-head CI run; device checks are still required for real session expiry, sign-out, account switching and background scheduling.
- Innertube browse responses can carry a top-level `error` object with HTTP 200. Every browse-response parser (home, continuation, mood/genre, history, and endpoint-specific parsers) must reject that envelope before reading content and preserve its code/status. Repository layers must not swallow transport or API failures into successful empty feeds when the UI or worker needs to distinguish unavailable data from a genuinely empty result. In particular, never turn an API error into an empty-history sync; preserve authentication status so the worker can pause for reconnect.


## Existing history-import provider contracts

- **ListenBrainz:** reuse the existing stored token. Validate it with `GET /1/validate-token` (the response supplies the account username). For bounded incremental reads, use `GET /1/user/{username}/listens` with supported time bounds. For a complete account history, use the authenticated asynchronous export flow (`POST /1/export`, poll the returned export ID, then download the archive); persist the export ID across worker restarts and respect the documented polling/rate limits. Do not repeatedly paginate the entire account history.
- **Last.fm:** `user.getRecentTracks` is an API-key + username method; it does not use the stored session key for authentication. Preserve the returned Unix timestamps, paginate within the documented page limit, and skip now-playing rows that have no listen timestamp.
- Neither provider returns a YouTube Music video ID as a guaranteed playable identifier. Imported rows may be used for statistics, but a queue must resolve title/artist against YouTube Music using `TrackMatcher` and reject ambiguous or unresolved matches. Never put an MBID, recording_msid, title, or synthetic string into `Song.videoId`.
- Keep provider imports explicitly opt-in and separate from outbound scrobbling. Do not expose a provider control until its credentials, pagination/export lifecycle, deduplication, timestamp semantics, and playable-track resolution are implemented and tested.
- For suspending network or database work, never use stdlib `runCatching` directly: it catches `CancellationException`. Use the cancellation-preserving `runCatchingCancellable` helper or explicitly rethrow cancellation before fallback. Cancellation must stop pagination and cancel sibling work; never return partial-success or empty-feed results because the caller cancelled.
