# 2026-08-29 — Dockerize frontend into start-pfe

| Field | Value |
| --- | --- |
| Date | 2026-08-29 |
| Module | Docker Compose / Nginx / start-pfe |
| Type | Packaging / operator UX |
| Status | Done |

## Context

The Spring Boot backend, FastAPI, Postgres, Ollama, NetFlow collector, capture sensor, and Containerlab lab already start from `./scripts/start-pfe.sh`. The React UI still required a separate `cd frontend && npm run dev` on `:3000`.

## Problem

Jury/operator startup was two-step. Browser JavaScript used `http://localhost:8080` and `http://localhost:8001`. That works with Vite on the host, but a Docker UI must not assume the API lives at those hostnames **inside** the container, and a production-like V1 should be one command.

## Design

Production image is multi-stage: `npm ci` + `npm run build`, then Nginx Alpine serving `dist`. Compose service `frontend` publishes `3000:80` on `pfe_default` only (not `banklab-mgmt`).

Browser → `http://localhost:3000`:

```text
/                     React SPA (try_files → index.html)
/api/analyze-incident  FastAPI :8001 (proxy_read_timeout 330s)
/api/*                Spring Boot :8080
```

Vite DEV still defaults to `localhost:8080` / `:8001`. Production build uses empty base URLs (same origin). No WebSocket/SSE in the app; none configured.

`start-pfe.sh` waits until `http://127.0.0.1:3000/` succeeds. `status-pfe.sh` treats a running Nginx that fails HTTP as DEGRADED.

## Implementation (files)

| File | Role |
| --- | --- |
| `frontend/Dockerfile` | Node 20 build → Nginx 1.27 runtime |
| `frontend/nginx.conf` | SPA fallback + `/api` proxy |
| `frontend/.dockerignore` | Skip `node_modules` / `.env` |
| `docker-compose.yml` | `frontend` service, host `:3000` |
| `frontend/src/api/aiopsApi.ts` | DEV vs production API base |
| `frontend/src/api/aiServiceApi.ts` | DEV vs production AI base |
| `scripts/lib/pfe-env.sh` | `frontend` required; `wait_http` |
| `scripts/start-pfe.sh` | HTTP wait; UI is Docker |
| `scripts/status-pfe.sh` | Container + HTTP `:3000` |
| `scripts/tests/test-frontend-docker.sh` | Static + optional live checks |
| `README.md`, `docs/environment-lifecycle.md`, `docs/demo-scenarios.md`, `frontend/README.md` | Operator docs |

## Tests

| Check | Result |
| --- | --- |
| `bash scripts/tests/test-frontend-docker.sh` | Pass (48) |
| `bash scripts/tests/test-mgmt-network.sh` | Pass (39); frontend not on `banklab-mgmt` |
| `bash scripts/tests/test-dataplane.sh` | Pass (47) |
| `bash scripts/tests/test-demo.sh` | Pass (64) |
| `docker compose build frontend` | Pass |
| `docker compose up -d frontend` then `curl -f http://127.0.0.1:3000/` | 200 |
| SPA `/incidents` | 200 |
| `GET /api/auth/me` via Nginx | 401 from Spring |
| `POST /api/auth/login` + `GET /api/components` via Nginx | 200, 6 components |
| `POST /api/analyze-incident` `{}` | 422 from FastAPI (not Spring 404) |
| `docker inspect` networks | `pfe_default` only |
| `docker compose stop frontend` then `./scripts/start-pfe.sh` | Frontend started; `[OK] Frontend running (Docker) on :3000` |
| `./scripts/status-pfe.sh` | Lab health OK, `frontend HTTP :3000` |
| `docker compose restart frontend` then status | Pass |
| `./scripts/demo/01-baseline.sh` | Pass |

## Result / example

```text
Waiting for frontend HTTP :3000...
[OK] Frontend running (Docker) on :3000
...
Startup complete.
UI:                      http://localhost:3000
```

`pfe-frontend-1` listens `0.0.0.0:3000->80/tcp`. Login through Nginx returned a token; `/api/components` listed six lab nodes.

## Out of scope

- Kubernetes / extra reverse proxy in front of Compose
- Changing Spring/FastAPI route contracts
- Incident, monitoring, or `banklab-mgmt` logic
- Removing `npm run dev`

## Verify commands

```bash
docker compose build frontend
./scripts/start-pfe.sh
./scripts/status-pfe.sh
curl -f http://localhost:3000/
bash scripts/tests/test-frontend-docker.sh
```

Rebuild the UI image after React changes (`start-pfe.sh` is `--no-build`).

## Rapport talking points

- One command now starts the **whole** platform, including the UI.
- The browser never talks to Docker-internal hostnames; Nginx on `:3000` is the only public origin.
- `/api/analyze-incident` is split from Spring `/api` so AI analysis still hits FastAPI with a 330s timeout.
- Frontend stays off the Containerlab management plane; Syslog/NetFlow attachments are unchanged.
