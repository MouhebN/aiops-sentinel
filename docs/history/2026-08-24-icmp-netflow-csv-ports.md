# 2026-08-24 — ICMP nfdump CSV type.code parsing

| Field | Value |
| --- | --- |
| Date | 2026-08-24 |
| Module | Spring Boot NetFlow import (`NfdumpCsvNetFlowRecordParser`) |
| Type | Bugfix |
| Status | Done |

## Context

Lab fprobe exports real ICMP (ping) as NetFlow v5. `nfdump` CSV uses `%sp`/`%dp` for ICMP **type.code** (`8.0` echo request, `0.0` echo reply), not TCP/UDP ports. Protocol column is `1` (ICMP).

## Problem

`NfdumpCsvNetFlowRecordParser.parseInteger()` called `Integer.valueOf("8.0")` / `"0.0"` **before** protocol mapping. Valid ICMP rows were skipped (`For input string: "8.0"`). A file with only ICMP then failed the whole import (`No valid NetFlow records found`).

## Design

Map protocol first. If ICMP:

- keep `protocol = ICMP`
- set `sourcePort` / `destinationPort` to **null** (model is integer ports; type/code stay in `rawRecord`)
- do not treat type/code as scan ports

TCP/UDP still require integer ports. Decimal TCP ports remain invalid. `applyPortScanRule` already requires `TCP` and a non-null destination port.

## Implementation (files)

| File | Role |
| --- | --- |
| `backend/src/main/java/com/aiops/backend/netflow/NfdumpCsvNetFlowRecordParser.java` | ICMP skip integer port parse |
| `backend/src/test/java/com/aiops/backend/NfdumpCsvNetFlowRecordParserTests.java` | TCP / ICMP 8.0 / ICMP 0.0 / malformed TCP |
| `backend/src/test/java/com/aiops/backend/NetFlowImportServiceIntegrationTests.java` | ICMP import, 0 PORT_SCAN incidents |
| `backend/src/test/java/com/aiops/backend/NetFlowIncidentLinkingTests.java` | ICMP with type-like dest integers is not PORT_SCAN |

## Tests

```bash
cd backend && ./mvnw -q test -Dtest=NfdumpCsvNetFlowRecordParserTests,NetFlowImportServiceIntegrationTests,NetFlowIncidentLinkingTests
```

## Result / example

CSV `...,10.0.0.10,10.0.0.1,0,8.0,1,...` → ICMP, ports null, not PORT_SCAN.

## Out of scope

fprobe, nfcapd, Containerlab, ICMP type/code columns, PORT_SCAN formula.

## Verify commands

Same Maven command as Tests.

## Rapport talking points

- nfdump ICMP `type.code` is not a transport port
- Parser must classify protocol before parsing port columns
