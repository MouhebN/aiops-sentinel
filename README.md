# AIOps Sentinel

End-to-end **AIOps / SOC supervision** platform: a React dashboard, Spring Boot backend, local LLM analysis, and a real mini bank network in [Containerlab](https://containerlab.dev/).

The lab (firewall, router, switch, bank server, ATM, camera, attacker) never calls the API. Sentinel **observes** it the way a production tool would: Syslog, NetFlow, packet capture, ICMP/HTTP/TCP/RTSP/SNMP health checks.

**UI:** http://localhost:3000 — `admin@aiops.local` / `admin123`

---

## Highlights

- **Correlated incidents** from live telemetry (not mocked events)
- **Syslog** — nftables DENY lines from BANK-FW-01 → UDP 5514
- **NetFlow v5** — fprobe on the firewall WAN → nfcapd → scan vs single-port access
- **AUTO_ROLLING PCAP** — rolling tcpdump on the firewall, snapshot around detection
- **Availability** — UP / DEGRADED / DOWN from mixed health checks (e.g. ATM pingable, TCP down)
- **Local AI analysis** — FastAPI + Ollama, with a deterministic fallback if the model is slow
- **One command** to start Compose + lab + UI (`./scripts/start-pfe.sh`)

```text
ATTACKER-01
     |
BANK-FW-01     Syslog + NetFlow + PCAP
     |
CORE-RTR-01
     |
CORE-SW-01
   /    |    \
BANK-SRV-01   ATM-01   BANK-CAMERA-01
```

## Stack

| Layer | Tech |
| --- | --- |
| UI | React, Vite, MUI — Nginx in Docker |
| API | Spring Boot 3, PostgreSQL |
| AI | FastAPI, Ollama (`llama3.2:3b`) |
| Lab | Containerlab, Alpine/Python nodes, nftables, fprobe, MediaMTX |
| Capture | Sidecar sensor in the firewall network namespace |

## Requirements

Linux or **WSL2 Ubuntu** (not native Windows). Docker, Docker Compose, [Containerlab](https://containerlab.dev/). ~16 GB RAM recommended.

Clone onto the Linux filesystem (`~/aiops-sentinel`), not `/mnt/c/...`.

## Quick start

```bash
docker compose build
./containerlab/scripts/build-lab-images.sh
./scripts/start-pfe.sh
./scripts/status-pfe.sh
```

Open http://localhost:3000

```bash
./scripts/stop-pfe.sh          # lab + Compose down, volumes kept
```

`start-pfe.sh` does not rebuild images. Rebuild after code changes: `docker compose build`.

Frontend hot reload: `docker compose stop frontend && cd frontend && npm run dev`.

## Demo scenarios

Scripts change **lab state only** (no fake DB rows). Wait ~30–45 s and refresh the UI after outages.

```bash
./scripts/demo/01-baseline.sh          # healthy lab, no security incident
./scripts/demo/04-bank-server-down.sh
./scripts/demo/05-bank-server-up.sh
./scripts/demo/06-atm-degraded.sh    # ping OK, TCP 9090 down → DEGRADED
./scripts/demo/07-atm-recover.sh
./scripts/demo/08-camera-rtsp-down.sh
./scripts/demo/09-camera-recover.sh
./scripts/demo/99-reset-lab.sh      # restore services, keep history

# last — these create security incidents
./scripts/demo/02-sensitive-port.sh  # SSH :22, not a port scan
./scripts/demo/03-port-scan.sh      # run once; Syslog + NetFlow + PCAP
```

## How telemetry works

| Source | What it is | Lab path |
| --- | --- | --- |
| Syslog | Firewall log lines | nft DENY → UDP 5514 → Spring |
| NetFlow | Flow metadata (IPs, ports, counts) | fprobe eth1 → nfcapd :2055 → import every 15s |
| PCAP | Actual packets | Rolling capture on FW eth1, auto-snapshot on PORT_SCAN |

Allowed bank HTTP is `:8080`. Ports `21,22,23,80,443,3306,5432` from the attacker to the bank server are **denied** and logged.

More detail: [docs/project-guide.md](docs/project-guide.md) · [docs/environment-lifecycle.md](docs/environment-lifecycle.md)

## Repository layout

```text
docker-compose.yml          postgres, backend, fastapi, ollama, netflow, frontend
backend/                    Spring Boot
frontend/                   React (dev: Vite; prod: Nginx)
fastapi/                    Incident analysis
capture-sensor/             tcpdump sidecar
containerlab/bank-lab.clab.yml
scripts/start-pfe.sh
scripts/demo/               Operator scenarios
```
