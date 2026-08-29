# 2026-08-24 — ACKNOWLEDGED lastActivityAt on new NetFlow evidence

| Field | Value |
| --- | --- |
| Date | 2026-08-24 |
| Module | Spring Boot incident correlation (NetFlow attach) |
| Type | Bugfix |
| Status | Done |

## Context

Open incidents (ACTIVE / ACKNOWLEDGED) may receive more matching Syslog and NetFlow evidence inside the 30-minute episode window. `lastActivityAt` is the authoritative clock for that window. The UI “Last activity” field reads `lastActivityAt`.

## Problem

After Acknowledge, a fresh matching scan still attached to the same incident (new `network_flows.incident_id`), but **Last activity stayed at the pre-ack time**.

NetFlow `alreadyHasNetFlowEvidence` treats the same src/dst/port as a duplicate **event**. A second scan with the same ports therefore skipped `Incident.addEvent()`, which was the only place that advanced `lastActivityAt`. The new flow rows were still linked via `markSuspicious(..., incidentId)`.

Re-import of an already-linked flow (`flow.incidentId != null`) was already skipped correctly and must not move the clock.

## Design

- Add `Incident.recordActivity(evidenceTime)`: `lastActivityAt = max(lastActivityAt, evidenceTime)` and the same for `lastSeenAt`. Never backwards. No status / `acknowledgedAt` change.
- `addEvent` uses `recordActivity` (Syslog/Event path unchanged in behavior).
- When a **new** NetFlow row is linked but a same-port Event already exists: still `recordActivity(flow.endTime)` (fallback startTime). Do not create another Event.
- When `flow.incidentId` is already set: do not call `recordActivity`.

Authoritative field: **`lastActivityAt`**. `lastSeenAt` is kept in sync for list sort and legacy readers.

## Implementation (files)

| File | Role |
| --- | --- |
| `backend/.../incident/Incident.java` | `recordActivity`; `addEvent` delegates |
| `backend/.../netflow/NetFlowAnomalyDetectionService.java` | activity update on new-flow / skipped-event attach |
| `backend/.../incident/IncidentTimestampTests.java` | no backwards move; ACKNOWLEDGED preserved |
| `backend/.../incident/IncidentEpisodeCorrelationTests.java` | fresh same-port NetFlow after ack; re-analyze no-op |
| `frontend/.../NotificationDropdown.tsx` | display `lastActivityAt ?? lastSeenAt` |

## Tests

```bash
/usr/bin/mvn -f backend/pom.xml test -Dtest=IncidentTimestampTests,IncidentEpisodeCorrelationTests,NetFlowIncidentLinkingTests
```

## Result / example

ACKNOWLEDGED at 17:20, fresh same-port scan at 17:25 → same incident, status ACKNOWLEDGED, `acknowledgedAt` unchanged, `lastActivityAt` ≈ 17:25. Re-running analyze on the same flow IDs does not move the clock.

## Out of scope

Episode window length, RESOLVED isolation, Flyway status check, PCAP upload timestamps.

## Verify commands

Same Maven command. Then: create scan → Acknowledge → run the same src/dst scan again within 30 min → Last activity advances, status stays ACKNOWLEDGED.

## Rapport talking points

- Linking a new flow to an incident is new evidence even if the destination port was already documented
- Duplicate **event** suppression is not the same as duplicate **flow import**
- Episode correlation must follow `lastActivityAt`, not `createdAt`
