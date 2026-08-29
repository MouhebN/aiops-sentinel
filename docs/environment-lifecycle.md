# Environment lifecycle (Compose + bank lab)

After a reboot, start everything with one command. Do not redeploy a healthy lab by hand.
Containers running is **not** proof the lab is healthy: host reboot can leave
Containerlab processes up while data-plane **veth** interfaces are gone.

## Commands

From the repository root (path is derived from the script; do not hardcode a home directory):

```bash
./scripts/start-pfe.sh
./scripts/status-pfe.sh
./scripts/stop-pfe.sh
```

`status-pfe.sh` is read-only. It exits 1 if Containerlab nodes are running but data-plane veths or the capture sensor are degraded, or if a running backend is missing from `banklab-mgmt`.

## Start flow (`start-pfe.sh`)

1. Ensure `banklab-mgmt` exists (create only if missing, same name/subnet/gateway Containerlab uses)
2. `docker compose up -d --no-build` (no image rebuild). Backend and netflow-tools join **both** the Compose default network and `banklab-mgmt` (external). Backend management IP is dynamic — not hardcoded. Frontend joins **only** the Compose default network (host port 3000).
3. Wait until postgres, fastapi, ollama, backend, netflow-tools, and frontend are **running**; wait until `http://127.0.0.1:3000/` returns HTTP success; repair `banklab-mgmt` attachment if Compose did not attach
4. Inspect Containerlab node state **and** data-plane veths (`eth1`/`eth2` on FW/RTR, `eth1` on attacker)
5. Recover the lab:
   - nodes absent → `containerlab deploy` (reuses the existing `banklab-mgmt` network; does not create a second one)
   - nodes running, veths present, IPs/routes missing → `restore-dataplane.sh` only
   - nodes running but a required veth is **missing** → stop capture sensor, `clab destroy --keep-mgmt-net`, deploy again (Compose/volumes untouched)
6. Re-apply data-plane addresses/routes (`restore-dataplane.sh`) — fails if veths are still missing
7. Recover in-node daemons with the existing idempotent start scripts (`start-snmpd.sh`, `start-server.sh`, ATM service+client, `start-camera.sh`)
8. Re-check `pfe-backend-1` and `pfe-netflow-tools-1` on `banklab-mgmt`
9. Run `attach-sentinel.sh` then `attach-netflow.sh` (write current mgmt IPs to `.runtime/` for lab destination discovery)
10. Restore BANK-FW-01 Syslog via `setup-firewall.sh` if collectors are down
11. Start/restart fprobe via `start-softflowd.sh` after NetFlow destination refresh
12. Create/recreate `pfe-capture-sensor` against the **current** BANK-FW-01 container id (must see `eth1`)
13. Lightweight readiness probes (warnings only for warm-up; missing veths already aborted)

Containers running is **not** enough: PID 1 is `sleep infinity`, and Containerlab `exec` does not re-run after a Docker/PC restart.

Frontend is served by Nginx in Docker (`http://localhost:3000`). `npm run dev` remains available for React hot reload (stop the Compose frontend if port 3000 is already in use).

## Stop flow (`stop-pfe.sh`)

1. Remove `pfe-capture-sensor` (it shares BANK-FW-01's network namespace)
2. `containerlab destroy -t <absolute yaml> --keep-mgmt-net`
3. `docker compose down` (no `-v`; Postgres/Ollama volumes stay)

`banklab-mgmt` is kept so Sentinel/netflow can share it on the next start. To remove it as well:

```bash
./scripts/stop-pfe.sh --delete-mgmt-net
```

## Idempotency

| Situation | Start behavior |
| --- | --- |
| Compose already up | `up -d --no-build` is a no-op for running containers |
| Lab nodes all running **and** expected veths exist | no deploy; restore addresses/routes; recover daemons (idempotent) |
| Lab nodes running but a data-plane veth is missing | **redeploy lab only** (not Compose); recreate capture sensor |
| Lab nodes absent | `containerlab deploy` |
| Lab partially present | **fail** (will not destroy a half-up lab) |
| Already on `banklab-mgmt` | skip `docker network connect` |
| Attach scripts | already connect-if-needed and rewrite `.runtime` IPs |
| Capture sensor stale / no `eth1` / `/health` fail | remove and recreate against current BANK-FW-01 |

## Readiness (warnings, not hard fail)

| Target | Check |
| --- | --- |
| BANK-SRV-01 | TCP `172.30.30.20:8080` |
| ATM-01 | TCP `172.30.30.30:9090` |
| BANK-CAMERA-01 | HTTP `172.30.30.40:8081`, TCP `8554` |
| FW / RTR / SW SNMP | `snmpd` (or UDP 161 listener) inside the node — no host `snmpget` |

## First-time setup

```bash
docker compose build
./containerlab/scripts/build-lab-images.sh   # banklab-camera:local
./scripts/start-pfe.sh
```

The UI is http://localhost:3000 after start. Rebuild the frontend image after UI changes: `docker compose build frontend`.

Containerlab may ask for sudo. Docker is run as the normal user.
