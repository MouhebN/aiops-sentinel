#!/bin/sh
# CAMERA DOWN: stop RTSP (MediaMTX + ffmpeg) only.
# HTTP :8081 stays up. GET /health remains HTTP 200 (liveness); JSON stream=unavailable.
# Zombies are ignored; they cannot hold :8554. Live processes are terminated.

set -eu

MTX_PID=/var/run/mediamtx.pid
FFMPEG_PID=/var/run/camera-ffmpeg.pid

live_pid() {
  pid=$1
  [ -n "$pid" ] || return 1
  [ -d "/proc/$pid" ] || return 1
  state=$(awk '/^State:/{print $2}' "/proc/$pid/status" 2>/dev/null || true)
  [ -n "$state" ] && [ "$state" != "Z" ]
}

collect_rtsp_pids() {
  python3 - <<'PY'
from pathlib import Path
pids = []
for p in Path("/proc").iterdir():
    if not p.name.isdigit():
        continue
    try:
        state = None
        for line in (p / "status").read_text().splitlines():
            if line.startswith("State:"):
                state = line.split()[1]
                break
        if state == "Z":
            continue
        cmd = (p / "cmdline").read_bytes().replace(b"\x00", b" ").decode("utf-8", "replace")
        if "/usr/local/bin/mediamtx" in cmd or "rtsp://127.0.0.1:8554/" in cmd:
            pids.append(p.name)
    except (OSError, PermissionError, IndexError):
        continue
print(" ".join(pids))
PY
}

kill_live() {
  for pid in "$@"; do
    live_pid "$pid" || continue
    kill "$pid" 2>/dev/null || true
  done
  i=0
  while [ "$i" -lt 15 ]; do
    left=
    for pid in "$@"; do
      live_pid "$pid" || continue
      left=1
    done
    [ -z "$left" ] && break
    i=$((i + 1))
    sleep 0.1 2>/dev/null || sleep 1
  done
  for pid in "$@"; do
    live_pid "$pid" || continue
    kill -9 "$pid" 2>/dev/null || true
  done
}

port_closed() {
  python3 - <<'PY'
import socket, time, sys
for _ in range(30):
    try:
        s = socket.create_connection(("127.0.0.1", 8554), 0.3)
        s.close()
    except OSError:
        sys.exit(0)
    time.sleep(0.1)
sys.exit(1)
PY
}

# shellcheck disable=SC2086
set -- $(collect_rtsp_pids)
if [ "$#" -gt 0 ]; then
  kill_live "$@"
fi
rm -f "$MTX_PID" "$FFMPEG_PID"

if ! port_closed; then
  echo "camera-01 warning: :8554 still open after stop" >&2
else
  echo "camera-01 RTSP stopped (:8554 closed, HTTP :8081 still up, node still up)"
fi
