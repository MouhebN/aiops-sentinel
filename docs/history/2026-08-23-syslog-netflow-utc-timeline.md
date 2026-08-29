# 2026-08-23 — Syslog / NetFlow UTC timeline alignment

| Field | Value |
| --- | --- |
| Date | 2026-08-23 |
| Module | Syslog RFC3164 ingestion, Containerlab syslog sender, NetFlow v5 timestamps |
| Type | Bugfix / timezone |
| Status | Done |

## Context

Bank-lab PORT_SCAN evidence is a Syslog `FIREWALL_DENY` (UDP 5514) plus NetFlow v5 (nfcapd / Import Latest). Correlation requires the same src/dst inside a **30-minute** window. Storage is UTC `Instant` (`…Z` in JSON).

## Problem

Observed incidents for `10.0.0.10 → 10.10.10.20`:

| Source | firstSeen (stored UTC) |
| --- | --- |
| Syslog SECURITY `incidentId=10` | `2026-08-22T14:33:05Z` |
| NetFlow `incidentId=18` | `2026-08-22T15:43:10.385Z` |

The scans were ~10 minutes apart. Stored gap was ~70 minutes, so Syslog-first correlation failed and NetFlow created a second incident.

RFC3164 line from the lab: `<134>Aug 22 15:33:05 BANK-FW-01 ...` (no timezone). Backend offset `+01:00` (`Africa/Tunis`).

## Where the one-hour offset was introduced

| Step | Clock | Result |
| --- | --- | --- |
| `forward-syslog.sh` | `LC_ALL=C date '+%b %d %H:%M:%S'` on Alpine (TZ=UTC, often no tzdata) | Wall clock **UTC**, e.g. `Aug 22 15:33:05` |
| `SyslogParserService` | Interpret RFC3164 in `app.syslog.default-timezone=Africa/Tunis` | `15:33:05+01:00` → **`14:33:05Z`** (one hour early) |
| `export-scan-flows.py` | `unix_secs = int(time.time())`; First/Last = monotonic vs `boot` | Valid NetFlow v5; nfdump `%tsr` epoch ≈ real UTC, e.g. **`15:43:10Z`** |
| `NfdumpCsvNetFlowRecordParser` | `Instant.ofEpochSecond` | No timezone shift |

NetFlow clocks were already split correctly (`time.time()` for unix_secs, `time.monotonic()` for SysUptime/First/Last). The mismatch was Syslog sender UTC wall-clock vs parser Africa/Tunis.

## Design

Keep production RFC3164 behaviour: interpret naive stamps in `app.syslog.default-timezone`, store UTC. Do not widen the 30-minute window and do not subtract a hardcoded hour.

Smallest portable fix: lab sender emits the **same** zone the parser uses (`Africa/Tunis`, POSIX `CET-1` without tzdata). `SYSLOG_TIMESTAMP_TZ` overrides if the Sentinel zone changes.

Canonical timeline remains UTC after conversion. A sender stamp generated in the parser zone round-trips to the real Instant in UTC, America/New_York, Europe/Paris, etc.

## Implementation (files)

| File | Role |
| --- | --- |
| `pfe-containerlab/services/firewall/forward-syslog.sh` | RFC3164 stamp in Africa/Tunis / `SYSLOG_TIMESTAMP_TZ` / POSIX `CET-1` |
| `pfe-containerlab/services/firewall/setup-firewall.sh` | Install tzdata when missing so IANA `Africa/Tunis` works |
| `pfe-containerlab/services/netflow/export-scan-flows.py` | Document/split unix vs monotonic encoding (wire format unchanged) |
| `backend/.../SyslogParserService.java` | Debug log of zone → stored UTC |
| `backend/.../NfdumpCsvNetFlowRecordParser.java` | Comment: `%tsr` is Unix epoch UTC |
| `backend/src/test/java/com/aiops/backend/SyslogNetFlowTimelineTests.java` | Ingestion + correlation 10-minute gap |
| `backend/src/test/java/com/aiops/backend/syslog/SyslogParserTimestampTests.java` | Zone matrix + UTC-sender mismatch |
| `backend/src/test/java/com/aiops/backend/NfdumpCsvNetFlowRecordParserTests.java` | Epoch stable if JVM TZ is America/New_York |
| `docs/testing-syslog-ingestion.md` | Sender/parser zone contract |

## Tests

- A. Syslog DENY and nfdump epoch 10 minutes later → stored Instants 10 minutes apart (not 70).
- B. Same SRC/DST inside 30 minutes → NetFlow attaches to the Syslog SECURITY incident.
- C. Sender stamp in parser zone does not introduce a ±1 hour offset vs Unix NetFlow.
- D. RFC3164 wall clock `Aug 22 15:33:05` maps to the configured zone only (`UTC`, `Africa/Tunis`, `Europe/Paris`, `America/New_York`); stamp generated **in** that zone always recovers the same UTC Instant; nfdump epoch is unchanged if the JVM default zone is New York.

## Result or example

Correct pairing at real event `2026-08-22T15:33:05Z` with parser `Africa/Tunis`:

```text
RFC3164: Aug 22 16:33:05
stored Syslog UTC: 2026-08-22T15:33:05Z
nfdump unix_secs ~10 min later: 2026-08-22T15:43:10Z
gap: 10 minutes (inside 30-minute correlation window)
```

Buggy pairing (UTC `date` + Tunis parser): Syslog `14:33:05Z`, NetFlow `15:43:10Z`, gap 70 minutes.

## Out of scope

- Correlation window, rules, nftables policy, Containerlab topology
- Hardcoded −1 hour or date-specific hacks
- Changing production default `Africa/Tunis` to UTC

## Verify commands

```bash
cd backend
./mvnw test -Dtest=SyslogNetFlowTimelineTests,SyslogParserTimestampTests,NfdumpCsvNetFlowRecordParserTests,NetFlowIncidentLinkingTests
./mvnw test
```

Lab: reload firewall scripts, run two scans ~10 minutes apart, Import Latest Flows; Syslog and NetFlow must share one SECURITY incident.

## Rapport talking points

- RFC3164 is timezone-less; the receiver zone is a **contract** with the sender, not a property of the log line.
- NetFlow v5 `unix_secs` is already UTC; First/Last must stay SysUptime-relative (monotonic), never `time.time()`.
- Correlation failed on a 1-hour parse error, not on a window that was “too small”.
