# 2026-08-26 — First-eligible AUTO_ROLLING PCAP

| Field | Value |
| --- | --- |
| Date | 2026-08-26 |
| Module | Spring Boot incidents / NetFlow / PCAP jobs |
| Type | Bugfix / trigger eligibility |
| Status | Done |

## Context

The lab capture sensor already keeps a rolling buffer on BANK-FW-01 `eth1`. Sentinel is supposed to start one `AUTO_ROLLING` snapshot when an open SECURITY incident becomes eligible (`PORT_SCAN` / `SUSPICIOUS` / `RECON`, WARNING+). Manual **Capture traffic** stays separate.

## Problem

Incident #44 was created from the first generic firewall `FIREWALL_DENY` (title “security alert”, not eligible). Later Syslog `POSSIBLE_PORT_SCAN` and NetFlow `PORT_SCAN` reused the **same** episode. Rolling was healthy (`rolling.running=true`), but **no snapshot** ran: sensor logs showed only `GET /health`.

Auto-capture listened only to `IncidentCreatedEvent`. Enrichment does not emit a new created event, so the incident never became a capture trigger.

## Design

Evaluate eligibility on **current enriched evidence**, not only at insert time.

```text
Syslog correlate (create or attach)
NetFlow anomaly attach (reuse or create)
        ↓
IncidentAutoCaptureCoordinator.evaluateAndTrigger(incidentId, reason)
        ↓  open + AutoCapturePolicy + no AUTO job
IncidentEligibleForAutoCaptureEvent
        ↓  AFTER_COMMIT (async)
AutoPacketCaptureService.captureIfEligible
        ↓
one AUTO_ROLLING job → existing LAB_SENSOR snapshot
```

The coordinator does not start tcpdump, does not mutate `lastActivityAt` / `lastSeenAt` / status, and is idempotent. Capture stays after commit so NetFlow import is not blocked for 20 s.

Policy prefers structured evidence:

1. event type (`NETFLOW_PORT_SCAN`, `POSSIBLE_PORT_SCAN`, …)
2. `anomalyType=` in event details
3. attached `NetworkFlow.anomalyType` (`PORT_SCAN`)
4. title / message / raw text as fallback only

## Implementation (files)

| File | Role |
| --- | --- |
| `backend/.../IncidentAutoCaptureCoordinator.java` | First-eligible gate |
| `backend/.../IncidentEligibleForAutoCaptureEvent.java` | After-commit event |
| `backend/.../AutoPacketCaptureListener.java` | Listens to eligible event, not `IncidentCreatedEvent` |
| `backend/.../AutoCapturePolicy.java` | Structured evidence before title |
| `backend/.../IncidentService.java` | `evaluateAndTrigger` after create and Syslog attach |
| `backend/.../NetFlowAnomalyDetectionService.java` | `evaluateAndTrigger` after `saveAll` for touched incidents |
| `backend/.../PacketCaptureJobService.java` | Unique-index catch on auto job insert |
| `backend/.../V4__packet_capture_auto_trigger.sql` | Partial unique index `incident_id` where trigger is AUTO_* |
| `docs/on-demand-pcap-capture.md` | First-eligible wording |

## Tests

| Check | Result |
| --- | --- |
| `AutoCapturePolicyTests` | Generic DENY skipped; `NETFLOW_PORT_SCAN` event, `anomalyType=PORT_SCAN`, and `NetworkFlow.anomalyType=PORT_SCAN` eligible; resolved / availability skipped |
| `FirstEligibleAutoCaptureTests` | Eligible at creation once; DENY skipped; same incident + NetFlow PORT_SCAN starts AUTO_ROLLING; 7 flows / later Syslog still one job; resolved and non-SECURITY skip; provider failure does not break attach; evaluation does not change timestamps; MANUAL still works |
| `IncidentCreatedAutoCaptureTests` | After-commit capture; same episode no second job |
| `AutoPacketCaptureServiceTests` | Existing auto / manual / provider-fail behaviour |

## Result or example

```text
FIREWALL_DENY → incident #N (not eligible, no PCAP)
NetFlow PORT_SCAN reused #N
Incident N first became eligible reason=netflow-anomaly-attached
AUTO_ROLLING  (exactly one)
sensor POST /snapshots  (not only GET /health)
```

Live after backend rebuild (2026-08-26): rolling `running=true` on `eth1`; resolved #44; one `port-scan.sh`. NetFlow created episode **#45** (`PORT_SCAN`, 7 flows), coordinator fired `reason=netflow-anomaly-attached`, **one** `AUTO_ROLLING` job, sensor `POST /snapshots` 201. That lab run had **no Syslog DENY** (firewall did not UDP-syslog this scan), so reuse-of-#44 could not apply; the DENY-then-PORT_SCAN path is covered by `FirstEligibleAutoCaptureTests`. Job #6 finished `EMPTY_CAPTURE` (ring had ~110 B before the scan; import ~27 s later vs 20 s pre-trigger). Trigger eligibility is fixed; empty PCAP is dataplane/timing, not the coordinator.

## Out of scope

Rolling buffer, sensor networking, tcpdump ring, merge, `PacketCaptureProvider`, tshark, manual Capture traffic UI.

## Verify commands

```bash
cd backend && ./mvnw -q -Dtest=AutoCapturePolicyTests,AutoPacketCaptureServiceTests,IncidentCreatedAutoCaptureTests,FirstEligibleAutoCaptureTests,PacketCaptureJobServiceTests,PacketCaptureRouteMappingTests,NetFlowIncidentLinkingTests test

curl -sS http://172.30.30.10:8090/health
# resolve the current open SECURITY episode if needed, wait ≥20s, then:
docker exec clab-bank-lab-attacker-01 sh /opt/scenarios/port-scan.sh
```

Do not click Capture traffic. Do not run a second scan. Expect one `AUTO_ROLLING` job on the new episode, a snapshot request on the sensor, and tshark ports 21, 22, 23, 80, 443, 3306, 5432.

## Rapport talking points

- Trigger on first eligibility, not on incident insert
- Generic DENY then PORT_SCAN on the same episode is the lab path
- Idempotency is DB unique index + `hasAutomaticJob`, not an in-memory flag
- Capture remains after-commit so import is not blocked
