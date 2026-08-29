#!/bin/sh
# BANK SERVER UP / RECOVERY: start the HTTP API on 8080. Does not recreate the node.
# Does not trust a PID file alone (stale PID / PID reuse).

set -eu

# shellcheck source=bank-server-lib.sh
. "$(CDPATH= cd -- "$(dirname "$0")" && pwd)/bank-server-lib.sh"

mkdir -p "$(dirname "$PIDFILE")" "$(dirname "$LOG")" 2>/dev/null || true

if [ -f "$PIDFILE" ]; then
  pid=$(read_pidfile || true)
  if service_is_healthy "$pid"; then
    echo "bank-srv-01 already running pid=$pid"
    exit 0
  fi
  if pid_is_our_server "$pid"; then
    echo "bank-srv-01 pid=$pid is ${SCRIPT} but :${PORT}/health is down; restarting" >&2
    stop_server_pid "$pid"
  elif is_numeric_pid "$pid"; then
    echo "bank-srv-01 stale PID file (pid=$pid is not ${SCRIPT}); removing" >&2
  else
    echo "bank-srv-01 stale PID file (invalid contents); removing" >&2
  fi
  clear_pidfile
fi

existing=$(list_server_pids | awk 'NF { print $1; exit }' || true)
if [ -n "$existing" ] && service_is_healthy "$existing"; then
  echo "$existing" > "$PIDFILE"
  echo "bank-srv-01 already running pid=$existing"
  exit 0
fi
if [ -n "$existing" ]; then
  echo "bank-srv-01 found unresponsive ${SCRIPT} pid=$existing; restarting" >&2
  stop_server_pid "$existing"
fi

nohup python3 "$SCRIPT" >> "$LOG" 2>&1 < /dev/null &
pid=$!
echo "$pid" > "$PIDFILE"

i=0
while [ "$i" -lt 25 ]; do
  if ! kill -0 "$pid" 2>/dev/null; then
    clear_pidfile
    echo "bank-srv-01 process exited during startup (see $LOG)" >&2
    tail -n 30 "$LOG" >&2 || true
    exit 1
  fi
  if health_ok; then
    echo "bank-srv-01 started pid=$pid :$PORT log=$LOG"
    exit 0
  fi
  i=$((i + 1))
  sleep 0.2 2>/dev/null || sleep 1
done

kill "$pid" 2>/dev/null || true
stop_server_pid "$pid"
clear_pidfile
echo "bank-srv-01 failed to become healthy on :$PORT (see $LOG)" >&2
tail -n 30 "$LOG" >&2 || true
exit 1
