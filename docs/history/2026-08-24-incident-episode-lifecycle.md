# 2026-08-24 — Incident episode lifecycle and inactivity correlation

| Field | Value |
| --- | --- |
| Date | 2026-08-24 |
| Module | Spring Boot incidents + NetFlow correlation + topology overlay + React Incidents |
| Type | Feature / bugfix |
| Status | Done |

## Context

AIOps Sentinel groups Syslog/Event and NetFlow evidence into incidents by identity (device + category, or NetFlow src/dst/anomaly). Operators acknowledge or resolve incidents from the UI. Topology overlays `UNDER ATTACK` from open security incidents.

## Problem

An ACTIVE incident stayed eligible forever. A port scan several days later with the same source/destination/type was attached to the old episode (`lastSeenAt` was refreshed), so:

- one incident absorbed unrelated attack days
- resolve had no real isolation: matching traffic could still land on the closed or stale row depending on path
- Syslog correlation (`deviceId:category`) had no time window, and NetFlow then reused that same stale incident
- the UI still spoke of Recovered / FINISHED-like close, not a clear RESOLVED episode with Created / Last activity / Resolved

## Design

Statuses: **ACTIVE**, **ACKNOWLEDGED**, **RESOLVED**. Legacy DB value `RECOVERED` still loads and is mapped to `RESOLVED` in APIs (`IncidentStatus.forApi()`).

Timestamps:

| Field | Storage | Behavior |
| --- | --- | --- |
| `createdAt` | new nullable column | set once on create; getter falls back to `firstSeenAt` |
| `lastActivityAt` | new nullable column | init = first evidence; updated when new Syslog/NetFlow evidence attaches; getter falls back to `lastSeenAt` |
| `acknowledgedAt` | existing | set on acknowledge; not cleared on later evidence |
| `resolvedAt` | reuses `recoveredAt` | set on operator resolve (and metric recovery) |

Reuse rule (shared `IncidentCorrelationPolicy`):

```text
status ∈ {ACTIVE, ACKNOWLEDGED}
AND (evidenceTime - lastActivityAt) ≤ inactivity window
```

Default window **30 minutes**, config `app.incident.correlation-inactivity-minutes` (env `INCIDENT_CORRELATION_INACTIVITY_MINUTES`). Use **lastActivityAt**, not createdAt.

RESOLVED is never reused and is never auto-reopened. A later matching attack creates a **new** incident (generation suffix `:g{id}` on the correlation key). Optional `previousSimilarIncidentId` / `recurring` on the new row; AI still lists family + device/category history.

Topology overlay: only **open** incidents **inside** the inactivity window. RESOLVED and stale ACTIVE/ACKNOWLEDGED rows do not draw attack edges.

ACKNOWLEDGED stays open and correlatable; further evidence does not flip it back to ACTIVE.

## Implementation (files)

| File | Role |
| --- | --- |
| `backend/.../incident/IncidentStatus.java` | ACTIVE / ACKNOWLEDGED / RESOLVED / legacy RECOVERED; `isOpen` / `isClosed` / `forApi` |
| `backend/.../incident/IncidentProperties.java` | inactivity minutes |
| `backend/.../incident/IncidentCorrelationPolicy.java` | shared episode matcher, identity, generation keys |
| `backend/.../incident/Incident.java` | timestamps, acknowledge → ACKNOWLEDGED, resolve → RESOLVED |
| `backend/.../incident/IncidentResponse.java` | createdAt, lastActivityAt, resolvedAt, previousSimilar, recurring |
| `backend/.../incident/IncidentService.java` | Syslog/Event episode reuse |
| `backend/.../incident/IncidentController.java` | resolve allowed for ADMIN and OPERATOR |
| `backend/.../netflow/NetFlowAnomalyDetectionService.java` | same `canReuse` for syslog match and NetFlow keys |
| `backend/.../topology/TopologyService.java` | overlay filters `isOpen()` + inactivity window |
| `backend/.../ai/AiContextBuilderService.java` | timestamps + similar family lookup |
| `backend/src/main/resources/application.properties` | `app.incident.correlation-inactivity-minutes=30` |
| `frontend/src/api/aiopsApi.ts` | status union + lifecycle fields |
| `frontend/src/pages/incidents/index.tsx` | Created / Last activity; Open / RESOLVED filters |
| `frontend/src/pages/incidents/detail/index.tsx` | lifecycle dates; Resolve confirmation dialog |
| `fastapi/app/schemas.py` | optional createdAt / lastActivityAt / resolvedAt on context |

## Tests

```bash
/usr/bin/mvn -f backend/pom.xml test
```

Targeted: `IncidentCorrelationPolicyTests`, `IncidentEpisodeCorrelationTests`, `TopologyServiceTests`, `NetFlowIncidentLinkingTests`.

Coverage: ACTIVE reuse, ACKNOWLEDGED reuse, inactivity → new incident, RESOLVED never reused, `resolvedAt` set, `lastActivityAt` updates, Syslog+NetFlow same policy, 3-day-old incident vs fresh NetFlow, fresh Syslog+NetFlow same new incident, topology ignores RESOLVED and stale open, previous similar in AI context.

## Result / example

16:00 port scan → Incident #10. 16:05 more packets → #10, `lastActivityAt=16:05`. 16:10 operator resolves → `RESOLVED` / `resolvedAt`. 16:11 same source scans → Incident #11, `previousSimilarIncidentId=10`. Topology no longer shows UNDER ATTACK for #10.

A 3-day-old Possible Port Scan `10.0.0.10 → 10.10.10.20` is not reused. Today's Syslog creates a new episode; today's NetFlow attaches to that new episode, not a duplicate NetFlow-only incident.

## Out of scope

Containerlab, Syslog transport, NetFlow exporter, nfcapd, PCAP pipeline, AI provider/model, topology component relationships.

## Verify commands

```bash
/usr/bin/mvn -f backend/pom.xml test
```

Manual: ingest/scan → Acknowledge → add matching event within 30 min (same incident) → Resolve incident → scan again (new incident; old only in previous similar).

## Rapport talking points

- Incidents are time-bounded episodes, not infinite bags keyed only by src/dst/type
- Shared `IncidentCorrelationPolicy` so Syslog and NetFlow cannot disagree
- RESOLVED is terminal; ACKNOWLEDGED remains an open, correlatable operator state
- Topology reflects current attack windows, not historical closed incidents
