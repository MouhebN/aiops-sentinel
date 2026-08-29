# NetFlow Traffic Analysis Feature – AIOps Sentinel

This guide explains the Real NetFlow Traffic Analysis feature: what it is, why it exists, how it works in demo and production, and how to present it to a client, student, or jury.

It describes what is implemented today. It does not invent extra products or collectors.

---

## 1. Simple explanation

**NetFlow is not full packet capture.**

A packet capture (PCAP / Wireshark) stores the content of packets: headers and, often, payload. That is packet-level evidence. It is precise, but heavy. You cannot keep it running all day on every link.

NetFlow stores **communication summaries**. Each flow is a conversation between two hosts, typically:

- source IP and destination IP
- source port and destination port
- protocol (TCP, UDP, …)
- packet count and byte count
- start time and end time

Think of it this way:

| | PCAP / Wireshark | NetFlow |
|---|---|---|
| What it stores | Individual packets | Traffic conversations |
| Size | Large | Light |
| Best for | Deep diagnosis of one incident | Continuous traffic behavior |
| Example question | “What was in this packet?” | “Who talked to whom, on which ports, and how much?” |

AIOps Sentinel already supports PCAP upload on an incident. NetFlow is the lighter, always-on companion: it watches traffic **behavior** over time instead of storing every packet.

---

## 2. Why we added this feature

The project is an intelligent IT supervision platform. The subject includes **logs and network supervision**. Active checks (PING, HTTP, SNMP) tell you if a device is up. Syslog tells you what a firewall or server logged. PCAP explains one capture in depth.

None of those continuously answers: **who is scanning whom, which sensitive ports are being reached, and how much data is moving.**

NetFlow adds that traffic-behavior layer. It helps detect:

- port scans
- one source contacting many destinations
- access to sensitive service ports (SSH, RDP, SMB, databases)
- unusually large transfers

It complements, it does not replace:

- **Syslog** — device logs and deny messages
- **SNMP / active monitoring** — reachability and device health
- **PCAP** — packet-level proof when an operator uploads a capture

Together they support a stronger AI diagnosis: logs + flows + optional packets.

---

## 3. Architecture

### Production architecture

```text
Router / firewall / switch
        │  NetFlow export (UDP, typically 2055)
        ▼
nfcapd collector          (netflow-tools container)
        │  writes collector files
        ▼
shared volume /data/netflow
        │
        ▼
nfdump decoder            (inside the backend container)
        │
        ▼
AIOps Sentinel backend
        │  stores NetworkFlow records
        ▼
anomaly detection
        │
        ▼
SECURITY incident created or linked
        │
        ▼
dashboard + AI report (FastAPI / Ollama)
```

In production, the exporter is a real network device (or a host tool such as `softflowd`). AIOps Sentinel does **not** parse raw NetFlow binaries in Java. It uses `nfcapd` to collect and `nfdump` to decode.

### Demo architecture

```text
netflow-demo-exporter
        │  valid NetFlow v5 UDP packets
        ▼
netflow-tools (nfcapd on UDP 2055)
        │  shared Docker volume netflow_data → /data/netflow
        ▼
backend (nfdump -R /data/netflow)
        │  Import Latest Flows
        ▼
NetworkFlow records + PORT_SCAN detection
        │
        ▼
incident + Related NetFlow Evidence + AI report
```

The demo exporter sends **real NetFlow v5 packets**, not a fake JSON shortcut. The pattern is a TCP port scan from `192.168.1.106` to `192.168.1.110` on ports `21, 22, 23, 80, 443, 3306, 5432`.

There is also a backend helper, `POST /api/netflow/import/sample`, which inserts sample records in code when the collector is not available. The Docker exporter is the path that proves the real collector chain.

### Docker services involved

| Service | Role |
|---|---|
| `postgres` | Stores NetworkFlow records, sources, import runs, incidents |
| `backend` | Import, decode with `nfdump`, detect anomalies, link incidents |
| `fastapi` | AI analysis, including NetFlow evidence in the prompt |
| `ollama` | Optional LLM (`llama3.2:3b`) |
| `netflow-tools` | Runs `nfcapd`, writes `/data/netflow` |
| `netflow-demo-exporter` | One-shot valid NetFlow v5 demo traffic |

Shared volume: `netflow_data` mounted at `/data/netflow` (collector read-write, backend read-only).

---

## 4. Components created or used

| Component | What it does |
|---|---|
| `netflow-tools` container | Runs `nfcapd` and listens on UDP `2055` |
| `netflow-demo-exporter` container | Sends valid NetFlow v5 demo records |
| `nfcapd` | Collects exporter packets into files |
| `nfdump` | Decodes those files into CSV-like records the backend can parse |
| Backend NetFlow module | Sources, import, storage, anomalies, incident linking |
| Frontend NetFlow page | `/netflow` — sources, import, flow table, import status |
| PostgreSQL `network_flows` | Persisted flow records, including `incidentId` when linked |
| AI context builder | Builds NetFlow summaries for the incident, not a dump of every row |
| Ollama / `llama3.2:3b` | Optional AI analysis; rules engine is the fallback |

Not implemented now: ElastiFlow, OpenSearch, or any other flow analytics platform. Those remain a possible future provider. Today the only implemented provider type is `NFDUMP`.

---

## 5. Backend feature summary

### NetFlowSource

A configured collector path: name, data directory, UDP port, enabled flag. In Docker the directory is `/data/netflow`. Operators (and admins) can create and enable sources from the UI.

### NetworkFlow

One decoded conversation: IPs, ports, protocol, packets, bytes, times, optional anomaly type/reason, and the linked `incidentId`.

### NetFlowImportRun

One import attempt: records read, imported, skipped (duplicates), suspicious count, incidents created, status `SUCCESS` or `FAILED`.

### Import Latest Flows

UI button or:

```text
POST /api/netflow/import/latest?sourceId=<id>
```

Requires `ADMIN` or `OPERATOR`. The backend runs `nfdump` with `ProcessBuilder`, parses the output, deduplicates by record hash, stores new rows, then runs anomaly detection.

### Anomaly detection

`NetFlowAnomalyDetectionService` applies the four rules below on the imported batch, marks matching flows as suspicious, and links them to incidents.

### Incident linking

Import is global (all decoded flows are stored). Evidence display is incident-specific (`GET /api/incidents/{id}/network-flows`). See section 7.

### AI context integration

For a linked incident, the backend sends NetFlow **summaries** (anomaly, IPs, ports, counts, bytes, reason) to FastAPI. The prompt does not replay dozens of identical NetFlow event rows.

---

## 6. Anomaly rules

Detection window for scan-style rules: **2 minutes**.

### PORT_SCAN

**Meaning.** Same source IP, same destination IP, TCP, at least **5 distinct destination ports** in the window.

**Example.** `192.168.1.106` probes `192.168.1.110` on 21, 22, 23, 80, 443, 3306, 5432. That is the Docker demo.

**Why it matters in a bank.** This is classic reconnaissance: find open services before an attack. On a branch or datacenter segment it is a security incident, not normal user traffic.

### MANY_DESTINATIONS

**Meaning.** Same source IP contacts at least **5 destination IPs** in the window.

**Example.** One workstation or compromised host talks to many internal servers in a few seconds.

**Why it matters in a bank.** It can be a sweep of the LAN, worm-like behavior, or a misconfigured scanner. Either way, operations and security need to know.

### SENSITIVE_PORT_ACCESS

**Meaning.** Traffic to a sensitive destination port: `22` (SSH), `3389` (RDP), `445` (SMB), `3306` (MySQL), `5432` (PostgreSQL), `1433` (SQL Server).

**Example.** An unusual client reaches the core-banking database port.

**Why it matters in a bank.** Those ports protect remote admin and data stores. Unexpected access is a control and audit concern, even when it is not yet a confirmed breach.

### HIGH_VOLUME_TRANSFER

**Meaning.** A flow whose byte count is above the configured threshold (default **100 MB**).

**Example.** A large copy from a file server or an unexpected outbound dump.

**Why it matters in a bank.** Volume spikes can mean backup jobs, but they can also mean data exfiltration. The platform flags the behavior; a human decides.

When several rules match the same flow, the strongest one is kept (port scan outranks many destinations, then sensitive port, then high volume).

---

## 7. Incident linking logic

**Import is global. Evidence is not.**

The NetFlow page shows all imported flows. The incident detail page calls only:

```text
GET /api/incidents/{incidentId}/network-flows
```

It does **not** load `GET /api/netflow/flows`. The UI also keeps rows whose `incidentId` matches the open incident.

A flow is linked when:

- source IP matches (and destination IP matches, except `MANY_DESTINATIONS`, which is source-centric)
- category is `SECURITY`, and anomaly type matches when it is present
- the incident is **ACTIVE** or **recent** (about 30 minutes)
- first/last seen times overlap that window

Then:

1. If a matching **Syslog** SECURITY incident exists for the same source/destination/time window, NetFlow evidence is **attached to it** (enrichment).
2. Otherwise a new SECURITY incident is created, or a recent matching NetFlow incident is reused.
3. Unrelated camera, UPS, or availability incidents are not touched.
4. Old recovered incidents are not reused. A new generation is created instead of reopening a stale case.

Validated behavior: unrelated incidents stay clean; Syslog-backed matching incidents can show Related NetFlow Evidence.

---

## 8. Demo commands

Start the stack (from the repository root):

```bash
docker compose up --build -d postgres ollama fastapi backend netflow-tools
```

Pull the LLM only if you want AI from Ollama (optional, not automatic):

```bash
docker compose exec ollama ollama pull llama3.2:3b
```

Run the demo exporter once:

```bash
docker compose run --rm netflow-demo-exporter
```

Inspect decoded flows inside the backend container:

```bash
docker compose exec backend sh -lc 'nfdump -R /data/netflow -o "csv:%tsr,%ter,%sa,%da,%sp,%dp,%pr,%pkt,%byt,%ra,%in,%out" | head -n 30'
```

Then in the UI:

1. Open **NetFlow Traffic Analysis**
2. Confirm a source pointing at `/data/netflow` (Docker) and port `2055`
3. Click **Import Latest Flows** (operator or admin)

Expected result:

- Total flows today > 0
- Suspicious flows > 0
- Latest import status **SUCCESS**
- Top source IP `192.168.1.106`, destination ports including `21, 22, 23, 80, 443, 3306, 5432`
- A SECURITY incident created or a matching Syslog incident enriched
- **Related NetFlow Evidence** visible on that incident only
- AI report can use NetFlow evidence (`provider=ollama`, `model=llama3.2:3b` when Ollama is up)

Example validation already observed in this project: 14 flows today, 14 suspicious, import SUCCESS, demo port-scan pattern.

---

## 9. AI / Ollama integration

FastAPI is the AI service. The backend sends incident context, including NetFlow summaries, to `POST /api/analyze-incident`.

When Ollama is available:

- provider: `ollama`
- model: `llama3.2:3b` (from `OLLAMA_MODEL`, not a manual CLI flag)
- timeout: 300 seconds

If Ollama is down, the model is missing, the call times out, or the JSON is invalid, FastAPI uses the **rule-based fallback**. The app does not crash.

Prompt size is limited on purpose:

- at most the latest 10 related events
- NetFlow incidents use summaries instead of repeating every flow event
- oversized prompts are trimmed (around 12 000 characters)

Example expected AI output for the demo scan: `provider=ollama`, `model=llama3.2:3b`, priority **P1**, with NetFlow evidence (source, destination, ports, anomaly `PORT_SCAN`) in the reasoning.

Check the provider:

```bash
curl http://localhost:8001/api/ai/provider-status
```

---

## 10. Questions and answers

**Is NetFlow the same as Wireshark?**  
No. Wireshark/PCAP is packet-level. NetFlow is a summary of conversations. AIOps Sentinel can use both: NetFlow continuously, PCAP when an operator uploads a file.

**Why do we not build our own NetFlow parser?**  
Raw NetFlow (v5, v9, IPFIX) is binary and version-sensitive. A custom Java parser would be fragile and expensive for a PFE. `nfdump` already does that job well.

**Why use nfcapd / nfdump?**  
`nfcapd` collects. `nfdump` decodes. They are the standard open-source pair. AIOps Sentinel adds what they do not: incidents, correlation with Syslog, dashboard, and AI.

**Why do we use a demo exporter container?**  
So we can show a full collector path without a physical router in the lab: real UDP NetFlow v5 → `nfcapd` → volume → `nfdump` → import → incident.

**Is the demo exporter fake?**  
The *scenario* is simulated (a port scan pattern). The *packets* are valid NetFlow v5. Production would send the same kind of packets from a router, firewall, switch, or `softflowd`.

**What changes in production?**  
Point a real exporter at UDP `2055` (or the collector you deploy). Stop using `netflow-demo-exporter`. Keep `nfcapd` / `nfdump` / import / anomalies / incidents.

**Does the bank employee need to understand nfdump?**  
No. They use the NetFlow page and the incident screen. `nfdump` stays in the backend container.

**Why not ElastiFlow / OpenSearch now?**  
Those are optional future providers for large-scale flow analytics. They are **not implemented**. The current provider is `NFDUMP` only.

**How does the incident know which NetFlow records belong to it?**  
Each suspicious flow stores `incidentId`. Linking uses source/destination, anomaly/category, and a recent time window. The incident API returns only those rows.

**Can NetFlow replace Syslog or PCAP?**  
No. Syslog explains what the device logged. PCAP proves packet content. NetFlow explains traffic behavior. AIOps is stronger when several sources agree.

**Why is this useful for AIOps?**  
AIOps needs structured evidence, not only a red light on a dashboard. NetFlow gives repeatable signals (scan, sweep, sensitive port, volume) that incidents and the AI module can explain in bank language.

---

## 11. Explication orale (soutenance)

« Le module NetFlow permet de superviser le comportement du trafic réseau, pas le contenu des paquets. Un routeur ou un pare-feu exporte des résumés de conversations : qui parle à qui, sur quels ports, avec quel volume. Dans la démonstration, un conteneur envoie de vrais enregistrements NetFlow v5 vers un collecteur `nfcapd`. Le backend les décode avec `nfdump`, détecte par exemple un scan de ports, et rattache cette preuve uniquement à l’incident de sécurité concerné. Ce n’est pas du Wireshark : c’est plus léger, adapté à une surveillance continue, et complémentaire des logs syslog et des captures PCAP. L’IA s’appuie sur ces synthèses pour expliquer l’incident, par exemple un scan depuis 192.168.1.106. En production, on remplacerait l’exportateur de démo par le routeur ou le pare-feu de la banque. »

---

## 12. Troubleshooting

**No matching flows / empty import**  
The collector directory has no `nfcapd` files yet, or the exporter did not run. Start `netflow-tools`, run the demo exporter, then inspect with `nfdump` before importing.

**Wrong data directory**  
- Docker backend: `/data/netflow` (shared volume, read-only).  
- Local backend without Docker: `./runtime/netflow` and host `nfdump`.  
If the UI source still points at `./runtime/netflow` while you run Compose, import will look in the wrong place inside the container.

**403 Forbidden vs 400**  
- **403**: the user is a viewer, or the token is missing. Import requires ADMIN or OPERATOR.  
- **400**: authorization worked; the collector path failed (empty dir, missing `nfdump`, no valid records). Read the API message.

**nfdump parser issue**  
The backend expects the CSV format it requests from `nfdump`. If `nfdump` is missing, the API says so instead of crashing. Check `docker compose exec backend nfdump -V`.

**Ollama falls back to rules**  
Look at FastAPI logs: `Ollama request failed, using rules fallback. reason=...`  
Check `curl http://localhost:8001/api/ai/provider-status` (`ollamaReachable`, `modelAvailable`). Pull the model if needed: `docker compose exec ollama ollama pull llama3.2:3b`.

**Model timeout**  
Prompts are already reduced (max 10 events, NetFlow summaries, 300s timeout). If it still times out, confirm FastAPI was rebuilt so `OLLAMA_TIMEOUT_SECONDS=300` is applied: `docker compose up --build -d fastapi`.

---

## 13. Final status checklist

Use this before a demo or soutenance:

- [ ] Collector running (`netflow-tools` / `nfcapd`)
- [ ] Demo exporter executed (`docker compose run --rm netflow-demo-exporter`)
- [ ] `nfdump` shows decoded flows in `/data/netflow`
- [ ] Import Latest Flows success
- [ ] Suspicious flows detected
- [ ] Incident created or matching Syslog incident linked
- [ ] Related NetFlow Evidence visible on that incident only
- [ ] AI report uses NetFlow evidence (`ollama` / `llama3.2:3b` when Ollama is up)

---

## Related docs

- Collector and Docker details: `docs/netflow-integration.md`
- Ollama in Docker: `docs/ollama-docker.md`
- PCAP upload test: `docs/testing-packet-capture-diagnosis.md`
- Syslog test: `docs/testing-syslog-ingestion.md`
