# 2026-08-25 — Containerlab data-plane recovery after host reboot

| Field | Value |
| --- | --- |
| Date | 2026-08-25 |
| Module | `scripts/start-pfe.sh` / Containerlab bank-lab / capture sensor |
| Type | Bugfix / lifecycle resilience |
| Status | Done |

## Context

`./scripts/start-pfe.sh` is the reboot entrypoint for Compose + bank-lab. After a PC reboot, all seven `clab-bank-lab-*` containers were still `running`, so start treated the lab as healthy. The capture sensor (`pfe-capture-sensor`, `--network container:clab-bank-lab-bank-fw-01`) shared BANK-FW-01's netns.

## Problem

Linux does not restore Containerlab **veth** pairs across a host reboot. Containers can be running while data-plane interfaces are gone.

Observed on BANK-FW-01:

- `eth0` = `172.30.30.10` (mgmt) present
- `eth1` = `10.0.0.1/24` missing
- `eth2` = `10.0.1.1/30` missing

`restore-dataplane.sh` only ran `ip addr add` / `ip route` on interfaces that **already existed**, then printed `bank-lab data-plane addresses restored`. Automated PCAP returned `EMPTY_CAPTURE` because the sensor had no `eth1` to sniff. Manual `clab destroy` + deploy restored the links.

Previous health assumption: **all Containerlab containers running ⇒ lab healthy**.

## Design

Health is **containers running AND expected data-plane interfaces present** (addresses checked when practical).

| Case | Condition | Action |
| --- | --- | --- |
| A | lab absent | `containerlab deploy` |
| B | nodes running, veths exist, IPs/routes missing | `restore-dataplane.sh` only |
| C | nodes running, any expected veth missing | **redeploy topology** (restore cannot recreate veths) |
| refuse | partial lab | fail; do not destroy a half-up lab |

Redeploy: stop `pfe-capture-sensor` first (shared netns), `clab destroy --keep-mgmt-net`, deploy. **Do not** `docker compose down`. Postgres/app volumes stay. Then restore addresses, recover inner daemons, reattach backend/netflow to `banklab-mgmt`, refresh destinations, recreate sensor against the **new** BANK-FW-01 container id, recover Syslog/fprobe.

Capture sensor is healthy only if: running **and** `NetworkMode` is `container:<current bank-fw-01 Id>` (full id, 12-char id, or name) **and** `eth1` exists **and** `GET /health` succeeds.

`restore-dataplane.sh` asserts required interfaces exist; if not, exit 1 with `Cannot restore data-plane addresses: <NODE> <iface> is missing. Containerlab topology redeploy is required.` It must not print `addresses restored` when links are gone.

## Implementation (files)

| File | Role |
| --- | --- |
| `scripts/lib/pfe-dataplane.sh` | Inventory, `classify_dataplane`, `lab_recovery_action`, restore/redeploy, sensor namespace checks |
| `scripts/lib/pfe-env.sh` | Sources dataplane; `ensure_capture_sensor` requires current netns + eth1 + `/health` |
| `scripts/start-pfe.sh` | Deploy / restore / redeploy decision; restore failure is fatal |
| `scripts/status-pfe.sh` | Data-plane OK/MISSING + sensor CURRENT/STALE; exit 1 if lab running but degraded |
| `scripts/stop-pfe.sh` | Stop sensor before `clab destroy` (unchanged order, explicit log) |
| `containerlab/scripts/restore-dataplane.sh` | Delegates to `restore_dataplane`; fails if veths missing |
| `scripts/tests/test-dataplane.sh` | Mocked docker/clab unit tests for the 10 recovery cases |
| `docs/environment-lifecycle.md` | Operator flow + decision table |

## Tests

| Check | Result |
| --- | --- |
| `bash -n` on lifecycle + dataplane scripts | Pass |
| `scripts/tests/test-dataplane.sh` | Pass (mocked) |
| absent containers → `deploy` | Pass |
| healthy lab → `restore`, not redeploy; second pass idempotent | Pass |
| veths present, IPs missing → `restore`; script prints restored | Pass |
| containers running, veths missing → `redeploy` | Pass |
| restore with missing iface → exit 1, no “restored” | Pass |
| stale sensor NetworkMode → remove + recreate | Pass |
| sensor without eth1 → not healthy, recreate | Pass |
| `redeploy_bank_lab` never calls Compose; no `down -v` | Pass |
| start-pfe order: lab → restore → daemons → Syslog → NetFlow → sensor | Pass |
| stop-pfe: sensor before `clab destroy` | Pass |

## Result or example

```text
[WARN] data-plane veths missing (containers running is not enough after a host reboot)
...
[OK] data-plane links present after redeploy
bank-lab data-plane addresses restored
[OK] capture sensor started (current FW namespace, eth1 present, http://172.30.30.10:8090/health)
```

After recovery, `docker exec clab-bank-lab-bank-fw-01 ip addr` and `docker exec pfe-capture-sensor ip addr` both show `eth0`, `eth1 10.0.0.1/24`, `eth2 10.0.1.1/30`.

## Out of scope

NetFlow/fprobe/nfcapd, Syslog, SNMP, PCAP provider abstraction, incident lifecycle, ComponentIdentityResolver, AI, frontend.

## Verify commands

```bash
bash -n scripts/lib/pfe-env.sh scripts/lib/pfe-dataplane.sh \
  scripts/start-pfe.sh scripts/stop-pfe.sh scripts/status-pfe.sh \
  containerlab/scripts/restore-dataplane.sh scripts/tests/test-dataplane.sh
bash scripts/tests/test-dataplane.sh
./scripts/start-pfe.sh
./scripts/status-pfe.sh
docker exec clab-bank-lab-bank-fw-01 ip addr
docker exec pfe-capture-sensor ip addr
```

## Rapport talking points

- Containerlab containers `running` after reboot does not imply data-plane veths exist
- Address restore cannot recreate missing veths; that requires topology redeploy
- Capture sensor must track the current firewall container id, not a stale netns
- Compose/Postgres volumes stay up across lab redeploy
