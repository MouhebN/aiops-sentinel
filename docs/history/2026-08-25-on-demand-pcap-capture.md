# 2026-08-25 — Automated on-demand PCAP capture

| Field | Value |
| --- | --- |
| Date | 2026-08-25 |
| Module | Spring Boot PCAP jobs + lab capture sensor + Incident Detail |
| Type | Feature / operator workflow |
| Status | Done |

## Context

Sentinel already stored uploaded PCAPs, ran FastAPI/tshark analysis, showed findings on Incident Detail, and passed PCAP summaries into AI context. Operators still had to run `tcpdump`/`nsenter` on the host and upload the file by hand.

## Problem

Live packet capture was a manual host procedure. Spring must not depend on the Docker socket, `nsenter`, or arbitrary shell. The lab still needs the same visibility already proven on BANK-FW-01 `eth1` (attacker `10.0.0.10` → target `10.10.10.20`).

## Design

Generic `PacketCaptureProvider` in Spring. First implementation: `LAB_SENSOR` HTTP client.

Lab visibility: capture-sensor sidecar shares BANK-FW-01’s network namespace (`--network container:clab-bank-lab-bank-fw-01`, `NET_RAW`+`NET_ADMIN`). WAN is a p2p veth, so there is no WAN Docker network to join. Sensor listens on FW mgmt `172.30.30.10:8090`. BPF is built on the sensor from validated IPs only (`host 10.0.0.10 and host 10.10.10.20`).

```text
network visibility point → capture sensor → PacketCaptureProvider → Sentinel → existing tshark
```

Manual upload and automated capture both call `PacketCaptureAnalysisService.ingest()` → `PcapAnalysisClient` → `/api/analyze-pcap`.

Capture points are configuration (`app.pcap.points.bank-firewall-wan`), not incident-type Docker commands. Live capture is disabled on RESOLVED incidents.

## Implementation (files)

| File | Role |
| --- | --- |
| `capture-sensor/` | FastAPI + tcpdump sensor (`POST /captures`, status, file, delete) |
| `backend/.../pcap/PacketCaptureProvider.java` | Provider abstraction |
| `backend/.../pcap/LabSensorPacketCaptureProvider.java` | LAB_SENSOR HTTP client |
| `backend/.../pcap/PacketCaptureJob.java` | Job entity/states |
| `backend/.../pcap/PacketCaptureJobService.java` | Incident-derived endpoints, limits, ingest |
| `backend/.../pcap/PcapAnalysisClient.java` | Shared FastAPI analyze-pcap client |
| `frontend/src/pages/incidents/detail/PacketCapturePanel.tsx` | Capture traffic dialog + status |
| `scripts/lib/pfe-env.sh`, `start-pfe.sh`, `status-pfe.sh`, `stop-pfe.sh` | Optional sensor lifecycle |
| `docs/on-demand-pcap-capture.md` | Lab vs production mapping |

Unchanged: NetFlow/fprobe/nfcapd, Syslog, incident episode rules, SNMP, `ComponentIdentityResolver` semantics, AI model, topology relations.

## Tests

Backend `PacketCaptureJobServiceTests` **12 passed** (provider and analysis client mocked) plus existing `PacketCaptureAnalysisDeletionTests` **4 passed**. Full backend suite exited 0.

1. Source/destination derived from incident (`10.0.0.10` / `10.10.10.20`)
2. Extra BPF/tcpdump fields cannot be used
3. Duration 120 s clamped to 60 s
4. RESOLVED incident cannot start live capture
5. Provider unavailable → 503, incident intact
6. Successful capture becomes attached PCAP evidence
7. Manual upload path still works
8. Automated capture calls existing analyze-pcap client
9. Completed PCAP appears in AI context
10. One active capture per incident
11. External `10.0.0.10` stays external; `10.10.10.20` → BANK-SRV-01
12. Capture failure does not corrupt the incident

Sensor **14 passed**: BPF filter, interface/IP rejection, duration clamp, argv has no shell, extra `bpf` field 422, file download, tcpdump timeout cleanup.

## Result or example

Operator: Incident Detail → Capture traffic → BANK-FW-01 / WAN, 20 s → job RUNNING → sensor tcpdump → PCAP attached → same tshark findings as manual upload.

## Out of scope

- FIREWALL_API / ARKIME / SURICATA providers
- Auto-rerun AI when PCAP arrives
- Reopening RESOLVED incidents
- Replacing enterprise NDR with lab tcpdump

## Verify commands

```bash
cd backend && ./mvnw -q test -Dtest=PacketCaptureJobServiceTests,PacketCaptureAnalysisDeletionTests
cd capture-sensor && PYTHONPATH=. python3 -m unittest discover -s tests -v
```

Live (lab up, sensor healthy):

```bash
# while capture job is RUNNING
docker exec clab-bank-lab-attacker-01 sh /opt/scenarios/port-scan.sh
```

Expect SYN from `10.0.0.10` to `10.10.10.20` ports 21, 22, 23, 80, 443, 3306, 5432, then tshark findings on the incident.

## Rapport talking points

- Sentinel depends on a capture **provider**, not Docker/tcpdump.
- Lab sensor is a visibility sidecar on the firewall netns, analogous to SPAN/TAP in production.
- One analysis pipeline for upload and automated capture.
- Hard limits: 20 s default, 60 s max, 20 MB, one concurrent capture.
