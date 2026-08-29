# 2026-08-27 — BANK-SRV-01 stale PID recovery

| Field | Value |
| --- | --- |
| Date | 2026-08-27 |
| Module | Containerlab bank-srv-01 HTTP service |
| Type | Bugfix / service lifecycle |
| Status | Done |

## Context

BANK-SRV-01 HTTP `:8080` is an operator-controlled lab service (`start-server.sh` / `stop-server.sh`). Sentinel probes `/health`. Stopping the API must leave the node up (PING still works).

## Problem

After the HTTP service was stopped, `start-server.sh` printed `already running pid=32` while `wget http://127.0.0.1:8080/health` was **connection refused**.

`start-server.sh` treated the PID file as truth:

```sh
if [ -f "$PIDFILE" ] && kill -0 "$(cat "$PIDFILE")"; then
  echo already running
  exit 0
fi
```

`kill -0` only tests that **some** process has that PID. After the python server exits, PID 32 can be reused (or the PID file left behind). The script never checked that the process was `/opt/bank-server/server.py` or that `:8080/health` answered. Recovery required a manual `stop-server.sh` first.

`stop-server.sh` used `pkill -f /opt/bank-server/server.py`, which can match unrelated command lines, and would `kill` whatever PID was in the file.

## Design

Do not trust the PID file alone. “Already running” requires:

1. numeric PID
2. process exists
3. command line contains `/opt/bank-server/server.py` **and** python
4. `GET /health` returns `bank-srv-01`

Otherwise remove the PID file (and stop a broken copy of our server if present) and start a new `nohup python3 /opt/bank-server/server.py`. After launch, wait until `/health` works; on failure delete the PID file, print `tail` of the log, exit non-zero.

Stop kills only PIDs that match the python server path. A foreign PID in the file is ignored (file still removed). Idempotent if the file is missing.

`server.py` is unchanged.

## Implementation (files)

| File | Role |
| --- | --- |
| `containerlab/services/bank-server/bank-server-lib.sh` | PID/cmdline/health helpers |
| `containerlab/services/bank-server/start-server.sh` | Stale-PID recovery + health wait |
| `containerlab/services/bank-server/stop-server.sh` | Kill only the bank-server python process |
| `scripts/tests/test-bank-server.sh` | Stale/invalid/missing PID, no duplicates, failed start |

## Tests

| Check | Result |
| --- | --- |
| Missing PID file starts the server | Pass |
| Healthy process: already running, no duplicate | Pass |
| Restart after stop | Pass |
| Dead PID / unrelated PID / invalid contents → start | Pass |
| Stop does not kill a foreign pidfile process | Pass |
| Failed start: exit 1, no leftover PID file | Pass |
| `scripts/tests/test-bank-server.sh` | 34 passed, 0 failed |

## Result / example

```text
# stale pidfile, port closed
bank-srv-01 stale PID file (pid=1 is not /opt/bank-server/server.py); removing
bank-srv-01 started pid=… :8080
{"status": "ok", "service": "bank-srv-01"}
```

Second start: `bank-srv-01 already running pid=…` (no second python).

## Out of scope

ATM/camera/snmp start scripts, Sentinel HTTP check code, `server.py` API.

## Verify commands

```bash
bash scripts/tests/test-bank-server.sh
docker exec clab-bank-lab-bank-srv-01 sh /opt/bank-server/stop-server.sh
docker exec clab-bank-lab-bank-srv-01 sh /opt/bank-server/start-server.sh
docker exec clab-bank-lab-bank-srv-01 wget -qO- http://127.0.0.1:8080/health
```

## Rapport talking points

- PID files are hints; liveness is process identity + health
- PID reuse is why `kill -0` is not enough after a crash or an incomplete stop
