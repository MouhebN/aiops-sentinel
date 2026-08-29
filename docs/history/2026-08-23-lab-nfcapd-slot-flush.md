# 2026-08-23 — Lab nfcapd slot flush after NetFlow export

| Field | Value |
| --- | --- |
| Date | 2026-08-23 |
| Module | Containerlab NetFlow exporter (`export-scan-flows.py`) |
| Type | Bugfix / collector visibility |
| Status | Done |

## Context

bank-fw-01 exports one NetFlow v5 UDP datagram (7 TCP DENY flows, 360 bytes) to `pfe-netflow-tools-1:2055` (`nfcapd` 1.7.5). Operators inspect with `nfdump -R /data/netflow` and Import Latest Flows.

## Problem

A second real scan was sent (`sent records=7` twice) and arrived on UDP 2055, but `nfdump` showed only the first 7-flow batch.

nfcapd 1.7.5 keeps the live interval in memory. `nfcapd.current.*` on disk is a 40-byte placeholder. `nfdump -R` reads finalized `nfcapd.YYYYMMDDHHMM` files only. The next UDP datagram closes the slot. After idle/sleep there is no following packet, so the latest scan stays invisible.

Not an exporter dedup, v5 timestamp, sequence, or delivery bug.

## Design

Lab-only, after a successful real v5 `sendto`:

1. Send one extra 1-byte UDP datagram (`0x00`) to the same host/port.
2. That payload is not NetFlow v5 (no version=5 header, not 7 scan records).
3. Do not increment `flow_sequence`.
4. If the flush `sendto` fails, log a warning and keep the real export successful.

nfcapd configuration, Syslog, and Sentinel correlation are unchanged.

## Implementation (files)

| File | Role |
| --- | --- |
| `pfe-containerlab/services/netflow/export-scan-flows.py` | `send_nfcapd_slot_flush` after real send; sent-log line |
| `pfe-containerlab/services/netflow/test_export_scan_flows.py` | Flush payload / send / failure / v5 size tests |

## Tests

```bash
python3 /home/mnaddari/pfe-containerlab/services/netflow/test_export_scan_flows.py
```

- Flush payload is not a v5 packet.
- Flush `sendto` uses collector:2055.
- `OSError` → warning, no exception.
- Real 7-record packet stays 360 bytes, version 5, sequence unchanged by flush helper.

## Result or example

```text
sent records=7 src=10.0.0.10 dst=10.10.10.20 proto=TCP ports=21,22,23,80,443,3306,5432 dest=172.30.30.2:2055 bytes=360
flush datagram sent dest=172.30.30.2:2055
```

Immediate `nfdump -R /data/netflow "src ip 10.0.0.10 and dst ip 10.10.10.20"` shows the new 7 flows without waiting for the 5-minute rotate.

## Out of scope

- nfcapd `-t` / Docker CMD
- Syslog, correlation, topology
- Changing v5 First/Last/`unix_secs`/`flow_sequence`

## Verify commands

Restart the exporter so it loads the bind-mounted script, then:

```bash
docker exec clab-bank-lab-bank-fw-01 sh -c 'pkill -f export-scan-flows.py || true; sh /opt/firewall/setup-firewall.sh'
docker exec clab-bank-lab-attacker-01 sh /opt/scenarios/port-scan.sh
sleep 8
docker exec clab-bank-lab-bank-fw-01 tail -5 /var/log/bank-netflow-sent.log
docker exec pfe-netflow-tools-1 nfdump -R /data/netflow -o "fmt:%ts %sa %da %sp %dp %pkt" "src ip 10.0.0.10 and dst ip 10.10.10.20"
```

Import Latest Flows in Sentinel; new rows should import (hash differs by source port / time).

## Rapport talking points

- Collector file rotation, not export success, gated nfdump visibility.
- A non-NetFlow 1-byte UDP datagram is enough to close nfcapd 1.7.5’s current slot.
- Dummy flush must not look like v5 so it cannot become PORT_SCAN evidence.
