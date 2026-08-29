# 2026-08-26 — Backend permanently on banklab-mgmt

| Field | Value |
| --- | --- |
| Date | 2026-08-26 |
| Module | Docker Compose / start-pfe / Containerlab management plane |
| Type | Networking / Syslog reachability |
| Status | Done |

## Context

BANK-FW-01 (`172.30.30.10`) and the nfcapd collector sit on the Containerlab management bridge `banklab-mgmt` (`172.30.30.0/24`). The Spring Boot backend listens for RFC 3164 Syslog on **UDP 5514**. The firewall resolves the current backend address via `/opt/firewall/sentinel-dest.sh` (runtime file + health probe), then forwards DENY lines to that IP.

## Problem

Compose attached `pfe-backend-1` only to `pfe_default` (e.g. `172.22.0.6`). `start-pfe.sh` could `docker network connect banklab-mgmt` after the lab was up, but a later `docker compose up` **recreated** the backend without the extra network because it was not in `docker-compose.yml`. Firewall discovery then wrote a `banklab-mgmt` address (often `172.30.30.3`) that was not actually on the backend. UDP Syslog was silently dropped.

Manual repair that proved the diagnosis:

```bash
docker network connect banklab-mgmt pfe-backend-1
```

Immediately after that: UDP received, `FIREWALL` parser, `FIREWALL_DENY`, incident created.

`status-pfe.sh` only **warned** when the backend was off the management network, so the stack still reported healthy.

## Design

1. Declare `banklab-mgmt` as an **external** Compose network (same Docker network ID Containerlab uses). Attach **backend** and **netflow-tools** to `default` + `banklab-mgmt`. No static `172.30.30.x` on the backend.
2. `ensure_mgmt_network` before `compose up`: create `banklab-mgmt` only if missing, with subnet `172.30.30.0/24` and gateway `172.30.30.1`. Never a second name/ID.
3. Keep `ensure_mgmt_attachment` as repair after Compose and again after lab deploy. `attach-sentinel.sh` still writes the **current** mgmt IP to `.runtime/sentinel-syslog-host`.
4. Sentinel dest: prefer that runtime IP if it still answers `/actuator/health` or pings; drop stale cache/runtime and re-probe the /24. Do not hardcode `172.30.30.3`.
5. `status-pfe.sh` sets `STATUS_RC=1` (DEGRADED) when a **running** backend has no `172.30.30.0/24` address, or BANK-FW-01 cannot ping it.

Spring Boot itself is unchanged: it still binds UDP 5514; lab/Compose networking delivers the packets.

## Implementation (files)

| File | Role |
| --- | --- |
| `docker-compose.yml` | `backend` / `netflow-tools` on `default` + external `banklab-mgmt`; UDP 5514 published |
| `scripts/lib/pfe-env.sh` | `ensure_mgmt_network`, `container_network_ip`, `is_mgmt_ipv4` |
| `scripts/start-pfe.sh` | Create/reuse mgmt net **before** Compose; validate backend mgmt IP |
| `scripts/status-pfe.sh` | Management-plane FAIL/DEGRADED |
| `containerlab/services/firewall/sentinel-dest.sh` | Runtime over cache; invalidate stale IPs |
| `docs/environment-lifecycle.md` | Start-order documentation |
| `scripts/tests/test-mgmt-network.sh` | Compose/startup/discovery regression tests |

## Tests

| Check | Result |
| --- | --- |
| Backend (and collector) join default + `banklab-mgmt`; `external: true`; no `ipv4_address` / `172.30.30.3` | Pass |
| `ensure_mgmt_network` before `compose up`; attachment repaired after | Pass |
| Missing backend mgmt attachment is a status DEGRADED path | Pass |
| Stale cache/runtime IP is not reused; live cache kept | Pass |
| Lab node mgmt IPs `.10` `.11` `.12` unchanged | Pass |
| `scripts/tests/test-dataplane.sh` | Pass (unchanged dataplane recovery) |

## Result / example

Live after `docker compose up -d --no-build` (backend recreated onto the **same** `banklab-mgmt` ID `9bd525ec…`):

```text
pfe_default     172.22.0.6
banklab-mgmt    172.30.30.3   (DHCP this run; alias sentinel-syslog)
```

`resolve_sentinel_ip` from BANK-FW-01 = `172.30.30.3` (matches inspect). Attacker `nc -z -w1 10.10.10.20 22` → backend:

```text
Received UDP syslog packet: sourceIp=172.30.30.10
matchedSource=BANK-FW-01, parserProfile=FIREWALL, eventType=FIREWALL_DENY
eventSource=SYSLOG  incidentId=48
```

## Out of scope

- Static backend IP on `172.30.30.0/24`
- Changing Containerlab node mgmt addresses
- Spring Syslog parser / port (stays UDP 5514)
- Putting postgres/fastapi/ollama on the lab management plane

## Verify commands

```bash
bash scripts/tests/test-mgmt-network.sh
bash scripts/tests/test-dataplane.sh

docker inspect pfe-backend-1 -f '{{json .NetworkSettings.Networks}}'
docker inspect -f '{{with index .NetworkSettings.Networks "banklab-mgmt"}}{{.IPAddress}}{{end}}' pfe-backend-1
docker exec clab-bank-lab-bank-fw-01 sh -c '. /opt/firewall/sentinel-dest.sh; echo "Resolved Sentinel IP: $(resolve_sentinel_ip)"'
docker exec clab-bank-lab-attacker-01 nc -z -w1 10.10.10.20 22 || true
```

## Rapport talking points

- Management plane vs Compose default bridge: Syslog is UDP to the backend **on banklab-mgmt**, not via `pfe_default`
- External network reuse: one `banklab-mgmt` ID shared by Containerlab and Compose
- Dynamic addressing: destination discovery, not a hardcoded `172.30.30.3`
