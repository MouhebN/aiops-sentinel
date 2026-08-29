#!/bin/sh
# ATM UP / RECOVERY: start the TCP availability listener on 9090. Does not recreate the node.
# Does not start or stop atm-client.sh (LAN traffic toward bank-srv-01).
# No runtime package install.

set -eu

PIDFILE=/var/run/atm-service.pid
LOG=/var/log/atm-service.log
SCRIPT=/opt/atm/atm-service.sh
PORT="${ATM_TCP_PORT:-9090}"

already_running() {
  if [ -f "$PIDFILE" ] && kill -0 "$(cat "$PIDFILE")" 2>/dev/null; then
    return 0
  fi
  if pgrep -f "/opt/atm/atm-service.sh" >/dev/null 2>&1; then
    return 0
  fi
  if pgrep -f "nc -n -lk -p ${PORT}" >/dev/null 2>&1; then
    return 0
  fi
  if nc -z -w 1 127.0.0.1 "$PORT" >/dev/null 2>&1; then
    return 0
  fi
  return 1
}

if already_running; then
  echo "atm-01 TCP service already running on :$PORT"
  exit 0
fi

rm -f "$PIDFILE"
nohup sh "$SCRIPT" >> "$LOG" 2>&1 < /dev/null &
echo $! > "$PIDFILE"

i=0
while [ "$i" -lt 20 ]; do
  if nc -z -w 1 127.0.0.1 "$PORT" >/dev/null 2>&1; then
    echo "atm-01 TCP service started pid=$(cat "$PIDFILE") :$PORT log=$LOG"
    exit 0
  fi
  i=$((i + 1))
  sleep 0.1 2>/dev/null || sleep 1
done

echo "atm-01 TCP service failed to listen on :$PORT (see $LOG)" >&2
tail -n 20 "$LOG" >&2 || true
exit 1
