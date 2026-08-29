# 2026-08-22 — Idempotent NetFlow import / correlation_key reuse

| Field | Value |
| --- | --- |
| Date | 2026-08-22 |
| Module | Spring Boot NetFlow import (`NetFlowImportService`, `NetFlowAnomalyDetectionService`) |
| Type | Bugfix / idempotency |
| Status | Done |

## Context

Import Latest Flows runs `nfdump -R <dataDir>` and then PORT_SCAN detection. Incidents use unique `incidents.correlation_key` values such as `NETFLOW:PORT_SCAN:<src>:<dst>` and a generation suffix `:g<id>`. Operators re-click import against the same nfcapd directory (full history, no file checkpoint).

## Problem

Re-importing the same PORT_SCAN (or minting the next generation key after an existing `:g5` row) executed:

```text
INSERT INTO incidents (correlation_key) VALUES ('NETFLOW:PORT_SCAN:192.168.0.15:192.168.0.1:g5')
```

when that key already existed. PostgreSQL raised `duplicate key value violates unique constraint`. Spring wrapped it as `DataIntegrityViolationException` and the whole import returned HTTP 500.

`nextCorrelationKey` always returned `baseKey + ":g" + baseIncidentId` without checking whether that generation key was already occupied. `findReusableCorrelationMatch` only looked at the base key and that one generation, and only reused when the time-window/active rules passed. A second occupied `:g5` therefore caused an INSERT instead of reuse.

The unique constraint must stay. Old incidents must not be deleted to “fix” the click.

## Design

Keep Syslog-first correlation:

```text
PORT_SCAN flow
  → matching recent Syslog SECURITY incident (src/dst/time) → attach
  → else reusable NetFlow incident in the correlation-key family → attach
  → else INSERT a free key only
```

Get-or-create rules:

1. Skip linking when `network_flows.incident_id` is already set (no extra event, no extra audit).
2. Scan the family `baseKey` and `baseKey:g*` and reuse a time-compatible ACTIVE/recent incident.
3. `nextFreeCorrelationKey` walks `:g<id>` until the key does **not** exist (never INSERT an occupied key).
4. Before INSERT, look up the chosen key again.
5. INSERT runs in `REQUIRES_NEW` (`NetFlowIncidentPersistence`). A remaining unique-constraint race rolls back only the nested transaction; the outer import loads the existing row and continues.
6. Attach a new `NETFLOW` event only if that incident does not already have NETFLOW evidence for the same src/dst/destination port.

`recordHash` still skips identical nfdump rows. This change does not add nfcapd high-water checkpointing.

## Implementation (files)

| File | Role |
| --- | --- |
| `backend/src/main/java/com/aiops/backend/netflow/NetFlowAnomalyDetectionService.java` | Family reuse, free-key allocation, duplicate evidence skip |
| `backend/src/main/java/com/aiops/backend/netflow/NetFlowIncidentPersistence.java` | Nested-transaction INSERT + constraint-conflict detection |
| `backend/src/main/java/com/aiops/backend/incident/IncidentRepository.java` | `findByCorrelationKeyStartsWithOrderByLastSeenAtDesc` |
| `backend/src/main/java/com/aiops/backend/netflow/NetFlowImportService.java` | `[NetFlow-import]` logs; comment that `-R` reads full history |
| `backend/src/test/java/com/aiops/backend/NetFlowIncidentLinkingTests.java` | Repeat analyze + occupied `:g<id>` |
| `backend/src/test/java/com/aiops/backend/NetFlowImportServiceIntegrationTests.java` | Repeat Import Latest success |

## Tests

- New PORT_SCAN → one incident, seven NETFLOW events (one per destination port).
- Analyze the same `NetworkFlow` list again → same incident id, same `eventCount`.
- Occupied `NETFLOW:PORT_SCAN:192.168.0.15:192.168.0.1:g{baseId}` plus a stale base row → no `DataIntegrityViolationException`; a new free generation is used.
- Fake `nfdump` CSV imported twice → first SUCCESS 7 imported / 1 incident; second SUCCESS 0 imported / 0 incidents; flow count and event count unchanged.
- Existing Syslog src/dst tests still attach NetFlow to the Syslog SECURITY incident.

## Result or example

```text
[NetFlow-correlation] reused existing NetFlow incident correlationKey=NETFLOW:PORT_SCAN:10.0.0.10:10.10.10.20
[NetFlow-import] skipped duplicate evidence count=7
[NetFlow-import] import completed sourceId=1 recordsRead=7 recordsImported=0 recordsSkipped=7 suspiciousFlows=0 incidentsCreated=0
```

HTTP 200 on a second Import Latest click. Unique constraint on `correlation_key` remains.

## Out of scope

- nfcapd file checkpoint / import only “new” files
- Containerlab exporter, frontend
- Removing or relaxing the unique constraint
- Deleting historical incidents

## Verify commands

```bash
cd backend
./mvnw test -Dtest=NetFlowIncidentLinkingTests,NetFlowImportServiceIntegrationTests
./mvnw test
```

UI: Import Latest Flows twice on the same source (`/data/netflow`). Second click must be SUCCESS, not HTTP 500.

## Rapport talking points

- Unique `correlation_key` is the last line of defence; the importer must not INSERT a key it already knows.
- Generation suffix `:g<id>` is for a **new** activity window, not for re-reading the same nfcapd files.
- `nfdump -R` always walks the whole collector directory; idempotency is hash skip + incident get-or-create, not a file cursor.
- Syslog FIREWALL_DENY and NetFlow PORT_SCAN still share one SECURITY incident when src/dst/time match.
