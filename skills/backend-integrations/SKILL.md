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
- On sign-out, cancel immediate and periodic work but preserve the user's explicit sync opt-in. A later successful sign-in may resume only when that preference permits it. Account/profile changes should invalidate old work and schedule a fresh silent import when enabled.
- Never log cookies, SAPISID values, account tokens or imported listening rows. Imported history remains local and must not be exported through backup/device-transfer rules.

Useful regression coverage: `AuthCookieTest`, `AccountSessionsTest`, and `ScrobbleSessionIdentityTest`. Verify Android unit tests and the exact PR-head CI run; device checks are still required for real session expiry, sign-out, account switching and background scheduling.
