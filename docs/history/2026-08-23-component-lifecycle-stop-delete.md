# 2026-08-23 — Persistent Stop Monitoring and Component delete

| Field | Value |
| --- | --- |
| Date | 2026-08-23 |
| Module | Spring Boot component monitoring + React Components / Infrastructure map |
| Type | Bugfix / lifecycle |
| Status | Done |

## Context

Components already had a persisted boolean `enabled` on `monitored_components`, `PATCH /api/components/{id}/enable|disable`, `DELETE /api/components/{id}`, and a single `@Scheduled(fixedDelay = 5000)` poller that queried `findAllByEnabledTrueOrderByCreatedAtDesc()`. Operators still could not trust Stop Monitoring, and the UI had no Delete action.

## Problem

Stop Monitoring looked temporary:

1. `update()` treated a missing `enabled` field as `true`, so any later PUT (edit form, refresh save) turned monitoring back on.
2. `disable()` wiped operational `lastStatus` to `UNKNOWN`, so Stop looked like a health change / DOWN.
3. `PATCH /api/components/{id}/status` is `permitAll` for `generic_collector.py`. After Stop, collector updates still wrote `lastCheckedAt` / status, so the UI appeared to keep checking.
4. `checkComponent()` did not re-check `isEnabled()`.
5. The list/detail pages had no Delete button, even though `DELETE /api/components/{id}` existed.

The poller is **not** a per-component `ScheduledFuture`. Inventing a second scheduler would have duplicated lifecycle.

## Design

Reuse `MonitoredComponent.enabled`. Do not add `monitoringEnabled` as a second column.

| Action | Persist | Runtime |
| --- | --- | --- |
| Start Monitoring | `enabled = true`, `lastCheckedAt = null` (due immediately) | next poller cycle runs checks |
| Stop Monitoring | `enabled = false` only; keep last operational status | poller + collector status PATCH skip the row |
| Check Now | does **not** change `enabled` | one-off `testConnection()`; allowed while stopped |
| Delete | disable, delete relations + thresholds, delete row | poller try/catch if an id disappears mid-cycle |

JSON:

- `/api/components` keeps `enabled` (existing field)
- `/api/topology` nodes expose `monitoringEnabled` (= `component.isEnabled()`) next to `operationalStatus`

Events / Incidents store `deviceId` as the string `component-{id}` (no FK). Delete does not cascade them. Thresholds are monitoring config and are removed.

Check Now convention: remain available when monitoring is stopped. Tooltip: “One-off check (does not resume monitoring)”. Disabling the button would hide a useful diagnostic.

## Implementation (files)

| File | Role |
| --- | --- |
| `backend/.../component/ComponentMonitoringPolicy.java` | `shouldRunScheduledCheck` / `shouldAcceptExternalStatusUpdate` |
| `backend/.../component/MonitoredComponent.java` | `enable()` / `disable()` no longer mutate operational status on stop |
| `backend/.../component/MonitoredComponentService.java` | PUT preserves `enabled` when null; delete stops then detaches; Check Now cannot re-enable |
| `backend/.../component/ComponentMonitoringScheduler.java` | enabled-only query + policy re-check + per-id try/catch |
| `backend/.../component/ComponentCheckService.java` | skip scheduled check if stopped; Check Now persists last status without touching `enabled` |
| `backend/.../metric/MetricThresholdRepository.java` | `deleteByComponentId` |
| `backend/.../topology/TopologyNodeResponse.java` | `monitoringEnabled` |
| `backend/.../topology/TopologyService.java` | map `component.isEnabled()` |
| `backend/src/test/java/com/aiops/backend/ComponentLifecycleTests.java` | persist / scheduler / delete / history |
| `backend/src/test/java/com/aiops/backend/component/ComponentMonitoringPolicyTests.java` | due/skip matrix |
| `frontend/src/pages/components/index.tsx` | Monitoring chip, Delete confirm, snackbar |
| `frontend/src/pages/components/detail/index.tsx` | Start/Stop labels, Delete confirm, navigate after delete |
| `frontend/src/pages/topology/TopologyNodes.tsx` | dashed / muted “Monitoring stopped” |
| `frontend/src/pages/topology/TopologyDetailsDrawer.tsx` | monitoring chip ≠ operational status |
| `frontend/src/api/aiopsApi.ts` | `TopologyNode.monitoringEnabled` |

Delete remains `@PreAuthorize("hasRole('ADMIN')")` (`canManageComponents`). OPERATOR keeps Check Now only.

## Tests

| Test | Result |
| --- | --- |
| `ComponentMonitoringPolicyTests` (4) | Pass |
| `ComponentLifecycleTests` (8) | Pass |
| `TopologyServiceTests` (5) | Pass |
| Command | `cd backend && bash ./mvnw -Dtest=ComponentLifecycleTests,ComponentMonitoringPolicyTests,TopologyServiceTests test` |

Covered: stop persists `enabled=false` and keeps `UP`; scheduler does not restart; start persists `true` and clears `lastCheckedAt`; collector PATCH ignored while stopped; Check Now does not re-enable; PUT with null `enabled` does not flip stopped → started; delete removes relations/topology node, 404s the component, keeps events/incidents; stop/start does not duplicate rows.

Startup does not re-enable anyone: there is no seeder/`CommandLineRunner` that sets `enabled=true`. Restart is the same as “read `enabled` from DB”. Duplicate in-memory jobs cannot exist because there is one poller, not a registry of `ScheduledFuture`s.

## Result / example

Stop `BANK-CAMERA-01`:

```json
{ "id": 12, "name": "BANK-CAMERA-01", "lastStatus": "UP", "enabled": false }
```

Topology node stays:

```json
{ "operationalStatus": "UP", "monitoringEnabled": false, "securityState": "NORMAL" }
```

UI shows **Monitoring: STOPPED** next to operational **UP**, not DOWN.

## Out of scope

- Redesigning monitoring as per-component `ScheduledFuture` jobs
- Cascade-deleting events, incidents, metric samples, or AI reports
- Changing collector Python beyond the existing `GET /api/components/enabled` loop (backend now also ignores status PATCH when stopped)

## Verify commands

```bash
cd backend && bash ./mvnw -Dtest=ComponentLifecycleTests,ComponentMonitoringPolicyTests,TopologyServiceTests test
```

UI: Components → Stop monitoring → wait > check interval → refresh → still STOPPED → restart backend → still STOPPED → Start monitoring → checks resume. Delete a disposable component that has a topology relation → node and edge gone, historical events remain.

## Rapport talking points

Monitoring enablement is a persisted operator intent (`enabled`), independent of last health (`lastStatus`). A 5-second poller must select enabled rows only and must not treat collector heartbeats as permission to keep writing status. Historical SOC artifacts stay after inventory delete because they are keyed by `deviceId` string, not a cascading FK.
