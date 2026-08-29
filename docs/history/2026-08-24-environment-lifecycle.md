# 2026-08-24 — Single-command PFE environment lifecycle

| Field | Value |
| --- | --- |
| Date | 2026-08-24 |
| Module | Docker Compose + Containerlab bank-lab |
| Type | Lifecycle orchestration |
| Status | Done |

## Context

After a reboot, operators had to start Compose, deploy Containerlab, attach Sentinel/netflow to `banklab-mgmt`, and refresh exporter destinations as separate commands. Paths were easy to hardcode (`~/Desktop/pfe`).

## Problem

No single idempotent entrypoint. A naive `containerlab deploy` on an already running lab can recreate nodes. `docker compose up --build` rebuilds images. Destroying `banklab-mgmt` would disconnect Sentinel from the lab.

## Design

Three scripts under `scripts/`, project root derived from the script path:

| Script | Role |
| --- | --- |
| `start-pfe.sh` | Compose `--no-build`, skip healthy lab deploy, attach mgmt, attach-sentinel/netflow, warn on warm-up |
| `stop-pfe.sh` | `containerlab destroy --keep-mgmt-net` then `docker compose down` (no `-v`) |
| `status-pfe.sh` | read-only Compose / nodes / attachments / reachability |

Sudo is used only for `containerlab`. Docker stays as the current user. Topology, IPs, firewall, Syslog, and NetFlow code are unchanged.

## Implementation (files)

| File | Role |
| --- | --- |
| `scripts/lib/pfe-env.sh` | Shared paths, Compose/clab helpers, probes |
| `scripts/start-pfe.sh` | Start |
| `scripts/stop-pfe.sh` | Stop |
| `scripts/status-pfe.sh` | Status |
| `docs/environment-lifecycle.md` | Operator guide |
| `README.md` | After-reboot commands |

## Tests

| Check | Result |
| --- | --- |
| `bash -n` on the four scripts | Pass |
| `./scripts/start-pfe.sh` with lab already up | Pass: Compose left running, deploy skipped, attachments no-op, destinations rewritten |
| `./scripts/status-pfe.sh` | Pass: read-only; reports Compose, nodes, `banklab-mgmt`, reachability |

Inner lab daemons that had already exited (HTTP/TCP/SNMP) produced `[WARN]` and did **not** fail or redeploy the stack.

## Result or example

```text
./scripts/start-pfe.sh
# after reboot: Compose up, lab skipped if healthy, destinations refreshed
```

## Out of scope

Topology, mgmt IPs, Sentinel check logic, nftables, Syslog/NetFlow exporters, camera/ATM implementations, adding a frontend Compose service.

## Verify commands

```bash
bash -n scripts/lib/pfe-env.sh scripts/start-pfe.sh scripts/stop-pfe.sh scripts/status-pfe.sh
./scripts/start-pfe.sh
./scripts/status-pfe.sh
```

## Rapport talking points

- One reboot workflow: `./scripts/start-pfe.sh`
- Idempotent lab start (do not destroy a healthy deploy)
- Keep `banklab-mgmt` across stop so Compose and the lab can share it
