# 2026-08-24 — Recover lab daemons after Docker restart

| Field | Value |
| --- | --- |
| Date | 2026-08-24 |
| Module | `scripts/start-pfe.sh` / Containerlab bank-lab |
| Type | Bugfix / lifecycle resilience |
| Status | Done |

## Context

`start-pfe.sh` skipped `containerlab deploy` when all seven `clab-bank-lab-*` containers were running. That is correct for topology (no duplicate nodes), but it is not full lab readiness.

## Problem

Node PID 1 is `sleep infinity`. Containerlab `exec` (snmpd, bank HTTP, ATM TCP, camera RTSP, firewall forwarders) runs only at deploy time. After Docker or PC restart the containers can be `running` while those daemons are dead. Readiness then printed WARN for :8080 / :9090 / :8081 / :8554 / SNMP and never recovered them.

## Design

Keep skip-deploy when all nodes are running. After that, `docker exec` the **existing** idempotent start scripts. Do not change topology, IPs, or service implementations. If a script fails: `[WARN]`, continue, do not destroy the lab.

Firewall Syslog/NetFlow is restored **after** attach-sentinel / attach-netflow so `/opt/runtime` destination files exist, and only if `setup-firewall.sh` helpers are not already alive (`start_once` / exporter pgrep).

## Implementation (files)

| File | Role |
| --- | --- |
| `scripts/lib/pfe-env.sh` | `recover_exec`, `recover_node_daemons`, `recover_firewall_forwarding` |
| `scripts/start-pfe.sh` | Recover daemons → attach → firewall forwarding → readiness |
| `docs/environment-lifecycle.md` | Start flow includes recovery |

## Tests

| Check | Result |
| --- | --- |
| `bash -n` | Pass |
| `./scripts/start-pfe.sh` on running nodes with dead daemons | Recovered SNMP/HTTP/TCP/RTSP; `setup-firewall.sh` restored Syslog/NetFlow; all readiness `[OK]` |
| Second `./scripts/start-pfe.sh` | Idempotent: same PIDs, forwarding skipped, no redeploy |

## Result or example

Skipped deploy, then:

```text
[OK] BANK-FW-01 SNMP
[OK] CORE-RTR-01 SNMP
[OK] CORE-SW-01 SNMP
[OK] BANK-SRV-01 HTTP :8080
[OK] ATM-01 TCP :9090
[OK] ATM-01 client
[OK] BANK-CAMERA-01 HTTP+RTSP
[OK] BANK-FW-01 firewall+Syslog+NetFlow
```

## Out of scope

Topology, monitoring code, rewriting start-*.sh / setup-firewall.sh.

## Verify commands

```bash
bash -n scripts/lib/pfe-env.sh scripts/start-pfe.sh
./scripts/start-pfe.sh
./scripts/status-pfe.sh
```

## Rapport talking points

- Lab container up ≠ lab service up when PID 1 is `sleep infinity`
- Recovery reuses the same idempotent scripts as Containerlab `exec`
- Destinations are refreshed before restarting Syslog/NetFlow forwarders
