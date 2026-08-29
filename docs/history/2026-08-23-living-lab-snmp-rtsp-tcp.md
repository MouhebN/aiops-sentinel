# 2026-08-23 — Living lab protocols (SNMP, RTSP, TCP)

| Field | Value |
| --- | --- |
| Date | 2026-08-23 |
| Module | Containerlab bank-lab |
| Type | Feature / lab realism |
| Status | Done |

## Context

Sentinel already implements PING, HTTP_HEALTH, TCP_PORT, RTSP_HEALTH, SNMP_BASIC, and SNMP_ROUTER_METRICS. The bank lab topology was in place, but most nodes only answered ICMP/HTTP, so those methods could not be demonstrated on realistic endpoints.

## Problem

- Firewall / router / switch had no SNMP agent
- Camera HTTP claimed a stream without RTSP
- ATM generated traffic but had no local TCP service to stop independently of PING
- Bank server HTTP existed; start/stop was inline `nohup` without a service script

## Design

Keep node names, management IPs, data-plane IPs, nftables DENY, Syslog, and NetFlow. Add **real** daemons on the existing containers. Simulators never call Sentinel APIs.

| Node | New / kept endpoint |
| --- | --- |
| bank-fw-01 | net-snmp `snmpd` UDP 161 community `banklab` |
| core-rtr-01 | same `snmpd` (IF-MIB counters from the kernel) |
| core-sw-01 | same `snmpd` |
| bank-srv-01 | HTTP/TCP 8080 via `start-server.sh` / `stop-server.sh` |
| atm-01 | TCP 9090 listener + existing HTTP client to bank-srv-01 |
| camera-01 | MediaMTX RTSP `:8554/live` (ffmpeg `testsrc2`) + HTTP `:8081` |

SNMP uses Alpine `net-snmp` (not a Python fake). Camera first boot installs ffmpeg and MediaMTX 1.11.3 in the background so `clab deploy` exec does not block for 1–2 minutes.

## Implementation (files)

| File | Role |
| --- | --- |
| `containerlab/bank-lab.clab.yml` | SNMP binds/exec, ATM TCP, camera async start |
| `containerlab/services/snmp/snmpd.conf` | SNMPv2c `banklab`, `agentAddress udp:0.0.0.0:161` |
| `containerlab/services/snmp/start-snmpd.sh` | Idempotent snmpd |
| `containerlab/services/camera/mediamtx.yml` | RTSP `/live`, ffmpeg test pattern |
| `containerlab/services/camera/start-camera.sh` | MediaMTX + HTTP, wait for SDP + :8081 |
| `containerlab/services/camera/stop-camera.sh` | Stop RTSP+HTTP, keep node |
| `containerlab/services/camera/camera-server.py` | `/health` 200/503 from RTSP listen |
| `containerlab/services/atm/atm-service.py` | TCP 9090 |
| `containerlab/services/atm/start-atm-service.sh` / `stop-atm-service.sh` | Service outage without killing the node |
| `containerlab/services/atm/start-atm-client.sh` | Existing periodic bank traffic |
| `containerlab/services/bank-server/start-server.sh` / `stop-server.sh` | HTTP 8080 lifecycle |
| `containerlab/scripts/validate-protocols.sh` | Host TCP/HTTP/RTSP + docker SNMP |
| `frontend/.../components/index.tsx` | TCP port helper mentions RTSP_HEALTH |

## Tests

| Test | Result |
| --- | --- |
| Alpine `snmpd` smoke (`sysDescr`, `sysUpTime`, `ifNumber`, `ifDescr`, `ifOperStatus`, `ifInOctets`) | Pass |
| ATM TCP 9090 start → banner → stop → port closed | Pass |
| Camera MediaMTX OPTIONS `/live` and `/stream` → `RTSP/1.0 200` | Pass |
| `stop-camera.sh` closes :8554 | Pass |
| `sh -n` on new shell scripts | Pass |
| Full `clab deploy` of bank-lab | Not run in this session (lab was down) |

## Result / example

```text
SNMPv2-MIB::sysDescr.0 = STRING: SMOKE-NODE Containerlab node (Alpine net-snmp)
IF-MIB::ifNumber.0 = INTEGER: 2
RTSP/1.0 200 OK   (OPTIONS rtsp://127.0.0.1:8554/live)
ATM-01 service OK
```

## Out of scope

- Topology redesign, Sentinel API fakes, per-component ScheduledFuture changes
- Changing Sentinel RTSP path (still OPTIONS `.../stream`; MediaMTX still returns RTSP 200)

## Verify commands

```bash
cd containerlab
sudo containerlab destroy -t bank-lab.clab.yml --cleanup || true
sudo containerlab deploy -t bank-lab.clab.yml
# camera ffmpeg/MediaMTX first boot ~1–2 min
docker exec clab-bank-lab-camera-01 sh -c 'tail -f /var/log/camera-boot.log'
./scripts/validate-protocols.sh
```

## Rapport talking points

AIOps monitoring methods are only as good as the endpoints. The lab now exposes the same protocols a bank firewall, router, switch, ATM, server, and camera would, so Sentinel health checks are observations, not stubs.
