# 2026-08-23 — ATM TCP 9090 without runtime apk

| Field | Value |
| --- | --- |
| Date | 2026-08-23 |
| Module | Containerlab ATM (`atm-01`) |
| Type | Bugfix |
| Status | Done |

## Context

Sentinel monitors ATM-01 with PING + TCP_PORT 9090. `atm-client.sh` already generates HTTP traffic to `bank-srv-01` using BusyBox `wget`. A separate TCP listener on 9090 is required so Stop Service can fail TCP_PORT while PING stays up.

## Problem

`start-atm-service.sh` ran `apk add python3` and `atm-service.py`. In the lab, Alpine has no reliable DNS/packages (`python3 (no such package)`). Deploy therefore left 9090 closed.

## Design

Use **BusyBox `nc` already in `alpine:latest`**. No apk, no Python, no Internet.

```text
nc -n -lk -p 9090 -e /opt/atm/atm-banner.sh
```

`-lk -e` is a persistent listener; each TCP client gets `ATM-01 service OK`. `atm-client.sh` is unchanged.

## Implementation (files)

| File | Role |
| --- | --- |
| `containerlab/services/atm/atm-banner.sh` | Banner printed per connection |
| `containerlab/services/atm/atm-service.sh` | `exec nc -n -lk -p 9090 -e …` |
| `containerlab/services/atm/start-atm-service.sh` | Idempotent start, no apk |
| `containerlab/services/atm/stop-atm-service.sh` | Stop listener only |
| `containerlab/services/atm/atm-service.py` | Removed |

Image remains `alpine:latest`. No package/image change.

## Tests

Docker `--network none` + bind-mount `/opt/atm:ro`:

- python3 absent
- start twice → second is already running
- two `nc` clients get the banner
- `nc -z` succeeds
- stop closes 9090
- start restores banner

## Result / example

```text
ATM-01 service OK
```

## Out of scope

Changing ATM HTTP client traffic or Sentinel TCP_PORT logic.

## Verify commands

```bash
docker exec clab-bank-lab-atm-01 sh /opt/atm/start-atm-service.sh
nc -vz 172.30.30.30 9090
nc -w 2 172.30.30.30 9090
docker exec clab-bank-lab-atm-01 sh /opt/atm/stop-atm-service.sh
nc -vz 172.30.30.30 9090   # must fail
docker exec clab-bank-lab-atm-01 sh /opt/atm/start-atm-service.sh
```

## Rapport talking points

Lab endpoints used for AIOps health checks must not depend on runtime package mirrors. Alpine BusyBox is enough for a TCP availability probe.
