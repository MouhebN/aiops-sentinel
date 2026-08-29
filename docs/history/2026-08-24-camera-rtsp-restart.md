# 2026-08-24 — Camera MediaMTX/ffmpeg restart after zombies

| Field | Value |
| --- | --- |
| Date | 2026-08-24 |
| Module | Containerlab camera-01 |
| Type | Bugfix / service lifecycle |
| Status | Done |

## Context

`stop-camera.sh` / `start-camera.sh` must take RTSP down and back up without recreating the node. HTTP `:8081` stays up (`/health` 503 while RTSP is down). Sentinel RTSP_HEALTH is unchanged.

## Problem

Containerlab PID 1 is `sleep infinity` and does not reap. After stop, MediaMTX and ffmpeg become **zombies**. `pgrep -x mediamtx` and `kill -0` still match them, so start printed “MediaMTX already running”, skipped a new listener, and `:8554` stayed closed. Leftover ffmpeg then logged `Broken pipe`. Sentinel saw HTTP 503 and RTSP connection refused.

## Design

“Already running” requires a **non-zombie** process **and** TCP `:8554` listening. Otherwise kill live RTSP processes and start MediaMTX, wait for `:8554`, then start ffmpeg. Unhealthy ffmpeg is restarted. `runOnInit` was removed so ffmpeg cannot publish before the listener is up.

## Implementation (files)

| File | Role |
| --- | --- |
| `containerlab/services/camera/start-camera.sh` | Live PID + port checks; stale cleanup; ffmpeg after `:8554` |
| `containerlab/services/camera/stop-camera.sh` | Stop live MTX/ffmpeg; wait until `:8554` closed; HTTP kept |
| `containerlab/services/camera/mediamtx.yml` | `live` is a publisher path only (no `runOnInit`) |

## Tests

| Check | Result |
| --- | --- |
| `stop-camera.sh` | `:8554` closed, HTTP `/health` 503, node still up |
| `start-camera.sh` | `:8554` open, OPTIONS `/live` 200, HTTP 200 |
| second `start-camera.sh` | “already running”; live mediamtx=1 ffmpeg=1 http=1 |
| Sentinel Check Now | `RTSP_HEALTH [UP]`, `HTTP_HEALTH [UP]`, `lastStatus UP` |

## Result or example

```text
camera-01 MediaMTX not ready (need live process AND TCP :8554)
camera-01 MediaMTX started pid=… :8554
camera-01 ffmpeg publisher started pid=…
camera-01 ready  rtsp://0.0.0.0:8554/live
```

## Out of scope

Sentinel, topology, camera image, SNMP, Syslog, NetFlow.

## Verify commands

```bash
docker exec clab-bank-lab-camera-01 sh /opt/camera/stop-camera.sh
docker exec clab-bank-lab-camera-01 sh /opt/camera/start-camera.sh
docker exec clab-bank-lab-camera-01 sh /opt/camera/start-camera.sh
```

## Rapport talking points

Zombie processes after `kill` on a non-reaping PID 1 are not a running service. Port listen is part of readiness, not only `pgrep`.
