# Operator demo scenarios

Reproduce PFE lab conditions with one command each. Scripts only change **network/service state**. They do not insert database rows, call incident APIs, fabricate Syslog/NetFlow/PCAP, or ACK/RESOLVE incidents.

Start the platform once:

```bash
./scripts/start-pfe.sh
```

Open http://localhost:3000 (Nginx in Docker). `npm run dev` is only needed when editing the React code.

Then either:

```bash
./scripts/demo/01-baseline.sh
```

or:

```bash
./scripts/demo/demo.sh baseline
```

`DEMO_FORCE=1` skips the port-scan cooldown (default 180s) if you must rerun `03-port-scan.sh`.

Observation delay: local service checks wait up to ~10–15s. Sentinel monitoring/correlation is typically **30–45s** (NetFlow import a bit longer; AUTO_ROLLING PCAP ~80s after eligibility). Refresh the UI; do not rerun the attack script while waiting.

---

## Scenarios

| Script | Command | What it simulates | Expected in Sentinel | Delay | Jury talking point |
| --- | --- | --- | --- | --- | --- |
| `01-baseline.sh` | `./scripts/demo/demo.sh baseline` | Status check, BANK-SRV `/health`, allowed HTTP :8080 and ping from ATTACKER-01 | Lab healthy, BANK-SRV UP, `suspiciousFlows` 0, no security incident | Immediate + one NetFlow slot | Show infrastructure map all UP before any attack |
| `02-sensitive-port.sh` | `./scripts/demo/demo.sh sensitive-port` | Five TCP SYNs to `10.10.10.20:22` (1s apart) | Syslog DENY, NetFlow **SENSITIVE_PORT_ACCESS**, one SECURITY incident, **not** PORT_SCAN | ~30–45s | Contrast with a full scan: one port ≠ PORT_SCAN |
| `03-port-scan.sh` | `./scripts/demo/demo.sh port-scan` | Existing `/opt/scenarios/port-scan.sh` **once** | Syslog + NetFlow PORT_SCAN + AUTO_ROLLING PCAP, one correlated incident, topology attack edge, target BANK-SRV-01 | Syslog seconds; NetFlow ~30s; PCAP ~80s | Main security demo: three evidence sources, then Analyze in UI |
| `04-bank-server-down.sh` | `./scripts/demo/demo.sh bank-down` | `stop-server.sh` (HTTP only) | HTTP_HEALTH + TCP_PORT fail, BANK-SRV **DOWN**, AVAILABILITY incident | ~30–45s | Node still pings; availability ≠ reachability |
| `05-bank-server-up.sh` | `./scripts/demo/demo.sh bank-up` | `start-server.sh` (stale-PID safe) | BANK-SRV **UP**, availability recovers per lifecycle | ~30–45s | Recovery without deleting the incident row |
| `06-atm-degraded.sh` | `./scripts/demo/demo.sh atm-down` | `stop-atm-service.sh` (TCP 9090 only) | PING UP, TCP_PORT fail, ATM **DEGRADED** | ~30–45s | Mixed checks → DEGRADED, not DOWN |
| `07-atm-recover.sh` | `./scripts/demo/demo.sh atm-up` | `start-atm-service.sh` | TCP_PORT recovers, ATM UP | ~30–45s | Client traffic was not killed |
| `08-camera-rtsp-down.sh` | `./scripts/demo/demo.sh camera-down` | `stop-camera.sh` (RTSP only) | HTTP_HEALTH UP, RTSP_HEALTH fail, camera **DEGRADED** | ~30–45s | Stream down, device HTTP still answers |
| `09-camera-recover.sh` | `./scripts/demo/demo.sh camera-up` | `start-camera.sh` | RTSP_HEALTH recovers | ~30–45s | MediaMTX + ffmpeg, HTTP kept |
| `99-reset-lab.sh` | `./scripts/demo/demo.sh reset` | Re-run in-node start scripts | Services listen again; **history kept** | ~15s local, then monitoring | Say out loud: incidents/events are not deleted |

---

## Recommended 5–10 minute jury sequence

1. `./scripts/start-pfe.sh` (if the lab is not already up)
2. `./scripts/demo/01-baseline.sh` — UI: infrastructure healthy
3. `./scripts/demo/03-port-scan.sh` — UI: Syslog + NetFlow + PCAP + topology edge + **Analyze**
4. ACK / RESOLVE that incident **in the UI**
5. `./scripts/demo/04-bank-server-down.sh` — UI: BANK-SRV DOWN + availability incident
6. `./scripts/demo/05-bank-server-up.sh` — UI: recovery
7. `./scripts/demo/99-reset-lab.sh`

Skip 02 / ATM / camera live unless time remains. Do not run `03` twice (AUTO_ROLLING is once per episode; cooldown applies).

---

## Safety

- No `docker compose down`, no `clab destroy`, no Postgres wipe
- Port scan: one existing scenario script; cooldown unless `DEMO_FORCE=1`
- BANK / ATM / camera use the official in-node start/stop helpers
- Reset restores **processes**, not history
