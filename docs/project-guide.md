# AIOps Sentinel — project guide

Bank-oriented AIOps platform: Sentinel **observes** a mini bank network. Lab nodes never call Sentinel APIs. Detection comes from real Syslog, NetFlow, PCAP, and health checks.

UI: http://localhost:3000  
Login: `admin@aiops.local` / `admin123` (also `operator@aiops.local` / `operator123`)

```bash
docker compose build
./containerlab/scripts/build-lab-images.sh
./scripts/start-pfe.sh
./scripts/status-pfe.sh
```

---

## Windows PC: do not run this natively

Containerlab, `nftables`, veth data-plane links, and the capture sensor need **Linux**. PowerShell / Git Bash / Docker Desktop “Linux containers” on NTFS is not enough.

**Use WSL2 Ubuntu** (or another Linux VM) and put the project **inside the Linux filesystem**, not under `/mnt/c/...`.

### Copy checklist

1. Install **WSL2 + Ubuntu 22.04/24.04**.
2. Inside Ubuntu: Docker Engine **or** Docker Desktop with “Use the WSL 2 based engine” and Ubuntu integration.
3. Install **Containerlab** in that same Ubuntu (`clab version` must work).
4. Copy the repo to e.g. `~/pfe` (`/home/<you>/pfe`).  
   **Do not** run from `C:\Users\...\Desktop\pfe` via `/mnt/c/Users/...`. Bind mounts, veths, and file watching break or become extremely slow.
5. If you zip/USB-copy from Windows: convert scripts if they got CRLF:

   ```bash
   find scripts containerlab -name '*.sh' -exec sed -i 's/\r$//' {} \;
   ```

   `set -euo pipefail` with a `\r` fails with a cryptic error.
6. RAM: **16 GB host** is comfortable (Ollama + Compose + 7 lab nodes). 8 GB is tight.
7. Free host ports: `3000`, `8080`, `8001`, `5432`, `11434`, UDP `5514`, UDP `2055`.
8. Nested virtualization: if Ubuntu is a VM on VMware/VirtualBox, Containerlab veths often fail. WSL2 or a real Linux install is the intended path.
9. First start on a new machine **must** build images (`start-pfe.sh` is `--no-build`):

   ```bash
   docker compose build
   ./containerlab/scripts/build-lab-images.sh   # banklab-camera:local
   ./scripts/start-pfe.sh
   ```

10. Timezone: Syslog RFC 3164 is parsed as **Africa/Tunis**. Keep `TZ` / `APP_SYSLOG_DEFAULT_TIMEZONE` as in Compose unless you change lab senders too.

`npm run dev` is optional (React hot reload). Default UI is Docker/Nginx on `:3000`. Stop it first if you need Vite: `docker compose stop frontend`.

---

## What the project is

Two planes:

| Plane | Network | Role |
| --- | --- | --- |
| **Management** | `banklab-mgmt` `172.30.30.0/24` | `docker exec`, SNMP, Syslog UDP 5514, NetFlow collector, capture-sensor HTTP |
| **Data plane** | WAN `10.0.0.0/24`, transit `10.0.1.0/30`, LAN `10.10.10.0/24` | Real attacker / firewall / bank traffic |

```text
ATTACKER-01  10.0.0.10
     |
BANK-FW-01   WAN 10.0.0.1   (nft DENY + Syslog + fprobe NetFlow + capture)
     |
CORE-RTR-01  LAN gw 10.10.10.1
     |
CORE-SW-01   L2 bridge
   /    |    \
BANK-SRV-01  10.10.10.20  HTTP :8080
ATM-01       10.10.10.30  TCP :9090
BANK-CAMERA-01 10.10.10.40 HTTP :8081 + RTSP :8554
```

Compose (Sentinel): postgres, backend (Spring), fastapi+Ollama, netflow-tools (`nfcapd`), frontend (Nginx). Backend and nfcapd also join `banklab-mgmt`. Frontend does **not**.

---

## Syslog — device said it blocked something

**What it is:** RFC 3164 text logs over **UDP**. Not packet payloads. “The firewall wrote a DENY line.”

**Lab path:**

1. Attacker hits `10.10.10.20` on a DENY-listed port (`21,22,23,80,443,3306,5432`). Port **8080 is allowed**.
2. `nftables` on BANK-FW-01 **drops** and `log prefix "BANK-FW-01 firewall: DENY "`.
3. `forward-syslog.sh` sends that line to the **current** backend mgmt IP, UDP **5514** (discovered, not hardcoded `172.30.30.3`).
4. Spring `SyslogIngestionService` parses (`FIREWALL_DENY`, IPs, port), stores an **Event**, correlates into a **SECURITY** incident.

**In the UI:** Events & Logs (`SYSLOG` source), Incidents, often Alerts. Parser profile / source IP if a Syslog Source matches.

**Delay:** usually a few seconds. Independent of NetFlow.

---

## NetFlow — who talked to whom, which ports, how many packets

**What it is:** flow **metadata** (src/dst IP, ports, packets, bytes, timestamps). Not a PCAP. Files are tiny.

**Lab path:**

1. **fprobe** on BANK-FW-01 **eth1** (WAN) exports NetFlow **v5** to nfcapd UDP **2055**. eth1 sees frames even when nft **drops** them (drops never reach eth2).
2. Compose `nfcapd` writes `/data/netflow` (15 s rotation).
3. Spring imports every **15 s**, detectors: `PORT_SCAN` (many dest ports) vs **`SENSITIVE_PORT_ACCESS`** (few attempts to e.g. :22 only).
4. Flows attach to the same incident episode when IPs/time window match.

**Delay:** ~30–45 s (exporter idle timeout + nfcapd slot + import). Do not rerun the attack while waiting.

**Not Syslog:** Syslog is the firewall’s log line. NetFlow is “these two IPs exchanged N packets on these ports.” They should **agree** on a scan.

---

## PCAP — actual packets around the detection

**What it is:** raw Ethernet/IP/TCP bytes (tcpdump). tshark later summarizes them for the AI.

**Lab path:**

1. `pfe-capture-sensor` shares BANK-FW-01’s **network namespace** (`--network container:...`) so it sees **eth1** like `tcpdump -i eth1` inside the firewall.
2. A **rolling ring** (~60 s pre-trigger, 5 s segments) is always recording.
3. On first eligible **SECURITY** episode (`PORT_SCAN` / recon tokens), Sentinel requests **AUTO_ROLLING**: copy pre-trigger ring + ~20 s post-trigger, merge, analyze.
4. Operator can also click **Capture traffic** (manual) on an open incident.

**Delay:** ~80 s after eligibility for AUTO_ROLLING. Ring must be **warm** (~60 s after sensor start). `03-port-scan.sh` warns if it is not.

**Not NetFlow:** PCAP proves SYN flags / missing 443 / etc. NetFlow only has counters.

No WebSocket/SSE; the UI polls job status.

---

## How the three sources fit together (port scan)

```text
ATTACKER-01  nc to 10.10.10.20:{21,22,23,80,443,3306,5432}
        │
        ├─ nft DENY  → Syslog UDP 5514     → FIREWALL_DENY events
        ├─ eth1 pkts  → fprobe → nfcapd     → PORT_SCAN flows
        └─ eth1 pkts  → rolling tcpdump     → AUTO_ROLLING PCAP
                │
                ▼
        One correlated SECURITY incident
        Topology attack edge → BANK-SRV-01
        Analyze (FastAPI/Ollama) uses Syslog + NetFlow + PCAP + health
```

Five SYNs to **:22 only** (`02-sensitive-port.sh`) → Syslog DENY + NetFlow **SENSITIVE_PORT_ACCESS**, **not** `PORT_SCAN`, usually **no** AUTO_ROLLING (policy is recon/PORT_SCAN tokens).

Allowed HTTP `:8080` and ping to `10.0.0.1` (`01-baseline.sh`) must **not** create a security incident.

---

## Availability vs security

Sentinel also **polls** components (PING, HTTP_HEALTH, TCP_PORT, RTSP_HEALTH, SNMP). Mixed checks → **DEGRADED**. All required checks fail → **DOWN**. That creates **AVAILABILITY** incidents, not SECURITY.

| Node | Typical checks | Outage demo |
| --- | --- | --- |
| BANK-SRV-01 | PING + HTTP_HEALTH + TCP :8080 | Stop HTTP only → DOWN (HTTP+TCP fail; ping still works) |
| ATM-01 | PING + TCP :9090 | Stop TCP only → **DEGRADED** |
| Camera | PING + HTTP :8081 + RTSP :8554 | Stop RTSP only → **DEGRADED** |

Containers stay up. Scripts never `docker kill` the node.

---

## Two kinds of “component”

### 1. Containerlab node (the real device)

Edit `containerlab/bank-lab.clab.yml`:

- `nodes.<name>`: image, `mgmt-ipv4`, `binds` (scripts under `containerlab/services/`), `exec` (IPs, routes, start script)
- `links`: `endpoints: ["core-sw-01:eth5", "new-node:eth1"]`
- Service helpers: start/stop only the process you need (HTTP, TCP, RTSP), not PID 1

Then keep **start-pfe recovery** in sync:

- `scripts/lib/pfe-env.sh` → `LAB_NODES`
- `scripts/lib/pfe-dataplane.sh` → `DATAPLANE_EXPECTATIONS` + restore addresses
- `recover_node_daemons` if there is a new daemon
- Camera-like images: `containerlab/scripts/build-lab-images.sh`

Redeploy lab (`clab destroy --keep-mgmt-net` then start) if you add **interfaces**. Address-only changes can use restore.

The node must **not** POST to `/api`. Sentinel discovers it by polling / receiving telemetry.

### 2. Sentinel inventory (the UI object)

**Components** page: name, type, IPs (mgmt + data-plane), monitoring methods, SNMP community `banklab`, HTTP URL / TCP port / RTSP URL as required.

**Topology** page: relations (FW—RTR, SW—SRV, …). Attack edges appear when SECURITY evidence points at a target.

**Syslog sources / NetFlow sources:** map sender IPs (e.g. FW `172.30.30.10`) to devices so events attach to the right node.

If the lab node exists but is missing from Components, health/incidents will not show it.

---

## Proof-of-concept demos

Scripts only change **lab service/network state**. They do not insert DB rows, fake Syslog/NetFlow/PCAP, or ACK/RESOLVE.

Wait **30–45 s** and **refresh the UI** after each outage/attack. Local checks (`:8080` closed) are immediate; Sentinel is not.

### Availability first (no security evidence)

```bash
./scripts/demo/01-baseline.sh
```

- **Pass** (exit 0), helpers/root/`status-pfe.sh` work
- BANK-SRV `/health` OK; attacker GET `:8080` and ping `10.0.0.1` OK
- **No** security incident; `suspiciousFlows` stay 0

```bash
./scripts/demo/04-bank-server-down.sh
```

- Stops **only** BANK-SRV HTTP (`/opt/bank-server/stop-server.sh`)
- `:8080` closed; container still running
- UI: BANK-SRV **DOWN**, **AVAILABILITY** incident

```bash
./scripts/demo/05-bank-server-up.sh
```

- `/health` back
- UI: BANK-SRV **UP** after a monitor cycle (incident lifecycle unchanged; do not edit DB)

```bash
./scripts/demo/06-atm-degraded.sh
```

- ATM still **pingable**; TCP **9090** down
- UI: **DEGRADED**, not DOWN

```bash
./scripts/demo/07-atm-recover.sh
```

- `:9090` restored → ATM **UP**

```bash
./scripts/demo/08-camera-rtsp-down.sh
```

- HTTP health stays up; RTSP fails → camera **DEGRADED**

```bash
./scripts/demo/09-camera-recover.sh
```

- RTSP back → camera **UP**

```bash
./scripts/demo/99-reset-lab.sh
```

- Services listening again
- **Incidents/events/Postgres kept** (not a data reset)

### Security last (creates incidents)

```bash
./scripts/demo/02-sensitive-port.sh
```

- Five TCP attempts to `10.10.10.20:22`
- Expect: firewall DENY, NetFlow **SENSITIVE_PORT_ACCESS**, one SECURITY incident, **not** PORT_SCAN, src `10.0.0.10` dst `10.10.10.20` port 22

```bash
./scripts/demo/03-port-scan.sh
```

- **Once** `/opt/scenarios/port-scan.sh` (cooldown 180 s unless `DEMO_FORCE=1`)
- Expect: Syslog + NetFlow **PORT_SCAN** + AUTO_ROLLING PCAP, one correlated incident, ports 21–5432 as listed, topology edge, target BANK-SRV-01
- Do **not** run twice; do not call AI from the script — click **Analyze** in the UI

Wrapper: `./scripts/demo/demo.sh baseline|bank-down|...|port-scan|reset`

---

## Useful commands

| Command | Purpose |
| --- | --- |
| `./scripts/start-pfe.sh` | Whole platform |
| `./scripts/status-pfe.sh` | Read-only health |
| `./scripts/stop-pfe.sh` | Lab + Compose down, volumes kept |
| `./scripts/demo/demo.sh reset` | Service reset, history kept |

After a host reboot, always `start-pfe.sh` (veths can vanish while containers still look “Up”).
