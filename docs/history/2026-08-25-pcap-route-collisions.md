# 2026-08-25 — Packet capture REST route collisions

| Field | Value |
| --- | --- |
| Date | 2026-08-25 |
| Module | Spring Boot PCAP REST |
| Type | Bugfix |
| Status | Done |

## Context

Automated capture added `GET /api/packet-captures/provider-health` next to the existing analysis lookup `GET /api/packet-captures/{id}`.

## Problem

Spring matched `provider-health` as `{id}` and tried to convert it to `Long`:

```text
java.lang.NumberFormatException: For input string: "provider-health"
```

The health handler never ran. Non-numeric IDs could become HTTP 500 via the generic exception handler.

## Design

Keep frontend paths unchanged. Constrain every numeric path variable with `{name:\\d+}` so literals (`provider-health`, `preview`, `start`, `jobs`, `cancel`) cannot bind as IDs. Unmatched non-numeric segments no longer convert to `Long`; Spring then raises `NoResourceFoundException`, which is mapped to HTTP 404. `MethodArgumentTypeMismatchException` is mapped to HTTP 400 if conversion still occurs.

## Implementation (files)

| File | Change |
| --- | --- |
| `PacketCaptureAnalysisController.java` | `{id:\\d+}`, `{incidentId:\\d+}`, `{analysisId:\\d+}` |
| `PacketCaptureJobController.java` | `{incidentId:\\d+}`, `{jobId:\\d+}` |
| `ApiExceptionHandler.java` | type-mismatch → 400 |
| `PacketCaptureRouteMappingTests.java` | MVC routing tests |

## Tests

`PacketCaptureRouteMappingTests`: provider-health, numeric analysis id, `/abc` → 404, preview, start, jobs list, job by id, cancel.

## Result or example

`GET /api/packet-captures/provider-health` reaches `providerHealth()`. `GET /api/packet-captures/123` still returns the analysis. `GET /api/packet-captures/abc` is 404, not 500.

## Out of scope

Frontend contract changes. Other non-PCAP `{id}` vs literal collisions.

## Verify commands

```bash
cd backend && ./mvnw -q test -Dtest=PacketCaptureRouteMappingTests,PacketCaptureJobServiceTests
```

## Rapport talking points

- Literal health path must not share an unconstrained `{id}` pattern.
- Regex-constrained path variables prevent NumberFormatException on static segments.
