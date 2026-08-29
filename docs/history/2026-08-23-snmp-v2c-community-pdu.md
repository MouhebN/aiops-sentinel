# 2026-08-23 — SNMPv2c community on the wire

| Field | Value |
| --- | --- |
| Date | 2026-08-23 |
| Module | Spring Boot component checks (SNMP_BASIC / SNMP_ROUTER_METRICS) |
| Type | Bugfix |
| Status | Done |

## Context

Sentinel polls lab switches/routers/firewalls with SNMPv2c. snmpd on CORE-SW-01 accepts community `banklab` from `172.30.30.0/24`. tcpdump showed a working GetRequest from CORE-RTR-01 with `C="banklab"` and a Sentinel GetRequest from `172.30.30.3` with no `C="banklab"`; snmpd dropped it. Postgres had `snmp_community=public` on those components (save default).

## Problem

Connection Test and scheduled monitoring built a GetRequest whose community OCTET STRING was empty or the silent default `public`, not the component's configured community. Blank community is encoded as a zero-length OCTET STRING; tcpdump prints `SNMPv2c { GetRequest... }` with no `C=`. snmpd does not reply.

## Design

- Persist `snmpCommunity` as given (no default `public`).
- Require a non-blank community when SNMP methods are selected.
- Encode the persisted community as the SNMPv2c OCTET STRING.
- If community is missing at check time, fail with a configuration error; do not send an empty community.
- Mask the community in check logs (`b***`, not `banklab`).

Connection Test and the scheduler both call `ComponentCheckService` → `snmpGet` → `SnmpV2cGetEncoder.encodeGet`.

## Implementation (files)

| File | Role |
| --- | --- |
| `backend/src/main/java/com/aiops/backend/component/SnmpV2cGetEncoder.java` | SNMPv2c GetRequest encoder; require/mask community |
| `backend/src/main/java/com/aiops/backend/component/ComponentCheckService.java` | Use persisted community; encoder; masked logs |
| `backend/src/main/java/com/aiops/backend/component/MonitoredComponentService.java` | Stop defaulting community to `public` |
| `backend/src/main/java/com/aiops/backend/component/SaveMonitoredComponentRequest.java` | SNMP methods require community |
| `frontend/src/pages/components/index.tsx` | Require community in save payload |
| `frontend/src/pages/components/detail/index.tsx` | Do not display missing community as `public` |

## Tests

| Test | Result |
| --- | --- |
| `SnmpV2cGetEncoderTests.encodeGetPutsConfiguredCommunityInPdu` | PDU contains OCTET STRING `banklab` |
| `SnmpV2cGetEncoderTests.encodeGetRejectsBlankCommunity` | IllegalArgumentException |
| `ComponentLifecycleTests.snmpCommunityIsPersistedAndReturned` | `banklab` round-trips |

## Result / example

```text
SNMPv2c C="banklab" { GetRequest ... .1.3.6.1.2.1.1.1.0 }
SNMPv2c C="banklab" { GetResponse ... sysDescr = CORE-SW-01 ... }
```

## Out of scope

- Containerlab / snmpd.conf
- Topology, monitoring methods, Sentinel SNMP client library swap

## Verify commands

```bash
mvn -pl backend -Dtest=SnmpV2cGetEncoderTests,ComponentLifecycleTests#snmpCommunityIsPersistedAndReturned test
# tcpdump on CORE-SW-01 UDP 161 while Connection Test / scheduler runs
```

## Rapport talking points

- SNMPv2c community is a BER OCTET STRING inside the message; empty string ≠ omitted field, but tcpdump hides `C=` when length is 0
- Check path and save path must not substitute `public` when the lab community is `banklab`
- Logs must not print the full community
