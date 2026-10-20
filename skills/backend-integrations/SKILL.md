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
