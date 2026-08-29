# NetFlow Integration

## What NetFlow is

NetFlow is a flow telemetry format exported by routers, firewalls, and switches. It summarizes traffic conversations instead of storing full packets. A flow typically includes:

- source and destination IP
- source and destination port
- protocol
- packet and byte counts
- start and end time

That makes it useful for reconnaissance detection, volume monitoring, and traffic pattern analysis.

## Why AIOps Sentinel uses `nfcapd` and `nfdump`

AIOps Sentinel does not implement a raw NetFlow binary parser in Java.

Instead it uses:

- `nfcapd` to collect NetFlow records into files
- `nfdump` to decode those files into structured records

This keeps the collector path stable and cross-platform. AIOps Sentinel adds:

- import orchestration
- anomaly detection
- incident creation and linking
- AI context enrichment
- operator UI and reporting

AIOps Sentinel does not replace `nfdump`. It uses `nfdump` as a collection and decoding tool, then adds incident correlation, AI diagnosis, and reporting on top.

## Docker architecture

`docker-compose.yml` adds a `netflow-tools` service:

- UDP `2055` exposed for NetFlow exporters
- shared volume `netflow_data`
- `nfcapd` runs continuously inside the container
- backend mounts the same volume read-only at `/data/netflow`
- backend image includes `nfdump` and imports from that shared directory
- optional `netflow-demo-exporter` (Compose profile `demo`) can send fake NetFlow v5; it is **not** started by `./scripts/start-pfe.sh`

Relevant backend config:

- `app.netflow.enabled=${NETFLOW_ENABLED:true}`
- `app.netflow.nfdump-command=nfdump`
- `app.netflow.data-dir=${NETFLOW_DATA_DIR:./runtime/netflow}`
- `app.netflow.collector-port=${NETFLOW_COLLECTOR_PORT:2055}`
- `app.netflow.auto-import-enabled=${NETFLOW_AUTO_IMPORT_ENABLED:true}`
- `app.netflow.import-interval-ms=${NETFLOW_IMPORT_INTERVAL_MS:15000}`

Local override variables:

- `NETFLOW_ENABLED`
- `NETFLOW_DATA_DIR`
- `NETFLOW_NFDUMP_COMMAND`
- `NETFLOW_COLLECTOR_PORT`

## Start the collector

From the repository root:

```bash
docker compose up --build postgres fastapi backend netflow-tools
```

Recommended mode for NetFlow tooling is Docker. In this mode:

- `netflow-tools` runs `nfcapd`
- `netflow-tools` writes collector files into the shared `netflow_data` volume
- backend reads the same volume at `/data/netflow`
- backend runs `nfdump` inside the backend container on a 15s scheduler (`importQuietly`) and on manual **Import latest flows**
- lab NetFlow comes from **fprobe on BANK-FW-01 eth1**, not from DENY logs

The collector stores `nfcapd` files in the shared `netflow_data` volume.

## Linux and Windows notes

- You do not need `nfdump` installed on the Windows or Linux host when using Docker mode.
- Docker mode is the recommended cross-platform path for NetFlow tools.
- On Linux/macOS local non-Docker backend development, the backend defaults to `./runtime/netflow` and creates that directory automatically before import.
- Local non-Docker import still requires host `nfdump`, because the backend process executes `nfdump` directly.
- You can override `NETFLOW_DATA_DIR` to point at a mounted or local collector directory and `NETFLOW_NFDUMP_COMMAND` to the local `nfdump` binary.

## How to send demo NetFlow traffic

There are two supported paths:

1. Real collector path:
   - configure your exporter to send NetFlow to UDP `2055`
   - let `nfcapd` write files into `/data/netflow`
   - trigger automatic import (backend scheduler, 15s) or `Import latest flows` from the UI / `POST /api/netflow/import/latest?sourceId=<id>`

2. Demo helper path:
   - call `POST /api/netflow/import/sample`
   - this creates realistic sample flow records directly in backend code
   - use this if the external collector is not available during a demo

## Docker demo exporter

The living lab exporter is **fprobe** on BANK-FW-01 (`docs/netflow-lab-export.md`). Compose still ships a one-shot `netflow-demo-exporter` behind profile `demo` so it does not pollute nfcapd during normal start.

- It sends valid NetFlow v5 UDP packets to `netflow-tools:2055`
- It models a TCP port scan (not bank-lab IPs)
- Do not run it beside a live BANK-FW-01 scan demo

```bash
docker compose --profile demo run --rm netflow-demo-exporter
```

Inspect decoded records inside the backend container:

```bash
docker compose exec backend sh -lc 'nfdump -R /data/netflow -o "csv:%tsr,%ter,%sa,%da,%sp,%dp,%pr,%pkt,%byt,%ra,%in,%out" | head -n 30'
```

Then trigger `Import latest flows` from the UI or call:

```bash
curl -X POST "http://localhost:8080/api/netflow/import/latest?sourceId=<id>" \
  -H "Authorization: Bearer <token>"
```

The imported demo records should satisfy the `PORT_SCAN` rule because the same source IP targets the same destination IP across at least 5 distinct TCP destination ports inside the detection window.

## How to import latest flows

Use either:

- the `NetFlow Traffic Analysis` page
- `POST /api/netflow/import/latest?sourceId=<id>`

The backend runs `nfdump` with `ProcessBuilder`, parses decoded records, deduplicates them, stores them, and runs anomaly detection.

If `nfdump` is missing or the data directory is empty, the API returns a clean error instead of crashing the application.

## Anomalies and incidents

Current detection rules:

- `PORT_SCAN`
  - same source IP
  - same destination IP
  - TCP
  - 5 or more distinct destination ports in a 2-minute window

- `MANY_DESTINATIONS`
  - same source IP
  - 5 or more destination IPs in a 2-minute window

- `SENSITIVE_PORT_ACCESS`
  - destination port in `22, 3389, 445, 3306, 5432, 1433`

- `HIGH_VOLUME_TRANSFER`
  - bytes above the configured threshold

Detected anomalies:

- mark involved flows as suspicious
- record anomaly type and reason
- create or reuse a `SECURITY` incident
- prefer linking to an existing matching Syslog incident when possible

## AI context usage

For incidents with linked NetFlow evidence, AI context includes:

- flow count
- source and destination IP
- observed destination ports
- protocols
- total packets
- total bytes
- anomaly type
- anomaly reason
- time window
- NetFlow source name

The FastAPI prompt builder includes a `NetFlow evidence` section. When Syslog, NetFlow, and packet capture evidence all exist for the same incident, the prompt explicitly marks that as multi-source support for the diagnosis.
