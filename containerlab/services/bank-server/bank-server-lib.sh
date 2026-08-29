# Shared helpers for BANK-SRV-01 start/stop. Sourced, not executed.

PIDFILE="${BANK_SERVER_PIDFILE:-/var/run/bank-server.pid}"
LOG="${BANK_SERVER_LOG:-/var/log/bank-server.log}"
SCRIPT="${BANK_SERVER_SCRIPT:-/opt/bank-server/server.py}"
PORT="${BANK_SERVER_PORT:-8080}"
HEALTH_HOST="${BANK_SERVER_HEALTH_HOST:-127.0.0.1}"

is_numeric_pid() {
  echo "$1" | grep -Eq '^[1-9][0-9]*$'
}

read_pidfile() {
  [ -f "$PIDFILE" ] || return 1
  awk 'NF { print $1; exit }' "$PIDFILE" 2>/dev/null
}

pid_cmdline() {
  pid=$1
  if [ -r "/proc/${pid}/cmdline" ]; then
    tr '\0' ' ' < "/proc/${pid}/cmdline" 2>/dev/null
    return 0
  fi
  ps -o args= -p "$pid" 2>/dev/null || true
}

pid_is_our_server() {
  pid=$1
  is_numeric_pid "$pid" || return 1
  kill -0 "$pid" 2>/dev/null || return 1
  cmd=$(pid_cmdline "$pid")
  echo "$cmd" | grep -Fq "$SCRIPT" || return 1
  echo "$cmd" | grep -qi python || return 1
  return 0
}

health_ok() {
  url="http://${HEALTH_HOST}:${PORT}/health"
  body=""
  if command -v wget >/dev/null 2>&1; then
    body=$(wget -qO- -T 1 "$url" 2>/dev/null || true)
  elif command -v python3 >/dev/null 2>&1; then
    body=$(HEALTH_URL="$url" python3 - <<'PY'
import os, urllib.request
url = os.environ["HEALTH_URL"]
try:
    with urllib.request.urlopen(url, timeout=1) as resp:
        print(resp.read().decode("utf-8", "replace"))
except Exception:
    pass
PY
)
  else
    return 1
  fi
  echo "$body" | grep -q 'bank-srv-01'
}

list_server_pids() {
  for dir in /proc/[0-9]*; do
    [ -d "$dir" ] || continue
    pid=${dir#/proc/}
    [ "$pid" = "$$" ] && continue
    pid_is_our_server "$pid" || continue
    echo "$pid"
  done
}

service_is_healthy() {
  pid=$1
  pid_is_our_server "$pid" || return 1
  health_ok
}

clear_pidfile() {
  rm -f "$PIDFILE"
}

stop_server_pid() {
  pid=$1
  pid_is_our_server "$pid" || return 0
  kill "$pid" 2>/dev/null || true
  n=0
  while [ "$n" -lt 20 ]; do
    kill -0 "$pid" 2>/dev/null || return 0
    n=$((n + 1))
    sleep 0.1 2>/dev/null || sleep 1
  done
  kill -9 "$pid" 2>/dev/null || true
}
