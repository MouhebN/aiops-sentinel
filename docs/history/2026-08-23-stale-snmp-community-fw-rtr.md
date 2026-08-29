# 2026-08-23 — Stale SNMP community on FW/RTR

| Field | Value |
| --- | --- |
| Date | 2026-08-23 |
| Module | Spring Boot components (SNMP_BASIC / SNMP_ROUTER_METRICS) |
| Type | Bugfix |
| Status | Done |

## Context

CORE-SW-01 (`172.30.30.12`, SNMP_BASIC, `banklab`) was UP. BANK-FW-01 and CORE-RTR-01 still failed. Containerlab/snmpd were not changed.

## Problem

Postgres still had `snmp_community=public` on BANK-FW-01 and CORE-RTR-01 (leftover from the old save default). The UI could look fine while GET/DTO returned `public`. Frontend `toPayload` sent `snmpCommunity: trim() || undefined`, so a blank field omitted the key; PUT then stored null/public and wiped `banklab`.

SNMP_ROUTER_METRICS already used the same `snmpGet` helper as SNMP_BASIC (sysUpTime `1.3.6.1.2.1.1.3.0`). With `public` that GET timed out.

## Design

Keep the persisted community on update when the request omits it. Always send `snmpCommunity` from the form. Write `banklab` on the two stale rows via the normal PUT API.

## Implementation (files)

| File | Role |
| --- | --- |
| `backend/.../MonitoredComponentService.java` | Update keeps existing community if request value is null |
| `frontend/.../pages/components/index.tsx` | Always include `snmpCommunity` in save payload |
| `backend/.../ComponentLifecycleTests.java` | Update-without-community keeps `banklab` |

## Tests

| Test | Result |
| --- | --- |
| Persist `banklab` on create | Pass |
| PUT with null community keeps `banklab` | Pass |
| Connection Test FW SNMP_BASIC | UP |
| Connection Test RTR SNMP_ROUTER_METRICS | UP |
| Connection Test SW SNMP_BASIC | UP |

## Result / example

```text
BANK-FW-01  snmp_community=banklab  SNMP_BASIC UP
CORE-RTR-01 snmp_community=banklab  SNMP_ROUTER_METRICS UP  ifNumber=4
CORE-SW-01  snmp_community=banklab  SNMP_BASIC UP
```

## Out of scope

- Containerlab / snmpd.conf, topology, IPs

## Verify commands

```bash
# GET /api/components/{id} snmpCommunity == banklab
# POST /api/components/{id}/check → resultStatus UP
```

## Rapport talking points

- Stale `public` in DB after a default-community bug is a data problem, not a second snmpd bug
- PUT must not treat omitted `snmpCommunity` as clear
- SNMP_ROUTER_METRICS failed on the same v2c GET path once community was wrong
