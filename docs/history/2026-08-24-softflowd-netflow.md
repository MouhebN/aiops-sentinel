# 2026-08-24 — Continuous interface-level NetFlow on BANK-FW-01

| Field | Value |
| --- | --- |
| Date | 2026-08-24 |
| Module | Containerlab bank-fw-01 + nfcapd + Spring Boot import |
| Type | Feature / lab telemetry |
| Status | Done |

## Context

NetFlow used to be synthesized from nftables DENY log lines (`export-scan-flows.py`) plus a 1-byte UDP datagram so nfcapd 1.7.5 would finalize the current slot. Sentinel import was manual (“Import Latest Flows”).

## Problem

Flows only existed when a DENY log was parsed. Idle or accepted traffic produced no NetFlow. The dummy flush was not a real exporter. Operators had to click import. After Docker restart, Containerlab `exec` does not re-apply WAN/LAN addresses, so the firewall data-plane was empty even when `softflowd`/`fprobe` was running.

## Design

Independent paths from the same attacker packets:

```text
attacker traffic
   ├─ nftables → Syslog (UDP 5514)
   └─ fprobe on eth1 → NetFlow v5 → nfcapd:2055 → nfdump → scheduled import
```

**Monitored interface:** `bank-fw-01` **eth1** (WAN `10.0.0.1`, peer attacker `10.0.0.10`). DROPs never appear on eth2, so WAN is the observation point. Names come from the Containerlab node, not host veths.

**Exporter:** Alpine **softflowd 1.1.0** live capture uses `pcap_set_timeout(0)`. On lab veths, libpcap counters rise but `Packets processed` stays 0. pcap-file replay works; live `-i eth1` does not. **fprobe** 1.1 is the compatible packet-to-NetFlow v5 exporter (`-n 5`, idle 5s, active 15s, scan 2s). Topology hook remains `/opt/netflow/start-softflowd.sh`.

**Collector destination (dynamic):** `SENTINEL_NETFLOW_HOST` if IPv4 → `containerlab/.runtime/sentinel-netflow-host` (`attach-netflow.sh`) → `/var/lib/bank-fw/netflow.host` → gateway `172.30.30.1`. Not hardcoded `172.30.30.2`. `SOFTFLOWD_FORCE=1` restarts after dest/dataplane refresh.

**nfcapd:** `nfcapd -w /data/netflow -p 2055 -t 15 -e -4`. Rotation 15s. `nfexpire -t 2d -s 256m` (flow metadata, not PCAP). No dummy UDP flush.

**Ingestion:** `NetFlowImportScheduler` every 15s calls `importQuietly`. Empty/duplicate runs stay quiet. Manual import remains debug-only. Anomaly/correlation code unchanged.

**Old exporter:** `export-scan-flows.py` marked DEPRECATED/demo-only. `setup-firewall.sh` kills it and does not start it. Compose `netflow-demo-exporter` is behind profile `demo`.

## Implementation (files)

| File | Role |
| --- | --- |
| `containerlab/services/netflow/start-softflowd.sh` | Install/start fprobe on eth1; stop log exporter and leftover softflowd |
| `containerlab/services/netflow/stop-softflowd.sh` | Stop fprobe without destroying the node |
| `containerlab/services/netflow/netflow-dest.sh` | Dynamic collector IPv4 |
| `containerlab/services/netflow/start-exporter.sh` | Deprecated wrapper → fprobe |
| `containerlab/services/netflow/export-scan-flows.py` | Deprecated banner; kept for unit tests |
| `containerlab/services/firewall/setup-firewall.sh` | Syslog only; kill DENY-log exporter |
| `containerlab/scripts/restore-dataplane.sh` | Re-apply topology addresses after Docker restart |
| `containerlab/bank-lab.clab.yml` | Comment + exec `start-softflowd.sh` |
| `docker-compose.yml` | nfcapd `-t 15 -e`; auto-import env; demo exporter profile |
| `NetFlowImportService.importQuietly` / `NetFlowImportScheduler` | Automatic ingestion |
| `scripts/start-pfe.sh` | restore-dataplane then recover fprobe after dest refresh |
| `scripts/lib/pfe-env.sh` | `softflowd_alive` = live fprobe; `SOFTFLOWD_FORCE=1` |
| `docs/netflow-lab-export.md` | Operator notes |

## Tests

`NetFlowImportServiceIntegrationTests.scheduledImportOfEmptyDirectoryDoesNotFail`.

Lab 2026-08-24 (no topology destroy):

| Step | Result |
| --- | --- |
| A. start exporter | fprobe pid live, dest `172.30.30.3:2055`, eth1, no `export-scan-flows.py` |
| B. ping `10.0.0.1` + HTTP `:8080` | nfdump `10.0.0.10 → 10.10.10.20:8080`; Sentinel `anomalyType=null`; scheduled import `suspiciousFlows=0` |
| C. `port-scan.sh` | nftables DENY + syslog sent; nfdump dpt 21,22,23,80,443,3306,5432 from `10.0.0.10`; scheduler imported 7 flows; PORT_SCAN linked to syslog incident id 10; **no** `POST /api/netflow/import/latest` |
| D. stop/start | live fprobe 0 then 1; all 7 `clab-bank-lab-*` still running |

## Result / example

After `docker exec clab-bank-lab-attacker-01 sh /opt/scenarios/port-scan.sh`:

- Syslog: `DENY SRC=10.0.0.10 DST=10.10.10.20 DPT=21..5432` forwarded automatically
- nfdump: seven TCP flows `10.0.0.10 → 10.10.10.20` on those ports (plus earlier `:8080`)
- Backend log: `scheduled import ... recordsImported=7 suspiciousFlows=7 incidentsCreated=0` (correlated onto existing SECURITY incident 10)
- API: those seven flows `anomalyType=PORT_SCAN`, `incidentId=10`

## Out of scope

Topology IPs, nftables DENY set, Syslog parser, PCAP, AI, anomaly rule formulas, topology map.

## Verify commands

```bash
./scripts/start-pfe.sh
docker exec clab-bank-lab-bank-fw-01 sh /opt/netflow/start-softflowd.sh
docker exec pfe-netflow-tools-1 nfdump -R /data/netflow "src ip 10.0.0.10"
docker exec clab-bank-lab-attacker-01 sh /opt/scenarios/port-scan.sh
docker exec clab-bank-lab-bank-fw-01 sh /opt/netflow/stop-softflowd.sh
docker exec clab-bank-lab-bank-fw-01 sh /opt/netflow/start-softflowd.sh
```

## Limitations

- After Docker restart without `clab deploy`, data-plane IPs are gone until `restore-dataplane.sh` (called from `start-pfe.sh`).
- nfcapd visibility delay is the 15s rotation, not a fake flush packet.
- PID 1 is `sleep infinity`, so killed exporters remain as zombies; start/stop ignore State Z.
- Alpine softflowd is not used for live export on this lab (pcap timeout 0 on veth).

## Rapport talking points

- Packet capture on the WAN ingress, not log-to-flow translation
- Syslog and NetFlow are independent evidence of the same scan
- Collector rotation + exporter idle/active timers, not fake UDP, make nfcapd files visible
- Scheduled nfdump import replaces the operator click
