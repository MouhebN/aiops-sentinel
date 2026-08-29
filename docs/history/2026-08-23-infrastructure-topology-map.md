# 2026-08-23 — Infrastructure / network topology map

| Field | Value |
| --- | --- |
| Date | 2026-08-23 |
| Module | Spring Boot topology API + React Infrastructure map |
| Type | Feature |
| Status | Done |

## Context

AIOps Sentinel already stores monitored Components, operational status (`UP` / `WARNING` / `DEGRADED` / `DOWN`), and active SECURITY incidents with Syslog / NetFlow / PCAP evidence. Operators still had no visual map of how components are connected, which node is targeted, or whether a source IP is an external attacker.

Containerlab (`BANK-FW-01`, `CORE-RTR-01`, …) is only the validation lab. Topology must work from Sentinel’s own database so a later real bank inventory can use the same page.

## Problem

Without a topology view:

- connections between components were not modeled
- operational DOWN and a security incident looked like the same kind of problem
- an attacker IP such as `10.0.0.10` had no place to appear unless someone created a fake Component
- firewall Syslog association could be confused with the actual destination of a `PORT_SCAN`

## Design

Persist generic `ComponentRelation` rows (manual V1, no LLDP/CDP, no Containerlab YAML).

`GET /api/topology` builds a frontend graph:

- **nodes** from `monitored_components`
- **links** from `component_relations`
- **externalEntities** for source IPs that are not a Component IP
- **activeAttacks** from `IncidentStatus.ACTIVE` + `category=SECURITY`

`securityState` is derived, not stored:

| State | When |
| --- | --- |
| `UNDER_ATTACK` | Destination IP maps to a Component and the incident is `PORT_SCAN` or `CRITICAL` |
| `TARGETED` | Destination IP maps to a Component, lower severity |
| `SUSPICIOUS_ACTIVITY` | Component reported the incident (deviceName / `component-{id}`) but is not the destination |
| `NORMAL` | No matching active security incident |

Operational status is left unchanged. `BANK-SRV-01` can be `UP` and `UNDER_ATTACK`.

Attacker IPs are never inserted into `monitored_components`.

## Implementation (files)

| File | Role |
| --- | --- |
| `backend/.../topology/ComponentRelation.java` | JPA entity, unique (source, target, type), `@OnDelete CASCADE` |
| `backend/.../topology/ComponentRelationType.java` | `CONNECTED_TO`, `ROUTES_TO`, `DEPENDS_ON`, `PROTECTS` |
| `backend/.../topology/ComponentRelationRepository.java` | list, exists, delete-by-endpoint |
| `backend/.../topology/ComponentRelationService.java` | create/list/delete, reject self-loop and duplicates |
| `backend/.../topology/TopologyController.java` | `/api/topology` + `/api/topology/relations` |
| `backend/.../topology/TopologyService.java` | graph + security overlay |
| `backend/.../topology/TopologyIpExtractor.java` | SRC/DST from details, rawLog, Event.sourceIp, PCAP text |
| `backend/.../component/MonitoredComponentService.java` | delete component also deletes its relations |
| `backend/.../audit/AuditAction.java` | `COMPONENT_RELATION_CREATED` / `DELETED` |
| `backend/src/test/java/com/aiops/backend/TopologyServiceTests.java` | graph, attack overlay, constraints |
| `backend/src/test/java/com/aiops/backend/topology/TopologyIpExtractorTests.java` | IP parsing |
| `frontend/src/pages/topology/*` | React Flow canvas, nodes, drawer, legend, manage connections |
| `frontend/src/api/aiopsApi.ts` | topology DTOs and CRUD |
| `frontend/src/routes/router.tsx`, `path.ts`, `MenuItems.ts` | `/topology` + **Infrastructure map** |
| `frontend/package.json` | `@xyflow/react` |

## Tests

| Test | Result |
| --- | --- |
| `TopologyServiceTests` (5) | Pass |
| `TopologyIpExtractorTests` (2) | Pass |
| Frontend unit tests | None (project has no Jest/Vitest runner) |
| `tsc --noEmit` | Pass |

## Result / example

Lab overlay after a PORT_SCAN to `10.10.10.20`:

```text
external:10.0.0.10  --PORT_SCAN · CRITICAL-->  BANK-SRV-01
                                                   UP + UNDER ATTACK
BANK-FW-01                                         SECURITY EVENT (reporter, not target)
```

## Out of scope

LLDP/CDP, auto-discovery, geo maps, Containerlab YAML parsing, WebSocket live animation, RAG, AI prompt changes.

## Verify commands

```bash
cd backend && bash ./mvnw -Dtest=TopologyServiceTests,TopologyIpExtractorTests test
cd frontend && node node_modules/typescript/bin/tsc --noEmit
```

Restart Spring Boot once so Hibernate `ddl-auto=update` creates `component_relations`.

## Rapport talking points

- Topology is an overlay on the existing CMDB (Components) and SOC incidents, not a second inventory.
- Operational health and security state are two independent badges; that is the bank-operator-facing point.
- External attacker IPs stay ephemeral graph nodes, which avoids polluting the monitored-asset list.
