# Lab NetFlow: fprobe on BANK-FW-01

BANK-FW-01 exports **real** NetFlow v5 from packets on **eth1** (WAN), not from nftables DENY log text.

Alpine **softflowd 1.1.0** was tried first. Its live `pcap_set_timeout(0)` does not deliver low-rate veth packets (`Packets received by libpcap` increases, `Packets processed` stays 0). pcap-file replay works; live `-i eth1` does not. **fprobe** is the compatible packet-to-NetFlow v5 exporter on this lab.

```text
attacker traffic
   ├─ nftables -> Syslog (UDP 5514)
   └─ fprobe eth1 -> nfcapd UDP 2055
```

## Monitored interface

**eth1** on `bank-fw-01` (Containerlab name, not a host veth).

| Link | Addressing |
| --- | --- |
| attacker-01:eth1 — bank-fw-01:eth1 | 10.0.0.10/24 — 10.0.0.1/24 WAN |
| bank-fw-01:eth2 — core-rtr-01:eth1 | 10.0.1.1/30 — 10.0.1.2/30 transit |

Dropped scans never reach eth2. eth1 sees attacker frames whether nftables **accept** or **drop** them. That is why WAN eth1 is monitored rather than transit eth2.

## Collector destination

`attach-netflow.sh` writes `containerlab/.runtime/sentinel-netflow-host`.
`start-softflowd.sh` (starts fprobe) reads that file (then cache, then gateway `172.30.30.1`). It does **not** hardcode `172.30.30.2`. If the collector IP changes, start is forced to restart.

## nfcapd

Compose runs `nfcapd -w /data/netflow -p 2055 -t 15 -e -4`.

- 15-second file rotation (lab-visible without fake UDP flush packets)
- `nfexpire -t 2d -s 256m` (short retention; files are flow **metadata**, not PCAP)
- No dummy UDP flush. fprobe idle=5s / active=15s / scan=2s expires flows; nfcapd rotation finalizes files
- After Docker restart without `clab deploy`, `./scripts/start-pfe.sh` re-applies WAN/LAN addresses so eth1 actually carries attacker traffic

Sentinel imports incrementally every 15s. Duplicate nfdump rows are skipped by `recordHash`.

## Sentinel ingestion

Scheduled import every 15s (`NETFLOW_AUTO_IMPORT_ENABLED`, `NETFLOW_IMPORT_INTERVAL_MS`). Manual **Import Latest Flows** remains for debug.

## Deprecated

`export-scan-flows.py` (DENY-log → v5) is demo-only. `setup-firewall.sh` will kill it if it is still running. Do not run it beside fprobe.

Compose `netflow-demo-exporter` is behind profile `demo` so `start-pfe.sh` does not inject fake 192.168.x scans into nfcapd.
