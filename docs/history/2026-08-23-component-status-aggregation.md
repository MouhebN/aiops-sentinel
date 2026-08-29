# 2026-08-23 — Component operational-status aggregation

| Field | Value |
| --- | --- |
| Date | 2026-08-23 |
| Module | Spring Boot component checks + React topology |
| Type | Bugfix / status semantics |
| Status | Done |

## Context

A monitored component can run several methods at once (PING, HTTP_HEALTH, TCP_PORT, RTSP_HEALTH, SNMP, …). Scheduler, Check Now, and Connection Test all call `ComponentCheckService` and persist `lastStatus`. Topology maps `lastStatus` to `operationalStatus` and shows it on the infrastructure map.

## Problem

Roll-up was binary: **any method DOWN made the whole component DOWN**.

That hid mixed health:

| Component | Checks | Wrong roll-up | Expected |
| --- | --- | --- | --- |
| ATM-01 | PING UP, TCP_PORT DOWN | DOWN | DEGRADED |
| BANK-CAMERA-01 | RTSP fail, HTTP reachable/unhealthy | DOWN if HTTP is DOWN | DEGRADED unless every enabled check is DOWN |
| BANK-SRV-01 | HTTP_HEALTH DOWN, TCP_PORT DOWN | DOWN | DOWN (unchanged) |

Stopped monitoring was already a separate `enabled=false` path; it must not be coerced to DOWN.

Root cause: `ComponentCheckService.resolveStatus()` returned DOWN as soon as any check was DOWN, before considering remaining UP / WARNING / DEGRADED results.

## Design

Do **not** change per-method check outcomes. Extract one aggregator used by scheduler (`checkComponent`) and Check Now / Connection Test (`testConnection`).

Exact rule on enabled-check statuses (UNKNOWN ignored):

1. No usable check statuses → `UNKNOWN`
2. At least one DOWN, and no UP / WARNING / DEGRADED → `DOWN`
3. At least one DOWN or at least one DEGRADED (mixed or all-DEGRADED) → `DEGRADED`
4. At least one WARNING and no DOWN / DEGRADED → `WARNING`
5. Otherwise → `UP`

So:

- all enabled checks UP → UP
- at least one UP and at least one DOWN → DEGRADED
- all enabled checks DOWN → DOWN
- existing threshold WARNING is kept when nothing is DOWN/DEGRADED
- scheduler still skips `enabled=false`; stop does not rewrite status to DOWN

`lastSeenAt` is updated when any check is UP or WARNING, including mixed DEGRADED.

Topology: StatusChip already uses `info` for DEGRADED vs `error` for DOWN. Node border when security is NORMAL: DEGRADED = amber, DOWN = red. Stopped monitoring stays dashed / muted and keeps the last operational chip.

## Implementation (files)

| File | Role |
| --- | --- |
| `backend/.../component/ComponentStatusAggregator.java` | Shared roll-up |
| `backend/.../component/ComponentCheckService.java` | `resolveStatus` delegates; Check Now message for mixed; event pick for UP+DOWN → DEGRADED |
| `backend/.../component/ComponentStatusAggregatorTests.java` | UP+UP, UP+DOWN, DOWN+DOWN, empty ≠ DOWN, WARNING |
| `backend/src/test/java/com/aiops/backend/ComponentLifecycleTests.java` | Stopped monitoring remains UP, not DOWN |
| `frontend/src/pages/topology/TopologyNodes.tsx` | Operational border DEGRADED vs DOWN |
| `frontend/src/pages/components/index.tsx` | Check result alert: DEGRADED = warning |
| `frontend/src/pages/components/detail/index.tsx` | Same alert severity |

## Tests

| Test | Result |
| --- | --- |
| `allUpIsUp` | UP + UP → UP |
| `upAndDownIsDegraded` | UP + DOWN → DEGRADED |
| `allDownIsDown` | DOWN + DOWN → DOWN |
| `noEnabledChecksIsUnknownNotDown` | empty / UNKNOWN → UNKNOWN, not DOWN |
| `warningIsPreservedWhenNoFailure` | UP + WARNING → WARNING |
| `downAndDegradedIsDegradedNotDown` | HTTP DOWN + RTSP DEGRADED → DEGRADED |
| `stopMonitoringPersistsAndKeepsOperationalStatus` | `enabled=false` keeps UP |
| `schedulerIgnoresStoppedComponentAndDoesNotRestartIt` | poller does not force DOWN |

## Result or example

ATM-01 with PING UP and TCP_PORT DOWN is **DEGRADED**. BANK-SRV-01 with HTTP and TCP both DOWN stays **DOWN**. Stop Monitoring still shows the last operational chip plus “Monitoring stopped”, not DOWN.

## Out of scope

Per-method results (PING, TCP, HTTP 503, RTSP `DEGRADED`, SNMP thresholds). Containerlab / snmpd. Topology graph layout.

## Verify commands

```bash
cd backend && bash ./mvnw -Dtest=ComponentStatusAggregatorTests,ComponentLifecycleTests test
```

## Rapport talking points

- Multi-method monitoring needs a three-state roll-up (UP / DEGRADED / DOWN), not AND of all checks.
- One aggregator for scheduler and Check Now avoids UI vs poller drift.
- Stopped monitoring is lifecycle (`enabled`), not an operational DOWN.
- Topology must paint DEGRADED and DOWN with different chips and borders.
