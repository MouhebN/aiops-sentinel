# 2026-08-23 — Camera RTSP image (offline runtime)

| Field | Value |
| --- | --- |
| Date | 2026-08-23 |
| Module | Containerlab camera-01 |
| Type | Bugfix / reproducibility |
| Status | Done |

## Context

camera-01 must expose RTSP `rtsp://172.30.30.40:8554/live` plus HTTP `:8081/health` so Sentinel can run RTSP_HEALTH (and optional HTTP_HEALTH).

## Problem

`start-camera.sh` ran `apk add ffmpeg wget` and downloaded MediaMTX v1.11.3 from GitHub at container start. Alpine DNS/mirrors fail in the lab, so RTSP never came up.

## Design

Bake ffmpeg, iproute2, Python 3.12, and **MediaMTX v1.11.3** into `banklab-camera:local`. `docker build` needs Internet once. Runtime is `--network none` capable.

`start-camera.sh` only starts processes. `stop-camera.sh` stops MediaMTX/ffmpeg only; HTTP stays up so `/health` returns **503** while PING still works.

## Implementation (files)

| File | Role |
| --- | --- |
| `containerlab/services/camera/Dockerfile` | `python:3.12-alpine` + ffmpeg + MediaMTX 1.11.3 |
| `containerlab/services/camera/start-camera.sh` | No apk/wget/curl/GitHub |
| `containerlab/services/camera/stop-camera.sh` | RTSP only; HTTP remains |
| `containerlab/scripts/build-lab-images.sh` | `docker build -t banklab-camera:local` |
| `containerlab/bank-lab.clab.yml` | `image: banklab-camera:local`; start in exec (not background apk) |

## Tests

`docker run --rm --network none -v services/camera:/opt/camera:ro banklab-camera:local`:

| Check | Result |
| --- | --- |
| `ffmpeg`, `/usr/local/bin/mediamtx`, `python3`, `ip` present | Pass |
| `start-camera.sh` has no apk/wget/curl/github | Pass |
| OPTIONS `/live` → `RTSP/1.0 200 OK` | Pass |
| GET `/health` → 200 `stream=available` | Pass |
| `stop-camera.sh` → RTSP closed, HTTP 503 | Pass |
| `start-camera.sh` restores RTSP + HTTP 200 | Pass |

## Result / example

```text
camera-01 ready  rtsp://0.0.0.0:8554/live  http://0.0.0.0:8081/health
RTSP: RTSP/1.0 200 OK
HTTP 503 {"status": "degraded", "stream": "unavailable"}
```

## Out of scope

Syslog, NetFlow, firewall, ATM, Sentinel APIs, topology.

## Verify commands

```bash
cd containerlab
./scripts/build-lab-images.sh
containerlab destroy -t "$(realpath bank-lab.clab.yml)" --keep-mgmt-net
containerlab deploy -t "$(realpath bank-lab.clab.yml)"
```

## Rapport talking points

Lab media endpoints belong in a version-pinned image. Runtime health checks must not depend on Alpine mirrors or GitHub.
