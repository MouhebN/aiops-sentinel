# 2026-08-27 — Operator demo scenario suite

| Field | Value |
| --- | --- |
| Date | 2026-08-27 |
| Module | scripts/demo + Containerlab helpers |
| Type | Operator UX / reproducibility |
| Status | Done |

## Context

The lab (ATTACKER → FW → RTR → SW → SRV/ATM/CAMERA) and Sentinel (Syslog, NetFlow, AUTO_ROLLING PCAP, availability checks) are stable enough for a jury demo. Operators were still typing raw `docker exec` during tests.

## Problem

No single, safe command set for: baseline traffic, SSH-only vs port scan, BANK-SRV outage/recovery, ATM/camera DEGRADED, and a service reset that **must not** wipe incidents.

## Design

`scripts/demo/` numbered bash scripts + `demo.sh` wrapper. They only create real network/service conditions. They source `scripts/lib/pfe-env.sh` (containers, `tcp_open`, `recover_node_daemons`, capture-sensor health). Existing in-node helpers:

- `/opt/scenarios/port-scan.sh` (exactly once; 180s cooldown unless `DEMO_FORCE=1`)
- `/opt/bank-server/{stop,start}-server.sh`
- `/opt/atm/{stop,start}-atm-service.sh` (TCP 9090 only)
- `/opt/camera/{stop,start}-camera.sh` (RTSP only)

Camera `GET /health` is HTTP **200** whenever the HTTP process is up (`stream=unavailable` in JSON if RTSP is down) so Sentinel HTTP_HEALTH stays UP while RTSP_HEALTH fails → component DEGRADED.

Reset calls `recover_node_daemons` and verifies ports. It does not `compose down`, destroy Containerlab, or delete DB rows.

## Implementation (files)

| File | Role |
| --- | --- |
| `scripts/demo/lib/demo-common.sh` | Root, require nodes, waits, expected-output, scan cooldown |
| `scripts/demo/01-baseline.sh` … `09-*.sh`, `99-reset-lab.sh` | Scenarios |
| `scripts/demo/demo.sh` | Named wrapper |
| `containerlab/services/camera/camera-server.py` | `/health` liveness 200 vs stream field |
| `docs/demo-scenarios.md` | Jury sequence + table |
| `scripts/tests/test-demo.sh` | Static safety tests |

## Tests

| Check | Result |
| --- | --- |
| `bash -n` on all demo scripts; executable bits | Pass |
| Port-scan invoked once via `/opt/scenarios/port-scan.sh` | Pass |
| Bank/ATM/camera use official helpers | Pass |
| No compose down / clab destroy / psql / incident API | Pass |
| Reset mentions preserved history | Pass |

## Result / example

```text
[Scenario] PORT_SCAN — ATTACKER-01 reconnaissance of BANK-SRV-01
[1/3] Checking lab and capture sensor
[OK] capture sensor + rolling buffer running
[2/3] Running existing /opt/scenarios/port-scan.sh once
[OK] one port-scan completed (21 22 23 80 443 3306 5432)
```

```text
[Scenario] BANK-SRV-01 availability outage
[2/3] Stopping HTTP service
[OK] :8080 unavailable (container still running)
```

## Out of scope

Detector thresholds, incident APIs, AI auto-analyze, wiping Postgres.

## Verify commands

```bash
bash scripts/tests/test-demo.sh
./scripts/demo/demo.sh baseline
./scripts/demo/demo.sh port-scan
```

## Rapport talking points

- Demo scripts are **stimuli**, Sentinel is the system under test
- DEGRADED (ATM/camera) vs DOWN (BANK-SRV HTTP+TCP)
- Reset restores daemons, not the incident timeline
