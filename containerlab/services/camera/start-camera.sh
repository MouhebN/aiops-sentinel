#!/bin/sh
# CAMERA UP / RECOVERY: MediaMTX on :8554, then ffmpeg publisher, HTTP on :8081.
# PID 1 is sleep(infinity) and does not reap. Zombies must not count as "already running".

set -eu

HTTP_PID=/var/run/camera-http.pid
MTX_PID=/var/run/mediamtx.pid
FFMPEG_PID=/var/run/camera-ffmpeg.pid
HTTP_LOG=/var/log/camera.log
MTX_LOG=/var/log/mediamtx.log
FFMPEG_LOG=/var/log/camera-ffmpeg.log
MTX_BIN=/usr/local/bin/mediamtx
MTX_CONF=/opt/camera/mediamtx.yml
RTSP_URL="rtsp://127.0.0.1:8554/live"

live_pid() {
  pid=$1
  [ -n "$pid" ] || return 1
  [ -d "/proc/$pid" ] || return 1
  state=$(awk '/^State:/{print $2}' "/proc/$pid/status" 2>/dev/null || true)
  [ -n "$state" ] && [ "$state" != "Z" ]
}

live_cmd_pids() {
  needle=$1
  python3 - "$needle" <<'PY'
import os, sys
from pathlib import Path
needle = sys.argv[1]
self_pid = str(os.getpid())
pids = []
for p in Path("/proc").iterdir():
    if not p.name.isdigit() or p.name == self_pid:
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
        if needle in cmd:
            pids.append(p.name)
    except (OSError, PermissionError, IndexError):
        continue
if not pids:
    raise SystemExit(1)
print(" ".join(pids))
PY
}

live_pids_named() {
  live_cmd_pids "/usr/local/bin/mediamtx"
}

live_ffmpeg_pids() {
  live_cmd_pids "rtsp://127.0.0.1:8554/"
}

pidfile_live() {
  file=$1
  [ -f "$file" ] || return 1
  live_pid "$(cat "$file" 2>/dev/null || true)"
}

port_open() {
  port=$1
  python3 - "$port" <<'PY'
import socket, sys
try:
    s = socket.create_connection(("127.0.0.1", int(sys.argv[1])), 1)
    s.close()
except OSError:
    raise SystemExit(1)
PY
}

wait_port() {
  port=$1
  python3 - "$port" <<'PY'
import socket, time, sys
port = int(sys.argv[1])
for _ in range(40):
    try:
        s = socket.create_connection(("127.0.0.1", port), 1)
        s.close()
        sys.exit(0)
    except OSError:
        time.sleep(0.25)
sys.exit(1)
PY
}

rtsp_stream_ok() {
  python3 - <<'PY'
import socket, sys

def exchange(req):
    s = socket.create_connection(("127.0.0.1", 8554), 2)
    s.settimeout(2)
    s.sendall(req.encode("ascii"))
    data = s.recv(2048).decode("utf-8", "replace")
    s.close()
    return data

try:
    options = exchange(
        "OPTIONS rtsp://127.0.0.1:8554/live RTSP/1.0\r\n"
        "CSeq: 1\r\nUser-Agent: camera-start\r\n\r\n"
    )
    describe = exchange(
        "DESCRIBE rtsp://127.0.0.1:8554/live RTSP/1.0\r\n"
        "CSeq: 2\r\nAccept: application/sdp\r\nUser-Agent: camera-start\r\n\r\n"
    )
except OSError:
    raise SystemExit(1)
if not options.startswith("RTSP/1.0 200"):
    raise SystemExit(1)
if not (describe.startswith("RTSP/1.0 200") and "application/sdp" in describe.lower()):
    raise SystemExit(1)
PY
}

wait_rtsp_stream() {
  python3 - <<'PY'
import socket, time, sys

def describe():
    req = (
        "DESCRIBE rtsp://127.0.0.1:8554/live RTSP/1.0\r\n"
        "CSeq: 1\r\n"
        "Accept: application/sdp\r\n"
        "User-Agent: camera-start\r\n\r\n"
    )
    s = socket.create_connection(("127.0.0.1", 8554), 2)
    s.settimeout(2)
    s.sendall(req.encode("ascii"))
    data = s.recv(2048).decode("utf-8", "replace")
    s.close()
    return data

for _ in range(40):
    try:
        data = describe()
        if data.startswith("RTSP/1.0 200") and "application/sdp" in data.lower():
            sys.exit(0)
    except OSError:
        pass
    time.sleep(0.5)
sys.exit(1)
PY
}

kill_pids() {
  for pid in "$@"; do
    live_pid "$pid" || continue
    kill "$pid" 2>/dev/null || true
  done
  i=0
  while [ "$i" -lt 10 ]; do
    left=
    for pid in "$@"; do
      live_pid "$pid" || continue
      left=1
    done
    [ -z "$left" ] && return 0
    i=$((i + 1))
    sleep 0.1 2>/dev/null || sleep 1
  done
  for pid in "$@"; do
    live_pid "$pid" || continue
    kill -9 "$pid" 2>/dev/null || true
  done
}

stop_stale_rtsp() {
  pids=
  if pidfile_live "$MTX_PID"; then
    pids="$pids $(cat "$MTX_PID")"
  fi
  named=$(live_pids_named mediamtx || true)
  pids="$pids $named"
  ff=$(live_ffmpeg_pids || true)
  pids="$pids $ff"
  if pidfile_live "$FFMPEG_PID"; then
    pids="$pids $(cat "$FFMPEG_PID")"
  fi
  # shellcheck disable=SC2086
  set -- $pids
  if [ "$#" -gt 0 ]; then
    echo "camera-01 cleaning stale/broken RTSP processes: $*"
    kill_pids "$@"
  fi
  rm -f "$MTX_PID" "$FFMPEG_PID"
}

mtx_ready() {
  live_pids_named mediamtx >/dev/null 2>&1 || return 1
  port_open 8554
}

ffmpeg_live() {
  live_ffmpeg_pids >/dev/null 2>&1
}

start_mediamtx() {
  nohup "$MTX_BIN" "$MTX_CONF" >> "$MTX_LOG" 2>&1 < /dev/null &
  echo $! > "$MTX_PID"
  echo "camera-01 MediaMTX started pid=$(cat "$MTX_PID") :8554"
}

start_ffmpeg() {
  nohup ffmpeg -hide_banner -loglevel error -nostdin -re \
    -f lavfi -i testsrc2=size=320x240:rate=10 \
    -c:v libx264 -pix_fmt yuv420p -preset ultrafast -tune zerolatency \
    -profile:v baseline -g 20 -an \
    -f rtsp "$RTSP_URL" >> "$FFMPEG_LOG" 2>&1 < /dev/null &
  echo $! > "$FFMPEG_PID"
  echo "camera-01 ffmpeg publisher started pid=$(cat "$FFMPEG_PID")"
}

require_bins() {
  if ! command -v ffmpeg >/dev/null 2>&1; then
    echo "camera-01: ffmpeg not found. Build and use image banklab-camera:local" >&2
    exit 1
  fi
  if [ ! -x "$MTX_BIN" ] && ! command -v mediamtx >/dev/null 2>&1; then
    echo "camera-01: mediamtx not found. Build and use image banklab-camera:local" >&2
    exit 1
  fi
  if [ ! -x "$MTX_BIN" ]; then
    MTX_BIN=$(command -v mediamtx)
  fi
  if [ ! -f "$MTX_CONF" ]; then
    echo "camera-01: missing $MTX_CONF" >&2
    exit 1
  fi
}

require_bins

if port_open 8081; then
  echo "camera-01 HTTP already running"
else
  nohup python3 /opt/camera/camera-server.py >> "$HTTP_LOG" 2>&1 < /dev/null &
  echo $! > "$HTTP_PID"
  echo "camera-01 HTTP started pid=$(cat "$HTTP_PID") :8081"
fi

if ! wait_port 8081; then
  echo "camera-01 HTTP :8081 did not become ready (see $HTTP_LOG)" >&2
  tail -n 40 "$HTTP_LOG" >&2 || true
  exit 1
fi

if mtx_ready; then
  echo "camera-01 MediaMTX already running (:8554 listening)"
else
  echo "camera-01 MediaMTX not ready (need live process AND TCP :8554)"
  stop_stale_rtsp
  start_mediamtx
  if ! wait_port 8554; then
    echo "camera-01 RTSP :8554 did not become ready (see $MTX_LOG)" >&2
    tail -n 40 "$MTX_LOG" >&2 || true
    exit 1
  fi
fi

if ffmpeg_live && rtsp_stream_ok; then
  echo "camera-01 ffmpeg publisher already running"
else
  if ffmpeg_live; then
    echo "camera-01 ffmpeg is up but /live is unhealthy; restarting publisher"
    # shellcheck disable=SC2046
    kill_pids $(live_ffmpeg_pids || true)
    rm -f "$FFMPEG_PID"
  fi
  start_ffmpeg
  if ! wait_rtsp_stream; then
    echo "camera-01 RTSP /live did not publish SDP (see $FFMPEG_LOG $MTX_LOG)" >&2
    tail -n 40 "$FFMPEG_LOG" >&2 || true
    tail -n 40 "$MTX_LOG" >&2 || true
    exit 1
  fi
fi

echo "camera-01 ready  rtsp://0.0.0.0:8554/live  http://0.0.0.0:8081/health"
