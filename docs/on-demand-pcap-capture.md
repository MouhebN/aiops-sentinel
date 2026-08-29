# On-demand packet capture

AIOps Sentinel can request a **selective, time-bounded PCAP** for an incident, attach it, and run the existing FastAPI/tshark analysis. The Spring backend never talks to Docker, `nsenter`, or `tcpdump`.

## Lab path

```text
BANK-FW-01 WAN (eth1, attacker 10.0.0.10 ↔ firewall 10.0.0.1)
        ↓  same network namespace
lightweight capture sensor (tcpdump, HTTP :8090)
        ↓  GET/POST /captures
PacketCaptureProvider (LAB_SENSOR)
        ↓
Sentinel  →  existing /api/analyze-pcap (tshark)
```

The Containerlab WAN link is a point-to-point veth (`attacker-01:eth1` — `bank-fw-01:eth1`). There is no shared WAN Docker network to join.

The lab sensor therefore runs as a sidecar with `--network container:clab-bank-lab-bank-fw-01` plus `NET_RAW`/`NET_ADMIN`. It sees the same packets as a manual `tcpdump -i eth1` inside BANK-FW-01. Sentinel reaches it at the firewall management IP: `http://172.30.30.10:8090`.

This is **not** `Spring → docker exec firewall`.

## Production path

```text
SPAN / TAP / packet broker / firewall mirror
        ↓
packet capture sensor (or NDR export API)
        ↓
PacketCaptureProvider
        ↓
Sentinel
```

The lab tcpdump sensor is **one** implementation of `PacketCaptureProvider`. It does not replace enterprise NDR (Arkime, Suricata, vendor capture APIs). Those can be added later as other providers (`FIREWALL_API`, `ARKIME`, `SURICATA`, `EXTERNAL_SENSOR`) without changing incident UI or tshark analysis.

## Capture points

Configured in Spring, not hardcoded to incident type `PORT_SCAN`:

```properties
app.pcap.points.bank-firewall-wan.provider=LAB_SENSOR
app.pcap.points.bank-firewall-wan.display-name=BANK-FW-01 / WAN
app.pcap.points.bank-firewall-wan.interface-name=eth1
```

The first lab scenario uses this default point. BPF is built on the sensor from validated IPs (`host 10.0.0.10 and host 10.10.10.20`). The browser never sends a filter string.

## Limits

| Limit | Value |
| --- | --- |
| Default duration | 20 s |
| Min / max duration | 5 / 60 s |
| Max PCAP size | 20 MB |
| Max concurrent captures | 1 |
| Active jobs per incident | 1 |
| Live capture on RESOLVED | disabled |

Hung `tcpdump` is wrapped with `timeout --kill-after=5s`. Temporary files live under `/tmp/captures` on the sensor.

## Automatic forensic capture (rolling buffer)

The sensor keeps a **bounded** rotating buffer on BANK-FW-01 `eth1` (default 12×5 s ≈ 60 s pre-trigger). Old ring files are overwritten. Disk stays capped (`max-file-size-mb=20`). This is not unlimited full-packet capture.

NetFlow detection can lag the packets (exporter flow expiry + nfcapd rotation + scheduled import). A live run saw PORT_SCAN ~27 s after the scan while the ring was only 20 s, so the original SYNs had already rotated out (`EMPTY_CAPTURE`). The 60 s window covers that latency. Syslog DENY missing on that same run is a **separate** problem (firewall UDP syslog), not a buffer-timing issue.

When an open SECURITY incident **first becomes eligible** (`PORT_SCAN` / `SUSPICIOUS` / `RECON`, WARNING+), whether at creation or later after Syslog/NetFlow enrichment:

1. Snapshot the current ring (pre-trigger)
2. Continue ~20 s post-trigger
3. Merge with `mergecap` or a libpcap merge (never raw concatenation)
4. Optionally filter to incident src/dst
5. Attach one PCAP and run the existing tshark path

A generic firewall DENY that is later classified as `PORT_SCAN` on the **same** episode still triggers exactly one `AUTO_ROLLING` job. Extra Syslog/NetFlow rows on that episode do not snapshot again.

Manual **Capture traffic** remains available (`MANUAL`). Automatic jobs are `AUTO_ROLLING`.

Incident correlation is never blocked: `IncidentEligibleForAutoCaptureEvent` is handled after commit. Sensor/provider failure stores `PROVIDER_UNAVAILABLE` on the job.

**Limitation:** if the rolling buffer was not running before the attack (sensor down, just started), pre-trigger packets can still be missing.

### Production mapping

```text
SPAN / TAP / packet broker
        ↓
sensor with bounded rolling PCAP buffer
        ↓
incident trigger preserves pre + post window
        ↓
Sentinel analyzes the incident PCAP (tshark)
```

### Config (`app.pcap`)

```properties
app.pcap.auto.enabled=true
app.pcap.auto.categories=SECURITY
app.pcap.auto.signal-tokens=PORT_SCAN,SUSPICIOUS,RECON
app.pcap.auto.min-severity=WARNING
app.pcap.rolling.enabled=true
app.pcap.rolling.pre-trigger-seconds=60
app.pcap.rolling.post-trigger-seconds=20
app.pcap.rolling.segment-seconds=5
app.pcap.rolling.max-file-size-mb=20
```

## Startup

`./scripts/start-pfe.sh` starts or recovers the sensor **after** the lab firewall is up. If health fails (stale namespace, missing `eth1`, missing `rolling` in `/health`, or rolling enabled but not running), the sensor container is removed, the image is rebuilt, and the sensor is recreated. A running sensor is not enough: NetworkMode must point at the **current** BANK-FW-01 container, `eth1` must exist, and `/health` must include a running rolling buffer when enabled.

If `eth1` appears after the sensor process starts (data-plane restore), the rolling janitor retries tcpdump automatically.

`./scripts/status-pfe.sh` reports data-plane interfaces plus sensor container / namespace (CURRENT vs STALE) / eth1 / health API / rolling buffer.

`./scripts/stop-pfe.sh` removes the sensor **before** `clab destroy`.
